package edu.gslis.scorers.temporal;

import java.util.Iterator;

import edu.gslis.main.temporal.TermTimeSeries;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

public class QTSMScorer extends TemporalScorer 
{

    String MU = "mu";
    String LAMBDA = "lambda";
    
    TermTimeSeries ts = null;
    public void init(SearchHits hits) {  
        // Build the term time series
        ts = new TermTimeSeries(startTime, endTime, interval, 
        		gQuery.getFeatureVector().getFeatures());
        
        for (int i=0; i< hits.size(); i++) {
        	SearchHit hit = hits.getHit(i);
    		long docTime = TemporalScorer.getTime(hit);
    		double score = hit.getScore();
    		ts.addDocument(docTime, score, hit.getFeatureVector());
        }
        ts.smooth();
    }
    
    public double score(SearchHit doc)
    {
        double logLikelihood = 0.0;
        Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
                
        // Dirichlet parameter controlling amount of smoothing using temporal model
        double mu = paramTable.get(MU);
        
        // Parameter controlling linear combination of smoothed document and collection language models.
        double lambda = paramTable.get(LAMBDA);

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

                // Get the bin for this document 
                long docTime = getDocTime(doc);
                int t = getBin(docTime);
                
                double temporalPr = 0;                
                // Only use temporal information if within the timeframe
                if (docTime >= startTime && docTime <= endTime) {
	                double[] dist = ts.getTermDist(feature);
	                	
	                if (dist != null)
	                	temporalPr = dist[t];
                }
            
                double docPr = 
                        (docFreq + mu*collectionProb) / 
                        (docLength + mu);
                
                double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
                                
                //System.out.println(feature + ":" + docPr + "," + temporalPr);
                if (temporalPr > 0)
                	logLikelihood += queryWeight * Math.log(Math.pow(docPr, lambda)*Math.pow(temporalPr, 1-lambda)); 
                else
                	logLikelihood += queryWeight * Math.log(docPr); 
            }

                
        } catch (Exception e) {
            e.printStackTrace(); 
        }                        
           
        return logLikelihood;
    }

      
    @Override
    public void close() {
    }
    
}
