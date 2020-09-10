package xmlsimulator;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;


import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import beast.core.BEASTObject;
import beast.core.Input;
import beast.core.Runnable;
import beast.evolution.alignment.Alignment;
import beast.util.XMLProducer;


public class SimulateXML extends Runnable {

	

	
	final public Input<Runnable> runnableInput = new Input<>("runner", "A runnable object (eg. mcmc)", Input.Validate.REQUIRED);
	
	final public Input<List<ModelSampler>> modelInput = new Input<>("model", "A component of the model. All of its data will be dumped into this xml.", new ArrayList<>());
	final public Input<File> xmlOutInput = new Input<>("xml", "Filename to save the sampled XML file to", Input.Validate.REQUIRED);
	final public Input<XMLSimulatorLogger> loggerInput = new Input<>("logger", "Log file for printing summary statistics to", Input.Validate.OPTIONAL);
	final public Input<List<Alignment>> dataInput = new Input<>("data", "A dataset samplers for loading and sampling data. Optional", new ArrayList<>());
	final public Input<Integer> nsamplesInput = new Input<>("nsamples", "Number of xml files to produce (default 1)", 1);
	
	
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
	List<Alignment> data;
	List<ModelSampler> modelElements;
	File xmlOutput;
	
	
	@Override
	public void initAndValidate() {
		
		this.runner = runnableInput.get();
		this.nsamples = nsamplesInput.get();
		this.data = dataInput.get();
		this.modelElements = modelInput.get();
		this.xmlOutput = xmlOutInput.get();
		
		
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
			for (Alignment dataset : this.data) {
				if (dataset instanceof XMLSample) {
					DatasetSampler d = (DatasetSampler) dataset;
					d.reset();
	        	}
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
		
		
	}
	
	
	/**
	 * Get the output xml string 
	 * All elements will be placed inside the runnable
	 * @return
	 * @throws Exception 
	 */
	protected String toXML() throws Exception {
		
		String sXML = new XMLProducer().toXML(this);
		
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
       

        
        // Tidy the XML of all XMLSampler datasets (and get some comments)
        for (Alignment dataset : this.data) {
			if (dataset instanceof XMLSample) {
				XMLSample d = (XMLSample) dataset;
				d.tidyXML(doc, runner);
				comments += d.getComments() + "\n";
        	}
		}
        
        // Tidy the XML of all XMLSampler models (and get some comments)
		for (ModelSampler model : this.modelElements) {
			model.tidyXML(doc, runner);
			comments += model.getComments() + "\n";
		}
  
		
		// Add a comment citing the dataset
        Node element = doc.getFirstChild();
		Comment comment = doc.createComment(comments);
		element.getParentNode().insertBefore(comment, element);
		
 
		
		// Merge elements which share an id
		XMLUtils.mergeElementsWhichShareID(doc);
		
		
        sXML = XMLUtils.getXMLStringFromDocument(doc);
		return sXML;
	}
	

	 
	

}












