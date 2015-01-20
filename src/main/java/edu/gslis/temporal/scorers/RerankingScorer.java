package edu.gslis.temporal.scorers;

import edu.gslis.docscoring.QueryDocScorer;
import edu.gslis.main.config.ScorerConfig;
import edu.gslis.searchhits.SearchHits;

public abstract class RerankingScorer extends QueryDocScorer 
{
    ScorerConfig config;

    public abstract void init(SearchHits hits);
    
    public void setConfig(ScorerConfig config) {
        this.config = config;
    }
    public ScorerConfig getConfig() {
        return config;
    }
}
