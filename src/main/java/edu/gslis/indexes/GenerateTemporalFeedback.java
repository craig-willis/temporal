package edu.gslis.indexes;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
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
import edu.gslis.temporal.expansion.TemporalRM;
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
        long startTime = Long.parseLong(cl.getOptionValue("startTime"));
        long endTime = Long.parseLong(cl.getOptionValue("endTime"));
        long interval = Long.parseLong(cl.getOptionValue("interval"));
        String outputPath = cl.getOptionValue("output");
        
        GQueries queries = null;
        if (topicsPath.endsWith("indri")) 
            queries = new GQueriesIndriImpl();
        else
            queries = new GQueriesJsonImpl();
        queries.read(topicsPath);
        
        FileWriter writer = new FileWriter(outputPath);
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
        if (topic != null) 
        {
            GQuery query = queries.getNamedQuery(topic);
            getTemporalRM(query, index, numFbDocs, numFbTerms, startTime, endTime, interval, lambda, writer);

        }
        else
        {
            Iterator<GQuery> queryIt = queries.iterator();
            while (queryIt.hasNext()) {
                GQuery query = queryIt.next();
                getTemporalRM(query, index, numFbDocs, numFbTerms, startTime, endTime, interval, lambda, writer);
            }
        }
    }
    
    public static void getTemporalRM(GQuery query, IndexWrapper index, int numFbDocs, int numFbTerms,
            long startTime, long endTime, long interval, double lambda, FileWriter writer)  throws IOException
    {
        SearchHits results = index.runQuery(query, numFbDocs);
        
        /*
        TemporalMixtureFeedbackModel term = new TemporalMixtureFeedbackModel();
        term.setIndex(index);
        term.setStopper(null);
        term.setRes(results);
        term.build(startTime, endTime, interval);
        */
        
        FeedbackRelevanceModel rm = new FeedbackRelevanceModel();
        rm.setDocCount(20);
        rm.setTermCount(20);
        rm.setIndex(index);
        rm.setRes(results);
        rm.build();
        FeatureVector rmfv = rm.asFeatureVector();
        rmfv.clip(20);
        rmfv.normalize();
        
        TemporalRM term = new TemporalRM();
        term.setIndex(index);
        term.setStopper(null);
        term.setRes(results);
        term.setDocCount(20);
        term.setTermCount(20);
        term.build(startTime, endTime, interval);
       
        DateFormat df = new SimpleDateFormat("yyyy.MM.dd");
        writer.write(query.getText() + "\n");
        int numBins = (int)((endTime - startTime)/interval);
        for (int i=0; i<numBins; i++) {
            long stime = startTime + interval*i;

            FeatureVector fv = term.asFeatureVector(i);
            fv.normalize();
            System.out.println("Bin=" + i);
            double score = score(query, fv, index);
            writer.write(i + ":" + df.format(stime*1000) + " " + score + "\n");
            writer.write(fv.toString(20) + "\n");
            System.out.println(fv.toString(20));
        }     
        System.out.println(rmfv.toString(20));
        writer.close();
    }
        
    public static double score(GQuery query, FeatureVector fv, IndexWrapper index) {
        double logLikelihood = 0.0;
        Iterator<String> queryIterator = query.getFeatureVector().iterator();
        while(queryIterator.hasNext()) {
            String feature = queryIterator.next();
            
            double freq = fv.getFeatureWeight(feature);
            double len = fv.getLength();
            double cp = (index.termFreq(feature)/ index.termCount());
            double dp = (freq + 1*cp)/ (len + 1);            
            //double dp = (freq/len);
            double queryWeight = query.getFeatureVector().getFeatureWeight(feature);
            logLikelihood += queryWeight * Math.log(dp);
            System.out.println(feature + "=" + queryWeight * Math.log(dp));
        }
        System.out.println("ll=" + logLikelihood);
        return logLikelihood;
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
        options.addOption("topic", true, "Optional single topic number");
        options.addOption("topics", true, "Path to topics file");
        options.addOption("numFbDocs", true, "Number of feedback docs");
        options.addOption("numFbTerms", true, "Number of feedback terms");
        options.addOption("lambda", true, "RM3 lambda");
        options.addOption("startTime", true, "Start time");
        options.addOption("endTime", true, "End time");
        options.addOption("interval", true, "Interval");
        options.addOption("output", true, "Output");
        return options;
    }

}
