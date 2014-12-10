package edu.gslis.temporal.main;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import edu.gslis.main.config.BatchConfig;
import edu.gslis.main.config.ScorerConfig;
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
    
    /**
     * For each scorer in the YAML config, setup a standard scorer using the
     * specified corpus stats and an oracle scorer that uses the test
     * collection.
     * @throws Exception
     */
    protected void setupScorers() throws Exception 
    {
        List<ScorerConfig> scorerConfigs = config.getScorers();
        for (ScorerConfig scorerConfig: scorerConfigs) 
        {               
            // Setup the scorers
            String scorerName = scorerConfig.getName();
            String className = scorerConfig.getClassName();

            if (!StringUtils.isEmpty(className))
            {
                RerankingScorer docScorer = (RerankingScorer)loader.loadClass(className).newInstance();
        
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
        
                scorers.put(scorerName, docScorer);
            } 
            else 
            {
                scorers.put(scorerName, null);
            }
        }
    }
}
