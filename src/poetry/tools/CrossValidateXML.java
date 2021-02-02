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
	final public Input<File> outInput = new Input<>("out", "text file to save cross-validation accuracy to", Validate.OPTIONAL);

	
	final int nfolds = 10;
	Instances dataset;
	
	@Override
	public void initAndValidate() {
		
		
		
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
		
		// Set the class
		Attribute cls = dataset.attribute(targetFeatureInput.get());
		dataset.setClass(cls);
		Log.warning("Setting class to " + cls.name());
		
		
	}

	@Override
	public void run() throws Exception {
		
		
		Log.warning("Running " + nfolds + " cross-validation...");
		
		GaussianProcesses model = new GaussianProcesses();
		double corr_reps = WekaUtils.kFoldCrossValidationReplicates(dataset, model, nfolds, null);
		double corr = WekaUtils.kFoldCrossValidationSafe(dataset, model, nfolds);
		
		
		Log.warning("Cross-validation (no stratifying): " + corr);
		Log.warning("Cross-validation (xml stratifying): " + corr_reps);
		
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
