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
	

	public enum ResponseMode {
		linear, logistic, test, log, dirichlet
	}
	
	
	final public Input<WekaData> dataInput = new Input<>("data", "The data", Validate.REQUIRED);
	
	final public Input<Integer> maxLeafCountInput = new Input<>("maxLeafCount", "Maximum tree leafset size (constant)", 100);
	final public Input<Integer> minInstancesPerLeafInput = new Input<>("minInstances", "Minimum number of instances per leaf (constant)", 30);
	
	final public Input<IntegerParameter> attributePointerInput = new Input<>("pointer", "points to attributes", Validate.REQUIRED);
	final public Input<RealParameter> splitPointInput = new Input<>("split", "split point for numeric attributes", Validate.REQUIRED);
	final public Input<DecisionTreeInterface> treeInput = new Input<>("tree", "The decision tree", Validate.REQUIRED);
	
	final public Input<RealParameter> interceptInput = new Input<>("intercept", "Regression intercepts at the leaves", Validate.REQUIRED);
	final public Input<RealParameter> slopeInput = new Input<>("slope", "Regression slopes at the leaves");
	final public Input<RealParameter> sigmaInput = new Input<>("sigma", "Regression standard deviation for linear, or modality tau for dirichlet (scalar)", Validate.REQUIRED);
	final public Input<RealParameter> multinomialPInput = new Input<>("multinomialProb", "Multinomial probability vector for flexible dirichlet (if not provided then will use standard dirichlet)");
	
	
	
	final public Input<ResponseMode> responseInput = new Input<>("regression", "The model applied to the class", ResponseMode.linear, ResponseMode.values());
	
	
	final public Input<List<Transform>> predInput = new Input<>("pred", 
			"one or more transformed features to be moved. Each feature must be a Feature object\n"
			+ "For scale parameters use LogTransform (where e.g. scale operators were used).\n"
			+ "For location parameter use NoTransform (where e.g. random walk operators were used).\n"
			+ "For parameters that sum to a constant use LogConstrainedSumTransform  (where e.g. delta-exchange operators were used).", new ArrayList<>()); 
	
	final public Input<List<Transform>> targetInput = new Input<>("class", "The target feature and its transformation. "
			+ "Only one value if linear, a vector if dirichlet", new ArrayList<>());
	final public Input<List<Transform>> removeInput = new Input<>("remove", "Attributes to exclude from all analyses", new ArrayList<>());
	
	
	final public Input<RealParameter> shapeInput = new Input<>("shape", "Dirichlet shape on the number of instances at each leaf. Set to 0 for uniform.", Validate.REQUIRED);
	
	
	WekaData wekaData;
	List<String> covariates;
	List<String> predictors;
	List<String> targets;
	DecisionTreeInterface treeI;
	int nPredictors;
	
	// Number of trees (in case of random forest)
	int ntrees;
	boolean isRF;
	
	
	// Response
	ResponseMode responseMode;
	
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
	RealParameter slope, intercept, sigmaOrTau, multinomialP;
	
	// Helper class for splitting
	DecisionSplit[] splits;
	
	
	@Override
    public void initAndValidate() {
		

		this.attributePointer = attributePointerInput.get();
		this.splitPoints = splitPointInput.get();
		this.treeI = treeInput.get();
		this.slope = slopeInput.get();
		this.intercept = interceptInput.get();
		this.sigmaOrTau = sigmaInput.get();
		this.multinomialP = multinomialPInput.get();
		this.maxLeafCount = maxLeafCountInput.get();
		if (this.maxLeafCount <= 0) this.maxLeafCount = 1;
		this.minInstancesPerLeaf = minInstancesPerLeafInput.get();
		this.wekaData = dataInput.get();
		this.responseMode = responseInput.get();
		
		
		// Random forest?
		this.isRF = this.treeI instanceof RandomForest;
		this.ntrees = this.treeI.getForestSize();
		this.trainingData = new Instances[ntrees];
		
		// Lower bound of sigma as 0
		this.sigmaOrTau.setLower(0.0);
		

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
		
		
		
		// Prepare for attributes
		this.prepareTargetAndPredictor(this.trainingData[0]);
		this.setAttributes(this.trainingData[0]);
		
		
		// Set dimension
		this.attributePointer.setDimension((this.maxLeafCount-1)*this.ntrees);
		this.splitPoints.setDimension((this.maxLeafCount-1)*this.ntrees);
		if (this.slope != null) this.slope.setDimension(this.maxLeafCount * this.nPredictors * this.ntrees);
		if (this.multinomialP != null) {
			this.multinomialP.setDimension(this.targets.size());
			for (int i = 0; i < this.multinomialP.getDimension(); i ++) {
				this.multinomialP.setValue(i, 1.0 / this.multinomialP.getDimension());
			}
		}
		this.intercept.setDimension(this.maxLeafCount * this.ntrees);
		
		// Create the split helper class
		this.splits = new DecisionSplit[this.ntrees];
		for (int i = 0; i < this.ntrees; i ++) {
			DecisionTree tree = this.treeI.getTree(i);
			this.splits[i] = new DecisionSplit(this.attributePointer, this.splitPoints, this.covariates, this.targets, this.trainingData[i], tree, treeI.getNAttr(), this.maxLeafCount);
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
		
		
		// Target feature(s)
		this.targets= new ArrayList<>(); 
		for (Transform t : this.targetInput.get()) {
			for (Function f : t.getF()) {
				Feature targetFeature = (Feature)f;
				this.targets.add(targetFeature.getAttributeName());
			}
		}
		if (this.targets.isEmpty()) {
			throw new IllegalArgumentException("Please provide at least 1 target feature");
		}
		if (this.responseMode != ResponseMode.dirichlet && this.targets.size() != 1) {
			throw new IllegalArgumentException("Error: for linear responses, only one target feature is required");
		}
		if (this.responseMode == ResponseMode.dirichlet) {
			if (this.targets.size()  < 2) throw new IllegalArgumentException("Error: for dirichlet responses, more than 1 target feature is required");
			
			
			// Check everything sums to ~1 
			final double threshold = 1e-5;
			for (int i = 0; i < data.size(); i ++) {
				
				Instance inst = data.get(i);
				double sum = 0;
				for (String target : this.targets) {
					double val = inst.value(data.attribute(target));
					if (Double.isNaN(val)) val = 0;
					sum += val;
				}
				
				if (Math.abs(sum - 1) > threshold) {
					throw new IllegalArgumentException("Error: for dirichlet purposes please ensure that all instances target features sum to 1. Instance "
							+ (i+1) + " sums to " + sum);
				}
				
			}
			
		}
		data.setClass(data.attribute(this.targets.get(0)));
		
		
		
		
		// Find predictor features
		this.predictors = new ArrayList<>();
		for (Transform t : this.predInput.get()) {
			for (Function f : t.getF()) {
				Feature predictorFeature = (Feature)f;
				this.predictors.add(predictorFeature.getAttributeName());
			}
		}
		
		
		if (this.responseMode == ResponseMode.dirichlet) {
			
			
			
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
		

		
		this.targets = new ArrayList<>();
		List<Feature> targetFeatures = new ArrayList<>();
		List<Transform> targetTransforms = new ArrayList<>();
		for (Transform t : this.targetInput.get()) {
			
			for (Function f : t.getF()) {
				if (!(f instanceof Feature)) {
					throw new IllegalArgumentException("Error: please ensure that every target is a transformation of " + Feature.class.getCanonicalName());
				}
				
				Feature targetFeature = (Feature)f;
				System.out.println("Adding target " + targetFeature.getAttributeName());
				this.targets.add(targetFeature.getAttributeName());
				targetFeatures.add(targetFeature);
				targetTransforms.add(t);
				
			}
			
			
		}
		
		
		// Remove some attributes
		for (Transform t : this.removeInput.get()) {
			for (Function f : t.getF()) {
				if (!(f instanceof Feature)) {
					throw new IllegalArgumentException("Error: please ensure that every attribute to remove is a transformation of " + Feature.class.getCanonicalName());
				}
				
				Feature toRemove = (Feature)f;
				
				// Skip if it is a target
				boolean isTarget = false;
				for (String target : this.targets) {
					if (toRemove.getAttrName().equals(target)) {
						isTarget = true;
						break;
					}
				}
				if (isTarget) continue;
				
				
				
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
		data.setClass(data.attribute(this.targets.get(0)));
		

		
		// Remove instances with missing class values
		//Log.warning("Num instances before : " + data.size());
		
		// tmp
		/*
		Filter filter = new RemoveWithValues();
		filter.setOptions(new String[] {"-C", "" + (WekaUtils.getIndexOfColumn(data, this.targets.get(0))+1), "-M", "-S", "" + Double.NEGATIVE_INFINITY });
		filter.setInputFormat(data);
		data = Filter.useFilter(data, filter);
		*/
		
		// Transform target
		for (int i = 0; i < targetFeatures.size(); i ++) {
			Feature targetFeature = targetFeatures.get(i);
			Transform ttarget = targetTransforms.get(i);
			targetFeature.transform(ttarget);
		}
		
		//Log.warning("Num instances after : " + data.size());
		
		
		// Predictor features
		this.predictors = new ArrayList<>();
		for (Transform t : this.predInput.get()) {
			
			for (Function f : t.getF()) {
				if (!(f instanceof Feature)) {
					throw new IllegalArgumentException("Error: please ensure that every predictor is a transformation of " + Feature.class.getCanonicalName());
				}
				
				Feature predictorFeature = (Feature)f;
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
		System.out.print("Target feature(s): ");
		for (int i = 0; i < this.targets.size(); i ++) {
			System.out.print(this.targets.get(i));
			if (i < this.targets.size() - 1) System.out.print(", ");
		}
		System.out.println();
		
		
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
		DecisionNode node = new DecisionNode(treeNum, this.ntrees, this.splits[treeNum], this.slope, this.intercept, this.sigmaOrTau, this.multinomialP, this.targets, this.predictors, this.responseMode);
		return node;
	}
	
	
	/**
	 * Set the attributes
	 */
	protected void setAttributes(Instances data) {
		this.covariates = new ArrayList<>();
		for (int i = 0; i < data.numAttributes(); i ++) {
			Attribute attr = data.attribute(i);
			
			// The target is not a covariate
			boolean isTarget = false;
			for (String target : this.targets) {
				if (target.equals(attr.name())) {
					isTarget = true;
					break;
				}
			}
			if (isTarget) continue;
			
			
			// Neither are the leaf predictors
			boolean isPred = false;
			for (String pred : this.predictors) {
				if (pred.equals(attr.name())) {
					isPred = true;
					break;
				}
			}
			//if (isPred) continue;
			
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
		arguments.add(sigmaOrTau.getID());
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
	 * Number of target features
	 * @return
	 */
	public int getNClasses() {
		return this.targets.size();
	}
	
	
	
	/**
	 * Computes R2 and correlation by comparing known target values with those predicted
	 * Returns a double[] { R2_t1(train), rho_t1(train), R2_t1(test), rho_t1(test), R2_t2(train), ... } for target features t1, t2, ...
	 * @return
	 */
	public double[] getR2AndCorrelation() {
		
		
		int nTargets = this.targets.size();
		
		
		// One value for each target
		double R2_train[] = new double[nTargets];
		double rho_train[] = new double[nTargets];
		double R2_test[] = new double[nTargets];
		double rho_test[] = new double[nTargets];
		
		PearsonsCorrelation pc = new PearsonsCorrelation();
		
		int ntrains = 0;
		int ntests = 0;
		for (int t = 0; t < 2; t ++) {
		
			
			//System.out.println(trueY.length + " total instances");
			
			// Take mean prediction across all trees in the forest. Each tree should have different data
			if (t == 1 && this.testData == null) continue;
			int ninstances = t == 0 ? this.trainingData[0].size() : this.testData.size();
			
			
			// True values
			double[][] trueY = new double[ninstances][];
			double[][] predY = new double[ninstances][];
			
			
			for (DecisionTree tree : this.treeI.getTrees()) {
				
				
				Instances data = t == 0 ? this.trainingData[tree.getTreeNum()] : this.testData;
				if (data == null || data.size() == 0 || !tree.splitData(data)) continue;
				
			
				
				// Calculate regression R2 at the leaves
				int instNum = 0;
				int nleaves = tree.getLeafCount();
				for (int nodeNum = 0; nodeNum < nleaves; nodeNum ++) {
					DecisionNode leaf = tree.getNode(nodeNum);
					double[][] trueYLeaf = leaf.getTargetVals();
					double[][] predYLeaf = leaf.getPredictionVals();
					
					//System.out.println(trueYLeaf.length + "/" + predYLeaf.length);
					
					
					// Put leaf-wise values in main list
					for (int i = 0; i < trueYLeaf.length; i ++) {
						trueY[instNum] = trueYLeaf[i];
						predY[instNum] = predYLeaf[i];
						instNum++;
					}
					
				}
				
				
				if (t == 0) ntrains++;
				if (t == 1) ntests++;
				
				// One R2 and one rho per class
				double[] trueYTarget = new double[ninstances];
				double[] predYTarget = new double[ninstances];
				for (int targetNum = 0; targetNum < nTargets; targetNum ++) {
					
					// Reorganise array
					for (int i = 0; i < ninstances; i ++) {
						trueYTarget[i] = trueY[i][targetNum];
						predYTarget[i] = predY[i][targetNum];
					}
					
					// Total sum of squares
					double TSS = getTSS(trueYTarget);
					
					// Residual sum of squares
					double RSS = getRSS(trueYTarget, predYTarget);
					
					// R squared
					double R2 =  1 - RSS/TSS;
					
				
					// Correlation
					double rho = pc.correlation(trueYTarget, predYTarget);
					
					if (t == 0) {
						R2_train[targetNum] += R2;
						rho_train[targetNum] += rho;
					}else {
						R2_test[targetNum] += R2;
						rho_test[targetNum] += rho;
					}
					
				}
				
				
					
			}
		
		}
		
		if (ntrains == 0) ntrains = 1;
		if (ntests == 0) ntests = 1;
		
		// Return a vector of length 4 * ntargets
		double[] result = new double[4 * nTargets];
		int pos = 0;
		for (int targetNum = 0; targetNum < nTargets; targetNum ++) {
			result[pos++] = R2_train[targetNum] / ntrains;
			result[pos++] = rho_train[targetNum] / ntrains;
			result[pos++] = R2_test[targetNum] / ntests;;
			result[pos++] = rho_test[targetNum] / ntests;;
		}
		
		
		return result; 
		
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
