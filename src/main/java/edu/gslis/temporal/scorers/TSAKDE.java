package edu.gslis.temporal.scorers;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RKernelDensity;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Calculate temporal language models for all intervals
 * Smooth the document using temporal language models 
 * weighted by KL divergence from the collection.
 */
public class TSAKDE extends TemporalScorer 
{

    String MU = "mu";
    String LAMBDA = "lambda";
    static String ALPHA = "alpha";

    RKernelDensity dist = null;

    
    
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
            Map<Integer, FeatureVector> pis = new TreeMap<Integer, FeatureVector>();
            
            // Totals for each bin
            double[] total = tsIndex.get("_total_");

            // Populate temporal language model for each bin pi_i = LM(bin(t))
            // As a shortcut, this is just the model for features in 
            // the document language model use the "_total_" values to get
            // length(t).
            Iterator<String> dvIt = dv.iterator();
            while(dvIt.hasNext()) {
                String feature = dvIt.next();
                // Time series for feature
                double[] series = tsIndex.get(feature); 
                if (series != null) {
                    // Populate feature vector for each bin
                    for (int i=0; i<series.length; i++) {
                        FeatureVector pi = pis.get(i);
                        if (total[i] == 0) 
                            continue;
                        if (pi == null)
                            pi = new FeatureVector(null);
                        pi.addTerm(feature, series[i]/total[i]);
                        pis.put(i, pi);
                    }
                }
            }
            
            // Document generation likelihood
            double[] scores = new double[pis.size()];
            double z = 0;            
            for (int b: pis.keySet()) {
                //System.out.println(gQuery.getTitle() + " scoring " + b + " of " + pis.size());
                double score = scoreTemporalModel(dv, pis.get(b));
                if (b < pis.size()) {
                    scores[b] = score;
                    z += score;
                }
            }
            
            // Now calculate the score for this document using 
            // a combination of the temporal and collection LM.
            while(queryIterator.hasNext()) 
            {
                String feature = queryIterator.next();
                
                double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
                double docLength = doc.getLength();

                //p(w | C): +1 is necessary when working with partial collections (i.e., latimes)
                double collectionProb = (1 + collectionStats.termCount(feature)) / collectionStats.getTokCount();

                // Weight the probability for each bin by the noramlized KL score
                double timePr = 0;
                for (int b: pis.keySet()) {
                    FeatureVector tfv = pis.get(b);
                    //  timePr += kls[b] * (tfv.getFeatureWeight(feature)/tfv.getLength());
                    if (b < pis.size() && tfv.getLength() > 0)
                        timePr += (scores[b]/z) * (tfv.getFeatureWeight(feature)/tfv.getLength());
                }
                                
                // Smooth temporal LM with collection LM
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
           
        double alpha = paramTable.get(ALPHA);        
        double kde = Math.log(dist.density(TemporalScorer.getTime(doc)));
        
        return alpha*kde + (1-alpha)*logLikelihood;
    }
    
    

    public double scoreTemporalModel(FeatureVector dv, FeatureVector tfv)
    {
        double logLikelihood = 0.0;
        
        
        double add = 1/(double)dv.getFeatureCount();
        
        for (String feature: dv.getFeatures())
        {
                                       
            double tfreq = tfv.getFeatureWeight(feature);
            double tlen = tfv.getLength();
            
            double smoothedProb = (tfreq + add)/(tlen + 1);

            double docWeight = dv.getFeatureWeight(feature);
            
            logLikelihood += docWeight * Math.log(smoothedProb);
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
        double[] x = TemporalScorer.getTimes(hits);
        double[] w = KDEScorer.getUniformWeights(hits);
        dist = new RKernelDensity(x, w);            
    }
}
