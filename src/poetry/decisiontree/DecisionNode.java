package poetry.decisiontree;




import beast.core.BEASTObject;
import beast.core.Description;
import beast.core.parameter.RealParameter;
import poetry.util.WekaUtils;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;



@Description("A node which acts as linear regression at the leaf or can split over an attribute to give two or more children")
public class DecisionNode extends BEASTObject {

	// Unique number of this node. The n leaves are numbered 0 - n-1
	int nodeIndex;
	
	// Depth of this node
	int depth;
	
	// The attribute which is split at the branch leading to this node (or null if this is root)
	DecisionSplit split;
	
	// Children
	DecisionNode[] children;
	
	// Parent
	DecisionNode parent;
	
	// The target feature
	Attribute targetAttr;
	
	// The predictor feature for regression at the leaves
	Attribute predAttr;
	
	// Slope and intercept parameters. These are vectors and the values at 'nodeIndex' correspond to this node. Sigma is a scalar
	RealParameter slope, intercept, sigma;
	
	
	// Is this the left child (true child) or the (right child) false child, or the root (null)
	Boolean isTrueChild;
	
	// The split data
	Instances splitData = null;
	
	
	@Override
	public void initAndValidate() {

	}

	
	/**
	 * Create a new node
	 * @param index
	 * @param split
	 * @param data
	 */
	public DecisionNode(DecisionSplit split, RealParameter slope, RealParameter intercept, RealParameter sigma, Attribute targetAttr, Attribute predAttr) {
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
	
	public DecisionNode copy() {
        final DecisionNode node = new DecisionNode(split, slope, intercept, sigma, targetAttr, predAttr);
        node.depth = depth;
        node.nodeIndex = nodeIndex;
        node.parent = null;
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
			if (splitTrue.size() == 0 || splitFalse.size() == 0) return false;
			
			if (!children[0].splitData(splitTrue)) return false;
			if (!children[1].splitData(splitFalse)) return false;
			
		}
		
		return true;
		
	}
	


	public void setIndex(int i) {
		this.nodeIndex = i;
	}
	
	
	
	public int getDepth() {
		return this.depth;
	}
	
	public DecisionNode[] getChildren(){
		return this.children;
	}
	
	public DecisionNode getParent() {
		return this.parent;
	}
	
	
	public void swapChildren() {
		if (!this.isLeaf()) {
			DecisionNode tmp = this.children[0];
			this.children[0] = this.children[1];
			this.children[1] = tmp;
			this.children[0].isTrueChild = true;
			this.children[1].isTrueChild = false;
		}
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
		child.setParent(this, true);
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
	 * Set the false child (i.e. the child who will take the larger numeric values (>) or opposite of the specified nominal (!=)
	 * @param child
	 */
	public void setFalseChild(DecisionNode child) {
		if (this.children[1] != null) {
			this.children[1].removeParent();
		}
		this.children[1] = child;
		child.setParent(this, false);
	}
	
	
	public void removeChildren() {
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
	 * Predict the target feature value using the predictor (if this is a leaf)
	 * @param x
	 * @return
	 */
	public Double predict(double x) {
		if (!this.isLeaf()) return null;
		
		double c = this.intercept.getArrayValue(this.nodeIndex);
		double m = this.slope.getArrayValue(this.nodeIndex);
		return c + x*m;
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
		double sigmaVal = this.sigma.getArrayValue();
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
		int yIndex = WekaUtils.getIndexOfColumn(this.splitData, this.targetAttr.name());
		for (int instNum = 0; instNum < this.splitData.numInstances(); instNum++) {
			
			// Get true y
			Instance inst = this.splitData.instance(instNum);
			double trueY = inst.value(yIndex);
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
		int xIndex = WekaUtils.getIndexOfColumn(this.splitData, this.predAttr.name());
		for (int instNum = 0; instNum < this.splitData.numInstances(); instNum++) {
			
			// Get predicted y
			Instance inst = this.splitData.instance(instNum);
			double x = inst.value(xIndex);
			double predY = this.predict(x);
			predYVals[instNum] = predY;
		}
		
		return predYVals;
	}






	
	
	
}
