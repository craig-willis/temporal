package edu.gslis.scorers.temporal;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import edu.gslis.main.temporal.TermTimeSeries;
import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Implements Lavrenko's relevance model, weighting terms
 * by the dominant power spectrum of their temporal distribution
 */
public class TRMDPSScorer extends TemporalScorer {
    
    public static String FB_DOCS = "fbDocs";
    public static String FB_TERMS = "fbTerms";
    public static String LAMBDA = "lambda";
    public static String ALPHA = "alpha";
                
	/**
	 * Estimate the relevance model from the top FB_DOCS
	 * results.
	 */
    @Override
    public void init(SearchHits hits) {   

    	int numFbDocs = 50;
    	int numFbTerms = 20;
    	double lambda = 1.0;
    	double alpha = 5.0;
    	
        if (paramTable.get(FB_DOCS) != null ) 
        	numFbDocs = paramTable.get(FB_DOCS).intValue();
        if (paramTable.get(FB_TERMS) != null ) 
        	numFbTerms = paramTable.get(FB_TERMS).intValue();
        if (paramTable.get(LAMBDA) != null ) 
        	lambda = paramTable.get(LAMBDA).doubleValue();
        if (paramTable.get(ALPHA) != null ) 
        	alpha = paramTable.get(ALPHA).doubleValue();        
        
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
        
        TermTimeSeries ts = new TermTimeSeries(startTime, endTime, interval, 
        		rm.asFeatureVector().getFeatures());
        
        for (SearchHit hit: hits.hits()) {
    		long docTime = TemporalScorer.getTime(hit);
    		double score = hit.getScore();
    		ts.addDocument(docTime, score, hit.getFeatureVector());
        }
        
        RUtil rutil = new RUtil();
        
        double sdps =0;
        for (String term: rmVector.getFeatures()) {
        	double[] tsw = ts.getTermFrequencies(term);
        	if (sum(tsw) > 0) {
        		try {
        			sdps += rutil.dps(tsw);
        		} catch (Exception e) {
        			e.printStackTrace();
        		}
        	}
        }  
        
        FeatureVector tsfv = new FeatureVector(null);
        for (String term: rmVector.getFeatures()) {
        	double[] termts = ts.getTermFrequencies(term);
        	try {
        		double dps = rutil.dps(termts)/sdps;
                		
            	double weight = Math.pow(dps, alpha) * Math.pow(rmVector.getFeatureWeight(term), (1-alpha));
                tsfv.addTerm(term, weight);
                
        	} catch (Exception e) {
        		e.printStackTrace();
        	}
        }
        tsfv.normalize();

        gQuery.getFeatureVector().normalize();
        FeatureVector fv =
        		FeatureVector.interpolate(gQuery.getFeatureVector(), tsfv, lambda);
        gQuery.setFeatureVector(fv);
        
        System.out.println(rmVector.toString(10));
        System.out.println(tsfv.toString(10));
        System.out.println(fv.toString(10));
        rutil.close();
        
    }         
    
    
}
