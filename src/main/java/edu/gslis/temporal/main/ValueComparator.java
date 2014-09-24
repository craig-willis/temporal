package edu.gslis.temporal.main;

import java.util.Comparator;
import java.util.Map;


public class ValueComparator implements Comparator<Long> {

    Map<Long, Integer> base;
    public ValueComparator(Map<Long, Integer> base) {
        this.base = base;
    }

    public int compare(Long a, Long b) {
        if (base.get(a) >= base.get(b)) {
            return -1;
        } else {
            return 1;
        }
    }
}