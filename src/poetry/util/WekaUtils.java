package poetry.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;

import beast.core.util.Log;
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
	 * Subsets the data based on xml column value and writes the subset to the specified file
	 * @param data
	 * @param xmls
	 * @param outfile
	 * @throws IOException
	 */
	public static void splitDataAndSaveArff(Instances data, List<Double> xmls, File outfile) throws IOException {
		
		// Generate the subset
		Instances subset = new Instances(data);
		subset.clear();
		for (double xml : xmls) {
			subset.addAll(getWithVal(data, "xml", xml));
		}
		
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
	 * Removes the column by name
	 * @param data
	 * @param name
	 */
	public static void removeCol(Instances data, String name) {
		
		
		int colNum = getIndexOfColumn(data, name);
		
		// If it exists then remove it
		if (colNum >= 0) {
			Log.warning("Removing attribute " + name);
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
	
	
	
}
