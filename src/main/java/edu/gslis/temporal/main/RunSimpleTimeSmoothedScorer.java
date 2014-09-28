package edu.gslis.temporal.main;

import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.TimeZone;

import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import edu.gslis.docscoring.QueryDocScorer;
import edu.gslis.eval.Qrels;
import edu.gslis.filtering.main.config.BatchConfig;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.scorers.SimpleTimeSmoothedScorer;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Rescore top-k documents by smoothing using linear combination of p(w|T) and p(w|C)
 */
public class RunSimpleTimeSmoothedScorer extends RunScorerBase {
    
    
    public RunSimpleTimeSmoothedScorer(BatchConfig config) {
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
                
        SimpleDateFormat df = null;
        if (!StringUtils.isEmpty(dateFormat)) {
            df = new SimpleDateFormat(dateFormat);        
            df.setTimeZone(TimeZone.getTimeZone("GMT"));
        }
        scorer.setQuery(query);
        scorer.init();
        ((SimpleTimeSmoothedScorer)scorer).setStartTime(startTime);
        ((SimpleTimeSmoothedScorer)scorer).setEndTime(endTime);
        ((SimpleTimeSmoothedScorer)scorer).setInterval(interval);
        ((SimpleTimeSmoothedScorer)scorer).setDateFormat(df);
        
        SearchHits results = new SearchHits();
        try
        {
    
            Iterator<SearchHit> it = hits.iterator();
            while (it.hasNext()) {
                SearchHit hit = it.next();
                double score = scorer.score(hit);
                hit.setScore(score);
                results.add(hit);
            }
                        
            results.rank();
        
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        System.out.println("Done\n");
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

        RunSimpleTimeSmoothedScorer runner = new RunSimpleTimeSmoothedScorer(config);
        runner.runBatch();
    }
}
