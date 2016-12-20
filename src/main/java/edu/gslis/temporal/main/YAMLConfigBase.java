package edu.gslis.temporal.main;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import edu.gslis.config.BatchConfig;
import edu.gslis.temporal.scorers.RerankingScorer;
import edu.gslis.utils.Stopper;

/**
 * Base class for YAML-configured drivers
 */
public class YAMLConfigBase 
{
    ClassLoader loader = ClassLoader.getSystemClassLoader();
    BatchConfig config = null;
    Stopper stopper = null;
    String prefix = null;
    String indexRoot = null;
    File outputDir = null;   
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
        stopper = new Stopper(config.getStopper());
        prefix = config.getRunPrefix();
        indexRoot = config.getIndexRoot();
                  
        outputDir = new File(config.getOutputDir());
    }

}
