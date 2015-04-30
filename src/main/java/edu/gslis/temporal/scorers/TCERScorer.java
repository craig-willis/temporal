package edu.gslis.temporal.scorers;

import java.util.Iterator;

import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

/**
 * Temporal variation of Kraaij's
 * cross-entropy reduction scorer
 *
 */
public class TCERScorer extends TemporalScorer {
	public String PARAMETER_NAME = "mu";
	
	public TCERScorer() {
		setParameter(PARAMETER_NAME, 2500);
	}
	public void setQuery(GQuery query) {
		this.gQuery = query;
	}
	

	public double score(SearchHit doc) 
	{
		double logLikelihood = 0.0;
		
        // Get the bin for this document 
        long docTime = getDocTime(doc);
        int bin = getBin(docTime);

        // Ignore documents outside of the temporal bounds
        if (docTime < startTime || docTime > endTime)
            return Double.NEGATIVE_INFINITY;

		Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
		double mu = paramTable.get(PARAMETER_NAME);
		
        try {
    
    		while(queryIterator.hasNext()) {
    			String feature = queryIterator.next();
                double cp = (1 + collectionStats.termCount(feature))/collectionStats.getTokCount();
    			
                double tf = tsIndex.get(feature, bin);
                double tl = tsIndex.getLength(bin);                
                double tp = (0.9)*(tf/tl) + 0.1*cp;
    
    	        double df = doc.getFeatureVector().getFeatureWeight(feature);
                double dl = doc.getLength();       
                double dp = (df + mu * cp) / (dl + mu);
                            
    			double qf = gQuery.getFeatureVector().getFeatureWeight(feature);
    			double ql = gQuery.getFeatureVector().getLength();
    			double qp = qf/ql;
    			
//                double cer = qp * Math.log(dp/cp);
              	double tcer = qp * Math.log(dp*tp/cp);
    			logLikelihood += tcer;

    		}
        } catch (Exception e) {
            e.printStackTrace();
        }
        
		return logLikelihood;
	}
	
    @Override
    public void init(SearchHits hits) {
        // TODO Auto-generated method stub
        
    }
	

	


	
	


}
