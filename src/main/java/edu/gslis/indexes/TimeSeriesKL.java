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
        for (int i=0; i<kls.length; i++) {
            System.out.println(i + "," + kls[i]);
        }

    }
    
    public double[] calculateBinKL(IndexWrapper index, TimeSeriesIndex tsindex) throws Exception
    {        
        List<String> vocab = tsindex.terms();
        
        double[] totals = tsindex.get("_total_");
        int numBins = totals.length;
        
        // Create a feature vector for each temporal bin
        FeatureVector[] tlms = new FeatureVector[numBins];
        for (int i=0; i<totals.length; i++) 
            tlms[i] = new FeatureVector(null);
        
        // Populate the temporal and collection feature vectors
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
        
        clm.normalize();
        
        // Calculate KL(CM || TM[i]) for each bin
        double kl[] = new double[numBins];
        for (int i=0; i<numBins; i++) {
            kl[i] = kl(clm, tlms[i]);
        }        
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
        
        Iterator<String> it = p.iterator();
        while(it.hasNext()) {
            String feature = it.next();
            double pi = p.getFeatureWeight(feature)/p.getLength();
            double qi = (q.getFeatureWeight(feature) + 1)/(q.getLength() + (double)p.getFeatureCount());
            kl += pi * Math.log(pi/qi);
        }
        return kl;
    }
        
    public static Options createOptions()
    {

        Options options = new Options();
        options.addOption("index", true, "Path to indri index");
        options.addOption("tsindex", true, "Path to time series index index");
        return options;
    }

}
