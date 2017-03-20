    package edu.gslis.indexes.temporal;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Interface to an underlying file containing term time series information for a collection.
 */
public class TimeSeriesIndex {

    int numBins = -1;
    FileWriter writer = null;
    Map<String, double[]> timeSeriesMap = new HashMap<String, double[]>();
    boolean readOnly = false;

    public void open(String path, boolean readOnly) 
            throws IOException
    {
        
        if (readOnly) {
            load(path);
        } else {
            writer = new FileWriter(path);
        }
    }
    
    public void load(String path) throws IOException {
        BufferedReader br = new BufferedReader(
                new InputStreamReader(new GZIPInputStream(new FileInputStream(path)), "UTF-8"));
        String line;
        int j=0;
        System.err.println("Loading " + path);

        while ((line = br.readLine()) != null) {
            if (j%10000 == 0) 
                System.err.print(".");
            if (j%100000 == 0) 
                System.err.print(j + "\n");

            String[] fields = line.split(",");
            String term = fields[0];
            double[] counts = new double[fields.length-1];
            for (int i=1; i<fields.length; i++) {
                counts[i-1] = Double.valueOf(fields[i]);
            }
            
            	timeSeriesMap.put(term, counts);
            j++;
        }
        System.err.println("Done");
                
        br.close();
    }
    
    

    public void add(String term, long[] counts) throws IOException {
        if (!readOnly) {
            writer.write(term);
            for (long count: counts) {
                writer.write("," + count);
            }
            writer.write("\n");
        }
    }
    
    public void close() throws IOException 
    {
    	writer.close();
    }
    
    public List<String> terms()
    {
        List<String> terms = new ArrayList<String>();

        terms.addAll(timeSeriesMap.keySet());
        
        return terms;
    }
    public double[] get(String term) 
    {
        return timeSeriesMap.get(term);
    }
    
    public double[] getDist(String term) {
		double[] tsw = timeSeriesMap.get(term);
		if (tsw == null)
			return null;
		
		double sum = sum(tsw);
		for (int i=0; i<tsw.length; i++) 
			tsw[i] = tsw[i]/sum;
		return tsw;
    }
    
    public double sum(double[] d) {
    	double sum = 0;
    	if (d == null)
    		return 0;
    	for (double x: d)
    		sum += x;
    	return sum;
    }
    
    
    public double get(String term, int bin)
    {
        double freq = 0;

        double[] counts = timeSeriesMap.get(term);
        if (counts != null && counts.length == getNumBins())
            freq = counts[bin];
        return freq;
    }
    
    public int getNumBins() {
        if (numBins == -1)
            numBins = timeSeriesMap.get("_total_").length;

        return numBins;
    }
          
    
    public double getLength(int bin) throws Exception {
        return get("_total_", bin);
    }
    


}
