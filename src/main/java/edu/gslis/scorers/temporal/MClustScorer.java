package edu.gslis.scorers.temporal;

import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RKernelDensity;
import edu.gslis.temporal.util.RUtil;

/**
 * Model-based clustering of initial retrieval 
 * based on score distribution over time.
 * 
 * Retrieve an initial set of documents, score using QL
 * Construct model-based cluster using document scores and timestamps
 * 
 * Re-rank results based on clusters
 * From the top k re-ranked documents, estimate feedback model
 * Re-run retrieval with feedback model
 */
public class MClustScorer extends TemporalScorer {
    
    
    public static String ALPHA = "alpha";
    public static String K = "k";
            
    RKernelDensity dist = null;
    RUtil rutil = new RUtil();
    
    /**
     * Model-based clustering of initial results
     */
    @Override
    public void init(SearchHits hits) {        
        
        int k = 0; 
        if (paramTable.get(K) != null ) 
            k = paramTable.get(K).intValue();
        
        if (k <= 0)
            k = hits.size();

        double[] times = getTimes(hits, k);
        double[] scores = getScores(hits, k);
       // double[] classes = rutil.mclust(times, scores);
        //dist = new RKernelDensity(x, w);            
    }
    
       
    /**
     * Combine QL score and temporal score
     */
    public double score(SearchHit doc) {
        
        double alpha = paramTable.get(ALPHA);
        
        double ll = super.score(doc);
        double kde = Math.log(dist.density(getTime(doc)));
        
        return alpha*kde + (1-alpha)*ll;
    }
    
    public double[] getScores(SearchHits hits, int k) {
        double[] scores = new double[k];
        
        for (int i=0; i < k ; i++) {
        	scores[i] = hits.getHit(i).getScore();
        }
        
        return scores;        
    }

}
