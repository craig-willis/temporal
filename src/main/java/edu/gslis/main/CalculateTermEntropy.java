package edu.gslis.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.Iterator;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import edu.gslis.indexes.temporal.TimeSeriesIndex;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.scorers.temporal.TemporalScorer;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Calculate per-term entropy
 */
public class CalculateTermEntropy 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( CalculateTermEntropy.class.getCanonicalName(), options );
            return;
        }
        String tsIndexPath = cl.getOptionValue("tsindex");
        
        TimeSeriesIndex tsIndex = new TimeSeriesIndex();
    	tsIndex.open(tsIndexPath, true);       
                

        RUtil rutil = new RUtil();
        
        System.out.println("term,sum,acf1,acf2,entropy,min0,min1,min2");
        for (String term: tsIndex.terms()) {
        	double[] tsw = tsIndex.get(term);
            if (tsw == null) {
            	System.err.println("Unexpected null termts for " + term);
            	continue;
            }

            double acf1 = 0;
            double acf2 = 0;
            double entropy = 0;
            double sum = tsIndex.sum(tsw);
            //System.out.println(sum);
        	if (sum > 1000) {
        		
        		double[] pw = new double[tsw.length];
        		for (int i = 0; i < tsw.length; i++) {
        			pw[i] = tsw[i] / sum;
        		}
        		
	        	try {        		
	        		acf1 = rutil.acf(pw,1);	
	        		acf2 = rutil.acf(pw,2);	
	        	} catch (Exception e) {
	        		e.printStackTrace();        		
	        	}
	        	
				for (int i = 0; i < pw.length; i++) {
					if (pw[i] > 0)
						entropy += - pw[i] * Math.log(pw[i]);
				}	
				
				double[] minfo = minfo(pw);
	        	System.out.println(term + "," + sum + "," + acf1 + "," + acf2 + "," + entropy + "," + minfo[0] 
	        			+ "," + minfo[1] + "," + minfo[2]);
        	}        	

        }
        
    }
    
	public static double[] minfo(double[] data) throws Exception
	{
		File tmp = File.createTempFile("minfo", "dat");
		FileWriter writer = new FileWriter(tmp);
		for (double x: data) {
			writer.write(x + "\n");
		}
		writer.close();
		
		String command = "minfo " + tmp.getAbsolutePath() + " | sort -u -n";
		Runtime run = Runtime.getRuntime();
		Process pr = run.exec(command) ;
		pr.waitFor() ;
		BufferedReader buffer = new BufferedReader( new InputStreamReader( pr.getInputStream() ) ) ;
		String line;
		double[] mi = new double[20];
		buffer.readLine(); // Skip header
		int i=0;
		while ( ( line = buffer.readLine() ) != null ) 
		{
			String[] vals = line.split(" ");			
			mi[i] = Double.parseDouble(vals[1]);
			i++;
		}
		tmp.delete();
		return mi;
	}
	
        

    
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("tsindex", true, "Path to input index");
        return options;
    }

}
