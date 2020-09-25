package xmlsimulator.functions;

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
		super.initAndValidate();
		
		// Check method returns List<Element>
		if (this.method.getReturnType() != List.class){
			throw new IllegalArgumentException("Found method " + this.method.getName() + " in " + this.obj.getID() + " but it does not return a NodeList");
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
