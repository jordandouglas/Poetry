package xmlsimulator;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;



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
	public static Element getElementById(Document doc, String targetID) throws Exception {
		
		if (targetID == null || targetID.isEmpty()) throw new Exception("Dev error: there is no id");
		
        NodeList elements = doc.getElementsByTagName("*");
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
	 * Merge all elements in the doc which share an id attr
	 * This is done by finding the last occurrences of each duplicate id and moving their contents into the first occurrence
	 * @param doc
	 */
	public static void mergeElementsWhichShareID(Document doc) {
		
		List<Node> elements = getAllElements(doc);

		// Elements to move (after iterating)
		//Map<Node, Element> moveNodeTo = new HashMap<Node, Element>();
		
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
        		//System.out.println("i= " + id_i + " " + ele_i.toString());
        		
        		for (int j = i+1; j < elements.size(); j ++) {
        			
        			Node node_j = elements.get(j);
        			if (node_j.getParentNode() == null) continue;
        			if (node_j.getNodeType() == Node.ELEMENT_NODE) {
        				
        				// Get the id of j
                		Element ele_j = (Element) node_j;
                		if (!ele_j.hasAttribute("id")) continue;
                		String id_j = ele_j.getAttribute("id");
                		//System.out.println("\tj= " + id_j);
                		
                		// Are the 2 ids the same?
        				if (id_i.equals(id_j)) {
        					
        					
        					System.out.println("Found match " + id_j);
        					
        					// Move the contents of j into i
        					List<Node> toMove = nodeListToList(ele_j.getChildNodes());
        					for (int k = 0; k < toMove.size(); k ++) {
        						Node node = toMove.get(k);
        						ele_j.removeChild(node);
        						ele_i.appendChild(node);
        						//moveNodeTo.put(node, ele_i);
        					}
        					nodesToDelete.add(ele_j);
        					
        				}
        				
        			}
        			
        		}
        		
        	}
	        	
	        	
		 }
		
		
		// Move the nodes which need to be moved (ie put 'node' into ele_i)
		/*
		for (Node node: moveNodeTo.keySet()) {
			if (node.getParentNode() != null) node.getParentNode().removeChild(node);
			Element moveTo = moveNodeTo.get(node);
			moveTo.appendChild(node);
		}
		*/
		
		
		// Delete the duplicates (ie the matched j's)
		for (Element duplicate : nodesToDelete) {
			if (duplicate.getParentNode() != null) {
				System.out.println("removing " + duplicate.getAttribute("id"));
				duplicate.getParentNode().removeChild(duplicate);
			}
		}
		
	}
	
	

}
