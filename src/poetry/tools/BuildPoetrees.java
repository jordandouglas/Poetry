package poetry.tools;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;

import beast.app.util.Application;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.util.Log;
import beast.util.Randomizer;
import poetry.learning.RandomLinearTree;
import poetry.sampler.POEM;
import poetry.util.WekaUtils;
import beast.core.Input.Validate;
import weka.classifiers.Classifier;
import weka.classifiers.evaluation.Evaluation;
import weka.classifiers.functions.GaussianProcesses;
import weka.classifiers.functions.LinearRegression;
import weka.classifiers.functions.SMOreg;
import weka.classifiers.lazy.KStar;
import weka.classifiers.trees.REPTree;
import weka.classifiers.trees.RandomForest;
import weka.classifiers.trees.RandomTree;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.NumericTransform;
import weka.filters.unsupervised.instance.RemoveWithValues;


@Description("Loads in a a training .arff file and outputs a decision tree for the specified POEM")
public class BuildPoetrees extends Runnable { 
	
	
	final public Input<String> poemNameInput = new Input<>("poem", "The name of the poem", Validate.REQUIRED);
	final public Input<File> trainingInput = new Input<>("training", "arff file containing the training data", Validate.REQUIRED);
	final public Input<File> outputInput = new Input<>("out", "A directory where the outputs will be stored", Validate.REQUIRED);
	
	
	final public Input<Integer> minInstancesPerLeafInput = new Input<>("min", "Minimum number of instances per leaf in decision tree", 20);
	
	
	final int nfolds = 1;
	String poemName;
	File outputDir;
	Instances trainingSet;
	//int weightColNum;
	String weightColName;
	
	// The classifiers
	final protected Class<?>[] classifiers = new Class[] { 	REPTree.class, RandomLinearTree.class, RandomForest.class, 
															LinearRegression.class, KStar.class };// GaussianProcesses.class,
															//SMOreg.class,   };

	@Override
	public void initAndValidate() {
		
		
		// Validate
		File trainingFile = trainingInput.get();
		if (!trainingFile.canRead()) throw new IllegalArgumentException("Error reading training file at " + trainingFile.getPath());
		
		
		// Read the training/test arffs
		try {
			trainingSet = new DataSource(trainingFile.getAbsolutePath()).getDataSet();
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Error opening arff file");
		}
		
		
		poemName = poemNameInput.get();

		
		// Find poem columns
		String poemESSColname = POEM.getESSColname(poemName);
		weightColName = POEM.getWeightColname(poemName);
		
		
		// Find ESS or ESS.m column
		int colNum = WekaUtils.getIndexOfColumn(trainingSet, poemESSColname);
		if (colNum == -1) {
			poemESSColname =  poemESSColname + ".m";
			colNum = WekaUtils.getIndexOfColumn(trainingSet, poemESSColname);
		}
		if (colNum == -1) throw new IllegalArgumentException("Error cannot locate " + poemESSColname + " column in " + trainingFile.getPath());
		
		
		// Tidy the columns
		poemName = poemNameInput.get();
		try {
			this.tidyColumns(poemESSColname, weightColName);
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Error tidying attributes");
		}
		
		// Set the target class
		int classIndex = WekaUtils.getIndexOfColumn(trainingSet, poemESSColname);
		Attribute poemESSColumn = trainingSet.attribute(classIndex);
		trainingSet.setClass(poemESSColumn);
		classIndex = trainingSet.classIndex();
		
		try {
			
			// Remove with missing class
			Filter filter = new RemoveWithValues();
			filter.setOptions(new String[] {"-C", "" + (classIndex+1), "-M" });
			filter.setInputFormat(trainingSet);
			trainingSet = Filter.useFilter(trainingSet, filter);
			
			
			// Log transform
			/*
			filter = new NumericTransform(); 
			filter.setOptions(new String[] {"-M", "log" });
			filter.setInputFormat(trainingSet);
			trainingSet = Filter.useFilter(trainingSet, filter);
			*/
			
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException(); 
		}
		
		

		// Find weight column
		int weightColNum = WekaUtils.getIndexOfColumn(trainingSet, weightColName);
		if (weightColNum == -1) throw new IllegalArgumentException("Error cannot locate " + weightColName + " column in " + trainingFile.getPath());
		
		
		
		// Make output directory
		outputDir = outputInput.get();
		if (outputDir.exists() && !outputDir.isDirectory()) throw new IllegalArgumentException("Error: output '" + outputDir.getPath() + "'  must be a directory");
		outputDir.mkdir();
		
	}
	
