package poetry;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import beast.core.BEASTObject;
import beast.core.Input;
import beast.core.util.Log;
import beast.util.Randomizer;
import poetry.functions.XMLCondition;

public class WeightedFile extends BEASTObject {

	
	final public Input<String> fileInput = new Input<>("file", "The location of the file (can be zipped)", Input.Validate.REQUIRED);
	final public Input<Double> weightInput = new Input<>("weight", "The prior weight of this file being sampled (default 1)", 1.0);
	final public Input<String> descInput = new Input<>("desc", "A simple description of this model", Input.Validate.REQUIRED);
	
	final public Input<String> speciesInput = new Input<>("species", "Position(s) within taxa name which contain the species. List a series of integers"
			+ "(eg. 1,2,5) where count starts at 1. The taxon names will be split on the 'split' symbol. If this string is not specified, then"
			+ "there will be no species tree.");
	final public Input<String> speciesSplitInput = new Input<>("speciessplit", "Character to split taxon names by to determine species string (default '_')", "_");
	
	
	final public Input<String> dateInput = new Input<>("date", "Position(s) within taxa name which contain the date. List a series of integers"
			+ "(eg. 1,2,5) where count starts at 1. If this string is not specified, then tip dates will not be used.");
	final public Input<String> dateSplitInput = new Input<>("datesplit", "Character to split taxon names by to determine data string (default '_')", "_");
	
	final public Input<List<XMLCondition>> conditionsInput = new Input<>("condition", "Condition which must be met by another class in order for this to be sampled."
			+ "Conditions are considered as a conjunction", new ArrayList<XMLCondition>());
	
	
	
	File file;
	double weight;
	String desc;
	boolean hasSpeciesMap;
	boolean tipsAreDated;
	boolean wasSampled;
	int[] speciesSplitIndices;
	int[] dateSplitIndices;
	String speciesSplitOn;
	String dateSplitOn;
	
	List<File> createdTmpFiles;
	List<XMLCondition> conditions;
	
	
	
	
	@Override
	public void initAndValidate() {
		this.file = new File(fileInput.get());
		this.weight = weightInput.get();
		this.desc = descInput.get();
		this.speciesSplitOn = speciesSplitInput.get();
		this.dateSplitOn = dateSplitInput.get();
		this.hasSpeciesMap = speciesInput.get() != null && !speciesInput.get().isEmpty();
		this.tipsAreDated = dateInput.get() != null && !dateInput.get().isEmpty();
		
		this.wasSampled = false;
		
		// Get indices to read from split taxon strings
		if (this.hasSpeciesMap) {
			String[] bits = this.speciesInput.get().split(",");
			this.speciesSplitIndices = new int[bits.length];
			for (int i = 0; i < this.speciesSplitIndices.length; i ++) {
				this.speciesSplitIndices[i] = Integer.parseInt(bits[i]) - 1;
			}
		}
		
		// Get indices to read from split dates strings
		if (this.tipsAreDated) {
			String[] bits = this.dateInput.get().split(",");
			this.dateSplitIndices = new int[bits.length];
			for (int i = 0; i < this.dateSplitIndices.length; i ++) {
				this.dateSplitIndices[i] = 1; // TODO parse date
			}
		}
		
		// A list of tmp files made during the unzipping. These will all be deleted when WeightedFile.close() is called
		this.createdTmpFiles = new ArrayList<File>();
				
		
		//if (!file.exists()) throw new IllegalArgumentException("Cannot locate file " + file.getAbsolutePath());
		if (this.weight < 0) throw new IllegalArgumentException("Please set weight to at least 0");
		
		
		// Conditions
		this.conditions = this.conditionsInput.get();
		
	}
	

	
	/**
	 * Get the weight of sampling this file
	 * If a condition has not been met then returns 0
	 * @return
	 */
	public double getWeight() {
		
		for (XMLCondition condition : this.conditions) {
			if (!condition.eval()) return 0;
		}
		
		return this.weight;
	}
	
	public double setWeight(double weight) {
		return weight;
	}
	
	/**
	 * Returns the File path. This will unzip and store in a tmp directory if necessary
	 * Make sure to call close() after you're done with this file so the tmp directories can be deleted
	 * @return
	 */
	public File unzipFile() {
		return this.readZippedFile(this.file);
	}
	
	
	public String getFilePath() {
		return this.file.getPath();
	}
	
	/**
	 * Deletes all tmp files made during the unzipping process
	 * If this function is not called, then tmp files will acumulate
	 */
	public void close() {
		WeightedFile.deleteFiles(this.createdTmpFiles);
	}
	
	
	public String getDesc() {
		return this.desc;
	}
	
	
	/**
	 * Flag this as being sampled
	 */
	public void flagSampled() {
		this.wasSampled = true;
	}
	
	/**
	 * Was this file sampled in this iteration?
	 * @return
	 */
	public boolean wasSampled() {
		return this.wasSampled;
	}
	
	
	
