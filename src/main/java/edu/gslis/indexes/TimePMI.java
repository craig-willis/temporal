package edu.gslis.indexes;

import java.io.FileWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;

/**
 * 
 * ./run.sh edu.gslis.temporal.main.ShrinkTermTimeIndex 
 *      -input <path to input csv>
 *      -output <path to output csv>
 */
public class TimePMI 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( TimePMI.class.getCanonicalName(), options );
            return;
        }
        String input = cl.getOptionValue("index");
        String queryFilePath = cl.getOptionValue("queries");
        String outputPath = cl.getOptionValue("output");
        String method = cl.getOptionValue("method");
        double thresh = Double.parseDouble(cl.getOptionValue("thresh"));

        FileWriter out = new FileWriter(outputPath);

        TimeSeriesIndex tsindex = new TimeSeriesIndex();
        tsindex.open(input, true, "csv");

        GQueries queries = null;
        if (queryFilePath.endsWith("indri")) 
            queries = new GQueriesIndriImpl();
        else
            queries = new GQueriesJsonImpl();
        
        queries.read(queryFilePath);
        Iterator<GQuery> queryIterator = queries.iterator();
        Set<String> vocab = new HashSet<String>();
        while(queryIterator.hasNext()) 
        {                            
            GQuery query = queryIterator.next();
            String text = query.getText();
            String[] terms = text.split(" ");
            for (String term: terms)
                vocab.add(term);
        }
        
        if (method.equals("chisq"))
        {
            Map<String, Double> sigMap = new HashMap<String, Double>();
            for (String term: vocab) {
                double[] counts = tsindex.getChiSq(term, thresh);
    
                int sig = 0;
                double avgCsq = 0;
                for (int bin=0; bin<counts.length; bin++) {
                    double csq = counts[bin];
                    out.write(term + "," + bin + "," + csq + "\n");
                    
                    avgCsq += csq;
                    if (csq > 10.828)
                        sig++;
                }
                avgCsq /= counts.length;
                sigMap.put(term, avgCsq);
            }
        }    
        else if (method.equals("npmi")) {
        
            double[] totals = tsindex.get("_total_");
            double N = 0;
            for (double t: totals)
                N += t;
            
    
            for (String term: vocab) {
                
                if (tsindex.get(term) != null) 
                {
                    double[] counts = tsindex.get(term);
        
                    double sum = 0;
                    for (double c: counts) 
                        sum+= c;
                    
                    for (int bin=0; bin<totals.length; bin++) {
                        //double mi = calcMi(N, (double)counts[bin], sum, (double)totals[bin]);
                        double npmi = calcNpmi(N, (double)counts[bin], sum, (double)totals[bin]);
                        if (totals[bin] == 0)
                            npmi = 0;
                        out.write(term + "," + bin + "," + npmi + "\n");

                    }
                } else {
                    System.err.println("Warning: " + term + " has no data");
                }
            }    
        }
        out.close();
    }
    
    private static double calcMi(double N, double nX1Y1, double nX1, double nY1)
    {
        
        //       | time  | ~time  |
        // ------|-------|--------|------
        //  word | nX1Y1 | nX1Y0  | nX1
        // ~word | nX0Y1 | nX0Y0  | nX0
        // ------|-------|--------|------
        //       |  nY1  |  nY0   | N

        // Marginal and joint frequencies
        double nX0 = N - nX1;
        double nY0 = N - nY1;       
        double nX1Y0 = nX1 - nX1Y1;
        double nX0Y1 = nY1 - nX1Y1;
        double nX0Y0 = nX0 - nX0Y1;

        // Marginal probabilities (smoothed)
        double pX1 = (nX1 + 0.5)/(1+N);
        double pX0 = (nX0 + 0.5)/(1+N);         
        double pY1 = (nY1 + 0.5)/(1+N);
        double pY0 = (nY0 + 0.5)/(1+N);
        
        // Joint probabilities (smoothed)
        double pX1Y1 = (nX1Y1 + 0.25) / (1+N);
        double pX1Y0 = (nX1Y0 + 0.25) / (1+N);
        double pX0Y1 = (nX0Y1 + 0.25) / (1+N);
        double pX0Y0 = (nX0Y0 + 0.25) / (1+N);
        
        double mi =  
                pX1Y1 * log2(pX1Y1, pX1*pY1) + 
                pX1Y0 * log2(pX1Y0, pX1*pY0) +
                pX0Y1 * log2(pX0Y1, pX0*pY1) +
                pX0Y0 * log2(pX0Y0, pX0*pY0);
        
        return mi;
    }
        
    private static double calcNpmi(double N, double nX1Y1, double nX1, double nY1)
    {
        
        //       | time  | ~time  |
        // ------|-------|--------|------
        //  word | nX1Y1 | nX1Y0  | nX1
        // ~word | nX0Y1 | nX0Y0  | nX0
        // ------|-------|--------|------
        //       |  nY1  |  nY0   | N

        // Marginal probabilities (smoothed)
        double pX1 = (nX1 + 0.5)/(1+N);
        double pY1 = (nY1 + 0.5)/(1+N);
        
        // Joint probabilities (smoothed)
        double pX1Y1 = (nX1Y1 + 0.25) / (1+N);
        
        // Ala http://www.aclweb.org/anthology/W13-0102
        double pmi = log2(pX1Y1, pX1*pY1);
        double npmi = pmi / -(Math.log(pX1Y1)/Math.log(2));
        
        return npmi;
    }
    
    private static double log2(double num, double denom) {
        if (num == 0 || denom == 0)
            return 0;
        else
            return Math.log(num/denom)/Math.log(2);
    }
        
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "Path to input csv");
        options.addOption("queries", true, "Path to queries");        
        options.addOption("output", true, "Path to ouput");        
        options.addOption("method", true, "Method: npmi or chisq");        
        options.addOption("thresh", true, "threshold");        

        return options;
    }

}
