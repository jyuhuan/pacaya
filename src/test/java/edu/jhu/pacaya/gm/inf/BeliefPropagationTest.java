package edu.jhu.pacaya.gm.inf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

import edu.jhu.pacaya.gm.inf.BeliefPropagation.BpScheduleType;
import edu.jhu.pacaya.gm.inf.BeliefPropagation.BpUpdateOrder;
import edu.jhu.pacaya.gm.inf.BeliefPropagation.BeliefPropagationPrm;
import edu.jhu.pacaya.gm.model.ExplicitFactor;
import edu.jhu.pacaya.gm.model.Factor;
import edu.jhu.pacaya.gm.model.FactorGraph;
import edu.jhu.pacaya.gm.model.FactorGraphsForTests;
import edu.jhu.pacaya.gm.model.Var;
import edu.jhu.pacaya.gm.model.Var.VarType;
import edu.jhu.pacaya.gm.model.VarConfig;
import edu.jhu.pacaya.gm.model.VarSet;
import edu.jhu.pacaya.gm.model.VarTensor;
import edu.jhu.pacaya.gm.model.globalfac.GlobalExplicitFactor;
import edu.jhu.pacaya.gm.model.globalfac.GlobalFactor;
import edu.jhu.pacaya.util.collections.QLists;
import edu.jhu.pacaya.util.semiring.Algebra;
import edu.jhu.pacaya.util.semiring.LogSemiring;
import edu.jhu.pacaya.util.semiring.LogSignAlgebra;
import edu.jhu.pacaya.util.semiring.RealAlgebra;
import edu.jhu.pacaya.util.semiring.ShiftedRealAlgebra;
import edu.jhu.pacaya.util.semiring.SplitAlgebra;


public class BeliefPropagationTest {
	
    @Test
    public void testOnOneVarProb() {
        testOneVarHelper(RealAlgebra.getInstance());
    }
    
    @Test
    public void testOnOneVarLogProb() {
        Algebra s = LogSemiring.getInstance();
        testOneVarHelper(s);
    }

    private void testOneVarHelper(Algebra s) {
        FactorGraph fg = FactorGraphsForTests.getOneVarFg();
        
        BruteForceInferencer bf = new BruteForceInferencer(fg, s);
        bf.run();

        BeliefPropagationPrm prm = new BeliefPropagationPrm();
        prm.maxIterations = 10;
        prm.s = s;
        BeliefPropagation bp = new BeliefPropagation(fg, prm);
        bp.run();

        assertEqualMarginals(fg, bf, bp);
    }

    @Test
    public void testTwoVars() {
        runTwoVars(RealAlgebra.getInstance(), null);
        runTwoVars(LogSemiring.getInstance(), null);
    }

    @Test
    public void testDumpingOfBeliefsForDebugging() {
        runTwoVars(RealAlgebra.getInstance(), Paths.get("./tmp/bpDump"));
        // No assertions, just make sure we don't fail.
    }

    private void runTwoVars(Algebra s, Path dumpDir) {
        FactorGraph fg = new FactorGraph();
        Var t0 = new Var(VarType.PREDICTED, 2, "t0", null);
        Var t1 = new Var(VarType.PREDICTED, 2, "t1", null);

        ExplicitFactor emit0 = new ExplicitFactor(new VarSet(t0)); 
        emit0.setValue(0, 1.1);
        emit0.setValue(1, 1.9);

        ExplicitFactor tran0 = new ExplicitFactor(new VarSet(t0, t1)); 
        tran0.fill(1);
        tran0.setValue(0, 2.2);
        tran0.setValue(1, 2.3);
        tran0.setValue(2, 2.4);
        tran0.setValue(3, 2.5);
        
        fg.addFactor(emit0);
        fg.addFactor(tran0);
        
        for (Factor f : fg.getFactors()) {
            ((VarTensor)f).convertRealToLog();
        }
        
        BruteForceInferencer bf = new BruteForceInferencer(fg, s);
        bf.run();

        BeliefPropagationPrm prm = new BeliefPropagationPrm();
        prm.maxIterations = 10;
        prm.s = s;
        prm.dumpDir = dumpDir;
        BeliefPropagation bp = new BeliefPropagation(fg, prm);
        bp.run();

        assertEqualMarginals(fg, bf, bp);
    }
    
