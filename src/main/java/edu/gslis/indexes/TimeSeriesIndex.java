    package edu.gslis.indexes;

import java.io.BufferedReader;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.inference.ChiSquareTest;

import edu.gslis.textrepresentation.FeatureVector;

/**
 * Interface to an underlying H2 DB containing term time series information for a collection.
 *
 */
public class TimeSeriesIndex {

    ChiSquareTest csqtest = new ChiSquareTest();

    String format = null;
    int numBins = -1;
    FileWriter writer = null;
    Map<String, double[]> timeSeriesMap = new HashMap<String, double[]>();
    boolean readOnly = false;

    public void open(String path, boolean readOnly, String format) 
            throws IOException
    {
        
        if (readOnly) {
            load(path);
        } else {
            writer = new FileWriter(path);
        }
    }
    
    public void load(String path) throws IOException {
        BufferedReader br = new BufferedReader(
                new InputStreamReader(new GZIPInputStream(new FileInputStream(path)), "UTF-8"));
        String line;
        int j=0;
        System.err.println("Loading " + path);

        while ((line = br.readLine()) != null) {
            if (j%10000 == 0) 
                System.err.print(".");
            if (j%100000 == 0) 
                System.err.print(j + "\n");

            String[] fields = line.split(",");
            String term = fields[0];
            double[] counts = new double[fields.length-1];
            for (int i=1; i<fields.length; i++) {
                counts[i-1] = Double.valueOf(fields[i]);
            }
            timeSeriesMap.put(term, counts);
            j++;
        }
        System.err.println("Done");
                
        br.close();
    }
    
    

    public void add(String term, long[] counts) throws IOException {
        if (!readOnly) {
            writer.write(term);
            for (long count: counts) {
                writer.write("," + count);
            }
            writer.write("\n");
        }
    }
    
    
    public List<String> terms()
    {
        List<String> terms = new ArrayList<String>();

        terms.addAll(timeSeriesMap.keySet());
        
        return terms;
    }
    public double[] get(String term) 
    {
        return timeSeriesMap.get(term);
    }
    
    public double get(String term, int bin)
    {
        double freq = 0;

        double[] counts = timeSeriesMap.get(term);
        if (counts != null && counts.length == getNumBins())
            freq = counts[bin];
        return freq;
    }
    
    public int getNumBins() {
        if (numBins == -1)
            numBins = timeSeriesMap.get("_total_").length;

        return numBins;
    }
    
    
    /**
     *    n(w, bin) - mean(w)  / sd(w)
     */
    public void shrink(String newpath) throws IOException {
        FileWriter writer = new FileWriter(newpath);
        Set<String> vocab = timeSeriesMap.keySet();
        
        DecimalFormat df = new DecimalFormat("###.####");
        double[] totals = timeSeriesMap.get("_total_");
        totals = new double[totals.length];
        for (String term: vocab) {
            
            if (term.equals("_total_"))
                continue;
            
            double[] counts = timeSeriesMap.get(term);
            if (counts.length < totals.length)
                continue;
            
            DescriptiveStatistics stats = new DescriptiveStatistics();
            for (double count: counts)
                stats.addValue(count);
            
            writer.write(term);
            for (int i=0; i<counts.length; i++) {
                double shrunk = (counts[i] - stats.getMean()) / stats.getStandardDeviation();
                if (shrunk < 0) 
                    shrunk = 0;
                writer.write("," + df.format(shrunk));
                
                totals[i] += shrunk;
            }
            writer.write("\n");           
        }
        
        writer.write("_total_");
        for (int i=0; i<totals.length; i++) {
            writer.write("," + df.format(totals[i]));
        }
        writer.write("\n");           

        writer.close();
    }
    
    public void shrinkChiSq(String newpath, double alpha) throws IOException {
        FileWriter writer = new FileWriter(newpath);
        Set<String> vocab = timeSeriesMap.keySet();
        
        DecimalFormat df = new DecimalFormat("###.####");
        
        
        double[] totals = timeSeriesMap.get("_total_");
        double N = 0;
        for (double t: totals)
            N += t;
        

        double[] newtotals = new double[totals.length];
        for (String term: vocab) {
            
            if (term.equals("_total_"))
                continue;

            double[] counts = timeSeriesMap.get(term);
            if (counts.length < totals.length)
                continue;
                       
            double sum = 0;
            for (double c: counts) 
                sum+= c;
            
            writer.write(term);
            for (int bin=0; bin<totals.length; bin++) {
                
                
                if (alpha > 0)
                {
                    boolean sig = chiSqTest(N, (double)counts[bin], sum, (double)totals[bin], alpha);
                    
                    if (sig) {
                        writer.write("," + counts[bin]);
                        newtotals[bin] += counts[bin];
                    } else
                        writer.write(",0");
                }
                else {
                    // Use the chisquare statistic as the term weight                    
                    double csq = chiSq(N, (double)counts[bin], sum, (double)totals[bin]);
                    
                    writer.write("," + csq);
                    newtotals[bin] += csq;

                }
            }
            writer.write("\n");           
        }
        
        writer.write("_total_");
        for (int i=0; i<newtotals.length; i++) {
            writer.write("," + df.format(newtotals[i]));
        }
        writer.write("\n");           

        writer.close();
    }
    
