package edu.gslis.main;

import java.io.FileWriter;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
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
        if (metric == null)
        	metric = "ap";
        
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
            if (verbose) {
	            System.out.println("==========================");
	            System.out.println("Query: " + query.getTitle());
	            System.out.println(query.toString());
            }
            
            // Run the initial query, which will be used for re-ranking
            SearchHits results = index.runQuery(query, MAX_RESULTS);
            double orig = metric(metric, results, qrels, query, MAX_RESULTS); 
            baseline += orig;
            
            if (verbose)
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
            rmVector.clip(numFbTerms);
            rmVector.normalize();
            //FeatureVector fv =
            //		FeatureVector.interpolate(query.getFeatureVector(), rmVector, 0.5);
            
            //System.out.println("RM");
        	qtmp.setFeatureVector(rmVector);
    		scorer.setQuery(qtmp);
            //System.out.println(rmVector.toString());
        	Iterator<SearchHit> it = htmp.iterator();
        	while (it.hasNext()) {
        		SearchHit hit = it.next();
        		hit.setScore(scorer.score(hit));
        	}
        	htmp.rank();
        	
        	double rmMetric = metric(metric, results, qrels, query, MAX_RESULTS);	
            
            if (verbose)
            	System.out.println("RM " + metric + ":" + rmMetric);
            

            double maxMetric = Math.max(orig, rmMetric);
            Set<String> terms = rmVector.getFeatures();
            FeatureVector maxFv = new FeatureVector(null);
            
            scorer.setParameter("mu", 2500);
            
            Set<Set<String>> sets = Sets.powerSet(terms);
            for (Set<String> set: sets) {	
            	if (set.size() <= 1 || set.size() > 5) 
            		continue;
            	            		
            	Set<Double> weights = Sets.newHashSet(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9);
                Set<Set<Double>> weightSets = Sets.powerSet(weights);
                for (Set<Double> weightSet: weightSets) {	
                	
                	if (weightSet.size() != set.size() || sum(weightSet) != 1)
                		continue;


                	
					Collection<List<Double>> permutations = Collections2.permutations(weightSet);
					
					// For all 
					for (List<Double> permutation: permutations) {
						FeatureVector workingFv = new FeatureVector(null);
						int j=0;
						for (String term: set) {							
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
		                		str += term + ": " + workingFv.getFeatureWeight(term) + ",";
		                	}
		                
		                	System.out.println(str + tmp);
		                }
		                
		                if (tmp > maxMetric) {
		                	maxMetric = tmp;
		                	maxFv = workingFv.deepCopy();
		                	//System.out.println(i + " " + StringUtils.join(set, " ") + ": " + tmpAp);               
		                }
					}
                }  
            }   
            
            maxFv.normalize();
            for (String term: terms) {
            	System.out.println(query.getTitle() + "," + term + "," + maxFv.getFeatureWeight(term));
            }
            
            System.err.println("Final query " + query.getTitle() + "," + maxMetric + "," + 
            		"," + StringUtils.join(maxFv.getFeatures(), " "));
            maxavg += maxMetric;
        }
        
        baseline /= queries.numQueries();
        maxavg /= queries.numQueries();
        System.err.println(metric + "," + baseline + "," + maxavg);
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
        return options;
    }

}
