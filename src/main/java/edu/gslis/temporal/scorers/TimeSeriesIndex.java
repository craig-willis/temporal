package edu.gslis.temporal.scorers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;


public class TimeSeriesIndex {


    Connection con = null;

    public void open(String path) throws SQLException, ClassNotFoundException
    {
        Class.forName("org.h2.Driver");
        con = DriverManager.getConnection("jdbc:h2:./" + path);
    }
    
    public void init(int numBins) throws SQLException
    {
        Statement stat = con.createStatement();

        String sql = "create table series (term varchar(255) ";
        for (int i=0; i<numBins; i++) {
            sql += ",bin"+ i + " int";
        }
        sql += ")";
        
        stat.execute(sql);
        stat.close();
    }
    
    public void index() throws SQLException
    {
        Statement stat = con.createStatement();
        stat.execute("create index idx on series(term)");
    }
    
    public void add(String term, long[] counts) throws SQLException {
        String sql = "insert into series values ('" + term + "'";
        for (long c: counts) {
            sql += "," + c;
        }
        sql += ")";
        Statement stat = con.createStatement();
        stat.execute(sql);
    }
    
    public double[] get(String term) throws SQLException
    {
        String sql = "select * from series where term = ?";
        PreparedStatement stat = con.prepareStatement(sql);
        
        stat.setString(1, term);
                
        ResultSet rs = stat.executeQuery();
        
        ResultSetMetaData rsmd = rs.getMetaData();
        int numCols = rsmd.getColumnCount();
        
        double[] series = new double[numCols];
        
        if (rs.next()) {
            for (int i=2; i<numCols; i++) {
                series[i] = rs.getInt(i);
            }
        }
        stat.close();
        
        return series;
    }
    public int get(String term, int bin) throws SQLException 
    {
        //System.out.println("get " + term + ", " + bin);
        String sql = "select bin" + bin + " from series where term = ?";
        PreparedStatement stat = con.prepareStatement(sql);
        
        stat.setString(1, term);
                
        ResultSet rs = stat.executeQuery();
        int freq = 0;
        if (rs.next())
            freq = rs.getInt(1);
        stat.close();
        
        return freq;
    }
    
    public void close() throws SQLException {
        con.close();
    }

}
