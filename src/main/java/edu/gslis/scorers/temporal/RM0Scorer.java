package edu.gslis.scorers.temporal;

import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Simple RM scorer (for analysis only) estimates the RM weights for the
 * query terms only with no expansion terms. This is used to 
 * determine how effective RM is at estimating ideal query term weights
 * for target collections.  
 */
public class RM0Scorer extends TemporalScorer {
    
    public static String FB_DOCS = "fbDocs";            
    
    @Override
    public void init(SearchHits hits) {   

    	int numFbDocs = 50;
    	
        if (paramTable.get(FB_DOCS) != null ) 
        	numFbDocs = paramTable.get(FB_DOCS).intValue();
        
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
      
        FeatureVector rmVector = rm.asFeatureVector();
                        
        // Get the weights for only the query terms
        FeatureVector brmfv = gQuery.getFeatureVector();
        for (String term: brmfv.getFeatures()) {
        	brmfv.setTerm(term, rmVector.getFeatureWeight(term));        	
        }
        brmfv.normalize();
        gQuery.setFeatureVector(brmfv);
        
        synchronized(this) {
        	System.out.println(gQuery.getTitle() + " numFbDocs=" + numFbDocs);
        	System.out.println(brmfv.toString(10));        
        }
    }    
}
