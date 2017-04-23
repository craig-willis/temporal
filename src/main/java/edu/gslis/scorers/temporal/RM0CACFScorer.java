package edu.gslis.scorers.temporal;

import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Simple RM scorer (for analysis only) estimates the RM weights for the
 * query terms only with no expansion terms. This is used to 
 * determine how effective RM is at estimating ideal query term weights
 * for target collections.  
 */
public class RM0CACFScorer extends TemporalScorer {
    
    public static String FB_DOCS = "fbDocs";            
    
    @Override
    public void init(SearchHits hits) {   

    	RUtil rutil = new RUtil();
    	
    	int numFbDocs = 50;
    	double lambda = 0.5;
    	
        if (paramTable.get(FB_DOCS) != null ) 
        	numFbDocs = paramTable.get(FB_DOCS).intValue();
        if (paramTable.get("lambda") != null ) 
        	lambda = paramTable.get("lambda").doubleValue();
        
        if (hits.size() < numFbDocs)
        	numFbDocs = hits.size();
        
        SearchHits fbDocs = new SearchHits(hits.hits().subList(0, numFbDocs));
        FeedbackRelevanceModel rm = new FeedbackRelevanceModel();
        rm.setDocCount(numFbDocs);
        rm.setTermCount(0); // ignored
        rm.setIndex(index);
        rm.setStopper(null);
        rm.setRes(fbDocs);
        rm.build();            
      
        FeatureVector rm0 = new FeatureVector(null);
        for (String term: gQuery.getFeatureVector().getFeatures()) {
        	rm0.addTerm(term, rm.asFeatureVector().getFeatureWeight(term));
        }
        rm0.normalize();
        
        // Get the weights for only the query terms
        FeatureVector acfn = new FeatureVector(null);
        for (String term: gQuery.getFeatureVector().getFeatures()) {
        	double[] tsw = tsIndex.get(term);
            if (tsw == null) {
            	System.err.println("Unexpected null termts for " + term);
            	continue;
            }

        	double sum = sum(tsw);
            
            double acf = 0;
        	if (sum > 0) {
	        	try {        		
	        		acf = rutil.acf(tsw, 2);
	        	} catch (Exception e) {
	        		e.printStackTrace();        		
	        	}
        	}
        	
        	acfn.addTerm(term, acf);
        }
        scale(acfn);
        
        /*
        FeatureVector rm0acf = new FeatureVector(null);
        for (String term: gQuery.getFeatureVector().getFeatures()) {        	 
        	double w = acfn.getFeatureWeight(term)*rm0.getFeatureWeight(term);
        	rm0acf.addTerm(term, w);        	
        }
        rm0acf.normalize();
        */
        FeatureVector rm0acf = FeatureVector.interpolate(acfn, rm0, lambda);
        
        gQuery.setFeatureVector(rm0acf);
        
        synchronized(this) {
        	System.out.println(gQuery.getTitle() + " numFbDocs=" + numFbDocs);
        	System.out.println(rm0.toString(10));    
        	System.out.println(acfn.toString(10));    
        	System.out.println(rm0acf.toString(10));        
        }
    }       
}
