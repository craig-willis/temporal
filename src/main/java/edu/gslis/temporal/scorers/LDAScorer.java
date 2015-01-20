package edu.gslis.temporal.scorers;

import java.util.Iterator;

import edu.gslis.indexes.LDAIndex;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

/**
 * Implements Wei and Croft topic-model-based document model
 * @author cwillis
 */
public class LDAScorer extends RerankingScorer {

    static String MU = "mu";
    static String LAMBDA = "lambda";
    
    
    LDAIndex ldaIndex = null;
    
    public void setIndex(LDAIndex ldaIndex) {
        this.ldaIndex = ldaIndex;
    }

    /**
     * Smooth the document using a combination of cluster
     * and collection language model.
     */
    public double score(SearchHit doc) 
    {
        
        double lambda = paramTable.get(LAMBDA);
        double mu = paramTable.get(MU);
        
        double logLikelihood = 0.0;
        Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
        while(queryIterator.hasNext()) 
        {
            String feature = queryIterator.next();
            double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
            double docLength = doc.getLength();
            
            // The 1 here is because some query terms in TREC don't appear in all collections when we 
            // separate out sources.
            double collectionProb = (1 + collectionStats.termCount(feature)) / collectionStats.getTokCount();
            
            // Probability of the term given the topics in the document
            double topicsProb = ldaIndex.getTermProbability2(doc.getDocno(), feature);
            
            // Smooth topic LM with collection LM
            double smoothedTopicProb = 
                    lambda*topicsProb + (1-lambda)*collectionProb;
            
            // Smooth document LM with topic LM            
            double smoothedDocProb = 
                    (docFreq + mu*smoothedTopicProb) / 
                    (docLength + mu);
            
            double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
            
            logLikelihood += queryWeight * Math.log(smoothedDocProb);                        
        }
        return logLikelihood;
    }
    
    @Override
    public void init(SearchHits hits) {
        // TODO Auto-generated method stub
        
    }

    
}
