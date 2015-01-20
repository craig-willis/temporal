package edu.gslis.indexes;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import edu.gslis.textrepresentation.FeatureVector;

/**
 * Aggregate models if adding a model increases KL from collection
 */
public class BuildBinKL 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( BuildBinKL.class.getCanonicalName(), options );
            return;
        }
        String indexPath = cl.getOptionValue("index");
        String tsindexPath = cl.getOptionValue("tsindex");
        String outputPath = cl.getOptionValue("output");

        int interval = Integer.parseInt(cl.getOptionValue("interval", "7"));

        TimeSeriesIndex tsindex = new TimeSeriesIndex();
        tsindex.open(tsindexPath, true, "csv");
        
        IndexWrapper index =  IndexWrapperFactory.getIndexWrapper(indexPath);

        BuildBinKL tskl = new BuildBinKL();
        tskl.createNewIndex(index, tsindex, interval, outputPath);
        tsindex.close();

    }
    
    public void createNewIndex(IndexWrapper index, TimeSeriesIndex tsindex, int interval, 
            String outputPath) throws Exception
    {        
        TimeSeriesIndex outputIndex = new TimeSeriesIndex();
        outputIndex.open(outputPath, false, "csv");
        
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
        
        // Now we have a set of temporal models. Aggregate these models based on
        // some interval, but only adding models that increase KL for the aggregated model        
        clm.normalize();

        List<FeatureVector> fvs = new ArrayList<FeatureVector>();
        FeatureVector current = new FeatureVector(null);
        double lastKL = 0;
        int j=1;
        for (int i=0; i<numBins; i++) 
        {
            if (i % interval == 0) {
                System.out.println(j +  "," + lastKL);

                fvs.add(current.deepCopy());
                current = new FeatureVector(null);
                j++;
                lastKL = 0;
            }
            
            FeatureVector tmp = combine(current, tlms[i]);
            double distance = kl(clm, tmp);
            if (distance > lastKL) {
                current = tmp;      
                lastKL = distance;
            }
        }
        
        for (String feature: clm.getFeatures()) {
            long counts[] = new long[fvs.size()];
            for (int i=0; i<fvs.size(); i++) {
                FeatureVector fv = fvs.get(i);
                counts[i] = (long)fv.getFeatureWeight(feature);
            }
            outputIndex.add(feature, counts);
        }

        outputIndex.close();

    }
    
    public FeatureVector combine(FeatureVector a, FeatureVector b) {
        FeatureVector c = new FeatureVector(null);
        for (String feature: a.getFeatures()) {
            c.addTerm(feature, a.getFeatureWeight(feature));
        }
        for (String feature: b.getFeatures()) {
            c.addTerm(feature, b.getFeatureWeight(feature));
        }
        //c.normalize();
        return c;
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
        options.addOption("output", true, "Path to output time series index index");
        options.addOption("interval", true, "Aggregation interval");

        return options;
    }

}
