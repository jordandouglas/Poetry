package poetry.learning;

import java.util.Collections;
import java.util.Random;
import java.util.Vector;

import beast.core.util.Log;
import poetry.util.WekaUtils;
import weka.classifiers.evaluation.RegressionAnalysis;
import weka.classifiers.functions.LinearRegression;
import weka.classifiers.functions.SimpleLinearRegression;
import weka.classifiers.trees.RandomTree;
import weka.core.Attribute;
import weka.core.Capabilities;
import weka.core.Capabilities.Capability;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Utils;


/**
 * Extension of standard Weka RandomTree but each leaf is a linear regression model between the class and 
 * a specified numerical attribute
 * @author jdou557
 *
 */
public class RandomLinearTree extends RandomTree {

	
	 /** For serialization */
	private static final long serialVersionUID = 1301481761133136252L;
	
	/** The Tree object */
	protected LinearTree m_Tree = null;
	
	/** The attribute to use as a linear predictor at the leaves */
	protected int m_xPredictorNum = -1;
	protected String m_xPredictorName = "";
	
	/** Minimum number of instances for leaf. */
	protected double m_MinNum = 40.0;

    
	protected class LinearTree extends Tree {

		
	    /** The subtrees appended to this tree. */
	    protected LinearTree[] m_Successors;
		
	    /** For serialization */
		private static final long serialVersionUID = 2467164046376174619L;
		

	    /**
	     * Computes size of the tree.
	     * 
	     * @return the number of nodes
	     */
	    public int numNodes() {

	      if (m_Attribute == -1) {
	        return 1;
	      } else {
	        int size = 1;
	        for (Tree m_Successor : m_Successors) {
	          size += m_Successor.numNodes();
	        }
	        return size;
	      }
	    }

		
		 /**
	     * Outputs one node for graph.
	     * 
	     * @param text the buffer to append the output to
	     * @param num unique node id
	     * @return the next node id
	     * @throws Exception if generation fails
	     */
		@Override
	    public int toGraph(StringBuffer text, int num) throws Exception {

	      int maxIndex = Utils.maxIndex(m_ClassDistribution);
	      String classValue =
	        m_Info.classAttribute().isNominal() ? m_Info.classAttribute().value(
	          maxIndex) : Utils.doubleToString(m_ClassDistribution[0],
	          getNumDecimalPlaces());

	      num++;
	      if (m_Attribute == -1) {
	        text.append("N" + Integer.toHexString(hashCode()) + " [label=\"" + num
	          + ": " + classValue + "\"" + "shape=box]\n");
	      } else {
	        text.append("N" + Integer.toHexString(hashCode()) + " [label=\"" + num
	          + ": " + classValue + "\"]\n");
	        for (int i = 0; i < m_Successors.length; i++) {
	          text.append("N" + Integer.toHexString(hashCode()) + "->" + "N"
	            + Integer.toHexString(m_Successors[i].hashCode()) + " [label=\""
	            + m_Info.attribute(m_Attribute).name());
	          if (m_Info.attribute(m_Attribute).isNumeric()) {
	            if (i == 0) {
	              text.append(" < "
	                + Utils.doubleToString(m_SplitPoint, getNumDecimalPlaces()));
	            } else {
	              text.append(" >= "
	                + Utils.doubleToString(m_SplitPoint, getNumDecimalPlaces()));
	            }
	          } else {
	            text.append(" = " + m_Info.attribute(m_Attribute).value(i));
	          }
	          text.append("\"]\n");
	          num = m_Successors[i].toGraph(text, num);
	        }
	      }

	      return num;
	    }

		
	    /**
	     * Outputs a leaf.
	     * 
	     * @return the leaf as string
	     * @throws Exception if generation fails
	     */
		@Override
	    protected String leafString() throws Exception {
			return " : (" + m_Distribution[0] + " + " + m_Distribution[1] + "w + " + m_Distribution[2] + "w^2) : " + m_Distribution[3];
	    }

		
	    /**
	     * Recursively outputs the tree.
	     * 
	     * @param level the current level of the tree
	     * @return the generated subtree
	     */
		@Override
	    protected String toString(int level) {

	      try {
	        StringBuffer text = new StringBuffer();

	        if (m_Attribute == -1) {

	          // Output leaf info
	          return leafString();
	        } else if (m_Info.attribute(m_Attribute).isNominal()) {

	          // For nominal attributes
	          for (int i = 0; i < m_Successors.length; i++) {
	            text.append("\n");
	            for (int j = 0; j < level; j++) {
	              text.append("|   ");
	            }
	            text.append(m_Info.attribute(m_Attribute).name() + " = "
	              + m_Info.attribute(m_Attribute).value(i));
	            text.append(m_Successors[i].toString(level + 1));
	          }
	        } else {

	          // For numeric attributes
	          text.append("\n");
	          for (int j = 0; j < level; j++) {
	            text.append("|   ");
	          }
	          text.append(m_Info.attribute(m_Attribute).name() + " < "
	            + Utils.doubleToString(m_SplitPoint, getNumDecimalPlaces()));
	          text.append(m_Successors[0].toString(level + 1));
	          text.append("\n");
	          for (int j = 0; j < level; j++) {
	            text.append("|   ");
	          }
	          text.append(m_Info.attribute(m_Attribute).name() + " >= "
	            + Utils.doubleToString(m_SplitPoint, getNumDecimalPlaces()));
	          text.append(m_Successors[1].toString(level + 1));
	        }

	        return text.toString();
	      } catch (Exception e) {
	        e.printStackTrace();
	        return "RandomLinearTree: tree can't be printed";
	      }
	    }
		

		
		 /**
	     * Computes class distribution of an instance using the decision tree.
	     * 
	     * @param instance the instance to compute the distribution for
	     * @return the computed class distribution
	     * @throws Exception if computation fails
	     */
		@Override
	    public double[] distributionForInstance(Instance instance) throws Exception {

			
			
	      double[] returnedDist = null;

	      if (m_Attribute > -1) {

	        // Node is not a leaf
	        if (instance.isMissing(m_Attribute)) {

	          // Value is missing
	          returnedDist = new double[m_Info.numClasses()];

	          // Split instance up
	          for (int i = 0; i < m_Successors.length; i++) {
	            double[] help = m_Successors[i].distributionForInstance(instance);
	            if (help != null) {
	              for (int j = 0; j < help.length; j++) {
	                returnedDist[j] += m_Prop[i] * help[j];
	              }
	            }
	          }
	        } else if (m_Info.attribute(m_Attribute).isNominal()) {

	        	
	        	double val = instance.value(m_Attribute);
	        	
	          // For nominal attributes
	          returnedDist =
	            m_Successors[(int) instance.value(m_Attribute)]
	              .distributionForInstance(instance);
	        } else {

	          // For numeric attributes
	          if (instance.value(m_Attribute) < m_SplitPoint) {
	            returnedDist = m_Successors[0].distributionForInstance(instance);
	          } else {
	            returnedDist = m_Successors[1].distributionForInstance(instance);
	          }
	        }
	      }

	      // Node is a leaf or successor is empty?
	      if ((m_Attribute == -1) || (returnedDist == null)) {

	        // Is node empty?
	        if (m_ClassDistribution == null || m_Distribution == null) {
	        	return null;
	        }

	        
	        return new double[] { getValueFromModel(instance, m_xPredictorNum, m_Distribution)};
	        
	        // Else return normalized distribution
	        //double[] normalizedDistribution = m_ClassDistribution.clone();
	       // if (m_Info.classAttribute().isNominal()) {
	         // Utils.normalize(normalizedDistribution);
	       // }
	       // return normalizedDistribution;
	      } else {
	        return returnedDist;
	      }
	    }

		

