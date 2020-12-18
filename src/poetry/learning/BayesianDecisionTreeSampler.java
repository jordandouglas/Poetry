package poetry.learning;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.MaxIter;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.linear.LinearConstraint;
import org.apache.commons.math3.optim.linear.LinearConstraintSet;
import org.apache.commons.math3.optim.linear.Relationship;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.NelderMeadSimplex;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer;

import beast.core.Input;
import beast.core.util.Log;
import beast.evolution.alignment.Alignment;
import beast.evolution.tree.Tree;
import beast.util.Randomizer;
import poetry.PoetryAnalyser;
import poetry.decisiontree.DecisionNode;
import poetry.decisiontree.DecisionTree;
import poetry.decisiontree.DecisionTreeDistribution.ResponseMode;
import poetry.sampler.POEM;
import poetry.util.BEAST2Weka;
import weka.classifiers.functions.GaussianProcesses;
import weka.classifiers.functions.supportVector.RBFKernel;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffSaver;

public class BayesianDecisionTreeSampler extends WeightSampler {

	
	final public Input<Tree> treeInput = new Input<>("tree", "The phylogenetic tree (used for machine learning)", Input.Validate.REQUIRED);
	final public Input<Alignment> dataInput = new Input<>("data", "The alignment (used for machine learning)", Input.Validate.REQUIRED);
	
	final public Input<ResponseMode> regressionInput = new Input<>("regression", "Regression model at the leaves", ResponseMode.test, ResponseMode.values());
	
	final public Input<List<ModelValue>> modelValuesInput = new Input<>("model", "List of models and their values -- for applying the decsion tree from the model", new ArrayList<>());
	final public Input<String> treesInput = new Input<>("trees", "A file containing decision trees", Input.Validate.REQUIRED);
	
	
	final private static String distClassName = "dist";
	
	
	@Override
	public void initAndValidate() {
		
		
		
		
	}

