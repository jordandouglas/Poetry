package poetry.learning;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.optim.BaseMultivariateOptimizer;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.MaxIter;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimplePointChecker;
import org.apache.commons.math3.optim.linear.LinearConstraint;
import org.apache.commons.math3.optim.linear.LinearConstraintSet;
import org.apache.commons.math3.optim.linear.Relationship;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.gradient.NonLinearConjugateGradientOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.BOBYQAOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.CMAESOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.NelderMeadSimplex;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer;
import org.apache.commons.math3.random.JDKRandomGenerator;

import beast.core.BEASTObject;
import beast.core.Description;
import beast.core.Input;
import beast.core.Operator;
import beast.core.StateNode;
import beast.core.util.Log;
import poetry.PoetryAnalyser;
import poetry.operators.MetaOperator;
import poetry.sampler.POEM;


@Description("Assign weights to operators using some black box method")
public abstract class WeightSampler extends BEASTObject {
	
	
	
	final public Input<Boolean> staticInput = new Input<>("static", "Use static weights? The sampled weights apply to all instances "
			+ "of this class. This will enable the same operator weights to apply across parallel chains in coupled MCMC for example. Default: false", false);
	
	List<POEM> poems;
	protected List<Operator> poeticOperators;
	protected List<Operator> unpoeticOperators;
	File database;
	boolean isStatic;
	private double poeticSumInit;
	
	
	List<Integer> invalidOps = new ArrayList<Integer>();
	
	// Non-static operator weights
	double[] poeticWeights_local;
	
	// Static weights
	static double[] poeticWeights_static;
	
	// A state node which is to be ignored when weighting operators
	StateNode placeholder = null;
	
	
	// For accessing the database
	PoetryAnalyser poetry;

	protected boolean isMC3;
	

	/**
	 * Initialise the weight sampler
	 * @param poems
	 * @param operators
	 * @param database
	 */
	public void initialise(List<POEM> poems, File database, StateNode placeholder, PoetryAnalyser poetry, boolean isMC3) {
		
		this.poems = poems;
		this.database = database;
		this.poeticOperators = null;
		this.unpoeticOperators = null;
		this.isStatic = staticInput.get();
		this.poeticSumInit = 0;
		this.placeholder = placeholder;
		this.invalidOps = new ArrayList<Integer>();
		this.poetry = poetry;
		this.isMC3 = isMC3;
		
		
		// Check the database for weights
		
	}
	
	
	/**
	 * Log information periodically
	 */
	public void log() throws Exception  {
		
	}
	
	
	public boolean isMC3() {
		return this.isMC3;
	}
	
	public List<POEM> getPoems(){
		return this.poems;
	}

	/**
	 * Add operators and validate
	 * @param operators
	 */
	public void setOperators(List<Operator> operators) {

		// Check that every poem corresponds to an operator in the list
		for (POEM poem : this.poems) {
			
			if (poem.getOperator() == null) {
				throw new IllegalArgumentException(poem.getID() + " must have an operator!");
			}

			// Any matches?
			if (!operators.contains(poem.getOperator())) {
				throw new IllegalArgumentException("Cannot locate operator " + poem.getOperatorID() + ". Please ensure that every poem is associated with an operator");
			}
			
		}
		

		
		// Determine which operators do and do not have poems
		this.poeticOperators = new ArrayList<Operator>();
		this.unpoeticOperators = new ArrayList<Operator>();
		for (Operator op : operators) {
			
			
			// Ensure that each operator has no more than 1 poem
			boolean foundPoem = false;
			for (POEM poem : this.poems) {
				if (poem.getOperatorID().equals(op.getID())) {
					if (foundPoem) {
						throw new IllegalArgumentException("Operator " + op.getID() + " is associated with more than 1 POEM. Please ensure that every operator has at most 1 POEM.");
					}
					foundPoem = true;
				}
			}
			
			if (foundPoem) {
				//this.poeticOperators.add(op);
			}else {
				this.unpoeticOperators.add(op);
			}
			
		}
		
		
		// Put operators in poem order
		for (POEM poem : this.poems) {
			this.poeticOperators.add(poem.getOperator());
		}
		
		
		// Take note of operators which do not have any state nodes 
		// The placeholder does not count as an operator
		this.invalidOps = new ArrayList<Integer>();
		for (Operator op : this.poeticOperators) {
			List<StateNode> stateNodes = op.listStateNodes();
			stateNodes.remove(this.placeholder);
			if (stateNodes.isEmpty()) {
				this.invalidOps.add(this.poeticOperators.indexOf(op));
				op.m_pWeight.set(0.0);
			}
		}
		
		
		
	

		
		
		// Calculate the initial probabilistic sum of poetic operators
		double sum1 = getOperatorWeightSum(this.poeticOperators);
		double sum2 = getOperatorWeightSum(this.unpoeticOperators);
		this.poeticSumInit = sum1 / (sum1 + sum2);
		
		
		
		// Normalise all operator weights so they sum to 1
		for (Operator op : operators) {
			op.m_pWeight.set(op.getWeight() / (sum1 + sum2));
		}
		
		
	}

	

	
	
