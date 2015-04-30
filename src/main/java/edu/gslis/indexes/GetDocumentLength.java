package edu.gslis.indexes;

import java.io.FileWriter;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

/**
 * Calculate the p(D \vert \theta_T)
 *
 */
public class GetDocumentLength 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( GetDocumentLength.class.getCanonicalName(), options );
            return;
        }
        String indexPath = cl.getOptionValue("index");
        String outputPath = cl.getOptionValue("output");
        FileWriter output = new FileWriter(outputPath);
        IndexWrapper index =  IndexWrapperFactory.getIndexWrapper(indexPath);
        
        int numDocs = (int)index.docCount();
        for (int docid=1; docid<numDocs; docid++) 
        {

            String docno = index.getDocNo(docid);
            double len = index.getDocLength(docid);
            output.write(docno + "," + len  + "\n");
        }
        output.close();
    }
    

        
        
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "Path to input index");
        options.addOption("output", true, "Output path");
        return options;
    }

}
