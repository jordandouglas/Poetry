package poetry.decisiontree;

import java.util.ArrayList;
import java.util.List;

import beast.core.Description;
import beast.core.Input;
import beast.util.Randomizer;

@Description("Moves a subtree of a decision tree from an internal node to a leaf node")
public class DecisionExchange extends SplitNodeOperator {

	
	final public Input<Boolean> wideInput = new Input<>("wide", "Wide exchange?", false);

	@Override
	public void initAndValidate() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public double proposal() {
		
		double logHR = 0;
		
		DecisionTreeInterface treeI = treeInput.get(this);
		//DecisionTreeDistribution dist = treeDistrInput.get();
		
		// Sample a tree (if random forest)
		DecisionTree tree = treeI.editTree(this);
		
		boolean wide = wideInput.get();
		
		int nleaves = tree.getLeafCount();
		
		// Sample a parental node (excluding root)
		int nParentsBefore = tree.getNodeCount() - tree.getLeafCount() - 1;
		if (nParentsBefore <= 0) return Double.NEGATIVE_INFINITY;
		DecisionNode parentBefore = tree.getNode(Randomizer.nextInt(nParentsBefore) + tree.getLeafCount());
		
		
		int nLeavesBefore;
		DecisionNode moveTo;
		if (wide) {
		
			// Sample a leaf that is NOT within the subtree
			List<DecisionNode> leavesBefore = new ArrayList<>();
			for (DecisionNode node : tree.getNodes()) {
				if (!node.isLeaf()) continue;
				if (parentBefore.isAncestorOf(node)) continue;
				leavesBefore.add(node);
			}
			if (leavesBefore.isEmpty()) return Double.NEGATIVE_INFINITY;
			nLeavesBefore = leavesBefore.size();
			moveTo = leavesBefore.get(Randomizer.nextInt(nLeavesBefore));
		
		
		}else {
			
			// Move it to the parents sibling 
			nLeavesBefore = 1;
			moveTo = parentBefore.getParent();
			if (parentBefore.isTrueChild()) moveTo = moveTo.getFalseChild();
			else moveTo = moveTo.getTrueChild();
			
		}
		
		
		// Move the subtree
		DecisionNode trueChild = parentBefore.getTrueChild();
		DecisionNode falseChild = parentBefore.getFalseChild();
		parentBefore.removeChildren();
		if (Randomizer.nextBoolean()) {
			moveTo.setTrueChild(trueChild);
			moveTo.setFalseChild(falseChild);
		}else {
			moveTo.setTrueChild(falseChild);
			moveTo.setFalseChild(trueChild);
		}
		
		
		
		// Renumber the nodes
		tree.reset();

		// Reverse transition: how many parent nodes are there now?
		int nParentsAfter = tree.getNodeCount() - tree.getLeafCount() - 1;
		
		
		int nLeavesAfter = 0;
		if (wide) {
			
			// Reverse transition: how many leaves outside of the subtree are there now?
			for (DecisionNode node : tree.getNodes()) {
				if (!node.isLeaf()) continue;
				if (moveTo.isAncestorOf(node)) continue;
				nLeavesAfter++;
			}
			
		}else {
			nLeavesAfter = 1;
		}
		
		
		// Hastings ratio
		double pFwd = -Math.log(nParentsBefore) -Math.log(nLeavesBefore);
		double pBck = -Math.log(nParentsAfter) -Math.log(nLeavesAfter);
		logHR = pBck - pFwd;
		
		
		// Reorder parameters
		if (Randomizer.nextDouble() < this.maintainOrderInput.get()) this.reorderParameters(tree, nleaves);
		
		
		return logHR;
	}
	
	
	
}
