package poetry.decisiontree;

import java.io.PrintStream;

import org.w3c.dom.Node;

import beast.core.Description;
import beast.core.Input;
import beast.core.Operator;
import beast.core.StateNode;
import beast.util.Randomizer;
import poetry.decisiontree.DecisionTreeDistribution.ResponseMode;
import weka.core.Instances;


@Description("A random forest with linear regression at the leaves")
public class RandomForest extends StateNode implements DecisionTreeInterface {
	
	
	final public Input<Integer> ntreesInput = new Input<>("ntrees", "Number of trees in the forest (constant)", 10);
	final public Input<Integer> nattrInput = new Input<>("nattr", "Number of attributes to randomly investigate per tree. Default: int(log_2(#predictors)+1)", 0);
	
	
	DecisionTree[] trees;
	DecisionTree[] storedTrees;
	int ntrees;
	int nattr;
	
	
	public RandomForest() {
		
	}
	
	
	@Override
	public void initAndValidate() {
		this.ntrees = ntreesInput.get();
		if (this.ntrees < 1) throw new IllegalArgumentException("Please ensure there is at least 1 tree in the forest");
		this.nattr = -1;
		this.trees = new DecisionTree[this.ntrees];
		this.storedTrees = new DecisionTree[this.ntrees];
		for (int i = 0; i < this.ntrees; i ++) {
			this.trees[i] = new DecisionTree(i);
		}
	}
	
	
	
	/**
	 * Calculates how many attributes will be used, given that there are n in total
	 * @param n
	 * @return
	 */
	@Override
	public void setNumAttributes(int n) {
		if (this.nattrInput.get() <= 0) {
			this.nattr = (int)(Math.log(n)/Math.log(2) + 1);
		}else {
			this.nattr = Math.min(n, this.nattrInput.get());
		}
	}
	
	@Override
	public int getNAttr() {
		return this.nattr;
	}
	
	
	/**
	 * Renumbers all nodes in the forest
	 */
	public void reset() {
		for (DecisionTree tree : this.trees) {
			tree.reset();
		}
	}
	
	
	@Override
    public void startEditing(final Operator operator) {
		hasStartedEditing = false;
		//for (DecisionTree tree : this.trees) {
			//tree.startEditing(operator);
		//}
        super.startEditing(operator);
    }
	
	

	/**
	 * Attempt to split the data down the forests in the tree
	 * If the split is invalid, will return false
	 * @param data
	 * @return
	 */
	public boolean splitData(Instances data) {
		
		boolean valid = true;
		
		// Split down each tree
		for (DecisionTree tree : this.trees) {
			valid = valid && tree.splitData(data);
		}
		return valid;
		
	}
	
	
	/**
	 * Sets the root at decision at index
	 * @param index
	 * @param tree
	 */
	public void setRoot(int index, DecisionNode root) {
		this.trees[index].setRoot(root);
	}
	
	
	/**
	 * Sets the initial state of the tree after the root has been set
	 */
	public void initTree(int index, Instances data) {
		this.trees[index].initTree(data);
	}
	

	
	@Override
	public DecisionTree[] getTrees() {
		return this.trees;
	}
	
	@Override
	public DecisionTree getTree(int i) {
		return this.trees[i];
	}
	
	/**
	 * The size of the forest
	 * @return
	 */
	@Override
	public int getForestSize() {
		return this.ntrees;
	}
	

	@Override
	public void init(PrintStream out) {
		
		
	}

	@Override
	public void close(PrintStream out) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int getDimension() {
		return this.ntrees;
	}

	@Override
	public double getArrayValue(int dim) {
		return 0;
	}

	

	@Override
	public void setEverythingDirty(boolean isDirty) {
		for (DecisionTree tree : this.trees) {
			tree.setEverythingDirty(isDirty);
		}
	}

	@Override
	public StateNode copy() {
		
		RandomForest copy = new RandomForest();
		DecisionTree[] copyTrees = new DecisionTree[this.ntrees];
		for (int i = 0; i < this.ntrees; i ++) {
			DecisionTree tree = this.trees[i];
			if (tree != null) tree = (DecisionTree) tree.copy();
			copyTrees[i] = tree;
		}
		
		copy.trees = copyTrees;
		copy.nattr = this.nattr;
		copy.ntrees = this.ntrees;
		
		return copy;
	}

	@Override
	public void assignTo(StateNode other) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void assignFrom(StateNode other) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void assignFromFragile(StateNode other) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void fromXML(Node node) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int scale(double scale) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	protected void store() {
		System.arraycopy(this.trees, 0, this.storedTrees, 0, this.trees.length);
		for (DecisionTree tree : this.trees) {
			tree.store();
		}
	}

	@Override
	public void restore() {
		DecisionTree[] tmp = this.storedTrees;
		this.storedTrees = this.trees;
		this.trees = tmp;
		for (DecisionTree tree : this.trees) {
			tree.restore();
		}
	}
	
	
	@Override
	public DecisionTree sampleTree() {
		
		// Randomly sample a tree
		int treeNum = Randomizer.nextInt(this.ntrees);
		return this.trees[treeNum];
	}


	@Override
	public DecisionTree editTree(Operator operator) {
		DecisionTree tree = this.sampleTree();
		tree.startEditing(operator);
		return tree;
	}


	@Override
	public StateNode getStateNode() {
		return this;
	}


	@Override
	public void setRegressionMode(ResponseMode mode) {
		//this.setRegressionMode(mode);
		
	}

}
