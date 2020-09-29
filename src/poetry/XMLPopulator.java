package poetry;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import beast.core.util.Log;


/**
 * Returns an XML fragment
 *
 */
public class XMLPopulator extends XMLFunction {

	

	@Override
	public void initAndValidate() {
		this.obj = objectInput.get();
		this.method = null;
		String methodName = methodInput.get();
		
		
		// Find the method with the matching name
		for (Method meth : obj.getClass().getMethods()) {
			
			if (meth.getName().equals(methodName)) {
				
				// Check it returns List<Element>
				if (meth.getReturnType() != List.class){
					Log.warning("Found method " + methodName + " in " + this.obj.getID() + " but it does not return a NodeList");
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
			throw new IllegalArgumentException("Cannot access an XML-returning method with 0 arguments named '" + methodName + "' in " + this.obj.getID());
		}
		
	}
	
	
	
	/**
	 * Evaluate this object
	 * @return a list of XML Document Elements
	 */
	public List<Element> eval() {
		try {
			Object o = this.method.invoke(this.obj);
			return (ArrayList<Element>) o;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	
}
