package edu.gslis.indexes;

import java.util.Iterator;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.lang.ArrayUtils;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.Rserve.RConnection;

import edu.gslis.docscoring.ScorerDirichlet;
import edu.gslis.docscoring.support.IndexBackedCollectionStats;
import edu.gslis.eval.Qrels;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.scorers.TemporalScorer;

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
        int port = Integer.parseInt(cl.getOptionValue("port", "6311"));

        GQueries queries = null;
        if (topicsPath.endsWith("indri")) 
            queries = new GQueriesIndriImpl();
        else
            queries = new GQueriesJsonImpl();
        queries.read(topicsPath);


        Qrels qrels =new Qrels(qrelsPath, false, 1);
        
        RConnection c = new RConnection("localhost", port);
        
        System.out.println("Setting output directory to:" + outputPath);
        c.voidEval("setwd(\"" + outputPath + "\")");
        
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
        IndexBackedCollectionStats collectionStats = new IndexBackedCollectionStats();
        collectionStats.setStatSource(indexPath);

        ScorerDirichlet scorer = new ScorerDirichlet();
        scorer.setCollectionStats(collectionStats);
        scorer.setParameter("mu", 2500);
        
        int numBins = (int) ((endTime - startTime) / interval)+1;
        int numBinsWeek = (int) ((endTime - startTime) / (interval*7))+1;
        System.out.println("Num bins: " + numBins);
        int[] bins = new int[numBins];
        for (int i=0; i<numBins; i++) 
            bins[i] = i;

        int[] binsWeek = new int[numBinsWeek];
        for (int i=0; i<numBinsWeek; i++) 
            binsWeek[i] = i;

        Iterator<GQuery> qit = queries.iterator();
        while (qit.hasNext()) 
        {
            GQuery query = qit.next();
            scorer.setQuery(query);
            
            // Relevant documents
            Set<String> relDocs = qrels.getRelDocs(query.getTitle());
            
            System.out.println("Query: " + query.getTitle());
            System.out.println("Getting relevant documents");
            double[] reldocs = new double[numBins];
            for (int bin=0; bin < numBins; bin++)
                reldocs[bin] =0 ;
            
            double[] reldocsWeek = new double[numBinsWeek];
            for (int bin=0; bin < numBinsWeek; bin++)
                reldocsWeek[bin] =0 ;

            
            int[] rug = new int[0];
            int[] rugw = new int[0];


            double total = 0;
            int k=0;
            SearchHits relDocHits = new SearchHits();
            if (relDocs != null) {
                rug = new int[relDocs.size()];
                rugw = new int[relDocs.size()];

                for (int i=0; i < rug.length; i++) {
                    rug[i] = 0;
                    rugw[i] = 0;
                }
                
                for (String relDoc: relDocs) {
                    int docid = index.getDocId(relDoc);
                    if (docid == -1) 
                        continue;
                    SearchHit hit = index.getSearchHit(relDoc, null);
                    double epoch = (Double)hit.getMetadataValue(Indexer.FIELD_EPOCH);
                    int bin = (int) ((epoch - startTime) / interval);
                    int binWeek = (int) ((epoch - startTime) / (interval*7));
                    double score = scorer.score(hit);
                    hit.setScore(score);
                    relDocHits.add(hit);
                    if (bin >=0 && bin < numBins) {
                        reldocs[bin] += score;
                        reldocsWeek[binWeek] += score;
                        rug[k] = bin;
                        rugw[k] = bin;
                        total += score;
                    }
                    else {
                        System.err.println("Warning: epoch out of collection time bounds: " + relDoc + "," + epoch);
                    }
                    k++;
                }
            }
            
            double[] relDocTimes = TemporalScorer.getTimes(relDocHits, relDocHits.size());
            double[] relDocWeights = getProportionalWeights(relDocHits, relDocHits.size());

            
            for (int bin=0; bin < numBins; bin++)
                reldocs[bin] /= total;
            for (int bin=0; bin < numBinsWeek; bin++)
                reldocsWeek[bin] /= total;
            System.out.println("Running query");
//
            // Query results
            //String uw = "#uw" + (int)query.getFeatureVector().getLength() + "(" + query.getText().trim() + ")";
            //System.out.println("uw: " + uw);
            SearchHits hits = index.runQuery(query, 1000);
            Iterator<SearchHit> hiterator = hits.iterator();
            double[] avgscore = new double[numBins];
            double[] sumscore = new double[numBins];
            double[] numdocs = new double[numBins];
            for (int i=0; i<numBins; i++) {
                avgscore[i] = 0;
                sumscore[i] = 0;
                numdocs[i] = 0;
            }
            double[] scoredist = new double[hits.size()];
            for (int i=0; i<hits.size(); i++)
                scoredist[i] = 0;
            
            total = 0;
            int j=0;
            while (hiterator.hasNext()) {
                SearchHit hit = hiterator.next();
                double score = scorer.score(hit);
                double epoch = (Double)hit.getMetadataValue(Indexer.FIELD_EPOCH);
                int bin = (int) ((epoch - startTime) / interval);
                scoredist[j] = score;
                if (bin >=0 && bin < numBins) {
                    avgscore[bin]+= Math.exp(score);
                    sumscore[bin]+= score;
                    numdocs[bin] ++;
                    total+= score;
                }
                else {
                    System.err.println("Warning: epoch out of collection time bounds: " +hit.getDocno() + "," + epoch);
                }
                j++;
            }
            
            double[] ranks = new double[hits.size()];
            for (int i=0; i<hits.size(); i++) 
                ranks[i] = i;
            
            double[] hitTimes = TemporalScorer.getTimes(hits, hits.size());
            double[] hitWeights = getProportionalWeights(hits, hits.size());

            
            double total2 = 0;
            for (int i=0; i<numBins; i++) {
                sumscore[i] /= total;
                if (numdocs[i] > 0)
                    avgscore[i] /= numdocs[i];
                total2 += avgscore[i];
            }
            
            for (int i=0; i<numBins; i++) {
                avgscore[i] /= total2;
            }

            
            for (int i=0; i<avgscore.length; i++) {
                System.out.println(i + "," + avgscore[i] + "," + sumscore[i] + "," + numdocs[i]);
            }

            System.out.println("Generating plots");
            
            // Plot search results over time
            c.assign("bins", bins);
            c.assign("docbins", sumscore);
            c.assign("avgscores", avgscore);
            c.assign("reldocs", reldocs);
            c.assign("binsw", binsWeek);
            c.assign("reldocsw", reldocsWeek);
            c.assign("rug", rug);
            c.assign("rugw", rugw);
            c.assign("scores", scoredist);
            c.assign("ranks", ArrayUtils.subarray(ranks, 0, 100));
            c.assign("times", ArrayUtils.subarray(hitTimes, 0, 100));
            
            try
            {
                c.voidEval("library(zoo)");
                c.voidEval("ma <- rollmean(docbins, 3, fill=list(NA, NULL, NA))");
                
                System.out.println("num bins: " + numBins);
                System.out.println("query: " + query.getTitle());
                
                // Plot the sum of scores
                c.voidEval("png(\"" + query.getTitle() + ".png" + "\")");
    
                c.voidEval("ymax <- max(docbins) + 0.02");
                String plotCmd = "plot(ma ~ bins, type=\"h\", lwd=2, xlab=\"Days\", ylab=\"% of results\", main=\"" + query.getText() + "\", ylim=c(0, ymax))";
                c.assign(".tmp.", plotCmd);
                REXP r = c.parseAndEval("try( eval (parse (text=.tmp.)),silent=TRUE)");
                if (r.inherits("try-error")) 
                    System.err.println("Error: "+ r.asString());
                c.voidEval("lines(density(bins, weights=docbins), col=\"blue\", bw=\"SJ\")");
    
                if (relDocs != null && relDocs.size() > 1) {
                    c.voidEval("lines(density(bins, weights=reldocs), col=\"red\", bw=\"SJ\")");
                }
                
                c.voidEval("rug(rug, col=\"red\")"); 
                c.eval("dev.off()"); 
                
                // Plot the average scores
                c.voidEval("png(\"" + query.getTitle() + "-avg.png" + "\")");
    
                c.voidEval("ymax <- max(avgscores) + 0.02");
                c.voidEval("mavg <- rollmean(avgscores, 3, fill=list(NA, NULL, NA))");
                plotCmd = "plot(mavg ~ bins, type=\"h\", lwd=2, xlab=\"Days\", ylab=\"% of results\", main=\"" + query.getText() + "\", ylim=c(0, ymax))";
                c.assign(".tmp.", plotCmd);
                r = c.parseAndEval("try( eval (parse (text=.tmp.)),silent=TRUE)");
                if (r.inherits("try-error")) 
                    System.err.println("Error: "+ r.asString());
                c.voidEval("lines(density(bins, weights=avgscores), col=\"blue\", bw=\"SJ\")");
    
                if (relDocs != null && relDocs.size() > 1) {
                    c.voidEval("lines(density(bins, weights=reldocs), col=\"red\", bw=\"SJ\")");
                }
                
                c.voidEval("rug(rug, col=\"red\")"); 
                c.eval("dev.off()");      
               
                // Plot the score distribution
                c.voidEval("png(\"" + query.getTitle() + "-scores.png" + "\")");
    
                plotCmd = "plot(density(scores), main=\"" + query.getText() + "\")";
                c.assign(".tmp.", plotCmd);
                r = c.parseAndEval("try( eval (parse (text=.tmp.)),silent=TRUE)");
                if (r.inherits("try-error")) 
                    System.err.println("Error: "+ r.asString());
                c.eval("dev.off()");  
                
                // Plot the relevant document distribution
                c.voidEval("png(\"" + query.getTitle() + "-rel.png" + "\")");
                
                c.assign("rtimes", relDocTimes);
                c.assign("rweights", relDocWeights);
                c.assign("htimes", hitTimes);
                c.assign("hweights", hitWeights);
                c.voidEval("rweights = rweights / sum(rweights)");
                c.voidEval("hweights = hweights / sum(hweights)");
    //            c.voidEval("hkern = density(htimes, weights=hweights, window=\"gaussian\", bw=\"SJ-dpi\", n=1024)");
                c.voidEval("hkern = density(htimes, weights=hweights, window=\"gaussian\", bw=\"SJ\")");
                plotCmd = "plot(hkern, main=\"" + query.getText() + "\")";
                c.assign(".tmp.", plotCmd);
                r = c.parseAndEval("try( eval (parse (text=.tmp.)),silent=TRUE)");
                if (r.inherits("try-error")) 
                    System.err.println("Error: "+ r.asString());
    //            c.voidEval("rkern = density(rtimes, weights=rweights, window=\"gaussian\", bw=\"SJ-dpi\", n=1024)");
                c.voidEval("rkern = density(rtimes, weights=rweights, window=\"gaussian\", bw=\"SJ\")");
                plotCmd = "lines(rkern, main=\"" + query.getText() + "\", col=\"red\")";
                c.assign(".tmp.", plotCmd);
                r = c.parseAndEval("try( eval (parse (text=.tmp.)),silent=TRUE)");
                if (r.inherits("try-error")) 
                    System.err.println("Error: "+ r.asString());
                c.eval("dev.off()"); 
                
                c.voidEval("png(\"" + query.getTitle() + "-ts.png" + "\")");
                c.voidEval("ts <- ts(docbins, freq=7)");
                String cmd =  "decomp.ts <- decompose(ts)";
                c.assign(".tmp.", cmd);
                r = c.parseAndEval("try( eval (parse (text=.tmp.)),silent=TRUE)");
                if (r.inherits("try-error")) 
                    System.err.println("Error: "+ r.asString());
    
                c.voidEval("plot(decomp.ts$trend, ylim=c(0, 0.2))");
                cmd =  "lines(density(binsw, weights=reldocsw), col=\"red\", bw=\"SJ\")";
                c.assign(".tmp.", cmd);
                r = c.parseAndEval("try( eval (parse (text=.tmp.)),silent=TRUE)");
                if (r.inherits("try-error")) 
                    System.err.println("Error: "+ r.asString());
                c.voidEval("rug(rugw, col=\"red\")"); 
    
                c.eval("dev.off()"); 
                
                

                
                // Plot rank over time
                c.voidEval("png(\"" + query.getTitle() + "-rank.png" + "\")");
    
                c.voidEval("ymax <- max(100) + 0.02");
                plotCmd = "plot(ranks ~ times, type=\"p\", xlab=\"Time\", ylab=\"Rank\", main=\"" + query.getText() + "\", ylim=c(0, ymax))";
                c.assign(".tmp.", plotCmd);
                r = c.parseAndEval("try( eval (parse (text=.tmp.)),silent=TRUE)");
                if (r.inherits("try-error")) 
                    
                    System.err.println("Error: "+ r.asString());
                c.eval("dev.off()"); 
                
                
             
                // Plot reldocs
                /*
                c.voidEval("png(\"" + query.getTitle() + "-reldocs.png" + "\")");
                //c.voidEval("plot(reldocs ~ bins, type=\"h\", lwd=2, main=\"" + query.getText() + " relevant documents\")");
                c.voidEval("plot(density(reldocs), col=\"red\")");
                c.voidEval("rug(reldocs, col=\"red\")");            
                c.eval("dev.off()"); 
                */
            } catch (Exception e) {
                e.printStackTrace();
            }
        }          
    }
        
    public static double[] getProportionalWeights(SearchHits hits, int k) {
        double[] weights = new double[k];
        
        double total = 0;
        for (int i=0; i<k; i++) 
            total += hits.getHit(i).getScore();
        for (int i=0; i<k; i++) {
            weights[i] = hits.getHit(i).getScore()/total;
        }
        
        return weights;
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
        options.addOption("port", true, "RServe port");

        return options;
    }

}
