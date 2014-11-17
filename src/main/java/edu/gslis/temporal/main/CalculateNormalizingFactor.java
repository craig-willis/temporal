package edu.gslis.temporal.main;

import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import umontreal.iro.lecuyer.gof.KernelDensity;
import umontreal.iro.lecuyer.probdist.EmpiricalDist;
import umontreal.iro.lecuyer.probdist.NormalDist;
import edu.gslis.temporal.scorers.TimeSeriesIndex;

/**
 */
public class CalculateNormalizingFactor 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( CalculateNormalizingFactor.class.getCanonicalName(), options );
            return;
        }
        String indexPath = cl.getOptionValue("index");
        String type = cl.getOptionValue("type");
        
        TimeSeriesIndex tsIndex = new TimeSeriesIndex();
        
        // Verify
        if (cl.hasOption("bin")) {
            int bin = Integer.valueOf(cl.getOptionValue("bin"));
            
            tsIndex.open(indexPath, true);

            double z = tsIndex.getNorm(type, bin);
            System.out.println("z=" + z);

            List<String> terms = tsIndex.terms();
            double sum = 0;
            for (String term: terms) {
                double[] hist = tsIndex.get(term); 
                
                                
                if (type.equals("kd")) {
                    // Get the histogram for this term 
                    int total =0;
                    for (int i=0; i<hist.length; i++)
                        total += hist[i];

                    // Replicate the histogram into x (required by SSJ)
                    double[] x = new double[total];
                    int l=0;
                    for (int i=0; i<hist.length; i++) {
                        double freq = hist[i];
                        for (int k=0; k<freq; k++)
                            x[l++] = i;
                    }
                    
                    if (x.length > 2) {
                        EmpiricalDist ed = new EmpiricalDist(x);
                        double[] density = KernelDensity.computeDensity(ed, new NormalDist(), hist);
                        
                        double s=0;
                        for (double d: density)
                            s+= d;
                        
                        double d = density[bin];
                        
                        double pt = 0;
                        if (!Double.isNaN(d) && !Double.isInfinite(d))
                            pt = d/s;
                        System.out.println(term + "\t" + pt);

                    }    
                }
                else {
                        
                    double nw = 0;
                    for (double s: hist)
                        nw += s;
                    double ptw = 0;
                    if (nw > 0 && z > 0)
                        ptw = (hist[bin]/nw) * (1/z);
                    System.out.println(term + "\t" + ptw);
                    sum += ptw;
                }
            }
            System.out.println(sum);


        }
        else 
        {
            String outputPath = cl.getOptionValue("output");
            FileWriter output = new FileWriter(new File(outputPath));
            tsIndex.open(indexPath, true);
            //tsIndex.initNorm(type);
            
            double[] totals = tsIndex.get("_total_");
    
            List<String> terms = tsIndex.terms();
            System.out.println("Calculating normalizing factor for " + 
                    terms.size() + " terms and " + totals.length + " bins");

            for (int i=0; i<totals.length; i++) {                
                System.err.println("Bin " + i);
                double norm = 0;
                
                double[] y = new double[1];
                y[0] = i;

                Map<String, double[]> map = new HashMap<String, double[]>();
                for (String term: terms) {
                    map.put(term,  tsIndex.get(term));
                }

                for (String term: terms) {
                    
                    if (term.equals("_total_")) 
                        continue;                    
                    
//                    double[] hist = tsIndex.get(term);  
                    double[] hist = map.get(term);
                    int total = 0;
                    for (double h: hist)
                        total += (int)h;
        
                    if (type.equals("kd")) {
                        // Compute the density for p(T|w) for all w.
                        
                        // replicate the histogram into x
                        double[] x = new double[total];
                        int l=0;
                        for (int bin=0; bin<hist.length; bin++) {
                            double freq = hist[bin];
                            for (int k=0; k<freq; k++) {
                                x[l++] = bin;
                            }
                        }
                        
                        if (x.length > 2) {
                            EmpiricalDist ed = new EmpiricalDist(x);
                            double[] pr = KernelDensity.computeDensity(ed, new NormalDist(), y);
                            if (!Double.isNaN(pr[0]) && !Double.isInfinite(pr[0])) 
                                norm += pr[0];
                        }                        
                            
                    }
                    else {

                        if (total > 0)                    
                            norm += hist[i]/total;                        
                    }
                }
            
                output.write(i + "," + norm + "\n");
                output.flush();
                //tsIndex.addNorm(type, i,  norm);
            }
            //tsIndex.indexNorm(type);
            output.close();
        }
        tsIndex.close();
    }
        
        
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "Path to input time series index");
        options.addOption("bin", true, "For testing, time");
        options.addOption("type", true, "tf, kd");
        options.addOption("output", true, "Output csv file");
        return options;
    }

}