	    /**
	     * Computes numeric class distribution for an attribute
	     * 
	     * @param props
	     * @param dists
	     * @param att
	     * @param subsetWeights
	     * @param data
	     * @param vals
	     * @return
	     * @throws Exception if a problem occurs
	     */
	    protected double numericDistribution(double[][] props, double[][][] dists,
	      int att, double[][] subsetWeights, Instances data, double[] vals, double minLeafSize)
	      throws Exception {

	      double splitPoint = Double.NaN;
	      Attribute attribute = data.attribute(att);
	      double[][] dist = null;
	      
	      // Split
	      Instances[] dataSplits = null;
	      
	      // Weights
	      double[] sumOfWeights = null;
	      
	      // Y (class)
	      double[] sumsY = null;
	      double[] sumSquaredY = null;
	      
	      
	      // X (predictor)
	      double[] sumsX = null;
	      double[] sumSquaredX = null;
	      
	      // XY
	      double[] sumsXY = null;
	      
	      double totalSumY = 0, totalSumSquaredY = 0, totalSumX = 0, totalSumSquaredX = 0, totalSumXY = 0, totalSumOfWeights = 0;
	      int indexOfFirstMissingValue = data.numInstances();

	      if (attribute.isNominal()) {
	        sumsY = new double[attribute.numValues()];
	        sumSquaredY = new double[attribute.numValues()];
	        sumsX = new double[attribute.numValues()];
	        sumSquaredX = new double[attribute.numValues()];
	        sumsXY = new double[attribute.numValues()];
	        sumOfWeights = new double[attribute.numValues()];
	        dataSplits = new Instances[attribute.numValues()];
	        int attVal;
	        
	        double x,y;
	        for (int i = 0; i < data.numInstances(); i++) {
	          Instance inst = data.instance(i);
	          if (inst.isMissing(att)) {

	            // Skip missing values at this stage
	            if (indexOfFirstMissingValue == data.numInstances()) {
	              indexOfFirstMissingValue = i;
	            }
	            continue;
	          }

	          y = inst.classValue();
	          attVal = (int) inst.value(att);
	          sumsY[attVal] += y * inst.weight();
	          sumSquaredY[attVal] += y * y * inst.weight();
	          sumOfWeights[attVal] += inst.weight();
	          
	          
	          // X-predictor value
	          if (m_xPredictorNum >= 0) {
	        	  x = inst.value(m_xPredictorNum);
	        	  sumsX[attVal] += x * inst.weight();
	        	  sumSquaredX[attVal] += x * x * inst.weight();
	        	  sumsXY[attVal] += y * x * inst.weight();
	          }
	          
	          
	        }
	        
	        totalSumOfWeights = Utils.sum(sumOfWeights);
	        totalSumY = Utils.sum(sumsY);
	        totalSumSquaredY = Utils.sum(sumSquaredY);
	        totalSumX = Utils.sum(sumsX);
	        totalSumSquaredX = Utils.sum(sumSquaredX);
	        totalSumXY = Utils.sum(sumsXY);
	        
	        
	        // Check split sizes
	        for (double w : sumOfWeights) {
	        	if (w < minLeafSize) {
	        		vals[att] = Double.NEGATIVE_INFINITY;
	      	      	return splitPoint;
	        	}
	        }
	        
	        
	        // Copy data splits
	        for (int i = 0; i < dataSplits.length; i ++) {
	        	
	        	Instances instancesSplit = new Instances(data);
	        	instancesSplit.clear();
	        	for (int j = 0; j < data.size(); j ++) {
	        		Instance inst = data.get(j);
	        		attVal = (int) inst.value(att);
	        		if (attVal == i) instancesSplit.add(inst);
	        	}
	        	dataSplits[i] = instancesSplit;
	        	
	        }
	        
	        
	      } else {
	    	  
	    	  
	    	  
	        // For numeric attributes
	        sumsY = new double[2];
	        sumSquaredY = new double[2];
	        sumsX = new double[2];
	        sumSquaredX = new double[2];
	        sumOfWeights = new double[2];
	        sumsXY = new double[2];
	        dataSplits = new Instances[2];
	        
	        double[] currSumOfWeights = new double[2];
	        double[] currSumsY = new double[2];
	        double[] currSumSquaredY = new double[2];
	        double[] currSumsX = new double[2];
	        double[] currSumSquaredX = new double[2];
	        double[] currSumXY = new double[2];
	        Instances[] currDataSplit = new Instances[2];
	        currDataSplit[0] = new Instances(data);
	        currDataSplit[0].clear();
	        currDataSplit[1] = new Instances(data);
	        
	        // Sort data
	        data.sort(att);

	        // Move all instances into second subset
	        double x,y;
	        for (int j = 0; j < data.numInstances(); j++) {
	          Instance inst = data.instance(j);
	          if (inst.isMissing(att)) {

	            // Can stop as soon as we hit a missing value
	            indexOfFirstMissingValue = j;
	            break;
	          }

	          y = inst.classValue();
	          currSumsY[1] += y * inst.weight();
	          currSumSquaredY[1] += y * y * inst.weight();
	          currSumOfWeights[1] += inst.weight();
	          
	          // X-predictor value
	          if (m_xPredictorNum >= 0) {
	        	  x = inst.value(m_xPredictorNum);
	        	  currSumsX[1] +=  x * inst.weight();
	        	  currSumSquaredX[1] +=  x * x * inst.weight();
	        	  currSumXY[1] += y * x * inst.weight();
	          }
	          
	        }

	        totalSumY = currSumsY[1];
	        totalSumSquaredY = currSumSquaredY[1];
	        totalSumX = currSumsX[1];
	        totalSumSquaredX = currSumSquaredX[1];
	        totalSumOfWeights = currSumOfWeights[1];
	        totalSumXY = currSumXY[1];

	        sumsY[1] = currSumsY[1];
	        sumSquaredY[1] = currSumSquaredY[1];
	        sumsX[1] = currSumsX[1];
	        sumSquaredX[1] = currSumSquaredX[1];
	        sumOfWeights[1] = currSumOfWeights[1];
	        sumsXY[1] = currSumXY[1];
	        //dataSplits[1] = currDataSplit[1];
	        
	        


	        // Try all possible split points
	        double currSplit = data.instance(0).value(att);
	        double currVal, bestVal = Double.MAX_VALUE;

	        for (int i = 0; i < indexOfFirstMissingValue; i++) {
	          Instance inst = data.instance(i);
	          
	          
	          if (inst.value(att) > currSplit) {
	        	  
	        	  
	            currVal = correlation(currSumsY, currSumSquaredY, currSumsX, currSumSquaredX, currSumXY, currSumOfWeights);
	            //currVal = calculateR2(currDataSplit, currSumOfWeights, m_xPredictorNum);
	            
	            // Is the split too small?
	            if (currSumOfWeights[0] >= minLeafSize && currSumOfWeights[1] >= minLeafSize && currVal < bestVal) {
	              bestVal = currVal;
	              splitPoint = (inst.value(att) + currSplit) / 2.0;

	              // Check for numeric precision problems
	              if (splitPoint <= currSplit) {
	                splitPoint = inst.value(att);
	              }

	              for (int j = 0; j < 2; j++) {
	                sumsY[j] = currSumsY[j];
	                sumSquaredY[j] = currSumSquaredY[j];
	                sumsX[j] = currSumsX[j];
	                sumSquaredX[j] = currSumSquaredX[j];
	                sumOfWeights[j] = currSumOfWeights[j];
	                sumsXY[j] = currSumXY[j];
	                dataSplits[j] = currDataSplit[j];
	              }
	            }
	          }

	          currSplit = inst.value(att);

	          double classVal = inst.classValue() * inst.weight();
	          double classValSquared = inst.classValue() * classVal;
	          double xVal = 0;
	          double xsquared = 0;
	          double XY = 0;
	          if (m_xPredictorNum >= 0) {
	        	  x = inst.value(m_xPredictorNum);
	        	  xVal = x * inst.weight();
	        	  xsquared = x * x * inst.weight();
	        	  XY = x * classVal;
	          }

	          currSumsY[0] += classVal;
	          currSumSquaredY[0] += classValSquared;
	          currSumsX[0] += xVal;
	          currSumSquaredX[0] += xsquared;
	          currSumXY[0] += XY;
	          currSumOfWeights[0] += inst.weight();
	          currDataSplit[0].add(inst);
	          

	          currSumsY[1] -= classVal;
	          currSumSquaredY[1] -= classValSquared;
	          currSumsX[1] -= xVal;
	          currSumSquaredX[1] -= xsquared;
	          currSumXY[0] -= XY;
	          currSumOfWeights[1] -= inst.weight();
	          currDataSplit[1].remove(inst);
	          
	          
	          
	          
	        }
	      }

	      // Compute weights
	      props[0] = new double[sumsY.length];
	      for (int k = 0; k < props[0].length; k++) {
	        props[0][k] = sumOfWeights[k];
	      }
	      if (!(Utils.sum(props[0]) > 0)) {
	        for (int k = 0; k < props[0].length; k++) {
	          props[0][k] = 1.0 / props[0].length;
	        }
	      } else {
	        Utils.normalize(props[0]);
	      }

	      // Distribute weights for instances with missing values
	      double x, y;
	      for (int i = indexOfFirstMissingValue; i < data.numInstances(); i++) {
	        Instance inst = data.instance(i);

	        for (int j = 0; j < sumsY.length; j++) {
	        	y = inst.classValue();
	        	sumsY[j] += props[0][j] * y * inst.weight();
	        	sumSquaredY[j] += props[0][j] * y * y * inst.weight();
	        	sumOfWeights[j] += props[0][j] * inst.weight();
	        	if (m_xPredictorNum >= 0) {
	        		x = inst.value(m_xPredictorNum);
	        		sumsX[j] += props[0][j] * x * inst.weight();
	        		sumSquaredX[j] += props[0][j] * x * x * inst.weight();
	        		sumsXY[j] += props[0][j] * x * y * inst.weight();
	        	}
	        }
	        y = inst.classValue();
	        totalSumY += y * inst.weight();
	        totalSumSquaredY += y * y * inst.weight();
	        totalSumOfWeights += inst.weight();
	        if (m_xPredictorNum >= 0) {
	        	  x = inst.value(m_xPredictorNum);
	        	  totalSumX += x * inst.weight();
	        	  totalSumSquaredX += x * x * inst.weight();
	        	  totalSumXY += x * y * inst.weight();
	          }
	      }

	      // Compute final distribution
	      dist = new double[sumsY.length][data.numClasses()];
	      for (int j = 0; j < sumsY.length; j++) {
	        if (sumOfWeights[j] > 0) {
	          dist[j][0] = sumsY[j] / sumOfWeights[j];
	        } else {
	          dist[j][0] = totalSumY / totalSumOfWeights;
	        }
	      }
	      
	      
    	  
	        // Check split sizes
	        for (double w : sumOfWeights) {
	        	if (w < minLeafSize) {
	        		vals[att] = Double.NEGATIVE_INFINITY;
	      	      	return Double.NaN;
	        	}
	        }
	      
	      
	      
	        double correlationBefore = singleCorrelation(totalSumY, totalSumSquaredY, totalSumX, totalSumSquaredX, totalSumXY, totalSumOfWeights);
	        double correlationAfter = correlation(sumsY, sumSquaredY, sumsX, sumSquaredX, sumsXY, sumOfWeights);
	        double gain = correlationAfter - correlationBefore;
	        
	        
	        double R2before = calculateSingleR2(data, m_xPredictorNum);
	        double R2after = calculateR2(dataSplits, sumOfWeights, m_xPredictorNum);
	        //gain = R2after - R2before;
	        //System.out.println(R2before + "->" + R2after);
	      

	      // Return distribution and split point
	        subsetWeights[att] = sumOfWeights;
	        dists[0] = dist;
	        vals[att] = gain;

	        return splitPoint;
	        
	    }

	    
	    
