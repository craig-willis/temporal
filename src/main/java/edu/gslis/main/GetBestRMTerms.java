package edu.gslis.main;

import java.io.FileWriter;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.Sets;

import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.docscoring.support.IndexBackedCollectionStats;
import edu.gslis.eval.Qrels;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.scorers.temporal.ScorerDirichlet;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Run initial query and build RM model
 * For each combination of query terms size 1-n built
 * from RM, re-score top 1000 documents. Find the
 * best query by AP.
 */
public class GetBestRMTerms 
{
	static int MAX_RESULTS=1000;
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( GetBestRMTerms.class.getCanonicalName(), options );
            return;
        }
        String indexPath = cl.getOptionValue("index");
        String topicsPath = cl.getOptionValue("topics");
        int numFbDocs =Integer.parseInt(cl.getOptionValue("numFbDocs", "50"));
        int numFbTerms =Integer.parseInt(cl.getOptionValue("numFbTerms", "20"));
        String outputPath = cl.getOptionValue("output");
        String qrelsPath = cl.getOptionValue("qrels");
        
        Qrels qrels =new Qrels(qrelsPath, false, 1);		

        GQueries queries = null;
        if (topicsPath.endsWith("indri")) 
            queries = new GQueriesIndriImpl();
        else
            queries = new GQueriesJsonImpl();
        queries.read(topicsPath);
        
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
        FileWriter outputWriter = new FileWriter(outputPath);
        Iterator<GQuery> queryIt = queries.iterator();
        while (queryIt.hasNext()) {
            GQuery query = queryIt.next();
            System.out.println("==========================");
            System.out.println("Query: " + query.getTitle());
            System.out.println(query.toString());

            // Run the initial query, which will be used for re-ranking
            SearchHits results = index.runQuery(query, MAX_RESULTS);
            double origAp = avgPrecision(results, qrels, query.getTitle());            
            System.out.println("Initial AP:" + origAp);
            
            // Build the relevance model
            SearchHits fbDocs = new SearchHits(results.hits());
            fbDocs.crop(numFbDocs);
            
            GQuery qtmp = new GQuery();
            qtmp.setTitle(query.getTitle());
            
            SearchHits htmp = new SearchHits(results.hits());
            ScorerDirichlet scorer = new ScorerDirichlet();
            CollectionStats corpusStats = new IndexBackedCollectionStats();            
            corpusStats.setStatSource(indexPath);
            scorer.setCollectionStats(corpusStats);


            FeedbackRelevanceModel rm = new FeedbackRelevanceModel();
            rm.setDocCount(numFbDocs);
            rm.setTermCount(numFbTerms);
            rm.setIndex(index);
            rm.setStopper(null);
            rm.setRes(fbDocs);
            rm.build();            
          
            FeatureVector rmVector = rm.asFeatureVector();
            rmVector.clip(numFbTerms);
            rmVector.normalize();
            //FeatureVector fv =
            //		FeatureVector.interpolate(query.getFeatureVector(), rmVector, 0.5);
            
            System.out.println("RM");
        	qtmp.setFeatureVector(rmVector);
    		scorer.setQuery(qtmp);
            System.out.println(rmVector.toString());
        	Iterator<SearchHit> it = htmp.iterator();
        	while (it.hasNext()) {
        		SearchHit hit = it.next();
        		hit.setScore(scorer.score(hit));
        	}
        	htmp.rank();
            double rmAp = avgPrecision(htmp, qrels, query.getTitle());
            System.out.println(query.getTitle() + " RM AP:" + rmAp);	
            
            double maxAp = Math.max(origAp, rmAp);
            Set<String> terms = rmVector.getFeatures();
            FeatureVector maxFv = new FeatureVector(null);
            
            scorer.setParameter("mu", 2500);
            
            Set<Set<String>> sets = Sets.powerSet(terms);
            //System.out.println("Set size: " + sets.size());
            int i=0;
            for (Set<String> set: sets) {	
            	if (set.size() <= 1 || set.size() > 5) 
            		continue;
            	            		
            	FeatureVector workingFv = new FeatureVector(null);
            	for (String term: set)
            		workingFv.addTerm(term);
            	
            	qtmp.setFeatureVector(workingFv);
        		scorer.setQuery(qtmp);
            	// Rescore the results
            	it = htmp.iterator();
            	while (it.hasNext()) {
            		SearchHit hit = it.next();
            		hit.setScore(scorer.score(hit));
            	}
            	htmp.rank();
            	
            	// Calculate ap
                double tmpAp = avgPrecision(htmp, qrels, query.getTitle());
                
                //if (tmpAp > Math.max(origAp, rmAp)) {
                //	System.out.println(i + " " + StringUtils.join(set, " ") + ": " + tmpAp);
                //}
                if (tmpAp > maxAp) {
                	maxAp = tmpAp;
                	maxFv = workingFv.deepCopy();
                	System.out.println(i + " " + StringUtils.join(set, " ") + ": " + tmpAp);               
                }
                i++;
            }   
            System.out.println("Final query " + query.getTitle() + "," + maxAp + "," + 
            		"," + StringUtils.join(maxFv.getFeatures(), " "));
            
            GQuery feedbackQuery = new GQuery();
            feedbackQuery.setTitle(query.getTitle());
            feedbackQuery.setText(query.getText());
            feedbackQuery.setFeatureVector(rmVector);     
            
        }
        outputWriter.close();
    }
        
    public static double avgPrecision(SearchHits results, Qrels qrels, String queryName) {
        
        double avgPrecision  = 0.0;
        
        Iterator<SearchHit> it = results.iterator();
        int k = 1;
        int numRelRet = 0;
        while(it.hasNext()) {
            SearchHit result = it.next();
            if(qrels.isRel(queryName, result.getDocno())) {
                numRelRet++;
                avgPrecision += (double)numRelRet/k;
            }
            k++;
        }
        avgPrecision /= qrels.numRel(queryName);
        return avgPrecision;
    }
	    
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "Path to input index");
        options.addOption("topics", true, "Path to topics file");
        options.addOption("numFbDocs", true, "Number of feedback docs");
        options.addOption("numFbTerms", true, "Number of feedback terms");
        options.addOption("output", true, "Path to output topics file");
        options.addOption("qrels", true, "Path to Qrels");
        return options;
    }

}
