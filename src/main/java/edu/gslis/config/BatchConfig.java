package edu.gslis.config;

import java.util.List;

/**
 * Batch run configuration object for YAMLConfigBase class.
 */
public class BatchConfig {
    String stopper = "";
    String indexRoot = "";
    String bgStatType = "";
    String bgSourcePath = "";
    List<ScorerConfig> scorers;
    List<CollectionConfig> collections;
    List<PriorConfig> priors;
    String optimizer = "";
    String runPrefix = "";
    String outputDir = "";
    String constraint;
    /* Number of threads for multi-threaded runs */
    int numThreads;
        
    public int getNumThreads() {
        return numThreads;
    }
    
    public void setNumThreads(int numThreads) {
        this.numThreads = numThreads;
    }
    public String getStopper() {
        return stopper;
    }
    public void setStopper(String stopper) {
        this.stopper = stopper;
    }
    public String getIndexRoot() {
        if (indexRoot == null) 
            return "";
        else
            return indexRoot;
    }
    public void setIndexRoot(String indexRoot) {
        this.indexRoot = indexRoot;
    }
    public String getBgStatType() {
        return bgStatType;
    }
    public void setBgStatType(String bgStatType) {
        this.bgStatType = bgStatType;
    }
    public List<ScorerConfig> getScorers() {
        return scorers;
    }
    public void setScorers(List<ScorerConfig> scorers) {
        this.scorers = scorers;
    }
    public List<CollectionConfig> getCollections() {
        return collections;
    }
    public void setCollections(List<CollectionConfig> collections) {
        this.collections = collections;
    }
       
    public List<PriorConfig> getPriors() {
        return priors;
    }
    public void setPriors(List<PriorConfig> priors) {
        this.priors = priors;
    }
    
    public String getRunPrefix() {
        return runPrefix;
    }
    public void setRunPrefix(String runPrefix) {
        this.runPrefix = runPrefix;
    }
    public String getOutputDir() {
        return outputDir;
    }
    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }
    public void setConstraint(String constraint) {
        this.constraint = constraint;
    }
    public String getConstraint() {
        return constraint;
    }
    public void setBgSourcePath(String bgSourcePath) {
        this.bgSourcePath = bgSourcePath;
    }
    public String getBgSourcePath() {
        return bgSourcePath;
    }

}