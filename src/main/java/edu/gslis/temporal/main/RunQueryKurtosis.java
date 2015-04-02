package edu.gslis.temporal.main;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import ucar.unidata.util.StringUtil;
import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.indexes.TimeSeriesIndex;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.main.config.BatchConfig;
import edu.gslis.main.config.CollectionConfig;
import edu.gslis.main.config.ScorerConfig;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.temporal.scorers.RerankingScorer;
import edu.gslis.temporal.scorers.TemporalScorer;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;



public class RunQueryKurtosis extends YAMLConfigBase 
{
    public static final String NAME_OF_TIMESTAMP_FIELD = "timestamp";
    static final int NUM_THREADS = 10;
    
    public RunQueryKurtosis(BatchConfig config) {
        super(config);
    }

    public void runBatch(boolean rescoreRm3) throws Exception 
    {
        initGlobals();
        
        List<CollectionConfig> collections = config.getCollections();
        for(CollectionConfig collection: collections) 
        {
            String collectionName = collection.getName();
            System.out.println("Processing " + collectionName);

            String timeSeriesDBPath = collection.getTsDB();
            
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
                
                // Calculate KL(CM||TM) for each temporal model
                TimeSeriesIndex timeSeriesIndex = new TimeSeriesIndex();
                int numBins = 0;
                if (interval > 0) 
                    numBins = (int) ((endTime - startTime) / interval);
                double[] klweights = new double[numBins];
                if (StringUtil.notEmpty(timeSeriesDBPath)) {
                    timeSeriesIndex.open(timeSeriesDBPath, true, "csv");
                }
                
                                    
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
                    String trecResultsFile = outputDir + File.separator + runId + ".out";
                    
                    String runIdRm3 = prefix + "-rm3-" + scorerName + "_" + collectionName + "_" + queryFileName;
                    String trecResultsFileRm3 = outputDir + File.separator + runIdRm3 + ".out";
                    
                    outputDir.mkdirs();                        

                    Writer trecResultsWriter = new BufferedWriter(new FileWriter(trecResultsFile));
                    FormattedOutputTrecEval trecFormattedWriter = new FormattedOutputTrecEval();
                    trecFormattedWriter.setRunId(runId);
                    trecFormattedWriter.setWriter(trecResultsWriter);

                    Writer trecResultsWriterRm3 = new BufferedWriter(new FileWriter(trecResultsFileRm3));
                    FormattedOutputTrecEval trecFormattedWriterRm3 = new FormattedOutputTrecEval();
                    trecFormattedWriterRm3.setRunId(runIdRm3);
                    trecFormattedWriterRm3.setWriter(trecResultsWriterRm3);
                    
                    int numThreads = config.getNumThreads();
                    ExecutorService executor = Executors.newFixedThreadPool(numThreads);
                    Iterator<GQuery> queryIterator = queries.iterator();
                    RUtil rutil = new RUtil();
                    while(queryIterator.hasNext()) 
                    {                            
                        GQuery query = queryIterator.next();
                        
                        // Weight query terms by kurtosis
                        FeatureVector fv = query.getFeatureVector();
                        for (String feature: fv.getFeatures()) {
                            double[] ts = timeSeriesIndex.get(feature);
                            double k = rutil.kurtosis(ts);
                            fv.setTerm(feature, k);
                        }
                        fv.normalize();
                        query.setFeatureVector(fv);
                                                
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
                
                        if (docScorer instanceof TemporalScorer) {
                            ((TemporalScorer)docScorer).setIndex(timeSeriesIndex);
                            ((TemporalScorer)docScorer).setStartTime(startTime);
                            ((TemporalScorer)docScorer).setEndTime(endTime);
                            ((TemporalScorer)docScorer).setInterval(interval);
                            ((TemporalScorer)docScorer).setKLs(klweights);
                        }
                                                
                        QueryRunner worker = new QueryRunner();
                        worker.setDocScorer(docScorer);
                        worker.setIndex(index);
                        worker.setQuery(query);
                        worker.setStopper(stopper);
                        worker.setTrecFormattedWriter(trecFormattedWriter);
                        worker.setTrecFormattedWriterRm3(trecFormattedWriterRm3);
                       // worker.setRescoreRm3(rescoreRm3);
                        executor.execute(worker);
                    }
                    
                    executor.shutdown();
                    // Wait until all threads are finish
                    executor.awaitTermination(360, TimeUnit.MINUTES);
                    System.out.println("Finished all threads");
                    
                    trecFormattedWriter.close();
                    trecFormattedWriterRm3.close();

                    rutil.close();
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
        
        boolean rescoreRm3 = true;
        if (args.length == 2) {
            rescoreRm3 = Boolean.parseBoolean(args[1]);
        }

        // Read the yaml config
        Yaml yaml = new Yaml(new Constructor(BatchConfig.class));
        BatchConfig config = (BatchConfig)yaml.load(new FileInputStream(yamlFile));

        RunQueryKurtosis runner = new RunQueryKurtosis(config);
        runner.runBatch(rescoreRm3);
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
