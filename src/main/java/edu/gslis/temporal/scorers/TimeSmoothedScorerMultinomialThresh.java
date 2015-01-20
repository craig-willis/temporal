    package edu.gslis.temporal.scorers;

import java.util.Iterator;

import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;

/*
 * Model 2: Time smoothed scorer based on temporal language model of document.
 * In this case, temporal score is weighted by KL divergence of temporal
 * model from collection.
 */
public class TimeSmoothedScorerMultinomialThresh extends TemporalScorer 
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

        // Only score documents published after startTime
        
        if (docTime > startTime) {
            
            // Usual Dirichlet parameter
            double mu = paramTable.get(MU);
            
            // Parameter controlling smoothing of temporal language models.
            double lambda = paramTable.get(LAMBDA);
    
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
                                    
                    if (series == null)
                        continue;
                    
                    double timePr = 0;
                    if (total[t] > 0)
                        timePr = series[t]/total[t];
                    
                    //p(w | C): +1 is necessary when working with partial collections (i.e., latimes)
                    double pwC = (1 + collectionStats.termCount(feature)) / collectionStats.getTokCount();
    
                    double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
                    double docLength = doc.getLength();
                    
                    double smoothedTempPr = pwC;                 
                    if (kls[t] > (klstats.getMean() +  klstats.getStandardDeviation())) {
                        // Smooth temporal language model with collection language model                    
                        smoothedTempPr = (lambda*timePr + (1-lambda)*pwC);
                    }                                        
                                            
                    // Smooth document language model with temporal language model
                    double smoothedDocProb = (docFreq + mu*smoothedTempPr)/(docLength + mu);
                    
                    double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
                    
                    logLikelihood += queryWeight * Math.log(smoothedDocProb);
                    
                }
            } catch (Exception e) {
                e.printStackTrace(); 
            }                      
        }
        else
            logLikelihood = Double.NEGATIVE_INFINITY;
        return logLikelihood;
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
