package edu.gslis.temporal.scorers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RUtil;

/**
 * New TSM scorer. 
 */
public class TSMScorer extends TemporalScorer 
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

        // Get the bin for this document       
        double epoch = (Double)doc.getMetadataValue(Indexer.FIELD_EPOCH);
        long docTime = (long)epoch;
        int t = (int)((docTime - startTime)/interval);

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
                double tlen = tsIndex.get("_total_", t);
                
                // Use Laplace smoothing for the temporal models. This is just for the case 
                // where lambda = 1
                //double timePr = (tfreq + 1) / (tlen + collectionStats.getTokCount()); 
                double timePr = tfreq / tlen; 
                                
                // 2-stage smoothing
                // 1. Smooth the document with the temporal model
                double smoothedTemporalProb = 
                        (docFreq + mu*timePr) / 
                        (docLength + mu);
                
                // 2. Smooth the smoothed model with the collection model
                double smoothedDocProb = 
                        lambda*smoothedTemporalProb + (1-lambda)*collectionProb;

                /*
                // Smooth the temporal models with the collection model
                double smoothedTemporalProb = 
                        lambda*timePr + (1-lambda)*collectionProb;
                
                // Smooth document LM with smoothed temporal LM            
                double smoothedDocProb = 
                        (docFreq + mu*smoothedTemporalProb) / 
                        (docLength + mu);
                */
                
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
    }       
}
