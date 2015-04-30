package edu.gslis.temporal.main;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.eval.Qrels;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.main.config.BatchConfig;
import edu.gslis.main.config.CollectionConfig;
import edu.gslis.main.config.ScorerConfig;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.temporal.scorers.KDELDAScorer;
import edu.gslis.temporal.scorers.LDAScorer;
import edu.gslis.temporal.scorers.RerankingScorer;
import edu.gslis.temporal.scorers.TemporalLDAScorer;
import edu.gslis.temporal.scorers.TemporalScorer;


/** 
 * 
 * Tune parameters using n-fold cross validation.
 */
public class RunQueryRankLib extends YAMLConfigBase 
{
    
    public RunQueryRankLib(BatchConfig config) {
        super(config);
    }
    
    public GQueries readQueries(String queryFilePath) {        
        GQueries queries = null;
        if (queryFilePath.endsWith("indri")) 
            queries = new GQueriesIndriImpl();
        else
            queries = new GQueriesJsonImpl();
                        
        queries.read(queryFilePath);
        return queries;
    }

    public void runBatch() throws Exception 
    {
        initGlobals();
        
        List<CollectionConfig> collections = config.getCollections();
        for(CollectionConfig collection: collections) 
        {
            String collectionName = collection.getName();
            System.out.println("Processing " + collectionName);
            
            String qrelPath = collection.getTrainQrels();
            Qrels qrels = new Qrels(qrelPath, true, collection.getRelLevel());
            
            Map<String, String> queryFiles = collection.getQueries();
            for (String queryFileName: queryFiles.keySet()) {
                System.err.println(collectionName + " " + queryFileName);
                String queryFilePath = queryFiles.get(queryFileName);
                
                GQueries queries = readQueries(queryFilePath);
                
                String indexPath = indexRoot + File.separator + collection.getTestIndex();
                IndexWrapper index = 
                        IndexWrapperFactory.getIndexWrapper(indexPath);
                index.setTimeFieldName(Indexer.FIELD_EPOCH);
                                                    
                String corpusStatsClass = config.getBgStatType();
                CollectionStats corpusStats = (CollectionStats)loader.loadClass(corpusStatsClass).newInstance();
                corpusStats.setStatSource(indexPath);
                                
                List<ScorerConfig> scorerConfigs = config.getScorers();
                for (ScorerConfig scorerConfig: scorerConfigs) 
                {               
                    // Setup the scorers
                    String scorerName = scorerConfig.getName();
                    String className = scorerConfig.getClassName();
                    System.out.println("Running scorer " + scorerName);

                    String runId = prefix + "-" + scorerName + "_" + collectionName + "_" + queryFileName;
                    String rankLibResultsFile = outputDir + File.separator + runId + ".out";
                                        
                    outputDir.mkdirs();                        

                    FileWriter rankLibResultsWriter = new FileWriter(rankLibResultsFile);
                    
                    int numThreads = config.getNumThreads();
                    ExecutorService executor = Executors.newFixedThreadPool(numThreads);
                    Iterator<GQuery> queryIterator = queries.iterator();
                    while(queryIterator.hasNext()) 
                    {                            
                        GQuery query = queryIterator.next();
                                                
                        // Scorer per worker means scorer per query
                        RerankingScorer docScorer = (RerankingScorer)loader.loadClass(className).newInstance();
                        
                        docScorer.setConfig(scorerConfig);
                        docScorer.setCollectionStats(corpusStats);
                        
                        Map<String, Object> params = scorerConfig.getParams();
                        for (String paramName: params.keySet()) {
                            Object obj = params.get(paramName);
                            if (obj instanceof Double) { 
                                docScorer.setParameter(paramName, (Double)obj);
                            }
                            else if (obj instanceof Integer) { 
                                docScorer.setParameter(paramName, (Integer)obj);
                            }
                            else if (obj instanceof String) {
                                if (((String)obj).contains(","))  {
                                    String[] vals = ((String)obj).split(",");
                                    List<Double> dvals = new ArrayList<Double>();
                                    for (String val: vals) {
                                        double dval = Double.parseDouble(val);
                                        dvals.add(dval);
                                    }
                                    docScorer.setParameter(paramName, dvals);
                                } else
                                    docScorer.setParameter(paramName, (String)obj);
                            }
                        }
                        docScorer.init();

                        QueryRunnerRankLib worker = new QueryRunnerRankLib();
                        worker.setDocScorer(docScorer);
                        worker.setIndex(index);
                        worker.setQrels(qrels);
                        worker.setQuery(query);
                        worker.setStopper(stopper);
                        worker.setWriter(rankLibResultsWriter);
                        worker.setCollectionStats(corpusStats);
                        executor.execute(worker);
                    }
                    
                    executor.shutdown();
                    // Wait until all threads are finish
                    executor.awaitTermination(360, TimeUnit.MINUTES);
                    System.out.println("Finished all threads");
                    
                    rankLibResultsWriter.close();
                }
            }
        }    
    }
    
    public static void main(String[] args) throws Exception 
    {
//        doPermute();
        File yamlFile = new File(args[0]);
        if(!yamlFile.exists()) {
            System.err.println("you must specify a parameter file to run against.");
            System.exit(-1);
        }
        
        // Read the yaml config
        Yaml yaml = new Yaml(new Constructor(BatchConfig.class));
        BatchConfig config = (BatchConfig)yaml.load(new FileInputStream(yamlFile));

        RunQueryRankLib runner = new RunQueryRankLib(config);
        runner.runBatch();
    }
        
    
    public static void doPermute() {
        double[] lambda = {0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9};
        double[] numDocs = {10, 20, 50, 100, 200, 500};
        double[] numTerms = {10, 20, 50, 100, 200, 500};
        double[] alpha = {0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9};
        
        List<double[]> params = new ArrayList<double[]>();
        for (double a: lambda) {
            for (double b: numDocs) {
                for (double c: numTerms) {
                    for (double d: alpha) {
                        double[] values = {a, b, c, d};
                        params.add(values);
                    }
                }
            }
        }
        System.out.println(params.size());

    }

}
