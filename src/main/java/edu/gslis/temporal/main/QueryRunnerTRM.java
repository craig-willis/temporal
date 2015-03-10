package edu.gslis.temporal.main;

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
        double epoch = (Double)doc.getMetadataValue(Indexer.FIELD_EPOCH);
        long docTime = (long)epoch;
        return docTime;
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

        /*
        SearchHits results = index.runQuery(query, 20);

        // Count the number of documents in each interval
        int k = (int) ((endTime - startTime) / interval)+1;
        double[] numdocs = new double[k];
        double[] avgscore = new double[k];
        int[] bins = new int[k];
        for (int i=0; i<k; i++) {
            bins[i] = i;
            numdocs[i] = 0;
            avgscore[i] = 0;
        }
        
        RUtil rutil = new RUtil();

        Iterator<SearchHit> it = results.iterator();
        while (it.hasNext()) {
            SearchHit hit = it.next();
            double epoch = (Double)hit.getMetadataValue(Indexer.FIELD_EPOCH);
            double score = docScorer.score(hit);            
            int bin = (int) ((epoch - startTime) / interval);
            if (bin >=0 && bin < k) {
                numdocs[bin]++;
                avgscore[bin]+= Math.exp(score);
            }
            else {
                System.err.println("Warning: epoch out of collection time bounds: " +hit.getDocno() + "," + epoch);
            }
        }
        
        double total = 0;
        for (int i=0; i<k; i++) {
            if (numdocs[i] > 0)
                avgscore[i] /= numdocs[i];
            total += avgscore[i];
        }
        
        for (int i=0; i<k; i++) {
            avgscore[i] /= total;
        }

        int[] minima = new int[0];
        try {
            minima = rutil.minima(bins, numdocs);
        } catch (Exception e) {
            e.printStackTrace();
        }

        long[] bounds = new long[minima.length +2];
        bounds[0] = startTime;
        for (int i=0; i<minima.length; i++) {
            bounds[i+1] = startTime + minima[i]*interval;
        }
        bounds[bounds.length-1]= endTime; 
        int numBins = minima.length+1;
        */
        
        int numBins = 1;

        System.out.println(query.getTitle() + " numBins=" + numBins);
        
        long diff = (endTime - startTime)/numBins;
        long[][] bounds = new long[numBins][2];
        for (int i=0; i<numBins; i++) {
            bounds[i][0] = startTime + i*diff;
            bounds[i][1] = bounds[i][0] + diff;
        }

        SearchHits[] binnedResults = new SearchHits[numBins];
        for (int i=0; i<numBins; i++) {
            binnedResults[i] = new SearchHits();
        }

        SearchHits results = index.runQuery(query, NUM_RESULTS);
        Iterator<SearchHit> it = results.iterator();
        while (it.hasNext()) {
            SearchHit hit = it.next();
            double score = docScorer.score(hit);
            hit.setScore(score);
            long epoch = getDocTime(hit);
            // Which bin?
            int bin = -1;
            for (int i=0; i<bounds.length; i++) {
                //if ((i+1 < bounds.length) && epoch >= bounds[i] && epoch < bounds[i+1]) {
                if (epoch >= bounds[i][0] && epoch < bounds[i][1]) {
                    bin = i;
                    break;
                }
            }
            if (score == Double.NaN || bin == -1) {
                System.err.println("Problem with score for " + query.getText() + "," + hit.getDocno() + "," + score);
            } else if (score != Double.NEGATIVE_INFINITY) {
                SearchHits hits = binnedResults[bin];
                hits.add(hit);
            }
        }
        
        FeatureVector[] binnedRM = new FeatureVector[numBins];

        // Now, build numBins different Rm3 models
        for (int i=0; i<numBins; i++) {
            
            SearchHits hits = binnedResults[i];
            
            FeedbackRelevanceModel rm3 = new FeedbackRelevanceModel();
            rm3.setDocCount(numFeedbackDocs);
            rm3.setTermCount(numFeedbackTerms);
            rm3.setIndex(index);
            rm3.setStopper(stopper);
            rm3.setRes(hits);
            rm3.build();
            
            FeatureVector rmVector = rm3.asFeatureVector();
            rmVector = cleanModel(rmVector);
            rmVector.clip(numFeedbackTerms);
            rmVector.normalize();
            FeatureVector feedbackVector =
            FeatureVector.interpolate(query.getFeatureVector(), rmVector, rmLambda);
            
           // System.out.println("FeedbackVector " + i + "\n" + rmVector.toString());
            
            binnedRM[i] = feedbackVector;
        }

        SearchHits rescored = new SearchHits();
        // Rescore each document based on the feedback model
        for (int i=0; i<numBins; i++) {
            SearchHits hits = binnedResults[i];
            FeatureVector qv = binnedRM[i];
            
            /*
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
                double score = docScorer.score(hit);
                hit.setScore(score);
                if (epoch >= minBound && epoch < maxBound)
                    rescored.add(hit);
                
            } 
            */           
            
            Iterator<SearchHit> hiterator = hits.iterator();
            while (hiterator.hasNext()) {
                SearchHit hit = hiterator.next();
                
                double logLikelihood = 0.0;
                Iterator<String> queryIterator = qv.iterator();
                while(queryIterator.hasNext()) {
                    String feature = queryIterator.next();
                    double docFreq = hit.getFeatureVector().getFeatureWeight(feature);
                    double docLength = hit.getLength();
                    double collectionProb = (1 + corpusStats.termCount(feature)) / corpusStats.getTokCount();
                    double pr = (docFreq + 1000*collectionProb) / (docLength + 1000);
                    double queryWeight = qv.getFeatureWeight(feature);
                    logLikelihood += queryWeight * Math.log(pr);
                }

                hit.setScore(logLikelihood);
                rescored.add(hit);
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
    
}
