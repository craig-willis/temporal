package edu.gslis.temporal.scorers;

import java.sql.SQLException;
import java.text.DateFormat;
import java.util.Iterator;

import weka.estimators.KernelEstimator;
import edu.gslis.docscoring.QueryDocScorer;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;


public class DocTimeScorer extends QueryDocScorer 
{

    String MU = "mu";
    String GAMMA = "gamma";
    
    long startTime = 0;
    long endTime = 0;
    long interval = 0;
    int winSize = 5;
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
        int t = (int)((docTime - startTime)/interval);

        
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
            
            long numBins = (endTime - startTime)/interval;

            double tempPr = 0;
            try {
                                
                double[] series = index.get(feature);
                double[] total = index.get("_total_");

                                
                // Moving average
                int size = series.length;
                if (t < size)
                {
                    double timeFreq = series[t];
                    int n = 1;
                    
                    for (int i=0; i < winSize; i++) {
                        if (t > i)
                            timeFreq += series[t - i];
                        if (t < size - i)
                            timeFreq += series[t + i];
                        n++;
                    }

                    // Average freq at time t
                    timeFreq = timeFreq/(double)n;
                    
                    // n(w,T)/n(w) = p(T | w)
                    double wordFreq = collectionStats.termCount(feature);
                    

                    // using p(T|w) smooted by p(T)
                    double pT = 1/(double)numBins;
                    tempPr = (1 + timeFreq + paramTable.get(GAMMA)*pT) / 
                            (wordFreq + paramTable.get(GAMMA));                    
                    
                }           
                

            } catch (SQLException e) {
                e.printStackTrace();
            }
            logLikelihood += queryWeight * Math.log(lexPr) + queryWeight*Math.log(tempPr);

            
            
            //System.out.println("score " + feature + "," + docFreq + "," + docLength + "," + collectionProb + "," + pr + "," + queryWeight + "," + logLikelihood);
                        
        }
       
        return logLikelihood;
    }
    
}
