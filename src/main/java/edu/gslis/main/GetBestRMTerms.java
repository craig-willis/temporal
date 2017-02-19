package edu.gslis.main;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.Collections2;
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
public class GetBestRMTerms extends Metrics
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
        String qrelsPath = cl.getOptionValue("qrels");
        String metric = cl.getOptionValue("metric");
        boolean verbose = cl.hasOption("verbose");
        String outputFile = cl.getOptionValue("output");
        if (metric == null)
        	metric = "ap";
        
        FileWriter writer = new FileWriter(outputFile);
        
        Qrels qrels =new Qrels(qrelsPath, false, 1);		

        GQueries queries = null;
        if (topicsPath.endsWith("indri")) 
            queries = new GQueriesIndriImpl();
        else
            queries = new GQueriesJsonImpl();
        queries.read(topicsPath);
        
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
        Iterator<GQuery> queryIt = queries.iterator();
        double baseline = 0;
        double maxavg = 0;
        while (queryIt.hasNext()) {
            GQuery query = queryIt.next();
	        System.out.println("==========================");
            System.out.println("Query: " + query.getTitle());
            System.out.println(query.toString());
            
            // Run the initial query, which will be used for re-ranking
            SearchHits results = index.runQuery(query, MAX_RESULTS);
            double orig = metric(metric, results, qrels, query, MAX_RESULTS); 
            baseline += orig;
            
            System.out.println("Initial " + metric + ":" + orig);
            
            
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
            Set<String> features = new HashSet<String>();
            features.addAll(rmVector.getFeatures());
            for (String feature: features) {
				if (feature.matches("^[0-9]*$")) {
					rmVector.removeTerm(feature);
				}
            }
          
            rmVector.clip(numFbTerms);
            rmVector.normalize();


        	qtmp.setFeatureVector(rmVector);
    		scorer.setQuery(qtmp);

        	Iterator<SearchHit> it = htmp.iterator();
        	while (it.hasNext()) {
        		SearchHit hit = it.next();
        		hit.setScore(scorer.score(hit));
        	}
        	htmp.rank();
        	
        	double rmMetric = metric(metric, results, qrels, query, MAX_RESULTS);	
        	System.out.println(rmVector.toString());
        	
            System.out.println("RM " + metric + ":" + rmMetric);
            

            double maxMetric = Math.max(orig, rmMetric);
            Set<String> terms = rmVector.getFeatures();
            FeatureVector maxFv = new FeatureVector(null);
            
            scorer.setParameter("mu", 2500);
                       	            		        	
            Set<List<Double>> weightSets = getWeights(numFbTerms);
            
            
			//System.err.println("Weights: " + weightSets.size());
			int i=0;
            for (List<Double> weightSet: weightSets) {	
            	
				Collection<List<Double>> permutations = Collections2.permutations(weightSet);

				// permutations contain duplicates, lets get the unique lists
				Set<List<Double>> psets = new HashSet<List<Double>>();
				psets.addAll(permutations);
				
				//System.err.println(i + "/" + weightSet.size() + " (" + psets.size() + ")");
				for (List<Double> permutation: psets) {
					FeatureVector workingFv = new FeatureVector(null);
					int j=0;
					for (String term: rmVector.getFeatures()) {							
						double w = permutation.get(j);
						workingFv.addTerm(term, w);
						j++;
					}
	            	
	            	qtmp.setFeatureVector(workingFv);
	        		scorer.setQuery(qtmp);
	            	// Rescore the results
	            	it = htmp.iterator();
	            	while (it.hasNext()) {
	            		SearchHit hit = it.next();
	            		hit.setScore(scorer.score(hit));
	            	}
	            	htmp.rank();
	            	
	            	
	                double tmp = metric(metric, results, qrels, query, MAX_RESULTS);
	                
	                if (verbose) {
	                	String str = "";
	                	for (String term: workingFv.getFeatures()) {
                			str += term + ":" + workingFv.getFeatureWeight(term) + ", ";
	                	}		                
	                	System.out.println(str + tmp);
	                }
	                
	                if (tmp > maxMetric) {
	                	maxMetric = tmp;
	                	maxFv = workingFv.deepCopy();
	                	
	                	String str = "";
	                	for (String term: workingFv.getFeatures()) {
	                		str += term + ": " + workingFv.getFeatureWeight(term) + ",";
	                	}
	                    System.out.println("Max " + query.getTitle() + "," + maxMetric + "," +  str);
	                }
				}
				i++;
            }  
        
            maxFv.normalize();
            for (String term: terms) {
            	writer.write(query.getTitle() + "," + term + "," + maxFv.getFeatureWeight(term) + "\n");
            }
            
        	String str = "";
        	for (String term: maxFv.getFeatures()) {
        		str += term + ": " + maxFv.getFeatureWeight(term) + ",";
        	}
            System.out.println("Final query " + query.getTitle() + "," + maxMetric + "," + 
            		"," + str);
            maxavg += maxMetric;
        }
        writer.close();
        
        baseline /= queries.numQueries();
        maxavg /= queries.numQueries();
        System.out.println(metric + "," + baseline + "," + maxavg);
    }
    

 	public static Set<List<Double>> getWeights(int size) throws Exception {
		List<String> lines = FileUtils.readLines(new File("weights.txt"));
		Set<List<Double>> sets = new HashSet<List<Double>>();
		for (String line: lines) {
			String[] fields = line.split(",");
			List<Double> list = new ArrayList<Double>();
			for (String field: fields) {
				double d = Double.parseDouble(field);
				list.add(d);
			}
			Collections.sort(list);
			
			int diff = size - list.size();
			for (int i=0; i<diff; i++) {
				list.add(0.0);
			}
			
			sets.add(list);
		}
		
		return sets;
	}
 	
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "Path to input index");
        options.addOption("topics", true, "Path to topics file");
        options.addOption("numFbDocs", true, "Number of feedback docs");
        options.addOption("numFbTerms", true, "Number of feedback terms");
        options.addOption("metric", true, "Metric to optimize for");
        options.addOption("qrels", true, "Path to Qrels");
        options.addOption("verbose", false, "Verbose output");
        options.addOption("output", true, "Output file");
        return options;
    }

}
