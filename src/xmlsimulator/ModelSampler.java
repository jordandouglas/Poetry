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
		
		
			
		
	}
	
	
	protected WeightedFile sampleAFile() {
		return WeightedFile.sampleFile(this.filesInput.get());
	}






	@Override
	public void tidyXML(Document doc, Element runnable) throws Exception {
		
		
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
		
		
		// Append the 'append' tags into the relevant sections
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
		
		
		// Replace elements with the 'override' tags
		List<Node> overrides = XMLUtils.nodeListToList(fragment.getElementsByTagName("override"));
		for (Node override : overrides) {
					
					
			if (! (override instanceof Element)) {
				throw new Exception("override tag must be an xml element: " + override.toString());
			}
			Element element = (Element) override;
			
			
			// Override what element?
			String id = element.hasAttribute("id") ? element.getAttribute("id") : null;	
			Element toOverride = XMLUtils.getElementById(doc, id);
			if (toOverride == null) throw new Exception("Override error: cannot find element in template xml with id " + id);

			
			// Replace overridable element in doc
			Node importedNode = doc.importNode(element, true);
			toOverride.getParentNode().insertBefore(importedNode, toOverride);
			toOverride.getParentNode().removeChild(toOverride);

			
		}
		
		/*
		// Elements in the <head> section go above the <run> element
		Node insertAt = runnable.getParentNode();
		NodeList heads = fragment.getElementsByTagName("head");
		if (heads.getLength() > 1) throw new Exception("There should only 0 or 1 head tag but there are " + heads.getLength() + " (" + this.sampledFile.getFile().getPath() + ")");
		if (heads.getLength() == 1) {
			Element head = (Element) heads.item(0);
			List<Node> elements = XMLUtils.nodeListToList(head.getChildNodes());
			for (int i = 0; i < elements.size(); i ++) {
	        	Node node = elements.get(i);
	        	Node importedNode = doc.importNode(node, true);
	        	insertAt.insertBefore(importedNode, runnable);
			}
		}
				
		
		
		// Elements in the <run> section go into the <run> element
		NodeList runs = fragment.getElementsByTagName("run");
		if (runs.getLength() > 1) throw new Exception("There should only 0 or 1 run tag but there are " + runs.getLength() + " (" + this.sampledFile.getFile().getPath() + ")");
		if (runs.getLength() == 1) {
			Element run = (Element) runs.item(0);
			List<Node> elements = XMLUtils.nodeListToList(run.getChildNodes());
			for (int i = 0; i < elements.size(); i ++) {
	        	Node node = elements.get(i);
	        	Node importedNode = doc.importNode(node, true);
	        	runnable.appendChild(importedNode);
			}
	
		}
		*/

		
	}



	@Override
	public String getComments() {
		return "Model fragment sampled from " + this.sampledFile.getFilePath() + ". Description: " +  this.sampledFile.getDesc();
	}


	
	
	

}

















