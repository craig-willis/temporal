package edu.gslis.temporal.scorers;

import java.util.Iterator;
import java.util.Map;

import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Uses a TimeSeriesIndex to calculate a temporal language model for a given
 * interval and smooth the document using the best matching temporal model to theta_D
 * based on KL divergence
 */
public class TimeSmoothedScorerBestMultinomial extends TemporalScorer 
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
            FeatureVector dv = doc.getFeatureVector();
            
            // Map of feature vectors for each bin(t)
            Map<Integer, FeatureVector> tms = createTemporalModels(dv);
            
            // Rank temporal models with respect to documents
            FeatureVector bestTM = tms.get(t);
            double maxScore = Double.NEGATIVE_INFINITY;
            int bestBin = t;
            for (int bin: tms.keySet()) {
                FeatureVector tfv = tms.get(bin);
                tfv.normalize();
                double score = scoreTemporalModel(dv, tfv);
                if (score > maxScore) {
                    maxScore = score;
                    bestTM = tfv;
                    bestBin = bin;
                }                    
            }            
            
            // Now calculate the score for this document using 
            // a combination of the temporal and collection LM.
            while(queryIterator.hasNext()) 
            {
                String feature = queryIterator.next();
                
                //p(w | C): +1 is necessary when working with partial collections (i.e., latimes)
                double pwC = (1 + collectionStats.termCount(feature)) / collectionStats.getTokCount();

                //p(w | T)
                double timePr = bestTM.getFeatureWeight(feature);
                                           
                double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
                double docLength = doc.getLength();

                // Smooth the temporal model wiith the collection model
                double smoothedTempPr = lambda*timePr + (1-lambda)*pwC;
                    
                // Smooth document language model with temporal language model
                double smoothedDocProb = (docFreq + mu*smoothedTempPr)/(docLength + mu);

                double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
                
                logLikelihood += queryWeight * Math.log(smoothedDocProb);

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
        // TODO Auto-generated method stub
        
    }   
    
}
