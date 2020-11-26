package poetry.decisiontree;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.w3c.dom.Node;

import beast.core.Description;
import beast.core.Operator;
import beast.core.StateNode;
import weka.core.Instances;


@Description("A decision tree with regression at the leaves")
public class DecisionTree extends StateNode  {

	
	DecisionNode root;
	DecisionNode stored_root;
	List<DecisionNode> nodes;
	List<DecisionNode> stored_nodes;
	
	
	public void setRoot(DecisionNode root) {
		this.root = root;
		this.nodes = this.listNodes(this.root);
		this.updateNodeIndices(this.nodes);
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
	public void init(PrintStream out) {
	
		if (true) return;
		
        out.println("#NEXUS\n");
        out.println("Begin taxa;");
        out.println("\tDimensions ntax=" + this.getLeafCount() + ";");
        out.println("\t\tTaxlabels");
        printTaxa(this.root, out, this.getLeafCount());
        out.println("\t\t\t;");
        out.println("End;");

        out.println("Begin trees;");
        out.println("\tTranslate");
        printTranslate(this.root, out, this.getLeafCount());
        out.print(";");
		
	}
	
	
    public static void printTaxa(final DecisionNode node, final PrintStream out, final int nodeCount) {
        final List<String> translateLines = new ArrayList<>();
        printTranslate(node, translateLines, nodeCount);
        Collections.sort(translateLines);
        for (String line : translateLines) {
            line = line.substring(line.indexOf(" ", 5)).replace(',', ' ').trim();
            out.println("\t\t\t" + line);
        }
    }
	
    /** Loggable interface implementation follows **/

    /**
     * print translate block for NEXUS beast.tree file
     */
    public static void printTranslate(final DecisionNode node, final PrintStream out, final int nodeCount) {
        final List<String> translateLines = new ArrayList<>();
        printTranslate(node, translateLines, nodeCount);
        Collections.sort(translateLines);
        for (final String line : translateLines) {
            out.println(line);
        }
    }
    
    
    static public int taxaTranslationOffset = 1;
    
    /**
     * need this helper so that we can sort list of entries *
     */
    static void printTranslate(DecisionNode node, List<String> translateLines, int nodeCount) {
        if (node.isLeaf()) {
            final String nr = (node.getIndex() + taxaTranslationOffset) + "";
            String line = "\t\t" + "    ".substring(nr.length()) + nr + " ";
            if (node.getName().indexOf(' ') > 0) {
            	char c = node.getName().charAt(0);
            	if (c == '\"' || c == '\'') {
                	line += node.getName();
            	} else {
            		line += '\"' + node.getName() + "\"";
            	}
            } else {
            	line += node.getName();
            }
            if (node.getIndex() < nodeCount) {
                line += ",";
            }
            translateLines.add(line);
        } else {
            printTranslate(node.getTrueChild(), translateLines, nodeCount);
            printTranslate(node.getFalseChild(), translateLines, nodeCount);
        }
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
