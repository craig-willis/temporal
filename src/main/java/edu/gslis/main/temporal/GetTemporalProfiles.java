package edu.gslis.main.temporal;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.tika.io.IOUtils;
import org.rosuda.REngine.Rserve.RConnection;

import edu.gslis.eval.Qrels;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.scorers.temporal.TemporalScorer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RUtil;

/**
 * Implementation of Jones and Diaz 2007 temporal profile -- p(t|Q) -- analysis.
 * Given an index and set of topics, calculates the first-order auto-correlation
 * and temporal KL-divergence of the smoothed query temporal profile.
 */
public class GetTemporalProfiles {

	static Double LAMBDA = 0.9;
	static int NUM_RESULTS = 1000;
	
	static double minTime = 0;
	static double maxTime = 0;

	public static void main(String[] args) throws Exception {
		Options options = createOptions();
		CommandLineParser parser = new GnuParser();
		CommandLine cl = parser.parse(options, args);
		if (cl.hasOption("help")) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(GetTemporalProfiles.class.getCanonicalName(), options);
			return;
		}
		String indexPath = cl.getOptionValue("index");
		String topicsPath = cl.getOptionValue("topics");
		String colProfilePath = cl.getOptionValue("colProfile");
		String qrelsPath = cl.getOptionValue("qrels");
		String outputPath = cl.getOptionValue("output");
		String intervalStr = cl.getOptionValue("interval");
		int interval = 86400;
		if (intervalStr != null) {
			interval = Integer.parseInt(intervalStr);			
		}
		
		boolean plot = cl.hasOption("plot");
		String plotPath = "";
		if (plot)
			plotPath = cl.getOptionValue("plotPath");
		FileWriter output = new FileWriter(outputPath);

		Qrels qrels =new Qrels(qrelsPath, false, 1);
		
		
		GQueries queries = null;
		if (topicsPath.endsWith("indri"))
			queries = new GQueriesIndriImpl();
		else
			queries = new GQueriesJsonImpl();
		queries.read(topicsPath);

		IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
		index.setTimeFieldName(Indexer.FIELD_EPOCH);

		// Read the collection temporal profile p(t|C)
		Map<Double, Double> colProfile = readCollectionProfile(colProfilePath);
		RUtil rutil = new RUtil();
		
