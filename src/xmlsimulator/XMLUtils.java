package xmlsimulator;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


/**
 * Utils for processing XMLs, where XMLS are represented the org.w3c.dom library
 * @author Jordan Douglas
 *
 */
public class XMLUtils {
	
	
	/**
	 * Removes all children which are NOT on the list of attribute values
	 * @param attr the name of an attribute
	 * @param valuesToKeep list of values of attr
	 */
	public static void removeChildrenWithoutAttrVal(Element element, String attr, List<String> valuesToKeep) {
		
		List<Node> elements = nodeListToList(element.getChildNodes());
        for (int i = 0; i < elements.size(); i ++) {
        	Node node = elements.get(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
            	Element child = (Element) node;
            	
            	if (!child.hasAttribute(attr)) continue;
             	String value = child.getAttribute(attr);
             	if (!valuesToKeep.contains(value)) {
             		element.removeChild(child);
             	}
            	 
             }
        	
        }
		
	}
	
	
	


	
	
	
	/**
	 * Removes all children which do not have a 'name' on the list of names
	 * If an element does not have a name, then the tagname is used instead, in standard BEAST fashion
	 * @param element
	 * @param namesToKeep
	 */
	public static void removeChildrenWithoutName(Element element, List<String> namesToKeep) {
		
		
		if (element == null) return;
		
		List<Node> elements = nodeListToList(element.getChildNodes());
        for (int i = 0; i < elements.size(); i ++) {
        	Node node = elements.get(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
            	Element child = (Element) node;


            	// Get its name
             	String name;
             	if (child.hasAttribute("name")) {
             		
             		// Use the name attribute
             		name  = child.getAttribute("name");
             	}else {
             		
             		// Use the tagname
             		name = child.getNodeName();
             	}
             	
             	
             	if (!namesToKeep.contains(name)) {
             		element.removeChild(child);
             	}
            	 
             }else {
            	 element.removeChild(node);
            	 
             }
        	
        }
		
	}
	
	
	
	/**
	 * Returns the first element with tagname 'tagname' and id='targetID'
	 * @param doc
	 * @param tagname
	 * @param targetID
	 * @return
	 * @throws Exception 
	 */
	public static Element getElementById(Node doc, String targetID) throws Exception {
		
		if (targetID == null || targetID.isEmpty()) return null;
		
		
		NodeList elements;
		if (doc instanceof Element) {
			elements = ((Element)doc).getElementsByTagName("*");
		}else if (doc instanceof Document) {
			elements = ((Document)doc).getElementsByTagName("*");
		}else {
			return null;
		}
		
        for (int i = 0; i < elements.getLength(); i ++) {
        	Element element = (Element) elements.item(i);
        	if (!element.hasAttribute("id")) continue;
        	String id = element.getAttribute("id");
        	if (id.equals(targetID)) {
        		return element;
        	}
        }
        
        return null;
		
	}
	
	
	/**
	 * Returns a list of elements which have attr=val
	 * @param ele
	 * @param attr
	 * @param val
	 * @return
	 */
	public static List<Element> getElementByAttrValue(Element ele, String attr, String val) {
		
		if (attr == null || attr.isEmpty()) return null;
		if (val == null || val.isEmpty()) return null;
		
		
		NodeList elements;
		if (ele instanceof Element) {
			elements = ((Element)ele).getElementsByTagName("*");
		}else if (ele instanceof Document) {
			elements = ((Document)ele).getElementsByTagName("*");
		}else {
			return null;
		}
		
		
		List<Element> matches = new ArrayList<Element>();
        for (int i = 0; i < elements.getLength(); i ++) {
        	Element element = (Element) elements.item(i);
        	if (!element.hasAttribute(attr)) continue;
        	String val2 = element.getAttribute(attr);
        	if (val.equals(val2)) {
        		matches.add(element);
        	}
        }
        
        return matches;

	}
	
	
	
	/**
	 * Load an xml file an return a Document
	 * @param filePath
	 * @return
	 * @throws Exception
	 */
	public static Document loadXMLFromFile(String filePath) throws Exception {
	      DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
	      DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
	      Document doc = docBuilder.parse(filePath);
	      return doc;
	}
	
	
	
	/**
	 * Parse an xml string as a Document
	 * @param xml
	 * @return
	 * @throws Exception
	 */
	public static Document loadXMLFromString(String xml) throws Exception {
	    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

	    factory.setNamespaceAware(true);
	    DocumentBuilder builder = factory.newDocumentBuilder();

	    return builder.parse(new ByteArrayInputStream(xml.getBytes()));
	}
	
	
	
