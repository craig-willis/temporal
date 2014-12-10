package edu.gslis.indexes;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Interface to an H2 DB containing a single table "CLUSTER"
 * with the collection document-cluster mapping.
 * 
 */
public class KMeansIndex {
    
    IndexWrapper index = null;
    Connection con = null;
    
    static String CLUSTER_SQL = "select cluster from clusters where docno = ?";
    PreparedStatement clusterPs = null;

    
    Map<String, Integer> docClusterMap = new HashMap<String, Integer>();
    Map<Integer, FeatureVector> clusterFVMap = new HashMap<Integer, FeatureVector>();
    public void open(String indexPath, String dbPath, boolean readOnly) throws SQLException, ClassNotFoundException
    {
        index =  IndexWrapperFactory.getIndexWrapper(indexPath);

        Class.forName("org.h2.Driver");
        if (readOnly)
            dbPath += ";ACCESS_MODE_DATA=r";
        con = DriverManager.getConnection("jdbc:h2:" + dbPath);
        
        clusterPs = con.prepareStatement(CLUSTER_SQL);
    }
    
    
    public double getTermProbability(String docno, String term) {
        
        int cluster = -1;
        if (docClusterMap.get(docno) == null) {
            try {
            
                clusterPs.setString(1, docno);
                ResultSet rs = clusterPs.executeQuery();
                
                if (rs.next())
                    cluster = rs.getInt(1);
                rs.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            docClusterMap.put(docno, cluster);
        } 
        else 
            cluster = docClusterMap.get(docno);
        
        FeatureVector dv = null;
        if (clusterFVMap.get(cluster) == null)  {
            dv = index.getDocVector(String.valueOf(cluster), null);
            clusterFVMap.put(cluster, dv);
        }
        else
            dv = clusterFVMap.get(cluster);
        
        return dv.getFeatureWeight(term)/dv.getLength();
        
    }
    
    public void close() throws SQLException {
        if (con != null)
            con.close();
        
        if (clusterPs != null)
            clusterPs.close();
    }
}
