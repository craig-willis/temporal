package edu.gslis.scorers.temporal;

import java.util.Iterator;

import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;


public class TwoStageLogLinear extends RerankingScorer {
	public double EPSILON = 1.0;
	

	public void setQuery(GQuery query) {
		this.gQuery = query;
	}
	

	public double score(SearchHit doc) {
		
		double mu = paramTable.get("mu");
		double lambda = paramTable.get("lambda");
		double logLikelihood = 0.0;
		Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
		while(queryIterator.hasNext()) {
			String feature = queryIterator.next();
			double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
			double docLength = doc.getLength();
			double collectionPr = (EPSILON + collectionStats.termCount(feature)) / collectionStats.getTokCount();
			double docPr = (docFreq + mu*collectionPr) / (docLength + mu);
			
			double pr = Math.pow(docPr, (1-lambda))* Math.pow(collectionPr, lambda);
			
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
