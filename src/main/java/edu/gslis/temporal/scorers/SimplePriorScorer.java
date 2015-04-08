package edu.gslis.temporal.scorers;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

import edu.gslis.searchhits.SearchHit;

public class SimplePriorScorer extends TemporalScorer {

    Map<String, Double> priors = null;
    
    public void readPriors() throws Exception {
        String priorPath = config.getPriorPath();
        if (priorPath != null) {
            priors = new HashMap<String, Double>();
            BufferedReader br = new BufferedReader(new FileReader(priorPath));
            String line;
            while ((line = br.readLine()) != null) {
                String[] fields = line.split(",");
                priors.put(fields[0], Double.parseDouble(fields[1]));
            }
            br.close();
        }
    }
	public double score(SearchHit doc) {
	    double score = super.score(doc);
	    
	    if (priors == null) {
	        try {
	            readPriors();
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	    }
	    double prior = Math.log(priors.get(doc.getDocno()));
	    
	    System.out.println(score + "," + prior + "," + (score + prior));
	    return score + prior;
	}

}