    public double[] getChiSq(String term, double alpha) throws IOException {
        
        double[] totals = timeSeriesMap.get("_total_");
        double N = 0;
        for (double t: totals)
            N += t;
        
        double[] newvals = new double[totals.length];
           
        if (timeSeriesMap.get(term) == null) {
            System.err.println("Null entry for " + term);
            for (int bin=0; bin<totals.length; bin++) 
                newvals[bin] = 0;
            return newvals;
        }
        
        double[] counts = timeSeriesMap.get(term);

        double sum = 0;
        for (double c: counts) 
            sum+= c;
        
        for (int bin=0; bin<totals.length; bin++) {                
            
            if (alpha > 0)
            {
                boolean sig = chiSqTest(N, (double)counts[bin], sum, (double)totals[bin], alpha);
                
                if (sig)
                    newvals[bin] = 1.0;
                else
                    newvals[bin] = 0.0;
            }
            else if (alpha == 0) {
                // Use the chisquare statistic as the term weight      
                // beatification,4.8052097E7,0.0,23.0,0.0
                // System.out.println(term + "," + N + "," + counts[bin] + "," + sum + "," + totals[bin]);
                double csq = 0;
                if (counts[bin] > 0 && totals[bin] > 0)
                    csq = chiSq(N, (double)counts[bin], sum, (double)totals[bin]);    
//                double pval = chiSqTest(N, (double)counts[bin], sum, (double)totals[bin]);    
//                ChiSquaredDistribution csdist = new ChiSquaredDistribution(1);
//                double pval2 = (1 - csdist.cumulativeProbability(csq));
                
                newvals[bin] = csq;
            }
            else {
                // Return the pvalue
                double pval = 0;
                if (counts[bin] > 0 && totals[bin] > 0)
                    pval = chiSqTest(N, (double)counts[bin], sum, (double)totals[bin]);    
//                double pval = chiSqTest(N, (double)counts[bin], sum, (double)totals[bin]);    
//                ChiSquaredDistribution csdist = new ChiSquaredDistribution(1);
//                double pval2 = (1 - csdist.cumulativeProbability(csq));
                
                newvals[bin] = pval;
            }
        }
        
        return newvals;
    }
    
    private boolean chiSqTest(double N, double nX1Y1, double nX1, double nY1, double alpha) {

        //       | time  | ~time  |
        // ------|-------|--------|------
        //  word | nX1Y1 | nX1Y0  | nX1
        // ~word | nX0Y1 | nX0Y0  | nX0
        // ------|-------|--------|------
        //       |  nY1  |  nY0   | N
        
        long[][] table = new long[2][2];
        
        double nY0 = N - nY1;
        double nX0 = N - nX1;
        double nX1Y0 = nX1 - nX1Y1;
        double nX0Y1 = nY1 - nX1Y1;
        double nX0Y0 = nY0 - nX1Y0;
 
        table[0][0] = (long)nX1Y1;
        table[0][1] = (long)nX1Y0;
        table[1][0] = (long)nX0Y1;
        table[1][1] = (long)nX0Y0;
        
        return csqtest.chiSquareTest(table, alpha);
    }
    
    private double chiSqTest(double N, double nX1Y1, double nX1, double nY1) {

        //       | time  | ~time  |
        // ------|-------|--------|------
        //  word | nX1Y1 | nX1Y0  | nX1
        // ~word | nX0Y1 | nX0Y0  | nX0
        // ------|-------|--------|------
        //       | nY1   |  nY0   | N
        
        long[][] table = new long[2][2];
        
        double nY0 = N - nY1;
        double nX0 = N - nX1;
        double nX1Y0 = nX1 - nX1Y1;
        double nX0Y1 = nY1 - nX1Y1;
        double nX0Y0 = nY0 - nX1Y0;
 

        table[0][0] = (long)nX1Y1;
        table[0][1] = (long)nX1Y0;
        table[1][0] = (long)nX0Y1;
        table[1][1] = (long)nX0Y0;
        return csqtest.chiSquareTest(table);
    }
    
