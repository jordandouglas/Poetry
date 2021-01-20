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
import weka.core.converters.ConverterUtils.DataSource;

public class BayesianDecisionTreeSampler extends WeightSampler {

	
	final public Input<Tree> treeInput = new Input<>("tree", "The phylogenetic tree (used for machine learning)", Input.Validate.REQUIRED);
	final public Input<Alignment> dataInput = new Input<>("data", "The alignment (used for machine learning)", Input.Validate.REQUIRED);
	
	final public Input<ResponseMode> regressionInput = new Input<>("regression", "Regression model at the leaves", ResponseMode.test, ResponseMode.values());
	
	final public Input<List<ModelValue>> modelValuesInput = new Input<>("model", "List of models and their values -- for applying the decsion tree from the model", new ArrayList<>());
	final public Input<String> treesInput = new Input<>("trees", "A file containing decision trees", Input.Validate.REQUIRED);
	final public Input<String> datasetInput = new Input<>("dataset", "A file instances to train GP on at the leaves", Input.Validate.REQUIRED);
	
	
	final private static String distClassName = "Pmean";
	
	// The decision tree
	DecisionTree tree;
	
	// The leaf in the decision tree corresponding to this xml file
	DecisionNode leaf;
	
	// The prior database associated with this leaf
	Instances priorDatabase = null;
	
	
	// This session represented as an Instances object with 1 entry
	Instances currentSessionInst;
	
	
	@Override
	public void initAndValidate() {
		
		
		try {
			
			
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Error loading decision tree");
		}
		
		
		
	}

	@Override
	public void sampleWeights() throws Exception {
		double[] weights = this.sampleWeights(poems);
		this.setWeights(weights);
	}
	
	
	/**
	 * Return the prior distribution database for the current leaf
	 * @return
	 * @throws Exception
	 */
	public Instances getDatabaseAtLeaf() throws Exception {
		
		// Load dataset for training GP
		if (priorDatabase == null) {
			this.priorDatabase = new DataSource(datasetInput.get()).getDataSet();
			this.priorDatabase.setClass(this.priorDatabase.attribute(distClassName));
		}
		return priorDatabase;
	}
	
	
	
	/**
	 * Load the current beast2 session as an instance
	 * @return
	 */
	public Instances getCurrentSession() {
		
		// Model values
		List<ModelValue> modelValues = modelValuesInput.get();
		
		//Current session
		return BEAST2Weka.getInstance(dataInput.get(), treeInput.get(), this, modelValues);
		
	}
	

