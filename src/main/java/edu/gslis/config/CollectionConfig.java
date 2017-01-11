package edu.gslis.config;

import java.util.Map;


/**
 * Collection configuration settings for use with YAMLConfigBase
 * @author willis8
 *
 */
public class CollectionConfig {
    String name;
    String index;
    Map<String, String> queries;
    String qrels;
    String bgSourcePath;
    Integer relLevel = 1;
    String dateFormat;
    long startDate;
    long endDate;
    long interval;
    

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getIndex() {
        return index;
    }
    public void setIndex(String trainIndex) {
        this.index = trainIndex;
    }

    
    public void setQrels(String qrels) {
        this.qrels = qrels;
    }
    public String getQrels() {
        return qrels;
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