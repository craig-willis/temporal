package edu.gslis.scorers.temporal;

import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;


/**
 * Query Likelihood scorer
 * Query term weights based on collection-level term ACF
 *
 */
public class QCPACFScorer extends TemporalScorer {

    
    @Override
    public void init(SearchHits hits) {   

    	RUtil rutil = new RUtil();
    	                
    	double mu = 0.05;
    	double sd = 0.10;
    	if (this.collectionName.equals("ap")) {
    		mu = 0.05;
    		sd = 0.10;
    	} else if (this.collectionName.equals("latimes")) {
    		mu = 0.004;
    		sd = 0.07;
    	}
        FeatureVector acfn = new FeatureVector(null);
        for (String term: gQuery.getFeatureVector().getFeatures()) {
        	double[] tsw = tsIndex.get(term);
            if (tsw == null) {
            	System.err.println("Unexpected null termts for " + term);
            	continue;
            }

        	double sum = sum(tsw);

            double pacf = 0;
        	if (sum > 0) {
	        	try {        		
	        		pacf = rutil.pacf(tsw, 1);
	        	} catch (Exception e) {
	        		e.printStackTrace();        		
	        	}
        	}        	
        	
        	acfn.addTerm(term, pacf);
        } 
        
        // Normalize term scores
        acfn.normalize();

        gQuery.setFeatureVector(acfn);
        
        System.out.println(acfn.toString(10));
        
    }    
    
    public GQuery getQuery() {
    	return gQuery;
    }    
}
