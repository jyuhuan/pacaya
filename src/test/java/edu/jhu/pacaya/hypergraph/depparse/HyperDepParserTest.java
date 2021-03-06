package edu.jhu.pacaya.hypergraph.depparse;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import edu.jhu.pacaya.hypergraph.Hyperalgo.Scores;
import edu.jhu.pacaya.parse.dep.DepIoChart;
import edu.jhu.pacaya.util.semiring.Algebra;
import edu.jhu.pacaya.util.semiring.LogSemiring;
import edu.jhu.pacaya.util.semiring.LogSignAlgebra;
import edu.jhu.pacaya.util.semiring.RealAlgebra;
import edu.jhu.prim.arrays.DoubleArrays;
import edu.jhu.prim.tuple.Pair;
import edu.jhu.prim.util.math.FastMath;

public class HyperDepParserTest {

    @Test
    public void testInsideOutsideSingleRoot1() {
        double[] root = new double[] {1, 2, 3}; 
        double[][] child = new double[][]{ {0, 4, 5}, {6, 0, 7}, {8, 9, 0} };
        
        DoubleArrays.log(root);
        DoubleArrays.log(child);
        
        DepIoChart chart = HyperDepParser.insideOutsideSingleRoot(root,  child);
        
        // Check inside scores.
        assertEquals(7, FastMath.exp(chart.getLogInsideScore(1, 2)), 1e-13);
        assertEquals(9, FastMath.exp(chart.getLogInsideScore(2, 1)), 1e-13);
        assertEquals(45+20, FastMath.exp(chart.getLogInsideScore(0, 2)), 1e-13);
        assertEquals(45+28+20, FastMath.exp(chart.getLogInsideScore(-1, 0)), 1e-10);
        assertEquals(84, FastMath.exp(chart.getLogInsideScore(-1, 1)), 1e-13);
        assertEquals(8*9+8*4, FastMath.exp(chart.getLogInsideScore(2, 0)), 1e-10);
        assertEquals(162+216+96, FastMath.exp(chart.getLogInsideScore(-1, 2)), 1e-3);
        
        // Check partition function.
        assertEquals(45+28+20+84+162+216+96, FastMath.exp(chart.getLogPartitionFunction()), 1e-3);
        
        // Check outside scores.
        assertEquals(1*4+2*6, FastMath.exp(chart.getLogOutsideScore(1, 2)), 1e-13);
        assertEquals(1*5+3*6+3*8, FastMath.exp(chart.getLogOutsideScore(2, 1)), 1e-13); // why is this 3*6 and not just 3?
        assertEquals(1, FastMath.exp(chart.getLogOutsideScore(0, 2)), 1e-13);
        assertEquals(1, FastMath.exp(chart.getLogOutsideScore(-1, 0)), 1e-13);
        assertEquals(1, FastMath.exp(chart.getLogOutsideScore(-1, 1)), 1e-13);
        assertEquals(1, FastMath.exp(chart.getLogOutsideScore(-1, 2)), 1e-3);
        assertEquals(2*7+3*9, FastMath.exp(chart.getLogOutsideScore(1, 0)), 1e-3);

        // Check sums.
        assertEquals(28+84, FastMath.exp(chart.getLogSumOfPotentials(1, 2)), 1e-3);
        assertEquals(45+162+216, FastMath.exp(chart.getLogSumOfPotentials(2, 1)), 1e-3);
        assertEquals(28+20+96, FastMath.exp(chart.getLogSumOfPotentials(0, 1)), 1e-3);
        assertEquals(96+216, FastMath.exp(chart.getLogSumOfPotentials(2, 0)), 1e-3);
        
        // Check expected counts.
        double Z = 45+28+20+84+162+216+96;
        assertEquals((28+84)/Z, FastMath.exp(chart.getLogExpectedCount(1, 2)), 1e-3);
        assertEquals((45+162+216)/Z, FastMath.exp(chart.getLogExpectedCount(2, 1)), 1e-3);
        assertEquals((28+20+96)/Z, FastMath.exp(chart.getLogExpectedCount(0, 1)), 1e-3);
        assertEquals((96+216)/Z, FastMath.exp(chart.getLogExpectedCount(2, 0)), 1e-3);        
    }    
    
