package xmlsimulator.functions;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

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
		
		super.initAndValidate();
		
		// Check method returns List<Element>
		if (this.method.getReturnType() != boolean.class){
			throw new IllegalArgumentException("Found method " + this.method.getName() + " in " + this.obj.getID() + " but it does not return a boolean");
		}
		
		this.negate = negateInput.get();
		
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
