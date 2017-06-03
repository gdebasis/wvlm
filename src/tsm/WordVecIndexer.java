/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tsm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.payloads.DelimitedPayloadTokenFilter;
import org.apache.lucene.analysis.payloads.FloatEncoder;
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
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;
import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.*;
import org.tartarus.snowball.ext.PorterStemmer;


/**
 *
 * @author Debasis
 */

class TermFreq {
    String term;
    float ntf;

    public TermFreq(String term, float ntf) {
        this.term = term;
        this.ntf = ntf;
    }
}

/*
class WordVecAnalyzer extends Analyzer {

    Properties prop;
    WordVecs wordvecs;

    public WordVecAnalyzer(WordVecs wordvecs, Properties prop) {
        this.wordvecs = wordvecs;
        this.prop = prop;
    }
        
    @Override
    protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
        final Tokenizer source = new StandardTokenizer(Version.LUCENE_4_9, reader);
        TokenStream result = source;
        // DR_TODO: Apply stemming and stopword removal
        result = new WordVecTokenStream(wordvecs, prop, result);        
        return new TokenStreamComponents(source, result);                
    }
}
*/

//
    class TSMTokenAnalyzer extends Analyzer {
       boolean stem;
       List<String> stopwords;

   public TSMTokenAnalyzer(Properties prop) {
               boolean stem = Boolean.parseBoolean(prop.getProperty("stem", "true"));
               String stopFile = prop.getProperty("stopfile", "common_words");
       
               this.stem = stem;
               stopwords = new ArrayList<>();
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
   }
   
       public TSMTokenAnalyzer(boolean stem, String stopFile) {
               this.stem = stem;
               stopwords = new ArrayList<>();
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
       }

    TSMTokenAnalyzer(Version version) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

       @Override
       protected Analyzer.TokenStreamComponents createComponents(String fieldName, Reader reader) {
            String token;
            TokenStream result = null;

            Version luceneVersion = Version.LUCENE_4_9;
       
            Tokenizer source = new StandardTokenizer(luceneVersion, reader);
            result = source;
            result = new StandardFilter(luceneVersion, result);
            result = new StopFilter(luceneVersion, result,
                StopFilter.makeStopSet(luceneVersion, stopwords));
            if (stem)
                result = new PorterStemFilter(result);
//            result = new NumericTokenFilter(luceneVersion, true, result);

            return new Analyzer.TokenStreamComponents(source, result);
       }
}
 