		// For each query Q
		Iterator<GQuery> qit = queries.iterator();
		while (qit.hasNext()) {
			GQuery query = qit.next();
			SearchHits hits = index.runQuery(query, NUM_RESULTS);

			// Get the normalizing constant
			Iterator<SearchHit> it = hits.iterator();
			double normalizer = 0;
			
			// Remove hits outside of time range
			SearchHits thits = new SearchHits();
			while (it.hasNext()) {
				SearchHit hit = it.next();
				normalizer += Math.exp(hit.getScore());
				double time = (Double) hit.getMetadataValue(Indexer.FIELD_EPOCH);
				time = (int)(time/interval)*interval;
				
				if (time < minTime || time > maxTime) {
					System.out.println("Skipping docno " + hit.getDocno());
					continue;
				}
				
				thits.add(hit);
			}

			// Construct the query temporal profile p(t|Q)
			it = thits.iterator();
			Map<Double, Double> queryProfile = new TreeMap<Double, Double>();
			double[] doctimes = new double[thits.size()];
			double[] scores = new double[thits.size()];
			double[] rels = new double[thits.size()];
			int j=0;
			while (it.hasNext()) {
				SearchHit hit = it.next();
				double score = Math.exp(hit.getScore());
				double time = (Double) hit.getMetadataValue(Indexer.FIELD_EPOCH);
				time = (int)(time/interval)*interval;
				
				//if (time < minTime || time > maxTime) 
				//	continue;
				
				//time = (int)(time/86400)*86400; // Daily
				//time = (int) (time / 3600) * 3600; // Hourly

				Double value = queryProfile.get(time);
				if (value == null) {
					value = 0.0;
				}
				queryProfile.put(time, value + (score / normalizer));
				
				doctimes[j] = time;
				scores[j] = score;
				rels[j] = qrels.getRelLevel(query.getTitle(), hit.getDocno());
				j++;
			}

			// Smooth the query temporal profile with the collection profile
			// Calculate temporalKL
			double[] data = new double[colProfile.size()];
			double[] col = new double[colProfile.size()];
			double[] qpr = new double[colProfile.size()];
			double[] times = new double[colProfile.size()];
			int i = 0;
			double temporalKL = 0;
			for (double time : colProfile.keySet()) {

				double cpr = colProfile.get(time);
				double tpr = 0;
				if (queryProfile.containsKey(time))
					tpr = queryProfile.get(time);
				double smoothed = LAMBDA * tpr + (1 - LAMBDA) * cpr;

				// Calculate temporal KL
				temporalKL += smoothed * Math.log(smoothed / cpr);

				times[i] = time;
				col[i] = cpr;
				qpr[i] = tpr;
				data[i] = smoothed;
				i++;
			}

			// Smooth the resulting time series using a simple moving average
			double[] sma = rutil.sma(data, 14);

			// Get the first-order autocorrelation of the unsmoothed series
			double acf = rutil.acf(qpr);

			// Write the query, acf, and temporalKL
			output.write(query.getTitle() + "," + acf + "," + temporalKL + "\n");

			if (plot) {
				
	            // Generate relevant document rug 
	            Set<String> relDocs = qrels.getRelDocs(query.getTitle());            
	            SearchHits relDocHits = new SearchHits();
	            if (relDocs != null) {                
	                for (String relDoc: relDocs) {
	                    int docid = index.getDocId(relDoc);
	                    if (docid == -1) 
	                        continue;
	                    SearchHit hit = index.getSearchHit(relDoc, null);
	                    relDocHits.add(hit);
	                }
	            }
	            
	            double[] relDocTimes = TemporalScorer.getTimes(relDocHits, relDocHits.size());
	            try
	            {
					RConnection c = rutil.getConnection();
	
					c.voidEval("setwd(\"" + plotPath + "\")");
	
					c.assign("time", times);
					c.assign("sma", sma);
					c.assign("col", col);
					c.assign("relDocs", relDocTimes);
					c.voidEval("png(\"" + query.getTitle() + ".png" + "\")");
					c.voidEval("plot(sma ~ time, main=\"" + query.getText() + "\", type=\"l\", ylim=c(0, 0.025))");
					c.voidEval("lines(col ~ time, col=\"lightskyblue\")");
					c.voidEval("kde <- density(time, weights=sma, n=1024, bw=\"SJ\")");
					c.voidEval("lines(kde$y/sum(kde$y) ~ kde$x, type=\"l\", col=\"gray\")");
					//c.voidEval("par(new = T)");				
					//c.voidEval("plot(kde, main=NA, col=\"gray\", axes=F, xlab=NA, ylab=NA, ylim=c(0, 0.000005))");
	
					c.voidEval("rug(relDocs, col=\"red\")"); 
					c.eval("dev.off()");
	
					c.assign("time", doctimes);
					c.assign("score", scores);
					c.assign("rel", rels);
	
					//c.voidEval("png(\"" + query.getTitle() + "-scores.png" + "\")");
					c.voidEval("library(ggplot2)");
					c.voidEval("library(mclust)");
					c.voidEval("library(gridExtra)");
					
					c.assign("pngname", query.getTitle() + "_scores.png");
					c.voidEval("d <- data.frame(time, score, rel)");
					c.voidEval("d$rel <- as.factor(d$rel)");
					c.voidEval("plot1 <- ggplot(d, aes(time, score, color = rel)) + geom_point(size=0.5)");
					c.voidEval("fit <- Mclust(d[,1:2])");
					c.voidEval("class <- as.factor(fit$classification)");
					c.voidEval("plot2 <- ggplot(d, aes(time, score, color = class)) + geom_point(size=0.5)");
					c.voidEval("g <- arrangeGrob(plot1, plot2, ncol=2)");
					c.voidEval("ggsave(pngname, g)");
					//c.voidEval("summary(fit, parameters=T)");		
					//c.eval("dev.off()");
	            }catch (Exception e) {
	            	e.printStackTrace();
	            }

			}

			/*
			 * IndriRunQuery -trecFormat=true
			 * -index=../indexes/ap.temporal.krovetz.88-89/
			 * topics/topics.ap.51-150.krovetz.indri >
			 * jones_diaz/ap.88-89.dir.results
			 * 
			 * trec_eval -c -q -m map qrels/qrels.ap.51-150
			 * jones_diaz/ap.88-89.dir.results | sort -k 2 -n > ap.88-89.map
			 * 
			 * R ap <- read.csv("ap.88-89.map",header=T) stats <-
			 * read.csv("ap.88-89.out",header=T) cor.test(ap$ap , stats$acf ,
			 * method = "spearman") data: ap$ap and stats$acf S = 127310,
			 * p-value = 0.03455 alternative hypothesis: true rho is not equal
			 * to 0 sample estimates: rho 0.2126908 cor.test(ap$ap , stats$kl ,
			 * method = "spearman") data: ap$ap and stats$kl S = 78066, p-value
			 * = 4.219e-08 alternative hypothesis: true rho is not equal to 0
			 * sample estimates: rho 0.5172187
			 */
		}
		output.close();
	}

	/**
	 * 
	 * p(t | D) = 1 if document date = t
	 * 
	 * p(t | Q) = sum_{d \in R} p(t | D) * p(q | D)/sum_{d' in R} p(q | D')
	 * 
	 * p(t | C) = 1/|C| sum_{d in C} p(t | D)
	 * 
	 * p'(t | Q) = lambda * p(t | Q) + (1-lamba) p(t | C)
	 * 
	 * SMA
	 * 
	 */
	public static Map<Double, Double> readCollectionProfile(String colProfilePath) throws Exception {
		Map<Double, Double> colProfile = new TreeMap<Double, Double>();
		List<String> lines = IOUtils.readLines(new FileInputStream(colProfilePath));
		int i=0;
		for (String line : lines) {
			String[] values = line.split(",");
			colProfile.put(Double.parseDouble(values[0]), Double.parseDouble(values[1]));
			
			if (i==0) {
				minTime = Double.parseDouble(values[0]);
				System.out.println("Setting minTime = " + minTime);
			} else if (i == lines.size()-1) {
				maxTime = Double.parseDouble(values[0]);
				System.out.println("Setting maxTime = " + maxTime);

			}
			i++;
		}
		return colProfile;
	}


	public static Options createOptions() {
		Options options = new Options();
		options.addOption("index", true, "Path to input index");
		options.addOption("colProfile", true, "Path to collection profile output");
		options.addOption("output", true, "Path to output directory");
		options.addOption("topics", true, "Path to topics file");
		options.addOption("qrels", true, "Path to qrels file");
		options.addOption("plot", false, "Whether to generate per-query plots");
		options.addOption("plotPath", true, "Path to plot directory, required if plot specified");
		options.addOption("interval", true, "Interval (3600, 86400)");
		return options;
	}
}
