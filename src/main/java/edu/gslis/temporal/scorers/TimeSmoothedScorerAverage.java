package edu.gslis.temporal.scorers;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Calculate temporal language models for all intervals
 * Smooth the document using temporal language models 
 * weighted by KL divergence from the collection.
 */
public class TimeSmoothedScorerAverage extends TemporalScorer 
{

    String MU = "mu";
    String LAMBDA = "lambda";
    
    
    
    public double score(SearchHit doc)
    {
        double logLikelihood = 0.0;
        Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
                
        // Usual Dirichlet parameter
        double mu = paramTable.get(MU);
        // Parameter controlling linear combination of temporal and collection language models.
        double lambda = paramTable.get(LAMBDA);

        try
        {
            FeatureVector dv = doc.getFeatureVector();
            
            // Map of feature vectors for each bin(t)
            Map<Integer, FeatureVector> tms = createTemporalModels(dv);
                        
            // Approximate document generation likelihood
            // Score each temporal model with respect to this document.
            double[] scores = new double[tms.size()];
            double z = 0;            
            for (int bin: tms.keySet()) {
                double score = scoreTemporalModel(dv, tms.get( bin));
                scores[ bin] = score;
                z += score;
            }
            
            // Now calculate the score for this document using 
            // a combination of the temporal and collection LM.
            while(queryIterator.hasNext()) 
            {
                String feature = queryIterator.next();
                
                double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
                double docLength = doc.getLength();

                //p(w | C): +1 is necessary when working with partial collections (i.e., latimes)
                double collectionProb = (1 + collectionStats.termCount(feature)) / collectionStats.getTokCount();

                // Weight the probability for each bin by the noramlized KL score
                double timePr = 0;
                for (int bin: tms.keySet()) {
                    FeatureVector tfv = tms.get(bin);
                    if (tfv.getLength() > 0)
                        timePr += (scores[bin]/z) * (tfv.getFeatureWeight(feature)/tfv.getLength());
                }
                                
                // Smooth temporal LM with collection LM
                double smoothedTemporalProb = 
                        lambda*timePr + (1-lambda)*collectionProb;
                
                // Smooth document LM with topic LM            
                double smoothedDocProb = 
                        (docFreq + mu*smoothedTemporalProb) / 
                        (docLength + mu);
                
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
