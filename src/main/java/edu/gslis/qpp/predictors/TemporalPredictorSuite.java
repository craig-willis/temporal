package edu.gslis.qpp.predictors;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.temporal.TimeSeriesIndex;
import edu.gslis.main.temporal.TermTimeSeries;
import edu.gslis.queries.GQuery;
import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.scorers.temporal.TemporalScorer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;

public class TemporalPredictorSuite  extends PredictorSuite {

	TimeSeriesIndex tsindex = null;
	long startTime = 0;
	long endTime = 0;
	long interval = 0;
	int fbDocs = 50;
	int numResults = 1000;
	String plotPath = null;
	String tsPath = null;
	boolean smooth = false;

	Set<String> fields = new TreeSet<String>();

	public void setTimeSeriesIndex(TimeSeriesIndex tsindex) {
		this.tsindex = tsindex;		
	}

	public void setNumResults(int numResults) {
		this.numResults = numResults;
	}

	public void setConstraints(long startTime, long endTime, long interval) {
		this.startTime = startTime;
		this.endTime = endTime;
		this.interval = interval;
	}

	public void setFbDocs(int fbDocs) {
		this.fbDocs = fbDocs;
	}

	public void setPlotPath(String plotPath) {
		this.plotPath = plotPath;
	}

	public void setTsPath(String tsPath) {
		this.tsPath = tsPath;
	}
	
	public void setSmooth(boolean smooth) {
		this.smooth = smooth;
	}
	
	public Set<String> getFields() {
		return fields;
	}

