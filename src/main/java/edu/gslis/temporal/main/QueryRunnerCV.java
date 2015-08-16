package edu.gslis.temporal.main;

import java.io.FileWriter;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.lemurproject.kstem.Stemmer;

import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.eval.Qrels;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.queries.GQuery;
import edu.gslis.queries.expansion.FeedbackRelevanceModelCV;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.scorers.RerankingScorer;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;

public class QueryRunnerCV implements Runnable
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
    
    FileWriter writer;
    FileWriter rm3writer;
    
    double[] rmLambdas;
    int[] numFeedbackDocs;
    int[] numFeedbackTerms;
    
    public void setRmLambdas(double[] lambdas) {
        this.rmLambdas = lambdas;
    }
    public void setNumFeedbackDocs(int[] numFeedbackDocs) {
        this.numFeedbackDocs = numFeedbackDocs;
    }
    public void setNumFeedbackTerms(int[] numFeedbackTerms) {
        this.numFeedbackTerms = numFeedbackTerms;
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

    public FileWriter getWriter() {
        return writer;
    }

    public void setWriter(FileWriter writer) {
        this.writer = writer;
    }
    public void setRm3Writer(FileWriter rm3writer) {
        this.rm3writer = rm3writer;
    }

    public void setQrels(Qrels qrels) {
        this.qrels = qrels;
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

        SearchHits results = index.runQuery(query, NUM_RESULTS);

        docScorer.init(results);

        Iterator<SearchHit> it = results.iterator();
        Map<SearchHit, double[]> rescored = new LinkedHashMap<SearchHit, double[]>();
        while (it.hasNext()) {
            SearchHit hit = it.next();
            double[] scores = docScorer.scoreMultiple(hit); 
            rescored.put(hit, scores);
        }
        

        List<Double> params = docScorer.getParameter();

        for (SearchHit hit: rescored.keySet()) {
                        
            StringBuffer str = new StringBuffer();
            double[] scores = rescored.get(hit);
            for (int k=0; k<scores.length; k++) {
                double score = scores[k];
                double param = params.get(k);
                
                str.append(query.getTitle() + " " + 
                        hit.getDocno() + " " + 
                        score + " " + 
                        param + " " + 
                        "\n"
                        );
            }
            
            synchronized (this) {
                try {
                    writer.write(str.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        for (int fbDocs: numFeedbackDocs) 
        {
            for (int fbTerms: numFeedbackTerms) 
            {                
               // Feedback model
               FeedbackRelevanceModelCV rm3 = new FeedbackRelevanceModelCV();
               rm3.setDocCount(fbDocs);
               rm3.setTermCount(fbTerms);
               rm3.setIndex(index);
               rm3.setStopper(stopper);
               rm3.setHits(rescored);
               rm3.build();
                
               for (double rmLambda: rmLambdas) {

                    int numVectors = rm3.getSize();
                    for (int i=0; i<numVectors; i++) 
                    {
                        double param = params.get(i);
                        
                        FeatureVector rmVector = rm3.asFeatureVector(i);
                        rmVector = cleanModel(rmVector);
                        rmVector.clip(fbTerms);
                        rmVector.normalize();
                        FeatureVector feedbackVector =
                        FeatureVector.interpolate(query.getFeatureVector(), rmVector, rmLambda);
                        
                        GQuery feedbackQuery = new GQuery();
                        feedbackQuery.setTitle(query.getTitle());
                        feedbackQuery.setText(query.getText());
                        feedbackQuery.setFeatureVector(feedbackVector);
                                      
                        Map<SearchHit, Double> rm3rescored = new LinkedHashMap<SearchHit, Double>();

                        try
                        {
                            SearchHits rm3results = index.runQuery(feedbackQuery, NUM_RESULTS);
                            docScorer.setQuery(feedbackQuery);
                            docScorer.init(rm3results);
                            System.out.println(query.getTitle() + " RM3 " + feedbackQuery);
                            
                            it = rm3results.iterator();

                            while (it.hasNext()) {            
                                SearchHit hit = it.next();
                                double[] scores = docScorer.scoreMultiple(hit);
                                double score = scores[i];
                                rm3rescored.put(hit, score);
                            }
                                             
                        } catch (Exception e) {
                            System.err.println("Error in query " + query.getTitle());
                            e.printStackTrace();
                        }
                        
                        for (SearchHit hit: rm3rescored.keySet()) {
                            
                            StringBuffer str = new StringBuffer();
                            double score = rm3rescored.get(hit);
                                                        
                            str.append(query.getTitle() + " " + 
                                    hit.getDocno() + " " + 
                                    score + " " + 
                                    param + "," + 
                                    fbDocs + "," + 
                                    fbTerms + "," + 
                                    rmLambda + "\n"
                                    );
                            
                            synchronized (this) {
                                try {
                                    rm3writer.write(str.toString());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }
            }
        }
        

        
        System.err.println(query.getTitle() + ": complete");
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

       
}
