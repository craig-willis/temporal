package edu.gslis.scorers.temporal;

import edu.gslis.main.temporal.TermTimeSeries;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;


public class QDPScorer extends TemporalScorer {
    public static String LAMBDA = "lambda";

    @Override
    public void init(SearchHits hits) {   
        
       	double lambda = 1.0;
  
       	double mu = 0;
       	if (paramTable.get(MU) != null ) 
       		mu = paramTable.get(MU).doubleValue();
       	
        if (paramTable.get(LAMBDA) != null ) 
        	lambda = paramTable.get(LAMBDA).doubleValue();
        
        TermTimeSeries ts = new TermTimeSeries(startTime, endTime, interval, 
        		gQuery.getFeatureVector().getFeatures());
        
        for (SearchHit hit: hits.hits()) {
    		long docTime = TemporalScorer.getTime(hit);
    		double score = hit.getScore();
    		ts.addDocument(docTime, score, hit.getFeatureVector());
        }
        
        RUtil rutil = new RUtil();
        
        double sdp =0;
        double sidf=0;
        for (String term: gQuery.getFeatureVector().getFeatures()) {
        	double[] tsw = ts.getTermFrequencies(term);
        	if (sum(tsw) > 0) {
        		try {
        			sdp += rutil.dp(tsw);
        			sidf += Math.log(1 + index.docCount()/index.docFreq(term));
        		} catch (Exception e) {
        			e.printStackTrace();
        		}
        	}
        }  
        
        FeatureVector tsfv = new FeatureVector(null);
        for (String term: gQuery.getFeatureVector().getFeatures()) {
        	double[] termts = ts.getTermFrequencies(term);
        	try {
        		double dp = rutil.dp(termts)/sdp;
        		double idf = Math.log(1 + index.docCount()/index.docFreq(term))/sidf;
        		if (idf == Double.NaN || idf == Double.NEGATIVE_INFINITY) 
        			idf = 0;
        		
        		double weight = dp * idf;
                tsfv.addTerm(term, weight);
                
        	} catch (Exception e) {
        		e.printStackTrace();
        	}
        }

        gQuery.getFeatureVector().normalize();
        FeatureVector fv =
        		FeatureVector.interpolate(gQuery.getFeatureVector(), tsfv, lambda);
        
        synchronized(this) {
        	System.out.println(gQuery.getTitle() + " mu=," + mu + ", lambda=" + lambda);
        	System.out.println("Orig\n" + gQuery.getFeatureVector().toString(10));        

        	System.out.println("DP\n" + tsfv.toString(10)); 
        	System.out.println("Final\n" + fv.toString(10));  
        }
        
        gQuery.setFeatureVector(fv);
        rutil.close();
        
    }         
   
       
}