	/**
	 * Reset in between sampling files
	 */
	public void reset() {
		this.wasSampled = false;
	}
	

	/**
	 * Returns the directory of this file
	 * If the file is unzipped, a temporary file will be created
	 * @param file
	 * @return
	 */
	public File readZippedFile(File file) {
		
		
		
		
		String[] pathEles = file.getPath().split("/");
		
		String cumulativePath = "";
		for (int i = 0; i < pathEles.length; i ++) {
			
			String ele = pathEles[i];
			if (ele.isEmpty()) continue;
			

			// Is this zipped?
			try {
				//
				//boolenew InputStream(archive)an zipped = type.equals("application/zip");
				//ZipFile zipFile = new ZipFile(cumulativePath + ele);
				//int x = 5;
				
				File archive = new File(cumulativePath + ele);
				if (!archive.isDirectory()){
					
					
					// Found the file
					if (WeightedFile.isTextFile(archive)) {
						
						return archive;
					}
					
					// Unzipping is needed
					else {
						
						String path = archive.getPath();
						while (!(new File(path).isDirectory()) && !WeightedFile.isTextFile(new File(path))) {
							InputStream stream = WeightedFile.getStream(path);
							File newTmpFile = new File(WeightedFile.getTmpFileDir(cumulativePath));
							
							// Extract
							WeightedFile.extract(stream, newTmpFile);
							
							
							this.createdTmpFiles.add(newTmpFile);
							path = newTmpFile.getPath();
							stream.close();
						}
						
						
					}
					
					cumulativePath += createdTmpFiles.get(createdTmpFiles.size()-1);
					
					
					
					
				}else {
					cumulativePath += ele;
				}
				

				
			} catch (Exception e) {
				e.printStackTrace();
				WeightedFile.deleteFiles(createdTmpFiles);
				throw new IllegalArgumentException("Error opening " + file.getAbsolutePath());
			}
			
			
			if (i < pathEles.length - 1) cumulativePath += "/";
			
			
		}
		
		
		// Delete tmp files
		this.close();
		
		throw new IllegalArgumentException("Cannot open " + file.getAbsolutePath() + ". Please ensure the terminal file is a plain text file");
		
	}
	
	
	/**
	 * Deletes the list of files
	 * @param files
	 */
	public static void deleteFiles(List<File> files) {
		
		for (File file : files) {
			
			if (file.isDirectory()) {
				
				File[] allContents = file.listFiles();
			    if (allContents != null) {
			        for (File file2 : allContents) {
			        	deleteDirectory(file2);
			        }
			    }
				
			}
			
			file.delete();
		}
	}
	
	
	
	public static void deleteDirectory(File directoryToBeDeleted) {
	    File[] allContents = directoryToBeDeleted.listFiles();
	    if (allContents != null) {
	        for (File file : allContents) {
	            deleteDirectory(file);
	        }
	    }
	    directoryToBeDeleted.delete();
	}
	
