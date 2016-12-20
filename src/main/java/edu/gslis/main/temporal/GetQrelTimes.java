package edu.gslis.main.temporal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.collections.Bag;
import org.apache.commons.collections.bag.TreeBag;

import edu.gslis.eval.Qrels;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;

public class GetQrelTimes 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( GetQrelTimes.class.getCanonicalName(), options );
            return;
        }
        String indexPath = cl.getOptionValue("index");
        String queryFilePath = cl.getOptionValue("query");
        String qrelsPath = cl.getOptionValue("qrels");
        long startTime = Long.parseLong(cl.getOptionValue("startTime"));
        long interval = Long.parseLong(cl.getOptionValue("interval"));

        GQueries queries = null;
        if (queryFilePath.endsWith("indri")) 
            queries = new GQueriesIndriImpl();
        else
            queries = new GQueriesJsonImpl();
        queries.read(queryFilePath);


        Qrels qrels =new Qrels(qrelsPath, false, 1);

        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);

        Iterator<GQuery> it = queries.iterator();
        while (it.hasNext()) 
        {
            GQuery query = it.next();
                        
            Set<String> relDocs = qrels.getRelDocs(query.getTitle());
            List<Integer> relDocBins = new ArrayList<Integer>();
            Bag relDocBag = new TreeBag();
            for (String relDoc: relDocs) {
                if (index.getMetadataValue(relDoc, Indexer.FIELD_EPOCH) != null)
                {
                    double epoch = Double.parseDouble(index.getMetadataValue(relDoc, Indexer.FIELD_EPOCH));
                    int bin = (int) ((epoch - startTime) / interval);
                    relDocBins.add(bin);
                    relDocBag.add(bin);
                    System.out.println(query.getTitle() + "," + relDoc + "," + epoch + "," + bin);
                }
            }
        }  
    }
        
        
    
    
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "Path to input index");
        options.addOption("query", true, "Query string");
        options.addOption("qrels", true, "Path to qrels");
        options.addOption("startTime", true, "Start time");
        options.addOption("endTime", true, "End time");
        options.addOption("interval", true, "interval");

        return options;
    }

}
