package edu.gslis.main;

import java.io.Writer;
import java.util.Iterator;

import org.lemurproject.kstem.Stemmer;

import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.queries.GQuery;
import edu.gslis.scorers.temporal.RerankingScorer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;

public class QueryRunner implements Runnable
{

    ClassLoader loader = ClassLoader.getSystemClassLoader();

    public static final int NUM_RESULTS = 1000;
    static final int NUM_THREADS = 10;
    
    Stopper stopper;
    Stemmer stemmer;
    GQuery query;
    RerankingScorer docScorer;
    IndexWrapper index;
    CollectionStats corpusStats;
    
    FormattedOutputTrecEval trecFormattedWriter;
    Writer queryWriter;
    
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

    public FormattedOutputTrecEval getTrecFormattedWriter() {
        return trecFormattedWriter;
    }

    public void setTrecFormattedWriter(FormattedOutputTrecEval trecFormattedWriter) {
        this.trecFormattedWriter = trecFormattedWriter;
    }

    public void setQueryWriter(Writer queryWriter) {
    	this.queryWriter = queryWriter;
    }
    
    public void run()
    {
        
        String queryText = query.getText().trim();
        String[] terms = queryText.split("\\s+");
        String stoppedQuery = "";
        for (String term: terms) {
            if (!stopper.isStopWord(term))
                stoppedQuery += term + " ";
        }
        stoppedQuery = stoppedQuery.trim();
        query.setText(stoppedQuery);
        FeatureVector qv = new FeatureVector(stoppedQuery, stopper);
        query.setFeatureVector(qv);
        
        
        docScorer.setQuery(query);
        SearchHits results = null;
        
        synchronized(this) {
        	try {
        		results = index.runQuery(query, NUM_RESULTS);        
        	} catch (Exception e) {
        		System.err.println("Error processing query " + query.getTitle() + ": " + query.getText());
        		e.printStackTrace();
        	}
        }
        
        docScorer.setIndex(index);;
        docScorer.init(results);
                     
        //System.out.println(query.getTitle() + " " + query);

        Iterator<SearchHit> it = results.iterator();
        SearchHits rescored = new SearchHits();
        while (it.hasNext()) {
            SearchHit hit = it.next();
            double score = docScorer.score(hit);
            hit.setScore(score);
            if (score == Double.NaN || score == Double.NEGATIVE_INFINITY) {
                System.err.println("Problem with score for " + query.getText() + "," + hit.getDocno() + "," + score);
            } else if (score != Double.NEGATIVE_INFINITY) {
                rescored.add(hit);
            }
        }
        rescored.rank();
                                
        synchronized (this) {
        	try {
	        	queryWriter.write(docScorer.getQuery().toString() + "\n\n");
	            trecFormattedWriter.write(rescored, query.getTitle());
        	} catch (Exception e) {
        		e.printStackTrace();
        	}
        }
        //System.out.println(query.getTitle() + ": complete");
    }
}
