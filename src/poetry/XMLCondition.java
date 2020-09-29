package poetry;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import beast.core.BEASTObject;
import beast.core.Input;
import beast.core.util.Log;


/**
 * Returns true or false
 *
 */
public class XMLCondition extends XMLFunction {

	
	final public Input<Boolean> negateInput = new Input<>("negate", "Whether to take the negation of the boolean (default false)", false);
	
	boolean negate;
	
	
	@Override
	public void initAndValidate() {
		this.obj = objectInput.get();
		this.method = null;
		this.negate = negateInput.get();
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
			boolean result = (Boolean) this.method.invoke(this.obj);
			if (this.negate) result = !result;
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	
	

}
