package edu.gslis.indexes;

import java.io.File;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;

import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;

/**
 * Given a file of query ids, create a subset topic file
 *
 */
public class SubsetQueries 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( SubsetQueries.class.getCanonicalName(), options );
            return;
        }
        String topicsPath = cl.getOptionValue("topics");
        String idsPath = cl.getOptionValue("ids");
        
        List<String> ids = FileUtils.readLines(new File(idsPath));

        GQueries queries = null;
        if (topicsPath.endsWith("indri")) 
            queries = new GQueriesIndriImpl();
        else
            queries = new GQueriesJsonImpl();
        queries.read(topicsPath);

        System.out.println("<parameters>");
        Iterator<GQuery> it = queries.iterator();
        while (it.hasNext()) 
        {
            GQuery query = it.next();
            if (ids.contains(query.getTitle())) {
 
                System.out.println("<query>");
                System.out.println("<number>" + query.getTitle() + "</number>");
                System.out.println("<text>" + query.getText() + "</text>");                
                System.out.println("</query>");
                
            }
 
       }
       System.out.println("</parameters>");

    }
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("topics", true, "Path to topics");
        options.addOption("ids", true, "Path to file containing ids");
        return options;
    }

}
