package edu.jhu.hltcoe.gridsearch.dmv;

import gnu.trove.TDoubleArrayList;
import gnu.trove.TIntArrayList;
import ilog.concert.IloException;
import ilog.concert.IloLPMatrix;
import ilog.concert.IloMPModeler;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloObjective;
import ilog.concert.IloRange;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.BasisStatus;
import ilog.cplex.IloCplex.Status;
import ilog.cplex.IloCplex.UnknownObjectException;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.apache.log4j.Logger;
import org.jboss.dna.common.statistic.Stopwatch;

import edu.jhu.hltcoe.data.DepTree;
import edu.jhu.hltcoe.data.DepTreebank;
import edu.jhu.hltcoe.data.Sentence;
import edu.jhu.hltcoe.data.SentenceCollection;
import edu.jhu.hltcoe.data.WallDepTreeNode;
import edu.jhu.hltcoe.gridsearch.RelaxStatus;
import edu.jhu.hltcoe.gridsearch.dmv.DmvBoundsDelta.Lu;
import edu.jhu.hltcoe.math.Vectors;
import edu.jhu.hltcoe.parse.DmvCkyParser;
import edu.jhu.hltcoe.parse.pr.DepProbMatrix;
import edu.jhu.hltcoe.util.Pair;
import edu.jhu.hltcoe.util.Time;
import edu.jhu.hltcoe.util.Utilities;

public class DmvDantzigWolfeRelaxationResolution extends DmvDantzigWolfeRelaxation implements DmvRelaxation {

    private static Logger log = Logger.getLogger(DmvDantzigWolfeRelaxationResolution.class);
    
    private int numGammas;
    private boolean hasInfeasibleBounds;
    private Stopwatch stoTimer;
    private MasterProblem mp;
    private Projections projections;
    
    public DmvDantzigWolfeRelaxationResolution(File tempDir) {
        super(tempDir, 0, null);
        this.projections = new Projections(tempDir);
        this.hasInfeasibleBounds = false;
        this.parsingTimer = new Stopwatch();
        this.stoTimer = new Stopwatch();
    }

    public void setSentences(SentenceCollection sentences) {
        this.sentences = sentences;
        this.idm = new IndexedDmvModel(sentences);    
    }

    protected void clearRelaxedSolution() {
        optimalLogProbs = null;
    }
        
    protected RelaxedDmvSolution extractSolution(RelaxStatus status, double objective) throws UnknownObjectException,
            IloException {
        log.info(String.format("Summary: #lambdas=%d #gammas=%d", mp.lambdaVars.size(), mp.gammaVars.size()));
        
        // Store optimal model parameters
        optimalLogProbs = new double[idm.getNumConds()][];
        for (int i = 0; i < mp.gammaVars.size(); i++) {
            GammaVar gv = mp.gammaVars.get(i);
            double gammaValue = cplex.getValue(gv.gammaVar);
            for (int c = 0; c < idm.getNumConds(); c++) {
                int numParams = idm.getNumParams(c);
                if (optimalLogProbs[c] == null) {
                    optimalLogProbs[c] = new double[numParams];
                }
                for (int m = 0; m < numParams; m++) {
                    optimalLogProbs[c][m] += gammaValue * gv.logProbs[c][m];
                }
            }
        }
        // Assert that the model parameters sum to <= 1.0
        for (int c = 0; c < idm.getNumConds(); c++) {
            double[] probs = Vectors.getExp(optimalLogProbs[c]);
            //assert Utilities.lte(Vectors.sum(probs), 1.0, 1e-8) : String.format("sum(probs[%d]) = %.15g", c, Vectors.sum(probs));
            if (!Utilities.lte(Vectors.sum(probs), 1.0, 1e-8)) {
                log.warn(String.format("Sum of log probs must be <= 1.0: sum(probs[%d]) = %.15g", c, Vectors.sum(probs)));
            }
        }

        // Store fractional corpus parse
        double[][] fracRoots = new double[sentences.size()][];
        double[][][] fracParses = new double[sentences.size()][][];
        for (int s = 0; s < sentences.size(); s++) {
            Sentence sentence = sentences.get(s);
            fracRoots[s] = new double[sentence.size()];
            fracParses[s] = new double[sentence.size()][sentence.size()];
        }
        for (LambdaVar triple : mp.lambdaVars) {
            double frac = cplex.getValue(triple.lambdaVar);
            int s = triple.s;
            int[] parents = triple.parents;

            double[] fracRoot = fracRoots[s];
            double[][] fracParse = fracParses[s];
            for (int child = 0; child < parents.length; child++) {
                int parent = parents[child];
                if (parent == WallDepTreeNode.WALL_POSITION) {
                    fracRoot[child] += frac;
                } else {
                    fracParse[parent][child] += frac;
                }
            }
        }
        
        return new RelaxedDmvSolution(Utilities.copyOf(optimalLogProbs), fracRoots, fracParses, objective, status);
    }

