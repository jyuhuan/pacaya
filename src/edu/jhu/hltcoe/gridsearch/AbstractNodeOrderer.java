package edu.jhu.hltcoe.gridsearch;

import edu.jhu.hltcoe.gridsearch.LazyBranchAndBoundSolver.NodeResult;

public abstract class AbstractNodeOrderer implements NodeOrderer {

    @Override
    public void addRoot(ProblemNode root) {
        add(root);
    }
    
    @Override
    public void addChildrenOfResult(NodeResult result, double globalUb, double globalLb, boolean isRoot) {
        for (ProblemNode childNode : result.children) {
            add(childNode);
        }
    }

    public abstract boolean add(ProblemNode node);

}
