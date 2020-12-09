package poetry.decisiontree;


import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

import beast.core.Description;
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
import weka.core.Instance;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.instance.RemoveWithValues;


@Description("Central decision tree / random forest distribution")
public class DecisionTreeDistribution extends Distribution {
	

	
	final public Input<WekaData> dataInput = new Input<>("data", "The data", Validate.REQUIRED);
	
	final public Input<Integer> maxLeafCountInput = new Input<>("maxLeafCount", "Maximum tree leafset size (constant)", 100);
	final public Input<Integer> minInstancesPerLeafInput = new Input<>("minInstances", "Minimum number of instances per leaf (constant)", 30);
	
	final public Input<IntegerParameter> attributePointerInput = new Input<>("pointer", "points to attributes", Validate.REQUIRED);
	final public Input<RealParameter> splitPointInput = new Input<>("split", "split point for numeric attributes", Validate.REQUIRED);
	final public Input<DecisionTreeInterface> treeInput = new Input<>("tree", "The decision tree", Validate.REQUIRED);
	
	final public Input<RealParameter> interceptInput = new Input<>("intercept", "Regression intercepts at the leaves", Validate.REQUIRED);
	final public Input<RealParameter> slopeInput = new Input<>("slope", "Regression slopes at the leaves");
	final public Input<RealParameter> sigmaInput = new Input<>("sigma", "Regression standard deviation (scalar)", Validate.REQUIRED);
	
	
	
	
	
	final public Input<List<Transform>> predInput = new Input<>("pred", 
			"one or more transformed features to be moved. Each feature must be a Feature object\n"
			+ "For scale parameters use LogTransform (where e.g. scale operators were used).\n"
			+ "For location parameter use NoTransform (where e.g. random walk operators were used).\n"
			+ "For parameters that sum to a constant use LogConstrainedSumTransform  (where e.g. delta-exchange operators were used).", new ArrayList<>()); 
	
	final public Input<Transform> targetInput = new Input<>("class", "The target feature and its transformation", Validate.REQUIRED);
	final public Input<List<Transform>> removeInput = new Input<>("remove", "Attributes to exclude from all analyses", new ArrayList<>());
	
	
	final public Input<RealParameter> shapeInput = new Input<>("shape", "Dirichlet shape on the number of instances at each leaf. Set to 0 for uniform.", Validate.REQUIRED);
	
	
	WekaData wekaData;
	List<String> covariates;
	List<String> predictors;
	String target;
	DecisionTreeInterface treeI;
	int nPredictors;
	
	// Number of trees (in case of random forest)
	int ntrees;
	boolean isRF;
	
	
	// Which attribute does each split point to
	IntegerParameter attributePointer;
	
	// For numeric attributes only: where is the split (between 0 and 1)
	RealParameter splitPoints;
	
	int maxLeafCount;
	int minInstancesPerLeaf;
	
	// The data
	Instances[] trainingData;
	Instances testData;
	
	// Slope and intercept
	RealParameter slope, intercept, sigma;
	
