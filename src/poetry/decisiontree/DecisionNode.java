package poetry.decisiontree;




import java.util.HashMap;
import java.util.List;


import beast.core.Description;
import beast.core.parameter.RealParameter;
import beast.core.util.Log;
import beast.evolution.tree.Node;
import poetry.decisiontree.DecisionTreeInterface.regressionDistribution;
import poetry.distribution.FlexibleDirichlet;
import poetry.decisiontree.DecisionTreeDistribution.ResponseMode;
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
	protected List<String> targetAttr;
	
	// The predictor features for regression at the leaves
	protected List<String> predAttr;
	
	// Slope and intercept parameters. These are vectors and the values at 'nodeIndex' correspond to this node. Sigma is a scalar
	protected RealParameter slope, intercept, sigmaOrTau, multinomialP;
	
	
	// Is this the left child (true child) or the (right child) false child, or the root (null)
	protected Boolean isTrueChild;
	
	// The split data
	protected Instances splitData = null;
	protected ResponseMode regression;
	private regressionDistribution regressionDistribution;
	
	// Metadata tokens if parsed from newick string
	HashMap<String, String> metadataTokens;
	
	
	
	
	
	@Override
	public void initAndValidate() {

	}

	
	/**
	 * Number of instances at this node (if the split has occurred yet)
	 * @return
	 */
	public int getNumInstances() {
		
		// Metadata token?
		if (this.metadataTokens.containsKey("ninstances")) return Integer.parseInt(this.metadataTokens.get("ninstances"));
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
		
		
		buf.append("cond=" + this.isTrueChild + ",");
		buf.append("ninstances=" + this.getNumInstances() + ",");
		
		if (this.isLeaf()) {
			
			// Regression info if leaf
			double intercept = this.getIntercept();
			buf.append("sigma=" + this.getSigma() + ",");
			buf.append("intercept=" + intercept + ",");
			if (this.split != null) {
				String eqn = "eqn='" + WekaUtils.roundToSF(intercept, 3) + "/(1";
				for (int i = 0; i < this.predAttr.size(); i ++) {
					
					// Log the slope
					double slope = this.getSlope(i);
					buf.append("slope_" + this.targetAttr.get(i) + "=" + slope + ",");
					
					
					buf.append("intercept_" + this.targetAttr.get(i) + "=" + this.getIntercept(i) + ",");
					
					// Round to 3 sf for the equation
					eqn += " + " + WekaUtils.roundToSF(slope, 3) + "/x" + (i+1);
					
				}
				eqn += ")'";
				buf.append(eqn);
			}
			
			
		}else {
			
			
			
			// Split info if non-leaf
			String attribute = this.getAttributeName();
			String value = this.getAttributeValueName();
			buf.append("attribute=" + attribute + ",");
			buf.append("value=" + value + ",");
			if (this.split != null) buf.append("eqn='" + this.split.getCondition(this.nodeIndex, 3) + "'");
			
		}
		
	}
	


	/**
	 * The name of this attribute
	 * @return
	 */
	public String getAttributeName() {
		if (this.isLeaf()) throw new IllegalArgumentException("Error: there is no attribute because this is a leaf!");
		if (this.metadataTokens.containsKey("attribute")) return this.metadataTokens.get("attribute"); 
		return this.split.getAttributeName(this.nodeIndex);
	}
	
	
	/**
	 * The value of this attribute split
	 * @return
	 */
	public String getAttributeValueName() {
		if (this.isLeaf()) throw new IllegalArgumentException("Error: there is no attribute value because this is a leaf!");
		if (this.metadataTokens.containsKey("value")) return this.metadataTokens.get("value"); 
		return this.split.getSplitValue(this.nodeIndex, -1);
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
	public DecisionNode(int treeNum, int maxLeafCount, DecisionSplit split, RealParameter slope, RealParameter intercept, RealParameter sigma, RealParameter multinomialP, List<String> targetAttr, List<String> predAttr, ResponseMode regression) {
		this.treeNum = treeNum;
		this.ntrees = maxLeafCount;
		this.nodeIndex = -1;
		this.lastNodeIndex = -1;
		this.split = split;
		this.slope = slope;
		this.intercept = intercept;
		this.sigmaOrTau = sigma;
		this.multinomialP = multinomialP;
		this.depth = 0;
		this.isTrueChild = null;
		this.splitData = null;
		this.targetAttr = targetAttr;
		this.predAttr = predAttr;
		this.children = new DecisionNode[2];
		this.splitData = null;
		this.metadataTokens = new HashMap<>();
		this.regression = regression;
	}
	


	public DecisionNode() {
		this.children = new DecisionNode[2];
		this.nodeIndex = -1;
		this.lastNodeIndex = -1;
		this.splitData = null;
		this.isTrueChild = null;
		this.splitData = null;
		this.depth = 0;
		this.metadataTokens = new HashMap<>();
	}


	public void parseFromNode(Node node) throws Exception {
		
		// Parse metadata
		String metadata = node.metaDataString;
		this.metadataTokens = new HashMap<>();
		String[] bits = metadata.split(",");
		for (String bit : bits) {
			String key = bit.split("=")[0];
			String value = bit.split("=")[1];
			this.metadataTokens.put(key, value);
		}
		
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
        final DecisionNode node = new DecisionNode(treeNum, ntrees, split, slope, intercept, sigmaOrTau, multinomialP, targetAttr, predAttr, regression);
        node.depth = depth;
        node.nodeIndex = nodeIndex;
        node.parent = null;
        node.isTrueChild = this.isTrueChild;
        node.regressionDistribution = this.regressionDistribution;
        this.splitData = null;
        if (!isLeaf()) {
        	node.setTrueChild(children[0].copy());
        	node.setFalseChild(children[1].copy());
        }
        //node.metadataTokens = (HashMap<String, String>) this.metadataTokens.clone();
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
	public double getSigma() {
		if (this.metadataTokens.containsKey("sigma")) return Double.parseDouble(this.metadataTokens.get("sigma"));
		return this.sigmaOrTau.getArrayValue();
	}


	/**
	 * The intercept
	 * @return
	 */
	public double getIntercept() {
		if (!this.isLeaf()) throw new IllegalArgumentException("Error: there is no intercept because this is not a leaf!");
		if (this.metadataTokens.containsKey("intercept")) return Double.parseDouble(this.metadataTokens.get("intercept"));
		int i = this.getTreeNum()*this.intercept.getDimension() / this.ntrees;
		int j = this.nodeIndex;
		return this.intercept.getArrayValue(i + j);
	}
	
	

	public double getIntercept(int k) {
		if (!this.isLeaf()) throw new IllegalArgumentException("Error: there is no intercept because this is not a leaf!");
		if (this.metadataTokens.containsKey("intercept" + (k+1))) return Double.parseDouble(this.metadataTokens.get("intercept" + (k+1)));
		int i = this.getTreeNum()*this.intercept.getDimension() / this.ntrees;
		int j = this.nodeIndex*this.predAttr.size();
		int index = i + j + k;
		
		return this.intercept.getArrayValue(index);
	}
	
	



	/**
	 * The slope of predictor i
	 * [m000, m011, ..., m00n, m010, ..., mtkn]
	 * Where mijk is tree i leaf j attribute k
	 * i is the number of this tree, j is the nodeindex of this leaf, and k is parsed below
	 * @param j 
	 * @return
	 */
	public double getSlope(int k) {
		if (!this.isLeaf()) throw new IllegalArgumentException("Error: there is no slope because this is not a leaf!");
		if (this.metadataTokens.containsKey("slope" + (k+1))) return Double.parseDouble(this.metadataTokens.get("slope" + (k+1)));
		int i = this.getTreeNum()*this.slope.getDimension() / this.ntrees;
		int j = this.nodeIndex*this.predAttr.size();
		int index = i + j + k;
		
		
		return this.slope.getArrayValue(index);
	}
	
	
	private int getTreeNum() {
		return this.treeNum;
	}

	
	
	
	/**
	 * Return the value predicted by the slope and intercept
	 * For normal this happens to also be the mean
	 * For dirichlet, this is alpha
	 * @return
	 */
	private double[] getSlopeInterceptResponse(Instance inst) {
		

		double[] response = new double[this.targetAttr.size()];
		
		for (int targetNum = 0; targetNum < response.length; targetNum ++) {
			
			double y = this.getIntercept();
			for (int i = 0; i < this.predAttr.size(); i ++) {
				
				if (this.regression == ResponseMode.dirichlet && targetNum != i) continue; 
				
				
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
				
				
				case dirichlet:{
					
					
					double v = 0;
					double addOn = 0; //this.sigmaOrTau.getArrayValue(1);	
					
					Attribute attr = inst.dataset().attribute(this.predAttr.get(targetNum));
					double x = inst.value(attr);
					
					double m = this.getSlope(targetNum);
					if (!Double.isNaN(x)) {
						v = m/x;
					}
					
					if (x == 0) y = 0;
					
					else {
						x = Math.log(x / (1-x));
						y = m*x + this.getIntercept(targetNum);
						y = 1 / (1 + Math.exp(-y)); 
						//System.out.println(x + "," + y);
					}
					
					//else y = this.getIntercept(targetNum) / (1 + v) + addOn;
					
					//System.out.println(targetNum + ": " + this.getIntercept() + "," + x + "," + m + " alpha = " + y);
					
					
					break;
				}
				
				default:{
					break;
				}
			
			
			}
			

			
			response[targetNum] = y;
			
		}
		
		
		
		
		// Normalise
		if (this.regression == ResponseMode.dirichlet) {
			double ysum = 0;
			double newsum = this.getSigma();
			for (int i = 0; i < response.length; i ++) ysum += response[i];
			for (int i = 0; i < response.length; i ++) response[i] = newsum * response[i] / ysum;
		}
	
		
		return response;
		
	}
	
	


	/**
	 * Predict the target feature value using the predictor (if this is a leaf)
	 * @param inst
	 * @return
	 */
	public double[] predict(Instance inst) {
		if (!this.isLeaf()) return null;
		
		double[] response = this.getSlopeInterceptResponse(inst);
		
		
		// If this is Dirichlet, the response is just alpha and the mean values need to be calculated from the distribution
		if (this.regression == ResponseMode.dirichlet) {
			
			double[] alpha = new double[response.length];
			System.arraycopy(response, 0, alpha, 0, response.length);
			for (int targetNum = 0; targetNum < response.length; targetNum ++) {
				response[targetNum] = FlexibleDirichlet.getExpectation(targetNum, alpha, this.getMultinomialP(), this.getSigma());
			}
			
			/*
			System.out.print("alpha: ");
			for (int targetNum = 0; targetNum < response.length; targetNum ++) {
				System.out.print(alpha[targetNum] + " ");
			}
			System.out.print(", y: ");
			for (int targetNum = 0; targetNum < response.length; targetNum ++) {
				System.out.print(response[targetNum] + " ");
			}
			System.out.println();
			
			*/
		}
		
		// For normal, the response IS the mean
		
		return response;
	}


	public double[] getMultinomialP() {
		if (this.multinomialP == null) return null;
		double[] probs = new double[this.multinomialP.getDimension()];
		for (int p = 0; p < probs.length; p ++) {
			probs[p] = this.multinomialP.getArrayValue(p);
		}
		return probs;
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
		double[][] trueYVals = this.getTargetVals();
		double[][] responseVals = this.getSlopeInterceptResponseVals();
		
		
		double logP = 0;
		double sigmaVal = this.getSigma();
		double[] probs = this.getMultinomialP();
		
    	if (sigmaVal <= 0) return Double.NEGATIVE_INFINITY;
    	
    	double a = -Math.log((Math.sqrt(2.0 * Math.PI) * sigmaVal));
    	double b;
		for (int instNum = 0; instNum < this.splitData.numInstances(); instNum++) {
			
			// Likelihood 
			double[] response = responseVals[instNum];
			double[] trueY = trueYVals[instNum];
			
			
			// Dirichlet: response is alpha
			if (this.regression == ResponseMode.dirichlet) {
				logP += FlexibleDirichlet.calcLogP(trueY, response, probs, sigmaVal);
			}
			
			// Normal: response is mu (which happens to be the expected value)
			else {
				
				// Univariate normal distribution where the predicted value is the mean
				b = -(trueY[0] - response[0]) * (trueY[0] - response[0]) / (2.0 * sigmaVal * sigmaVal);
				logP += a + b;
				
			}
			
			
			
			
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
	
	/*
    public double logDensity(double x, double mean, double standardDeviation) {
    	
    	
    	if (standardDeviation <= 0) return Double.NEGATIVE_INFINITY;
    	
    	
    	double a = 1.0 / (Math.sqrt(2.0 * Math.PI) * standardDeviation);
        double b = -(x - mean) * (x - mean) / (2.0 * standardDeviation * standardDeviation);
        return Math.log(a) + b;

        /*
        
    	// tmp hack
    	if (this.regressionDistribution == null) this.regressionDistribution = regressionDistribution.normal;
    	
    	switch (this.regressionDistribution) {
    	
	    	case ndouble a = 1.0 / (Math.sqrt(2.0 * Math.PI) * standardDeviation);
	        double b = -(x - mean) * (x - mean) / (2.0 * standardDeviation * standardDeviation);
	        return Math.log(a) + b;ormal:{
	    		
	    	}
	    	
	    	case student: {
	    		TDistribution dist = new TDistribution(standardDeviation);
	    		return Math.log(dist.density(x - mean));
	    	}
	    	
    	
    	}
    	
    	
    	return 0;
    	
       
    }
	*/

    /**
     * Get the true target values
     * @return
     */
	public double[][] getTargetVals() {
		
		if (!this.isLeaf()) return null;
		if (this.splitData == null) return null;
		
		double[][] trueYVals = new double[this.splitData.numInstances()][];
		
		for (int instNum = 0; instNum < this.splitData.numInstances(); instNum++) {
			
			Instance inst = this.splitData.instance(instNum);
			trueYVals[instNum] = new double[this.targetAttr.size()];
			
			// Get true y for each target
			for (int targetNum = 0; targetNum < this.targetAttr.size(); targetNum ++) {
				
				Attribute target = this.splitData.attribute(this.targetAttr.get(targetNum));
				double trueY = inst.value(target);
				if (Double.isNaN(trueY)) trueY = 0;
				trueYVals[instNum][targetNum] = trueY;
				
			}
			
		}
		
		return trueYVals;
	}

	/**
	 * Get the response values at this leaf (not necessarily the mean)
	 * @return
	 */
	public double[][] getSlopeInterceptResponseVals() {
		
		if (!this.isLeaf()) return null;
		if (this.splitData == null) return null;
		
		double[][] predYVals = new double[this.splitData.numInstances()][];
		
		for (int instNum = 0; instNum < this.splitData.numInstances(); instNum++) {
			
			predYVals[instNum] = new double[this.targetAttr.size()];
			
			// Get predicted y for each target
			Instance inst = this.splitData.instance(instNum);
			double[] predY = this.getSlopeInterceptResponse(inst);
			predYVals[instNum] = predY;
			
			
		}
		
		return predYVals;
	}
	
	
	/**
	 * Get the predicted target values at this leaf
	 * @return
	 */
	public double[][] getPredictionVals() {
		
		if (!this.isLeaf()) return null;
		if (this.splitData == null) return null;
		
		double[][] predYVals = new double[this.splitData.numInstances()][];
		
		for (int instNum = 0; instNum < this.splitData.numInstances(); instNum++) {
			
			predYVals[instNum] = new double[this.targetAttr.size()];
			
			// Get predicted y for each target
			Instance inst = this.splitData.instance(instNum);
			double[] predY = this.predict(inst);
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


	public void setRegressionMode(ResponseMode regression) {
		this.regression = regression;
		if (this.isLeaf()) return;
		this.children[0].setRegressionMode(regression);
		this.children[1].setRegressionMode(regression);
	}
	
	public void setRegressionMode(ResponseMode regression, regressionDistribution regressionDistribution) {
		this.regression = regression;
		this.regressionDistribution = regressionDistribution;
		if (this.isLeaf()) return;
		this.children[0].setRegressionMode(regression, regressionDistribution);
		this.children[1].setRegressionMode(regression, regressionDistribution);
		
	}


	/**
	 * Find the instance corresponding to this leaf
	 * @param inst - a single instance (in Instances object)
	 * @return
	 */
	public DecisionNode getLeaf(Instances instances) {
		
		if (this.isLeaf()) return this;
		if (instances.size() != 1) throw new IllegalArgumentException("Dev error: make sure there is exactly 1 instance");
		
		
		if (this.split == null) {
			
			
			String attribute = this.getAttributeName();
			String value = this.getAttributeValueName();
			DecisionNode child;
			if (instances.attribute(attribute) == null) {
				Log.warning("Warning: cannot find attribute " + attribute + " in instances. Selecting largest subtree...");
				child = this.getFalseChild().getNumInstances() > this.getTrueChild().getNumInstances() ? this.getFalseChild() : this.getTrueChild();
			}else {
				
				Attribute attr = instances.attribute(attribute);
				boolean match = true;
				if (attr.isNominal()) {
					
					// Does the instance exactly match?
					int instanceValue = attr.indexOfValue(instances.get(0).stringValue(attr));
					int index = attr.indexOfValue(value);
					
					// New value - take largest subtree
					match = instanceValue == index;
					
					
					Log.warning(attribute + (match ? " == " : " != ") + value);
					
				}else {
					
					double instanceValue = instances.get(0).value(attr);
					match = instanceValue <= Double.parseDouble(value);
					Log.warning(attribute + (match ? " <= " : " > ") + value);
					
				}
				
				child = match ? this.getTrueChild() : this.getFalseChild();
				
			}
			
			return child.getLeaf(instances);
			
			
		}else {
			
			Instances[] splits = this.split.splitData(instances, this.nodeIndex);
			Instances splitTrue = splits[0];
			
			if (splitTrue.size() == 1) return this.getTrueChild().getLeaf(instances);
			else return this.getFalseChild().getLeaf(instances);
			
		}
		
		
		
	}


	public ResponseMode getRegressionMode() {
		return this.regression;
	}


	public String getToken(String string) {
		return this.metadataTokens.get(string);
	}


	










	
	
	
}
