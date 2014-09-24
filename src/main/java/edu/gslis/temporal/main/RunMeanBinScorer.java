package edu.gslis.temporal.main;

import java.io.File;
import java.io.FileInputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
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
import edu.gslis.temporal.scorers.BinScorer;
import edu.gslis.textrepresentation.FeatureVector;

public class RunMeanBinScorer extends RunScorerBase {
    
    //long INTERVAL_DAY = 60*60*24;
    //long INTERVAL_WEEK = 7*60*60*24;
    //long INTERVAL = INTERVAL_DAY;
    
    public RunMeanBinScorer(BatchConfig config) {
        super(config);
    }

    public SearchHits run(GQuery query, IndexWrapper index, Qrels trainQrels, 
            Qrels testQrels, QueryDocScorer scorer, long startTime, long endTime, long interval, 
            String dateFormat, String tsIndex) 
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
        
        //long min = 671691600000L;
        //long max = 788853600000L;
        //long interval = 60*60*24*1000;
        DakkaHistogram dakka = new DakkaHistogram(startTime, endTime, interval, dateFormat);
        Map<Long, Integer> hist = dakka.getRunningMeanBins(hits);
        SimpleDateFormat df = null;
        if (!StringUtils.isEmpty(dateFormat)) {
            df = new SimpleDateFormat(dateFormat);        
            df.setTimeZone(TimeZone.getTimeZone("GMT"));
        }
        SearchHits results = new SearchHits();
        try
        {
            //long max = df.parse("941231").getTime()/1000;
    
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
                //long epoch = df.parse(epochStr).getTime()/1000;
                //long t = (max - epoch)/INTERVAL;
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
                
            
            scorer.setQuery(query);
            scorer.init();
            ((BinScorer)scorer).setRate(0.01);
            ((BinScorer)scorer).setBins(bins);
//            ((BinScorer)scorer).setMax(max);
//            ((BinScorer)scorer).setInterval(INTERVAL);
          ((BinScorer)scorer).setMax(endTime);
          ((BinScorer)scorer).setInterval(interval);
          ((BinScorer)scorer).setDateFormat(df);

            
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

        RunMeanBinScorer runner = new RunMeanBinScorer(config);
        runner.runBatch();
    }
}
