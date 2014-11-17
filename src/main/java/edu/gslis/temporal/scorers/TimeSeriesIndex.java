package edu.gslis.temporal.scorers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Interface to an underlying H2 DB containing term time series information for a collection.
 *
 */
public class TimeSeriesIndex {


    Connection con = null;

    public void open(String path, boolean readOnly) throws SQLException, ClassNotFoundException
    {
        Class.forName("org.h2.Driver");
        if (readOnly)
            path += ";ACCESS_MODE_DATA=r";
        con = DriverManager.getConnection("jdbc:h2:" + path);
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
    
    public void initNorm(String type) throws SQLException
    {
        Statement stat = con.createStatement();
        
        String sql = "drop table norm_" + type;
        stat.execute(sql);

        sql = "create table norm_" + type + " (bin int, const double)";
        stat.execute(sql);   
       
        
        stat.close();
    }
    
    public void index() throws SQLException
    {
        Statement stat = con.createStatement();
        stat.execute("create index idx1 on series(term)");
        stat.close();
    }
    
    public void indexNorm(String type) throws SQLException
    {
        Statement stat = con.createStatement();
        stat.execute("create index idx2 on norm_" + type + "(term)");
        stat.close();
    }
    
    
    public void addNorm(String type, int bin, double cnst) throws SQLException {
        String sql = "insert into norm_" + type + " values (?, ?)";
        PreparedStatement stat = con.prepareStatement(sql);
        stat.setInt(1, bin);
        stat.setDouble(2, cnst);
        
        stat.execute();

        stat.close();
    }
    
    public double getNorm(String type, int bin) throws SQLException {
        String sql = "select const from norm_" + type + " where bin = ?";
        PreparedStatement stat = con.prepareStatement(sql);
        stat.setInt(1, bin);
        ResultSet rs = stat.executeQuery();
        
        double val = 0;
        if (rs.next())
            val = rs.getDouble(1);
        stat.close();
        return val;
        
    }
    public void add(String term, long[] counts) throws SQLException {
        String sql = "insert into series values ('" + term + "'";
        for (long c: counts) {
            sql += "," + c;
        }
        sql += ")";
        Statement stat = con.createStatement();
        stat.execute(sql);
        stat.close();
    }
    
    
    public List<String> terms() throws SQLException
    {
        List<String> terms = new ArrayList<String>();
        String sql = "select term from series";
        Statement stat = con.createStatement();
        
        ResultSet rs = stat.executeQuery(sql);
        
        while (rs.next()) {
            String term = rs.getString(1);
            terms.add(term);
        }
        stat.close();
        
        return terms;
    }
    public double[] get(String term) throws SQLException
    {
        String sql = "select * from series where term = ?";
        PreparedStatement stat = con.prepareStatement(sql);
        
        stat.setString(1, term);
                
        ResultSet rs = stat.executeQuery();
        
        ResultSetMetaData rsmd = rs.getMetaData();
        int numCols = rsmd.getColumnCount();
        
        double[] series = new double[numCols-1];
        
        if (rs.next()) {
            for (int i=0; i<numCols-1; i++) {
                series[i] = rs.getInt(i+2);
            }
        }
        stat.close();
        
        return series;
    }
    public int get(String term, int bin) throws SQLException 
    {
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
