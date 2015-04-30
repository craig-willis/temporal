package edu.gslis.temporal.main;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQuery;


/**
 * Contains queries and associated qrels
 *
 */
public class CrossValidationFold 
{
    List<GQuery> foldQueries = new ArrayList<GQuery>();
    public void addQuery(GQuery gquery) {
        foldQueries.add(gquery);       
    }

    public int size() {
        return foldQueries.size();
    }
    
    public List<GQuery> getQueries() {
        return foldQueries;
    }

    public static List<CrossValidationFold> createFolds(GQueries gqueries, int numFolds) {
        List<CrossValidationFold> folds = new ArrayList<CrossValidationFold>();
        for (int i=0; i<numFolds; i++) 
            folds.add(new CrossValidationFold());
                
        List<GQuery> queries = new ArrayList<GQuery>();
        for (int i=0; i<gqueries.numQueries(); i++) 
            queries.add(gqueries.getIthQuery(i));
                
        while (queries.size() > 0) {
            for (int i=0; i< numFolds; i++) {
                if (queries.size() ==0) 
                    break;
                Collections.shuffle(queries);
                GQuery gquery = queries.remove(0);
                folds.get(i).addQuery(gquery);
            }
        }
        return folds;        
    }
    
}
