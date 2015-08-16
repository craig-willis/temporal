package edu.gslis.temporal.main.old;

import java.io.FileWriter;
import java.util.Iterator;

import org.lemurproject.kstem.Stemmer;

import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.eval.Qrels;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.expansion.TemporalRM;
import edu.gslis.temporal.main.FormattedOutputTrecEval;
import edu.gslis.temporal.scorers.RerankingScorer;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;

/**
 * Temporal relevance model (TERM)
 * Assumes CER scorer
 *
 */
public class QueryRunnerTERM implements Runnable
{

    ClassLoader loader = ClassLoader.getSystemClassLoader();

    public static final int NUM_RESULTS = 1000;
    static final int NUM_THREADS = 10;
    
    Stopper stopper;
    Stemmer stemmer;
    GQuery query;
    RerankingScorer docScorer;
    IndexWrapper index;
    Qrels qrels;
    CollectionStats corpusStats;
    long startTime;
    long endTime;
    long interval;
    
    FormattedOutputTrecEval trecFormattedWriterRm3 = null;
    FileWriter rankLibWriter = null;
    
    int numFeedbackTerms = 20;
    int numFeedbackDocs = 20;
    double rmLambda = 0.5;
    double sd = 1;
    
    public void setStdDev(double sd) {
        this.sd = sd;
    }
    public double getStdDev() {
        return sd;
    }
    public void setInterval(long interval) {
        this.interval = interval;
    }
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public void setNumFeedbackTerms(int numFeedbackTerms) {
        this.numFeedbackTerms = numFeedbackTerms;
    }
    public void setNumFeedbackDocs(int numFeedbackDocs) {
        this.numFeedbackDocs = numFeedbackDocs;
    }
    public void setRmLambda(double lambda) {
        this.rmLambda = lambda;
    }
    public Stopper getStopper() {
        return stopper;
    }
    
    public void setCollectionStats(CollectionStats corpusStats) {
        this.corpusStats = corpusStats;
    }

    public void setStopper(Stopper stopper) {
        this.stopper = stopper;
    }

    public Stemmer getStemmer() {
        return stemmer;
    }

    public void setStemmer(Stemmer stemmer) {
        this.stemmer = stemmer;
    }

    public GQuery getQuery() {
        return query;
    }

    public void setQuery(GQuery query) {
        this.query = query;
    }

    public RerankingScorer getDocScorer() {
        return docScorer;
    }

    public void setDocScorer(RerankingScorer scorer) {   
        this.docScorer = scorer;
    }

    public IndexWrapper getIndex() {
        return index;
    }

    public void setIndex(IndexWrapper index) {
        this.index = index;
    }


    public FormattedOutputTrecEval getTrecFormattedWriterRm3() {
        return trecFormattedWriterRm3;
    }

    public void setTrecFormattedWriterRm3(
            FormattedOutputTrecEval trecFormattedWriterRm3) {
        this.trecFormattedWriterRm3 = trecFormattedWriterRm3;
    }
    public void setQrels(Qrels qrels) {
        this.qrels = qrels;
    }
    
    public void setRankLibWriter(FileWriter writer) {
        this.rankLibWriter = writer;
    }
    