	/**
	 * Opens the file as an inputstream. The file may be a text file or compressed as .zip or .tar or .gz
	 * @param filePath
	 * @return
	 * @throws Exception
	 */
	public static InputStream getStream(String filePath) throws Exception {
		
		String type = Files.probeContentType(Paths.get(filePath));
		switch (type) {
		
		
			// Gunzip 
			case "application/x-compressed-tar":{
				InputStream stream = new GZIPInputStream(new FileInputStream(filePath));
				return stream;
				
			}
			
			
			// Tarball 
			case "application/x-tar":{
				
				final InputStream is = new FileInputStream(filePath); 
				final TarArchiveInputStream stream = (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream("tar", is);
				return stream;
				//InputStream stream = new TarInputStream(new FileInputStream(filePath));
				//return stream;
				//WeightedFile.gunzip(stream, cumulativePath + "tmp");
			}
			
			
			// Zipped
			//case "application/zip":{
				//InputStream stream = new ZipInputStream(new FileInputStream(filePath));
				//return stream;
			//}
			
		
			// Plain text file 
			case "text/plain":{
				InputStream stream = new FileInputStream(filePath);
				return stream;
			}
			
			
			// XML file
			case "application/xml":{
				InputStream stream = new FileInputStream(filePath);
				return stream;
			}
			
			
			default: {
				throw new IllegalArgumentException("Error opening " + filePath + ". Please ensure this is a text file or .xml or .tar or .gz");
			}
		
		}
		
		
	}
	
	
	
	/**
	 * Checks whether the file is a simple text file (as opposed to a folder/zipped file)
	 * @param filePath
	 * @return
	 * @throws IOException
	 */
	public static boolean isTextFile(File file) throws IOException {
		String type = Files.probeContentType(Paths.get(file.getPath()));
		return type.equals("text/plain") || type.equals("application/xml");
	}
	
	
	
	
	/**
	 * Gunzip / untar a compressed archive and save into 'outFile'
	 * @param zipIn
	 * @param outFile
	 * @throws Exception 
	 */
	public static void extract(InputStream zipIn, File outFile) throws Exception {
		
		if (zipIn instanceof GZIPInputStream) gunzip((GZIPInputStream)zipIn, outFile);
		//else if (zipIn instanceof ZipInputStream) unzip((ZipInputStream)zipIn, outFile);
		else if (zipIn instanceof TarArchiveInputStream) untar((TarArchiveInputStream)zipIn, outFile);
		
	}
	
	/**
	 * Gunzip a compressed archive and save into 'outFile'
	 * @param zipIn
	 * @param outFile
	 * @throws IOException
	 */
	private static void gunzip(GZIPInputStream zipIn, File outFile) throws IOException {
		final int BUFFER_SIZE = 4096;
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outFile));
        byte[] bytesIn = new byte[BUFFER_SIZE];
        int read = 0;
        while ((read = zipIn.read(bytesIn)) != -1) {
            bos.write(bytesIn, 0, read);
        }
        bos.close();
    }
	
	

	
	/**
	 * Untar 
	 * @param inputFile
	 * @param outFile
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws ArchiveException
	 */
	public static void untar(final TarArchiveInputStream debInputStream, final File outFile) throws FileNotFoundException, IOException, ArchiveException {


	    TarArchiveEntry entry = null; 
	    while ((entry = (TarArchiveEntry)debInputStream.getNextEntry()) != null) {
	        final File outputFile = new File(outFile, entry.getName());
	        if (entry.isDirectory()) {
	            if (!outputFile.exists()) {
	                if (!outputFile.mkdirs()) {
	                    throw new IllegalStateException(String.format("Couldn't create directory %s.", outputFile.getAbsolutePath()));
	                }
	            }
	        } else {
	            final OutputStream outputFileStream = new FileOutputStream(outputFile); 
	            IOUtils.copy(debInputStream, outputFileStream);
	            outputFileStream.close();
	        }
	    }

	}

	
	
	/**
	 * Get a unique filename in this directory 
	 * @param path
	 * @return
	 */
	public static String getTmpFileDir(String path) {
		
		String name = "tmp";
		File file = new File(path + "/" + name);
		if (file.exists()) {
			int k = 0;
			while (file.exists()) {
				k++;
				file = new File(path + "/" + name + k);
			}
			name = name + k;
		}
		
		return path + "/" + name;
		
	}
	
	
	/**
	 * Samples a weighted file from a list of files
	 * Resets all files first and flags the sampled one as sampled
	 * @param weightedFiles
	 * @return
	 */
	public static WeightedFile sampleFile(List<WeightedFile> weightedFiles) {
		
		// Reset all files
		for (WeightedFile file : weightedFiles) file.reset();
		

		// Sum the weights
		double weightSum = 0;
		for (int i = 0; i < weightedFiles.size(); i ++) {
			weightSum += weightedFiles.get(i).getWeight();
		}
		
		if (weightSum == 0) {
			Log.warning("Warning: cannot sampled a WeightedFile because the weight sum is 0. Perhaps all conditions have failed?");
			return null;
		}
		
		
		// Get cumulative probability vector
		double cumulativeWeight = 0;
		double[] weights = new double[weightedFiles.size()];
		for (int i = 0; i < weightedFiles.size(); i ++) {
			double weight = weightedFiles.get(i).getWeight() / weightSum;
			cumulativeWeight += weight;
			weights[i] = cumulativeWeight;
		}
		
		// Sample a file
		int fileNum = Randomizer.randomChoice(weights);
		WeightedFile sampled = weightedFiles.get(fileNum);
		sampled.flagSampled();
		return sampled;
		
		
	}



	/**
	 * Get a species name from the taxon name using the string splitting rules supplied in the input
	 * @param taxon
	 * @return
	 */
	public String getSpecies(String taxon) {
		if (!this.hasSpeciesMap) return null;
		//System.out.println(taxon);
		String[] bits = taxon.split(this.speciesSplitOn);
		String species = "";
		for (int i = 0; i < this.speciesSplitIndices.length; i ++) {
			int index = this.speciesSplitIndices[i];
			if (index >= bits.length) {
				throw new IllegalArgumentException("Cannot parse species from " + taxon + " at position " + index + " because there are only " + bits.length + " bits");
			}
			String bit = bits[index];
			species += bit;
			if (i < this.speciesSplitIndices.length - 1) species += this.speciesSplitOn;
			
		}
		return species;
	}



	/**
	 * Whether or not there are rules for extracting species from taxon names
	 * @return
	 */
	public boolean hasSpeciesMap() {
		return this.hasSpeciesMap;
	}
	
	
	/**
	 * Whether or not tips are dated
	 * @return
	 */
	public boolean tipsAreDated() {
		return this.tipsAreDated;
	}
	
	
}
