package edu.gslis.indexes;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import edu.gslis.docscoring.support.IndexBackedCollectionStats;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.scorers.TemporalScorer;
import edu.gslis.temporal.scorers.TimeSmoothedScorerAverage;
import edu.gslis.temporal.util.RUtil;

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
        long startTime = Long.parseLong(cl.getOptionValue("startTime"));
        long interval = Long.parseLong(cl.getOptionValue("interval"));

        GQueries queries = null;
        if (queryFilePath.endsWith("indri")) 
            queries = new GQueriesIndriImpl();
        else
            queries = new GQueriesJsonImpl();
        queries.read(queryFilePath);

        TimeSeriesIndex tsIndex = new TimeSeriesIndex();
        tsIndex.open(tsIndexPath, true, "csv");
        
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
        IndexBackedCollectionStats collectionStats = new IndexBackedCollectionStats();
        collectionStats.setStatSource(indexPath);
        
        TemporalScorer tsa = new TimeSmoothedScorerAverage();
        tsa.setIndex(tsIndex);
        tsa.setCollectionStats(collectionStats);
        
        
        double N = index.docCount();
        Iterator<GQuery> it = queries.iterator();
        while (it.hasNext()) 
        {
            GQuery query = it.next();
                        
            Iterator<String> queryIterator = query.getFeatureVector().iterator();            
            List<double[]> npmis = new ArrayList<double[]>();
            List<String> terms = new ArrayList<String>();
            while(queryIterator.hasNext()) 
            {
                String feature = queryIterator.next();
                try {
                    double[] npmi = tsIndex.getNpmi(feature);
                    npmis.add(npmi);
                    terms.add(feature);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            PearsonsCorrelation cor = new PearsonsCorrelation();
            double avgCor = 0;
            int k = 0;
            for (int i=0; i<npmis.size(); i++) {
                for (int j=i; j<npmis.size(); j++) {
                    if (i==j) continue;
                    double c = cor.correlation(npmis.get(i), npmis.get(j));                    
                    avgCor += c;
                    k++;
                }
            }
            if (k>0)
                avgCor /= k;

            String[] qterms = query.getText().split(" ");
            DescriptiveStatistics idfstats = new DescriptiveStatistics();
            DescriptiveStatistics scqstats = new DescriptiveStatistics();
            for (String qterm: qterms) {
                double idf = Math.log(N / (1 + index.docFreq(qterm)));
                idfstats.addValue(idf);
                
                double scq = (1 + Math.log(index.docFreq(qterm))) * idf;
                scqstats.addValue(scq);
            }
            double scs = Math.log(1/(double)qterms.length) + idfstats.getMean();

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
            for (int bin=0; bin<numBins; bin++) {
                scores[bin] = scores[bin]/z;
                total += tsIndex.get("_total_", bin);
            }
            
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


            /*
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
        
            }
    */
       }
        
  
    }
        
        
    
    
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "Path to input index");
        options.addOption("tsindex", true, "Path to input tsindex");
        options.addOption("query", true, "Query string");
        options.addOption("startTime", true, "Start time");
        options.addOption("endTime", true, "End time");
        options.addOption("interval", true, "interval");

        return options;
    }

}
