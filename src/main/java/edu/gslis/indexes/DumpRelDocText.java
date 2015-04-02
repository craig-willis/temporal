package edu.gslis.indexes;

import java.io.FileWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.trec.util.Qrels;

/** 
 * Dump all documents in an Indri index
 */
public class DumpRelDocText 
{
    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( DumpRelDocText.class.getCanonicalName(), options );
            return;
        }
        String indexPath = cl.getOptionValue("index");
        String outputPath = cl.getOptionValue("output");
        String qrelsPath = cl.getOptionValue("qrels");
        String topic = cl.getOptionValue("topic");
        IndexWrapper index =  IndexWrapperFactory.getIndexWrapper(indexPath);
        Map<String, Qrels> qrelsMap = Qrels.readQrels(qrelsPath);

        Qrels qrels = qrelsMap.get(topic);
        
        FileWriter writer = new FileWriter(outputPath);
 
        DateFormat df = new SimpleDateFormat("MM/dd/yyyy");
        Map<String, Integer> relDocs = qrels.getDocs();
        for (String docno: relDocs.keySet()) {
            int relLevel = relDocs.get(docno);
            if (relLevel > 0) {
                SearchHit hit = index.getSearchHit(docno,  null);
                String text = index.getDocText(hit.getDocID());
                writer.write(text + "\n");
            }
        }

        writer.close();
    }
        
        
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("index", true, "Path to input index");
        options.addOption("output", true, "Path to output file");        
        options.addOption("qrels", true, "Path to qrels");        
        options.addOption("topic", true, "Topic");        
        return options;
    }

}
