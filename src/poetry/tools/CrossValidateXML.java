package poetry.tools;

import java.io.File;
import java.io.PrintWriter;

import beast.app.util.Application;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.util.Log;
import beast.core.Input.Validate;
import poetry.util.WekaUtils;
import weka.classifiers.functions.GaussianProcesses;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;


@Description("Tests a Gaussian Process on an arff file using xml-stratified cross-validation")
public class CrossValidateXML extends Runnable  {
	
	
	final public Input<File> datasetInput = new Input<>("arff", "arff file", Validate.REQUIRED);
	final public Input<String> targetFeatureInput = new Input<>("target", "target feature", Validate.REQUIRED);
	final public Input<Integer> nfoldsInput = new Input<>("nfolds", "number of 10-fold cross-validations to do", 1);
	final public Input<File> outInput = new Input<>("out", "text file to save cross-validation accuracy to", Validate.OPTIONAL);

	
	int nfolds;
	Instances dataset;
	
	@Override
	public void initAndValidate() {
		
		nfolds = nfoldsInput.get();
		if (nfolds < 1) nfolds = 1;
		
		// Validate
		File dataFile = datasetInput.get();
		if (!dataFile.canRead()) throw new IllegalArgumentException("Error reading data file at " + dataFile.getPath());
				
		
		// Read the dataset arffs
		try {
			Log.warning("Opening " + dataFile.getAbsolutePath());
			dataset = new DataSource(dataFile.getAbsolutePath()).getDataSet();
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Error opening arff file");
		}
		
		
		// Remove dataset column
		WekaUtils.removeCol(dataset, "dataset");
		
		
		// Print the attributes
		System.out.print("Covariates: ");
		for (int attrNum  = 0;  attrNum < dataset.numAttributes(); attrNum ++) {
			Attribute attr = dataset.attribute(attrNum);
			if (attrNum == dataset.classIndex()) continue;
			System.out.print(attr.name());
			if (attrNum < dataset.numAttributes() - 1 & attrNum+1 != dataset.classIndex()) System.out.print(", ");
		}
		System.out.println();
		
		
		
		// Set the class
		Attribute cls = dataset.attribute(targetFeatureInput.get());
		dataset.setClass(cls);
		System.out.println("Target: " + dataset.classAttribute().name());
		
		
	}

	@Override
	public void run() throws Exception {
		
		
		Log.warning("Running " + nfolds + "-times 10-fold cross-validation...");
		
		GaussianProcesses model = new GaussianProcesses();
		
		
		double corr_reps = WekaUtils.kFoldCrossValidationReplicates(dataset, model, nfolds, null);
		Log.warning("Cross-validation (xml stratifying): " + corr_reps);
		
		double corr = WekaUtils.kFoldCrossValidationSafe(dataset, model, nfolds);
		Log.warning("Cross-validation (no stratifying): " + corr);
		
		// Save to file?
		if (outInput.get() != null) {
			Log.warning("Saving to " + outInput.get().getPath());
			PrintWriter pw = new PrintWriter(outInput.get());
			pw.println("corr=" + corr);
			pw.println("corr_r=" + corr_reps);
			pw.close();
		}
		
		
	}
	
	
	
	public static void main(String[] args) throws Exception {
		new Application(new CrossValidateXML(), "Tests a Gaussian Process on an arff file using xml-stratified cross-validation", args);
	}
	
	

}











