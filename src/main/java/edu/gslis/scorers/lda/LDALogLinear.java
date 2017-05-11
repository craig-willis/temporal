package edu.gslis.scorers.lda;

import java.util.Iterator;

import edu.gslis.indexes.temporal.lda.LDAIndex;
import edu.gslis.scorers.temporal.RerankingScorer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

/**
 * Implements Wei and Croft topic-model-based document model
 * Log-Linear interpolation
 */
public class LDALogLinear extends LDAScorer {

    static String MU = "mu";
    static String LAMBDA = "lambda";
    
    LDAIndex ldaIndex = null;
    
    public void setIndex(LDAIndex ldaIndex) {
        this.ldaIndex = ldaIndex;
    }

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
            
            double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);

            double smoothedTopicProb = 
                    (docFreq + mu*collectionProb) / 
                    (docLength + mu);
            
            double smoothedDocProb  = smoothedTopicProb;
            if (topicsProb>0) 
            	smoothedDocProb =  Math.pow(smoothedTopicProb, lambda)*Math.pow(topicsProb, 1-lambda);
            else 
            	System.err.println("Zero topicsProb for " + doc.getDocno() + " " + feature);
            
            logLikelihood += queryWeight * Math.log(smoothedDocProb);                        
        }
        return logLikelihood;
    }
    
    
    @Override
    public void init(SearchHits hits) {
        // TODO Auto-generated method stub
        
    }

    
}
