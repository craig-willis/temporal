package edu.gslis.temporal.scorers;

import java.util.Iterator;

import edu.gslis.searchhits.SearchHit;

/**
 * Implements Liu and Croft cluster-based document model
 * @author cwillis
 */
public class KMeansScorer extends ClusterScorer {

    static String MU = "mu";
    static String LAMBDA = "lambda";
    
    
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
            
            double clusterProb = clusterIndex.getTermProbability(doc.getDocno(), feature);
            
            // smooth cluster LM using J-M
            double smoothedClusterProb = 
                    lambda*clusterProb + (1-lambda)*collectionProb;
            
            // smooth document LM using Dirichlet            
            double smoothedDocProb = 
                    (docFreq + mu*smoothedClusterProb) / 
                    (docLength + mu);
            
            double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
            
            logLikelihood += queryWeight * Math.log(smoothedDocProb);                        
        }
        return logLikelihood;
    }

    
    
}
