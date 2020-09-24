package xmlsimulator;

import java.io.File;
import java.io.FileNotFoundException;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;


import beast.core.BEASTObject;
import beast.core.Input;
import beast.core.Logger;
import beast.core.Runnable;
import beast.core.util.Log;
import beast.evolution.alignment.Alignment;
import beast.util.Randomizer;



public class SimulateXML extends Runnable {

	

	
	final public Input<Runnable> runnableInput = new Input<>("runner", "A runnable object (eg. mcmc)", Input.Validate.REQUIRED);
	
	final public Input<List<ModelSampler>> modelInput = new Input<>("model", "A component of the model. All of its data will be dumped into this xml.", new ArrayList<>());
	final public Input<Alignment> dataInput = new Input<>("data", "A dataset samplers for loading and sampling data. Optional", Input.Validate.OPTIONAL);
	final public Input<Integer> nsamplesInput = new Input<>("nsamples", "Number of xml files to produce (default 1)", 1);
	final public Input<List<XMLFunction>> functionsInput = new Input<>("function", "Functions which can be called during xml simulation", new ArrayList<>());
	final public Input<File> outFolderInput = new Input<>("out", "A folder to save the results into", Input.Validate.REQUIRED);
	final public Input<List<POEM>> poemsInput = new Input<>("poem", "A map between operators and log outputs", new ArrayList<>());
	
	
	
	final public Input<List<BEASTObject>> nodesInput = new Input<>("object", "Any beast object to be added into the main file "
			+ "(eg. StateNode, CalculationNode, Distribution, Operator)", new ArrayList<>());
	
	/*
	final public Input<List<Distribution>> posteriorInput = new Input<>("distribution", "probability distribution to sample over", new ArrayList<>() );
	final public Input<List<Operator>> operatorsInput = new Input<>("operator", "operator for generating proposals in MCMC state space", new ArrayList<>());
	final public Input<List<StateNode>> stateInput = new Input<>("state", "a state node", new ArrayList<>());
	final public Input<List<StateNodeInitialiser>> initialisersInput = new Input<>("init", "a state node initiliser for determining the start state", new ArrayList<>());
	*/
	
	
	int nsamples;
	Runnable runner;
	Alignment data;
	List<ModelSampler> modelElements;
	List<XMLFunction> functions;
	File outFolder;
	File dbFile;
	PrintStream dbOut;
	List<POEM> poems;
	
	
	
