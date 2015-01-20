    package edu.gslis.temporal.scorers;

import java.util.Iterator;

import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;

/*
 * Model 2  : Time smoothed scorer using weighted temporal model based on KL divergence
 */
public class TimeSmoothedScorerMultinomialOld extends TemporalScorer 
{

    String MU = "mu";
    String LAMBDA = "lambda";
    int WINSIZE = 3;
    
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

        // Only score documents published after startTime
        if (docTime > startTime) {
            
            // Usual Dirichlet parameter
            double mu = paramTable.get(MU);
            
            // Parameter controlling smoothing of temporal language models.
            double lambda = paramTable.get(LAMBDA);
    
           // System.out.println(doc.getDocno()  + "," + t);
            try
            {
                // Total number of events for each time = bin(t)
                // The TimeSeriesIndex contains 1 row per term and 1 column per bin based on interval.
                double[] total = tsIndex.get("_total_");
                
                // Now calculate the score for this document using 
                // a combination of the temporal and collection LM.
                while(queryIterator.hasNext()) 
                {
                    String feature = queryIterator.next();
                    
                    // Get the series for this feature
                    double[] series = tsIndex.get(feature);
                                    
                    //double timePr = movingAverage(series, total, t, WINSIZE);
                    if (series == null)
                        continue;
                    
                    double timePr = 0;
                    if (total[t] > 0)
                        timePr = series[t]/total[t];
                    
                    //p(w | C): +1 is necessary when working with partial collections (i.e., latimes)
                    double pwC = (1 + collectionStats.termCount(feature)) / collectionStats.getTokCount();
    
                    double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
                    double docLength = doc.getLength();
                    
                    // Smooth temporal language model with collection language model
                    double smoothedTempPr = lambda*timePr + (1-lambda)*pwC;
                                            
                    // Smooth document language model with temporal language model
                    double smoothedDocProb = (docFreq + mu*smoothedTempPr)/(docLength + mu);
                    
                    double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
                    
                    logLikelihood += queryWeight * Math.log(smoothedDocProb);
                }
            } catch (Exception e) {
                e.printStackTrace(); 
            }                      
        }
        return logLikelihood;
    }
    
    public double movingAverage(double[] series, double[] total, int t, int winSize) 
    {        
        double timePr = 0;
        
        double timeFreq = series[t];
        int n = 1;
        
        int size = series.length;
        if (t < size) {

            for (int i=0; i < winSize; i++) {
                if (t > i)
                    timeFreq += series[t - i];
                if (t < size - i)
                    timeFreq += series[t + i];
                n++;
            }
        }

        // Average freq at time t
        timeFreq = timeFreq/(double)n;
        
        if (series[t] > 0 && total[t] > 0)
            timePr = timeFreq / total[t];
        
        return timePr;
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
    public void close() {
        try {
            tsIndex.close();            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Override
    public void init(SearchHits hits) {
        // TODO Auto-generated method stub
        
    }
}