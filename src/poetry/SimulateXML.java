package poetry;

import java.io.File;
import java.io.FileNotFoundException;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

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
import poetry.functions.XMLFunction;
import poetry.functions.XMLInputSetter;
import poetry.learning.DirichletSampler;
import poetry.operators.PoetryScheduler;
import poetry.sampler.DatasetSampler;
import poetry.sampler.ModelSampler;
import poetry.sampler.POEM;
import poetry.sampler.RunnableSampler;
import poetry.sampler.XMLSampler;
import poetry.util.Lock;
import poetry.util.RuntimeLoggable;
import poetry.util.XMLUtils;




public class SimulateXML extends Runnable {

	
	private static final String DATABASE_FILENAME = "database.tsv";
	private static final String RUNTIME_LOGNAME = "runtime.log";

	

	final public Input<Runnable> runnableInput = new Input<>("runner", "A runnable object (eg. mcmc)", Input.Validate.REQUIRED);
	
	final public Input<List<ModelSampler>> modelInput = new Input<>("model", "A component of the model. All of its data will be dumped into this xml.", new ArrayList<>());
	final public Input<Alignment> dataInput = new Input<>("data", "A dataset samplers for loading and sampling data. Optional", Input.Validate.OPTIONAL);
	final public Input<Integer> nsamplesInput = new Input<>("nsamples", "Number of xml files to produce (default 1)", 1);
	final public Input<List<XMLFunction>> functionsInput = new Input<>("function", "Functions which can be called during xml simulation", new ArrayList<>());
	final public Input<File> outFolderInput = new Input<>("out", "A folder to save the results into", Input.Validate.REQUIRED);
	final public Input<List<POEM>> poemsInput = new Input<>("poem", "A map between operators and log outputs", new ArrayList<>());
	
	
	final public Input<Integer> updateEveryInput = new Input<>("updateEvery", "How often to update the database with weights/ESSes "
			+ "(default: only at the end of the chain)", 0);
	
	
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
	int updateEvery;
	
	//Document poetry;
	
	
	
	@Override
	public void initAndValidate() {
		
		this.runner = runnableInput.get();
		this.nsamples = nsamplesInput.get();
		this.data = dataInput.get();
		this.modelElements = modelInput.get();
		this.functions = functionsInput.get();
		this.outFolder = outFolderInput.get();
		this.poems = poemsInput.get();
		this.updateEvery = updateEveryInput.get();
		//this.poetry = null;
		
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
		this.dbFile = Paths.get(this.outFolder.getPath(), DATABASE_FILENAME).toFile();
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
			if (this.data != null && this.data instanceof XMLSampler) {
				DatasetSampler d = (DatasetSampler) this.data;
				d.reset();
			}
			
			// Sample model components
			for (ModelSampler model : this.modelElements) {
				model.reset();
			}
			

			
			// Sample a search algorithm
			if (this.runner instanceof RunnableSampler) ((RunnableSampler)this.runner).reset();
			
			
			// Print the new xml
			String sXML = this.toXML(sample);
			this.writeXMLFile(sXML, sample);
			
			
			// Update database
			this.appendToDatabase(sample);
			
		
		}
		
