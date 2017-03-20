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

    @Override
    public void init(SearchHits hits) {   

    	RUtil rutil = new RUtil();
  
       	double mu = 0;
       	if (paramTable.get(MU) != null ) 
       		mu = paramTable.get(MU).doubleValue();
       	
        TermTimeSeries ts = new TermTimeSeries(startTime, endTime, interval, 
        		gQuery.getFeatureVector().getFeatures());
        
        for (SearchHit hit: hits.hits()) {
    		long docTime = TemporalScorer.getTime(hit);
    		double score = hit.getScore();
    		ts.addDocument(docTime, score, hit.getFeatureVector());
        }
        ts.smooth();
        
        double[] background = ts.getBinTotals();
        /*
        double sum = sum(background);
        for (int i=0; i<background.length; i++) 
        	background[i] = (background[i]/sum);
        	*/        
        
        FeatureVector nidf = new FeatureVector(null);       
        FeatureVector dp = new FeatureVector(null);
        FeatureVector dps = new FeatureVector(null);
        FeatureVector tklc = new FeatureVector(null);
        FeatureVector tkli = new FeatureVector(null);
        FeatureVector tkln = new FeatureVector(null);
        FeatureVector acfn = new FeatureVector(null);
        
        for (String term: gQuery.getFeatureVector().getFeatures()) {
        	
        	double[] tsw = ts.getTermFrequencies(term);
            if (tsw == null) {
            	System.err.println("Unexpected null termts for " + term);
            	continue;
            }

        	double idf = Math.log(1 + index.docCount()/index.docFreq(term));
        	nidf.addTerm(term, idf);

        	double sum = sum(tsw);

        	double tkl = 0;
            for (int i=0; i<tsw.length; i++) {
            	tsw[i] = (tsw[i]/sum);
            	if (tsw[i] >0 && background[i] > 0)
            		tkl += tsw[i] * Math.log(tsw[i]/background[i]);
            }
            
        	if (sum > 0) {
	        	try {        		
	        		dp.addTerm(term, rutil.dp(tsw)); 
	        		dps.addTerm(term, rutil.dps(tsw)); 
	        		acfn.addTerm(term, rutil.acf(tsw));
	        	} catch (Exception e) {
	        		e.printStackTrace();        		
	        	}
        	}
        	
        	tkln.addTerm(term, tkl);
            tkli.addTerm(term, (Math.exp(-(1/tkl))));           
        	tklc.addTerm(term, 1 - (Math.exp(-(tkl)))); 
        }
        nidf.normalize();
        dp.normalize();
        dps.normalize();
        normalize(tkli);
        normalize(tkln);
        normalize(tklc);
        normalize(acfn);
        
        FeatureVector tsfv = new FeatureVector(null);

        for (String term: gQuery.getFeatureVector().getFeatures())
        {
            
            double n = nidf.getFeatureWeight(term);
        	double tklin = tkli.getFeatureWeight(term);
        	double tklcn = tklc.getFeatureWeight(term);
        	double dpsn = dps.getFeatureWeight(term);
        	double acf = acfn.getFeatureWeight(term);
        	
            if (collectionName.equals("ap")) {
            	//double lm = (8 - 5*dpsn - 3*tklin);
            	
            	//double lm = (5 - Math.log(dpsn) - 3*tklin);
            	//if (lm == 0) lm = 1;
            	//double weight = 1/lm;
            	
//            	double weight = 1/(7.2 - 5.7*tklcn);
            	
            	//double lm = 3 - 3*acf;
            	//if (lm == 0) lm = 1;
            	//double weight = Math.pow((1/lm), 2);
            	
            	//tsfv.addTerm(term, dps.getFeatureWeight(term));
            	tsfv.addTerm(term, acf);
            }
            else if (collectionName.equals("latimes")) {
            	//double weight = 0.10 + 0.2*dpsn + 0.5*tklcn;
            	//double weight = 0.10 + 0.7*tklcn;
            	//double weight = 0.03 + 0.93*acf;
            	tsfv.addTerm(term, acf);	            	
            }
            else if (collectionName.startsWith("tweets")) {
            	//double weight = 0.01 + 0.95*acf;
                tsfv.addTerm(term, acf);	            	
            } else {
            	System.err.println("No collection match for " + collectionName);
            }

        }  
        tsfv.normalize();
                
        synchronized(this) {
        	System.out.println(gQuery.getTitle() + " mu=," + mu);
        	System.out.println("Orig\n" + gQuery.getFeatureVector().toString(10));
        	System.out.println("Final\n" + tsfv.toString(10));  
        }
        
        gQuery.setFeatureVector(tsfv);
        rutil.close();
        
    }           
    
}
