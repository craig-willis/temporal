package edu.gslis.scorers.temporal;

import edu.gslis.queries.GQuery;

import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;

public class QNIDFScorer extends TemporalScorer {

    
    @Override
    public void init(SearchHits hits) {   

        FeatureVector nidf = new FeatureVector(null);
        for (String term: gQuery.getFeatureVector().getFeatures()) {
        	
        	double idf = Math.log(1 + index.docCount()/index.docFreq(term));
        	nidf.addTerm(term, idf);        	
        } 
        
        // Normalize term scores
        nidf.normalize();

        gQuery.setFeatureVector(nidf);
        
        System.out.println(nidf.toString(10));        
    }    
    
    public GQuery getQuery() {
    	return gQuery;
    }    
}
