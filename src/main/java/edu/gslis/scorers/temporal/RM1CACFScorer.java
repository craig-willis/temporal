	package edu.gslis.scorers.temporal;

import edu.gslis.queries.GQuery;
import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;


public class RM1CACFScorer extends TemporalScorer {
    
    
    @Override
    public void init(SearchHits hits) {   

    	RUtil rutil = new RUtil();
    	
    	int numFbDocs = 50;
    	int numFbTerms = 20;
    	int lag = 2;
    	double lambda = 0.5;
    	
        if (paramTable.get("fbDocs") != null ) 
        	numFbDocs = paramTable.get("fbDocs").intValue();
        if (paramTable.get("fbTerms") != null ) 
        	numFbTerms = paramTable.get("fbTerms").intValue();
        
        if (paramTable.get("lag") != null ) 
        	lag = paramTable.get("lag").intValue();

        if (paramTable.get("lambda") != null ) 
        	lambda = paramTable.get("lambda");
        
        if (hits.size() < numFbDocs) 
        	numFbDocs = hits.size();
        

        // Build the RM1 model
        SearchHits fbDocs = new SearchHits(hits.hits().subList(0, numFbDocs));
        FeedbackRelevanceModel rm = new FeedbackRelevanceModel();
        rm.setDocCount(numFbDocs);
        rm.setTermCount(0); // ignored
        rm.setIndex(index);
        rm.setStopper(null);
        rm.setRes(fbDocs);
        rm.build();            
      
        FeatureVector rm1fv = rm.asFeatureVector();
        rm1fv.clip(numFbTerms);
        rm1fv.normalize();
                
        double[] background = tsIndex.getDist("_total_");
        double bacf = 0;
        try {
        	bacf = rutil.acf(background, lag);
        } catch (Exception e) {
        	e.printStackTrace();
        }
        
        FeatureVector acfn = new FeatureVector(null);
        for (String term: rm1fv.getFeatures()) {
        	double[] tsw = tsIndex.getDist(term);
            if (tsw == null) {
            	System.err.println("Unexpected null termts for " + term);
            	continue;
            }
            
            double acf = 0;
        	if (sum(tsw) > 0) {
	        	try {        		
	        		acf = rutil.acf(tsw, lag);
	        	} catch (Exception e) {
	        		e.printStackTrace();        		
	        	}
        	}
        	
        	if (acf > Math.abs(bacf)) {
        		acfn.addTerm(term, Math.abs(acf));
        	}
        } 
        
        // Normalize term scores
        scale(acfn);

        FeatureVector rm1acf = FeatureVector.interpolate(acfn, rm1fv, lambda);
        
        gQuery.setFeatureVector(rm1acf);
        
        synchronized(this) {
        	System.out.println(gQuery.getTitle() 
        			+ " numFbDocs=" + numFbDocs + ", numFbTerms=" + numFbTerms 
        			+ ", lag=" + lag + ", lambda=" + lambda);
        	System.out.println(rm1fv.toString(10));    
        	System.out.println(acfn.toString(10));    
        	System.out.println(rm1acf.toString(10));        
        }
        
        rutil.close();
    }    
    

    public GQuery getQuery() {
    	return gQuery;
    }
    
}
