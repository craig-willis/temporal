package edu.gslis.main.temporal;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;

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
import edu.gslis.temporal.expansion.ScorableFeatureVector;
import edu.gslis.temporal.expansion.TemporalClusterFeedback;
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
        String topic = cl.getOptionValue("topic");
        int numFbDocs =Integer.parseInt(cl.getOptionValue("numFbDocs", "1000"));
        int numFbTerms =Integer.parseInt(cl.getOptionValue("numFbTerms", "20"));
        double lambda =Double.parseDouble(cl.getOptionValue("lambda", "0.5"));
        String outputPath = cl.getOptionValue("output");
        
        Writer writer = new FileWriter(outputPath);
        writer.write("<parameters>\n");
        GQueries queries = null;
        if (topicsPath.endsWith("indri")) 
            queries = new GQueriesIndriImpl();
        else
            queries = new GQueriesJsonImpl();
        queries.read(topicsPath);
        
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
        index.setTimeFieldName(Indexer.FIELD_EPOCH);
        if (topic != null) 
        {
            GQuery query = queries.getNamedQuery(topic);
            getTemporalRM(query, index, numFbDocs, numFbTerms, lambda, writer);

        }
        else
        {
            Iterator<GQuery> queryIt = queries.iterator();
            while (queryIt.hasNext()) {
                GQuery query = queryIt.next();
                getTemporalRM(query, index, numFbDocs, numFbTerms, lambda, writer);
            }
        }
        writer.write("</parameters>\n");
        writer.close();
    }
    
    public static void getTemporalRM(GQuery query, IndexWrapper index, int numFbDocs, int numFbTerms,
            double lambda, Writer writer)  throws IOException
    {
    	System.out.println("Generating temporal feedback for query " + query.getTitle());
    	TemporalClusterFeedback rm = new TemporalClusterFeedback();
        rm.setDocCount(numFbDocs);
        rm.setTermCount(numFbTerms);
        rm.setIndex(index);
        rm.setOriginalQuery(query);
        rm.build();
        FeatureVector rmVector = rm.asFeatureVector();
        /*
        rm.buildMultipleRM();
        List<ScorableFeatureVector> rmFvs = rm.getFeatureVectors();
        FeatureVector rmVector = rmFvs.get(0);
        for (int i=1; i<4; i++) {
          
            if (rmFvs.size() > i+1) {
	            ScorableFeatureVector fv = rmFvs.get(i);
	            rmVector = FeatureVector.interpolate(rmVector, fv, 0.75);
            }
        }
       
        //FeatureVector rmVector = null;
        //if (rmFvs.size() > 1) {
        //	ScorableFeatureVector fv2 = rmFvs.get(1);
        //	rmVector = FeatureVector.interpolate(fv1, fv2, 0.75);
        //	System.out.println("Score2: " + fv2.getScore());
        //} else {
        //	rmVector = fv1;        	
        //}
        */
        rmVector.clip(numFbTerms);
        rmVector.normalize();
        
        
        FeatureVector feedbackVector =
        FeatureVector.interpolate(query.getFeatureVector(), rmVector, lambda);
        
        GQuery feedbackQuery = new GQuery();
        feedbackQuery.setTitle(query.getTitle());
        feedbackQuery.setText(query.getText());
        feedbackQuery.setFeatureVector(feedbackVector);
        
        writer.write("<query>\n");
        writer.write("<number>" +  query.getTitle() + "</number>\n");
        writer.write("<text>" + toIndri(feedbackQuery) + "</text>\n");
        writer.write("</query>\n");
        
        
        /*
        int i=0;
        for (ScorableFeatureVector rmfv: rmFvs) {
        	double score = rmfv.getScore();
	        rmfv.clip(20);
	        rmfv.normalize();
	        System.out.println(rmfv.toString(20));
	        i++;
        }
        */
        
        
        /*
        FeatureVector fv = rm.asFeatureVector();
        fv.clip(numFbTerms);
        fv.normalize();
        System.out.println(fv);
        */

        /*
        List<ScorableFeatureVector> rmFvs = rm.getFeatureVectors();
        int i=0;
        for (ScorableFeatureVector rmfv: rmFvs) {
        	double score = rmfv.getScore();
	        rmfv.clip(20);
	        rmfv.normalize();
	        System.out.println(rmfv.toString(20));
	        i++;
        }
        */

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
        options.addOption("topic", true, "Optional single topic number");
        options.addOption("topics", true, "Path to topics file");
        options.addOption("numFbDocs", true, "Number of feedback docs");
        options.addOption("numFbTerms", true, "Number of feedback terms");
        options.addOption("lambda", true, "RM3 lambda");
        options.addOption("output", true, "path to output file");
        return options;
    }

}
