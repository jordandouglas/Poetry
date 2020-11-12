package poetry.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import beast.core.util.Log;
import beast.util.Randomizer;
import poetry.learning.RandomLinearTree;
import weka.classifiers.Classifier;
import weka.classifiers.evaluation.Evaluation;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffSaver;


/**
 * Utils for processing Weka objects
 * @author jdou557
 *
 */
public class WekaUtils {


	/**
	 * Subsets the data based on xml column value and optionally writes the subset to the specified file
	 * @param data
	 * @param xmls
	 * @param outfile
	 * @throws IOException
	 */
	public static Instances splitDataAndSaveArff(Instances data, List<Double> xmls, File outfile) throws IOException {
		
		// Generate the subset
		Instances subset = new Instances(data);
		subset.clear();
		for (double xml : xmls) {
			subset.addAll(getWithVal(data, "xml", xml));
		}
		
		// Save to .arff file
		if (outfile != null) {
			ArffSaver saver = new ArffSaver();
			saver.setInstances(subset);
			saver.setFile(new File(outfile.getPath()));
			saver.writeBatch();
		}
		
		return subset;
		
	}
	
	
	/**
	 * Returns list of instances which have this attribute value
	 * @param data
	 * @param attr
	 * @param val
	 * @return
	 */
	public static Collection<Instance> getWithVal(Instances data, String attr, double val) {
		
		int colNum = getIndexOfColumn(data, attr);
		if (colNum < 0) throw new IllegalArgumentException("Cannot find column " + attr);
		
		// Get matches
		Collection<Instance> matches = new ArrayList<>();
		Enumeration<Instance> instances = data.enumerateInstances();
		Instance instance;
		while (instances.hasMoreElements()) {
			instance = instances.nextElement();
			if (instance.value(colNum) == val) {
				matches.add(instance);
			}
		}
		
		
		return matches;
	}
	
	/**
	 * Returns list of values under this column
	 * @param data
	 * @param colname
	 */
	public static List<Double> getVals(Instances data, String colname) {
	
		
		int colNum = getIndexOfColumn(data, colname);
		if (colNum < 0) throw new IllegalArgumentException("Cannot find column " + colname);
		
		// Build list of values
		List<Double> vals = new ArrayList<>();
		Enumeration<Instance> instances = data.enumerateInstances();
		Instance instance;
		while (instances.hasMoreElements()) {
			instance = instances.nextElement();
			vals.add(instance.value(colNum));
		}
		return vals;
		
	}
	
	/**
	 * Get the index of a column
	 * @param data
	 * @param name
	 * @return
	 */
	public static int getIndexOfColumn(Instances data, String name) {
		
		
		
		// Get the index number of this attribute
		Enumeration<Attribute> attributes = data.enumerateAttributes();
		Attribute attr;
		int i = 0;
		while (attributes.hasMoreElements()) {
			attr = attributes.nextElement();
			if (attr.name().equals(name)) return i;
			i++;
		}
		return -1;
		
	}
	
	
	/**
	 * Get the index of a column
	 * @param instance
	 * @param name
	 * @return
	 */
	public static int getIndexOfColumn(Instance instance, String name) {
		
		
		// Get the index number of this attribute
		Enumeration<Attribute> attributes = instance.enumerateAttributes();
		Attribute attr;
		int i = 0;
		while (attributes.hasMoreElements()) {
			attr = attributes.nextElement();
			if (attr.name().equals(name)) return i;
			i++;
		}
		return -1;
		
	}
	
	/**
	 * Removes the column by name
	 * @param data
	 * @param name
	 */
	public static void removeCol(Instances data, String name) {
		
		
		int colNum = getIndexOfColumn(data, name);
		
		// If it exists then remove it
		if (colNum >= 0) {
			//Log.warning("Removing attribute " + name);
			data.deleteAttributeAt(colNum);
		}else {
			//Log.warning("Cannot remove attribute " + name + " because it does not exist");
		}
		
	}


