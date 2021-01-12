package poetry.tools;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.util.Log;
import beast.util.LogAnalyser;
import poetry.PoetryAnalyser;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import beast.app.util.Application;


@Description("Compute the minimum ESS from a log file and save it to a text file")
public class MinESS extends Runnable {
	
	
	
	final public Input<File> logfileInput = new Input<>("log", "The log file where the parameters are stored", Input.Validate.REQUIRED);
	final public Input<File> outInput = new Input<>("out", "The output file to save the value");
	final public Input<Integer> burninInput = new Input<>("burnin", "Burnin percentage for ESS computation (default 10)", 10);
	
	final public Input<String> skipInput = new Input<>("skip", "List of column names to skip, separated by ','. eg. -skip posterior,likelihood ");

	
	
	List<String> skip;
	File logFile;
	boolean verbose = true;
	double minESS;
	double meanESS;
	int nlogs;
	
	
	public MinESS() {
		
	}
	
	public MinESS(File logFile, String skip) {
		this.skip = new ArrayList<>(); 
		if (skip != null && !skip.isEmpty()) {
			String[] bits = skip.split(",");
			for (String bit : bits) this.skip.add(bit);
		}
		this.logFile = logFile;
		this.verbose = false;
	}
	

	

	@Override
	public void initAndValidate() {
		this.skip = new ArrayList<>(); 
		this.logFile = logfileInput.get();
		if (skipInput.get() != null) {
			String[] bits = skipInput.get().split(",");
			for (String bit : bits) this.skip.add(bit);
		}
		
	}

	@Override
	public void run() throws Exception {
		
		double minESS = Double.POSITIVE_INFINITY;
		
	
		if (!logFile.exists() || !logFile.canRead()) throw new IllegalArgumentException("Could not locate/read logfile " + logFile.getPath());
		LogAnalyser analyser = new LogAnalyser(logFile.getAbsolutePath(), this.burninInput.get(), true, null);
		
		
		
		// Get minimum ESS across all column names
		double meanESS = 0;
		int numCols = 0;
		for (String colname : analyser.getLabels()) {
			if (this.skip.contains(colname)) continue;
			if (colname.equals("Sample")) continue;
			double ESS = analyser.getESS(colname);
			if (this.verbose) Log.warning(colname + " has an ESS of " + (int) ESS);
			if (ESS < 0 || Double.isNaN(ESS)) continue;
			minESS = Math.min(minESS, ESS);
			meanESS += ESS;
			numCols ++;
		}
		
		if (minESS == Double.POSITIVE_INFINITY) minESS = 0;
		
		meanESS /= numCols;
		if (this.verbose) {
			Log.warning("There is a minimum ESS of " + (int) minESS);
			Log.warning("There is a mean ESS of " + (int) meanESS);
		}

		this.minESS = minESS;
		this.meanESS = meanESS;
		this.nlogs = analyser.getTrace(0).length;
		
		// Save it
		if (this.outInput.get() != null) {
			File outfile = outInput.get();
			if (outfile != null) {
				Log.warning("Saving to " + outfile.getPath());
				PrintWriter pw = new PrintWriter(outfile);
				pw.println(minESS);
				pw.println(meanESS);
				pw.close();
			}
		}
		
	}
	
	public double getMinESS() {
		return this.minESS;
	}
	
	public double getMeanESS() {
		return this.meanESS;
	}
	
	public int getNLogs() {
		return this.nlogs;
	}
	
	
	public static void main(String[] args) throws Exception {
		new Application(new MinESS(), "Compute ESS of a POEM", args);		
	}

}
