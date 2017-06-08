package edu.gslis.qpp;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
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
import org.apache.commons.cli.ParseException;

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.indexes.temporal.TimeSeriesIndex;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.qpp.predictors.PostRetrievalPredictorSuite;
import edu.gslis.qpp.predictors.PreRetrievalPredictorSuite;
import edu.gslis.qpp.predictors.PredictorSuite;
import edu.gslis.qpp.predictors.TemporalPredictorSuite;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHits;

/**
 * 
 * Work in progress
 * 
 * Basic CLI for running query performance predictor suites.
 */
public class RunQPP {
	static int MAX_RESULTS = 1000;

	public static void main(String[] args) throws IOException, ParseException {
		
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( RunQPP.class.getCanonicalName(), options );
            return;
        }
        String indexPath = cl.getOptionValue("index");
        String topicsPath = cl.getOptionValue("topics");	
        String outputPath = cl.getOptionValue("output");	        
        String tsindexPath = cl.getOptionValue("tsindex");
        long startTime = Long.parseLong(cl.getOptionValue("startTime"));
        long endTime = Long.parseLong(cl.getOptionValue("endTime"));
        long interval = Long.parseLong(cl.getOptionValue("interval"));
        
        GQueries queries = new GQueriesIndriImpl();
        queries.read(topicsPath);
        
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
		index.setTimeFieldName(Indexer.FIELD_EPOCH);
        TimeSeriesIndex tsindex = new TimeSeriesIndex();
        tsindex.load(tsindexPath);
        
        
		Writer output = new FileWriter(outputPath);		
		
		// getPredictors() returns a list of predictor short names. 
		List<PredictorSuite> predictorSuites = getPredictorSuites();
		TemporalPredictorSuite suite = new TemporalPredictorSuite();
		suite.setConstraints(startTime, endTime, interval);
		suite.setIndex(index);
		suite.setTimeSeriesIndex(tsindex);
		predictorSuites.add(suite);

		
		Map<String, Map<String, Double>> qpp = new TreeMap<String, Map<String, Double>>();
		
		 Iterator<GQuery> queryIt = queries.iterator();
		Set<String> labels = new TreeSet<String>();
		while (queryIt.hasNext()) {
			GQuery query = queryIt.next();
			System.out.println("Calculating predictors for " + query.getTitle());
			
			Map<String, Double> predictors = new TreeMap<String, Double>();
			SearchHits hits = index.runQuery(query, MAX_RESULTS);
			
			// Each predictor suite implements one or more predictors, each with a short name.
			// e.g., avgIDF, maxIDF, varIDF, minIDF; clarity; simplified
			for (PredictorSuite predictor: predictorSuites) {
				predictor.setIndex(index);
				predictor.setQuery(query);
				predictor.setK(MAX_RESULTS);

				predictor.setHits(hits);
				
				Map<String, Double> values = predictor.calculatePredictors();	
				predictors.putAll(values);
				labels.addAll(predictors.keySet());
			}				
			qpp.put(query.getTitle(),  predictors);
		}
		
		System.out.println("Writing out predictor matrix");
		String header="query";
		for (String label: labels) {
			header += "," + label;
		}
		header += "\n";
		output.write(header);
		
		for (String query: qpp.keySet()) {
			Map<String, Double> predictors = qpp.get(query);
			
			String row = query;
			for (String label: labels) {
				double value = predictors.get(label);
				row += ","+ value;
			}
			row += "\n";
			
			output.write(row);

		}				
		output.close();
	}
	
	public void writeOutput(Map<String, Map<String, Double>> qpp, Set<String> labels, Writer output) 
			throws IOException
	{
		String header="query";
		for (String label: labels) {
			header += label + ",";
		}
		header += "\n";
		output.write(header);
		
		for (String query: qpp.keySet()) {
			Map<String, Double> predictors = qpp.get(query);
			
			String row = query;
			for (String label: labels) {
				double value = predictors.get(label);
				row += value + ",";
			}
			row += "\n";
			
			output.write(row);

		}				
		output.close();
	}
	
	
	public static List<PredictorSuite> getPredictorSuites() {
		ArrayList<PredictorSuite> predictorSuites = new ArrayList<PredictorSuite>();
		predictorSuites.add(new PreRetrievalPredictorSuite());
		predictorSuites.add(new PostRetrievalPredictorSuite());
		return predictorSuites;	
	}
	
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "Path to input index");
        options.addOption("topics", true, "Path to topics file");
        options.addOption("output", true, "Output predictors"); 
        options.addOption("tsindex", true, "Path to input time-series index");
        options.addOption("startTime", true, "Collection start time"); 
        options.addOption("endTime", true, "Collection end time"); 
        options.addOption("interval", true, "Collection interval"); 
        
        return options;
    }
}
