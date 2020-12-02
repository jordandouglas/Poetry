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

	
	final public Input<DecisionTreeInterface> treeInput = new Input<>("tree", "The decision tree", Validate.REQUIRED);
	

	
	@Override
	public void init(PrintStream out) {
		if (this.treeInput.get().getForestSize() > 1) {
			for (int i = 1; i <= this.treeInput.get().getForestSize(); i ++) {
				out.print(getLeafColname() + i + "\t");
			}
		}else {
			out.print(getLeafColname() + "\t");
		}
		
	}

	
	public static String getLeafColname() {
		return "leafcount";
	}
	
	
	@Override
	public void close(PrintStream out) {
		
		
		
	}
	
	@Override
	public void log(long sample, PrintStream out) {
		for (DecisionTree tree : treeInput.get().getTrees()) {
			out.print(tree.getLeafCount() + "\t");
		}
	}


	@Override
	public void initAndValidate() {
		// TODO Auto-generated method stub
		
	}


	@Override
	public int getDimension() {
		return this.treeInput.get().getForestSize();
	}


	@Override
	public double getArrayValue(int dim) {
		return treeInput.get().getTree(dim).getNodeCount();
	}
	
	

}
