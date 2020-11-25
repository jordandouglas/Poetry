package poetry.decisiontree;

import java.io.PrintStream;

import beast.core.CalculationNode;
import beast.core.Function;
import beast.core.Input;
import beast.core.Loggable;
import beast.core.Input.Validate;


/**
 * Logs the R2 and correlation of a decision tree
 */
public class RegressionLogger extends CalculationNode implements Loggable, Function {

	
	final public Input<DecisionTreeDistribution> distInput = new Input<>("dist", "The decision tree distribution", Validate.REQUIRED);
	

	
	@Override
	public void init(PrintStream out) {
		out.print(getR2Colname() + "\t" + getCorrelaionColname() + "\t");
	}

	
	public static String getR2Colname() {
		return "R2";
	}
	
	public static String getCorrelaionColname() {
		return "rho";
	}
	
	@Override
	public void close(PrintStream out) {
		
		
		
	}
	
	@Override
	public void log(long sample, PrintStream out) {
		double[] res = distInput.get().getR2AndCorrelation();
		out.print(res[0] + "\t" + res[1] + "\t");
		
	}


	@Override
	public void initAndValidate() {
		// TODO Auto-generated method stub
		
	}


	@Override
	public int getDimension() {
		return 2;
	}


	@Override
	public double getArrayValue(int dim) {
		double[] res = distInput.get().getR2AndCorrelation();
		return res[dim];
	}
	
	

}
