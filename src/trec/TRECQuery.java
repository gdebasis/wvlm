/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package trec;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.payloads.AveragePayloadFunction;
import org.apache.lucene.search.payloads.PayloadFunction;
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
    
    @Override
    public String toString() {
        return id + "\t" + title;
    }

    public Query getQuery(float lambda, float mu) {
        BooleanQuery q = new BooleanQuery();
        PayloadFunction pf = new AveragePayloadFunction();        
        Term thisTerm;
        
        String[] terms = title.split("\\s+");
        for (String term : terms) {
            thisTerm = new Term(WordVecIndexer.FIELD_BAG_OF_VECS, term);
            Query tq = new LMLinearCombinationTermQuery(thisTerm, pf, lambda, mu);
            q.add(tq, BooleanClause.Occur.SHOULD);
        }
        return q;
    }
}
