package edu.gslis.temporal.scorers;

import java.util.Iterator;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import edu.gslis.indexes.TimeSeriesIndex;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;

public abstract class TemporalScorer extends RerankingScorer 
{
    String MU = "mu";
    
    long startTime;
    long endTime;
    long interval;
    String tsIndexPath;
    
    TimeSeriesIndex tsIndex = null;
    
    double[] kls = null;
    DescriptiveStatistics klstats = new DescriptiveStatistics();
    
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
    
    public void setKLs(double[] kls) {
        
        double total = 0;
        for (int i=0; i< kls.length; i++) {
            total += kls[i];
        }
        
        for (int i=0; i< kls.length; i++)
            kls[i] /= total;

        for (int i=0; i< kls.length; i++)
            klstats.addValue(kls[i]);
        
        this.kls = kls;
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
    
    public static double[] getTimes(SearchHits hits) {
        
        double[] times = new double[hits.size()];
        
        Iterator<SearchHit> it = hits.iterator();
        int i=0;
        while (it.hasNext()) {
            SearchHit hit = it.next();            
            times[i++] = getTime(hit);
        }
        
        return times;
    }
    
    public static long getTime(SearchHit hit) {
        String epochStr = String.valueOf((((Double)hit.getMetadataValue(Indexer.FIELD_EPOCH)).longValue()));  
        
        long epoch = Long.parseLong(epochStr);
        return epoch;
    }
    
    public double kl(FeatureVector p, FeatureVector q) {
        double kl = 0;
        
        Iterator<String> it = p.iterator();
        while(it.hasNext()) {
            String feature = it.next();
            double pi = p.getFeatureWeight(feature)/p.getLength();
            double qi = q.getFeatureWeight(feature)/q.getLength();
            if (pi > 0 && qi > 0)
                kl += pi * Math.log(pi/qi);
        }
        return kl;
    }
    
    public double movingAverage(double[] series, double[] total, int t, int winSize) 
    {        
        double timePr = 0;
        
        double timeFreq = series[t];
        int n = 1;
        
        int size = series.length;
        if (t < size) {

            for (int i=0; i < winSize; i++) {
                if (t > i)
                    timeFreq += series[t - i];
                if (t < size - i)
                    timeFreq += series[t + i];
                n++;
            }
        }

        // Average freq at time t
        timeFreq = timeFreq/(double)n;
        
        if (series[t] > 0 && total[t] > 0)
            timePr = timeFreq / total[t];
        
        return timePr;
    }
    
    /**
     * Score the temporal model with repsect to the document model.
     * Smooth the temporal model using the collection model.
     * 
     * @param dv    Document model
     * @param tfv   Temporal model
     * @return
     */
    public double scoreTemporalModel(FeatureVector dm, FeatureVector tm)
    {
        double logLikelihood = 0.0;
                     
        for (String feature: dm.getFeatures())
        {                                       
            double tfreq = tm.getFeatureWeight(feature);
            double tlen = tm.getLength();
            
            double smoothedProb = (tfreq + 1)/(tlen + collectionStats.getTokCount());

            double docWeight = dm.getFeatureWeight(feature);
            
            logLikelihood += docWeight * Math.log(smoothedProb);
        }                        
           
        return logLikelihood;
    }
    
}
