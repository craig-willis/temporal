package edu.gslis.indexes;

import java.util.Iterator;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.Rserve.RConnection;

import edu.gslis.eval.Qrels;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

public class GeneratePlots 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( GeneratePlots.class.getCanonicalName(), options );
            return;
        }
        String indexPath = cl.getOptionValue("index");
        String topicsPath = cl.getOptionValue("topics");
        String qrelsPath = cl.getOptionValue("qrels");
        long startTime = Long.parseLong(cl.getOptionValue("startTime"));
        long endTime = Long.parseLong(cl.getOptionValue("endTime"));
        long interval = Long.parseLong(cl.getOptionValue("interval"));
        String outputPath = cl.getOptionValue("output");

        GQueries queries = null;
        if (topicsPath.endsWith("indri")) 
            queries = new GQueriesIndriImpl();
        else
            queries = new GQueriesJsonImpl();
        queries.read(topicsPath);


        Qrels qrels =new Qrels(qrelsPath, false, 1);
        
        RConnection c = new RConnection();
        
        System.out.println("Setting output directory to:" + outputPath);
        c.voidEval("setwd(\"" + outputPath + "\")");
        
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
        
        int numBins = (int) ((endTime - startTime) / interval)+1;
        int[] bins = new int[numBins];
        for (int i=0; i<numBins; i++) 
            bins[i] = i;
        
        Iterator<GQuery> qit = queries.iterator();
        while (qit.hasNext()) 
        {
            GQuery query = qit.next();

            // Relevant documents
            Set<String> relDocs = qrels.getRelDocs(query.getTitle());
            
            System.out.println("Query: " + query.getTitle());
            System.out.println("Getting relevant documents");
            int[] reldocs = new int[0];
            if (relDocs != null) {
                reldocs = new int[relDocs.size()];
                int k=0;
                for (String relDoc: relDocs) {
                    if (index.getMetadataValue(relDoc, Indexer.FIELD_EPOCH) != null)
                    {
                        double epoch = Double.parseDouble(index.getMetadataValue(relDoc, Indexer.FIELD_EPOCH));
                        int bin = (int) ((epoch - startTime) / interval);
                        reldocs[k] = bin;
                    }
                    else 
                        reldocs[k] = 0;
                    k++;
                }
            }
            System.out.println("Running query");

            // Query results
            SearchHits hits = index.runQuery(query, 1000);
            Iterator<SearchHit> hiterator = hits.iterator();
            double[] docbins = new double[numBins];
            for (int i=0; i<numBins; i++) 
                docbins[i] = 0;
            
            while (hiterator.hasNext()) {
                SearchHit hit = hiterator.next();
                double epoch = (Double)hit.getMetadataValue(Indexer.FIELD_EPOCH);
                int bin = (int) ((epoch - startTime) / interval);
                if (bin < 0 || bin >= numBins)
                    continue;
                
                docbins[bin]++;
            }
            
            for (int i=0; i<numBins; i++) 
                docbins[i] = docbins[i]/(double)1000;

            System.out.println("Generating plots");

            // Plot search results over time
            c.assign("bins", bins);
            c.assign("docbins", docbins);
            c.assign("reldocs", reldocs);
            
            System.out.println("num bins: " + numBins);
            System.out.println("query: " + query.getTitle());
            
            c.voidEval("png(\"" + query.getTitle() + ".png" + "\")");

            
            String plotCmd = "plot(docbins ~ bins, type=\"h\", lwd=2, xlab=\"Days\", ylab=\"% of results\", main=\"" + query.getText() + "\", ylim=c(0, 0.05))";
            c.assign(".tmp.", plotCmd);
            REXP r = c.parseAndEval("try( eval (parse (text=.tmp.)),silent=TRUE)");
            if (r.inherits("try-error")) 
                System.err.println("Error: "+ r.asString());

            if (reldocs.length > 1)
                c.voidEval("lines(density(reldocs), col=\"red\")");
            
            c.voidEval("rug(reldocs, col=\"red\")");    
            c.eval("dev.off()"); 

            
            // Plot reldocs
            /*
            c.voidEval("png(\"" + query.getTitle() + "-reldocs.png" + "\")");
            //c.voidEval("plot(reldocs ~ bins, type=\"h\", lwd=2, main=\"" + query.getText() + " relevant documents\")");
            c.voidEval("plot(density(reldocs), col=\"red\")");
            c.voidEval("rug(reldocs, col=\"red\")");            
            c.eval("dev.off()"); 
            */
        }          
    }
        
        
    
    
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "Path to input index");
        options.addOption("output", true, "Path to output directory");
        options.addOption("topics", true, "Path to topics file");
        options.addOption("qrels", true, "Path to qrels file");
        options.addOption("startTime", true, "Start time");
        options.addOption("endTime", true, "End time");
        options.addOption("interval", true, "interval");

        return options;
    }

}
