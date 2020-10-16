package poetry;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.math3.random.EmpiricalDistribution;

import beast.core.Input;
import beast.core.Runnable;
import beast.core.parameter.Map;
import beast.core.util.ESS;
import beast.core.util.Log;
import beast.util.LogAnalyser;
import poetry.sampler.POEM;
import poetry.util.Lock;
import poetry.util.RuntimeLoggable;


/**
 * Reads in poems, a log file, and a database
 * Computes the ESS of any parameters in the log file which are specified by the poems, and inserts them into the database
 * @author jdou557
 *
 */
public class PoetryAnalyser extends Runnable {

	
	final public Input<File> databaseFileInput = new Input<>("database", "The location of a the database (tsv)", Input.Validate.REQUIRED);
	final public Input<Integer> sampleNumInput = new Input<>("number", "The row number in the database of this sample", Input.Validate.REQUIRED);
	final public Input<List<POEM>> poemsInput = new Input<>("poem", "A map between operators and log outputs", new ArrayList<>());
	final public Input<File> runtimeLoggerInput = new Input<>("runtime", "Lof file containing runtimes");
	final public Input<Integer> burninInput = new Input<>("burnin", "Burnin percentage for ESS computation (default 10)", 10);

	
	
	boolean verbose;
	int sampleNum;
	int replicateNum;
	int rowNum;
	int burnin;
	File database;
	File databaseBackup;
	File runtimeLogfile;
	List<POEM> poems;
	HashMap<String, String[]> db;
	
	
	Lock lock;
	
	public PoetryAnalyser(int sampleNum, File database, List<POEM> poems, File runtimeLogfile) {
		this(sampleNum, database, poems, runtimeLogfile, 10);
	}
	
	public PoetryAnalyser(int sampleNum, File database, List<POEM> poems, File runtimeLogfile, int burnin) {
		this.sampleNum = sampleNum;
		this.replicateNum = this.getReplicateNumber();
		this.database = database;
		this.databaseBackup = new File(this.database.getPath() + ".bu");
		this.poems = poems;
		this.runtimeLogfile = runtimeLogfile;
		this.burnin = burnin;
		this.verbose = false;
		
		// File validation
		if (!this.database.exists()) throw new IllegalArgumentException("Could not locate database " + this.database.getPath());
		this.lock = new Lock(this.sampleNum + " " + this.replicateNum, this.database, this.verbose);
		
	}
	
	
	
	@Override
	public void initAndValidate() {
		
		this.sampleNum = sampleNumInput.get();
		this.database = databaseFileInput.get();
		this.databaseBackup = new File(this.database.getPath() + ".bu");
		this.replicateNum = this.getReplicateNumber();
		this.poems = poemsInput.get();
		this.runtimeLogfile = runtimeLoggerInput.get();
		this.burnin = burninInput.get();
		this.rowNum = -1;
		this.verbose = true;
		
		//if (this.poems.isEmpty()) throw new IllegalArgumentException("Please provide at least 1 poem");
		
		// File validation
		if (!this.database.exists()) throw new IllegalArgumentException("Could not locate database " + this.database.getPath());
		this.lock = new Lock(this.sampleNum + " " + this.replicateNum, this.database, this.verbose);
		
	}
	
	public List<POEM> getPoems(){
		return this.poems;
	}

