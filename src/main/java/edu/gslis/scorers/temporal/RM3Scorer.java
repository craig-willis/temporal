package edu.gslis.scorers.temporal;

import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Implements Lavrenko's relevance model with interpolation with the original query (RM3)
 */
public class RM3Scorer extends ScorerDirichlet {
    
    public static String FB_DOCS = "fbDocs";
    public static String FB_TERMS = "fbTerms";
    public static String LAMBDA = "lambda";
            
    
	/**
	 * Estimate the relevance model from the top FB_DOCS
	 * results.
	 */
    @Override
    public void init(SearchHits hits) {   

    	int numFbDocs = 50;
    	int numFbTerms = 20;
    	double lambda = 1.0;
    	
        if (paramTable.get(FB_DOCS) != null ) 
        	numFbDocs = paramTable.get(FB_DOCS).intValue();
        if (paramTable.get(FB_TERMS) != null ) 
        	numFbTerms = paramTable.get(FB_TERMS).intValue();
        if (paramTable.get(LAMBDA) != null ) 
        	lambda = paramTable.get(LAMBDA).doubleValue();
        
        //System.out.println("numFbDocs=" + numFbDocs + ", numFbTerms=" + numFbTerms + ", lambda=" + lambda);
        
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
        
        gQuery.getFeatureVector().normalize();
        FeatureVector fv =
        		FeatureVector.interpolate(gQuery.getFeatureVector(), rmVector, lambda);
        gQuery.setFeatureVector(fv);
        
        //System.out.println(gQuery.toString());
    }         
    
}
