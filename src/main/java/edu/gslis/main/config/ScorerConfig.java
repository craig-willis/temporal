package edu.gslis.main.config;

import java.util.Map;

public class ScorerConfig {
    public String name;
    public String className;
    public String expander;
    public Map<String, Object> params;
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
    
}