	@Override
	public void run() throws Exception {
		
		Log.warning("Building models...");
		Log.warning("Total Number of Instances: " + trainingSet.size());
		
		
		/*
		WekaUtils.removeCol(trainingSet, "xml");
		
		// Tree
		RandomLinearTree rlt = new RandomLinearTree();
		//rlt.setOptions(new String[] { "-x",  weightColName });
		rlt.buildClassifier(trainingSet);
		
		// Print
		File out2 = Paths.get(outputDir.getPath(), "RandomLinearTree.txt").toFile();
		Log.warning("Saving tree to " + out2.getPath());
		PrintWriter pw2 = new PrintWriter(out2);
		pw2.println(rlt.toString());
		pw2.close();
		
		// Forest
		RandomForest rf1 = new RandomForest();
		rf1.setClassifier(rlt);
		rf1.buildClassifier(trainingSet);
		

		
		Evaluation eval = new Evaluation(trainingSet);
		eval.evaluateModel(rf1, trainingSet);
		double corr_f = eval.correlationCoefficient();
		
		
		//double c1 = WekaUtils.kFoldCrossValidationReplicates(trainingSet, rlt, nfolds);
		//double c2 = WekaUtils.kFoldCrossValidationSafe(trainingSet, rlt, nfolds);
		Log.warning("RandomLinearTree:");
		System.out.println("Training set correlation: " + corr_f);
		//System.out.println("Cross-validation correlation (reps): " + c1);
		
		
		
		if (true) return;
		*/


		// Print out
		File tableOut = Paths.get(outputDir.getPath(), "correlation.tsv").toFile();
		Log.warning("Saving results to " + tableOut.getPath());
		PrintWriter writer = new PrintWriter(tableOut);
		writer.println("Algorithm\tCorrelation\tCorrelationReps");
		
		
		for (Class<?> c : classifiers) {
			
			// Get the model
			Constructor<?> constructor = c.getConstructor();
			Classifier model = (Classifier) constructor.newInstance();
			String modelName = model.getClass().getSimpleName();
			
			
			double[][] fits = new double[2][];
			
			// 10-fold cross-validation while accounting for replicates
			double corr_reps = WekaUtils.kFoldCrossValidationReplicates(trainingSet, model, nfolds, fits);
			
			
			// Print tree to file
			if (model instanceof RandomLinearTree) {
				
				RandomLinearTree tree = new RandomLinearTree();
				Instances training2 = new Instances(trainingSet);
				WekaUtils.removeCol(training2, "xml");
				//tree.setOptions(new String[] { "-M", "" + minInstancesPerLeafInput.get() });
				tree.buildClassifier(training2);
				File out = Paths.get(outputDir.getPath(), "tree.txt").toFile();
				Log.warning("Saving tree to " + out.getPath());
				PrintWriter pw = new PrintWriter(out);
				pw.println(tree.toString());
				pw.close();
			}
			
			
			// 10-fold cross validation without accounting for replicates
			double corr = WekaUtils.kFoldCrossValidationSafe(trainingSet, model, nfolds);
			
			
			Log.warning(modelName + ":");
			System.out.println("Cross-validation correlation: " + corr);
			System.out.println("Cross-validation correlation (reps): " + corr_reps);
			
			writer.println(modelName + "\t" + corr + "\t" + corr_reps);
			
			if (model instanceof RandomForest) {
				
				File out = Paths.get(outputDir.getPath(), "randomforest.txt").toFile();
				Log.warning("Saving random forest predictions to " + out.getPath());
				PrintWriter pw = new PrintWriter(out);
				pw.println("true\tpred");
				for (int i = 0; i < fits[0].length; i ++) {
					pw.println(fits[0][i] + "\t" + fits[1][i]);
				}
				pw.close();
				
				
			}
			
			
		}

		writer.close();
		Log.warning("Done!");
		
	}

	
	/**
	 * Remove unwanted columns including those applicable to other poems
	 * Convert all data into the appropriate formats
	 * @throws Exception 
	 */
	protected void tidyColumns(String poemESSColname, String poemWeightColname) throws Exception {
		
		Log.warning("Tidying attributes...");
		
		
		// Apply training set
		Instances data = trainingSet;
		
		// Remove unwanted columns
		WekaUtils.removeCol(data, "nspecies"); // Temp
		WekaUtils.removeCol(data, "replicate");
		WekaUtils.removeCol(data, "dataset");
		WekaUtils.removeCol(data, "nstates");
		WekaUtils.removeCol(data, "runtime.smooth.hr");
		WekaUtils.removeCol(data, "runtime.smooth.hr.m");
		WekaUtils.removeCol(data, "runtime.raw.hr");
		WekaUtils.removeCol(data, "runtime.raw.hr.m");
		WekaUtils.removeCol(data, "ESS.mean.m");
		WekaUtils.removeCol(data, "ESS.sd.m");
		WekaUtils.removeCol(data, "ESS.cov.m");
	
	
	
		// Remove all poem weights and esses except for the current ess
		List<Attribute> poems = WekaUtils.getAttributesWithSubstring(data, POEM.getESSColname(""));
		poems.addAll(WekaUtils.getAttributesWithSubstring(data, POEM.getWeightColname("")));
		for (Attribute attr : poems) {
			if (attr.name().equals(poemESSColname) | attr.name().equals(poemWeightColname)) continue;
			WekaUtils.removeCol(data, attr.name());
		}
	
		
		//data.deleteStringAttributes();
		

		
		/*
		// Convert all strings to nominals
		Enumeration<Attribute> attributes = data.enumerateAttributes();
		Attribute attr;
		while (attributes.hasMoreElements()) {
			attr = attributes.nextElement();
			
			
			//Log.warning("Checking " + attr.name() + "...");
			
			// Convert string to nominal and remove all entries which have 'NA'
			if (attr.isString()) {
				
				Log.warning("Converting " + attr.name() + " to string");
				
				int colnum = WekaUtils.getIndexOfColumn(data, attr.name()) + 1;
				StringToNominal filter = new StringToNominal();
				
			    filter.setOptions(new String[] { "-R", "" + colnum } );
				filter.setInputFormat(data);
				data = Filter.useFilter(data, filter);
				
				// Remove with NA
				int NAindex = attr.indexOfValue("NA");
				if (NAindex > -1) {
					RemoveWithValues removeFilter = new RemoveWithValues();
					removeFilter.setOptions(new String[] { "-C", "" + colnum, "-L", "" + NAindex });
					removeFilter.setInputFormat(data);
					data = Filter.useFilter(data, removeFilter);
				}
				
	
				
				
			}else {
				//Log.warning(attr.name() + " does not need converting");
			}
		}
		*/
		

		
	
		trainingSet = data;
		Log.warning("Finished tidying.");
		
	}

	
	public static void main(String[] args) throws Exception {
		new Application(new BuildPoetrees(), "Build one decision tree per POEM", args);
	}
	

}