	    /**
	     * Recursively generates a tree.
	     * 
	     * @param data the data to work with
	     * @param classProbs the class distribution
	     * @param attIndicesWindow the attribute window to choose attributes from
	     * @param random random number generator for choosing random attributes
	     * @param depth the current depth
	     * @throws Exception if generation fails
	     */
	    @Override
	    protected void buildTree(Instances data, double[] classProbs,
	      int[] attIndicesWindow, double totalWeight, Random random, int depth,
	      double minVariance) throws Exception {

	      // Make leaf if there are no training instances
	      if (data.numInstances() == 0) {
	        m_Attribute = -1;
	        m_ClassDistribution = null;
	        m_Prop = null;

	        if (data.classAttribute().isNumeric()) {
	          m_Distribution = new double[4];
	        }
	        return;
	      }
	      
	      
	    

	      double priorVar = 0;
	      if (data.classAttribute().isNumeric()) {

	        // Compute prior variance
	        double totalSum = 0, totalSumSquared = 0, totalSumOfWeights = 0;
	        for (int i = 0; i < data.numInstances(); i++) {
	          Instance inst = data.instance(i);
	          totalSum += inst.classValue() * inst.weight();
	          totalSumSquared += inst.classValue() * inst.classValue() * inst.weight();
	          totalSumOfWeights += inst.weight();
	        }
	        priorVar = RandomLinearTree.singleVariance(totalSum, totalSumSquared, totalSumOfWeights);
	      }

	      // System.err.println("Total weight " + totalWeight);
	      // double sum = Utils.sum(classProbs);
	      if (totalWeight < 2 * m_MinNum ||


	        // Numeric case
	        (data.classAttribute().isNumeric() && priorVar / totalWeight < minVariance)

	        ||

	        // check tree depth
	        ((getMaxDepth() > 0) && (depth >= getMaxDepth()))) {

	        // Make leaf
	        m_Attribute = -1;
	        m_ClassDistribution = classProbs.clone();
	        m_Distribution = new double[4];
		    buildLinearModel(data, m_xPredictorNum, m_Distribution); 

	        m_Prop = null;
	        return;
	      }

	      // Compute class distributions and value of splitting
	      // criterion for each attribute
	      double val = -Double.MAX_VALUE;
	      double split = -Double.MAX_VALUE;
	      double[][] bestDists = null;
	      double[] bestProps = null;
	      int bestIndex = 0;

	      // Handles to get arrays out of distribution method
	      double[][] props = new double[1][0];
	      double[][][] dists = new double[1][0][0];
	      double[][] totalSubsetWeights = new double[data.numAttributes()][0];

	      // Investigate K random attributes
	      int attIndex = 0;
	      int windowSize = attIndicesWindow.length;
	      int k = m_KValue;
	      boolean gainFound = false;
	      double[] tempNumericVals = new double[data.numAttributes()];
	      while ((windowSize > 0) && (k-- > 0 || !gainFound)) {

	        int chosenIndex = random.nextInt(windowSize);
	        attIndex = attIndicesWindow[chosenIndex];
	        Attribute attr = data.attribute(attIndex);
	        
	        if (attr.name().equals("NodeHeightPOEM.min.ESS.m")) {
	        	int x = 5;
	        }

	        // shift chosen attIndex out of window
	        attIndicesWindow[chosenIndex] = attIndicesWindow[windowSize - 1];
	        attIndicesWindow[windowSize - 1] = attIndex;
	        windowSize--;

	        double currSplit = numericDistribution(props, dists, attIndex, totalSubsetWeights, data, tempNumericVals, m_MinNum);

	        double currVal = tempNumericVals[attIndex];


	        if (Utils.gr(currVal, 0)) {
	          gainFound = true;
	        }

	        if ((currVal > val)
	          || ((!getBreakTiesRandomly()) && (currVal == val) && (attIndex < bestIndex))) {
	          val = currVal;
	          bestIndex = attIndex;
	          split = currSplit;
	          bestProps = props[0];
	          bestDists = dists[0];
	        }
	      }

	      // Find best attribute
	      m_Attribute = bestIndex;
	      Attribute attr = data.attribute(m_Attribute);


	      // Any useful split found?
	      if (Utils.gr(val, 0)) {
	        if (m_computeImpurityDecreases) {
	          m_impurityDecreasees[m_Attribute][0] += val;
	          m_impurityDecreasees[m_Attribute][1]++;
	        }

	        // Build subtrees
	        m_SplitPoint = split;
	        m_Prop = bestProps;
	        Instances[] subsets = splitData(data);
	        m_Successors = new LinearTree[bestDists.length];
	        double[] attTotalSubsetWeights = totalSubsetWeights[bestIndex];

	        for (int i = 0; i < bestDists.length; i++) {
	          m_Successors[i] = new LinearTree();
	          m_Successors[i].buildTree(subsets[i], bestDists[i], attIndicesWindow, attTotalSubsetWeights[i], random, depth + 1, minVariance);
	        }
	        
	        
	        
	        /*

	        // If all successors are non-empty, we don't need to store the class
	        // distribution
	        boolean emptySuccessor = false;
	        for (int i = 0; i < subsets.length; i++) {
	          if (m_Successors[i].m_ClassDistribution == null) {
	            emptySuccessor = true;
	            break;
	          }
	        }
	        if (emptySuccessor) {
	          m_ClassDistribution = classProbs.clone();
	          buildLinearModel(data, m_xPredictorNum, m_Distribution); // tmp
	        }
	        */
	      } else {

	        // Make leaf
	        m_Attribute = -1;
	        m_ClassDistribution = classProbs.clone();
	        
        	// Build linear model
        	//buildLinearModel(data, m_xPredictorNum, m_Distribution);
         // m_Distribution = new double[2];
         // m_Distribution[0] = priorVar;
          //m_Distribution[1] = totalWeight;
	      }
	      
	      m_Distribution = new double[4];
	      buildLinearModel(data, m_xPredictorNum, m_Distribution); // tmp
	    }

		
		
	}
	
	
	
