package edu.gslis.indexes;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

public class ReadTrecTopics {

    public static void main(String[] args) throws Exception {
        readTopics("topics/src/topics.301-350");
    }
    
    public static void readTopics(String path) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(path));
        String line;
        String field = "";
        String value = "";
        Map<String, String> fields = new HashMap<String, String>();
        while ((line = br.readLine()) != null) {

            if (line.startsWith("<")) {
                fields.put(field, value.replaceAll("  *", " "));
                if (line.startsWith("</top>")) {    
                    System.out.println(
                            fields.get("num") + "\n\t Title: " + 
                            fields.get("title").replaceAll("Title:",  "").length() + "\n\t Description: " +  
                            fields.get("desc").replaceAll("Description:",  "").length() + "\n\t Narrative: " + 
                            fields.get("narr").replaceAll("Narrative:",  "").length() + "\n"
                            
                            );
                }
                else if (!line.startsWith("<top>")) {
                    field = line.substring(1, line.indexOf(">"));
                    value = line.substring(line.indexOf(">") + 1, line.length());
                }
            }
            else {
                value += " " + line;
            }
        }
        br.close();
    }
}
