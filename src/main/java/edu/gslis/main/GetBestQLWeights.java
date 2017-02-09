package edu.gslis.main;

import java.util.ArrayList;
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
public class GetBestQLWeights  extends Metrics
{
	static int MAX_RESULTS=1000;
    public static void main(String[] args) throws Exception 
    {
    	boolean verbose = false;
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( GetBestQLWeights.class.getCanonicalName(), options );
            return;
        }
        String indexPath = cl.getOptionValue("index");
        String topicsPath = cl.getOptionValue("topics");
        String qrelsPath = cl.getOptionValue("qrels");
        String metric = cl.getOptionValue("metric");
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
        System.out.println("query,term,weight");
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
            
            ScorerDirichlet scorer = new ScorerDirichlet();
            CollectionStats corpusStats = new IndexBackedCollectionStats();            
            corpusStats.setStatSource(indexPath);
            scorer.setCollectionStats(corpusStats);
    
            
            double max = orig;
            FeatureVector qv = query.getFeatureVector();
            List<String> terms = new ArrayList<String>();
            terms.addAll(qv.getFeatures());

            FeatureVector maxFv = qv.deepCopy();
            
            scorer.setParameter("mu", 2500);
            
            GQuery qtmp = new GQuery();
            qtmp.setTitle(query.getTitle());
            SearchHits htmp = new SearchHits(results.hits());
            Iterator<SearchHit> it = htmp.iterator();
            
            Set<Double> weights = Sets.newHashSet(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9);
            Set<Set<Double>> sets = Sets.powerSet(weights);
            for (Set<Double> set: sets) {	
            	if (set.size() != terms.size() || sum(set) != 1)
            		continue;
            	            		
            	Collection<List<Double>> permutations = Collections2.permutations(set);
            	
            	// For all 
            	for (List<Double> permutation: permutations) {
                	FeatureVector workingFv = new FeatureVector(null);
            		for (int j=0; j<qv.getLength(); j++) {
            			double w = permutation.get(j);
            			String term = terms.get(j);
            			workingFv.addTerm(term, w);
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
	            	
	            	// Calculate ap
	                //double tmpAp = avgPrecision(htmp, qrels, query.getTitle());
	                double tmp = metric(metric, results, qrels, query, MAX_RESULTS);
	                
	                if (verbose) {
	                	String str = "";
	                	for (String term: workingFv.getFeatures()) {
	                		str += term + ": " + workingFv.getFeatureWeight(term) + ",";
	                	}
	                
	                	System.out.println(str + tmp);
	                }
	                
	                if (tmp > max) {
	                	max = tmp;
	                	maxFv = workingFv.deepCopy();
	                	
	                	//System.out.println(i + " " + StringUtils.join(set, " ") + ": " + tmpAp);               
	                }
            	}
            }   
            
            maxFv.normalize();
            for (String term: terms) {
            	System.out.println(query.getTitle() + "," + term + "," + maxFv.getFeatureWeight(term));
            }

            if (verbose) {
	            System.out.println("Final query " + query.getTitle() + "," + max + "," + 
	            		"," + StringUtils.join(maxFv.getFeatures(), " "));
            }
            maxavg += max;
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
        options.addOption("qrels", true, "Path to Qrels");
        options.addOption("metric", true, "One of ap, ndcg, p_20");
        return options;
    }

}
