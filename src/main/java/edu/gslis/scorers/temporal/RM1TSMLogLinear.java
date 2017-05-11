package edu.gslis.scorers.temporal;

import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Implements Lavrenko's relevance model
 */
public class RM1TSMLogLinear extends TSMLogLinear {
    
    public static String FB_DOCS = "fbDocs";
    public static String FB_TERMS = "fbTerms";
    public static String MU = "mu";
    public static String LAMBDA = "lambda";
          
    
	/**
	 * Estimate the RM1 model from the top FB_DOCS.
	 */
    @Override
    public void init(SearchHits hits) {   

    	int numFbDocs = 50;
    	int numFbTerms = 20;
    	double mu = 0;
    	
        if (paramTable.get(FB_DOCS) != null ) 
        	numFbDocs = paramTable.get(FB_DOCS).intValue();
        if (paramTable.get(FB_TERMS) != null ) 
        	numFbTerms = paramTable.get(FB_TERMS).intValue();
        
        if (paramTable.get(MU) != null ) 
        	mu = paramTable.get(MU).intValue();
        
        if (hits.size() < numFbDocs)
        	numFbDocs = hits.size();
        SearchHits fbDocs = new SearchHits(hits.hits().subList(0, numFbDocs));
        FeedbackRelevanceModel rm = new FeedbackRelevanceModel();
        rm.setDocCount(numFbDocs);
        rm.setTermCount(numFbTerms);
        rm.setIndex(index);
        rm.setStopper(null);
        rm.setRes(fbDocs);
        rm.build();            
      
        FeatureVector rmVector = rm.asFeatureVector();
        rmVector.clip(numFbTerms);
        rmVector.normalize();
        
        gQuery.setFeatureVector(rmVector);
        
        synchronized(this) {
        	System.out.println(gQuery.getTitle() + " mu=" + mu + ", numFbDocs=" + numFbDocs + ", numFbTerms=" + numFbTerms);
        	System.out.println(rmVector.toString(10));        
        }
    }    
}
