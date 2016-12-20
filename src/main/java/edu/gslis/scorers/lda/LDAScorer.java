package edu.gslis.scorers.lda;

import java.util.Iterator;

import edu.gslis.indexes.LDAIndex;
import edu.gslis.scorers.temporal.RerankingScorer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

/**
 * Implements Wei and Croft topic-model-based document model
 * @author cwillis
 */
public class LDAScorer extends RerankingScorer {

    static String MU = "mu";
    static String LAMBDA = "lambda";
    static String SMOOTH = "smooth";
    
    
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
        double smooth = paramTable.get(SMOOTH);
        
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

            if (smooth == 1) {
                // 2-stage                
                double smoothedTopicProb = 
                        (docFreq + mu*topicsProb) / 
                        (docLength + mu);
                
                double smoothedDocProb = 
                        lambda*smoothedTopicProb + (1-lambda)*collectionProb;
                
                logLikelihood += queryWeight * Math.log(smoothedDocProb);                        
            }
            else if (smooth == 2) { 
                // Wei & Croft
                double smoothedTopicProb = 
                        (docFreq + mu*collectionProb) / 
                        (docLength + mu);
                
                double smoothedDocProb = 
                        lambda*smoothedTopicProb + (1-lambda)*topicsProb;
                
                logLikelihood += queryWeight * Math.log(smoothedDocProb);                        

            }
            else if (smooth == 3) { 
                // J-M Smoothed dirichlet

                double smoothedTopicProb = 
                        lambda*topicsProb + (1-lambda)*collectionProb;

                double smoothedDocProb = 
                        (docFreq + mu*smoothedTopicProb) / 
                        (docLength + mu);
                                
                logLikelihood += queryWeight * Math.log(smoothedDocProb);                        

            }  
            else {
                System.err.println("Invalid smoothing model specified.");
            }     
            
            
        }
        return logLikelihood;
    }
    
    public double[] scoreMultiple(SearchHit hit) {
        return new double[0];
    }
    
    @Override
    public void init(SearchHits hits) {
        // TODO Auto-generated method stub
        
    }

    
}
