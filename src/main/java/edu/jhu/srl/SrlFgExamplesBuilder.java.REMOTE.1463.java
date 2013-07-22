package edu.jhu.srl;

import java.util.List;

import edu.berkeley.nlp.PCFGLA.smoothing.BerkeleySignatureBuilder;
import edu.jhu.data.concrete.SimpleAnnoSentenceCollection;
import edu.jhu.data.conll.CoNLL09FileReader;
import edu.jhu.data.conll.CoNLL09Sentence;
import edu.jhu.gm.FactorGraph;
import edu.jhu.gm.Feature;
import edu.jhu.gm.FeatureExtractor;
import edu.jhu.gm.FgExample;
import edu.jhu.gm.FgExamples;
import edu.jhu.gm.VarConfig;
import edu.jhu.srl.SrlFgExampleBuilder.SrlFgExampleBuilderPrm;
import edu.jhu.util.Alphabet;

/**
 * Factory for FgExamples.
 * 
 * @author mgormley
 * @author mmitchell
 */
public class SrlFgExamplesBuilder {

    private Alphabet<Feature> alphabet;
    private SrlFgExampleBuilderPrm prm;
    private BerkeleySignatureBuilder sig;
    
    public SrlFgExamplesBuilder(SrlFgExampleBuilderPrm prm, Alphabet<Feature> alphabet) {
        this.prm = prm;
        this.alphabet = alphabet;
        this.sig = new BerkeleySignatureBuilder(new Alphabet());
    }
        
    public FgExamples getData(SimpleAnnoSentenceCollection sents) {
        throw new RuntimeException("Not implemented");
    }
    
    public FgExamples getData(CoNLL09FileReader reader) {
        List<CoNLL09Sentence> sents = reader.readAll();
        CorpusStatistics cs = new CorpusStatistics(prm);
        cs.init(sents);

        // TODO: set these params.
        SrlFgExampleBuilder ps = new SrlFgExampleBuilder(prm, alphabet, cs, sig);

        FgExamples data = new FgExamples(alphabet);
        for (CoNLL09Sentence sent : sents) {
            data.add(ps.getFGExample(sent));
        }
        return data;
    }

    public Alphabet<Feature> getAlphabet() {
        return alphabet;
    }
    
}
