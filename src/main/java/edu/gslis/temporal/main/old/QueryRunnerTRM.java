package edu.gslis.temporal.main.old;

import java.util.Iterator;


import org.lemurproject.kstem.Stemmer;

import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.eval.Qrels;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQuery;
import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.main.FormattedOutputTrecEval;
import edu.gslis.temporal.scorers.RerankingScorer;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;

public class QueryRunnerTRM implements Runnable
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
    double trmBeta = 0.5;
    
    
    public void setBeta(double beta) {
        this.trmBeta = beta;
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
        
    public long getDocTime(SearchHit doc) {
        if (doc != null && doc.getDocno() != null) {
            double epoch = (Double)doc.getMetadataValue(Indexer.FIELD_EPOCH);
            return  (long)epoch;
        } else {
            System.err.println("Null " + doc + "," + doc.getDocID() + "," + doc.getDocno());
            return 0;
        }
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

        // Initial retrieval
        SearchHits results = index.runQuery(query, NUM_RESULTS);

        int numBins = 2;
        long[] bounds = new long[3];
        bounds[0] = startTime;
        bounds[1] = startTime + (endTime - startTime)/2;
        bounds[2] = endTime;
        
        SearchHits[] binnedResults = new SearchHits[numBins];
        for (int i=0; i<numBins; i++)
            binnedResults[i] = new SearchHits();

        SearchHits allHits = new SearchHits();
        Iterator<SearchHit> it = results.iterator();
        while (it.hasNext()) {
            SearchHit hit = it.next();
            double score = docScorer.score(hit);
            hit.setScore(score);
            long epoch = getDocTime(hit);
            // Which bin?
            int bin = -1;
            for (int i=0; i<bounds.length; i++) {
                if ((i+1 < bounds.length) && epoch >= bounds[i] && epoch < bounds[i+1]) {
                    bin = i;
                    break;
                }
            }
            if (score == Double.NaN || bin == -1) {
                System.err.println("Problem with score for " + query.getText() + "," + hit.getDocno() + "," + score);
            } else if (score != Double.NEGATIVE_INFINITY) {
                SearchHits hits = binnedResults[bin];
                hits.add(hit);
                allHits.add(hit);
            }
        }
        allHits.rank();
        
        
        // Build relevance model for all hits
        FeedbackRelevanceModel rm = new FeedbackRelevanceModel();
        rm.setDocCount(numFeedbackDocs);
        rm.setTermCount(numFeedbackTerms);
        rm.setIndex(index);
        rm.setStopper(stopper);
        rm.setRes(allHits);
        rm.build();
        
        FeatureVector rmVector = rm.asFeatureVector();
        rmVector = cleanModel(rmVector);
        rmVector.clip(numFeedbackTerms);
        rmVector.normalize();
        
        FeatureVector rm3Vector =
                FeatureVector.interpolate(query.getFeatureVector(), rmVector, rmLambda);        
                
        // Build relevance models for each of the temporal bins
        FeatureVector[] binnedRM = new FeatureVector[numBins];
        if (numBins > 1) 
        {
            
            for (int i=0; i<numBins; i++) {
                
                // Get the hits for the current bin
                SearchHits hits = binnedResults[i];

                // Build relevance model
                FeedbackRelevanceModel binRm = new FeedbackRelevanceModel();
                binRm.setDocCount(numFeedbackDocs);
                binRm.setTermCount(numFeedbackTerms);
                binRm.setIndex(index);
                binRm.setStopper(stopper);
                binRm.setRes(hits);
                binRm.build();
                
                FeatureVector binRmVector = binRm.asFeatureVector();
                binRmVector = cleanModel(binRmVector);
                binRmVector.clip(numFeedbackTerms);
                binRmVector.normalize();
                
                            
                // Interpolate with original RM3 model
                FeatureVector trmVector =
                        FeatureVector.interpolate(binRmVector, rm3Vector, trmBeta);

                trmVector.clip(numFeedbackTerms);
                trmVector.normalize();
                
                binnedRM[i] = trmVector;
            }
        }
        else
            binnedRM[0] = rm3Vector;
                

        SearchHits rescored = new SearchHits();
        for (int i=0; i<numBins; i++) {
            FeatureVector qv = binnedRM[i];
            
            // Execute expanded query
            GQuery newQuery = new GQuery();
            newQuery.setTitle(query.getTitle());
            newQuery.setText(query.getText());
            newQuery.setFeatureVector(qv);
            SearchHits hits = index.runQuery(newQuery, NUM_RESULTS);
            Iterator<SearchHit> hiterator = hits.iterator();
            
            docScorer.setQuery(newQuery);
            long minBound = bounds[i];
            long maxBound = bounds[i+1];
            while (hiterator.hasNext()) {
                SearchHit hit = hiterator.next();
                long epoch = getDocTime(hit);
                if (epoch >= minBound && epoch < maxBound) {
                    double score = docScorer.score(hit);
                    hit.setScore(score);

                    rescored.add(hit);
                }                
            } 
        }

        rescored.rank();
        synchronized (this) {
            trecFormattedWriterRm3.write(rescored, query.getTitle());
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
    
    
    // "The K-L divergence is only defined if P and Q both sum to 1 and if Q(i) > 0
    // for any i such that P(i) > 0."
    public static double kl(FeatureVector p, FeatureVector q)
    {
        double kl = 0;

        double add = (1/(double)p.getFeatureCount());

        double total = 0;
        Iterator<String> it = p.iterator();
        while(it.hasNext()) {
            String feature = it.next();
            double pi = p.getFeatureWeight(feature)/p.getLength();
            double qi = (q.getFeatureWeight(feature) + add)/(q.getLength() + 1);
            kl += pi * Math.log(pi/qi);
            total += qi;
        }
        //System.out.println(total);
        return kl;
    }
}
