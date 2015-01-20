package edu.gslis.temporal.scorers;

import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RKernelDensity;


public class PeetzScorer extends TemporalScorer 
{            
    RKernelDensity dist = null;
    double mean = 0;
    double sd = 0;
    
    public void init(SearchHits hits) {        
        // Estimate density for hits based on document timestamp
        
        double[] x = getTimes(hits);
        double[] w = getProportionalWeights(hits);
        dist = new RKernelDensity(x, w);    
        mean = dist.mean();
        sd = dist.sd();
        
    }    
       
    /**
     * Combine QL score and temporal score
     */
    public double score(SearchHit doc) {

        double score = -100;
        double density = dist.density(getTime(doc));
        if ((density + sd)> mean) {
            // we're in a burst
            score = super.score(doc);
        }
        return score;

    }
    
    public static double[] getScores(SearchHits hits) {
        double[] weights = new double[hits.size()];
        
        for (int i=0; i<hits.size(); i++) {
            weights[i] = hits.getHit(i).getScore();
        }
        
        return weights;        
    }
    public static double[] getProportionalWeights(SearchHits hits) {
        double[] weights = new double[hits.size()];
        
        double total = 0;
        for (int i=0; i<hits.size(); i++) 
            total += hits.getHit(i).getScore();
        for (int i=0; i<hits.size(); i++) {
            weights[i] = hits.getHit(i).getScore()/total;
        }
        
        return weights;
    }
    
    public static double[] getUniformWeights(SearchHits hits) {
        double[] weights = new double[hits.size()];
        
        for (int i=0; i<hits.size(); i++)
            weights[i] = 1;
        
        return weights;
    }
}
