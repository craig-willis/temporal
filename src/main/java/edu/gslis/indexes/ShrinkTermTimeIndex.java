package edu.gslis.indexes;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * 
 * ./run.sh edu.gslis.temporal.main.ShrinkTermTimeIndex 
 *      -input <path to input csv>
 *      -output <path to output csv>
 */
public class ShrinkTermTimeIndex 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( ShrinkTermTimeIndex.class.getCanonicalName(), options );
            return;
        }
        String input = cl.getOptionValue("input");
        String output = cl.getOptionValue("output");


        TimeSeriesIndex inputIndex = new TimeSeriesIndex();
        inputIndex.open(input, true, "csv");
        inputIndex.shrink(output);

    }
        
        
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("input", true, "Path to input csv");
        options.addOption("output", true, "Output time series index");        
        return options;
    }

}