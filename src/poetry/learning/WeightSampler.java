package poetry.learning;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import beast.core.BEASTObject;
import beast.core.Description;
import beast.core.Input;
import beast.core.Operator;
import beast.core.util.Log;
import poetry.sampler.POEM;


@Description("Assign weights to operators using some black box method")
public abstract class WeightSampler extends BEASTObject {
	
	
	
	final public Input<Boolean> staticInput = new Input<>("static", "Use static weights? The sampled weights apply to all instances "
			+ "of this class. This will enable the same operator weights to apply across parallel chains in coupled MCMC for example. Default: false", false);
	
	List<POEM> poems;
	List<Operator> poeticOperators;
	List<Operator> unpoeticOperators;
	File database;
	boolean isStatic;
	private double poeticSumInit;
	
	
	// Non-static operator weights
	double[] poeticWeights_local;
	
	// Static weights
	static double[] poeticWeights_static;
	

	/**
	 * Initialise the weight sampler
	 * @param poems
	 * @param operators
	 * @param database
	 */
	public void initialise(List<POEM> poems,  File database) {
		
		this.poems = poems;
		this.database = database;
		this.poeticOperators = null;
		this.unpoeticOperators = null;
		this.isStatic = staticInput.get();
		this.poeticSumInit = 0;
		
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
			
			boolean foundPoem = false;
			for (POEM poem : this.poems) {
				if (poem.getOperatorID().equals(op.getID())) {
					foundPoem = true;
					break;
				}
			}
			
			if (foundPoem) {
				this.poeticOperators.add(op);
			}else {
				this.unpoeticOperators.add(op);
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
	 * @param poemWeights
	 */
	protected void setWeights(double[] poemWeights) {
		if (this.isStatic) {
			poeticWeights_static = poemWeights;
		}else {
			this.poeticWeights_local = poemWeights;
		}
	}
	
	
	
	/**
	 * Get the sampled weight vector
	 * First normalise by multiplying by the weight max
	 * @return
	 */
	public double[] getWeights() {
		double[] weights;
		if (this.isStatic) {
			weights = poeticWeights_static;
		}else {
			weights = this.poeticWeights_local;
		}
		
		double[] weightsNorm = new double[weights.length];
		for (int i = 0; i < weights.length; i ++) {
			weightsNorm[i] = weights[i] * this.poeticSumInit;
		}
		
		return weightsNorm;
	}
	
	
	/**
	 * Sample operator weights but don't set them
	 */
	public abstract void sampleWeights();
	
	
	/**
	 * Assign the pre-set weights to the list of operators
	 */
	public void applyWeights() {
		double[] weights = this.getWeights();
		for (int j = 0; j < weights.length; j++) {
			Operator op = this.poeticOperators.get(j);
			POEM poem = this.poems.get(j);
			op.m_pWeight.set(weights[j]);
			poem.setWeight(weights[j]);
		}
		
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



	
	
}
