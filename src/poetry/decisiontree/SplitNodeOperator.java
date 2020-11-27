package poetry.decisiontree;

import java.util.ArrayList;
import java.util.List;

import beast.core.Input;
import beast.core.Operator;
import beast.core.Input.Validate;
import beast.core.parameter.IntegerParameter;
import beast.core.parameter.Parameter;
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
	
	final public Input<RealParameter> interceptInput = new Input<>("intercept", "Regression intercepts at the leaves", Validate.OPTIONAL);
	final public Input<RealParameter> slopeInput = new Input<>("slope", "Regression slopes at the leaves", Validate.OPTIONAL);
	final public Input<IntegerParameter> attributePointerInput = new Input<>("pointer", "points to attributes", Validate.OPTIONAL);
	final public Input<RealParameter> splitPointInput = new Input<>("split", "split point for numeric attributes", Validate.OPTIONAL);

	
	
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
			int nodeIndex = Randomizer.nextInt(nleaves);
			DecisionNode parent = tree.getNode(nodeIndex);
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
		
		
		// Reorder parameters
		if (slopeInput.get() != null) this.reorganiseVector(slopeInput.get(this), tree, true, nleaves);
		if (interceptInput.get() != null) this.reorganiseVector(interceptInput.get(this), tree, true, nleaves);
		if (attributePointerInput.get() != null) this.reorganiseVector(attributePointerInput.get(this), tree, false, nleaves);
		if (splitPointInput.get() != null) this.reorganiseVector(splitPointInput.get(this), tree, false, nleaves);
		
		
		return logHR;
		
	}
	
	// Reorganise the vector elements so that the old values are pointed to by the new node numbering
	protected void reorganiseVector(Parameter param, DecisionTree tree, boolean leaves, int nleavesBefore) {
		
		
		// Create empty null array
		Double[] newVals = new Double[param.getDimension()];
		boolean[] oldIndicesTaken = new boolean[param.getDimension()];
		
		// Reorganise the vector elements so that the old values are pointed to by the new node numbering
		List<DecisionNode> nodesAfter = tree.getNodes();
		
		
		int paramIndexBefore, paramIndexAfter;
		for (int indexAfter = 0; indexAfter < nodesAfter.size(); indexAfter ++) {
			int indexBefore = nodesAfter.get(indexAfter).getLastIndex();
			
			
			
			
			// Leaves only
			if (leaves) {
				paramIndexBefore = indexBefore;
				paramIndexAfter = indexAfter;
				if (indexBefore >= tree.getLeafCount()) continue;
				if (indexAfter >= tree.getLeafCount()) break;
			}
			
			
			// Non leaves only (subtract leaf count)
			else {
				//Log.warning("leaves " + nleavesBefore + " -> " + tree.getLeafCount());
				paramIndexBefore = indexBefore - nleavesBefore;
				paramIndexAfter = indexAfter - tree.getLeafCount();
				if (indexBefore >= tree.getNodeCount()) continue;
				if (indexAfter >= tree.getNodeCount()) break;
			}
			
			
			if (indexBefore != -1 && paramIndexAfter >= 0 && paramIndexBefore >= 0) {
				
				// Preexisting node
				
				//Log.warning("a The value at " + indexBefore + "/" + paramIndexBefore + " will move to " + indexAfter);
				double paramValBefore = param.getArrayValue(paramIndexBefore);
				newVals[paramIndexAfter] = paramValBefore;
				
				
				// This index has been taken
				oldIndicesTaken[paramIndexBefore] = true;
			}
			
		}
		
		
		// Fill remaining values in order
		paramIndexBefore = 0;
		for (paramIndexAfter = 0; paramIndexAfter < newVals.length; paramIndexAfter ++) {
			if (newVals[paramIndexAfter] == null) {
				
				
				// Find the first non-taken old index
				while(oldIndicesTaken[paramIndexBefore]) {
					paramIndexBefore++;
				}
				
				
				//Log.warning("b The value at " + paramIndexBefore + " will move to " + paramIndexAfter);
				newVals[paramIndexAfter] = param.getArrayValue(paramIndexBefore);
				paramIndexBefore ++;
			}
		}
		
		//Log.warning("------------");
		

		// Transfer over
		for (int i = 0; i < newVals.length; i ++) {
			if (param instanceof RealParameter) {
				((RealParameter)param).setValue(i, newVals[i]);
			}
			
			else if (param instanceof IntegerParameter) {
				((IntegerParameter)param).setValue(i,  (int)(double)newVals[i]);
			}
		}
		
		
		
		// If there are any elements that corresponded to nodes in the old state but they don't any more, then move from the end (in the same order)
		/*
		int endOfTree = leaves ? tree.getLeafCount() : tree.getLeafCount() - 1;
		for (int i = 0; i < newVals.length; i ++) {
			if (newVals[i] == null) {
				double oldVal = param.getArrayValue(endOfTree);
				newVals[i] = oldVal;
				endOfTree++;
			}
		}
		*/
		
		
		// Do the swapping
			
		
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
