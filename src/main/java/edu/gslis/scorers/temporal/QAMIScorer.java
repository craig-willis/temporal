package edu.gslis.scorers.temporal;

import edu.gslis.main.temporal.TermTimeSeries;
import edu.gslis.qpp.predictors.TemporalPredictorSuite;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;


public class QAMIScorer extends TemporalScorer {

    
    @Override
    public void init(SearchHits hits) {   

    	TemporalPredictorSuite tpred = new TemporalPredictorSuite();
    	        
    	double lag = paramTable.get("lag");  
    	
    	double numDocs = 1000;
    	if (paramTable.get("k") != null) {
    		numDocs = paramTable.get("k");
    	}
                
        // Build the term time series
        TermTimeSeries ts = new TermTimeSeries(startTime, endTime, interval, 
        		gQuery.getFeatureVector().getFeatures());
        
        if (hits.size() < numDocs) 
        	numDocs = hits.size();
        
        for (int i=0; i< numDocs; i++) {
        	SearchHit hit = hits.getHit(i);
    		long docTime = TemporalScorer.getTime(hit);
    		double score = hit.getScore();
    		ts.addDocument(docTime, score, hit.getFeatureVector());
        }
        ts.smooth();
        
        FeatureVector acfn = new FeatureVector(null);
        for (String term: gQuery.getFeatureVector().getFeatures()) {
        	double[] tsw = ts.getTermDist(term);
            if (tsw == null) {
            	System.err.println("Unexpected null termts for " + term);
            	continue;
            }

        	double sum = sum(tsw);

            double qami = 0;
        	if (sum > 0) {
	        	try {        		
	        		qami = tpred.minfo(tsw)[(int)lag];
	        	} catch (Exception e) {
	        		e.printStackTrace();        		
	        	}
        	}        	
        	acfn.addTerm(term, qami);
        } 
        
        // Normalize term scores
        scale(acfn);

        gQuery.setFeatureVector(acfn);
        
        synchronized (this) {
        	System.out.println(gQuery.getTitle() 
        			+ " numDocs=" + numDocs + ", mu=" + paramTable.get("mu") 
        			+ ", lag=" + lag);
        	System.out.println(acfn.toString(10));       
        }
                
    }    
    
    public GQuery getQuery() {
    	return gQuery;
    }    
}
