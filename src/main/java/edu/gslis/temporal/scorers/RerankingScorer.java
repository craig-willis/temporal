package edu.gslis.temporal.scorers;

import edu.gslis.docscoring.QueryDocScorer;
import edu.gslis.searchhits.SearchHits;

public abstract class RerankingScorer extends QueryDocScorer 
{

    public abstract void init(SearchHits hits);
}
