package edu.jhu.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.jhu.srl.MutableInt;

/**
 * Bidirectional mapping of Objects to ints.
 * 
 * @author mgormley
 * @author mmitchell
 *
 */
public class Alphabet<T> implements Serializable {

    private static final long serialVersionUID = -3703345017300334421L;
    private static final int MISSING_OBJECT_INDEX = -1;
	private ArrayList<T> idxObjMap;
	private Map<T, Integer> objIdxMap;
	private boolean isGrowing;
    private HashMap<T, MutableInt> objIdxCountMap;
	
	public Alphabet() {
		isGrowing = true;
		idxObjMap = new ArrayList<T>();
		objIdxMap = new HashMap<T, Integer>();
		objIdxCountMap = new HashMap<T, MutableInt>();
	}
	
	public Alphabet(Alphabet<T> other) {
		isGrowing = true;
		idxObjMap = new ArrayList<T>(other.idxObjMap);
		objIdxMap = new HashMap<T, Integer>(other.objIdxMap);
		objIdxCountMap = new HashMap<T, MutableInt>(other.objIdxCountMap);
	}

	public int lookupIndex(T object) {
		return lookupIndex(object, true);
	}

	public int lookupIndexIncrement(T object) {
	    return lookupIndexIncrement(object, true);
	    
	}
	
	public int lookupIndexIncrement(T object, boolean addIfMissing) {
        Integer index = objIdxMap.get(object);
        if (index == null) {
            // A new object we haven't seen before.
            if (isGrowing && addIfMissing) {
                // Add this new object to the alphabet.
                index = idxObjMap.size();
                idxObjMap.add(object);
                objIdxMap.put(object, index);
                objIdxCountMap.put(object, new MutableInt());
            } else {
                return MISSING_OBJECT_INDEX;
            }
        } else {
            objIdxCountMap.get(object).increment();
        }
        return index;
    }

    public int lookupIndex(T object, boolean addIfMissing) {
		Integer index = objIdxMap.get(object);
		if (index == null) {
			// A new object we haven't seen before.
			if (isGrowing && addIfMissing) {
				// Add this new object to the alphabet.
				index = idxObjMap.size();
				idxObjMap.add(object);
				objIdxMap.put(object, index);
			} else {
			    return MISSING_OBJECT_INDEX;
			}
		}
		return index;
	}
	
	public T lookupObject(int index) {
		return idxObjMap.get(index);
	}
	
	public Integer lookupObjectCount(int index) {
	    return objIdxCountMap.get(index).get();
	}
	
	@Override
	public Object clone() {
		return new Alphabet<T>(this);
	}

	public int size() {
		return idxObjMap.size();
	}

	public void startGrowth() {
		isGrowing = true;
	}

	public void stopGrowth() {
		isGrowing = false;
	}
	
	public List<T> getObjects() {
		return Collections.unmodifiableList(idxObjMap);
	}

	public int[] lookupIndices(T[] objectSequence) {
		int[] ids = new int[objectSequence.length];
		for (int i=0; i<objectSequence.length; i++) {
			ids[i] = lookupIndex(objectSequence[i]);
		}
		return ids;
	}

    @Override
    public String toString() {
        return "Alphabet [idxObjMap=" + idxObjMap + ", isGrowing=" + isGrowing
                + "]";
    }

    public static <T> Alphabet<T> getEmptyStoppedAlphabet() {
        Alphabet<T> alphabet = new Alphabet<T>();
        alphabet.stopGrowth();
        return alphabet;
    }
	
}
