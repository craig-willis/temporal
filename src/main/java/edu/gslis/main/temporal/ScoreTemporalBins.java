package edu.gslis.main.temporal;

import java.util.Iterator;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.indexes.temporal.TimeSeriesIndex;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * For each query, score each of the temporal bins in tsindex.
 * Plot.
 *
 */
public class ScoreTemporalBins 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( ScoreTemporalBins.class.getCanonicalName(), options );
            return;
        }
        String tsIndexPath = cl.getOptionValue("tsindex");
        String indexPath = cl.getOptionValue("index");
        String topicsPath = cl.getOptionValue("topics");

        TimeSeriesIndex tsIndex = new TimeSeriesIndex();
        tsIndex.open(tsIndexPath, true, "csv");
        int numBins = tsIndex.getNumBins();

        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
        
        GQueries queries = null;
        if (topicsPath.endsWith("indri")) 
            queries = new GQueriesIndriImpl();
        else
            queries = new GQueriesJsonImpl();
        queries.read(topicsPath);

        
        Iterator<GQuery> queryIt = queries.iterator();
        while(queryIt.hasNext()) {
            GQuery query = queryIt.next();
            
            for (int i=0; i<numBins; i++) {
                double llc = scoreCollection(query.getFeatureVector(), i, tsIndex, index);
                double ll = scoreTemporalModel(query.getFeatureVector(), i, tsIndex, index);
                System.out.println(query.getTitle() + "," + i + "," + Math.exp(ll) + "," + Math.exp(llc));
            }
        }
    }
        
    public static double scoreCollection(FeatureVector dm, int bin, TimeSeriesIndex tsIndex,
            IndexWrapper index)
    {
        double logLikelihood = 0.0;

        try
        {

            for (String feature: dm.getFeatures())
            {                         
                double smoothedProb = index.termFreq(feature)/index.termCount();
                        
                double docWeight = dm.getFeatureWeight(feature);
                
                logLikelihood += docWeight * Math.log(smoothedProb);
            }                        
        } catch (Exception e) {
            e.printStackTrace();
        }
        return logLikelihood;
    }
    
    
    // KL divergence of temporal model (bin) 
    public static double scoreTemporalModel(FeatureVector dm, int bin, TimeSeriesIndex tsIndex,
            IndexWrapper index)
    {
        double logLikelihood = 0.0;

        try
        {
            double tlen = tsIndex.getLength(bin);
            if (tlen == 0)
                return Double.NEGATIVE_INFINITY;
            
            for (String feature: dm.getFeatures())
            {         
                double tfreq = tsIndex.get(feature, bin);
    
                //double smoothedProb = (tfreq + 1)/(tlen + index.termCount());
                //double pwC = collectionStats.termCount(feature) / collectionStats.getTokCount();
                //double smoothedProb = (tfreq + 1000 * pwC ) / (tlen + 1000);
                double smoothedProb = tfreq/tlen;
                        
                double docWeight = dm.getFeatureWeight(feature);
                
                logLikelihood += docWeight * Math.log(smoothedProb);
            }                        
        } catch (Exception e) {
            e.printStackTrace();
        }
        return logLikelihood;
    }
        
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "Path to input index");
        options.addOption("tsindex", true, "Path to input tsindex");
        options.addOption("topics", true, "Path to input topics");
        return options;
    }

}
