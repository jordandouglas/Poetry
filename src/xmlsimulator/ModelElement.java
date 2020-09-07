package xmlsimulator;

import java.util.ArrayList;
import java.util.List;

import beast.core.BEASTObject;

import beast.core.Input;


public class ModelElement extends BEASTObject {

	
	final public Input<List<BEASTObject>> nodesInput = new Input<>("object", "Any beast object to be added into the main file "
			+ "(eg. StateNode, CalculationNode, Distribution, Operator)", new ArrayList<>());
	
	
	/**
	final public Input<List<Distribution>> posteriorInput = new Input<>("distribution", "probability distribution to sample over", new ArrayList<>() );
	final public Input<List<Operator>> operatorsInput = new Input<>("operator", "operator for generating proposals in MCMC state space", new ArrayList<>());
	final public Input<List<StateNode>> stateInput = new Input<>("stateNode", "a state node", new ArrayList<>());
	final public Input<List<StateNode>> stateInput = new Input<>("stateNode", "a state node", new ArrayList<>());
	final public Input<List<StateNodeInitialiser>> initialisersInput = new Input<>("init", "a state node initiliser for determining the start state", new ArrayList<>());
	**/
	

	
	@Override
	public void initAndValidate() {

		
	}

	
	
	
	
	
	
}
