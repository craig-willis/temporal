package edu.gslis.main;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.math3.linear.OpenMapRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.tika.io.IOUtils;

/**
 * Work in progress. QPP pipeline
 */
public class LOOCVResult {

	String query;
	String params;
	double value;
	
	public LOOCVResult(String query, String params, double value) {
		this.query = query;
		this.params = params;
		this.value = value;
	}
	
	
	public String getQuery() {
		return query;
	}


	public String getParams() {
		return params;
	}


	public double getValue() {
		return value;
	}


	public static Map<String, LOOCVResult> readResults(String path) throws IOException {
		
		Map<String, LOOCVResult> results = new TreeMap<String, LOOCVResult>();
		
		List<String> lines = IOUtils.readLines(new FileInputStream(path));
		
		
		for (String line: lines) {
			String[] fields = line.split("\\s*");
			LOOCVResult result = new LOOCVResult(fields[0], fields[1], Double.parseDouble(fields[2]));
			results.put(fields[0], result);		
		}
		
		return results;
	}
}
