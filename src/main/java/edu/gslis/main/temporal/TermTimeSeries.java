package edu.gslis.main.temporal;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import edu.gslis.temporal.util.RUtil;
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
    
    RUtil rutil = new RUtil();
    
	public TermTimeSeries(long startTime, long endTime, long interval, Set<String> terms) {
		this.startTime = startTime;
		this.endTime = endTime;
		this.interval = interval;
        this.numBins = (int) ((endTime - startTime)/interval)+1;
        this.totals = new double[numBins];
        this.terms = terms;
	}
	
	public void addDocument(long docTime, double score, FeatureVector docVector) 
	{
        int t = (int)((docTime - startTime)/interval);
                    
        if (t >= numBins || t < 0) {
        	System.out.println("Document out of time window " + docTime + ", ignoring");
        	return;
        }
        
        Iterator<String> it = docVector.iterator();
        while (it.hasNext()) {
            String f = it.next();
            
            // p(w | D)
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
            
            
            if (termScore[t] < 0) {
            	System.err.println("Score for " + f + " in time " + t + " is less than zero?");
            }
            
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
	
	public void smooth() {	
		int win=3;
		try {
			totals = rutil.sma(totals, win);
			
			for (String term: termMap.keySet()) {
				double[] freq = termMap.get(term);
				termMap.put(term, rutil.sma(freq, win));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public double[] getBinDist() {
		double[] background = getBinTotals();
		double sum = sum(background);
		for (int i=0; i<background.length; i++) 
			background[i] = background[i]/sum;
		return background;
	}
	
	public double[] getTermDist(String term) {
		double[] tsw = termMap.get(term);
		if (tsw == null)
			return null;
		
		double sum = sum(tsw);
		for (int i=0; i<tsw.length; i++) 
			tsw[i] = tsw[i]/sum;
		return tsw;
	}
	


	public double[] getDP() {
		return null;
	}
	
	public double[] getDPS() {
		return null;
	}

	public double[] getTKL() {
		return null;
	}
	
	public double[] getTKLI() {
		return null;
	}
	
	public double[] getTKLC() {
		return null;
	}
	
	public double[] getACF() {
		return null;
	}
	
	public double[] getBinProp() {
		return null;
	}

	public double[] getDPNorm() {
		return null;
	}

	public double[] getDPSNorm() {
		return null;
	}
	
	public double[] getTKLNorm() {
		return null;
	}

	public double[] getTKLINorm() {
		return null;
	}
	public double[] getTKLCNorm() {
		return null;
	}
	
	public void save(String path) throws IOException {
		FileWriter writer = new FileWriter(path, true);
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
	
    public double sum(double[] d) {
    	double sum = 0;
    	if (d == null)
    		return 0;
    	for (double x: d)
    		sum += x;
    	return sum;
    }
    
    public void plot(String term, String path) {
    	double[] background = getBinDist();
    	double[] tsw = getTermDist(term);
    	try {
    		rutil.plot2(term, tsw, background, path);    	
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    }
    
}