    @Test
    public void testThreeConnectedComponents() {
        Algebra s = LogSemiring.getInstance();
        
        FactorGraph fg = getThreeConnectedComponentsFactorGraph();
        
        BruteForceInferencer bf = new BruteForceInferencer(fg, s);
        bf.run();

        BeliefPropagation bp = getDefaultBpForAcyclic(s, fg);
        bp.run();

        assertEqualMarginals(fg, bf, bp);
    }

    public static FactorGraph getThreeConnectedComponentsFactorGraph() {
        FactorGraph fg = new FactorGraph();
        
        // Create three tags.
        Var t0 = new Var(VarType.PREDICTED, 2, "t0", QLists.getList("N", "V"));
        Var t1 = new Var(VarType.PREDICTED, 2, "t1", QLists.getList("N", "V"));
        Var t2 = new Var(VarType.PREDICTED, 2, "t2", QLists.getList("N", "V"));
        
        // Emission factors. 
        ExplicitFactor emit0 = new ExplicitFactor(new VarSet(t0));; 
        ExplicitFactor emit1 = new ExplicitFactor(new VarSet(t1));; 
        ExplicitFactor emit2 = new ExplicitFactor(new VarSet(t2));; 

        emit0.setValue(0, 0.1);
        emit0.setValue(1, 0.9);
        emit1.setValue(0, 3);
        emit1.setValue(1, 7);
        emit2.setValue(0, 1);
        emit2.setValue(1, 1);
                
        fg.addFactor(emit0);
        fg.addFactor(emit1);
        fg.addFactor(emit2);

        for (Factor f : fg.getFactors()) {
            ((VarTensor)f).convertRealToLog();
        }
        
        return fg;
    }
    
    @Test
    public void testOnSimpleProb() throws IOException {
        // Test in the probability domain.
        Algebra s = RealAlgebra.getInstance();
        testOnSimpleHelper(s);
    }
    
    @Test
    public void testOnSimpleLogProb() throws IOException {
        // Test in the log-probability domain.
        Algebra s = LogSemiring.getInstance();        
        testOnSimpleHelper(s);
    }

    private void testOnSimpleHelper(Algebra s) throws IOException {
        FactorGraph fg = BruteForceInferencerTest.readSimpleFg();
        BruteForceInferencer bf = new BruteForceInferencer(fg, s);
        bf.run();

        BeliefPropagationPrm prm = getDefaultBpPrmLoopy(s);
        BeliefPropagation bp = new BeliefPropagation(fg, prm);
        bp.run();

        //BruteForceInferencerTest.testInfOnSimpleGraph(fg, bp, s);

        // TODO: unfortunately, loopy BP does very poorly on this simple example
        // and does not converge to the correct marginals. Hence we use a (very
        // high) tolerance of 2 to catch the partition function's value.
        assertEqualMarginals(fg, bf, bp, 2);
    }

    protected BeliefPropagationPrm getDefaultBpPrmLoopy(Algebra s) {
        BeliefPropagationPrm prm = new BeliefPropagationPrm();
        prm.maxIterations = 10;
        prm.s = s;
        prm.normalizeMessages = true;
        return prm;
    }
    
    @Test
    public void testMultipleSemiringsOnSimple() throws IOException {
        FactorGraph fg = BruteForceInferencerTest.readSimpleFg();

        BeliefPropagation bpReal = runHelper(fg, RealAlgebra.getInstance());
        BeliefPropagation bpSplit = runHelper(fg, SplitAlgebra.getInstance());
        BeliefPropagation bpShift = runHelper(fg, ShiftedRealAlgebra.getInstance());
        BeliefPropagation bpLog = runHelper(fg, LogSemiring.getInstance());
        BeliefPropagation bpLogSign = runHelper(fg, LogSignAlgebra.getInstance());
        
        assertEqualMarginals(fg, bpReal, bpSplit, 1e-4);
        assertEqualMarginals(fg, bpReal, bpShift, 1e-13);
        assertEqualMarginals(fg, bpReal, bpLog, 1e-13);
        assertEqualMarginals(fg, bpReal, bpLogSign, 1e-13);
        assertEqualMarginals(fg, bpLog, bpLogSign, 1e-13);
    }

    private BeliefPropagation runHelper(FactorGraph fg, Algebra s) throws IOException {        
        BeliefPropagationPrm prm = new BeliefPropagationPrm();
        prm.maxIterations = 4;
        prm.s = s;
        prm.normalizeMessages = true;
        prm.updateOrder = BpUpdateOrder.PARALLEL;
        BeliefPropagation bp = new BeliefPropagation(fg, prm);
        bp.run();
        return bp;
    }

