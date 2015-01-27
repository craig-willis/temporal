package edu.gslis.temporal.main;

import java.util.Iterator;

import org.lemurproject.kstem.Stemmer;

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.queries.GQuery;
import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.queries.expansion.TemporalRelevanceModel;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.scorers.RerankingScorer;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;

public class QueryRunner implements Runnable
{

    ClassLoader loader = ClassLoader.getSystemClassLoader();

    public static final int NUM_RESULTS = 1000;
    static final int NUM_FEEDBACK_TERMS = 20;
    static final int NUM_FEEDBACK_DOCS = 20;
    static final double LAMBDA = 0.5;
    static final int NUM_THREADS = 10;
    
    Stopper stopper;
    Stemmer stemmer;
    GQuery query;
    RerankingScorer docScorer;
    IndexWrapper index;
    
    FormattedOutputTrecEval trecFormattedWriter;
    FormattedOutputTrecEval trecFormattedWriterRm3;
    
    
    public Stopper getStopper() {
        return stopper;
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

    public FormattedOutputTrecEval getTrecFormattedWriter() {
        return trecFormattedWriter;
    }

    public void setTrecFormattedWriter(FormattedOutputTrecEval trecFormattedWriter) {
        this.trecFormattedWriter = trecFormattedWriter;
    }

    public FormattedOutputTrecEval getTrecFormattedWriterRm3() {
        return trecFormattedWriterRm3;
    }

    public void setTrecFormattedWriterRm3(
            FormattedOutputTrecEval trecFormattedWriterRm3) {
        this.trecFormattedWriterRm3 = trecFormattedWriterRm3;
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
        
//        System.out.println(query.getTitle() + ": run basic");

        SearchHits results = index.runQuery(query, NUM_RESULTS);
        docScorer.init(results);
             
//        System.out.println(query.getTitle() + ": score basic");

        Iterator<SearchHit> it = results.iterator();
        while (it.hasNext()) {
            SearchHit hit = it.next();
            double score = docScorer.score(hit);
            hit.setScore(score);
        }
        results.rank();
        
        // Feedback model
        FeedbackRelevanceModel rm3 = new FeedbackRelevanceModel();
        rm3.setDocCount(NUM_FEEDBACK_DOCS);
        rm3.setTermCount(NUM_FEEDBACK_TERMS);
        rm3.setIndex(index);
        rm3.setStopper(stopper);
        rm3.setRes(results);
        rm3.build();
        FeatureVector rmVector = rm3.asFeatureVector();
        rmVector = cleanModel(rmVector);
        rmVector.clip(NUM_FEEDBACK_TERMS);
        rmVector.normalize();
        FeatureVector feedbackVector =
                FeatureVector.interpolate(query.getFeatureVector(), rmVector, LAMBDA);
        
        GQuery feedbackQuery = new GQuery();
        feedbackQuery.setTitle(query.getTitle());
        feedbackQuery.setText(query.getText());
        feedbackQuery.setFeatureVector(feedbackVector);
                                
//        System.out.println(query.getTitle() + ": run RM3");

        //System.out.println(getQueryString(feedbackQuery));
        SearchHits rm3results = new SearchHits();
        try
        {
            rm3results = index.runQuery(feedbackQuery, NUM_RESULTS);
            docScorer.setQuery(feedbackQuery);
            docScorer.init(rm3results);
                 
//            System.out.println(query.getTitle() + ": score RM3");

            it = rm3results.iterator();
            while (it.hasNext()) {            
                SearchHit hit = it.next();
                double score = docScorer.score(hit);
                hit.setScore(score);
            }
            
            rm3results.rank();
        } catch (Exception e) {
            System.out.println("Error in query " + query.getTitle());
            e.printStackTrace();
        }
        

//       System.out.println( query.getTitle() + ": writing results");
        synchronized (this) {
            trecFormattedWriter.write(results, query.getTitle());
            trecFormattedWriterRm3.write(rm3results, query.getTitle());
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