	@Override
	public void run() throws Exception {
		
		
		// File validation
		if (this.runtimeLogfile != null && !this.runtimeLogfile.exists()) throw new IllegalArgumentException("Could not locate runtime log " + this.runtimeLogfile.getPath());
		
		// Runtime 
		int nstates = 0;
		double smoothRuntime = 0, rawRuntime = 0;
		if (this.runtimeLogfile != null && getNumLineInFile(this.runtimeLogfile) > 5) {
	
			if (this.verbose) Log.warning("Computing runtime...");
			LogAnalyser analyser = new LogAnalyser(this.runtimeLogfile.getAbsolutePath(), this.burnin, true, null); 
			
			// nstates
			Double[] sampled = analyser.getTrace("Sample");
			nstates = (int) Math.floor(sampled[sampled.length-1]);
			int nsamples = nstates / (int) (sampled[sampled.length-1] - sampled[sampled.length-2]);
			if (this.verbose) System.out.println("There are " + nstates + " states and " + nsamples + " samples");
			
			
			// Actual runtime
			Double[] cumulative = analyser.getTrace(RuntimeLoggable.getCumulativeColname());
			rawRuntime = cumulative[cumulative.length-1] / 3600;
			
			// Smooth runtime
			smoothRuntime = getSmoothRuntime(analyser.getTrace(RuntimeLoggable.getIncrementalColname()), nsamples); //cumulative[cumulative.length-1]  / 3600;
			smoothRuntime = smoothRuntime / 3600000;
			if (this.verbose) Log.warning("Total runtime (raw) is " + rawRuntime + "hr and runtime (smoothed) is " + smoothRuntime + "hr");
			
		}
		
	
		
		// Open logfile using loganalyser
		if(this.verbose) Log.warning("Computing ESSes...");
		
		// Calculate ESS for each POEM
		for (POEM poem : this.poems) {
			
			File logFile = new File(poem.getLoggerFileName());
			
			if (getNumLineInFile(logFile) <= 5) continue;
			
			if (!logFile.exists() || !logFile.canRead()) throw new IllegalArgumentException("Could not locate/read logfile " + logFile.getPath());
			LogAnalyser analyser = new LogAnalyser(logFile.getAbsolutePath(), this.burnin, true, null);
			
			
			// Get minimum ESS across all relevant column names
			double minESS = Double.POSITIVE_INFINITY;
			for (String colname : analyser.getLabels()) {

				double ESS = analyser.getESS(colname);
				if (ESS < 0 || Double.isNaN(ESS)) continue;
				minESS = Math.min(minESS, ESS);
			}
			if (this.verbose) System.out.println(poem.getID() + " has a minimum ESS of " + (int) minESS);
			
			poem.setMinESS(minESS);
			
			
		}
		
		double[] ESSstats = POEM.getESSStats(this.poems);
		if (this.verbose) System.out.println("ESS coefficient of variation is " + ESSstats[2]);
		
		
		
		// Save the new row to the database. This will lock the database file to avoid conflicts
		if(this.verbose) Log.warning("Saving to database...");
		this.updateDatabase(ESSstats, smoothRuntime, rawRuntime, nstates);
		
		
		if(this.verbose) Log.warning("Done!");
		
	}
	
	
	/**
	 * Get the replicate number of this by examining the current working directory
	 * eg. if the pwd is out/xml2/replicate7/ then the replicate number is 7
	 * @return
	 */
	public int getReplicateNumber() {
		
		String pwd = System.getProperty("user.dir");
		String[] split = pwd.split("/");
		String replicate = split[split.length-1];
		try {
			int replicateNum = Integer.parseInt(replicate.replace("replicate", ""));
			System.out.println("Poetry replicate number: " + replicateNum);
			return replicateNum;
		}catch(Exception e) {
			Log.warning("Error: cannot find replicate number. Please ensure your working directory is within "
					+ "the the replicateX folder where X is the replicate number. pwd: " + pwd);
			throw e;
		}
		
	}
	
	/**
	 * Same as getSmoothRuntime(double[] incrTrace) except this works on Double[])
	 * @param incrTrace - a Double array with capital D
	 * @param nsamples - number of time samples (including burnin)
	 * @return
	 */
	public static double getSmoothRuntime(Double[] incrTrace, int nsamples) {
		
		// Convert Double[] to double[]
		double[] arr = new double[incrTrace.length];
		for (int i = 0; i < arr.length; i ++) {
			arr[i] = (double) incrTrace[i];
		}
		return getSmoothRuntime(arr, nsamples);
	}
	