	/**
	 * Return all attributes which match
	 * @param data
	 * @param substr
	 * @return
	 */
	public static List<Attribute> getAttributesWithSubstring(Instances data, String substr) {
		
		List<Attribute> matches = new ArrayList<>();
		
		Enumeration<Attribute> attributes = data.enumerateAttributes();
		Attribute attr;
		while (attributes.hasMoreElements()) {
			attr = attributes.nextElement();
			if (attr.name().contains(substr)) matches.add(attr);
		}
		
		return matches;
		
	}
	
	
	/**
	 * Performs cross-validation k times (with replicates accounted for) and reports mean correlation
	 * @param data
	 * @param model
	 * @return
	 * @throws Exception
	 */
	public static double kFoldCrossValidationReplicates(Instances data, Classifier model, int nfolds, double[][] fits) throws Exception {
		
		double correlation = 0;
		for (int i = 0; i < nfolds; i ++) {
			correlation += crossValidateReplicates(data, model, fits);
		}
		return correlation / nfolds;
		
	}
	
	
	/**
	 * Performs cross-validation k times and reports mean correlation
	 * @param data
	 * @param model
	 * @return
	 * @throws Exception
	 */
	public static double kFoldCrossValidationSafe(Instances data, Classifier model, int nfolds) throws Exception {
		
		double correlation = 0;
		for (int i = 0; i < nfolds; i ++) {
			correlation += crossValidateSafe(data, model);
		}
		return correlation / nfolds;
		
	}
	
	
	


