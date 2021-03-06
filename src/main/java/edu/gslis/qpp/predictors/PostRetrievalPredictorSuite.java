package edu.gslis.qpp.predictors;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import edu.gslis.queries.expansion.Feedback;
import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;

public class PostRetrievalPredictorSuite extends PredictorSuite {


	public Map<String, Double> calculatePredictors() {
		Map<String, Double> values = new HashMap<String, Double>();
		
		
		FeatureVector rmfv = getFeedbackModel(50);
		
		double perplexity = perplexity(hits, k);
		double drift = queryDrift(hits, k);
		double deviation = deviation(hits, k);
		double clarity = clarity(hits, rmfv);
		double qfbdiv_a = divergence(hits, rmfv);
		double qent_a = feedbackQueryEntropy(rmfv);
		
		values.put("perplexity", perplexity);
		values.put("drift",  drift);
		values.put("deviation",  deviation);
		values.put("clarity",  clarity);  // aka Lv Zhai QEnt_R2
		values.put("qfbdiv_a",  qfbdiv_a);
		values.put("QEnt_A",  qent_a);
		values.put("QEnt_R4",  Math.exp(clarity));
		
		return values;
	
	}
	
	public FeatureVector getFeedbackModel(int numFbTerms)
	{
        // Update the query model using feedback documents
        Feedback rm = new FeedbackRelevanceModel();
        rm.setIndex(index);
        rm.setRes(hits);
        rm.setDocCount(k);
        rm.build();
        
        FeatureVector rmfv = rm.asFeatureVector();
        rmfv.clip(numFbTerms);
        rmfv.normalize();
        return rmfv;
	}

	
	/**
	 * Feedback query entropy (Lv and Zhai QEnt_A)
	 */
	public double feedbackQueryEntropy(FeatureVector rmfv) {
		double fbentropy = 0;
		
		for (String w: rmfv.getFeatures())
			fbentropy += -(rmfv.getFeatureWeight(w)) * Math.log(rmfv.getFeatureWeight(w));
		
		return fbentropy;
	}
	
	/**
	 * Absolute divergence: KL-divergence of query model and feedback documents (QFBDiv_A) (Lv and Zhai)
	 */

	public double divergence(SearchHits hits, FeatureVector rmfv) {
		double qfbdiv_a = 0;
		                
        // Update the query model using feedback documents
        FeatureVector fbfv = new FeatureVector(null);
        for (SearchHit hit: hits.hits()) {
			FeatureVector dv = index.getDocVector(hit.getDocID(), stopper);
        	for (String term: dv.getFeatures()) {        		
        		double w = dv.getFeatureWeight(term);
            	fbfv.addTerm(term, w);
        	}
        }
        
        Iterator<String> it = rmfv.iterator();

        while (it.hasNext()) {
            String term = it.next();
            double pwq = rmfv.getFeatureWeight(term)/rmfv.getLength();
            double pwfb = fbfv.getFeatureWeight(term)/fbfv.getLength();
            qfbdiv_a += pwq*Math.log(pwq/pwfb);
        }
        
		return qfbdiv_a;
		
	}
    /**
     * Post-retrieval predictor.
     * Standard deviation of top-k scores. 
     * 
     * Based on: 
     *  Perez-Iglesias, J., & Araujo, L. (2010).    
     *      Standard Deviation as a Query Hardness, 207–212.
     * @param hits
     * @param k
     * @return
     */
    public double deviation(SearchHits hits, int k)
    {        
        DescriptiveStatistics stat = new DescriptiveStatistics();
        for (int i=0; i<k; i++) {
            double score = hits.getHit(i).getScore();
            stat.addValue(score);
        }
        
        return stat.getStandardDeviation();
    }
    
	/**
     * Post-retrieval predictor.
     * Perplexity (2^entropy) of top k documents.
     * 
     * @return
     */
    public double perplexity(SearchHits hits, int k) {
        double perplexity = 0;
        if (hits.size() == 0)
            return 0;
        
        if (k > hits.size()) 
            k = hits.size();

        // Create a feature vector containing terms and weights from top k docs
        FeatureVector fv = new FeatureVector(stopper);
        for (int i=0; i<k; i++) {
            SearchHit hit = hits.getHit(i);
            FeatureVector dv = index.getDocVector(hit.getDocno(), null);
            Set<String> features = dv.getFeatures();
            for (String feature: features) {
                double w = dv.getFeatureWeight(feature);
                fv.addTerm(feature, w);
            }
        }
 
        Set<String> features = fv.getFeatures();
        for (String feature: features) {
            double pw = fv.getFeatureWeight(feature) / fv.getLength();
            perplexity += pw * Math.log(pw)/Math.log(2);
        }
        
        perplexity = Math.exp(perplexity);
        return perplexity;        
    }

    /**
     * Post-retrieval predictor.
     * 
     * Based on NQC method in:
     *  Shtok, Kurland, Carmel. (2009). Predicting query performance by query-drift 
     *      estimation. Advances in Information Retrieval Theory.
     * 
     * @param hits
     * @param k
     * @param scorer
     * @return
     */
    public double queryDrift(SearchHits hits, int k) 
    {
        double queryDrift = 0.0;
        
        double muhat = 0.0;
        for (int i=0; i<k; i++) {
            muhat += hits.getHit(i).getScore();
        }
        muhat /= k;
        
        double ss = 0.0;
        for (int i=0; i<k; i++) {
            double score = hits.getHit(i).getScore();
            //D += score;
            ss += Math.pow(score - muhat, 2);
        }
        
		double D = 0.0;
		Iterator<String> queryIterator = query.getFeatureVector().iterator();
		while(queryIterator.hasNext()) {
			String feature = queryIterator.next();
			double pwc = index.termFreq(feature) / index.termCount();
			double pwq = query.getFeatureVector().getFeatureWeight(feature);
			D += pwq * Math.log(pwc);
		}
        
        ss /= k;
        queryDrift = Math.sqrt(ss)/Math.abs(D);
        
        return queryDrift;
    }
    
   
    
    public double clarity(SearchHits hits, FeatureVector rmfv) 
    {
        double clarity = 0.0;
        
        // Total number of terms in vocabulary
        double numTerms = index.termCount();
                        
        Iterator<String> it = rmfv.iterator();

        while (it.hasNext()) {
            String term = it.next();
            double pwq = rmfv.getFeatureWeight(term);
            double pwc = index.termFreq(term)/numTerms;
            clarity += pwq*Math.log(pwq/pwc);
        }
        return clarity;
    }
    
}