	/**
	 * Builds classifier.
	 * 
	 * @param data the data to train with
	 * 	@throws Exception if something goes wrong or the data doesn't fit
	 */
	  @Override
	  public void buildClassifier(Instances data) throws Exception {
	
	    if (m_computeImpurityDecreases) {
	      m_impurityDecreasees = new double[data.numAttributes()][2];
	    }
	    
	    
	    // X predictor column name
	    if (!m_xPredictorName.isEmpty()) {
	    	m_xPredictorNum = WekaUtils.getIndexOfColumn(data, m_xPredictorName);
	    	if (m_xPredictorNum == -1) throw new Exception("Cannot find column " + m_xPredictorName + " in instances");
	    }
	    
	    // By default the predictor is the attribute before the class
	    else {
	    	//String x = data.classAttribute().name();
	    	//int y = WekaUtils.getIndexOfColumn(data, x);
	    	m_xPredictorNum = data.classIndex() - 1;
	    	m_xPredictorName = data.attribute(m_xPredictorNum).name();
	    	if (!data.attribute(m_xPredictorNum).isNumeric()) {
	    		throw new Exception("Trying to use " + m_xPredictorName + " as a predictor but it is not numeric");
	    	}
	    }
	    
	
	    // Make sure K value is in range
	    if (m_KValue > data.numAttributes() - 1) {
	      m_KValue = data.numAttributes() - 1;
	    }
	    if (m_KValue < 1) {
	      m_KValue = (int) Utils.log2(data.numAttributes() - 1) + 1;
	    }
	
	    // can classifier handle the data?
	    getCapabilities().testWithFail(data);
	
	    // remove instances with missing class
	    data = new Instances(data);
	    data.deleteWithMissingClass();
	
	    // only class? -> build ZeroR model
	    if (data.numAttributes() == 1) {
	      System.err
	        .println("Cannot build model (only class attribute present in data!), "
	          + "using ZeroR model instead!");
	      m_zeroR = new weka.classifiers.rules.ZeroR();
	      m_zeroR.buildClassifier(data);
	      return;
	    } else {
	      m_zeroR = null;
	    }
	
	    // Figure out appropriate datasets
	    Instances train = null;
	    Instances backfit = null;
	    Random rand = data.getRandomNumberGenerator(m_randomSeed);
	    if (m_NumFolds <= 0) {
	      train = data;
	    } else {
	      data.randomize(rand);
	      data.stratify(m_NumFolds);
	      train = data.trainCV(m_NumFolds, 1, rand);
	      backfit = data.testCV(m_NumFolds, 1);
	    }
	
	    // Create the attribute indices window
	    int[] attIndicesWindow = new int[data.numAttributes() - (m_xPredictorNum < 0 ? 1 : 2)];
	    int j = 0;
	    for (int i = 0; i < attIndicesWindow.length; i++) {
	      while (j == data.classIndex() || j == m_xPredictorNum) {
	        j++; // do not include the class or leaf predictor
	      }
	      attIndicesWindow[i] = j++;
	    }
	    
	    double totalWeight = 0;
	    double totalSumSquared = 0;
	
	    // Compute initial class counts
	    double[] classProbs = new double[train.numClasses()];
	    for (int i = 0; i < train.numInstances(); i++) {
	    	Instance inst = train.instance(i);
	    	classProbs[0] += inst.classValue() * inst.weight();
	    	totalSumSquared += inst.classValue() * inst.classValue() * inst.weight();
	    	totalWeight += inst.weight();
	    }
	
	    double trainVariance = 0;
	    if (data.classAttribute().isNumeric()) {
	      trainVariance =
	        RandomLinearTree.singleVariance(classProbs[0], totalSumSquared, totalWeight) / totalWeight;
	      classProbs[0] /= totalWeight;
	    }
	
	    // Build tree
	    m_Tree = new LinearTree();
	    m_Info = new Instances(data, 0);
	    m_Tree.buildTree(train, classProbs, attIndicesWindow, totalWeight, rand, 0, m_MinVarianceProp * trainVariance);
	
	    // Backfit if required
	    if (backfit != null) {
	      m_Tree.backfitData(backfit);
	    }
	  }
	
	  
	  public static double calculateR2(Instances[] data, double[] weight, int predXIndex) throws Exception {
		  
		  double wsum = Utils.sum(weight);
		  double meanR2 = 0;
		  double maxR2 = Double.NEGATIVE_INFINITY;
		  for (int i = 0; i < data.length; i ++) {
			  
			  double R2 = calculateSingleR2(data[i], predXIndex);
			  meanR2 += R2 * weight[i];
			  if (R2 > maxR2) maxR2 = R2;
		  }
		  
		  if (true) return maxR2;
		  return meanR2 / wsum;
		  
	  }
	  
	  
	
