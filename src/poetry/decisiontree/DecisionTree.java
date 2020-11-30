package poetry.decisiontree;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.w3c.dom.Node;

import beast.core.Description;
import beast.core.Operator;
import beast.core.StateNode;
import beast.core.util.Log;
import beast.util.TreeParser;
import weka.core.Instances;


@Description("A decision tree with regression at the leaves")
public class DecisionTree extends StateNode  {

	
	DecisionNode root;
	DecisionNode stored_root;
	List<DecisionNode> nodes;
	List<DecisionNode> stored_nodes;
	DecisionSplit split;
	
	
	public void setRoot(DecisionNode root) {
		this.root = root;
		this.split = root.getSplit();
		this.reset();
	}
	
	
	/**
	 * Refreshes the node numbering list
	 */
	public void reset() {
		this.nodes = this.listNodes(this.root);
		this.updateNodeIndices(this.nodes);
	}
	
	
	protected void updateNodeIndices(List<DecisionNode> nodes) {
		for (int i = 0; i < nodes.size(); i ++) {
			nodes.get(i).setIndex(i);
		}
	}
	
	
	
	/**
	 * Reset the node ordering
	 */
	protected List<DecisionNode> listNodes(DecisionNode theRoot) {
		
		List<DecisionNode> toReturn = new ArrayList<>();
		
		// Post order traversal
		DecisionNode[] nodeArr = new DecisionNode[theRoot.getNodeCount()];
		DecisionTree.getNodesPostOrder(theRoot, nodeArr, 0);
		
		// Leaves first, then internal, then root
		for (DecisionNode node : nodeArr) {
			if (node.isLeaf()) {
				toReturn.add(node);
			}
		}
		for (DecisionNode node : nodeArr) {
			if (!node.isLeaf() && node.getParent() != null) toReturn.add(node);
		}
		for (DecisionNode node : nodeArr) {
			if (!node.isLeaf() && node.getParent() == null) toReturn.add(node);
		}
		
		return toReturn;
		
		
	}
	
	@Override
	public String toString() {
        return root.toString();
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
		return this.root.getNodeCount();
	}

	@Override
	public double getArrayValue(int dim) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void initAndValidate() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setEverythingDirty(boolean isDirty) {
		
		
	}

	@Override
	public StateNode copy() {
		DecisionTree tree = new DecisionTree();
		tree.setID(this.getID());
		tree.setRoot(this.root.copy());
		return tree;
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

	/**
    * Reconstruct tree from XML fragment in the form of a DOM node *
    */
	@Override
	public void fromXML(Node node) {
		
		//Log.warning(this.root.);
		
		
		final String newick = node.getTextContent();
        final TreeParser parser = new TreeParser();
        try {
            parser.thresholdInput.setValue(1e-10, parser);
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        try {
            parser.offsetInput.setValue(0, parser);
            beast.evolution.tree.Node rootNode = parser.parseNewick(newick);
            this.root.parseFromNode(rootNode);
            this.setRoot(this.root);
        } catch (Exception e) {
            // TODO Auto-generated catch block
        	Log.warning(newick);
            e.printStackTrace();
        }
		
	}

	@Override
	public int scale(double scale) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	protected void store() {
		this.stored_root = this.root.copy();
		this.stored_nodes = this.listNodes(stored_root);
		this.updateNodeIndices(this.stored_nodes);
	}

	@Override
	public void restore() {
		
		List<DecisionNode> tmp = this.stored_nodes;
		this.stored_nodes = this.nodes;
		this.nodes = tmp;
		
		DecisionNode tmp2 = this.stored_root;
		this.stored_root = this.root;
		this.root = tmp2;
		
		//hasStartedEditing = false;
	}

	
	@Override
    public void startEditing(final Operator operator) {
		hasStartedEditing = false;
        super.startEditing(operator);
    }
	
	
    public static int getNodesPostOrder(final DecisionNode node, final DecisionNode[] nodes, int pos) {
    	if (!node.isLeaf()) {
	        for (final DecisionNode child : node.getDecisionChildren()) {
	            pos = getNodesPostOrder(child, nodes, pos);
	        }
    	}
        nodes[pos] = node;
        return pos + 1;
    }

    /**
     * Number of leaves
     * @return
     */
	public int getLeafCount() {
		return (nodes.size()+1) / 2;
	}


	/**
	 * Total number of nodes including the root
	 * @return
	 */
	public int getNodeCount() {
		return nodes.size();
	}


	public DecisionNode getNode(int nodeNum) {
		return this.nodes.get(nodeNum);
	}

	/**
	 * Attempt to split the data down the tree
	 * If the split is invalid, will return false
	 * @param data
	 * @return
	 */
	public boolean splitData(Instances data) {
		
		// Reset the data
		for (DecisionNode node : this.nodes) {
			node.resetData();
		}
		return this.root.splitData(data);
	}


	/**
	 * Return list of nodes
	 * @return
	 */
	public List<DecisionNode> getNodes() {
		return this.nodes;
	}




	/**
	 * Get the root
	 * @return
	 */
	public DecisionNode getRoot() {
		return this.root;
	}





}