	public Map<String, Double> calculatePredictors() {
		
		Map<String, Double> values = new HashMap<String, Double>();

		TermTimeSeries ts = new TermTimeSeries(startTime, endTime, interval, query.getFeatureVector().getFeatures());

		RUtil rutil = new RUtil();
		
		// Run the initial query
		SearchHits results = index.runQuery(query, numResults);

		// Create the feedback temporal distribution
		FeatureVector dfv = new FeatureVector(null);
		for (SearchHit result : results.hits()) {
			long docTime = TemporalScorer.getTime(result);
			double score = result.getScore();
			ts.addDocument(docTime, score, result.getFeatureVector());

			for (String term : query.getFeatureVector().getFeatures()) {
				dfv.addTerm(term, result.getFeatureVector().getFeatureWeight(term));
			}
		}
		dfv.normalize();

		// TODO: Explore more smoothing options
		if (smooth)
			ts.smooth();

		// Feedback temporal distribution 
		double[] qbackground = ts.getBinDist();
		
		// Background temporal distribution for collection
		double[] cbackground = tsindex.get("_total_");

		
		// Calculate feedback temporal distribution predictors
		try {
			// Auto-correlation of feedback temporal distribution
			double fbacf2 = 1+rutil.acf(qbackground, 2);
	        values.put("fbACF", fbacf2);
			
			// Cross-correlation of fb distribution with collection
			double fbcccf = 1+rutil.ccf(cbackground, qbackground, 0);
	        values.put("fbCCF", fbcccf);
			
			// Dominant period of fb distribution
			double fbdp = rutil.dp(qbackground);
	        values.put("fbDP", fbdp);

			// Dominant power spectrum of fb distribution
			double fbdps = rutil.dps(qbackground);
	        values.put("fbDPS", fbdps);
	        
	        // Feedback temporal KL from collection
			double fbtkl = 0;
			for (int i = 0; i < qbackground.length; i++) {
				if (qbackground[i] > 0 && qbackground[i] > 0)
					fbtkl += qbackground[i] * Math.log(qbackground[i] / cbackground[i]);
			}
			values.put("fbTKL", fbtkl);

			double fbtklc =  1 - (Math.exp(-(fbtkl)));
			values.put("fbTKLC", fbtklc);
			
			double fbtkli = (Math.exp(-(1 / fbtkl)));
			values.put("fbTKLI", fbtkli);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
		// Calculate term temporal distribution statistics
		DescriptiveStatistics qacfstats = new DescriptiveStatistics();
		DescriptiveStatistics cacfstats = new DescriptiveStatistics();
		DescriptiveStatistics qccfstats = new DescriptiveStatistics();
		DescriptiveStatistics cccfstats = new DescriptiveStatistics();
		DescriptiveStatistics qdpstats = new DescriptiveStatistics();
		DescriptiveStatistics cdpstats = new DescriptiveStatistics();
		DescriptiveStatistics qdpsstats = new DescriptiveStatistics();
		DescriptiveStatistics cdpsstats = new DescriptiveStatistics();	
		DescriptiveStatistics tklistats = new DescriptiveStatistics();
		DescriptiveStatistics tklcstats = new DescriptiveStatistics();
		DescriptiveStatistics tklstats = new DescriptiveStatistics();
		
		for (String term : query.getFeatureVector().getFeatures()) 
		{
			double[] qtsw = ts.getTermDist(term);
			if (qtsw == null) {
				System.err.println("Unexpected null query termts for " + term);
				continue;
			}

			double[] ctsw = tsindex.get(term);
			if (ctsw == null) {
				System.err.println("Unexpected null collection termts for " + term);
				continue;
			}

			if (sum(qtsw) > 0) {
				try {

					double qacf2 = 1+rutil.acf(qtsw, 2);
					qacfstats.addValue(qacf2);

					double cacf2 = 1+rutil.acf(ctsw, 2);
					cacfstats.addValue(cacf2);

					double qccf =  1+rutil.ccf(qbackground, qtsw, 0);
					qccfstats.addValue(qccf);

					double cccf = 1+rutil.ccf(cbackground, ctsw, 0);
					cccfstats.addValue(cccf);

					double qdp = rutil.dp(qtsw);
					qdpstats.addValue(qdp);

					double qdps = rutil.dps(qtsw);
					qdpsstats.addValue(qdps);
					
					double cdp = rutil.dp(ctsw);
					cdpstats.addValue(cdp);
					
					double cdps = rutil.dps(ctsw);
					cdpsstats.addValue(cdps);

				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			double tkl = 0;
			for (int i = 0; i < qtsw.length; i++) {
				if (qtsw[i] > 0 && qbackground[i] > 0)
					tkl += qtsw[i] * Math.log(qtsw[i] / qbackground[i]);
			}

			double tklc =  1 - (Math.exp(-(tkl)));
			tklcstats.addValue(tklc);
			
			double tkli = (Math.exp(-(1 / tkl)));
			tklistats.addValue(tkli);

			tklstats.addValue(tkl);

		}
		
        values.put("varQACF", qacfstats.getVariance());
        values.put("avgQACF", qacfstats.getMean());
        values.put("minQACF", qacfstats.getMin());
        values.put("maxQACF", qacfstats.getMax());

        values.put("varCACF", cacfstats.getVariance());
        values.put("avgCACF", cacfstats.getMean());
        values.put("minCACF", cacfstats.getMin());
        values.put("maxCACF", cacfstats.getMax());

        values.put("varQCCF", qccfstats.getVariance());
        values.put("avgQCCF", qccfstats.getMean());
        values.put("minQCCF", qccfstats.getMin());
        values.put("maxQCCF", qccfstats.getMax());

        values.put("varCCCF", cccfstats.getVariance());
        values.put("avgCCCF", cccfstats.getMean());
        values.put("minCCCF", cccfstats.getMin());
        values.put("maxCCCF", cccfstats.getMax());
        
        values.put("varQDP", qdpstats.getVariance());
        values.put("avgQDP", qdpstats.getMean());
        values.put("minQDP", qdpstats.getMin());
        values.put("maxQDP", qdpstats.getMax());
        
        values.put("varQDPS", qdpsstats.getVariance());
        values.put("avgQDPS", qdpsstats.getMean());
        values.put("minQDPS", qdpsstats.getMin());
        values.put("maxQDPS", qdpsstats.getMax());

        values.put("varCDP", cdpstats.getVariance());
        values.put("avgCDP", cdpstats.getMean());
        values.put("minCDP", cdpstats.getMin());
        values.put("maxCDP", cdpstats.getMax());
        
        values.put("varCDPS", cdpsstats.getVariance());
        values.put("avgCDPS", cdpsstats.getMean());
        values.put("minCDPS", cdpsstats.getMin());
        values.put("maxCDPS", cdpsstats.getMax());

        values.put("varTKLI", tklistats.getVariance());
        values.put("avgTKLI", tklistats.getMean());
        values.put("minTKLI", tklistats.getMin());
        values.put("maxTKLI", tklistats.getMax());

        values.put("varTKLC", tklcstats.getVariance());
        values.put("avgTKLC", tklcstats.getMean());
        values.put("minTKLC", tklcstats.getMin());
        values.put("maxTKLC", tklcstats.getMax());
        
        values.put("varTKL", tklstats.getVariance());
        values.put("avgTKL", tklstats.getMean());
        values.put("minTKL", tklstats.getMin());
        values.put("maxTKL", tklstats.getMax());

		return values;
	}
	
	// Per term features
	public Map<String, Map<String, Double>> getFeatures(GQuery query, boolean smooth) {
	
		Map<String, Map<String, Double>> queryPredictors = new TreeMap<String, Map<String, Double>>();

		// Need to convert from featurevector to DescripriveStatistics?
		RUtil rutil = new RUtil();

		double[] cbackground = tsindex.get("_total_");

		FeatureVector nidffv = new FeatureVector(null); // Normalized IDF
		FeatureVector qacffv = new FeatureVector(null); // Raw ACF, lag 2
		FeatureVector qacfsfv = new FeatureVector(null); // Scaled ACF, lag 2
		FeatureVector cacf2fv = new FeatureVector(null); // Collection ACF, lag 2
		FeatureVector cacfs2fv = new FeatureVector(null); // Collection ACF, scaled, lag2
		FeatureVector qccffv = new FeatureVector(null); // Query CCF
		FeatureVector ccffv = new FeatureVector(null); // Collection CCF
		FeatureVector rmqacfn = new FeatureVector(null); // RM*QACF
		FeatureVector rmcacfn = new FeatureVector(null); // RM*CACF
		FeatureVector tklfv = new FeatureVector(null);
		//FeatureVector tklcfv = new FeatureVector(null);
		//FeatureVector tklifv = new FeatureVector(null);
		FeatureVector dpfv = new FeatureVector(null);
		FeatureVector dpsfv = new FeatureVector(null);
		FeatureVector cdpfv = new FeatureVector(null);
		FeatureVector cdpsfv = new FeatureVector(null);
		//FeatureVector kurtosisfv = new FeatureVector(null);
		FeatureVector dpnfv = new FeatureVector(null);
		FeatureVector dpsnfv = new FeatureVector(null);
		FeatureVector cdpnfv = new FeatureVector(null);
		FeatureVector cdpsnfv = new FeatureVector(null);
		//FeatureVector kurtosisnfv = new FeatureVector(null);

		TermTimeSeries ts = new TermTimeSeries(startTime, endTime, interval, query.getFeatureVector().getFeatures());

		SearchHits results = index.runQuery(query, numResults);

		FeatureVector dfv = new FeatureVector(null);
		for (SearchHit result : results.hits()) {
			long docTime = TemporalScorer.getTime(result);
			double score = result.getScore();
			ts.addDocument(docTime, score, result.getFeatureVector());

			for (String term : query.getFeatureVector().getFeatures()) {
				dfv.addTerm(term, result.getFeatureVector().getFeatureWeight(term));
			}
		}
		dfv.normalize();

		if (smooth)
			ts.smooth();

		double[] background = ts.getBinDist();

		if (tsPath != null) {
			try {
				ts.save(tsPath + "/" + query.getTitle() + ".ts");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		FeatureVector rmfv = getRMFV(results, fbDocs, 100, index, query.getFeatureVector().getFeatures());
		FeatureVector rmnfv = rmfv.deepCopy();

		FeatureVector cfv = new FeatureVector(null);
		for (String term : query.getFeatureVector().getFeatures()) {
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

			double idf = Math.log(1 + index.docCount() / index.docFreq(term));
			nidffv.addTerm(term, idf);

			if (sum(tsw) > 0) {
				try {

					double acf2 = rutil.acf(tsw, 2);
					qacffv.addTerm(term, acf2);
					qacfsfv.addTerm(term, acf2);

					double cacf2 = rutil.acf(ctsw, 2);
					cacf2fv.addTerm(term, cacf2);
					cacfs2fv.addTerm(term, cacf2);

					qccffv.addTerm(term, rutil.ccf(background, tsw, 0));
					ccffv.addTerm(term, rutil.ccf(cbackground, ctsw, 0));

					double dp = rutil.dp(tsw);
					dpfv.addTerm(term, dp);
					dpnfv.addTerm(term, dp);

					double dps = rutil.dps(tsw);
					dpsfv.addTerm(term, dps);
					dpsnfv.addTerm(term, dps);

					double cdp = rutil.dp(background);
					cdpfv.addTerm(term, cdp);
					cdpnfv.addTerm(term, cdp);

					double cdps = rutil.dps(background);
					cdpsfv.addTerm(term, cdps);
					cdpsnfv.addTerm(term, cdps);

//					double kurtosis = rutil.kurtosis(tsw);
//					kurtosisfv.addTerm(term, kurtosis);
//					kurtosisnfv.addTerm(term, kurtosis);

				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			double tkl = 0;
			for (int i = 0; i < tsw.length; i++) {
				if (tsw[i] > 0 && background[i] > 0)
					tkl += tsw[i] * Math.log(tsw[i] / background[i]);
			}

			tklfv.addTerm(term, tkl);
			//tklcfv.addTerm(term, 1 - (Math.exp(-(tkl))));
			//tklifv.addTerm(term, (Math.exp(-(1 / tkl))));

			cfv.addTerm(term, index.termFreq(term) / index.termCount());
		}
		FeatureVector idffv = nidffv.deepCopy();
		FeatureVector qccffvs = qccffv.deepCopy();
		FeatureVector ccffvs = ccffv.deepCopy();
		//FeatureVector tklcnfv = tklcfv.deepCopy();
		//FeatureVector tklinfv = tklifv.deepCopy();
		FeatureVector tklnfv = tklfv.deepCopy();

		rmnfv.normalize();
		nidffv.normalize();
		scale(qacfsfv);
		scale(cacfs2fv);
		scale(qccffvs);
		scale(ccffvs);
		//tklcnfv.normalize();
		//tklinfv.normalize();
		tklnfv.normalize();

		dpsnfv.normalize();
		cdpsnfv.normalize();
		//kurtosisnfv.normalize();

		for (String term : query.getFeatureVector().getFeatures()) {
			double rm = rmfv.getFeatureWeight(term);
			rmqacfn.addTerm(term, rm * qacfsfv.getFeatureWeight(term));
			rmcacfn.addTerm(term, rm * cacfs2fv.getFeatureWeight(term));
		}
		rmqacfn.normalize();
		rmcacfn.normalize();

		for (String term : query.getFeatureVector().getFeatures()) {

			double rm = rmfv.getFeatureWeight(term);
			double rmn = rmnfv.getFeatureWeight(term);
			double idf = idffv.getFeatureWeight(term);
			double nidf = nidffv.getFeatureWeight(term);
			double qacf2 = qacffv.getFeatureWeight(term);
			double qacfs2 = qacfsfv.getFeatureWeight(term);

			double cacf2 = cacf2fv.getFeatureWeight(term);
			double cacf2s = cacfs2fv.getFeatureWeight(term);

			double ccfq = qccffv.getFeatureWeight(term);
			double ccfc = ccffv.getFeatureWeight(term);
			double ccfqs = qccffvs.getFeatureWeight(term);
			double ccfcs = ccffvs.getFeatureWeight(term);
			double rmqacf = rmqacfn.getFeatureWeight(term);
			double rmcacf = rmcacfn.getFeatureWeight(term);

			//double tklc = tklcfv.getFeatureWeight(term);
			//double tklcn = tklcnfv.getFeatureWeight(term);
			//double tkli = tklifv.getFeatureWeight(term);
			//double tklin = tklifv.getFeatureWeight(term);
			double tkln = tklnfv.getFeatureWeight(term);
			double tkl = tklfv.getFeatureWeight(term);

			double dp = dpfv.getFeatureWeight(term);
			double dpn = dpnfv.getFeatureWeight(term);
			double dps = dpsfv.getFeatureWeight(term);
			double dpsn = dpsnfv.getFeatureWeight(term);
			//double kurtosis = kurtosisfv.getFeatureWeight(term);
			//double kurtosisn = kurtosisnfv.getFeatureWeight(term);

			Map<String, Double> predictors = new HashMap<String, Double>();
			predictors.put("rm", rm);
			predictors.put("rmn", rmn);
			predictors.put("idf", idf);
			predictors.put("nidf", nidf);
			predictors.put("qacf2", qacf2);
			predictors.put("qacfs2", qacfs2);
			predictors.put("cacf2", cacf2);
			predictors.put("cacf2s", cacf2s);
			predictors.put("ccfq", ccfq);
			predictors.put("ccfqs", ccfqs);
			predictors.put("ccfc", ccfc);
			predictors.put("ccfcs", ccfcs);
			predictors.put("rmqacf", rmqacf);
			predictors.put("rmcacf", rmcacf);
			//predictors.put("tklc", tklc);
			//predictors.put("tklcn", tklcn);
			//predictors.put("tkli", tkli);
			//predictors.put("tklin", tklin);
			predictors.put("tkl", tkl);
			predictors.put("tkln", tkln);
			predictors.put("dps", dps);
			predictors.put("dpsn", dpsn);
			predictors.put("dp", dp);
			predictors.put("dpn", dpn);
			//predictors.put("kurtosis", kurtosis);
			//predictors.put("kurtosisn", kurtosisn);
			queryPredictors.put(term, predictors);

			fields.addAll(predictors.keySet());
		}
		return queryPredictors;
	}

	public static double sum(double[] d) {
		double sum = 0;
		if (d == null)
			return 0;
		for (double x : d)
			sum += x;
		return sum;
	}

	public static double normalize(double x, double[] d) {

		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		for (double v : d) {
			if (v < min)
				min = v;
			if (v > max)
				max = v;
		}
		System.out.println("x=" + x + ",min=" + min + ",max=" + max);
		return (x - min) / (max - min);
	}

	public static void scale(FeatureVector fv, double min, double max) {
		for (String term : fv.getFeatures()) {
			double x = fv.getFeatureWeight(term);
			double z = (x - min) / (max - min);
			fv.setTerm(term, z);
		}
		fv.normalize();
	}

	public static void scale(FeatureVector fv) {
		for (String term : fv.getFeatures()) {
			double x = fv.getFeatureWeight(term);
			double z = x + 1;
			fv.setTerm(term, z);
		}
		normalize(fv);
		// fv.normalize();
	}

	public static void normalize(FeatureVector fv) {
		double min = Double.POSITIVE_INFINITY;
		for (String term : fv.getFeatures()) {
			double x = fv.getFeatureWeight(term);
			if (x < min)
				min = x;
		}

		if (min < 0) {
			for (String term : fv.getFeatures()) {
				double x = fv.getFeatureWeight(term);
				double z = Math.abs(min) + x;
				fv.setTerm(term, z);
			}
		}
		fv.normalize();
	}

	public static void normalize(FeatureVector fv, double min, double max) {
		for (String term : fv.getFeatures()) {
			double x = fv.getFeatureWeight(term);
			double z = (x - min) / (max - min);
			fv.setTerm(term, z);
		}
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
		for (String term : terms) {
			qfv.setTerm(term, rfv.getFeatureWeight(term));
		}

		// qfv.normalize();
		return qfv;
	}
}