	  /**
	   * Calculate R2 of current node using the predictor
	   * @param data
	   * @param predXIndex
	   * @return
	   * @throws Exception
	   */
	  public static double calculateSingleR2(Instances data, int predXIndex) throws Exception {
		  
		  if (data == null || data.size() == 0) return 0;
		  
		  // Slope
		  double meanSlope = 0;
		  double weightSum = 0;
		  for (int i = 0; i < data.size(); i ++) {
			  Instance inst = data.get(i);
			  double x = inst.value(predXIndex);
			  double y = inst.classValue();
			  meanSlope += y/x * inst.weight();
			  weightSum += inst.weight();
		  }
		  meanSlope = meanSlope / weightSum;
		  
		  
		  
		 // LinearRegression lm = new LinearRegression();
		 // lm.buildClassifier(data);
		 // double[] coeff = lm.coefficients();
		 // double intercept = coeff[coeff.length-1];
		 // double slope = coeff[predXIndex];
		  
		  double ssr = RegressionAnalysis.calculateSSR(data, data.attribute(predXIndex), meanSlope, 0);
		  return ssr / weightSum;
		  //double r2 = RegressionAnalysis.calculateRSquared(data, ssr);
		 // return r2;
		  
	  }
	  
	  
	  /**
	   * Calculate weighted-average correlation for an array of sums and sum-of-squares
	   * @param sy
	   * @param ssy
	   * @param sx
	   * @param ssx
	   * @param sxy
	   * @param weight
	   * @return
	   */
	  public static double correlation(double[] sy, double[] ssy, double[] sx, double[] ssx, double[] sxy, double[] weight) {
		 //if (true) return -variance(sy, ssy, weight);
		 
		 double maxCorr = Double.NEGATIVE_INFINITY;
		 double wsum = Utils.sum(weight);
		 double meanCorr = 0;
		 for (int i = 0; i < sy.length; i++) {
			 double corr = singleCorrelation(sy[i], ssy[i], sx[i], ssx[i], sxy[i], weight[i]);
			 meanCorr += corr*weight[i];
			 if (corr > maxCorr) maxCorr = corr;
		 }
		 if (true) return maxCorr;
		 return meanCorr / wsum;

	}






