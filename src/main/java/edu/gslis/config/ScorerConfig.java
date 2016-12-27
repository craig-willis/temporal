package edu.gslis.config;

import java.util.Map;

public class ScorerConfig {
	/* Scorer name */
    public String name;
    
    /* Class to be loaded */
    public String className;
    
    /* Param map */
    public Map<String, String> params;
    
    /* Path to prior map */
    public String priorPath;
               
    public void setPriorPath(String priorPath) {
        this.priorPath = priorPath;
    }
    
    public String getPriorPath() {
        return priorPath;
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
    public Map<String, String> getParams() {
        return params;
    }
    public void setParams(Map<String, String> params) {
        this.params = params;       
    }
}
