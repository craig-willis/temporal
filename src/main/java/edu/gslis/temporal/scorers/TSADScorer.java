package edu.gslis.temporal.scorers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;

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
            FeatureVector dv = doc.getFeatureVector();
                        
            Set<String> features = new HashSet<String>();
            features.addAll(dv.getFeatures());
            features.addAll(gQuery.getFeatureVector().getFeatures());
            // Map of feature vectors for each bin(t)
            Map<Integer, Map<String, Double>> tms = createTemporalModels(features);
                        

            // Get the current bin
            Map<String, Double> dtm = tms.get(t);
            if (dtm != null) {
                // Remove this document from the bin
                for (String feature: dv.getFeatures()) {
                    double w2 = dv.getFeatureWeight(feature);
                    double w1 = dtm.get(feature);
                    dtm.put(feature, w1 - w2);
                }
                double total = dtm.get("_total_");
                total = total - dv.getLength();
                dtm.put("_total_", total);
                tms.put(t, dtm);
            }
            // Approximate document generation likelihood 
            double[] scores = new double[tms.size()];
            double z = 0;     
            double max = Double.NEGATIVE_INFINITY;
            int maxBin = t;
            for (int bin: tms.keySet()) {
                // Log-likelihood of document given temporal model
                double ll = scoreTemporalModel(dv, tms.get(bin));
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
            for (int bin: tms.keySet()) {
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
                
                double timePr = 0;
                for (int bin: tms.keySet()) {
                    Map<String, Double> tm = tms.get(bin);
                    if (tm.get(feature) == null) {
                        System.err.println("Missing " + feature + " for " + bin);
                        continue;
                    }
                    if (tm.get("_total_") > 0)
                        timePr += (scores[bin]) * (tm.get(feature) / tm.get("_total_"));
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
