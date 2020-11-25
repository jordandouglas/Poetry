package poetry.decisiontree;

import java.util.ArrayList;
import java.util.List;

import beast.core.Input;
import beast.core.Operator;
import beast.core.StateNode;
import beast.core.parameter.RealParameter;
import beast.core.util.Log;
import beast.util.Randomizer;


/**
 * Adds 2 children to a decision tree leaf node OR turns a 2-leaf cherry into a leaf
 *
 */
public class SplitNodeOperator extends Operator {
	
	
	final public Input<DecisionTree> treeInput = new Input<>("tree", "the tree", Input.Validate.REQUIRED);
	final public Input<DecisionTreeDistribution> treeDistrInput = new Input<>("dist", "the tree distribution", Input.Validate.REQUIRED);

	
	
	@Override
	public void initAndValidate() {
	}

	@Override
	public double proposal() {
		
		DecisionTree tree = treeInput.get(this);
		DecisionTreeDistribution dist = treeDistrInput.get();
		
		double logHR = 0;
		int nleaves = tree.getLeafCount();
		
		// Expand or shrink?
		boolean expand = Randomizer.nextBoolean();
		if (expand) {
			
			// Cannot expand a max-sized tree
			if (tree.getLeafCount() >= dist.getMaxLeafCount()) return Double.NEGATIVE_INFINITY;
			
			
			// Sample a leaf to expand
			int nodeNum = Randomizer.nextInt(nleaves);
			DecisionNode parent = tree.getNode(nodeNum);
			if (!parent.isLeaf()) {
				Log.warning("Dev error @SplitNodeOperator: this node is not a leaf");
				System.exit(0);
			}
			
			// Create 2 new nodes
			DecisionNode trueChild = dist.newNode();
			DecisionNode falseChild = dist.newNode();
			parent.setTrueChild(trueChild);
			parent.setFalseChild(falseChild);
			
			
		} else {
			
			// Cannot shrink a 1 node tree
			if (nleaves == 0) return Double.NEGATIVE_INFINITY;
			
			
			// Sample a cherry to shrink
			List<DecisionNode> cherries = new ArrayList<>();
			for (DecisionNode node : tree.getNodes()){
				if (node.isCherry()) cherries.add(node);
			}
			if (cherries.size() == 0) return Double.NEGATIVE_INFINITY;
			int nodeNum = Randomizer.nextInt(cherries.size());
			DecisionNode parent = cherries.get(nodeNum);
			
			
			// Delete its children
			parent.removeChildren();
			
		}
		
		// Relabel the tree
		tree.reset();
		return logHR;
		
	}

	
	/*
    @Override
    public List<StateNode> listStateNodes() {
    	List<StateNode> stateNodes = new ArrayList<StateNode>(); 
    	stateNodes.add(treeInput.get());
    	//stateNodes.addAll(treeDistrInput.get().getStateNodes());
    	return stateNodes;
    }
    */
    
    @Override
    public void accept() {
    	super.accept();	
    }
    
    @Override
    public void reject() {
    	super.reject();	
    }
	
    @Override
    public void reject(int reason) {
    	super.reject(reason);	
    }
    
}
