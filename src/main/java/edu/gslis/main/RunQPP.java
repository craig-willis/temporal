package edu.gslis.main;

import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

/** 
 * Work in progress. Run query performance prediction pipeline
 */
public class RunQPP {
	
	static int MAX_RESULTS=1000;
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( RunQPP.class.getCanonicalName(), options );
            return;
        }
        String[] modelNames = cl.getOptionValue("models").split(",");
        String predictors = cl.getOptionValue("predictors", "qpp");
        String input = cl.getOptionValue("input", "loocv");
        String collection = cl.getOptionValue("collection");
        String metric = cl.getOptionValue("metric", "ndcg");
        String topics = cl.getOptionValue("topics", "full");
        String algorithm = cl.getOptionValue("algorithm", "svm");
    	boolean verbose = cl.hasOption("verbose");

    	Map<String, Map<String, LOOCVResult>> models = new TreeMap<String, Map<String, LOOCVResult>>();
    	// Read LOOCV output for desired models
    	for (String model: modelNames) {
    		String inputFile = input + "/" + collection + "." + topics +  "." + model + "." + metric + ".out";
    		
    		// Read the input file
    		Map<String, LOOCVResult> results = LOOCVResult.readResults(inputFile);
    		models.put(model, results);
    		
    		// Calculate correlations   
    		// For each result-set, calculate pearson and spearman's correlation
    	}
    	
    	// Build regression models via LOOCV.  For each query, there will be one regression model per model
    	
    	//
    }

    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("models", true, "Comma separated list of models");
        options.addOption("predictors", true, "Path to directory containing qpp output");
        options.addOption("input", true, "Path to directory containing loocv output");
        options.addOption("collection", true, "Test collection");
        options.addOption("topics", true, "Name of topics used");
        options.addOption("metric", true, "One of map, ndcg, p_20, ndcg_20");
        options.addOption("agorithm", true, "lm,svm");
        options.addOption("verbose", false, "Verbose");
        return options;
    }
}
