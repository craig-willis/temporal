package edu.gslis.temporal.scorers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Calculate temporal language models for all intervals
 * Smooth the document using temporal language models 
 * weighted by KL divergence from the collection.
 */
public class TimeSmoothedScorerAverage extends TemporalScorer 
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

        try
        {
            FeatureVector dv = doc.getFeatureVector();
            
            // Map of feature vectors for each bin(t)
            Map<Integer, FeatureVector> tms = createTemporalModels(dv);
                        
            // Approximate document generation likelihood
            // Score each temporal model with respect to this document.
            double[] scores = new double[tms.size()];
            double z = 0;            
            for (int bin: tms.keySet()) {
                double score = scoreTemporalModel(dv, tms.get(bin));
                scores[bin] = score;
                z += score;
            }

            
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

                // Weight the probability for each temporal model by the normalized score
                double timePr = 0;
                for (int bin: tms.keySet()) {
                    FeatureVector tfv = tms.get(bin);
                    if (tfv.getLength() > 0)
                        timePr += (scores[bin]/z) * (tfv.getFeatureWeight(feature)/tfv.getLength());
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
