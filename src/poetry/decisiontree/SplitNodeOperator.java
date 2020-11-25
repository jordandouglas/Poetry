package poetry.decisiontree;

import java.util.ArrayList;
import java.util.List;

import beast.core.Input;
import beast.core.Operator;
import beast.core.StateNode;
import beast.core.parameter.CompoundRealParameter;
import beast.core.util.Log;
import beast.util.Randomizer;


/**
 * Adds 2 children to a decision tree leaf node OR turns a 2-leaf cherry into a leaf
 *
 */
public class SplitNodeOperator extends Operator {
	
	
	final public Input<DecisionTree> treeInput = new Input<>("tree", "the tree", Input.Validate.REQUIRED);
	final public Input<DecisionTreeDistribution> treeDistrInput = new Input<>("dist", "the tree distribution", Input.Validate.REQUIRED);

	DecisionTree tree;
	DecisionTreeDistribution dist;
	
	
	@Override
	public void initAndValidate() {
		this.tree = treeInput.get();
		this.dist = treeDistrInput.get();
	}

	@Override
	public double proposal() {
		
		double logHR = 0;
		int nleaves = tree.getLeafCount();
		
		// Expand or shrink?
		boolean expand = Randomizer.nextBoolean();
		if (expand) {
			
			// Sample a leaf to expand
			int nodeNum = Randomizer.nextInt(nleaves);
			DecisionNode parent = tree.getNode(nodeNum);
			if (!parent.isLeaf()) {
				Log.warning("Dev error @SplitNodeOperator: this node is not a leaf");
				System.exit(0);
			}
			
			// Create 2 new nodes
			DecisionNode trueChild = this.dist.newNode();
			DecisionNode falseChild = this.dist.newNode();
			parent.setTrueChild(trueChild);
			parent.setFalseChild(falseChild);
			
			
			// Relabel the tree
			tree.listNodes();
			
			
			
		} else {
			
			// Cannot shrink a 1 node tree
			if (nleaves == 0) return Double.NEGATIVE_INFINITY;
			
		}
		
		
		// Update the parameter dimensions
		this.dist.updateDimensions();
		
		
		return logHR;
	}

	

    @Override
    public List<StateNode> listStateNodes() {
    	List<StateNode> stateNodes = new ArrayList<StateNode>(); 
    	stateNodes.add(this.tree);
    	stateNodes.addAll(this.dist.getStateNodes());
    	return stateNodes;
    }
    

	
}