	// Helper class for splitting
	DecisionSplit[] splits;
	
	
	@Override
    public void initAndValidate() {
		

		this.attributePointer = attributePointerInput.get();
		this.splitPoints = splitPointInput.get();
		this.treeI = treeInput.get();
		this.slope = slopeInput.get();
		this.intercept = interceptInput.get();
		this.sigma = sigmaInput.get();
		this.maxLeafCount = maxLeafCountInput.get();
		if (this.maxLeafCount <= 0) this.maxLeafCount = 1;
		this.minInstancesPerLeaf = minInstancesPerLeafInput.get();
		this.wekaData = dataInput.get();
		
		
		// Random forest?
		this.isRF = this.treeI instanceof RandomForest;
		this.ntrees = this.treeI.getForestSize();
		this.trainingData = new Instances[ntrees];
		
		// Lower bound of sigma as 0
		this.sigma.setLower(0.0);
		

		// Set min and max values of the numeric split to (0,1)
		splitPoints.setBounds(0.0, 1.0);
		
		// Load class/predictors and transform them
		if (this.isRF) {
			
			// Bootstrapping
			for (int i = 0; i < this.ntrees; i ++) {
				this.trainingData[i] = wekaData.bootstrapTrainingData();
			}
			
			
			
		}else {
			this.trainingData[0] = wekaData.getTrainingData();
		}
		this.testData = wekaData.getTestData();
		try {
			this.trainingData = this.transform(this.trainingData);
			this.testData = this.transform(this.testData);
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Error transforming data");
		}
		
		
		
		// Slope is required iff there are predictors
		this.nPredictors = this.predictors.size();
		if (this.nPredictors > 0) {
			if (slopeInput.get() == null) {
				throw new IllegalArgumentException("Error: please provide slope because there are predictors");
			}
		}else {
			if (slopeInput.get() != null) {
				Log.warning("Warning no need to provide slope because there are no predictors");
				slopeInput.set(null);
			}
		}
		
		
		// Set dimension
		this.attributePointer.setDimension((this.maxLeafCount-1)*this.ntrees);
		this.splitPoints.setDimension((this.maxLeafCount-1)*this.ntrees);
		if (this.slope != null) this.slope.setDimension(this.maxLeafCount * this.nPredictors * this.ntrees);
		this.intercept.setDimension(this.maxLeafCount * this.ntrees);
		
		
		// Prepare for attributes
		this.prepareTargetAndPredictor(this.trainingData[0]);
		this.setAttributes(this.trainingData[0]);
		
		// Create the split helper class
		this.splits = new DecisionSplit[this.ntrees];
		for (int i = 0; i < this.ntrees; i ++) {
			DecisionTree tree = this.treeI.getTree(i);
			this.splits[i] = new DecisionSplit(this.attributePointer, this.splitPoints, this.covariates, this.target, this.trainingData[i], tree, treeI.getNAttr(), this.maxLeafCount);
		}
		
		
		// Initialise the tree
		this.initTrees();
		
		
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
		this.target = targetFeature.getAttributeName();
		data.setClass(data.attribute(this.target));
		
		
		// Find predictor features
		this.predictors = new ArrayList<>();
		for (Transform t : this.predInput.get()) {
			for (Function f : t.getF()) {
				Feature predictorFeature = (Feature)f;
				//predictorFeature.setDataset(data);
				this.predictors.add(predictorFeature.getAttributeName());
			}
			
		}
		
	}



	
	
	/**
	 * Load and transform class and predictor(s)
	 * @throws Exception 
	 */
	private Instances[] transform(Instances[] data) throws Exception {
		for (int i = 0; i < data.length; i ++) {
			data[i] = this.transform(data[i]);
		}
		return data;
	}

	/**
	 * Load and transform class and predictor(s)
	 * @throws Exception 
	 */
	private Instances transform(Instances data) throws Exception {
		
		
		Transform ttarget = this.targetInput.get();
		if (ttarget.getF().size() != 1) {
			throw new IllegalArgumentException("Error: please ensure that 'class' has only one feature");
		}
		Function func = ttarget.getF().get(0);
		if (!(func instanceof Feature)) {
			throw new IllegalArgumentException("Error: please ensure that 'class' is a transformation of " + Feature.class.getCanonicalName());
		}
		Feature targetFeature = (Feature)func;
		
		
		// Remove some attributes
		for (Transform t : this.removeInput.get()) {
			for (Function f : t.getF()) {
				if (!(f instanceof Feature)) {
					throw new IllegalArgumentException("Error: please ensure that every attribute to remove is a transformation of " + Feature.class.getCanonicalName());
				}
				
				Feature toRemove = (Feature)f;
				if (toRemove.getAttrName().equals(targetFeature.getAttrName())) continue;
				
				int index = WekaUtils.getIndexOfColumn(data, toRemove.getAttrName());
				if (index == -1) {
					Log.warning("Warning: cannot remove " + toRemove.getAttrName() + " because it cannot be found in the dataset");
				}
				else {
					Log.warning("Removing attribute " + toRemove.getAttrName());
					data.deleteAttributeAt(index);
				}
			}
			
		}
		
		

		// Target feature
		this.target = targetFeature.getAttributeName();
		data.setClass(data.attribute(this.target));
		

		
		// Remove instances with missing class values
		//Log.warning("Num instances before : " + data.size());
		Filter filter = new RemoveWithValues();
		filter.setOptions(new String[] {"-C", "" + (WekaUtils.getIndexOfColumn(data, this.target)+1), "-M", "-S", "" + Double.NEGATIVE_INFINITY });
		filter.setInputFormat(data);
		data = Filter.useFilter(data, filter);
		
		// Transform
		targetFeature.transform(ttarget);
		//Log.warning("Num instances after : " + data.size());
		
		
		// Predictor features
		this.predictors = new ArrayList<>();
		for (Transform t : this.predInput.get()) {
			
			for (Function f : t.getF()) {
				if (!(f instanceof Feature)) {
					throw new IllegalArgumentException("Error: please ensure that every predictor is a transformation of " + Feature.class.getCanonicalName());
				}
				
				Feature predictorFeature = (Feature)f;
				//predictorFeature.setDataset(data);
				System.out.println("Adding predictor " + predictorFeature.getAttributeName());
				this.predictors.add(predictorFeature.getAttributeName());
				predictorFeature.transform(t);
				
			}
			
			
		}
		
		return data;
		
		
	}



