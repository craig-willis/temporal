package edu.gslis.temporal.main;

import java.io.File;
import java.io.FileInputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.TimeZone;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import edu.gslis.docscoring.QueryDocScorer;
import edu.gslis.eval.Qrels;
import edu.gslis.filtering.main.config.BatchConfig;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.scorers.RecencyScorer;
import edu.gslis.textrepresentation.FeatureVector;

public class RunRecencyScorer extends RunScorerBase {
    
    //long INTERVAL_DAY = 60*60*24;
    //long INTERVAL_WEEK = 7*60*60*24;
    //long INTERVAL = INTERVAL_DAY;
    
    public RunRecencyScorer(BatchConfig config) {
        super(config);
    }

    public SearchHits run(GQuery query, IndexWrapper index, Qrels trainQrels, 
            Qrels testQrels, QueryDocScorer scorer, long startTime, long endTime, 
            long interval, String dateFormat, String tsIndex) 
    {
        FeatureVector surfaceForm = new FeatureVector(stopper);
        Iterator<String> queryTerms = query.getFeatureVector().iterator();
        while(queryTerms.hasNext()) {
            String term = queryTerms.next();
            surfaceForm.addTerm(term, query.getFeatureVector().getFeatureWeight(term));
        }
        query.setFeatureVector(surfaceForm);
                    
        System.out.println(query);
        
        // Get the top-K hits
        SearchHits hits = index.runQuery(query, 500);
        
        //String dateFormatStr = "yyMMdd"; 
        SimpleDateFormat df = null;
        if (!StringUtils.isEmpty(dateFormat)) {
            df = new SimpleDateFormat(dateFormat);        
            df.setTimeZone(TimeZone.getTimeZone("GMT"));
        }
        SearchHits results = new SearchHits();
        try
        {
            //long max = df.parse("941231").getTime()/1000;
            //long now = System.currentTimeMillis()/1000;
    
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
            
            scorer.setQuery(query);
            scorer.init();
            ((RecencyScorer)scorer).setRate(mleRate);
            ((RecencyScorer)scorer).setMax(endTime);
            ((RecencyScorer)scorer).setInterval(interval);
    
            
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
    
    public static void main(String[] args) throws Exception 
    {
        File yamlFile = new File(args[0]);
        if(!yamlFile.exists()) {
            System.err.println("you must specify a parameter file to run against.");
            System.exit(-1);
        }

        // Read the yaml config
        Yaml yaml = new Yaml(new Constructor(BatchConfig.class));
        BatchConfig config = (BatchConfig)yaml.load(new FileInputStream(yamlFile));

        RunRecencyScorer runner = new RunRecencyScorer(config);
        runner.runBatch();
    }
}
