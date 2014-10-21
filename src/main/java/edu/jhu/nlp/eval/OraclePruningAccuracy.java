package edu.jhu.nlp.eval;

import org.apache.log4j.Logger;

import edu.jhu.nlp.Evaluator;
import edu.jhu.nlp.data.simple.AnnoSentence;
import edu.jhu.nlp.data.simple.AnnoSentenceCollection;

public class OraclePruningAccuracy implements Evaluator {

    private static final Logger log = Logger.getLogger(OraclePruningAccuracy.class);
    
    @Override
    public void evaluate(AnnoSentenceCollection goldSents, AnnoSentenceCollection predSents, String name) {
        int numTot = 0;
        int numCorrect = 0;
        for (int i=0; i<predSents.size(); i++) {
            AnnoSentence predSent = predSents.get(i);
            AnnoSentence goldSent = goldSents.get(i);
            if (predSent.getDepEdgeMask() != null) {
                for (int c=0; c<goldSent.size(); c++) {
                    int p = goldSent.getParent(c);
                    if (predSent.getDepEdgeMask().isKept(p, c)) {
                        numCorrect++;
                    }
                    numTot++;
                }
            }
        }
        log.info("Oracle pruning accuracy on " + name + ": " + (double) numCorrect / numTot);
    }

}
