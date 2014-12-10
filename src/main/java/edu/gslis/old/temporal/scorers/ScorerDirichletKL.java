package edu.gslis.old.temporal.scorers;

import java.util.Iterator;

import edu.gslis.docscoring.QueryDocScorer;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;


/**
 * Preliminary KL-divergence scorer implementation with Dirichlet smoothing. 
 */
public class ScorerDirichletKL extends QueryDocScorer 
{
	public static final String MU = "mu";
	
	public ScorerDirichletKL() {
		setParameter(MU, 2500);
	}
	public void setQuery(GQuery query) {
		this.gQuery = query;
	}

   
    public double score(SearchHit doc) 
    {
        double kld = 0.0;
        Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
        
        double mu = paramTable.get(MU);

        double dl = doc.getLength();
                        
        while(queryIterator.hasNext()) {            
            String feature = queryIterator.next();
            double cwd = doc.getFeatureVector().getFeatureWeight(feature);
            
            // p(w|C)
            double pwc =  collectionStats.termCount(feature) / collectionStats.getTokCount();
            
            // Skip OOV terms
            if (pwc == 0) 
                continue;
            // p(w|Q)
            double pwq =  gQuery.getFeatureVector().getFeatureWeight(feature)/
                               gQuery.getFeatureVector().getFeatureCount();            
            // p(w|D)
            double pwd = (cwd + mu * pwc)/(dl + mu);
            kld += pwq*Math.log(pwq) - pwq*Math.log(pwd);
                       
        }
        
        //double zhai = scoreZhai(doc);
        //double ql = scoreQL(doc);
        //System.out.println(doc.getDocno() + "," + -1*kld + "," + zhai + "," + ql);
        return -1*kld;
    }   

    /**
     * Score the query against the collection as a single pseudo-document
     * @return
     */
    public double scoreCollection() 
    {
        double kld = 0.0;
        
        double mu = paramTable.get(MU);

        double dl = collectionStats.getTermTypeCount();
        
        Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();                        
        while(queryIterator.hasNext()) {            
            String feature = queryIterator.next();
            double cwd = collectionStats.termCount(feature);
            
            // p(w|C)
            double pwc =  collectionStats.termCount(feature) / collectionStats.getTokCount();
            // p(w|Q)
            double pwq =  gQuery.getFeatureVector().getFeatureWeight(feature)/
                               gQuery.getFeatureVector().getFeatureCount();            
            // p(w|D)
            double pwd = (cwd + mu * pwc)/(dl + mu);
           
            kld += pwq*Math.log(pwq) - pwq*Math.log(pwd);
        }
        
        return -1*kld;
    }   
        
    /**
     * According to Zhai's KL-note (http://sifaka.cs.uiuc.edu/course/498cxz04f/kldir.pdf),
     * KL-divergence is essentially:
     * 
     * sum_{w:c(w;d)>0, p(w|Q)>0) p(w|Q) * log(1 + c(w,d)/(mu*p(w|C))) + log(mu/(mu+|d|)
     */
    public double scoreZhai(SearchHit doc) {
        double kld = 0.0;
        Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
        
        double mu = paramTable.get(MU);

        double dl = doc.getLength();
        double numTerms = collectionStats.getTokCount();
        double unseen = Math.log(mu / (mu + dl));
        
        while(queryIterator.hasNext()) {            
            String feature = queryIterator.next();
            double cwd = doc.getFeatureVector().getFeatureWeight(feature);
            double pwq =  gQuery.getFeatureVector().getFeatureWeight(feature)/
                    gQuery.getFeatureVector().getFeatureCount();       
            
            if (cwd == 0)
                continue;
            
            // sum_{w:c(w;d)>0, p(w|Q)>0) p(w|Q) * log(1 + c(w,d)/(mu*p(w|C))) + log(mu/(mu+|d|)

            double pwc =  collectionStats.termCount(feature) / numTerms;
     
            double seen = Math.log(1 + cwd/(mu*pwc));
                       
            kld += pwq*seen;
        }
        
        kld += unseen;
        
        // This comes from the Lemur implementation, not the note.
        // Subtract of the constant query-collection divergence
        queryIterator = gQuery.getFeatureVector().iterator();
        double collDiv = 0;
        while(queryIterator.hasNext()) {            
            String feature = queryIterator.next();
            double pwq =  gQuery.getFeatureVector().getFeatureWeight(feature)/gQuery.getFeatureVector().getFeatureCount();
           double pwc =  collectionStats.termCount(feature) / collectionStats.getTokCount();
           collDiv += pwq * Math.log(pwq/pwc);            
        }
        kld -= collDiv;
        
        return kld;
        
    }
   
    public double scoreQL(SearchHit doc) {
        double logLikelihood = 0.0;
        Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
        while(queryIterator.hasNext()) {
            String feature = queryIterator.next();
            double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
            double docLength = doc.getLength();
            double collectionProb = collectionStats.termCount(feature)/ collectionStats.getTokCount();
            double pr = (docFreq + 
                    paramTable.get(MU)*collectionProb) / 
                    (docLength + paramTable.get(MU));
            double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature)/
                    gQuery.getFeatureVector().getFeatureCount();
            logLikelihood += queryWeight * Math.log(pr) - queryWeight*Math.log(queryWeight);
        }
        return logLikelihood;
    }
    
}
