package edu.gslis.scorers.temporal;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import edu.gslis.main.temporal.TermTimeSeries;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RKernelDensity;

/**
 * Query likelihood weighted by temporal KL
 */
public class TQLScorer extends TemporalScorer {
    
    public static String LAMBDA = "lambda";
                
    public TermTimeSeries ts = null;
    
    Map<String, RKernelDensity> densities = new HashMap<String, RKernelDensity>();
    
    @Override
    public void init(SearchHits hits) {   
        
        ts = new TermTimeSeries(startTime, endTime, interval, 
        		gQuery.getFeatureVector().getFeatures());
        
        for (SearchHit hit: hits.hits()) {
    		long docTime = TemporalScorer.getTime(hit);
    		double score = hit.getScore();
    		ts.addDocument(docTime, score, hit.getFeatureVector());
        }
        
        ts.smooth();
        
        
        // Get density for each term
        for (String term: gQuery.getFeatureVector().getFeatures()) {
        	double[] tws = ts.getTermDist(term);
            if (tws == null) {
            	System.err.println("Unexpected null termts for " + term);
            	continue;
            }
            

            try {
            	RKernelDensity density = new RKernelDensity(tws);
            	densities.put(term, density);
            } catch (Exception e) {
                String str = term;
                for (int i=0; i<tws.length; i++) {
                	str += "," + tws[i];
                }

                System.out.println(str);
            	System.err.println("Error estimating density for query " + gQuery.getTitle() + " " + term);
            	e.printStackTrace();
            }
        }
    }   
    
    
    public double score(SearchHit doc) 
    {
        // Get the bin for this document 
        long docTime = getDocTime(doc);
        
        double logLikelihood = 0.0;
        Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
        while(queryIterator.hasNext()) 
        {
            String feature = queryIterator.next();
            double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
            double docLength = doc.getLength();
            
            RKernelDensity density = densities.get(feature);
            double tpr = 1;
            if (density != null)
            	tpr = density.density(docTime);
            
            // The 1 here is because some query terms in TREC don't appear in all collections when we 
            // separate out sources.
            double collectionProb = (1 + collectionStats.termCount(feature)) / collectionStats.getTokCount();
            double pr = (docFreq + 
                    paramTable.get(MU)*collectionProb) / 
                    (docLength + paramTable.get(MU));
            double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
            logLikelihood += queryWeight * Math.log(pr*tpr);                        
        }
        return logLikelihood;
    }
    
    public GQuery getQuery() {
    	return gQuery;
    }
    

}
