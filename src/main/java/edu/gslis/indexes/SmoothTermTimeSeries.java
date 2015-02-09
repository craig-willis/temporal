package edu.gslis.indexes;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

/**
 * 
 * ./run.sh edu.gslis.temporal.main.ShrinkTermTimeIndex 
 *      -input <path to input csv>
 *      -output <path to output csv>
 */
public class SmoothTermTimeSeries 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( SmoothTermTimeSeries.class.getCanonicalName(), options );
            return;
        }
        String input = cl.getOptionValue("input");
        String output = cl.getOptionValue("output");
        int win = Integer.parseInt(cl.getOptionValue("win", "3"));

        TimeSeriesIndex inputIndex = new TimeSeriesIndex();
        inputIndex.open(input, true, "csv");
        inputIndex.smoothMovingAverage(output, win);

    }
        
        
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("input", true, "Path to input csv");
        options.addOption("output", true, "Output time series index");
        options.addOption("win", true, "window size");
        return options;
    }

}
