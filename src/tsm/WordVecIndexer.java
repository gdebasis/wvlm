/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tsm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.payloads.DelimitedPayloadTokenFilter;
import org.apache.lucene.analysis.payloads.FloatEncoder;
import org.apache.lucene.analysis.payloads.NumericPayloadTokenFilter;
import org.apache.lucene.analysis.payloads.PayloadEncoder;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.standard.UAX29URLEmailTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;
import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.*;
import org.tartarus.snowball.ext.PorterStemmer;
import static tsm.WordVecIndexer.FIELD_BAG_OF_WORDS;
import static tsm.WordVecIndexer.FIELD_ID;
import static tsm.WordVecIndexer.PAYLOAD_DELIM;


/**
 *
 * @author Debasis
 */

class TermFreq {
    String term;
    float ntf_d;  // document component

    public TermFreq(String term) {
        this.term = term;
    }
}

class PayloadAnalyzer extends Analyzer {
    private PayloadEncoder encoder;
    
    public PayloadAnalyzer() {
        this.encoder = new FloatEncoder();
    }

    @Override
    protected Analyzer.TokenStreamComponents createComponents(String fieldName, Reader reader) {
        Tokenizer source = new WhitespaceTokenizer(Version.LUCENE_4_9, reader);
        TokenStream filter = new DelimitedPayloadTokenFilter(source, WordVecIndexer.PAYLOAD_DELIM, encoder);
        return new Analyzer.TokenStreamComponents(source, filter);
    }
}

public class WordVecIndexer {
    Properties prop;
    File indexDir;
    File wvIndexDir;
    WordVecs wordvecs;
    IndexWriter writer;
    PerFieldAnalyzerWrapper wrapper;
    int indexingPass;
    List<String> stopwords;
    
    static final public String FIELD_ID = "id";
    static final public String FIELD_TIME = "time";
    static final public String FIELD_BAG_OF_WORDS = "words";  // Baseline
    static final public String FIELD_P_WVEC_D = "wv_d";
    static final public String FIELD_P_WVEC_C = "wv_c";
    static final char PAYLOAD_DELIM = '|';

