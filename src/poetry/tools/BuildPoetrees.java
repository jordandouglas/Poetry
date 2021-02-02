package poetry.tools;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.nio.file.Paths;
import java.util.List;


import beast.app.util.Application;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.util.Log;
import poetry.learning.RandomLinearTree;
import poetry.sampler.POEM;
import poetry.util.WekaUtils;
import beast.core.Input.Validate;
import weka.classifiers.Classifier;
import weka.classifiers.functions.GaussianProcesses;
import weka.classifiers.functions.LinearRegression;
import weka.classifiers.functions.MultilayerPerceptron;
import weka.classifiers.functions.SMOreg;
import weka.classifiers.lazy.IBk;
import weka.classifiers.lazy.KStar;
import weka.classifiers.lazy.LWL;
import weka.classifiers.trees.REPTree;
import weka.classifiers.trees.RandomForest;
import weka.classifiers.trees.RandomTree;
import weka.clusterers.SimpleKMeans;
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
	final protected Class<?>[] classifiers = new Class[] { 	REPTree.class, RandomForest.class, IBk.class,
															LinearRegression.class, GaussianProcesses.class  };
															//SMOreg.class,   };GaussianProcesses.class, RandomLinearTree.class, RandomTree.class, 

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
		weightColName = POEM.getWeightColname(poemName) + ".d";
		
		
		
		// Find ESS or ESS.hr or ESS.m column
		int classIndex = WekaUtils.getIndexOfColumn(trainingSet, poemESSColname);
		if (classIndex == -1) {
			poemESSColname =  POEM.getESSColname(poemName) + ".p";
			classIndex = WekaUtils.getIndexOfColumn(trainingSet, poemESSColname);
		}
		if (classIndex == -1) {
			poemESSColname =  POEM.getESSColname(poemName) + ".m";
			classIndex = WekaUtils.getIndexOfColumn(trainingSet, poemESSColname);
		}
		if (classIndex == -1) throw new IllegalArgumentException("Error cannot locate " + poemESSColname + " column in " + trainingFile.getPath());
		
		
		// Tidy the columns
		poemName = poemNameInput.get();
		try {
			this.tidyColumns(poemESSColname, weightColName);
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Error tidying attributes");
		}
		
		// Set the target class
		classIndex = WekaUtils.getIndexOfColumn(trainingSet, poemESSColname);
		Attribute poemESSColumn = trainingSet.attribute(classIndex);
		trainingSet.setClass(poemESSColumn);
		classIndex = trainingSet.classIndex();
		
		try {
			
			// Remove with missing class
			Filter filter = new RemoveWithValues();
			filter.setOptions(new String[] {"-C", "" + (classIndex+1), "-M", "-S", "" + -100000 });
			filter.setInputFormat(trainingSet);
			trainingSet = Filter.useFilter(trainingSet, filter);
			
			
			// Log transform
			
			//filter = new NumericTransform(); 
			//filter.setOptions(new String[] {"-M", "log" });
			//filter.setInputFormat(trainingSet);
			//trainingSet = Filter.useFilter(trainingSet, filter);
			
			
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException(); 
		}
		
		

		// Find weight column
		int weightColNum = WekaUtils.getIndexOfColumn(trainingSet, weightColName);
		//if (weightColNum == -1) throw new IllegalArgumentException("Error cannot locate " + weightColName + " column in " + trainingFile.getPath());
		
		
		
		// Make output directory
		outputDir = outputInput.get();
		if (outputDir.exists() && !outputDir.isDirectory()) throw new IllegalArgumentException("Error: output '" + outputDir.getPath() + "'  must be a directory");
		outputDir.mkdir();
		
	}
	
	@Override
	public void run() throws Exception {
		
		Log.warning("Building models...");
		Log.warning("Total Number of Instances: " + trainingSet.size());
		



		// Print out
		File tableOut = Paths.get(outputDir.getPath(), "correlation.tsv").toFile();
		Log.warning("Saving results to " + tableOut.getPath());
		PrintWriter writer = new PrintWriter(tableOut);
		writer.println("Algorithm\tCorrelation\tCorrelationReps");
		

		
		
		
		// Print the attributes
		System.out.print("Covariates: ");
		for (int attrNum  = 0;  attrNum < trainingSet.numAttributes(); attrNum ++) {
			Attribute attr = trainingSet.attribute(attrNum);
			if (attrNum == trainingSet.classIndex()) continue;
			System.out.print(attr.name());
			if (attrNum < trainingSet.numAttributes() - 1 & attrNum+1 != trainingSet.classIndex()) System.out.print(", ");
		}
		System.out.println();
		System.out.println("Target: " + trainingSet.classAttribute().name());
		
		
		for (Class<?> c : classifiers) {
			
			// Get the model
			Constructor<?> constructor = c.getConstructor();
			Classifier model = (Classifier) constructor.newInstance();
			String modelName = model.getClass().getSimpleName();
			
			
			double[][] fits = new double[4][];
			
			// 10-fold cross-validation while accounting for replicates
			double corr_reps = WekaUtils.kFoldCrossValidationReplicates(trainingSet, model, nfolds, fits);
			
			
			// Print tree to file
			if (model instanceof REPTree) {
				
				REPTree tree = new REPTree();
				Instances training2 = new Instances(trainingSet);
				WekaUtils.removeCol(training2, "xml");
				//tree.setOptions(new String[] { "-M", "10" });
				tree.buildClassifier(training2);
				File out = Paths.get(outputDir.getPath(), "tree.txt").toFile();
				Log.warning("Saving tree to " + out.getPath());
				PrintWriter pw = new PrintWriter(out);
				pw.println(tree.toString());
				pw.close();
			}

			
			
			// Print linear model to file
			if (model instanceof LinearRegression) {
				
				LinearRegression lm = new LinearRegression();
				Instances training2 = new Instances(trainingSet);
				WekaUtils.removeCol(training2, "xml");
				WekaUtils.removeCol(training2, "replicate");
				//tree.setOptions(new String[] { "-M", "" + minInstancesPerLeafInput.get() });
				lm.buildClassifier(training2);
				File out = Paths.get(outputDir.getPath(), "lm.txt").toFile();
				Log.warning("Saving linear model to " + out.getPath());
				PrintWriter pw = new PrintWriter(out);
				pw.println(lm.toString());
				pw.close();
			}
			
			
			// 10-fold cross validation without accounting for replicates
			double corr = WekaUtils.kFoldCrossValidationSafe(trainingSet, model, nfolds);
			
			
			Log.warning(modelName + ":");
			System.out.println("Cross-validation correlation: " + corr);
			System.out.println("Cross-validation correlation (reps): " + corr_reps);
			
			writer.println(modelName + "\t" + corr + "\t" + corr_reps);
			
				
			File out = Paths.get(outputDir.getPath(), modelName + ".tsv").toFile();
			Log.warning("Saving predictions to " + out.getPath());
			PrintWriter pw = new PrintWriter(out);
			pw.println("xml\treplicate\ttrue\tpred");
			for (int i = 0; i < fits[0].length; i ++) {
				pw.println(fits[0][i] + "\t" + fits[1][i] + "\t" + fits[2][i] + "\t" + fits[3][i]);
			}
			pw.close();

			

			
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
		WekaUtils.removeCol(data, "dated"); // Temp
		WekaUtils.removeCol(data, "partition.model"); // Temp
		//WekaUtils.removeCol(data, "replicate");
		WekaUtils.removeCol(data, "dataset");
		WekaUtils.removeCol(data, "nstates");
		WekaUtils.removeCol(data, "runtime.smooth.hr");
		WekaUtils.removeCol(data, "runtime.smooth.hr.m");
		WekaUtils.removeCol(data, "runtime.raw.hr");
		WekaUtils.removeCol(data, "runtime.raw.hr.m");
		WekaUtils.removeCol(data, "ESS.mean.m");
		WekaUtils.removeCol(data, "ESS.sd.m");
		WekaUtils.removeCol(data, "ESS.cov.m");
		WekaUtils.removeCol(data, "ESS.mean.hr");
		WekaUtils.removeCol(data, "ESS.sd.hr");
		WekaUtils.removeCol(data, "ESS.cov.hr");	
		WekaUtils.removeCol(data, "Pmean");	
	
		// Remove all poem weights and esses except for the current ess
		List<Attribute> poems = WekaUtils.getAttributesWithSubstring(data, POEM.getESSColname(""));
		poems.addAll(WekaUtils.getAttributesWithSubstring(data, POEM.getWeightColname("")));
		for (Attribute attr : poems) {
			if (attr.name().equals(poemESSColname)) continue;
			if (attr.name().equals(poemWeightColname)) continue;
			if (attr.name().contains(".weight")) continue;
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