    public WarmStart getWarmStart() {
        try {
            WarmStart warmStart = new WarmStart();
            warmStart.numVars = mp.lpMatrix.getNumVars();
            warmStart.ranges = mp.lpMatrix.getRanges();
            warmStart.numVarStatuses = cplex.getBasisStatuses(warmStart.numVars);
            warmStart.rangeStatuses = cplex.getBasisStatuses(warmStart.ranges);
            return warmStart;
        } catch (IloException e) {
            throw new RuntimeException(e);
        }
    }
    
    protected ArrayList<IloNumVar> getUnknownVars(HashSet<IloNumVar> knownVars) {
        ArrayList<IloNumVar> unknownVars = new ArrayList<IloNumVar>();
        for (int i=0; i<mp.lambdaVars.size(); i++) {
            IloNumVar lv = mp.lambdaVars.get(i).lambdaVar;
            if (!knownVars.contains(lv)) {
                unknownVars.add(lv);
            }
        }
        for (int i=0; i<mp.gammaVars.size(); i++) {
            IloNumVar gv = mp.gammaVars.get(i).gammaVar;
            if (!knownVars.contains(gv)) {
                unknownVars.add(gv);
            }
        }
        return unknownVars;
    }
    
    public double[][] getRegretCm() {
        try {
            // If optimal model parameters \theta_{c,m} are not present, return null.
            if (optimalLogProbs == null) {
                return null;
            }

            // Store feature counts \bar{f}_{c,m} (i.e. number of times each
            // model parameter was used)
            double[][] featCounts = new double[idm.getNumConds()][];
            for (int c = 0; c < idm.getNumConds(); c++) {
                featCounts[c] = new double[idm.getNumParams(c)];
            }

            for (LambdaVar triple : mp.lambdaVars) {
                double frac = cplex.getValue(triple.lambdaVar);
                int s = triple.s;
                int[] sentSol = triple.sentSol;
                for (int i = 0; i < sentSol.length; i++) {
                    int c = idm.getC(s, i);
                    int m = idm.getM(s, i);
                    featCounts[c][m] += sentSol[i] * frac;
                }
            }

            // Store objective values z_{c,m}
            double[][] objVals = new double[idm.getNumConds()][];
            for (int c = 0; c < idm.getNumConds(); c++) {
                objVals[c] = cplex.getValues(mp.objVars[c]);
            }

            // Compute the regret as the difference between the
            // objective value and true objective value
            double[][] regret = new double[idm.getNumConds()][];
            for (int c = 0; c < idm.getNumConds(); c++) {
                regret[c] = new double[idm.getNumParams(c)];
                for (int m = 0; m < idm.getNumParams(c); m++) {
                    regret[c][m] = objVals[c][m] - (optimalLogProbs[c][m] * featCounts[c][m]);
                    //TODO: this seems to be too strong:
                    //assert Utilities.gte(regret[c][m], 0.0, 1e-7) : String.format("regret[%d][%d] = %f", c, m, regret[c][m]);
                    if (!Utilities.gte(regret[c][m], 0.0, 1e-7)) {
                        log.warn(String.format("Invalid negative regret: regret[%d][%d] = %f", c, m, regret[c][m]));
                    }
                }
            }

            return regret;
        } catch (IloException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Convenience class for passing around Master Problem variables
     */
    private static class MasterProblem {
        public IloObjective objective;
        public IloRange[] lambdaSumCons;
        public List<LambdaVar> lambdaVars;
        public HashSet<LambdaVar> lambdaVarSet;
        public IloRange gammaSumCons;
        public List<GammaVar> gammaVars;
        public IloNumVar[][] objVars;
        public IloRange[][] couplConsLower;
        public IloRange[][] couplConsUpper;
        public IloLPMatrix lpMatrix;
    }
    
    /**
     * Convenience class for storing the columns generated by
     * Dantzig-Wolfe and their corresponding model parameters
     */
    private static class GammaVar {
        
        public IloNumVar gammaVar;
        public double[][] logProbs;

        public GammaVar(IloNumVar gammaVar, double[][] logProbs) {
            super();
            this.gammaVar = gammaVar;
            this.logProbs = logProbs;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("logProbs:\n");
            for (int c=0; c<logProbs.length; c++){
                sb.append(Arrays.toString(logProbs[c]));
                sb.append("\n");
            }
            return sb.toString();
        }

        @Override
        public int hashCode() {
            throw new RuntimeException("not implemented");
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            GammaVar other = (GammaVar) obj;
            if (logProbs.length != other.logProbs.length) {
                return false;
            }
            for (int c=0; c<logProbs.length; c++) {
                if (logProbs[c].length != other.logProbs[c].length) {
                    return false;
                }
                for (int m=0; m<logProbs[c].length; m++) {
                    if (!Utilities.equals(logProbs[c][m], other.logProbs[c][m], 1e-13)) {
                        return false;
                    }
                }
            }
            return true;
        }
        
    }
    
    private static class GammaVarHashObj00 extends GammaVar {
        
        protected double[][] logProbs;
        
        public GammaVarHashObj00(double[][] logProbs) {
            super(null, logProbs);
            this.logProbs = logProbs;            
        }
        
        @Override
        public int hashCode() {
            return roundingHashCode(logProbs, 0.0);
        }
        
        public static int roundingHashCode(double[][] a, double addend) {
            if (a == null)
                return 0;

            int result = 1;
            for (double[] element : a) {
                result = 31 * result + roundingHashCode(element, addend);
            }
            return result;
        }
        
        /**
         * Rounds any double to a long before taking its hash.
         * Before rounding, one can add addend
         */
        public static int roundingHashCode(double a[], double addend) {
            if (a == null)
                return 0;

            int result = 1;
            for (double element : a) {
                long bits = Math.round(element + addend);
                result = 31 * result + (int) (bits ^ (bits >>> 32));
            }
            return result;
        }
        
    }
    

    private static class GammaVarHashObj05 extends GammaVarHashObj00 {

        public GammaVarHashObj05(double[][] logProbs) {
            super(logProbs);
        }

        @Override
        public int hashCode() {
            return roundingHashCode(logProbs, 0.5);
        }
        
    }
        
    
    /**
     * Convenience class for storing the columns generated by
     * Dantzig-Wolfe and their corresponding parses.
     */
    private static class LambdaVar {
        
        public IloNumVar lambdaVar;
        public int s;
        public int[] parents;
        private int[] sentSol;
        public int colind;
        
        public LambdaVar(IloNumVar lambdaVar, int s, int[] parents, int[] sentSol, int colind) {
            super();
            this.lambdaVar = lambdaVar;
            this.s = s;
            this.parents = parents;
            this.sentSol = sentSol;
            // TODO: If we start removing LambdaVars, then these column indices are wrong!!
            this.colind = colind;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + Arrays.hashCode(parents);
            result = prime * result + s;
            return result;
        }

        /**
         * Two LambdaVar objects are equal if their sentence index, s, and their
         * parents array are equal.
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            LambdaVar other = (LambdaVar) obj;
            if (!Arrays.equals(parents, other.parents))
                return false;
            if (s != other.s)
                return false;
            return true;
        }
                
    }

    protected void buildModel(IloMPModeler cplex, DmvSolution initFeasSol) throws IloException {
        this.bounds = new DmvBounds(this.idm);

        mp = new MasterProblem();
        
        // ----- row-wise modeling -----
        // Add x_0 constraints in the original model space first

        int numConds = idm.getNumConds();

        // Create the objective
        mp.objective = cplex.addMinimize();
        // Create the objective variables, adding them to the objective
        mp.objVars = new IloNumVar[numConds][];
        for (int c = 0; c < numConds; c++) {
            int numParams = idm.getNumParams(c);
            mp.objVars[c] = new IloNumVar[numParams];
            for (int m=0; m<numParams; m++) {
                mp.objVars[c][m] = cplex.numVar(-Double.MAX_VALUE, Double.MAX_VALUE, String.format("z_{%d,%d}",c,m));
                // Negate the objVars since we are minimizing
                cplex.setLinearCoef(mp.objective, -1.0, mp.objVars[c][m]);
            }
        }
        
        // Add the coupling constraints considering only the model parameters
        // aka. the relaxed-objective-coupling-constraints
        mp.couplConsLower = new IloRange[numConds][];
        mp.couplConsUpper = new IloRange[numConds][];
        for (int c = 0; c < numConds; c++) {
            int numParams = idm.getNumParams(c);
            mp.couplConsLower[c] = new IloRange[numParams];
            mp.couplConsUpper[c] = new IloRange[numParams];
            for (int m=0; m<numParams; m++) {
                String name;
                
                // Add the lower coupling constraint
                IloNumVar slackVarLower = cplex.numVar(-Double.MAX_VALUE, 0.0, String.format("slackVarLower_{%d,%d}",c,m));
                name = String.format("ccLb(%d,%d)", c, m);   
                double maxFreqCm = idm.getTotalMaxFreqCm(c,m);
                IloNumExpr rhsLower = cplex.diff(slackVarLower, mp.objVars[c][m]);
                mp.couplConsLower[c][m] = cplex.eq(maxFreqCm * bounds.getLb(c,m), rhsLower, name);
                
                // Add the upper coupling constraint
                IloNumVar slackVarUpper = cplex.numVar(-Double.MAX_VALUE, 0.0, String.format("slackVarUpper_{%d,%d}",c,m));
                name = String.format("ccUb(%d,%d)", c, m);
                IloNumExpr rhsUpper = cplex.sum(cplex.prod(-1.0, mp.objVars[c][m]), slackVarUpper);
                mp.couplConsUpper[c][m] = cplex.eq(0.0, rhsUpper, name);
            }
        }        
        // We need the lower coupling constraints (and the upper) to each 
        // be added in sequence to the master problem. So we add all the upper
        // constraints afterwards
        mp.lpMatrix = cplex.addLPMatrix("couplingMatrix");
        for (int c = 0; c < numConds; c++) {
            mp.lpMatrix.addRows(mp.couplConsLower[c]);
        }
        for (int c = 0; c < numConds; c++) {
            mp.lpMatrix.addRows(mp.couplConsUpper[c]);
        }

        // ----- column-wise modeling -----

        // Create the lambda sum to one constraints
        mp.lambdaSumCons = new IloRange[sentences.size()];
        mp.lambdaVars = new ArrayList<LambdaVar>();
        mp.lambdaVarSet = new HashSet<LambdaVar>();

        addFeasibleSolution(initFeasSol);
    }

    @Override
    public void addFeasibleSolution(DmvSolution feasSol) {
        try {
            int numConds = idm.getNumConds();

            // Add the initial feasible parse as the first lambda columns
            for (int s = 0; s < sentences.size(); s++) {
                DepTree tree = feasSol.getTreebank().get(s);
                addLambdaVar(s, tree);
            }

            // Create the gamma sum to one constraints
            mp.gammaVars = new ArrayList<GammaVar>();
            // Add the initial feasible solution as the first gamma column
            double[][] initLogProbs = feasSol.getLogProbs();
            for (int c = 0; c < numConds; c++) {
                // Project the initial solution onto the feasible region
                double[] params = Vectors.getExp(initLogProbs[c]);
                params = projections.getProjectedParams(bounds, c, params);
                if (params == null) {
                    throw new IllegalStateException("The initial bounds are infeasible");
                }
                initLogProbs[c] = Vectors.getLog(params);
            }
            addGammaVar(initLogProbs);

        } catch (IloException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean addGammaVar(double[][] logProbs) throws IloException {
        GammaVar gvTemp = new GammaVar(null, logProbs);
        if (mp.gammaVars.contains(gvTemp)) {
            // Don't add the duplicate, since this probably just means its reduced cost is really close to zero
            return false;
        }
        
        IloNumVar gammaVar = cplex.numVar(0.0, Double.MAX_VALUE, String.format("gamma^{%d}", numGammas++));
        
        // Add the gamma var to the relaxed-objective-coupling-constraints
        // matrix
        int[] ind = new int[idm.getNumNonZeroMaxFreqCms()];
        double[] val = new double[idm.getNumNonZeroMaxFreqCms()];
        int j = 0;
        for (int c = 0; c < idm.getNumConds(); c++) {
            for (int m = 0; m < idm.getNumParams(c); m++) {
                // Only update non zero rows
                int totMaxFreqCm = idm.getTotalMaxFreqCm(c, m);
                if (totMaxFreqCm > 0) {
                    // Add to the lower coupling constraint
                    ind[j] = mp.lpMatrix.getIndex(mp.couplConsLower[c][m]);
                    val[j] = totMaxFreqCm * logProbs[c][m];
                    j++;
                }
            }
        }
        int colind = mp.lpMatrix.addColumn(gammaVar, ind, val);


        // Add the gamma var to its sum to one constraint
        if (mp.gammaSumCons == null) {
            mp.gammaSumCons = cplex.eq(gammaVar, 1.0, "gammaSum");
            mp.lpMatrix.addRow(mp.gammaSumCons);
        } else {
            int rowind = mp.lpMatrix.getIndex(mp.gammaSumCons);
            mp.lpMatrix.setNZ(rowind, colind, 1.0);
        }
        
        GammaVar gv = new GammaVar(gammaVar, logProbs);
        mp.gammaVars.add(gv);
        return true;
    }

    private void removeGammaVar(GammaVar gv)  throws IloException {

        //TODO: remove these printouts
        if (tempDir != null) {
            // TODO: remove this or add a debug flag to the if
            cplex.exportModel(new File(tempDir, "dw.beforeremoval.lp").getAbsolutePath());
        }        
        
        //TODO: this might be wrong
        mp.lpMatrix.removeColumn(mp.lpMatrix.getIndex(gv.gammaVar));
       
        if (tempDir != null) {
            // TODO: remove this or add a debug flag to the if
            cplex.exportModel(new File(tempDir, "dw.afterremoval.lp").getAbsolutePath());
        }
        mp.gammaVars.remove(gv);

    }

    private boolean addLambdaVar(int s, DepTree tree) throws IloException {

        LambdaVar lvTemp = new LambdaVar(null, s, tree.getParents(), null, -1);
        if (mp.lambdaVarSet.contains(lvTemp)) {
            //int lvIdx = mp.lambdaVars.lastIndexOf(lvTemp);
            //log.error("Duplicate Lambda Var: " + lvTemp);
            //throw new IllegalStateException("Duplicate LambdaVar already in the master problem");
            //log.warn("Duplicate LambdaVar already in the master problem");
            
            // Don't add the duplicate, since this probably just means its reduced cost is really close to zero
            return false;
        }
        
        int[] sentSol = idm.getSentSol(sentences.get(s), s, tree);
        int numSentVars = idm.getNumSentVars(s);
        
        IloNumVar lambdaVar = cplex.numVar(0.0, Double.MAX_VALUE, String.format("lambda_{%d}^{%d}", s, numLambdas++));
        
        // Add the lambda var to the relaxed-objective-coupling-constraints matrix
        int[] ind = new int[numSentVars*2];
        double[] val = new double[numSentVars*2];
        int j=0;
        for (int i = 0; i < numSentVars; i++) {
            int c = idm.getC(s, i);
            int m = idm.getM(s, i);
            
            // Add to the lower coupling constraint
            ind[j] = mp.lpMatrix.getIndex(mp.couplConsLower[c][m]);
            val[j] = bounds.getLb(c, m) * sentSol[i];
            j++;
            
            // Add to the upper coupling constraint
            ind[j] = mp.lpMatrix.getIndex(mp.couplConsUpper[c][m]);
            val[j] = bounds.getUb(c, m) * sentSol[i];
            j++;
        }
        int colind = mp.lpMatrix.addColumn(lambdaVar, ind, val);
        

        // Add the lambda var to its sum to one constraint
        if (mp.lambdaSumCons[s] == null) {
            mp.lambdaSumCons[s] = cplex.eq(lambdaVar, 1.0, "lambdaSum");
            mp.lpMatrix.addRow(mp.lambdaSumCons[s]);
        } else {
            int rowind = mp.lpMatrix.getIndex(mp.lambdaSumCons[s]);
            mp.lpMatrix.setNZ(rowind, colind, 1.0);
        }
        
        LambdaVar lv = new LambdaVar(lambdaVar, s, tree.getParents(), sentSol, colind);
        
        mp.lambdaVars.add(lv);
        mp.lambdaVarSet.add(lv);
        
        return true;
    }

    

    protected void printSummary() {
        log.debug("Avg parsing time(ms) per solve: " + Time.totMs(parsingTimer) / getNumSolves());
        log.debug("Avg sum-to-one time(ms) per solve: " + Time.totMs(stoTimer) / getNumSolves());
    }

    protected boolean isFeasible() {
        return !hasInfeasibleBounds && areFeasibleBounds(bounds);
    }
    
    protected SubproblemRetVal addColumns(IloCplex cplex) throws IloException {
        // Get the simplex multipliers (shadow prices).
        // These are shared across all slaves, since each slave
        // has the same D_s matrix.
        double[] pricesLower = cplex.getDuals(mp.lpMatrix, 0, idm.getNumTotalParams());
        double[] pricesUpper = cplex.getDuals(mp.lpMatrix, idm.getNumTotalParams(), idm.getNumTotalParams());
        
        // Compute the parse weights, which will be shared across all subproblems
        int numConds = idm.getNumConds();
        double[][] parseWeights = new double[numConds][];
        int j = 0;
        for (int c = 0; c < numConds; c++) {
            int numParams = idm.getNumParams(c);
            parseWeights[c] = new double[numParams];

            for (int m = 0; m < numParams; m++) {
                // Calculate new model parameter values for parser
                // based on the relaxed-objective-coupling-constraints
                parseWeights[c][m] = (pricesLower[j] * bounds.getLb(c, m) + pricesUpper[j] * bounds.getUb(c, m));
                j++;
                // We want to minimize the following:
                // c^T - q^T D_s = - (pricesLower[m]*bounds.getLb(c,m) + pricesUpper[m]*bounds.getUb(c,m))
                // but we negate this since the parser will try to maximize. In other words:
                // minimize -q^T D_s = maximize q^T D_s
                // 
            }
        }
        DepProbMatrix dpm = idm.getDepProbMatrix(parseWeights);

        // Get the simplex multipliers (shadow prices) for the lambda
        // sentence constraints
        double[] convexLambdaPrices = cplex.getDuals(mp.lambdaSumCons);
           
        // Compute the model parameter weights, used by the model parameters subproblem
        double[][] modelWeights = new double[numConds][];
        j = 0;
        for (int c = 0; c < numConds; c++) {
            int numParams = idm.getNumParams(c);
            modelWeights[c] = new double[numParams];
            for (int m = 0; m < numParams; m++) {
                modelWeights[c][m] = - pricesLower[j] * idm.getTotalMaxFreqCm(c, m);
                j++;
            }
        }
                 
        // Get the simplex multipliers (shadow prices) for the gamma 
        double convexGammaPrice = cplex.getDual(mp.gammaSumCons);
        
        // Keep track of minimum subproblem reduced cost
        double sumReducedCost = 0.0;
        
        // Solve the model parameters subproblem
        int numPositiveGammaRedCosts = 0;
        ModelParamSubproblem mps = new ModelParamSubproblem();
        stoTimer.start();
        Pair<double[][], Double> mPair = mps.solveModelParamSubproblemJOptimizeLogProb(modelWeights, bounds);
        stoTimer.stop();
        if (mPair == null) {
            hasInfeasibleBounds = true;
            return new SubproblemRetVal(0.0, 0, true);
        }
        double[][] logProbs = mPair.get1();
        double mReducedCost = mPair.get2() - convexGammaPrice;
        if (log.isDebugEnabled()) {
            int index = mp.gammaVars.indexOf(new GammaVar(null, logProbs));
            if (index != -1) {
                GammaVar gv = mp.gammaVars.get(index);
                log.debug(String.format("CPLEX redcost=%f, My redcost=%f", cplex.getReducedCost(gv.gammaVar), mReducedCost));
                assert(Utilities.equals(cplex.getReducedCost(gv.gammaVar), mReducedCost, 1e-13));
            }
        }  
        if (mReducedCost < NEGATIVE_REDUCED_COST_TOLERANCE) {
            // Introduce a new gamma variable
            if (addGammaVar(logProbs)) {
                numPositiveGammaRedCosts++;

                if (mp.gammaVars.size() > mp.lpMatrix.getNrows()) {
                    // Remove the non-basic gamma variables 
                    // TODO: remove the gamma variables that price out the highest
                    for (int i=0; i<mp.gammaVars.size(); i++) {
                        GammaVar gv = mp.gammaVars.get(i);
                        BasisStatus bstatus = cplex.getBasisStatus(gv.gammaVar);
                        if (bstatus != BasisStatus.Basic) {
                            removeGammaVar(gv);
                        }
                    }
                    assert(mp.gammaVars.size() > 0);
                }
            }
        }
        sumReducedCost += mReducedCost;
        
        // Solve each parsing subproblem
        DmvCkyParser parser = new DmvCkyParser();

        int numPositiveLambdaRedCosts = 0;
        for (int s = 0; s < sentences.size(); s++) {

            parsingTimer.start();
            Pair<DepTree, Double> pPair = parser.parse(sentences.get(s), dpm);
            parsingTimer.stop();
            DepTree tree = pPair.get1();
            // We must negate pair.get2() since we were just maximizing
            double pReducedCost = -pPair.get2() - convexLambdaPrices[s];

            if (pReducedCost < NEGATIVE_REDUCED_COST_TOLERANCE) {
                // Introduce a new lambda variable
                if (addLambdaVar(s, tree)) {
                    numPositiveLambdaRedCosts++;
                } else {
                    log.warn(String.format("Duplicate Lambda Var: redCost=%f s=%d tree=%s ", pReducedCost, s, tree
                            .getParents().toString()));
                }
            } // else: do nothing
            
            sumReducedCost += pReducedCost;
        }
        if (numPositiveLambdaRedCosts > 0 && sumReducedCost > 1e-6) {
            log.warn("The sum of the reduced costs should be negative: sumPReducedCost = " + sumReducedCost);
        }
        if (numPositiveLambdaRedCosts > 0 || numPositiveGammaRedCosts > 0) {
            log.debug(String.format("Added %d new trees and %d new gammas", numPositiveLambdaRedCosts, numPositiveGammaRedCosts));
        }
        int numPositiveRedCosts = numPositiveLambdaRedCosts + numPositiveGammaRedCosts;
        
        return new SubproblemRetVal(sumReducedCost, numPositiveRedCosts, false);
    }

    protected int addCuts(IloCplex cplex, TDoubleArrayList iterationObjVals,
            ArrayList<Status> iterationStatus, int cut) throws UnknownObjectException, IloException {
        return 0;
    }

    public void reverseApply(DmvBoundsDelta delta) {
        applyDelta(DmvBoundsDelta.getReverse(delta));
    }

    public void forwardApply(DmvBoundsDelta delta) {
        applyDelta(delta);
    }

    private void applyDelta(DmvBoundsDelta delta) {
        try {
            int c = delta.getC();
            int m = delta.getM();
            
            double origLb = bounds.getLb(c, m);
            double origUb = bounds.getUb(c, m);
            double newLb = origLb;
            double newUb = origUb;
            
            // TODO: all the logAdds should be relegated to the Bounds Delta Factory
            if (delta.getLu() == Lu.LOWER) {
                newLb = origLb + delta.getDelta();
            } else if (delta.getLu() == Lu.UPPER) {
                newUb = origUb + delta.getDelta();
            } else {
                throw new IllegalStateException();
            }

            assert newLb <= newUb : String.format("l,u = %f, %f", newLb, newUb);
            
            // Updates the bounds of the model parameters
            bounds.set(c, m, newLb, newUb);

            // Update lambda column if it uses parameter c,m
            TIntArrayList rowind = new TIntArrayList();
            TIntArrayList colind = new TIntArrayList();
            TDoubleArrayList val = new TDoubleArrayList();
            int lowCmInd = mp.lpMatrix.getIndex(mp.couplConsLower[c][m]);
            int upCmInd = mp.lpMatrix.getIndex(mp.couplConsUpper[c][m]);
            for (LambdaVar lv : mp.lambdaVars) {
                int i = idm.getSi(lv.s, c, m);
                if (i != -1) {
                    // Using cplex.setLinearCoef() is horridly slow. Some suggestions for how to make modification 
                    // of the problem faster here:
                    // https://www.ibm.com/developerworks/forums/thread.jspa?threadID=324926

                    // Update the lower coupling constraint coefficient
                    rowind.add(lowCmInd);
                    colind.add(lv.colind);
                    val.add(bounds.getLb(c, m) * lv.sentSol[i]);
                    // Update the upper coupling constraint coefficient
                    rowind.add(upCmInd);
                    colind.add(lv.colind);
                    val.add(bounds.getUb(c, m) * lv.sentSol[i]);
                }
            }
            if (rowind.size() > 0) {
                mp.lpMatrix.setNZs(rowind.toNativeArray(), colind.toNativeArray(), val.toNativeArray());
            }
            
            // Reset this flag
            hasInfeasibleBounds = false;
            
            // The gamma columns may need to be projected back onto the feasible region 
            for (int i=0; i<mp.gammaVars.size(); i++) {
                GammaVar gv = mp.gammaVars.get(i);
                if (gv.logProbs[c][m] < newLb || newUb < gv.logProbs[c][m]) {
                    // TODO: This isn't blazing fast, but we could make it faster if necessary
                    double[] params = Vectors.getExp(gv.logProbs[c]);
                    params = projections.getProjectedParams(bounds, c, params);
                    if (params == null) {
                        //throw new IllegalStateException("The bounds are infeasible");
                        hasInfeasibleBounds = true;
                        break;
                    }
                    for (int mm=0; mm<params.length; mm++) {
                        assert params[mm] >= 0 : String.format("params[%d] = %g", mm, params[mm]);
                    }
                    double[] lps = Vectors.getLog(params);
                    gv.logProbs[c] = lps;
                    
                    // Update gamma var column  
                    updateGammaVar(gv, c);
                }
            }
        } catch (IloException e) {
            throw new RuntimeException(e);
        }
    }
    
    private void updateGammaVar(GammaVar gv, int c) throws IloException {
        TIntArrayList rowind = new TIntArrayList();
        TIntArrayList colind = new TIntArrayList();
        TDoubleArrayList val = new TDoubleArrayList();

        int colindForGv = mp.lpMatrix.getIndex(gv.gammaVar);

        for (int m = 0; m < idm.getNumParams(c); m++) {
            // Only update non zero rows
            int totMaxFreqCm = idm.getTotalMaxFreqCm(c, m);
            if (totMaxFreqCm > 0) {
                // Add to the lower coupling constraint
                rowind.add(mp.lpMatrix.getIndex(mp.couplConsLower[c][m]));
                colind.add(colindForGv);
                val.add(totMaxFreqCm * gv.logProbs[c][m]);
            }
        }
        if (rowind.size() > 0) {
            mp.lpMatrix.setNZs(rowind.toNativeArray(), colind.toNativeArray(), val.toNativeArray());
        }
    }
    
    private boolean areFeasibleBounds(DmvBounds bounds) {
        // Check that the upper bounds sum to at least 1.0
        for (int c=0; c<idm.getNumConds(); c++) {
            double logSum = Double.NEGATIVE_INFINITY;
            int numParams = idm.getNumParams(c);
            // Sum the upper bounds
            for (int m = 0; m < numParams; m++) {
                logSum = Utilities.logAdd(logSum, bounds.getUb(c, m));
            }
            
            if (logSum < -1e-10) {
                // The problem is infeasible
                return false;
            }
        }
        
        // Check that the lower bounds sum to no more than 1.0
        for (int c=0; c<idm.getNumConds(); c++) {
            double logSum = Double.NEGATIVE_INFINITY;
            int numParams = idm.getNumParams(c);
            // Sum the lower bounds
            for (int m = 0; m < numParams; m++) {
                logSum = Utilities.logAdd(logSum, bounds.getLb(c, m));
            }
            
            if (logSum > 1e-10) {
                // The problem is infeasible
                return false;
            }
        }
        return true;
    }

    public double computeTrueObjective(double[][] logProbs, DepTreebank treebank) {
        double score = 0.0;
        for (int s = 0; s < sentences.size(); s++) {
            Sentence sentence = sentences.get(s);
            DepTree tree = treebank.get(s);
            int[] sentSol = idm.getSentSol(sentence, s, tree);
            for (int i=0; i<sentSol.length; i++) {
                int c = idm.getC(s, i);
                int m = idm.getM(s, i);
                if (sentSol[i] != 0) {
                    // This if-statement is to ensure that 0 * -inf == 0.
                    score += sentSol[i] * logProbs[c][m];
                }
                assert (!Double.isNaN(score));
            }
        }
        return score;
    }

    public DmvBounds getBounds() {
        return bounds;
    }

    public IndexedDmvModel getIdm() {
        return idm;
    }
    
}
