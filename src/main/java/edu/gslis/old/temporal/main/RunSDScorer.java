package edu.gslis.old.temporal.main;

import java.io.File;
import java.io.FileInputStream;
import java.util.Iterator;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import edu.gslis.docscoring.QueryDocScorer;
import edu.gslis.eval.Qrels;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.main.config.BatchConfig;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Baseline sequential dependency 
 */
public class RunSDScorer extends RunScorerBase {
    
    //long INTERVAL_DAY = 60*60*24;
    
    public RunSDScorer(BatchConfig config) {
        super(config);
    }

    public SearchHits run(GQuery query, IndexWrapper index, Qrels trainQrels, 
            Qrels testQrels, QueryDocScorer scorer, long startDate, long endDate, 
            long interval, String dateFormat, String tsIndex) 
    {
        FeatureVector surfaceForm = new FeatureVector(stopper);
        Iterator<String> queryTerms = query.getFeatureVector().iterator();
        while(queryTerms.hasNext()) {
            String term = queryTerms.next();
            surfaceForm.addTerm(term, query.getFeatureVector().getFeatureWeight(term));
        }
        query.setFeatureVector(surfaceForm);
                    
        // Get the top-K hits
        String sd = index.toDMQuery(query.getText(), "sd", 0.85, 0.10, 0.05);
        System.out.println(sd);
        SearchHits hits = index.runQuery(sd, 500);
        return hits;
        
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

        RunSDScorer runner = new RunSDScorer(config);
        runner.runBatch();
    }
}
