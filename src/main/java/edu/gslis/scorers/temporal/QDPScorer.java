package edu.gslis.scorers.temporal;

import edu.gslis.main.temporal.TermTimeSeries;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;


public class QDPScorer extends TemporalScorer {

    
    @Override
    public void init(SearchHits hits) {   

    	RUtil rutil = new RUtil();
    	        
        // Build the term time series
        TermTimeSeries ts = new TermTimeSeries(startTime, endTime, interval, 
        		gQuery.getFeatureVector().getFeatures());
        
        for (SearchHit hit: hits.hits()) {
    		long docTime = TemporalScorer.getTime(hit);
    		double score = hit.getScore();
    		ts.addDocument(docTime, score, hit.getFeatureVector());
        }
        ts.smooth();
        
        FeatureVector dpsn = new FeatureVector(null);
        for (String term: gQuery.getFeatureVector().getFeatures()) {
        	double[] tsw = ts.getTermDist(term);
            if (tsw == null) {
            	System.err.println("Unexpected null termts for " + term);
            	continue;
            }

        	double sum = sum(tsw);

            double dps = 0;
        	if (sum > 0) {
	        	try {        		
	        		dps = rutil.dps(tsw);
	        	} catch (Exception e) {
	        		e.printStackTrace();        		
	        	}
        	}
        	
        	dpsn.addTerm(term, dps);
        } 
        
        // Normalize term scores
        dpsn.normalize();
        
        gQuery.setFeatureVector(dpsn);
        
        System.out.println(dpsn.toString(10));
        
    }    
    

    public GQuery getQuery() {
    	return gQuery;
    }
    
    
}
