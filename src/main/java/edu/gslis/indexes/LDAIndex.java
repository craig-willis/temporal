package edu.gslis.indexes;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class LDAIndex {
    
    Connection con = null;

    //static String TOPIC_SQL = "select topic, prob from topics where docno = ?";
    //static String TERM_SQL =  "select prob from topic_terms where topic = ? and term=?";        

    static String TOPIC_SQL = "select topics.topic, topics.prob, topic_terms.prob " + 
            "   from topics, topic_terms " + 
            "   where topic_terms.term = ? and topics.docno=? and topic_terms.topic = topics.topic";
    PreparedStatement topicPs = null;
    //PreparedStatement termPs = null;
   
    Map<String, Double> docTopicProb = new HashMap<String, Double>();
    Map<String, Double> termTopicProb = new HashMap<String, Double>();

    public void open(String path, boolean readOnly) throws SQLException, ClassNotFoundException
    {
        Class.forName("org.h2.Driver");
        if (readOnly)
            path += ";ACCESS_MODE_DATA=r;CACHE_SIZE=1048576";
        con = DriverManager.getConnection("jdbc:h2:" + path);
        
        topicPs = con.prepareStatement(TOPIC_SQL);
        //termPs = con.prepareStatement(TERM_SQL);
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
                Integer topic = rs.getInt(1);
                Double topicProb = rs.getDouble(2);
                Double termProb = rs.getDouble(3);
                pr += topicProb * termProb;
            }
            rs.close();
            
            /*
            topicPs.setString(1, docno);
            ResultSet rs1 = topicPs.executeQuery();
            Map<Integer, Double> docTopicProbs = new HashMap<Integer, Double>();
            while (rs1.next()) {
                Integer topic = rs1.getInt(1);
                Double prob = rs1.getDouble(2);
                docTopicProbs.put(topic, prob);
            }
            rs1.close();
            
            for (int topic: docTopicProbs.keySet()) {
                double docTopicProb = docTopicProbs.get(topic);
                
                termPs.setInt(1, topic);
                termPs.setString(2, term);
                ResultSet rs = termPs.executeQuery();
                while (rs.next()) {
                    double termTopicProb = rs.getDouble(1);
                    termProb += docTopicProb*termTopicProb;
                }
                rs.close();        
            }
            */
        } catch (Exception e) {
            e.printStackTrace();
        }
        return pr;        
    }
    
    public void close() throws SQLException {
        if (con != null)
            con.close();
        //termPs.close();
        if (topicPs != null)
            topicPs.close();
    }
 
}
