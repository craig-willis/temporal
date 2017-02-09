package edu.gslis.scorers.temporal;

import edu.gslis.main.temporal.TermTimeSeries;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * QL + BP
 */
public class TBPScorer extends TemporalScorer {
    
    //public static String ALPHA = "alpha";
            
    
	/**
	 * Dirichlet score with terms weighted by temporal bin proportion
	 */
    @Override
    public void init(SearchHits hits) {   

    	//double alpha = 5.0;
    	
        //if (paramTable.get(ALPHA) != null ) 
        //	alpha = paramTable.get(ALPHA).doubleValue();        
        
                
        TermTimeSeries ts = new TermTimeSeries(startTime, endTime, interval, 
        		gQuery.getFeatureVector().getFeatures());
        
        for (SearchHit hit: hits.hits()) {
    		long docTime = TemporalScorer.getTime(hit);
    		double score = hit.getScore();
    		ts.addDocument(docTime, score, hit.getFeatureVector());
        }
               

        FeatureVector tsfv = new FeatureVector(null);
        for (String term: gQuery.getFeatureVector().getFeatures()) {
        	
        	double[] tsw = ts.getTermFrequencies(term);
            if (tsw == null) {
            	System.err.println("Unexpected null termts for " + term);
            	continue;
            }

            double bp = 0;
            for (int i=0; i<tsw.length; i++) {
            	if (tsw[i] > 0)
            		bp ++;
            }
            bp /= tsw.length;
            
// ap            tsfv.addTerm(term, 0.15 + 0.28*bp);
            tsfv.addTerm(term, 0.25 + 0.57*bp);
        }
 
        tsfv.normalize();

//        gQuery.getFeatureVector().normalize();
//        FeatureVector fv =
//        		FeatureVector.interpolate(gQuery.getFeatureVector(), tsfv, alpha);
        gQuery.setFeatureVector(tsfv);
        

        //System.out.println(tsfv.toString(10));
        System.out.println(tsfv.toString());
        
    }    
    

    public GQuery getQuery() {
    	return gQuery;
    }
    
    
}
