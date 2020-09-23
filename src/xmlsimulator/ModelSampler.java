package xmlsimulator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import beast.core.BEASTObject;
import beast.core.Input;
import beast.core.util.Log;
import beast.util.Randomizer;

public class ModelSampler extends BEASTObject implements XMLSample {

	
	final public Input<List<WeightedFile>> filesInput = new Input<>("file", "The location of an xml file which contains model details", new ArrayList<>());
	
	
	WeightedFile sampledFile;
	int numFiles;
	
	@Override
	public void initAndValidate() {
		
		this.numFiles = filesInput.get().size();
		if (this.numFiles == 0) {
			throw new IllegalArgumentException("Please provide at least 1 model file");
		}
		
		if (this.getID() == null || this.getID().isEmpty()) {
			throw new IllegalArgumentException("Please ensure this object has an ID");
		}
		
		this.reset();

		
	}
	
	
	
	@Override
	public void reset() {
		
		// Sample a model according to its prior probability
		this.sampledFile = this.sampleAFile();
		
		System.out.println("Sampling model: " +  this.sampledFile.getFilePath());
		
	}
	
	/**
	 * Sample a file. Reset all files first
	 * @return
	 */
	protected WeightedFile sampleAFile() {
		return WeightedFile.sampleFile(this.filesInput.get());
	}






	@Override
	public void tidyXML(Document doc, Element runnable, List<XMLFunction> functions) throws Exception {
		
		
		// Load the xml content from the sampled file and put it in the doc xml
		Document sampled;
		try {
			File file = this.sampledFile.unzipFile();
			sampled = XMLUtils.loadXMLFromFile(file.getPath());
			this.sampledFile.close();
		} catch (Exception e) {
			Log.err("Encountered a problem when parsing " + this.sampledFile.getFilePath());
			throw e;
		}
		Element fragment = (Element) sampled.getFirstChild();
		
		
		// 1) Delete the 'remove' tags
		List<Node> removes = XMLUtils.nodeListToList(fragment.getElementsByTagName("remove"));
		for (Node remove : removes) {
			
			if (! (remove instanceof Element)) {
				throw new Exception("remove tag must be an xml element: " + remove.toString());
			}
			Element element = (Element) remove;
			element.getParentNode().removeChild(element);
			
		}
		
		
		// 2) Append the 'append' tags into the relevant sections
		List<Node> appends = XMLUtils.nodeListToList(fragment.getElementsByTagName("append"));
		for (Node append : appends) {
			
			
			if (! (append instanceof Element)) {
				throw new Exception("append tag must be an xml element: " + append.toString());
			}
			Element element = (Element) append;
			
			// Append to what?
			String id = element.hasAttribute("id") ? element.getAttribute("id") : null;
			Node appendTo = null;
			
			
			// Append to xml head
			if (id == null) {
				appendTo = runnable.getParentNode();
			}
			
			// Append to the tag with matching id
			else {
				appendTo = XMLUtils.getElementById(doc, id);
				if (appendTo == null) {
					throw new Exception("Append error: cannot find element in template xml with id " + id);
				}
			}
			
			
			// Do the appending
			List<Node> elements = XMLUtils.nodeListToList(element.getChildNodes());
			for (int i = 0; i < elements.size(); i ++) {
	        	Node node = elements.get(i);
	        	Node importedNode = doc.importNode(node, true);
	        	appendTo.appendChild(importedNode);
			}
			
		}
		
		
		
		// 3) Replace elements with the 'override' tags
		List<Node> overrides = XMLUtils.nodeListToList(fragment.getElementsByTagName("override"));
		for (Node override : overrides) {
					
					
			if (! (override instanceof Element)) {
				throw new Exception("override tag must be an xml element: " + override.toString());
			}
			Element element = (Element) override;
			
			
			// Override what element?
			String id = element.hasAttribute("id") ? element.getAttribute("id") : null;	
			Element toOverride = XMLUtils.getElementById(doc, id);
			Element importedNode = (Element) doc.importNode(element, true);
			

			// If the object does not exist then add it to the head
			if (toOverride == null) {
				runnable.getParentNode().insertBefore(importedNode, runnable);
			}
			
			// Replace overridable element in doc
			else {
				toOverride.getParentNode().insertBefore(importedNode, toOverride);
				toOverride.getParentNode().removeChild(toOverride);
			}

			
			
			// Rename the node to its name and remove its name
			if (importedNode.hasAttribute("name")) {
				String name = importedNode.getAttribute("name");
				importedNode.removeAttribute("name");
				doc.renameNode(importedNode, null, name);
			}
			

			
		}
		
		
		
		// 4) Populate sections using the 'populate' tag
		List<Node> populates = XMLUtils.nodeListToList(fragment.getElementsByTagName("populate"));
		for (Node populate : populates) {
					
					
			if (! (populate instanceof Element)) {
				throw new Exception("override tag must be an xml element: " + populate.toString());
			}
			Element element = (Element) populate;
			
			
			// Override what element?
			String id = element.getAttribute("id");	
			Element toPopulate = XMLUtils.getElementById(doc, id);
			if (toPopulate == null) throw new Exception("Populate error: cannot find element in template xml with id " + id);

			
			
			// Find the XMLPopulator object
			String functionID = element.getAttribute("function");	
			if (functionID == null || functionID.isEmpty()) throw new Exception("Populate error: please provide a 'function' id for " + id);
			XMLPopulator populator = null;
			for (XMLFunction fn : functions) {
				if ( !(fn instanceof XMLPopulator)) continue;
				if (("@" + fn.getID()).equals(functionID)) {
					populator = (XMLPopulator) fn;
					break;
				}
			}
			if (populator == null) throw new Exception("Populate error: cannot find element in template xml with id " + id);
			
			
			// Evaluate the function
			List<Element> elements = populator.eval();
			
			
			// Append elements
			for (Element ele : elements) {
				Node importedNode = doc.importNode(ele, true);
				toPopulate.appendChild(importedNode);
			}
			
		}
		
	}



	@Override
	public String getComments() {
		return "Model fragment sampled from " + this.sampledFile.getFilePath() + ". Description: " +  this.sampledFile.getDesc();
	}


	
	
	

}

