	/**
	   * Calculate the correlation between X and Y
	   * @param sumY
	   * @param ssY
	   * @param sumX
	   * @param ssX
	   * @param sumXY
	   * @param weight
	   * @return
	   */
	  protected static double singleCorrelation(double sumY, double ssY, double sumX, double ssX, double sumXY, double weight) {
		  //if (true) return -singleVariance(sumY, ssY, weight);
		  double sdX = Math.sqrt(singleVariance(sumX, ssX, weight) / weight);
		  double sdY = Math.sqrt(singleVariance(sumY, ssY, weight) / weight);
		  double covXY = singleCovariance(sumXY, sumX, sumY, weight) / weight;
		  double correlation = covXY / (sdX * sdY);
		  return Math.abs(correlation);
	  }
	  
	  
	  
	  /**
	   * Calculate the covariance for subsets
	   * @param sxy
	   * @param sx
	   * @param sy
	   * @param weight
	   * @return
	   */
	  protected static double covariance(double[] sxy, double[] sx, double[] sy, double[] weight) {
			double cov = 0;
		    for (int i = 0; i < sy.length; i++) {
		    	if (weight[i] > 0) {
		    		cov += singleCovariance(sxy[i], sx[i], sy[i], weight[i]);
		    	}
		    }
		    return cov;
		}

	
	  
