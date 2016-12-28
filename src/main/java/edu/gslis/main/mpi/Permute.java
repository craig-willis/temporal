package edu.gslis.main.mpi;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Sets;
public class Permute {

	public static void main(String[] args) {
		
		Set<Double> i1 = Sets.newHashSet(1.0,2.0,3.0);
		Set<Double> i2 = Sets.newHashSet(0.1,0.2,0.3);
		Set<Double> i3 = Sets.newHashSet(4.0,5.0,6.0);
		
		List<Set<Double>> l = new ArrayList<Set<Double>>();
		l.add(i1);
		l.add(i2);
		l.add(i3);
		
		Set<List<Double>> s = Sets.cartesianProduct(l);
		for (List<Double> l2: s) {
			for (double d: l2) {
				System.out.print(" " + d);
			}
			System.out.println();
		}
	}
	
}