	@Override
	public void sampleWeights() throws Exception {
		
		
		// Model values
		List<ModelValue> modelValues = modelValuesInput.get();
		
		// Get the Weka Instance of this session
		Instances instances = BEAST2Weka.getInstance(dataInput.get(), treeInput.get(), this, modelValues);
				
		int dim = poems.size();
		double[] weights = new double[dim];
		
		
		/*
		
		DecisionTree[] decisionTrees = new DecisionTree[dim];
		// Validate: check all POEMS have tree files
		for (int j = 0; j < dim; j++) {
			
			POEM poem = poems.get(j);
			File decisionTreeFile = poem.getDecisionTreeFile();
			if (decisionTreeFile == null) {
				throw new Exception("Please specify the decision tree file ('trees' input) for " + poem.getID());
			}
			if (!decisionTreeFile.canRead()) {
				throw new Exception("Error locating decision tree file " + decisionTreeFile.getAbsolutePath());
			}
			
			// Read the tree file
			List<DecisionTree> trees = parseDecisionTrees(decisionTreeFile);
			
			// Take the last tree
			DecisionTree tree = trees.get(trees.size()-1);
			tree.setRegressionMode(regressionInput.get());
			decisionTrees[j] = tree;
			
			System.out.println(poem.getID() + ": " + tree.toString());
			
			
		}
		weights = this.getWeights(decisionTrees, instances);
		*/
		
		
		// Load decision tree
		List<DecisionTree> trees = parseDecisionTrees(new File(treesInput.get()), 10);
		DecisionTree decisionTree = trees.get(trees.size()-1);
		decisionTree.setRegressionMode(regressionInput.get());
		System.out.println("tree : " + decisionTree.toString());
		
		
		weights = this.getGPWeights();
		
		//weights = this.getWeights(trees, instances);
		
		//System.exit(1);
		
		
		this.setWeights(weights);
		
	}
	
	

	
	protected double[] getWeights(List<DecisionTree> trees, Instances inst) throws Exception {
		
		double[] weights = new double[this.poems.size()];
		
		int ntaxa = (int)(inst.instance(0).value(inst.attribute("ntaxa")));
		
		SquaredAlphaDistance[] fns = new SquaredAlphaDistance[trees.size()];
		
		for (int treeNum = 0; treeNum < trees.size(); treeNum ++) {
				
			DecisionTree tree = trees.get(treeNum);
			DecisionNode leaf = tree.getLeaf(inst);
			
			// Optimise
			double tau = Double.parseDouble(leaf.getToken("sigma"));
			double target = tau / this.getNumPoems();
			double[] slopes = new double[this.poems.size()];
			double[] intercepts = new double[this.poems.size()];
			double[] dims  = new double[this.poems.size()];
			for (int i = 0; i < this.getNumPoems(); i ++) {
				POEM poem = this.poems.get(i);
				
				
				String slopeStr = leaf.getToken("slope_" + poem.getESSColname() + ".p");
				String interceptStr = leaf.getToken("intercept_" + poem.getESSColname() + ".p");
				double slope = Double.parseDouble(slopeStr);
				double intercept = Double.parseDouble(interceptStr);
				double dim = BEAST2Weka.getDimension(poem.getID(), poem.getDim(), ntaxa); 
				
				slopes[i] = slope;
				intercepts[i] = intercept;
				dims[i] = dim;
				
				
				if (treeNum == trees.size()-1) {
					System.out.println(poem.getID() + " slope " + slope + " intercept " + intercept + " target " + target);
				}
				
				
				/*
				// Find weight such that E = 1/tau
				double weight;
				if (intercept < target) {
					double newTarget = 1.5;
					weight = slope / (newTarget - 1);
					Log.warning("Cannot reach emax");
				}else {
					weight = slope / (intercept/target - 1);
				}
				
				
				
				
				
				// Multiply by dimension
				
				weights[i] = weight*dim;
				*/
			}
			
			
			SquaredAlphaDistance fn = new SquaredAlphaDistance(slopes, intercepts, dims, tau);
			fns[treeNum] = fn;
			
			if (treeNum == trees.size()-1) {
				
				double[] opt = optimiseSimplex(fn, fn.getDimension());
				weights = SquaredAlphaDistance.repairSticks(opt);
				System.out.print("max: ");
				for (double o : weights) System.out.print(o + ", ");
				System.out.println(" eval: " + fn.value(opt));
				
				System.out.print("alpha: ");
				for (double o : fn.getAlpha(opt)) System.out.print(o + ", ");
				System.out.println();
				
			}
			
		}

		
		
		buildEpsilonDistribution(fns, this.poems);
		
		return weights;
	}
	
	

