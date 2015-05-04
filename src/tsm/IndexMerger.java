/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package tsm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import static tsm.WordVecIndexer.FIELD_BAG_OF_WORDS;


/**
 * This shouldn't really be called.. During implementation forgot to
 * write out the bag-of-words field in the wv index... hence creating
 * this quick fix to merge the field from word.index to wv.index...
 * But now the code takes care of it...
 * 
 * @author dganguly
 */
public class IndexMerger {
    // usage: IndexMerger <bag-of-words index> <wv index> <updated wv index>
    public static void main(String[] args) throws Exception {
        String wordIndex = "/mnt/sdb2/research/wvlm/trec/word.index/";
        String wvIndex = "/mnt/sdb2/research/wvlm/trec/wv.index/";
        String new_wvIndex = "/mnt/sdb2/research/wvlm/trec/wv.merged.index/";
        
        
        List<String> stopwords = new ArrayList<>();
        String stopFile = "/mnt/sdb2/research/wvlm/smart-stopwords";
        
        String line;

        FileReader fr = new FileReader(stopFile);
        BufferedReader br = new BufferedReader(fr);
        while ( (line = br.readLine()) != null ) {
            stopwords.add(line.trim());
        }
        br.close(); fr.close();
        
        Analyzer analyzer = new EnglishAnalyzer(Version.LUCENE_4_9, StopFilter.makeStopSet(Version.LUCENE_4_9, stopwords));        
        Map<String,Analyzer> analyzerPerField = new HashMap<>();
        analyzerPerField.put(FIELD_BAG_OF_WORDS, new EnglishAnalyzer(Version.LUCENE_4_9, StopFilter.makeStopSet(Version.LUCENE_4_9, stopwords)));
        PerFieldAnalyzerWrapper wrapper = new PerFieldAnalyzerWrapper(analyzer, analyzerPerField);
        
        IndexWriterConfig iwcfg = new IndexWriterConfig(Version.LUCENE_4_9, wrapper);
        iwcfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        
        try (
            IndexWriter writer = new IndexWriter(FSDirectory.open(new File(new_wvIndex)), iwcfg);
            IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(wordIndex)));
            IndexReader wvreader = DirectoryReader.open(FSDirectory.open(new File(wvIndex)));
        ) { 
            
            int maxDoc = reader.maxDoc();
            int wv_maxDoc = wvreader.maxDoc();
            
            if (maxDoc != wv_maxDoc) {
                System.err.println("#documents don't match!");
            }
            
            for (int i = 0; i < maxDoc; i++) {
                if (i%1000 == 0)
                    System.out.println("DocId: " + i);
                
                Document doc = reader.document(i);
                Document wvdoc = wvreader.document(i);
                
                Document newdoc = new Document();
                newdoc.add(new Field(WordVecIndexer.FIELD_ID, doc.get(WordVecIndexer.FIELD_ID), Field.Store.YES, Field.Index.ANALYZED));
                newdoc.add(new Field(WordVecIndexer.FIELD_BAG_OF_WORDS, doc.get(WordVecIndexer.FIELD_BAG_OF_WORDS), Field.Store.YES, Field.Index.ANALYZED));
                
                String wvec_d = wvdoc.get(WordVecIndexer.FIELD_P_WVEC_D);
                if (wvec_d==null)
                    wvec_d = "";
                String wvec_c = wvdoc.get(WordVecIndexer.FIELD_P_WVEC_C);
                if (wvec_c==null)
                    wvec_c = "";
                
                newdoc.add(new Field(WordVecIndexer.FIELD_P_WVEC_D, wvec_d, Field.Store.YES, Field.Index.ANALYZED));
                newdoc.add(new Field(WordVecIndexer.FIELD_P_WVEC_C, wvec_c, Field.Store.YES, Field.Index.ANALYZED));
                                
                writer.addDocument(newdoc);
            }
        }
    }
}