	/**
	 * Calculates the total runtime of this system by multiplying the 
	 * mode incremental runtime by the number of reported times.
	 * This can help when a cluster is used and thread interrupting is common
	 * @param incrTrace - a double array with little d
	 * @param nsamples - number of time samples (including burnin)
	 * @return
	 */
	public static double getSmoothRuntime(double[] incrTrace, int nsamples) {
		
		
		if (incrTrace == null || incrTrace.length == 0) return 0;
		final int nbins = 1 + (int) Math.sqrt(incrTrace.length); // incrTrace.length / 2;

		
		// Build kernel density estimation
		EmpiricalDistribution kde = new EmpiricalDistribution(nbins);
		kde.load(incrTrace);
		
		
		//double y = kde.density(4500.0);
		
		double xMax = 0; // x-value of mode
		double pMax = 0; // density of mode
		
		// Find the mode by iterating through quantiles
		final int nquant = 1000;
		for (int i = 1; i < nquant; i++) {
			
			
			
			double quantile = 1.0 * i/nquant;
			try {
				
				double x = kde.inverseCumulativeProbability(quantile);
				double p = kde.density(x);
			
			//	System.out.println(quantile + "\t" + x + "\t" + p);
				
				// Found the mode?
				if (p > pMax) {
					pMax = p;
					xMax = x;
				}
				
			} catch (Exception e) {
				
			}
			
		}
		
		
		// Assume that the incremental runtime of the mode is the true mean 
		// if there were no interruptions
		//System.out.println("Modal time per sample: " + xMax);
		return xMax * nsamples;
		
	}
	
	
	/**
	 * Number of lines in a file
	 * @param file
	 * @return
	 * @throws Exception
	 */
	public static long getNumLineInFile(File file) throws Exception {
		
		
		try(
		   FileReader fr = new FileReader(file);
		   LineNumberReader count = new LineNumberReader(fr);
		)
		{
		   while (count.skip(Long.MAX_VALUE) > 0)
		   {
		      // Loop just in case the file is > Long.MAX_VALUE or skip() decides to not read the entire file
		   }
		   
		   fr.close();
		   
		   //System.out.println(file.getAbsolutePath() + " has " + (count.getLineNumber() + 1) + "lines");
		   return count.getLineNumber() + 1;  // +1 because line index starts at 0
		}
		
	}
	

	
	/**
	 * Searches the database for weights under this sample number, across all replicates
	 * If the weights have not been sampled, then this process will sample the weights
	 * Otherwise, this process use the weights in the database
	 */
	public void correctWeights(double[] candidateWeights) throws Exception {
		
		
		if (candidateWeights.length != this.poems.size()) {
			throw new Exception("Dev error: candidateWeights length does not equal poems length " + candidateWeights.length + " != " + poems.size());
		}
		
		
		try {
			
			this.lock.lock();
			
			// Backup
			//Files.copy(this.database.toPath(), this.databaseBackup.toPath());
			
			
			// Open the database and find the right line
			this.db = this.openDatabase(this.database);
			this.rowNum = this.getRowNum(this.sampleNum, this.replicateNum);
			boolean initialised = this.getValueAtRow(POEM.getStartedColumn()).equals("true");
			
			
			// If it has not started, then set all of the others to true
			if (!initialised) {
				
				//RandomAccessFile raf = new RandomAccessFile(this.database, "rw");
				//FileChannel channel = raf.getChannel();
				
				Log.warning("Sampling POEM weights and saving to the database. All replicates of this xml file will use the same weights.");
				
				
				// Set 'started' to true and set weights for all replicates/poems in this xml file
				List<Integer> rowNums = this.getRowNums(this.sampleNum);
				for (int repRowNum : rowNums) {
					
						
					this.setValueAtRow(POEM.getStartedColumn(), repRowNum, "true");
					
					// Set poem weights and set started to true for all poems
					for (int i = 0; i < this.poems.size(); i ++) {
						POEM poem = this.poems.get(i);
						double weight = candidateWeights[i];
						this.setValueAtRow(poem.getWeightColname(), repRowNum, "" + weight);
					}
				}
				
				
				// Update the database
				this.updateDatabaseLines(rowNums, this.database, this.databaseBackup);
				
				
				// Validation. If something has gone wrong, discard the backup and return
				try {
					validateDatabase(this.databaseBackup);
					Files.move(this.databaseBackup.toPath(), this.database.toPath(), StandardCopyOption.REPLACE_EXISTING);
				}catch(Exception e) {
					
					e.printStackTrace();
					
					// Delete the backup
					this.databaseBackup.delete();
				}
				
				


				
			}else {
				
				Log.warning("Weights have already been set by another replicate in the database. Using those weights.");
				
				// The replicate has already started. Use its weights
				for (int i = 0; i < this.poems.size(); i ++) {
					POEM poem = this.poems.get(i);
					double weight = Double.parseDouble(this.getValueAtRow(poem.getWeightColname()));
					candidateWeights[i] = weight;
				}
				
			}
			
			
		} catch (Exception e) {
			this.lock.unlock();
			throw e;
		}
		
		
		this.lock.unlock();
		
	}
	
	
	