	/**
	 * Select weights using gaussian process
	 * @return
	 * @throws Exception
	 */
	public double[] getGPWeights() throws Exception {
		
		
		if (this.database == null) return null;
		
		
		// Create Instances object
		ArrayList<Attribute> attributes = new ArrayList<>();
		for (int i = 0; i < this.getNumPoems()-1; i ++) {
			Attribute tweight = new Attribute("tweight" + i);
			attributes.add(tweight);
		}
		attributes.add(new Attribute(distClassName));
		int nattr = attributes.size();
		Instances instances = new Instances("dirichlet", attributes,  attributes.size());
		instances.setClass(instances.attribute(distClassName));
			
		// Read in database and add instances
		LinkedHashMap<String, String[]> db = PoetryAnalyser.openDatabase(this.database);
		for (int i = 0; i < db.size(); i ++) {
			
			// One instance per row in database
			Instance instance = new DenseInstance(nattr);
			instance.setDataset(instances);
			
			// One column per weight
			double[] weights = new double[this.poems.size()];
			for (int p = 0; p < this.getNumPoems(); p ++) {
				POEM poem = this.poems.get(p);
				String weightCol = poem.getWeightColname(); // + ".d";
				if (!db.containsKey(weightCol)) {
					throw new Exception("Cannot find column " + weightCol + " in database");
				}
				double weight = Double.parseDouble(db.get(weightCol)[i]);
				weights[p] = weight;
				
			}
			
			
			// Set breaks
			double[] tweights = SquaredAlphaDistance.breakSticks(weights);
			for (int p = 0; p < this.getNumPoems()-1; p ++) {
				instance.setValue(instances.attribute("tweight" + p), tweights[p]);
			}
			
			// Class value (distance) as Pmean
			if (!db.containsKey("Pmean")) {
				throw new Exception("Cannot find column Pmean in database");
			}
			double dist = Double.parseDouble(db.get("Pmean")[i]);
			dist = Math.log(dist);
			instance.setValue(instances.attribute(distClassName), dist);
			instances.add(instance);
			
		}
		
		
		// Save the dataset
		ArffSaver saver = new ArffSaver();
		saver.setInstances(instances);
		saver.setFile(new File("/home/jdou557/Documents/Marsden2019/Months/December2020/BDT/kernel30.arff"));
		saver.writeBatch();
		
		
		
		// Train the kernel
		// No normalisation or standardisation. RBFKernel
		GaussianProcesses kernel = new GaussianProcesses();
		kernel.setOptions(new String[] { "-N", "2", "-K", RBFKernel.class.getCanonicalName() });
		kernel.buildClassifier(instances);
		
		
		// What is the smallest observed mean so far?
		double bestMean = Double.POSITIVE_INFINITY;
		double[] bestTweights = new double[this.poems.size()-1];
		for (int i = 0; i < instances.size(); i ++) {
			double val = instances.get(i).value(instances.attribute(distClassName));
			if (val < bestMean) {
				for (int p = 0; p < this.getNumPoems()-1; p ++) {
					bestTweights[p] = instances.get(i).value(instances.attribute("tweight" + p));
				}
				bestMean = val;
			}
		}
		String msg = "Cumulative optimal value " + bestMean + ", with weights";
		double[] bestWeights = SquaredAlphaDistance.repairSticks(bestTweights);
		for (double w : bestWeights) msg += " " + w;
		Log.warning(msg);
		
		// Optimise the expected improvement function (minimisation)
		Log.warning("Computing maximum expected improvement...");
		ExpectedImprovementFunction fn = new ExpectedImprovementFunction(instances, kernel, bestMean);
		double[] opt = optimiseSimplex(fn, this.poems.size());
		double[] weights = SquaredAlphaDistance.repairSticks(opt);
		System.out.print("EI max: ");
		for (double o : weights) System.out.print(o + ", ");
		System.out.println(" eval: " + fn.value(opt) + "|"  + fn.getExpectedVal(opt) + " | " + Math.exp(fn.getExpectedVal(opt)));
		//fn.getExpectedVal(opt);
		
		//System.exit(1);
		
		return weights;
		
	}
	
	
	/**
	 * Get the expected improvement of the weights (ie. expected value above the bestMean)
	 * @author jdou557
	 *
	 */
	 private static class ExpectedImprovementFunction implements MultivariateFunction {

		 
		 final double epsilon = -0.2;
		 GaussianProcesses gp;
		 NormalDistribution normal;
		 double bestMean;
		 Instances instances;
		 Instance instance;
		 
		 public ExpectedImprovementFunction(Instances instances, GaussianProcesses gp, double bestMean) {
			 this.instance = new DenseInstance(instances.numAttributes());
			 this.instances = instances;
			 this.bestMean = bestMean;
			 instance.setDataset(instances);
			 this.gp = gp;
		 }
		 
		 
		 public double getExpectedVal(double[] tweights) {
			 
			 double expectedImprovement = this.value(tweights);
			 //Log.warning("Expected improvement " + expectedImprovement);
			 //Log.warning("Best mean " + this.bestMean);
			 //Log.warning("Epsilon " + this.epsilon);
			// Log.warning("Expected value " + expectedImprovement + (this.bestMean + this.epsilon));
			 
			 return expectedImprovement + (this.bestMean);/// + this.epsilon);
		 }
		 
		 
		 @Override
		 public double value(double[] tweights) {
			 
			// Set the transformed weight (ie the broken stick)
			for (int j = 0; j < tweights.length; j ++) {
				instance.setValue(instances.attribute("tweight" + j), tweights[j]);
			}
			
			
			// Get mean and standard deviation (in log space)
			double sd, mean;
			try {
				sd = gp.getStandardDeviation(instance);
				mean = gp.classifyInstance(instance);
			} catch (Exception e1) {
				e1.printStackTrace();
				return Double.POSITIVE_INFINITY;
			}
			
			double delta = mean - (bestMean + epsilon);
			//delta = -delta; // Minimise
			normal = new NormalDistribution(mean, sd);
			
			 
			// Expected value
			double e = delta * normal.cumulativeProbability(delta / sd) + sd*normal.density(delta / sd);
			return e;
			
		 }
		 
		 
	 }
	
	

	
	