	/**
	 * Performs cross-validation but it does not throw an error when 'GaussianProcesses' is the classifier
	 * TODO: Remove xml column
	 * @param data
	 * @param model
	 * @return
	 */
	public static double crossValidateSafe(Instances data, Classifier model) throws Exception {
		
		
		
	
		final int nfolds = 10;
		double correlation = 0;
		
		// Get list of instances
		List<Integer> indices = new ArrayList<>();
		for (int i = 0; i < data.size(); i ++) indices.add(i);
		int numInstances = indices.size();
		
		// Shuffle
		Collections.shuffle(indices, new Random(Randomizer.nextInt()));
		
		// Prepare
		List<List<Integer>> splits = new ArrayList<>();
		for (int i = 0; i < nfolds; i ++) {
			splits.add(new ArrayList<Integer>());
		}
		
		
		// Split into 10 even groups with remainders allocated to the first ones
		int i = 0;
		while (!indices.isEmpty()) {
			List<Integer> split = splits.get(i);
			int index = indices.remove(0);
			split.add(index);
			i++;
			if (i >= nfolds) i = 0;
		}
		
		try {
		
			// Perform cross-validation
			for (int f = 0; f < nfolds; f++) {
				
				// The training set
				Instances training = new Instances(data);
				training.clear();
				for (int j = 0; j < nfolds; j ++) {
					if (j == f) continue;
					for (int index : splits.get(j)) {
						training.add(data.instance(index));
					}
				}
				WekaUtils.removeCol(training, "xml");
				
				// The test set
				Instances test = new Instances(data);
				test.clear();
				for (int index : splits.get(f)) {
					test.add(data.instance(index));
				}
				WekaUtils.removeCol(test, "xml");
				
				// New model
				Classifier freshModel = model.getClass().getConstructor().newInstance();
				if (freshModel instanceof RandomForest) {
					
					// Tree
					RandomLinearTree rlt = new RandomLinearTree();
					
					// Forest
					//((RandomForest)freshModel).setClassifier(rlt);
					
				}
				freshModel.buildClassifier(training);
				
				// Evaluate
				Evaluation eval = new Evaluation(training);
				eval.evaluateModel(freshModel, test);
				double corr_f = eval.correlationCoefficient();
				correlation += corr_f;
				
				//Log.warning("Fold " + (f+1) + ": training on " + training.size() + " and testing on " + test.size() + ". p = " + corr_f);
				
				
			}
		
		
		}catch (Exception e) {
			e.printStackTrace();
		}
		
		
		return correlation / nfolds;
		
	}
	
	
	
	
	/**
	 * Performs cross-validation but it splits by xml file so that all replicates remain in the same partition
	 * TODO: Remove xml column
	 * @param data
	 * @param model
	 * @return
	 */
	public static double crossValidateReplicates(Instances data, Classifier model, double[][] fits) throws Exception {
		
		// True vs fit values
		double[] xmlNum = new double[data.size()];
		double[] trueX = new double[data.size()];
		double[] predX = new double[data.size()];
		if (fits != null && fits.length >= 3) {
			fits[0] = xmlNum;
			fits[1] = trueX;
			fits[2] = predX;
		}
		int instanceNum = 0;
	
		final int nfolds = 10;
		double correlation = 0;
		
		// Get unique list of xml files
		List<Double> xmls = WekaUtils.getVals(data, "xml");
		xmls = xmls.stream().distinct().collect(Collectors.toList());
		int numXMLs = xmls.size();
		
		// Shuffle
		Collections.shuffle(xmls, new Random(Randomizer.nextInt()));
		
		// Prepare
		List<List<Double>> splits = new ArrayList<>();
		for (int i = 0; i < nfolds; i ++) {
			splits.add(new ArrayList<Double>());
		}
		
		
		// Split into 10 even groups with remainders allocated to the first ones
		int i = 0;
		while (!xmls.isEmpty()) {
			List<Double> split = splits.get(i);
			double val = xmls.remove(0);
			split.add(val);
			i++;
			if (i >= nfolds) i = 0;
		}
		
		try {
		
			// Perform cross-validation
			for (int f = 0; f < nfolds; f++) {
				
				// The training set
				List<Double> trainingXMLs = new ArrayList<>();
				for (int j = 0; j < nfolds; j ++) {
					if (j == f) continue;
					trainingXMLs.addAll(splits.get(j));
				}
				Instances training = splitDataAndSaveArff(data, trainingXMLs, null);
				
				
				// The test set
				List<Double> testXMLs = splits.get(f);
				Instances test = splitDataAndSaveArff(data, testXMLs, null);
				
				
				// Store xml number
				double[] xmlsNames = new double[test.size()];
				int xmlColNum = WekaUtils.getIndexOfColumn(test, "xml");
				for (int j = 0; j < test.size(); j++) {
					Instance inst = test.get(j);
					xmlsNames[j] = inst.value(xmlColNum);
				}
				
				// Remove xml column
				WekaUtils.removeCol(training, "xml");
				WekaUtils.removeCol(test, "xml");
				
				
				// New model
				Classifier freshModel = model.getClass().getConstructor().newInstance();
				if (freshModel instanceof RandomForest) {
					
					// Tree
					RandomLinearTree rlt = new RandomLinearTree();
					
					// Forest
					//((RandomForest)freshModel).setClassifier(rlt);
					
				}
				freshModel.buildClassifier(training);
				
				// Evaluate
				Evaluation eval = new Evaluation(training);
				eval.evaluateModel(freshModel, test);
				double corr_f = eval.correlationCoefficient();
				correlation += corr_f;
				
				
				// Fit
				int xmlIndex = 0;
				for (Instance inst : test) {
					double truth = inst.classValue();
					double pred = freshModel.classifyInstance(inst);
					xmlNum[instanceNum] = xmlsNames[xmlIndex];
					trueX[instanceNum] = truth;
					predX[instanceNum] = pred;
					instanceNum++;
					xmlIndex++;
				}
				
				//Log.warning("Fold " + (f+1) + ": training on " + training.size() + " and testing on " + test.size() + ". p = " + corr_f);
				
				
			}
		
		
		}catch (Exception e) {
			e.printStackTrace();
		}
		
		
		return correlation / nfolds;
		
	}
	
	

	/**
	 * Fits a linear model y~x and returns the entropy of the fit
	 * @param x
	 * @param y
	 * @return
	 */
	public static double getEntropyOfLinearModel(double[] x, double[] y) {
		
		
		return 0;
		
	}
	
	
}
