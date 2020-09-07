package xmlsimulator;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import beast.core.BEASTObject;
import beast.core.Input;
import beast.evolution.alignment.Alignment;
import beast.util.NexusParser;
import beast.util.Randomizer;


public class DatasetSampler extends BEASTObject  {
	
	final public Input<List<WeightedFile>> filesInput = new Input<>("file", "The location of a dataset file in .nexus format (can be zipped)", new ArrayList<>());
	final public Input<Integer> maxNumPartitionsInput = new Input<>("partitions", 
			"The maximum number of partitions to use, or set to zero to concatenate all into a single partition (default: all)", Integer.MAX_VALUE);
	

	protected int numFiles;
	protected int maxNumPartitions;
	
	@Override
	public void initAndValidate() {
		
		this.numFiles = filesInput.get().size();
		this.maxNumPartitions = maxNumPartitionsInput.get();
		if (this.maxNumPartitions < 0) {
			throw new IllegalArgumentException("Please set 'partitions' to at least 0");
		}
		if (this.numFiles == 0) {
			throw new IllegalArgumentException("Please provide at least 1 alignment file");
		}
		

		
	}
	
	
	
	/**
	 * Samples a dataset uniformly at random, and subsamples its partitions uniformly at random
	 * @return 1 alignment per partition
	 * @throws IOException 
	 */
	public List<Alignment> sampleAlignments() throws IOException {
		
		
		List<Alignment> partitions = new ArrayList<Alignment>();
		
		// Sample a file uniformly at random
		NexusParser parser = new NexusParser();
		int fileNum = Randomizer.nextInt(this.numFiles);
		parser.parseFile(filesInput.get().get(fileNum).getFile());
		
		
		// Just 1 partition?
		int numPart = parser.filteredAlignments.size();
		
		if (numPart == 1 || this.maxNumPartitions == 0) {
			partitions.add(parser.m_alignment);
			
		}else {
		

			// Sample the number of partitions to subsample (UAR)
			int numPartSamples = Randomizer.nextInt(Math.min(this.maxNumPartitions, numPart));

			// Subsample this many partitions
			int[] indices = Randomizer.permuted(numPart);
			for (int i = 0; i < numPartSamples; i ++) {
				int partitionIndex = indices[i];
				partitions.add(parser.filteredAlignments.get(i));
			}
		
		}
		
		
		return partitions;
		
		
	}
	


}



