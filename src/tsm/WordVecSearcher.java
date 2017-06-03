/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tsm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.payloads.AveragePayloadFunction;
import org.apache.lucene.search.payloads.PayloadFunction;
import org.apache.lucene.store.FSDirectory;
import trec.TRECQuery;
import trec.TRECQueryParser;


/**
 *
 * @author Debasis
 */

public class WordVecSearcher {
    
    IndexReader reader;
    IndexSearcher searcher;
    Properties prop;
    int numWanted;
    HashMap<Integer, Float> docScorePredictionMap;
    boolean isSupervised;
    
    public WordVecSearcher(String propFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));
        
        String index_dir = prop.getProperty("index");
        
        System.out.println("Running queries against index: " + index_dir);
        boolean useTopicWeights = Boolean.parseBoolean(
                prop.getProperty("retrieve.use_topics", "true"));
        try {
            File indexDir = new File(index_dir);
            reader = DirectoryReader.open(FSDirectory.open(indexDir));
            searcher = new IndexSearcher(reader);

            searcher.setSimilarity(new GeneralizedLMSimilarity(useTopicWeights));
            
            numWanted = Integer.parseInt(prop.getProperty("retrieve.num_wanted", "1000"));
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }        
    }

    List<TRECQuery> constructQueries() throws Exception {        
        String queryFile = prop.getProperty("query.file");
        TRECQueryParser parser = new TRECQueryParser(queryFile);        
        return parser.queries;
    }    
    
    public void retrieveAll() throws Exception {
        ScoreDoc[] hits;
        TopDocs topDocs;
        String runName = prop.getProperty("retrieve.runname", "baseline");
        float lambda = Float.parseFloat(prop.getProperty("lambda", "0.4"));
        float mu = Float.parseFloat(prop.getProperty("mu", "0.4"));
        
        String resultsFile = prop.getProperty("retrieve.results_file");        
        FileWriter fw = new FileWriter(resultsFile);
        
        List<TRECQuery> queries = constructQueries();
        
        for (TRECQuery query : queries) {

            TopScoreDocCollector collector = TopScoreDocCollector.create(numWanted, true);
            searcher.search(query.getQuery(lambda, mu), collector);
            topDocs = collector.topDocs();
            hits = topDocs.scoreDocs;
            
            System.out.println("Retrieved results for query " + query.id);
            StringBuffer buff = new StringBuffer();            
            for (int i = 0; i < hits.length; ++i) {
                int docId = hits[i].doc;
                Document d = searcher.doc(docId);
                buff.append(query.id).append("\tQ0\t").
                        append(d.get(WordVecIndexer.FIELD_BAG_OF_VECS)).append("\t").
                        append((i+1)).append("\t").
                        append(hits[i].score).append("\t").
                        append(runName).append("\n");                
            }
            fw.write(buff.toString());
        }
        fw.close();        
        reader.close();
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            args = new String[1];
            args[0] = "retrieve.properties";
        }
        try {
            WordVecSearcher searcher = new WordVecSearcher(args[0]);
            searcher.retrieveAll();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
