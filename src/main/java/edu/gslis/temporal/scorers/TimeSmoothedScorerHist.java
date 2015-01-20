package edu.gslis.temporal.scorers;

import java.text.DateFormat;
import java.util.Iterator;

import edu.gslis.indexes.TimeSeriesIndex;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Uses SSJ KernelDensity to estimate p(w|T) = p(T|w)/sum_{w in V} p(T|w)
 */
public class TimeSmoothedScorerHist extends TemporalScorer 
{

    String MU = "mu";
    String LAMBDA = "lambda";
    int winSize = 3;
    
    long startTime = 0;
    long endTime = 0;
    long interval = 0;
    DateFormat df = null;
    
    TimeSeriesIndex index = new TimeSeriesIndex();
    
    public void setTsIndex(String tsIndex) {
        try {
            System.out.println("Opening: " + tsIndex);
            index.open(tsIndex, true, "h2");
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

        try
        {
            double z = index.getNorm("tf", t);  
            
            // Now calculate the score for this document using 
            // a combination of the temporal and collection LM.
            while(queryIterator.hasNext()) 
            {
                String feature = queryIterator.next();
                
                // Get the series for this feature
                double[] hist = index.get(feature);
               
                //p(w | C): +1 is necessary when working with partial collections (i.e., latimes)
                double pwC = (1 + collectionStats.termCount(feature)) / collectionStats.getTokCount();
                                           
                double total = 0;
                for (double h: hist)
                    total += h;
                               
                double timePr = 0;
                // Moving average
                int size = hist.length;
                if (t < size)
                {
                    double freq = hist[t];
                    int n = 1;
                    
                    for (int i=0; i < winSize; i++) {
                        if (t > i) {
                            freq += hist[t - i];
                            n++;
                        }
                        if (t < size - i) {
                            freq += hist[t + i];
                            n++;
                        }
                    }

                    // Average freq at time t
                    freq = freq/(double)n;
                    
                    // p(t|w)
                    if (total > 0 && z > 0) {
                        timePr = (1+freq)/total;
                        timePr /= z;
                    }                    
                }           

                
                
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
    @Override
    public void close() {
        try {
            index.close();            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Override
    public void init(SearchHits hits) {
        // TODO Auto-generated method stub
        
    }
}
