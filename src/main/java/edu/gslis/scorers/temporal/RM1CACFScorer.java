	package edu.gslis.scorers.temporal;

import edu.gslis.main.temporal.TermTimeSeries;
import edu.gslis.queries.GQuery;
import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;


public class RM1CACFScorer extends TemporalScorer {
    
    public static String FB_DOCS = "fbDocs";
    public static String FB_TERMS = "fbTerms";
            
    
    @Override
    public void init(SearchHits hits) {   

    	RUtil rutil = new RUtil();
    	
    	int numFbDocs = 50;
    	int numFbTerms = 20;
    	
        if (paramTable.get(FB_DOCS) != null ) 
        	numFbDocs = paramTable.get(FB_DOCS).intValue();
        if (paramTable.get(FB_TERMS) != null ) 
        	numFbTerms = paramTable.get(FB_TERMS).intValue();
        
        if (hits.size() < numFbDocs) 
        	numFbDocs = hits.size();
        

        // Build the RM1 model
        SearchHits fbDocs = new SearchHits(hits.hits().subList(0, numFbDocs));
        FeedbackRelevanceModel rm = new FeedbackRelevanceModel();
        rm.setDocCount(numFbDocs);
        rm.setTermCount(100);
        rm.setIndex(index);
        rm.setStopper(null);
        rm.setRes(fbDocs);
        rm.build();            
      
        FeatureVector rmVector = rm.asFeatureVector();
        rmVector.clip(100);
        rmVector.normalize();
                
        FeatureVector acfn = new FeatureVector(null);
        for (String term: rmVector.getFeatures()) {
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
        
        // Normalize term scores
        scale(acfn);
        acfn.clip(numFbTerms);
        acfn.normalize();
        
        System.out.println(gQuery.getTitle() + ": " + gQuery.getText());
        System.out.println(rmVector.toString(numFbTerms));
        System.out.println(acfn.toString(numFbTerms));
    }    
    

    public GQuery getQuery() {
    	return gQuery;
    }
    
}
