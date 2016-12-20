package edu.gslis.indexes.temporal.lda;

import java.io.BufferedReader;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Interface to an H2 DB containing two tables:
 *  topics: docno, topic, probability
 *  topic_terms: term, topic, probability
 */
public class LDAIndex {
    
    Connection con = null;

    static String TOPIC_SQL = "select topics.topic, topics.prob, topic_terms.prob " + 
            "   from topics, topic_terms " + 
            "   where topic_terms.term = ? and topics.docno=? and topic_terms.topic = topics.topic ";

    /*
    static String TOPIC_TIME_SQL = "select topics.topic, topics.prob, topic_terms.prob, topic_time.prob " + 
            "   from topics, topic_terms, topic_time " + 
            "   where topic_terms.term = ? " + 
            "   and topics.docno=? " + 
            "   and topic_terms.topic = topics.topic " + 
            "   and topic_time.time = ? " + 
            "   and topic_time.topic = topics.topic";
            */

    //PreparedStatement topicPs = null;

 
    public void open(String path, boolean readOnly) throws SQLException, ClassNotFoundException
    {
        long cacheSize = 32*1024*1024; // 32 GB
        Class.forName("org.h2.Driver");
        if (readOnly)
            path += ";ACCESS_MODE_DATA=r;CACHE_SIZE=" + cacheSize;
        con = DriverManager.getConnection("jdbc:h2:" + path);
        
        //topicPs = con.prepareStatement(TOPIC_SQL);
    }
    
    Map<String, Double> termTopicProb = new HashMap<String, Double>();
    Map<String, Double> docTopicProb = new HashMap<String, Double>();
    public void load(String docTopicsPath, String termTopicsPath) throws Exception {
        long start = System.currentTimeMillis();
        
        BufferedReader br1 = new BufferedReader(
                new InputStreamReader(new GZIPInputStream(new FileInputStream(docTopicsPath)), "UTF-8"));
        
        String line;
        long i=0;
        long last = System.currentTimeMillis();
        while ((line = br1.readLine()) != null) {
            if (i%100000 == 0) 
                System.out.print(".");
            if (i%1000000 == 0) {
                System.out.println(i + "(" + (System.currentTimeMillis() - last) + ")");
                last = System.currentTimeMillis();  
            }
            String[] fields = line.split(",");
            String docid = fields[0].trim();
            int topic = Integer.parseInt(fields[1].trim());
            double prob = Double.valueOf(fields[2].trim());  
            docTopicProb.put(docid + " " + topic, prob);
            i++;
        }
        br1.close();
        
        BufferedReader br2 = new BufferedReader(
                new InputStreamReader(new GZIPInputStream(new FileInputStream(termTopicsPath)), "UTF-8"));

        while ((line = br2.readLine()) != null) {
            String[] fields = line.split(",");
            int topic = Integer.parseInt(fields[0].trim());
            String term = fields[1].trim();
            double prob = Double.valueOf(fields[4].trim());    
            termTopicProb.put(topic + " " + term, prob);
        }
        br2.close();        
        long end = System.currentTimeMillis();
        System.out.println((end -start) + " ms");
    }
    
    public double getTermProbability2(String docno, String term) {
        
        double pr = 0;
   
        for (int i=0; i<800; i++) {
            double topicProb = 0;            
            if ( docTopicProb.get(docno + " " + i) != null)
                 topicProb = docTopicProb.get(docno + " " + i);
            double termProb = 0;
            if (termTopicProb.get(i + " " + term) != null) 
                 termProb = termTopicProb.get(i + " " + term);
            if (topicProb > 0 && termProb > 0)
                pr += topicProb * termProb;
        }
        return pr;   
    }
    
    public double getTermProbability(String docno, String term)
    {       
        double pr = 0;
        
        try
        {
        
           // long start = System.currentTimeMillis();
            PreparedStatement topicPs = con.prepareStatement(TOPIC_SQL);
            topicPs.setString(1, term);
            topicPs.setString(2, docno);
            ResultSet rs = topicPs.executeQuery();
            while (rs.next()) {
                Double topicProb = rs.getDouble(2);
                Double termProb = rs.getDouble(3);
                pr += topicProb * termProb;
            }
            rs.close();
            topicPs.close();

           // long end = System.currentTimeMillis();
           // System.out.println(docno + "," + term + "=" + (end-start) + " ms");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return pr;        
    }

    /*
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
    */
    
    
    public void close() throws SQLException {
        if (con != null)
            con.close();
        //if (topicPs != null)
        //    topicPs.close();
//        if (topicTimePs != null)
//            topicTimePs.close();
    }
 
}
