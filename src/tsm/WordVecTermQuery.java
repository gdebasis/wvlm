/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tsm;

import java.io.IOException;
import org.apache.lucene.analysis.payloads.PayloadHelper;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.payloads.PayloadFunction;
import org.apache.lucene.search.payloads.PayloadTermQuery;
import org.apache.lucene.search.similarities.BasicStats;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.LMSimilarity;
import org.apache.lucene.search.similarities.LMSimilarity.CollectionModel;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.spans.TermSpans;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;


/**
 * TopDocs computation using the wordvecs
 * @author Debasis
 */

class WordVecSimilarity extends LMJelinekMercerSimilarity {
    boolean usePayload;
    
    public WordVecSimilarity(boolean usePayload) {       
        super(0.3f /*unused*/);
        this.usePayload = usePayload;
    }

	public CollectionModel getCollectionModel() { return collectionModel; }

    @Override
    protected float score(BasicStats stats, float freq, float docLen) {
      return (float)(stats.getTotalBoost() * freq / docLen);
    }
    
    @Override
    protected float scorePayload(int doc, int start, int end, BytesRef payload) {        
        if (payload == null || !usePayload) return 1.0f;
        
        // DR_TODO: Implementing a language modeling weight
        // involving NN smoothing. DR: Check this
        float wt = PayloadHelper.decodeFloat(payload.bytes, payload.offset);
        return wt;
    }    
}

public class WordVecTermQuery extends PayloadTermQuery {
    
    public WordVecTermQuery(Term term, PayloadFunction function) {
        super(term, function, true);
    }
    
    @Override
    public Weight createWeight(IndexSearcher searcher) throws IOException {
        return new LMLinearCombinationTermWeight(this, searcher);
    }

    protected class LMLinearCombinationTermWeight extends PayloadTermQuery.PayloadTermWeight {

        public LMLinearCombinationTermWeight(WordVecTermQuery query, IndexSearcher searcher)
            throws IOException {
            super(query, searcher);
        }

        @Override
        public Scorer scorer(AtomicReaderContext context, Bits acceptDocs) throws IOException {
            return new WordVecTermSpanScorer(
                (TermSpans) query.getSpans(context, acceptDocs, termContexts),
                this, similarity.simScorer(stats, context));
        }
    
        protected class WordVecTermSpanScorer extends PayloadTermSpanScorer {

            public WordVecTermSpanScorer(TermSpans spans, Weight weight, Similarity.SimScorer docScorer) throws IOException {
                super(spans, weight, docScorer);
            }

            /**
             * The main logic of using the wordvec payloads goes here.
             * Note: Payloads use positional indexing, i.e. if a term occurs
             * twice, we are going to hit on this function twice. 
             */
            @Override
            public float score() throws IOException {
                // If you want to use collection stats here is how to do it..
                // I'm commenting this out for the time being...
                //WordVecLMSimilarity lmSim = (WordVecSimilarity)similarity; // this sim has to be an LMSimilarity
                //CollectionModel collectionModel = lmSim.getCollectionModel();
                //collectionModel.computeProbability((BasicStats)stats);

                // DR_TODO: this is the vec that we got back.. explore different ways
                // of using it...
                float wvecScore = getPayloadScore();
                return wvecScore;
            }
        }
    }
}

    
