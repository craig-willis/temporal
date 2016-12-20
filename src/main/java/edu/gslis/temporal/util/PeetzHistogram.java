package edu.gslis.temporal.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import edu.gslis.scorers.temporal.TemporalScorer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;



public class PeetzHistogram {
	
    double mean = 0;
    double sd = 0;
    long startTime = 0;
    long interval = 0;
    
    SearchHits hits = null;
    
	Map<Integer, Double> bins = new TreeMap<Integer, Double>();
	
	public PeetzHistogram(SearchHits hits, long startTime, long endTime, long interval) 
	{ 
	    this.hits = hits;
	    this.startTime = startTime;
	    this.interval = interval;
	    
	    int numBins = (int) ((endTime-startTime)/interval);
	    for (int i=0; i<numBins; i++) {
	        bins.put(i, 0.0);
	    }
	    Iterator<SearchHit> it = hits.iterator();
	    while (it.hasNext()) {
	        SearchHit hit = it.next();
	        long docTime = TemporalScorer.getTime(hit);
	        int bin = (int)((docTime - startTime) / interval);
	        
	        double score = 0;
	        if (bins.get(bin) != null)
	            score = bins.get(bin);
	        
	        score += Math.exp(hit.getScore());
	        bins.put(bin, score);
	    }
	    
	    DescriptiveStatistics ds = new DescriptiveStatistics();
        for (int bin: bins.keySet()) {
            double score = bins.get(bin);
            //System.out.println(bin + "," + score);
            ds.addValue(score);
        }
       

        mean = ds.getMean();
        sd = ds.getStandardDeviation();
	}
	
	public SearchHits getBurstDocs() {
	    SearchHits burstDocs = new SearchHits();
	    
        // Find bursts
        // 1) find peaked bins with t(i) > mu + 2*sd
        // 2) for each peak, find bins with t(i) > mu + 1*sd

	    // Find peaks
	    List<Integer> peaks = new ArrayList<Integer>();
	    for (int bin: bins.keySet()) {
	        if (bins.get(bin) > mean + 2*sd )
	            peaks.add(bin);
	    }
	    
	    Set<Integer> bursts = new TreeSet<Integer>();
	    for (int peak: peaks) {
	        	      
	        bursts.add(peak);
	        
	        /*
	        for (int i = peak + 1; i < bins.size(); i++) {
	            if (bins.get(i) == null)
                    continue;

                double score = bins.get(i);
                if (score > (mean + 1*sd))
	                bursts.add(i);	                
	            else
	                break;
	        }
	        for (int i = peak - 1; i > 0; i--) {
	            
	            if (bins.get(i) == null)
	                continue;
	            double score = bins.get(i);
	            if (score > (mean + 1*sd))
	                bursts.add(i);       
	            else
	                break;	        
	                  
	        }
	        */
	    }

	    Iterator<SearchHit> it = hits.iterator();
        while (it.hasNext()) {
            SearchHit hit = it.next();
            long docTime = TemporalScorer.getTime(hit);
            int bin = (int)((docTime - startTime) / interval);
            if (bursts.contains(bin)) 
                burstDocs.add(hit);
        }
        return burstDocs;
	}
	

    public double mean() {       
        return mean;
    }
	
	public double sd() {
        return sd;
	}

	public double value(int bin) {
	    return bins.get(bin);
	}
}
