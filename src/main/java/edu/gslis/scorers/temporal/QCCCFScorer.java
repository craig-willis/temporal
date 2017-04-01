package edu.gslis.scorers.temporal;

import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;

//Query-independent Cross-Correlation
public class QCCCFScorer extends TemporalScorer {

    
    @Override
    public void init(SearchHits hits) {   

    	RUtil rutil = new RUtil();
    	        
        
    	double[] background = tsIndex.get("_total_");
        FeatureVector ccfn = new FeatureVector(null);
        for (String term: gQuery.getFeatureVector().getFeatures()) {
        	double[] tsw = tsIndex.get(term);
            if (tsw == null) {
            	System.err.println("Unexpected null termts for " + term);
            	continue;
            }

        	double sum = sum(tsw);

            double ccf = 0;
        	if (sum > 0) {
	        	try {        		
	        		ccf = rutil.ccf(background, tsw, 0);
	        	} catch (Exception e) {
	        		e.printStackTrace();        		
	        	}
        	}        	
        	if (ccf < 0)
        		ccf = 0;
        	ccfn.addTerm(term, (1-ccf));
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
