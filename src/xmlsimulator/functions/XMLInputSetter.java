package xmlsimulator.functions;


import org.w3c.dom.Document;
import org.w3c.dom.Element;

import beast.core.BEASTObject;
import beast.core.Input;
import xmlsimulator.XMLUtils;

public class XMLInputSetter  extends XMLFunction {

	
	final public Input<BEASTObject> targetInput = new Input<>("target", "The target whose input will be set", Input.Validate.REQUIRED);
	final public Input<String> inputInput = new Input<>("input", "The name of the input to set", Input.Validate.REQUIRED);
	
	BEASTObject target;
	String inputName;
	
	@Override
	public void initAndValidate() {
		
		super.initAndValidate();

		// Find the input
		this.target = targetInput.get();
		this.inputName = inputInput.get();
		Input<?> input = this.target.getInput(this.inputInput.get());
		
		// Check method returns the appropriate return type
		if (this.method.getReturnType() != input.getType()){
			throw new IllegalArgumentException("Found method " + this.method.getName() + " in " + this.obj.getID() + " but it does not return a " + input.getType());
		}
		
	}
	
	
	/**
	 * Modify the xml by setting the attribute of the target to the appropriate value
	 * @param doc
	 * @throws Exception 
	 */
	public void tidyXML(Document doc) throws Exception {
		
		// Evaluate
		Object val = this.method.invoke(this.obj);
		
		// Find element with ID
		Element targetEle = XMLUtils.getElementById(doc, this.target.getID());
		if (targetEle == null) throw new Exception("Input setter error: cannot find " + target.getID());
		
		// Set its attribute to the appropriate value
		targetEle.setAttribute(this.inputName, val.toString());
		
	}
	
	
}

