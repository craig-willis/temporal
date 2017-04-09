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
        
        System.out.println("query,term,rmn,nidf,qacf2,qacfn2,qacfs2,cacf2,cacf2s,scacfs,ccfq,ccfc");
        
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
             
            FeatureVector nidffv = new FeatureVector(null);   // Normalized IDF
            FeatureVector qacffv = new FeatureVector(null);   // Raw ACF, lag 2
            FeatureVector qacfnfv = new FeatureVector(null);  // Normalized query ACF, lag 2
            FeatureVector qacfsfv = new FeatureVector(null);  // Scaled ACF, lag 2
            FeatureVector cacf2fv = new FeatureVector(null);  // Collection ACF, lag 2
            FeatureVector cacfs2fv = new FeatureVector(null);  // Collection ACF, scaled, lag2
            FeatureVector scacfsfv = new FeatureVector(null); // Smoothed collection ACF
            FeatureVector qccffv = new FeatureVector(null);   // Query CCF
            FeatureVector ccffv = new FeatureVector(null);    // Collection CCF
            
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
            	nidffv.addTerm(term, idf);
                
            	if (sum(tsw) > 0) {
    	        	try {        		
    	        		
    	        		double acf2 = rutil.acf(tsw, 2);
    	        		qacffv.addTerm(term, acf2);  
    	        		qacfnfv.addTerm(term, acf2);
    	        		qacfsfv.addTerm(term, acf2);
    	        		    	        		
		        		cacf2fv.addTerm(term, rutil.acf(ctsw, 2));    	        		
    	        		cacfs2fv.addTerm(term, rutil.acf(ctsw, 2));    	        		    	           	        		
		        		scacfsfv.addTerm(term, rutil.sma_acf(ctsw, 2, 3));
		        		
		        		qccffv.addTerm(term, 1-rutil.ccf(background, tsw, 0));
		        		ccffv.addTerm(term, 1-rutil.ccf(cbackground, ctsw, 0));
    	        	} catch (Exception e) {
    	        		e.printStackTrace();        		
    	        	}
            	}
            	
            	cfv.addTerm(term,index.termFreq(term)/index.termCount() );
            }  
            rmfv.normalize();
            nidffv.normalize();
            qacfnfv.normalize();
            scale(qacfsfv);
            scale(cacfs2fv);
            scale(scacfsfv);
            scale(qccffv);
            scale(ccffv);
            
            for (String term: query.getFeatureVector().getFeatures()) {

            	double rm = rmfv.getFeatureWeight(term);
            	double idf = nidffv.getFeatureWeight(term);
            	double qacf2 = qacffv.getFeatureWeight(term);
            	double qacfn2 = qacfnfv.getFeatureWeight(term);
            	double qacfs2 = qacfsfv.getFeatureWeight(term);

        		double cacf2 = cacf2fv.getFeatureWeight(term);
            	double cacf2s = cacfs2fv.getFeatureWeight(term);
            	double scacfs = scacfsfv.getFeatureWeight(term);
            	
        		double ccfq = qccffv.getFeatureWeight(term);	        
        		double ccfc = ccffv.getFeatureWeight(term);	        
            	
                System.out.println(query.getTitle() + "," + term 
                	+ "," + rm + "," + idf + "," + qacf2 + "," + qacfn2 
                	+ "," + qacfs2 + ","+ cacf2 + "," + cacf2s + "," + scacfs + "," + ccfq + "," + ccfc);
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
        
        options.addOption("plot", true, "Plot");
        return options;
    }

}