	public static void buildEpsilonDistribution(SquaredAlphaDistance[] fns, List<POEM> poems) throws Exception {
		

		final int nsamples = 1000;
		
		
		Log.warning("Building kernel density...");
		

		
		// Create Instances object
		ArrayList<Attribute> attributes = new ArrayList<>();
		for (int i = 0; i < fns[0].getDimension()-1; i ++) {
			Attribute tweight = new Attribute("tweight" + i);
			attributes.add(tweight);
		}
		attributes.add(new Attribute(distClassName));
		int nattr = attributes.size();
		Instances instances = new Instances("dirichlet", attributes,  attributes.size());
		instances.setClass(instances.attribute(distClassName));
		
		
		
		
		// Sample weights using a dirichlet on the poem alphas
		boolean isNaN = false;
		for (int i = 0; i < 2*nsamples; i ++) {
			
			SquaredAlphaDistance fn = fns[Randomizer.nextInt(fns.length)];
			
			double[] tweights;
			if (i >= nsamples) {
				
				// Include the optimal weight with some jitter
				tweights = optimiseSimplex(fn, fn.getDimension());
				
				
				// Add some random jitter
				for (int j = 0; j < fn.getDimension()-1; j ++) {
					tweights[j] = tweights[j] + Randomizer.nextGaussian()*Randomizer.nextExponential(5);
					//if (true || i != 2*nsamples -1)tweights[j] += Randomizer.nextGaussian()*Randomizer.nextExponential(5);
				}
				
				
				
			}else {
				
				double[] weights = DimensionalSampler.sampleWeights(poems, Randomizer.nextExponential(0.1));
				double weightSum = 0;
				for (int j = 0; j < fn.getDimension(); j ++) {
					weights[j] += 0.00001; // Prevent numerical instabilities from tiny weights
					weightSum += weights[j];
				}
				for (int j = 0; j < fn.getDimension(); j ++) weights[j] /= weightSum;
				//double[] weights = DirichletSampler.sampleWeights(poems);
				tweights = SquaredAlphaDistance.breakSticks(weights);
				
				
				// Check for numerical instabilities
				for (int j = 0; j < fn.getDimension()-1; j ++) {
					//System.out.println("weight " + weights[j] + " tweight " + tweights[j]);
					if (Double.isNaN(tweights[j])) isNaN = true;
				}
				//System.out.println("weight final " + weights[fn.getDimension()-1]);
			}
			
			Instance instance = new DenseInstance(nattr);
			instance.setDataset(instances);
			
			// Set the transformed weight (ie the broken stick)
			for (int j = 0; j < fn.getDimension()-1; j ++) {
				instance.setValue(instances.attribute("tweight" + j), tweights[j]);
			}
			//System.out.println("weight final " + weights[fn.getDimension()-1]);
			
			// Compute the distance in logspace
			double dist = fn.value(tweights);
			if (isNaN || Double.isNaN(dist)) continue;
			dist = Math.log(dist);
			instance.setValue(instances.attribute(distClassName), dist);
			instances.add(instance);
			
		}
		
		
		Log.warning(instances.size() + " kernel samples");
		SquaredAlphaDistance fn = fns[fns.length-1];
		
		
		// Save the dataset
		ArffSaver saver = new ArffSaver();
		saver.setInstances(instances);
		saver.setFile(new File("/home/jdou557/Documents/Marsden2019/Months/December2020/BDT/kernel.arff"));
		saver.writeBatch();
		
		
		
		// Train the kernel
		// No normalisation or standardisation. RBFKernel
		GaussianProcesses kernel = new GaussianProcesses();
		kernel.setOptions(new String[] { "-N", "2", "-K", RBFKernel.class.getCanonicalName() });
		kernel.buildClassifier(instances);
		
		
		
		Instance instance = new DenseInstance(nattr);
		instance.setDataset(instances);
		double[] tweights = optimiseSimplex(fn, fn.getDimension()); // fn.breakSticks(DirichletSampler.sampleWeights(poems));
		
		// Set the transformed weight (ie the broken stick)
		for (int j = 0; j < fn.getDimension()-1; j ++) {
			System.out.println("tweight " + tweights[j]);
			instance.setValue(instances.attribute("tweight" + j), tweights[j]);
		}
		
		
		double sd = kernel.getStandardDeviation(instance);
		double mean = kernel.classifyInstance(instance);
		System.out.println("opt mean: " + mean + " sd: " + sd + " real space val " + Math.exp(mean) + " true " + fn.value(tweights));
		
		
		
		tweights = SquaredAlphaDistance.breakSticks(DimensionalSampler.sampleWeights(poems, 20));
		
		// Set the transformed weight (ie the broken stick)
		for (int j = 0; j < fn.getDimension()-1; j ++) {
			instance.setValue(instances.attribute("tweight" + j), -1.0);
		}
		
		
		sd = kernel.getStandardDeviation(instance);
		mean = kernel.classifyInstance(instance);
		System.out.println("rand mean: " + mean + " sd: " + sd + " real space val " + Math.exp(mean) + " true " + fn.value(tweights));
		
	}
	

	
	/**
	 * Parse decision trees from a newick file
	 * @param newickFile
	 * @return
	 * @throws IOException
	 */
	public static List<DecisionTree> parseDecisionTrees(File newickFile, int burnin) throws IOException {
		BufferedReader fin = new BufferedReader(new FileReader(newickFile));
		List<DecisionTree> trees = new ArrayList<>();
		
		// Parse trees from newick strings
		String str = null;
        while (fin.ready()) {
            str = fin.readLine();
            if (!str.matches("\\s*")) {
	            DecisionTree tree = new DecisionTree();
	            tree.fromNewick(str);
	            trees.add(tree);
            }
            
        }
		fin.close();  
		
		
		// Burnin
		int desiredLen = (int)Math.ceil(1.0 * trees.size() * burnin / 100);
		while (trees.size() > desiredLen) trees.remove(0);
		
		return trees;
	}
	
	
	
