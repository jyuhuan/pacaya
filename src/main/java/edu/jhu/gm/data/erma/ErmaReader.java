package edu.jhu.gm.data.erma;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;

import data.DataSample;
import data.FeatureFile;
import data.FeatureInstance;
import data.RV;
import dataParser.DataParser;
import edu.jhu.gm.data.FgExampleList;
import edu.jhu.gm.data.FgExampleMemoryStore;
import edu.jhu.gm.data.FgExampleStore;
import edu.jhu.gm.data.LFgExample;
import edu.jhu.gm.data.LabeledFgExample;
import edu.jhu.gm.data.UFgExample;
import edu.jhu.gm.feat.Feature;
import edu.jhu.gm.feat.FeatureExtractor;
import edu.jhu.gm.feat.FeatureVector;
import edu.jhu.gm.model.ExpFamFactor;
import edu.jhu.gm.model.FactorGraph;
import edu.jhu.gm.model.FeExpFamFactor;
import edu.jhu.gm.model.Var;
import edu.jhu.gm.model.Var.VarType;
import edu.jhu.gm.model.VarConfig;
import edu.jhu.gm.model.VarSet;
import edu.jhu.util.FeatureNames;
import featParser.FeatureFileParser;

/**
 * Reads input files in ERMA format.
 * 
 * @author mgormley
 * 
 */
public class ErmaReader {

    private static final Logger log = Logger.getLogger(ErmaReader.class);
    private boolean includeUnsupportedFeatures;

    /**
     * Constructs an ERMA reader, including all the unsupported features (ERMA's default).
     */
    public ErmaReader() {
        this(true);
    }
    
    /**
     * Constructs an ERMA reader.
     * @param includeUnsupportedFeatures Whether to include the "unsupported" features in the model.
     */
    public ErmaReader(boolean includeUnsupportedFeatures) {
        this.includeUnsupportedFeatures = includeUnsupportedFeatures;
    }
    
    public FgExampleList read(File featureTemplate, File dataFile, FeatureNames alphabet) {
        return read(featureTemplate.getAbsolutePath(), dataFile.getAbsolutePath(), alphabet);
    }
    
