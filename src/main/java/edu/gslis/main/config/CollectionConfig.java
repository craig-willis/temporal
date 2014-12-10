package edu.gslis.main.config;

import java.util.Map;


public class CollectionConfig {
    String name;
    String trainIndex;
    String testIndex;
    Map<String, String> queries;
    String testQrels;
    String trainQrels;
    String bgSourcePath;
    Integer relLevel;
    String trainDocs;
    String dateFormat;
    long startDate;
    long endDate;
    long interval;
    String tsDB;
    String clusterDB;
    String clusterIndex;
    String ldaDB;
    
    public void setTsDB(String tsDB) {
        this.tsDB = tsDB;
    }
    public String getTsDB() {
        return tsDB;
    }
    
    
    public String getClusterIndex() {
        return clusterIndex;
    }
    public void setClusterIndex(String clusterIndex) {
        this.clusterIndex = clusterIndex;
    }
    public String getClusterDB() {
        return clusterDB;
    }
    public void setClusterDB(String clusterDB) {
        this.clusterDB = clusterDB;
    }    
    public String getLdaDB() {
        return ldaDB;
    }
    public void setLdaDB(String ldaDB) {
        this.ldaDB = ldaDB;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getTrainIndex() {
        return trainIndex;
    }
    public void setTrainIndex(String trainIndex) {
        this.trainIndex = trainIndex;
    }
    public String getTestIndex() {
        return testIndex;
    }
    public void setTestIndex(String testIndex) {
        this.testIndex = testIndex;
    }
    public String getTestQrels() {
        return testQrels;
    }
    public void setTestQrels(String testQrels) {
        this.testQrels = testQrels;
    }
    public String getTrainQrels() {
        return trainQrels;
    }
    public void setTrainDocs(String trainDocs) {
        this.trainDocs = trainDocs;
    }
    public String getTrainDocs() {
        return trainDocs;
    }
    public void setTrainQrels(String trainQrels) {
        this.trainQrels = trainQrels;
    }
    public String getBgSourcePath() {
        return bgSourcePath;
    }
    public void setBgSourcePath(String bgSourcePath) {
        this.bgSourcePath = bgSourcePath;
    }
    public Integer getRelLevel() {
        return relLevel;
    }
    public void setRelLevel(Integer relLevel) {
        this.relLevel = relLevel;
    }
    public Map<String, String> getQueries() {
        return queries;
    }
    public void setQueries(Map<String, String> queries) {
        this.queries = queries;
    }        
    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }
    public String getDateFormat() {
        return dateFormat;
    }
    public long getStartDate() {
        return startDate;
    }
    public void setStartDate(long startDate) {
        this.startDate = startDate;
    }
    public long getEndDate() {
        return endDate;
    }
    public void setEndDate(long endDate) {
        this.endDate = endDate;
    }
    public long getInterval() {
        return interval;
    }
    public void setInterval(long interval) {
        this.interval = interval;
    }    
    
}