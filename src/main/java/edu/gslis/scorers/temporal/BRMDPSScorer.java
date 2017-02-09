package edu.gslis.scorers.temporal;

import edu.gslis.main.temporal.TermTimeSeries;
import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Simple RM scorer for analysis only estimates the RM weights for the
 * query terms only (no expansion terms) + temporal info via DP
 */
public class BRMDPSScorer extends TemporalScorer {
    
    public static String FB_DOCS = "fbDocs";
    public static String LAMBDA = "lambda";
            
    
    @Override
    public void init(SearchHits hits) {   

    	int numFbDocs = 50;
       	double lambda = 1.0;
        
        if (paramTable.get(LAMBDA) != null ) 
        	lambda = paramTable.get(LAMBDA).doubleValue();
        if (paramTable.get(FB_DOCS) != null ) 
        	numFbDocs = paramTable.get(FB_DOCS).intValue();
        
        if (hits.size() < numFbDocs)
        	numFbDocs = hits.size();
        SearchHits fbDocs = new SearchHits(hits.hits().subList(0, numFbDocs));
        FeedbackRelevanceModel rm = new FeedbackRelevanceModel();
        rm.setDocCount(numFbDocs);
        rm.setTermCount(0); // not used
        rm.setIndex(index);
        rm.setStopper(null);
        rm.setRes(fbDocs);
        rm.build();            
      
        FeatureVector rmVector = rm.asFeatureVector();
                        
        RUtil rutil = new RUtil();
                
        TermTimeSeries ts = new TermTimeSeries(startTime, endTime, interval, 
        		gQuery.getFeatureVector().getFeatures());
        
        for (SearchHit hit: hits.hits()) {
    		long docTime = TemporalScorer.getTime(hit);
    		double score = hit.getScore();
    		ts.addDocument(docTime, score, hit.getFeatureVector());
        }
        
        double sdps =0;
        for (String term: gQuery.getFeatureVector().getFeatures()) {
        	double[] tsw = ts.getTermFrequencies(term);
        	if (sum(tsw) > 0) {
        		try {
        			sdps += rutil.dps(tsw);
        			//sdp += rutil.dp(tsw);
        		} catch (Exception e) {
        			e.printStackTrace();
        		}
        	}
        }  
        
        FeatureVector dpsfv = new FeatureVector(null);
        for (String term: gQuery.getFeatureVector().getFeatures()) {
        	double[] termts = ts.getTermFrequencies(term);
        	try {
        		double dps = rutil.dps(termts)/sdps;
        		//double dp = rutil.dp(termts)/sdp;
        	
        		
        		double weight = dps;
        		dpsfv.addTerm(term, weight);
                
        	} catch (Exception e) {
        		e.printStackTrace();
        	}
        }
        dpsfv.normalize();
        rutil.close();
        
        FeatureVector rmfv = gQuery.getFeatureVector();
        for (String term: rmfv.getFeatures()) {
        	rmfv.setTerm(term, rmVector.getFeatureWeight(term));        	
        }
        rmfv.normalize();
        
        FeatureVector fv =
        		FeatureVector.interpolate(rmfv, dpsfv, lambda);
        
        gQuery.setFeatureVector(fv);
        
        synchronized(this) {
        	System.out.println(gQuery.getTitle() + " numFbDocs=" + numFbDocs + ", lambda=" + lambda);
	        System.out.println("RM\n" + rmfv.toString(10));
	        System.out.println("DPS\n" + dpsfv.toString(10));
	        System.out.println("Interpolated\n" + fv.toString(10));
        }

    }         
    
}
