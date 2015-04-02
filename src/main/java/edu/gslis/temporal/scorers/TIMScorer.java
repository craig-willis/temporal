package edu.gslis.temporal.scorers;

import java.util.Iterator;

import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

/**
 * Assume that temporal and lexical evidence are independent
 * Score 
 */
public class TIMScorer extends TemporalScorer 
{

    String MU = "mu";
    String ALPHA = "alpha";
    
    public double score(SearchHit doc)
    {
        // Get the bin for this document 
        long docTime = getDocTime(doc);
        int t = getBin(docTime);

        
        double alpha = paramTable.get(ALPHA);


        // Ignore documents outside of the temporal bounds
        if (docTime < startTime || docTime > endTime)
            return Double.NEGATIVE_INFINITY;
        
        
        double logLikelihood = 0.0;

        try {

            Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
            while(queryIterator.hasNext()) 
            {
                String feature = queryIterator.next();
                double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
                double docLength = doc.getLength();
                
                // The 1 here is because some query terms in TREC don't appear in all collections when we 
                // separate out sources.
                double collectionProb = (1 + collectionStats.termCount(feature)) / collectionStats.getTokCount();
                double pr = (docFreq + 
                        paramTable.get(MU)*collectionProb) / 
                        (docLength + paramTable.get(MU));
                double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
                double lexicalPr = queryWeight * Math.log(pr);                 
    
    
                double tfreq = tsIndex.get(feature, t);
                double tlen = tsIndex.getLength(t);
                double temporalPr = 0;
                if (tfreq > 0 && tlen > 0) 
                    temporalPr = queryWeight * Math.log(tfreq/tlen);
                
                logLikelihood += alpha*temporalPr + (1-alpha)*lexicalPr;
                
                
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