//
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
    File topDir;
    File indexDir;
    File wvIndexDir;
    WordVecs wordvecs;
    IndexWriter writer;
    Analyzer analyzer;

    static final public String FIELD_ID = "id";
    static final public String FIELD_BAG_OF_WORDS = "words";  // Baseline
    static final public String FIELD_BAG_OF_VECS = "wordvecs";
    static final char PAYLOAD_DELIM = '|';
    
    public WordVecIndexer(String propFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));
                
        this.topDir = new File(prop.getProperty("coll"));
        String indexPath = prop.getProperty("word.index");
        
        // Load the word2vecs so as to prepare the analyzer
        analyzer = new EnglishAnalyzer(Version.LUCENE_4_9);
        //analyzer = new TSMTokenAnalyzer(prop);
        indexDir = new File(indexPath);

        IndexWriterConfig iwcfg = new IndexWriterConfig(Version.LUCENE_4_9, analyzer);
        iwcfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        //if (!DirectoryReader.indexExists(FSDirectory.open(indexDir)))
        writer = new IndexWriter(FSDirectory.open(indexDir), iwcfg);        
    }
    
    void indexAll() throws Exception {
        if (writer == null) {
            System.err.println("Skipping indexing... Index already exists at " + indexDir.getName() + "!!");
            return;
        }

        indexDirectory(topDir);
        writer.close();
    }

    private void indexDirectory(File dir) throws Exception {
        File[] files = dir.listFiles();
        System.out.println("Indexing directory: "+files.length);
        for (int i=0; i < files.length; i++) {
            File f = files[i];
            if (f.isDirectory()) {
                System.out.println("Indexing directory " + f.getName());
                indexDirectory(f);  // recurse
            }
            indexFile(f);
        }
    }
    
    Document constructDoc(String id, String content) throws IOException {
        final PorterStemmer stemmer = new PorterStemmer();

        Document doc = new Document();
        doc.add(new Field(FIELD_ID, id, Field.Store.YES, Field.Index.NOT_ANALYZED));

        org.jsoup.nodes.Document jdoc = Jsoup.parse(content);
        StringBuffer buff = new StringBuffer();
        
        Elements elts = jdoc.getElementsByTag("p");
        for (Element elt : elts) {
            buff.append(elt.text()).append(" ");
        }

        String txt = buff.toString();
        txt = txt.replace(':', ' ');
        txt = txt.replaceAll("'s", "");

        //System.out.println(txt);
        
        StringBuffer tokenizedContentBuff = new StringBuffer();
        Analyzer analyzer = new TSMTokenAnalyzer(prop);
        TokenStream stream = analyzer.tokenStream(FIELD_BAG_OF_WORDS, new StringReader(txt));
        CharTermAttribute termAtt = stream.addAttribute(CharTermAttribute.class);
        stream.reset();
        
        while (stream.incrementToken()) {
            String term = termAtt.toString();
            term = term.toLowerCase();
            tokenizedContentBuff.append(term).append(" ");
        }
        
        //System.out.println(tokenizedContentBuff.toString());
        
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
        
        if(file.toString().endsWith(".dat")){
            while ((line = br.readLine()) != null) {
                if (line.startsWith("#UID")) {
                    docId = line.substring(5);
                    System.out.println("Indexing: "+docId);
                }
                else if (line.startsWith("#CONTENT")) {
                    buff = new StringBuffer();
                    collect = true;
                }
                else if (line.startsWith("#EOR")) {
                    collect = false;
                    doc = constructDoc(docId, buff.toString());
                    writer.addDocument(doc);                
                }
                else if (collect) {
//                    System.out.println(line.length());
                    for (int i=0; i<line.length(); i++)
                        stemmer.setCurrent(line);
                    boolean stemmed = stemmer.stem();
                    buff.append(stemmer.getCurrent());
                    //
/*                    Tokenizer tokenizer = new StandardTokenizer(Version.LUCENE_4_9, new StringReader(line));
//                   new StringReader("I've got a brand new combine harvester, and I'm giving you the key"));

                    final StandardFilter standardFilter = new StandardFilter(Version.LUCENE_4_9, tokenizer);
                    final StopFilter stopFilter = new StopFilter(Version.LUCENE_4_9, standardFilter, StopAnalyzer.ENGLISH_STOP_WORDS_SET);

                    final CharTermAttribute charTermAttribute = tokenizer.addAttribute(CharTermAttribute.class);

                    stopFilter.reset();
                    while(stopFilter.incrementToken()) {
                        final String token = charTermAttribute.toString();
                        //System.out.println(token);
                        buff.append(token);
                        System.out.println(buff);
                    }
*/                    //
                    //buff.append(line);
                    //System.out.println(buff);
                }
            }
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

        wvIndexDir = new File(wvIndexPath);

        wordvecs = new WordVecs(prop);
	analyzer = new PayloadAnalyzer();

        // Open the new wv index for writing
        IndexWriterConfig iwcfg = new IndexWriterConfig(Version.LUCENE_4_9, analyzer);
        iwcfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        
        if (!DirectoryReader.indexExists(FSDirectory.open(indexDir)))
            writer = new IndexWriter(FSDirectory.open(indexDir), iwcfg);
        else {
            System.err.println("Index exists!");
            return;
        }
        
        try (IndexReader reader = DirectoryReader.open(FSDirectory.open(indexDir))) {
            int maxDoc = reader.maxDoc();
            Document expDoc;
            for (int i = 0; i < maxDoc; i++) {
                expDoc = expandDoc(reader, i);
                if (expDoc == null) {
                    System.err.println("Can't expand documents in 2nd pass");
                    writer.close();
                    return;
                }
                writer.addDocument(expDoc);
            }
        }
        writer.close();
    }
    
    void dumpIndex() {
        try (IndexReader reader = DirectoryReader.open(FSDirectory.open(indexDir))) {
            PrintWriter pout = new PrintWriter("index_dump.txt");
            int maxDoc = reader.maxDoc();
            for (int i = 0; i < maxDoc; i++) {
                Document d = reader.document(i);
                //System.out.print(d.get(FIELD_BAG_OF_WORDS) + " ");
                pout.print(d.get(FIELD_BAG_OF_WORDS) + " ");
            }
            pout.close();
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    Document expandDoc(IndexReader reader, int docId) throws IOException {
	int N = reader.numDocs();
        ArrayList<TermFreq> tfvec = new ArrayList<>();
        
        Document newdoc = new Document();
        Document doc = reader.document(docId);
        
        StringBuffer buff = new StringBuffer();
        
        doc.add(new Field(FIELD_ID, doc.get(FIELD_ID), Field.Store.YES, Field.Index.NOT_ANALYZED));
        
        //get terms vectors stored in 1st pass
	Terms terms = reader.getTermVector(docId, FIELD_BAG_OF_WORDS);
	if (terms == null || terms.size() == 0)
            return null;
        
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
            DocsEnum docsEnum = termsEnum.docs(null, null); // enumerate through documents, in this case only one
            while (docsEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            //get the term frequency in the document
		int tf = docsEnum.freq();
		float ntf = tf/(float)docLen;
		tfvec.add(new TermFreq(term.utf8ToString(), ntf));
            }
	}
        
        // Iterate over the normalized tf vector
        int i, j, len = tfvec.size();
        float prob, sim, totalSim = 0.0f;
        float mu = Float.parseFloat(prop.getProperty("mu", "0.2"));
        float oneMinusMu = 1-mu;
        
        for (i = 0; i < len; i++) {
            TermFreq tf_i = tfvec.get(i);
            for (j = 0; j < len; j++) {
                if (i == j)
                    continue;
                TermFreq tf_j = tfvec.get(j);
                totalSim += this.wordvecs.getSim(tf_i.term, tf_j.term);
            }
        }
        
        // P(t|t',d)
        for (i = 0; i < len; i++) {
            TermFreq tf_i = tfvec.get(i);
            for (j = 0; j < len; j++) {
                if (i == j)
                    continue;
                TermFreq tf_j = tfvec.get(j);
                // CHECK: Currently not checking if t'
                // is a near neighbour of t
                sim = this.wordvecs.getSim(tf_i.term, tf_j.term);
                prob = mu * tf_j.ntf*sim/totalSim;
                tf_i.ntf += prob;
            }
        }
        
        // P(t|t',C)
        for (i = 0; i < len; i++) {
            TermFreq tf_i = tfvec.get(i);
            // Get the nearest neighbours of tf_i
            List<WordVec> nn_tf_i = wordvecs.getNearestNeighbors(tf_i.term);
            
            float normalizer = 0.0f;
            for (WordVec nn : nn_tf_i) {
                normalizer += nn.querySim;
            }
            
            for (WordVec nn : nn_tf_i) {
                long docFreq = reader.docFreq(
                        new Term(WordVecIndexer.FIELD_BAG_OF_WORDS, nn.word));
                tf_i.ntf += (float)(oneMinusMu * nn.querySim/normalizer * docFreq/(double)N); 
            }
        }
        
        for (i = 0; i < len; i++) {
            TermFreq tf_i = tfvec.get(i);
            buff.append(tf_i.term).append(PAYLOAD_DELIM).append(tf_i.ntf).append(" ");
        }
        
        newdoc.add(new Field(FIELD_BAG_OF_VECS, buff.toString(),
                Field.Store.YES, Field.Index.ANALYZED));
        return newdoc;
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            args[0] = "/home/dwaipayan/tsm/init.properties";
        }

        try {
            WordVecIndexer indexer = new WordVecIndexer(args[0]);
            indexer.indexAll();
            indexer.dumpIndex();
            //indexer.expandIndex();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }    
}
