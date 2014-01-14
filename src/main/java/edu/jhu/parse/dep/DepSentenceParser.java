package edu.jhu.parse.dep;

import edu.jhu.data.DepTree;
import edu.jhu.data.Sentence;
import edu.jhu.induce.model.Model;

public interface DepSentenceParser {

    DepTree getViterbiParse(Sentence sentence, Model model);

}
