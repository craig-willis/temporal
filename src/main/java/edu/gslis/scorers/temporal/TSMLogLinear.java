package edu.gslis.scorers.temporal;

import java.util.Iterator;

import edu.gslis.searchhits.SearchHit;


/**
 * Log-linear combination
 */
public class TSMLogLinear extends TemporalScorer 
{

    String MU = "mu";
    String LAMBDA = "lambda";
    
    
    public double score(SearchHit doc)
    {

        Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
                
        double mu = paramTable.get(MU);
        
        double lambda = paramTable.get(LAMBDA);

        double logLikelihood = 0.0;
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

                // Get the bin for this document 
                long docTime = getDocTime(doc);
                int t = getBin(docTime);
                
                double temporalPr = 0;                
                // Only use temporal information if within the timeframe
                if (docTime >= startTime && docTime <= endTime) {
                    double[] dist = tsIndex.get(feature);
                	
	                if (dist != null)
	                	temporalPr = dist[t]/tsIndex.getLength(t);
                }	

                double docPr = 
                        (docFreq + mu*collectionProb) / 
                        (docLength + mu);
                
                double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
                   
                if (temporalPr > 0)      
                	logLikelihood += queryWeight * Math.log(Math.pow(docPr, lambda)*Math.pow(temporalPr, 1-lambda)); 
                else
                	logLikelihood += queryWeight * Math.log(docPr);                 
            }
                
        } catch (Exception e) {
            e.printStackTrace(); 
        }                        
           
        return logLikelihood;
    }
      
    @Override
    public void close() {
    }
    
}
