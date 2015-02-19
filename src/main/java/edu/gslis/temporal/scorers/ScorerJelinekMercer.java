package edu.gslis.temporal.scorers;

import java.util.Iterator;

import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

/**
 * Standard dirichlet query likelihood scorer
 * 
 * @author mefron
 *
 */
public class ScorerJelinekMercer extends RerankingScorer {
	public String LAMBDA = "lambda";
	public double EPSILON = 1.0;
	
	public ScorerJelinekMercer() {
		setParameter(LAMBDA, 0.5);
	}
	public void setQuery(GQuery query) {
		this.gQuery = query;
	}
	
	/**
	 * retrieves the log-likelihood.  assumes the search hit is populated w term counts.
	 */
	public double score(SearchHit doc) {
		double logLikelihood = 0.0;
		double lambda = paramTable.get(LAMBDA);

		Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
		while(queryIterator.hasNext()) {
			String feature = queryIterator.next();
			double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
			double docLength = doc.getLength();
			
			// EPSILON is required here when working with TREC subcollections (i.e., LATimes) that do no
			// necessarily contain all query terms.
			double collectionProb = (EPSILON + collectionStats.termCount(feature)) / collectionStats.getTokCount();
			double docProb = docFreq/docLength;
			
    	        double pr = (1-lambda)*docProb + lambda*collectionProb;
	        double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
	        logLikelihood += queryWeight * Math.log(pr);

		}
		return logLikelihood;
	}
	
    @Override
    public void init(SearchHits hits) {
    }
}
