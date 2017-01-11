package edu.gslis.main.temporal;

import java.util.Iterator;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import edu.gslis.eval.Qrels;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.scorers.temporal.TemporalScorer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

/**
 * Generate document/score plots for each query
 *
 */
public class PlotQueries 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( PlotQueries.class.getCanonicalName(), options );
            return;
        }
        String indexPath = cl.getOptionValue("index");
        String topicsPath = cl.getOptionValue("topics");
        String qrelsPath = cl.getOptionValue("qrels");
        String outputPath = cl.getOptionValue("output");

        GQueries queries = null;
        if (topicsPath.endsWith("indri")) 
            queries = new GQueriesIndriImpl();
        else
            queries = new GQueriesJsonImpl();
        queries.read(topicsPath);


        Qrels qrels =new Qrels(qrelsPath, false, 1);
                        
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
        index.setTimeFieldName(Indexer.FIELD_EPOCH);
        
        // For each query, plug the document score and time
        Iterator<GQuery> qit = queries.iterator();
        while (qit.hasNext()) 
        {
            GQuery query = qit.next();
            
            SearchHits hits = index.runQuery(query, 1000);	
            
            Iterator<SearchHit> it = hits.iterator();
            while (it.hasNext()) {
            	SearchHit hit = it.next();
            	double epoch = TemporalScorer.getTime(hit);
            	double score = hit.getScore();    
            	int rel = qrels.getRelLevel(query.getTitle(), hit.getDocno());
            	System.out.println(query.getTitle() + "," + hit.getDocno() + "," +  epoch + "," + score + "," + rel);
            }
        }          
    }
            
    
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "Path to input index");
        options.addOption("output", true, "Path to output directory");
        options.addOption("topics", true, "Path to topics file");
        options.addOption("qrels", true, "Path to qrels file");

        return options;
    }

}
