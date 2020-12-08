package poetry.decisiontree;




import java.util.List;

import beast.core.Description;
import beast.core.parameter.RealParameter;
import beast.evolution.tree.Node;
import poetry.decisiontree.DecisionTreeInterface.regressionMode;
import poetry.util.WekaUtils;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;



@Description("A node which acts as linear regression at the leaf or can split over an attribute to give two or more children")
public class DecisionNode extends Node {

	// Unique number of this node. The n leaves are numbered 0 - n-1
	protected int nodeIndex;
	protected int lastNodeIndex;
	
	protected int treeNum;
	protected int ntrees;
	
	// Depth of this node
	protected int depth;
	
	// The attribute which is split at the branch leading to this node (or null if this is root)
	protected DecisionSplit split;
	
	// Children
	protected DecisionNode[] children;
	
	// Parent
	protected DecisionNode parent;
	
	// The target feature
	protected String targetAttr;
	
	// The predictor features for regression at the leaves
	protected List<String> predAttr;
	
	// Slope and intercept parameters. These are vectors and the values at 'nodeIndex' correspond to this node. Sigma is a scalar
	protected RealParameter slope, intercept, sigma;
	
	
	// Is this the left child (true child) or the (right child) false child, or the root (null)
	protected Boolean isTrueChild;
	
	// The split data
	protected Instances splitData = null;
	private regressionMode regression;
	
	
	
	
	@Override
	public void initAndValidate() {

	}

	
	/**
	 * Number of instances at this node (if the split has occurred yet)
	 * @return
	 */
	public int getNumInstances() {
		if (this.splitData == null) return 0;
		return this.splitData.size();
	}
	

	public DecisionSplit getSplit() {
		return this.split;
	}

	
	/**
	 * Meta data string for newick
	 */
	public void getMetaDataString(StringBuffer buf) {
		
		buf.append("ninstances=" + this.getNumInstances() + ",");
		buf.append("cond=" + this.isTrueChild + ",");
		
		if (this.isLeaf()) {
			
			// Regression info if leaf
			double intercept = this.getIntercept();
			buf.append("sigma=" + this.getSigma() + ",");
			buf.append("intercept=" + intercept + ",");
			String eqn = "eqn='" + WekaUtils.roundToSF(intercept, 3) + "/(1";
			for (int i = 0; i < this.predAttr.size(); i ++) {
				
				// Log the slope
				double slope = this.getSlope(i);
				buf.append("slope" + (i+1) + "=" + slope + ",");
				
				// Round to 3 sf for the equation
				eqn += " + " + WekaUtils.roundToSF(slope, 3) + "/x" + (i+1);
				
			}
			eqn += ")'";
			buf.append(eqn);
			
			
		}else {
			
			
			
			// Split info if non-leaf
			buf.append("attribute=" + this.split.getAttributeName(this.nodeIndex) + ",");
			buf.append("value=" + this.split.getSplitValue(this.nodeIndex, -1) + ",");
			buf.append("eqn='" + this.split.getCondition(this.nodeIndex, 3) + "'");
			
		}
		
	}
	



	/**
	 * The name of this node
	 * @return
	 */
	public String getName() {
		if (this.isLeaf()) {
			return "Leaf" + this.nodeIndex;
		}else {
			return "Split" + this.nodeIndex;
		}
	}
	
	/**
	 * Create a new node
	 * @param index
	 * @param split
	 * @param data
	 */
	public DecisionNode(int treeNum, int maxLeafCount, DecisionSplit split, RealParameter slope, RealParameter intercept, RealParameter sigma, String targetAttr, List<String> predAttr) {
		this.treeNum = treeNum;
		this.ntrees = maxLeafCount;
		this.nodeIndex = -1;
		this.lastNodeIndex = -1;
		this.split = split;
		this.slope = slope;
		this.intercept = intercept;
		this.sigma = sigma;
		this.depth = 0;
		this.isTrueChild = null;
		this.splitData = null;
		this.targetAttr = targetAttr;
		this.predAttr = predAttr;
		this.children = new DecisionNode[2];
		this.splitData = null;
	}
	


