package edu.gslis.scorers.temporal;

import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;


public class QCTKLIScorer extends TemporalScorer {

    
    @Override
    public void init(SearchHits hits) {   
    	        
        
        double[] background = tsIndex.getDist("_total_");
        
        FeatureVector tklin = new FeatureVector(null);
        for (String term: gQuery.getFeatureVector().getFeatures()) {
        	double[] tsw = tsIndex.getDist(term);
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
