package edu.gslis.scorers.temporal;

import edu.gslis.main.temporal.TermTimeSeries;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;


public class QEntScorer extends TemporalScorer {

    
    @Override
    public void init(SearchHits hits) {   
    	            	
    	double numDocs = 1000;
    	if (paramTable.get("k") != null) {
    		numDocs = paramTable.get("k");
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
        
        FeatureVector ent = new FeatureVector(null);
        for (String term: gQuery.getFeatureVector().getFeatures()) {
        	double[] tsw = ts.getTermDist(term);
            if (tsw == null) {
            	System.err.println("Unexpected null termts for " + term);
            	continue;
            }

        	double sum = sum(tsw);

            double entropy = 0;
        	if (sum > 0) {
				for (int i = 0; i < tsw.length; i++) {
					if (tsw[i] > 0)
						entropy += - tsw[i] * Math.log(tsw[i]);
				}	
        	}        	
        	ent.addTerm(term, 1/entropy);
        } 
        
        // Normalize term scores
        ent.normalize();

        gQuery.setFeatureVector(ent);
        
        synchronized (this) {
        	System.out.println(gQuery.getTitle() 
        			+ " numDocs=" + numDocs + ", mu=" + paramTable.get("mu"));
        	System.out.println(ent.toString(10));       
        }
                
    }    
    
    public GQuery getQuery() {
    	return gQuery;
    }    
}
