package edu.gslis.temporal.scorers;

import java.util.Iterator;

import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

/**
 * New TSM scorer. 
 */
public class TSMNPMIScorer extends TemporalScorer 
{

    String MU = "mu"; // Used by Dirichlet
    String BETA = "beta"; // NPMI value
    String LAMBDA = "lambda"; // Used for linear combo of temporal+collection model
    
    
    
    public double score(SearchHit doc)
    {
        double logLikelihood = 0.0;
        Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
                
        // Dirichlet parameter controlling amount of smoothing using temporal model
        double mu = paramTable.get(MU);
        // J-M lambda
        double lambda = paramTable.get(LAMBDA);
        double beta = paramTable.get(BETA);

        
        // Get the bin for this document 
        long docTime = getDocTime(doc);
        int t = getBin(docTime);

        // Ignore documents outside of the temporal bounds
        if (docTime < startTime)
            return Double.NEGATIVE_INFINITY;

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
                double tfreq = tsIndex.get(feature, t);
                double tlen = tsIndex.getLength(t);
                double npmi = tsIndex.getNpmi(feature)[t];
                
                double temporalPr = tfreq / tlen; 
                
                double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);

                
                // First, smooth the temporal probability with the collection probability
                double smoothedTempProb = 
                        lambda*temporalPr + (1-lambda)*collectionProb;

                
                // Second, if the npmi value is high enough, smooth the document
                // with the temporal model
                if (npmi > beta) {
                    double smoothedDocProb = 
                            (docFreq + mu*smoothedTempProb) / 
                            (docLength + mu);                    
                    logLikelihood += queryWeight * Math.log(smoothedDocProb);                     
                }
                else {
                    // Otherwise, just use the collection 
                    double smoothedDocProb = 
                            (docFreq + mu*collectionProb) / 
                            (docLength + mu);                    
                    logLikelihood += queryWeight * Math.log(smoothedDocProb);                    

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
