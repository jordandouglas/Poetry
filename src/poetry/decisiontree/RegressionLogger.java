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
		if (distInput.get().getNClasses() == 1) {
			out.print(getR2Colname() +  "(train)\t" + getCorrelaionColname() + "(train)\t" + getR2Colname()+ "(test)\t" + getCorrelaionColname() + "(test)\t");
		}else {
			for (int t = 1; t <=  distInput.get().getNClasses(); t++) {
				out.print(getR2Colname(t) +  "(train)\t" + getCorrelaionColname(t) + 
							"(train)\t" + getR2Colname(t) + "(test)\t" + getCorrelaionColname(t) + "(test)\t");
			}
		}
	}

	
	public static String getR2Colname() {
		return "R2";
	}
	
	public static String getCorrelaionColname() {
		return "rho";
	}
	
	public static String getR2Colname(int index) {
		return "R2_" + index;
	}
	
	public static String getCorrelaionColname(int index) {
		return "rho_" + index;
	}
	
	@Override
	public void close(PrintStream out) {
		
		
		
	}
	
	@Override
	public void log(long sample, PrintStream out) {
		double[] res = distInput.get().getR2AndCorrelation();
		for (double r : res) {
			out.print(r + "\t");
		}
	}


	@Override
	public void initAndValidate() {
		// TODO Auto-generated method stub
		
	}


	@Override
	public int getDimension() {
		return 4 * distInput.get().getNumPredictors();
	}


	@Override
	public double getArrayValue(int dim) {
		double[] res = distInput.get().getR2AndCorrelation();
		return res[dim];
	}
	
	

}
