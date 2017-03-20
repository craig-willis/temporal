package edu.gslis.main.temporal;

import java.io.FileInputStream;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.io.IOUtils;

import edu.gslis.indexes.temporal.TimeSeriesIndex;
import edu.gslis.temporal.util.RUtil;


/**
 * calculate per-term statistics for the vocabulary
 *
 */
public class CalculateTermStats 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( CalculateTermStats.class.getCanonicalName(), options );
            return;
        }
        String tsIndexPath = cl.getOptionValue("tsindex");
        TimeSeriesIndex tsIndex = new TimeSeriesIndex();
        tsIndex.load(tsIndexPath);
        RUtil rutil = new RUtil();
        
        String termsPath = cl.getOptionValue("terms");
        List<String> terms = tsIndex.terms();
        if (termsPath != null) {
        	terms = IOUtils.readLines(new FileInputStream(termsPath));
        }
        
        double[] background = tsIndex.get("_total_");
        
        System.out.println("term,acf1,acf2,pacf1,pacf2,dp,dps,sacf,tkl,freq");
        for (String term: terms) {
        	
        	double[] ts = tsIndex.get(term);

        	double freq = sum2(ts);
        	double acf1 = rutil.acf(ts, 1);
        	double acf2 = rutil.acf(ts, 2);
        	double pacf1 = rutil.pacf(ts, 1);
        	double pacf2 = rutil.pacf(ts, 2);
        	double dp = rutil.dp(ts);
        	double dps = rutil.dps(ts);
        	double sacf = rutil.sma_acf(ts, 2, 3);
        	
        	double tkl = 0;
            for (int i=0; i<ts.length; i++) {
            	if (ts[i] >0 && background[i] > 0)
            		tkl += ts[i] * Math.log(ts[i]/background[i]);
            }
                        
        	System.out.println(term + "," + acf1 + "," + acf2 + "," + pacf1 + "," + pacf2 + "," + dp + "," + dps + "," + sacf + "," + tkl + "," + freq);
        }
    }
        
        
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("tsindex", true, "Path to ts index");
        options.addOption("terms", true, "Path to term list");
        return options;
    }
   
    public static double sum2(double[] d) {
    	double sum = 0;
    	if (d == null)
    		return 0;
    	for (double	 x: d)
    		sum += x;
    	return sum;
    }
    
    public static double sum(long[] d) {
    	long sum = 0;
    	if (d == null)
    		return 0;
    	for (long	 x: d)
    		sum += x;
    	return sum;
    }
}
