package edu.gslis.main.temporal;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import edu.gslis.textrepresentation.FeatureVector;

public class TermTimeSeries 
{
	
	long startTime=0;
	long interval=0;
	int numBins=0;
	long endTime = 0;
	
    /* Per term bin counts */
    Map<String, double[]> termMap = new TreeMap<String, double[]>();
    
    /* Total counts for bin */
    double[] totals = new double[0];
    Set<String> terms = null;
    
	public TermTimeSeries(long startTime, long endTime, long interval, Set<String> terms) {
		this.startTime = startTime;
		this.endTime = endTime;
		this.interval = interval;
        this.numBins = (int) ((endTime - startTime)/interval);
        this.totals = new double[numBins];
        this.terms = terms;
	}
	
	public void addDocument(long docTime, double score, FeatureVector docVector) 
	{
        int t = (int)((docTime - startTime)/interval);
            
        if (t >= numBins || t < 0) {
        	System.out.println("Document out of time window " + docTime);
        	return;
        }
        Iterator<String> it = docVector.iterator();
        while (it.hasNext()) {
            String f = it.next();

            
            double pd = docVector.getFeatureWeight(f)/docVector.getLength();
            
            double[] termScore = new double[numBins];
            if (termMap.containsKey(f))
            	termScore = termMap.get(f);
            else {
            	// initialize to zero
            	for (int i=0; i<numBins; i++) 
            		termScore[i] = 0;
            }           
           
            termScore[t]+=pd*Math.exp(score);
            totals[t]+=pd*Math.exp(score);
            
            if (terms != null && terms.contains(f)) {
            	termMap.put(f, termScore);     
            }
        }
	}
	
	public Set<String> getTerms() {
		return termMap.keySet();
	}
	
	public double[] getTermFrequencies(String term) {
		return termMap.get(term);		
	}
	
	public double[] getBinTotals() {
		return totals;
	}
	
	public double getTotalFrequencies(int bin) {
		return totals[bin];
	}
	
	public void save(String path) throws IOException {
		FileWriter writer = new FileWriter(path);
		for (String term: termMap.keySet()) {
			String record = term;
			for (double t: termMap.get(term)) {
				record += "," + t;
			}
			writer.write(record + "\n");
		}
		String record = "_total_";
		for (double t: totals) {
			record += "," + t;
		}
		writer.write(record + "\n");
		writer.close();
	}
}
