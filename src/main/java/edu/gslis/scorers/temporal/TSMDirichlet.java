package edu.gslis.scorers.temporal;

import java.util.Iterator;

import edu.gslis.searchhits.SearchHit;


/**
 * Smooth the document distribution by the temporal distribution at time t
 */
public class TSMDirichlet extends TemporalScorer 
{

    String MU = "mu";
    String GAMMA = "gamma";
    
    
    public double score(SearchHit doc)
    {

        Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
                
        double mu = paramTable.get(MU);
        double gamma = paramTable.get(GAMMA);
        
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

                // Get the bin for this document 
                long docTime = getDocTime(doc);
                int t = getBin(docTime);
                
                
                //p(w | C): +1 is necessary when working with partial collections (i.e., latimes)
                double collectionProb = (1 + collectionStats.termCount(feature)) 
                        / collectionStats.getTokCount();
                
                double temporalFreq = 0;                
                // Only use temporal information if within the timeframe
                if (docTime >= startTime && docTime <= endTime) {
                    double[] dist = tsIndex.get(feature);
	                	
	                if (dist != null) {
	                	temporalFreq = dist[t];
	                }
                }	
                                                           
                double docPr = 
                        (docFreq + mu*collectionProb) / 
                        (docLength + mu);
                
                double temporalDocPr = 
                        (temporalFreq + gamma*collectionProb) / 
                        (tsIndex.getLength(t) + gamma);
                
                double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
                          
            	logLikelihood += queryWeight * Math.log(temporalDocPr);                 
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
