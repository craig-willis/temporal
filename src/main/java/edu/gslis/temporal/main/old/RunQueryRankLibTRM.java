package edu.gslis.temporal.main.old;

import java.io.BufferedWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.Iterator;
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
import edu.gslis.temporal.main.old.YAMLConfigBase;
import edu.gslis.temporal.scorers.RerankingScorer;
import edu.gslis.textrepresentation.FeatureVector;


/** 
 * 
 */
public class RunQueryRankLibTRM extends YAMLConfigBase 
{
    public static final String NAME_OF_TIMESTAMP_FIELD = "timestamp";
    static final int NUM_THREADS = 10;
    
    public RunQueryRankLibTRM(BatchConfig config) {
        super(config);
    }

    public void runBatch(String model) throws Exception 
    {
        initGlobals();
        
        List<CollectionConfig> collections = config.getCollections();
        for(CollectionConfig collection: collections) 
        {
            String collectionName = collection.getName();
            System.out.println("Processing " + collectionName);
            
            String qrelPath = collection.getTrainQrels();
            Qrels qrels = new Qrels(qrelPath, true, collection.getRelLevel());
            
            long startTime = collection.getStartDate();
            long endTime = collection.getEndDate();
            long interval = collection.getInterval();
            Map<String, String> queryFiles = collection.getQueries();
            for (String queryFileName: queryFiles.keySet()) {
                System.err.println(collectionName + " " + queryFileName);
                String queryFilePath = queryFiles.get(queryFileName);
                
                GQueries queries = null;
                if (queryFilePath.endsWith("indri")) 
                    queries = new GQueriesIndriImpl();
                else
                    queries = new GQueriesJsonImpl();

                
                queries.setMetadataField(NAME_OF_TIMESTAMP_FIELD);
                queries.read(queryFilePath);
                
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
                    
                    String outputPath = outputDir + File.separator + config.getRunPrefix()  + "-" + scorerName + ".out";
                    
                    outputDir.mkdirs();                        
           
                    FileWriter outputWriter = new FileWriter(outputPath);

                    for (double lambda=0.1; lambda < 1.0; lambda += 0.1) {
                        for (int fbDocs = 10; fbDocs < 50; fbDocs += 10) {
                            for (int fbTerms = 10; fbTerms < 50; fbTerms += 10) {
                                
                                System.out.println(scorerName + " " + lambda + ":" + fbDocs + ":" + fbTerms);
                                 
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
                                             docScorer.setParameter(paramName, (String)obj);
                                         }
                                     }
                                     docScorer.init();
                             
                                     if (model.equals("term")) {
                                         QueryRunnerTERM worker = new QueryRunnerTERM();
                                         worker.setDocScorer(docScorer);
                                         worker.setIndex(index);
                                         worker.setStartTime(startTime);
                                         worker.setEndTime(endTime);
                                         worker.setInterval(interval);
                                         worker.setQrels(qrels);
                                         worker.setQuery(query);
                                         worker.setStopper(stopper);
                                         worker.setRankLibWriter(outputWriter);
                                         worker.setCollectionStats(corpusStats);
                                         worker.setNumFeedbackDocs(fbDocs);
                                         worker.setNumFeedbackTerms(fbTerms);
                                         worker.setStdDev(scorerConfig.getStdDev()[0]);
                                         worker.setRmLambda(lambda);
                                         executor.execute(worker);
                                     }     
                                     if (model.equals("rm")) {
                                         QueryRunnerRM worker = new QueryRunnerRM();
                                         worker.setDocScorer(docScorer);
                                         worker.setIndex(index);
                                         worker.setStartTime(startTime);
                                         worker.setEndTime(endTime);
                                         worker.setInterval(interval);
                                         worker.setQrels(qrels);
                                         worker.setQuery(query);
                                         worker.setStopper(stopper);
                                         worker.setRankLibWriter(outputWriter);
                                         worker.setCollectionStats(corpusStats);
                                         worker.setNumFeedbackDocs(fbDocs);
                                         worker.setNumFeedbackTerms(fbTerms);
                                         worker.setStdDev(scorerConfig.getStdDev()[0]);
                                         worker.setRmLambda(lambda);
                                         executor.execute(worker);
                                     }                            
                                 }
                                 executor.shutdown();
                                 // Wait until all threads are finish
                                 executor.awaitTermination(360, TimeUnit.MINUTES);
                                 System.out.println("Finished all threads");

                            }
                        }
                    }
                    outputWriter.close();
                }                
            }
        }    
    }
    
    public static void main(String[] args) throws Exception 
    {
        File yamlFile = new File(args[0]);
        if(!yamlFile.exists()) {
            System.err.println("you must specify a parameter file to run against.");
            System.exit(-1);
        }
        
        
        String model = "trm";
        if (args.length == 2) {
            model = args[1];
        }

        // Read the yaml config
        Yaml yaml = new Yaml(new Constructor(BatchConfig.class));
        BatchConfig config = (BatchConfig)yaml.load(new FileInputStream(yamlFile));

        RunQueryRankLibTRM runner = new RunQueryRankLibTRM(config);
        runner.runBatch(model);
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
    
}
