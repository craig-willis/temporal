package edu.gslis.indexes;

import java.text.DecimalFormat;
import java.util.Iterator;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.scorers.TemporalScorer;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;

public class CalculateQueryStats 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( CalculateQueryStats.class.getCanonicalName(), options );
            return;
        }
        String tsIndexPath = cl.getOptionValue("tsindex");
        String indexPath = cl.getOptionValue("index");

        String queryFilePath = cl.getOptionValue("topics");
        double alpha = Double.parseDouble(cl.getOptionValue("alpha", "0.05"));
        
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);


        GQueries queries = null;
        if (queryFilePath.endsWith("indri")) 
            queries = new GQueriesIndriImpl();
        else
            queries = new GQueriesJsonImpl();
        queries.read(queryFilePath);

        TimeSeriesIndex tsIndex = new TimeSeriesIndex();
        tsIndex.open(tsIndexPath, true, "csv");
        
        Iterator<GQuery> it = queries.iterator();
        DecimalFormat df = new DecimalFormat("#0.0000");
        RUtil rutil = new RUtil();
        System.out.println("title,numterms,scoremean,scoresd,scoremin,scoremax," +
                "scorekmean,scoreksd,scorekmin,scorekmax," +
                "scoresmean,scoressd,scoresmin,scoresmax," + 
                "npmimean,npmisd,npmimin,npmimax," + 
                "csmean,cssd,csmin,csmax," + 
                "ac2mean,ac2sd,ac2min,ac2max," + 
                "ac3mean,ac3sd,ac3min,ac3max," + 
                "kmean,ksd,kmin,kmax." + 
                "smean,ssd,smin,smax," + 
                "cpmean,cpsd,cpmin,cpmax"); 
              
        
        //title,numterms,scoremean,scoresd,scoremin,scoremax,scorekmean,scoreksd,scorekmin,scorekmax,scoresmean,scoressd,scoresmin,scoresmax,npmimean,npmisd,npmimin,npmimax,csmean,cssd,csmin,csmax,ac2mean,ac2sd,ac2min,ac2max,ac3mean,ac3sd,ac3min,ac3max,kmean,ksd,kmin,kmax,smean,ssd,smin,smax,cpmean,cpsd,cpmin,cpmax"

        while (it.hasNext()) 
        {
            DescriptiveStatistics npmistats = new DescriptiveStatistics();  
            DescriptiveStatistics chisqstats = new DescriptiveStatistics();  
            DescriptiveStatistics acf2stats = new DescriptiveStatistics();  
            DescriptiveStatistics acf3stats = new DescriptiveStatistics();  
            DescriptiveStatistics kstats = new DescriptiveStatistics();  
            DescriptiveStatistics skewstats = new DescriptiveStatistics();
            DescriptiveStatistics cpstats = new DescriptiveStatistics();  
            DescriptiveStatistics scorestats = new DescriptiveStatistics();  
            DescriptiveStatistics scorekstats = new DescriptiveStatistics();  
            DescriptiveStatistics scoreskewstats = new DescriptiveStatistics();  

            GQuery query = it.next();
            SearchHits hits = index.runQuery(query, 1000);
            Iterator<SearchHit> hiterator = hits.iterator();
            double[] times = new double[hits.size()];
            int i=0;
            while (hiterator.hasNext()) {
                SearchHit hit = hiterator.next();
                scorestats.addValue(hit.getScore());
                times[i] = TemporalScorer.getTime(hit);
                i++;
            }
            double scorek = rutil.kurtosis(times);
            scorekstats.addValue(scorek);                   
            double scoreskew = rutil.skewness(times);
            scoreskewstats.addValue(scoreskew);                   


            FeatureVector fv = query.getFeatureVector();
            for (String feature: fv.getFeatures()) 
            {
                double[] npmi = tsIndex.getNpmi(feature);
                for (int bin=0; bin<npmi.length; bin++) {
                    npmistats.addValue(npmi[bin]);
                }
                double[] chisq = tsIndex.getChiSq(feature, alpha);
                for (int bin=0; bin<chisq.length; bin++) {
                    chisqstats.addValue(chisq[bin]);
                }                    
                double ac2 = rutil.acf(tsIndex.get(feature), 2);
                acf2stats.addValue(ac2);
                double ac3 = rutil.acf(tsIndex.get(feature), 3);
                acf3stats.addValue(ac3);
                
                double k = rutil.kurtosis(tsIndex.get(feature));
                kstats.addValue(k);                   
                
                double s = rutil.skewness(tsIndex.get(feature));
                skewstats.addValue(s);                    

                double cp = index.termFreq(feature)/index.termCount();                
                cpstats.addValue(cp);                                        

            }

            System.out.println(query.getTitle() + "," + fv.getFeatureCount() + "," + 
               scorestats.getMean() + ", " + scorestats.getStandardDeviation()  + "," + scorestats.getMin() + "," + scorestats.getMax() + "," +
               scorekstats.getMean() + ", " + scorekstats.getStandardDeviation()  + "," + scorekstats.getMin() + "," + scorekstats.getMax() + "," +
               scoreskewstats.getMean() + ", " + scoreskewstats.getStandardDeviation()  + "," + scoreskewstats.getMin() + "," + scoreskewstats.getMax() + "," +
               npmistats.getMean() + ", " + npmistats.getStandardDeviation()  + "," + npmistats.getMin() + "," + npmistats.getMax() + "," +
               chisqstats.getMean() + ", " + chisqstats.getStandardDeviation()  + "," + chisqstats.getMin() + "," + chisqstats.getMax() + "," +
               acf2stats.getMean() + ", " + acf2stats.getStandardDeviation()  + "," + acf2stats.getMin() + "," + acf2stats.getMax() + "," +
               acf3stats.getMean() + ", " + acf3stats.getStandardDeviation()  + "," + acf3stats.getMin() + "," + acf3stats.getMax() + "," +
               kstats.getMean() + ", " + kstats.getStandardDeviation()  + "," + kstats.getMin() + "," + kstats.getMax() + "," +
               skewstats.getMean() + ", " + skewstats.getStandardDeviation()  + "," + skewstats.getMin() + "," + skewstats.getMax() + "," +
               cpstats.getMean() + ", " + cpstats.getStandardDeviation()  + "," + cpstats.getMin() + "," + cpstats.getMax());

         } 
    }
        
        
    
    
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "index");        
        options.addOption("tsindex", true, "Path to input tsindex");
        options.addOption("topics", true, "Path to topics file");
        options.addOption("method", true, "npmi chisq ac");
        options.addOption("alpha", true, "chisq alpha");
        return options;
    }

}