    /**
     * Reads a feature file containing templates of features and a data file
     * containing a list of examples. Converts all the DataSample objects to
     * FgExamples and returns them.
     * 
     * @param featureTemplate The path to the feature file.
     * @param dataFile The path to the data file.
     * @param alphabet The alphabet used to create the FgExamples.
     * @return The new FgExamples.
     */
    public FgExampleList read(String featureTemplate, String dataFile, FeatureNames alphabet) {
        log.info("Feature template file: " + featureTemplate);
        log.info("Data file: " + dataFile);  
        try {
            return read(new FileInputStream(featureTemplate), new FileInputStream(dataFile), alphabet);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
    
    public FgExampleList read(InputStream featureTemplate, InputStream dataFile, FeatureNames alphabet) {
        FeatureFile ff;
        log.info("Reading features");
        try {
            FeatureFileParser fp = FeatureFileParser.createParser(featureTemplate);
            ff = fp.parseFile();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        log.info("Reading and converting data");  
        FgExampleMemoryStore data = new FgExampleMemoryStore();
        try {
            // This will convert each DataSample to an FgExample and add it to data.
            ConvertingDataParser dp = new ConvertingDataParser(dataFile, ff, data, alphabet);
            dp.parseFile();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (includeUnsupportedFeatures) {
            log.info("Including unsupported features in the model.");
            for (data.Feature feat : ff.getFeatures()) {
                alphabet.lookupIndex(new Feature(feat.getName()));
            }
        } else {
            log.info("Excluding unsupported features from the model.");
        }
        
        return data;
    }

    /**
     * The ERMA data structures use a lot of memory. This data parser,
     * immediately converts each one to an FgExample and then discards the ERMA
     * version.
     * 
     * @author mgormley
     */
    private static class ConvertingDataParser extends DataParser {
        
        private FgExampleStore data;
        private FeatureNames alphabet;
        
        public ConvertingDataParser(InputStream is, FeatureFile ff, FgExampleStore data, FeatureNames alphabet) throws FileNotFoundException {
            super(is, ff);
            this.data = data;
            this.alphabet = alphabet;
        }
        
        @Override
        protected void addDataSample(DataSample s) {
            data.add(toFgExample(s, this.features, alphabet));
        }
        
    }
    
    /* -------  Notes on ERMA internal representations.  ------ */

    /*
     * FeatureFile.types contains a map from variable type names (e.g.
     * CHUNK) to a Type object, which contains a list of the states it can
     * take (e.g. CHUNK:= [O,B_PP,B_VP,B_NP,I_NP]).
     * 
     * FeatureFile.features is a map from an expanded name of a variable
     * (e.g. w2_oov_chunk_link*_B_NP_I_NP) to a single Feature object (e.g.
     * w2_oov_chunk_link(CHUNK,CHUNK):= [B_NP,I_NP]).
     * 
     * FeatureFile.featureGroups is a map from a feature name
     * (e.g.wm1_john_chunk_link) to a list of Feature objects (e.g.
     * [wm1_john_chunk_link(CHUNK,CHUNK):= [O,O],
     * wm1_john_chunk_link(CHUNK,CHUNK):= [B_PP,O],
     * wm1_john_chunk_link(CHUNK,CHUNK):= [B_VP,O],...]).
     */

    /*
     * DataSample.variables is a map from variable names (e.g. C4) to RV
     * objects (e.g. C4o).
     * 
     * The RV object describes the type (RV.type) as a Type object, the
     * value (RV.value) as an int--which can be mapped to a string using
     * Type.getTypeString(), the name (RV.name), and the visibility type
     * (RV.visibilityType) which describes whether it is INPUT,OUTPUT, or
     * HIDDEN.
     * 
     * DataSample.featureInstances is a list of FeatureInstance objects.
     * 
     * Each FeatureInstance object contains a feature group (as in the
     * values of FeatureFile.featureGroups), and a list of RVs. 
     * 
     * TODO: It's still not clear exactly how FeatureInstances are used.
     */
    
    /*
     * Factor graphs contain a list of factors. 
     * 
     * Each factor contains a list of its variables.
     */
    
    
    /**
     * Creates our {@link LFgExample} from an ERMA {@link DataSample} and
     * {@link FeatureFile}.
     * 
     * The feature vector reuse is (currently) consistent with ERMA. That is,
     * each factor in each example will have a unique set of feature vectors. We
     * could likely reduce memory usage by adding a notion of variable types --
     * this would allow us to find identical variable type configurations across
     * the corpus and reuse the feature vectors across them.
     * 
     * @param s The data sample
     * @param ff The feature file.
     * @param alphabet The alphabet corresponding to our factor graph model.
     * @return A new factor graph example constructed from the inputs.
     */
    private static LFgExample toFgExample(DataSample s, FeatureFile ff, FeatureNames alphabet){
        //Saves the variable set to factor HashMappings
        HashMap<String,ExpFamFactor> facs = new HashMap<String, ExpFamFactor>();
        // MRG: A mapping from a string identifier for a FeatureInstance, to a
        // list of FeatureVectors represented as HashMap<Feature,Double> (one
        // for each configuration of the variables).
        // MRG: ERMA WAY: HashMap<String,ArrayList<HashMap<Feature,Double> > > featureRefs = new HashMap<String, ArrayList<HashMap<Feature,Double>>>();
        HashMap<String,ArrayList<FeatureVector>> featureRefs = new HashMap<String, ArrayList<FeatureVector>>();
        
        //Go over the features and add each feature to the appropriate cell of the conditional
        //probability table of the appropriate factor
        HashMap<String,Var> newVars=new HashMap<String, Var>();
        int next = 0;
        ArrayList<FeatureInstance> featureInstances = s.getFeatureInstances();
        
        // MRF: Create an "empty" feature extractor which is populated with the features below.
        SimpleLookupFeatureExtractor featExtractor = new SimpleLookupFeatureExtractor();
        for(int j=0; j<featureInstances.size(); j++){
            FeatureInstance fi = featureInstances.get(j);
            
            // MRG: --- PART I ----
            //
            // Constructs a factor (fac) from all the variables participating in
            // this featureInstance (fi).
            // Also gets a feature reference (featRef) which contains a feature
            // vector for each configuration of the variables in the factor.
            
            List<RV> rvs = fi.getVariables();
            List<Var> varList = new ArrayList<Var>();
            for (RV v : rvs) {
                varList.add(getRV(v, newVars));
            }
            
            //System.out.println("fi --> "+fi);
            //System.out.println(fi+"__");
            String key = s.makeKey(fi.getVariables());
            ExpFamFactor fac;
            // MRG: ERMA WAY: ArrayList<HashMap<Feature,Double>> featRef;
            ArrayList<FeatureVector> featRef;
            if(!facs.containsKey(key)){
                VarSet Ivars=new VarSet();
                for(Var v:varList){
                    Ivars.add(v);
                }
                // MRG: ERMA's way was: fac = new Factor(next++,Ivars, 1.0);
                
                fac = new FeExpFamFactor(Ivars, featExtractor);
                facs.put(key,fac);
                //ArrayList<set<feature* > > feat_r_vec;
                
                // MRG: One feature vector for each configuration of the variables for this factor.
                featRef = new ArrayList<FeatureVector>();
                int numConfigs = fac.getVars().calcNumConfigs();
                for(int t=0;t<numConfigs;t++){
                    //MRG: ERMA WAY: featRef.add(new HashMap<Feature,Double>());
                    featRef.add(new FeatureVector());
                }
                featureRefs.put(key, featRef);
            }else{
                fac = facs.get(key);
                featRef = featureRefs.get(key);
            }
            

            // MRG: --- PART II ----
            // 
            // For each feature in this feature instance's feature group
            // (featGroup), the feature is added to the feature vector for the
            // appropriate configuration of the variables.

            ArrayList<data.Feature> featGroup = fi.getFeatures();
            for (int t=0; t<featGroup.size(); t++){
                data.Feature feat = featGroup.get(t);
                //System.out.println(feat);
                //Compute the state corresponding to the variable setting
                VarConfig varVals=new VarConfig();
                int k=0;
                for(Var v:varList){
                    int varVal = feat.getValue(k);
                    if(varVal<0){
                        // MRG: TODO Why would a feature NOT have a value for a variable in the feature instance?
                        varVal = fi.getValue(k);
                    }
                    varVals.put(v,varVal);
                    //System.out.println("variable " + v + " value "+feat.getValue(k)+"="+v.getType().getValName(feat.getValue(k)));
                    k++;
                }
                int state = varVals.getConfigIndex();

                // MRG: Commenting out the following section which was just setting values in ERMA.
                //                if(ff!=null){
                //                    data.Feature feat1 = ff.getFeature(feat.getName());
                //                    //          if(feat!=feat1){
                //                    //              cout <<feat.get_name()<<": feat.get_weight()="<<feat.get_weight()<<" feat1.get_weight()="<<feat1.get_weight()<<endl;
                //                    //              cout << &feat << " vs " <<feat1<<endl;
                //                    //              DAI_THROW(RUNTIME_ERROR);
                //                    //          }
                //                    /* TO DO: Need to update the derivatives to work with weighted feature instances*/
                //                    fac.getCondTable().setValue(state,fac.getCondTable().getValue(state).product(Math.exp(feat1.getWeight().getValue()*fi.getWeight())));
                //                }else{
                //                    fac.getCondTable().setValue(state,fac.getCondTable().getValue(state).product(Math.exp(feat.getWeight().getValue()*fi.getWeight())));
                //                }
                
                //System.out.println("Adding "+feat+" w "+feat.getWeight());
                
                // MRG: ERMA WAY: featRef.get(state).put(feat,featRef.get(state).containsKey(feat)?featRef.get(state).get(feat)+fi.getWeight():fi.getWeight());
                
                // MRG: Convert the ERMA feature to our feature and lookup its index.                               
                int featIdx = alphabet.lookupIndex(new Feature(feat.getName()));           
                FeatureVector featureVector = featRef.get(state);
                // Add the feature weight for this feature to the feature vector.
                featureVector.add(featIdx, fi.getWeight());                
            }
            //Record that this feature was used for this factor value
        }

        // MRG: An array list of factors, indexed by factor Id.
        ArrayList<ExpFamFactor> facs_vec=new ArrayList<ExpFamFactor>();
        // MRG: An array of feature vectors, indexed by factor id and config index.
        ArrayList<ArrayList<FeatureVector> > feature_ref_vec=new ArrayList<ArrayList<FeatureVector>>();
        for (String factKey:facs.keySet()) {
            ExpFamFactor fact = facs.get(factKey);
            facs_vec.add(fact);
            ArrayList<FeatureVector> fr = featureRefs.get(factKey);
            feature_ref_vec.add(fr);
        }
        
        // MRG: Construct a new factor graph.
        FactorGraph fg = new FactorGraph();
        for (ExpFamFactor factor : facs_vec) {
            fg.addFactor(factor);
        }
        
        // MRG: Create an assignment to the variables given in the training data.
        VarConfig trainConfig = new VarConfig();
        for (RV rv : s.getVariables()) {
            Var var = getRV(rv, newVars);
            trainConfig.put(var, rv.getValue());
        }
        
        // MRG: Create a feature extractor which just looks up the appropriate feature vectors in feature_ref_vec.
        featExtractor.setFeatureRefVec(feature_ref_vec);
        
        LFgExample fgEx = new LabeledFgExample(fg, trainConfig, featExtractor);
        return fgEx;
        // MRG: ERMA WAY: FeatureFactorGraph ffg = new FeatureFactorGraph(facs_vec,feature_ref_vec); return ffg;
        //cout << "--de "<<endl;
//      //HashMap<String, variable>::const_iterator iter;
//      for (RV v: variables.values()) {
//          //cout << fv.label()<<"-."<<ffg.var(fv.label()).label() << endl;
//          //DAI_ASSERT(fv.label()==ffg.var(fv.label()).label());
//          int val = v.getValue();
//
//      }
        //System.out.println("Total of "+ffg.numFactors()+" factors");
        //factorGraph = ffg;
    }
    
    /** Converts the RV to a Var, creating a new Var if it is not already present in the hash map. */
    private static Var getRV(RV v,HashMap<String,Var> vars){
        if(!vars.containsKey(v.getName())){
            VarType type = getVisibilityType(v);
            int numStates = v.getType().numValues();
            String name = v.getName();
            List<String> stateNames = v.getType().getValueMap();
            vars.put(v.getName(), new Var(type, numStates, name, stateNames));
        }
        return vars.get(v.getName());
    }

    /** Converts the visibility type of the RV to a VarType. */
    private static VarType getVisibilityType(RV v) {
        if (v.isHidden()) {
            return VarType.LATENT;
        } else if (v.isInput()) {
            return VarType.OBSERVED;
        } else if (v.isOutput()) {
            return VarType.PREDICTED;
        } else {
            throw new RuntimeException("Missing visibility type");
        } 
    }
    
    private static class SimpleLookupFeatureExtractor implements FeatureExtractor, Serializable {
        
        private static final long serialVersionUID = 1L;
        private ArrayList<ArrayList<FeatureVector>> feature_ref_vec;

        public SimpleLookupFeatureExtractor() {
        }
        
        public void setFeatureRefVec(ArrayList<ArrayList<FeatureVector>> feature_ref_vec) {
            this.feature_ref_vec = feature_ref_vec;
        }

        @Override
        public FeatureVector calcFeatureVector(FeExpFamFactor factor, int configId) {
            return feature_ref_vec.get(factor.getId()).get(configId);
        }

        @Override
        public void init(UFgExample ex) {
            // Do nothing.
        }
    }
    
}
