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

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.main.config.BatchConfig;
import edu.gslis.main.config.CollectionConfig;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.queries.expansion.TemporalRelevanceModel;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;


public class RunQuerySDM extends YAMLConfigBase 
{
    static final String NAME_OF_TIMESTAMP_FIELD = "timestamp";
    
    double W1 = 0.85;
    double W2 = 0.1;
    double W3 = 0.05;
    
    public RunQuerySDM(BatchConfig config) {
        super(config);
    }

    public void runBatch() throws Exception 
    {
        initGlobals();
        
        List<CollectionConfig> collections = config.getCollections();
        for(CollectionConfig collection: collections) 
        {
            String collectionName = collection.getName();

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
                
                String scorerName = "sdm";
                
                
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

                
                Iterator<GQuery> queryIterator = queries.iterator();
                while(queryIterator.hasNext()) 
                {                            
                    GQuery query = queryIterator.next();
                                            

                    System.err.println(query.getTitle() + ":" + query.getText());
                    String queryText = query.getText().trim();
                    String[] terms = queryText.split("\\s+");
                    String stoppedQuery = "";
                    for (String term: terms) {
                        if (!stopper.isStopWord(term))
                            stoppedQuery += term  + " ";
                    }
                    stoppedQuery = stoppedQuery.trim();
                    query.setText(stoppedQuery);
                    FeatureVector fv = new FeatureVector(stoppedQuery, stopper);
                    query.setFeatureVector(fv);

                    
                    String dmQuery = index.toDMQuery(query.getText(), "sd", W1, W2, W3);
         
                    System.out.println("\t " + dmQuery);
                    SearchHits results = index.runQuery(dmQuery, QueryRunner.NUM_RESULTS);
                    results.rank();
                    
                    // Feedback model
                    TemporalRelevanceModel rm3 = new TemporalRelevanceModel();
                    rm3.setDocCount(QueryRunner.NUM_FEEDBACK_DOCS);
                    rm3.setTermCount(QueryRunner.NUM_FEEDBACK_TERMS);
                    rm3.setIndex(index);
                    rm3.setStopper(stopper);
                    rm3.setRes(results);
                    rm3.build();
                    FeatureVector rmVector = rm3.asFeatureVector();
                    rmVector = cleanModel(rmVector);
                    rmVector.clip(QueryRunner.NUM_FEEDBACK_TERMS);
                    rmVector.normalize();
                    FeatureVector feedbackVector =
                            FeatureVector.interpolate(query.getFeatureVector(), rmVector, QueryRunner.LAMBDA);
                    
                    GQuery feedbackQuery = new GQuery();
                    feedbackQuery.setTitle(query.getTitle());
                    feedbackQuery.setText(query.getText());
                    feedbackQuery.setFeatureVector(feedbackVector);
                                            
                    SearchHits rm3results = index.runQuery(feedbackQuery, QueryRunner.NUM_RESULTS);
                    rm3results.rank();

                    trecFormattedWriter.write(results, query.getTitle());
                    trecFormattedWriterRm3.write(rm3results, query.getTitle());
                }
                trecFormattedWriter.close();
                trecFormattedWriterRm3.close();

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

        RunQuerySDM runner = new RunQuerySDM(config);
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
