package edu.gslis.scorers.temporal;

import java.util.Iterator;

import edu.gslis.main.temporal.TermTimeSeries;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;


/**
 * Log-linear combination
 */
public class QACFTSMLogLinear extends TemporalScorer 
{

    String MU = "mu";
    String LAMBDA = "lambda";
    
    @Override
    public void init(SearchHits hits) {   

    	RUtil rutil = new RUtil();
    	        
    	double lag = paramTable.get("lag");  
    	
    	double numDocs = 1000;
    	if (paramTable.get("k") != null) {
    		numDocs = paramTable.get("k");
    	}
                
        // Build the term time series
        TermTimeSeries ts = new TermTimeSeries(startTime, endTime, interval, 
        		gQuery.getFeatureVector().getFeatures());
        
        if (hits.size() < numDocs) 
        	numDocs = hits.size();
        
        for (int i=0; i< numDocs; i++) {
        	SearchHit hit = hits.getHit(i);
    		long docTime = TemporalScorer.getTime(hit);
    		double score = hit.getScore();
    		ts.addDocument(docTime, score, hit.getFeatureVector());
        }
        ts.smooth();
        
        FeatureVector acfn = new FeatureVector(null);
        for (String term: gQuery.getFeatureVector().getFeatures()) {
        	double[] tsw = ts.getTermDist(term);
            if (tsw == null) {
            	System.err.println("Unexpected null termts for " + term);
            	continue;
            }

        	double sum = sum(tsw);

            double acf = 0;
        	if (sum > 0) {
	        	try {        		
	        		acf = rutil.acf(tsw, (int)lag);
	        	} catch (Exception e) {
	        		e.printStackTrace();        		
	        	}
        	}        	
        	acfn.addTerm(term, acf);
        } 
        
        // Normalize term scores
        scale(acfn);

        gQuery.setFeatureVector(acfn);
        
        synchronized (this) {
        	System.out.println(gQuery.getTitle() 
        			+ " numDocs=" + numDocs + ", mu=" + paramTable.get("mu") 
        			+ ", lag=" + lag);
        	System.out.println(acfn.toString(10));       
        }
                
    }    
    
    
    public double score(SearchHit doc)
    {

        Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
                
        double mu = paramTable.get(MU);
        
        double lambda = paramTable.get(LAMBDA);

        double logLikelihood = 0.0;
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
                    double[] dist = tsIndex.get(feature);
                	
	                if (dist != null)
	                	temporalPr = dist[t]/tsIndex.getLength(t);
                }	

                double docPr = 
                        (docFreq + mu*collectionProb) / 
                        (docLength + mu);
                
                double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
                   
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
