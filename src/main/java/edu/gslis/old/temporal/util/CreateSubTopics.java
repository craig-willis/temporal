package edu.gslis.old.temporal.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;


public class CreateSubTopics {

    public static void main(String[] args) throws IOException, ParseException {

        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (!cl.hasOption("t") || !cl.hasOption("l") || cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( CreateSubTopics.class.getCanonicalName(), options );
            return;
        }
        
        String outputPath = cl.getOptionValue("o");        
        Writer writer = null;
        if (! StringUtils.isEmpty(outputPath)) {
            File outputFile = new File(outputPath);
            if (!outputFile.getParentFile().exists())
                outputFile.getParentFile().mkdirs();
            writer = new FileWriter(new File(outputPath));
        }
        else
            writer = new OutputStreamWriter(System.out);
                    
        String topicsPath = cl.getOptionValue("t");
        String subsetPath = cl.getOptionValue("l");
        
        List<String> topicIds = FileUtils.readLines(new File(subsetPath));
        
        GQueries queries = new GQueriesJsonImpl();
        queries.read(topicsPath);

        GQueries subset = new GQueriesJsonImpl();

        Iterator<GQuery> it = queries.iterator();
 
        while (it.hasNext()) {
            GQuery query = it.next();
            if (topicIds.contains(query.getTitle())) {
                subset.addQuery(query);
            }
        }
        
        writer.write(subset.toString());
        writer.close();        
       
    }
    
    
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("t", true, "Path to topics json file");
        options.addOption("l", true, "Path to list of subset topics");
        options.addOption("o", true, "Path to output file (default: stdout)");
        options.addOption("help", false, "Print this help message");

        return options;
    }
}
