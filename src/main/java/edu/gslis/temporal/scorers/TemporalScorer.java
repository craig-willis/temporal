package edu.gslis.temporal.scorers;

import java.util.Iterator;


import edu.gslis.indexes.TimeSeriesIndex;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

public abstract class TemporalScorer extends RerankingScorer 
{
    String MU = "mu";
    
    long startTime;
    long endTime;
    long interval;
    String tsIndexPath;
    
    TimeSeriesIndex tsIndex = null;
    
    public abstract void init(SearchHits hits);
    
 
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }
    public void setInterval(long interval) {
        this.interval = interval;
    }
    public void setIndex(TimeSeriesIndex tsIndex) {
        this.tsIndex = tsIndex;
    }
    
    public void close() {        
    }

    public double score(SearchHit doc) 
    {
        double logLikelihood = 0.0;
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
            logLikelihood += queryWeight * Math.log(pr);                        
        }
        return logLikelihood;
    }
    
    public double[] getTimes(SearchHits hits) {
        
        double[] times = new double[hits.size()];
        
        Iterator<SearchHit> it = hits.iterator();
        int i=0;
        while (it.hasNext()) {
            SearchHit hit = it.next();            
            times[i++] = getTime(hit);
        }
        
        return times;
    }
    
    public long getTime(SearchHit hit) {
        String epochStr = String.valueOf((((Double)hit.getMetadataValue(Indexer.FIELD_EPOCH)).longValue()));  
        
        long epoch = Long.parseLong(epochStr);
        return epoch;
    }
}