    @Test
    public void testOnChainProb() {
        // Test in the probability domain.
        Algebra s = RealAlgebra.getInstance();
        testOnChainHelper(s);
    }

    @Test
    public void testOnChainLogProb() {
        // Test in the log-probability domain.
        Algebra s = LogSemiring.getInstance();        
        testOnChainHelper(s);
    }

    private void testOnChainHelper(Algebra s) {
        FactorGraph fg = BruteForceInferencerTest.getLinearChainGraph();
        BruteForceInferencer bf = new BruteForceInferencer(fg, s);
        bf.run();

        BeliefPropagation bp = getDefaultBpForAcyclic(s, fg);
        bp.run();

        BruteForceInferencerTest.testInfOnLinearChainGraph(fg, bp);
                    
        assertEqualMarginals(fg, bf, bp);
    }

    protected BeliefPropagation getDefaultBpForAcyclic(Algebra s, FactorGraph fg) {
        BeliefPropagationPrm prm = getDefaultBpPrmForAcyclic(s);
        BeliefPropagation bp = new BeliefPropagation(fg, prm);
        return bp;
    }

    protected BeliefPropagationPrm getDefaultBpPrmForAcyclic(Algebra s) {
        BeliefPropagationPrm prm = new BeliefPropagationPrm();
        prm.maxIterations = 1;
        prm.s = s;
        prm.schedule = BpScheduleType.TREE_LIKE;
        prm.updateOrder = BpUpdateOrder.SEQUENTIAL;
        // Don't normalize the messages, so that the partition function is the
        // same as in the brute force approach.
        prm.normalizeMessages = false;
        return prm;
    }
    
    @Test
    public void testConvergence() {
        // Test with a threshold of 0 (i.e. exact equality implies convergence)
        testConvergenceHelper(LogSemiring.getInstance(), 1e-13, 6);
        testConvergenceHelper(RealAlgebra.getInstance(), 0, 6);
        // Test with a threshold of 1e-3 (i.e. fewer iterations, 5, to convergence)
        testConvergenceHelper(LogSemiring.getInstance(), 1e-3, 5);
        testConvergenceHelper(RealAlgebra.getInstance(), 1e-3, 5);
    }

    private void testConvergenceHelper(Algebra s, double convergenceThreshold, int expectedConvergenceIterations) {
        FactorGraph fg = BruteForceInferencerTest.getLinearChainGraph();

        BruteForceInferencer bf = new BruteForceInferencer(fg, s);
        bf.run();
        
        BeliefPropagationPrm prm = new BeliefPropagationPrm();
        prm.maxIterations = 1;
        prm.s = s;
        prm.normalizeMessages = true;
        prm.updateOrder = BpUpdateOrder.PARALLEL;
        prm.convergenceThreshold = convergenceThreshold;        
        
        BeliefPropagation bp = null;
        
        for (int i=0; i<20; i++) {
            prm.maxIterations = i;
            bp = new BeliefPropagation(fg, prm);
            bp.run();
            System.out.println("maxiters: " + i);
            System.out.println("isConverged: " + bp.isConverged());
            if (bp.isConverged()) {
                assertEqualMarginals(fg, bf, bp, convergenceThreshold + 1e-13);
                assertTrue(prm.maxIterations >= expectedConvergenceIterations);
            } else {
                assertTrue(prm.maxIterations < expectedConvergenceIterations);
                try {
                    assertEqualMarginals(fg, bf, bp);
                    fail("Marginals should not be equal");
                } catch (AssertionError e) {
                    // pass
                }
            }
        }
        assertTrue(bp.isConverged());
    }
    
    @Test
    public void testCanHandleProbHardFactors() {
        testCanHandleHardFactorsHelper(false, false, RealAlgebra.getInstance());
        testCanHandleHardFactorsHelper(true, false, RealAlgebra.getInstance());
        testCanHandleHardFactorsHelper(false, true, RealAlgebra.getInstance());
        testCanHandleHardFactorsHelper(true, true, RealAlgebra.getInstance());
    }
    
    @Test
    public void testCanHandleLogHardFactors() {
        testCanHandleHardFactorsHelper(false, false, LogSemiring.getInstance());
        testCanHandleHardFactorsHelper(true, false, LogSemiring.getInstance());
        testCanHandleHardFactorsHelper(false, true, LogSemiring.getInstance());
        testCanHandleHardFactorsHelper(true, true, LogSemiring.getInstance());
    }    
    
