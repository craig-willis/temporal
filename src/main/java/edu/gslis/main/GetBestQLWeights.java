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
public class GetBestQLWeights 
{
	static int MAX_RESULTS=1000;
    public static void main(String[] args) throws Exception 
    {
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
        
        Qrels qrels =new Qrels(qrelsPath, false, 1);		

        GQueries queries = null;
        if (topicsPath.endsWith("indri")) 
            queries = new GQueriesIndriImpl();
        else
            queries = new GQueriesJsonImpl();
        queries.read(topicsPath);
        
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
        Iterator<GQuery> queryIt = queries.iterator();
        while (queryIt.hasNext()) {
            GQuery query = queryIt.next();
            System.out.println("==========================");
            System.out.println("Query: " + query.getTitle());
            System.out.println(query.toString());

            // Run the initial query, which will be used for re-ranking
            SearchHits results = index.runQuery(query, MAX_RESULTS);
            //double origAp = avgPrecision(results, qrels, query.getTitle());   
            double origNDCG = ndcg(MAX_RESULTS, query, results, qrels); 
            System.out.println("Initial NDCG:" + origNDCG);
            
            
            
            
            ScorerDirichlet scorer = new ScorerDirichlet();
            CollectionStats corpusStats = new IndexBackedCollectionStats();            
            corpusStats.setStatSource(indexPath);
            scorer.setCollectionStats(corpusStats);
    
            
            double maxNDCG = origNDCG;
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
	                
	                double tmpNDCG = ndcg(MAX_RESULTS, query, htmp, qrels); 
	                
	                String str = "";
	                for (String term: workingFv.getFeatures()) {
	                	str += term + ": " + workingFv.getFeatureWeight(term) + ",";
	                }
	                
	                System.out.println(str + tmpNDCG);
	                
	                if (tmpNDCG > maxNDCG) {
	                	maxNDCG = tmpNDCG;
	                	maxFv = workingFv.deepCopy();
	                	//System.out.println(i + " " + StringUtils.join(set, " ") + ": " + tmpAp);               
	                }
            	}
            }   
            
            maxFv.normalize();
            for (String term: terms) {
            	System.out.println(query.getTitle() + "," + term + "," + maxFv.getFeatureWeight(term));
            }
            System.out.println(query.getTitle() + ",MaxAP," + maxNDCG);
            System.out.println("Final query " + query.getTitle() + "," + maxNDCG + "," + 
            		"," + StringUtils.join(maxFv.getFeatures(), " "));
                        
        }
    }
        
    public static double sum(Set<Double> set) {
    	double sum = 0;
    	for (double d: set)
    		sum+= d;
    	return sum;
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
    
    
	
	/**
	 * Compute nDCG@k for a single query
	 * @param rankCutoff The k in nDCG@k
	 * @param query
	 * @param results
	 * @param qrels Relevance judgments
	 * @return
	 */
	public static double ndcg(int rankCutoff, GQuery query, SearchHits results, Qrels qrels) {
		return ndcg(rankCutoff, query.getTitle(), results, qrels);
	}

	/**
	 * Compute nDCG@k for a single query
	 * @param rankCutoff The k in nDCG@k
	 * @param query
	 * @param results
	 * @param qrels Relevance judgments
	 * @return
	 */
	public static double ndcg(int rankCutoff, String query, SearchHits results, Qrels qrels) {
		double dcg = dcg(rankCutoff, query, results, qrels);
		double idcg = idcg(rankCutoff, query, qrels);
		if (idcg == 0) {
			System.err.println("No relevant documents for query "+query+"?");
			return 0;
		}
		return dcg / idcg;
	}
	
	/**
	 * Compute DCG@k for a single query
	 * @param rankCutoff The k in DCG@k
	 * @param query
	 * @param results
	 * @param qrels Relevance judgments
	 * @return
	 */
	public static double dcg(int rankCutoff, GQuery query, SearchHits results, Qrels qrels) {
		return dcg(rankCutoff, query.getTitle(), results, qrels);
	}
	
	/**
	 * Compute DCG@k for a single query
	 * @param rankCutoff The k in DCG@k
	 * @param query
	 * @param results
	 * @param qrels Relevance judgments
	 * @return
	 */
	public static double dcg(int rankCutoff, String query, SearchHits results, Qrels qrels) {
		double dcg = 0.0;
		for (int i = 1; i <= rankCutoff; i++) {
			SearchHit hit = results.getHit(i-1);
			int rel = qrels.getRelLevel(query, hit.getDocno());
			dcg += dcgAtRank(i, rel);
		}
		return dcg;
	}
	
	/**
	 * Compute ideal DCG@k for a single query
	 * @param rankCutoff The k in DCG@k
	 * @param query
	 * @param results
	 * @param qrels Relevance judgments
	 * @return
	 */
	public static double idcg(int rankCutoff, GQuery query, Qrels qrels) {
		return idcg(rankCutoff, query.getTitle(), qrels);
	}
	
	/**
	 * Compute ideal DCG@k for a single query
	 * @param rankCutoff The k in DCG@k
	 * @param query
	 * @param results
	 * @param qrels Relevance judgments
	 * @return
	 */
	public static double idcg(int rankCutoff, String query, Qrels qrels) {
		SearchHits idealResults = new SearchHits();
		
		Set<String> relDocs = qrels.getRelDocs(query);

		if (relDocs == null) {
			return dcg(rankCutoff, query, idealResults, qrels);
		}

		for (String doc : relDocs) {
			int relLevel = qrels.getRelLevel(query, doc);

			SearchHit hit = new SearchHit();
			hit.setDocno(doc);
			hit.setScore(relLevel);
			idealResults.add(hit);
		}
		
		idealResults.rank();
		
		return dcg(rankCutoff, query, idealResults, qrels);
	}
	
	private static double dcgAtRank(int rank, int rel) {
		return (double)(Math.pow(2, rel) - 1)/(Math.log(rank+1));
	}

    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "Path to input index");
        options.addOption("topics", true, "Path to topics file");
        options.addOption("qrels", true, "Path to Qrels");
        return options;
    }

}
