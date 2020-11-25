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
public class LeafCountLogger extends CalculationNode implements Loggable, Function {

	
	final public Input<DecisionTree> treeInput = new Input<>("tree", "The decision tree", Validate.REQUIRED);
	

	
	@Override
	public void init(PrintStream out) {
		out.print(getColname() + "\t");
	}

	
	public static String getColname() {
		return "leafcount";
	}
	
	
	@Override
	public void close(PrintStream out) {
		
		
		
	}
	
	@Override
	public void log(long sample, PrintStream out) {
		out.print(treeInput.get().getLeafCount() + "\t");
	}


	@Override
	public void initAndValidate() {
		// TODO Auto-generated method stub
		
	}


	@Override
	public int getDimension() {
		return 1;
	}


	@Override
	public double getArrayValue(int dim) {
		return treeInput.get().getNodeCount();
	}
	
	

}