		Log.warning("Done!");
		
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
		
		
		// Write the poem file
		//Path poemPath = Paths.get(folder.getPath(), "poems.xml");
		//out = new PrintStream(poemPath.toFile());
		//out.println(XMLUtils.getXMLStringFromDocument(this.poetry));
		//out.close();
		
	}
	
	
	/**
	 * Get the output xml string 
	 * All elements will be placed inside the runnable
	 * @return
	 * @throws Exception 
	 */
	protected String toXML(int sampleNum) throws Exception {
		
		
		XMLSimProducer producer = new XMLSimProducer();
		String sXML = producer.toXML(this);
		
		
		// xml comments
		String comments = "\nXML sample " + sampleNum + "\n";
		
		
		// Parse the xml and get the runner element
        Document doc = XMLUtils.loadXMLFromString(sXML);
        Element runner = XMLUtils.getElementById(doc, this.runner.getID());
        Element run = XMLUtils.getElementById(doc, this.getID());
        

		// Move the alignment to the head if it is not already
        if (this.data != null) {
        	Element dataset = XMLUtils.getElementById(doc, this.data.getID());
			Element datasetParent = (Element) dataset.getParentNode();
			datasetParent.removeChild(dataset);
			run.getParentNode().insertBefore(dataset, run);
			if (!datasetParent.getNodeName().equals("beast")) {
				datasetParent.setAttribute(dataset.getNodeName(), "@" + this.data.getID());
			}
        }

        
        // Search mode
        if (this.runner instanceof RunnableSampler) {
        	RunnableSampler sampler = (RunnableSampler) this.runner;
        	sampler.tidyXML(doc, runner, this.functions);
        	comments += sampler.getComments() + "\n";
        	runner = XMLUtils.getElementById(doc, this.runner.getID());
        }
        



        // Tidy the XML of all XMLSampler models (and get some comments)
		for (ModelSampler model : this.modelElements) {
			model.tidyXML(doc, run, this.functions);
			comments += model.getComments() + "\n";
		}
		
		
		// Call any input setter functions
		for (XMLFunction function : this.functions) {
			if (function instanceof XMLInputSetter) {
				((XMLInputSetter)function).tidyXML(doc);
			}
		}
		
		

		 // Tidy the XML of all XMLSampler datasets (and get some comments)
        if (this.data != null && this.data instanceof XMLSampler) {
			DatasetSampler d = (DatasetSampler) this.data;
			d.tidyXML(doc, run, this.functions);
			comments += d.getComments() + "\n";
		}
        

 
        
        // Operator weights
        for (POEM poem : this.poems) {
        	poem.tidyXML(doc, runner, this.functions);
        	comments += poem.getComments() + "\n";
        }
      
		
    	// Generate poem operator schedule
		this.writePoems(sampleNum, doc, runner, run);
		
        
        // Replace this runnable element (and all of its children) with its runnable child
        Element parent = (Element) run.getParentNode();
        parent.removeChild(run);
        parent.appendChild(runner);
  
		
		// Add a comment citing the dataset
        Node element = doc.getFirstChild();
		Comment comment = doc.createComment(comments);
		element.getParentNode().insertBefore(comment, element);
		
		
	
		
		
		// Merge elements which share an id
		XMLUtils.mergeElementsWhichShareID(doc);
		


		
		// Create a temporal logger
		Element runtimeLogger = doc.createElement("logger");
		runner.appendChild(runtimeLogger);
		runtimeLogger.setAttribute("id", XMLUtils.getUniqueID(doc, "RuntimeLogger"));
		runtimeLogger.setAttribute("fileName", RUNTIME_LOGNAME);
		runtimeLogger.setAttribute("logEvery", "" + 10000);
		runtimeLogger.setAttribute("spec", Logger.class.getCanonicalName());
		Element runtimeLoggable = doc.createElement("log");
		runtimeLogger.appendChild(runtimeLoggable);
		runtimeLoggable.setAttribute("spec", RuntimeLoggable.class.getCanonicalName());
		runtimeLoggable.setAttribute("static", "true");
		runtimeLoggable.setAttribute("id", XMLUtils.getUniqueID(doc, "runtime"));
		
		
		
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
		
		
		
		
		// Final check: ensure there are no operators which are not affiliated with poems
		for (Element operator : operators) {
			
			int numPoems = 0;
			for (POEM poem : this.poems) {
				if (poem.getOperatorID().equals(operator.getAttribute("id"))) {
					numPoems ++;
				}
			}
			
			if (numPoems != 1) throw new Exception("Operator " + operator.getAttribute("id") + " must have exactly 1 POEMS but it has " + numPoems);
		}
		
		
		
        sXML = XMLUtils.getXMLStringFromDocument(doc);
		return sXML;
		
	}
	
	
	/**
	 * Write xml for a poetry scheduler
	 * @param doc
	 */
	private void writePoems(int sampleNum, Document doc, Element runner, Element run) throws Exception {
		
		// Create new document
		//DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
	   //DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
	    //this.poetry = dBuilder.newDocument();
	   // Element beast = (Element) this.poetry.importNode(XMLUtils.getElementsByName(doc, "beast").get(0), false);
	    
		
		
		
		// How often to update the database?
		String updateEvery2 = "" + this.updateEvery;
		if (this.updateEvery <= 0) {
			if (runner.hasAttribute("chainLength")) updateEvery2 = runner.getAttribute("chainLength");
			else updateEvery2 = "1000000";
		}
		
		
		
	    // Create the PoetryAnalyser runnable element and populate its inputs
	    Element scheduler = doc.createElement("operatorschedule");
	    scheduler.setAttribute("spec", PoetryScheduler.class.getCanonicalName());
	    scheduler.setAttribute("database", "../" + DATABASE_FILENAME);
	    scheduler.setAttribute("runtime", RUNTIME_LOGNAME);
	    scheduler.setAttribute("number", "" + sampleNum);
	    scheduler.setAttribute("updateEvery", updateEvery2);
	    runner.appendChild(scheduler);
	    
	    
	    // Use a dirichlet sampler initially
	    Element sampler = doc.createElement("sampler");
	    sampler.setAttribute("spec", DirichletSampler.class.getCanonicalName());
	    sampler.setAttribute("static", "true");
	    scheduler.appendChild(sampler);
	    
		
	    // Copy poems over from main doc
	    for (Element poem : XMLUtils.getElementsByName(run, "poem")) {
	    	
	    	// Only consider poems which have non-zero weight
	    	POEM poemObj = null;
	    	for (POEM p : this.poems) {
	    		if (p.getID().equals(poem.getAttribute("id"))){
	    			poemObj = p;
	    			break;
	    		}
	    	}
	    	if (!poemObj.isApplicableToModel()) {
	    		continue;
	    	}
	    	
	    	Element imported =  (Element) doc.importNode(poem, true);
	    	
	    	// Remove idrefs using hack so it initialises on load
	    	//if (imported.hasAttribute("operator")){
	    		//imported.setAttribute("operatorID", imported.getAttribute("operator").substring(1));
	    		//imported.removeAttribute("operator");
	    	//}
	    	//if (imported.hasAttribute("log")){
	    	//	imported.setAttribute("logID", imported.getAttribute("log").substring(1));
	    	//	imported.removeAttribute("log");
	    	//}
	    	for (Node child : XMLUtils.nodeListToList(imported.getChildNodes())) {
	    		if (!(child instanceof Element)) continue;
	    		Element childEle = (Element)child;
	    		if (childEle.getNodeName().equals("log")) {
	    			imported.removeChild(childEle);
	    		}
	    		//else if (childEle.hasAttribute("idref")) {
	    			//String idref = childEle.getAttribute("idref");
	    			//childEle.removeAttribute("idref");
	    			//childEle.setTextContent(idref);
	    			//doc.renameNode(childEle, null, childEle.getNodeName() + "ID");
	    		//}
	    	}
	    	scheduler.appendChild(imported);
	    }
		
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
		this.dbOut.print("nspecies\t");
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
		this.dbOut.print(POEM.getMeanColumnName() + "\t");
		this.dbOut.print(POEM.getStddevColumnName() + "\t");
		this.dbOut.print(POEM.getCoefficientOfVariationColumnName() + "\t");
		this.dbOut.print(POEM.getNumberOfStatesColumnName() + "\t");
		
		
		// Runtime (million states per hr)
		this.dbOut.print(POEM.getRuntimeSmoothColumn() + "\t");
		this.dbOut.print(POEM.getRuntimeRawColumn());
		this.dbOut.println();
	
		
	}
	
	
	

	/**
	 * Prepare header for the database
	 */
	protected void appendToDatabase(int sampleNum) {
	
		String dataset = this.data == null ? "NA" : !(this.data instanceof DatasetSampler) ? "NA" : ((DatasetSampler)this.data).getFilePath();
		int npartitions = this.data == null ? 0 : !(this.data instanceof DatasetSampler) ? 1 : ((DatasetSampler)this.data).getNumPartitions();
		double pgaps = this.data == null ? 0 : !(this.data instanceof DatasetSampler) ? 0 : ((DatasetSampler)this.data).getProportionGaps();
		String datedTips = this.data == null ? "false" : !(this.data instanceof DatasetSampler) ? "NA" : "" + ((DatasetSampler)this.data).tipsAreDated();
		String treeHeight = this.data == null ? "0" : !(this.data instanceof DatasetSampler) ? "NA" : "" + ((DatasetSampler)this.data).getEstimatedTreeHeight();
		int nspecies = this.data == null ? 1 :  !(this.data instanceof DatasetSampler) ? 0 : ((DatasetSampler)this.data).getNumSpecies();
		
		
		
		// Dataset summary
		this.dbOut.print(sampleNum + "\t"); // Sample number
		this.dbOut.print(dataset + "\t"); // Dataset folder 
		this.dbOut.print((this.data == null ? 0 : this.data.getTaxonCount()) + "\t"); // Taxon count
		this.dbOut.print((this.data == null ? 0 : this.data.getSiteCount()) + "\t"); // Site count
		this.dbOut.print((this.data == null ? 0 : this.data.getPatternCount()) + "\t"); // Pattern count
		this.dbOut.print(npartitions + "\t"); // Number of partitions
		this.dbOut.print(nspecies + "\t");
		this.dbOut.print(pgaps + "\t"); // Proportion of sites which are gaps
		this.dbOut.print((this.data == null ? 0 : this.data.getDataType().getStateCount()) + "\t"); // Number of characters (4 for nt, 20 for aa etc)
		this.dbOut.print(datedTips + "\t"); // Are the tips dated?
		this.dbOut.print(treeHeight + "\t"); // Estimate the tree height using neighbour joining
		
		
		// Model summary
		for (ModelSampler model : this.modelElements) {
			this.dbOut.print(model.getSampledID() + "\t");
		}
		
		// Operator weight summary
		if (this.runner instanceof RunnableSampler) {
			String id = ((RunnableSampler)this.runner).getSampledID();
			this.dbOut.print(id + "\t");
		}else {
			this.dbOut.print("NA\t");
		}
		
		for (POEM poem : this.poems) {
			this.dbOut.print("NA\t");
		}
		
		
		// ESS summary
		for (POEM poem : this.poems) {
			this.dbOut.print("NA\t");
		}
		this.dbOut.print("NA\t");
		this.dbOut.print("NA\t");
		this.dbOut.print("NA\t");
		this.dbOut.print("NA\t");
		
		
		
		// Runtime (num hr)
		this.dbOut.print("NA\t");
		this.dbOut.print("NA");

		this.dbOut.println();
		
	}
	
	
	

}








