package edu.gslis.main;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;

/** 
 * Print all docnos from index to file
 */
public class GetDocnos 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( GetDocnos.class.getCanonicalName(), options );
            return;
        }
        String indexPath = cl.getOptionValue("index");
        String outputPath = cl.getOptionValue("output");
        IndexWrapper index =  IndexWrapperFactory.getIndexWrapper(indexPath);

        FileOutputStream fos= new FileOutputStream(outputPath);
 
        OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8");
        int numDocs = (int)index.docCount();
        for (int docid=1; docid<numDocs; docid++) 
        {
            if (docid > 1 && docid % 30000  == 0) {
                System.err.println(docid + "...");
            }
            String docno = index.getDocNo(docid);
            writer.write(docno   + "\n");
        }

        writer.close();
    }
        
        
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "Path to input index");
        options.addOption("output", true, "Output time series index");        
        return options;
    }

}
