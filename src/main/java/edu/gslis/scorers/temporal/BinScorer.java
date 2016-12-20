package edu.gslis.scorers.temporal;

import java.text.DateFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import edu.gslis.docscoring.QueryDocScorer;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;

/**
 * Implementation of Dakka et al. models for time-sensitive queries.
 *  1. Compute query-frequency histogram for q using publication time of documents 
 *     Issue query, get top k documents
 *  2. Partition times into bins b_0,...,b_l based on histogram characteristics
 *     DAY (daily frequency):  bins = days
 *     FIXED (fixed interval): tested 10 (TEN) and 30 (MONTH)
 *     WIN (moving window): tested 3,7,14,28 (no difference, used 7)
 *     MEAN (running mean)
 *     BUMP (bump shapes): Continuous intervals where frequency > average
 *     WORD (word tracking): x = 14
 *  3. Define p(q|t) of each time t based on t's bin.
 * 
 * Dakka, W., Gravano, L., Ipeirotis, P.G. (2012). Answering general time-sensitive queries.
 * IEEE Transactions on Knowledge and Data Engineering 24(2).
 *  
 * @author cwillis
 *
 */
public class BinScorer extends QueryDocScorer 
{

    String MU = "mu";
    Map<Long, Integer> bins = new TreeMap<Long, Integer>();
    long max = 0;
    long interval = 0;
    double rate = 0;
    double total = 0;
    DateFormat df = null;

    public void setDateFormat(DateFormat df) {
        this.df = df;
    }
    public void setQuery(GQuery query) {
        this.gQuery = query;
    }
    
    public void setRate(double rate) {
        this.rate = rate;
    }
    public void setBins(Map<Long, Integer> bins) {
        this.bins = bins;
        total = 0;
        for (long time: bins.keySet()) {
            int b = bins.get(time);
            total += rate * Math.exp(-1*rate*b);
        }
    }
    
    public void setMax(long max) {
        this.max = max;
    }
    
    public void setInterval(long interval) {
        this.interval = interval;
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
            //System.out.println("score " + feature + "," + docFreq + "," + docLength + "," + collectionProb + "," + pr + "," + queryWeight + "," + logLikelihood);
                        
        }
        
        String epochStr = String.valueOf((((Double)doc.getMetadataValue(Indexer.FIELD_EPOCH)).longValue()));  
        
        //String dateFormatStr = "yyMMdd"; 
        //SimpleDateFormat df = new SimpleDateFormat(dateFormatStr);
        //df.setTimeZone(TimeZone.getTimeZone("GMT"));

        try {
            long epoch = 0;
            if (df != null)
                epoch = df.parse(epochStr).getTime()/1000;
            else
                epoch = Long.parseLong(epochStr);
            long t = (max - epoch)/interval;

            
            int bin = bins.get(t);
            
            double timePr = rate*Math.exp(-1*rate*bin) / total;
            //System.out.println("time " + gQuery.getText() + "," + epoch + "," + t + "," + bin + "," + timePr);
                    
            if (timePr > 0) {
                logLikelihood += Math.log(timePr);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return logLikelihood;
    }
    
}
