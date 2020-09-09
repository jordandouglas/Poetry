package xmlsimulator;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import beast.core.BEASTObject;
import beast.core.Input;
import beast.core.util.Log;
import beast.evolution.alignment.Alignment;
import beast.evolution.alignment.Sequence;
import beast.util.NexusParser;
import beast.util.Randomizer;


public class DatasetSampler extends Alignment  {
	
	final public Input<List<WeightedFile>> filesInput = new Input<>("file", "The location of a dataset file in .nexus format (can be zipped)", new ArrayList<>());
	final public Input<Integer> maxNumPartitionsInput = new Input<>("partitions", 
			"The maximum number of partitions to use, or set to zero to concatenate all into a single partition (default: all)", Integer.MAX_VALUE);
	

	protected int numFiles;
	protected int maxNumPartitions;
	protected File alignmentFile;
	protected List<Alignment> partitions;
	
	
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
	 * (Re)sample the alignment and partitions
	 */
	public void reset() {
		
		// Sample an alignment and set this object's inputs accordingly
		NexusParser parser = this.sampleAlignment();
		Alignment aln = parser.m_alignment;
		System.out.println("Sampling alignment: " +  this.alignmentFile.getAbsolutePath());
		this.initAlignment(aln.sequenceInput.get(), aln.dataTypeInput.get());
		

		// Subsample partitions from the alignment
		this.partitions = this.samplePartitions(parser);
		System.out.println("Subsampling " + this.partitions.size() + " partitions from the alignment");
		
	}
	
	
	
	public List<Alignment> getPartitions(){
		return this.partitions;
	}
	
	
	
	/**
	 * Initialise this alignment from sequences and datatype
	 * @param sequences
	 * @param dataType
	 */
	protected void initAlignment(List<Sequence> sequences, String dataType) {
		
		for (Sequence sequence : sequences) {
            sequenceInput.setValue(sequence, this);
        }
        dataTypeInput.setValue(dataType, this);
        super.initAndValidate();
		
	}
	
	
	
	/**
	 * Sample an alignment from the list of files, uniformly at random
	 * @return
	 * @throws IOException
	 */
	protected NexusParser sampleAlignment() {
		
		// Sample a file uniformly at random and get its alignment
		NexusParser parser = new NexusParser();
		int fileNum = Randomizer.nextInt(this.numFiles);
		this.alignmentFile = filesInput.get().get(fileNum).getFile();
		try {
			parser.parseFile(this.alignmentFile);
		} catch(IOException e) {
			Log.err("Cannot find " + this.alignmentFile.getAbsolutePath());
			System.exit(1);
		}
		
		return parser;
	}
	
	
	
	/**
	 * Subsample partitions uniformly at random from the alignment
	 * @return 1 alignment per partition
	 * @throws IOException 
	 */
	protected List<Alignment> samplePartitions(NexusParser parser) {
		
		
		List<Alignment> partitions = new ArrayList<Alignment>();

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
				partitions.add(parser.filteredAlignments.get(partitionIndex));
			}
		
		}
		
		
		return partitions;
		
		
	}
	


}



