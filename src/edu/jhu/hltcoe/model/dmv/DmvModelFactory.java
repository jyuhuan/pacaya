package edu.jhu.hltcoe.model.dmv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.jhu.hltcoe.data.Label;
import edu.jhu.hltcoe.data.Sentence;
import edu.jhu.hltcoe.data.SentenceCollection;
import edu.jhu.hltcoe.data.WallDepTreeNode;
import edu.jhu.hltcoe.model.Model;
import edu.jhu.hltcoe.model.ModelFactory;
import edu.jhu.hltcoe.util.Pair;
import edu.jhu.hltcoe.util.Triple;
import edu.jhu.hltcoe.util.Utilities;

public class DmvModelFactory implements ModelFactory {

    public static final String[] leftRight = new String[] { "l", "r" };
    private static final boolean[] adjacent = new boolean[] { true, false };
    private DmvWeightGenerator weightGen;

    public DmvModelFactory(DmvWeightGenerator weightGen) {
        this.weightGen = weightGen;
    }

    @Override
    public Model getInstance(SentenceCollection sentences) {
        return getInstance(sentences.getVocab());
    }

    public Model getInstance(Set<Label> vocab) {
        return getInstance(vocab, true);
    }

    public Model getInstance(Set<Label> vocab, boolean oneRoot) {
        // TODO: we waste effort on parameters that cannot ever appear
        // in the corpus.

        DmvModel dmv = new DmvModel();
        for (Label label : vocab) {
            for (String lr : leftRight) {
                for (boolean adj : adjacent) {
                    Triple<Label, String, Boolean> triple = new Triple<Label, String, Boolean>(label, lr, adj);
                    double weight;
                    weight = weightGen.getStopWeight(triple);
                    dmv.putStopWeight(triple, weight);
                }
            }
        }
        // OLD WAY used a parentChildMap
        // Map<Label, Set<Label>> parentChildMap = getParentChildMap(sentences);
        // for (Entry<Label, Set<Label>> entry : parentChildMap.entrySet()) {
        // Label parent = entry.getKey();
        // List<Label> children = new ArrayList<Label>(entry.getValue());
        // TODO: This is slow making a list like this
        List<Label> vocabList = new ArrayList<Label>(vocab);
        for (Label parent : vocabList) {
            for (String lr : leftRight) {
                Pair<Label, String> pair = new Pair<Label, String>(parent, lr);
                dmv.setChooseWeights(parent, lr, weightGen.getChooseMulti(pair, vocabList));
            }
        }

        if (oneRoot) {
            // Fix the Wall probabilities to disallow vine parsing
            dmv.putStopWeight(WallDepTreeNode.WALL_LABEL, "l", true, 1.0);
            dmv.putStopWeight(WallDepTreeNode.WALL_LABEL, "l", false, 1.0);
            dmv.putStopWeight(WallDepTreeNode.WALL_LABEL, "r", true, 0.0);
            dmv.putStopWeight(WallDepTreeNode.WALL_LABEL, "r", false, 1.0);
        }

        return dmv;
    }

    public static Map<Label, Set<Label>> getParentChildMap(SentenceCollection sentences) {
        Map<Label, Set<Label>> map = new HashMap<Label, Set<Label>>();
        for (Sentence sent : sentences) {
            for (int i = 0; i < sent.size(); i++) {
                Label parent = sent.get(i);
                for (int j = 0; j < sent.size(); j++) {
                    if (j != i) {
                        Label child = sent.get(j);
                        Utilities.addToSet(map, parent, child);
                    }
                }
                // Special case for Wall
                Utilities.addToSet(map, WallDepTreeNode.WALL_LABEL, parent);
                Utilities.addToSet(map, parent, WallDepTreeNode.WALL_LABEL);
            }
        }
        return map;
    }

    public static Map<Label, Set<Label>> getParentChildMap(Sentence sent) {
        Map<Label, Set<Label>> map = new HashMap<Label, Set<Label>>();

        for (int i = 0; i < sent.size(); i++) {
            Label parent = sent.get(i);
            for (int j = 0; j < sent.size(); j++) {
                if (j != i) {
                    Label child = sent.get(j);
                    Utilities.addToSet(map, parent, child);
                }
            }
            // Special case for Wall
            Utilities.addToSet(map, WallDepTreeNode.WALL_LABEL, parent);
            Utilities.addToSet(map, parent, WallDepTreeNode.WALL_LABEL);
        }

        return map;
    }

}
