package edu.gslis.indexes;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Interface to an H2 DB containing two tables:
 *  topics: docno, topic, probability
 *  topic_terms: term, topic, probability
 */
public class LDAIndex {
    
    Connection con = null;

    static String TOPIC_SQL = "select topics.topic, topics.prob, topic_terms.prob " + 
            "   from topics, topic_terms, topic_time " + 
            "   where topic_terms.term = ? and topics.docno=? and topic_terms.topic = topics.topic ";

    static String TOPIC_TIME_SQL = "select topics.topic, topics.prob, topic_terms.prob, topic_time.prob " + 
            "   from topics, topic_terms, topic_time " + 
            "   where topic_terms.term = ? " + 
            "   and topics.docno=? " + 
            "   and topic_terms.topic = topics.topic " + 
            "   and topic_time.time = ? " + 
            "   and topic_time.topic = topics.topic";

    PreparedStatement topicPs = null;
    PreparedStatement topicTimePs = null;

 
    public void open(String path, boolean readOnly) throws SQLException, ClassNotFoundException
    {
        Class.forName("org.h2.Driver");
        if (readOnly)
            path += ";ACCESS_MODE_DATA=r;CACHE_SIZE=1048576";
        con = DriverManager.getConnection("jdbc:h2:" + path);
        
        topicPs = con.prepareStatement(TOPIC_SQL);
        topicTimePs = con.prepareStatement(TOPIC_TIME_SQL);
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

    public double getTermProbability(String docno, String term, int time)
    {
        double pr = 0;
        
        try
        {        
            topicTimePs.setString(1, term);
            topicTimePs.setString(2, docno);
            topicTimePs.setInt(3, time);
            ResultSet rs = topicTimePs.executeQuery();
            while (rs.next()) {
                Double topicProb = rs.getDouble(2);
                Double termProb = rs.getDouble(3);
                Double timeProb = rs.getDouble(4);
                pr += topicProb * termProb * timeProb;
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
        if (topicTimePs != null)
            topicTimePs.close();
    }
 
}
