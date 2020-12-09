package poetry.decisiontree;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

import beast.core.parameter.IntegerParameter;
import beast.core.parameter.RealParameter;
import beast.core.util.Log;
import beast.util.Randomizer;
import poetry.util.WekaUtils;
import weka.classifiers.trees.REPTree;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

public class DecisionSplit {

	
	String targetFeature;
	IntegerParameter pointers;
	RealParameter splits;
	List<Attribute> covariates;
	DecisionTree tree;
	
	
	// Min/max value of each numeric covariate
	double[] mins;
	double[] maxs;
	int maxLeafCount;
	
	
	/** 
	 * Helper class for splitting attributes
	 * @param pointers
	 * @param splits
	 * @param covariates
	 * @param data
	 * @param nattr - number of attributes to subsample
	 */
	public DecisionSplit(IntegerParameter pointers, RealParameter splits, List<String> covariates, String targetFeature, Instances data, DecisionTree tree, int nattr, int maxLeafCount) {
		
		this.pointers = pointers;
		this.splits = splits;
		this.covariates = new ArrayList<>();
		for (String cov : covariates) {
			this.covariates.add(data.attribute(cov));
		}
		this.tree = tree;
		this.maxLeafCount = maxLeafCount;
		this.targetFeature = targetFeature;
		
		
		// Subsample covariates?
		if (nattr < this.covariates.size()) {
			
			
			System.out.print("Tree " + (this.tree.getTreeNum()+1) + " will be built on ");
			List<Attribute> copy  = new ArrayList<>();
			copy.addAll(this.covariates);
			List<Attribute> sample = new ArrayList<>();
			for (int s = 0; s < nattr; s ++) {
				int index = Randomizer.nextInt(copy.size());
				sample.add(copy.get(index));
				System.out.print(copy.get(index).name());
				if (s < nattr-1) System.out.print(", ");
				copy.remove(index);
				
			}
			System.out.println();
			
			this.covariates = sample;
			
		}
		
		// Min / max values
		this.mins = new double[this.covariates.size()];
		this.maxs = new double[this.covariates.size()];
		for (int i = 0; i < this.covariates.size(); i ++) {
			
			Attribute attr = this.covariates.get(i);
			if (attr.isNumeric()) {
				double min = data.kthSmallestValue(attr, 1);
				double max = data.kthSmallestValue(attr, data.numInstances());
				this.mins[i] = min;
				this.maxs[i] = max;
			}else {
				this.mins[i] = Double.NEGATIVE_INFINITY;
				this.maxs[i] = Double.POSITIVE_INFINITY;
			}
			
		}
		
	}
	
	
	/**
	 * Split the data and return the split, or return null if split is impossible
	 * @param preSplit
	 * @param index
	 * @return
	 */
	public Instances[] splitData(Instances preSplit, int index) {
		
		
		
		// Since leaves do not have split parameters, the vectors are shifted by leafCount
		int treeAddon = this.tree.getTreeNum() * (this.maxLeafCount-1);
		int nleaves = tree.getLeafCount();
		int paramIndex = index - nleaves + treeAddon;
		
		if (this.pointers.getDimension() <= paramIndex) return null;

		
		// Which attribute is being split on
		int attrIndex = (int) this.pointers.getArrayValue(paramIndex);
		Attribute splitAttr = this.covariates.get(attrIndex);
		
		
		// Test
		if (splitAttr.name().equals(this.targetFeature)) {
			Log.warning("Fatal error: splitting on target feature! " + this.targetFeature);
			System.exit(0);
		}
		
		
		// If attribute is missing, return null
		//int attrNum = WekaUtils.getIndexOfColumn(preSplit, splitAttr.name());
		//if (attrNum == -1) {
			//return null;
		//}
		
		// Where to split
		double splitPoint = this.splits.getArrayValue(paramIndex);
		
		
		// Nominal split
		int splitPointNominal = -1;
		if (splitAttr.isNominal()) {
			
			// How many values?
			int nvals = splitAttr.numValues();
			
			// Index the pointer (from double to integer)
			// eg. if there are 5 values, then [0,0.2) -> 0, [0.2,0.4) -> 1, etc.
			double cumulative = 1.0 / nvals;
			for (int i = 0; i < nvals; i ++) {
				if (splitPoint < cumulative){
					splitPointNominal = i;
					break;
				}
				cumulative += 1.0 / nvals;
			}
			
		}
		
		// Numeric split
		else {
			
			// Normalise splitPoint into [min,max] range
			double min = this.mins[attrIndex];
			double max = this.maxs[attrIndex];
			splitPoint = splitPoint*(max - min) + min;
			
		}
		
		
		// Do the split
		Instances trueSplit = new Instances(preSplit);
		Instances falseSplit = new Instances(preSplit);
		trueSplit.clear();
		falseSplit.clear();
		for (int i = 0; i < preSplit.numInstances(); i ++) {
			
			Instance inst = preSplit.instance(i);
			
			boolean success;
			
			// Nominal split
			if (splitAttr.isNominal()) {
				int value = splitAttr.indexOfValue(inst.stringValue(splitAttr));
				success = value == splitPointNominal;
			}
			
			// Numeric split
			else {
				double value = inst.value(splitAttr);
				success = value <= splitPoint;
			}
			
			
			// Accept the split?
			if (success) {
				trueSplit.add(inst);
			}else {
				falseSplit.add(inst);
			}
			
		}
		
		
		//trueSplit.deleteAttributeAt(WekaUtils.getIndexOfColumn(trueSplit, splitAttr.name()));
		//falseSplit.deleteAttributeAt(WekaUtils.getIndexOfColumn(falseSplit, splitAttr.name()));
		return new Instances[] { trueSplit,  falseSplit };
		
	}
	


