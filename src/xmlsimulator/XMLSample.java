package xmlsimulator;

import org.w3c.dom.Document;
import org.w3c.dom.Element;


/**
 * An interface for xml fragments which are sampled and then inserted into the xml
 * @author Jordan Douglas
 *
 */
public interface XMLSample {

	/**
	 * Samples the next element
	 */
	public abstract void reset();
	
	
	/**
	 * Tidies the xml of this object
	 * May involve relocating / deleting some of its children
	 * @param doc
	 * @param runnable 
	 */
	public abstract void tidyXML(Document doc, Element runnable) throws Exception;
	
	
	
	/**
	 * Comments to add to the top of the xml
	 * @return string of comments
	 */
	public abstract String getComments();
	
	
}
