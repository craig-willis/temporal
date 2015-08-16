package edu.gslis.temporal.main.old;

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
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.scorers.RerankingScorer;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;

public class QueryRunnerRankLib implements Runnable
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
    
    double[] rmLambdas;
    double[] numFeedbackDocs;
    double[] numFeedbackTerms;
    
    public void setRmLambdas(double[] lambdas) {
        this.rmLambdas = lambdas;
    }
    public void setNumFeedbackDocs(double[] numFeedbackDocs) {
        this.numFeedbackDocs = numFeedbackDocs;
    }
    public void setNumFeedbackTerms(double[] numFeedbackTerms) {
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

        docScorer.init(results);

        Iterator<SearchHit> it = results.iterator();
        Map<SearchHit, double[]> rescored = new LinkedHashMap<SearchHit, double[]>();
        while (it.hasNext()) {
            SearchHit hit = it.next();
            double[] scores = docScorer.scoreMultiple(hit); 
            rescored.put(hit, scores);
        }
        
        for (SearchHit hit: rescored.keySet()) {
            
            List<Double> params = docScorer.getParameter();
            
            StringBuffer str = new StringBuffer();
            double[] scores = rescored.get(hit);
            for (int i=0; i<scores.length; i++) {
                double score = scores[i];
                double param = params.get(i);
                
                str.append(query.getTitle() + " " + 
                        hit.getDocno() + " " + 
                        param + " " + 
                        score + "\n");
            }
            
            synchronized (this) {
                try {
                    writer.write(str.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            
            /*
            row.append(qrels.getRelLevel(query.getTitle(), hit.getDocno()) + " ");   
            row.append("qid:" + query.getTitle() + " ");
            double[] scores = rescored.get(hit);
            for (int i=0; i<scores.length; i++) {
                row.append( (i+1) + ":" + scores[i] + " ");
            }
            row.append("#" + hit.getDocno());
            synchronized (this) {
                try {
                    writer.write(row + "\n");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            */
        }
        System.out.println(query.getTitle() + ": complete");
    }
       
}
