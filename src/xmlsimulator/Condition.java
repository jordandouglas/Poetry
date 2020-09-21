package xmlsimulator;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import beast.core.BEASTObject;
import beast.core.Input;
import beast.core.util.Log;

public class Condition extends BEASTObject {

	
	final public Input<BEASTObject> objectInput = new Input<>("obj", "The object to call the function on", Input.Validate.REQUIRED);
	final public Input<String> methodInput = new Input<>("method", "The boolean-returning method in that class to call", Input.Validate.REQUIRED);
	
	
	BEASTObject obj;
	Method method;
	
	
	
	@Override
	public void initAndValidate() {
		this.obj = objectInput.get();
		this.method = null;
		String methodName = methodInput.get();
		
		// Find the method with the matching name
		for (Method meth : obj.getClass().getMethods()) {
			
			if (meth.getName().equals(methodName)) {
				
				// Check it returns boolean
				if (meth.getReturnType() != boolean.class) {
					Log.warning("Found method " + methodName + " in " + this.obj.getID() + " but it does not return boolean");
					continue;
				}
				
				// Check it is accessible
				if (!Modifier.isPublic(meth.getModifiers())) {
					Log.warning("Found method " + methodName + " in " + this.obj.getID() + " but it is not accessible");
					continue;
				}
				
				// Check there are no args
				if (meth.getParameterCount() > 0) {
					Log.warning("Found method " + methodName + " in " + this.obj.getID() + " but there are more than 0 arguments");
					continue;
				}
				
				
				this.method = meth;
				break;
			}
			
		}
		
		if (this.method == null) {
			throw new IllegalArgumentException("Cannot access a boolean-returning method with 0 arguments named '" + methodName + "' in " + this.obj.getID());
		}
		
	}
	
	
	/**
	 * Evaluate this object
	 * @return
	 */
	public boolean eval() {
		try {
			return (Boolean) this.method.invoke(this.obj);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	
	

}