	/**
	 * 
	 */
	@Override
	public double[] sampleWeights(List<POEM> poems) throws Exception {

		
		double[] weights = new double[poems.size()];
		
		

		// Load decision tree and prepare the decision tree leaf
		List<DecisionTree>  trees = parseDecisionTrees(new File(treesInput.get()), 10);
		this.tree = trees.get(trees.size()-1);
		this.tree.setRegressionMode(regressionInput.get());
		Log.warning("tree : " + this.tree.toString());
		
		
		
		
		
		// Get the Weka Instance of this session
		currentSessionInst = this.getCurrentSession();
		
		// Get the leaf
		this.leaf = tree.getLeaf(currentSessionInst);
		Log.warning("leaf" + this.leaf.toString());
		
		
		// Weight columns are predictors
		List<String> weightCols = new ArrayList<>();
		for (int i = 0; i < this.getNumPoems()-1;i++) {
			POEM poem = poems.get(i);
			String weightCol = poem.getWeightColname();
			weightCols.add(weightCol);
		}
		leaf.setPredAttrs(weightCols);
		
		
		// Load dataset for training GP
		this.priorDatabase = new DataSource(datasetInput.get()).getDataSet();
		this.priorDatabase.setClass(this.priorDatabase.attribute(distClassName));

		
		// Filter instances using leaf and train the GP
		//Log.warning(this.priorDatabase.size() + " instances in database");
		this.leaf.filterInstances(this.priorDatabase, true);
		this.priorDatabase = this.leaf.setSplitData(this.priorDatabase, true);
		//Log.warning(this.priorDatabase.size() + " instances at leaf");
		
		
		// Minimise pmean
		PMeanFunction fn = new PMeanFunction(this.leaf, this.priorDatabase);
		double[] tweights = optimiseSimplex(fn, fn.getDimension(), false);
		Log.warning("Minimised mean distance: " + Math.exp(fn.value(tweights)));
		weights = repairSticks(tweights);
		return weights;
		
		
		
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
	
	
	private class PMeanFunction implements MultivariateFunction {
		
		int ndim;
		DecisionNode leaf;
		Instance inst;
		Instances database;
		
		public PMeanFunction(DecisionNode leaf, Instances database) {
			this.leaf = leaf;
			this.inst = new DenseInstance(database.numAttributes());
			this.inst.setDataset(database);
			this.database = database;
			this.ndim = this.leaf.getPredAttrs().size() + 1;
		}
		
		
		public int getDimension() {
			 return this.ndim;
		 }
		
		
		@Override
		public double value(double[] breaks) {
			
			
			if (breaks.length + 1 != this.ndim) {
				throw new IllegalArgumentException("Dimensional mismatch: " + breaks.length + "+1 != " + this.ndim);
			}
			
			
			// Set values
			for (int i = 0; i < breaks.length; i ++) {
				String weightColName = this.leaf.getPredAttrs().get(i);
				Attribute attr = this.database.attribute(weightColName);
				double weight = breaks[i];
				this.inst.setValue(attr, weight);
			}
			
			
			//Log.warning("weights " + inst.toString());
			
			// Classify. Want to mininise pmean
			return leaf.predict(this.inst)[0];
			
		}
		
	}
	
	
	
	
	 
	 /**
	  * A function which can be optimised using a MultivariateOptimizer
	  *
	  */
	 private class SquaredAlphaDistance implements MultivariateFunction {

		 
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
		 
		
	
		 
		 public double[] getAlpha(double[] breaks) {
			 
			 
			 double[] weights = repairSticks(breaks);
			 
			 
			 // Evaluate alpha-vector
			 this.alphaSum = 0;
			 for (int i = 0; i < this.ndim; i ++) {
				 double weightDividedByDim = weights[i] / this.dims[i];
				 //this.alpha[i] = this.emax[i] / (1 + weightDividedByDim);
				 
				 
				 weightDividedByDim = logit(weightDividedByDim);
				 double y = this.ehalf[i]*weightDividedByDim + this.emax[i];
				 this.alpha[i] = ilogit(y);
				 
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
				 
				 weightDividedByDim = logit(weightDividedByDim);
				 double y = this.ehalf[i]*weightDividedByDim + this.emax[i];
				 this.alpha[i] = ilogit(y);
				 
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
					tweights = optimiseSimplex(fn, fn.getDimension(), false);
					
					
					// Add some random jitter
					for (int j = 0; j < fn.getDimension()-1; j ++) {
						tweights[j] = tweights[j] + Randomizer.nextGaussian()*Randomizer.nextExponential(5);
						//if (true || i != 2*nsamples -1)tweights[j] += Randomizer.nextGaussian()*Randomizer.nextExponential(5);
					}
					
					
					
				}else {
					
					double[] weights = null;//TODO DimensionalSampler.sampleWeights(poems, Randomizer.nextExponential(0.1));
					double weightSum = 0;
					for (int j = 0; j < fn.getDimension(); j ++) {
						weights[j] += 0.00001; // Prevent numerical instabilities from tiny weights
						weightSum += weights[j];
					}
					for (int j = 0; j < fn.getDimension(); j ++) weights[j] /= weightSum;
					//double[] weights = DirichletSampler.sampleWeights(poems);
					tweights = breakSticks(weights);
					
					
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
			
			/*
			// Save the dataset
			ArffSaver saver = new ArffSaver();
			saver.setInstances(instances);
			saver.setFile(new File("/home/jdou557/Documents/Marsden2019/Months/December2020/BDT/kernel.arff"));
			saver.writeBatch();
			*/
			
			
			// Train the kernel
			// No normalisation or standardisation. RBFKernel
			GaussianProcesses kernel = new GaussianProcesses();
			kernel.setOptions(new String[] { "-N", "2", "-K", RBFKernel.class.getCanonicalName() });
			kernel.buildClassifier(instances);
			
			
			
			Instance instance = new DenseInstance(nattr);
			instance.setDataset(instances);
			double[] tweights = optimiseSimplex(fn, fn.getDimension(), false); // fn.breakSticks(DirichletSampler.sampleWeights(poems));
			
			// Set the transformed weight (ie the broken stick)
			for (int j = 0; j < fn.getDimension()-1; j ++) {
				System.out.println("tweight " + tweights[j]);
				instance.setValue(instances.attribute("tweight" + j), tweights[j]);
			}
			
			
			double sd = kernel.getStandardDeviation(instance);
			double mean = kernel.classifyInstance(instance);
			System.out.println("opt mean: " + mean + " sd: " + sd + " real space val " + Math.exp(mean) + " true " + fn.value(tweights));
			
			
			
			tweights = null;// TODO breakSticks(DimensionalSampler.sampleWeights(poems, 20));
			
			// Set the transformed weight (ie the broken stick)
			for (int j = 0; j < fn.getDimension()-1; j ++) {
				instance.setValue(instances.attribute("tweight" + j), -1.0);
			}
			
			
			sd = kernel.getStandardDeviation(instance);
			mean = kernel.classifyInstance(instance);
			System.out.println("rand mean: " + mean + " sd: " + sd + " real space val " + Math.exp(mean) + " true " + fn.value(tweights));
			
		}
		
	
	
	

}
