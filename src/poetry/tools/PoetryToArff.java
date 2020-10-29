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
		
		// Remove unwanted columns
		removeCol(data, "replicate");
		removeCol(data, "dataset");
		removeCol(data, "nstates");
		removeCol(data, "runtime.smooth.hr");
		removeCol(data, "runtime.smooth.hr.m");
		removeCol(data, "runtime.raw.hr");
		removeCol(data, "runtime.raw.hr.m");
		removeCol(data, "ESS.mean.m");
		removeCol(data, "ESS.sd.m");
		removeCol(data, "ESS.cov.m");

		
		// The test/training split must ensure that no single xml file has replicates on either side of the partition
		List<Double> xmls = getVals(data, "xml");
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
		splitDataAndSaveArff(data, xmlTrain, trainingOut);
		
		// Generate test set and save
		splitDataAndSaveArff(data, xmlTest, testOut);
		

		
		Log.warning("Done!");
		
	}
	
	
	
	/**
	 * Subsets the data based on xml column value and writes the subset to the specified file
	 * @param data
	 * @param xmls
	 * @param outfile
	 * @throws IOException
	 */
	private static void splitDataAndSaveArff(Instances data, List<Double> xmls, File outfile) throws IOException {
		
		// Generate the subset
		Instances subset = new Instances(data);
		subset.clear();
		for (double xml : xmls) {
			subset.addAll(getWithVal(data, "xml", xml));
		}
		
		// Remove xml column
		removeCol(subset, "xml");
		
		// Save to .arff file
		ArffSaver saver = new ArffSaver();
		saver.setInstances(subset);
		saver.setFile(new File(outfile.getPath()));
		saver.writeBatch();
				
		
	}
	
	
	/**
	 * Returns list of instances which have this attribute value
	 * @param data
	 * @param attr
	 * @param val
	 * @return
	 */
	private static Collection<Instance> getWithVal(Instances data, String attr, double val) {
		
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
	private static List<Double> getVals(Instances data, String colname) {
	
		
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
	
	
	private static int getIndexOfColumn(Instances data, String name) {
		
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
	 * Removes the column by name
	 * @param data
	 * @param name
	 */
	private static void removeCol(Instances data, String name) {
		
		
		int colNum = getIndexOfColumn(data, name);
		
		// If it exists then remove it
		if (colNum >= 0) {
			Log.warning("Removing attribute " + name);
			data.deleteAttributeAt(colNum);
		}else {
			//Log.warning("Cannot remove attribute " + name + " because it does not exist");
		}
		
	}
	
	public static void main(String[] args) throws Exception {
		new Application(new PoetryToArff(), "Prepare poetry database for analysis by weka", args);
	}
	
	
	

}
