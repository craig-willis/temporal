    package edu.gslis.old.temporal.scorers;

import java.text.DateFormat;
import java.util.Iterator;

import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Simple multinomial estimate of p(w|T) given 
 * the temporal language model at time T and the 
 * timestamp of the document.
 * 
 * This scorer requires an existing TimeSeriesIndex
 * with a matching startTime, endTime and interval.
 * 
 * This has three parameters:
 *      mu:         Standard Dirichlet parameter
 *      lambda:     Weight of p(w|T) v p(w|C) in smoothing
 *      window:     Size of window used to smooth the histogram (moving average)
 */
public class TimeSmoothedScorerMultinomialDirichlet extends TemporalScorer 
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
            index.open(tsIndex, true);
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
        
        // Document language model smoothed with a linear combination
        // of the temporal language model at bin(t) and the collection language model

        // temporal model        
        double epoch = (Double)doc.getMetadataValue(Indexer.FIELD_EPOCH);
        long docTime = (long)epoch;
        int t = (int)((docTime - startTime)/interval);
        
        // Usual Dirichlet parameter
        double mu = paramTable.get(MU);
        // Parameter controlling linear combination of temporal and collection language models.
        double lambda = paramTable.get(LAMBDA);

       // System.out.println(doc.getDocno()  + "," + t);
        try
        {
            // Total number of events for each time = bin(t)
            // The TimeSeriesIndex contains 1 row per term and 1 column per bin based on interval.
            // There are tf and df versions of each index.
            double[] total = index.get("_total_");
            
            // Now calculate the score for this document using 
            // a combination of the temporal and collection LM.
            while(queryIterator.hasNext()) 
            {
                String feature = queryIterator.next();
                
                // Get the series for this feature
                double[] series = index.get(feature);
                
                double timeFreq = series[t];
                int n = 1;
                
                int size = series.length;
                int winSize = 5;
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
                                
                //p(w | C): +1 is necessary when working with partial collections (i.e., latimes)
                double pwC = (1 + collectionStats.termCount(feature)) / collectionStats.getTokCount();
                                           
                // Smooth the temporal model with the collection model
                double timePr = (timeFreq + lambda*pwC)/(total[t] + lambda);

                double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
                double docLength = doc.getLength();
                double pr = (docFreq + mu*timePr)/(docLength + mu);
                
                double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
                

                double ll = queryWeight * Math.log(pr);
                logLikelihood += ll;
                //System.out.println("\t" + feature + "," + docFreq + "," + mu + "," + lambda + "," + timePr +"," + pwC + "," + docLength + "," + mu + "," + queryWeight + "," + pr + "," + ll);
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
    @Override
    public void close() {
        try {
            index.close();            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
