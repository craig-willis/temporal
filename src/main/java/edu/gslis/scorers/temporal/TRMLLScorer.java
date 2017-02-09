package edu.gslis.scorers.temporal;

import edu.gslis.main.temporal.TermTimeSeries;
import edu.gslis.queries.GQuery;
import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Implements Lavrenko's relevance model, but weighting
 * individual expansion terms by the KL divergence of their
 * temporal distribution compared to the background temporal
 * distribution.
 */
public class TRMLLScorer extends TemporalScorer {
    
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
        
        double[] background = ts.getBinTotals();
        double sum = sum(background);
        for (int i=0; i<background.length; i++) 
        	background[i] = (background[i]/sum)+0.0000000001;

        FeatureVector tsfv = new FeatureVector(null);
        for (String term: rmVector.getFeatures()) {
        	double[] termts = ts.getTermFrequencies(term);
            if (termts == null) {
            	System.err.println("Unexpected null termts for " + term);
            	continue;
            }

            sum = sum(termts);
            for (int i=0; i<termts.length; i++)
            	termts[i] = (termts[i]/sum)+0.0000000001;
            
            double ll = 0;
            for (int i=0; i<termts.length; i++) {
            	ll += termts[i] * Math.log(termts[i]/background[i]);
            }
            double weight = Math.pow((1-Math.exp(-ll)), alpha) * Math.pow(rmVector.getFeatureWeight(term), (1-alpha));           
            tsfv.addTerm(term, weight);
        }
 
        tsfv.normalize();
//        FeatureVector fv =
//        		FeatureVector.interpolate(tsfv, rmVector, lambda);
//        gQuery.setFeatureVector(tsfv);

        gQuery.getFeatureVector().normalize();
        FeatureVector fv =
        		FeatureVector.interpolate(gQuery.getFeatureVector(), tsfv, lambda);
        gQuery.setFeatureVector(fv);
        

        System.out.println(rmVector.toString(10));
        System.out.println(tsfv.toString(10));
        System.out.println(fv.toString(10));
        
    }    
    
    public GQuery getQuery() {
    	return gQuery;
    }
    
    public double sum(double[] d) {
    	double sum = 0;
    	if (d == null)
    		return 0;
    	for (double x: d)
    		sum += x;
    	return sum;
    }
    
}