	/**
	 * Extract an xml string from a Document object
	 * @param doc
	 * @return
	 * @throws Exception
	 */
	public static String getXMLStringFromDocument(Document doc) throws Exception {

		doc.normalize();
		Transformer tf = TransformerFactory.newInstance().newTransformer();
		tf.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		tf.setOutputProperty(OutputKeys.INDENT, "yes");
		Writer out = new StringWriter();
		tf.transform(new DOMSource(doc), new StreamResult(out));
		return out.toString();
		
		
	}
	
	
	/**
	 * Get all elements in a document and return them in a list
	 * This allows permutation of the document without affecting the ordering of elements in the list
	 * @param doc A Document
	 * @return
	 */
	public static List<Node> getAllElements(Document doc){
		return nodeListToList(doc.getElementsByTagName("*"));
	}
	
	
	/**
	 * Returns the root of each tree in the xml forest
	 * @param elements
	 * @return
	 */
	public static List<Element> getTopLevels(List<Element> elements){
		
		

		// Find tree tops
		List<Element> tops = new ArrayList<Element>();
		for (Element ele1 : elements) {
			
			
			// Get the path to the root
			List<Element> path = new ArrayList<Element>();
			//path.add(ele1);
			Node parent = ele1.getParentNode();
			while (parent != null) {
				if (parent instanceof Element) path.add((Element) parent);
				parent = parent.getParentNode();
			}
			
			
			// Make sure this is not a descendent of another element
			boolean isRoot = true;
			for (Element ele2 : elements) {
				for (Element pathEle : path) {
					if (pathEle == ele2) {
						isRoot = false;
						break;
					}
				}
				if (!isRoot) break;
			}
			
			if (isRoot && !tops.contains(ele1)) {
				tops.add(ele1);
			}
			
		}
		
	
		
		return tops;
	}
	
	
	
	/**
	 * Find and return all elements which have an attr that contains the pattern as a substring (no regex) 
	 * @param doc
	 * @param attr - specify either an attribute name or use wildcard * for any attribute
	 * @param pattern
	 * @return
	 */
	public static List<Element> getAllElementsWithAttrMatch(Document doc, String attr, String pattern){
		List<Node> nodes = nodeListToList(doc.getElementsByTagName("*"));
		List<Element> matches = new ArrayList<Element>();
		for (Node node : nodes) {
			if ( !(node instanceof Element)) continue;
			Element element = (Element) node;
			
			// Wildcard - use all attributes 
			if (attr.equals("*")) {
				
				List<String> values = getAllAttributeValues(element);
				for (String val : values) {
					if (val.contains(pattern)) {
						matches.add(element);
						break;
					}
				}
				
			}
			
			// Just look at the specified attribute
			else {
			
				if (!element.hasAttribute(attr)) continue;
				if (element.getAttribute(attr).contains(pattern)) {
					matches.add(element);
				}
			}
		}
		
		return matches;
	}
	
	
	/**
	 * Get all descendants of an element and return them in a list
	 * This allows permutation of the document without affecting the ordering of elements in the list
	 * @param element An Element
	 * @return
	 */
	public static List<Node> getAllElements(Element element){
		return nodeListToList(element.getElementsByTagName("*"));
	}
	
	
	
	/**
	 * Convert a NodeList into a List<Node>
	 * @param nodes
	 */
	public static List<Node> nodeListToList(NodeList nodes) {
		List<Node> list = new ArrayList<Node>();
		for (int i = 0; i < nodes.getLength(); i ++) {
			list.add(nodes.item(i));
		}
		return list;
	}
	
	
	/**
	 * Recursively replaces substring 'pattern' with 'replace' in all nodes
	 * @param node
	 * @param pattern
	 * @param replace
	 */
	public static void XMLReplace(Node node, String pattern, String replace) {
		
		List<Node> children = XMLUtils.nodeListToList(node.getChildNodes());
		for (Node child : children) {
			XMLUtils.XMLReplace(child, pattern, replace);
		}
		
		
		// Replace value
		String value = node.getNodeValue();
		if (value != null && !value.isEmpty() && value.contains(pattern)) {
			value = value.replace(pattern, replace);
			node.setNodeValue(value);
		}
		
		// Replace attributes
		if (node instanceof Element) {
			Element ele = (Element) node;
			NamedNodeMap attributes = ele.getAttributes();

			for (int i = 0; i < attributes.getLength(); i ++) {
				
				Attr attr = (Attr) attributes.item(i);
				String attrName = attr.getNodeName();
	            String attrValue = attr.getNodeValue();
	            if (attrValue != null && !attrValue.isEmpty() && attrValue.contains(pattern)) {
	            	attrValue = attrValue.replace(pattern, replace);
	            	ele.setAttribute(attrName, attrValue);
	    		}
			}
				
		}
		
	}
	
	
	/**
	 * Returns all attribute values of this element
	 * @param ele
	 * @return
	 */
	public static List<String> getAllAttributeValues(Element ele){
		
		List<String> values = new ArrayList<String>();
		NamedNodeMap attributes = ele.getAttributes();

		for (int i = 0; i < attributes.getLength(); i ++) {
			Attr attr = (Attr) attributes.item(i);
            String attrValue = attr.getNodeValue();
            values.add(attrValue);
		}
		
		return values;
		
	}
	
	
	/**
	 * Get all unique id's in the document
	 * @param doc
	 * @return
	 */
	public static List<String> getIDs(Document doc) {
		
		List<String> ids = new ArrayList<String>();
		for (Node node : XMLUtils.getAllElements(doc)) {
			
			if (! (node instanceof Element)) continue; 
			Element ele = (Element) node;
			if (!ele.hasAttribute("id")) continue; 
			String id = ele.getAttribute("id");
			ids.add(id);
				
		}
		
		return ids;
	}
	
	
	/**
	 * Ensures that the id is unique in the document, and if not then appends integers until it becomes unique 
	 * @param doc
	 * @return the id
	 */
	public static String getUniqueID(Document doc, String id) {
		
		List<String> IDs = XMLUtils.getIDs(doc);
		if (IDs.contains(id)) {
            int k = 1;
            while (IDs.contains(id + k)) {
                k++;
            }
            id = id + k;
        }
		return id;
	}
	
	
	/**
	 * Sets the ID of this element. First it makes sure that the id is unique
	 * @param ele
	 * @param id
	 * @param doc
	 */
	public static void setID(Element ele, String id, Document doc) {
		String id2 = XMLUtils.getUniqueID(doc, id);
		//System.out.println(id + " -> " + id2);
		ele.setAttribute("id", id2);
	}


