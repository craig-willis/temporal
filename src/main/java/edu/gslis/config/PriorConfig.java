package edu.gslis.config;


public class PriorConfig {
    public String name;
    public String className;
    public String path;
    public double weight;
    
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
    public String getPath() {
        return path;
    }
    public void setPath(String path) {
        this.path = path;
    }
    
    public void setWeight(double weight) {
        this.weight = weight;        
    }
    
    public double getWeight() {
        return weight;
    }
}
