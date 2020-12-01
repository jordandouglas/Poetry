package poetry.decisiontree;


import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.math.distribution.PoissonDistributionImpl;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

import beast.core.Distribution;
import beast.core.Function;
import beast.core.Input;
import beast.core.State;
import beast.core.Input.Validate;
import beast.core.parameter.IntegerParameter;
import beast.core.parameter.RealParameter;
import beast.core.util.Log;
import beast.util.Randomizer;
import beast.util.Transform;
import poetry.util.WekaUtils;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;



public class DecisionTreeDistribution extends Distribution {
	
	
	final public Input<WekaData> dataInput = new Input<>("data", "The data", Validate.REQUIRED);
	
	final public Input<Integer> maxLeafCountInput = new Input<>("maxLeafCount", "Maximum tree leafset size (constant)", 100);
	final public Input<Integer> minInstancesPerLeafInput = new Input<>("minInstances", "Minimum number of instances per leaf (constant)", 30);
	
	final public Input<IntegerParameter> attributePointerInput = new Input<>("pointer", "points to attributes", Validate.REQUIRED);
	final public Input<RealParameter> splitPointInput = new Input<>("split", "split point for numeric attributes", Validate.REQUIRED);
	final public Input<DecisionTree> treeInput = new Input<>("tree", "The decision tree", Validate.REQUIRED);
	
	final public Input<RealParameter> interceptInput = new Input<>("intercept", "Regression intercepts at the leaves", Validate.REQUIRED);
	final public Input<RealParameter> slopeInput = new Input<>("slope", "Regression slopes at the leaves");
	final public Input<RealParameter> sigmaInput = new Input<>("sigma", "Regression standard deviation (scalar)", Validate.REQUIRED);
	
	
	
	
	final public Input<List<Transform>> predInput = new Input<>("pred", 
			"one or more transformed features to be moved. Each feature must be a Feature object\n"
			+ "For scale parameters use LogTransform (where e.g. scale operators were used).\n"
			+ "For location parameter use NoTransform (where e.g. random walk operators were used).\n"
			+ "For parameters that sum to a constant use LogConstrainedSumTransform  (where e.g. delta-exchange operators were used).", new ArrayList<>()); 
	
	final public Input<Transform> targetInput = new Input<>("class", "The target feature and its transformation", Validate.REQUIRED);
	
	
	
	final public Input<RealParameter> shapeInput = new Input<>("shape", "Dirichlet shape on the number of instances at each leaf. Set to 0 for uniform.", Validate.REQUIRED);
	
	
	WekaData wekaData;
	List<Attribute> covariates;
	List<Attribute> predictors;
	Attribute target;
	DecisionTree tree;
	int nPredictors;
	
	
	
	
	// Which attribute does each split point to
	IntegerParameter attributePointer;
	
	// For numeric attributes only: where is the split (between 0 and 1)
	RealParameter splitPoints;
	
	int maxLeafCount;
	int minInstancesPerLeaf;
	
	// The data
	Instances trainingData;
	Instances testData;
	
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
		if (this.maxLeafCount <= 0) this.maxLeafCount = 1;
		this.minInstancesPerLeaf = minInstancesPerLeafInput.get();
		this.wekaData = dataInput.get();
		

		
		
		// Lower bound of sigma as 0
		this.sigma.setLower(0.0);
		

		// Set min and max values of the numeric split to (0,1)
		splitPoints.setBounds(0.0, 1.0);
		
		// Load class/predictors and transform them
		this.trainingData = wekaData.getTrainingData();
		this.testData = wekaData.getTestData();
		this.transform(this.trainingData);
		this.transform(this.testData);
		
		// Slope is required iff there are predictors
		this.nPredictors = this.predictors.size();
		if (this.nPredictors > 0) {
			if (slopeInput.get() == null) {
				throw new IllegalArgumentException("Error: please provide slope because there are predictors");
			}
		}else {
			if (slopeInput.get() != null) {
				throw new IllegalArgumentException("Error: do not provide slope because there are no predictors");
			}
		}
		
		
		// Set dimension
		this.attributePointer.setDimension(this.maxLeafCount-1);
		this.splitPoints.setDimension(this.maxLeafCount-1);
		if (this.slope != null) this.slope.setDimension(this.maxLeafCount * this.nPredictors);
		this.intercept.setDimension(this.maxLeafCount);
		
		
		// Prepare for attributes
		this.prepareTargetAndPredictor(this.trainingData);
		this.setAttributes(this.trainingData);
		
		// Create the split helper class
		this.split = new DecisionSplit(this.attributePointer, this.splitPoints, this.covariates, this.trainingData, this.tree);
		
