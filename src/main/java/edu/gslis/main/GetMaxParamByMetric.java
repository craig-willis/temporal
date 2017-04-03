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
 * Given a directory of trec_eval output, find the max param set 
 * for each query
 */
public class GetMaxParamByMetric 
{
	
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( GetMaxParamByMetric.class.getCanonicalName(), options );
            return;
        }
        String inputPath = cl.getOptionValue("input");
        String metric = cl.getOptionValue("metric");
        
        
        // Read trec_eval output 
        File inputDir = new File(inputPath);
        Set<String> topics = new TreeSet<String>();
        Map<String, Map<String, Double>> trecEval = 
        		new TreeMap<String, Map<String, Double>>();
        if (inputDir.isDirectory()) {
        	
        	//Read each input file (parameter output)
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
        				System.err.println(e.getMessage());
        				continue;
        			}
        			
        			topics.add(topic);
        			
        			if (measure.equals(metric)) {
        				// Store the topic=value pair for each parameter set for this metric
        				Map<String, Double> topicMap = trecEval.get(paramSet);
        				if (topicMap == null) 
        					topicMap = new TreeMap<String, Double>();
        				
        				topicMap.put(topic, value);
        				trecEval.put(paramSet, topicMap);
        			}

        		}
        	}
        }
        
		for (String topic: topics) {			
			
			// Find parameter combination with best metric
			double max = 0;
			String maxParam = "";
			for (String paramSet: trecEval.keySet()) {
				Map<String, Double> topicMap = trecEval.get(paramSet);
			
				double score = topicMap.get(topic);
				if (score > max) {
					max = score;
					maxParam = paramSet;					
				}		
			}
				
			System.out.println(topic + "\t" + maxParam + "\t" + max );		
        }


    }
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("input", true, "Path to directory trec_eval output files");
        options.addOption("metric", true, "Metric");
        options.addOption("verbose", false, "Verbose output");
        return options;
    }
      
}