	public void parseFromNode(Node node) throws Exception {
		
		
		if (node.getChildCount() != 0 && node.getChildCount() != 2) {
			throw new Exception("Error: binary trees only. Node " + node.getID() + " has " + node.getChildCount() + " children");
		}
		
		this.removeChildren();
		
		if (!node.isLeaf()) {
			
			Node tchild = node.getChild(0);
			Node fchild = node.getChild(1);
			
			DecisionNode tchild2 = this.copy();
			DecisionNode fchild2 = this.copy();
			
			this.setTrueChild(tchild2);
			this.setFalseChild(fchild2);
			
			tchild2.parseFromNode(tchild);
			fchild2.parseFromNode(fchild);
			
			
		}
		
		
		
	}

	
	@Override
	public String toString() {
		return DecisionTreeLogger.toNewick(this);
	}


	public DecisionNode copy() {
        final DecisionNode node = new DecisionNode(this.treeNum, this.ntrees, split, slope, intercept, sigma, targetAttr, predAttr);
        node.depth = depth;
        node.nodeIndex = nodeIndex;
        node.parent = null;
        node.isTrueChild = this.isTrueChild;
        node.regression = this.regression;
        this.splitData = null;
        if (!isLeaf()) {
        	node.setTrueChild(children[0].copy());
        	node.setFalseChild(children[1].copy());
        }
        return node;
    } 
	
	
	/**
	 * Reset split data
	 */
	public void resetData() {
		this.splitData = null;
	}

	/**
	 * Attempt to split the data, returns false if this or a child cannot split it
	 * @param preSplitData
	 * @return
	 */
	public boolean splitData(Instances preSplitData) {
		
		
		this.splitData = preSplitData;
		if (!this.isLeaf()) {
			
			// Split for left and right children
			Instances[] splits = this.split.splitData(preSplitData, this.nodeIndex);
			
			
			// Valid?
			if (splits == null) return false;
			
			Instances splitTrue = splits[0];
			Instances splitFalse = splits[1];
			
			// Do both sides have instances?
			//if (splitTrue.size() == 0 || splitFalse.size() == 0) return false;
			
			if (!children[0].splitData(splitTrue)) return false;
			if (!children[1].splitData(splitFalse)) return false;
			
		}
		
		return true;
		
	}
	


	public void setIndex(int i) {
		this.lastNodeIndex = nodeIndex;
		this.nodeIndex = i;
	}
	
	
	
	public int getDepth() {
		return this.depth;
	}
	
	public DecisionNode[] getDecisionChildren(){
		return this.children;
	}
	
	public DecisionNode getParent() {
		return this.parent;
	}
	
	
	public void swapChildren() {
		if (!this.isLeaf()) {
			DecisionNode oldTrue = this.children[0];
			DecisionNode oldFalse = this.children[1];
			this.removeChildren();
			this.setTrueChild(oldFalse);
			this.setFalseChild(oldTrue);
		}
	}
	
	

	/**
	 * Return the true child (left child)
	 * @return
	 */
	public DecisionNode getTrueChild() {
		if (this.isLeaf()) return null;
		return this.children[0];
	}
	
	/**
	 * Return the false child (right child)
	 * @return
	 */
	public DecisionNode getFalseChild() {
		if (this.isLeaf()) return null;
		return this.children[1];
	}

	
	/**
	 * Node is a cherry if it is not a leaf, and its 2 children are leaves
	 * @return
	 */
	public boolean isCherry() {
		if (this.isLeaf()) return false;
		if (!this.children[0].isLeaf()) return false;
		if (!this.children[1].isLeaf()) return false;
		return true;
	}