	/**
	 * Get sum of operator weights
	 * @param ops
	 * @return
	 */
	public static double getOperatorWeightSum(List<Operator> ops) {
		double sum = 0;
		for (Operator op : ops) sum += op.getWeight();
		return sum;
	}
	
	
	/**
	 * Return the initial probabilistic weight sum of operators
	 * which are affiliated with poems
	 * @return
	 */
	public double getInitialPoeticProb() {
		return this.poeticSumInit;
	}
	
	
	
	/**
	 * Set the sampled weights
	 * Parsed weights do not need to be normalised yet
	 * @param poemWeights
	 * @throws Exception 
	 */
	protected void setWeights(double[] poemWeights) throws Exception {

		if (this.isStatic) {
			poeticWeights_static = poemWeights;
		}else {
			this.poeticWeights_local = poemWeights;
		}
		
		// Correct the weights if they have already been set
		if (this.poetry != null) this.poetry.correctWeights(poemWeights);
		
	}
	

	
	
	
	/**
	 * Does this operator have any state nodes (aside from the placeholder) ?
	 * @param operator
	 * @return
	 */
	protected boolean opIsValid(Operator operator) {
		for (int index : this.invalidOps) {
			Operator invalid = this.poeticOperators.get(index);
			if (invalid == operator) return false;
		}
		return true;
	}
	
	
	
	/**
	 * Get the sampled weight vector
	 * First normalise by multiplying by the weight max
	 * Filters out the weights of invalid operators
	 * @return
	 */
	public double[] getWeights() {
		double[] weights;
		if (this.isStatic) {
			weights = poeticWeights_static;
		}else {
			weights = this.poeticWeights_local;
		}
		

		
		// Sum weights and set weights to zero if the operator is invalid
		double[] weightsNormalised = new double[weights.length];
		double sum = 0.0;
		for (int j = 0; j < weights.length; j++) {
			
			if (!this.opIsValid(this.poeticOperators.get(j))) {
				weightsNormalised[j] = 0;
			}else {
				weightsNormalised[j] = weights[j];
			}
			sum += weightsNormalised[j];
		}
		
		// Normalise so they sum to the weight max
		sum *= this.poeticSumInit;
		for (int j = 0; j < weights.length; j++) {
			if (sum == 0) weightsNormalised[j] = 0;
			else weightsNormalised[j] = weightsNormalised[j] / sum;
		}
		
		return weightsNormalised;
	}
	
	
	/**
	 * Sample operator weights and set them
	 */
	public abstract void sampleWeights() throws Exception;
	
	
	/**
	 * Return a list of sampled weights, without applying them
	 * @param poems
	 * @return
	 */
	public abstract double[] sampleWeights(List<POEM> poems)  throws Exception;
	