	@Override
	public void initAndValidate() {
		
		this.runner = runnableInput.get();
		this.nsamples = nsamplesInput.get();
		this.data = dataInput.get();
		this.modelElements = modelInput.get();
		this.functions = functionsInput.get();
		this.outFolder = outFolderInput.get();
		this.poems = poemsInput.get();
		
		// Ensure that runner already has an ID
		if (this.runner.getID() == null || this.runner.getID().isEmpty()) {
			throw new IllegalArgumentException("Please provide an id for the <runner /> element");
		}
		
		if (this.outFolder.exists()) {
			
			// Is it a directory
			if (!this.outFolder.isDirectory()) {
				throw new IllegalArgumentException(this.outFolder.getPath() + " is not a directory. Please provide a directory");
			}
			
			// Overwrite?
			if (Logger.FILE_MODE != Logger.LogFileMode.overwrite) {
				throw new IllegalArgumentException("Cannot write to " + this.outFolder.getPath() + " because it already exists. Perhaps use the -overwrite flag");
			}
			
		}
		
		// Make the folder
		else {
			if (!this.outFolder.mkdir()) {
				throw new IllegalArgumentException("Failed to create directory at " + this.outFolder.getPath());
			}
		}
		
		
		// Prepare database file
		this.dbFile = Paths.get(this.outFolder.getPath(), "database.tsv").toFile();
		try {
			this.dbOut = new PrintStream(this.dbFile);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Failed to create database at " + this.dbFile);
		}
		this.initDatabase();


	}
	
	
	@Override
	public void run() throws Exception {

		
		for (int sample = 1; sample <= this.nsamples; sample ++) {
		
			Log.warning("--------------------------------------------------");
			Log.warning("Sample " + sample);
			Log.warning("--------------------------------------------------\n");
			
			// Sample alignments
			if (this.data != null && this.data instanceof XMLSample) {
				DatasetSampler d = (DatasetSampler) this.data;
				d.reset();
			}
			
			// Sample model components
			for (ModelSampler model : this.modelElements) {
				model.reset();
			}
			
			
			// Sample operator weights
			this.sampleOperatorWeights();
			
			
			// MCMC or MC3?
			boolean useMC3 = Randomizer.nextBoolean();
			
			// Print the new xml
			String sXML = this.toXML(useMC3);
			this.writeXMLFile(sXML, sample);
			
			
			// Update database
			this.appendToDatabase(sample, useMC3);
			
		
		}
		
		Log.warning("Done!");
		
	}
	
	
	/**
	 * Sample weights for each operator
	 */
	protected void sampleOperatorWeights() {
		
		double[] weights = new double[this.poems.size()];
		double weightSum = 0;
		for (int i = 0; i < weights.length; i ++) {
			weights[i] = Randomizer.nextDouble();
			weightSum += weights[i];
		}
		
		// Normalise so they sum to 1
		for (int i = 0; i < weights.length; i ++) weights[i] = weights[i] / weightSum;
		
		
		// Set the weight of each poem
		for (int i = 0; i < weights.length; i ++) {
			this.poems.get(i).setWeight(weights[i]);
		}
		
		
	}
	
	
	/**
	 * Write the xml file in a folder indexed by its sample number
	 * @param xml
	 * @param sampleNum
	 * @return
	 * @throws Exception 
	 */
	protected void writeXMLFile(String xml, int sampleNum) throws Exception {
		
		// Path to subfolder. Build it if it does not exist
		File folder = Paths.get(this.outFolder.getPath(), "xml" + sampleNum).toFile();
		if (!folder.exists()) {
			if (!folder.mkdir()) throw new IllegalArgumentException("Cannot generate folder " + folder.getPath());
		}
		
		// Path to xml file
		Path path = Paths.get(folder.getPath(), "out.xml");
		
		// Write the file
		PrintStream out = new PrintStream(path.toFile());
		out.println(xml);
		out.close();
		
	}
	
	
	/**
	 * Get the output xml string 
	 * All elements will be placed inside the runnable
	 * @return
	 * @throws Exception 
	 */
	protected String toXML(boolean useMC3) throws Exception {
		
		
		XMLSimProducer producer = new XMLSimProducer();
		String sXML = producer.toXML(this);
		
		
		// xml comments
		String comments = "\n";
		
		
		/*  Rename the 'run' to 'runner'
		 *  The reason why it is named runner in the first place is to prevent
		 *  XMLParser from throwing an exception upon detecting two 'run' elements */
        Document doc = XMLUtils.loadXMLFromString(sXML);
        Element runner = XMLUtils.getElementById(doc, this.runner.getID());
        doc.renameNode(runner, runner.getNamespaceURI(), "run");
  
        
        
        // MC3?
        if (useMC3) {
        	comments += "Using coupled MCMC (i.e. MC3)\n";
        	runner.setAttribute("spec", "beast.coupledMCMC.CoupledMCMC");
        	runner.setAttribute("deltaTemperature", "0.05");
        	runner.setAttribute("chains", "4");
        	runner.setAttribute("resampleEvery", "10000");
        }else {
        	comments += "Using standard MCMC (i.e. MC2)\n";
        }
        


		// Move the alignment to the head if it is not already
        if (this.data != null) {
        	Element dataset = XMLUtils.getElementById(doc, this.data.getID());
			Element datasetParent = (Element) dataset.getParentNode();
			datasetParent.removeChild(dataset);
			runner.getParentNode().insertBefore(dataset, runner);
			if (!datasetParent.getNodeName().equals("beast")) {
				datasetParent.setAttribute(dataset.getNodeName(), "@" + this.data.getID());
			}
        }


 
        // Tidy the XML of all XMLSampler models (and get some comments)
		for (ModelSampler model : this.modelElements) {
			model.tidyXML(doc, runner, this.functions);
			comments += model.getComments() + "\n";
		}
		
		
		 // Tidy the XML of all XMLSampler datasets (and get some comments)
        if (this.data != null && this.data instanceof XMLSample) {
			DatasetSampler d = (DatasetSampler) this.data;
			d.tidyXML(doc, runner, this.functions);
			comments += d.getComments() + "\n";
		}
        
        
        
        // Operator weights
        for (POEM poem : this.poems) {
        	poem.tidyXML(doc, runner, this.functions);
        	comments += poem.getComments() + "\n";
        }
      
        
        
        // Replace this runnable element (and all of its children) with its runnable child
        Element run = XMLUtils.getElementById(doc, this.getID());
        Element parent = (Element) run.getParentNode();
        parent.removeChild(run);
        parent.appendChild(runner);
  
		
		// Add a comment citing the dataset
        Node element = doc.getFirstChild();
		Comment comment = doc.createComment(comments);
		element.getParentNode().insertBefore(comment, element);
		
		
		// Merge elements which share an id
		XMLUtils.mergeElementsWhichShareID(doc);
		
		
		// Sort elements by putting operators and loggers at the bottom of runnable
		List<Element> operators = XMLUtils.getElementsByName(runner, "operator");
		List<Element> loggers = XMLUtils.getElementsByName(runner, "logger");
		for (Element operator : operators) {
			runner.removeChild(operator);
			runner.appendChild(operator);
		}
		for (Element logger : loggers) {
			runner.removeChild(logger);
			runner.appendChild(logger);
		}
		
        sXML = XMLUtils.getXMLStringFromDocument(doc);
		return sXML;
		
	}
	
	
	
