package poetry.operators;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;

import beast.core.Input;
import beast.core.Operator;
import beast.core.OperatorSchedule;
import beast.core.StateNode;
import beast.core.parameter.CompoundRealParameter;
import beast.util.Randomizer;


/**
 * A single operator which samples sub-operators according to their weights
 * @author jdou557
 *
 */
public class MetaOperator extends Operator {

	
	final public Input<List<Operator>> operatorsInput = new Input<>("operator", "list of operators to select from", new ArrayList<Operator>());
	
	List<Operator> operators;
	Operator lastOperator;
	
	
	@Override
	public void initAndValidate() {
		this.operators = operatorsInput.get();
		
		if (this.operators.isEmpty()) {
			this.m_pWeight.set(0.0);
		}
	}

	@Override
	public double proposal() {
		
		if (this.operators.size() == 0) return Double.NEGATIVE_INFINITY;
		
		// Sample according to weights
		double[] cumulativeWeights = this.getCumulativeWeights();
		int i = Randomizer.binarySearchSampling(cumulativeWeights);
		if (i < 0) return Double.NEGATIVE_INFINITY;
		this.lastOperator = this.operators.get(i);
		return this.lastOperator.proposal();
	}
	
	@Override
    public void optimize(double logAlpha) {
		this.lastOperator.optimize(logAlpha);
    }
	
	@Override
    public void setOperatorSchedule(final OperatorSchedule operatorSchedule) {
    	super.setOperatorSchedule(operatorSchedule);
    	for (int i = 0; i < this.operators.size(); i ++) this.operators.get(i).setOperatorSchedule(operatorSchedule);
	}
	 
	
	@Override 
	public void accept() {
		this.lastOperator.accept();
	}
	
	@Override
	public void reject(int reason) {
		this.lastOperator.reject(reason);
	}
	
	
	@Override
	public void reject() {
		this.lastOperator.reject();
	}

    @Override
    public List<StateNode> listStateNodes() {
    	List<StateNode> stateNodes = new ArrayList<StateNode>(); //super.listStateNodes();
    	
    	for (int i = 0; i < this.operators.size(); i ++) {
    		
    		// Maintain a list of unique elements
    		for (StateNode node : this.operators.get(i).listStateNodes()) {
    			if (!stateNodes.contains(node)) stateNodes.add(node);
    		}

    	}
    	
    	
    	// Remove compound operators and add their components instead
    	boolean hasCompoundRealParameter = true;
	   	while (hasCompoundRealParameter) {
	   		hasCompoundRealParameter = false;
	       	for (int i = 0; i < stateNodes.size(); i++) {
	       		StateNode s = stateNodes.get(i);
	       		if (s instanceof CompoundRealParameter) {
	       			CompoundRealParameter c = (CompoundRealParameter) s;
	       			stateNodes.remove(i);
	       			
	       			for (StateNode node : c.parameterListInput.get()) {
	        			if (!stateNodes.contains(node)) stateNodes.add(node);
	        		}

	       			i--;
	       			hasCompoundRealParameter = true;
	       		}
	       	}
	   	}
    	

    	return stateNodes;
    }
    

		
	
	
	public double[] getCumulativeWeights() {
	
		
		// Get weights and weight sum
		double weightSum = 0;
		double[] weights = new double[this.operators.size()];
		for (int i = 0; i < this.operators.size(); i ++) {
			Operator operator = this.operators.get(i);
			double weight = operator.getWeight();
			weightSum += weight;
			weights[i] = weight;
		}

		
		// Normalise 
		double[] cumulativeWeights = new double[this.operators.size()];
		double cumSum = 0;
		for (int i = 0; i < this.operators.size(); i ++) {
			double probability = weights[i] / weightSum;
			cumSum += probability;
			cumulativeWeights[i] = cumSum;
		}
		
		return cumulativeWeights; 
		
		
	}

	
	

    @Override
    public void storeToFile(final PrintWriter out) {
    	

        
    	try {
	        JSONStringer json = new JSONStringer();
	        json.object();
	
	        if (getID() == null) setID("unknown");
	
	        // id
	        json.key("id").value(getID());
	        
	        
	        
	        // Store generic beast core properties by writing its json to a string and then parsing it back
	        StringWriter outStr = new StringWriter();
	        PrintWriter writer = new PrintWriter(outStr);
	        super.storeToFile(writer);
	        JSONObject obj = new JSONObject(outStr.toString());
	        for (String key : obj.keySet()) {
	        	if (key.equals("id")) continue;
	        	json.key(key).value(obj.get(key));
	        }
	        
	        
	        // Store sub-operators in a list
	        JSONArray operatorListJson = new JSONArray();
	        for (int i = 0; i < this.operators.size(); i ++) {
	        	
	        	// Write the operator's json to a string
	        	outStr = new StringWriter();
		        writer = new PrintWriter(outStr);
	        	this.operators.get(i).storeToFile(writer);
	        	

	        	
	        	// Parse the json of the operator
	        	obj = new JSONObject(outStr.toString());
	        	operatorListJson.put(obj);

	        	//System.out.println(outStr.toString());

	        	
	        }
	        json.key("operators").value(operatorListJson);
	        
	        json.endObject();
	        out.print(json.toString());
	        

	        
    	} catch (JSONException e) {
    		// failed to log operator in state file
    		// report and continue
    		e.printStackTrace();
    	}
    }
    
    
    

    @Override
    public void restoreFromFile(JSONObject o) {

    	
    	super.restoreFromFile(o);
    	
    	
    	try {
    		
    		
	    	// Load sub-operators
	        JSONArray operatorArray = o.getJSONArray("operators");
	        if (operatorArray.length() != this.operators.size()) {
	        	throw new IllegalArgumentException("Cannot resume because there are " + operatorArray.length() + " elements in 'operators' but there should be " + this.operators.size());
		 	     
	        }
	        for (int i = 0; i < this.operators.size(); i ++) {
	        	JSONObject jsonOp = operatorArray.getJSONObject(i);
	        	this.operators.get(i).restoreFromFile(jsonOp);
	        }
	        
    		
	        super.restoreFromFile(o);  	
    	} catch (JSONException e) {
    		// failed to restore from state file
    		// report and continue
    		e.printStackTrace();
    	}
    }

    
	
	
	
}




