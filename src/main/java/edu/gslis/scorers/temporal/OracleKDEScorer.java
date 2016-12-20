package edu.gslis.scorers.temporal;

import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RKernelDensity;


public class OracleKDEScorer extends TemporalScorer {
    
    
    static String ALPHA = "alpha";
            
    RKernelDensity dist = null;
    
    /**
     * Estimate temporal density of relevant documents
     */
    public void init(SearchHits hits) {    
        int k = hits.size();

        double[] x = getTimes(hits, k);
        double[] w = getProportionalWeights(hits, k);
        dist = new RKernelDensity(x, w);            
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
    
    public double[] getScores(SearchHits hits) {
        double[] weights = new double[hits.size()];
        
        for (int i=0; i<hits.size(); i++) {
            weights[i] = hits.getHit(i).getScore();
        }
        
        return weights;        
    }
    public double[] getProportionalWeights(SearchHits hits, int k) {
        double[] weights = new double[k];
        
        double total = 0;
        for (int i=0; i<k; i++) 
            total += hits.getHit(i).getScore();
        for (int i=0; i<k; i++) {
            weights[i] = hits.getHit(i).getScore()/total;
        }
        
        return weights;
    }

    public static double[] getUniformWeights(SearchHits hits) {
        return getUniformWeights(hits, hits.size());
    }
    
    public static double[] getUniformWeights(SearchHits hits, int k) {
        double[] weights = new double[k];
        
        for (int i=0; i<k; i++)
            weights[i] = 1;
        
        return weights;
    }
}