	  /**
	   * Calculate the covariance between X and Y
	   * @param sumXY - sum of of XY
	   * @param sumX - sum of X
	   * @param sumY - sum of Y
	   * @param weight - weight sum
	   * @return
	   */
	  protected static double singleCovariance(double sumXY, double sumX, double sumY, double weight) {
	    return sumXY - ((sumX*sumY)/weight); // / weight;
	  }
	  
	  
	  @Override
	  public void setOptions(String[] options) throws Exception {
		  
		  // Parse the index of the regression attribute
		  String tmpStr;
		  tmpStr = Utils.getOption('x', options);
		  if (tmpStr.length() != 0) {
			  m_xPredictorName = tmpStr;
		  } else {
			  m_xPredictorName = "";
			  m_xPredictorNum = -1;
		  }
		  
		  super.setOptions(options);
		  
		  
	  }
	  
	  
	  @Override
	  public String[] getOptions() {
		  
		  Vector<String> result = new Vector<String>();
		  result.add("-x");
		  result.add("" + m_xPredictorNum);
		  
		  Collections.addAll(result, super.getOptions());
		  return result.toArray(new String[result.size()]);
	  }
	  
	  
	  
	  /**
	   * Computes class distribution of an instance using the tree.
	   * 
	   * @param instance the instance to compute the distribution for
	   * @return the computed class probabilities
	   * @throws Exception if computation fails
	   */
	  @Override
	  public double[] distributionForInstance(Instance instance) throws Exception {

  
	    if (m_zeroR != null) {
	      return m_zeroR.distributionForInstance(instance);
	    } else {
	      return m_Tree.distributionForInstance(instance);
	    }
	  }
	  
	  

