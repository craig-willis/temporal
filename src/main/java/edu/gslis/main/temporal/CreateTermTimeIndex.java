package edu.gslis.main.temporal;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.indexes.temporal.TimeSeriesIndex;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Builds term-time index
 * 
 * ./run.sh edu.gslis.main.temporal.CreateTermTimeIndex 
 *      -index <path to index>
 *      -start <start time in seconds>
 *      -end <end time in seconds>
 *      -interval <interval in seconds>
 *      -output <path to output  file>
 *      -df <if true, use document freq, if false use term freq>
 */
public class CreateTermTimeIndex 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( CreateTermTimeIndex.class.getCanonicalName(), options );
            return;
        }
        String indexPath = cl.getOptionValue("index");
        long startTime = Long.parseLong(cl.getOptionValue("start"));
        long endTime = Long.parseLong(cl.getOptionValue("end"));
        long interval = Long.parseLong(cl.getOptionValue("interval"));
        boolean useDf = cl.hasOption("df");
        int minOccur = Integer.parseInt(cl.getOptionValue("minOccur", "0"));
        boolean smooth = cl.hasOption("smooth");
        
        
        RUtil rutil = new RUtil();
        
        /* Per term bin counts */
        Map<String, Map<Long, Double>> termTimeMap = new TreeMap<String, Map<Long, Double>>();
        
        /* Total counts for bin */
        Map<Long, Double> totalTimeMap = new TreeMap<Long, Double>();
        
        IndexWrapper index =  IndexWrapperFactory.getIndexWrapper(indexPath);
        int numDocs = (int)index.docCount();
        for (int docid=1; docid<numDocs; docid++) 
        {
            if (docid % 1000  == 0)
                System.err.println(docid + "...");
            
            String docno = index.getDocNo(docid);
            FeatureVector dv = index.getDocVector(docid, null);
            String epochStr = index.getMetadataValue(docno, Indexer.FIELD_EPOCH);
            long docTime = 0;
            try {
                docTime = Long.parseLong(epochStr);
            } catch (NumberFormatException e) {
                System.err.println("Problem parsing epoch for " + docid);
                continue;
            }
            
            if (docTime > endTime || docTime < startTime) {
            	System.err.println("Document " + docno + " has time outside of window");
            	continue;
            }
            long t = (docTime - startTime)/interval;
            
            double total = 0L;
            if (totalTimeMap.get(t) != null)
                total = totalTimeMap.get(t);
                
            Iterator<String> it = dv.iterator();
            while (it.hasNext()) {
                String f = it.next();
                double w = dv.getFeatureWeight(f);

                Map<Long, Double> timeMap = termTimeMap.get(f);
                if (timeMap == null)
                    timeMap = new TreeMap<Long, Double>();

                double freq = 0;
                if (timeMap.get(t) != null)
                    freq = timeMap.get(t);
                
                if (useDf) {
                    freq ++;
                    total ++;
                } else {                
                    freq += (long)w;
                    total += (long)w;                    
                }
                timeMap.put(t, freq);                
                termTimeMap.put(f, timeMap);                
            }
            totalTimeMap.put(t, total);
        }


        int numBins = 0;
        for (long time = startTime; time <= endTime; time+=interval)
            numBins ++;

        String output = cl.getOptionValue("output");

        System.err.println("Creating " + output + " with " + numBins + " bins");

        TimeSeriesIndex tsIndex = new TimeSeriesIndex();
        tsIndex.open(output, false);
            
        System.err.println("Calculating bin totals");
        double[] totals = new double[numBins];
        for (long time = startTime; time <= endTime; time+=interval) {
            long t = (time - startTime)/interval;
            double freq = 0;
            if (totalTimeMap.get(t) != null)
                freq = totalTimeMap.get(t);
            
            totals[(int)t] = freq;
        }        

        if (smooth)
        	totals = rutil.sma(totals, 3);
        
        tsIndex.add("_total_", totals);
        
        System.err.println("Adding terms");

        int j = 0;
        for (String term: termTimeMap.keySet()) {
            
            double[] freqs = new double[numBins];

            Map<Long, Double> timeMap = termTimeMap.get(term);
            for (long time = startTime; time <= endTime; time+=interval) {
                long t = (time - startTime)/interval;
                double freq = 0;
                if (timeMap.get(t) != null)
                    freq = timeMap.get(t);
                
                freqs[(int)t] = freq;
            }
            
            if (sum(freqs) > minOccur) {
            	
            	if (smooth) {
            		freqs = rutil.sma(freqs, 3);
            	}
            	tsIndex.add(term, freqs);
                if (j % 1000 == 0) 
                    System.err.println(j + "...");
                j++;
            }
        }
    }
        
        
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "Path to input index");
        options.addOption("start", true, "Start time");
        options.addOption("end", true, "End time");
        options.addOption("interval", true, "Interval");        
        options.addOption("format", true, "h2 or csv");        
        options.addOption("output", true, "Output time series index");        
        options.addOption("df", false, "If true, event is num docs. If false, event is num terms."); 
        options.addOption("minOccur", true, "Minimum occurrence"); 
        options.addOption("smooth", false, "Smooth ts");
        return options;
    }
   
    public static double sum(double[] d) {
    	double sum = 0;
    	if (d == null)
    		return 0;
    	for (double	 x: d)
    		sum += x;
    	return sum;
    }
}
