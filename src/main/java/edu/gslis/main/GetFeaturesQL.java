package edu.gslis.main;

import java.util.Iterator;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
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
        String topicsPath = cl.getOptionValue("topics");	
        
        long startTime = Long.parseLong(cl.getOptionValue("startTime"));
        long endTime  = Long.parseLong(cl.getOptionValue("endTime"));
        long interval = Long.parseLong(cl.getOptionValue("interval"));  
        boolean smooth = cl.hasOption("smooth");

        GQueries queries = null;
        if (topicsPath.endsWith("indri")) 
            queries = new GQueriesIndriImpl();
        else
            queries = new GQueriesJsonImpl();
        queries.read(topicsPath);
        
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
        index.setTimeFieldName(Indexer.FIELD_EPOCH);
        
        RUtil rutil = new RUtil();
        Iterator<GQuery> queryIt = queries.iterator();
        System.out.println(
        			"query,term,rm,rmn,dp,dpn,dps,dpsn," + 
        			"tkl,tkln,tkli,tklin,tklc,tklcn," + 
        		    "pwr,pwc,idf,nidf,acf");

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
                        
            FeatureVector rmfv = getRMFV(results, 50, 20, index, query.getFeatureVector().getFeatures());
            
            double[] background = ts.getBinTotals();

            double srm = 0;
            double sdps = 0;
            double sdp = 0;
            double sidf = 0;
            double stkl = 0;
            double stkli = 0;
            double stklc = 0;   

            double[] ndp= new double[(int)query.getFeatureVector().getLength()];
            double[] ndps= new double[(int)query.getFeatureVector().getLength()];
            double[] ntkl= new double[(int)query.getFeatureVector().getLength()];
            double[] ntkli= new double[(int)query.getFeatureVector().getLength()];
            double[] ntklc= new double[(int)query.getFeatureVector().getLength()];
            double[] nidf= new double[(int)query.getFeatureVector().getLength()];
            
            FeatureVector cfv = new FeatureVector(null);
            int j=0;
            for (String term: query.getFeatureVector().getFeatures()) {
            	double[] tsw = ts.getTermFrequencies(term);

            	srm += rmfv.getFeatureWeight(term);
            	
            	double sum = sum(tsw);
            	if (sum > 0) {

                    for (int i=0; i<tsw.length; i++) {
                    	tsw[i] = (tsw[i]/sum);
                    }

                    
                    double dps = rutil.dps(tsw);
                    ndps[j] = dps;
                	sdps += dps;
                	
                    double dp = rutil.dp(tsw);
                    ndp[j] = dp;
                	sdp += dp;
                	                    
                	double tkl = 0;
                    for (int i=0; i<tsw.length; i++) {
                    	if (tsw[i] >0 && background[i] > 0)
                    		tkl += tsw[i] * Math.log(tsw[i]/background[i]);
                    }
                    
                    ntkl[j] = tkl;
                    stkl += tkl;
                    
                    double tkli =  (Math.exp(-(1/tkl)));
                    stkli += tkli;
                    ntkli[j] = tkli;
                    
                    double tklc =  1 - (Math.exp(-(tkl))); 
                    stklc += tklc;
                    ntklc[j] = tklc;
                    
                    //System.out.println(term + ",tkl=" + tkl + ",tkli=" + tkli + ",tlkc=" + tklc);
                   
            	}
            	double idf = Math.log(1 + index.docCount()/index.docFreq(term));
            	sidf += idf;
            	nidf[j] = idf;
            	cfv.addTerm(term,index.termFreq(term)/index.termCount() );
            	j++;
            }  
                        
            //System.out.println("stkl=" + stkl + ",stkli=" + stkli + ",stklc=" + stklc);
            for (String term: query.getFeatureVector().getFeatures()) {
            	double[] tsw = ts.getTermFrequencies(term);

                double sum = sum(tsw);
                                
            	double rm = rmfv.getFeatureWeight(term);
            	double dps = 0;
            	double dp =0;
            	double tkl = 0;
            	double tkli = 0;
            	double tklc = 0;
             	double acf = 0;
            	if (sum > 0) {
            		acf = rutil.acf(tsw);
                	dps = rutil.dps(tsw);
                	dp = rutil.dp(tsw);  
                    for (int i=0; i<tsw.length; i++) {
                    	tsw[i] = (tsw[i]/sum);                    	
                    }
                    
                    for (int i=0; i<tsw.length; i++) {
                    	if (tsw[i] >0 && background[i] > 0)
                    		tkl += tsw[i] * Math.log(tsw[i]/background[i]);
                    }
                    
                	tkli += (Math.exp(-(1/tkl))); 
                	tklc += 1 - (Math.exp(-(tkl)));                     
            	}
            	
            	double pwc = cfv.getFeatureWeight(term);
            	double idf = Math.log(1 + index.docCount()/index.docFreq(term));
            	double pwr = dfv.getFeatureWeight(term);
            	
                System.out.println(query.getTitle() + "," + term 
                	+ "," + rm + "," + rm/srm + "," + dp + "," + dp/sdp + "," + dps + "," + dps/sdps 
                	+ "," + tkl + "," + tkl/stkl + "," + tkli + "," + tkli/stkli + "," + tklc + "," + tklc/stklc
                	+ "," + pwr + "," + pwc + "," + idf + "," + idf/sidf 
                	+ "," + acf);
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
    
    


    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "Path to input index");
        options.addOption("topics", true, "Path to topics file");
        options.addOption("startTime", true, "Collection start time");
        options.addOption("endTime", true, "Collection end time");
        options.addOption("interval", true, "Collection interval");       
        options.addOption("smooth", false, "Smooth the timeseries");
        return options;
    }

}
