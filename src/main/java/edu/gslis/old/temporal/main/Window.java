package edu.gslis.old.temporal.main;

import java.util.LinkedHashMap;
import java.util.Map;

public class Window<K, V> extends LinkedHashMap<K, V> 
{

    private static final long serialVersionUID = -8598046148539215583L;
    
    int maxSize = 0;
    public Window(int maxSize) {
        this.maxSize = maxSize;
    }
    protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
        return size() > maxSize;
    }
}
