package edu.jhu.hypergraph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.jhu.hypergraph.MemHypergraph.MemHypernode;

/** A hyperedge in a hypergraph. */
public class Hyperedge {

    private Hypernode headNode;
    private List<Hypernode> tailNodes;
    private String label;
    private int id;
        
    public Hyperedge(int id, String label, Hypernode headNode, List<Hypernode> tailNodes) {
        this.headNode = headNode;
        this.tailNodes = tailNodes;
        this.label = label;
        this.id = id;
    }
    
    public Hyperedge(int id, String label, Hypernode headNode, Hypernode... tailNodes) {
        this.headNode = headNode;
        this.tailNodes = Arrays.asList(tailNodes);
        this.label = label;
        this.id = id;
    }

    public Hyperedge(int id, String label) {
        this.headNode = null;
        this.tailNodes = new ArrayList<Hypernode>();
        this.label = label;
        this.id = id;
    }

    /** Gets the consequent node for this edge. */
    public Hypernode getHeadNode() {
        return headNode;
    }

    /** Gets the list of antecedent nodes for this edge. */
    public List<Hypernode> getTailNodes() {
        return tailNodes;
    }
    
    /** Gets a name for this edge. */
    public String getLabel() {
        if (label == null) {
            // Lazily construct an edge label from the head and tail nodes.
            StringBuilder label = new StringBuilder();
            label.append(headNode.getLabel());
            label.append("<--");
            for (Hypernode tailNode : tailNodes) {
                label.append(tailNode.getLabel());
                label.append(",");
            }
            if (tailNodes.size() > 0) {
                label.deleteCharAt(label.length()-1);
            }
            return label.toString();
        }
        return label;
    }
    
    /** Gets a unique identifier for this edge within the hypergraph. */
    public int getId() {
        return id;
    }
    
    public String toString() {
        return label;
    }
    
    /* ------------------- Modifiers ---------------- */
    
    public void setHeadNode(Hypernode headNode) {
        this.headNode = headNode;
    }
    
    public void setLabel(String label) {
        this.label = label;
    }

    public void setId(int id) {
        this.id = id;
    }
    
    public void addTailNode(Hypernode tailNode) {
        tailNodes.add(tailNode);
    }
    
    public void setTailNodes(Hypernode... tailNodes) {
        this.tailNodes.clear();
        for (Hypernode tail : tailNodes) {
            this.tailNodes.add(tail);
        }
    }

    public void clearTailNodes() {
        this.tailNodes.clear();
    }

    public void clear() {
        this.headNode = null;
        this.label = null;
        this.id = -1;
        this.tailNodes.clear();
    }
    
}