    @Test
    public void testInsideOutsideSingleRoot2() {
        double[] root = new double[] {1, 1, 1}; 
        double[][] child = new double[][]{ {0, 1, 1}, {1, 0, 1}, {1, 1, 0} };
        
        DoubleArrays.log(root);
        DoubleArrays.log(child);
        
        DepIoChart chart = HyperDepParser.insideOutsideSingleRoot(root,  child);

        // Check partition function.
        assertEquals(7, FastMath.exp(chart.getLogPartitionFunction()), 1e-3);
        
        // Check inside scores.
        assertEquals(1, FastMath.exp(chart.getLogInsideScore(1, 2)), 1e-13);
        assertEquals(1, FastMath.exp(chart.getLogInsideScore(2, 1)), 1e-13);
        assertEquals(1+1, FastMath.exp(chart.getLogInsideScore(0, 2)), 1e-13);
        assertEquals(1+1+1, FastMath.exp(chart.getLogInsideScore(-1, 0)), 1e-10);
        assertEquals(1, FastMath.exp(chart.getLogInsideScore(-1, 1)), 1e-13);
        assertEquals(1+1+1, FastMath.exp(chart.getLogInsideScore(-1, 2)), 1e-3);
        
        // Check outside scores.
        assertEquals(1+1, FastMath.exp(chart.getLogOutsideScore(1, 2)), 1e-13);
        assertEquals(1+1+1, FastMath.exp(chart.getLogOutsideScore(2, 1)), 1e-13); // why is this 3*6 and not just 3?
        assertEquals(1, FastMath.exp(chart.getLogOutsideScore(0, 2)), 1e-13);
        assertEquals(1, FastMath.exp(chart.getLogOutsideScore(-1, 0)), 1e-13);
        assertEquals(1, FastMath.exp(chart.getLogOutsideScore(-1, 1)), 1e-13);
        assertEquals(1, FastMath.exp(chart.getLogOutsideScore(-1, 2)), 1e-3);
        assertEquals(1+1, FastMath.exp(chart.getLogOutsideScore(1, 0)), 1e-3);

        // Check sums.
        assertEquals(1+1, FastMath.exp(chart.getLogSumOfPotentials(1, 2)), 1e-3);
        assertEquals(1+1+1, FastMath.exp(chart.getLogSumOfPotentials(2, 1)), 1e-3);
        assertEquals(1+1+1, FastMath.exp(chart.getLogSumOfPotentials(0, 1)), 1e-3);
        assertEquals(1+1, FastMath.exp(chart.getLogSumOfPotentials(2, 0)), 1e-3);
        
        // Check expected counts.
        double Z = 7;
        assertEquals((1+1)/Z, FastMath.exp(chart.getLogExpectedCount(1, 2)), 1e-3);
        assertEquals((1+1+1)/Z, FastMath.exp(chart.getLogExpectedCount(2, 1)), 1e-3);
        assertEquals((1+1+1)/Z, FastMath.exp(chart.getLogExpectedCount(0, 1)), 1e-3);
        assertEquals((1+1)/Z, FastMath.exp(chart.getLogExpectedCount(2, 0)), 1e-3);        
    }

    @Test
    public void testInsideOutsideSingleRoot3() {
        double[] root = new double[] {1, 1}; 
        double[][] child = new double[][]{ {0, 1}, {1, 0} };
        
        DoubleArrays.log(root);
        DoubleArrays.log(child);
        
        DepIoChart chart = HyperDepParser.insideOutsideSingleRoot(root,  child);

        // Check partition function.
        assertEquals(2, FastMath.exp(chart.getLogPartitionFunction()), 1e-3);        
    }    

