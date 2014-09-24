package edu.gslis.temporal.scorers;

import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.TimeZone;

import edu.gslis.docscoring.QueryDocScorer;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;

/**
 * Based on Efron and Golovchinsky.
 * 
 * All of these models are based on the top-K documents
 * 
 * 1. QL/KL-divergence retrieval model
 * 2. Support RM + temporal RM
 * 3. Support JM and Dirichlet smoothing
 * 4. Smooth language model -- temporal and non-temporal
 * 5. Smooth time model ala Jones and Diaz
 * 
 * Training Qrels used to estimate r_q (query specific rate parameter)
 * For each training query
 *      Get top K documents
 *      Sweep r_q to maximize MAP
 * 
 */
public class RecencyScorer extends QueryDocScorer 
{

    String MU = "mu";
    long max = 0;
    long interval = 0;
    
    double rate = 0;
    public void setQuery(GQuery query) {
        this.gQuery = query;
    }
        
    /**
     * rate = 1/hits.size();
     * rate = (rho + k - 1)/(sigma + sum(hits.t);
     * @param rate
     */
    public void setRate(double rate) {
        this.rate = rate;
    }
    
    public void setMax(long max) {
        this.max = max;
    }
    
    public void setInterval(long interval) {
        this.interval = interval;
    }
    
    public double score2(SearchHit doc) {
        double logLikelihood = 0.0;
        Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
        while(queryIterator.hasNext()) {
            String feature = queryIterator.next();
            double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
            double docLength = doc.getLength();
            double collectionProb = (collectionStats.termCount(feature)) / collectionStats.getTokCount();
            double pr = (docFreq + 
                    paramTable.get(MU)*collectionProb) / 
                    (docLength + paramTable.get(MU));
            double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
            logLikelihood += queryWeight * Math.log(pr);
        }
        return logLikelihood;
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
            
            double collectionProb = (1 + collectionStats.termCount(feature)) / collectionStats.getTokCount();
            double pr = (docFreq + 
                    paramTable.get(MU)*collectionProb) / 
                    (docLength + paramTable.get(MU));
            double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
            logLikelihood += queryWeight * Math.log(pr);
            
        }
        
        //double epoch = (Double)doc.getMetadataValue(Indexer.FIELD_EPOCH);
        
        String epochStr = String.valueOf((((Double)doc.getMetadataValue(Indexer.FIELD_EPOCH)).longValue()));  
        
        String dateFormatStr = "yyMMdd"; 
        SimpleDateFormat df = new SimpleDateFormat(dateFormatStr);
        df.setTimeZone(TimeZone.getTimeZone("GMT"));

        try {
            long epoch = df.parse(epochStr).getTime()/1000;
            long t = (max - epoch)/interval;

            
            double timePr = rate * Math.exp(-1*rate*t);
            if (timePr > 0) {
                logLikelihood += Math.log(timePr);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return logLikelihood;
    }
    
}
