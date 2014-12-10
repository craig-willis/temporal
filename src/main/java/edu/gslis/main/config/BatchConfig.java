package edu.gslis.main.config;

import java.util.List;


public class BatchConfig {
    String stopper = "";
    String indexRoot = "";
    String bgStatType = "";
    String bgSourcePath = "";
    List<ScorerConfig> scorers;
    List<CollectionConfig> collections;
    List<OptimizerConfig> optimizers;
    List<PriorConfig> priors;
    String optimizer = "";
    String runPrefix = "";
    String outputDir = "";
    QPPConfig qpp;
    String constraint;
    
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
    
    public List<OptimizerConfig> getOptimizers() {
        return optimizers;
    }
    public void setOptimizers(List<OptimizerConfig> optimizers) {
        this.optimizers = optimizers;
    }
    public String getOptimizer() {
        return optimizer;
    }
    public void setOptimizer(String optimizer) {
        this.optimizer = optimizer;
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
    public QPPConfig getQpp() {
        return qpp;
    }
    public void setQpp(QPPConfig qpp) {
        this.qpp = qpp;
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