package edu.gslis.temporal.main;

import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import edu.gslis.docscoring.support.IndexBackedCollectionStats;
import edu.gslis.filtering.session.FilterSession;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.scorers.BinScorer;
import edu.gslis.temporal.scorers.RecencyScorer;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;

/**
 * Generates queries based on true or pseudo feedback
 * using RM3. 
 * 
 * @author cwillis
 *
 */
public class RunTemporalFeedbackRM3 {
    
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help") || !cl.hasOption("queries")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( RunTemporalFeedbackRM3.class.getCanonicalName(), options );
            return;
        }
        
        String queryFilePath = cl.getOptionValue("queries", "");
        GQueries queries = new GQueriesJsonImpl();
        queries.setMetadataField(FilterSession.NAME_OF_EMIT_STATUS_FIELD);
        queries.setMetadataField(FilterSession.NAME_OF_CONSTRAINT_FIELD);
        queries.setMetadataField(FilterSession.NAME_OF_TIMESTAMP_FIELD);
        queries.read(queryFilePath);

        String indexPath = cl.getOptionValue("index", "");
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
        System.err.println(indexPath);
        
        String numFbDocsOpt = cl.getOptionValue("numFbDocs", "20");
        int numFbDocs = Integer.parseInt(numFbDocsOpt);

        String numFbTermsOpt = cl.getOptionValue("numFbTerms", "20");
        int numFbTerms = Integer.parseInt(numFbTermsOpt);
        
        String lambdaOpt = cl.getOptionValue("lambda", "0.5");
        double lambda = Double.parseDouble(lambdaOpt);
        
        String startStr = cl.getOptionValue("start");
        long start = Long.parseLong(startStr);
        String endStr = cl.getOptionValue("end");
        long end = Long.parseLong(endStr);
        String intervalStr = cl.getOptionValue("interval");
        long interval = Long.parseLong(intervalStr);
        String method = cl.getOptionValue("method");
        
        
        Stopper stopper = null;
        String stopperPath = cl.getOptionValue("stopper");
        stopperPath = "./data/stoplist.kba";
        if (!StringUtils.isEmpty(stopperPath))
            stopper = new Stopper(stopperPath);
        
        Writer output = null;
        String outputFile = cl.getOptionValue("output");
        if (StringUtils.isEmpty(outputFile))
            output = new OutputStreamWriter(System.out);
        else 
            output = new FileWriter(outputFile);
            
        GQueries feedbackQueries = new GQueriesJsonImpl();
        feedbackQueries.setMetadataField(FilterSession.NAME_OF_TIMESTAMP_FIELD);
        Iterator<GQuery> queryIterator = queries.iterator();
        while(queryIterator.hasNext()) 
        {                            
            GQuery query = queryIterator.next();
            System.err.println(query.getTitle());
            
            GQuery newQuery = pseudoFb(query, index, numFbDocs, numFbTerms, lambda, stopper, start,
                    end, interval, method, indexPath);
                
            newQuery.setMetadata(FilterSession.NAME_OF_TIMESTAMP_FIELD, 
                    query.getMetadata(FilterSession.NAME_OF_TIMESTAMP_FIELD));
            feedbackQueries.addQuery(newQuery);
        }
        
        output.write(feedbackQueries.toString());
        output.close();
    }
    
    public static GQuery pseudoFb(GQuery query, IndexWrapper index, int numFbDocs, int numFbTerms, 
            double lambda, Stopper stopper, long start, long end, long interval, String method,
            String indexPath)
    {        
        SearchHits results = index.runQuery(query, numFbDocs);
        
        if (method.equals("recency"))            
            results = recency(query, results, start, end, interval, null, indexPath);
        else
            results = dakka(query, results, start, end, interval, null, method, indexPath);
            

        return rm(results, query, index, numFbTerms, lambda, stopper);
    }    
    
 

    private static GQuery rm(SearchHits results, GQuery query, IndexWrapper index, 
            int numFbTerms, double lambda, Stopper stopper)
    {
        String text = query.getText();
        if (stopper != null)
            text = stopper.apply(text);
        FeatureVector textVector = new FeatureVector(text, null);
        textVector.normalize();
        
        if(results.size() > 0) {
            FeedbackRelevanceModel rm3 = new FeedbackRelevanceModel();
            rm3.setDocCount(results.size());
            rm3.setTermCount(numFbTerms);
            rm3.setIndex(index);
            rm3.setStopper(stopper);
            rm3.setRes(results);
            rm3.build();
            FeatureVector rmVector = rm3.asFeatureVector();
            rmVector = RunTemporalFeedbackRM3.cleanModel(rmVector);
            rmVector.clip(numFbTerms);
            rmVector.normalize();
            textVector = FeatureVector.interpolate(textVector, rmVector, lambda);
        }
        
        GQuery newQuery = new GQuery();
        newQuery.setTitle(query.getTitle());
        newQuery.setText(query.getText());
        newQuery.setFeatureVector(textVector);
        
        return newQuery;
    }
    
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("queries", true, "Path to queries");
        options.addOption("qrels", true, "Path to qrels file");
        options.addOption("index", true, "Path to index");
        options.addOption("numFbDocs", true, "Number of feedback documents (default: 20)");
        options.addOption("numFbTerms", true, "Number of feedback terms (default: 20)");
        options.addOption("lambda", true, "RM coefficient (default: 0.5)");
        options.addOption("stopper", true, "Stop word list (default: none)");
        options.addOption("output", true, "Output file (default: stdout)");
        options.addOption("help", false, "Print this help message");
        options.addOption("start", true, "Collection start date");
        options.addOption("end", true, "Collection end date");
        options.addOption("interval", true, "Collection interval");
        options.addOption("method", true, "One of: recency, day, mean");
        
        return options;
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
    
    public static SearchHits recency(GQuery query, SearchHits hits, long startTime, long endTime, long interval, 
            String dateFormat, String indexPath) 
    {
        //String dateFormatStr = "yyMMdd"; 
        SimpleDateFormat df = null;
        if (!StringUtils.isEmpty(dateFormat)) {
            df = new SimpleDateFormat(dateFormat);        
            df.setTimeZone(TimeZone.getTimeZone("GMT"));
        }
        SearchHits results = new SearchHits();
        try
        {
            // Estimate the rate parameter
            Iterator<SearchHit> it = hits.iterator();
            DescriptiveStatistics stat = new DescriptiveStatistics();
            while (it.hasNext()) {
                SearchHit hit = it.next();
                if (hit.getMetadataValue(Indexer.FIELD_EPOCH) == null) {
                    System.err.println("Missing epoch: " + hit.getDocID()) ;
                    continue;
                }
                String epochStr = String.valueOf((((Double)hit.getMetadataValue(Indexer.FIELD_EPOCH)).longValue()));  
 
                long docTime = 0;
                if (df != null)
                    docTime = df.parse(epochStr).getTime()/1000;
                else 
                    docTime = Long.valueOf(epochStr);
                //long epoch = df.parse(epochStr).getTime()/1000;
                //long t = (max - epoch)/INTERVAL;
                long t = (docTime - startTime)/interval; // normalized time
                stat.addValue(t);
            }
            double mleRate = 1/stat.getMean();
            System.out.println("MLE Rate = " + mleRate);
            
            RecencyScorer scorer = new RecencyScorer();
            scorer.setParameter("mu", 2500);
            scorer.setQuery(query);
            IndexBackedCollectionStats stats = new IndexBackedCollectionStats();
            stats.setStatSource(indexPath);
            scorer.setCollectionStats(stats);
            scorer.init();
            scorer.setRate(mleRate);
            scorer.setMax(endTime);
            scorer.setInterval(interval);
    
            
            // Rescore
            it = hits.iterator();
            while (it.hasNext()) {
                SearchHit hit = it.next();
                double score = scorer.score(hit);
                hit.setScore(score);
                results.add(hit);
            }
            
            results.rank();
        
        } catch (ParseException e) {
            e.printStackTrace();
        }
        
        return results;        
    }
    
    public static SearchHits dakka(GQuery query, SearchHits hits, long startTime, long endTime, long interval, 
            String dateFormat, String method, String indexPath) 
    {
        DakkaHistogram dakka = new DakkaHistogram(startTime, endTime, interval, dateFormat);
        Map<Long, Integer> hist = null;
        if (method.equals("mean")) 
            hist = dakka.getRunningMeanBins(hits);
        else 
            hist = dakka.getDayBins(hits);
        
        SimpleDateFormat df = null;
        if (!StringUtils.isEmpty(dateFormat)) {
            df = new SimpleDateFormat(dateFormat);        
            df.setTimeZone(TimeZone.getTimeZone("GMT"));
        }
        SearchHits results = new SearchHits();
        try
        {
            Iterator<SearchHit> it = hits.iterator();
            while (it.hasNext()) {
                SearchHit hit = it.next();
                if (hit.getMetadataValue(Indexer.FIELD_EPOCH) == null) {
                    System.err.println("Missing epoch: " + hit.getDocID()) ;
                    continue;
                }
                String epochStr = String.valueOf((((Double)hit.getMetadataValue(Indexer.FIELD_EPOCH)).longValue()));  
 
                long docTime = 0;
                if (df != null) {
                    docTime = df.parse(epochStr).getTime()/1000;                    
                }
                else
                    docTime = Long.valueOf(epochStr);
                
                long t = (endTime - docTime)/interval;
                int count = 0;
                if (hist.get(t) != null)
                    count = hist.get(t);
                count ++;
                hist.put(t, count);

            }
            
            ValueComparator vc =  new ValueComparator(hist);
            Map<Long,Integer> sorted = new TreeMap<Long,Integer>(vc);
            sorted.putAll(hist);
            
            int bin = 1;
            Map<Long, Integer> bins = new TreeMap<Long, Integer>();
            for (long key: sorted.keySet()) {
                bins.put(key,  bin);
                bin++;
            }
                
            
            BinScorer scorer = new BinScorer();
            IndexBackedCollectionStats stats = new IndexBackedCollectionStats();
            stats.setStatSource(indexPath);
            scorer.setCollectionStats(stats);
            scorer.setParameter("mu", 2500);

            scorer.setQuery(query);
            scorer.init();
            scorer.setRate(0.01);
            scorer.setBins(bins);
            scorer.setMax(endTime);
            scorer.setInterval(interval);
            scorer.setDateFormat(df);

            
            // Rescore
            it = hits.iterator();
            while (it.hasNext()) {
                SearchHit hit = it.next();
                double score = scorer.score(hit);
                if (Double.isNaN(score) || Double.isInfinite(score)) {
                    System.err.println(query.getText() + "," + hit.getDocno() + "," +  score);
                }
                hit.setScore(score);
                results.add(hit);
            }
            
            results.rank();
        
        } catch (ParseException e) {
            e.printStackTrace();
        }
        
        return results;     
    }
}
