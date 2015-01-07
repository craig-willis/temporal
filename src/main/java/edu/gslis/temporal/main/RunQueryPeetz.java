package edu.gslis.temporal.main;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import edu.gslis.docscoring.support.CollectionStats;
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
import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.scorers.KDEScorer;
import edu.gslis.temporal.scorers.RerankingScorer;
import edu.gslis.temporal.util.PeetzHistogram;
import edu.gslis.temporal.util.RKernelDensity;
import edu.gslis.textrepresentation.FeatureVector;


/** 
 * 
 * edu.gslis.temporal.main.RunQuery config/config.yaml
 * 
 * For each configured collection
 *    For each configured scorer
 *       For each configured set of topics
 *          Run initial retrieval
 *          Rescore
 *          Calculate RM3 model
 *          Rescore
 *          Output results
 */
public class RunQueryPeetz extends YAMLConfigBase 
{
    static final String NAME_OF_TIMESTAMP_FIELD = "timestamp";
    static final int NUM_RESULTS = 1000;
    static final int NUM_FEEDBACK_TERMS = 20;
    static final int NUM_FEEDBACK_DOCS = 20;
    static final double LAMBDA = 0.5;
    static final int NUM_THREADS = 10;
    
    public RunQueryPeetz(BatchConfig config) {
        super(config);
    }

    public void runBatch() throws Exception 
    {
        initGlobals();
        
        List<CollectionConfig> collections = config.getCollections();
        for(CollectionConfig collection: collections) 
        {
            String collectionName = collection.getName();
            System.out.println("Processing " + collectionName);
        
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
                    System.out.println("Running scorer " + scorerName);

                                        
                    String runIdRm3 = prefix + "-rm3-" + scorerName + "_" + collectionName + "_" + queryFileName;
                    String trecResultsFileRm3 = outputDir + File.separator + runIdRm3 + ".out";
                    
                    outputDir.mkdirs();                        

                    Writer trecResultsWriterRm3 = new BufferedWriter(new FileWriter(trecResultsFileRm3));
                    FormattedOutputTrecEval trecFormattedWriterRm3 = new FormattedOutputTrecEval();
                    trecFormattedWriterRm3.setRunId(runIdRm3);
                    trecFormattedWriterRm3.setWriter(trecResultsWriterRm3);
                    
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
                


                        System.err.println(query.getTitle() + ":" + query.getText());
                        String queryText = query.getText().trim();
                        String[] terms = queryText.split("\\s+");
                        String stoppedQuery = "";
                        for (String term: terms) {
                            if (!stopper.isStopWord(term))
                                stoppedQuery += term + " ";
                        }
                        stoppedQuery = stoppedQuery.trim();
                        query.setText(stoppedQuery);
                        FeatureVector fv = new FeatureVector(stoppedQuery, stopper);
                        query.setFeatureVector(fv);
                                    
                        docScorer.setQuery(query);
                        
                        SearchHits results = index.runQuery(query, NUM_RESULTS);
                        docScorer.init(results);
                        
                        // Peetz feedback model
                             
                        Iterator<SearchHit> it = results.iterator();
                        while (it.hasNext()) {
                            SearchHit hit = it.next();
                            double score = docScorer.score(hit);
                            hit.setScore(score);
                        }
                        results.rank();
                        
                        PeetzHistogram hist = new PeetzHistogram(results, startTime, endTime, interval);
                        
                        SearchHits burstDocs = hist.getBurstDocs();
                        burstDocs.rank();

                        // Feedback model
                        FeedbackRelevanceModel rm3 = new FeedbackRelevanceModel();
                        rm3.setDocCount(burstDocs.size());
                        rm3.setTermCount(NUM_FEEDBACK_TERMS);
                        rm3.setIndex(index);
                        rm3.setStopper(stopper);
                        rm3.setRes(burstDocs);
                        rm3.build();
                        FeatureVector rmVector = rm3.asFeatureVector();
                        rmVector = cleanModel(rmVector);
                        rmVector.clip(NUM_FEEDBACK_TERMS);
                        rmVector.normalize();
                        FeatureVector feedbackVector =
                                FeatureVector.interpolate(query.getFeatureVector(), rmVector, LAMBDA);
                        
                        GQuery feedbackQuery = new GQuery();
                        feedbackQuery.setTitle(query.getTitle());
                        feedbackQuery.setText(query.getText());
                        feedbackQuery.setFeatureVector(feedbackVector);
                                                
                        SearchHits rm3results = index.runQuery(feedbackQuery, NUM_RESULTS);
                        
                        docScorer.setQuery(feedbackQuery);
                        docScorer.init(rm3results);
                             
                        it = rm3results.iterator();
                        while (it.hasNext()) {
                            SearchHit hit = it.next();
                            double score = docScorer.score(hit);
                            hit.setScore(score);
                        }
                        
                        rm3results.rank();
                        trecFormattedWriterRm3.write(rm3results, query.getTitle());
                    }
                                        
                    trecFormattedWriterRm3.close();

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

        // Read the yaml config
        Yaml yaml = new Yaml(new Constructor(BatchConfig.class));
        BatchConfig config = (BatchConfig)yaml.load(new FileInputStream(yamlFile));

        RunQueryPeetz runner = new RunQueryPeetz(config);
        runner.runBatch();
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
