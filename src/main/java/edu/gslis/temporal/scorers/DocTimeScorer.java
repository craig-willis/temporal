package edu.gslis.temporal.scorers;

import java.sql.SQLException;
import java.text.DateFormat;
import java.util.Iterator;

import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;


public class DocTimeScorer extends TemporalScorer 
{

    String MU = "mu";
    String GAMMA = "gamma";
    
    long startTime = 0;
    long endTime = 0;
    long interval = 0;
    int winSize = 3;
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
    
    public void setWin(int winSize) {
        this.winSize = winSize;
    }
    public double score(SearchHit doc) 
    {
        double logLikelihood = 0.0;
        Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
        //System.out.println(doc.getDocno());
        
        // temporal model        
        double epoch = (Double)doc.getMetadataValue(Indexer.FIELD_EPOCH);
        long docTime = (long)epoch;
        if (docTime < startTime) {
            System.err.println("Warning: doc time " + docTime + " is less than the start time " + startTime);
            docTime = startTime;
        }

        int t = (int)((docTime - startTime)/interval);

        try
        {
            double z= index.getNorm("tf", t);
    
            while(queryIterator.hasNext()) 
            {
                String feature = queryIterator.next();
    
                // Lexical model            
                double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
                double docLength = doc.getLength();
                double collectionProb = (1 + collectionStats.termCount(feature)) / collectionStats.getTokCount();
                double lexPr = (docFreq + 
                        paramTable.get(MU)*collectionProb) / 
                        (docLength + paramTable.get(MU));
                double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
                
                double tempPr = 0;
                                
                double[] series = index.get(feature);
                double total = 0;
                for (double s: series)
                    total += s;

                                
                // Moving average
                int size = series.length;
                if (t < size)
                {
                    double timeFreq = series[t];
                    int n = 1;
                    
                    for (int i=0; i < winSize; i++) {
                        if (t > i) {
                            timeFreq += series[t - i];
                            n++;
                        }
                        if (t < size - i) {
                            timeFreq += series[t + i];
                            n++;
                        }
                    }

                    // Average freq at time t
                    timeFreq = timeFreq/(double)n;
                    
                    // p(t|w)
                    tempPr = (1+timeFreq)/total;
                    // Normalized
                    if (z > 0)
                        tempPr /= z;
                    
                }           

                if (Double.isInfinite(tempPr))
                        System.out.println("tempPr=Inf for " + doc.getDocno() + ", total=" + total + ",z=" + z + ", t=" + t);

                if (tempPr == 0)
                    System.out.println("tempPr=0 for " + doc.getDocno());

                logLikelihood += queryWeight * Math.log(lexPr) + queryWeight*Math.log(tempPr); 
    
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
       
        return logLikelihood;
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
