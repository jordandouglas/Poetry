package xmlsimulator.sampler;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import beast.core.BEASTObject;
import beast.core.Function;
import beast.core.Input;
import beast.core.Operator;
import xmlsimulator.XMLUtils;
import xmlsimulator.functions.XMLFunction;


/**
 * A Mapping between Parameters, Operators, and ESSes (a POEM)
 * @author jdou557
 *
 */
public class POEM extends BEASTObject implements XMLSampler {

	
	final public Input<Operator> operatorInput = new Input<>("operator", "An operator");
	final public Input<List<Function>> logInput = new Input<>("log", "A parameter/logger to report", new ArrayList<>());
	
	
	final public Input<String> operatorIDInput = new Input<>("operatorID", "The id of an operator. Use this when the operator is in a different xml file");
	final public Input<List<String>> logIDInput = new Input<>("logID", "The id of a function to log. Use this when the function is in a different xml file", new ArrayList<>());
	
	
	boolean useRealObjects;
	double weight;
	String operatorID;
	double minESS;
	
	@Override
	public void initAndValidate() {
		
		this.useRealObjects = operatorInput.get() == null;
		if (this.useRealObjects) {
			if (this.operatorIDInput.get() == null || this.operatorIDInput.get().isEmpty()) {
				throw new IllegalArgumentException("Please provide either an operator or an operator ID");
			}
		}else {
			this.operatorID = this.operatorInput.get().getID();
		}
		
		this.weight = 1;
		this.minESS = 0;
	}
	
	
	/**
	 * Return the id's of all objects which are logged
	 * @return
	 */
	public List<String> getLogIDs(){
		
		List<String> ids = new ArrayList<String>();
		if (this.useRealObjects){
			for (String id: this.logIDInput.get()) {
				ids.add(id);
			}
		}else {
			for (Function fun : this.logInput.get()) {
				ids.add(((BEASTObject)fun).getID());
			}
		}
		
		
		return ids;
	}
	
	
	/**
	 * Column name of this poem's minimum ess in the database file
	 * @return
	 */
	public String getESSColname() {
		return this.getID() + ".min.ESS.M";
	}
	
	/**
	 * Column name of this poem's oeprator weight in the database file
	 * @return
	 */
	public String getWeightColname() {
		return this.getID() + ".weight";
	}
	
	
	
	
	/**
	 * Set the weight of this operator
	 * @param weight
	 */
	public void setWeight(double weight) {
		this.weight = weight;
	}
	
	
	/**
	 * Get the operator weight
	 * @return
	 */
	public double getWeight() {
		return this.weight;
	}
	


	@Override
	public void reset() {
		this.weight = 1;
	}


	@Override
	public void tidyXML(Document doc, Element runnable, List<XMLFunction> functions) throws Exception {
		
		

		// Get the operator ID of this POEM (may have been added to the doc)
		Element thisEle = XMLUtils.getElementById(doc, this.getID());
		if (thisEle == null) throw new Exception("Error: cannot locate " + this.getID());
		List<Element> children = XMLUtils.getElementsByName(thisEle, "operator");
		String operatorID;
		if (thisEle.hasAttribute("operator")) {
			operatorID = thisEle.getAttribute("operator");
			operatorID = operatorID.replace("@", "");
		}else if (!children.isEmpty()) {
			operatorID = children.get(0).getAttribute("idref");
		}else {
			throw new Exception("Error: " + this.getID() + " does not have an operator");
		}
		
		
		// Set the weight of that operator in the XML to the appropriate weight
		Element operator = XMLUtils.getElementById(doc, operatorID);
		if (operator == null) {
			throw new IllegalArgumentException("Error: cannot locate operator " + operatorID);
		}
		operator.setAttribute("weight", "" + this.getWeight());
		this.operatorID = operatorID;
		
	}


	@Override
	public String getComments() {
		return "The operator weight (prob) for " + this.getID() + " is " + this.getWeight();
	}


	public String getOperatorID() {
		return this.operatorID;
	}


	@Override
	public String getSampledID() {
		return "NA";
	}

	/**
	 * Set the minimum ESS
	 * @param minESS
	 */
	public void setMinESS(double minESS) {
		this.minESS = minESS;
	}
	
	
	/**
	 * Get the minimum ESS
	 * @return
	 */
	public double getMinESS() {
		return this.minESS;
	}
	
	

}
