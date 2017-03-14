package edu.gslis.scorers.temporal;

import edu.gslis.main.temporal.TermTimeSeries;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;


public class QTKLIScorer extends TemporalScorer {

    
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
        
        double[] background = ts.getBinDist();
        
        FeatureVector tklin = new FeatureVector(null);
        for (String term: gQuery.getFeatureVector().getFeatures()) {
        	double[] tsw = ts.getTermDist(term);
            if (tsw == null) {
            	System.err.println("Unexpected null termts for " + term);
            	continue;
            }

        	double tkl = 0;
            for (int i=0; i<tsw.length; i++) {
            	if (tsw[i] >0 && background[i] > 0)
            		tkl += tsw[i] * Math.log(tsw[i]/background[i]);
            }
            
            tklin.addTerm(term, (Math.exp(-(1/tkl))));           
        } 
        
        // Normalize term scores
        normalize(tklin);
        
        gQuery.setFeatureVector(tklin);
        
        System.out.println(tklin.toString(10));
        
    }    
    

    public GQuery getQuery() {
    	return gQuery;
    }
}
