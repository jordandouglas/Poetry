package xmlsimulator;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import beast.core.BEASTInterface;
import beast.core.BEASTObject;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.util.Log;
import beast.evolution.alignment.Alignment;
import beast.util.XMLParser;


public class SimulateXML extends Runnable {

	

	
	final public Input<Runnable> runnableInput = new Input<>("runner", "A runnable object (eg. mcmc)", Input.Validate.REQUIRED);
	
	final public Input<List<ModelSampler>> modelInput = new Input<>("model", "A component of the model. All of its data will be dumped into this xml.", new ArrayList<>());
	final public Input<String> xmlOutInput = new Input<>("xml", "Filename to save the sampled XML file to", Input.Validate.REQUIRED);
	final public Input<XMLSimulatorLogger> loggerInput = new Input<>("logger", "Log file for printing summary statistics to", Input.Validate.OPTIONAL);
	final public Input<Alignment> dataInput = new Input<>("data", "A dataset samplers for loading and sampling data. Optional", Input.Validate.OPTIONAL);
	final public Input<Integer> nsamplesInput = new Input<>("nsamples", "Number of xml files to produce (default 1)", 1);
	final public Input<List<XMLFunction>> functionsInput = new Input<>("function", "Functions which can be called during xml simulation", new ArrayList<>());

	
	
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
	File xmlOutput;
	List<XMLFunction> functions;
	
	
	
	@Override
	public void initAndValidate() {
		
		this.runner = runnableInput.get();
		this.nsamples = nsamplesInput.get();
		this.data = dataInput.get();
		this.modelElements = modelInput.get();
		this.xmlOutput = new File(xmlOutInput.get());
		this.functions = functionsInput.get();
		
		
		// Ensure that runner already has an ID
		if (this.runner.getID() == null || this.runner.getID().isEmpty()) {
			throw new IllegalArgumentException("Please provide an id for the <runner /> element");
		}

	}
	
	
	@Override
	public void run() throws Exception {

		
		for (int sample = 1; sample <= this.nsamples; sample ++) {
		
			System.out.println("Sample " + sample);
			
			// Sample alignments
			if (this.data != null && this.data instanceof XMLSample) {
				DatasetSampler d = (DatasetSampler) this.data;
				d.reset();
			}
			
			// Sample model components
			for (ModelSampler model : this.modelElements) {
				model.reset();
			}
			
			// Print the new xml
			String sXML = this.toXML();
			PrintStream out = new PrintStream(this.xmlOutput);
			out.println(sXML);
			out.close();
		
		}
		
		Log.warning("Done!");
		
	}
	
	
	/**
	 * Get the output xml string 
	 * All elements will be placed inside the runnable
	 * @return
	 * @throws Exception 
	 */
	protected String toXML() throws Exception {
		
		
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
        
        
        
        // Replace this runnable element (and all of its children) with its runnable child
        Element run = XMLUtils.getElementById(doc, this.getID());
        Element parent = (Element) run.getParentNode();
        parent.removeChild(run);
        parent.appendChild(runner);
        
        

		// Move the alignment to the head if it is not already
        if (this.data != null) {
        	Element dataset = XMLUtils.getElementById(doc, this.data.getID());
			Element datasetParent = (Element) dataset.getParentNode();
			datasetParent.removeChild(dataset);
			runner.getParentNode().insertBefore(dataset, runner);
			if (!datasetParent.getNodeName().equals("beast")) {
				datasetParent.setAttribute(dataset.getNodeName(), "@" + this.getID());
			}
        }

 
        // Tidy the XML of all XMLSampler models (and get some comments)
		for (ModelSampler model : this.modelElements) {
			model.tidyXML(doc, runner, this.functions);
			comments += model.getComments() + "\n";
			//PrintStream out = new PrintStream(this.xmlOutput);
			//out.println(XMLUtils.getXMLStringFromDocument(doc));
			//out.close();
		}
		
		
		 // Tidy the XML of all XMLSampler datasets (and get some comments)
        if (this.data != null && this.data instanceof XMLSample) {
			DatasetSampler d = (DatasetSampler) this.data;
			d.tidyXML(doc, runner, this.functions);
			comments += d.getComments() + "\n";
		}
  
		
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
	
	
	

}








