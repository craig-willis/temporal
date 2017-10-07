package edu.gslis.scorers.temporal;

import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;


public class QCEntScorer extends TemporalScorer {

    
    @Override
    public void init(SearchHits hits) {   
    	            	        
        FeatureVector ent = new FeatureVector(null);
        for (String term: gQuery.getFeatureVector().getFeatures()) {
        	double[] tsw = tsIndex.getDist(term);
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
        	System.out.println(gQuery.getTitle() + ", mu=" + paramTable.get("mu"));
        	System.out.println(ent.toString(10));       
        }
                
    }    
    
    public GQuery getQuery() {
    	return gQuery;
    }    
}
