package edu.gslis.scorers.temporal;

import java.util.Iterator;

import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

/**
 * QL with JM smoothing
 *
 */
public class JelinekMercer extends RerankingScorer {
	public double EPSILON = 1.0;
	
	public void setQuery(GQuery query) {
		this.gQuery = query;
	}
	
	public double score(SearchHit doc) {
		double logLikelihood = 0.0;
		double lambda = paramTable.get("lambda");
		
		Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
		while(queryIterator.hasNext()) {
			String feature = queryIterator.next();
			double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
			double docLength = doc.getLength();
			double collectionProb = (EPSILON + collectionStats.termCount(feature)) / collectionStats.getTokCount();
			
			double pr = (1-lambda)*(docFreq/docLength) + lambda*collectionProb; 
			
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