	/**
	 * Open the database file and ensure that everything is in order
	 * tab delimited, correct number of row/columns etc.
	 * Multiple processes writing to the same file in quick succession can cause issues on some OSs / clusters etc
	 * If the output is corrupted / incomplete then the new database should be discarded 
	 * @return
	 */
	protected boolean validateDatabase(File filename) throws Exception {
		
		try {
			this.db = this.openDatabase(filename);
			int rownumber = this.getRowNum(this.sampleNum, this.replicateNum);
			if (rownumber < 0) throw new Exception("Cannot locate row " + this.sampleNum + " replicate " + this.replicateNum + " in the database.");
		}catch(Exception e) {
			
			Log.warning("Error opening newly created database. The database may have been corrupted by the file writing system.");
			throw e;
			
		}
		
		return true;
	}
	
	
	/**
	 * Edits the database file by updating specified row number
	 * @param rowNum
	 * @throws IOException
	 */
	protected void updateDatabaseLine(int rowNum, File dbin, File dbout) throws IOException {
		List<Integer> rowNums = new ArrayList<Integer>();
		rowNums.add(rowNum);
		this.updateDatabaseLines(rowNums, dbin, dbout);
	}
	
	/**
	 * Edits the database file by updating all specified row numbers (fast)
	 * @throws IOException 
	 */
	protected void updateDatabaseLines(List<Integer> rowNums, File dbin, File dbout) throws IOException {
		
		//System.out.println("Writing to db...");
		
		List<String> fileContent = new ArrayList<>(Files.readAllLines(dbin.toPath(), StandardCharsets.UTF_8));
		for (int i = 1; i < fileContent.size(); i++) {
			
			Integer rowNum = i-1;
			if (rowNums.contains(rowNum)) {
				
				String line = "";
				for (String colname : this.db.keySet()) {
					line += this.db.get(colname)[rowNum] + "\t";
				}
				fileContent.set(i, line);
			}
		}

		Files.write(dbout.toPath(), fileContent, StandardCharsets.UTF_8);
		
		
		
	}
	
	
	
	
	/**
	 * Get the tsv string of the database (slow)
	 * @return
	 */
	protected String dbToString() {
		
		String out = "";
		for (String colname : this.db.keySet()) out += colname + "\t";
		out += "\n";
		int nrow = this.db.get("xml").length;
		for (int rowNum = 0; rowNum < nrow; rowNum ++) {
			for (String colname : this.db.keySet()) {
				out += this.db.get(colname)[rowNum] + "\t";
			}
			out += "\n";
		}
		
		return out;
	}
	
	
	private void updateDatabase(double[] ESSstats, double smoothRuntime, double rawRuntime, int nstates) throws Exception {
		
		
		// Lock the database
		lock.lock();
		
		try {
			

			// Open database and find the right line
			this.db = this.openDatabase(this.database);
			this.rowNum = this.getRowNum(this.sampleNum, this.replicateNum);
			if (this.rowNum < 0) throw new Exception("Cannot locate sample " + this.sampleNum + ", replicate " + this.replicateNum + " in the database");
			
			
			//Thread.sleep(2000);
			
			
			// Write ESSes and weights
			for (POEM poem : poems) {
				this.setValueAtRow(poem.getESSColname(), "" + poem.getMinESS());
				this.setValueAtRow(poem.getDimColName(), "" + poem.getDim());
				this.setValueAtRow(poem.getWeightColname(), "" + poem.getWeight());
			}
			
			// Save coefficient of variation
			this.setValueAtRow(POEM.getMeanColumnName(), "" + ESSstats[0]);
			this.setValueAtRow(POEM.getStddevColumnName(), "" + ESSstats[1]);
			this.setValueAtRow(POEM.getCoefficientOfVariationColumnName(), "" + ESSstats[2]);
			
			// Number of states
			this.setValueAtRow(POEM.getNumberOfStatesColumnName(), "" + nstates);
			
			// Save runtime
			this.setValueAtRow(POEM.getRuntimeSmoothColumn(), "" + smoothRuntime);
			this.setValueAtRow(POEM.getRuntimeRawColumn(), "" + rawRuntime);
			
			
			
			// Update the database at this row (save to the backup file)
			this.updateDatabaseLine(this.rowNum, this.database, this.databaseBackup);
			
			
			// Validation. If something has gone wrong, discard the backup and return
			try {
				validateDatabase(this.databaseBackup);
				Files.move(this.databaseBackup.toPath(), this.database.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}catch(Exception e) {
				
				e.printStackTrace();
				
				// Delete the backup
				this.databaseBackup.delete();
			}
			

			
			
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		
		// Unlock the database
		lock.unlock();
		
		
		
	}

	

	/**
	 * Gets the value at the specified column for this sampleNum in the database
	 * @param colname
	 * @return
	 */
	private String getValueAtRow(String colname) throws Exception {
		if (this.db == null || this.rowNum < 0) return null;
		if (!db.containsKey(colname)) throw new Exception("Cannot locate column " + colname + " in the database");
		String[] vals = db.get(colname);
		return vals[this.rowNum];
	}
	
	
	/**
	 * Set a value within this row of the database array
	 * @param colname
	 * @param value
	 * @throws Exception
	 */
	private void setValueAtRow(String colname, String value) throws Exception {
		this.setValueAtRow(colname, this.rowNum, value);
	}
	
	
	/**
	 * Set a value within specified row of the database array
	 * @param colname
	 * @param rowNumber
	 * @param value
	 * @throws Exception
	 */
	private void setValueAtRow(String colname, int rowNumber, String value) throws Exception {
		if (this.db == null || rowNumber < 0) return;
		if (!db.containsKey(colname)) throw new Exception("Cannot locate column " + colname + " in the database");
		String[] vals = db.get(colname);
		vals[rowNumber] = value;
	}
	
	
	/**
	 * Gets the row number associated with this sample number and replicate number
	 * @param sampleNum
	 * @param replicateNum
	 * @return
	 */
	public int getRowNum(int sampleNum, int replicateNum) {
		
		if (this.db == null) return -1;
		
		String sampleStr = "" + sampleNum;
		String replicateStr = "" + replicateNum;
		String[] ids = db.get(POEM.getXMLColumn() );
		String[] reps = db.get(POEM.getReplicateColumn() );
		for (int i = 0; i < ids.length; i ++) {
			if (sampleStr.equals(ids[i]) && replicateStr.equals(reps[i])) {
				return i;
			}
		}
		
		
		return -1;
		
	}
	
	
	/**
	 * Gets the row number associated with this sample number
	 * @param sampleNum
	 * @return
	 */
	public List<Integer> getRowNums(int sampleNum) {
		
		List<Integer> rowNums = new ArrayList<>();
		if (this.db == null) return rowNums;
		String sampleStr = "" + sampleNum;
		String[] ids = db.get(POEM.getXMLColumn() );
		for (int i = 0; i < ids.length; i ++) {
			if (sampleStr.equals(ids[i])) {
				rowNums.add(i);
			}
		}
		return rowNums;
		
	}
	
	
	
	/**
	 * Opens the database and returns it as a hashmap
	 * @return
	 * @throws Exception
	 */
	public LinkedHashMap<String, String[]> openDatabase(File filename) throws Exception {
		
		LinkedHashMap<String, String[]> map = new LinkedHashMap<String, String[]>();
		
		// Read headers
		Scanner scanner = new Scanner(filename);
		String[] headers = scanner.nextLine().split("\t");
		int ncol = headers.length;
		List<String[]> values = new ArrayList<String[]>();
		
		// Read in data
		int lineNum = 1;
		while (scanner.hasNextLine()) {
			
			lineNum ++;
			String line = scanner.nextLine();
			if (line.isEmpty()) continue;
			String[] spl = line.split("\t");
			if (spl.length == 0) continue;
			if (spl.length != ncol) {
				throw new Exception("Error: database has " + spl.length + " elements on file line " + lineNum + " but there should be " + ncol);
			}
			values.add(line.split("\t"));
			
		}
		
		
		// Build map
		int nrow = values.size();
		for (int colNum = 0; colNum < ncol; colNum ++) {
			String colname = headers[colNum];
			String[] vals = new String[nrow];
			for (int rowNum = 0; rowNum < nrow; rowNum ++) {
				String[] row = values.get(rowNum);
				String val;
				if (colNum > row.length -1) val = "NA";
				else val = row[colNum];
				vals[rowNum] = val;
			}
			map.put(colname, vals);
		}
		
		
		// Check the xml column exists
		if (!map.containsKey(POEM.getXMLColumn())) throw new IllegalArgumentException("Cannot locate '" + POEM.getXMLColumn() + "' column in database");
		
		// Check the replicate column exists
		if (!map.containsKey(POEM.getReplicateColumn())) throw new IllegalArgumentException("Cannot locate '" + POEM.getReplicateColumn() + "' column in database");
		
		scanner.close();
		
		return map;
		
	}
	

}











