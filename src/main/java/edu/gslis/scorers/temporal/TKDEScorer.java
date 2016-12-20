package edu.gslis.scorers.temporal;

import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RKernelDensity;

/**
 * Variation of the KDE scorer that uses 
 * the average score in a time period
 */
public class TKDEScorer extends TemporalScorer {
    
    
    static String ALPHA = "alpha";
    static String K = "k"; // Controls the number of results used in the density estimation
            
    RKernelDensity dist = null;
    double[] weights = null;
    /**
     * Estimate temporal density of hits
     */
    public void init(SearchHits hits) {        
        
        int k = hits.size();
        if (paramTable.get(K) != null)
            k = paramTable.get(K).intValue();
            
        
        double[] x = getBins();
        weights = getBinWeights(hits, k);
        dist = new RKernelDensity(x, weights);            
    }
    
       
    /**
     * Combine QL score and temporal score
     */
    public double score(SearchHit doc) {
        
        double alpha = paramTable.get(ALPHA);
        
        double ll = super.score(doc);
        
        long docTime = getDocTime(doc);
        int bin = getBin(docTime);
        
//        double w = Math.log(weights[bin]);

        double kde = Math.log(dist.density(bin));
        
        return alpha*kde + (1-alpha)*ll;
        //return alpha*w + (1-alpha)*ll;
    }
    
    public double[] getBins() {
        int numBins = tsIndex.getNumBins();
        double[] bins = new double[numBins];
        for (int i=0; i<numBins; i++)
            bins[i] = i;
        return bins;
    }
    
    public double[] getBinWeights(SearchHits hits, int k) 
    {
        int numBins = tsIndex.getNumBins();
                
        double[] scores = new double[numBins];
        double[] numdocs = new double[numBins];
        for (int i=0; i<numBins; i++) {
            scores[i] = 0;
            numdocs[i] = 0;
        }
        
        // Get the average score for each bin
        for (int i=0; i<k; i++) {
            SearchHit hit = hits.getHit(i);

            long docTime = getDocTime(hit);
            int bin = getBin(docTime);
            if (bin >=0 && bin < tsIndex.getNumBins()) {
                scores[bin] += Math.exp(hit.getScore());
                numdocs[bin] ++;
            }                      
        }
        
        double total = 0;
        for (int i=0; i<numBins; i++) {
            if (numdocs[i] > 0)
                scores[i] /= numdocs[i];
            total += scores[i];
        }
        
        for (int i=0; i<numBins; i++)
            scores[i] /= total;
                
        scores = tsIndex.average(scores, 3);
        return scores;
    }
    
}
