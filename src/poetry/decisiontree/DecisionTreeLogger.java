package poetry.decisiontree;

import java.io.PrintStream;
import java.util.List;

import beast.core.BEASTObject;
import beast.core.Description;
import beast.core.Function;
import beast.core.Input;
import beast.core.Loggable;
import beast.core.Input.Validate;


@Description("Logs decision tree annotated with splits at the internal nodes and regression at the leaves")
public class DecisionTreeLogger extends BEASTObject implements Loggable { 

	final public Input<DecisionTreeInterface> treeInput = new Input<>("tree", "Decision tree to be logged", Validate.REQUIRED);
	final public Input<DecisionTreeDistribution> treeDistrInput = new Input<>("dist", "the tree distribution", Input.Validate.REQUIRED);
	
	@Override
	public void initAndValidate() {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void init(PrintStream out) {
		treeInput.get().init(out);
	}
	
	@Override
    public void log(long sample, PrintStream out) {
		
		
		// Log a random tree if there is more than one
		DecisionTreeInterface treeI = treeInput.get();
		DecisionTree tree = (DecisionTree) treeI.sampleTree().getCurrent();

		
		treeDistrInput.get().split();
        out.print(toNewick(tree.getRoot()));
        out.print(";");
        
	}
	
	@Override
	public void close(PrintStream out) {
		// TODO Auto-generated method stub
		
	}
	
	
	

    public static String toNewick(DecisionNode node) {
        StringBuffer buf = new StringBuffer();
        if (node.getTrueChild() != null) {
            buf.append("(");
            buf.append(toNewick(node.getTrueChild()));
            if (node.getFalseChild() != null) {
                buf.append(',');
                buf.append(toNewick(node.getFalseChild()));
            }
            buf.append(")");
        } else {
            buf.append(node.getIndex() + 1);
        }
		
		//Metadata
		buf.append("[&");
		node.getMetaDataString(buf);
		buf.append(']');
		
		
        buf.append(":");
        
        
        // Branch lengths are 1
        buf.append(1);
        
        return buf.toString();
    }
    



}