	/**
	 * Set the true child (i.e. the child who will take the smaller numeric values (<=) or the specified nominal (==)
	 * @param child
	 */
	public void setTrueChild(DecisionNode child) {
		if (this.children[0] != null) {
			this.children[0].removeParent();
		}
		this.children[0] = child;
		this.children[0].regression = this.regression;
		child.setParent(this, true);
	}
	
	
	/**
	 * Set the false child (i.e. the child who will take the larger numeric values (>) or opposite of the specified nominal (!=)
	 * @param child
	 */
	public void setFalseChild(DecisionNode child) {
		if (this.children[1] != null) {
			this.children[1].removeParent();
		}
		this.children[1] = child;
		this.children[1].regression = this.regression;
		child.setParent(this, false);
	}
	
	
	public void removeChildren() {
		if (this.isLeaf()) return;
		this.children[0].removeParent();
		this.children[1].removeParent();
		this.children[0] = null;
		this.children[1] = null;
	}

	
	/**
	 * Set the parent of this node and specify whether this child is the true or false child of the parent
	 * @param parent
	 * @param trueChild
	 */
	protected void setParent(DecisionNode parent, boolean trueChild) {
		this.parent = parent;
		this.depth = parent.getDepth() + 1;
		this.isTrueChild = trueChild;
	}

	
	/**
	 * Remove the parent so this node becomes a root
	 */
	protected void removeParent() {
		this.parent = null;
		this.depth = 0;
		this.isTrueChild = null;
	}
	

	
	
	/**
	 * Is this a leaf node
	 * @return
	 */
	public boolean isLeaf() {
		return this.children[0] == null && this.children[1] == null;
	}
	
	
	
	/**
	 * The standard deviation
	 * @return
	 */
	protected double getSigma() {
		return this.sigma.getArrayValue();
	}


	/**
	 * The intercept
	 * @return
	 */
	protected double getIntercept() {
		if (!this.isLeaf()) throw new IllegalArgumentException("Error: there is no intercept because this is not a leaf!");
		int i = this.getTreeNum()*this.intercept.getDimension() / this.ntrees;
		int j = this.nodeIndex;
		return this.intercept.getArrayValue(i + j);
	}


	/**
	 * The slope of predictor i
	 * [m000, m011, ..., m00n, m010, ..., mtkn]
	 * Where mijk is tree i leaf j attribute k
	 * i is the number of this tree, j is the nodeindex of this leaf, and k is parsed below
	 * @param j 
	 * @return
	 */
	protected double getSlope(int k) {
		if (!this.isLeaf()) throw new IllegalArgumentException("Error: there is no slope because this is not a leaf!");
		int i = this.getTreeNum()*this.slope.getDimension() / this.ntrees;
		int j = this.nodeIndex*this.predAttr.size();
		int index = i + j + k;
		
		// Tmp
		//if (true) return this.slope.getArrayValue(k);
		
		return this.slope.getArrayValue(index);
	}
	
	
	private int getTreeNum() {
		return this.treeNum;
	}


	/**
	 * Predict the target feature value using the predictor (if this is a leaf)
	 * @param inst
	 * @return
	 */
	public Double predict(Instance inst) {
		if (!this.isLeaf()) return null;
		
		double y = this.getIntercept();
		for (int i = 0; i < this.predAttr.size(); i ++) {
			
			Attribute attr = inst.dataset().attribute(this.predAttr.get(i));
			double x = inst.value(attr);
			double m = this.getSlope(i);
			if (Double.isNaN(x)) continue;
			y = y + m*x;
			
		}
		
		// Regression mode
		switch (this.regression) {
		
			case logistic:{
				y = 1 / (1 + Math.exp(y));
				break;
			}
			
			
			case log:{
				y = Math.log(y);
				break;
			}
			
			case test:{
				
				double v = 0;
				for (int i = 0; i < this.predAttr.size(); i ++) {
					
					Attribute attr = inst.dataset().attribute(this.predAttr.get(i));
					double x = inst.value(attr);
					double m = this.getSlope(i);
					if (Double.isNaN(x)) continue;
					v = v + m/x;
					
				}
				
				y = this.getIntercept() / (1 + v);
				break;
			}
			
			default:{
				break;
			}
		
		
		}
	
		
		
		
		return y;
	}


