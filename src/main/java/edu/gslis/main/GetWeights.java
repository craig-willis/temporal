package edu.gslis.main;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;


public class GetWeights {

	public static void main(String[] args) throws Exception{
		List<String> lines = FileUtils.readLines(new File("weights.txt"));
		Set<List<Double>> sets = new HashSet<List<Double>>();
		for (String line: lines) {
			String[] fields = line.split(",");
			List<Double> list = new ArrayList<Double>();
			for (String field: fields) {
				double d = Double.parseDouble(field);
				list.add(d);
			}
			Collections.sort(list);;
			sets.add(list);
		}
		
		for (List<Double> list: sets) {
			String w = "";
			for (Double d: list) {
				w += d + " ";				
			}
			System.out.println(w);
		}
	}
}