    @Test
    public void testInsideOutsideMultiRoot1() {
        double[] root = new double[] {1, 2, 3}; 
        double[][] child = new double[][]{ {0, 4, 5}, {6, 0, 7}, {8, 9, 0} };
        
        DoubleArrays.log(root);
        DoubleArrays.log(child);
        
        DepIoChart chart = HyperDepParser.insideOutsideMultiRoot(root,  child);

        // Check partition function.
        double multiTrees = 6+14+12+36+27;
        double Z = 45+28+20+84+162+216+96+multiTrees;
        assertEquals(Z, FastMath.exp(chart.getLogPartitionFunction()), 1e-3);
                        
        // Check inside scores.
        assertEquals(7, FastMath.exp(chart.getLogInsideScore(1, 2)), 1e-13);
        assertEquals(9, FastMath.exp(chart.getLogInsideScore(2, 1)), 1e-13);
        assertEquals(45+20, FastMath.exp(chart.getLogInsideScore(0, 2)), 1e-13);
        assertEquals(1, FastMath.exp(chart.getLogInsideScore(-1, 0)), 1e-10);
        assertEquals(14, FastMath.exp(chart.getLogInsideScore(-1, 1)), 1e-13);
        assertEquals(8*9+8*4, FastMath.exp(chart.getLogInsideScore(2, 0)), 1e-10);
        assertEquals(555, FastMath.exp(chart.getLogInsideScore(-1, 2)), 1e-3);

        // Check outside scores.
        assertEquals(18, FastMath.exp(chart.getLogOutsideScore(1, 2)), 1e-13);
        assertEquals(50, FastMath.exp(chart.getLogOutsideScore(2, 1)), 1e-13); // why is this 3*6 and not just 3?
        assertEquals(1, FastMath.exp(chart.getLogOutsideScore(0, 2)), 1e-13);
        assertEquals(152, FastMath.exp(chart.getLogOutsideScore(-1, 0)), 1e-13);
        assertEquals(10, FastMath.exp(chart.getLogOutsideScore(-1, 1)), 1e-13);
        assertEquals(1, FastMath.exp(chart.getLogOutsideScore(-1, 2)), 1e-3);
        assertEquals(47, FastMath.exp(chart.getLogOutsideScore(1, 0)), 1e-3);

        // Check sums.
        assertEquals(6+14+12+27+28+45+20, FastMath.exp(chart.getLogSumOfPotentials(-1, 0)), 1e-10);
        assertEquals(6+14+36+84, FastMath.exp(chart.getLogSumOfPotentials(-1, 1)), 1e-13);
        assertEquals(6+12+36+27+54*3+32*3+72*3, FastMath.exp(chart.getLogSumOfPotentials(-1, 2)), 1e-3);
        assertEquals(126, FastMath.exp(chart.getLogSumOfPotentials(1, 2)), 1e-3);
        assertEquals(27+45+54*3+72*3, FastMath.exp(chart.getLogSumOfPotentials(2, 1)), 1e-3);
        assertEquals(12+28+20+32*3, FastMath.exp(chart.getLogSumOfPotentials(0, 1)), 1e-3);
        assertEquals(312, FastMath.exp(chart.getLogSumOfPotentials(2, 0)), 1e-3);
        
        // Check expected counts.
        assertEquals((126)/Z, FastMath.exp(chart.getLogExpectedCount(1, 2)), 1e-3);
        assertEquals((27+45+54*3+72*3)/Z, FastMath.exp(chart.getLogExpectedCount(2, 1)), 1e-3);
        assertEquals((12+28+20+32*3)/Z, FastMath.exp(chart.getLogExpectedCount(0, 1)), 1e-3);
        assertEquals((312)/Z, FastMath.exp(chart.getLogExpectedCount(2, 0)), 1e-3);        
    }    

