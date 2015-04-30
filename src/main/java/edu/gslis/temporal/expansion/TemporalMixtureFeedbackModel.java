package edu.gslis.temporal.expansion;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;


/**
 * Builds a set of models
 */
public class TemporalMixtureFeedbackModel
{
    
    protected IndexWrapper index;
    protected SearchHits relDocs;
    protected Stopper stopper;
    protected FeatureVector[] features;
    
       
    public FeatureVector asFeatureVector(int bin) {
        return features[bin];
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
            List<FeatureVector> fbDocVectors = new LinkedList<FeatureVector>();

            FeatureVector globalVector = new FeatureVector(null);
            
            int numBins = (int)((endTime - startTime)/interval);
            FeatureVector[] binVectors = new FeatureVector[numBins];
            for (int i=0; i<numBins; i++) 
                binVectors[i] = new FeatureVector(null);

            double[] rsvs = new double[relDocs.size()];

            for (int i=0; i< relDocs.size(); i++) {
                SearchHit hit = relDocs.getHit(i);
                // Exponentiate the score = p(M)
                rsvs[i] = Math.exp(hit.getScore());
                
                // Add all features for this document to the vocabulary
                FeatureVector docVector = index.getDocVector(hit.getDocID(), stopper);
                
                // Add this document vector
                fbDocVectors.add(docVector);

                // Populate bin feature vector
                double docTime = (Double)hit.getMetadataValue(Indexer.FIELD_EPOCH);
                int bin = (int)((docTime - startTime)/interval);
                if (bin < numBins) {
                    for (String term: docVector.getFeatures()) {
                        globalVector.addTerm(term, docVector.getFeatureWeight(term));
                        binVectors[bin].addTerm(term, docVector.getFeatureWeight(term));
                    }
                }
            }
            
            
            double lambda = 0.5;
            int numIterations = 50;
            double alpha = 0.001;
            
            features = new FeatureVector[numBins];
            
            for (int i=0; i<numBins; i++) {
                FeatureVector theta_t = new FeatureVector(null);    // Empirical distribution of terms for bin t
                FeatureVector theta_est = new FeatureVector(null);  // Mixture-model estimated distribution
                FeatureVector theta = new FeatureVector(null);      // Working unigram model
                double meanLL = 1e-40;

                // Initialize theta_t and theta_est (random)
                for (String term: globalVector.getFeatures()) {
                    double w = binVectors[i].getFeatureWeight(term);
                    theta_t.addTerm(term, w);                
                    theta_est.addTerm(term, Math.random() + 0.001);
                }            
                // Normalize theta_est
                theta_est.normalize();

                double total = binVectors[i].getLength();
                if (total > 0) 
                {
                    // Start iterations for this bin
                    for (int j=0; j < numIterations; j++) {
                        
                        double ll = 0;
                        
                        // Initialize working model
                        for (String term: globalVector.getFeatures())
                            theta.addTerm(term, theta_est.getFeatureWeight(term));
                        theta.normalize();

                        // Re-initialize estimate
                        theta_est = new FeatureVector(null);
                        for (String term: globalVector.getFeatures())
                            theta_est.setTerm(term, 0);

                        // compute likelihood
                        for (String term: theta_t.getFeatures()) {
                            double collectionPr =
                                    (index.termFreq(term) - binVectors[i].getFeatureWeight(term)) /
                                        (index.termCount() - binVectors[i].getLength());

                            
                            double weight = theta_t.getFeatureWeight(term);
                            ll += weight * Math.log(lambda*collectionPr  
                                    + (1-lambda)*theta.getFeatureWeight(term));
                        }
                        
                        meanLL = 0.5*meanLL + 0.5*ll;
                        if (Math.abs( (meanLL - ll)/meanLL) < alpha) { 
                            System.err.println("Converged at " + j + " with likelihood " + ll);
                            break;
                        } else {
                            System.err.println("Iteration " + j + " " + Math.abs( (meanLL - ll)/meanLL) + ", " + ll);
                        }
                            
                        
                        // update counts
                        for (String term: theta_t.getFeatures()) {
                            
                            double collectionPr =
                                    (index.termFreq(term) - binVectors[i].getFeatureWeight(term)) /
                                        (index.termCount() - binVectors[i].getLength());

                            double weight = theta_t.getFeatureWeight(term);
                            double pr = theta.getFeatureWeight(term);
        
                            double prTopic = (1-lambda)*pr/
                                    ((1-lambda)*pr+lambda*collectionPr);
        
                            double incVal = weight * prTopic;
                            if (incVal > 0) 
                                theta_est.addTerm(term, incVal);
                        }
                        theta_est.normalize();
                    }
                }
                else 
                    theta = theta_t;

                theta.normalize();
                
               
                System.out.println("\nEmpirical:\n" + theta_t.toString(50));
                System.out.println("\nEstimated:\n" + theta.toString(50));
                
                theta.normalize();

                features[i] = theta;
            }

            /*
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
                fv.clip(fbTermCount);
                fv.normalize();
                features[i] = fv;
                
                System.out.println("Bin " + i + " RM:\n" + fv.toString());
            }
            */
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