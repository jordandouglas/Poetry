package poetry.decisiontree;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

import beast.core.parameter.IntegerParameter;
import beast.core.parameter.RealParameter;
import beast.core.util.Log;
import poetry.util.WekaUtils;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

public class DecisionSplit {

	IntegerParameter pointers;
	RealParameter splits;
	List<Attribute> covariates;
	DecisionTree tree;
	
	
	// Min/max value of each numeric covariate
	double[] mins;
	double[] maxs;
	
	
	/** 
	 * Helper class for splitting attributes
	 * @param pointers
	 * @param splits
	 * @param covariates
	 * @param data
	 */
	public DecisionSplit(IntegerParameter pointers, RealParameter splits, List<Attribute> covariates, Instances data, DecisionTree tree) {
		
		this.pointers = pointers;
		this.splits = splits;
		this.covariates = covariates;
		this.tree = tree;
		
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
		int paramIndex = index - tree.getLeafCount();
		
		if (this.pointers.getDimension() <= paramIndex) return null;
		
		
		// What attribute is being split on
		int attrIndex = (int) this.pointers.getArrayValue(paramIndex);
		Attribute splitAttr = this.covariates.get(attrIndex);
		
		
		// If attribute is missing, return null
		int attrNum = WekaUtils.getIndexOfColumn(preSplit, splitAttr.name());
		if (attrNum == -1) {
			return null;
		}
		
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
				int value = splitAttr.indexOfValue(inst.stringValue(attrNum));
				success = value == splitPointNominal;
			}
			
			// Numeric split
			else {
				double value = inst.value(attrNum);
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
	
	
}
