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
public class QPrCACFScorer extends TemporalScorer {

    
    @Override
    public void init(SearchHits hits) {   

    	RUtil rutil = new RUtil();
    	        
    	double mean_acf = paramTable.get("mean");
    	double sd_acf = paramTable.get("sd");    
            	
        FeatureVector tsvf = new FeatureVector(null);
        FeatureVector acfn = new FeatureVector(null);
        for (String term: gQuery.getFeatureVector().getFeatures()) {
        	double[] tsw = tsIndex.get(term);
            if (tsw == null) {
            	System.err.println("Unexpected null termts for " + term);
            	continue;
            }
            double pr=0;
            double acf = 0;
        	if (sum(tsw) > 0) {
	        	try {     
	        		acf = rutil.acf(tsw, 2);
	        		pr = rutil.pnorm(acf, mean_acf, sd_acf);
	        	} catch (Exception e) {
	        		e.printStackTrace();        		
	        	}
        	}        	
        	
        	tsvf.addTerm(term, pr);
        	acfn.addTerm(term, acf);
        } 
        System.out.println(gQuery.getTitle() + " " + paramTable.get("mu"));
        System.out.println(acfn.toString(10));
        scale(acfn);    
        System.out.println(acfn.toString(10));
        
        System.out.println(tsvf.toString(10));
        tsvf.normalize();       
        System.out.println(tsvf.toString(10));
        
        gQuery.setFeatureVector(tsvf);
        
    }    
    
    public GQuery getQuery() {
    	return gQuery;
    }    
}
