package edu.gslis.temporal.scorers;

import java.text.DateFormat;
import java.util.Iterator;

import edu.gslis.docscoring.QueryDocScorer;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Uses a TimeSeriesIndex to calculate a temporal language model for a given
 * interval.
 */
public class SimpleTimeSmoothedScorer extends QueryDocScorer 
{

    String MU = "mu";
    String LAMBDA = "lambda";
    
    long startTime = 0;
    long endTime = 0;
    long interval = 0;
    DateFormat df = null;
    
    TimeSeriesIndex index = new TimeSeriesIndex();
    
    public void setTsIndex(String tsIndex) {
        try {
            System.out.println("Opening: " + tsIndex);
            index.open(tsIndex);
        } catch (Exception e) {
            e.printStackTrace();
        }               
    }
    public void setDateFormat(DateFormat df) {
        this.df = df;
    }
    public void setQuery(GQuery query) {
        this.gQuery = query;
    }
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }
    public void setInterval(long interval) {
        this.interval = interval;
    }
    
    
    public double score(SearchHit doc) 
    {
        double logLikelihood = 0.0;
        Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
        
        // temporal model        
        double epoch = (Double)doc.getMetadataValue(Indexer.FIELD_EPOCH);
        long docTime = (long)epoch;
        int t = (int)((docTime - startTime)/interval);
        
        long numBins = (endTime - startTime)/interval;
        
        double mu = paramTable.get(MU);
        double lambda = paramTable.get(LAMBDA);


        try
        {
            while(queryIterator.hasNext()) 
            {
                String feature = queryIterator.next();
                
                //p(w | C) 
                double pwC = (1+ collectionStats.termCount(feature)) / collectionStats.getTokCount();

                // Attempt 1: p(w | T) = p(T|w)p(w) / p(T)
                double[] series = index.get(feature);

                double total = 0;
                for (double s: series)
                    total += s;
                double pTw = series[t] / total;

                // p(w | t)
                double timePr = pTw*pwC/(1/(double)numBins);
                
                // Lexical model            
                double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
                double docLength = doc.getLength();
                double pr = (docFreq + mu*(lambda*timePr + (1-lambda)*pwC))/(docLength + mu);
                double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
                
                logLikelihood += queryWeight * Math.log(pr);

            }
        } catch (Exception e) {
            e.printStackTrace(); 
        }                        
           
        return logLikelihood;
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
}
