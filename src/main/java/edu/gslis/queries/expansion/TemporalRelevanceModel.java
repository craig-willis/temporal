package edu.gslis.queries.expansion;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import edu.gslis.searchhits.SearchHit;
import edu.gslis.temporal.scorers.KDEScorer;
import edu.gslis.temporal.util.RKernelDensity;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.KeyValuePair;
import edu.gslis.utils.KeyValuePairs;



/**
 * Inspired by Peetz.  What if we incorporate the KDE data directly into the relevance model estimation,
 * instead of via the scores?
 */
public class TemporalRelevanceModel extends Feedback {
	private double[] docWeights = null;
	
	@Override
	public void build() {
		try {
			Set<String> vocab = new HashSet<String>();
			List<FeatureVector> fbDocVectors = new LinkedList<FeatureVector>();

			if(relDocs == null) {
				relDocs = index.runQuery(originalQuery, fbDocCount);
			}
			
	        double[] x = KDEScorer.getTimes(relDocs);
	        double[] w = KDEScorer.getUniformWeights(relDocs);
	        RKernelDensity dist = new RKernelDensity(x, w);    

			double[] rsvs = new double[relDocs.size()];
            double[] times = new double[relDocs.size()];
			int k=0;
			Iterator<SearchHit> hitIterator = relDocs.iterator();
			while(hitIterator.hasNext()) {
				SearchHit hit = hitIterator.next();
				times[k] = KDEScorer.getTime(hit);
				rsvs[k++] = Math.exp(hit.getScore());
			}
			
			hitIterator = relDocs.iterator();
			while(hitIterator.hasNext()) {
				SearchHit hit = hitIterator.next();
				FeatureVector docVector = index.getDocVector(hit.getDocID(), stopper);
				vocab.addAll(docVector.getFeatures());
				fbDocVectors.add(docVector);
			}

			features = new KeyValuePairs();

			
			Iterator<String> it = vocab.iterator();
			while(it.hasNext()) {
				String term = it.next();
				
				double fbWeight = 0.0;
				
				Iterator<FeatureVector> docIT = fbDocVectors.iterator();
				k=0;
				while(docIT.hasNext()) {
					FeatureVector docVector = docIT.next();
					double docProb = docVector.getFeatureWeight(term) / docVector.getLength();
					
					double docWeight = 1.0;
					if(docWeights != null)
						docWeight = docWeights[k];
					docProb *= rsvs[k++];
					docProb *= docWeight;
					fbWeight += docProb;
				}
				
				fbWeight /= (double)fbDocVectors.size();
				
				KeyValuePair tuple = new KeyValuePair(term, fbWeight);
				features.add(tuple);
			}
			
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void setDocWeights(double[] docWeights) {
		this.docWeights = docWeights;
	}
}
