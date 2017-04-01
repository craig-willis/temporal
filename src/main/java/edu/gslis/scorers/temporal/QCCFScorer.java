package edu.gslis.scorers.temporal;

import edu.gslis.main.temporal.TermTimeSeries;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;



// Query-dependent Cross-Correlation
public class QCCFScorer extends TemporalScorer {

    
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
        
        double[] background = ts.getBinTotals();
        FeatureVector ccfn = new FeatureVector(null);
        for (String term: gQuery.getFeatureVector().getFeatures()) {
        	double[] tsw = ts.getTermFrequencies(term);
            if (tsw == null) {
            	System.err.println("Unexpected null termts for " + term);
            	continue;
            }


            double ccf = 0;
        	if (sum(tsw) > 0) {
	        	try {        		
	    	    	ccf = rutil.ccf(background, tsw, 0);
	        	} catch (Exception e) {
	        		e.printStackTrace();        		
	        	}
        	}        	
        	ccfn.addTerm(term, ccf);
        } 
        
        // Normalize term scores
        scale(ccfn);

        gQuery.setFeatureVector(ccfn);
        
        System.out.println(ccfn.toString(10));
        
    }    
    
    public GQuery getQuery() {
    	return gQuery;
    }    
}
