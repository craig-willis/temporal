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
import edu.gslis.old.temporal.scorers.TimeSeriesIndex;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Only score the temporal language model -- no documents
 */
public class RunSimpleTLMScorer extends RunScorerBase {
    
    
    public RunSimpleTLMScorer(BatchConfig config) {
        super(config);
    }

    public SearchHits run(GQuery query, IndexWrapper index, Qrels trainQrels, 
            Qrels testQrels, QueryDocScorer scorer, long startTime, long endTime, long interval, 
            String dateFormat, String tsIndexPath) 
    {
        FeatureVector surfaceForm = new FeatureVector(stopper);
        Iterator<String> queryTerms = query.getFeatureVector().iterator();
        while(queryTerms.hasNext()) {
            String term = queryTerms.next();
            surfaceForm.addTerm(term, query.getFeatureVector().getFeatureWeight(term));
        }
        query.setFeatureVector(surfaceForm);
                    
        System.out.println(query);
        SearchHits results = new SearchHits();

        long numBins = (endTime - startTime)/interval;
        try
        {
            TimeSeriesIndex tsIndex = new TimeSeriesIndex();
            tsIndex.open(tsIndexPath, true);
    
            double[] total = tsIndex.get("_total_");
    
            for (int t=0; t<numBins; t++)
            {                
                double ll = 0;

                Iterator<String> queryIterator = query.getFeatureVector().iterator();
                while(queryIterator.hasNext()) 
                {
                    String feature = queryIterator.next();
                    double queryWeight = query.getFeatureVector().getFeatureWeight(feature);
                    
                    double[] series = tsIndex.get(feature);
                    
                    double tempPr = (1 + series[t]) / (1 + total[t]);  
                    double score = queryWeight*Math.log(tempPr);
                    
                    ll += score;
                }
                if (ll != 0) {
                    SearchHit hit = new SearchHit();
                    hit.setDocno(String.valueOf(t));
                    hit.setScore(ll);
                    results.add(hit);
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        }                
        results.rank();
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

        RunSimpleTLMScorer runner = new RunSimpleTLMScorer(config);
        runner.runBatch();
    }
}
