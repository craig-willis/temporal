package edu.gslis.temporal.main;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.lemurproject.kstem.KrovetzStemmer;
import org.lemurproject.kstem.Stemmer;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import ucar.unidata.util.StringUtil;
import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.indexes.KMeansIndex;
import edu.gslis.indexes.LDAIndex;
import edu.gslis.indexes.TimeSeriesIndex;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.main.config.BatchConfig;
import edu.gslis.main.config.CollectionConfig;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.scorers.ClusterScorer;
import edu.gslis.temporal.scorers.LDAScorer;
import edu.gslis.temporal.scorers.RerankingScorer;
import edu.gslis.temporal.scorers.TemporalScorer;
import edu.gslis.temporal.scorers.TimeLDA;
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
public class RunQuery extends YAMLConfigBase 
{
    static final String NAME_OF_TIMESTAMP_FIELD = "timestamp";
    static final int NUM_RESULTS = 1000;
    static final int NUM_FEEDBACK_TERMS = 20;
    static final int NUM_FEEDBACK_DOCS = 20;
    static final double LAMBDA = 0.5;
    
    public RunQuery(BatchConfig config) {
        super(config);
    }

    public void runBatch() throws Exception 
    {
        initGlobals();
        setupScorers();
        
        List<CollectionConfig> collections = config.getCollections();
        for(CollectionConfig collection: collections) 
        {
            String collectionName = collection.getName();

            String timeSeriesDBPath = collection.getTsDB();
            String clusterIndexPath = collection.getClusterIndex();
            String clusterDBPath = collection.getClusterDB();
            String ldaDBPath = collection.getLdaDB();
        
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
                
                TimeSeriesIndex timeSeriesIndex = new TimeSeriesIndex();
                if (StringUtil.notEmpty(timeSeriesDBPath))
                    timeSeriesIndex.open(timeSeriesDBPath, true);
                
                KMeansIndex clusterIndex = new KMeansIndex();
                if (StringUtil.notEmpty(clusterIndexPath))
                    clusterIndex.open(clusterIndexPath, clusterDBPath, true);
                
                LDAIndex ldaIndex = new LDAIndex();
                if (StringUtil.notEmpty(ldaDBPath))                
                    ldaIndex.open(ldaDBPath, true);
                                    
                String corpusStatsClass = config.getBgStatType();
                CollectionStats corpusStats = (CollectionStats)loader.loadClass(corpusStatsClass).newInstance();
                corpusStats.setStatSource(indexPath);
                                       
                for (String scorerName: scorers.keySet()) 
                {
                    RerankingScorer docScorer = scorers.get(scorerName);

                    docScorer.setCollectionStats(corpusStats);

                    if (docScorer instanceof TemporalScorer) {
                        ((TemporalScorer)docScorer).setIndex(timeSeriesIndex);
                        ((TemporalScorer)docScorer).setStartTime(startTime);
                        ((TemporalScorer)docScorer).setEndTime(endTime);
                        ((TemporalScorer)docScorer).setInterval(interval);
                    }
                    if (docScorer instanceof ClusterScorer)  {
                        ((ClusterScorer)docScorer).setIndex(clusterIndex);
                    }
                    if (docScorer instanceof LDAScorer)
                        ((LDAScorer)docScorer).setIndex(ldaIndex);

                    if (scorerName.contains("tlda"))
                        ((TimeLDA)docScorer).setIndex(ldaIndex);

                    
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
                    
                    Stemmer stemmer = new KrovetzStemmer();

                    Iterator<GQuery> queryIterator = queries.iterator();
                    while(queryIterator.hasNext()) 
                    {                            
                        GQuery query = queryIterator.next();
                        System.err.println(query.getTitle() + ":" + query.getText());
                        String queryText = query.getText().trim();
                        String[] terms = queryText.split("\\s+");
                        String stemmedQuery = "";
                        for (String term: terms) {
                            if (!stopper.isStopWord(term))
                                stemmedQuery += stemmer.stem(term) + " ";
                        }
                        stemmedQuery = stemmedQuery.trim();
                        query.setText(stemmedQuery);
                        FeatureVector fv = new FeatureVector(stemmedQuery, stopper);
                        query.setFeatureVector(fv);
                        System.err.println("\t stemmed: " + query.getText());

                                    
                        docScorer.setQuery(query);
                        
                        SearchHits results = index.runQuery(query, NUM_RESULTS);
                        docScorer.init(results);
                             
                        Iterator<SearchHit> it = results.iterator();
                        while (it.hasNext()) {
                            SearchHit hit = it.next();
                            double score = docScorer.score(hit);
                            hit.setScore(score);
                        }
                        results.rank();
                        
                        // Feedback model
                        FeedbackRelevanceModel rm3 = new FeedbackRelevanceModel();
                        rm3.setDocCount(NUM_FEEDBACK_DOCS);
                        rm3.setTermCount(NUM_FEEDBACK_TERMS);
                        rm3.setIndex(index);
                        rm3.setStopper(stopper);
                        rm3.setRes(results);
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

                        trecFormattedWriter.write(results, query.getTitle());
                        trecFormattedWriterRm3.write(rm3results, query.getTitle());
                    }
                    trecFormattedWriter.close();
                    trecFormattedWriterRm3.close();
                }
                
                timeSeriesIndex.close();
                clusterIndex.close();
                ldaIndex.close();
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

        RunQuery runner = new RunQuery(config);
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
