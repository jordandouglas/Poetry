package poetry.learning;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import beast.core.Input;
import beast.evolution.alignment.Alignment;
import beast.evolution.tree.Tree;
import poetry.decisiontree.DecisionNode;
import poetry.decisiontree.DecisionTree;
import poetry.decisiontree.DecisionTreeInterface.regressionMode;
import poetry.sampler.POEM;
import poetry.util.BEAST2Weka;
import weka.core.Instances;

public class BayesianDecisionTreeSampler extends WeightSampler {

	
	final public Input<Tree> treeInput = new Input<>("tree", "The phylogenetic tree (used for machine learning)", Input.Validate.REQUIRED);
	final public Input<Alignment> dataInput = new Input<>("data", "The alignment (used for machine learning)", Input.Validate.REQUIRED);
	
	final public Input<regressionMode> regressionInput = new Input<>("regression", "Regression model at the leaves", regressionMode.test, regressionMode.values());
	
	final public Input<List<ModelValue>> modelValuesInput = new Input<>("model", "List of models and their values -- for applying the decsion tree from the model", new ArrayList<>());
	
	
	
	@Override
	public void initAndValidate() {
		
		
		
		
	}

	@Override
	public void sampleWeights() throws Exception {
		
		
		// Model values
		List<ModelValue> modelValues = modelValuesInput.get();
		
		// Get the Weka Instance of this session
		Instances instances = BEAST2Weka.getInstance(dataInput.get(), treeInput.get(), this, modelValues);
				
		int dim = poems.size();
		double[] weights = new double[dim];
		DecisionTree[] decisionTrees = new DecisionTree[dim];
		
		// Validate: check all POEMS have tree files
		for (int j = 0; j < dim; j++) {
			
			POEM poem = poems.get(j);
			File decisionTreeFile = poem.getDecisionTreeFile();
			if (decisionTreeFile == null) {
				throw new Exception("Please specify the decision tree file ('trees' input) for " + poem.getID());
			}
			if (!decisionTreeFile.canRead()) {
				throw new Exception("Error locating decision tree file " + decisionTreeFile.getAbsolutePath());
			}
			
			// Read the tree file
			List<DecisionTree> trees = parseDecisionTrees(decisionTreeFile);
			
			// Take the last tree
			DecisionTree tree = trees.get(trees.size()-1);
			tree.setRegressionMode(regressionInput.get());
			decisionTrees[j] = tree;
			
			System.out.println(poem.getID() + ": " + tree.toString());
			
			
		}
		
		
		weights = this.getWeights(decisionTrees, instances);
		
		this.setWeights(weights);
		
	}
	
	
	
	protected double[] getWeights(DecisionTree[] trees, Instances inst) {
		
		
		// Get optimal weights from current state
		double[] weights = new double[trees.length];
		for (int j = 0; j < weights.length; j++) {
			DecisionNode leaf = trees[j].getLeaf(inst);
			POEM poem = this.poems.get(j);
			
			double slope = leaf.getSlope(0);
			double dim = DimensionalSampler.modifyDimension(poem.getID(), poem.getDim());
			double essVal = slope * dim;
			weights[j] = essVal;
			
		}
		
		
		return weights;
		
		
	}
	
	
	
	/**
	 * Attempts to optimise operator weights using the tree
	 * @param trees
	 * @param inst
	 * @return
	 */
	protected double[] optimise(DecisionTree[] trees, Instances inst) {
		
		
		return null;
		
	}
	
	
	/**
	 * Predict mean distance between fractional ESS and the ideal balance
	 * @param trees
	 * @param inst
	 * @return
	 */
	protected double predictPMean(DecisionTree[] trees, Instances inst) {
		
		// Predict fractional ESSes
		double essSum = 0;
		double[] ess = new double[trees.length];
		for (int j = 0; j < ess.length; j++) {
			DecisionNode leaf = trees[j].getLeaf(inst);
			POEM poem = this.poems.get(j);
			
			double slope = leaf.getSlope(0);
			double intercept = leaf.getIntercept();
			double weightDim = inst.get(0).value(inst.attribute(BEAST2Weka.getPoemWeightDimensionAttr(poem).name()));
			double dim = DimensionalSampler.modifyDimension(poem.getID(), poem.getDim());
			double weight = weightDim * dim;
			double essVal = intercept / (1 + slope/weight);
			essSum += essVal;
			ess[j] = essVal;
		}
		
		// Normalise
		double perfectFraction = 1.0 / trees.length;
		double pmean = 0;
		for (int j = 0; j < ess.length; j++) {
			ess[j] /= essSum;
			pmean += Math.pow(ess[j] - perfectFraction, 2) / ess.length;
		}
				
		return pmean;
	}
	
	
	
	/**
	 * Parse decision trees from a newick file
	 * @param newickFile
	 * @return
	 * @throws IOException
	 */
	public static List<DecisionTree> parseDecisionTrees(File newickFile) throws IOException {
		
		BufferedReader fin = new BufferedReader(new FileReader(newickFile));
		List<DecisionTree> trees = new ArrayList<>();
		
		// Parse trees from newick strings
		String str = null;
        while (fin.ready()) {
            str = fin.readLine();
            if (!str.matches("\\s*")) {
	            DecisionTree tree = new DecisionTree();
	            tree.fromNewick(str);
	            trees.add(tree);
            }
            
        }
		fin.close();  
		
		
		return trees;
	}
	
	
	
	

}