	/**
	 * Sample weights from a list of poems (static)
	 * @param poems
	 * @return
	 * @throws Exception
	 */
	//public abstract double[] sampleWeights(List<POEM> poems);
	
	
	
	
	/**
	 * Assign the pre-set weights to the list of operators
	 * @throws Exception 
	 */
	public void applyWeights() throws Exception {
		
		double[] weights = this.getWeights();
		

		for (int j = 0; j < weights.length; j++) {
			Operator op = this.poeticOperators.get(j);
			POEM poem = this.poems.get(j);
			
			// Can the weight be set?
			if (!this.allowOpWeightSet(op)) {
				poem.setWeight(op.getWeight());
			}else {
				op.m_pWeight.set(weights[j]);
				poem.setWeight(weights[j]);
			}
			
			
		}
		
	}
	
	
	/**
	 * Is this operator allowed to have its weight changed?
	 * @param op
	 * @return
	 */
	protected boolean allowOpWeightSet(Operator op) {
		
		// Do not change operator weight if its was loaded from a state file
		if (op instanceof MetaOperator) {
			MetaOperator meta = (MetaOperator)op;
			if (meta.weightLoadedFromStateFile()){
				return false;
			}
		}
		return true;
		
	}


	
	/**
	 * Report the current weights
	 */
	public void report() {
		
		for (Operator op : this.poeticOperators) {
			double weight = op.getWeight();
			Log.warning("Sampling " + op.getID() +  " weight as " + weight);
		}
		for (Operator op : this.unpoeticOperators) {
			double weight = op.getWeight();
			Log.warning("The weight of " + op.getID() +  " is " + weight + " (this operator is not affiliated with any POEMS)");
		}
		
		
	}


	public int getNumPoems() {
		return this.poems.size();
	}

	
	

	
	/**
	 * Return weights which optimise the function
	 * @param fn
	 * @return
	 */
	public static double[] optimiseSimplex(MultivariateFunction fn, int ndim, boolean maximise) {
		
		// Return optimum after trying several starting points
		PointValuePair optimal = null;
		
		
		
		// Number of iterations
		MaxIter maxIter = new MaxIter(1000);
		MaxEval maxEval = new MaxEval(10000);
		ObjectiveFunction objective = new ObjectiveFunction(fn);
		
		
		// Maximimise or minimise
		GoalType goaltype = maximise ? GoalType.MAXIMIZE : GoalType.MINIMIZE;
		
		
		//SimpleBounds bounds = new SimpleBounds(new double[] { 0,0,0,0,0,0 }, new double[] { 1,1,1,1,1 } );
		NelderMeadSimplex simplex = new NelderMeadSimplex(ndim-1, 1.0);
		
		SimplexOptimizer opt = new SimplexOptimizer(1e-8, 1e-20);
		//MultivariateOptimizer opt = new CMAESOptimizer(1000, 0.01, true, 1, 1, new JDKRandomGenerator(), false, checker);

		// Midpoint
		double[] midPoint = new double[ndim];
		for (int i = 0; i < ndim; i ++) midPoint[i] = 1.0 / ndim;
		midPoint = breakSticks(midPoint);
		
		double[] initArr;
		for (int a = -1; a < ndim; a++) {
			
			

			//SearchInterval interval = new SearchInterval(0.0, 1.0, 0.5);
			
			
			// Start in a corner
			if (a > -1) {
				initArr = new double[ndim-1];
				
				for (int i = 0; i < ndim-1; i ++) {
					initArr[i] = i == a ? -1 : 1;
				}
				
			}
			
			// Start in the middle
			else {
				initArr = midPoint;
			}
			
			
			
			//SimplePointChecker<PointValuePair> checker = new SimplePointChecker<PointValuePair>(1e-20, 1e-20);
			
			InitialGuess init = new InitialGuess(initArr);
			
		
		
			try {
				PointValuePair result = opt.optimize(maxIter, maxEval, objective, init, simplex, goaltype);	
				
				boolean better;
				if (optimal == null) better = true;
				else {
					double oldV = fn.value(optimal.getPoint());
					double newV = fn.value(result.getPoint()); 
					better = maximise ? (newV > oldV) : (newV < oldV) ;
				}
				
						
				if (better) optimal = result;
				
				//PointValuePair min = opt.optimize(maxIter, maxEval, objective, goaltype, init);		

				//return min.getPoint();
				
			}catch(Exception e) {
				e.printStackTrace();
				//return initArr;
			}
			
			
		}
		
		if (optimal == null) {
			return midPoint;
		}
		
		else return optimal.getPoint();
		
	}
	
	
	/**
	 * Logit function
	 * @param x
	 * @return
	 */
	 public static double logit(double x) {
		 return Math.log(x / (1-x));
	 }
	 
	 
	 /**
	  * Inverse logic
	  * @param x
	  * @return
	  */
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
	 

	
	
}
