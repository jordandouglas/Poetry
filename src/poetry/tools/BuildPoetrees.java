package poetry.tools;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;

import beast.app.util.Application;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.util.Log;
import beast.util.Randomizer;
import poetry.sampler.POEM;
import poetry.util.WekaUtils;
import beast.core.Input.Validate;
import weka.classifiers.evaluation.Evaluation;
import weka.classifiers.trees.REPTree;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.converters.ArffSaver;
import weka.core.converters.ConverterUtils.DataSource;


@Description("Loads in a a training .arff file and outputs a decision tree for the specified POEM")
public class BuildPoetrees extends Runnable { 
	
	
	final public Input<String> poemNameInput = new Input<>("poem", "The name of the poem", Validate.REQUIRED);
	
	final public Input<File> trainingInput = new Input<>("training", "arff file containing the training data", Validate.REQUIRED);
	final public Input<File> testInput = new Input<>("test", "arff file containing the test data (optional)", Validate.OPTIONAL);
	final public Input<File> outputInput = new Input<>("out", "A directory where the outputs will be stored", Validate.REQUIRED);
	
	
	String poemName;
	//Attribute poemESSColumn;
	//Attribute poemWeightColumn;
	File outputDir;
	Instances trainingSet, testSet;

	@Override
	public void initAndValidate() {
		
		
		
		// Validate
		File trainingFile = trainingInput.get();
		File testFile = testInput.get();
		if (!trainingFile.canRead()) throw new IllegalArgumentException("Error reading training file at " + trainingFile.getPath());
		if (testFile != null && !testFile.canRead()) throw new IllegalArgumentException("Error reading training file at " + testFile.getPath());
		
		
		// Read the training/test arffs
		try {
			trainingSet = new DataSource(trainingFile.getAbsolutePath()).getDataSet();
			if (testFile != null) testSet = new DataSource(testFile.getAbsolutePath()).getDataSet();
			else testSet = null;
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Error opening arff file");
		}
		
		
		poemName = poemNameInput.get();

		
		// Find poem columns
		String poemESSColname = POEM.getESSColname(poemName);
		String poemWeightColname = POEM.getWeightColname(poemName);
		
		
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
			this.tidyColumns(poemESSColname);
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Error tidying attributes");
		}
		
		// Set the target class
		Attribute poemESSColumn = trainingSet.attribute(WekaUtils.getIndexOfColumn(trainingSet, poemESSColname));
		trainingSet.setClass(poemESSColumn);
		
		if (testSet != null) {
			poemESSColumn = testSet.attribute(WekaUtils.getIndexOfColumn(testSet, poemESSColname));
			testSet.setClass(poemESSColumn);
		}
			
		// Find weight column
		//colNum = WekaUtils.getIndexOfColumn(trainingSet, poemWeightColname);
		//if (colNum == -1) throw new IllegalArgumentException("Error cannot locate " + poemWeightColname + " column in " + trainingFile.getPath());
		//poemWeightColumn = trainingSet.attribute(colNum);
		
		
		
		
		
		
		
		
		// Make output directory
		outputDir = outputInput.get();
		if (outputDir.exists() && !outputDir.isDirectory()) throw new IllegalArgumentException("Error: output '" + outputDir.getPath() + "'  must be a directory");
		outputDir.mkdir();
		
	}
	
	@Override
	public void run() throws Exception {
		
		
		Log.warning("Building tree...");
		
		
		ArffSaver saver = new ArffSaver();
		saver.setInstances(trainingSet);
		saver.setFile(new File("test.arff"));
		saver.writeBatch();
		
		
		
		// Build the tree on training set
		REPTree tree = new REPTree();
		String[] opts = { "-L", "3" }; // Max depth
		tree.setOptions(opts);
		tree.buildClassifier(trainingSet);
		
		
		// Cross validation
		Evaluation eval = new Evaluation(trainingSet);
		eval.crossValidateModel(tree, trainingSet, 10, new Random(Randomizer.nextInt()));
		System.out.println(eval.toSummaryString("\nCV results\n======\n", false));
		
		
		// Test set
		if (testSet != null) {
			Evaluation eval2 = new Evaluation(testSet);
			eval2.evaluateModel(tree, testSet);
			System.out.println(eval2.toSummaryString("\nTest results\n======\n", false));
		}
		
		
		// Random forest
		RandomForest rf = new RandomForest();
		rf.setOptions(opts);
		rf.buildClassifier(trainingSet);
		eval.crossValidateModel(rf, trainingSet, 10, new Random(Randomizer.nextInt()));
		System.out.println(eval.toSummaryString("\nRF CV results\n======\n", false));
		
		
		// Test set
		if (testSet != null) {
			Evaluation eval2 = new Evaluation(testSet);
			eval2.evaluateModel(rf, testSet);
			System.out.println(eval2.toSummaryString("\nRF Test results\n======\n", false));
		}
		
		
		// Print tree to file
		File out = Paths.get(outputDir.getPath(), "tree.txt").toFile();
		Log.warning("Saving tree to " + out.getPath());
		PrintWriter pw = new PrintWriter(out);
		pw.println(tree.toString());
		pw.close();
		
		Log.warning("Done!");
		
	}

	
	/**
	 * Remove unwanted columns including those applicable to other poems
	 * Convert all data into the appropriate formats
	 * @throws Exception 
	 */
	protected void tidyColumns(String poemESSColname) throws Exception {
		
		Log.warning("Tidying attributes...");
		
		
		// Apply to test and training sets
		for (Instances data : new Instances[] { trainingSet, testSet }) {
			if (data == null) continue;
			
			// Remove unwanted columns
			WekaUtils.removeCol(data, "xml");
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
				if (attr.name().equals(poemESSColname)) continue;
				WekaUtils.removeCol(data, attr.name());
			}
		
			
			data.deleteStringAttributes();
			
			/**
			// Convert all strings to nominals
			Enumeration<Attribute> attributes = data.enumerateAttributes();
			Attribute attr;
			while (attributes.hasMoreElements()) {
				attr = attributes.nextElement();
				
				// Convert string to nominal and remove all entries which have 'NA'
				if (attr.isString()) {
					
					int colnum = WekaUtils.getIndexOfColumn(data, attr.name()) + 1;
					StringToNominal filter = new StringToNominal();
					//filter.setAttributeRange("" + colnum);
					filter.setInputFormat(data);
					
					String[] opts = { "-R", "" + colnum };
				    filter.setOptions(opts);
					
					data = Filter.useFilter(data, filter);
					
					Log.warning("Converting " + attr.name() + " to string");
				}
			}
			**/
			

	
		
		}
		
		
		
		
	}

	
	public static void main(String[] args) throws Exception {
		new Application(new BuildPoetrees(), "Build one decision tree per POEM", args);
	}
	

}