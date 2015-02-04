package edu.gslis.temporal.scorers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RUtil;

/**
 * New TSM scorer. 
 */
public class TSMScorer extends TemporalScorer 
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

        // Get the bin for this document       
        double epoch = (Double)doc.getMetadataValue(Indexer.FIELD_EPOCH);
        long docTime = (long)epoch;
        int t = (int)((docTime - startTime)/interval);

        // Ignore documents outside of the temporal bounds
        if (docTime < startTime)
            return Double.NEGATIVE_INFINITY;

        try
        {
            
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
                double tfreq = tsIndex.get(feature, t);
                double tlen = tsIndex.get("_total_", t);

                
                // Use Laplace smoothing for the temporal models. This is just for the case 
                // where lambda = 1
                double timePr = (tfreq + 1) / (tlen + collectionStats.getTokCount());                
                                
                // Smooth the temporal models with the collection model
                double smoothedTemporalProb = 
                        lambda*timePr + (1-lambda)*collectionProb;
                
                // Smooth document LM with smoothed temporal LM            
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
            
            // use NPMI
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
                     //   System.out.println("\t cor: " + terms.get(i) + ", " + terms.get(j) + "=" + c);
                       // for (int l=0; l<npmii.length; l++) {
                       //     System.out.println(terms.get(i) + ", " + terms.get(j) 
                       //             + "," + npmii[l]  + ", " + npmij[l] + "\n");
                       // }
                        
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
        else if (lambda == -2)         
        {
            // Dynamically set lambda using AC
            if (gQuery.getFeatureVector().getFeatureCount() > 1)
            {
                try
                {
                    // Set lambda based on autocorrelation
                    Map<Integer, Map<String, Double>> tms 
                        = createTemporalModels(gQuery.getFeatureVector().getFeatures());
    
                    double[] scores = new double[tms.size()];
                    double z = 0;            
                    for (int bin: tms.keySet()) {
                        // Log-likelihood of query given temporal model
                        double ll = scoreTemporalModel(gQuery.getFeatureVector(), tms.get(bin));
                        scores[bin] = Math.exp(ll);
                        z += scores[bin];
                    }
            
                    // p(theta_i given Q)
                    for (int bin: tms.keySet()) {
                        scores[bin] = scores[bin]/z;
                    }
                    
                    // Calculate autocorrelation at lag 1
                    RUtil rutil = new RUtil();
                    double ac = rutil.acf(scores);
                    if (ac > 0)     
                        paramTable.put(LAMBDA, ac);
                    else 
                        paramTable.put(LAMBDA, 0D); 
                    rutil.close();
                    
                    System.out.println(gQuery.getTitle() + "," + ac);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            else
                paramTable.put(LAMBDA, 0D);
            
            System.err.println(gQuery.getTitle() + " lambda=" + paramTable.get(LAMBDA));            
        }
    }   
    
}
