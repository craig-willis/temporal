package edu.gslis.indexes;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import edu.gslis.textrepresentation.FeatureVector;

/**
 * Calculate KL-divergence of LDA LM from collection LM.
 */
public class LDAKL 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( LDAKL.class.getCanonicalName(), options );
            return;
        }
        String indexPath = cl.getOptionValue("index");
        String ldaTermTopicPath = cl.getOptionValue("ldaTermTopicPath");
                
        BufferedReader br = new BufferedReader(
                new InputStreamReader(new GZIPInputStream(new FileInputStream(ldaTermTopicPath)), "UTF-8"));

        String line;
        Map<String, FeatureVector> fvs = new HashMap<String, FeatureVector>();
        Set<String> vocab = new TreeSet<String>();
        while ((line = br.readLine()) != null) {
            String[] fields = line.split(",");
            String topic = fields[0].trim();
            String term = fields[1].trim();
            double freq  = Double.valueOf(fields[3].trim());    
            
            FeatureVector fv = fvs.get(topic);
            if (fv == null)
                fv = new FeatureVector(null);
            
            fv.addTerm(term, freq);
            
            fvs.put(topic, fv);
            vocab.add(term);
        }
        br.close();        
        
        // Normalize the feature vectors
        for (String topic: fvs.keySet()) {
            FeatureVector fv = fvs.get(topic);
            fv.normalize();
            fvs.put(topic, fv);
        }
        
        IndexWrapper index =  IndexWrapperFactory.getIndexWrapper(indexPath);
        FeatureVector cfv = new FeatureVector(null);
        for (String term: vocab) {
            double cfreq = index.termFreq(term);
            if (cfreq > 0)
                cfv.addTerm(term, cfreq);
        }
        cfv.normalize();
       
        for (String topic: fvs.keySet()) {
            FeatureVector tfv = fvs.get(topic);
            double kld = kl(cfv, tfv);
            System.out.println(topic + "," + kld);
        }
        
    }
    
    // "The K-L divergence is only defined if P and Q both sum to 1 and if Q(i) > 0 
    // for any i such that P(i) > 0."
    /**
     * 
     * @param p Collection LM
     * @param q LDA LM
     * @return
     */
    public static double kl(FeatureVector p, FeatureVector q) 
    {
        double kl = 0;
        
        // Need to smooth q to ensure non-zero probabilities for all terms in p.
               
        Set<String> features = p.getFeatures();
        for (String feature: features) {
            double pi = p.getFeatureWeight(feature)/p.getLength();
            double qi = q.getFeatureWeight(feature)/q.getLength();
            if (qi > 0)
                kl += pi * Math.log(pi/qi);
        }
        return kl;
    }
        
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "Path to indri index");
        options.addOption("ldaTermTopicPath", true, "Path to ida index");
        return options;
    }

}
