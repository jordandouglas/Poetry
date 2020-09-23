package xmlsimulator;

import java.lang.reflect.Method;

import beast.core.BEASTObject;
import beast.core.Input;

public abstract class XMLFunction extends BEASTObject {

	final public Input<BEASTObject> objectInput = new Input<>("obj", "The object to call the function on", Input.Validate.REQUIRED);
	final public Input<String> methodInput = new Input<>("method", "The boolean-returning method in that class to call", Input.Validate.REQUIRED);
	
	
	
	protected BEASTObject obj;
	protected Method method;
	
	
	
	@Override
	public void initAndValidate() {
		// TODO Auto-generated method stub
		
	}
	
	
	
		
	
}