    @Test
    public void testInsideOutsideMultiRoot2() {
        double[] root = new double[] {1, 1, 1}; 
        double[][] child = new double[][]{ {0, 1, 1}, {1, 0, 1}, {1, 1, 0} };
        
        DoubleArrays.log(root);
        DoubleArrays.log(child);
        
        DepIoChart chart = HyperDepParser.insideOutsideMultiRoot(root,  child);

        // Check partition function.
        assertEquals(7+5, FastMath.exp(chart.getLogPartitionFunction()), 1e-3);
                       
        // Check inside scores.
        assertEquals(1, FastMath.exp(chart.getLogInsideScore(1, 2)), 1e-13);
        assertEquals(1, FastMath.exp(chart.getLogInsideScore(2, 1)), 1e-13);
        assertEquals(2, FastMath.exp(chart.getLogInsideScore(0, 2)), 1e-13);
        assertEquals(1, FastMath.exp(chart.getLogInsideScore(-1, 0)), 1e-10);
        assertEquals(2, FastMath.exp(chart.getLogInsideScore(-1, 1)), 1e-13);
        assertEquals(7, FastMath.exp(chart.getLogInsideScore(-1, 2)), 1e-3);
        
        // Check outside scores.
        assertEquals(3, FastMath.exp(chart.getLogOutsideScore(1, 2)), 1e-13);
        assertEquals(4, FastMath.exp(chart.getLogOutsideScore(2, 1)), 1e-13);  // checked
        assertEquals(1, FastMath.exp(chart.getLogOutsideScore(0, 2)), 1e-13);
        assertEquals(7, FastMath.exp(chart.getLogOutsideScore(-1, 0)), 1e-13); // checked
        assertEquals(2, FastMath.exp(chart.getLogOutsideScore(-1, 1)), 1e-13); // checked
        assertEquals(1, FastMath.exp(chart.getLogOutsideScore(-1, 2)), 1e-3);  // checked
        assertEquals(3, FastMath.exp(chart.getLogOutsideScore(1, 0)), 1e-3);   // checked

        // Check sums.
        assertEquals(3, FastMath.exp(chart.getLogSumOfPotentials(1, 2)), 1e-3);
        assertEquals(4, FastMath.exp(chart.getLogSumOfPotentials(2, 1)), 1e-3); // checked
        assertEquals(4, FastMath.exp(chart.getLogSumOfPotentials(0, 1)), 1e-3); // checked
        assertEquals(2, FastMath.exp(chart.getLogSumOfPotentials(2, 0)), 1e-3); // checked
        
        // Check expected counts.
        double Z = 7+5;
        assertEquals((3)/Z, FastMath.exp(chart.getLogExpectedCount(1, 2)), 1e-3);
        assertEquals((4)/Z, FastMath.exp(chart.getLogExpectedCount(2, 1)), 1e-3);
        assertEquals((4)/Z, FastMath.exp(chart.getLogExpectedCount(0, 1)), 1e-3);
        assertEquals((2)/Z, FastMath.exp(chart.getLogExpectedCount(2, 0)), 1e-3);        
    }
    
    @Test
    public void testInsideOutsideMultiRoot3() {
        double[] root = new double[] {1, 1}; 
        double[][] child = new double[][]{ {0, 1}, {1, 0} };
        
        DoubleArrays.log(root);
        DoubleArrays.log(child);
        
        DepIoChart chart = HyperDepParser.insideOutsideMultiRoot(root,  child);

        // Check partition function.
        assertEquals(3, FastMath.exp(chart.getLogPartitionFunction()), 1e-3);        
    }
    
    @Test
    public void testInsideFirstOrderExpectSingleRoot1() {
        helpTestInsideFirstOrderExpectSingleRoot(RealAlgebra.getInstance());
        helpTestInsideFirstOrderExpectSingleRoot(LogSemiring.getInstance());
        helpTestInsideFirstOrderExpectSingleRoot(LogSignAlgebra.getInstance());
    }

    private void helpTestInsideFirstOrderExpectSingleRoot(Algebra s) {
        double[] root = new double[] {1, 2, 3}; 
        double[][] child = new double[][]{ {0, 4, 5}, {6, 0, 7}, {8, 9, 0} };
        
        DoubleArrays.log(root);
        DoubleArrays.log(child);
        
        Pair<O1DpHypergraph, Scores> pair = HyperDepParser.insideSingleRootEntropyFoe(root,  child, s);
        O1DpHypergraph graph = pair.get1();
        Scores scores = pair.get2();
        // Fill with dummy outside scores.
        scores.alpha = new double[scores.beta.length];
        DepIoChart chart = HyperDepParser.getDepIoChart(graph, scores);
        
        // Check inside scores. (These LogInsideScore checks are mostly unnecessary.)
        assertEquals(7, s.toReal(chart.getLogInsideScore(1, 2)), 1e-13);
        assertEquals(9, s.toReal(chart.getLogInsideScore(2, 1)), 1e-13);
        assertEquals(45+20, s.toReal(chart.getLogInsideScore(0, 2)), 1e-10);
        assertEquals(45+28+20, s.toReal(chart.getLogInsideScore(-1, 0)), 1e-10);
        assertEquals(84, s.toReal(chart.getLogInsideScore(-1, 1)), 1e-13);
        assertEquals(8*9+8*4, s.toReal(chart.getLogInsideScore(2, 0)), 1e-10);
        assertEquals(162+216+96, s.toReal(chart.getLogInsideScore(-1, 2)), 1e-3);
        
        // Check partition function.
        int rt = graph.getRoot().getId();
        double Z = s.toReal(scores.beta[rt]);        
        assertEquals(45+28+20+84+162+216+96, Z, 1e-10);

        // Check expected log of derivations.
        double[] trees = new double[] {45, 28, 20, 84, 162, 216, 96};
        double expectedRbar = 0;
        for (int t=0; t<trees.length; t++) {
            expectedRbar += trees[t] * FastMath.log(trees[t]);
        }
        double rBar = scores.betaFoe[rt];
        assertEquals(expectedRbar, s.toReal(rBar), 1e-10);
        
        double logZ = FastMath.log(Z);
        double entropy = logZ - FastMath.exp(FastMath.log(s.toReal(rBar)) - logZ);
        assertEquals(1.685678668864755, entropy, 1e-10);
    }    
    
