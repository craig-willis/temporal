package edu.gslis.indexes;

import java.io.FileWriter;
import java.math.BigDecimal;
import java.util.Iterator;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;

public class GenerateFeedbackQueries 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( GenerateFeedbackQueries.class.getCanonicalName(), options );
            return;
        }
        String indexPath = cl.getOptionValue("index");
        String topicsPath = cl.getOptionValue("topics");
        int numFbDocs =Integer.parseInt(cl.getOptionValue("numFbDocs", "20"));
        int numFbTerms =Integer.parseInt(cl.getOptionValue("numFbTerms", "20"));
        double lambda =Double.parseDouble(cl.getOptionValue("lambda", "0.5"));
        String outputPath = cl.getOptionValue("output");
        
        GQueries queries = null;
        if (topicsPath.endsWith("indri")) 
            queries = new GQueriesIndriImpl();
        else
            queries = new GQueriesJsonImpl();
        queries.read(topicsPath);
        
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
        FileWriter outputWriter = new FileWriter(outputPath);
        Iterator<GQuery> queryIt = queries.iterator();
        outputWriter.write("<parameters>\n");
        while (queryIt.hasNext()) {
            GQuery query = queryIt.next();
            SearchHits results = index.runQuery(query, numFbDocs);
            
            FeedbackRelevanceModel rm3 = new FeedbackRelevanceModel();
            rm3.setDocCount(numFbDocs);
            rm3.setTermCount(numFbTerms);
            rm3.setIndex(index);
            rm3.setStopper(null);
            rm3.setRes(results);
            rm3.build();
            FeatureVector rmVector = rm3.asFeatureVector();
            rmVector = cleanModel(rmVector);
            rmVector.clip(numFbTerms);
            rmVector.normalize();
            FeatureVector feedbackVector =
            FeatureVector.interpolate(query.getFeatureVector(), rmVector, lambda);
            
            GQuery feedbackQuery = new GQuery();
            feedbackQuery.setTitle(query.getTitle());
            feedbackQuery.setText(query.getText());
            feedbackQuery.setFeatureVector(feedbackVector);
            

            outputWriter.write("<query>\n");
            outputWriter.write("<number>" +  query.getTitle() + "</number>\n");
            outputWriter.write("<text>" + toIndri(feedbackQuery) + "<text>\n");
            outputWriter.write("</query>\n");

        }
        outputWriter.write("</parameters>\n");
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
    
    public static FeatureVector cleanModel(FeatureVector model) {
        FeatureVector cleaned = new FeatureVector(null);
        Iterator<String> it = model.iterator();
        while(it.hasNext()) {
            String term = it.next();
            if(term.length() < 3 || term.matches(".*[0-9].*"))
                continue;
            cleaned.addTerm(term, model.getFeatureWeight(term));
        }
        cleaned.normalize();
        return cleaned;
    }
    
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "Path to input index");
        options.addOption("topics", true, "Path to topics file");
        options.addOption("numFbDocs", true, "Number of feedback docs");
        options.addOption("numFbTerms", true, "Number of feedback terms");
        options.addOption("output", true, "Path to output topics file");
        options.addOption("lambda", true, "RM3 lambda");
        return options;
    }

}
