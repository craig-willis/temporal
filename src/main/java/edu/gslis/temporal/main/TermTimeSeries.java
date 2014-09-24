package edu.gslis.temporal.main;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;


public class TermTimeSeries 
{
    
    public static void main(String[] args) throws Exception 
    {
        
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( TermTimeSeries.class.getCanonicalName(), options );
            return;
        }
        
        String vocabFilePath = cl.getOptionValue("vocab", "");
        List<String> vocab = FileUtils.readLines(new File(vocabFilePath));

        String indexPath = cl.getOptionValue("index", "");
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
        
        // Format of date to use for binning
        String dateFormatStr = cl.getOptionValue("df", "yyMMdd");
        SimpleDateFormat df = new SimpleDateFormat(dateFormatStr);
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        

        // Format of date used in index (assumes long epoch)
        SimpleDateFormat indexDf = null;
        if (cl.hasOption("indexdf")) {
            String indexDateFormatStr = cl.getOptionValue("indexdf");
            indexDf = new SimpleDateFormat(indexDateFormatStr);
        }
        
        
        // Start date of collection 960101
        String startDateStr = cl.getOptionValue("start", "910415");
        // End date of collection 20001001
        String endDateStr = cl.getOptionValue("end", "941231");
        Date startDate = df.parse(startDateStr);
        Date endDate = df.parse(endDateStr);
        
        Calendar start = Calendar.getInstance();
        start.setTime(startDate);
        Calendar end = Calendar.getInstance();
        end.setTime(endDate);
        end.add(Calendar.DAY_OF_YEAR, 1);
        
        int i = 0;
        String outputPath = cl.getOptionValue("output", "");
        FileWriter writer = new FileWriter(outputPath);
        
        Map<String, Integer> bins = new HashMap<String, Integer>();
        for (Date date = start.getTime(); !start.after(end); start.add(Calendar.DAY_OF_YEAR, 1), date = start.getTime()) 
        {
            String dateStr = df.format(date);
            bins.put(dateStr, i);
            System.out.println(i + "\t" + dateStr);
            i++;
        }        
        
        Map<Integer, Integer> cfmap = new HashMap<Integer, Integer>();
        if (cl.hasOption("cf")) {
            String cfPath = cl.getOptionValue("cf");
            List<String> cflist = FileUtils.readLines(new File(cfPath));
            for (String row: cflist) {
                String[] fields = row.split(",");
                String dateStr = df.format(new Date(Long.valueOf(fields[0])*1000));
                System.out.println(fields[0] + "," + dateStr);
                if (bins.get(dateStr) == null) {
                    continue;
                }
                int b = bins.get(dateStr);
                int f = Integer.parseInt(fields[1]);
                cfmap.put(b, f);
            }            
        }
        
        for (String term: vocab) {
    
            Map<Integer, Double> timeSeries = new TreeMap<Integer, Double>();
            for (int j=0; j<i; j++) 
                timeSeries.put(j, 0D);
            
            //String constraint = index.toAndQuery(term, null);
            SearchHits hits = index.runQuery(term, (int)index.docCount()); 
            Iterator<SearchHit> it = hits.iterator();
            System.out.println(term + ": " + hits.size());

            while (it.hasNext())
            {
                SearchHit hit = it.next();
                String epochStr = index.getMetadataValue(hit.getDocno(), Indexer.FIELD_EPOCH);
                
                String dateStr = "";
                if (indexDf != null) {
                    Date date = indexDf.parse(epochStr);
                    dateStr = df.format(date);
                } else {
                    long epoch = Long.valueOf(epochStr);
                    dateStr = df.format(new Date(epoch*1000));
                }

                if (bins.get(dateStr) == null) {
                    System.out.println("Strange: " + epochStr + "," + dateStr);
                    continue;
                }
                int bin = bins.get(dateStr);
                double freq = timeSeries.get(bin);
                if (cl.hasOption("cf")) {               
                    if (cfmap.get(bin) == null) {
                        System.out.println("Error: no such bin " + bin);
                        continue;
                    }
                    int cf = cfmap.get(bin);
                    freq += 1/(double)cf;
                }
                else
                    freq ++;
                
                timeSeries.put(bin, freq);                    
            }

            /*
            Map<Integer, Integer> docs = index.getDocsByTerm(term);
            System.out.println(term + ": " + docs.size());
            for (int docid: docs.keySet()) {
                String docno = index.getDocNo(docid);
                String epochStr = index.getMetadataValue(docno, Indexer.FIELD_EPOCH);
                if (bins.get(epochStr) == null) {
                    System.out.println("Warning: no bin for " + epochStr);
                    continue;
                }
                int bin = bins.get(epochStr);
                int freq = timeSeries.get(bin);
                freq += docs.get(docid);
                timeSeries.put(bin, freq);
            }
            */
            
            for (int j=0; j<i; j++) {
                writer.write(term + "," + j + "," + timeSeries.get(j) + "\n");
            }
            writer.flush();
        }
        writer.close();
    }
    
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("df", true, "Date format string");
        options.addOption("index", true, "Path to index");
        options.addOption("vocab", true, "Path to vocab");
        options.addOption("start", true, "Start date");
        options.addOption("output", true, "Output path");
        options.addOption("end", true, "End date");
        options.addOption("cf", true, "Collection frequencies");
        options.addOption("help", false, "Print this help message");
        
        return options;
    }
}
