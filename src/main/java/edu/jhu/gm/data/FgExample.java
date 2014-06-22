package edu.jhu.gm.data;

import java.io.Serializable;

import edu.jhu.gm.model.FactorGraph;
import edu.jhu.gm.model.FgModel;
import edu.jhu.gm.model.VarConfig;

/**
 * Factor graph example. This class facilitates creation of the clamped factor
 * graphs, creation/caching of feature vectors, and storage of the training data
 * assignments to variables.
 * 
 * @author mgormley
 * 
 */
public interface FgExample extends UFgExample, Serializable {

    /**
     * Gets the factor graph with the OBSERVED and PREDICTED variables clamped
     * to their values from the training example.
     */
    public FactorGraph getFgLat();
    
    /**
     * Updates the factor graph with the OBSERVED and PREDICTED variables clamped
     * to their values from the training example.
     * @param params The parameters with which to update.
     * @param logDomain TODO
     */
    public FactorGraph updateFgLat(FgModel model, boolean logDomain);

    /** Gets the gold configuration of the variables. */
    public VarConfig getGoldConfig();

    /** Gets the gold configuration of the predicted variables ONLY for the given factor. */ 
    public VarConfig getGoldConfigPred(int factorId);
    
    /** Gets the gold configuration index of the predicted variables for the given factor. */
    public int getGoldConfigIdxPred(int factorId);
    
    /** Gets the weight of this example for use in log-liklihood training. */
    public double getWeight();
    
}