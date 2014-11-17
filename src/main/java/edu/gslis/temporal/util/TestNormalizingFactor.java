    package edu.gslis.temporal.util;

import java.io.BufferedReader;
import java.io.FileReader;

import umontreal.iro.lecuyer.gof.KernelDensity;
import umontreal.iro.lecuyer.probdist.EmpiricalDist;
import umontreal.iro.lecuyer.probdist.NormalDist;

/**
 */
public class TestNormalizingFactor 
{
    public static void main(String[] args) throws Exception 
    {
        BufferedReader br = new BufferedReader(new FileReader(args[0]));
        String line;
        while ((line = br.readLine()) != null)
        {            
            String[] fields = line.replaceAll("\"", "").split(",");
            String term = fields[0];
            if (term.equals("TERM") || term.equals("_total_"))
                continue;
            
            // Get the histogram for this term 
            double[] hist = new double[fields.length-1];
            int total =0;
            for (int i=0; i<fields.length-1; i++) {
                hist[i] = Double.valueOf(fields[i+1]);
                total += hist[i];
            }

//            System.out.println(line);
            double[] avg =movingAverage(hist, 3);
            /*for (int i=0; i<avg.length; i++) {
                System.out.print("," + avg[i]);
            }
            */
            
            double sum = 0;
            for (int i=0; i<avg.length; i++)
                sum+=avg[i];
            System.out.println(term + "," + sum);
    
            /*
            // Replicate the histogram into x (required by SSJ)
            double[] x = new double[total];
            int l=0;
            for (int bin=0; bin<hist.length; bin++) {
                double freq = hist[bin];
                for (int k=0; k<freq; k++) {
                    x[l++] = bin;
                }
            }

            int i = 200;

            if (x.length > 2) {
                EmpiricalDist ed = new EmpiricalDist(x);
                double[] density = KernelDensity.computeDensity(ed, new NormalDist(), hist);
                
                double sum=0;
                for (double d: density)
                    sum+= d;

                double d = density[i];

                System.out.println(i + "," + term + "," + d + "," + sum + "," + d/sum);
                for (int i=0; i<hist.length; i++) {
                    double d = density[i];
                                            
                    System.out.println(i + "," + term + "," + d + "," + sum);
                } 
            } else {
                for (int i=0; i<hist.length; i++) {
                    System.out.println(i + "," + term + "," + 0 + "," + 0);
                }     
                System.out.println(i + "," + term + "," + 0 + "," + 0);

            }
            */
        }
        br.close();
    }
    
    
    private static double[] movingAverage(double[] hist, int winSize) {
        double[] avg = new double[hist.length];
        for (int j=0; j<hist.length; j++) 
        {

            if (j < hist.length)
            {           
                double freq = hist[j];

                int n = 1;
                
                for (int i=0; i < winSize; i++) {
                    if (j > i) {
                        freq += hist[j - i];
                        n++;
                    }
                    if (j < hist.length - i) {
                        freq += hist[j + i];
                        n++;
                    }
                }

                // Average freq at time j
                avg[j] = freq/(double)n;
            }   
        }        
        return avg;
    }
}
