package edu.gslis.indexes;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import edu.gslis.textrepresentation.FeatureVector;

/**
 * 
 * Output top n terms for each temporal bin.
 */
public class DumpTemporalBins 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( DumpTemporalBins.class.getCanonicalName(), options );
            return;
        }
        String tsIndexPath = cl.getOptionValue("input");
        int numTerms = Integer.parseInt(cl.getOptionValue("numTerms", "10"));
        long startTime = Long.parseLong(cl.getOptionValue("startTime"));
        long interval = Long.parseLong(cl.getOptionValue("interval"));
        

        TimeSeriesIndex tsIndex = new TimeSeriesIndex();
        tsIndex.open(tsIndexPath, true, "csv");
        
        
        int numBins = tsIndex.getNumBins();
        List<String> vocab = tsIndex.terms();

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        for (int i=0; i<numBins; i++) {
            long ms = (startTime + i*interval) * 1000;
            String date = df.format(ms);
            
            FeatureVector fv = new FeatureVector(null);

            for (String term: vocab) {
                if (term.equals("_total_")) 
                    continue;
                double w = tsIndex.get(term, i);
                fv.addTerm(term, w);                
            }            
            System.out.println(i + " - " + date);
            System.out.println(fv.toString(numTerms));
        }
    }
        
        
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("input", true, "Path to input csv");
        options.addOption("numTerms", true, "Number of terms to output");
        options.addOption("startTime", true, "Start time of temporal index");
        options.addOption("interval", true, "Interval used in temporal index construction");
        return options;
    }

}
