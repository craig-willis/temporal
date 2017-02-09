package edu.gslis.main;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import edu.gslis.eval.Qrels;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

public class Metrics {
	   public static double metric(String metric, SearchHits results, Qrels qrels, GQuery query, int maxResults) {
	    	
	    	if (metric.equals("ndcg")) {    		
	    		return ndcg(maxResults, query, results, qrels);
	    	} else if (metric.equals("p_20")) {
	    		return p20(results.hits(), qrels, query.getTitle());    		
	    	} else {
	    		return avgPrecision(results.hits(), qrels, query.getTitle());
	    	}
	    }
	        
	    public static double sum(Set<Double> set) {
	    	double sum = 0;
	    	for (double d: set)
	    		sum+= d;
	    	return sum;
	    }
	    
	    
	    public static double p20(List<SearchHit> hits, Qrels qrels, String queryName) {
	    	
	        double k=1;
	        double numRelRet = 0;
	        for (SearchHit hit: hits) {
	        	if (k > 10)
	        		break;
	    		if(qrels.isRel(queryName, hit.getDocno())) {
	    			numRelRet++;
	    		}
	            k++;
	        }
	        return numRelRet/k;    	
	    }
	    
	    public static double avgPrecision(List<SearchHit> results, Qrels qrels, String queryName) {
	        
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
}
