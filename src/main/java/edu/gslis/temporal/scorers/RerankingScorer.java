package edu.gslis.temporal.scorers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.gslis.docscoring.QueryDocScorer;
import edu.gslis.main.config.ScorerConfig;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

public abstract class RerankingScorer extends QueryDocScorer 
{
    ScorerConfig config;
    
    Map<String, List<Double>> paramMap = new HashMap<String, List<Double>>();
    
    public abstract void init(SearchHits hits);
    
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
}
