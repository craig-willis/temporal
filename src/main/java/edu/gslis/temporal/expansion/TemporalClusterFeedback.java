package edu.gslis.temporal.expansion;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import edu.gslis.queries.expansion.Feedback;
import edu.gslis.scorers.temporal.TemporalScorer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.KeyValuePair;
import edu.gslis.utils.KeyValuePairs;
import edu.gslis.utils.ScorableComparator;


/**
 * Generate multiple feedback queries for temporal clusters.
 */
public class TemporalClusterFeedback extends Feedback 
{
	
	List<ScorableFeatureVector> rmVectors = new LinkedList<ScorableFeatureVector>();
	
	public void buildMultipleRM() {
		try 
		{
			RUtil rutil = new RUtil();
			
			Set<String> vocab = new HashSet<String>();
			Map<Double, List<FeatureVector>> classDocVectors 
				= new TreeMap<Double, List<FeatureVector>>();

			if(relDocs == null) {
				relDocs = index.runQuery(originalQuery, fbDocCount);
			}

			double[] scores = new double[relDocs.size()];
			double[] times = new double[relDocs.size()];
			int i=0;
			Iterator<SearchHit> hitIterator = relDocs.iterator();
			while(hitIterator.hasNext()) {
				SearchHit hit = hitIterator.next();
				scores[i] = hit.getScore();
				times[i] = TemporalScorer.getTime(hit);
				i++;
			}

			System.out.println("Clustering");
			double[] classes = rutil.mclust(times, scores); 
			
			double numClasses = max(classes)+1;
			double[] classScores = new double[(int)numClasses];
			double[] classScoresLen  = new double[(int)numClasses];
			hitIterator = relDocs.iterator();
			i=0;
			while(hitIterator.hasNext()) {
				SearchHit hit = hitIterator.next();
				double cls = classes[i];
				
				classScores[(int)cls] += scores[i];
				classScoresLen[(int)cls]++;
				
				List<FeatureVector> fbDocVectors = classDocVectors.get(cls);
				if (fbDocVectors == null) 
					fbDocVectors = new LinkedList<FeatureVector>();
				FeatureVector docVector = index.getDocVector(hit.getDocID(), stopper);
				vocab.addAll(docVector.getFeatures());
				fbDocVectors.add(docVector);
				
				classDocVectors.put(cls, fbDocVectors);
				i++;
			}
			
			for (i=0; i<numClasses; i++) {
				classScores[i] /= classScoresLen[i];
			}
			
			for (Double cls: classDocVectors.keySet())  {
				System.out.println("Getting RM for class " + cls );
				
				double avgDocScore = classScores[cls.intValue()];							
				ScorableFeatureVector rm = new ScorableFeatureVector(avgDocScore);

				List<FeatureVector> fbDocVectors = classDocVectors.get(cls);
				Iterator<String> it = vocab.iterator();
				while(it.hasNext()) {
					String term = it.next();
					
					double fbWeight = 0.0;
					
					Iterator<FeatureVector> docIT = fbDocVectors.iterator();
					int k=0;
					while(docIT.hasNext()) {
						FeatureVector docVector = docIT.next();
						double docProb = docVector.getFeatureWeight(term) / docVector.getLength();
						docProb *= Math.exp(scores[k]);
						fbWeight += docProb;
						k++;
					}
					
					fbWeight /= (double)fbDocVectors.size();
					if (fbWeight > 0) 
						rm.addTerm(term, fbWeight);
				}		
				rmVectors.add(rm);
				ScorableComparator comparator = new ScorableComparator(true);
				Collections.sort(rmVectors, comparator);

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public double max(double[] values) {
		double max = 0;
		for (double v: values) {
			if (v > max)
				max = v;
		}
		return max;
	}
	public List<ScorableFeatureVector> getFeatureVectors() {
		return rmVectors;
	}

	@Override
	public void build() {
		try 
		{
			RUtil rutil = new RUtil();
			
			Set<String> vocab = new HashSet<String>();
			List<FeatureVector> fbDocVectors = new LinkedList<FeatureVector>();

			if(relDocs == null) {
				relDocs = index.runQuery(originalQuery, fbDocCount);
			}

			double[] scores = new double[relDocs.size()];
			double[] times = new double[relDocs.size()];
			int[] docids = new int[relDocs.size()];
			int i=0;
			Iterator<SearchHit> hitIterator = relDocs.iterator();
			while(hitIterator.hasNext()) {
				SearchHit hit = hitIterator.next();
				scores[i] = hit.getScore();
				times[i] = TemporalScorer.getTime(hit);
				docids[i] = hit.getDocID();
				
				FeatureVector docVector = index.getDocVector(hit.getDocID(), stopper);
				vocab.addAll(docVector.getFeatures());
				fbDocVectors.add(docVector);
				i++;
			}

			System.out.println("Clustering");
			double[] classes = rutil.mclust(times, scores); 
			
			int numClasses = (int)max(classes)+1;
			double[] classScores = new double[numClasses];
			double[] classScoresLen  = new double[numClasses];
			for (i=0; i<classes.length; i++) {
				int cls = (int)classes[i];
				
				classScores[cls] += scores[i];
				classScoresLen[cls]++;
			}
			
			for (i=0; i<numClasses; i++) {
				classScores[i] /= classScoresLen[i];
			}
			
			features = new KeyValuePairs();

			Iterator<String> it = vocab.iterator();
			while(it.hasNext()) {
				String term = it.next();
				
				double fbWeight = 0.0;
				
				Iterator<FeatureVector> docIT = fbDocVectors.iterator();
				int k=0;
				while(docIT.hasNext()) {
					int cls = (int)classes[k];
					double classScore = classScores[cls];
					
					FeatureVector docVector = docIT.next();
					double docProb = docVector.getFeatureWeight(term) / docVector.getLength();
					docProb *= Math.exp(scores[k]);
					docProb *= Math.exp(classScore);
					fbWeight += docProb;
					k++;
				}
				
				fbWeight /= (double)fbDocVectors.size();
				
				KeyValuePair tuple = new KeyValuePair(term, fbWeight);
				features.add(tuple);
			}		
		} catch (Exception e) {
			e.printStackTrace();
		}		
	}
}