	/**
	 * Delete all duplicate ids
	 * This is done by finding the last occurrences of each duplicate and deleting them
	 * Their parent nodes will refer to the first occurrence of the object with the id
	 * @param doc
	 */
	public static void mergeElementsWhichShareID(Document doc) {
		
		List<Node> elements = getAllElements(doc);

		
		// Elements to delete (after iterating)
		List<Element> nodesToDelete = new ArrayList<Element>();
		

		
		// Pairwise comparison of all elements to find if they share an id
		for (int i = 0; i < elements.size(); i ++) {
			Node node_i = elements.get(i);
			if (node_i.getParentNode() == null) continue;
        	if (node_i.getNodeType() == Node.ELEMENT_NODE) {
        		
  
        		// Get the id of i
        		Element ele_i = (Element) node_i;
        		if (!ele_i.hasAttribute("id")) continue;
        		String id_i = ele_i.getAttribute("id");
        		
        		for (int j = i+1; j < elements.size(); j ++) {
        			
        			Node node_j = elements.get(j);
        			if (node_j.getParentNode() == null) continue;
        			if (node_j.getNodeType() == Node.ELEMENT_NODE) {
        				
        				// Get the id of j
                		Element ele_j = (Element) node_j;
                		if (!ele_j.hasAttribute("id")) continue;
                		String id_j = ele_j.getAttribute("id");
                		
                		// Are the 2 ids the same?
        				if (id_i.equals(id_j)) {
        					
        					
        					//System.out.println("Found match " + id_j);
        					
        					// Move the contents of j into i
        					/*
        					List<Node> toMove = nodeListToList(ele_j.getChildNodes());
        					for (int k = 0; k < toMove.size(); k ++) {
        						Node node = toMove.get(k);
        						ele_j.removeChild(node);
        						ele_i.appendChild(node);
        					}
        					*/
        					nodesToDelete.add(ele_j);
        					
        				}
        				
        			}
        			
        		}
        		
        	}
	        	
	        	
		 }
		

		
		// Delete the duplicates (ie the matched j's)
		for (Element duplicate : nodesToDelete) {
			if (duplicate.getParentNode() != null) {
				//System.out.println("removing " + duplicate.getAttribute("id"));
				String name = duplicate.hasAttribute("name") ? duplicate.getAttribute("name") : duplicate.getNodeName();
				String id = duplicate.getAttribute("id");
				((Element)duplicate.getParentNode()).setAttribute(name, "@" + id);
				duplicate.getParentNode().removeChild(duplicate);
			}
		}
		
	}


	/**
	 * Returns all child elements which have a matching tagname or a 'name' attribute
	 * @param runner
	 * @param string
	 * @return
	 */
	public static List<Element> getElementsByName(Node element, String name) {
		
		List<Element> elements = new ArrayList<Element>();
		for (Node child : XMLUtils.nodeListToList(element.getChildNodes())) {
			
			if (!(child instanceof Element)) continue;
			Element childEle = (Element) child;
			String childName = childEle.hasAttribute("name") ? childEle.getAttribute("name") : childEle.getNodeName();
			if (childName.equals(name)) elements.add(childEle);
		}
		return elements;
	}


}
