package edu.gslis.temporal.scorers;

import java.text.DateFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import edu.gslis.docscoring.QueryDocScorer;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Uses a TimeSeriesIndex to calculate a temporal language model for a given
 * interval and smooth the document using the best matching temporal model.
 */
public class TimeSmoothedScorer extends QueryDocScorer 
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
            // Document language model smoothed with a linear combination
            // of the temporal language model at bin(t) and the collection language model

            // Total number of events for each time = bin(t)
            // The TimeSeriesIndex contains 1 row per term and 1 column per bin based on interval.
            // There are tf and df versions of the index.
            double[] total = index.get("_total_");
            
            
            // Map of language model for bin(t)
            Map<Integer, FeatureVector> pis = new TreeMap<Integer, FeatureVector>();
            FeatureVector dv = doc.getFeatureVector();

            // Populate language model for each bin pi_i = LM(bin(t))
            // As a shortcut, this is just the model for features in 
            // the document language model use the "_total_" values to get
            // length(t).
            Iterator<String> dvIt = dv.iterator();
            while(dvIt.hasNext()) {
                String feature = dvIt.next();
                double[] series = index.get(feature);                
                for (int i=0; i<series.length; i++) {
                    FeatureVector pi = pis.get(i);
                    if (pi == null)
                        pi = new FeatureVector(null);
                    pi.addTerm(feature, series[i]);
                    pis.put(i, pi);
                }
            }
            
            // Find the best pi for this document based on KL(tlm, dlm)
            FeatureVector bestTM = null;
            double minKL = 1;
            int bestBin = 0;
            for (int bin: pis.keySet()) {
                FeatureVector pi = pis.get(bin);
                // KL (pi|dv)
                double kl = kl(pi, dv);
                if (kl >0 && kl < minKL) {
                    minKL = kl;
                    bestTM = pi;
                    bestBin = bin;
                }
            }
            
            // Total number of events for the "best" bin
            double tempLen = total[bestBin];

            while(queryIterator.hasNext()) 
            {
                String feature = queryIterator.next();
                
                //p(w | C) 
                double pwC = (1+ collectionStats.termCount(feature)) / collectionStats.getTokCount();

                // Attempt 2: p(w | T) = p(w | pi)
                double timePr = 0;
                if (bestTM != null)
                     timePr = bestTM.getFeatureWeight(feature)/tempLen;
                
                /* Attempt 1: p(w | T) = p(T|w)p(w) / p(T)
                double[] series = index.get(feature);

                double total = 0;
                for (double s: series)
                    total += s;
                double pTw = series[t] / total;

                // p(w | t)
                double timePr = pTw*pwC/(1/(double)numBins);
                */
                // Lexical model            
                double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
                double docLength = doc.getLength();
//                double pr = (1 + docFreq + mu*( lambda*timePr + (1-lambda)*pwC))/(docLength + mu);
                double pr = (docFreq + mu*(lambda*timePr + (1-lambda)*pwC))/(docLength + mu);
//                double pr = (docFreq + mu*timePr)/(docLength + mu);
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