	/**
	 * Print the attributes / class / predictor
	 */
	public void printSummary() {
		System.out.println("-------------------------------------");
		System.out.println(this.isRF ? "RandomForest" : "DecisionTree");
		System.out.println("Data organised into an " + this.trainingData[0].size() + "/" + this.testData.size() + " training/test split");
		System.out.println("Maximum leaf count: " + this.maxLeafCount);
		System.out.println("Minimum number of instances per leaf: " + this.minInstancesPerLeaf);
		System.out.println("Target feature: " + this.target);
		
		
		// Predictors
		if (!this.predictors.isEmpty()) {
			System.out.print("Predictors at leaves: "); 
			for (int i = 0; i < this.predictors.size(); i ++) {
				System.out.print(this.predictors.get(i));
				if (i < this.predictors.size() - 1) System.out.print(", ");
			}
			System.out.println();
		}
		
		
		// Covariates
		System.out.print("Covariates at internal nodes: ");
		for (int i = 0; i < this.covariates.size(); i ++) {
			System.out.print(this.covariates.get(i));
			if (i < this.covariates.size() - 1) System.out.print(", ");
		}
		System.out.println();
		if (this.isRF) System.out.println("This is a RandomForest therefore " + ((RandomForest)treeI).getNAttr() + " of the above are sampled per tree");
		System.out.println("-------------------------------------");
	}
	


	
	/**
	 * Initialise the tree
	 */
	protected void initTrees() {

		
		for (int treeNum = 0; treeNum < this.ntrees; treeNum++) {
			
			// Initialise an empty tree
			DecisionNode root = this.newNode(treeNum);
			
			

			// Decision tree? This is the tree
			if (this.treeI instanceof DecisionTree) {
				DecisionTree tree = ((DecisionTree)this.treeI);
				tree.setRoot(root);
				tree.initTree(this.trainingData[treeNum]);
			}
			
			
			// Random forest? This is one of the trees
			if (this.treeI instanceof RandomForest) {
				RandomForest forest = ((RandomForest) this.treeI);
				forest.setRoot(treeNum, root);
				forest.initTree(treeNum, this.trainingData[treeNum]);
			}
			
		}
	
		
	}
	
	
	/**
	 * Create a new node with access to all necessary objects
	 * @param - the Tree which this node will be added to
	 * @return
	 */
	public DecisionNode newNode(int treeNum) {
		DecisionNode node = new DecisionNode(treeNum, this.ntrees, this.splits[treeNum], this.slope, this.intercept, this.sigma, this.target, this.predictors);
		return node;
	}
	
	
	/**
	 * Set the attributes
	 */
	protected void setAttributes(Instances data) {
		this.covariates = new ArrayList<>();
		for (int i = 0; i < data.numAttributes(); i ++) {
			Attribute attr = data.attribute(i);
			if (attr.name().equals(this.target)) continue;
			
			boolean isPred = false;
			for (String pred : this.predictors) {
				if (pred.equals(attr.name())) {
					isPred = true;
					break;
				}
			}
			if (isPred) continue;
			
			// Check that every attribute is either numeric or nominal (no strings etc.)
			if (!attr.isNominal() && !attr.isNumeric()) {
				throw new IllegalArgumentException("Error: every attribute must be either numeric or nominal - " + attr.name());
			}
			
			this.covariates.add(data.attribute(i).name());
		}
		
		
		
		
		// Set min and max values of the pointer to the number of attributes (excluding target and predictor)
		int nattr = this.covariates.size();
		this.treeI.setNumAttributes(nattr);
		
		// Check if a subsample of attributes are being used
		nattr = this.treeI.getNAttr();
		
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
		String attrName = this.covariates.get(index);
		Attribute attr = this.trainingData[0].attribute(attrName);
		return attr.isNumeric();
	}
	


	
	 @Override
	 public double calculateLogP() {
		 
		 logP = 0;
		 
		
		 
		 for (DecisionTree tree : this.treeI.getTrees()) {
		 
			 //Log.warning("tree size " + tree.getLeafCount());
			 
			 // Tree size exceeded?
			 if (tree.getLeafCount() > this.maxLeafCount) {
				 //Log.warning("too big");
				 logP = Double.NEGATIVE_INFINITY;
				 return logP;
			 }
			 
			 // Ensure that the split is valid
			 Instances data = this.trainingData[tree.getTreeNum()];
			 boolean valid = tree.splitData(data);
			 if (!valid) {
				 Log.warning("invalid");
				 logP = Double.NEGATIVE_INFINITY;
				 return logP;
			 }
			 
			 
			 // Dirichlet distribution on instances per leaf
			 double alpha = shapeInput.get().getArrayValue();
			 double sumAlpha = alpha * tree.getLeafCount();
			 int ninstances = data.size() + tree.getLeafCount(); // Pseudocount 
			 for (int i = 0; i < tree.getLeafCount(); i++) {
				 int n = tree.getNode(i).getNumInstances() + 1;
				 double pinstances = 1.0 * n / ninstances;
				 if (n < this.minInstancesPerLeaf || n == 0) {
					 logP = Double.NEGATIVE_INFINITY;
					 return logP;
				 }
				 logP += (alpha - 1) * Math.log(pinstances);
				 logP -= org.apache.commons.math.special.Gamma.logGamma(alpha);
			 }
			 logP += org.apache.commons.math.special.Gamma.logGamma(sumAlpha);
			 
			 
			 // Calculate regression likelihood at the leaves
			 for (int nodeNum = 0; nodeNum < tree.getLeafCount(); nodeNum ++) {
				 DecisionNode leaf = tree.getNode(nodeNum);
				 double ll = leaf.getLogLikelihood();
				 logP += ll;
			 }
			 
		 
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
		arguments.add(treeI.getID());
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
		boolean valid = true;
		for (DecisionTree tree : this.treeI.getTrees()) {
			valid = valid && tree.splitData(this.trainingData[tree.getTreeNum()]);
		}
		return valid;
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
		
		int ntrains = 0;
		int ntests = 0;
		for (int t = 0; t < 2; t ++) {
		
			
			//System.out.println(trueY.length + " total instances");
			
			// Take mean prediction across all trees in the forest. Each tree should have different data
			
			for (DecisionTree tree : this.treeI.getTrees()) {
				
				
				Instances data = t == 0 ? this.trainingData[tree.getTreeNum()] : this.testData;
				if (data == null || data.size() == 0 || !tree.splitData(data)) continue;
				
				
				double[] trueY = new double[data.size()];
				double[] predY = new double[data.size()];
			
				
				// Calculate regression R2 at the leaves
				int instNum = 0;
				int nleaves = tree.getLeafCount();
				for (int nodeNum = 0; nodeNum < nleaves; nodeNum ++) {
					DecisionNode leaf = tree.getNode(nodeNum);
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
					R2_train += R2;
					rho_train += rho;
					ntrains++;
				}else {
					R2_test += R2;
					rho_test += rho;
					ntests++;
				}
				
				
			}
		
		}
		
		
		// Take means
		if (ntrains == 0) ntrains = 1;
		if (ntests == 0) ntests = 1;
		return new double[] { R2_train/ntrains, rho_train/ntrains, R2_test/ntests, rho_test/ntests };
	}



	/**
	 * Calculate the total sum of squares
	 * @param y
	 * @return
	 */
	public static double getTSS(double[] y) {
		
		// Find the mean
		double mean = 0;
		int len = 0;
		for (int i = 0; i < y.length; i ++) {
			if (Double.isNaN(y[i])) continue;
			mean += y[i];
			len++;
		}
		mean /= len;
		
		// Total sum of squares
		double SS = 0;
		for (int i = 0; i < y.length; i ++) {
			if (Double.isNaN(y[i])) continue;
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
			if (Double.isNaN(y[i])) continue;
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




	public int getForestSize() {
		return this.ntrees;
	}




	public int getNumPredictors() {
		return this.nPredictors;
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
