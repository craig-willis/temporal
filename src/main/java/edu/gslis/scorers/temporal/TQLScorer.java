package edu.gslis.scorers.temporal;

import edu.gslis.main.temporal.TermTimeSeries;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Query likelihood weighted by temporal KL
 */
public class TQLScorer extends TemporalScorer {
    
    public static String LAMBDA = "lambda";
            
    
	/**
	 * Estimate the temporal model from the initial results
	 * results.
	 */
    @Override
    public void init(SearchHits hits) {   

    	double lambda = 5.0;
    	
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
            double weight = (1-Math.exp(-ll));           
            tsfv.addTerm(term, weight);
        }
 
        tsfv.normalize();

        gQuery.getFeatureVector().normalize();
        FeatureVector fv =
        		FeatureVector.interpolate(gQuery.getFeatureVector(), tsfv, lambda);
        gQuery.setFeatureVector(fv);        

        System.out.println(tsfv.toString());
        System.out.println(fv.toString());
        
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
