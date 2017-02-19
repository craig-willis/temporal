package edu.gslis.scorers.temporal;

import edu.gslis.main.temporal.TermTimeSeries;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Query term weights estimated via linear regression
 */
public class RegressionScorer extends TemporalScorer {
    public static String LAMBDA = "lambda";

    @Override
    public void init(SearchHits hits) {   

    	RUtil rutil = new RUtil();
       	double lambda = 1.0;
  
       	double mu = 0;
       	if (paramTable.get(MU) != null ) 
       		mu = paramTable.get(MU).doubleValue();
       	
        if (paramTable.get(LAMBDA) != null ) 
        	lambda = paramTable.get(LAMBDA).doubleValue();
        
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
        	stklc += 1 - (Math.exp(-(tkl))); 
        }
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
            	
            	// 0.12 + -0.23*tkli + 0.72*nidf
            	
            	double lm = 0.12 - 0.07*tkl + 0.75*n;
            	System.out.println("tkl=" + tkl + ",nidf=" + n + ",lm=" + lm);

                tsfv.addTerm(term, lm);
            	
            } else if (collectionName.equals("latimes-krovetz")) {
           
            	double lm=0;
            	if (sum > 0) {
	            	double dpn = 0 ;
	            	try {
	            		dpn = rutil.dp(termts)/sdp;
	            	} catch (Exception e) {
	            		e.printStackTrace();
	            	}
	            	double tklcn = tklc/stklc;
	            	
	            	// LM learned over even topics, using mu=500 from QL via cross validation
	            	// 0.16 - 10*tkl + 0.80*nidf
	            	lm = 0.16 - 0.10*tkl + 0.80*n;
	            		
	            	// # 0.1 + 0.25*dpn + 0.52*tklcn
	            	//lm = 0.1 + 0.25*dpn + 0.50*tklcn;
	            	
	            	//System.out.println("dpn=" + dpn + ",tklc=" + tklc + ",stklc=" + stklc + ",lm=" + lm);
            	}

                tsfv.addTerm(term, lm);
                
            } else if (collectionName.equals("tweets2011-krovetz")) {
            	
            	double lm=0;
            	if (sum > 0) {
	            	double dpsn = 0 ;
	            	try {
	            		dpsn = rutil.dps(termts)/sdps;
	            	} catch (Exception e) {
	            		e.printStackTrace();
	            	}
	            	
	            	lm = 0.31 + 0.37*dpsn - 0.28*tklc;
	            	
	            	System.out.println("dpsn=" + dpsn + ",tklc=" + tklc);

            	}
            	tsfv.addTerm(term, lm);
            	
            } else {
            	System.err.println("No collection match for " + collectionName);
            }

        }  
        tsfv.normalize();
        
        gQuery.getFeatureVector().normalize();
        FeatureVector fv =
        		FeatureVector.interpolate(gQuery.getFeatureVector(), tsfv, lambda);
        
        synchronized(this) {
        	System.out.println(gQuery.getTitle() + " mu=," + mu + ", lambda=" + lambda);
//        	System.out.println(gQuery.getTitle() + " mu=," + mu);
        	System.out.println("Orig\n" + gQuery.getFeatureVector().toString(10));
//        	System.out.println("TKL\n" + tsfv.toString(10)); 
        	System.out.println("Final\n" + tsfv.toString(10));  
        }
        
        gQuery.setFeatureVector(fv);
//        gQuery.setFeatureVector(tsfv);
        rutil.close();
        
    }                  
}
