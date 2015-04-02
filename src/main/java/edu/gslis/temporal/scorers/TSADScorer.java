    package edu.gslis.temporal.scorers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

/**
 * Calculate temporal language models for all intervals
 * Smooth documents using term probabilities based on 
 * Calculate temporal language models for all intervals
 * Smooth the document using temporal language models 
 * weighted by KL divergence from the collection.
 */
public class TSADScorer extends TemporalScorer 
{

    String MU = "mu";
    String LAMBDA = "lambda";
    
    
    
    public double score(SearchHit doc)
    {
        double logLikelihood = 0.0;
        Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
                
        // Usual Dirichlet parameter
        double mu = paramTable.get(MU);
        // Parameter controlling linear combination of temporal and collection language models.
        double lambda = paramTable.get(LAMBDA);
        
        double epoch = (Double)doc.getMetadataValue(Indexer.FIELD_EPOCH);
        long docTime = (long)epoch;
        int t = (int)((docTime - startTime)/interval);

        if (docTime < startTime) {
            return Double.NEGATIVE_INFINITY;
        }

        try
        {
                        

            int numBins = tsIndex.getNumBins();

            double[] scores = new double[numBins];
            double z = 0;     
            double max = Double.NEGATIVE_INFINITY;
            int maxBin = t;
            for (int bin = 0; bin < numBins; bin++) {
                // Log-likelihood of document given temporal model
                double ll = 0;
                if (bin == t)
                    ll = this.scoreTemporalModelIgnore(doc.getFeatureVector(), bin);
                else
                    ll = scoreTemporalModel(doc.getFeatureVector(), bin);
                    
                scores[bin] = ll;
                if (ll > max)  {
                    max = ll;
                    maxBin = bin;
                }
            }
            int shift = (int)(-1*(max + 99)/100) * 100;
            
            for (int i=0; i<scores.length; i++) {
                scores[i] = Math.exp(scores[i]+shift);
                z += scores[i];
            }
            
            // p(theta_i given Q)
            for (int bin = 0; bin < numBins; bin++) {
                scores[bin] = scores[bin]/z;                
                //System.out.println(gQuery.getTitle() + ", " + bin + "," + scores[bin]);
            }
            
            System.out.println(t + "," + maxBin);
            
            
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
                
                // Sum of probabilities of this term across all bins
                double timePr = 0;
                for (int bin = 0; bin < numBins; bin++) {
                    double tfreq = tsIndex.get(feature, bin);
                    double tlen = tsIndex.get("_total_", bin);

                    timePr += (scores[bin]) * (tfreq/tlen);
                }
                           
                double smoothedTemporalProb = 
                        lambda*timePr + (1-lambda)*collectionProb;
                
                // Smooth document LM with topic LM            
                double smoothedDocProb = 
                        (docFreq + mu*smoothedTemporalProb) / 
                        (docLength + mu);
                
                double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
                
                logLikelihood += queryWeight * Math.log(smoothedDocProb);                  
            }
        } catch (Exception e) {
            e.printStackTrace(); 
        }                        
           
        return logLikelihood;
    }
    
    

      
    @Override
    public void close() {
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