    public void run()
    {
        
        System.err.println(query.getTitle() + ":" + query.getText());
        String queryText = query.getText().trim();
        String[] terms = queryText.split("\\s+");
        String stoppedQuery = "";
        for (String term: terms) {
            if (!stopper.isStopWord(term))
                stoppedQuery += term + " ";
        }
        stoppedQuery = stoppedQuery.trim();
        query.setText(stoppedQuery);
        FeatureVector fv = new FeatureVector(stoppedQuery, stopper);
        query.setFeatureVector(fv);
        
        docScorer.setQuery(query);

        // Initial retrieval
        SearchHits results = index.runQuery(query, NUM_RESULTS);
        docScorer.init(results);
 
        // Rescore with configured scorer
        Iterator<SearchHit> it = results.iterator();
        SearchHits rescored = new SearchHits();
        while (it.hasNext()) {
            SearchHit hit = it.next();
            double score = docScorer.score(hit);
            hit.setScore(score);
            if (score == Double.NaN) {
                System.err.println("Problem with score for " + query.getText() + "," + hit.getDocno() + "," + score);
            } else if (score != Double.NEGATIVE_INFINITY) {
                rescored.add(hit);
            }
        }
        rescored.rank();
                
        // Construct temporal relevance models given the initial results
        TemporalRM term = new TemporalRM();
        term.setDocCount(numFeedbackDocs);
        term.setTermCount(numFeedbackTerms);
        term.setIndex(index);
        term.setStopper(stopper);
        term.setRes(rescored);
        term.build(startTime, endTime, interval);

        SearchHits termrescored = new SearchHits();

        // Don't re-run the query (ala RM3), but just rescore the initial retrieval
        // using the temporal RM model
        try
        {
            
            int numBins = (int)((endTime - startTime)/interval);

            it = results.iterator();
            while (it.hasNext()) {            
                SearchHit hit = it.next();
                
                double epoch = (Double)hit.getMetadataValue(Indexer.FIELD_EPOCH);
                int bin = (int)((epoch - startTime)/interval);
                if (bin >=0 && bin < numBins) {
                    
                    // Get the model for this bin
                    FeatureVector termFv = term.asFeatureVector(bin);
                    // Interpolate with query
                    FeatureVector termRm3 =
                            FeatureVector.interpolate(query.getFeatureVector(), termFv, rmLambda);

                    // Rescore document against TeRM3 model
                    GQuery termQuery = new GQuery();
                    termQuery.setTitle(query.getTitle());
                    termQuery.setText(query.getText());
                    termQuery.setFeatureVector(termRm3);
                    docScorer.setQuery(termQuery);
                    
                    double score = docScorer.score(hit);
                    hit.setScore(score);
                    if (score == Double.NaN) {
                        System.err.println("Problem with score for " + query.getText() + "," + hit.getDocno() + "," + score);
                    } else if (score != Double.NEGATIVE_INFINITY) {
                        termrescored.add(hit);
                    }
                }
            }            
            termrescored.rank();
            
        } catch (Exception e) {
            System.out.println("Error in query " + query.getTitle());
            e.printStackTrace();
        }
        

        synchronized (this) {
            if (trecFormattedWriterRm3 != null) 
                trecFormattedWriterRm3.write(termrescored, query.getTitle());
            if (rankLibWriter != null) {
                try {
                    Iterator<SearchHit> hits = termrescored.iterator();
                    while (hits.hasNext()) {
                        SearchHit hit = hits.next();
    
                        rankLibWriter.write(
                                query.getTitle() + "," +
                                hit.getDocno() + "," +
                                hit.getScore() + "," +
                                numFeedbackTerms + "," +
                                numFeedbackDocs + "," +
                                rmLambda
                        );
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        
        System.out.println(query.getTitle() + ": complete");
    }
    
    public static FeatureVector cleanModel(FeatureVector model) {
        FeatureVector cleaned = new FeatureVector(null);
        Iterator<String> it = model.iterator();
        while(it.hasNext()) {
            String term = it.next();
            if(term.length() < 3 || term.matches(".*[0-9].*"))
                continue;
            cleaned.addTerm(term, model.getFeatureWeight(term));
        }
        cleaned.normalize();
        return cleaned;
    }

    public String getQueryString(GQuery query) {
        StringBuilder queryString = new StringBuilder("#weight(");
        Iterator<String> qt = query.getFeatureVector().iterator();
        while(qt.hasNext()) {
            String term = qt.next();
            queryString.append(query.getFeatureVector().getFeatureWeight(term) + " " + term + " ");
        }
        queryString.append(")");
        return queryString.toString();
    }
    
    
    
}
