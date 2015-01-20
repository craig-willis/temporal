package edu.gslis.temporal.scorers;

import java.util.Iterator;

import weka.estimators.KernelEstimator;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RKernelDensity;
import edu.gslis.textrepresentation.FeatureVector;


/**
 * Smooth document model using combination of temporal
 * and collection models.
 * 
 * Uses kernel density estimation to estimate p(T | w) given 
 * the temporal language model for the term w. This is then normalized
 * to estimate p(w|T).
 * 
 * This scorer requires an existing TimeSeriesIndex
 * with a matching startTime, endTime and interval. This scorer
 * also requires pre-calculation of normalizing constants
 * 
 * This has three parameters:
 *      mu:         Standard Dirichlet parameter
 *      lambda:     Weight of p(w|T) v p(w|C) in smoothing
 */
public class TimeSmoothedScorerKD extends TemporalScorer 
{

    String MU = "mu";
    String LAMBDA = "lambda";
    
    public double score(SearchHit doc)
    {
        double logLikelihood = 0.0;
        Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
        
        // Document language model smoothed with a linear combination
        // of the temporal language model at bin(t) and the collection language model

        // temporal model        
        double epoch = (Double)doc.getMetadataValue(Indexer.FIELD_EPOCH);
        long docTime = (long)epoch;
        int t = (int)((docTime - startTime)/interval);
        
        // Usual Dirichlet parameter
        double mu = paramTable.get(MU);
        // Parameter controlling linear combination of temporal and collection language models.
        double lambda = paramTable.get(LAMBDA);

        try
        {
            
            // Now calculate the score for this document using 
            // a combination of the temporal and collection LM.
            while(queryIterator.hasNext()) 
            {
                String feature = queryIterator.next();
                
                // Get the series for this feature
                double[] hist = tsIndex.get(feature);
               
                //p(w | C): +1 is necessary when working with partial collections (i.e., latimes)
                double pwC = (1 + collectionStats.termCount(feature)) / collectionStats.getTokCount();
                                           
                double timePr = probabilityWeka(t, hist);
                
                double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
                double docLength = doc.getLength();
                double pr = (docFreq + mu*(lambda*timePr + (1-lambda)*pwC))/(docLength + mu);
                
                double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
                
                logLikelihood += queryWeight * Math.log(pr);
            }
        } catch (Exception e) {
            e.printStackTrace(); 
        }                        
           
        return logLikelihood;
    }
    
    
    public double probabilityWeka(double t, double[] hist) {
        
        KernelEstimator weka = new KernelEstimator(0.001);
        for (int bin=0; bin<hist.length; bin++) {
            double freq = hist[bin];
            weka.addValue(bin, freq);
        }           
        
        return weka.getProbability(t);
        
    }

    
    public double probabilityR(double t, double[] hist) {
        
        double x[]= new double[hist.length];
        for (int i=0; i<hist.length; i++)
            x[i] = i;
        
        RKernelDensity rkd = new RKernelDensity(x, hist);
        
        
        double sum = 0;
        for (int i=0; i<hist.length; i++)
            sum += rkd.density(i);
        
        double density = rkd.density(t);
        density /= sum;
        
        rkd.close();    
        
        return density;
    }


    
    public double kl(FeatureVector p, FeatureVector q) {
        double kl = 0;
        
        Iterator<String> it = p.iterator();
        while(it.hasNext()) {
            String feature = it.next();
            double pi = p.getFeatureWeight(feature)/p.getLength();
            double qi = q.getFeatureWeight(feature)/q.getLength();
            if (pi > 0 && qi > 0)
                kl += pi * Math.log(pi/qi);
        }
        return kl;
    }

    @Override
    public void init(SearchHits hits) {
        // TODO Auto-generated method stub
        
    }   
}
