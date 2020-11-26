package poetry.decisiontree;

import beast.core.Description;
import beast.core.Input;
import beast.core.Operator;
import beast.util.Randomizer;

@Description("Removes the root and one of its leaf-children, or adds a new root with one leaf child")
public class Reroot extends Operator {

	
	final public Input<DecisionTree> treeInput = new Input<>("tree", "the tree", Input.Validate.REQUIRED);
	final public Input<DecisionTreeDistribution> treeDistrInput = new Input<>("dist", "the tree distribution", Input.Validate.REQUIRED);

	@Override
	public void initAndValidate() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public double proposal() {
		
		double logHR = 0;
		
		DecisionTree tree = treeInput.get(this);
		DecisionTreeDistribution dist = treeDistrInput.get();
		DecisionNode root = tree.getRoot();
		
		
		
		// Expand or shrink?
		boolean expand = Randomizer.nextBoolean();
		if (expand) {
			
			// Cannot expand a max-sized tree
			if (tree.getLeafCount() >= dist.getMaxLeafCount()) return Double.NEGATIVE_INFINITY;
			
			// New root
			DecisionNode newRoot = dist.newNode();
			DecisionNode newLeaf = dist.newNode();
			
			// New child is true or false?
			boolean trueChild = Randomizer.nextBoolean();
			if (trueChild) {
				newRoot.setTrueChild(newLeaf);
				newRoot.setFalseChild(root);
			}else {
				newRoot.setFalseChild(newLeaf);
				newRoot.setTrueChild(root);
			}
			tree.setRoot(newRoot);
			
			
		} else {
			
			// Delete the root
			if (root.isLeaf()) return Double.NEGATIVE_INFINITY;
			
			// Has at least 1 leaf child?
			int nLeafChildren = 0;
			if (root.getTrueChild().isLeaf()) nLeafChildren++;
			if (root.getFalseChild().isLeaf()) nLeafChildren++;
			if (nLeafChildren == 0) return Double.NEGATIVE_INFINITY;
			
			// Which child to delete?
			boolean deleteTrueChild = true;
			if (nLeafChildren == 2) deleteTrueChild = Randomizer.nextBoolean();
			else if (root.getFalseChild().isLeaf()) deleteTrueChild = false;
			
			// New root
			DecisionNode newRoot = deleteTrueChild ? root.getFalseChild() : root.getTrueChild();
			root.removeChildren();
			tree.setRoot(newRoot);
			
			
		}
		

		
		// Renumber the tree
		tree.reset();
		
		
		return logHR;
	}
}
