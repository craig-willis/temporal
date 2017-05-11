package edu.gslis.scorers.temporal;

import java.util.Iterator;

import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

/**
 * Instead of JM or Dirichlet, try the log-linear aproach.
 */
public class LogLinearScorer extends RerankingScorer {
	public double EPSILON = 1.0;
	
	public void setQuery(GQuery query) {
		this.gQuery = query;
	}
	
	/**
	 * retrieves the log-likelihood.  assumes the search hit is populated w term counts.
	 */
	public double score(SearchHit doc) {
		double logLikelihood = 0.0;
		
		double alpha=paramTable.get("alpha");
		
		Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
		while(queryIterator.hasNext()) {
			String feature = queryIterator.next();
			double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
			double docLength = doc.getLength();
			double docProb = docFreq/docLength;
			double collectionProb = (EPSILON + collectionStats.termCount(feature)) / collectionStats.getTokCount();
			double pr = 0;
			if (docProb > 0) 
				pr = Math.pow(docProb, (1-alpha))*Math.pow(collectionProb, alpha);
			else 
				pr = collectionProb;
			double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
			logLikelihood += queryWeight * Math.log(pr);
		}
		return logLikelihood;
	}
    @Override
    public void init(SearchHits hits) {  
    	//System.out.println(gQuery.getText());
    }

}
