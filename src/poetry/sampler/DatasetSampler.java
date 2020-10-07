package poetry.sampler;


import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;


import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import beast.core.BEASTInterface;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.parameter.RealParameter;
import beast.core.util.Log;
import beast.evolution.alignment.Alignment;
import beast.evolution.alignment.FilteredAlignment;
import beast.evolution.alignment.Sequence;
import beast.evolution.alignment.Taxon;
import beast.evolution.alignment.TaxonSet;
import beast.evolution.datatype.Aminoacid;
import beast.evolution.datatype.DataType;
import beast.evolution.datatype.Nucleotide;
import beast.evolution.tree.Tree;
import beast.evolution.tree.TreeInterface;
import beast.evolution.tree.TreeUtils;
import beast.math.distributions.MRCAPrior;
import beast.math.distributions.ParametricDistribution;
import beast.util.ClusterTree;
import beast.util.NexusParser;
import beast.util.Randomizer;
import beast.util.XMLProducer;
import poetry.XMLSimProducer;
import poetry.functions.XMLFunction;
import poetry.util.WeightedFile;
import poetry.util.XMLUtils;


public class DatasetSampler extends Alignment implements XMLSampler  {
	

	
	final public Input<List<WeightedFile>> filesInput = new Input<>("file", "The location of a dataset file in .nexus format (can be zipped)", new ArrayList<>());
	final public Input<Integer> maxNumPartitionsInput = new Input<>("partitions", 
			"The maximum number of partitions to use, or set to zero to concatenate all into a single partition (default: all)", 100);
	final public Input<List<String>> norepeatsInput = new Input<>("norepeat", "ids of elements that should not be repeated 1x for each partition", new ArrayList<>());
	
	final public Input<RealParameter> clockRateInput = new Input<>("clock.rate", "The clock rate parameter. If there are MRCA priors, this will create a clock rate prior.");
	
	
	//protected String newID; // A new unique identifier for this element in output xmls
	protected int numFiles;
	protected int maxNumPartitions;
	protected WeightedFile sampledFile;
	protected List<Alignment> partitions;
	protected List<String> norepeats;
	protected DataType datatype;
	protected boolean hasBegun;
	
	TreeInterface tree;
	
	// Clock rate lognormal priors
	protected RealParameter clockRate;
	protected double clockPriorMean;
	protected double clockPriorSD;
	protected List<MRCAPrior> calibrations;
	
	
	
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
		
		if (this.getID() == null || this.getID().isEmpty()) {
			throw new IllegalArgumentException("Please ensure this object has an ID");
		}
		
		this.norepeats = norepeatsInput.get();
		this.datatype = null;
		
		this.clockRate = clockRateInput.get();
		
		this.tree = this.getTree();
		if (this.tree == null) Log.warning("Warning: cannot find a tree pointing to " + this.getID());
		
