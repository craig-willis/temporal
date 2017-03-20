package edu.gslis.scorers.temporal;

import edu.gslis.main.temporal.TermTimeSeries;
import edu.gslis.queries.GQuery;
import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * RM + TKLN
 * Implements Lavrenko's relevance model, but weighting
 * individual expansion terms by the KL divergence of their
 * temporal distribution compared to the background temporal
 * distribution (normalized)
 */
public class RM1TKLINScorer extends TemporalScorer {
    
    public static String FB_DOCS = "fbDocs";
    public static String FB_TERMS = "fbTerms";
            
    
	/**
	 * Estimate the relevance model from the top FB_DOCS
	 * results.
	 */
    @Override
    public void init(SearchHits hits) {   

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
        
        
        // Build the term time series
        TermTimeSeries ts = new TermTimeSeries(startTime, endTime, interval, 
        		rm.asFeatureVector().getFeatures());
        
        for (SearchHit hit: hits.hits()) {
    		long docTime = TemporalScorer.getTime(hit);
    		double score = hit.getScore();
    		ts.addDocument(docTime, score, hit.getFeatureVector());
        }
        
        // Get the query temporal model
        double[] background = ts.getBinDist();

        // score each term with respect to KL(w || q)
        FeatureVector tsfv = new FeatureVector(null);
        for (String term: rmVector.getFeatures()) {
        	double[] termts = ts.getTermDist(term);
            if (termts == null) {
            	System.err.println("Unexpected null termts for " + term);
            	continue;
            }
            
            double ll = 0;
            for (int i=0; i<termts.length; i++) {
            	if (termts[i] >0 && background[i] > 0)
            		ll += termts[i] * Math.log(termts[i]/background[i]);
            }
            double weight = (Math.exp(-(1/ll)) * rmVector.getFeatureWeight(term));         
            tsfv.addTerm(term, weight);
        } 
        
        // Normalize term scores
        tsfv.normalize();
        
        gQuery.setFeatureVector(tsfv);
        
        System.out.println(rmVector.toString(10));
        System.out.println(tsfv.toString(10));
        
    }    
    

    public GQuery getQuery() {
    	return gQuery;
    }
    
}