	/**
	 * Size of tree
	 * @return
	 */
	public int getNodeCount() {
		int size = 1;
		if (!this.isLeaf()) {
			for (DecisionNode child : this.children) size += child.getNodeCount();
		}
		return size;
	}

	
	
	
	/**
	 * Compute log likelihood of this leaf
	 * @return
	 */
	public double getLogLikelihood() {
		
		if (!this.isLeaf()) return 0;
		if (this.splitData == null) return 0;
		
		// Get true and predicted values
		double[] trueYVals = this.getTargetVals();
		double[] predYVals = this.getPredictedTargetVals();
		
		
		double logP = 0;
		double sigmaVal = this.getSigma();
		for (int instNum = 0; instNum < this.splitData.numInstances(); instNum++) {
			
			double predY = predYVals[instNum];
			double trueY = trueYVals[instNum];
			
			// Likelihood 
			logP += logDensity(trueY, predY, sigmaVal);
			
		}
		
		return logP;
	}



	/**
	 * Log density of a normal distribution
	 * @param x
	 * @param mean
	 * @param standardDeviation
	 * @return
	 */
    public static double logDensity(double x, double mean, double standardDeviation) {
        double a = 1.0 / (Math.sqrt(2.0 * Math.PI) * standardDeviation);
        double b = -(x - mean) * (x - mean) / (2.0 * standardDeviation * standardDeviation);
        return Math.log(a) + b;
    }


    /**
     * Get the true target values
     * @return
     */
	public double[] getTargetVals() {
		
		if (!this.isLeaf()) return null;
		if (this.splitData == null) return null;
		
		double[] trueYVals = new double[this.splitData.numInstances()];
		Attribute target = this.splitData.attribute(this.targetAttr);
		for (int instNum = 0; instNum < this.splitData.numInstances(); instNum++) {
			
			// Get true y
			Instance inst = this.splitData.instance(instNum);
			double trueY = inst.value(target);
			if (Double.isNaN(trueY)) trueY = 0;
			trueYVals[instNum] = trueY;
		}
		
		return trueYVals;
	}

	/**
	 * Get the predicted target values at this leaf
	 * @return
	 */
	public double[] getPredictedTargetVals() {
		
		if (!this.isLeaf()) return null;
		if (this.splitData == null) return null;
		
		double[] predYVals = new double[this.splitData.numInstances()];
		
		for (int instNum = 0; instNum < this.splitData.numInstances(); instNum++) {
			
			// Get predicted y
			Instance inst = this.splitData.instance(instNum);
			double predY = this.predict(inst);
			predYVals[instNum] = predY;
		}
		
		return predYVals;
	}


	
	/**
	 * Returns the current unique index of this node
	 * @return
	 */
	public int getIndex() {
		return this.nodeIndex;
	}


	
	/**
	 * Returns the previous index of this node before it was last changed
	 * @return
	 */
	public int getLastIndex() {
		return this.lastNodeIndex;
	}


	/**
	 * Is this node an ancestor of 'node' ?
	 * @param node
	 * @return
	 */
	public boolean isAncestorOf(DecisionNode node) {
		if (this.getIndex() == node.getIndex()) return true;
		if (this.isLeaf()) return false;
		if (this.children[0].isAncestorOf(node)) return true;
		if (this.children[1].isAncestorOf(node)) return true;
		return false;
	}


	public boolean isTrueChild() {
		return this.isTrueChild;
	}


	public void setRegressionMode(regressionMode regression) {
		this.regression = regression;
		if (this.isLeaf()) return;
		this.children[0].setRegressionMode(regression);
		this.children[1].setRegressionMode(regression);
	}










	
	
	
}
