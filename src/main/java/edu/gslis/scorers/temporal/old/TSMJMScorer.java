package edu.gslis.scorers.temporal.old;

import java.util.Iterator;

import edu.gslis.scorers.temporal.TemporalScorer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

/**
 * Simple linear combination temporal smoothing
 */
public class TSMJMScorer extends TemporalScorer 
{

    String BETA = "beta";
    String LAMBDA = "lambda";

    
    
    public double score(SearchHit doc)
    {
        double logLikelihood = 0.0;
        Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
                
        // Parameter controlling linear combination of smoothed document and collection language models.
        double lambda = paramTable.get(LAMBDA);
        double beta = paramTable.get(BETA);

        
        // Get the bin for this document 
        long docTime = getDocTime(doc);
        int t = getBin(docTime);

        // Ignore documents outside of the temporal bounds
        if (docTime < startTime || docTime > endTime)
            return Double.NEGATIVE_INFINITY;

        try
        {
            
            // Now calculate the score for this document using 
            // a combination of the temporal and collection LM.
            queryIterator = gQuery.getFeatureVector().iterator();
            while(queryIterator.hasNext()) 
            {
                String feature = queryIterator.next();

                // Document model
                double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
                double docLength = doc.getLength();                
                double docProb = docFreq/docLength;

                // Temporal model
                double tfreq = tsIndex.get(feature, t);
                double tlen = tsIndex.getLength(t);
                double temporalProb = tfreq / tlen; 

                // Collection model
                //p(w | C): +1 is necessary when working with partial collections (i.e., latimes)
                double collectionProb = (1 + collectionStats.termCount(feature)) 
                        / collectionStats.getTokCount();

                
                
                // Simple linear combination of document, temporal, and collection models
                double smoothedDocProb = (1-lambda)*docProb + 
                        lambda*( (1-beta)*temporalProb + beta*collectionProb);
                
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

    }
    
    @Override
    public void init(SearchHits hits) {
    }       
}
