package edu.gslis.temporal.main;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

/**
 * 
 * @author cwillis
 */
public class DakkaHistogram {

    long minTime = 0;
    long maxTime = 0;
    long interval = 0;
    SimpleDateFormat df = null;
    
    public DakkaHistogram(long min, long max, long interval, String dateFormat) {
        this.minTime = min;
        this.maxTime = max;
        this.interval = interval;
        if (dateFormat != null) {
            this.df = new SimpleDateFormat(dateFormat);
        }
    }
    
    public Map<Long, Integer> getFrequencies(SearchHits hits) 
    {
        // Construct a histogram of daily document frequencies
        Map<Long, Integer> hist = new TreeMap<Long, Integer>();
        
        int numBins = (int)((maxTime - minTime)/interval);
        for (long i=0; i<numBins; i++) {
            hist.put(i, 0);
        }
        
        Iterator<SearchHit> it = hits.iterator();
        while (it.hasNext()) 
        {
            SearchHit hit = it.next();

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
            
            
            long t = (maxTime - docTime)/interval;
            int count = 0;
            if (hist.get(t) != null) 
                count = hist.get(t);
            count++;
            
            hist.put(t, count);
        }
        return hist;
    }
    
    /**
     * Assign day d to a bin based on the number of matching
     * documents for the query that were published in the same 
     * day t. bin[0] will contain the days with the highest
     * number of published documents.
     * @param hits
     * @return
     */
    public Map<Long, Integer> getDayBins(SearchHits hits) 
    {
        
        // Construct a histogram of daily document frequencies
        Map<Long, Integer> hist = getFrequencies(hits);
                
        // Sort bins by value
        ValueComparator vc =  new ValueComparator(hist);
        Map<Long,Integer> sorted = new TreeMap<Long,Integer>(vc);
        sorted.putAll(hist);
        
        int bin = 1;
        Map<Long, Integer> bins = new TreeMap<Long, Integer>();
        for (long key: sorted.keySet()) {
            bins.put(key,  bin);
            bin++;
        }
        
        return bins;
    }

    /**
     * Average daily frequency in larger interval of x consecutive days
     */
    public Map<Long, Integer> getFixedBins(SearchHits hits, int interval) 
    {
        
        Map<Long, Integer> freq = getFrequencies(hits);
        
        // Average the daily histories over the specified interval
        Map<Long, Integer> averaged = new HashMap<Long, Integer>();

        LinkedList<Integer> win = new LinkedList<Integer>();
        for (long t: freq.keySet()) {
            int count = freq.get(t);

            if (win.size() == interval)
                win.removeFirst();
            win.add(count);
            
            int avg = 0;
            for (int i=0; i<win.size(); i++)
               avg += win.get(i);
            avg /= win.size();
            
            averaged.put(t, avg);            
        }
        
        // Sort bins by value
        ValueComparator vc =  new ValueComparator(averaged);
        Map<Long,Integer> sorted = new TreeMap<Long,Integer>(vc);
        sorted.putAll(averaged);
        
        int bin = 0;
        Map<Long, Integer> bins = new TreeMap<Long, Integer>();
        for (long key: sorted.keySet()) {
            bins.put(key,  bin);
            bin++;
        }
        
        return bins;
    }
    
    /**
     * Average daily window using window of size x (before and after)
     */
    private final static int CACHE_MAX_SIZE = 100; 
    public Map<Long, Integer> getWinBins(SearchHits hits, int winSize) 
    {
        Map<Long, Integer> freq = getFrequencies(hits);
        
        // Average the daily histories over the specified window
        Map<Long, Integer> averaged = new HashMap<Long, Integer>();

        Window<Long, Integer> win = new Window<Long, Integer>(winSize*2);
        int k=1;
        for (long t: freq.keySet()) 
        {
            int count = freq.get(t);

            // Keep last winSize*2 counts
            win.put(t, count);
            
            if (k >= winSize) 
            {                
                // Calculate average for current - winSize
                long key = t - winSize + 1;
                int avg = 0;
                for (long w: win.keySet()) {
                    avg += win.get(w);
                }
                avg /= win.size();
                            
                averaged.put(key, avg); 
            }
            k++;
        }
        
        // Sort bins by value
        ValueComparator vc =  new ValueComparator(averaged);
        Map<Long,Integer> sorted = new TreeMap<Long,Integer>(vc);
        sorted.putAll(averaged);
        
        int bin = 0;
        Map<Long, Integer> bins = new TreeMap<Long, Integer>();
        for (long key: sorted.keySet()) {
            bins.put(key,  bin);
            bin++;
        }
        
        return bins;
    }

    public Map<Long, Integer> getRunningMeanBins(SearchHits hits) {
        // Construct a histogram of daily document frequencies
        Map<Long, Integer> hist = getFrequencies(hits);

        int sum = 0;
        int i = 0;
        for (long t: hist.keySet()) {
            int cnt = hist.get(t);
            sum +=  cnt;
            int avg = 0;
            if (i > 0) 
                avg = sum/i;
            
            hist.put(t, cnt - avg);
            i++;
        }
        
        
        // Sort bins by value
        ValueComparator vc =  new ValueComparator(hist);
        Map<Long,Integer> sorted = new TreeMap<Long,Integer>(vc);
        sorted.putAll(hist);
        
        int bin = 1;
        Map<Long, Integer> bins = new TreeMap<Long, Integer>();
        for (long key: sorted.keySet()) {
            bins.put(key, bin);
            bin++;
        }
        
        return bins;
    }

}