    private double chiSq(double N, double nX1Y1, double nX1, double nY1) {

        //       | time  | ~time  |
        // ------|-------|--------|------
        //  word | nX1Y1 | nX1Y0  | nX1
        // ~word | nX0Y1 | nX0Y0  | nX0
        // ------|-------|--------|------
        //       | nY1   |  nY0   | N
        
        double nY0 = N - nY1;
        double nX0 = N - nX1;
        double nX1Y0 = nX1 - nX1Y1;
        double nX0Y1 = nY1 - nX1Y1;
        double nX0Y0 = nY0 - nX1Y0;
 
        /*
        double[] observed = new double[4];
        double[] expected = new double[4];
        
        observed[0] = nX1Y1;
        observed[1] = nX1Y0;
        observed[2] = nX0Y1;
        observed[3] = nX0Y0;

        expected[0] = nY1*nX1/N;
        expected[1] = nY0*nX1/N;
        expected[2] = nY1*nX0/N;
        expected[3] = nY0*nX0/N;

        
        double chisq = 0;
        for (int i=0; i<4; i++) {
            chisq += Math.pow((observed[i] - expected[i]), 2)/expected[i];
        }
        */
        
        long[][] table = new long[2][2];
        table[0][0] = (long)nX1Y1;
        table[0][1] = (long)nX1Y0;
        table[1][0] = (long)nX0Y1;
        table[1][1] = (long)nX0Y0;

        
        return csqtest.chiSquare(table);
           
    }
    
        
    public void shrinkNpmi(String newpath, double threshold) throws IOException {
        FileWriter writer = new FileWriter(newpath);
        Set<String> vocab = timeSeriesMap.keySet();
        
        DecimalFormat df = new DecimalFormat("###.####");
        
        
        double[] totals = timeSeriesMap.get("_total_");
        double N = 0;
        for (double t: totals)
            N += t;
        

        double[] newtotals = new double[totals.length];
        for (String term: vocab) {
            
            if (term.equals("_total_"))
                continue;

            double[] counts = timeSeriesMap.get(term);
            if (counts.length < totals.length)
                continue;
                       
            double sum = 0;
            for (double c: counts) 
                sum+= c;
            
            writer.write(term);
            for (int bin=0; bin<totals.length; bin++) {
                double npmi = calcNpmi(N, (double)counts[bin], sum, (double)totals[bin]);
                
                if (threshold > 0) {
                    if (npmi > threshold) {
                        writer.write("," + counts[bin]);
                        newtotals[bin] += counts[bin];
                    } else
                        writer.write(",0");
                }
                else {
                    // Treat the npmi value as the weight for this term
                    if (npmi > 0) {
                        writer.write("," + counts[bin]);
                        newtotals[bin] += counts[bin];
                    } else
                        writer.write(",0");
                }                
            }
            writer.write("\n");           
        }
        
        writer.write("_total_");
        for (int i=0; i<newtotals.length; i++) {
            writer.write("," + df.format(newtotals[i]));
        }
        writer.write("\n");           

        writer.close();
    }
    
    public double[] getNpmi(String term) throws IOException {
        
        double[] totals = timeSeriesMap.get("_total_");
        double N = 0;
        for (double t: totals)
            N += t;
        
        double[] npmi = new double[totals.length];
           
        double[] counts = timeSeriesMap.get(term);
        if (timeSeriesMap.get(term) != null) {
            double sum = 0;
            for (double c: counts) 
                sum+= c;
            
            for (int bin=0; bin<totals.length; bin++) {                            
                npmi[bin] = calcNpmi(N, (double)counts[bin], sum, (double)totals[bin]);
                
                if (totals[bin] == 0)
                    npmi[bin] = 0;
            }
        }
        else {
            for (int bin=0; bin<totals.length; bin++)
                npmi[bin] = 0;
        }
        return npmi;
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
    
    public void smoothMovingAverage(String newpath, int win) throws IOException {
        FileWriter writer = new FileWriter(newpath);
        Set<String> vocab = timeSeriesMap.keySet();
        
        DecimalFormat df = new DecimalFormat("###.####");
        double[] totals = timeSeriesMap.get("_total_");
        totals = new double[totals.length];
        for (String term: vocab) {
            
            if (term.equals("_total_"))
                continue;
            
            double[] series = timeSeriesMap.get(term);
            if (series.length < series.length)
                continue;
            
            series = average(series, win);
            
            writer.write(term);
            for (int i=0; i<series.length; i++) {
                writer.write("," + df.format(series[i]));
                
                totals[i] += series[i];
            }
            writer.write("\n");           
        }
        
        writer.write("_total_");
        for (int i=0; i<totals.length; i++) {
            writer.write("," + df.format(totals[i]));
        }
        writer.write("\n");           

        writer.close();
    }
    
    public double[] average(double[] series, int winSize) 
    {        
        double[] smoothed = new double[series.length];
        
        for (int t=0; t<series.length; t++) {
            double timeFreq = series[t];
            int n = 1;
            
            int size = series.length;
            if (t < size) {

                for (int i=0; i < winSize; i++) {
                    if (t > i)
                        timeFreq += series[t - i];
                    if (t < size - i)
                        timeFreq += series[t + i];
                    n++;
                }
            }
            smoothed[t] = timeFreq/(double)n;
        }
        return smoothed;
    }
    
    public double getLength(int bin) throws Exception {
        return get("_total_", bin);
    }
}
