package edu.gslis.scorers.temporal;

import edu.gslis.queries.GQuery;
import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;


/**
 * Collection cross-correlation 
 *
 */
public class RM1CCCFScorer extends TemporalScorer {
    
    public static String FB_DOCS = "fbDocs";
    public static String FB_TERMS = "fbTerms";

    
	/**
	 * Estimate the relevance model from the top FB_DOCS
	 * results.
	 */
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
        rm.setTermCount(numFbTerms);
        rm.setIndex(index);
        rm.setStopper(null);
        rm.setRes(fbDocs);
        rm.build();            
      
        FeatureVector rmVector = rm.asFeatureVector();
        rmVector.clip(numFbTerms);
        rmVector.normalize();
        
          
        // Get the query temporal model
        double[] background = tsIndex.get("_total_");

        // score each term with respect to KL(w || q)
        FeatureVector ccfn = new FeatureVector(null);
        for (String term: rmVector.getFeatures()) {
        	double[] termts = tsIndex.get(term);
            if (termts == null) {
            	System.err.println("Unexpected null termts for " + term);
            	continue;
            }

            double ccf = 0;
            if (sum(termts) > 0) {
            	try {
            		ccf = rutil.ccf(background, termts, 0);
            	} catch (Exception e) {
            		e.printStackTrace();
            	}
            }

            if (ccf < 0)
            	ccf = 0;
                        
            ccfn.addTerm(term, (1-ccf));
        } 
        
        // Normalize term scores
        ccfn.normalize();
        
        FeatureVector tsfv = new FeatureVector(null);
        for (String term: rmVector.getFeatures()) {
        	double w = ccfn.getFeatureWeight(term) * rmVector.getFeatureWeight(term);
        	tsfv.addTerm(term, w);
        }
        tsfv.normalize();
        
        gQuery.setFeatureVector(tsfv);
        
        System.out.println(gQuery.getTitle() + ": " + gQuery.getText());
        System.out.println(rmVector.toString(10));
        System.out.println(ccfn.toString(10));
        System.out.println(tsfv.toString(10));
                
    }    
    

    public GQuery getQuery() {
    	return gQuery;
    }
    
}
