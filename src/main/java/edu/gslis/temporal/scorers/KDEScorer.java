package edu.gslis.temporal.scorers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RKernelDensity;
import edu.gslis.temporal.util.RUtil;

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
    
    
    static String ALPHA = "alpha";
            
    RKernelDensity dist = null;
    
    /**
     * Estimate temporal density of hits
     */
    public void init(SearchHits hits) {        
        // Estimate density for hits based on document timestamp
        
        double[] x = getTimes(hits);
        double[] w = getProportionalWeights(hits);
        dist = new RKernelDensity(x, w);    
        

        double alpha = paramTable.get(ALPHA);

        if (alpha == -1) 
        {
            
            if (gQuery.getFeatureVector().getFeatureCount() > 1)
            {
                // Dynamic smoothing, calculate correlation between NPMI values for all query terms
                Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();            
                List<double[]> npmis = new ArrayList<double[]>();
                List<String> terms = new ArrayList<String>();
                while(queryIterator.hasNext()) 
                {
                    String feature = queryIterator.next();
                    try {
                        double[] npmi = tsIndex.getNpmi(feature);
                        npmis.add(npmi);
                        terms.add(feature);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                PearsonsCorrelation cor = new PearsonsCorrelation();
                double avgCor = 0;
                int k = 0;
                for (int i=0; i<npmis.size(); i++) {
                    for (int j=i; j<npmis.size(); j++) {
                        if (i==j) continue;

                        double c = cor.correlation(npmis.get(i), npmis.get(j));
                        avgCor += c;
                        k++;
                    }
                }
                avgCor /= k;
                
                if (avgCor > 0)     
                    paramTable.put(ALPHA, avgCor);
                else 
                    paramTable.put(ALPHA, 0D);                 
            }
            else
                paramTable.put(ALPHA, 0D);
            
            System.err.println(gQuery.getTitle() + " npmi   lambda=" + paramTable.get(ALPHA));
        } else if (alpha == -2) {
            // Use AC
            // Dynamically set lambda using AC
            if (gQuery.getFeatureVector().getFeatureCount() > 1)
            {
                try
                {
                    // Set lambda based on autocorrelation
                    Map<Integer, Map<String, Double>> tms 
                        = createTemporalModels(gQuery.getFeatureVector().getFeatures());
    
                    double[] scores = new double[tms.size()];
                    double z = 0;            
                    for (int bin: tms.keySet()) {
                        // Log-likelihood of query given temporal model
                        double ll = scoreTemporalModel(gQuery.getFeatureVector(), tms.get(bin));
                        scores[bin] = Math.exp(ll);
                        z += scores[bin];
                    }
            
                    // p(theta_i given Q)
                    for (int bin: tms.keySet()) {
                        scores[bin] = scores[bin]/z;
                    }
                    
                    // Calculate autocorrelation at lag 1
                    RUtil rutil = new RUtil();
                    double ac = rutil.acf(scores);
                    if (ac > 0)     
                        paramTable.put(ALPHA, ac);
                    else 
                        paramTable.put(ALPHA, 0D); 
                    rutil.close();
                    
                    System.out.println(gQuery.getTitle() + "," + ac);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            else
                paramTable.put(ALPHA, 0D);
            
            System.err.println(gQuery.getTitle() + " ac alpha=" + paramTable.get(ALPHA)); 
        }
        
        
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
