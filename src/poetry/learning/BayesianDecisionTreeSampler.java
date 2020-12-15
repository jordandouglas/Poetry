package poetry.learning;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.geometry.euclidean.oned.Interval;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.MaxIter;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.optim.linear.LinearConstraint;
import org.apache.commons.math3.optim.linear.LinearConstraintSet;
import org.apache.commons.math3.optim.linear.LinearObjectiveFunction;
import org.apache.commons.math3.optim.linear.Relationship;
import org.apache.commons.math3.optim.linear.SimplexSolver;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.NelderMeadSimplex;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer;
import org.apache.commons.math3.optim.univariate.BrentOptimizer;
import org.apache.commons.math3.optim.univariate.SearchInterval;
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction;
import org.apache.commons.math3.optim.univariate.UnivariateOptimizer;
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair;

import beast.core.Input;
import beast.core.util.Log;
import beast.evolution.alignment.Alignment;
import beast.evolution.tree.Tree;
import poetry.decisiontree.DecisionNode;
import poetry.decisiontree.DecisionTree;
import poetry.decisiontree.DecisionTreeDistribution.ResponseMode;
import poetry.sampler.POEM;
import poetry.util.BEAST2Weka;
import weka.core.Instances;

public class BayesianDecisionTreeSampler extends WeightSampler {

	
	final public Input<Tree> treeInput = new Input<>("tree", "The phylogenetic tree (used for machine learning)", Input.Validate.REQUIRED);
	final public Input<Alignment> dataInput = new Input<>("data", "The alignment (used for machine learning)", Input.Validate.REQUIRED);
	
	final public Input<ResponseMode> regressionInput = new Input<>("regression", "Regression model at the leaves", ResponseMode.test, ResponseMode.values());
	
	final public Input<List<ModelValue>> modelValuesInput = new Input<>("model", "List of models and their values -- for applying the decsion tree from the model", new ArrayList<>());
	final public Input<String> treesInput = new Input<>("trees", "A file containing decision trees", Input.Validate.REQUIRED);
	
	
	
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
		List<DecisionTree> trees = parseDecisionTrees(new File(treesInput.get()));
		DecisionTree decisionTree = trees.get(trees.size()-1);
		decisionTree.setRegressionMode(regressionInput.get());
		System.out.println("tree : " + decisionTree.toString());
		
		weights = this.getWeights(decisionTree, instances);
		
		
		
		this.setWeights(weights);
		
	}
	
	
	
	protected double[] getWeights(DecisionTree tree, Instances inst) {
		
		double[] weights = new double[this.poems.size()];
		
		int ntaxa = (int)(inst.instance(0).value(inst.attribute("ntaxa")));
		
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
			
			
			System.out.println(poem.getID() + " slope " + slope + " intercept " + intercept + " target " + target);
			
			
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
		double[] opt = optimiseSimplex(fn);
		weights = fn.repairSticks(opt);
		System.out.print("max: ");
		for (double o : weights) System.out.print(o + ", ");
		System.out.println(" eval: " + fn.value(opt));
		
		System.out.print("alpha: ");
		for (double o : fn.getAlpha(opt)) System.out.print(o + ", ");
		System.out.println();

		
		return weights;
	}
	
	
	
	protected double[] getWeights(DecisionTree[] trees, Instances inst) {
		
		int ntaxa = (int)(inst.instance(0).value(inst.attribute("ntaxa")));
		
		// Get optimal weights from current state
		double[] weights = new double[trees.length];
		for (int j = 0; j < weights.length; j++) {
			DecisionNode leaf = trees[j].getLeaf(inst);
			POEM poem = this.poems.get(j);
			
			double slope = leaf.getSlope(0);
			double dim = BEAST2Weka.getDimension(poem.getID(), poem.getDim(), ntaxa); 
			double essVal = slope * dim;
			weights[j] = essVal;
			
		}
		
		
		return weights;
		
		
	}
	
	
	
	/**
	 * Attempts to optimise operator weights using the tree
	 * @param trees
	 * @param inst
	 * @return
	 */
	protected double[] optimise(DecisionTree[] trees, Instances inst) {
		
		
		return null;
		
	}
	
	
	
