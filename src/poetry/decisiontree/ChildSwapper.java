package poetry.decisiontree;

import beast.core.Description;
import beast.core.Input;
import beast.core.Operator;
import beast.util.Randomizer;

@Description("Swaps two children in a decision tree")
public class ChildSwapper extends Operator {

	
	final public Input<DecisionTreeInterface> treeInput = new Input<>("tree", "the tree", Input.Validate.REQUIRED);
	
	
	@Override
	public void initAndValidate() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public double proposal() {
		
		
		DecisionTree tree = treeInput.get(this).editTree(this);
		double logHR = 0;
		
		
		// Sample a parent node
		int numParents = tree.getNodeCount() - tree.getLeafCount();
		if (numParents == 0) return Double.NEGATIVE_INFINITY;
		int nodeNum = Randomizer.nextInt(numParents) + tree.getLeafCount();
		DecisionNode parent = tree.getNode(nodeNum);
		
		
		// Swap its children
		parent.swapChildren();
		
		
		// Renumber the tree
		tree.reset();
		
		
		return logHR;
		
	}

}
