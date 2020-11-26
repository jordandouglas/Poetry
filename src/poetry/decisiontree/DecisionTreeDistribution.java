package poetry.decisiontree;


import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.math.distribution.PoissonDistributionImpl;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

import beast.core.Distribution;
import beast.core.Input;
import beast.core.State;
import beast.core.Input.Validate;
import beast.core.parameter.IntegerParameter;
import beast.core.parameter.RealParameter;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;



public class DecisionTreeDistribution extends Distribution {
	
	
	
	
	final public Input<Integer> maxLeafCountInput = new Input<>("maxLeafCount", "Maximum tree leafset size (constant)", 100);
	
	final public Input<IntegerParameter> attributePointerInput = new Input<>("pointer", "points to attributes", Validate.REQUIRED);
	final public Input<RealParameter> splitPointInput = new Input<>("split", "split point for numeric attributes", Validate.REQUIRED);
	final public Input<DecisionTree> treeInput = new Input<>("tree", "The decision tree", Validate.REQUIRED);
	
	final public Input<RealParameter> interceptInput = new Input<>("intercept", "Regression intercepts at the leaves", Validate.REQUIRED);
	final public Input<RealParameter> slopeInput = new Input<>("slope", "Regression slopes at the leaves", Validate.REQUIRED);
	final public Input<RealParameter> sigmaInput = new Input<>("sigma", "Regression standard deviation (scalar)", Validate.REQUIRED);
	
	final public Input<String> arffInput = new Input<>("data", "The .arff file which contains data", Validate.REQUIRED);
	final public Input<String> predInput = new Input<>("pred", "The predictor feature for regression at the leaves", Validate.REQUIRED);
	final public Input<String> targetInput = new Input<>("class", "The target feature", Validate.REQUIRED);
	
	
	List<Attribute> covariates;
	Attribute predictor;
	Attribute target;
	DecisionTree tree;
	
	
	
	
	// Which attribute does each split point to
	IntegerParameter attributePointer;
	
	// For numeric attributes only: where is the split (between 0 and 1)
	RealParameter splitPoints;
	
	int maxLeafCount;
	
	// The data
	Instances data;
	
	// Slope and intercept
	RealParameter slope, intercept, sigma;
	
	// Helper class for splitting
	DecisionSplit split;
	
	
	@Override
    public void initAndValidate() {
		

		this.attributePointer = attributePointerInput.get();
		this.splitPoints = splitPointInput.get();
		this.tree = treeInput.get();
		this.slope = slopeInput.get();
		this.intercept = interceptInput.get();
		this.sigma = sigmaInput.get();
		this.maxLeafCount = maxLeafCountInput.get();
		
		
		// Lower bound of sigma as 0
		this.sigma.setLower(0.0);
		

		// Set min and max values of the numeric split to (0,1)
		splitPoints.setBounds(0.0, 1.0);
		
		
		// Set dimension
		this.attributePointer.setDimension(this.maxLeafCount-1);
		this.splitPoints.setDimension(this.maxLeafCount-1);
		this.slope.setDimension(this.maxLeafCount);
		this.intercept.setDimension(this.maxLeafCount);
		
		// Read the arff file
		try {
			
			this.data = new DataSource(arffInput.get()).getDataSet();
			
			
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Error opening arff file " + arffInput.get());
		}
		
		// Set class
		this.target = data.attribute(this.targetInput.get());
		if (this.target == null) throw new IllegalArgumentException("Could not find target feature " + this.targetInput.get());
		if (!this.target.isNumeric()) throw new IllegalArgumentException("Target feature must be numeric");
		this.data.setClass(target);
		
		// Find predictor feature
		this.predictor = data.attribute(this.predInput.get());
		if (this.predictor == null) throw new IllegalArgumentException("Could not find predictor feature " + this.predInput.get());
		if (!this.predictor.isNumeric()) throw new IllegalArgumentException("Predictor feature must be numeric");
		
		// Prepare for attributes
		this.setAttributes(this.data);
		
		// Create the split helper class
		this.split = new DecisionSplit(this.attributePointer, this.splitPoints, this.covariates, this.data, this.tree);
		
		// Initialise the tree
		this.initTree();
		
		// Friendly printing
		this.printAttributes();
		
	}
	

	
	/**
	 * Print the attributes / class / predictor
	 */
	public void printAttributes() {
		System.out.println("-------------------------------------");
		System.out.println("Target feature: " + this.target.name());
		System.out.println("Predictor at leaves: " + this.predictor.name());
		System.out.print("Covariates: ");
		for (int i = 0; i < this.covariates.size(); i ++) {
			System.out.print(this.covariates.get(i).name());
			if (i < this.covariates.size() - 1) System.out.print(", ");
		}
		System.out.println();
		System.out.println("-------------------------------------");
	}
	


	
	/**
	 * Initialise the tree
	 */
	protected void initTree() {

		// Initialise as an empty tree
		DecisionNode root = this.newNode();
		this.tree.setRoot(root);
		
	}
	
	
	/**
	 * Create a new node with access to all necessary objects
	 * @return
	 */
	public DecisionNode newNode() {
		DecisionNode node = new DecisionNode(this.split, this.slope, this.intercept, this.sigma, this.target, this.predictor);
		return node;
	}
	
	
	/**
	 * Set the attributes
	 */
	protected void setAttributes(Instances data) {
		this.covariates = new ArrayList<>();
		for (int i = 0; i < data.numAttributes(); i ++) {
			Attribute attr = data.attribute(i);
			if (attr == this.target || attr == this.predictor) continue;
			
			// Check that every attribute is either numeric or nominal (no strings etc.)
			if (!attr.isNominal() && !attr.isNumeric()) {
				throw new IllegalArgumentException("Error: every attribute must be either numeric or nominal - " + attr.name());
			}
			
			this.covariates.add(data.attribute(i));
		}
		
		
		// Set min and max values of the pointer to the number of attributes (excluding target and predictor)
		int nattr = this.covariates.size();
		if (nattr == 0) {
			throw new IllegalArgumentException("Error: please ensure there is at least 1 covariate (excluding the target and predictor)");
		}
		this.attributePointer.setBounds(0, nattr-1);
		
	}
	
	
	/**
	 * Is this split numerical?
	 * @param index
	 * @return
	 */
	public boolean isNumeric(int index) {
		return this.covariates.get(index).isNumeric();
	}
	

	
	 @Override
	 public double calculateLogP() {
		 
		 logP = 0;
		 
		 
		 // Tree size exceeded?
		 if (this.tree.getLeafCount() > this.maxLeafCount) {
			 logP = Double.NEGATIVE_INFINITY;
			 return logP;
		 }
		 
		 // Ensure that the split is valid
		 boolean valid = tree.splitData(this.data);
		 if (!valid) {
			 logP = Double.NEGATIVE_INFINITY;
			 return logP;
		 }
		 
		 
		 // Calculate regression likelihood at the leaves
		 for (int nodeNum = 0; nodeNum < this.tree.getLeafCount(); nodeNum ++) {
			 DecisionNode leaf = this.tree.getNode(nodeNum);
			 double ll = leaf.getLogLikelihood();
			 logP += ll;
		 }
		 
		 
		
		 
		 return logP;
	 }


