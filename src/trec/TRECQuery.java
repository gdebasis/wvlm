/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package trec;

import java.io.IOException;
import java.io.StringReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.payloads.AveragePayloadFunction;
import org.apache.lucene.search.payloads.MaxPayloadFunction;
import org.apache.lucene.search.payloads.PayloadFunction;
import org.apache.lucene.search.payloads.PayloadTermQuery;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import tsm.*;
import tsm.LMLinearCombinationTermQuery;
import tsm.WordVecIndexer;

/**
 *
 * @author Debasis
 */
public class TRECQuery {
    public String       id;
    public String       title;
    public String       desc;
    public String       narr;
    public Query        luceneQuery;
    WordVecSearcher     parent;
    
    TRECQuery(WordVecSearcher parent) { this.parent = parent; }
    
    @Override
    public String toString() {
        return id + "\t" + title;
    }

    String analyze(Analyzer analyzer, String queryField) throws Exception {
        StringBuffer buff = new StringBuffer(); 
        TokenStream stream = analyzer.tokenStream(WordVecIndexer.FIELD_BAG_OF_WORDS, new StringReader(queryField));
        CharTermAttribute termAtt = stream.addAttribute(CharTermAttribute.class);
        stream.reset();        
        while (stream.incrementToken()) {
            String term = termAtt.toString();
            term = term.toLowerCase();
            buff.append(term).append(" ");
        }
        stream.end();
        stream.close();
        return buff.toString();
    }
    
    public Query getBOWQuery(Analyzer analyzer) throws Exception {
        BooleanQuery q = new BooleanQuery();
        Term thisTerm;
        
        String[] terms = analyze(analyzer, title).split("\\s+");
        for (String term : terms) {
            thisTerm = new Term(WordVecIndexer.FIELD_BAG_OF_WORDS, term);
            Query tq = new TermQuery(thisTerm);
            q.add(tq, BooleanClause.Occur.SHOULD);
        }
        return q;
    }
    
    public Query getWVQuery(float lambda, float mu, float alpha, float beta, Analyzer analyzer) throws Exception {
        BooleanQuery q = new BooleanQuery();
        PayloadFunction pf = new AveragePayloadFunction();        
        Term thisTerm;
        
        String[] terms = analyze(analyzer, title).split("\\s+");
        for (String term : terms) {
            thisTerm = new Term(WordVecIndexer.FIELD_BAG_OF_WORDS, term);
            Query tq = new TermQuery(thisTerm);
            //tq.setBoost(lambda);
            q.add(tq, BooleanClause.Occur.SHOULD);
            
            if (alpha > 0) {
                thisTerm = new Term(WordVecIndexer.FIELD_P_WVEC_D, term);
                Query tq_d = new LMLinearCombinationTermQuery(thisTerm, pf, alpha, this.parent);
                //tq_d.setBoost(alpha);
                q.add(tq_d, BooleanClause.Occur.SHOULD);
            }

            if (beta > 0) {
                thisTerm = new Term(WordVecIndexer.FIELD_P_WVEC_C, term);
                Query tq_c = new LMLinearCombinationTermQuery(thisTerm, pf, beta, this.parent);
                //tq_c.setBoost(beta);
                q.add(tq_c, BooleanClause.Occur.SHOULD);
            }
        }
        return q;        
    }
}
