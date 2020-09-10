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
		
		
		// Sum the weights
		double weightSum = 0;
		for (int i = 0; i < this.numFiles; i ++) {
			weightSum += this.filesInput.get().get(i).getWeight();
		}
		
		
		// Get cumulative probability vector
		double cumulativeWeight = 0;
		double[] weights = new double[this.numFiles];
		for (int i = 0; i < this.numFiles; i ++) {
			double weight = this.filesInput.get().get(i).getWeight() / weightSum;
			cumulativeWeight += weight;
			weights[i] = cumulativeWeight;
		}
		
		// Sample a file
		int fileNum = Randomizer.randomChoice(weights);
		return  this.filesInput.get().get(fileNum);

		
	}






	@Override
	public void tidyXML(Document doc, Element runnable) throws Exception {
		
		
		// Load the xml content from the sampled file and put it in the doc xml
		Document sampled;
		try {
			sampled = XMLUtils.loadXMLFromFile(this.sampledFile.getFile().getPath());
		} catch (Exception e) {
			Log.err("Encountered a problem when parsing " + this.sampledFile.getFile().getPath());
			throw e;
		}
		Element fragment = (Element) sampled.getFirstChild();
		
		// Elements in the <head> section go above the <run> element
		Node insertAt = runnable.getParentNode();
		NodeList heads = fragment.getElementsByTagName("head");
		if (heads.getLength() > 1) throw new Exception("There should only 0 or 1 head tag but there are " + heads.getLength() + " (" + this.sampledFile.getFile().getPath() + ")");
		if (heads.getLength() == 1) {
			Element head = (Element) heads.item(0);
			List<Node> elements =XMLUtils.nodeListToList(head.getChildNodes());
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

		
	}



	@Override
	public String getComments() {
		return "Model fragment sampled from " + this.sampledFile.getFile().getPath() + ". Description: " +  this.sampledFile.getDesc();
	}


	
	
	

}

















