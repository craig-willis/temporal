package edu.gslis.main;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import edu.gslis.config.BatchConfig;
import edu.gslis.scorers.temporal.RerankingScorer;
import edu.gslis.utils.Stopper;

/**
 * Base class for YAML batch configured query drivers
 */
public class YAMLConfigBase 
{
	protected ClassLoader loader = ClassLoader.getSystemClassLoader();
    protected BatchConfig config = null;
    protected Stopper stopper = null;
    protected String prefix = null;
    protected String indexRoot = null;
    protected File outputDir = null;   
    Map<String, RerankingScorer> scorers = new HashMap<String, RerankingScorer>();
    Map<String, Object> priors = new HashMap<String, Object>();
    Map<String, Double> priorWeights = new HashMap<String, Double>();

    public YAMLConfigBase(BatchConfig config) {
        this.config = config;
    }
    
    /**
     * Sets up a few global settings
     */
    protected void initGlobals()  throws Exception
    {
        // Global settings
    	
    	// Stop list
        stopper = new Stopper(config.getStopper());
        // Run prefix
        prefix = config.getRunPrefix();
        // Index root path
        indexRoot = config.getIndexRoot();
        // Output directory
        outputDir = new File(config.getOutputDir());
    }
}