	  /**
	   * Do not allow nominal classes
	   * @return the capabilities of this classifier
	   */
	  @Override
	  public Capabilities getCapabilities() {
		  Capabilities result = super.getCapabilities();
		  result.disable(Capability.NOMINAL_CLASS);
		  result.disable(Capability.MISSING_CLASS_VALUES);
		  return result;
	  }
	  
	  
	  @Override
	  public String toString() {

	    // only ZeroR model?
	    if (m_zeroR != null) {
	      StringBuffer buf = new StringBuffer();
	      buf.append(this.getClass().getName().replaceAll(".*\\.", "") + "\n");
	      buf.append(this.getClass().getName().replaceAll(".*\\.", "")
	        .replaceAll(".", "=")
	        + "\n\n");
	      buf
	        .append("Warning: No model could be built, hence ZeroR model is used:\n\n");
	      buf.append(m_zeroR.toString());
	      return buf.toString();
	    }

	    if (m_Tree == null) {
	      return "nRandomLinearTree: no model has been built yet.";
	    } else {
	      return "\nRandomLinearTree\n==========\n"
	        + m_Tree.toString(0)
	        + "\n"
	        + "\nSize of the tree : "
	        + m_Tree.numNodes()
	        + (getMaxDepth() > 0 ? ("\nMax depth of tree: " + getMaxDepth()) : (""));
	    }
	  }

	  

	  
	  /**
	   * Build a model
	   * @param data
	   * @param model
	 * @throws Exception 
	   */
	  public static void buildLinearModel(Instances data, int predictorColnum, double[] model) throws Exception {
		  
		  
		  
		  
		  if (predictorColnum <= 0) return;
		  
		  
		  // Estimate slope but not intercept
		  model[0] = 0.0;
		  double meanSlope = 0;
		  double weightSum = 0;
		  for (int i = 0; i < data.size(); i ++) {
			  Instance inst = data.get(i);
			  if (inst.isMissing(predictorColnum)) continue;
			  double x = inst.value(predictorColnum);
			  double y = inst.classValue();
			  meanSlope += y/x * inst.weight();
			  weightSum += inst.weight();
		  }
		  meanSlope /= weightSum;
		  model[1] = meanSlope;
		  model[2] = 0;
		  
		  //if (true) return;
		  
		  
		  String predictorName = data.attribute(predictorColnum).name();
		  
		  // Subset the attributes
		  Instances subset = new Instances(data);
		  for (int i = subset.numAttributes()-1; i >= 0; i--) {
			  
			  // Keep the predictor
			  if (i == predictorColnum) continue;
			  
			  // Keep the class
			  if (subset.attribute(i) == subset.classAttribute()) continue;
			  
			  // Remove the rest
			  subset.deleteAttributeAt(i);
			  
		  }
		  
		  Attribute squared = new Attribute("squared");
		  int predictorIndex = WekaUtils.getIndexOfColumn(subset, predictorName);
		  int squaredIndex = -1;
		  if (false) {
			  
			  // Create a polynomial term
			  subset.insertAttributeAt(squared, 1);
			  squaredIndex = WekaUtils.getIndexOfColumn(subset, squared.name());
			  for (int i = 0; i < subset.size(); i ++) {
				  
				  Instance inst = subset.get(i);
				  double weight = inst.value(predictorIndex);
				  double weight2 = weight*weight;
				  inst.setValue(squaredIndex, weight2);
				  
			  }
		  }
		  
		  // Linear regression
		  LinearRegression lm = new LinearRegression();
		  lm.buildClassifier(subset);
		  double[] coeff = lm.coefficients();
		  
		  double intercept = coeff[coeff.length-1];
		  double slope1 = coeff[predictorIndex];
		  double slope2 = squaredIndex == -1 ? 0 : coeff[squaredIndex];
		  model[0] = intercept;
		  model[1] = slope1;
		  model[2] = slope2;
		  model[3] = 0; 
		  
		  double ssr = RegressionAnalysis.calculateSSR(subset, subset.attribute(predictorIndex), slope1, intercept);
		  double r2 = RegressionAnalysis.calculateRSquared(subset, ssr);
		  model[3] = r2;
		 // System.out.println(ssr + ", " + r2);
		  
		  //model[0] = lm.getIntercept();
		  //model[1] = lm.getSlope();
		  //model[2] = lm.slop
		  
	  }
	
	  
	  
	  /**
	   * Predict using model
	   * @param inst
	   * @param model
	   * @return
	   */
	  public static double getValueFromModel(Instance inst, int predictorColnum, double[] model) {
		  double x = inst.value(predictorColnum);
		  return model[0] + x*model[1] + x*x*model[2];
	  }
	  
}













