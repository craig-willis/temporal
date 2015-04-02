package edu.gslis.indexes;

import java.util.Iterator;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import edu.gslis.docscoring.ScorerDirichlet;
import edu.gslis.docscoring.support.IndexBackedCollectionStats;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.scorers.TemporalScorer;

public class CalculateChiSquare 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( CalculateChiSquare.class.getCanonicalName(), options );
            return;
        }
        String tsIndexPath = cl.getOptionValue("tsindex");
        String indexPath = cl.getOptionValue("index");

        String queryFilePath = cl.getOptionValue("topics");
        long startTime = Long.parseLong(cl.getOptionValue("startTime"));
        long endTime = Long.parseLong(cl.getOptionValue("endTime"));
        long interval = Long.parseLong(cl.getOptionValue("interval"));

        
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
        IndexBackedCollectionStats collectionStats = new IndexBackedCollectionStats();
        collectionStats.setStatSource(indexPath);

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
        tsIndex.open(tsIndexPath, true, "csv");
        
        Iterator<GQuery> it = queries.iterator();

        int numBins = (int) ((endTime - startTime) / interval)+1;
        
        while (it.hasNext()) 
        {
            GQuery query = it.next();
            scorer.setQuery(query);

            SearchHits hits = index.runQuery(query, 1000);
            
            double[] scores = new double[numBins];
            for (int i=0; i<numBins; i++) 
                scores[i] = 0;
            
            double[] docs = new double[numBins];
            for (int i=0; i<numBins; i++) 
                docs[i] = 0;            

            Iterator<SearchHit> hiterator = hits.iterator();
            double[] times = new double[hits.size()];
            int i=0;
            double totaldocs = 0;
            double totalscore = 0;
            
            while (hiterator.hasNext()) {
                SearchHit hit = hiterator.next();
                times[i] = TemporalScorer.getTime(hit);
                
                double score = scorer.score(hit);
                double epoch = (Double)hit.getMetadataValue(Indexer.FIELD_EPOCH);
                int bin = (int) ((epoch - startTime) / interval);
                
                if (bin >=0 && bin < numBins) {
                    scores[bin] += score;
                    totalscore += score;
                    docs[bin]++;
                    totaldocs++;
                }
                i++;
            }
            
            // Calculate the average score per bin
            double[] avgscore = new double[numBins];
            System.out.print(query.getTitle());
            for (int bin=0; bin < numBins; bin++) {
                avgscore[bin] = scores[bin] / docs[bin];
                System.out.print("," + avgscore[bin]);                
            }
            System.out.println("\n");
         } 
    }
        
        
    
    
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "index");        
        options.addOption("tsindex", true, "Path to input tsindex");
        options.addOption("topics", true, "Path to topics file");
        options.addOption("startTime", true, "Start time");
        options.addOption("endTime", true, "End time");
        options.addOption("interval", true, "interval");
        
        return options;
    }

}
