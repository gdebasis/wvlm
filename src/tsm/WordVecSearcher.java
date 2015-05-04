/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tsm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.payloads.AveragePayloadFunction;
import org.apache.lucene.search.payloads.PayloadFunction;
import org.apache.lucene.search.similarities.AfterEffectB;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.BasicModelBE;
import org.apache.lucene.search.similarities.DFRSimilarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.MultiSimilarity;
import org.apache.lucene.search.similarities.NormalizationH1;
import org.apache.lucene.search.similarities.PerFieldSimilarityWrapper;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;
import trec.TRECQuery;
import trec.TRECQueryParser;


/**
 *
 * @author Debasis
 */


class CombinationSimilarity extends PerFieldSimilarityWrapper {

    Properties prop;
    float lambda;
    
    public CombinationSimilarity(Properties prop) {
        this.prop = prop;
        lambda = Float.parseFloat(prop.getProperty("retrieve.lambda", "0.3"));
    }

    @Override
    public Similarity get(String fieldName) {
        Similarity[] sims = {
            new LMJelinekMercerSimilarity(lambda),
            new LMDirichletSimilarity(),
            new BM25Similarity(),
            //new DFRSimilarity(new BasicModelBE(), new AfterEffectB(), new NormalizationH1()),
        };

        if (fieldName.equals(WordVecIndexer.FIELD_BAG_OF_WORDS)) {
            return Boolean.parseBoolean(prop.getProperty("retrieve.use_wv")) ?
                new MultiSimilarity(sims) : new LMJelinekMercerSimilarity(lambda);
        }
        else {
            return new GeneralizedLMSimilarity();
        }
    }    
}

public class WordVecSearcher {
    
    IndexReader reader;
    IndexSearcher searcher;
    Properties prop;   // retrieve.properties
    Properties iprop; // init.properties
    int numWanted;      // number of result to be retrieved
    HashMap<Integer, Float> docScorePredictionMap;
    boolean isSupervised;
    WordVecIndexer wvIndexer;
    String runName;     // name of the run
    float lambda, mu, alpha, beta;   // mu < 1; lambda + alpha < 1
    
    public WordVecSearcher(String ipropFile, String rpropFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(rpropFile));

        iprop = new Properties();
        iprop.load(new FileReader(ipropFile));

        wvIndexer = new WordVecIndexer(ipropFile);
        //String index_dir = prop.getProperty("index");
        String wvIndex_dir = prop.getProperty("wv.index");

        System.out.println("Running queries against index: " + wvIndex_dir);
        try {
            File indexDir;
            indexDir = new File(wvIndex_dir);
            reader = DirectoryReader.open(FSDirectory.open(indexDir));
            searcher = new IndexSearcher(reader);

            runName = prop.getProperty("retrieve.runname", "word2vec");
            searcher.setSimilarity(new CombinationSimilarity(prop));
            numWanted = Integer.parseInt(prop.getProperty("retrieve.num_wanted", "1000"));
            
            lambda = Float.parseFloat(prop.getProperty("retrieve.lambda"));
            mu = Float.parseFloat(prop.getProperty("retrieve.mu", "0.0"));
            alpha = Float.parseFloat(prop.getProperty("retrieve.alpha"));
            beta = Float.parseFloat(prop.getProperty("retrieve.beta"));
            
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    List<TRECQuery> constructQueries() throws Exception {
        String queryFile = prop.getProperty("query.file");
        TRECQueryParser parser = new TRECQueryParser(queryFile, this);
        parser.parse();
        return parser.queries;
    }    
    
    public void retrieveAll() throws Exception {
        ScoreDoc[] hits = null;
        TopDocs topDocs = null;
        
        String resultsFile = prop.getProperty("retrieve.wv_results_file");
        FileWriter fw = new FileWriter(resultsFile);

        List<TRECQuery> queries = constructQueries();
        for (TRECQuery query : queries) {
            TopScoreDocCollector collector = TopScoreDocCollector.create(numWanted, true);
            Query luceneQry;
            luceneQry = query.getWVQuery(lambda, mu, alpha, beta, wvIndexer.getAnalyzer());
            
            System.out.println(luceneQry);

            searcher.search(luceneQry, collector);
            topDocs = collector.topDocs();
            hits = topDocs.scoreDocs;
            if(hits == null)
                System.out.println("Nothing found");

            System.out.println("Retrieved results for query: " + query.id);
            StringBuffer buff = new StringBuffer();
            int hits_length = hits.length;
            System.out.println("Retrieved Length: " + hits_length);
            for (int i = 0; i < hits_length; ++i) {
                int docId = hits[i].doc;
                Document d = searcher.doc(docId);
                buff.append(query.id).append("\tQ0\t").
                    append(d.get(WordVecIndexer.FIELD_ID)).append("\t").
                    append((i)).append("\t").
                    append(hits[i].score).append("\t").
                    append(runName).append("\n");                
            }
            fw.write(buff.toString());
        }
        fw.close();
        System.out.println("The result is saved in: "+resultsFile);
    }
    
    public void closeReader() throws IOException {
        reader.close();        
    }
    
    public static void main(String[] args) {
        if (args.length < 2) {
            args = new String[2];
            args[0] = "tweet.index.properties";
            args[1] = "tweet.retrieve.properties";
        }
        
        try {
            WordVecSearcher searcher = new WordVecSearcher(args[0], args[1]);
            
            searcher.retrieveAll();
            searcher.closeReader();
        }
        
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
