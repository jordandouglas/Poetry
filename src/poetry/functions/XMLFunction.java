package poetry.functions;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import beast.core.BEASTObject;
import beast.core.Input;
import beast.core.util.Log;

public abstract class XMLFunction extends BEASTObject {

	final public Input<BEASTObject> objectInput = new Input<>("obj", "The object to call the function on", Input.Validate.REQUIRED);
	final public Input<String> methodInput = new Input<>("method", "The boolean-returning method in that class to call", Input.Validate.REQUIRED);
	
	
	
	protected BEASTObject obj;
	protected Method method;
	


	@Override
	public void initAndValidate() {
		this.obj = objectInput.get();
		this.method = null;
		String methodName = methodInput.get();
		
		// Find the method with the matching name
		for (Method meth : obj.getClass().getMethods()) {
			
			if (meth.getName().equals(methodName)) {
				
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
			throw new IllegalArgumentException("Cannot access a method with 0 arguments named '" + methodName + "' in " + this.obj.getID());
		}
		
	}
	
	
	
		
	
}
