package edu.gslis.indexes;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import edu.gslis.textrepresentation.FeatureVector;

/**
 * Calculate KL-divergence of temporal LM from collection LM.
 */
public class TimeSeriesKL 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( TimeSeriesKL.class.getCanonicalName(), options );
            return;
        }
        String indexPath = cl.getOptionValue("index");
        String tsindexPath = cl.getOptionValue("tsindex");

        TimeSeriesIndex tsindex = new TimeSeriesIndex();
        tsindex.open(tsindexPath, true, "csv");
        
        IndexWrapper index =  IndexWrapperFactory.getIndexWrapper(indexPath);

        TimeSeriesKL tskl = new TimeSeriesKL();
        double[] kls = tskl.calculateBinKL(index, tsindex);
        tsindex.close();

    }
    
    public double[] calculateBinKL(IndexWrapper index, TimeSeriesIndex tsindex) throws Exception
    {        
        List<String> vocab = tsindex.terms();
        double[] totals = tsindex.get("_total_");
        int numBins = totals.length;
        FeatureVector[] tlms = new FeatureVector[numBins];
        for (int i=0; i<totals.length; i++) 
            tlms[i] = new FeatureVector(null);
        
        FeatureVector clm = new FeatureVector(null);
        for (String term: vocab) {
            if (term.equals("_total_")) 
                continue;
            double[] freq = tsindex.get(term);
            if (freq.length != numBins) 
                continue;
            for (int i=0; i<numBins; i++) {
                if (freq[i] > 0)
                    tlms[i].addTerm(term, freq[i]);                    
            }
            double cfreq = index.termFreq(term);
            if (cfreq > 0)
                clm.addTerm(term, cfreq);            
        }
        // Smooth the temporal bins
        /*
        for (int i=0; i<numBins; i++) {
            tlms[i].normalize();
        }
        */
        clm.normalize();
        
        double kl[] = new double[numBins];
        double total = 0;
        for (int i=0; i<numBins; i++) {
            kl[i] = kl(clm, tlms[i]);
            //kl[i] = kl(tlms[i], clm);
            //System.out.println(i + "," + kl[i]);
            total += kl[i];
        }
        
        /*
        for (int i=0; i<numBins; i++) {
            System.out.println(i + "," + kl[i]/total);
        }
        */
        
        return kl;

    }
    
    // "The K-L divergence is only defined if P and Q both sum to 1 and if Q(i) > 0 
    // for any i such that P(i) > 0."
    /**
     * 
     * @param p  Collection LM
     * @param q  Temporal bin LM
     * @return
     */
    public static double kl(FeatureVector p, FeatureVector q) 
    {
        double kl = 0;
        
        double add = (1/(double)p.getFeatureCount()); 
               
        double total = 0;
        Iterator<String> it = p.iterator();
        while(it.hasNext()) {
            String feature = it.next();
            double pi = p.getFeatureWeight(feature)/p.getLength();
            double qi = (q.getFeatureWeight(feature) + add)/(q.getLength() + 1);
            kl += pi * Math.log(pi/qi);
            total += qi;
        }
        //System.out.println(total);
        return kl;
    }
        /*
    public static double kl(FeatureVector p, FeatureVector clm) 
    {

        double kl = 0;
               
        FeatureVector q = new FeatureVector(null);
        Set<String> features = p.getFeatures();
        for (String feature: features) {            
            q.addTerm(feature, clm.getFeatureWeight(feature));
        }
        q.normalize();
                
        for (String feature: features) {            
            if (p.getFeatureWeight(feature) == 0 || q.getFeatureWeight(feature) == 0)
                System.out.println("\t" + feature + ": " + p.getFeatureWeight(feature) + "," + q.getFeatureWeight(feature));
            kl += p.getFeatureWeight(feature) * Math.log(p.getFeatureWeight(feature) / q.getFeatureWeight(feature));
        }
        return kl;
            }

        */
        
        
    public static Options createOptions()
    {

        Options options = new Options();
        options.addOption("index", true, "Path to indri index");
        options.addOption("tsindex", true, "Path to time series index index");
        return options;
    }

}