/*
	protected double predictPMean(DecisionTree[] trees, Instances inst) {
		
		// Predict fractional ESSes
		double essSum = 0;
		double[] ess = new double[trees.length];
		for (int j = 0; j < ess.length; j++) {
			DecisionNode leaf = trees[j].getLeaf(inst);
			POEM poem = this.poems.get(j);
			
			double slope = leaf.getSlope(0);
			double intercept = leaf.getIntercept();
			double weightDim = inst.get(0).value(inst.attribute(BEAST2Weka.getPoemWeightDimensionAttr(poem).name()));
			double dim = getDimension(poem.getID(), poem.getDim(), ntaxa); 
			double weight = weightDim * dim;
			double essVal = intercept / (1 + slope/weight);
			essSum += essVal;
			ess[j] = essVal;
		}
		
		// Normalise
		double perfectFraction = 1.0 / trees.length;
		double pmean = 0;
		for (int j = 0; j < ess.length; j++) {
			ess[j] /= essSum;
			pmean += Math.pow(ess[j] - perfectFraction, 2) / ess.length;
		}
				
		return pmean;
	}
	*/
	
	
	/**
	 * Parse decision trees from a newick file
	 * @param newickFile
	 * @return
	 * @throws IOException
	 */
	public static List<DecisionTree> parseDecisionTrees(File newickFile) throws IOException {
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
		
		
		return trees;
	}
	
	
	
	
	public double[] optimiseSimplex(SquaredAlphaDistance fn) {
		
		MaxIter maxIter = new MaxIter(100);
		MaxEval maxEval = new MaxEval(10000);
		ObjectiveFunction objective = new ObjectiveFunction(fn);
		//SearchInterval interval = new SearchInterval(0.0, 1.0, 0.5);
		double[] initArr = new double[fn.getDimension()-1];
		InitialGuess init = new InitialGuess(initArr);
		
		//SimpleBounds bounds = new SimpleBounds(new double[] { 0,0,0,0,0,0 }, new double[] { 1,1,1,1,1 } );
		NelderMeadSimplex simplex =  new org.apache.commons.math3.optim.nonlinear.scalar.noderiv.NelderMeadSimplex(fn.getDimension()-1, 1.0);
		
		SimplexOptimizer opt = new SimplexOptimizer(1e-10, 1e-30);
		
		// Unit sum
		Collection<LinearConstraint> constraints = new ArrayList<>();
		constraints.add(new LinearConstraint(new double[] { 1,1,1,1,1,1 }, Relationship.LEQ, 1.0));
		LinearConstraintSet constraintSet = new LinearConstraintSet(constraints);
		
		PointValuePair max = opt.optimize(maxIter, maxEval, constraintSet, objective, init, simplex,  GoalType.MINIMIZE);		
		
		System.out.println(" init: " + fn.value(init.getInitialGuess()));
		   
		 
		//BrentOptimizer bo = new BrentOptimizer(1e-5, 1e-5);
		//UnivariatePointValuePair max = bo.optimize(	new MaxIter(maxIter), new MaxEval(maxEval), interval, 
												//	new UnivariateObjectiveFunction(fn), GoalType.MINIMIZE);
		
		
		
		//System.out.print("alpha: ");
		//for (double o : fn.getAlpha(max.getPoint())) System.out.print(o + ", ");
		//System.out.println();
		
		/*
		// Break sticks test
		double[] weights = new double[] { 0.3, 0.2, 0.000001, 0.1, 0.1, 0.3-0.000001 };
		double[] breaks = fn.breakSticks(weights);
		double[] sticks = fn.repairSticks(breaks);
		
		System.out.print("breaks: ");
		for (double o : breaks) System.out.print(o + ", ");
		System.out.println();
		
		System.out.print("sticks: ");
		for (double o : sticks) System.out.print(o + ", ");
		System.out.println();
		*/
		
		return max.getPoint();
		
	}
	
	
	 private static class TestFunction implements UnivariateFunction {
	     public double value(double x) {
	         double y = x*x - 5*x;;
	         return y;
	     }
	 }
	 
	 
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
		 
		 public double logit(double x) {
			 return Math.log(x / (1-x));
		 }
		 
		 
		 public double ilogit(double x) {
			 return 1.0 / (1 + Math.exp(-x));
		 }
		 
		 
		 public double[] breakSticks(double[] x) {
			 
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
		 
		 
		 public double[] repairSticks(double[] y) {
			 
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
				 this.alpha[i] = this.emax[i] / (1 + weightDividedByDim);
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
			 
			 
			 double[] weights = repairSticks(breaks);
			 
			 // Evaluate alpha-vector
			 this.alphaSum = 0;
			 for (int i = 0; i < this.ndim; i ++) {
				 double weightDividedByDim = weights[i] / this.dims[i];
				 this.alpha[i] = this.emax[i] / (1 + this.ehalf[i]/weightDividedByDim);
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
