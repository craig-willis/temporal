package edu.gslis.scorers.temporal;

import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;


public class QCTKLCScorer extends TemporalScorer {

    
    @Override
    public void init(SearchHits hits) {   
    	        
        
        double[] background = tsIndex.getDist("_total_");
        
        FeatureVector tklcn = new FeatureVector(null);
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
            
            tklcn.addTerm(term, 1 - (Math.exp(-(tkl))));          
        } 
        
        // Normalize term scores
        normalize(tklcn);
        
        gQuery.setFeatureVector(tklcn);
        
        System.out.println(tklcn.toString(10));
        
    }    
    

    public GQuery getQuery() {
    	return gQuery;
    }
}
