package poetry.decisiontree;

import java.util.ArrayList;
import java.util.List;

import beast.core.BEASTInterface;
import beast.core.Input;
import beast.core.Operator;
import beast.core.StateNode;
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
	
	
	final public Input<DecisionTreeInterface> treeInput = new Input<>("tree", "the tree", Input.Validate.REQUIRED);
	final public Input<DecisionTreeDistribution> treeDistrInput = new Input<>("dist", "the tree distribution", Input.Validate.REQUIRED);
	
	final public Input<RealParameter> interceptInput = new Input<>("intercept", "Regression intercepts at the leaves", Validate.OPTIONAL);
	final public Input<RealParameter> slopeInput = new Input<>("slope", "Regression slopes at the leaves", Validate.OPTIONAL);
	final public Input<IntegerParameter> attributePointerInput = new Input<>("pointer", "points to attributes", Validate.OPTIONAL);
	final public Input<RealParameter> splitPointInput = new Input<>("split", "split point for numeric attributes", Validate.OPTIONAL);

	
	final public Input<Double> maintainOrderInput = new Input<>("maintain", "probability of maintaining parameter ordering", 1.0);
	final public Input<Double> extendInput = new Input<>("extend", "probability of iteratively extending the split by 1", 0.5);
	
	
	@Override
	public void initAndValidate() {
	}

	@Override
	public double proposal() {
		
		DecisionTreeInterface treeI = treeInput.get(this);
		DecisionTreeDistribution dist = treeDistrInput.get();
		
		// Sample a tree (if random forest)
		DecisionTree tree = treeI.editTree(this);
		double logHR = 0;
		
		
		long numIterations = 1 + Randomizer.nextGeometric(1 - this.extendInput.get());
		
		
		
		for (int i = 0; i < numIterations; i ++) {
			
			// Expand or shrink?
			boolean expand = Randomizer.nextBoolean();
			
			int nleaves = tree.getLeafCount();
			
			if (expand) {
				
				// Cannot expand a max-sized tree
				if (tree.getLeafCount() >= dist.getMaxLeafCount()) {
					//Log.warning("op too big");
					return Double.NEGATIVE_INFINITY;
				}
				
				
				// Sample a leaf to expand
				int nodeIndex = Randomizer.nextInt(nleaves);
				DecisionNode parent = tree.getNode(nodeIndex);
				if (!parent.isLeaf()) {
					Log.warning("Dev error @SplitNodeOperator: this node is not a leaf");
					System.exit(0);
				}
				
				// Create 2 new nodes
				DecisionNode trueChild = dist.newNode(tree.getTreeNum());
				DecisionNode falseChild = dist.newNode(tree.getTreeNum());
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
			if (Randomizer.nextDouble() < this.maintainOrderInput.get()) this.reorderParameters(tree, nleaves);
			
		}
		
		
		//Log.warning("Expanded " + expand + " " + numIterations + " times");
		return logHR;
		
	}
	
	

	
	/**
	 * Reorders the elements within a parameter vector to account for the moving around of tree node indices
	 * @param tree
	 * @param nleavesBefore
	 */
	protected void reorderParameters(DecisionTree tree, int nleavesBefore) {
		
		if (true) return;
		
		 
		if (slopeInput.get() != null && interceptInput.get() != null) {
			int nparamsPerTree = treeDistrInput.get().getMaxLeafCount() * treeDistrInput.get().getNumPredictors();
			int nrepeats = slopeInput.get().getDimension() / interceptInput.get().getDimension();
			if (nrepeats >= 1) this.reorganiseVector(slopeInput.get(this), nrepeats, nparamsPerTree, tree, true, nleavesBefore);
		}
		
		int nparamsPerTree = treeDistrInput.get().getMaxLeafCount();
		if (interceptInput.get() != null) this.reorganiseVector(interceptInput.get(this), 1, nparamsPerTree, tree, true, nleavesBefore);
		
		if (attributePointerInput.get() != null) this.reorganiseVector(attributePointerInput.get(this), 1, nparamsPerTree-1, tree, false, nleavesBefore);
		if (splitPointInput.get() != null) this.reorganiseVector(splitPointInput.get(this), 1, nparamsPerTree-1,  tree, false, nleavesBefore);
	}
	
	// Reorganise the vector elements so that the old values are pointed to by the new node numbering
	protected void reorganiseVector(Parameter param, int nrepeats, int nparamsPerTree, DecisionTree tree, boolean leaves, int nleavesBefore) {
		
		
		
		
		// Which tree?
		int start = tree.getTreeNum() * nparamsPerTree;
		int stop = start + nparamsPerTree;
		
		
		// Create empty null array
		Double[] newVals = new Double[param.getDimension()];
		boolean[] oldIndicesTaken = new boolean[param.getDimension()];
		
		// Reorganise the vector elements so that the old values are pointed to by the new node numbering
		List<DecisionNode> nodesAfter = tree.getNodes();
		
		int unitSize = param.getDimension() / nrepeats;
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
				//if (indexBefore >= tree.getNodeCount()) continue;
				//if (indexAfter >= tree.getNodeCount()) break;
			}
			
			
			if (indexBefore != -1 && paramIndexAfter >= 0 && paramIndexBefore >= 0) {
				
				
				paramIndexBefore += start;
				paramIndexAfter += start;
				
				// Preexisting node
				
				//Log.warning("a The value at " + indexBefore + "/" + paramIndexBefore + " will move to " + indexAfter);
				
				// Special case: slope can be multivariate 
				for (int rep = 0; rep < nrepeats; rep ++) {
					
					int paramIndexBeforeRep = paramIndexBefore + rep*unitSize;
					int paramIndexAfterRep = paramIndexAfter + rep*unitSize;
					double paramValBefore = param.getArrayValue(paramIndexBeforeRep);
					newVals[paramIndexAfterRep] = paramValBefore;
					
					// This index has been taken
					oldIndicesTaken[paramIndexBeforeRep] = true;
					
					
				}
				
			
			}
			
		}
		
		
		// Fill remaining values in order
		paramIndexBefore = start;
		for (paramIndexAfter = start; paramIndexAfter < stop; paramIndexAfter ++) {
			
			
			
			if (newVals[paramIndexAfter] == null) {
				
				// Find the first non-taken old index
				while(oldIndicesTaken[paramIndexBefore]) {
					
					
					
					paramIndexBefore++;
					
					if (paramIndexBefore >= oldIndicesTaken.length) {
						int x = 5;
					}
				}
				oldIndicesTaken[paramIndexBefore] = true;
				
				//Log.warning("b The value at " + paramIndexBefore + " will move to " + paramIndexAfter);
				newVals[paramIndexAfter] = param.getArrayValue(paramIndexBefore);
				//paramIndexBefore ++;
			}
		}
		
		//Log.warning("------------");
		

		// Transfer over
		for (int i = start; i < stop; i ++) {
			
			if (param instanceof RealParameter) {
				((RealParameter)param).setValue(i, newVals[i]);
			}
			
			else if (param instanceof IntegerParameter) {
				((IntegerParameter)param).setValue(i,  (int)(double)newVals[i]);
			}
			
		}
		

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
