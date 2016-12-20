package edu.gslis.scorers.temporal;

import java.util.Iterator;

import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

/**
 * Calculate temporal language models for all intervals
 * Smooth the document using temporal language models 
 * weighted by KL divergence from the collection.
 */
public class TSAQScorer extends TemporalScorer 
{

    String MU = "mu";
    String LAMBDA = "lambda";
    String SMOOTH = "smooth";

    
    
    
    public double score(SearchHit doc)
    {
        double logLikelihood = 0.0;
        Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
                
        // Usual Dirichlet parameter
        double mu = paramTable.get(MU);
        // Parameter controlling linear combination of temporal and collection language models.
        double lambda = paramTable.get(LAMBDA);
        double smooth = paramTable.get(SMOOTH);

        // Get the document time      
        long docTime = getDocTime(doc);        
        if (docTime < startTime) {
            return Double.NEGATIVE_INFINITY;
        }

        try
        {                        
            // Approximate query generation likelihood for each bin
            int numBins = tsIndex.getNumBins();
            double[] scores = new double[numBins];
            double z = 0;            
            for (int bin = 0; bin < numBins; bin++) {
                // Log-likelihood of query given temporal model
                double ll = scoreTemporalModel(gQuery.getFeatureVector(), bin);
                scores[bin] = Math.exp(ll);
                z += scores[bin];
            }

            // p(theta_i given Q)
            for (int bin = 0; bin < numBins; bin++) {
                scores[bin] = scores[bin]/z;
            }
            
            
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
                
                // Sum of probabilities of this term across all bins
                double temporalPr = 0;
                for (int bin = 0; bin < numBins; bin++) {
                    double tfreq = tsIndex.get(feature, bin);
                    double tlen = tsIndex.get("_total_", bin);

                    temporalPr += (scores[bin]) * (tfreq/tlen);
                }
                     
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
    }
    @Override
    public void init(SearchHits hits) {
    }   
    
}
