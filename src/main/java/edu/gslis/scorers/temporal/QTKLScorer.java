package edu.gslis.scorers.temporal;

import edu.gslis.main.temporal.TermTimeSeries;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Implements Lavrenko's relevance model, weighting terms
 * by the dominant power spectrum of their temporal distribution
 */
public class QTKLScorer extends TemporalScorer {
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
        
        double[] background = ts.getBinTotals();
        double sum = sum(background);
        for (int i=0; i<background.length; i++) 
        	background[i] = (background[i]/sum)+0.0000000001;
        
        FeatureVector tsfv = new FeatureVector(null);
        
        for (String term: gQuery.getFeatureVector().getFeatures()) {
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
            tsfv.addTerm(term, ll);
        }  
        tsfv.normalize();
        
        gQuery.getFeatureVector().normalize();
        FeatureVector fv =
        		FeatureVector.interpolate(gQuery.getFeatureVector(), tsfv, lambda);
        
        synchronized(this) {
        	System.out.println(gQuery.getTitle() + " mu=," + mu + ", lambda=" + lambda);
        	System.out.println("Orig\n" + gQuery.getFeatureVector().toString(10));
        	System.out.println("TKL\n" + tsfv.toString(10)); 
        	System.out.println("Final\n" + fv.toString(10));  
        }
        
        gQuery.setFeatureVector(fv);
        
    }         
   
       
}
