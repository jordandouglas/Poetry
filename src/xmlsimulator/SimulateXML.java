package xmlsimulator;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import beast.core.BEASTObject;
import beast.core.Input;
import beast.core.Runnable;
import beast.evolution.alignment.Alignment;
import beast.util.XMLProducer;


public class SimulateXML extends Runnable {

	

	final public Input<DatasetSampler> dataInput = new Input<>("data", "A DatasetSampler for loading and sampling data", Input.Validate.REQUIRED);
	final public Input<List<ModelElementSampler>> modelInput = new Input<>("model", "A component of the model. All of its data will be dumped into this xml.", new ArrayList<>());
	final public Input<File> xmlOutInput = new Input<>("xml", "Filename to save the sampled XML file to", Input.Validate.REQUIRED);
	final public Input<XMLSimulatorLogger> loggerInput = new Input<>("logger", "Log file for printing summary statistics to", Input.Validate.OPTIONAL);
	
	
	
	final public Input<List<BEASTObject>> nodesInput = new Input<>("object", "Any beast object to be added into the main file "
			+ "(eg. StateNode, CalculationNode, Distribution, Operator)", new ArrayList<>());
	
	/*
	final public Input<List<Distribution>> posteriorInput = new Input<>("distribution", "probability distribution to sample over", new ArrayList<>() );
	final public Input<List<Operator>> operatorsInput = new Input<>("operator", "operator for generating proposals in MCMC state space", new ArrayList<>());
	final public Input<List<StateNode>> stateInput = new Input<>("state", "a state node", new ArrayList<>());
	final public Input<List<StateNodeInitialiser>> initialisersInput = new Input<>("init", "a state node initiliser for determining the start state", new ArrayList<>());
	*/
	
	DatasetSampler data;
	List<ModelElementSampler> modelElements;
	File xmlOutput;
	
	@Override
	public void initAndValidate() {
		this.data = dataInput.get();
		this.modelElements = modelInput.get();
		this.xmlOutput = xmlOutInput.get();
	}
	
	
	@Override
	public void run() throws Exception {

		
		
		
		// Sample a dataset
		List<Alignment> alignments = data.sampleAlignments();
		
		String sXML = new XMLProducer().toXML(this);
		
		PrintStream out = new PrintStream(this.xmlOutput);
		out.println(sXML);
		out.close();

		
		
	}

}












