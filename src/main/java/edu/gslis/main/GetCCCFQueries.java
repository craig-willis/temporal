package edu.gslis.main;

import java.io.FileWriter;
import java.text.DecimalFormat;
import java.util.Iterator;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import edu.gslis.indexes.temporal.TimeSeriesIndex;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.scorers.temporal.TemporalScorer;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Get Feedback Queries -- currently supports only RM3
 * TODO: Extend to support RM, RM3, and possibly Zhai's DFM.
 */
public class GetCCCFQueries 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( GetCCCFQueries.class.getCanonicalName(), options );
            return;
        }
        String tsIndexPath = cl.getOptionValue("tsindex");
        String topicsPath = cl.getOptionValue("topics");
        
        TimeSeriesIndex tsIndex = new TimeSeriesIndex();
    	tsIndex.open(tsIndexPath, true);       
        
    	double[] background = tsIndex.get("_total_");
        String outputPath = cl.getOptionValue("output");
        
        GQueries queries = null;
        if (topicsPath.endsWith("indri")) 
            queries = new GQueriesIndriImpl();
        else
            queries = new GQueriesJsonImpl();
        queries.read(topicsPath);
        
        RUtil rutil = new RUtil();
        
        FileWriter outputWriter = new FileWriter(outputPath);
        Iterator<GQuery> queryIt = queries.iterator();
        outputWriter.write("<parameters>\n");
        while (queryIt.hasNext()) {
            GQuery query = queryIt.next();

            FeatureVector acfn = new FeatureVector(null);
            for (String term: query.getFeatureVector().getFeatures()) {
            	double[] tsw = tsIndex.get(term);
                if (tsw == null) {
                	System.err.println("Unexpected null termts for " + term);
                	continue;
                }

            	double sum = TemporalScorer.sum(tsw);

                double ccf = 0;
            	if (sum > 0) {
    	        	try {        		
    	        		ccf = rutil.ccf(background, tsw,0);	        		
    	        	} catch (Exception e) {
    	        		e.printStackTrace();        		
    	        	}
            	}        	
            	if (ccf < 0) 
            		ccf = 0;
            	
            	acfn.addTerm(term, 1-ccf);
            } 
            
            // Normalize term scores
            acfn.normalize();
                        
            GQuery feedbackQuery = new GQuery();
            feedbackQuery.setTitle(query.getTitle());
            feedbackQuery.setText(query.getText());
            feedbackQuery.setFeatureVector(acfn);
            
            outputWriter.write("<query>\n");
            outputWriter.write("<number>" +  query.getTitle() + "</number>\n");
            outputWriter.write("<text>" + toIndri(feedbackQuery) + "</text>\n");
            outputWriter.write("</query>\n");
        }
        outputWriter.write("</parameters>\n");
        outputWriter.close();
    }
        
    
    public static String toIndri(GQuery query) {
        
    	DecimalFormat df = new DecimalFormat("#.#####");
        StringBuilder queryString = new StringBuilder("#weight(");
        Iterator<String> qt = query.getFeatureVector().iterator();
        while(qt.hasNext()) {
            String term = qt.next();
            double weight = query.getFeatureVector().getFeatureWeight(term);
            if (!Double.isNaN(weight)) {
                queryString.append(df.format(weight) + " " + term + " ");
            }
        }
        queryString.append(")");
        return queryString.toString();
    }
    
    public static void scale(FeatureVector fv, double min, double max) {
    	for (String term: fv.getFeatures()) {
    		double x = fv.getFeatureWeight(term);
    		double z = (x - min)/(max -min);
    		fv.setTerm(term, z);
    	}
      	fv.normalize(); 	
    }
        
    
    
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("tsindex", true, "Path to input index");
        options.addOption("topics", true, "Path to topics file");
        options.addOption("output", true, "Path to output topics file");
        options.addOption("minacf", true, "Minimum ACF for scaling");
        options.addOption("maxacf", true, "Maximum ACF for scaling");        
        return options;
    }

}
