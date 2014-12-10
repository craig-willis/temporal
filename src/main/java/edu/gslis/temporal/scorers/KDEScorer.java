package edu.gslis.temporal.scorers;

import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RKernelDensity;

/**
 * Implements Efron et al. (2014) temporal KDE model
 * 
 * Retrieve initial set of documents, score using QL
 * Apply temporal retrieval model to re-rank results
 * From the top k re-ranked documents, estimate feedback models
 * Re-run retrieval with feedback model
 * Re-rank results using temporal model
 * 
 * Temporal model:
 *  Retrieve top 1000 documents
 *  Estimate KDE based on timestamps
 *  Score QL
 *  Score based on KDE
 */
public class KDEScorer extends TemporalScorer {
    
    
    RKernelDensity dist = null;
    
    /**
     * Estimate temporal density of hits
     */
    public void init(SearchHits hits) {        
        // Estimate density for hits based on document timestamp
        
        double[] x = getTimes(hits);
        double[] w = getUniformWeights(hits);
        dist = new RKernelDensity(x, w);        
    }
    
       
    /**
     * Combine QL score and temporal score
     */
    public double score(SearchHit doc) {
        
        double score = super.score(doc);
        score += Math.log(dist.density(getTime(doc)));
        
        return score;
    }
    
    private double[] getUniformWeights(SearchHits hits) {
        double[] weights = new double[hits.size()];
        
        for (int i=0; i<hits.size(); i++)
            weights[i] = 1;
        
        return weights;
    }
}
