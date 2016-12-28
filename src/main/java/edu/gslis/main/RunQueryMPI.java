package edu.gslis.main;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import com.google.common.collect.Sets;

import edu.gslis.config.BatchConfig;
import edu.gslis.config.CollectionConfig;
import edu.gslis.config.ScorerConfig;
import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.main.mpi.QueryTask;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.scorers.temporal.RerankingScorer;
import edu.gslis.scorers.temporal.TemporalScorer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;
import mpi.MPI;
import mpi.Request;


/** 
 * Uses MPJ (MPI) to distribute queries to multiple nodes.  Assumes that 
 * indexes are present on the same path on each machine.
 */
public class RunQueryMPI extends YAMLConfigBase 
{
	/* MPI rank of current process */
    int rank = -1;
    /* MPI size (number of threads) */
    int size = -1;
    /* Maximum number of results (Indri) */
    int NUM_RESULTS=1000;
    
    public RunQueryMPI(int rank, int size) {
        this.rank = rank;
        this.size = size;
    }
    
    public RunQueryMPI(int rank, int size, BatchConfig config) {
        super(config);
        this.rank = rank;
        this.size = size;
    }

    public void runBatch() throws Exception 
    {
    	
    	if (rank == 0) {
    		// The master process reads the config and topics
    		// files and distributes tasks to each worker.
	        initGlobals();
	        	        
	        // For each collection
	        List<CollectionConfig> collections = config.getCollections();
	        for(CollectionConfig collection: collections) 
	        {
	            String collectionName = collection.getName();
	            System.err.println("Processing " + collectionName);
	            
	            long startTime = collection.getStartDate();
	            long endTime = collection.getEndDate();
	            
	            // For each query set
	            Map<String, String> queryFiles = collection.getQueries();
	            for (String queryFileName: queryFiles.keySet()) {
	                System.err.println(collectionName + " " + queryFileName);
	                String queryFilePath = queryFiles.get(queryFileName);
	                
	                // Read the queries
	                GQueries queries = null;
	                if (queryFilePath.endsWith("indri")) 
	                    queries = new GQueriesIndriImpl();
	                else
	                    queries = new GQueriesJsonImpl();
	                
	                queries.read(queryFilePath);
	                
	                String indexPath = indexRoot + File.separator + collection.getIndex();
	                
	                // Open the index
	                /*
	                String indexPath = indexRoot + File.separator + collection.getIndex();
	                IndexWrapper index = 
	                        IndexWrapperFactory.getIndexWrapper(indexPath);
	                index.setTimeFieldName(Indexer.FIELD_EPOCH);
	                                                    
	                String corpusStatsClass = config.getBgStatType();
	                CollectionStats corpusStats = (CollectionStats)loader.loadClass(corpusStatsClass).newInstance();
	                corpusStats.setStatSource(indexPath);
	                */
	                             
	                // For each scorer
	                List<ScorerConfig> scorerConfigs = config.getScorers();
	                for (ScorerConfig scorerConfig: scorerConfigs) 
	                {               
	                    // Setup the scorers
	                    String scorerName = scorerConfig.getName();
	                    String className = scorerConfig.getClassName();
	                    System.err.println("Running scorer " + scorerName);
	                    
	                    // Get all permutations of configured parameters
	                    Map<String, String> paramMap = scorerConfig.getParams();
	                    List<Set<Double>> plist = new ArrayList<Set<Double>>();
	                    List<String> paramNames = new ArrayList<String>();
	                    for (String param: paramMap.keySet()) {
	                    	paramNames.add(param);
	                    	
	                    	String paramStr = paramMap.get(param);
	                    	String[] paramElems = paramStr.split(",");
	                    	Double[] params = new Double[paramElems.length];
	                    	for (int i=0; i<paramElems.length; i++) {
	                    		params[i] = Double.valueOf(paramElems[i]);
	                    	}
	                    	plist.add(Sets.newHashSet(params));
	                    }
	                    
	                    Set<List<Double>> prod = Sets.cartesianProduct(plist);
	
	                    // for each parameter combination
	                    for (List<Double> params: prod) {

	                        
	                    	String paramStr = "";
	                    	for (int i=0; i<params.size(); i++) {
	                        	String name = paramNames.get(i);
	                        	Double value = params.get(i);
	                            
	                            if (paramStr.length() > 0) 
	                            	paramStr += ":";
	                            paramStr += name +"=" + value;
	                    	}
	                    	       

	                        Iterator<GQuery> queryIterator = queries.iterator();
	                        while(queryIterator.hasNext()) 
	                        {                            
	                            GQuery query = queryIterator.next();
	                            QueryTask task = new QueryTask();
	                            task.setCollectionName(collectionName);
	                            task.setEndTime(endTime);
	                            task.setIndexPath(indexPath);
	                            task.setOutputDir(outputDir.getAbsolutePath());
	                            task.setParamStr(paramStr);
	                            task.setQuery(query);
	                            task.setQueryFileName(queryFileName);
	                            task.setScorerClass(className);
	                            task.setScorerName(scorerName);
	                            task.setStartTime(startTime);
	                            task.setStopper(stopper);

                				// Wait for ready worker
                				int[]source = {0};
                				Request r = MPI.COMM_WORLD.Irecv(source, 0, 1, MPI.INT, MPI.ANY_SOURCE, 0);
                				r.Wait();
                				
                				System.out.println(rank + " received ready from " + source[0]);
                				if (source[0] > 0) {
                					
                					System.out.println(rank + " sending task to " + source[0]);
                					// Send task object to worker
                					ByteBuffer byteBuff = ByteBuffer.allocateDirect(2000 + MPI.SEND_OVERHEAD);
                					MPI.Buffer_attach(byteBuff);
                					try {
                						ByteArrayOutputStream bos = new ByteArrayOutputStream();
                						ObjectOutput out = null;
                						out = new ObjectOutputStream(bos);
                						out.writeObject(task);
                						byte[] bytes = bos.toByteArray();

                						r= MPI.COMM_WORLD.Isend(bytes, 0, bytes.length, MPI.BYTE, source[0], 0);
                						r.Wait();
                						System.out.println(rank + " sent " + task + " to " + source[0]);						
                					bos.close();
                					} catch (IOException ex) {
                					}
                				}
	                			
	                			int j=0;
	                			while (j < size-1) {				
	                				// Wait for ready worker
	                				int[]buf = {0};
	                				r = MPI.COMM_WORLD.Irecv(buf, 0, 1, MPI.INT, MPI.ANY_SOURCE, 0);
	                				r.Wait();				
	                				int src = buf[0];
	                				System.out.println(rank + " waiting to send shutdown to " + src);
	                				
	                				// Signal all is done
	                				// Send task object to worker
	                				ByteBuffer byteBuff = ByteBuffer.allocateDirect(2000 + MPI.SEND_OVERHEAD);
	                				MPI.Buffer_attach(byteBuff);
	                				try {
	                					ByteArrayOutputStream bos = new ByteArrayOutputStream();
	                					ObjectOutput out = null;
	                					out = new ObjectOutputStream(bos);
	                					QueryTask sd = new QueryTask();
	                					sd.shutdown = true;
	                					out.writeObject(sd );
	                					byte[] bytes = bos.toByteArray();

	                					r= MPI.COMM_WORLD.Isend(bytes, 0, bytes.length, MPI.BYTE, src, 0);
	                					r.Wait();					
	                					System.out.println(rank + " sent shutdown to " + source);
	                				bos.close();
	                				} catch (IOException ex) {
	                				}
	                				j++;
	                			}
	                        }
	                    }
	                }
	            }
	        }    
    	} else {    		
    		    		
			boolean done = false;
			while (!done) 
			{
				// Send ready signal to master
				System.out.println(rank + " ready ");
				int[] buf = {rank};
				Request r1 = MPI.COMM_WORLD.Isend(buf, 0, 1, MPI.INT, 0, 0);
				r1.Wait();
				
				System.out.println(rank + " waiting ");
				// Receive task object
				byte[] bytes = new byte[2000];
				Request r2 = MPI.COMM_WORLD.Irecv(bytes, 0, 2000, MPI.BYTE, MPI.ANY_SOURCE, 0);
				r2.Wait();
				
				QueryTask task = null;
				ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
				ObjectInput in = null;
				try {
					in = new ObjectInputStream(bis);
					Object obj = in.readObject();
					task = (QueryTask) obj;
					System.out.println(rank + " received " + task);
					if (task.shutdown) {
						System.out.println (rank + " shutdown");
						done = true;
						break;							
					}
				} catch (IOException ex) {
				} catch (ClassNotFoundException cnf) {
				}
				bis.close();
				
		   		
	    		// Worker
	    		
	    		// Open index, requires index path
	    		// Setup corpusStats
	    		// Create output directory
	    		// Setup Scorer
	    		// Set scorer params
	    		
				String collectionName = task.getCollectionName();
				String className = task.getScorerClass();
	            // Scorer per worker means scorer per query
	            RerankingScorer docScorer = (RerankingScorer)loader.loadClass(className).newInstance();
	            String scorerName = task.getScorerName();
				
				long startTime = task.getStartTime();
				long endTime = task.getEndTime();
				String paramStr = task.getParamStr();
				String[] params = paramStr.split(":");
				for (String param: params) {
					String[] p = param.split("=");
					docScorer.setParameter(p[0], Double.valueOf(p[1]));
				}
	    			        
				String indexPath = task.getIndexPath();
	          	            	            
	            if (docScorer instanceof TemporalScorer) {
	            	((TemporalScorer)docScorer).setStartTime(startTime);
	            	((TemporalScorer)docScorer).setStartTime(endTime);
	            }
	            IndexWrapper index = 
	                    IndexWrapperFactory.getIndexWrapper(indexPath);
	            index.setTimeFieldName(Indexer.FIELD_EPOCH);
	                                                
	            String corpusStatsClass = config.getBgStatType();
	            CollectionStats corpusStats = (CollectionStats)loader.loadClass(corpusStatsClass).newInstance();
	            corpusStats.setStatSource(indexPath);
	            
	            String queryFileName = task.getQueryFileName();
	            
	            //docScorer.setConfig(scorerConfig);
	            docScorer.setCollectionStats(corpusStats);
	            
	    		String outputDir = task.getOutputDir();
	            // Create output file
	        	File resultsDir = new File(outputDir + "/" + collectionName + "/" + queryFileName + "/" + scorerName);
	        	resultsDir.mkdirs();     
	            String runId = scorerName + "_" + paramStr;
	            String trecResultsFile = resultsDir + File.separator + paramStr + ".out";
	            
	            Writer trecResultsWriter = new BufferedWriter(new FileWriter(trecResultsFile));
	            FormattedOutputTrecEval trecFormattedWriter = new FormattedOutputTrecEval();
	            trecFormattedWriter.setRunId(runId);
	            trecFormattedWriter.setWriter(trecResultsWriter);
	                             
	            GQuery query = task.getQuery();
	            
	            docScorer.setQuery(query);		                                                
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
	            FeatureVector qv = new FeatureVector(stoppedQuery, stopper);
	            query.setFeatureVector(qv);
	            
	            System.out.println(query.getTitle() + " " + query);
	            
	            docScorer.setQuery(query);
	            SearchHits results = index.runQuery(query, NUM_RESULTS);
	            
	            docScorer.init(results);
	                 
	            Iterator<SearchHit> it = results.iterator();
	            SearchHits rescored = new SearchHits();
	            while (it.hasNext()) {
	                SearchHit hit = it.next();
	                double score = docScorer.score(hit);
	                hit.setScore(score);
	                if (score == Double.NaN || score == Double.NEGATIVE_INFINITY) {
	                    System.err.println("Problem with score for " + query.getText() + "," + hit.getDocno() + "," + score);
	                } else if (score != Double.NEGATIVE_INFINITY) {
	                    rescored.add(hit);
	                }
	            }
	            rescored.rank();
	                                    
	            synchronized (this) {
	                trecFormattedWriter.write(rescored, query.getTitle());
	            }
	            System.out.println(query.getTitle() + ": complete");
	            
	            trecFormattedWriter.close();
			}
    	}
    }
    
    public static void main(String[] args) throws Exception 
    {
		MPI.Init(args);

		int rank = MPI.COMM_WORLD.Rank();
		int size = MPI.COMM_WORLD.Size();
		
		if (rank == 0) {
	        File yamlFile = new File(args[0]);
	        if(!yamlFile.exists()) {
	            System.err.println("you must specify a parameter file to run against.");
	            System.exit(-1);
	        }
	        
	        // Read the yaml config
	        Yaml yaml = new Yaml(new Constructor(BatchConfig.class));
	        BatchConfig config = (BatchConfig)yaml.load(new FileInputStream(yamlFile));
	        RunQueryMPI runner = new RunQueryMPI(rank, size, config);
	        runner.runBatch();
		} else {
			RunQueryMPI runner = new RunQueryMPI(rank, size);
			 runner.runBatch();
		}
		
        
       
    }
       
}
