    package edu.gslis.indexes;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 * Interface to an underlying H2 DB containing term time series information for a collection.
 *
 */
public class TimeSeriesIndex {


    Connection con = null;
    String format = null;
    int numBins = 0;
    FileWriter writer = null;
    Map<String, double[]> timeSeriesMap = new HashMap<String, double[]>();
    boolean readOnly = false;

    public void open(String path, boolean readOnly, String format) 
            throws SQLException, ClassNotFoundException, IOException
    {
        this.format = format;
        this.readOnly = readOnly;
        
        if (format.equals("h2")) {
            Class.forName("org.h2.Driver");
            if (readOnly)
                path += ";ACCESS_MODE_DATA=r";
            con = DriverManager.getConnection("jdbc:h2:" + path);
        } else {
            
            if (readOnly) {
                load(path);
            } else {
                writer = new FileWriter(path);
            }
        }
    }
    
    public void load(String path) throws IOException {
        BufferedReader br = new BufferedReader(
                new InputStreamReader(new GZIPInputStream(new FileInputStream(path)), "UTF-8"));
        String line;
        int j=0;
        System.out.println("Loading " + path);

        while ((line = br.readLine()) != null) {
            if (j%10000 == 0) 
                System.out.print(".");
            if (j%100000 == 0) 
                System.out.print(j + "\n");

            String[] fields = line.split(",");
            String term = fields[0];
            double[] counts = new double[fields.length-1];
            for (int i=1; i<fields.length; i++) {
                counts[i-1] = Double.valueOf(fields[i]);
            }
            timeSeriesMap.put(term, counts);
            j++;
        }
        System.out.println("Done");
                
        br.close();
    }
    
    public void init(int numBins) throws SQLException
    {
        this.numBins = numBins;
        if (format.equals("h2")) {
            Statement stat = con.createStatement();
    
            String sql = "create table series (term varchar(255) ";
            for (int i=0; i<numBins; i++) {
                sql += ",bin"+ i + " int";
            }
            sql += ")";
            
            stat.execute(sql);
    
            stat.close();
        }
    }
    
    public void initNorm(String type) throws SQLException
    {
        if (format.equals("h2")) {
            Statement stat = con.createStatement();
            
            String sql = "drop table norm_" + type;
            stat.execute(sql);
    
            sql = "create table norm_" + type + " (bin int, const double)";
            stat.execute(sql);   
           
            
            stat.close();
        }
    }
    
    public void index() throws SQLException
    {
        if (format.equals("h2")) {

            Statement stat = con.createStatement();
            stat.execute("create index idx1 on series(term)");
            stat.close();
        }
    }
    
    public void indexNorm(String type) throws SQLException
    {
        if (format.equals("h2")) {
    
            Statement stat = con.createStatement();
            stat.execute("create index idx2 on norm_" + type + "(term)");
            stat.close();
        }
    }
    
    
    public void addNorm(String type, int bin, double cnst) throws SQLException {
        if (format.equals("h2")) {
    
            String sql = "insert into norm_" + type + " values (?, ?)";
            PreparedStatement stat = con.prepareStatement(sql);
            stat.setInt(1, bin);
            stat.setDouble(2, cnst);
            
            stat.execute();
    
            stat.close();
        }
    }
    
    public double getNorm(String type, int bin) throws SQLException {
        double val = 0;
        if (format.equals("h2")) {    
            String sql = "select const from norm_" + type + " where bin = ?";
            PreparedStatement stat = con.prepareStatement(sql);
            stat.setInt(1, bin);
            ResultSet rs = stat.executeQuery();
            
            if (rs.next())
                val = rs.getDouble(1);
            stat.close();
        }
        return val;        
    }
    public void add(String term, long[] counts) throws SQLException, IOException {
        if (format.equals("h2")) {
            String sql = "insert into series values ('" + term + "'";
            for (long c: counts) {
                sql += "," + c;
            }
            sql += ")";
            Statement stat = con.createStatement();
            stat.execute(sql);
            stat.close();
        } else {
            if (!readOnly) {
                writer.write(term);
                for (long count: counts) {
                    writer.write("," + count);
                }
                writer.write("\n");
            }
        }
    }
    
    
    public List<String> terms() throws SQLException
    {
        List<String> terms = new ArrayList<String>();

        if (format.equals("h2")) {
            String sql = "select term from series";
            Statement stat = con.createStatement();
            
            ResultSet rs = stat.executeQuery(sql);
            
            while (rs.next()) {
                String term = rs.getString(1);
                terms.add(term);
            }
            stat.close();
        } else {
            terms.addAll(timeSeriesMap.keySet());
        }
        
        return terms;
    }
    public double[] get(String term) throws SQLException
    {
        if (format.equals("h2")) {
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
        } else {
            return timeSeriesMap.get(term);
        }
        
    }
    public int get(String term, int bin) throws SQLException 
    {
        int freq = 0;
        if (format.equals("h2")) {
            String sql = "select bin" + bin + " from series where term = ?";
            PreparedStatement stat = con.prepareStatement(sql);
            
            stat.setString(1, term);
                    
            ResultSet rs = stat.executeQuery();
            if (rs.next())
                freq = rs.getInt(1);
            stat.close();
        } else {
            double[] counts = timeSeriesMap.get(term);
            freq = (int) counts[bin];
        }
        return freq;
    }
    
    public void close() throws SQLException {
        if (con!= null)
            con.close();
    }
    
    /**
     *    n(w, bin) - mean(w)  / sd(w)
     */
    public void shrink(String newpath) throws IOException {
        FileWriter writer = new FileWriter(newpath);
        Set<String> vocab = timeSeriesMap.keySet();
        
        DecimalFormat df = new DecimalFormat("###.####");
        double[] totals = timeSeriesMap.get("_total_");
        totals = new double[totals.length];
        for (String term: vocab) {
            
            if (term.equals("_total_"))
                continue;
            
            double[] counts = timeSeriesMap.get(term);
            if (counts.length < totals.length)
                continue;
            
            DescriptiveStatistics stats = new DescriptiveStatistics();
            for (double count: counts)
                stats.addValue(count);
            
            writer.write(term);
            for (int i=0; i<counts.length; i++) {
                double shrunk = (counts[i] - stats.getMean()) / stats.getStandardDeviation();
                if (shrunk < 0) 
                    shrunk = 0;
                writer.write("," + df.format(shrunk));
                
                totals[i] += shrunk;
            }
            writer.write("\n");           
        }
        
        writer.write("_total_");
        for (int i=0; i<totals.length; i++) {
            writer.write("," + df.format(totals[i]));
        }
        writer.write("\n");           

        writer.close();
    }

}
