package poetry.functions;


import org.w3c.dom.Document;
import org.w3c.dom.Element;

import beast.core.BEASTObject;
import beast.core.Input;
import beast.core.Input.Validate;
import poetry.util.XMLUtils;

public class XMLInputSetter  extends XMLFunction {

	
	final public Input<BEASTObject> targetInput = new Input<>("target", "The target whose input will be set", Input.Validate.REQUIRED);
	final public Input<String> inputInput = new Input<>("input", "The name of the input to set", Input.Validate.REQUIRED);
	
	final public Input<String> valueInput = new Input<>("value", "Value to set input to. Can use this instead of a method", Input.Validate.XOR, methodInput);
	
	BEASTObject target;
	String inputName;
	boolean usingMethod;
	Object value;

	public XMLInputSetter() {
		objectInput.setRule(Validate.OPTIONAL);
		methodInput.setRule(Validate.OPTIONAL);
	}
	
	
	@Override
	public void initAndValidate() {
		
		if (this.objectInput.get() != null) {
			this.usingMethod = true;
			super.initAndValidate();
		}else {
			this.usingMethod = false;
			this.value = valueInput.get();
		}

		// Find the input
		this.target = targetInput.get();
		this.inputName = inputInput.get();
		Input<?> input = this.target.getInput(this.inputInput.get());
		
		// Check method returns the appropriate return type
		if (this.usingMethod && this.method.getReturnType() != input.getType()){
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
		Object val = this.usingMethod ? this.method.invoke(this.obj) : this.value;
		
		// Find element with ID
		Element targetEle = XMLUtils.getElementById(doc, this.target.getID());
		if (targetEle == null) throw new Exception("Input setter error: cannot find " + target.getID());
		
		// Set its attribute to the appropriate value
		targetEle.setAttribute(this.inputName, val.toString());
		
	}
	
	
}

