package edu.gslis.main;

import java.util.Iterator;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.indexes.temporal.TimeSeriesIndex;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.qpp.predictors.TemporalPredictorSuite;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;


/** 
 * Calculate feature values for query terms:
 * 
 *
 */
public class GetFeaturesQL 
{
	static int MAX_RESULTS=1000;
	
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( GetFeaturesQL.class.getCanonicalName(), options );
            return;
        }
        String indexPath = cl.getOptionValue("index");
        String tsIndexPath = cl.getOptionValue("tsindex");
        String topicsPath = cl.getOptionValue("topics");	
        
        long startTime = Long.parseLong(cl.getOptionValue("startTime"));
        long endTime  = Long.parseLong(cl.getOptionValue("endTime"));
        long interval = Long.parseLong(cl.getOptionValue("interval"));  
        int fbDocs = Integer.parseInt(cl.getOptionValue("fbDocs", "50"));

        boolean smooth = cl.hasOption("smooth");
        String tsPath = null;        
        if (cl.hasOption("ts")) 
        	tsPath = cl.getOptionValue("ts");
        
        String plotPath = null;        
        if (cl.hasOption("plot")) 
        	plotPath = cl.getOptionValue("plot");        

        GQueries queries = null;
        if (topicsPath.endsWith("indri")) 
            queries = new GQueriesIndriImpl();
        else
            queries = new GQueriesJsonImpl();
        queries.read(topicsPath);
        
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
        index.setTimeFieldName(Indexer.FIELD_EPOCH);
        
        TimeSeriesIndex tsindex = new TimeSeriesIndex();
        tsindex.open(tsIndexPath, true);
        
        Iterator<GQuery> queryIt = queries.iterator();
        
        TemporalPredictorSuite qpp = new TemporalPredictorSuite();
        qpp.setIndex(index);
        qpp.setTimeSeriesIndex(tsindex);
        qpp.setConstraints(startTime, endTime, interval);
        qpp.setFbDocs(fbDocs);
        qpp.setNumResults(MAX_RESULTS);;
        qpp.setPlotPath(plotPath);
        qpp.setTsPath(tsPath);
        
        String header = "query,term";
        for (String field: qpp.getFields())
        	header += "," + field;
        
        System.out.println(header);
        
        while (queryIt.hasNext()) {
        	
            GQuery query = queryIt.next();
        	Map<String, Map<String, Double>> queryPredictors = qpp.getFeatures(query, smooth);


            for (String term: query.getFeatureVector().getFeatures()) {

            	Map<String, Double> predictors = queryPredictors.get(term);
            	
                String row = query.getTitle() + "," + term;

                for (String field: predictors.keySet()) {
                	row += "," + predictors.get(field);
                }
                
                System.out.println(row);
            }            
        }
    }

    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "Path to input index");
        options.addOption("tsindex", true, "Path to collection time series index");
        options.addOption("topics", true, "Path to topics file");
        options.addOption("startTime", true, "Collection start time");
        options.addOption("endTime", true, "Collection end time");
        options.addOption("interval", true, "Collection interval");       
        options.addOption("smooth", false, "Smooth the timeseries");
        options.addOption("fbDocs", true, "Number of feedback docs");
        options.addOption("ts", true, "Save the timeseries to this file");
        
        options.addOption("plot", true, "Plot");
        return options;
    }

}
