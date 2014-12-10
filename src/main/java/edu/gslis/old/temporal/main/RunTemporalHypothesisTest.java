package edu.gslis.old.temporal.main;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import edu.gslis.eval.Qrels;
import edu.gslis.filtering.session.FilterSession;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.main.config.BatchConfig;
import edu.gslis.main.config.CollectionConfig;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;

/**
 * Test whether the collection is temporal.
 * 
 * For each collection
 *  Get temporal extents, divide into bins based on interval
 *  For each query
 *    For each relevant document 
 *       Count number of relevant documents in interval 0 ... i
 *      
 */
public  class RunTemporalHypothesisTest  extends YAMLConfigBase
{

    public RunTemporalHypothesisTest(BatchConfig config) {
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
                Qrels qrels = new Qrels(collection.getTrainQrels(), true, relLevel);
                                       

                //String runId = prefix + "-" + "_" + collectionName + "_" + queryFileName;
                //String trecResultsFile = outputDir + File.separator + runId + "-tht.out";
                
                outputDir.mkdirs();                        

                
                long endTime = collection.getEndDate();
                long startTime = collection.getStartDate();
                long interval = collection.getInterval();
                
                long numIntervals = (endTime - startTime)/interval;
                
                //long totalRelDocs = 0;
                double[] totals = new double[5];
                for (int i=0; i<5; i++)
                    totals[0] = 0;
                System.out.println("interval=" + interval);
                System.out.println("intervals=" + numIntervals);
                Iterator<GQuery> queryIterator = queries.iterator();
                while(queryIterator.hasNext()) 
                {                            
                    GQuery query = queryIterator.next();
                    System.err.println(query.getTitle());
                    Set<String> relDocs = qrels.getRelDocs(query.getTitle());
                    if (relDocs == null)
                        relDocs = new HashSet<String>();
                    
                    //totalRelDocs += relDocs.size();

                    
                    // We could also look at the average distance between relevant documents.

                    double avgdist = 0;
                    DescriptiveStatistics stat = new DescriptiveStatistics();
                    for (String docno1: relDocs) 
                    {
                        String epoch1 = index.getMetadataValue(docno1, Indexer.FIELD_EPOCH);
                        if (epoch1 == null)
                            continue;
                        long docTime1 = Long.valueOf(epoch1);
                        
                        long i1 = (endTime-docTime1)/interval;
                        
                        for (String docno2: relDocs) {
                            if (docno1.equals(docno2)) continue;
                            
                            String epoch2 = index.getMetadataValue(docno2, Indexer.FIELD_EPOCH);
                            if (epoch2 == null)
                                continue;
                            long docTime2 = Long.valueOf(epoch2);
                            
                            long i2 = (endTime-docTime2)/interval;
                            
                            int dist = (int)Math.abs(i2 - i1);
                            stat.addValue(dist);
                        }                            
                    } 
                    
                    System.out.println(query.getTitle() + "," + relDocs.size() 
                            + "," + Math.round(stat.getMean()) + "," + Math.round(stat.getMax()) 
                            + "," + Math.round(stat.getMin()) + "," + Math.round(stat.getStandardDeviation()));
                    
                    // Following Voorhees, we can look at the percentage of relevant documents
                    // that are within 1-5 intervals of another relevant document.
                    
                    /*
                     *                     
                     *                     double[] subtotals = new double[5];
                    for (int i=0; i<5; i++)
                        subtotals[0] = 0;

                    for (String docno1: relDocs) 
                    {
                        String epoch1 = index.getMetadataValue(docno1, Indexer.FIELD_EPOCH);
                        long docTime1 = Long.valueOf(epoch1);
                        
                        long i1 = (endTime-docTime1)/interval;
                        
                        int min = (int)numIntervals;
                        for (String docno2: relDocs) {
                            if (docno1.equals(docno2)) continue;
                            
                            String epoch2 = index.getMetadataValue(docno2, Indexer.FIELD_EPOCH);
                            long docTime2 = Long.valueOf(epoch2);
                            
                            long i2 = (endTime-docTime2)/interval;
                            
                            int dist = (int)Math.abs(i2 - i1);   
                            if (dist < min)
                                min = dist;
                        }       
                        
                        for (int i=min; i<5; i++) 
                            subtotals[i]++;                       
                    } 
                    for (int i=0; i<5; i++) {
                        totals[i] += subtotals[i];
                    }
                    /*
                    System.out.println(query.getTitle() + "," + relDocs.size());
                    for (int i=0; i<5; i++) {
                        System.out.println("\t" + i + "," + totals[i]);
                    }
                    */
                }     
                /*
                for (int i=0; i<5; i++) {
                    System.out.println("\t" + i + "," + totals[i] + ", " + totals[i]/(double)totalRelDocs);
                }
                */
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

        RunTemporalHypothesisTest runner = new RunTemporalHypothesisTest(config);
        runner.runBatch();
    }
}
