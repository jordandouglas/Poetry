package poetry.util;

import java.util.ArrayList;
import java.util.List;

import beast.core.BEASTInterface;
import beast.evolution.alignment.Alignment;
import beast.evolution.tree.Tree;
import beast.math.distributions.MRCAPrior;
import beast.util.ClusterTree;
import poetry.learning.WeightSampler;
import poetry.sampler.POEM;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

/**
 * Extracts information from a BEAST2 session into a Weka Instances object
 * @author jdou557
 *
 */
public class BEAST2Weka {

	public static Instances getInstance(Alignment dataset, Tree tree, WeightSampler weightSampler) {

		
		// TODO dimension needs to be calculated properly not by using operator dimension
		
		final int nattr = weightSampler.getNumPoems()*2 + 8;
		Instance instance = new DenseInstance(nattr);
		
		// Create attribute list
		ArrayList<Attribute> attributes = new ArrayList<>();
		attributes.add(getNtaxaAttr()); // Ntaxa
		attributes.add(getNsitesAttr()); // Nsites
		attributes.add(getNpatternsAttr()); // Npatterns
		attributes.add(getNpartitionsAttr()); // Npartitions
		attributes.add(getNcalibrationsAttr()); // Ncalibrations
		attributes.add(getPgapsAttr()); // Pgaps
		attributes.add(getTreeHeightAttr()); // Tree height
		attributes.add(getNcharAttr()); // Num states
		for (POEM poem : weightSampler.getPoems()) {
			attributes.add(getPoemWeightDimensionAttr(poem)); // Weight
			attributes.add(getPoemDimensionAttr(poem)); // Dimension
		}
		Instances instances = new Instances("beast2", attributes,  nattr);
		instance.setDataset(instances);
		
		// Partitions
		List<Alignment> partitions = new ArrayList<>();
		partitions.add(dataset);
				
		// 1 Ntaxa
		instance.setValue(instances.attribute(getNtaxaAttr().name()), getNtaxa(partitions));
		
		// 2 Nsites
		instance.setValue(instances.attribute(getNsitesAttr().name()), getNsites(partitions));
		
		// 3 Npatterns
		instance.setValue(instances.attribute(getNpatternsAttr().name()), getNpatterns(partitions));
		
		// 4 Npartitions
		instance.setValue(instances.attribute(getNpartitionsAttr().name()), getNpartitions(partitions));

		// 5 Ncalibrations
		instance.setValue(instances.attribute(getNcalibrationsAttr().name()), getNcalibrations(tree));
		
		// 6 Proportion of gaps
		instance.setValue(instances.attribute(getPgapsAttr().name()), getPgaps(partitions));
		
		// 7 Tree height
		instance.setValue(instances.attribute(getTreeHeightAttr().name()), getTreeHeight(partitions));
		
		// 8 Number of states
		instance.setValue(instances.attribute(getNcharAttr().name()), getNchar(partitions));
		
		// POEMS
		for (POEM poem : weightSampler.getPoems()) {
			
			// Weight/dimension
			instance.setValue(instances.attribute(getPoemWeightDimensionAttr(poem).name()), getPoemWeightDimension(poem));
			
			// Dimension
			instance.setValue(instances.attribute(getPoemDimensionAttr(poem).name()), getPoemDimension(poem));
			
		}
		
		instances.add(instance);
		return instances;
		
	}
	
	
	/** 
	 * Get weight of a poem divided by its dimension attribute
	 * @return
	 */
	public static Attribute getPoemWeightDimensionAttr(POEM poem) {
		return new Attribute(poem.getWeightColname() + ".d");
	}
	
	/** 
	 * Get dimension
	 * @return
	 */
	public static Attribute getPoemDimensionAttr(POEM poem) {
		return new Attribute(poem.getDimColName());
	}


	/** 
	 * Get the proportion of gaps attribute
	 * @return
	 */
	public static Attribute getPgapsAttr() {
		return new Attribute("pgaps");
	}
	
	/** 
	 * Get the number of characters attribute
	 * @return
	 */
	public static Attribute getNcharAttr() {
		return new Attribute("nchar");
	}
	
	/** 
	 * Get the number of taxa attribute
	 * @return
	 */
	public static Attribute getNtaxaAttr() {
		return new Attribute("ntaxa");
	}
	
	/** 
	 * Get the number of sites attribute
	 * @return
	 */
	public static Attribute getNsitesAttr() {
		return new Attribute("nsites");
	}
	
