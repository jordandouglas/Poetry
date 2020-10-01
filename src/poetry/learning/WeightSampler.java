package poetry.learning;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import beast.core.BEASTObject;
import beast.core.Description;
import beast.core.Operator;
import beast.core.util.Log;
import poetry.sampler.POEM;


@Description("Assign weights to operators using some black box method")
public abstract class WeightSampler extends BEASTObject {
	
	
	List<POEM> poems;
	List<Operator> poeticOperators;
	List<Operator> unpoeticOperators;
	File database;
	private double poeticSumInit;

	/**
	 * Initialise the weight sampler
	 * @param poems
	 * @param operators
	 * @param database
	 */
	public void initialise(List<POEM> poems, List<Operator> operators, File database) {
		
		
		this.poems = poems;
		this.database = database;
		this.poeticOperators = new ArrayList<Operator>();
		this.unpoeticOperators = new ArrayList<Operator>();
		
		if (operators == null || this.poems == null) return;
		//if (!this.database.exists() || !this.database .canRead()) {
			//throw new IllegalArgumentException("Cannot read from the database");
		//}
		
		
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
	 * Assign weights to the list of operators
	 */
	public abstract void assignWeights();


	
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
