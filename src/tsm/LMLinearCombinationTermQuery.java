/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tsm;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.analysis.payloads.PayloadHelper;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.payloads.PayloadFunction;
import org.apache.lucene.search.payloads.PayloadTermQuery;
import org.apache.lucene.search.similarities.BasicStats;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.LMSimilarity;
import org.apache.lucene.search.similarities.PerFieldSimilarityWrapper;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.spans.TermSpans;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;

/**
 * Generalized Language model
 * To be used with LMLinearCombinationTermQuery.
 * Defers the linear combination for later. Simply returns the tf/doclen
 * The collection stats are to be used later on.
 * @lucene.experimental
 */
class GeneralizedLMSimilarity extends LMJelinekMercerSimilarity {
    
    public GeneralizedLMSimilarity() {       
        super(0.3f /*unused*/);
    }

    public LMSimilarity.CollectionModel getCollectionModel() { return collectionModel; }

    @Override
    protected float score(BasicStats stats, float freq, float docLen) {
      return (float)(stats.getTotalBoost() * freq / docLen);
    }
    
    @Override
    protected float scorePayload(int doc, int start, int end, BytesRef payload) {
        if (payload == null) return 1.0f;
        float wt = PayloadHelper.decodeFloat(payload.bytes, payload.offset);
        return wt;
    }    
}

/**
 *
 * @author Debasis
 */
/**
 * This class is very similar to
 * {@link org.apache.lucene.search.spans.SpanTermQuery} except that it factors
 * in the value of the payload located at each of the positions where the
 * {@link org.apache.lucene.index.Term} occurs.
 * <p/>
 * NOTE: In order to take advantage of this with the default scoring implementation
 * ({@link DefaultSimilarity}), you must override {@link DefaultSimilarity#scorePayload(int, int, int, BytesRef)},
 * which returns 1 by default.
 * <p/>
 * Payload scores are aggregated using a pluggable {@link PayloadFunction}.
 * @see org.apache.lucene.search.similarities.Similarity.SimScorer#computePayloadFactor(int, int, int, BytesRef)
 **/

public class LMLinearCombinationTermQuery extends PayloadTermQuery {
    float mu;  // relative importance to w_D or w_C
    WordVecSearcher parent;

    public LMLinearCombinationTermQuery(Term term, PayloadFunction function, float mu, WordVecSearcher parent) {
        super(term, function, true);
        this.mu = mu;  
        this.parent = parent;
    }

    @Override
    public Weight createWeight(IndexSearcher searcher) throws IOException {
        return new LMLinearCombinationTermWeight(this, searcher, super.term, parent);
    }

    protected class LMLinearCombinationTermWeight extends PayloadTermQuery.PayloadTermWeight {
        WordVecSearcher parent;
        Term term;
        
        public LMLinearCombinationTermWeight(LMLinearCombinationTermQuery query, IndexSearcher searcher,
            Term term, WordVecSearcher parent)
            throws IOException {
            super(query, searcher);
            this.term = term;
            this.parent = parent;
        }

        @Override
        public Scorer scorer(AtomicReaderContext context, Bits acceptDocs) throws IOException {
            return new LMLinearCombinationTermSpanScorer((TermSpans) query.getSpans(context, acceptDocs, termContexts),
                this, similarity.simScorer(stats, context), term, context.reader(), parent);
        }

        protected class LMLinearCombinationTermSpanScorer extends PayloadTermQuery.PayloadTermWeight.PayloadTermSpanScorer {

            WordVecSearcher parent;
            IndexReader indexReader;
            Term term;
            
            public LMLinearCombinationTermSpanScorer(TermSpans spans, Weight weight, Similarity.SimScorer docScorer,
                            Term term, IndexReader indexReader, WordVecSearcher parent) throws IOException {
                super(spans, weight, docScorer);
                this.term = term;
                this.indexReader = indexReader;
                this.parent = parent;
            }

            /**
             * The main tweak goes here, where instead of multiplying
             * the payload score, we add a linear combination of it and then
             * take log (1 + score_lm).
             * Remember that the scorer in this case needs to be changed to
             * a new class which doesn't return the log as in LMJelineckMercerSmoothing.
             * The log of the linear combination has to be taken here...
             * @return {@link #getSpanScore()} * {@link #getPayloadScore()}
             * @throws IOException if there is a low-level I/O error
             */
            @Override
            public float score() throws IOException {
                CombinationSimilarity simWrapper = (CombinationSimilarity)similarity;
                
                //Similarity thisFieldSim = simWrapper.get(term.field());                
                //GeneralizedLMSimilarity lmSim = (GeneralizedLMSimilarity)thisFieldSim; // this sim has to be an LMSimilarity
                                
                PerFieldSimilarityWrapper.PerFieldSimWeight perfieldStats = (PerFieldSimilarityWrapper.PerFieldSimWeight)stats;                
                LMSimilarity.LMStats perFieldLMStats = (LMSimilarity.LMStats)perfieldStats.delegateWeight;
                float collectionProb = perFieldLMStats.getCollectionProbability();
                
                float reciprocalCollectionProb = 1/collectionProb;  //collectionModel.computeProbability((BasicStats)stats);
                
                // not using the precomputed NNs; instead,
                // we are computing the k-NNs in here itself. 
                // WvScore in this case are stored in different fields...
                // (either wv_c or wv_d)
                float wvScore = getPayloadScore();  // P(t|t';d)
                
                //float mu = lmSim.mu;
                return (float)(Math.log(1 +
                    reciprocalCollectionProb * mu/(1-mu) * wvScore));
            }
        }
    }
}