	/**
	 * The name of this attribute
	 * @param nodeIndex
	 * @return
	 */
	public String getAttributeName(int nodeIndex) {
		
		int paramIndex = nodeIndex - tree.getLeafCount();
		
		// What attribute is being split on
		int attrIndex = (int) this.pointers.getArrayValue(paramIndex);
		Attribute splitAttr = this.covariates.get(attrIndex);
		return splitAttr.name();
	}


	/**
	 * Value of where to split
	 * Returns a string of a number if attribute is numeric
	 * Or the value if the attribute is nonimal
	 * @param nodeIndex
	 * @param sf rounds to sf (set to -1 for no rounding)
	 * @return
	 */
	public String getSplitValue(int nodeIndex, int sf) {
		
		int paramIndex = nodeIndex - tree.getLeafCount();
		
		// What attribute is being split on
		int attrIndex = (int) this.pointers.getArrayValue(paramIndex);
		Attribute splitAttr = this.covariates.get(attrIndex);
		
		
		// Where to split
		double splitPoint = this.splits.getArrayValue(paramIndex);
		
		
		// Nominal split
		if (splitAttr.isNominal()) {
			
			int splitPointNominal = -1;
			
			// How many values?
			int nvals = splitAttr.numValues();
			
			// Index the pointer (from double to integer)
			// eg. if there are 5 values, then [0,0.2) -> 0, [0.2,0.4) -> 1, etc.
			double cumulative = 1.0 / nvals;
			for (int i = 0; i < nvals; i ++) {
				if (splitPoint < cumulative){
					splitPointNominal = i;
					break;
				}
				cumulative += 1.0 / nvals;
			}
			
			
			// Value
			return splitAttr.value(splitPointNominal);
			
		}
		
		// Numeric split
		else {
			
			// Normalise splitPoint into [min,max] range
			double min = this.mins[attrIndex];
			double max = this.maxs[attrIndex];
			splitPoint = splitPoint*(max - min) + min;
			
			
			// Rounding to sf
			if (sf >= 0) {
				BigDecimal bd = new BigDecimal(splitPoint);
				bd = bd.round(new MathContext(sf));
				splitPoint = bd.doubleValue();
			}
			
			
			return "" + splitPoint;
			
		}
		
	}


	/**
	 * Returns a rule as a String
	 * 		attr <= val (if numeric)
	 * 		attr == val (if nominal)
	 * @param nodeIndex
	 * @return
	 */
	public String getCondition(int nodeIndex, int sf) {
		
		String attr = this.getAttributeName(nodeIndex);
		String val = this.getSplitValue(nodeIndex, sf);
		
		
		// What attribute is being split on
		int attrIndex = (int) this.pointers.getArrayValue(nodeIndex - tree.getLeafCount());
		Attribute splitAttr = this.covariates.get(attrIndex);
		
		String relationship = splitAttr.isNominal() ? " == " : " <= ";
		return attr + relationship + val;
			
		
	}


	/**
	 * Computes the REPTree
	 * @return
	 */
	public REPTree getRepTree(Instances data) {
		
		// Copy the data
		Instances subset = new Instances(data);
		
		
		// Remove all attrributes are not covariates or class 
		for (int i = 0; i < data.numAttributes(); i ++) {
			Attribute attr = data.attribute(i);
			
			// Keep the class
			if (attr.name().equals(this.targetFeature)) continue;
			
			// Keep the covariates
			boolean isCovariate = false;
			for (Attribute cov : this.covariates) {
				if (cov.name().equals(attr.name())) {
					isCovariate = true;
					break;
				}
			}
			if (isCovariate) continue;
			
			
			// Remove all other attributes including leaf predictors
			//subset.deleteAttributeAt(WekaUtils.getIndexOfColumn(subset, attr.name()));
			
		}
		
		REPTree reptree = new REPTree();
		try {
			
			reptree.buildClassifier(subset);
			
			
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Error building REPTree");
		}
		
		// TODO Auto-generated method stub
		return reptree;
	}
	
	
}
