package edu.gslis.main.config;

import java.util.Map;

public class ScorerConfig {
    public String name;
    public String className;
    public String expander;
    public Map<String, Object> params;
    public String priorPath;
    
    public double beta = 0.5;
    public double lambda = 0.5;
    public int numFeedbackTerms = 20;    
    public int numFeedbackDocs = 20;
    public double sd = 1;
    
    
    public void setPriorPath(String priorPath) {
        this.priorPath = priorPath;
    }
    
    public String getPriorPath() {
        return priorPath;
    }
    
    public double getStdDev() {
        return sd;
    }
    
    public void setStdDev(double sd) {
        this.sd = sd;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getClassName() {
        return className;
    }
    public void setClassName(String className) {
        this.className = className;
    }
    public String getExpander() {
        return expander;
    }
    public void setExpander(String expander) {
        this.expander = expander;
    }
    public Map<String, Object> getParams() {
        return params;
    }
    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public double getBeta() {
        return beta;
    }
    
    public void setBeta(double beta) {
        this.beta = beta;
    }
    public double getLambda() {
        return lambda;
    }
    public void setLambda(double lambda) {
        this.lambda = lambda;
    }
    public int getNumFeedbackTerms() {
        return numFeedbackTerms;
    }
    public void setNumFeedbackTerms(int numFeedbackTerms) {
        this.numFeedbackTerms = numFeedbackTerms;
    }
    public int getNumFeedbackDocs() {
        return numFeedbackDocs;
    }
    public void setNumFeedbackDocs(int numFeedbackDocs) {
        this.numFeedbackDocs = numFeedbackDocs;
    }
    
}