		// Initialise the tree
		this.initTree();
		
		
		// Set initial values 
		for (int i = 0; i < splitPoints.getDimension(); i ++) {
			splitPoints.setValue(i, Randomizer.nextDouble());
			attributePointer.setValue(i, Randomizer.nextInt(attributePointer.getUpper()));
		}
		
		
		// Friendly printing
		this.printSummary();
		
	}
	

	
	
	private void prepareTargetAndPredictor(Instances data) {
		
		
		// Target feature
		Transform ttarget = this.targetInput.get();
		Feature targetFeature = (Feature)(ttarget.getF().get(0));
		//targetFeature.setDataset(data);
		this.target = targetFeature.getAttribute();
		data.setClass(target);
		
		
		// Find predictor features
		this.predictors = new ArrayList<>();
		for (Transform t : this.predInput.get()) {
			for (Function f : t.getF()) {
				Feature predictorFeature = (Feature)f;
				//predictorFeature.setDataset(data);
				this.predictors.add(predictorFeature.getAttribute());
			}
			
		}
		
	}




	/**
	 * Load and transform class and predictor(s)
	 */
	private void transform(Instances data) {
		
		
		// Target feature
		Transform ttarget = this.targetInput.get();
		if (ttarget.getF().size() != 1) {
			throw new IllegalArgumentException("Error: please ensure that 'class' has only one feature");
		}
		Function func = ttarget.getF().get(0);
		if (!(func instanceof Feature)) {
			throw new IllegalArgumentException("Error: please ensure that 'class' is a transformation of " + Feature.class.getCanonicalName());
		}
		Feature targetFeature = (Feature)func;
		//targetFeature.setDataset(data);
		this.target = targetFeature.getAttribute();
		data.setClass(target);
		targetFeature.transform(ttarget);

		
		// Predictor features
		this.predictors = new ArrayList<>();
		for (Transform t : this.predInput.get()) {
			
			for (Function f : t.getF()) {
				if (!(f instanceof Feature)) {
					throw new IllegalArgumentException("Error: please ensure that every predictor is a transformation of " + Feature.class.getCanonicalName());
				}
				
				Feature predictorFeature = (Feature)f;
				//predictorFeature.setDataset(data);
				this.predictors.add(predictorFeature.getAttribute());
				predictorFeature.transform(t);
				
			}
			
			
		}
		

		
		
	}



	/**
	 * Print the attributes / class / predictor
	 */
	public void printSummary() {
		System.out.println("-------------------------------------");
		System.out.println("Data organised into an " + this.trainingData.size() + "/" + this.testData.size() + " training/test split");
		System.out.println("Maximum leaf count: " + this.maxLeafCount);
		System.out.println("Minimum number of instances per leaf: " + this.minInstancesPerLeaf);
		System.out.println("Target feature: " + this.target.name());
		
		
		// Predictors
		if (!this.predictors.isEmpty()) {
			System.out.print("Predictors at leaves: "); 
			for (int i = 0; i < this.predictors.size(); i ++) {
				System.out.print(this.predictors.get(i).name());
				if (i < this.predictors.size() - 1) System.out.print(", ");
			}
			System.out.println();
		}
		
		
		// Covariates
		System.out.print("Covariates at internal nodes: ");
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
		DecisionNode node = new DecisionNode(this.split, this.slope, this.intercept, this.sigma, this.target, this.predictors);
		return node;
	}
	
	
	/**
	 * Set the attributes
	 */
	protected void setAttributes(Instances data) {
		this.covariates = new ArrayList<>();
		for (int i = 0; i < data.numAttributes(); i ++) {
			Attribute attr = data.attribute(i);
			if (attr == this.target) continue;
			if (this.predictors.contains(attr)) continue;
			
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
			 //Log.warning("too big");
			 logP = Double.NEGATIVE_INFINITY;
			 return logP;
		 }
		 
		 // Ensure that the split is valid
		 boolean valid = tree.splitData(this.trainingData);
		 if (!valid) {
			 Log.warning("invalid");
			 logP = Double.NEGATIVE_INFINITY;
			 return logP;
		 }
		 
		 
		 // Dirichlet distribution on instances per leaf
		 double alpha = shapeInput.get().getArrayValue();
		 double sumAlpha = alpha * this.tree.getLeafCount();
		 int ninstances = this.trainingData.size(); 
		 for (int i = 0; i < this.tree.getLeafCount(); i++) {
			 int n = this.tree.getNode(i).getNumInstances();
			 double pinstances = 1.0 * n / ninstances;
			 if (n < this.minInstancesPerLeaf) {
				 logP = Double.NEGATIVE_INFINITY;
				 return logP;
			 }
			 logP += (alpha - 1) * Math.log(pinstances);
			 logP -= org.apache.commons.math.special.Gamma.logGamma(alpha);
		 }
		 logP += org.apache.commons.math.special.Gamma.logGamma(sumAlpha);
		 
		 
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
		return tree.splitData(this.trainingData);
	}
	

	

	
	/**
	 * Computes R2 and correlation by comparing known target values with those predicted
	 * Returns a double[] { R2, rho }
	 * @return
	 */
	public double[] getR2AndCorrelation() {
		
		
		double R2_train = 0;
		double rho_train = 0;
		double R2_test = 0;
		double rho_test = 0;
		
		
		for (int t = 0; t < 2; t ++) {
		
			Instances data = t == 0 ? this.trainingData : this.testData;
			if (data == null || data.size() == 0 || !tree.splitData(data)) continue;
			
			double[] trueY = new double[data.size()];
			double[] predY = new double[data.size()];
			
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
			
			if (t == 0) {
				R2_train = R2;
				rho_train = rho;
			}else {
				R2_test = R2;
				rho_test = rho;
			}
			
			
		
		}
		
		return new double[] { R2_train, rho_train, R2_test, rho_test };
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
