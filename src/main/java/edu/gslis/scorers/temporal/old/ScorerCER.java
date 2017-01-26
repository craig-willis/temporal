package edu.gslis.scorers.temporal.old;

import java.util.Iterator;

import edu.gslis.queries.GQuery;
import edu.gslis.scorers.temporal.RerankingScorer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

/**
 * Kraaij's Cross-entropy reduction scorer
 *
 */
public class ScorerCER extends RerankingScorer {
	public String PARAMETER_NAME = "mu";
	public double EPSILON = 1.0;
	
	public ScorerCER() {   
		setParameter(PARAMETER_NAME, 2500);
	}
	public void setQuery(GQuery query) {
		this.gQuery = query;
	}
	

	public double score(SearchHit doc) 
	{
		double logLikelihood = 0.0;
		Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
		double mu = paramTable.get(PARAMETER_NAME);
		
		while(queryIterator.hasNext()) {
			String feature = queryIterator.next();
			
			double cp = (1 + collectionStats.termCount(feature))/collectionStats.getTokCount();

	        double df = doc.getFeatureVector().getFeatureWeight(feature);
            double dl = doc.getLength();
            double dp = (df + mu * cp) / (dl + mu);
                        
			double qf = gQuery.getFeatureVector().getFeatureWeight(feature);
			double ql = gQuery.getFeatureVector().getLength();
			double qp = qf/ql;
			
			logLikelihood += qp * Math.log(dp/cp);
//			logLikelihood += qp * Math.log(cp/dp);
		}
		return logLikelihood;
	}
	
    @Override
    public void init(SearchHits hits) {
        // TODO Auto-generated method stub
        
    }
	

	


	
	


}
