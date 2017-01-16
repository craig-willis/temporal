package edu.gslis.main;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;

/** 
 * Very simple leave-one-out cross validation that takes a directory of trec_eval 
 * output files.
 * 
 * The basic process works as follows:
 * 1. Use edu.gslis.main.RunQuery framework to generate trec-formatted output
 *    for each parameter combination.
 * 2. Run mkeval.sh, which simply runs trec_eval -c -m all_trec -q, and outputs
 *    to a separate evaluation file per parameter combination.
 * 3. Run this class passing in the path to the trec_eval output directory and the
 *    desired metric
 * 
 */
public class CrossValidation 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( CrossValidation.class.getCanonicalName(), options );
            return;
        }
        //String outputPath = cl.getOptionValue("output");
        String inputPath = cl.getOptionValue("input");
        String metric = cl.getOptionValue("metric");
        
        // Read trec_eval output 
        File inputDir = new File(inputPath);
        Set<String> topics = new TreeSet<String>();
        Map<String, Map<String, Double>> trecEval = 
        		new TreeMap<String, Map<String, Double>>();
        if (inputDir.isDirectory()) {
        	//Read output file
        	for (File file: inputDir.listFiles()) {
        		if (file.isDirectory())
        			continue;
        		
        		String paramSet = file.getName();
        		
        		List<String> lines = FileUtils.readLines(file);
        		for (String line: lines) {
        			String[] fields = line.split("\\t");
        			String measure = fields[0].trim();
        			String topic = fields[1];

        			if (measure.equals("runid") || topic.equals("all") || measure.equals("relstring"))
        				continue;
        		
        			double value =0;
        			try {
        				 value = Double.parseDouble(fields[2]); 
        			} catch (Exception e) {
        				continue;
        			}
        			
        			topics.add(topic);
        			
        			if (measure.equals(metric)) {
        				Map<String, Double> topicMap = trecEval.get(paramSet);
        				if (topicMap == null) 
        					topicMap = new TreeMap<String, Double>();
        				
        				topicMap.put(topic, value);
        				trecEval.put(paramSet, topicMap);
        			}

        		}
        	}
        }
        
		// For each topic
        Map<String, Double> testMap = new TreeMap<String, Double>();
		for (String heldOut: topics) {			
			// This is the held-out topic.		
			
			// Find parameter combination with best metric across the training fold
			double max = 0;
			String maxParam = "";
			for (String paramSet: trecEval.keySet()) {
				Map<String, Double> topicMap = trecEval.get(paramSet);
			
				double score = 0;
				for (String topic: topicMap.keySet()) {
					// Ignore held-out topic
					if (topic.equals(heldOut))
						continue;
					score += topicMap.get(topic);										
				}				
				score /= (topicMap.size() - 1);
								
				if (score > max) {
					max = score;
					maxParam = paramSet;
				}
			}
			
			// Get the score for the held out topic
			if (trecEval.get(maxParam).get(heldOut) != null) {
				double value = trecEval.get(maxParam).get(heldOut);
				System.out.println(heldOut + "\t" + maxParam + "\t" + value);
				testMap.put(heldOut, value);			
			} else {
				System.err.println("Warning: no value for " + heldOut);
			}
        }
		
		// Average the resulting scores
		double score = 0;
		for (String topic: testMap.keySet()) {
			score += testMap.get(topic);
		}
		score /= testMap.size();
		System.out.println(metric + "\t" + score);

    }
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("input", true, "Path to directory trec_eval output files");
        options.addOption("metric", true, "Cross validation metric");
        options.addOption("output", true, "Output path");
        return options;
    }
      
}
