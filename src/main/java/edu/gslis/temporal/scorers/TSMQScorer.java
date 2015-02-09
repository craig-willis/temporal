package edu.gslis.temporal.scorers;

import java.util.Iterator;

import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

/**
 * TSM-Q:  
 * Smooth documents using temporal model based on KL(Q || TM)
 * 
 */
public class TSMQScorer extends TemporalScorer 
{

    String MU = "mu";
    String LAMBDA = "lambda";
    String SMOOTH = "smooth";

    
    
    public double score(SearchHit doc)
    {
        double logLikelihood = 0.0;
        Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
                
        // Dirichlet parameter controlling amount of smoothing using temporal model
        double mu = paramTable.get(MU);
        // Parameter controlling linear combination of smoothed document and collection language models.
        double lambda = paramTable.get(LAMBDA);
        double smooth = paramTable.get(SMOOTH);

        
        // Get the bin for this document 
        long docTime = getDocTime(doc);
        int t = getBin(docTime);

        // Ignore documents outside of the temporal bounds
        if (docTime < startTime)
            return Double.NEGATIVE_INFINITY;

        // Approximate query generation likelihood
        int numBins = tsIndex.getNumBins();
        double max = Double.NEGATIVE_INFINITY;
        int bestBin = t;
        for (int bin = 0; bin < numBins; bin++) {
            // Log-likelihood of query given temporal model
            double ll = scoreTemporalModel(gQuery.getFeatureVector(), bin);
                
            if (ll > max)  {
                max = ll;
                bestBin = bin;
            }
        }

        
        try
        {
            
            // Now calculate the score for this document using 
            // a combination of the temporal and collection LM.
            queryIterator = gQuery.getFeatureVector().iterator();
            while(queryIterator.hasNext()) 
            {
                String feature = queryIterator.next();
                
                double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
                double docLength = doc.getLength();

                //p(w | C): +1 is necessary when working with partial collections (i.e., latimes)
                double collectionProb = (1 + collectionStats.termCount(feature)) 
                        / collectionStats.getTokCount();

                // Temporal model
                double tfreq = tsIndex.get(feature, bestBin);
                double tlen = tsIndex.getLength(bestBin);
                
                double temporalPr = tfreq / tlen; 
                
                double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);

                                
                if (smooth == 1) {
                    // 2-stage                
                    double smoothedTopicProb = 
                            (docFreq + mu*temporalPr) / 
                            (docLength + mu);
                    
                    double smoothedDocProb = 
                            lambda*smoothedTopicProb + (1-lambda)*collectionProb;
                    
                    logLikelihood += queryWeight * Math.log(smoothedDocProb);                        
                }
                else if (smooth == 2) { 
                    // Wei & Croft
                    double smoothedTopicProb = 
                            (docFreq + mu*collectionProb) / 
                            (docLength + mu);
                    
                    double smoothedDocProb = 
                            lambda*smoothedTopicProb + (1-lambda)*temporalPr;
                    
                    logLikelihood += queryWeight * Math.log(smoothedDocProb);                        

                }
                else if (smooth == 3) { 
                    // J-M Smoothed dirichlet

                    double smoothedTempProb = 
                            lambda*temporalPr + (1-lambda)*collectionProb;

                    double smoothedDocProb = 
                            (docFreq + mu*smoothedTempProb) / 
                            (docLength + mu);
                                    
                    logLikelihood += queryWeight * Math.log(smoothedDocProb);                        

                }  
                else {
                    System.err.println("Invalid smoothing model specified.");
                }     
            }
                
        } catch (Exception e) {
            e.printStackTrace(); 
        }                        
           
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
    }       
}
