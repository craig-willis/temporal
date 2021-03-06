package edu.gslis.main;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang.StringUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import com.google.common.collect.Sets;

import edu.gslis.config.BatchConfig;
import edu.gslis.config.CollectionConfig;
import edu.gslis.config.ScorerConfig;
import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.indexes.temporal.TimeSeriesIndex;
import edu.gslis.indexes.temporal.lda.LDAIndex;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.scorers.lda.LDAScorer;
import edu.gslis.scorers.temporal.RerankingScorer;
import edu.gslis.scorers.temporal.TemporalScorer;


/** 
 * Multi-threaded query driver for YAMLConfigBase.
 * 
 * edu.gslis.temporal.main.RunQuery config/config.yaml
 * 
 * For each configured collection
 *    For each configured scorer
 *       For each configured set of topics
 *          Run initial retrieval
 *          Re-score
 *          Output results
 */
public class RunQuery extends YAMLConfigBase 
{
    static final int NUM_THREADS = 10;
    
    public RunQuery(BatchConfig config) {
        super(config);
    }

    public void runBatch(String configPath) throws Exception 
    {
        initGlobals();
                
        // Start a thread per query
        int numThreads = config.getNumThreads();
        if (numThreads == 0) { numThreads = 1; }
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        List<Writer> writers = new ArrayList<Writer>();
        List<Writer> queryWriters = new ArrayList<Writer>();
        // For each collection
        List<CollectionConfig> collections = config.getCollections();
        for(CollectionConfig collection: collections) 
        {
            String collectionName = collection.getName();
            System.err.println("Processing " + collectionName);
            
            long startTime = collection.getStartDate();
            long endTime = collection.getEndDate();
            long interval = collection.getInterval();
            
            String ldaTermTopicPath = collection.getLdaTermTopicPath();
            String ldaDocTopicsPath = collection.getLdaDocTopicsPath();
            
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
                
                // Open the index
                String indexPath = indexRoot + File.separator + collection.getIndex();
                IndexWrapper index = 
                        IndexWrapperFactory.getIndexWrapper(indexPath);
                index.setTimeFieldName(Indexer.FIELD_EPOCH);
                                                    
                String corpusStatsClass = config.getBgStatType();
                CollectionStats corpusStats = (CollectionStats)loader.loadClass(corpusStatsClass).newInstance();
                corpusStats.setStatSource(indexPath);
                             
                // Open the TimeSeriesIndex, if present
                String tsIndexPath = collection.getTimeSeriesIndex();
                TimeSeriesIndex tsIndex = new TimeSeriesIndex();
                if (tsIndexPath != null)
                	tsIndex.open(tsIndexPath, true);             
                
                LDAIndex ldaIndex = new LDAIndex();
                if (StringUtils.isNotEmpty(ldaDocTopicsPath) &&
                        StringUtils.isNotEmpty(ldaTermTopicPath))   
                {             
                    //ldaIndex.open(ldaDBPath, true);
                    System.out.println("Loading LDA data");
                    ldaIndex.load(ldaDocTopicsPath, ldaTermTopicPath);
                    System.out.println("done");
                }
                
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
                    	

                    	
                        // Create output file
                    	File resultsDir = new File(outputDir + "/" + collectionName + "/" + queryFileName + "/" + scorerName);
                    	resultsDir.mkdirs();                     	 
                        String trecResultsFile = resultsDir + File.separator + paramStr + ".out";
                        if (new File(trecResultsFile).exists()) {
                        	System.out.println("File " + trecResultsFile + " exists, skipping");
                        	continue;
                        }
                        
                        Writer trecResultsWriter = new BufferedWriter(new FileWriter(trecResultsFile));
                        FormattedOutputTrecEval trecFormattedWriter = new FormattedOutputTrecEval();
                        trecFormattedWriter.setRunId(scorerName);
                        trecFormattedWriter.setWriter(trecResultsWriter);
                        writers.add(trecResultsWriter);
                        
                    	// Create file to store the resulting query
                    	File queryDir = new File("queries/" + collectionName + "/" + queryFileName + "/" + scorerName);
                    	queryDir.mkdirs();                     	 
                        String queryFile = queryDir + File.separator + paramStr + ".out";
                        Writer queryWriter = new BufferedWriter(new FileWriter(queryFile));
                        queryWriters.add(queryWriter);
                                         
                        Iterator<GQuery> queryIterator = queries.iterator();
                        while(queryIterator.hasNext()) 
                        {                            
	                        GQuery query = queryIterator.next();
	                        
	                        // Scorer per worker means scorer per query
	                        RerankingScorer docScorer = (RerankingScorer)loader.loadClass(className).newInstance();
	                        
	                        docScorer.setConfig(scorerConfig);
	                        docScorer.setCollectionStats(corpusStats);
	                        
	                        if (docScorer instanceof TemporalScorer) {
	                        	((TemporalScorer)docScorer).setStartTime(startTime);
	                        	((TemporalScorer)docScorer).setEndTime(endTime);
	                        	((TemporalScorer)docScorer).setInterval(interval);
	                        	((TemporalScorer)docScorer).setCollectionName(collectionName);
	                        	((TemporalScorer)docScorer).setTimeSeriesIndex(tsIndex);
	                        	
	                        }
	                       	        
	                        if (docScorer instanceof LDAScorer)
	                            ((LDAScorer)docScorer).setIndex(ldaIndex);
	                        
	        				String[] prams = paramStr.split(":");
	        				for (String param: prams) {
	        					String[] p = param.split("=");
	        					docScorer.setParameter(p[0], Double.valueOf(p[1]));
	        				}
	        	    			        

	                        docScorer.setQuery(query);		                                                


	                        // Run worker
	                        QueryRunner worker = new QueryRunner();
	                        worker.setDocScorer(docScorer);
	                        //worker.setIndex(indexPath);
	                        worker.setIndex(index);
	                        worker.setQuery(query);
	                        worker.setStopper(stopper);
	                        worker.setTrecFormattedWriter(trecFormattedWriter);
	                        worker.setQueryWriter(queryWriter);
	                        worker.setCollectionStats(corpusStats);
	                        executor.execute(worker);
	                    }                        
                    }
                }
            }
        }  
        executor.shutdown();
        
        // Wait until all threads are finish
        executor.awaitTermination(48, TimeUnit.HOURS);
        System.err.println("Finished all threads");
        
        for (Writer writer: writers) {
        	writer.close();
        }
        
        for (Writer writer: queryWriters) {
        	writer.close();
        }
        
        sendMail(configPath);
    }
    
    public void sendMail(String config) {
    	String addr = "willis8@illinois.edu";
    
        Properties properties = System.getProperties();  
        properties.setProperty("mail.smtp.host", "smtp.ncsa.illinois.edu");  
        Session session = Session.getDefaultInstance(properties);  
    
        try {  
           MimeMessage message = new MimeMessage(session);  
           message.setFrom(new InternetAddress(addr));  
           message.addRecipient(Message.RecipientType.TO, new InternetAddress(addr));  
           message.setSubject(config + " completed");  
           message.setText(config + " completed");
    
           Transport.send(message);      
        }catch (MessagingException e) {
        	e.printStackTrace();
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
        runner.runBatch(args[0]);
    }
       
}
