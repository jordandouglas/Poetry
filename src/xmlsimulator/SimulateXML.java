package xmlsimulator;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import beast.core.BEASTObject;
import beast.core.Input;
import beast.core.Runnable;
import beast.util.XMLProducer;


public class SimulateXML extends Runnable {

	

	
	final public Input<Runnable> runnableInput = new Input<>("run", "A runnable object (eg. mcmc)", Input.Validate.REQUIRED);
	
	final public Input<List<ModelElementSampler>> modelInput = new Input<>("model", "A component of the model. All of its data will be dumped into this xml.", new ArrayList<>());
	final public Input<File> xmlOutInput = new Input<>("xml", "Filename to save the sampled XML file to", Input.Validate.REQUIRED);
	final public Input<XMLSimulatorLogger> loggerInput = new Input<>("logger", "Log file for printing summary statistics to", Input.Validate.OPTIONAL);
	final public Input<List<DatasetSampler>> dataInput = new Input<>("data", "A dataset samplers for loading and sampling data. Optional", new ArrayList<>());
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
	List<DatasetSampler> data;
	List<ModelElementSampler> modelElements;
	File xmlOutput;
	
	
	@Override
	public void initAndValidate() {
		
		this.runner = runnableInput.get();
		this.nsamples = nsamplesInput.get();
		this.data = dataInput.get();
		this.modelElements = modelInput.get();
		this.xmlOutput = xmlOutInput.get();

	}
	
	
	@Override
	public void run() throws Exception {

		
		
		
		for (int sample = 1; sample <= this.nsamples; sample ++) {
		
		
			System.out.println("Sample " + sample);
			
			// Sample alignments
			for (DatasetSampler dataset : this.data) {
				dataset.reset();
			}
			
			
			// Sample model components
			
			
			
			
			
			
			
			// Print the new xml
			String sXML = this.toXML();
			PrintStream out = new PrintStream(this.xmlOutput);
			out.println(sXML);
			out.close();

		
		}
		
		
	}
	
	
	/**
	 * Get the output xml string 
	 * All elements will be placed inside runnable
	 * @return
	 */
	protected String toXML() {
		
		String sXML = new XMLProducer().toXML(this);
		
		
		// Remove all 
		
		
		return sXML;
	}

}












