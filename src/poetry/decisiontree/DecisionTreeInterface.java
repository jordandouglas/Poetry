package poetry.decisiontree;

import java.io.PrintStream;

import beast.core.Input;
import beast.core.Operator;
import beast.core.StateNode;
import weka.core.Instances;

public interface DecisionTreeInterface  {

	
	// Tree initialisation method
	enum initTree {
		root, reptree
	}
	
	
	// Regression at leaves
	public enum regressionMode {
		linear, logistic, test, log
	}
	
	
	// Distribution
	public enum regressionDistribution {
		normal, student
	}
	
	
	final public Input<initTree> initInput = new Input<>("init", "Method for initialising decision tree(s)", initTree.root, initTree.values());
	final public Input<regressionMode> regressionInput = new Input<>("regression", "Regression model at the leaves", regressionMode.linear, regressionMode.values());
	
	final public Input<regressionDistribution> distributionInput = new Input<>("dist", "Regression distribution at the leaves", regressionDistribution.student, regressionDistribution.values());
	
	
	/**
	 * Id of this object
	 * @return
	 */
	public String getID();
	
	
	/**
	 * Returns a random tree
	 * @return
	 */
	public DecisionTree sampleTree();
	
	
	/**
	 * Returns a random tree and start editing it
	 * @return
	 */
	public DecisionTree editTree(Operator operator);
	
	
	/**
	 * Resets all node labellings
	 */
	public void reset();


	
	/**
	 * Number of trees
	 * @return
	 */
	public int getForestSize();


	/**
	 * Array of trees
	 * @return
	 */
	public DecisionTree[] getTrees();
	
	
	
	public void init(PrintStream out);


	/**
	 * Split the data
	 * @param data
	 * @return
	 */
	public boolean splitData(Instances data);


	
	/**
	 * Returns tree at index i
	 * @param i
	 * @return
	 */
	public DecisionTree getTree(int i);


	/**
	 * Number of covariates available in the data
	 * @param nattr
	 */
	public void setNumAttributes(int nattr);


	/**
	 * Number of covariates being used by the tree
	 * @return
	 */
	public int getNAttr();


	public StateNode getStateNode();
	
}