    public void testCanHandleHardFactorsHelper(boolean cacheVarBeliefs, boolean cacheFacBeliefs, Algebra s) {     
        BeliefPropagationPrm prm = new BeliefPropagationPrm();
        prm.maxIterations = 1;
        prm.schedule = BpScheduleType.TREE_LIKE;
        prm.updateOrder = BpUpdateOrder.SEQUENTIAL;
        prm.s = s;
        prm.minVarNbsForCache = cacheVarBeliefs ? 0 : Integer.MAX_VALUE;
        prm.minFacNbsForCache = cacheFacBeliefs ? 0 : Integer.MAX_VALUE;
        
        Var x0 = new Var(VarType.PREDICTED, 2, "x0", null);
        Var x1 = new Var(VarType.PREDICTED, 2, "x1", null);
        
        ExplicitFactor xor = new ExplicitFactor(new VarSet(x0, x1));
        for(int cfg=0; cfg < xor.getVars().calcNumConfigs(); cfg++) {
            VarConfig vCfg = xor.getVars().getVarConfig(cfg);
            int v0 = vCfg.getState(x0);
            int v1 = vCfg.getState(x1);
            if(v0 != v1) {
                xor.setValue(cfg, 0d);
            } else {
                xor.setValue(cfg, 1d);
            }
        }
        xor.convertRealToLog();
        
        FactorGraph fg = new FactorGraph();
        fg.addFactor(xor);
        
        {
            // should have uniform mass
            BruteForceInferencer bf = new BruteForceInferencer(fg, s);
            bf.run();
            BeliefPropagation bp = new BeliefPropagation(fg, prm);
            bp.run();
            System.out.println(bp.isConverged());
            assertEqualMarginals(fg, bf, bp);
            
            VarTensor x0_marg = bp.getMarginals(x0);
            assertEquals(0.5d, x0_marg.getValue(0), 1e-6);
            assertEquals(0.5d, x0_marg.getValue(1), 1e-6);
            VarTensor x1_marg = bp.getMarginals(x1);
            assertEquals(0.5d, x1_marg.getValue(0), 1e-6);
            assertEquals(0.5d, x1_marg.getValue(1), 1e-6);                
        }
        
        // check again once we've added some unary factors on x0 and x1
        ExplicitFactor f0 = new ExplicitFactor(new VarSet(x0));
        f0.setValue(0, 3d);
        f0.setValue(1, 2d);
        f0.convertRealToLog();
        fg.addFactor(f0);
        
        ExplicitFactor f1 = new ExplicitFactor(new VarSet(x1));
        f1.setValue(0, 4d);
        f1.setValue(1, 1d);
        f1.convertRealToLog();
        fg.addFactor(f1);
        
        {
            BruteForceInferencer bf = new BruteForceInferencer(fg, s);
            bf.run();
            BeliefPropagation bp = new BeliefPropagation(fg, prm);
            bp.run();
            System.out.println(bp.isConverged());
            System.out.println(bf.getMarginals(x0));
            System.out.println(bp.getMarginals(x0));
            System.out.println(bf.getMarginals(x1));
            System.out.println(bp.getMarginals(x1));
            assertEquals(3, fg.getFactors().size());
            assertTrue(fg.getBipgraph().isAcyclic());
            assertEqualMarginals(fg, bf, bp);
        }
    }    

    @Test
    public void testDividingOutTrick() throws Exception {
        helpDividingOutTrickLinearChain(LogSemiring.getInstance(), Integer.MAX_VALUE, Integer.MAX_VALUE); // no dividing out
        helpDividingOutTrickLinearChain(LogSemiring.getInstance(), 0, Integer.MAX_VALUE); // var only
        helpDividingOutTrickLinearChain(LogSemiring.getInstance(), Integer.MAX_VALUE, 0); // fac only
        helpDividingOutTrickLinearChain(LogSemiring.getInstance(), 0, 0); // both
        helpDividingOutTrickSimple(LogSemiring.getInstance(), Integer.MAX_VALUE, Integer.MAX_VALUE); // no dividing out
        helpDividingOutTrickSimple(LogSemiring.getInstance(), 0, Integer.MAX_VALUE); // var only
        helpDividingOutTrickSimple(LogSemiring.getInstance(), Integer.MAX_VALUE, 0); // fac only
        helpDividingOutTrickSimple(LogSemiring.getInstance(), 0, 0); // both
    }

