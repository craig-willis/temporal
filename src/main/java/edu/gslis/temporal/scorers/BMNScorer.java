package edu.gslis.temporal.scorers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Calculate temporal language models for all intervals
 * Smooth the document using temporal language models 
 * weighted by KL divergence from the collection.
 */
public class BMNScorer extends TemporalScorer 
{

    String MU = "mu";
    String LAMBDA = "lambda";
    String SMOOTH = "smooth";

    
    
    public double score(SearchHit doc)
    {
        double logLikelihood = 0.0;
        Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
                
        // Usual Dirichlet parameter
        double mu = paramTable.get(MU);
        // Parameter controlling linear combination of temporal and collection language models.
        double lambda = paramTable.get(LAMBDA);
        double smooth = paramTable.get(SMOOTH);
        
        // Get the bin for this document 
        long docTime = getDocTime(doc);
        int t = getBin(docTime);

        if (docTime < startTime) {
            return Double.NEGATIVE_INFINITY;
        }
       
        try
        {
            FeatureVector dv = doc.getFeatureVector();
            
            Set<String> features = new HashSet<String>();
            features.addAll(dv.getFeatures());
            features.addAll(gQuery.getFeatureVector().getFeatures());
            
            int numBins = tsIndex.getNumBins();
            // Approximate document generation likelihood 
            double max = Double.NEGATIVE_INFINITY;
            int bestBin = t;
            for (int bin = 0; bin < numBins; bin++) {
                // Log-likelihood of document given temporal model
                double ll = 0;
                if (bin == t)
                    ll = scoreTemporalModelIgnore(dv, bin);
                else    
                    ll = scoreTemporalModel(dv, bin);
                    
                if (ll > max)  {
                    max = ll;
                    bestBin = bin;
                }
            }

            //System.out.println(t + "," + bestBin);           
            
            // Now calculate the score for this document using 
            // a combination of the temporal and collection LM.
            queryIterator = gQuery.getFeatureVector().iterator();
            while(queryIterator.hasNext()) 
            {
                String feature = queryIterator.next();
                
                double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
                double docLength = doc.getLength();

                //p(w | C): +1 is necessary when working with partial collections (i.e., latimes)
                double collectionProb = (1 + collectionStats.termCount(feature)) 
                        / collectionStats.getTokCount();
                
                // Temporal model
                double tfreq = tsIndex.get(feature, bestBin);
                double tlen = tsIndex.getLength(bestBin);
                
                double temporalPr = tfreq / tlen; 

                double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);

                
                if (smooth == 1) {
                    // 2-stage                
                    double smoothedTopicProb = 
                            (docFreq + mu*temporalPr) / 
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
                            lambda*smoothedTopicProb + (1-lambda)*temporalPr;
                    
                    logLikelihood += queryWeight * Math.log(smoothedDocProb);                        

                }
                else if (smooth == 3) { 
                    // J-M Smoothed dirichlet

                    double smoothedTempProb = 
                            lambda*temporalPr + (1-lambda)*collectionProb;

                    double smoothedDocProb = 
                            (docFreq + mu*smoothedTempProb) / 
                            (docLength + mu);
                                    
                    logLikelihood += queryWeight * Math.log(smoothedDocProb);                        

                }  
                else {
                    System.err.println("Invalid smoothing model specified.");
                }                           
            }
        } catch (Exception e) {
            e.printStackTrace(); 
        }                        
           
        return logLikelihood;
    }
    
    

      
    @Override
    public void close() {
        try {
            tsIndex.close();            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Override
    public void init(SearchHits hits) {
        

        double lambda = paramTable.get(LAMBDA);

        if (lambda == -1) {
            
            if (gQuery.getFeatureVector().getFeatureCount() > 1)
            {
                // Dynamic smoothing, calculate correlation between NPMI values for all query terms
                Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();            
                List<double[]> npmis = new ArrayList<double[]>();
                List<String> terms = new ArrayList<String>();
                while(queryIterator.hasNext()) 
                {
                    String feature = queryIterator.next();
                    try {
                        double[] npmi = tsIndex.getNpmi(feature);
                        npmis.add(npmi);
                        terms.add(feature);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                PearsonsCorrelation cor = new PearsonsCorrelation();
                double avgCor = 0;
                int k = 0;
                for (int i=0; i<npmis.size(); i++) {
                    for (int j=i; j<npmis.size(); j++) {
                        if (i==j) continue;
//                        double[] npmii = npmis.get(i);
//                        double[] npmij = npmis.get(j);

                        double c = cor.correlation(npmis.get(i), npmis.get(j));
/*                        System.out.println("\t cor: " + terms.get(i) + ", " + terms.get(j) + "=" + c);
                        for (int l=0; l<npmii.length; l++) {
                            System.out.println(terms.get(i) + ", " + terms.get(j) 
                                    + "," + npmii[l]  + ", " + npmij[l] + "\n");
                        }
                        */
                        avgCor += c;
                        k++;
                    }
                }
                avgCor /= k;
                
                if (avgCor > 0)     
                    paramTable.put(LAMBDA, avgCor);
                else 
                    paramTable.put(LAMBDA, 0D);                 
            }
            else
                paramTable.put(LAMBDA, 0D);
            
            System.err.println(gQuery.getTitle() + " lambda=" + paramTable.get(LAMBDA));
        }
    }   
    
}
