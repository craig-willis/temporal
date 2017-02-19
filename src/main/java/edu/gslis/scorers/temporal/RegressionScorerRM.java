package edu.gslis.scorers.temporal;

import edu.gslis.main.temporal.TermTimeSeries;
import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Query term weights estimated via linear regression
 */
public class RegressionScorerRM extends TemporalScorer {
    
   	public static String FB_DOCS = "fbDocs";     


    @Override
    public void init(SearchHits hits) {   

    	RUtil rutil = new RUtil();
  
       	int numFbDocs = 50;
       	
       	double mu = 0;
       	if (paramTable.get(MU) != null ) 
       		mu = paramTable.get(MU).doubleValue();
       	        
        if (paramTable.get(FB_DOCS) != null ) 
        	numFbDocs = paramTable.get(FB_DOCS).intValue();
        
        if (hits.size() < numFbDocs)
        	numFbDocs = hits.size();
        
        SearchHits fbDocs = new SearchHits(hits.hits().subList(0, numFbDocs));
        FeedbackRelevanceModel rm = new FeedbackRelevanceModel();
        rm.setDocCount(numFbDocs);
        rm.setTermCount(0); // ignored
        rm.setIndex(index);
        rm.setStopper(null);
        rm.setRes(fbDocs);
        rm.build();            
      
        FeatureVector rmVector = rm.asFeatureVector();
                        
        // Get the weights for only the query terms
        FeatureVector brmfv = gQuery.getFeatureVector();
        for (String term: brmfv.getFeatures()) {
        	brmfv.setTerm(term, rmVector.getFeatureWeight(term));        	
        }
        brmfv.normalize();
        
        TermTimeSeries ts = new TermTimeSeries(startTime, endTime, interval, 
        		gQuery.getFeatureVector().getFeatures());
        
        for (SearchHit hit: hits.hits()) {
    		long docTime = TemporalScorer.getTime(hit);
    		double score = hit.getScore();
    		ts.addDocument(docTime, score, hit.getFeatureVector());
        }
        
        double[] background = ts.getBinTotals();
        double sum = sum(background);
        for (int i=0; i<background.length; i++) 
        	background[i] = (background[i]/sum)+0.0000000001;
        
        FeatureVector tsfv = new FeatureVector(null);
        
        double sdp = 0;
        double sdps = 0;
        double stkl = 0;
        double stkli = 0;
        double stklc = 0;
        FeatureVector nidf = new FeatureVector(null);
        for (String term: gQuery.getFeatureVector().getFeatures()) {
        	double idf = Math.log(1 + index.docCount()/index.docFreq(term));
        	nidf.addTerm(term, idf);
        	
        	double[] tsw = ts.getTermFrequencies(term);

        	sum = sum(tsw);
        	if (sum > 0) {
	        	try {        		
	        		sdp += rutil.dp(tsw);  
	        		sdps += rutil.dps(tsw); 
	        	} catch (Exception e) {
	        		e.printStackTrace();        		
	        	}
        	}
        	double tkl = 0;
            for (int i=0; i<tsw.length; i++) {
            	tsw[i] = (tsw[i]/sum)+0.0000000001;
            	tkl += tsw[i] * Math.log(tsw[i]/background[i]);
            }
            
            stkl += tkl;      
            stkli += (Math.exp(-(1/tkl)));
            System.out.println("term=" + term + ",tkl=" + tkl + ",stklc=" + (1 - (Math.exp(-(tkl)))));
        	stklc += (1 - (Math.exp(-(tkl)))); 
        }
        if (stklc == 0) stklc=1;
        	
        nidf.normalize();
        
        for (String term: gQuery.getFeatureVector().getFeatures()) {
           	double[] termts = ts.getTermFrequencies(term);
            if (termts == null) {
            	System.err.println("Unexpected null termts for " + term);
            	continue;
            }

            sum = sum(termts);
            for (int i=0; i<termts.length; i++)
            	termts[i] = (termts[i]/sum)+0.0000000001;
            
            double tkl = 0;
            for (int i=0; i<termts.length; i++) {
            	tkl += termts[i] * Math.log(termts[i]/background[i]);
            }
            
            double tkli = (Math.exp(-(1/tkl)));
            double tklc = 1 - (Math.exp(-(tkl))); 
            
            
            double n = nidf.getFeatureWeight(term);
            if (collectionName.equals("ap-krovetz")) {

            	
            } else if (collectionName.equals("latimes-krovetz")) {
                       	
            	double tklcn = tklc/stklc;	            	
            	System.out.println("tklc=" + tklc + ",stlkc=" + stklc);
            	double rmn = brmfv.getFeatureWeight(term);
            	// 0.05 + 0.45*tklcn + 0.43*rmn
            	double lm = 0.05 + 0.45*tklcn + 0.43*rmn;
	            	
                tsfv.addTerm(term, lm);
                
            } else if (collectionName.equals("tweets2011-krovetz")) {
            	
            	
            } else {
            	System.err.println("No collection match for " + collectionName);
            }

        }  
        tsfv.normalize();
        
        
        synchronized(this) {
        	System.out.println("Orig\n" + gQuery.getFeatureVector().toString(10));
        	System.out.println("RM0" + brmfv.toString(10)); 
        	System.out.println("TSFV\n" + tsfv.toString(10)); 
        }
        
        gQuery.setFeatureVector(tsfv);
        rutil.close();
        
    }         
   
       
}
