package edu.gslis.temporal.main;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import edu.gslis.docscoring.QueryDocScorer;
import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.eval.FilterEvaluation;
import edu.gslis.eval.Qrels;
import edu.gslis.filtering.main.config.BatchConfig;
import edu.gslis.filtering.main.config.CollectionConfig;
import edu.gslis.filtering.session.FilterSession;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.output.FormattedOutputTrecEval;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.scorers.TemporalScorer;

/**
 * Base class for different filtering harnesses.
 * 
 * This uses the YAML-based configuration to run a filtering
 * configuration against multiple collections with different scorers,
 * topics, etc.
 * 
 * Subclasses should implement the filter() method.
 * 
 * @see edu.gslis.kba.main.RunSimpleFilter
 */
public abstract class RunScorerBase  extends YAMLConfigBase
{

    public RunScorerBase(BatchConfig config) {
        super(config);
    }
    

	public void runBatch() throws Exception 
	{
        initGlobals();
        setupScorers();
        setupPriors();
        
        List<CollectionConfig> collections = config.getCollections();
        for(CollectionConfig collection: collections) 
        {
            String collectionName = collection.getName();

            String tsIndex = collection.getTsIndex();
        
            Map<String, String> queryFiles = collection.getQueries();
            for (String queryFileName: queryFiles.keySet()) {
                System.err.println(collectionName + " " + queryFileName);
                String queryFilePath = queryFiles.get(queryFileName);
                
                GQueries queries = null;
                if (queryFilePath.endsWith("indri")) 
                    queries = new GQueriesIndriImpl();
                else
                    queries = new GQueriesJsonImpl();
                
                queries.setMetadataField(FilterSession.NAME_OF_TIMESTAMP_FIELD);
                queries.read(queryFilePath);
                
                String indexPath = indexRoot + File.separator + collection.getTestIndex();
                IndexWrapper index = 
                        IndexWrapperFactory.getIndexWrapper(indexPath);
                index.setTimeFieldName(Indexer.FIELD_EPOCH);
                                    
                int relLevel = collection.getRelLevel();
                Qrels trainQrels = new Qrels(collection.getTrainQrels(), true, relLevel);
                Qrels testQrels = new Qrels(collection.getTestQrels(), true, relLevel);
                String corpusStatsClass = config.getBgStatType();
                CollectionStats corpusStats = (CollectionStats)loader.loadClass(corpusStatsClass).newInstance();
                corpusStats.setStatSource(indexPath);
                                       
                for (String scorerName: scorers.keySet()) 
                {
                    QueryDocScorer docScorer = scorers.get(scorerName);

                    docScorer.setCollectionStats(corpusStats);

                    if (docScorer instanceof TemporalScorer)
                        ((TemporalScorer)docScorer).setTsIndex(tsIndex);

                    String runId = prefix + "-" + scorerName + "_" + collectionName + "_" + queryFileName;
                    String trecResultsFile = outputDir + File.separator + runId + ".out";
                    
                    outputDir.mkdirs();                        

                    Writer trecResultsWriter = new BufferedWriter(new FileWriter(trecResultsFile));
                    FormattedOutputTrecEval trecFormattedWriter 
                        = FormattedOutputTrecEval.getInstance(runId, trecResultsWriter);
                    
                    Iterator<GQuery> queryIterator = queries.iterator();
                    while(queryIterator.hasNext()) 
                    {                            
                        GQuery query = queryIterator.next();
                        System.err.println(query.getTitle());
                        
                        SearchHits results = run(query, index, 
                                trainQrels, testQrels, docScorer, 
                                collection.getStartDate(), 
                                collection.getEndDate(), collection.getInterval(),
                                collection.getDateFormat(), tsIndex);
                        
                        FilterEvaluation filterEval = new FilterEvaluation(testQrels);
                        filterEval.setResults(results);
                        
                        trecFormattedWriter.write(results, query.getTitle());
                    }
                    trecFormattedWriter.close();
                }
            }
        }    
	}
	
	public abstract SearchHits run(GQuery query, IndexWrapper index, Qrels trainQrels, Qrels testQrels, 
	        QueryDocScorer scorer, long startDate, long endDate, long interval, String dateFormat, String tsIndex);	

}