	/** 
	 * Get the number of sites attribute
	 * @return
	 */
	public static Attribute getNpatternsAttr() {
		return new Attribute("npatterns");
	}
	
	/** 
	 * Get the number of partitions attribute
	 * @return
	 */
	public static Attribute getNpartitionsAttr() {
		return new Attribute("npartitions");
	}
	
	/** 
	 * Get the number of calbrations attribute
	 * @return
	 */
	public static Attribute getNcalibrationsAttr() {
		return new Attribute("ncalibrations");
	}
	
	
	
	/** 
	 * Get the proportion of gaps attribute
	 * @return
	 */
	public static Attribute getTreeHeightAttr() {
		return new Attribute("NJtree.height");
	}
	
	
	
	/**
	 * Get the proportion of gaps in an alignment
	 * @param partitions
	 * @return
	 */
	public static double getPgaps(List<Alignment> partitions) {
		
		if (partitions == null) return 0;
		
		// Proportion of gaps in alignment(s)
		int numGaps = 0;
		int nsites = 0;
		for (Alignment aln : partitions) {
			for (String taxon : aln.getTaxaNames()) {
				String seq = aln.getSequenceAsString(taxon);
				numGaps += seq.length() - seq.replace("-", "").length();
				nsites += seq.length();
			}
		}
		double proportion = 1.0 * numGaps / nsites;
		return proportion;	
				
		
	}
	
	/**
	 * Estimated tree height using neighbour joining
	 * @param partitions
	 * @return
	 */
	public static double getTreeHeight(List<Alignment> partitions) {
		
		if (partitions == null) return 0;
		
		// Take the weighted-mean height across all partitions
		double weightedMeanHeight = 0;
		int nsitesTotal = 0;
		ClusterTree tree = new ClusterTree();
		
		for (Alignment aln : partitions) {
			
			// Set estimate to false so that the tree is still calculated even in -resume mode
			tree.initByName("clusterType", "neighborjoining", "taxa", aln, "estimate", false);
			
			// Calculate height
			double height = tree.getRoot().getHeight();
			double nsites = aln.getSiteCount();
			weightedMeanHeight += height * nsites;
			nsitesTotal += nsites;
		}
		
		return weightedMeanHeight / nsitesTotal;
		
	}

	
	/**
	 * Ntaxa
	 * @param partitions
	 * @return
	 */
	public static int getNtaxa(List<Alignment> partitions) {
		if (partitions == null || partitions.isEmpty()) return 0;
		return partitions.get(0).getTaxaNames().size();
	}

	
	/**
	 * Nsites
	 * @param partitions
	 * @return
	 */
	public static int getNsites(List<Alignment> partitions) {
		if (partitions == null || partitions.isEmpty()) return 0;
		int siteCount = 0;
		for (Alignment aln : partitions) {
			siteCount += aln.getSiteCount();
		}
		return siteCount;
	}

	/**
	 * Npatterns
	 * @param partitions
	 * @return
	 */
	public static int getNpatterns(List<Alignment> partitions) {
		if (partitions == null || partitions.isEmpty()) return 0;
		int patternCount = 0;
		for (Alignment aln : partitions) {
			patternCount += aln.getPatternCount();
		}
		return patternCount;
	}
	
	
	/**
	 * Npartitions
	 * @param partitions
	 * @return
	 */
	public static int getNpartitions(List<Alignment> partitions) {
		if (partitions == null || partitions.isEmpty()) return 0;
		return partitions.size();
	}
	
	
	
	/**
	 * Look for any MRCA priors pointing to the tree and count them
	 * @param partitions
	 * @return
	 */
	public static int getNcalibrations(Tree tree) {
		if (tree == null) return 0;
		int ncalibrations = 0;
		for (BEASTInterface obj : tree.getOutputs()) {
			if (obj instanceof MRCAPrior) ncalibrations++;
		}
		return ncalibrations;
	}
	
	
	/** 
	 * Get the number of characters
	 * @return
	 */
	public static int getNchar(List<Alignment> partitions) {
		if (partitions == null || partitions.isEmpty()) return 0;
		return partitions.get(0).getDataType().getStateCount();
	}
	
	
	/** 
	 * Get weight of a poem divided by its dimension attribute
	 * @return
	 */
	public static double getPoemWeightDimension(POEM poem) {
		return poem.getWeight() / poem.getDim();
	}
	
	
	/** 
	 * Get weight of a poem divided by its dimension attribute
	 * @return
	 */
	public static int getPoemDimension(POEM poem) {
		return poem.getDim();
	}
	

	
}



