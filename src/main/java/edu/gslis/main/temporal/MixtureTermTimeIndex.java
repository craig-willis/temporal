    package edu.gslis.main.temporal;

import java.io.FileWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.indexes.temporal.TimeSeriesIndex;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * 
 * ./run.sh edu.gslis.temporal.main.ShrinkTermTimeIndex 
 *      -input <path to input csv>
 *      -output <path to output csv>
 */
public class MixtureTermTimeIndex 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( MixtureTermTimeIndex.class.getCanonicalName(), options );
            return;
        }
        String tsIndexPath = cl.getOptionValue("input");
        String indexPath = cl.getOptionValue("index");
        String outputPath = cl.getOptionValue("output");
        double alpha = Double.parseDouble(cl.getOptionValue("alpha", "0.0001"));
        

        TimeSeriesIndex tsIndex = new TimeSeriesIndex();
        tsIndex.open(tsIndexPath, true, "csv");
        
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
        FileWriter output = new FileWriter(outputPath);
        
        double lambda = 0.5;
        int numIterations = 50;
        
        int numBins = tsIndex.getNumBins();
        Map<Integer, FeatureVector> thetas = new HashMap<Integer, FeatureVector>();
        List<String> vocab = tsIndex.terms();
        vocab.remove("_total_");
        
        for (int i=0; i<numBins; i++) {
            FeatureVector theta_t = new FeatureVector(null);    // Empirical distribution of terms for bin t (dCounter)
            FeatureVector theta_est = new FeatureVector(null);  // Mixture-model estimated distribution (distQueryEst)
            FeatureVector theta = new FeatureVector(null);      // Working unigram model (distQuery)
            double meanLL = 1e-40;


            // Initialize theta_t and theta_est (random)
            for (String term: vocab) {
                double w = tsIndex.get(term, i);
                theta_t.addTerm(term, w);                
                theta_est.addTerm(term, Math.random() + 0.001);
            }            
            // Normalize theta_est
            theta_est.normalize();

            double total = tsIndex.getLength(i);
            if (total > 0) 
            {
                // Start iterations for this bin
                for (int j=0; j < numIterations; j++) {
                    
                    double ll = 0;
                    
                    // Initialize working model
                    for (String term: vocab)
                        theta.addTerm(term, theta_est.getFeatureWeight(term));
                    theta.normalize();

                    // Re-initialize estimate
                    theta_est = new FeatureVector(null);
                    for (String term: vocab)
                        theta_est.setTerm(term, 0);

                    // compute likelihood
                    for (String term: theta_t.getFeatures()) {
                        double collectionPr =
                                (index.termFreq(term) - tsIndex.get(term, i)) /
                                    (index.termCount() - tsIndex.getLength(i));

                        
                        double weight = theta_t.getFeatureWeight(term);
                        ll += weight * Math.log(lambda*collectionPr  
                                + (1-lambda)*theta.getFeatureWeight(term));
                    }
                    
                    meanLL = 0.5*meanLL + 0.5*ll;
                    if (Math.abs( (meanLL - ll)/meanLL) < alpha) { 
                        System.err.println("Converged at " + j + " with likelihood " + ll);
                        break;
                    } else {
                        System.err.println("Iteration " + j + " " + Math.abs( (meanLL - ll)/meanLL) + ", " + ll);
                    }
                        
                    
                    // update counts
                    for (String term: theta_t.getFeatures()) {
                        
                        double collectionPr =
                                (index.termFreq(term) - tsIndex.get(term, i)) /
                                    (index.termCount() - tsIndex.getLength(i));

                        double weight = theta_t.getFeatureWeight(term);
                        double pr = theta.getFeatureWeight(term);
    
                        double prTopic = (1-lambda)*pr/
                                ((1-lambda)*pr+lambda*collectionPr);
    
                        double incVal = weight * prTopic;
                        if (incVal > 0) 
                            theta_est.addTerm(term, incVal);
                    }
                    theta_est.normalize();
                }
            }
            else 
                theta = theta_t;

            theta.normalize();
            
           
            System.out.println("\nEmpirical:\n" + theta_t.toString(50));
            System.out.println("\nEstimated:\n" + theta.toString(50));
            thetas.put(i, theta);
        }

        for (String term: vocab) {
            output.write(term);
            for (int i=0; i<numBins; i++) {
                FeatureVector theta = thetas.get(i);
                double w = theta.getFeatureWeight(term);
                output.write("," + w);
            }
            output.write("\n");
        }

        output.write("_total_");
        for (int i=0; i<numBins; i++) {
            FeatureVector theta = thetas.get(i);
            double total= theta.getLength();
            output.write("," + total);
        }
        output.write("\n");      
        
        output.close();
    }
        
        
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("input", true, "Path to input csv");
        options.addOption("index", true, "Path to index");
        options.addOption("output", true, "Path to output csv");
        options.addOption("alpha", true, "Convergence alpha");
        return options;
    }

}
