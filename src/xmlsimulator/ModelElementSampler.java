package xmlsimulator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import beast.core.BEASTObject;
import beast.core.Input;
import beast.util.Randomizer;

public class ModelElementSampler extends BEASTObject {

	
	final public Input<List<WeightedFile>> filesInput = new Input<>("file", "The location of an xml file which contains model details", new ArrayList<>());
	
	
	int numFiles;
	
	@Override
	public void initAndValidate() {
		
		this.numFiles = filesInput.get().size();
		if (this.numFiles == 0) {
			throw new IllegalArgumentException("Please provide at least 1 model file");
		}
		

		
	}
	
	
	

	public void sample() {
		

		// Sample a model according to its prior probability
		WeightedFile file = this.sampleAFile();
		
			
		
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
	
	
	
	
	

}

















