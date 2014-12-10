package edu.gslis.temporal.scorers;

import java.text.DateFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Uses a TimeSeriesIndex to calculate a temporal language model for a given
 * interval and smooth the document using the best matching temporal model to theta_D.
 */
public class TimeSmoothedScorerBestMultinomial extends TemporalScorer 
{

    String MU = "mu";
    String LAMBDA = "lambda";
    
    long startTime = 0;
    long endTime = 0;
    long interval = 0;
    DateFormat df = null;
    

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
            // Total number of events for each time = bin(t)
            // The TimeSeriesIndex contains 1 row per term and 1 column per bin based on interval.
            // There are tf and df versions of each index.
            double[] total = tsIndex.get("_total_");
            
            FeatureVector dv = doc.getFeatureVector();
            
            // Map of feature vectors for each bin(t)
            Map<Integer, FeatureVector> pis = new TreeMap<Integer, FeatureVector>();

            // Populate temporal language model for each bin pi_i = LM(bin(t))
            // As a shortcut, this is just the model for features in 
            // the document language model use the "_total_" values to get
            // length(t).
            Iterator<String> dvIt = dv.iterator();
            while(dvIt.hasNext()) {
                String feature = dvIt.next();
                // Time series for feature
                double[] series = tsIndex.get(feature);   
                // For each bin
                for (int i=0; i<series.length; i++) {
                    FeatureVector pi = pis.get(i);
                    if (pi == null)
                        pi = new FeatureVector(null);
                    pi.addTerm(feature, series[i]);
                    pis.put(i, pi);
                }
            }
            // At this point, pis contains a FeatureVector for each bin.
            
            // Find the best bin for this document based on KL(tlm, dlm)
            FeatureVector bestTM = pis.get(t);
            double minKL = -1;
            int bestBin = 0;
            for (int bin: pis.keySet()) {
                FeatureVector pi = pis.get(bin);
                // KL (pi|dv)
                double kl = kl(pi, dv);
                if ((kl > 0 && minKL < 0) || (kl > 0 && kl < minKL)) {
                    minKL = kl;
                    bestTM = pi;
                    bestBin = bin;
                }
            }
            
            
            // Total number of events for the best matching bin
            double binLen = total[bestBin];

            // Now calculate the score for this document using 
            // a combination of the temporal and collection LM.
            while(queryIterator.hasNext()) 
            {
                String feature = queryIterator.next();
                
                //p(w | C): +1 is necessary when working with partial collections (i.e., latimes)
                double pwC = (1 + collectionStats.termCount(feature)) / collectionStats.getTokCount();

                //p(w | T)
                double timePr = bestTM.getFeatureWeight(feature)/binLen;
                                           
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
            tsIndex.close();            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Override
    public void init(SearchHits hits) {
        // TODO Auto-generated method stub
        
    }   
    
}