	/**
	 * Return weights which minimise the function
	 * @param fn
	 * @return
	 */
	public static double[] optimiseSimplex(MultivariateFunction fn, int ndim) {
		
		MaxIter maxIter = new MaxIter(1000);
		MaxEval maxEval = new MaxEval(10000);
		ObjectiveFunction objective = new ObjectiveFunction(fn);
		//SearchInterval interval = new SearchInterval(0.0, 1.0, 0.5);
		double[] initArr = new double[ndim-1];
		InitialGuess init = new InitialGuess(initArr);
		
		//SimpleBounds bounds = new SimpleBounds(new double[] { 0,0,0,0,0,0 }, new double[] { 1,1,1,1,1 } );
		NelderMeadSimplex simplex =  new org.apache.commons.math3.optim.nonlinear.scalar.noderiv.NelderMeadSimplex(ndim-1, 1.0);
		
		SimplexOptimizer opt = new SimplexOptimizer(1e-10, 1e-30);
		
		// Unit sum
		Collection<LinearConstraint> constraints = new ArrayList<>();
		constraints.add(new LinearConstraint(new double[] { 1,1,1,1,1,1 }, Relationship.LEQ, 1.0));
		LinearConstraintSet constraintSet = new LinearConstraintSet(constraints);
		
		
		try {
			PointValuePair max = opt.optimize(maxIter, maxEval, constraintSet, objective, init, simplex,  GoalType.MINIMIZE);		

			return max.getPoint();
			
		}catch(Exception e) {
			e.printStackTrace();
			return initArr;
		}
		
	}
	


	 
	 /**
	  * A function which can be optimised using a MultivariateOptimizer
	  * @author jdou557
	  *
	  */
	 private static class SquaredAlphaDistance implements MultivariateFunction {

		 
		 int ndim;
		 double tau;
		 double[] ehalf, emax, dims;
		 double[] alpha;
		 double alphaSum, squaredDistance;
		 double target;
		 
