package edu.gslis.main;

import java.io.File;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.indexes.temporal.TimeSeriesIndex;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.main.temporal.TermTimeSeries;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.scorers.temporal.TemporalScorer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;


/** 
 * Calculate feature values for query terms:
 * 
 *
 */
public class GetFeaturesQL 
{
	static int MAX_RESULTS=1000;
	
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( GetFeaturesQL.class.getCanonicalName(), options );
            return;
        }
        String indexPath = cl.getOptionValue("index");
        String tsIndexPath = cl.getOptionValue("tsindex");
        String topicsPath = cl.getOptionValue("topics");	
        
        long startTime = Long.parseLong(cl.getOptionValue("startTime"));
        long endTime  = Long.parseLong(cl.getOptionValue("endTime"));
        long interval = Long.parseLong(cl.getOptionValue("interval"));  
        int fbDocs = Integer.parseInt(cl.getOptionValue("fbDocs", "50"));
        double minacf = Double.parseDouble(cl.getOptionValue("minacf"));
        double maxacf = Double.parseDouble(cl.getOptionValue("maxacf"));

        boolean smooth = cl.hasOption("smooth");
        String tsPath = null;        
        if (cl.hasOption("ts")) 
        	tsPath = cl.getOptionValue("ts");
        
        String plotPath = null;        
        if (cl.hasOption("plot")) 
        	plotPath = cl.getOptionValue("plot");        

        GQueries queries = null;
        if (topicsPath.endsWith("indri")) 
            queries = new GQueriesIndriImpl();
        else
            queries = new GQueriesJsonImpl();
        queries.read(topicsPath);
        
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
        index.setTimeFieldName(Indexer.FIELD_EPOCH);
        
        TimeSeriesIndex tsindex = new TimeSeriesIndex();
        tsindex.open(tsIndexPath, true);
        
        RUtil rutil = new RUtil();
        Iterator<GQuery> queryIt = queries.iterator();
        
        System.out.println("query,term,rmn,dpn,dpsn,tkln,tklin,tklcn,pwr,pwc,nidf,qacf,acf,acfn,acfs,burst,cafcs,dpc,dpsc,scacf,cacf1,cacf2,cpacf1,cpacf2,qccf,ccf");

        double[] cbackground = tsindex.get("_total_");
        while (queryIt.hasNext()) {
            GQuery query = queryIt.next();
  
            SearchHits results = index.runQuery(query, MAX_RESULTS);
                        
            TermTimeSeries ts = new TermTimeSeries(startTime, endTime, interval, 
            		query.getFeatureVector().getFeatures());
            
            FeatureVector dfv = new FeatureVector(null);
            for (SearchHit result: results.hits()) {
        		long docTime = TemporalScorer.getTime(result);
        		double score = result.getScore();
        		ts.addDocument(docTime, score, result.getFeatureVector());
        		
        		for (String term: query.getFeatureVector().getFeatures()) {
        			dfv.addTerm(term, result.getFeatureVector().getFeatureWeight(term));
        		}
            }
            dfv.normalize();
            
            if (smooth)
            	ts.smooth();
                        
            if (tsPath != null)
            	ts.save(tsPath + "/" + query.getTitle() + ".ts");
            FeatureVector rmfv = getRMFV(results, fbDocs, 100, index, query.getFeatureVector().getFeatures());
            
            double[] background = ts.getBinDist();
            double qacf = rutil.acf(background, 1);
 
            
            FeatureVector nidf = new FeatureVector(null);   // Normalized IDF
            FeatureVector dp = new FeatureVector(null);     // Dominant period
            FeatureVector dps = new FeatureVector(null);    // Dominant power spectrum
            FeatureVector tklc = new FeatureVector(null);   // Temporal KL, complement
            FeatureVector tkli = new FeatureVector(null);   // Temporal KL, inverse
            FeatureVector tkln = new FeatureVector(null);   // Temporal KL
            FeatureVector acf = new FeatureVector(null);    // ACF
            FeatureVector acfn = new FeatureVector(null);   // Normalized ACF
            FeatureVector acfs = new FeatureVector(null);   // Scaled ACF
            FeatureVector bd = new FeatureVector(null);     // Bursts
            FeatureVector cacfs = new FeatureVector(null);  // Collection ACF
            FeatureVector scacfs = new FeatureVector(null); // Smoothed collection ACF
            FeatureVector cdps = new FeatureVector(null);   // Collection DPS
            FeatureVector cdp = new FeatureVector(null);    // Collection DP
            FeatureVector cacf1 = new FeatureVector(null);   
            FeatureVector cacf2 = new FeatureVector(null);   
            FeatureVector cpacf1 = new FeatureVector(null); 
            FeatureVector cpacf2 = new FeatureVector(null);
            FeatureVector qccf = new FeatureVector(null);
            FeatureVector ccf = new FeatureVector(null);
            
            FeatureVector cfv = new FeatureVector(null);
            for (String term: query.getFeatureVector().getFeatures()) {
               	double[] tsw = ts.getTermDist(term);
                if (tsw == null) {
                	System.err.println("Unexpected null termts for " + term);
                	continue;
                }
                
                double[] ctsw = tsindex.get(term);
                if (ctsw == null) {
                	System.err.println("Unexpected null collection termts for " + term);
                	continue;
                }            
                
                if (plotPath != null) {
                	File dir = new File(plotPath + "/" + query.getTitle());
                	dir.mkdirs();
                	ts.plot(term, plotPath + "/" + query.getTitle());
                }

            	double idf = Math.log(1 + index.docCount()/index.docFreq(term));
            	nidf.addTerm(term, idf);

            	double sum = sum(tsw);

            	double tkl = 0;
                for (int i=0; i<tsw.length; i++) {
                	if (tsw[i] >0 && background[i] > 0)
                		tkl += tsw[i] * Math.log(tsw[i]/background[i]);
                }
                
            	if (sum > 0) {
    	        	try {        		
    	        		dp.addTerm(term, rutil.dp(tsw)); 
    	        		dps.addTerm(term, rutil.dps(tsw)); 
    	        		
    	        		double acf2 = rutil.acf(tsw, 1);
    	        		acfn.addTerm(term, acf2);
    	        		acfs.addTerm(term, acf2);
    	        		
    	        		acf.addTerm(term, acf2);
    	        		
    	        		bd.addTerm(term, rutil.bursts(tsw));
    	        		
    	        		cacfs.addTerm(term, rutil.acf(ctsw, 2));
    	        		cdp.addTerm(term, rutil.dp(ctsw)); 
    	        		cdps.addTerm(term, rutil.dps(ctsw));
    	        		    	       
    	        		
		        		cacf1.addTerm(term, rutil.acf(ctsw, 1));
		        		cacf2.addTerm(term, rutil.acf(ctsw, 2));
		        		cpacf1.addTerm(term, rutil.pacf(ctsw, 1));
		        		cpacf2.addTerm(term, rutil.pacf(ctsw, 2));
		        		scacfs.addTerm(term, rutil.sma_acf(ctsw, 2, 3));
		        		
		        		qccf.addTerm(term, 1-rutil.ccf(background, tsw, 0));
		        		ccf.addTerm(term, 1-rutil.ccf(cbackground, ctsw, 0));
    	        	} catch (Exception e) {
    	        		e.printStackTrace();        		
    	        	}
            	}
            	
            	tkln.addTerm(term, tkl);
                tkli.addTerm(term, (Math.exp(-(1/tkl))));           
            	tklc.addTerm(term, 1 - (Math.exp(-(tkl))));

            	cfv.addTerm(term,index.termFreq(term)/index.termCount() );
            }  
            rmfv.normalize();
            nidf.normalize();
            dp.normalize();
            dps.normalize();
            normalize(tkli);
            normalize(tkln);
            normalize(tklc);
           // normalize(acfn);
            acfn.normalize();
            scale(acfs);
            scale(cacfs);
            scale(scacfs);
            cdps.normalize();
            cdp.normalize();
            scale(cacf1, minacf, maxacf);
            scale(cacf2, minacf, maxacf);
            //scale(cpacf1);
            //scale(cpacf2);
            scale(qccf);
            scale(ccf);
            

            for (String term: query.getFeatureVector().getFeatures()) {

            	double rm = rmfv.getFeatureWeight(term);
            	double dpn = dp.getFeatureWeight(term);
            	double dpsn = dps.getFeatureWeight(term);
            	double tkl = tkln.getFeatureWeight(term);
            	double tklin = tkli.getFeatureWeight(term);
            	double tklcn = tklc.getFeatureWeight(term);
            	double pwc = cfv.getFeatureWeight(term);
            	double idf = nidf.getFeatureWeight(term);
            	double pwr = dfv.getFeatureWeight(term);
            	double acf1 = acf.getFeatureWeight(term);
            	double acf2 = acfn.getFeatureWeight(term);
            	double acf3 = acfs.getFeatureWeight(term);
            	
            	double burst = bd.getFeatureWeight(term);
            	double cacf = cacfs.getFeatureWeight(term);
            	double scacf = scacfs.getFeatureWeight(term);
            	double dpc = cdp.getFeatureWeight(term);
            	double dpsc = cdps.getFeatureWeight(term);
            	
        		double acf1c = cacf1.getFeatureWeight(term);	        		
        		double acf2c = cacf2.getFeatureWeight(term);	        		
        		double pacf1c = cpacf1.getFeatureWeight(term);	        		
        		double pacf2c = cpacf2.getFeatureWeight(term);	        
        		double ccfq = qccf.getFeatureWeight(term);	        
        		double ccfc = ccf.getFeatureWeight(term);	        
            	
                System.out.println(query.getTitle() + "," + term 
                	+ "," + rm + "," + dpn + "," + dpsn 
                	+ "," + tkl + "," + tklin + "," +  tklcn
                	+ "," + pwr + "," + pwc + "," + idf + "," + qacf + "," + acf1 + "," + acf2 
                	+ "," + acf3 + "," + burst + "," + cacf + "," + dpc + "," + dpsc + "," + scacf 
                	+ "," + acf1c + "," + acf2c + "," + pacf1c + "," + pacf2c + "," + ccfq + "," + ccfc);
            }            
        }
    }
        
    public static double sum(double[] d) {
    	double sum = 0;
    	if (d == null)
    		return 0;
    	for (double x: d)
    		sum += x;
    	return sum;
    }
    
    public static double normalize(double x, double[] d) {
    	
    	double min=Double.POSITIVE_INFINITY;
    	double max=Double.NEGATIVE_INFINITY;
    	for (double v: d) {
    		if (v < min) 
    			min = v;
    		if (v > max) 
    			max =v;
    	}
    	System.out.println("x=" + x + ",min=" + min + ",max=" + max);
    	return (x - min)/(max - min);
    }
    
    public static FeatureVector getRMFV(SearchHits hits, int numFbDocs, int numFbTerms, IndexWrapper index,
    		Set<String> terms) {
        
    	if (hits.size() < numFbDocs)
    		numFbDocs = hits.size();
        SearchHits fbDocs = new SearchHits(hits.hits().subList(0, numFbDocs));
        FeedbackRelevanceModel rm = new FeedbackRelevanceModel();
        rm.setDocCount(numFbDocs);
        rm.setTermCount(numFbTerms);
        rm.setIndex(index);
        rm.setStopper(null);
        rm.setRes(fbDocs);
        rm.build(); 
        
        FeatureVector rfv = rm.asFeatureVector();
        FeatureVector qfv = new FeatureVector(null);
        for (String term: terms) {
        	qfv.setTerm(term,  rfv.getFeatureWeight(term));
        }

        //qfv.normalize();
        return qfv;
    }

    public static void scale(FeatureVector fv, double min, double max) {
    	for (String term: fv.getFeatures()) {
    		double x = fv.getFeatureWeight(term);
    		double z = (x - min)/(max -min);
    		fv.setTerm(term, z);
    	}
      	fv.normalize(); 	
    }
        
    public static void scale(FeatureVector fv) {    	
    	for (String term: fv.getFeatures()) {
    		double x = fv.getFeatureWeight(term);
    		double z = x + 1;
    		fv.setTerm(term, z);
    	}
    	normalize(fv);
//    	fv.normalize();
    }
    
    public static void normalize(FeatureVector fv) {    	
    	double min = Double.POSITIVE_INFINITY;
    	for (String term: fv.getFeatures()) {
    		double x = fv.getFeatureWeight(term);
    		if (x < min) min = x;    		
    	}

    	if (min < 0) {
	    	for (String term: fv.getFeatures()) {
	    		double x = fv.getFeatureWeight(term);
	    		double z = Math.abs(min) + x;
	    		fv.setTerm(term, z);
	    	}    	
    	}
    	fv.normalize();
    }

    public static void normalize(FeatureVector fv, double min, double max) {    	
    	for (String term: fv.getFeatures()) {
    		double x = fv.getFeatureWeight(term);
    		double z = (x - min)/(max - min);
    		fv.setTerm(term, z);
    	}    	
    }

    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "Path to input index");
        options.addOption("tsindex", true, "Path to collection time series index");
        options.addOption("topics", true, "Path to topics file");
        options.addOption("startTime", true, "Collection start time");
        options.addOption("endTime", true, "Collection end time");
        options.addOption("interval", true, "Collection interval");       
        options.addOption("smooth", false, "Smooth the timeseries");
        options.addOption("fbDocs", true, "Number of feedback docs");
        options.addOption("ts", true, "Save the timeseries to this file");
        options.addOption("minacf", true, "Minimum ACF for scaling");
        options.addOption("maxacf", true, "Maximum ACF for scaling");
        
        options.addOption("plot", true, "Plot");
        return options;
    }

}
