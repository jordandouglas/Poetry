package poetry.tools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;

import beast.app.util.Application;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.util.Log;
import beast.util.Randomizer;
import poetry.util.WekaUtils;
import beast.core.Input.Validate;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffSaver;
import weka.core.converters.ConverterUtils.DataSource;


@Description("Loads in a poetry database file (csv), splits into a training and test set and outputs two .arff files")
public class PoetryToArff extends Runnable { 
	
	final public Input<File> databaseInput = new Input<>("database", "csv file containing the data", Validate.REQUIRED);
	final public Input<Double> splitInput = new Input<>("split", "percentage of data which will be in the training set (default: 70)", 70.0);
	
	
	File database, trainingOut, testOut;
	double splitPercentage;
	String[] columnsToRemove;
	

	@Override
	public void initAndValidate() {
		database = databaseInput.get();
		if (!database.canRead()) throw new IllegalArgumentException("Error reading database at " + database.getPath());
		
		// Output files are in same directory as input file
		String[] spl = database.getAbsolutePath().split("[.]");
		if (!spl[spl.length-1].equals("csv")) throw new IllegalArgumentException("Please ensure the database is a csv file with a .csv extension");
		
		// Get filebase
		String str = "";
		for (int i = 0; i < spl.length-1; i ++) {
			str += spl[i];
			if (i < spl.length - 2) str += "/";
		}
		trainingOut = new File(str + ".training.arff");
		testOut = new File(str  + ".test.arff");
		
	

		// Training test percentage split
		splitPercentage = splitInput.get();
		if (splitPercentage < 0 || splitPercentage > 100) throw new IllegalArgumentException("Please ensure that split is between 0 and 100");
		
		// Columns to remove
		//columnsToRemove = new String[] {"xml", "replicate", "dataset", "nstates", "runtime.smooth.hr", "runtime.smooth.hr.m", "runtime.raw.hr", "runtime.raw.hr.m"};
		
		
	}

	@Override
	public void run() throws Exception {
		
		Log.warning("Writing training data to " + trainingOut.getPath());
		Log.warning("Writing test data to " + testOut.getPath());
		
		
		// Read in the database
		DataSource source = new DataSource(database.getAbsolutePath());
		Instances data = source.getDataSet();


		
		// The test/training split must ensure that no single xml file has replicates on either side of the partition
		List<Double> xmls = WekaUtils.getVals(data, "xml");
		xmls = xmls.stream().distinct().collect(Collectors.toList()); // Remove duplicates
		int trainingSize = (int) Math.floor(xmls.size() * this.splitPercentage/100);
		
		// Sample an xml split
		List<Double> xmlTrain = new ArrayList<>();;
		List<Double> xmlTest = new ArrayList<>();
		for (double xml : xmls) {
			
			// Test or training?
			boolean training;
			if (xmlTrain.size() >= trainingSize) training = false;
			else if (xmlTest.size() >= xmls.size() - trainingSize) training = true;
			else training = Randomizer.nextFloat() < this.splitPercentage/100;
			
			if (training) {
				xmlTrain.add(xml);
			}else {
				xmlTest.add(xml);
			}
		}
		Log.warning("Splitting the " + xmls.size() + " xml files into a " + xmlTrain.size() + "/" + xmlTest.size() + " split");
		
		
		// Generate training set and save
		WekaUtils.splitDataAndSaveArff(data, xmlTrain, trainingOut);
		
		// Generate test set and save
		WekaUtils.splitDataAndSaveArff(data, xmlTest, testOut);
		

		
		Log.warning("Done!");
		
	}
	
	
	
	public static void main(String[] args) throws Exception {
		new Application(new PoetryToArff(), "Prepare poetry database for analysis by weka", args);
	}
	
	
	

}
