package edu.gslis.temporal.scorers;

import edu.gslis.indexes.KMeansIndex;
import edu.gslis.searchhits.SearchHits;

public abstract class ClusterScorer extends RerankingScorer 
{
    
    KMeansIndex clusterIndex = null;
    
    public void setIndex(KMeansIndex clusterIndex) {
        this.clusterIndex = clusterIndex;
    }
    
    public void init(SearchHits hits) {         
    }
}