		 public SquaredAlphaDistance(double[] ehalf, double[] emax, double[] dims, double tau) {
			 this.ehalf = ehalf;
			 this.emax = emax;
			 this.dims = dims;
			 this.tau = tau;
			 this.ndim = emax.length;
			 this.alpha = new double[this.ndim];
			 this.target = this.tau / this.ndim;
		 }
		 
		 public static double logit(double x) {
			 return Math.log(x / (1-x));
		 }
		 
		 
		 public static double ilogit(double x) {
			 return 1.0 / (1 + Math.exp(-x));
		 }
		 
		 
	
		 // https://mc-stan.org/docs/2_18/reference-manual/simplex-transform-section.html
		 public static double[] breakSticks(double[] x) {
			 
			 int K = x.length;
			 double[] y = new double[K-1];
			 double sum = 0;
			 for (int k = 0; k < K-1; k ++) {
				 double zk = x[k] / (1 - sum);
				 double lzk = logit(zk);
				 sum += x[k];
				 y[k] = lzk - Math.log(1.0/(K-k));
			 }
			 return y;
			 
		 }
		 
		 
		 public static double[] repairSticks(double[] y) {
			 
			 int K = y.length+1;
			 double[] x = new double[K];
			 double sum = 0;
			 for (int k = 0; k < K-1; k ++) {
				 double zk = ilogit(y[k] + Math.log(1.0 / (K-k)));
				 x[k] = (1 - sum)*zk;
				 sum += x[k];
			 }
			 x[K-1] = 1-sum;
			 
			 return x;
			 
		 }
		 
		 
		 public double[] getAlpha(double[] breaks) {
			 
			 
			 double[] weights = repairSticks(breaks);
			 
			 
			 // Evaluate alpha-vector
			 this.alphaSum = 0;
			 for (int i = 0; i < this.ndim; i ++) {
				 double weightDividedByDim = weights[i] / this.dims[i];
				 //this.alpha[i] = this.emax[i] / (1 + weightDividedByDim);
				 
				 
				 weightDividedByDim = this.logit(weightDividedByDim);
				 double y = this.ehalf[i]*weightDividedByDim + this.emax[i];
				 this.alpha[i] = this.ilogit(y);
				 
				 this.alphaSum += this.alpha[i];
			 }

			 // Normalise and take mean distance
			 this.squaredDistance = 0;
			 for (int i = 0; i < this.ndim; i ++) {
				 this.alpha[i] = this.tau * this.alpha[i] / this.alphaSum;
			 }
			 return this.alpha;
		 }
		 
		 
		 public int getDimension() {
			 return this.ndim;
		 }
		 
		 @Override
		 public double value(double[] breaks) {
			 
			// double addon = 0;
			 
			 double[] weights = repairSticks(breaks);
			 
			 // Evaluate alpha-vector
			 this.alphaSum = 0;
			 for (int i = 0; i < this.ndim; i ++) {
				 double weightDividedByDim = weights[i] / this.dims[i];
				 //this.alpha[i] = this.emax[i] / (1 + this.ehalf[i]/weightDividedByDim) + addon;
				 
				 weightDividedByDim = this.logit(weightDividedByDim);
				 double y = this.ehalf[i]*weightDividedByDim + this.emax[i];
				 this.alpha[i] = this.ilogit(y);
				 
				//x = Math.log(x / (1-x));
				//y = m*x + this.getIntercept(targetNum);
				//y = 1 / (1 + Math.exp(-y)); 
				 
				 
				 this.alphaSum += this.alpha[i];
			 }

			 
			 // Normalise and take mean distance
			 this.squaredDistance = 0;
			 for (int i = 0; i < this.ndim; i ++) {
				 this.alpha[i] = this.tau * this.alpha[i] / this.alphaSum;
				 
				 this.squaredDistance += Math.pow(this.alpha[i] - this.target, 2);
				 //this.squaredDistance = Math.max(this.squaredDistance, Math.pow(this.alpha[i] - this.target, 2));
			 }
			 
			 // Return squared distance
			 return this.squaredDistance;
		 }
	    
	 }
	
	
	
	

}
