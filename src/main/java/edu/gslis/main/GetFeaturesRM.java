package edu.gslis.main;

import java.util.HashSet;
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
        String topicsPath = cl.getOptionValue("topics");	
        
        long startTime = Long.parseLong(cl.getOptionValue("startTime"));
        long endTime  = Long.parseLong(cl.getOptionValue("endTime"));
        long interval = Long.parseLong(cl.getOptionValue("interval"));   
        int numFbDocs =Integer.parseInt(cl.getOptionValue("numFbDocs", "50"));
        int numFbTerms =Integer.parseInt(cl.getOptionValue("numFbTerms", "20"));


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
        System.out.println("query,term,rm,acf,dps,dpsn,dp,dpn,tkl,tkln,bp,idf,nidf,pwc,pwr,aspec,burstd,tkli,tklin,tklc,tklcn");
        while (queryIt.hasNext()) {
            GQuery query = queryIt.next();
  
            SearchHits results = index.runQuery(query, MAX_RESULTS);
                      
            FeatureVector rmfv = getRMFV(results, numFbDocs, numFbTerms, index, null);
            
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
                        

            
//            System.err.println(rmfv.toString(10));
            double[] background = ts.getBinTotals();
            double sum = sum(background);
            for (int i=0; i<background.length; i++) 
            	background[i] = (background[i]/sum)+0.0000000001;

            double sdps =0;
            double sdp =0;
            double sacf = 0;
            double sidf = 0;
            double specs = 0;
            double durations = 0;
            double stkl = 0;
            double stkli = 0;
            double stklc = 0;   
            
            FeatureVector cfv = new FeatureVector(null);
            for (String term: rmfv.getFeatures()) {
            	double[] tsw = ts.getTermFrequencies(term);

            	sum = sum(tsw);
            	if (sum > 0) {
                	sacf += rutil.acf(tsw);
                	sdps += rutil.dps(tsw);
                	sdp += rutil.dp(tsw);  
                	specs += rutil.avgSpec(tsw);
                	durations += rutil.bursts(tsw);
                	
                    for (int i=0; i<tsw.length; i++) {
                    	tsw[i] = (tsw[i]/sum)+0.0000000001;
                    }

                    for (int i=0; i<tsw.length; i++) {
                    	stkl += tsw[i] * Math.log(tsw[i]/background[i]);
                    }
                    
                	stkli += (Math.exp(-(1/stkl))); 
                	stklc += 1 - (Math.exp(-(stkl))); 
            	}
            	sidf += Math.log(1 + index.docCount()/index.docFreq(term));
            	cfv.addTerm(term,index.termFreq(term)/index.termCount() );
            }  
            //cfv.normalize();
                        
            for (String term: rmfv.getFeatures()) {
            	double[] tsw = ts.getTermFrequencies(term);
                sum = sum(tsw);
                                
            	double rmweight = rmfv.getFeatureWeight(term);
            	double acf = 0;
            	double dps = 0;
            	double dp =0;
            	double tkl = 0;
            	double binProp = 0;
            	double aspec = 0;
            	double duration = 0;
            	if (sum > 0) {
                	acf = rutil.acf(tsw);
                	dps = rutil.dps(tsw);
                	dp = rutil.dp(tsw);  
                	duration = rutil.bursts(tsw)/durations;
                	aspec = rutil.avgSpec(tsw)/specs;
                    for (int i=0; i<tsw.length; i++) {
                    	if (tsw[i] > 0) {
                    		binProp ++;
                    	}
                    	tsw[i] = (tsw[i]/sum)+0.0000000001;
                    	
                    }
                                        
                    binProp /= tsw.length;

                    for (int i=0; i<tsw.length; i++) {
                    	tkl += tsw[i] * Math.log(tsw[i]/background[i]);
                    }
                    
            	}

            	double tkli = (Math.exp(-(1/tkl))); 
            	double tklc = 1 - (Math.exp(-(tkl))); 
            	
            	double pwc = cfv.getFeatureWeight(term);
            	double idf = Math.log(1 + index.docCount()/index.docFreq(term));
            	double pwr = dfv.getFeatureWeight(term);
                //query,term
            	//  rm,acf,dps,dpsn
            	//  dp,dpn,
                //	tkl,tkln,
            	//  bp,idf,nidf,pwc,pwr,aspec,burstd, 
                //	tkli,tklin,tklc,tklcn

                
                System.out.println(query.getTitle() + "," + term 
                	+ "," + rmweight + "," + acf + "," + dps + "," + dps/sdps 
                	+ "," + dp + "," + dp/sdp + "," + tkl + "," + tkl/stkl 
                	+ "," + binProp + "," + idf + "," + idf/sidf + "," + pwc + "," + pwr 
                	+ "," + aspec + "," + duration + "," + tkli + "," + tkli/stkli
                	+ "," + tklc + "," + tklc/stklc);            	
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


    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "Path to input index");
        options.addOption("topics", true, "Path to topics file");
        options.addOption("numFbDocs", true, "Number of feedback documents");
        options.addOption("numFbTerms", true, "Number of feedback terms");
        options.addOption("startTime", true, "Collection start time");
        options.addOption("endTime", true, "Collection end time");
        options.addOption("interval", true, "Collection interval");        
        return options;
    }

}
