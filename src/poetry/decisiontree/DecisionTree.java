package poetry.decisiontree;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Node;

import beast.core.Description;
import beast.core.StateNode;
import weka.core.Instances;


@Description("A decision tree with regression at the leaves")
public class DecisionTree extends StateNode  {

	
	DecisionNode root;
	List<DecisionNode> nodes;
	int numLeaves;
	
	
	public void setRoot(DecisionNode root) {
		this.root = root;
		this.listNodes();
	}
	
	
	/**
	 * Reset the node ordering
	 */
	public void listNodes() {
		
		// Post order traversal
		DecisionNode[] nodeArr = new DecisionNode[this.root.getNodeCount()];
		DecisionTree.getNodesPostOrder(this.root, nodeArr, 0);
		
		// Leaves first, then internal, then root
		this.numLeaves = 0;
		this.nodes = new ArrayList<>();
		for (DecisionNode node : nodeArr) {
			if (node.isLeaf()) {
				this.nodes.add(node);
				this.numLeaves++;
			}
		}
		for (DecisionNode node : nodeArr) {
			if (!node.isLeaf() && node.getParent() != null) this.nodes.add(node);
		}
		for (DecisionNode node : nodeArr) {
			if (!node.isLeaf() && node.getParent() == null) this.nodes.add(node);
		}
		
		
		// Set node indices
		for (int i = 0; i < this.nodes.size(); i ++) {
			this.nodes.get(i).setIndex(i);
		}
		
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
		// TODO Auto-generated method stub
		
	}

	@Override
	public StateNode copy() {
		// TODO Auto-generated method stub
		return null;
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
		// TODO Auto-generated method stub
		
	}

	@Override
	public void restore() {
		// TODO Auto-generated method stub
		
	}

	
	
    public static int getNodesPostOrder(final DecisionNode node, final DecisionNode[] nodes, int pos) {
    	if (!node.isLeaf()) {
	        for (final DecisionNode child : node.getChildren()) {
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
		return this.numLeaves;
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


}
