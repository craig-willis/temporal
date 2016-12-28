package edu.gslis.main.temporal;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.collections.Bag;
import org.apache.commons.collections.bag.TreeBag;
import org.rosuda.REngine.Rserve.RConnection;

import edu.gslis.docscoring.support.IndexBackedCollectionStats;
import edu.gslis.eval.Qrels;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.indexes.temporal.TimeSeriesIndex;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.scorers.temporal.TSMScorer;
import edu.gslis.scorers.temporal.TemporalScorer;

public class PlotQuery 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( PlotQuery.class.getCanonicalName(), options );
            return;
        }
        String tsIndexPath = cl.getOptionValue("tsindex");
        String indexPath = cl.getOptionValue("index");
        String queryFilePath = cl.getOptionValue("query");
        String qrelsPath = cl.getOptionValue("qrels");
        long startTime = Long.parseLong(cl.getOptionValue("startTime"));
        long interval = Long.parseLong(cl.getOptionValue("interval"));
        String outputPath = cl.getOptionValue("output");

        GQueries queries = null;
        if (queryFilePath.endsWith("indri")) 
            queries = new GQueriesIndriImpl();
        else
            queries = new GQueriesJsonImpl();
        queries.read(queryFilePath);


        Qrels qrels =new Qrels(qrelsPath, false, 1);

        TimeSeriesIndex tsIndex = new TimeSeriesIndex();
        tsIndex.open(tsIndexPath, true);
        
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
        IndexBackedCollectionStats collectionStats = new IndexBackedCollectionStats();
        collectionStats.setStatSource(indexPath);
        
        TemporalScorer tsa = new TSMScorer();
        tsa.setIndex(tsIndex);
        tsa.setCollectionStats(collectionStats);
        
        
        double N = index.docCount();
        Iterator<GQuery> it = queries.iterator();
        DecimalFormat df = new DecimalFormat("#0.0000");
        while (it.hasNext()) 
        {
            GQuery query = it.next();
                        
            // Get the relevant documents
            Set<String> relDocs = qrels.getRelDocs(query.getTitle());
            List<Integer> relDocBins = new ArrayList<Integer>();
            Bag relDocBag = new TreeBag();
            int i = 0;
            for (String relDoc: relDocs) {
                if (index.getMetadataValue(relDoc, Indexer.FIELD_EPOCH) != null)
                {
                    double epoch = Double.parseDouble(index.getMetadataValue(relDoc, Indexer.FIELD_EPOCH));
                    int bin = (int) ((epoch - startTime) / interval);
                    relDocBins.add(bin);
                    relDocBag.add(bin);
                    //System.out.println(relDoc + "," + bin);
                    i++;
                }
            }

            // Score the bin with respect to the query
            // Approximate query generation likelihood
            int numBins = tsIndex.getNumBins();
            double[] scores = new double[numBins];
            double z = 0;            
            for (int bin=0; bin<numBins; bin++) {
                // Log-likelihood of query given temporal model
                double ll = tsa.scoreTemporalModel(query.getFeatureVector(), bin);
                scores[bin] = Math.exp(ll);
                z += scores[bin];
            }
    
            // p(theta_i given Q)
            double total = 0;
            double[] bins = new double[numBins];
            for (int bin=0; bin<numBins; bin++) {
                scores[bin] = scores[bin]/z;
                bins[bin] = bin;
                total += tsIndex.getLength(bin);                
                //System.out.println(query.getTitle() + "," + scores[bin]);
            }
            
            


            System.out.println("<h2>" + query.getTitle() + ": " + query.getText() + "</h2></br>");
            System.out.println("<img src=\"" + query.getTitle() + ".png\">" + query.getTitle() + "</img><br>");
            

            RConnection c = new RConnection();
            
            c.voidEval("setwd(\"" + outputPath + "\")");

            int[] reldocs = new int[relDocBins.size()];
            for (int j=0; j<relDocBins.size(); j++) 
                reldocs[j] = relDocBins.get(j);

            // Plot p(time | Q)
            c.assign("x", bins);
            
            c.assign("y", scores);
            c.voidEval("png(\"" + query.getTitle() + ".png" + "\")");
            c.voidEval("plot(y ~ x, type=\"h\", lwd=2, main=\"p(time | " + query.getText() + ")\", ylim=c(0,0.3))");

            if (relDocBins.size() > 0) {
                c.assign("reldocs", reldocs);
                if (relDocBins.size() > 1) {
                    c.voidEval("lines(density(reldocs), col=\"red\")");
                }
                c.voidEval("rug(reldocs, col=\"red\")");
            }            
            c.eval("dev.off()");
            
         
            
            // Individual term plots
            int qterms = query.getText().split(" ").length;
            String par="par(mfrow=c(1,1))";
            switch (qterms) {
            case 1:
                par="par(mfrow=c(1,1))";
                break;
            case 2:
                par="par(mfrow=c(2,1))";                
                break;
            case 3:
            case 4:
                par="par(mfrow=c(2,2))";          
                break;
            case 5:
            case 6:
                par="par(mfrow=c(3,2))";     
                break;
            case 7:
            case 8:
                par="par(mfrow=c(3,3))";   
                break;
            }
            
            // Plot p(term|Time)
            c.voidEval("png(\"" + query.getTitle() + "-terms.png" + "\")");
            c.voidEval(par);
            for (String feature: query.getFeatureVector().getFeatures()) 
            {
                double collectionProb = (1 + collectionStats.termCount(feature)) 
                    / collectionStats.getTokCount();
                
                double[] tprobs = new double[numBins];
                double max = 0;
                for (int bin=0; bin<numBins; bin++) {
                    tprobs[bin] = tsIndex.get(feature, bin) / (double)tsIndex.get("_total_", bin);
                    if (tprobs[bin] > max)
                       max = tprobs[bin] ;
                }
                
                c.assign("x", bins);
                c.assign("y", tprobs);
                c.assign("z", String.valueOf(collectionProb));
                double ylim = 0.001;
                if (max > ylim)
                    ylim = max;
                c.voidEval("plot(y ~ x, type=\"h\", lwd=2, main=\"p(" + feature + " | T)\")");
                
                if (relDocBins.size() > 0) {
                    c.voidEval("rug(reldocs, col=\"red\")");
                }
                c.voidEval("abline(h=z, col=\"red\")");
            }
            c.eval("dev.off()");             
            
            
            /*
            // Diaz and Jones: temporal KL
            double temporalKL = 0;
            for (int bin=0; bin <numBins; bin++) {
                double cf = tsIndex.get("_total_", bin);
                double cp = cf/total;
                temporalKL += scores[bin] * Math.log(scores[bin]/cp);
            }
            
            RUtil rutil = new RUtil();
            double acf2 = rutil.acf(scores, 2);
            double acf3 = rutil.acf(scores, 3);
            double acf4 = rutil.acf(scores, 4);
            double kurtosis = rutil.kurtosis(scores);
            rutil.close();
            
            double magic = 
                    0.305836 + 
                    0.655996* avgCor + 
                    0.966632* idfstats.getMean() + 
                    -0.178094* idfstats.getStandardDeviation() + 
                    0.158812* idfstats.getMax() + 
                    -0.794763* scs + 
                    0.007517* kurtosis + 
                    -0.503339* temporalKL + 
                    -0.199759 * qterms.length + 
                    -0.034920 * scqstats.getMean();
            
            System.out.println(query.getTitle() + "," 
                    + avgCor + "," + idfstats.getMean() + "," 
                    + idfstats.getStandardDeviation() + "," 
                    + idfstats.getMax() + "," + scs  + "," 
                    + acf2 + "," + acf3 + "," + acf4  + "," 
                    +  kurtosis + "," + temporalKL  + "," 
                    + qterms.length + "," + scqstats.getMean() + "," 
                    + scqstats.getStandardDeviation() + "," 
                    + scqstats.getMax() + "," + magic
                    );            
            


            // Map of feature vectors for each bin(t)
            Map<Integer, Map<String, Double>> tms = 
                    tsa.createTemporalModels(query.getFeatureVector().getFeatures());
            
            double[] pseudoDocs = new double[tms.size()];
            for (int bin: tms.keySet())
                pseudoDocs[bin] = 0;
            
            SearchHits hits = index.runQuery(query, 1000);
            Iterator<SearchHit> hiterator = hits.iterator();
            while (hiterator.hasNext()) {
                SearchHit hit = hiterator.next();
                // temporal model        
                double epoch = (Double)hit.getMetadataValue(Indexer.FIELD_EPOCH);
                long docTime = (long)epoch;
                int t = (int)((docTime - startTime)/interval);
                if (t < startTime) 
                    continue;
                pseudoDocs[t]++;
            }
            for (int bin: tms.keySet())
                pseudoDocs[bin] /= hits.size();
            
            */

                        
            /*
            for (String feature: query.getFeatureVector().getFeatures()) 
            {
                double collectionProb = (1 + collectionStats.termCount(feature)) 
                    / collectionStats.getTokCount();
                
                
                for (int bin: tms.keySet()) {
                    
                    Map<String, Double> tfv = tms.get(bin);
                    double tProb = 0;
                    if (tfv.get("_total_") != null && tfv.get("_total_") > 0)
                        tProb = tfv.get(feature)/tfv.get("_total_");

           System.out.println(query.getTitle() + "," + 
                            feature + ","  + bin + "," + collectionProb + "," + 
                                    scores[bin]  + "," + tProb + "," + pseudoDocs[bin] + "," + 
                                  avgCor );
                                  
                }
                    */

            }        
  
    }
        
        
    
    
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "Path to input index");
        options.addOption("tsindex", true, "Path to input tsindex");
        options.addOption("output", true, "Path to output");
        options.addOption("query", true, "Query string");
        options.addOption("qrels", true, "Path to qrels");
        options.addOption("startTime", true, "Start time");
        options.addOption("endTime", true, "End time");
        options.addOption("interval", true, "interval");

        return options;
    }

}
