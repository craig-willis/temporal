package edu.gslis.scorers.temporal;

import java.util.HashMap;

import java.util.List;
import java.util.Map;

import edu.gslis.config.ScorerConfig;
import edu.gslis.docscoring.QueryDocScorer;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

public abstract class RerankingScorer extends QueryDocScorer 
{
    public ScorerConfig config;
    
    public Map<String, List<Double>> paramMap = new HashMap<String, List<Double>>();
    IndexWrapper index = null;
    
    public abstract void init(SearchHits hits);
    
    public void setIndex(IndexWrapper index) {
    	this.index = index;
    }
    
    public void setConfig(ScorerConfig config) {
        this.config = config;
    }
    public ScorerConfig getConfig() {
        return config;
    }
    
    public double[] scoreMultiple(SearchHit hit) {
        return new double[0];
    }
        
    
    public List<Double> getParameter() {
        return null;
    }
    
    public void setParameter(String paramName, List<Double> paramValues) {
        paramMap.put(paramName, paramValues);        
    }   
    
    public GQuery getQuery() {
    	return this.gQuery;
    }
}
