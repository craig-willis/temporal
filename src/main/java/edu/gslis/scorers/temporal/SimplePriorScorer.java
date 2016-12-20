package edu.gslis.scorers.temporal;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

import edu.gslis.searchhits.SearchHit;

public class SimplePriorScorer extends TemporalScorer {

    static String ALPHA = "alpha";

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
	    double prior = priors.get(doc.getDocno());
	    	    
            
        if (paramTable.get(ALPHA) != null) {
            double alpha = paramTable.get(ALPHA);
            return alpha*score + (1-alpha)*prior;
        }
        else
            return score + prior;

	    
	}

}