		this.reset();
		this.hasBegun = false;
		
	}
	
	
	/**
	 * Find the tree pointing to this alignment
	 * @return
	 */
	private TreeInterface getTree() {
		
		// Find the taxonset pointing to this alignment
		TreeInterface tree = null;
		for (BEASTInterface obj : this.getOutputs()) {
			System.out.println(obj.getID());
			if (obj instanceof TaxonSet) {
				
				
				// Find the tree pointing to this taxonset
				for (BEASTInterface obj2 : obj.getOutputs()) {
					
					if (obj2 instanceof TreeInterface) {
						
						if (tree != null) {
							throw new IllegalArgumentException("More than 1 tree is pointing to " + this.getID() + ". Poetry can only handle single trees "
									+ " at this stage.");
						}
						
						tree = (TreeInterface) obj2;
						break;
					}
					
				}
				
			}
		}
		
		
		return tree;
		
	}
	
	

	
	/**
	 * (Re)sample the alignment and partitions
	 */
	@Override
	public void reset() {
		
		this.hasBegun = true;
		
		// Sample an alignment and set this object's inputs accordingly
		this.sequenceInput.get().clear();
		NexusParser parser = this.sampleAlignment();
		Alignment aln = parser.m_alignment;
		this.datatype = aln.getDataType();
		
		
		
		System.out.println("Sampling alignment: " +  this.sampledFile.getFilePath());
		this.initAlignment(aln.sequenceInput.get(), aln.dataTypeInput.get());
		
		// Subsample partitions from the alignment
		this.partitions = null; // Clear some memory
		this.partitions = this.samplePartitions(parser);
		System.out.println("Subsampling " + this.partitions.size() + " partitions from the alignment");
		
		
		// MRCA priors (if applicable)
		this.processMRCAPriors(parser);
		
		
		// Remove unused sites from the main alignment
		this.tidyAlignment();
		
		
	}
	
	
	
	/**
	 * Record all MRCA priors and use this to come up with a clock rate prior (for naughty Bayesians)
	 * @param parser
	 */
	protected void processMRCAPriors(NexusParser parser) {
		
		if (this.clockRate == null) return;
		
		
		// Get calibrations which actually have a prior
		this.calibrations = new ArrayList<>();
		for (MRCAPrior prior : parser.calibrations) {
			if (prior.distInput.get() != null) {
				this.calibrations.add(prior);
			}
		}
		
		
		if (this.calibrations.isEmpty()) {
			this.clockPriorMean = 0;
			this.clockPriorSD = 0;
		}else {
			
			
			// Expected mutation rate is average of 
			//			crown heights (in substitutions/site) divided by by crown times (time units)  
			int numCalibrations = this.calibrations.size();
			double[] crownRates = new double[numCalibrations];
			for (int i = 0; i < numCalibrations; i ++) crownRates[i] = 0;
			
			
			ClusterTree tree = new ClusterTree();
			
			
			// For each partition
			for (Alignment aln : this.partitions) {
				tree.initByName("clusterType", "neighborjoining", "taxa", aln);
				
				// Get mrca node height
				for (int i = 0; i < this.calibrations.size(); i ++) {
					MRCAPrior prior = this.calibrations.get(i);
					TaxonSet taxa = prior.taxonsetInput.get();
					taxa.initAndValidate();
					
					// Node height (substitutions per site)
					beast.evolution.tree.Node mrca = TreeUtils.getCommonAncestorNode(tree, taxa.getTaxaNames());
					double height = mrca.getHeight();
					
					// Mean node time under the prior
					ParametricDistribution distr = prior.distInput.get();
					if (distr == null) {
					System.out.println(distr);
					}
					double time = distr.getMean();
					
					// Clock rate
					double rate = height / time;
					crownRates[i] += rate / this.partitions.size();
				
				}
				
			}
			
			
			// Calculate log mean 
			double logmean = 0;
			for (int i = 0; i < numCalibrations; i ++) {
				crownRates[i] = Math.log(crownRates[i]);
				logmean += crownRates[i];
			}
			logmean = logmean / numCalibrations;
			
			// Log standard deviation
			double logsd = 0;
			if (numCalibrations == 1) {
				logsd = 0.2;
			}else {
				for (int i = 0; i < numCalibrations; i ++) {
					logsd += Math.pow(crownRates[i] - logmean, 2);
				}
				logsd = logsd / numCalibrations;
				logsd = Math.sqrt(logsd);
			}
			if (logsd <= 0) logsd = 0.2;
			
			
			
			
			this.clockPriorMean = logmean;
			this.clockPriorSD = logsd;
				
			System.out.println("Computed a clock rate prior: lognormal(" + clockPriorMean + "," +  this.clockPriorSD + ")");
				
			
		}
		
		
	}
	
	
	
	private void tidyAlignment() {
		
		if (true) return;
		
		// Which sites to include?
		int nsites = this.sequences.get(0).getData().length();
		boolean[] include = new boolean[nsites];
		for (int i = 0; i < nsites; i ++) include[i] = false;
		
		
		// Find the sites which are being used by any partition
		for (Alignment alignment : this.partitions) {
			
			FilteredAlignment filtered = (FilteredAlignment) alignment;
			//filtered/
			int[] sitesFilter = filtered.indices();
			for (int i = 0; i < sitesFilter.length; i ++) {
				int site = sitesFilter[i];
				include[site-1] = true;
			}
			
		}
		
		
		
		
		// Remove duplicates
		//LinkedHashSet<Integer> hashSet = new LinkedHashSet<>(sites);
		//sites = new ArrayList<>(hashSet);
		
		
		// Sort in reverse order
		//sites.sort(Collections.reverseOrder());
		
		
		
		//for (int i = 0; i < )
		
		
		
	}
	
	
	/**
	 * Coerce the XML into that of an Alignment
	 */
	@Override
	public void tidyXML(Document doc, Element runnable, List<XMLFunction> functions) throws Exception {
		
		
		Element thisEle = XMLUtils.getElementById(doc, this.getID());
		

		// Get all input names which are specific to the Alignment superclass
		List<String> namesToKeep = new ArrayList<String>();
		for (Field field : Alignment.class.getDeclaredFields()) {
			Class<?> cls = field.getType();
			if (cls.equals(Input.class)) {
				final Input<?> input = (Input<?>) field.get(this);
				namesToKeep.add(input.getName());
			}
		}

		// Remove all children that don't correspond to the Alignment superclass
		XMLUtils.removeChildrenWithoutName(thisEle, namesToKeep);
		
		// Change the spec attribute to 'Alignment'
		thisEle.setAttribute("spec", Alignment.class.getCanonicalName());
		
		

		
		
		
		// Replace all occurrences of $(partition) with partition names, and repeat these elements to reach the number of partitions
		List<Element> idMatch = XMLUtils.getAllElementsWithAttrMatch(doc, "*", "$(partition)");
		//idMatch.addAll(XMLUtils.getAllElementsWithAttrMatch(doc, "idref", "$(partition)"));
		List<Element> roots = XMLUtils.getTopLevels(idMatch);
		for (Element root : roots) {
			
			
			// If this has been flagged under 'norepeat' then do not copy it once for each partition. Only use partition 1.
			boolean repeat = true;
			String rootID = root.getAttribute("id");
			if (rootID.equals(this.getID())) continue;
			if (rootID != null && this.norepeats.contains(rootID)) repeat = false;
			

			// The full subtree will be repeated once for each partition
			for (int pNum = 0; pNum < this.partitions.size(); pNum++) {
				
				Alignment partition = this.partitions.get(pNum);
				
				// Partition info
				FilteredAlignment filtered = (FilteredAlignment) partition;
				String pName = filtered.getID();
				//int[] filter = filtered.indices();
				
				// Deep copy the subtree
				Element copy = (Element) root.cloneNode(true);
				
				// Replace all occurrences of $(partition) with the partition name
				if (repeat) XMLUtils.XMLReplace(copy, "$(partition)", pName);
				
				// Add this into the XML subtree
				root.getParentNode().insertBefore(copy, root);
				
				// Make sure all ids are unique
				for (Node child : XMLUtils.getAllElements(copy)) {
					if (!(child instanceof Element)) continue;
					Element ele = (Element) child;
					if (!ele.hasAttribute("id")) continue;
					//XMLUtils.setID(ele, ele.getAttribute("id"), doc);
				}


				
				if (!repeat) break;


			}
			
			// Remove the original subtree
			root.getParentNode().removeChild(root);
			

			
		}
		
		
		
		
		// One alignment per partition rather than just a long one
		for (int pNum = 0; pNum < this.partitions.size(); pNum++) {
			
			FilteredAlignment partition = (FilteredAlignment) this.partitions.get(pNum);
			String pName = partition.getID();
			//partition.setID(this.getID());
	        Document pdoc = XMLUtils.loadXMLFromString(new XMLProducer().toXML(partition));
	        Element partitionNode = XMLUtils.getElementById(pdoc, pName);
	        Element importedNode = (Element) doc.importNode(partitionNode, true);
	        
	        // Set id to partition name
	        importedNode.setAttribute("id", this.getID().replace("$(partition)", pName));
	        
	        // Remove data from element children and make it an attribute
	        importedNode.setAttribute("data", "@" + this.getID());
	        NodeList children = importedNode.getElementsByTagName("data");
	        if (children.getLength() > 0) importedNode.removeChild(children.item(0));
	        
	        // Add the data to the xml
	        thisEle.getParentNode().insertBefore(importedNode, thisEle);
	        doc.renameNode(importedNode, null, "data");
	        
	        
			
		}
		
		
		// Clock rate
		if (this.clockRate != null) {
			Element prior = XMLUtils.getElementById(doc, "prior");
			if (prior == null) {
				throw new Exception("Cannot locate element with id 'prior'");
			}
			
			// Clock rate initial value
			Element clockRateElement = XMLUtils.getElementById(doc, this.clockRate.getID());
			clockRateElement.setAttribute("value", "" + Math.exp(this.clockPriorMean));
			for (Node node : XMLUtils.nodeListToList(clockRateElement.getChildNodes())) {
				if (node instanceof Text) {
					clockRateElement.removeChild(node);
				}
			}
			
			// Clock rate prior
			Element clockRatePrior = doc.createElement("prior");
			clockRatePrior.setAttribute("name", "distribution");
			clockRatePrior.setAttribute("x", "@" + this.clockRate.getID());
			prior.appendChild(clockRatePrior);
			
			Element logNormal = doc.createElement("LogNormal");
			logNormal.setAttribute("name", "distr");
			logNormal.setAttribute("M", "" + this.clockPriorMean);
			logNormal.setAttribute("S", "" + this.clockPriorSD);
			clockRatePrior.appendChild(logNormal);
			
			// MRCA priors
			XMLSimProducer producer = new XMLSimProducer();
			for (MRCAPrior mrcaPrior : this.calibrations) {
				
				
				String xml = producer.toXML(mrcaPrior);
				Document priorDoc = XMLUtils.loadXMLFromString(xml);
				Element priorEle = XMLUtils.getElementById(priorDoc, mrcaPrior.getID());
				Element imported = (Element) doc.importNode(priorEle, true);
				doc.renameNode(imported, null, "prior");
				imported.setAttribute("tree", "@" + this.tree.getID());
				imported.setAttribute("name", "distribution");
				prior.appendChild(imported);
				
				// Remove id's from distr and all if its descendents
				for (Element distr : XMLUtils.getElementsByName(imported, "distr")) {
					distr.removeAttribute("id");
					for (Node child : XMLUtils.getAllElements(distr)) {
						if (child instanceof Element) {
							Element c = (Element) child;
							c.removeAttribute("id");
						}
					}
					
				}
				
				
				
				
			}
			
		
		}
		

		
		
		
		
		// Remove the generic unfiltered alignment
		//thisEle.getParentNode().removeChild(thisEle);
		
		
		
		
	
		
		
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
		
		this.sequenceInput.get().clear();
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
		
		// Sample an alignment proportionally to its weight
		NexusParser parser = new NexusParser();
		this.sampledFile =  WeightedFile.sampleFile(this.filesInput.get());
		try {
			File file = this.sampledFile.unzipFile();
			parser.parseFile(file);
			this.sampledFile.close();
		} catch(IOException e) {
			Log.err("Cannot find " + this.sampledFile.getFilePath());
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
			int numPartSamples = Randomizer.nextInt(Math.min(this.maxNumPartitions, numPart)) + 1;

			// Subsample this many partitions
			int[] indices = Randomizer.permuted(numPart);
			for (int i = 0; i < numPartSamples; i ++) {
				int partitionIndex = indices[i];
				partitions.add(parser.filteredAlignments.get(partitionIndex));
			}
		
		}
		
		
		return partitions;
		
		
	}


	@Override
	public String getComments() {
		return "Dataset sampled from " + this.sampledFile.getFilePath() +
						" with " + this.partitions.size() + " partitions. Description: " +  this.sampledFile.getDesc();
	}


	
	
	/**
	 * Return species to taxon mapping for this alignment, according to the string split function specified in the WeightedFile
	 * @param alignment
	 * @param wfile
	 * @return
	 */
	public static HashMap<String, List<String>> getSpeciesMap(Alignment alignment, WeightedFile wfile){
		
		if (!wfile.hasSpeciesMap()) return null;
		HashMap<String, List<String>> map = new HashMap<String, List<String>>();
		
		// Iterate through taxon names
		for (String taxon : alignment.getTaxaNames()) {
			String species = wfile.getSpecies(taxon); 
			List<String> mapped;
			if (map.containsKey(species)) {
				mapped = map.get(species);
			}else {
				mapped = new ArrayList<String>();
			}
			mapped.add(taxon);
			map.put(species, mapped);
			//map.put(taxon, species);
		}
		
		return map;
		
	}

	
	
	/**
	 * Methods below are used by the Condition class
	 */
	
	/**
	 * Is the currently sampled alignment nucleotide?
	 * @return
	 */
	public boolean isDNA() {
		return this.datatype instanceof Nucleotide;
	}
	
	
	
	/**
	 * Is the currently sampled alignment amino acid?
	 * @return
	 */
	public boolean isAminoAcid() {
		return this.datatype instanceof Aminoacid;
	}
	
	/**
	 * Does the currently sampled file have a species tree map?
	 * @return
	 */
	public boolean hasSpeciesMap() {
		return this.sampledFile.hasSpeciesMap();
	}
	
	
	/**
	 * Are the tips dated?
	 * @return
	 */
	public boolean tipsAreDated() {
		return this.sampledFile.tipsAreDated();
	}
	
	
	
	/**
	 * Number of species (if MSC analysis)
	 * @return
	 */
	public int getNumSpecies() {
		if (!this.hasSpeciesMap() || this.partitions.isEmpty()) return 1;
		return DatasetSampler.getSpeciesMap(this.partitions.get(0), this.sampledFile).size();
	}
	
	
	/**
	 * Return the taxon to species map as an XML Element
	 * @return
	 */
	public List<Element> getMSCTaxonSet() {
		
		
		if (!this.hasSpeciesMap() || this.partitions.isEmpty()) return null;
		
		// Build a TaxonSet using the taxon to species map
		List<Element> elements = new ArrayList<Element>();
		HashMap<String, List<String>> speciesMap = DatasetSampler.getSpeciesMap(this.partitions.get(0), this.sampledFile);
		
		// Get all individuals within any given species
		for (String s : speciesMap.keySet()) {
			
			// Create taxonset for this species
			List<String> t = speciesMap.get(s);
			List<Taxon> taxa = Taxon.createTaxonList(t);
			TaxonSet taxonSet = new TaxonSet(taxa);
			
			// Convert to XML Element
			XMLSimProducer producer = new XMLSimProducer();
			String sXML = producer.toXML(taxonSet);
			try {
				Document doc = XMLUtils.loadXMLFromString(sXML);
				Element run = (Element) doc.getElementsByTagName("run").item(0);
				//Element element = null;
				//for (Node child : XMLUtils.nodeListToList(run.getChildNodes())) {
					//if ( !(child instanceof Element)) continue;
					//element = (Element) child;
					//break;
				//}
				run.setAttribute("id", XMLUtils.getUniqueID(doc, s));
				doc.renameNode(run, null, "taxon");
				elements.add(run);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		
		return elements;
		
	}
	
	@Override
	public int getSiteCount() {
		
		if (this.partitions == null || !hasBegun) return super.getSiteCount();
		
		int numSites = 0;
		for (Alignment aln : this.partitions) {
			numSites += aln.getSiteCount();
		}
		
		return numSites;
		
	}
	
	
	@Override
	public int getPatternCount() {
		
		if (this.partitions == null || !hasBegun) return super.getPatternCount();
		
		int numPatterns = 0;
		for (Alignment aln : this.partitions) {
			FilteredAlignment partition = (FilteredAlignment) aln;
			numPatterns += partition.getPatternCount();
		}
		
		return numPatterns;
		
	}



	/**
	 * Number of partitions
	 * @return
	 */
	public int getNumPartitions() {
		if (this.partitions == null) return 0;
		return this.partitions.size();
	}
	
	
	/**
	 * Number of partitions as a double
	 * @return
	 */
	public Double getNumPartitionsDouble() {
		return (double) this.getNumPartitions();
	}
	
	
	/**
	 * Proportion of sites which are gaps
	 * @return
	 */
	public double getProportionGaps() {
		if (this.partitions == null) return 0;
		
		int numGaps = 0;
		int nsites = 0;
		for (Alignment aln : this.partitions) {
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
	 * Get the sampled file path
	 * @return
	 */
	public String getFilePath() {
		if (this.sampledFile == null) return "NA";
		return this.sampledFile.getFilePath();
	}



	/**
	 * Build a neighbour joining tree and return its height
	 * If there are multiple partitions then one tree per partition is computed and the weighted-mean (proportional to nsites) is returned
	 * @return
	 */
	public double getEstimatedTreeHeight() {
		
		if (this.partitions == null) return 0;
		
		// Take the weighted-mean height across all partitions
		double weightedMeanHeight = 0;
		int nsitesTotal = 0;
		ClusterTree tree = new ClusterTree();
		
		for (Alignment aln : this.partitions) {
			tree.initByName("clusterType", "neighborjoining", "taxa", aln);
			double height = tree.getRoot().getHeight();
			double nsites = aln.getSiteCount();
			weightedMeanHeight += height * nsites;
			nsitesTotal += nsites;
		}
		
		return weightedMeanHeight / nsitesTotal;
	}




	@Override
	public String getSampledID() {
		if (this.sampledFile == null) return "NA";
		return this.sampledFile.getID();
	}






}












