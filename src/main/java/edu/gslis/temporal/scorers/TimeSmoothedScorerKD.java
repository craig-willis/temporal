package edu.gslis.temporal.scorers;

import java.text.DateFormat;
import java.util.Iterator;

import umontreal.iro.lecuyer.gof.KernelDensity;
import umontreal.iro.lecuyer.probdist.EmpiricalDist;
import umontreal.iro.lecuyer.probdist.NormalDist;
import weka.estimators.KernelEstimator;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.temporal.util.RKernelDensity;
import edu.gslis.textrepresentation.FeatureVector;


/**
 * Smooth document model using combination of temporal
 * and collection models.
 * 
 * Uses kernel density estimation to estimate p(T | w) given 
 * the temporal language model for the term w. This is then normalized
 * to estimate p(w|T).
 * 
 * This scorer requires an existing TimeSeriesIndex
 * with a matching startTime, endTime and interval. This scorer
 * also requires pre-calculation of normalizing constants
 * 
 * This has three parameters:
 *      mu:         Standard Dirichlet parameter
 *      lambda:     Weight of p(w|T) v p(w|C) in smoothing
 */
public class TimeSmoothedScorerKD extends TemporalScorer 
{

    String MU = "mu";
    String LAMBDA = "lambda";
    
    long startTime = 0;
    long endTime = 0;
    long interval = 0;
    DateFormat df = null;
    
    TimeSeriesIndex index = new TimeSeriesIndex();
    
    public void setTsIndex(String tsIndex) {
        try {
            System.out.println("Opening: " + tsIndex);
            index.open(tsIndex, true);
        } catch (Exception e) {
            e.printStackTrace();
        }               
    }
    public void setDateFormat(DateFormat df) {
        this.df = df;
    }
    public void setQuery(GQuery query) {
        this.gQuery = query;
    }
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }
    public void setInterval(long interval) {
        this.interval = interval;
    }
    
    public double score(SearchHit doc)
    {
        double logLikelihood = 0.0;
        Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
        
        // Document language model smoothed with a linear combination
        // of the temporal language model at bin(t) and the collection language model

        // temporal model        
        double epoch = (Double)doc.getMetadataValue(Indexer.FIELD_EPOCH);
        long docTime = (long)epoch;
        int t = (int)((docTime - startTime)/interval);
        
        // Usual Dirichlet parameter
        double mu = paramTable.get(MU);
        // Parameter controlling linear combination of temporal and collection language models.
        double lambda = paramTable.get(LAMBDA);

        try
        {
            

            double z = index.getNorm("weka", t);  
            
            // Now calculate the score for this document using 
            // a combination of the temporal and collection LM.
            while(queryIterator.hasNext()) 
            {
                String feature = queryIterator.next();
                
                // Get the series for this feature
                double[] hist = index.get(feature);
               
                //p(w | C): +1 is necessary when working with partial collections (i.e., latimes)
                double pwC = (1 + collectionStats.termCount(feature)) / collectionStats.getTokCount();
                                           
                //double timePr = probabilitySSJ(t, hist);
                //double timePr = probabilityR(t, hist);
                double timePr = probabilityWeka(t, hist);
                if (z > 0)
                    timePr /= z;
                
                double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
                double docLength = doc.getLength();
                double pr = (docFreq + mu*(lambda*timePr + (1-lambda)*pwC))/(docLength + mu);
                
                double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
                
                logLikelihood += queryWeight * Math.log(pr);
            }
        } catch (Exception e) {
            e.printStackTrace(); 
        }                        
           
        return logLikelihood;
    }
    
    
    public double probabilityWeka(double t, double[] hist) {
        
        KernelEstimator weka = new KernelEstimator(0.001);
        for (int bin=0; bin<hist.length; bin++) {
            double freq = hist[bin];
            weka.addValue(bin, freq);
        }           
        
        return weka.getProbability(t);
        
    }

    
    public double probabilityR(double t, double[] hist) {
        
        double x[]= new double[hist.length];
        for (int i=0; i<hist.length; i++)
            x[i] = i;
        
        RKernelDensity rkd = new RKernelDensity(x, hist);
        
        
        double sum = 0;
        for (int i=0; i<hist.length; i++)
            sum += rkd.density(i);
        
        double density = rkd.density(t);
        density /= sum;
        
        rkd.close();    
        
        return density;
    }

    public double probabilitySSJ(double t, double[] hist) {
        
        int total =0;
        for (int i=0; i<hist.length; i++) {
            total += hist[i];
        }        
        
        double[] y = new double[1];
        y[0] = t;

        // replicate the histogram into x
        double[] x = new double[total];
        int l=0;
        for (int bin=0; bin<hist.length; bin++) {
            double freq = hist[bin];
            for (int k=0; k<freq; k++) {
                x[l++] = bin;
            }
        }
        double pt = 0;
        if (x.length > 2) {
            
            EmpiricalDist ed = new EmpiricalDist(x);
            double[] density = KernelDensity.computeDensity(ed, new NormalDist(), hist);
            
            double sum=0;
            for (double d: density)
                sum+= d;
            
            double d = density[(int)t];
            
            if (!Double.isNaN(d) && !Double.isInfinite(d))
                pt = d/sum;
        }    
        return pt;
    }
    
    public double kl(FeatureVector p, FeatureVector q) {
        double kl = 0;
        
        Iterator<String> it = p.iterator();
        while(it.hasNext()) {
            String feature = it.next();
            double pi = p.getFeatureWeight(feature)/p.getLength();
            double qi = q.getFeatureWeight(feature)/q.getLength();
            if (pi > 0 && qi > 0)
                kl += pi * Math.log(pi/qi);
        }
        return kl;
    }
    @Override
    public void close() {
        try {
            index.close();            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }   
}
