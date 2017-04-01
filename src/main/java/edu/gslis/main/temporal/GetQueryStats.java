package edu.gslis.main.temporal;

import java.util.Iterator;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import edu.gslis.docscoring.ScorerDirichlet;
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
import edu.gslis.scorers.temporal.TemporalScorer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;

public class GetQueryStats 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( GetQueryStats.class.getCanonicalName(), options );
            return;
        }
        String tsIndexPath = cl.getOptionValue("tsindex");
        String indexPath = cl.getOptionValue("index");

        String queryFilePath = cl.getOptionValue("topics");
        String qrelsPath = cl.getOptionValue("qrels");
        long startTime = Long.parseLong(cl.getOptionValue("startTime"));
        long endTime = Long.parseLong(cl.getOptionValue("endTime"));
        long interval = Long.parseLong(cl.getOptionValue("interval"));
        int port = Integer.parseInt(cl.getOptionValue("port", "6311"));

        
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
        IndexBackedCollectionStats collectionStats = new IndexBackedCollectionStats();
        collectionStats.setStatSource(indexPath);

        Qrels qrels =new Qrels(qrelsPath, false, 1);

        ScorerDirichlet scorer = new ScorerDirichlet();
        scorer.setCollectionStats(collectionStats);
        scorer.setParameter("mu", 2500);

        
        GQueries queries = null;
        if (queryFilePath.endsWith("indri")) 
            queries = new GQueriesIndriImpl();
        else
            queries = new GQueriesJsonImpl();
        queries.read(queryFilePath);

        TimeSeriesIndex tsIndex = new TimeSeriesIndex();
        tsIndex.open(tsIndexPath, true);
        
        Iterator<GQuery> it = queries.iterator();
        RUtil rutil = new RUtil(port);
        System.out.println("title,numterms,scoremean,scoresd,scoremin,scoremax," +
                "scorekmean,scoreksd,scorekmin,scorekmax," +
                "scoresmean,scoressd,scoresmin,scoresmax," + 
                "ac2mean,ac2sd,ac2min,ac2max," + 
                "ac3mean,ac3sd,ac3min,ac3max," + 
                "kmean,ksd,kmin,kmax," + 
                "smean,ssd,smin,smax," + 
                "cpmean,cpsd,cpmin,cpmax," +
                "corrmean,corrsd,corrmin,corrmax," + 
                "ccf,st1,st2,st3"); 
              
        
        //title,numterms,scoremean,scoresd,scoremin,scoremax,scorekmean,scoreksd,scorekmin,scorekmax,scoresmean,scoressd,scoresmin,scoresmax,npmimean,npmisd,npmimin,npmimax,csmean,cssd,csmin,csmax,ac2mean,ac2sd,ac2min,ac2max,ac3mean,ac3sd,ac3min,ac3max,kmean,ksd,kmin,kmax,smean,ssd,smin,smax,cpmean,cpsd,cpmin,cpmax"

        int numBins = (int) ((endTime - startTime) / interval)+1;
        System.out.println("Num bins: " + numBins);
        int[] bins = new int[numBins];
        for (int i=0; i<numBins; i++) 
            bins[i] = i;

        while (it.hasNext()) 
        {
            DescriptiveStatistics acf2stats = new DescriptiveStatistics();  
            DescriptiveStatistics acf3stats = new DescriptiveStatistics();  
            DescriptiveStatistics kstats = new DescriptiveStatistics();  
            DescriptiveStatistics skewstats = new DescriptiveStatistics();
            DescriptiveStatistics cpstats = new DescriptiveStatistics();  
            DescriptiveStatistics scorestats = new DescriptiveStatistics();  
            DescriptiveStatistics scorekstats = new DescriptiveStatistics();  
            DescriptiveStatistics scoreskewstats = new DescriptiveStatistics();  
            DescriptiveStatistics corrstats = new DescriptiveStatistics();
            PearsonsCorrelation corr = new PearsonsCorrelation();
            DescriptiveStatistics bindist = new DescriptiveStatistics();  
            DescriptiveStatistics topbindist = new DescriptiveStatistics();  
            DescriptiveStatistics scoredist = new DescriptiveStatistics();  
            DescriptiveStatistics topscoredist = new DescriptiveStatistics();  

            
            GQuery query = it.next();
            scorer.setQuery(query);
            
            // Relevant documents
            Set<String> relDocs = qrels.getRelDocs(query.getTitle());
            
            System.out.println("Query: " + query.getTitle());
            double[] reldocs = new double[numBins];
            for (int bin=0; bin < numBins; bin++) {
                reldocs[bin] =0 ;
            }
            
            double total = 0;
            int k=0;
            if (relDocs != null) {
                for (String relDoc: relDocs) {
                    SearchHit hit = index.getSearchHit(relDoc, null);
                    double epoch = (Double)hit.getMetadataValue(Indexer.FIELD_EPOCH);
                    int bin = (int) ((epoch - startTime) / interval);
                    double score = scorer.score(hit);
                    if (bin >=0 && bin < numBins) {
                        reldocs[bin] += score;
                        total += score;
                    }
                    else {
                        System.err.println("Warning: epoch out of collection time bounds: " + relDoc + "," + epoch);
                    }
                    k++;
                }
            }
            
            for (int bin=0; bin < numBins; bin++) {
                reldocs[bin] /= total;
            }

            // Retrieval 
            SearchHits hits = index.runQuery(query, 1000);
            
            double[] docbins = new double[numBins];
            for (int i=0; i<numBins; i++) 
                docbins[i] = 0;

            Iterator<SearchHit> hiterator = hits.iterator();
            double[] times = new double[hits.size()];
            int i=0;
            while (hiterator.hasNext()) {
                SearchHit hit = hiterator.next();
                scorestats.addValue(hit.getScore());
                times[i] = TemporalScorer.getTime(hit);
                
                double score = scorer.score(hit);
                double epoch = (Double)hit.getMetadataValue(Indexer.FIELD_EPOCH);
                int bin = (int) ((epoch - startTime) / interval);
                
                if (bin >=0 && bin < numBins) {
                    docbins[bin]+= score;
                    bindist.addValue(bin);
                    scoredist.addValue(score);
                    if (i < 20) {
                        topbindist.addValue(bin);
                        topscoredist.addValue(score);
                    }
                    total+= score;
                }
                else {
                    System.err.println("Warning: epoch out of collection time bounds: " +hit.getDocno() + "," + epoch);
                }                
                i++;
            }
            for (int bin=0; bin<numBins; bin++) 
                docbins[bin] /= total;

            double scorek = rutil.kurtosis(times);
            scorekstats.addValue(scorek);                   
            double scoreskew = rutil.skewness(times);
            scoreskewstats.addValue(scoreskew);   
            
            double ccf0 = rutil.ccf(reldocs, docbins, 0);
//            double ccf1 = rutil.ccf(reldocs, docbins, 1);
//            double ccf2 = rutil.ccf(reldocs, docbins, 2);
//            double ccf3 = rutil.ccf(reldocs, docbins, 3);
//            double ccf4 = rutil.ccf(reldocs, docbins, 4);

            //double st0 = rutil.silvermantest(reldocs, 0);
            double st1 = rutil.silvermantest(reldocs, 1);
            double st2 = rutil.silvermantest(reldocs, 2);
            double st3 = rutil.silvermantest(reldocs, 3);

            
            FeatureVector fv = query.getFeatureVector();
            for (String feature: fv.getFeatures()) 
            {
                double ac2 = rutil.acf(tsIndex.get(feature), 2);
                acf2stats.addValue(ac2);
                double ac3 = rutil.acf(tsIndex.get(feature), 3);
                acf3stats.addValue(ac3);
                
                double kurtosis = rutil.kurtosis(tsIndex.get(feature));
                kstats.addValue(kurtosis);                   
                
                double s = rutil.skewness(tsIndex.get(feature));
                skewstats.addValue(s);                    

                double cp = index.termFreq(feature)/index.termCount();                
                cpstats.addValue(cp);
                
                // Pairwise pearson's correlation between features
                for (String feature2: fv.getFeatures()) 
                {
                    if (feature.equals(feature2)) 
                        continue;
                    
                    double[] freq1 = tsIndex.get(feature);
                    double[] freq2 = tsIndex.get(feature2);
                    double rho = corr.correlation(freq1, freq2);
                    corrstats.addValue(rho);                   
                }
            }
            

            System.out.println(query.getTitle() + "," + fv.getFeatureCount() + "," + 
               scorestats.getMean() + ", " + scorestats.getStandardDeviation()  + "," + scorestats.getMin() + "," + scorestats.getMax() + "," +
               scorekstats.getMean() + ", " + scorekstats.getStandardDeviation()  + "," + scorekstats.getMin() + "," + scorekstats.getMax() + "," +
               scoreskewstats.getMean() + ", " + scoreskewstats.getStandardDeviation()  + "," + scoreskewstats.getMin() + "," + scoreskewstats.getMax() + "," +
               acf2stats.getMean() + ", " + acf2stats.getStandardDeviation()  + "," + acf2stats.getMin() + "," + acf2stats.getMax() + "," +
               acf3stats.getMean() + ", " + acf3stats.getStandardDeviation()  + "," + acf3stats.getMin() + "," + acf3stats.getMax() + "," +
               kstats.getMean() + ", " + kstats.getStandardDeviation()  + "," + kstats.getMin() + "," + kstats.getMax() + "," +
               skewstats.getMean() + ", " + skewstats.getStandardDeviation()  + "," + skewstats.getMin() + "," + skewstats.getMax() + "," +
               cpstats.getMean() + ", " + cpstats.getStandardDeviation()  + "," + cpstats.getMin() + "," + cpstats.getMax() + ","  +
               corrstats.getMean() + ", " + corrstats.getStandardDeviation() + "," + corrstats.getMin() + "," + corrstats.getMax() + "," +
               "," + ccf0 + "," + st1 + "," +st2 + "," + st3 + "," + 
               bindist.getMean() + ", " + bindist.getStandardDeviation() + "," +
               topbindist.getMean() + "," + topbindist.getStandardDeviation() + "," + 
               scoredist.getMean() + "," + scoredist.getStandardDeviation() + "," + 
               topscoredist.getMean() + "," + topscoredist.getStandardDeviation());
         } 
    }
        
        
    
    
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "index");        
        options.addOption("tsindex", true, "Path to input tsindex");
        options.addOption("topics", true, "Path to topics file");
        options.addOption("alpha", true, "chisq alpha");
        options.addOption("qrels", true, "Path to qrels file");
        options.addOption("startTime", true, "Start time");
        options.addOption("endTime", true, "End time");
        options.addOption("interval", true, "interval");
        options.addOption("port", true, "RServe port");
        
        return options;
    }

}
