package edu.gslis.temporal.main;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;

public class Peetz {
    long minTime = 0;
    long maxTime = 0;
    long interval = 0;
    SimpleDateFormat df = null;
    
    public Peetz(long min, long max, long interval, String dateFormat) {
        this.minTime = min;
        this.maxTime = max;
        this.interval = interval;
        if (dateFormat != null) {
            this.df = new SimpleDateFormat(dateFormat);
        }
    }
    
    public long getDocTime(SearchHit hit) {
        double epoch = (Double)hit.getMetadataValue(Indexer.FIELD_EPOCH);
        
        long docTime = 0;
        if (df != null) {
            String epochStr = String.valueOf((long)epoch);  
            try {
                docTime = df.parse(epochStr).getTime();     
            } catch (ParseException e) {
                e.printStackTrace();
            }
        } else 
            docTime = (long)epoch;
        
        
        // Time normalized document time
        long t = (docTime - minTime)/interval;
        return t;
    }
    public Map<Long, Double> getTimeSeries(SearchHits hits) 
    {
        // Construct a histogram of daily document frequencies
        Map<Long, Double> hist = new TreeMap<Long, Double>();
        
        int numBins = (int)((maxTime - minTime)/interval);
        for (long i=0; i<numBins; i++) {
            hist.put(i, 0D);
        }
        
        Iterator<SearchHit> it = hits.iterator();
        while (it.hasNext()) 
        {
            SearchHit hit = it.next();

            double score = hit.getScore();
            
            long t = getDocTime(hit);
            

            double r = 0;
            if (hist.get(t) != null) 
                r = hist.get(t);
            r += score;
            
            hist.put(t, r);
        }
        return hist;
    }
    
    // What this should really be is a list of documents in each burst
    public List<Long> getBursts(Map<Long, Double> bins) {
        List<Long> bursts = new LinkedList<Long>();
        DescriptiveStatistics stats = new DescriptiveStatistics();
        for (long t: bins.keySet())
            stats.addValue(bins.get(t));

        double mu = stats.getMean();
        double sigma = stats.getStandardDeviation();
        
        for (long t: bins.keySet()) {
            double r = bins.get(t);
            if (r > (sigma + mu)) {
                bursts.add(t);
            }
        }
        return bursts;
    }
    
    public void score(SearchHits hits) {
        // Ordered map of normalized timestamp to sum of document scores
        Map<Long, Double> bins = getTimeSeries(hits);
        List<Long> bursts = getBursts(bins);
        
        // Get the list of documents in bursts
        List<SearchHit> burstDocs = new LinkedList<SearchHit>();
        Iterator<SearchHit> it = hits.iterator();
        while (it.hasNext()) {
            SearchHit hit = it.next();
            long t = getDocTime(hit);
            if (bursts.contains(t))
                burstDocs.add(hit);
        }
        
        double N = hits.size();
        
        // p(B) = 1 / bursts.size()
        double pB = 1 / bursts.size();
        
        
        // p(w | q) = sum {b in bursts(D)} p(w|B)
        List<String> query = new ArrayList<String>();
        for (String term: query) {
            double pWQ = 0;
            for (long b: bursts) { 
                // p(w | B) = 1/N sum {d in D_B} p(D | B) p(w | D)       
                double pWB = 0;
                for (SearchHit doc: burstDocs) {
                    FeatureVector dv = doc.getFeatureVector();
                    // p(D | B) = 1 if D in D_B, 0 o.w.  -- implementation: 1 if document time is in list of burst times
                    double pDB = burstDocs.contains(doc.getDocno()) ? 1 : 0;
                    double pWD = dv.getFeatureWeight(term)/dv.getLength();
                    pWB += pDB * pWD;
                }
                pWB *= 1/N;
                
                pWQ += pWB;
            }
            // Now we have the probability of the word given the query
        }
        
        // P(w | D)
    }
}
