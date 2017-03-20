package edu.gslis.main;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.tika.io.IOUtils;

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
public class GetFeaturesRM 
{
	static int MAX_RESULTS=1000;
	
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( GetFeaturesRM.class.getCanonicalName(), options );
            return;
        }
        String indexPath = cl.getOptionValue("index");
        String tsIndexPath = cl.getOptionValue("tsindex");
        String topicsPath = cl.getOptionValue("topics");
        String termPath = cl.getOptionValue("terms");
        Map<String, Set<String>> termMap = readTerms(termPath);

        long startTime = Long.parseLong(cl.getOptionValue("startTime"));
        long endTime  = Long.parseLong(cl.getOptionValue("endTime"));
        long interval = Long.parseLong(cl.getOptionValue("interval"));   
        int numFbDocs =Integer.parseInt(cl.getOptionValue("numFbDocs", "50"));
        int numFbTerms =Integer.parseInt(cl.getOptionValue("numFbTerms", "20"));
        boolean smooth = cl.hasOption("smooth");


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
        System.out.println("query,term,rmn,dpn,dpsn,tkln,tklin,tklcn,pwr,pwc,nidf,qacf,acf,acfn,acfs,burst,cafcs,dpc,dpsc,scacf");
        while (queryIt.hasNext()) {
            GQuery query = queryIt.next();
  
            SearchHits results = index.runQuery(query, MAX_RESULTS);
                      
            FeatureVector rmfv = getRMFV(results, numFbDocs, numFbTerms, index, termMap.get(query.getTitle()));
            
            Set<String> features = new HashSet<String>();
            features.addAll(rmfv.getFeatures());
            for (String feature: features) {
				if (feature.matches("^[0-9]*$")) {
					rmfv.removeTerm(feature);
				}
            }
            
            rmfv.clip(numFbTerms);
            rmfv.normalize();
            
            TermTimeSeries ts = new TermTimeSeries(startTime, endTime, interval, 
            		rmfv.getFeatures());
            
            FeatureVector dfv = new FeatureVector(null);
            for (SearchHit result: results.hits()) {
        		long docTime = TemporalScorer.getTime(result);
        		double score = result.getScore();
        		ts.addDocument(docTime, score, result.getFeatureVector());
        		
        		for (String term: rmfv.getFeatures()) {
        			dfv.addTerm(term, result.getFeatureVector().getFeatureWeight(term));
        		}
            }
            dfv.normalize();
                        
            if (smooth)
            	ts.smooth();
            
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
            
            FeatureVector cfv = new FeatureVector(null);
            for (String term: rmfv.getFeatures()) {
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
    	        		
    	        		
    	        		double cacf = rutil.acf(ctsw, 2);
    	        		
    	        		scacfs.addTerm(term, rutil.sma_acf(ctsw, 2, 3));
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
                        
            for (String term: rmfv.getFeatures()) {
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
            	
                System.out.println(query.getTitle() + "," + term 
                    	+ "," + rm + "," + dpn + "," + dpsn 
                    	+ "," + tkl + "," + tklin + "," +  tklcn
                    	+ "," + pwr + "," + pwc + "," + idf + "," + qacf + "," + acf1 + "," + acf2 
                    	+ "," + acf3 + "," + burst + "," + cacf + "," + dpc + "," + dpsc + "," + scacf );
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
        if (terms != null) {
		    FeatureVector qfv = new FeatureVector(null);
		    for (String term: terms) {
		    	qfv.setTerm(term,  rfv.getFeatureWeight(term));
		    }
		    qfv.normalize();
		    return qfv;
        }
        else {
        	return rfv;
        }
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

    public static Map<String, Set<String>> readTerms(String termsPath) throws IOException {
    	Map<String, Set<String>> termMap = new HashMap<String, Set<String>>();
    	List<String> lines = IOUtils.readLines(new FileInputStream(termsPath));
    	for (String line: lines) {
    		String[] fields = line.split(",");
    		String query = fields[0];
    		String term = fields[1];
    		
    		Set<String> terms = termMap.get(query);
    		if (terms == null) {
    			terms = new TreeSet<String>();
    			terms.add(term);    			
    		} else {
    			terms.add(term);
    		}
    		termMap.put(query, terms);    		
    	}
    	
    	return termMap;
    	
    }

    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "Path to input index");
        options.addOption("tsindex", true, "Path to collection time series index");
        options.addOption("smooth", false, "Smooth the timeseries");
        options.addOption("topics", true, "Path to topics file");
        options.addOption("numFbDocs", true, "Number of feedback documents");
        options.addOption("numFbTerms", true, "Number of feedback terms");
        options.addOption("startTime", true, "Collection start time");
        options.addOption("endTime", true, "Collection end time");
        options.addOption("interval", true, "Collection interval");        
        options.addOption("terms", true, "Term map");
        return options;
    }

}
