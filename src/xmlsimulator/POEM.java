package xmlsimulator;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import beast.core.BEASTObject;
import beast.core.Function;
import beast.core.Input;
import beast.core.Operator;

public class POEM extends BEASTObject implements XMLSample{

	
	final public Input<Operator> operatorInput = new Input<>("operator", "An operator");
	final public Input<List<Function>> logInput = new Input<>("log", "A parameter/logger to report", new ArrayList<>());
	
	double weight;
	
	@Override
	public void initAndValidate() {
		
		this.weight = 1;
		
	}
	
	
	public String getESSColname() {
		return this.getID() + ".min.ESS.M";
	}
	
	
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
		
		
	}


	@Override
	public String getComments() {
		return "Operator weight for " + this.getID() + " is equal to " + this.getWeight();
		
	}
	
	

}
