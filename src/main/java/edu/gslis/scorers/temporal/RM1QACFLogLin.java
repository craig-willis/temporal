package edu.gslis.scorers.temporal;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.main.temporal.TermTimeSeries;
import edu.gslis.queries.GQuery;
import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;


public class RM1QACFLogLin extends TemporalScorer {
    
    
    @Override
    public void init(SearchHits hits) {   

    	RUtil rutil = new RUtil();
    	
    	int numFbDocs = 50;
    	int numFbTerms = 20;
    	int lag = 2;
    	double lambda = 0.5;
    	
        if (paramTable.get("fbDocs") != null ) 
        	numFbDocs = paramTable.get("fbDocs").intValue();
        
        if (paramTable.get("fbTerms") != null ) 
        	numFbTerms = paramTable.get("fbTerms").intValue();
        
        if (paramTable.get("lag") != null ) 
        	lag = paramTable.get("lag").intValue();

        if (paramTable.get("lambda") != null ) 
        	lambda = paramTable.get("lambda");
        
    	double numDocs = 1000;
    	if (paramTable.get("k") != null) {
    		numDocs = paramTable.get("k");
    	}
                
        
        if (hits.size() < numFbDocs)
        	numFbDocs = hits.size();

        if (hits.size() < numDocs)
        	numDocs = hits.size();
        

        // Build the RM1 model
        SearchHits fbDocs = new SearchHits(hits.hits().subList(0, numFbDocs));
        FeedbackRelevanceModel rm = new FeedbackRelevanceModel();
        rm.setDocCount(numFbDocs);
        rm.setTermCount(0); // ignored
        rm.setIndex(index);
        rm.setStopper(null);
        rm.setRes(fbDocs);
        rm.build();            
      
        FeatureVector rm1fv = rm.asFeatureVector();
        rm1fv.clip(numFbTerms);
        rm1fv.normalize();
        
        // Build the term time series
        TermTimeSeries ts = new TermTimeSeries(startTime, endTime, interval, 
        		rm1fv.getFeatures());
        
        for (int i=0; i< numDocs; i++) {
        	SearchHit hit = hits.getHit(i);
        	if (hit.getMetadataValue(Indexer.FIELD_EPOCH) != null) {
	    		long docTime = TemporalScorer.getTime(hit);
	    		double score = hit.getScore();
	    		ts.addDocument(docTime, score, hit.getFeatureVector());
        	} else {
        		System.err.println("No epoch for document " + hit.getDocno() + " i = " 
        					+ i + ", size=" + hits.size());
        	}
        }
        ts.smooth();
                
        
        FeatureVector acfn = new FeatureVector(null);
        for (String term: rm1fv.getFeatures()) {
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

        FeatureVector rm1acf = interpolate(acfn, rm1fv, lambda);
        
        gQuery.setFeatureVector(rm1acf);
        
        synchronized(this) {
        	System.out.println(gQuery.getTitle() 
        			+ " numFbDocs=" + numFbDocs + ", numFbTerms=" + numFbTerms 
        			+ ", lag=" + lag + ", lambda=" + lambda);
        	System.out.println(rm1fv.toString(10));    
        	System.out.println(acfn.toString(10));    
        	System.out.println(rm1acf.toString(10));        
        }
        
        rutil.close();

    }    
    
	public static FeatureVector interpolate(FeatureVector x, FeatureVector y, double xWeight) {
		FeatureVector z = new FeatureVector(null);
		Set<String> vocab = new HashSet<String>();
		vocab.addAll(x.getFeatures());
		vocab.addAll(y.getFeatures());
		Iterator<String> features = vocab.iterator();
		while(features.hasNext()) {
			String feature = features.next();
			double weight  = 0.0;
			if(xWeight >= 0 && xWeight <= 1) {
				weight = Math.pow(x.getFeatureWeight(feature), xWeight)*Math.pow(y.getFeatureWeight(feature), (1-xWeight)); 
			} else {
				System.err.println("Mixing weight is not between 0 and 1. Performing unweighted mixing.");
				weight = x.getFeatureWeight(feature) + y.getFeatureWeight(feature);
			}
			z.addTerm(feature, weight);
		}
		z.normalize();
		return z;
	}


    public GQuery getQuery() {
    	return gQuery;
    }
    
}
