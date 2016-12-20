package edu.gslis.indexes.temporal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class TemporalLDAIndex 
{
    int NUM_TOPICS = 800;
    
    Map<Integer, Map<String, Double>> termTopicProbs = 
            new HashMap<Integer, Map<String, Double>>();
    Map<Integer, Map<String, Double>> docTopicProbs = 
            new HashMap<Integer, Map<String, Double>>();
    
    public void load(String inputPath) throws Exception 
    {
        long start = System.currentTimeMillis();
    
        File[] files = new File(inputPath).listFiles();
        for (File file: files) {
            // 0-term-topics.txt.gz
            if (file.getName().contains("term-topics.txt")) {
                int bin = Integer.parseInt(file.getName().substring(0, file.getName().indexOf("-")));
                BufferedReader br2 = new BufferedReader(
                        new InputStreamReader(new GZIPInputStream(
                                new FileInputStream(file.getAbsolutePath())), "UTF-8"));
                
                Map<String, Double> termTopicProb = termTopicProbs.get(bin);
                if (termTopicProb == null)
                    termTopicProb = new HashMap<String, Double>();


                String line;
                while ((line = br2.readLine()) != null) {
                    String[] fields = line.split(",");
                    int topic = Integer.parseInt(fields[0].trim());
                    String term = fields[1].trim();
                    double prob = Double.valueOf(fields[4].trim());    
                                        
                    termTopicProb.put(topic + " " + term, prob);                    
                }
                termTopicProbs.put(bin, termTopicProb);
                br2.close();        
            } else if (file.getName().contains("doc-topics.txt")) {
                // 17-doc-topics.txt.gz
                BufferedReader br1 = new BufferedReader(
                        new InputStreamReader(new GZIPInputStream(
                                new FileInputStream(file.getAbsolutePath())), "UTF-8"));
                int bin = Integer.parseInt(file.getName().substring(0, file.getName().indexOf("-")));
                
                Map<String, Double> docTopicProb = docTopicProbs.get(bin);
                if (docTopicProb == null)
                    docTopicProb = new HashMap<String, Double>();

                String line;
                long i=0;
                long last = System.currentTimeMillis();
                while ((line = br1.readLine()) != null) {
                    if (i%10000 == 0) 
                        System.out.print(".");
                    if (i%100000 == 0) {
                        System.out.println(bin + " " + i + "(" + (System.currentTimeMillis() - last) + ")");
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
                docTopicProbs.put(bin, docTopicProb);

            }
        }

        
        long end = System.currentTimeMillis();
        System.out.println((end -start) + " ms");
    }
    
    public double getTermProbability(String docno, String term, int bin) {
        
        double pr = 0;

        Map<String, Double> docTopicProb = docTopicProbs.get(bin);
        Map<String, Double> termTopicProb = termTopicProbs.get(bin);
        if (docTopicProb != null && termTopicProb != null)
        {       
            for (int i=0; i<NUM_TOPICS; i++) 
            {
                double topicProb = 0;            
                if ( docTopicProb.get(docno + " " + i) != null)
                    topicProb = docTopicProb.get(docno + " " + i);
                double termProb = 0;
                if (termTopicProb.get(i + " " + term) != null) 
                     termProb = termTopicProb.get(i + " " + term);
                if (topicProb > 0 && termProb > 0)
                    pr += topicProb * termProb;
            }
        }
        return pr;   
    }    
}
