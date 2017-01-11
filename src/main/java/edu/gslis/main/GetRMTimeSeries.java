package edu.gslis.main;

import java.io.FileWriter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.main.temporal.TermTimeSeries;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.scorers.temporal.TemporalScorer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;

/**
 * Run initial query and build RM model.
 * For each RM model, get the term ACF.
 * a) from the collection
 * b) from the top 1000 documents
 */
public class GetRMTimeSeries 
{
	static int MAX_RESULTS=1000;
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( GetRMTimeSeries.class.getCanonicalName(), options );
            return;
        }
        
        String indexPath = cl.getOptionValue("index");
        String topicsPath = cl.getOptionValue("topics");
        int numFbDocs =Integer.parseInt(cl.getOptionValue("numFbDocs", "50"));
        int numFbTerms =Integer.parseInt(cl.getOptionValue("numFbTerms", "20"));
        String outputPath = cl.getOptionValue("output");
        String qrelsPath = cl.getOptionValue("qrels");
        long startTime = Long.parseLong(cl.getOptionValue("startTime"));
        long endTime = Long.parseLong(cl.getOptionValue("endTime"));
        long interval = Long.parseLong(cl.getOptionValue("interval"));
        

        GQueries queries = null;
        if (topicsPath.endsWith("indri")) 
            queries = new GQueriesIndriImpl();
        else
            queries = new GQueriesJsonImpl();
        queries.read(topicsPath);
        
        
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
        index.setTimeFieldName(Indexer.FIELD_EPOCH);
        Iterator<GQuery> queryIt = queries.iterator();
        while (queryIt.hasNext()) {
            GQuery query = queryIt.next();
            System.out.println("Query: " + query.getTitle());

            // Run the initial query, which will be used for re-ranking
            SearchHits results = index.runQuery(query, MAX_RESULTS);
            double[] docTimes = TemporalScorer.getTimes(results);
            
			Iterator<SearchHit> it = results.iterator();
			List<FeatureVector> docVectors = new LinkedList<FeatureVector>();
			while(it.hasNext()) {
				SearchHit hit = it.next();
				FeatureVector docVector = index.getDocVector(hit.getDocID(), null);
				docVectors.add(docVector);
			}
                        
            // Build the relevance model
            SearchHits fbDocs = new SearchHits(results.hits());
            fbDocs.crop(numFbDocs);
                    
            FeatureVector rmVector = buildRM(index, fbDocs, null, query, numFbDocs, docVectors);
            rmVector.clip(numFbTerms);
            rmVector.normalize();
            FeatureVector fv =
            		FeatureVector.interpolate(query.getFeatureVector(), rmVector, 0.5);
        
            // Build the time series index
            TermTimeSeries ts = buildTermTimeSeries(results.hits(), startTime, 
            		endTime, interval, rmVector.getFeatures());
            
            ts.save(outputPath + "/" + query.getTitle() + ".ts");
            
            FileWriter writer = new FileWriter(outputPath + "/" + query.getTitle() + ".rm");
            writer.write("term,score\n");
            for (String term: rmVector.getFeatures()) {
            	writer.write(term + "," + rmVector.getFeatureWeight(term) + "\n");
            }
            writer.close();
            
            /*for (String term: fv.getFeatures()) {
            	double[] freq = ts.getTermFrequencies(term);
            	double acf = rutil.acf(freq);
            }
            */
            
        }
    }
    
    private static TermTimeSeries buildTermTimeSeries(List<SearchHit> hits, long startTime, 
    		long endTime, long interval, Set<String> terms) 
    {
    	
    	TermTimeSeries ts = new TermTimeSeries(startTime, endTime, interval, terms);
    	        
    	for (SearchHit hit: hits)
    	{
    		long docTime = TemporalScorer.getTime(hit);
    		double score = hit.getScore();
    		
    		ts.addDocument(docTime, score, hit.getFeatureVector());
        }

    	return ts;
    }
    
    
	public static FeatureVector buildRM(IndexWrapper index, SearchHits relDocs, Stopper stopper, 
			GQuery originalQuery, int fbDocCount, List<FeatureVector> docVectors) 
	{
		FeatureVector fv = new FeatureVector(stopper);
		try {
			Set<String> vocab = new HashSet<String>();

			if (fbDocCount > relDocs.size())
				fbDocCount = relDocs.size();
			
			List<FeatureVector> fbDocVectors = 
					new LinkedList<FeatureVector>(docVectors.subList(0, fbDocCount));
			if(relDocs == null) {
				relDocs = index.runQuery(originalQuery, fbDocCount);
			}

			double[] rsvs = new double[relDocs.size()];
			int k=0;
			Iterator<SearchHit> hitIterator = relDocs.iterator();
			while(hitIterator.hasNext()) {
				SearchHit hit = hitIterator.next();
				vocab.addAll(fbDocVectors.get(k).getFeatures());
				rsvs[k++] = Math.exp(hit.getScore());
			}
			
			Iterator<String> it = vocab.iterator();
			while(it.hasNext()) {
				String term = it.next();
								
				double fbWeight = 0.0;

				Iterator<FeatureVector> docIT = fbDocVectors.iterator();
				k=0;
				while(docIT.hasNext()) {
					FeatureVector docVector = docIT.next();
					double docProb = docVector.getFeatureWeight(term) / docVector.getLength();
					docProb *= rsvs[k++];
					fbWeight += docProb;
				}				
				fbWeight /= (double)fbDocVectors.size();
				
				fv.addTerm(term, fbWeight);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return fv;
	}
        
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "Path to input index");
        options.addOption("topics", true, "Path to topics file");
        options.addOption("numFbDocs", true, "Number of feedback docs");
        options.addOption("numFbTerms", true, "Number of feedback terms");
        options.addOption("lambda", true, "RM3 lambda");
        options.addOption("output", true, "Path to output topics file");
        options.addOption("qrels", true, "Path to Qrels");
        options.addOption("startTime", true, "Start time");
        options.addOption("endTime", true, "End time");
        options.addOption("interval", true, "Interval");
        options.addOption("output", true, "Output directory");

        
        return options;
    }

}