    private void helpDividingOutTrickLinearChain(Algebra s, int minVarNbsForCache, int minFacNbsForCache) {
        FactorGraph fg = BruteForceInferencerTest.getLinearChainGraph();
        BruteForceInferencer bf = new BruteForceInferencer(fg, s);
        bf.run();
        BeliefPropagationPrm prm = getDefaultBpPrmForAcyclic(s);
        prm.minVarNbsForCache = minVarNbsForCache;
        prm.minFacNbsForCache = minFacNbsForCache;
        BeliefPropagation bp = new BeliefPropagation(fg, prm);        
        bp.run();
        BruteForceInferencerTest.testInfOnLinearChainGraph(fg, bp);                    
        assertEqualMarginals(fg, bf, bp);
    }

    private void helpDividingOutTrickSimple(Algebra s, int minVarNbsForCache, int minFacNbsForCache) throws IOException {
        FactorGraph fg = BruteForceInferencerTest.readSimpleFg();
        BruteForceInferencer bf = new BruteForceInferencer(fg, s);
        bf.run();
        BeliefPropagationPrm prm1 = getDefaultBpPrmLoopy(s);
        BeliefPropagation bp1 = new BeliefPropagation(fg, prm1);        
        bp1.run();
        BeliefPropagationPrm prm2 = getDefaultBpPrmLoopy(s);
        prm2.minVarNbsForCache = minVarNbsForCache;
        prm2.minFacNbsForCache = minFacNbsForCache;
        BeliefPropagation bp2 = new BeliefPropagation(fg, prm2);        
        bp2.run();
        assertEqualMarginals(fg, bp1, bp2);
    }
    
    @Test
    public void testGlobalExplicitFactor() throws IOException {
        FactorGraph fg = getThreeConnectedComponentsFactorGraph();
        
        VarSet allVars = new VarSet(fg.getVars().toArray(new Var[0]));
        ExplicitFactor gf = new GlobalExplicitFactor(allVars);
        gf.setValue(0, 2);
        gf.setValue(1, 3);
        gf.setValue(2, 5);
        gf.setValue(3, 7);
        gf.setValue(4, 11);
        gf.setValue(5, 15);
        gf.setValue(6, 19);
        gf.setValue(7, 23);
        gf.convertRealToLog();
        
        fg.addFactor(gf);

        Algebra s = LogSemiring.getInstance();
        BruteForceInferencer bf = new BruteForceInferencer(fg, s);
        bf.run();
        BeliefPropagation bp = getDefaultBpForAcyclic(s, fg);
        bp.run();

        System.out.println(bp.isConverged());
        assertEqualMarginals(fg, bf, bp, 1e-12);
    }

    public static void assertEqualMarginals(FactorGraph fg, FgInferencer bf,
            FgInferencer bp) {
        assertEqualMarginals(fg, bf, bp, 1e-13);
    }

    public static void assertEqualMarginals(FactorGraph fg, FgInferencer bf,
            FgInferencer bp, double tolerance) {
        assertEqualMarginals(fg, bf, bp, tolerance, true);
    }
    
    public static void assertEqualMarginals(FactorGraph fg, FgInferencer bf,
            FgInferencer bp, double tolerance, boolean compareGlobalFactors) {
        for (Var var : fg.getVars()) {
            {
                VarTensor bfm = bf.getMarginals(var);
                VarTensor bpm = bp.getMarginals(var);
                if (!bfm.equals(bpm, tolerance)) {
                    assertEquals(bfm, bpm);
                }
            }
            {
                VarTensor bfm = bf.getLogMarginals(var);
                VarTensor bpm = bp.getLogMarginals(var);
                if (!bfm.equals(bpm, tolerance)) {
                    assertEquals(bfm, bpm);
                }
            }
        }
        for (Factor f : fg.getFactors()) {
            if (!compareGlobalFactors && f instanceof GlobalFactor) {
                continue;
            }
            {
                VarTensor bfm = bf.getMarginals(f);
                VarTensor bpm = bp.getMarginals(f);
                if (!bfm.equals(bpm, tolerance)) {
                    assertEquals(bfm, bpm);
                }
            }
            {
                VarTensor bfm = bf.getLogMarginals(f);
                VarTensor bpm = bp.getLogMarginals(f);
                if (!bfm.equals(bpm, tolerance)) {
                    assertEquals(bfm, bpm);
                }
            }
        }
        assertEquals(bf.getPartition(), bp.getPartition(), tolerance);
        assertEquals(bf.getLogPartition(), bp.getLogPartition(), tolerance);
    }
    
}
