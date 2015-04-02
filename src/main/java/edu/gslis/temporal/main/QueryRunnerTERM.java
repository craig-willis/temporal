package edu.gslis.temporal.main;

import java.util.Iterator;
import java.util.Set;

import org.lemurproject.kstem.Stemmer;

import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.eval.Qrels;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQuery;
import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.expansion.TemporalRM;
import edu.gslis.temporal.scorers.OracleKDEScorer;
import edu.gslis.temporal.scorers.RerankingScorer;
import edu.gslis.temporal.scorers.ScorerDirichlet;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;

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
    
    FormattedOutputTrecEval trecFormattedWriterRm3;
    
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
    
    // Problem: Need a scorer per thread...

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

        SearchHits results = index.runQuery(query, NUM_RESULTS);

        if (docScorer instanceof OracleKDEScorer) {
            Set<String> relDocs = qrels.getRelDocs(query.getTitle());
            SearchHits relHits = new SearchHits();
            RerankingScorer dirScorer = new ScorerDirichlet();
            dirScorer.setCollectionStats(corpusStats);
            dirScorer.setQuery(query);
            dirScorer.setParameter("mu", 1000);
            for (String docno: relDocs) {
                SearchHit hit = index.getSearchHit(docno, null);
                double score = dirScorer.score(hit);
                hit.setScore(score);
                relHits.add(hit);
            }
            docScorer.init(relHits);
        }
        else {
            docScorer.init(results);
        }
             
//        System.out.println(query.getTitle() + ": score basic");

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
        

        
        // Full relevance model
        FeedbackRelevanceModel rm3 = new FeedbackRelevanceModel();
        rm3.setDocCount(numFeedbackDocs);
        rm3.setTermCount(numFeedbackTerms);
        rm3.setIndex(index);
        rm3.setStopper(stopper);
        rm3.setRes(rescored);
        rm3.build();
        FeatureVector rmVector = rm3.asFeatureVector();
        rmVector = cleanModel(rmVector);
        rmVector.clip(numFeedbackTerms);
        rmVector.normalize();
        FeatureVector feedbackVector =
        FeatureVector.interpolate(query.getFeatureVector(), rmVector, rmLambda);
        
        GQuery feedbackQuery = new GQuery();
        feedbackQuery.setTitle(query.getTitle());
        feedbackQuery.setText(query.getText());
        feedbackQuery.setFeatureVector(feedbackVector);
        System.out.println(query.getTitle() + " RM3 " + feedbackQuery);


        TemporalRM term2 = new TemporalRM();
        term2.setDocCount(numFeedbackDocs);
        term2.setTermCount(numFeedbackTerms);
        term2.setIndex(index);
        term2.setStopper(stopper);
        term2.setRes(rescored);
        term2.build(startTime, endTime, interval, sd);

        //SearchHits rm3results = new SearchHits();
        //SearchHits rm3rescored = new SearchHits();
        SearchHits termrescored = new SearchHits();
        

        try
        {
            // What if we don't rerun the query, since it's already too centered 

            
            int numBins = (int)((endTime - startTime)/interval);

            it = results.iterator();
            while (it.hasNext()) {            
                SearchHit hit = it.next();
                
                double epoch = (Double)hit.getMetadataValue(Indexer.FIELD_EPOCH);
                int bin = (int)((epoch - startTime)/interval);
                if (bin >=0 && bin < numBins) {
                    FeatureVector termFv = term2.asFeatureVector(bin);
                    termFv = cleanModel(termFv);
                    FeatureVector termRm3 =
                            FeatureVector.interpolate(query.getFeatureVector(), termFv, rmLambda);

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
            
            
            // RM3 version
            /*
            it = results.iterator();
            while (it.hasNext()) {            
                SearchHit hit = it.next();
                
                docScorer.setQuery(feedbackQuery);
                    
                double score = docScorer.score(hit);
                hit.setScore(score);
                if (score == Double.NaN) {
                    System.err.println("Problem with score for " + query.getText() + "," + hit.getDocno() + "," + score);
                } else if (score != Double.NEGATIVE_INFINITY) {
                    termrescored.add(hit);
                }
            }
            
            termrescored.rank();
            */
            
            
            /*
            rm3results = index.runQuery(feedbackQuery, NUM_RESULTS);
            docScorer.init(rm3results);
            
            TemporalRM term = new TemporalRM();
            term.setDocCount(numFeedbackDocs);
            term.setTermCount(numFeedbackTerms);
            term.setIndex(index);
            term.setStopper(stopper);
            term.setRes(rm3results);
            term.build(startTime, endTime, interval);

            int numBins = (int)((endTime - startTime)/interval);

            it = rm3results.iterator();
            while (it.hasNext()) {            
                SearchHit hit = it.next();
                
                double epoch = (Double)hit.getMetadataValue(Indexer.FIELD_EPOCH);
                int bin = (int)((epoch - startTime)/interval);
                if (bin >=0 && bin < numBins) {
                    FeatureVector termFv = term.asFeatureVector(bin);
                    termFv = cleanModel(termFv);
                    FeatureVector termRm3 =
                            FeatureVector.interpolate(query.getFeatureVector(), termFv, rmLambda);

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
                        rm3rescored.add(hit);
                    }
                }

            }
            
            rm3rescored.rank();
            */

        } catch (Exception e) {
            System.out.println("Error in query " + query.getTitle());
            e.printStackTrace();
        }
        

//       System.out.println( query.getTitle() + ": writing results");
        synchronized (this) {
            //trecFormattedWriterRm3.write(rm3rescored, query.getTitle());
            trecFormattedWriterRm3.write(termrescored, query.getTitle());
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
