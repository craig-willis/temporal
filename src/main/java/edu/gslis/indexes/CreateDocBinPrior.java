package edu.gslis.indexes;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.textrepresentation.FeatureVector;


public class CreateDocBinPrior 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( CreateDocBinPrior.class.getCanonicalName(), options );
            return;
        }
        String indexPath = cl.getOptionValue("index");
        String tsindexPath = cl.getOptionValue("tsindex");
        long startTime = Long.parseLong(cl.getOptionValue("start"));
        long interval = Long.parseLong(cl.getOptionValue("interval"));
        
        
        Map<Integer, List<Integer>> binDocMap = new TreeMap<Integer, List<Integer>>();
        IndexWrapper index =  IndexWrapperFactory.getIndexWrapper(indexPath);
        
        int numDocs = (int)index.docCount();
        for (int docid=1; docid<numDocs; docid++) 
        {
            if (docid % 1000  == 0)
                System.err.println(docid + "...");
            
            long docTime = 0;
            String docno = index.getDocNo(docid);
            String epochStr = index.getMetadataValue(docno, Indexer.FIELD_EPOCH);

            try {
                docTime = Long.parseLong(epochStr);
            } catch (NumberFormatException e) {
                System.err.println("Problem parsing epoch for " + docid);
                continue;
            }
            int bin = (int)((docTime - startTime)/interval);
            
            List<Integer> docids = binDocMap.get(bin);
            if (docids == null)
                docids = new ArrayList<Integer>();
            
            docids.add(docid);
            binDocMap.put(bin, docids);
        }
        
        TimeSeriesIndex tsIndex = new TimeSeriesIndex();
        tsIndex.open(tsindexPath, true, "csv");
        
        String outputPath = cl.getOptionValue("output");
        FileWriter output = new FileWriter(outputPath);

        Map<Integer, Double> priors = new TreeMap<Integer, Double>();
        for(int bin: binDocMap.keySet()) {
            List<Integer> docids = binDocMap.get(bin);
            double z= 0;
            for (int docid: docids) {
                FeatureVector dv = index.getDocVector(docid, null);
                // Score document w.r.t. bin
                double ll = score(tsIndex, dv, bin, index);
                priors.put(docid, ll);
                z+= ll;
            }          
            for (int docid: docids) {
                double prior = priors.get(docid);
                priors.put(docid, prior/z);
            }            
        }        
        
        for (int docid: priors.keySet()) {
            double prior = priors.get(docid);
            String docno = index.getDocNo(docid);

            output.write(docno + "," + prior + "\n");
        }
        output.close();
    }
    

    public static double score(TimeSeriesIndex tsIndex, FeatureVector fv, int bin, IndexWrapper index) 
    {
        
        double score = 0.0;


        try {

            Iterator<String> it = fv.iterator();
            while(it.hasNext()) 
            {
                String feature = it.next();
                
                double weight = fv.getFeatureWeight(feature);
    
                double tfreq = tsIndex.get(feature, bin);
                double tlen = tsIndex.getLength(bin);
                
                double pr = (1 + tfreq) / (tlen + index.termCount());
                
                score += weight * Math.log(pr);
                
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return score;
    }
        
        
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("tsindex", true, "Path to input tsindex");
        options.addOption("index", true, "Path to input index");
        options.addOption("start", true, "Start time");
        options.addOption("interval", true, "Interval");        
        options.addOption("output", true, "Output time series index");        
        return options;
    }

}
