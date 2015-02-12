package edu.gslis.temporal.scorers;

import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.TimeZone;

import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

/**
 * Li & Croft
 * 
 */
public class LiCroftScorer extends TemporalScorer
{

    String LAMBDA = "lambda";

    
    
    public double score(SearchHit doc) 
    {
        double logLikelihood = 0.0;

        double lambda = paramTable.get(LAMBDA);
        
        // Get the bin for this document 
        long docTime = getDocTime(doc);
        long t = (endTime - docTime)/interval;

        Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
        while(queryIterator.hasNext()) 
        {
            String feature = queryIterator.next();
            double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
            double docLength = doc.getLength();
            
            double collectionProb = (1 + collectionStats.termCount(feature)) / collectionStats.getTokCount();
            double pr = (docFreq + 
                    paramTable.get(MU)*collectionProb) / 
                    (docLength + paramTable.get(MU));
            double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
            

            logLikelihood += queryWeight * Math.log(pr);
            
        }
        
        double timePr = lambda * Math.exp(-1*lambda*t);

        logLikelihood += timePr;
        return logLikelihood;
    }

    @Override
    public void init(SearchHits hits) {
        // TODO Auto-generated method stub
        
    }
    
}
