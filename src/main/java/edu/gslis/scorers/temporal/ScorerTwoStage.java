package edu.gslis.scorers.temporal;

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
public class ScorerTwoStage extends RerankingScorer {
	public String MU = "mu";
	public String LAMBDA = "lambda";
	public double EPSILON = 1.0;
	
	public ScorerTwoStage() {
		setParameter(MU, 2500);
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
		double mu = paramTable.get(MU);
		double lambda = paramTable.get(LAMBDA);
		Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
		while(queryIterator.hasNext()) {
			String feature = queryIterator.next();
			double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
			double docLength = doc.getLength();
			double collectionProb = (EPSILON + collectionStats.termCount(feature)) / collectionStats.getTokCount();
			double pr = (1-lambda)*( (docFreq +  mu*collectionProb) / (docLength + mu)) + lambda*collectionProb;
			double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
			logLikelihood += queryWeight * Math.log(pr);
		}
		return logLikelihood;
	}
    @Override
    public void init(SearchHits hits) {
        // TODO Auto-generated method stub
        
    }
	

	


	
	


}
