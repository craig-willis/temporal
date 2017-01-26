package edu.gslis.main.temporal;

import java.io.FileWriter;
import java.math.BigDecimal;
import java.util.Iterator;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.scorers.temporal.TRMScorer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;

public class GenerateTemporalFeedback 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( GenerateTemporalFeedback.class.getCanonicalName(), options );
            return;
        }
        String indexPath = cl.getOptionValue("index");
        String topicsPath = cl.getOptionValue("topics");
        double mu =Double.parseDouble(cl.getOptionValue("mu", "2500"));
        int numFbDocs =Integer.parseInt(cl.getOptionValue("numFbDocs", "20"));
        int numFbTerms =Integer.parseInt(cl.getOptionValue("numFbTerms", "20"));
        double lambda =Double.parseDouble(cl.getOptionValue("lambda", "0.5"));
        String outputPath = cl.getOptionValue("output");
        
        long startTime =Long.parseLong(cl.getOptionValue("startTime"));
        long endTime =Long.parseLong(cl.getOptionValue("endTime"));
        long interval =Long.parseLong(cl.getOptionValue("interval"));
        
        
        GQueries queries = null;
        if (topicsPath.endsWith("indri")) 
            queries = new GQueriesIndriImpl();
        else
            queries = new GQueriesJsonImpl();
        queries.read(topicsPath);
        
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
        index.setTimeFieldName(Indexer.FIELD_EPOCH);
        FileWriter outputWriter = new FileWriter(outputPath);
        
        TRMScorer scorer = new TRMScorer();
        scorer.setIndex(index);
        scorer.setEndTime(endTime);
        scorer.setStartTime(startTime);
        scorer.setInterval(interval);
        scorer.setParameter("mu", mu);
        scorer.setParameter("fbDocs", numFbDocs);
        scorer.setParameter("fbTerms", numFbTerms);
        scorer.setParameter("lambda", lambda);
        
        Iterator<GQuery> queryIt = queries.iterator();
        while (queryIt.hasNext()) {
            GQuery query = queryIt.next();
            SearchHits results = index.runQuery(query, numFbDocs);
            
            scorer.setQuery(query);

            scorer.init(results);
                        
            outputWriter.write(scorer.getQuery().toString() + "\n");
        }
        outputWriter.close();
        
    }
    public static String toIndri(GQuery query) {
        
        StringBuilder queryString = new StringBuilder("#weight(");
        Iterator<String> qt = query.getFeatureVector().iterator();
        while(qt.hasNext()) {
            String term = qt.next();
            double weight = query.getFeatureVector().getFeatureWeight(term);
            if (!Double.isNaN(weight)) {
                String w = new BigDecimal(weight).toPlainString();              
                // Use BigDecimal to avoid scientific notation
                queryString.append(w + " " + term + " ");
            }
        }
        queryString.append(")");
        return queryString.toString();
    }
    
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "Path to input index");
        options.addOption("topics", true, "Path to topics file");
        options.addOption("mu", true, "Dirichlet mu");
        options.addOption("numFbDocs", true, "Number of feedback docs");
        options.addOption("numFbTerms", true, "Number of feedback terms");
        options.addOption("lambda", true, "RM3 lambda");
        options.addOption("startTime", true, "Collection start time");
        options.addOption("endTime", true, "Collection end time");
        options.addOption("interval", true, "Collection interval");
        options.addOption("output", true, "path to output file");
        return options;
    }

}
