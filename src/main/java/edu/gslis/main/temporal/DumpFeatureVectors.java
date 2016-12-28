package edu.gslis.main.temporal;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import edu.gslis.indexes.temporal.TimeSeriesIndex;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Dump the top n terms for each temporal model
 */
public class DumpFeatureVectors 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( DumpFeatureVectors.class.getCanonicalName(), options );
            return;
        }
        String index = cl.getOptionValue("index");
        int numTerms = Integer.parseInt(cl.getOptionValue("numTerms"));

        TimeSeriesIndex tsIndex = new TimeSeriesIndex();
        tsIndex.open(index, true);
        
        Map<Integer, FeatureVector> tms = new TreeMap<Integer, FeatureVector>();
        
        double[] total = tsIndex.get("_total_");
        // Totals for each bin
        for (String term: tsIndex.terms()) {
            double[] series = tsIndex.get(term); 
            if (series != null) {
                // Populate feature vector for each bin
                for (int i=0; i<series.length; i++) {
                    FeatureVector tm = tms.get(i);
                    if (tm == null)
                        tm = new FeatureVector(null);
                    
                    if (total[i] != 0) 
                        tm.addTerm(term, series[i]/total[i]);                    
                    tms.put(i, tm);
                }
            }
        }
        
        for (int bin: tms.keySet()) {
            FeatureVector fv = tms.get(bin);
            fv.clip(10);
            System.out.println(bin);
            System.out.println(fv + "\n\n");
        }

    }
        
        
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "Path to input csv");
        options.addOption("numTerms", true, "Number of terms");
        return options;
    }

}
