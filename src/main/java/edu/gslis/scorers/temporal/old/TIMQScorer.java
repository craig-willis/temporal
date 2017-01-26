package edu.gslis.scorers.temporal.old;

import java.util.Iterator;

import edu.gslis.scorers.temporal.TemporalScorer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

/**
 * Weight documents by probability that temporal bin generated the query
 */
public class TIMQScorer extends TemporalScorer 
{

    String MU = "mu";
    String ALPHA = "alpha";
    
    public double scoreBin(int bin) 
    {
        
        double logLikelihood = 0.0;


        try {

            Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
            while(queryIterator.hasNext()) 
            {
                String feature = queryIterator.next();
                
                double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
    
                double tfreq = tsIndex.get(feature, bin);
                double tlen = tsIndex.getLength(bin);
                
                double pr = (1 + tfreq) / (tlen + collectionStats.getTokCount());
                
                logLikelihood += queryWeight * Math.log(pr);
                
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return logLikelihood;
    }
    
    public double score(SearchHit doc)
    {
        
        // Get the bin for this document 
        long docTime = getDocTime(doc);
        

        // Ignore documents outside of the temporal bounds
        if (docTime < startTime || docTime > endTime)
            return Double.NEGATIVE_INFINITY;

        int bin = getBin(docTime);

        double docll = super.score(doc);
        double binll = scoreBin(bin);

        
        double alpha = paramTable.get(ALPHA);
                
        return alpha*binll + (1-alpha)*docll;
    }
    
      
    @Override
    public void close() {
    }
    
    @Override
    public void init(SearchHits hits) {
    }       
}
