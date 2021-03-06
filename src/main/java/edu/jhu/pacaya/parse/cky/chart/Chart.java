package edu.jhu.pacaya.parse.cky.chart;

import edu.jhu.pacaya.nlp.data.Sentence;
import edu.jhu.pacaya.parse.cky.CnfGrammar;
import edu.jhu.pacaya.parse.cky.Rule;
import edu.jhu.pacaya.parse.cky.chart.ConstrainedChartCell.ChartCellConstraint;
import edu.jhu.pacaya.parse.cky.data.BinaryTree;
import edu.jhu.prim.tuple.Pair;


/**
 * Parsing chart that stores every cell explicitly.
 * 
 * @author mgormley
 * 
 */
public class Chart {
    
    public static class BackPointer {
        
        /** This backpointer is used to identify a non-null backpointer. */
        public static final BackPointer NON_NULL_BACKPOINTER = new BackPointer(null, -1);
        
        /** The rule. */
        public final Rule r;
        /** The midpoint of the rule application. */
        public final int mid;

        public BackPointer(Rule r, int mid) {
            this.r = r;
            this.mid = mid;
        }

        /**
         * Get the midpoint of the rule application.
         */
        public int getMid() {
            return mid;
        }
        
        /**
         * Get the rule.
         */
        public Rule getRule() {
            return r;
        }
    }

    public enum ChartCellType { FULL, SINGLE_HASH, DOUBLE_HASH, FULL_BREAK_TIES, CONSTRAINED_FULL, SINGLE_HASH_BREAK_TIES, CONSTRAINED_SINGLE };
    public enum ParseType { VITERBI, INSIDE };
    
    private final ChartCellType cellType;
    private final CnfGrammar grammar;
    
    private ChartCell[][] chart;
    private Sentence sentence;
    private ParseType parseType;
    private ChartCellConstraint constraint;

    public Chart(Sentence sentence, CnfGrammar grammar, ChartCellType cellType, ParseType parseType, ChartCellConstraint constraint) {
        this.cellType = cellType;
        this.parseType = parseType;
        this.sentence = sentence;
        this.grammar = grammar;
        this.constraint = constraint;
        this.chart = getNewChart(sentence, grammar, cellType, parseType, constraint);
    }

    /**
     * Resets the chart for the input sentence.
     */
    public void reset(Sentence sentence) {
        this.sentence = sentence;
        // Ensure that the chart is large enough.
        if (sentence.size() > chart.length){
            chart = getNewChart(sentence, grammar, cellType, parseType, constraint);
        } else {
            // Clear the chart.
            //
            // Note that we only need to clear the portion that will be used while parsing this sentence.
            for (int i = 0; i < sentence.size(); i++) {
                for (int j = i+1; j < sentence.size() + 1; j++) {
                    chart[i][j].reset(sentence);
                }
            }
        }
    }
    
    /**
     * Gets a new chart of the appropriate size for the sentence, specific to
     * this grammar, and with cells of the specified type.
     */
    private static ChartCell[][] getNewChart(Sentence sentence, CnfGrammar grammar, ChartCellType cellType, ParseType parseType, ChartCellConstraint constraint) {
        ChartCell[][] chart = new ChartCell[sentence.size()][sentence.size()+1];
        for (int i = 0; i < chart.length; i++) {
            for (int j = i+1; j < chart[i].length; j++) {
                if (parseType == ParseType.INSIDE && cellType != ChartCellType.FULL) {
                    throw new RuntimeException("Inside algorithm not implemented for cell type: " + cellType);
                }
                ChartCell cell;
                switch(cellType) {
                case SINGLE_HASH:
                    chart[i][j] = new SingleHashChartCell(grammar, false);
                    break;
                case SINGLE_HASH_BREAK_TIES:
                    chart[i][j] = new SingleHashChartCell(grammar, true);
                    break;
                case CONSTRAINED_SINGLE:
                    cell = new SingleHashChartCell(grammar, true);
                    chart[i][j] = new ConstrainedChartCell(i, j, cell, constraint, sentence);
                    break;
                case DOUBLE_HASH:
                    chart[i][j] = new DoubleHashChartCell(grammar);
                    break;
                case FULL:
                    chart[i][j] = new FullChartCell(i, j, grammar, parseType);
                    break;
                case FULL_BREAK_TIES:
                    chart[i][j] = new FullTieBreakerChartCell(grammar, true);
                    break;
                case CONSTRAINED_FULL:
                    cell = new FullTieBreakerChartCell(grammar, true);
                    chart[i][j] = new ConstrainedChartCell(i, j, cell, constraint, sentence);
                    break;
                default:
                    throw new RuntimeException("not implemented for " + cellType);
                }
            }
        }
        return chart;
    }

    public Pair<BinaryTree,Double> getViterbiParse() {
        BinaryTree root = getViterbiTree(0, sentence.size(), grammar.getRootSymbol());
        double rootScore = chart[0][sentence.size()].getScore(grammar.getRootSymbol());
        return new Pair<BinaryTree, Double>(root, rootScore);
    }
    
    /**
     * Gets the highest probability tree with the span (start, end) and the root symbol rootSymbol.
     * 
     * @param start The start of the span of the requested tree.
     * @param end The end of the span of the requested tree.
     * @param rootSymbol The symbol of the root of the requested tree.
     * @return The highest probability tree or null if no parse exists.
     */
    private BinaryTree getViterbiTree(int start, int end, int rootSymbol) {
        ChartCell cell = chart[start][end];
        BackPointer bp = cell.getBp(rootSymbol);
        if (bp == null) {
            return null;
        }
        
        BinaryTree leftChild;
        BinaryTree rightChild;
        if (bp.r.isLexical()) {
            String lcSymbolStr = grammar.getLexAlphabet().lookupObject(bp.r.getLeftChild());
            leftChild = new BinaryTree(lcSymbolStr, start, end, null, null, true);
            rightChild = null;
        } else if (bp.r.isUnary()) {
            leftChild = getViterbiTree(start, bp.mid, bp.r.getLeftChild());
            rightChild = null;
        } else {
            leftChild = getViterbiTree(start, bp.mid, bp.r.getLeftChild());
            rightChild = getViterbiTree(bp.mid, end, bp.r.getRightChild());
        }
        
        String rootSymbolStr = grammar.getNtAlphabet().lookupObject(rootSymbol);
        return new BinaryTree(rootSymbolStr, start, end, leftChild, rightChild, false);
    }

    public ChartCell getCell(int start, int end) {
        return chart[start][end];
    }
    
    public int getSentenceSize() {
        return sentence.size();
    }
    
    public CnfGrammar getGrammar() {
        return grammar;
    }
}
