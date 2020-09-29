package poetry.sampler;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import beast.core.Input;
import beast.core.MCMC;
import beast.core.util.Log;
import beast.evolution.alignment.Alignment;
import poetry.functions.XMLFunction;
import poetry.util.WeightedFile;
import poetry.util.XMLUtils;

public class RunnableSampler extends MCMC implements XMLSampler {

	
	final public Input<List<WeightedFile>> filesInput = new Input<>("file", "The location of a file containing runnable terms", new ArrayList<>());

	WeightedFile sampledFile = null;
	
	@Override
    public void initAndValidate() {
		 
		 
		 this.reset();
	 }

	@Override
	public void run() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void reset() {
		
		// Sample a runnable
		this.sampledFile = WeightedFile.sampleFile(this.filesInput.get());
		
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
		List<Element> runnersSampled = XMLUtils.getElementsByName(sampled, "runner");
		if (runnersSampled.size() != 1) throw new Exception("Could not find a <runner /> element in " + this.sampledFile.getFilePath() + " instead there were " + runnersSampled.size());
		
		
		// Import new runnable and remove this one
		Element imported = (Element) doc.importNode(runnersSampled.get(0), true);
		imported.setAttribute("id", this.getID());
		runnable.getParentNode().insertBefore(imported, runnable);
		for (Node child : XMLUtils.nodeListToList(runnable.getChildNodes())){
			imported.appendChild(child);
		}
		runnable.getParentNode().removeChild(runnable);
		
		
		// Remove all file inputs
		for (Element toRemove : XMLUtils.getElementsByName(imported, "file")) {
			imported.removeChild(toRemove);
		}
		
		// Rename the 'run' to 'runner'
		// The reason why it is named runner in the first place is to prevent
		// XMLParser from throwing an exception upon detecting two 'run' elements 
		doc.renameNode(imported, runnable.getNamespaceURI(), "run");
		
		
	}

	@Override
	public String getComments() {
		if (this.sampledFile == null) return "";
		return "Search algorithm sampled from " + this.sampledFile.getFilePath() + ". Description: " +  this.sampledFile.getDesc();
	}

	@Override
	public String getSampledID() {
		if (this.sampledFile == null) return "NA";
		return this.sampledFile.getID();
	}


}
