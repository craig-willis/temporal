package edu.gslis.temporal.scorers;

import java.util.Iterator;
import java.util.List;

import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;

/**
 * Standard dirichlet query likelihood scorer
 * 
 * @author mefron
 *
 */
public class ScorerDirichlet extends RerankingScorer {
	public String MU = "mu";
	public double EPSILON = 1.0;
	
	public ScorerDirichlet() {
		setParameter(MU, 2500);
	}
	public void setQuery(GQuery query) {
		this.gQuery = query;
	}
	
	/**
	 * retrieves the log-likelihood.  assumes the search hit is populated w term counts.
	 */
	public double score(SearchHit doc) {
		double logLikelihood = 0.0;
		Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
		while(queryIterator.hasNext()) {
			String feature = queryIterator.next();
			double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
			double docLength = doc.getLength();
			double collectionProb = (EPSILON + collectionStats.termCount(feature)) / collectionStats.getTokCount();
			double pr = (docFreq + 
					paramTable.get(MU)*collectionProb) / 
					(docLength + paramTable.get(MU));
			double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
			logLikelihood += queryWeight * Math.log(pr);
		}
		return logLikelihood;
	}
    @Override
    public void init(SearchHits hits) {
        // TODO Auto-generated method stub
        
    }
	

	
    public double[] scoreMultiple(SearchHit doc) {
        List<Double> mus = paramMap.get(MU);
        double[] scores = new double[mus.size()];
        
        for(int i=0; i<mus.size(); i++) {
            double logLikelihood = 0.0;
            double mu = mus.get(i);
            Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
            while(queryIterator.hasNext()) {
                String feature = queryIterator.next();
                double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
                double docLength = doc.getLength();
                double collectionProb = (EPSILON + collectionStats.termCount(feature)) / collectionStats.getTokCount();
                double pr = (docFreq + 
                        mu*collectionProb) / 
                        (docLength + mu);
                double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
                logLikelihood += queryWeight * Math.log(pr);
            }
            scores[i] = logLikelihood;
        }
        return scores;
    }

    public List<Double> getParameter() {
        return paramMap.get(MU);
    }

	
	


}