    public WordVecIndexer(String propFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));
                
        String indexPath = prop.getProperty("word.index");
        
        stopwords = new ArrayList<>();
        String stopFile = prop.getProperty("stopfile");
        
        String line;

        try {
            FileReader fr = new FileReader(stopFile);
            BufferedReader br = new BufferedReader(fr);
            while ( (line = br.readLine()) != null ) {
                stopwords.add(line.trim());
            }
            br.close(); fr.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }        
                
        // Load the word2vecs so as to prepare the analyzer
        Analyzer analyzer = new PayloadAnalyzer();
        
        // English analyzer (standard stopword removal and stemming for the words)...
        // payload analyzers for the payloads
        Map<String,Analyzer> analyzerPerField = new HashMap<>();
        analyzerPerField.put(FIELD_BAG_OF_WORDS, new EnglishAnalyzer(Version.LUCENE_4_9, StopFilter.makeStopSet(Version.LUCENE_4_9, stopwords)));
        wrapper = new PerFieldAnalyzerWrapper(analyzer, analyzerPerField);
        
        indexDir = new File(indexPath);

        // This is for pass 1 indexing...
        IndexWriterConfig iwcfg = new IndexWriterConfig(Version.LUCENE_4_9, wrapper);
        iwcfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        writer = new IndexWriter(FSDirectory.open(indexDir), iwcfg);
        
        indexingPass = Integer.parseInt(prop.getProperty("pass"));
    }
    
    public Analyzer getAnalyzer() { return wrapper; }
    
    void indexAll() throws Exception {
        if (writer == null) {
            System.err.println("Skipping indexing... Index already exists at " + indexDir.getName() + "!!");
            return;
        }
        
        File topDir = new File(prop.getProperty("coll"));
        indexDirectory(topDir);
        writer.close();
    }

    private void indexDirectory(File dir) throws Exception {
        File[] files = dir.listFiles();
        //System.out.println("Indexing directory: "+files.length);
        for (int i=0; i < files.length; i++) {
            File f = files[i];
            if (f.isDirectory()) {
                System.out.println("Indexing directory " + f.getName());
                indexDirectory(f);  // recurse
            }
            else
                indexFile(f);
        }
    }
    
    Document constructDoc(String id, String content/*, String time*/) throws IOException {
        final PorterStemmer stemmer = new PorterStemmer();

        Document doc = new Document();
        doc.add(new Field(FIELD_ID, id, Field.Store.YES, Field.Index.NOT_ANALYZED));
        //if (time != null) {
        //    doc.add(new Field(FIELD_TIME, time, Field.Store.YES, Field.Index.NOT_ANALYZED));            
        //}

        String txt = content;
        txt = txt.replace(':', ' ');
        txt = txt.replaceAll("'s", "");

        StringBuffer tokenizedContentBuff = new StringBuffer();

        TokenStream stream = wrapper.tokenStream(FIELD_BAG_OF_WORDS, new StringReader(txt));
        CharTermAttribute termAtt = stream.addAttribute(CharTermAttribute.class);
        stream.reset();
        
        while (stream.incrementToken()) {
            String term = termAtt.toString();
            term = term.toLowerCase();
            tokenizedContentBuff.append(term).append(" ");
        }

        stream.end();
        stream.close();
        
        // For the 1st pass, use a standard analyzer to write out
        // the words (also store the term vector)
        doc.add(new Field(FIELD_BAG_OF_WORDS, tokenizedContentBuff.toString(),
                Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.YES));
        return doc;
    }
    
    void indexFile(File file) throws Exception {
        FileReader fr = new FileReader(file);
        BufferedReader br = new BufferedReader(fr);
        String line;
        String docId = "";
        StringBuffer buff = new StringBuffer();
        boolean collect = false;
        Document doc;
        final PorterStemmer stemmer = new PorterStemmer();

        String docType = prop.getProperty("docType");
        if (docType.equalsIgnoreCase("trec")) {
            StringBuffer txtbuff = new StringBuffer();
            while ((line = br.readLine()) != null)
                txtbuff.append(line).append("\n");
            String content = txtbuff.toString();
            
            org.jsoup.nodes.Document jdoc = Jsoup.parse(content);
            Elements docElts = jdoc.select("DOC");
                        
            for (Element docElt : docElts) {
                Element docIdElt = docElt.select("DOCNO").first();
                //+++For the tweet articles
                Element docTimeElt = docElt.select("tweettime").first();
                String time = docTimeElt == null? docIdElt.text() : docTimeElt.text();
                //---Tweet
                doc = constructDoc(time, docElt.text()/*, time*/);
                writer.addDocument(doc);
            }
            
            /* This was skipping some important information from documents...
            for (Element docElt : docElts) {
                Element docIdElt = docElt.select("DOCNO").first();
                //System.out.println("Indexing: " + docIdElt.text());
                
                Element docTimeElt = docElt.select("tweettime").first();
                Element docTxtElt = docElt.select("text").first();
                String time = docTimeElt == null? null : docTimeElt.text();
                String text = docTxtElt == null? null : docTxtElt.text();

                if (text == null) {
                    // In some TREC documents, the <TEXT> tag is absent.
                    // Use the <P> fields
                    txtbuff = new StringBuffer();
                    Elements pElts = docElt.select("p");
                    for (Element pElt : pElts) {
                            txtbuff.append(pElt.text()).append(" ");
                    }
                    text = txtbuff.toString();
                }

                doc = constructDoc(docIdElt.text(), text, time);
                writer.addDocument(doc);
            }
            */
        }
    }

    // 2nd pass: Read each document in the index
    // and smooth each term generation probability
    // by its neighbouring terms (as obtained from
    // the abstract vector space of word2vec).
    public void expandIndex() throws Exception {
        String wvIndexPath = prop.getProperty("wv.index");
        if (wvIndexPath == null) {
            System.out.println("Skipping expansion");
            return;
        }
        else {
            System.out.println("Saving the word-vector in: " + wvIndexPath);
        }

        wvIndexDir = new File(wvIndexPath);
        
        wordvecs = new WordVecs(prop);
        if (wordvecs.wordvecmap != null)
            wordvecs.loadPrecomputedNNs();

        // Open the new wv index for writing
        IndexWriterConfig iwcfg = new IndexWriterConfig(Version.LUCENE_4_9, wrapper);
        iwcfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        
        writer = new IndexWriter(FSDirectory.open(wvIndexDir), iwcfg);
        
        int start = Integer.parseInt(prop.getProperty("wv_expand.start.docid"));
        int end = Integer.parseInt(prop.getProperty("wv_expand.end.docid", "-1"));
       
        Document expDoc;
        
        try (IndexReader reader = DirectoryReader.open(FSDirectory.open(indexDir))) {
            int maxDoc = reader.maxDoc();
            end = Math.min(end, maxDoc);
            if (end == -1)
                end = maxDoc;

            for (int i = start; i < end; i++) {
                System.out.println("DocId: " + i);                
                expDoc = expandDoc(reader, i);
                writer.addDocument(expDoc);
            }
        }
        
        writer.close();
    }

    boolean isNumber(String term) {
        int len = term.length();
        for (int i = 0; i < len; i++) {
            char ch = term.charAt(i);
            if (Character.isDigit(ch))
                return true;
        }
        return false;
    }
        
    Document expandDoc(IndexReader reader, int docId) throws IOException {
        
        int N = reader.numDocs();
        ArrayList<TermFreq> tfvec = new ArrayList<>();

        Document newdoc = new Document();
        Document doc = reader.document(docId);

        StringBuffer buff = new StringBuffer(), cbuff = new StringBuffer();
        
        //get terms vectors stored in 1st pass
        Terms terms = reader.getTermVector(docId, FIELD_BAG_OF_WORDS);
        if (terms == null || terms.size() == 0)
            return doc;
        
        TermsEnum termsEnum = terms.iterator(null); // access the terms for this field
        BytesRef term;
        int docLen = 0;

        // Calculate doc len
        while (termsEnum.next() != null) {// explore the terms for this field
            DocsEnum docsEnum = termsEnum.docs(null, null); // enumerate through documents, in this case only one

            while (docsEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                //get the term frequency in the document
                docLen += docsEnum.freq();
            }
        }

        // Construct the normalized tf vector
        termsEnum = terms.iterator(null); // access the terms for this field
        while ((term = termsEnum.next()) != null) { // explore the terms for this field
            String termStr = term.utf8ToString();
            if (isNumber(termStr))
                continue;
            DocsEnum docsEnum = termsEnum.docs(null, null); // enumerate through documents, in this case only one
            while (docsEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                //get the term frequency in the document
                int tf = docsEnum.freq();
                float ntf = tf/(float)docLen;
                TermFreq tfObj = new TermFreq(termStr);
                tfObj.ntf_d = ntf;
                tfvec.add(tfObj);
            }
        }
        
        // P(t|t',d):        
        // Iterate over the normalized tf vector
        int i, j, len = tfvec.size();
        float prob, sim, totalSim = 0.0f;
            
        for (i = 0; i < len; i++) {
            TermFreq tf_i = tfvec.get(i);
            for (j = 0; j < len; j++) {
                if (i==j)
                    continue;
                TermFreq tf_j = tfvec.get(j);
                totalSim += this.wordvecs.getSim(tf_i.term, tf_j.term);
            }
        }

        for (i = 0; i < len; i++) {
            TermFreq tf_i = tfvec.get(i);
            
            for (j = 0; j < len; j++) {
                if (i==j)
                    continue;
                
                TermFreq tf_j = tfvec.get(j);
                // CHECK: Currently not checking if t'
                // is a near neighbour of t
                sim = this.wordvecs.getSim(tf_i.term, tf_j.term);
                prob = tf_j.ntf_d * sim/totalSim;
                tf_i.ntf_d += prob;
            }
            buff.append(tf_i.term).append(PAYLOAD_DELIM).append(tf_i.ntf_d).append(" ");
        }
        
        // number of neighbors wanted
        final int K = Integer.parseInt(prop.getProperty("wvexpand.numnearest", "3"));
        final float thresh = Float.parseFloat(prop.getProperty("wvexpand.thresh", "0.6"));

        // P(t|t',C)
        for (i = 0; i < len; i++) {
            
            TermFreq tf_i = tfvec.get(i);
            
            // Get the nearest neighbours of tf_i
            List<WordVec> nn_tf_i = wordvecs.getPrecomputedNNs(tf_i.term, K, thresh);
            if (nn_tf_i == null || nn_tf_i.size() == 0) {
                continue;
            }

            // Add the term itself in the list (A word is also a neighbor
            // of itself). No need to maintain the sorted order here...
            nn_tf_i.add(new WordVec(tf_i.term, 1.0f));
            
            float normalizer = 0.0f;
            for (WordVec nn : nn_tf_i) {
                normalizer += nn.querySim;
            }

            // Expand the current document by NN words (including itself)
            for (WordVec nn : nn_tf_i) {
                // We can do this since it's postional indexing... no need
                // to add only one occurrence of term with its frequency
                // No need to incorporate the collection freq here because
                // it will any way be taken care of during retrieval.
                float probNN = (float)(nn.querySim/normalizer);
                cbuff.append(nn.word).append(PAYLOAD_DELIM).append(probNN).append(" ");
            }
        }
        
        newdoc.add(new Field(FIELD_ID, doc.get(FIELD_ID), Field.Store.YES, Field.Index.NOT_ANALYZED));
        newdoc.add(new Field(FIELD_BAG_OF_WORDS, doc.get(FIELD_BAG_OF_WORDS),
            Field.Store.YES, Field.Index.ANALYZED));
        
        // Two new additional fields
        // P(t|t';d)
        newdoc.add(new Field(FIELD_P_WVEC_D, buff.toString(),
                Field.Store.YES, Field.Index.ANALYZED));
        // P(t|t';C)
        newdoc.add(new Field(FIELD_P_WVEC_C, cbuff.toString(),
                Field.Store.YES, Field.Index.ANALYZED));
        
        return newdoc;
    }
    
    void dumpIndex() {
        String dumpPath = prop.getProperty("dumpPath");
        if (dumpPath == null)
            return;
        
        System.out.println("Dumping the index in: "+ dumpPath);
        File f = new File(dumpPath);
        try (IndexReader reader = DirectoryReader.open(FSDirectory.open(indexDir))) {
            PrintWriter pout = new PrintWriter(dumpPath);
            int maxDoc = reader.maxDoc();
            for (int i = 0; i < maxDoc; i++) {
                Document d = reader.document(i);
                pout.print(d.get(FIELD_BAG_OF_WORDS) + " ");
            }
            System.out.println("Index dumped in: " + dumpPath);
            pout.close();
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java WordVecIndexer <prop-file>");
            args[0] = "tweet.index.properties";
        }

        try {
            WordVecIndexer indexer = new WordVecIndexer(args[0]);
            if (indexer.indexingPass == 1) {
                indexer.indexAll();
                indexer.dumpIndex();
            }
            else {
                indexer.expandIndex();                
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }    
}