	@Override
	public List<String> getArguments() {
		return null;
	}

	
    @Override
    public boolean requiresRecalculation() {
    	return true; // treeInput.get().somethingIsDirty();
    }


	@Override
	public List<String> getConditions() {
		List<String> arguments = new ArrayList<>();
		arguments.add(attributePointer.getID());
		arguments.add(splitPoints.getID());
		arguments.add(intercept.getID());
		arguments.add(slope.getID());
		arguments.add(sigma.getID());
		arguments.add(tree.getID());
		return arguments;
	}
	
	/*
	public List<StateNode> getStateNodes() {
		List<StateNode> arguments = new ArrayList<>();
		arguments.add(attributePointer);
		arguments.add(splitPoints);
		arguments.add(intercept);
		arguments.add(slope);
		arguments.add(sigma);
		return arguments;
	}
*/


	@Override
	public void sample(State state, Random random) {
		
	}

	
	/**
	 * Reset the training data stored at each node in the tree
	 * @return
	 */
	public boolean split() {
		return tree.splitData(this.data);
	}
	

	/**
	 * Computes R2 and correlation by comparing known target values with those predicted
	 * Returns a double[] { R2, rho }
	 * @return
	 */
	public double[] getR2AndCorrelation() {
		
		
		
		if (!tree.splitData(this.data)) {
			//Log.warning("Dev error @getR2AndCorrelation: invalid split");
			return new double[] { 0, 0 };
		}
		
		double[] trueY = new double[this.data.size()];
		double[] predY = new double[this.data.size()];
		
		//System.out.println(trueY.length + " total instances");
		
		// Calculate regression R2 at the leaves
		int instNum = 0;
		int nleaves = this.tree.getLeafCount();
		for (int nodeNum = 0; nodeNum < nleaves; nodeNum ++) {
			DecisionNode leaf = this.tree.getNode(nodeNum);
			double[] trueYLeaf = leaf.getTargetVals();
			double[] predYLeaf = leaf.getPredictedTargetVals();
			
			//System.out.println(trueYLeaf.length + "/" + predYLeaf.length);
			
			
			// Put leaf-wise values in main list
			for (int i = 0; i < trueYLeaf.length; i ++) {
				trueY[instNum] = trueYLeaf[i];
				predY[instNum] = predYLeaf[i];
				instNum++;
			}
			
		}
		
		
		
		// Total sum of squares
		double TSS = getTSS(trueY);
		
		// Residual sum of squares
		double RSS = getRSS(trueY, predY);
		
		// R squared
		double R2 =  1 - RSS/TSS;
		
		// Correlation
		PearsonsCorrelation pc = new PearsonsCorrelation();
		double rho = pc.correlation(trueY, predY);
		
		return new double[] { R2, rho };
	}



	/**
	 * Calculate the total sum of squares
	 * @param y
	 * @return
	 */
	public static double getTSS(double[] y) {
		
		// Find the mean
		double mean = 0;
		for (int i = 0; i < y.length; i ++) {
			mean += y[i];
		}
		mean /= y.length;
		
		// Total sum of squares
		double SS = 0;
		for (int i = 0; i < y.length; i ++) {
			SS += (y[i] - mean)*(y[i] - mean);
		}
		
		return SS;
		
	}

	/**
	 * Calculate residual sum of squares
	 * @param y
	 * @param x
	 * @return
	 */
	public static double getRSS(double[] y, double[] x) {
		double SS = 0;
		for (int i = 0; i < y.length; i ++) {
			SS += (x[i] - y[i])*(x[i] - y[i]);
		}
		return SS;
	}


	/**
	 * The maximum size the tree can be
	 * @return
	 */
	public int getMaxLeafCount() {
		return this.maxLeafCount;
	}

	/*
	public static double getCovariance(double[] x, double[] y) {
		
		
		// Mean
		double xMean = 0;
		double yMean = 0;
		for (int i = 0; i < x.length; i++) {
			xMean += x[i];
			yMean += y[i];
		}
		xMean /= x.length;
		yMean /= x.length;
		

		// Covariance
		double covariance = 0;
		for (int i = 0; i < x.length; i++) {
			covariance += (x[i] - xMean)*(y[i] - yMean);
		}
		
		return covariance / x.length;
	}
	*/

	
	
}
