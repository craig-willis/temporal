package edu.gslis.scorers.temporal;

import java.util.Iterator;

import edu.gslis.main.temporal.TermTimeSeries;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;


/**
 * Query Likelihood scorer
 * Document term weights based on collection-level term ACF
 *
 */
public class DACFScorer extends TemporalScorer {

	public double EPSILON = 1.0;
	
    FeatureVector acfn = new FeatureVector(null);
    
    double lambda = 0.5; 
    @Override
    public void init(SearchHits hits) {   

       	RUtil rutil = new RUtil();
        
    	double lag = paramTable.get("lag");  
    	
    	double numDocs = 1000;
    	if (paramTable.get("k") != null) {
    		numDocs = paramTable.get("k");
    	}
    	
    	if (paramTable.get("lambda") != null) {
    		lambda = paramTable.get("lambda");
    	}
                
        // Build the term time series
        TermTimeSeries ts = new TermTimeSeries(startTime, endTime, interval, 
        		gQuery.getFeatureVector().getFeatures());
        
        if (hits.size() < numDocs) 
        	numDocs = hits.size();
        
        for (int i=0; i< numDocs; i++) {
        	SearchHit hit = hits.getHit(i);
    		long docTime = TemporalScorer.getTime(hit);
    		double score = hit.getScore();
    		ts.addDocument(docTime, score, hit.getFeatureVector());
        }
        ts.smooth();
        
        for (String term: gQuery.getFeatureVector().getFeatures()) {
        	double[] tsw = ts.getTermDist(term);
            if (tsw == null) {
            	System.err.println("Unexpected null termts for " + term);
            	continue;
            }

        	double sum = sum(tsw);

            double acf = 0;
        	if (sum > 0) {
	        	try {        		
	        		acf = rutil.acf(tsw, (int)lag);
	        	} catch (Exception e) {
	        		e.printStackTrace();        		
	        	}
        	}        	
        	acfn.addTerm(term, acf);
        } 
        
        // Normalize term scores
        scale(acfn);
        //System.out.println(acfn.toString(10));
        
        //gQuery.setFeatureVector(acfn);
        
    }  
    
    
	public double score(SearchHit doc) {
		double logLikelihood = 0.0;
		Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
		while(queryIterator.hasNext()) {
			String feature = queryIterator.next();
			double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
			double docLength = doc.getLength();
			double collectionProb = (EPSILON + collectionStats.termCount(feature)) / collectionStats.getTokCount();
			double pr = (docFreq + 
					paramTable.get(MU)*collectionProb) / 
					(docLength + paramTable.get(MU));
			double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
			//System.out.println(feature + ":" + pr + "," + acfn.getFeatureWeight(feature));
			if (acfn.getFeatureWeight(feature) > 0) 
				logLikelihood += queryWeight * Math.log((Math.pow(pr, lambda)*Math.pow(acfn.getFeatureWeight(feature), 1-lambda)));
		}
		return logLikelihood;
	}
    
    public GQuery getQuery() {
    	return gQuery;
    }    
}
