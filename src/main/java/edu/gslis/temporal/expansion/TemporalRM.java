package edu.gslis.temporal.expansion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.distribution.NormalDistribution;

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;


/**
 * Builds a set of models
 */
public class  TemporalRM
{
    
    protected IndexWrapper index;
    protected SearchHits relDocs;
    protected int fbDocCount  = 20;
    protected int fbTermCount = 20;
    protected Stopper stopper;
    protected FeatureVector[] features;
    
       
    public FeatureVector asFeatureVector(int bin) {
        return features[bin];
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
    public void setRes(SearchHits relDocs) {
        this.relDocs = relDocs;
    }
    public void setStopper(Stopper stopper) {
        this.stopper = stopper;
    }
    

    public void build(long startTime, long endTime, long interval) 
    {
        try 
        {
            Set<String> vocab = new HashSet<String>();
            Map<Integer, List<SearchHit>> fbDocs = new HashMap<Integer, List<SearchHit>>();

            int numBins = (int)((endTime - startTime)/interval);

            for (int i=0; i<numBins; i++) 
               fbDocs.put(i, new ArrayList<SearchHit>());

                
            if (fbDocCount == -1) 
                fbDocCount = relDocs.size();
            
            else if (fbDocCount > relDocs.size())
                fbDocCount = relDocs.size();
                
            
            Map<String, Double> rsvs = new HashMap<String, Double>();

            for (int i=0; i< fbDocCount; i++) {
                SearchHit hit = relDocs.getHit(i);
                // Exponentiate the score = p(M)
                double rsv = Math.exp(hit.getScore());
                rsvs.put(hit.getDocno(), rsv);
                // Add all features for this document to the vocabulary
                FeatureVector docVector = index.getDocVector(hit.getDocID(), stopper);
                vocab.addAll(docVector.getFeatures());
                
                // Get the bin
                double docTime = (Double)hit.getMetadataValue(Indexer.FIELD_EPOCH);
                int bin = (int)((docTime - startTime)/interval);
                
                // Add this document vector
                if (fbDocs.get(bin) != null)
                    fbDocs.get(bin).add(hit);
            }
            
                        
            features = new FeatureVector[numBins];
            
            for (int i=0; i<numBins; i++) {

                // Construct a relevance model based on the feedback documents
                // in this bin
                FeatureVector fv = new FeatureVector(stopper);

                // For each term in the vocabulary
                Iterator<String> it = vocab.iterator();
                while(it.hasNext()) {
                    String term = it.next();
                    
                    double fbWeight = 0.0;
                                    
                    // For each document
                    Iterator<SearchHit> docIt = fbDocs.get(i).iterator();
                    while(docIt.hasNext()) {
                        SearchHit hit = docIt.next();
                        FeatureVector docVector = hit.getFeatureVector();
                        
                        // Calculate the term feedback weight
                        double docProb = docVector.getFeatureWeight(term) / docVector.getLength();                        
                                                
                        docProb *= rsvs.get(hit.getDocno());
                        fbWeight += docProb;
                        
                    }
                    
                    fbWeight /= (double)fbDocs.get(i).size();
                    
                    fv.addTerm(term, fbWeight);
                }
                
                if (fbTermCount != -1)
                    fv.clip(fbTermCount);
                fv.normalize();
                features[i] = fv;
                
                System.out.println("Bin " + i + " RM:\n" + fv.toString(20));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    
    
    public void build(long startTime, long endTime, long interval, double sd) 
    {
        try 
        {
            Set<String> vocab = new HashSet<String>();
            List<FeatureVector> fbDocVectors = new LinkedList<FeatureVector>();

            
            double[] rsvs = new double[relDocs.size()];
            double[] bins = new double[relDocs.size()];

            for (int i=0; i< fbDocCount; i++) {
                SearchHit hit = relDocs.getHit(i);
                // Exponentiate the score = p(M)
                rsvs[i] = Math.exp(hit.getScore());
                
                // Add all features for this document to the vocabulary
                FeatureVector docVector = index.getDocVector(hit.getDocID(), stopper);
                vocab.addAll(docVector.getFeatures());
                
                // Add this document vector
                fbDocVectors.add(docVector);

                // Get the bin
                double docTime = (Double)hit.getMetadataValue(Indexer.FIELD_EPOCH);
                bins[i] = (int)((docTime - startTime)/interval);
            }
            
            
            int numBins = (int)((endTime - startTime)/interval);
            
            features = new FeatureVector[numBins];
            
            // Build the binned relevance models            
            for (int i=0; i<numBins; i++) {

                FeatureVector fv = new FeatureVector(stopper);

                // Normal distribution centered at current bin
                NormalDistribution pnorm = new NormalDistribution(i, sd);
                
                // For each term in the vocabulary
                Iterator<String> it = vocab.iterator();
                while(it.hasNext()) {
                    String term = it.next();
                    
                    double fbWeight = 0.0;
                                    
                    // For each document
                    Iterator<FeatureVector> docIT = fbDocVectors.iterator();
                    int k=0;
                    while(docIT.hasNext()) {
                        FeatureVector docVector = docIT.next();
                        
                        // Calculate the term feedback weight
                        double docProb = docVector.getFeatureWeight(term) / docVector.getLength();                        
                        double docBin = bins[k];
                        double tProb = pnorm.density(docBin);
                                                
                        docProb *= rsvs[k];
                        docProb *= tProb;
                        fbWeight += docProb;
                        
                        k++;
                    }
                    
                    fbWeight /= (double)fbDocVectors.size();
                    
                    fv.addTerm(term, fbWeight);
                }
                
                fv = cleanModel(fv);
                if (fbTermCount != -1)
                    fv.clip(fbTermCount);
                fv.normalize();
                features[i] = fv;
                
                System.out.println("Bin " + i + " RM:\n" + fv.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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