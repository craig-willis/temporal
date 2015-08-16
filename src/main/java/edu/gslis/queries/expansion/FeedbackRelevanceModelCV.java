package edu.gslis.queries.expansion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.KeyValuePair;
import edu.gslis.utils.KeyValuePairs;
import edu.gslis.utils.Stopper;

public class FeedbackRelevanceModelCV  
{
    protected IndexWrapper index;
    protected Map<SearchHit, double[]> hits;
    protected GQuery originalQuery;
    protected int fbDocCount  = 20;
    protected int fbTermCount = 20;
    protected KeyValuePairs[] features; 
    protected Stopper stopper;
        

    public int getSize() {
        return features.length;
    }

    public FeatureVector asFeatureVector(int i) {
        FeatureVector f = new FeatureVector(stopper);
        Iterator<KeyValuePair> it = features[i].iterator();
        
        while(it.hasNext()) {           
            KeyValuePair tuple = it.next();
            f.addTerm(tuple.getKey(), tuple.getScore());
        }   
        
        return f;
    }
    
    public void setDocCount(int fbDocCount) {
        this.fbDocCount = fbDocCount;
    }
    public void setTermCount(int fbTermCount) {
        this.fbTermCount = fbTermCount;
    }
        
    public void setIndex(IndexWrapper index) {
        this.index = index;
    }
    public void setHits(Map<SearchHit, double[]> hits) {
        this.hits = hits;
    }

    public void setOriginalQuery(GQuery originalQuery) {
        this.originalQuery = originalQuery;
    }
    public void setStopper(Stopper stopper) {
        this.stopper = stopper;
    }
            
    public void build() 
    {
        try 
        {
            Set<String> vocab = new HashSet<String>();
            List<FeatureVector> fbDocVectors = new LinkedList<FeatureVector>();

            if (fbDocCount > hits.size())
                fbDocCount = hits.size();

            List<double[]> rsvs = new ArrayList<double[]>();
            
            int numScores = 0;
            for (SearchHit hit: hits.keySet()) {
                double[] scores = hits.get(hit);
                numScores = scores.length;
                double[] rsv = new double[scores.length];
                for (int i=0; i<scores.length; i++)
                    rsv[i] = Math.exp(scores[i]);

                rsvs.add(rsv);
                FeatureVector docVector = index.getDocVector(hit.getDocID(), stopper);
                vocab.addAll(docVector.getFeatures());
                fbDocVectors.add(docVector);
            }

            features = new KeyValuePairs[numScores];
            for (int i=0; i<numScores; i++) 
                features[i] = new KeyValuePairs();

            
            Iterator<String> it = vocab.iterator();
            while(it.hasNext()) {
                String term = it.next();
                
                double[] fbWeights = new double[numScores];
                for (int i=0; i<fbWeights.length; i++) 
                    fbWeights[i] = 0;

                for (int i=0; i<fbDocVectors.size(); i++) {
                    
                    FeatureVector docVector = fbDocVectors.get(i);
                    double[] docRsvs = rsvs.get(i);
                    
                    
                    for (int j=0; j<docRsvs.length; j++) {
                        double docProb = docVector.getFeatureWeight(term) / docVector.getLength();
                        double docWeight = 1.0;                        
                        docProb *= docRsvs[j];
                        docProb *= docWeight;
                        fbWeights[j] += docProb;                            
                    }
                }
                
                for (int i=0; i<fbWeights.length; i++) {
                    fbWeights[i] /= (double)fbDocVectors.size();
                    
                    KeyValuePair tuple = new KeyValuePair(term, fbWeights[i]);
                    features[i].add(tuple);
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }   
}