    @Test
    public void testTreeCountSingleRoot() {
        double edgeWeight = 1.0;
        assertEquals(1, countTrees(1, true, edgeWeight), 1e-13);
        assertEquals(2, countTrees(2, true, edgeWeight), 1e-13);
        assertEquals(7, countTrees(3, true, edgeWeight), 1e-13);
        assertEquals(30, countTrees(4, true, edgeWeight), 1e-13);
        assertEquals(143, countTrees(5, true, edgeWeight), 1e-13);
        assertEquals(728, countTrees(6, true, edgeWeight), 1e-13);        
    }

    @Test
    public void testTreeCountMultiRoot() {
        double edgeWeight = 1.0;
        assertEquals(1, countTrees(1, false, edgeWeight), 1e-13);
        assertEquals(3, countTrees(2, false, edgeWeight), 1e-13);
        assertEquals(12, countTrees(3, false, edgeWeight), 1e-13);
        assertEquals(55, countTrees(4, false, edgeWeight), 1e-13);
        assertEquals(273, countTrees(5, false, edgeWeight), 1e-13);
        assertEquals(1428, countTrees(6, false, edgeWeight), 1e-10);
    }

    @Test
    public void testTreeCountSingleRootWeight2() {
        double edgeWeight = 2.0;
        assertEquals(1 * Math.pow(edgeWeight, 1), countTrees(1, true, edgeWeight), 1e-13);
        assertEquals(2 * Math.pow(edgeWeight, 2), countTrees(2, true, edgeWeight), 1e-13);
        assertEquals(7 * Math.pow(edgeWeight, 3), countTrees(3, true, edgeWeight), 1e-13);
        assertEquals(30 * Math.pow(edgeWeight, 4), countTrees(4, true, edgeWeight), 1e-10);
        assertEquals(143 * Math.pow(edgeWeight, 5), countTrees(5, true, edgeWeight), 1e-10);
        assertEquals(728 * Math.pow(edgeWeight, 6), countTrees(6, true, edgeWeight), 1e-8);
    }
    
    @Test
    public void testTreeCountMultiRootWeight2() {
        double edgeWeight = 2.0;
        assertEquals(1 * Math.pow(edgeWeight, 1), countTrees(1, false, edgeWeight), 1e-13);
        assertEquals(3 * Math.pow(edgeWeight, 2), countTrees(2, false, edgeWeight), 1e-13);
        assertEquals(12 * Math.pow(edgeWeight, 3), countTrees(3, false, edgeWeight), 1e-13);
        assertEquals(55 * Math.pow(edgeWeight, 4), countTrees(4, false, edgeWeight), 1e-10);
        assertEquals(273 * Math.pow(edgeWeight, 5), countTrees(5, false, edgeWeight), 1e-10);
        assertEquals(1428 * Math.pow(edgeWeight, 6), countTrees(6, false, edgeWeight), 1e-8);
    }

    /** Returns the partition function from running the inside algorithm on a sentence of length n with the given edge weights. */
    protected double countTrees(int n, boolean singleRoot, double edgeWeight) {
        double[] root = new double[n]; 
        double[][] child = new double[n][n];
        DoubleArrays.fill(root, edgeWeight);
        DoubleArrays.fill(child, edgeWeight);
        DoubleArrays.log(root);
        DoubleArrays.log(child);        
        DepIoChart chart = singleRoot ? HyperDepParser.insideOutsideSingleRoot(root,  child) : HyperDepParser.insideOutsideMultiRoot(root,  child);
        return Math.exp(chart.getLogPartitionFunction());
    }
    
}
