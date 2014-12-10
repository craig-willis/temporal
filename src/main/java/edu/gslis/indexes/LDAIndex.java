package edu.gslis.indexes;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Interface to an H2 DB containing two tables:
 *  topics: docno, topic, probability
 *  topic_terms: term, topic, probability
 */
public class LDAIndex {
    
    Connection con = null;

    static String TOPIC_SQL = "select topics.topic, topics.prob, topic_terms.prob " + 
            "   from topics, topic_terms " + 
            "   where topic_terms.term = ? and topics.docno=? and topic_terms.topic = topics.topic";
    PreparedStatement topicPs = null;
   
    Map<String, Double> docTopicProb = new HashMap<String, Double>();
    Map<String, Double> termTopicProb = new HashMap<String, Double>();

    public void open(String path, boolean readOnly) throws SQLException, ClassNotFoundException
    {
        Class.forName("org.h2.Driver");
        if (readOnly)
            path += ";ACCESS_MODE_DATA=r;CACHE_SIZE=1048576";
        con = DriverManager.getConnection("jdbc:h2:" + path);
        
        topicPs = con.prepareStatement(TOPIC_SQL);
    }
    
    
    public double getTermProbability(String docno, String term)
    {
        double pr = 0;
        
        try
        {
        
            topicPs.setString(1, term);
            topicPs.setString(2, docno);
            ResultSet rs = topicPs.executeQuery();
            while (rs.next()) {
                Double topicProb = rs.getDouble(2);
                Double termProb = rs.getDouble(3);
                pr += topicProb * termProb;
            }
            rs.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return pr;        
    }
    
    public void close() throws SQLException {
        if (con != null)
            con.close();
        if (topicPs != null)
            topicPs.close();
    }
 
}