	/**
	 * Prepare header for the database
	 */
	protected void initDatabase() {
	
		
		// Data summary
		this.dbOut.print("xml\t");
		this.dbOut.print("dataset\t");
		this.dbOut.print("ntaxa\t");
		this.dbOut.print("nsites\t");
		this.dbOut.print("npatterns\t");
		this.dbOut.print("npartitions\t");
		this.dbOut.print("pgaps\t");
		this.dbOut.print("nchar\t");
		this.dbOut.print("dated\t");
		this.dbOut.print("NJtree.height\t");
		
		
		// Model summary
		for (ModelSampler model : this.modelElements) {
			this.dbOut.print(model.getID() + "\t");
		}
		

		
		// Operator weight summary
		this.dbOut.print("search.mode\t");
		for (POEM poem : this.poems) {
			this.dbOut.print(poem.getWeightColname() + "\t");
		}
		
		
		// ESS summary (ESS per million states)
		for (POEM poem : this.poems) {
			this.dbOut.print(poem.getESSColname() + "\t");
		}
		
		
		// Runtime (million states per hr)
		this.dbOut.print("runtime.M.hr\t");
		this.dbOut.println();
	
		
	}
	
	
	

	/**
	 * Prepare header for the database
	 */
	protected void appendToDatabase(int sampleNum, boolean isMC3) {
	
		String dataset = this.data == null ? "NA" : !(this.data instanceof DatasetSampler) ? "NA" : ((DatasetSampler)this.data).getFilePath();
		int npartitions = this.data == null ? 0 : !(this.data instanceof DatasetSampler) ? 1 : ((DatasetSampler)this.data).getNumPartitions();
		double pgaps = this.data == null ? 0 : !(this.data instanceof DatasetSampler) ? 0 : ((DatasetSampler)this.data).getProportionGaps();
		String datedTips = this.data == null ? "false" : !(this.data instanceof DatasetSampler) ? "NA" : "" + ((DatasetSampler)this.data).tipsAreDated();
		String treeHeight = this.data == null ? "0" : !(this.data instanceof DatasetSampler) ? "NA" : "" + ((DatasetSampler)this.data).getEstimatedTreeHeight();
		
		
		// Dataset summary
		this.dbOut.print(sampleNum + "\t"); // Sample number
		this.dbOut.print(dataset + "\t"); // Dataset folder 
		this.dbOut.print((this.data == null ? 0 : this.data.getTaxonCount()) + "\t"); // Taxon count
		this.dbOut.print((this.data == null ? 0 : this.data.getSiteCount()) + "\t"); // Site count
		this.dbOut.print((this.data == null ? 0 : this.data.getPatternCount()) + "\t"); // Pattern count
		this.dbOut.print(npartitions + "\t"); // Number of partitions
		this.dbOut.print(pgaps + "\t"); // Proportion of sites which are gaps
		this.dbOut.print((this.data == null ? 0 : this.data.getDataType().getStateCount()) + "\t"); // Number of characters (4 for nt, 20 for aa etc)
		this.dbOut.print(datedTips + "\t"); // Are the tips dated?
		this.dbOut.print(treeHeight + "\t"); // Estimate the tree height using neighbour joining
		
		
		// Model summary
		for (ModelSampler model : this.modelElements) {
			this.dbOut.print(model.getSampledID() + "\t");
		}
		
		// Operator weight summary
		this.dbOut.print((isMC3 ? "MC3" : "MC2") + "\t");
		for (POEM poem : this.poems) {
			this.dbOut.print(poem.getWeight() + "\t");
		}
		
		
		// ESS summary
		for (POEM poem : this.poems) {
			this.dbOut.print("?\t");
		}
		
		
		// Runtime (million states per hr)
		this.dbOut.print("?\t");

		this.dbOut.println();
		
	}
	
	
	

}








