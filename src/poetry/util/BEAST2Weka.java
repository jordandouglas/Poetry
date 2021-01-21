package poetry.util;

import java.util.ArrayList;
import java.util.List;

import beast.core.BEASTInterface;
import beast.core.Operator;
import beast.core.StateNode;
import beast.core.parameter.RealParameter;
import beast.core.util.Log;
import beast.evolution.alignment.Alignment;
import beast.evolution.alignment.FilteredAlignment;
import beast.evolution.branchratemodel.BranchRateModel;
import beast.evolution.branchratemodel.StrictClockModel;
import beast.evolution.branchratemodel.UCRelaxedClockModel;
import beast.evolution.likelihood.GenericTreeLikelihood;
import beast.evolution.likelihood.TreeLikelihood;
import beast.evolution.sitemodel.SiteModel;
import beast.evolution.sitemodel.SiteModelInterface;
import beast.evolution.speciation.BirthDeathGernhard08Model;
import beast.evolution.speciation.YuleModel;
import beast.evolution.substitutionmodel.Blosum62;
import beast.evolution.substitutionmodel.GTR;
import beast.evolution.substitutionmodel.HKY;
import beast.evolution.substitutionmodel.JukesCantor;
import beast.evolution.substitutionmodel.MTREV;
import beast.evolution.substitutionmodel.SubstitutionModel;
import beast.evolution.substitutionmodel.WAG;
import beast.evolution.tree.Tree;
import beast.evolution.tree.TreeDistribution;
import beast.evolution.tree.coalescent.BayesianSkyline;
import beast.evolution.tree.coalescent.Coalescent;
import beast.evolution.tree.coalescent.ConstantPopulation;
import beast.evolution.tree.coalescent.ExponentialGrowth;
import beast.math.distributions.MRCAPrior;
import beast.util.ClusterTree;
import poetry.learning.DimensionalSampler;
import poetry.learning.ModelValue;
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
	


	public static Instances getInstance(Alignment dataset, Tree tree, WeightSampler weightSampler, List<ModelValue> modelValues) {


		
		
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
		
		attributes.add(getTreeClockModelAttr()); // Tree clock model
		attributes.add(getSiteModelAttr()); // Site model
		attributes.add(getSiteHetModelAttr()); // Site heterogeneity model
		//attributes.add(getBranchRateModelAttr()); // Branch rate model
		attributes.add(getTreeModelAttr()); // Tree model
		attributes.add(getSearchModeAttr()); // Search mode
		
		for (POEM poem : weightSampler.getPoems()) {
			//attributes.add(getPoemWeightDimensionAttr(poem)); // Weight/dimension
			attributes.add(getPoemWeightAttr(poem)); // Weight
			attributes.add(getPoemDimensionAttr(poem)); // Dimension
		}
		
		attributes.add(getTargetAttr()); // Class 
		
		if (modelValues != null) {
			for (ModelValue mv : modelValues) {
				
				// Do not allow duplicate column names
				boolean duplicate = false;
				for (Attribute attr : attributes) {
					if (attr.name().equals(mv.getModel())) {
						duplicate = true;
						break;
					}
				}
				if (duplicate) continue;
				
				attributes.add(getModelValueAttr(mv)); // Model value
			}
		}
		final int nattr = attributes.size();
		Instances instances = new Instances("beast2", attributes,  nattr);
		instances.setClass(instances.attribute(getTargetAttr().name()));
		
		
		
		// Create the instance
		Instance instance = new DenseInstance(nattr);
		instance.setDataset(instances);
		
		
		// Partitions
		List<Alignment> partitions = new ArrayList<>();
		partitions.add(dataset);
		
				
		// 1 Ntaxa
		instance.setValue(instances.attribute(getNtaxaAttr().name()), Math.log(getNtaxa(partitions)));
		
		// 2 Nsites
		instance.setValue(instances.attribute(getNsitesAttr().name()), Math.log(getNsites(partitions)));
		
		// 3 Npatterns
		instance.setValue(instances.attribute(getNpatternsAttr().name()), Math.log(getNpatterns(partitions)));
		
		// 4 Npartitions
		instance.setValue(instances.attribute(getNpartitionsAttr().name()), Math.log(getNpartitions(partitions)));

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
			//instance.setValue(instances.attribute(getPoemWeightDimensionAttr(poem).name()), getPoemWeightDimension(poem, getNtaxa(partitions)));
			
			// Weight
			instance.setValue(instances.attribute(getPoemWeightAttr(poem).name()), getPoemWeight(poem, getNtaxa(partitions)));
			
			// Dimension
			//instance.setValue(instances.attribute(getPoemDimensionAttr(poem).name()), Math.log(getPoemDimension(poem)));
			instance.setValue(instances.attribute(getPoemDimensionAttr(poem).name()), getPoemDimension(poem));
			
		}
		
		
		//instance.setValue(instances.attribute(getTargetAttr().name()), 1.0);
		
		
		// Attempt to find the model using the xml
		String treeClockModel = getTreeClockModel(tree);
		String treeModel = getTreeModel(tree);
		String branchModel = getBranchRateModel(tree);
		String siteModel = getSiteModel(tree);
		String siteHetModel = getSiteHetModel(tree);
		String searchMode = getSearchMode(weightSampler);
		System.out.println("The clock model is " + treeClockModel);
		System.out.println("The tree model is " + treeModel);
		System.out.println("The branch rate model is " + branchModel);
		System.out.println("The site model is " + siteModel);
		System.out.println("The site het model is " + siteHetModel);
		System.out.println("The search mode is " + searchMode);
		
		
		if (treeClockModel != null)  instance.setValue(instances.attribute(getTreeClockModelAttr().name()), treeClockModel);
		if (treeModel != null) instance.setValue(instances.attribute(getTreeModelAttr().name()), treeModel);
		//if (branchModel != null) instance.setValue(instances.attribute(getBranchRateModelAttr().name()), branchModel);
		if (siteModel != null) instance.setValue(instances.attribute(getSiteModelAttr().name()), siteModel);
		if (siteHetModel != null) instance.setValue(instances.attribute(getSiteHetModelAttr().name()), siteHetModel);
		if (searchMode != null) instance.setValue(instances.attribute(getSearchModeAttr().name()), searchMode);
		

		
		
		
		// Set user set values. These will override any auto-detected attributes
		if (modelValues != null) {
			for (ModelValue mv : modelValues) {
				if (mv.isNumeric()) {
					instance.setValue(instances.attribute(getModelValueAttr(mv).name()), getModelValueNumeric(mv));
				}else {
					instance.setValue(instances.attribute(getModelValueAttr(mv).name()), getModelValueNominal(mv));
				}
			}
		}
		
		
		//Log.warning("Current session:" + instance.toString());
		
		instances.add(instance);
		return instances;
		
	}
	


	/**
	 * Model details
	 * @return
	 */
	public static Attribute getTreeClockModelAttr() {
		ArrayList<String> values = new ArrayList<>();
		values.add("Fixed.clock.model");
		values.add("Estimated.clock.model");
		values.add("Calibrated.clock.model");
		return new Attribute("treeclock.model", values);
	}
	public static Attribute getSiteModelAttr() {
		ArrayList<String> values = new ArrayList<>();
		
		values.add("JukesCantor.model");
		values.add("HKY.model");
		values.add("HKYf.model");
		values.add("GTR.model");
		values.add("BModelTest.model");
		
		values.add("WAG.model");
		values.add("Blosum62.model");
		values.add("MTREV.model");
		values.add("OBAMA.model");
		
		return new Attribute("site.model", values);
	}
	public static Attribute getSiteHetModelAttr() {
		ArrayList<String> values = new ArrayList<>();
		values.add("NoHet.model");
		values.add("GammaSite.model");
		values.add("Invariant.model");
		values.add("GammaInvariant.model");
		return new Attribute("site.heterogeneity.model", values);
	}
	public static Attribute getBranchRateModelAttr() {
		ArrayList<String> values = new ArrayList<>();
		values.add("Strict.model");
		values.add("RelaxedLN.model");
		return new Attribute("branch.model", values);
	}
	public static Attribute getTreeModelAttr() {
		ArrayList<String> values = new ArrayList<>();
		values.add("Yule.model");
		values.add("CoalescentExp.model");
		values.add("Coalescent.model");
		values.add("BirthDeath.model");
		values.add("Skyline.model");
		return new Attribute("tree.model", values);
	}
	public static Attribute getSearchModeAttr() {
		ArrayList<String> values = new ArrayList<>();
		values.add("MCMC.search");
		values.add("MCMCMC.search");
		return new Attribute("search.mode", values);
	}
	
	// partition.model	treeclock.model	site.model	site.heterogeneity.model	branch.model	tree.model	search.mode
	
	
	/** 
	 * Get weight of a poem divided by its dimension attribute
	 * @return
	 */
	public static Attribute getModelValueAttr(ModelValue mv) {
		if (mv.isNumeric()) {
			return new Attribute(mv.getModel()); // Numeric
		}else {
			ArrayList<String> values = new ArrayList<>();
			values.add(mv.getValue());
			return new Attribute(mv.getModel(), values); // Nominal
		}
		
	}
	
	
	/** 
	 * Class value
	 * @return
	 */
	public static Attribute getTargetAttr() {
		return new Attribute("Pmean");
	}
	
	
	/** 
	 * Get weight of a poem divided by its dimension attribute
	 * @return
	 */
	public static Attribute getPoemWeightDimensionAttr(POEM poem) {
		return new Attribute(poem.getWeightColname() + ".d");
	}
	
	/** 
	 * Get weight of a poem divided by its dimension attribute
	 * @return
	 */
	public static Attribute getPoemWeightAttr(POEM poem) {
		return new Attribute(poem.getWeightColname());
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
		
		if (partitions == null || partitions.isEmpty()) return 0;
		
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
		
		int npartitions = 0;
		for (Alignment aln : partitions) {
			
			// Count the number of filtered alignments pointing to it
			boolean hasFilter = false;
			for (BEASTInterface i : aln.getOutputs()) {
				if (i instanceof FilteredAlignment) {
					npartitions++;
					hasFilter = true;
				}
			}
			
			if (!hasFilter) npartitions++;
			
		}
		
		
		return npartitions;
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
	public static double getPoemWeightDimension(POEM poem, int ntaxa) {
		double weight = poem.getWeight();
		double dim = getDimension(poem.getID(), poem.getDim(), ntaxa);
		return weight / dim;
	}
	
	
	/** 
	 * Get weight of a poem divided by its dimension attribute
	 * @return
	 */
	public static double getPoemWeight(POEM poem, int ntaxa) {
		return poem.getWeight();
	}
	
	
	
	
	/**
	 * Incorporate prior knowledge about the poem to adjust for its effective dimension
	 * @param poemID
	 * @param dimension
	 * @return
	 */
	public static double getDimension(String poemID, int dimension, int ntaxa) {
		
		
		switch (poemID) {
		
			
			// Number of topology dimensions is the number of non-root nodes
			case "TopologyPOEM":{
				return 2*ntaxa-2;
			}
			
			// Number of node height dimensions is the number of non-leaf nodes, potentially plus the clock rate too
			case "NodeHeightPOEM":{
				return ntaxa-1;
			}
				
			// Site model POEM is just the number of dimensions
			case "SiteModelPOEM":{
				return dimension;
			}
			
			
			// Tree prior POEM is also the number of dimensions
			case "SpeciesTreePriorPOEM":{
				return dimension;
			}
			
			
			// Branch rates equal to number of nodes minus 1
			case "ClockModelRatePOEM":{
				return 2*ntaxa-2;
			}
			
			
			// 1
			case "ClockModelSDPOEM":{
				return 1;
			}
		
		}
		
		return dimension;
		
	}

	
	
	/** 
	 * Get weight of a poem divided by its dimension attribute
	 * @return
	 */
	public static int getPoemDimension(POEM poem) {
		return poem.getDim();
	}
	
	
	/** 
	 * Get the value of this model value
	 * @return
	 */
	public static String getModelValueNominal(ModelValue mv) {
		return mv.getValue();
	}
	
	/** 
	 * Get the value of this model value
	 * @return
	 */
	public static double getModelValueNumeric(ModelValue mv) {
		return Double.parseDouble(mv.getValue());
	}
	
	
	
	/**
	 * Try to find the tree clock model of the current beast2 session
		 * Fixed.clock.model - the clock rate is not estimated
		 * Estimated.clock.model - the clock rate is estimated but there are no calibrations
		 * Calibrated.clock.model - there are calibrations
		 * Else return missing value (?)
	 * @param tree
	 * @return
	 */
	public static String getTreeClockModel(Tree tree) {
		
		
		boolean clockRateEstimated = false;
		boolean thereAreCalibrations = false;
		
		// Find the likelihood(s) pointing to the tree and get their branchrate models
		List<BranchRateModel.Base> clockModels = new ArrayList<>();
		for (BEASTInterface i : tree.getOutputs()) {
			if (i instanceof GenericTreeLikelihood) {
				BranchRateModel.Base model = ((GenericTreeLikelihood)i).branchRateModelInput.get();
				clockModels.add(model);
			}
		}
		if (clockModels.isEmpty()) return null;
		
		
		// Is the clock rate estimated? 
		
		for (BranchRateModel.Base model : clockModels) {
			RealParameter clockRate = model.meanRateInput.get();
			
			// Check if it is estimated and has at least 1 operator
			if (isStateEstimated(clockRate)) {
				clockRateEstimated = true;
				break;
			}
		}
		
		
		// Are there calibration nodes?
		for (BEASTInterface i : tree.getOutputs()) {
			if (i instanceof MRCAPrior) {
				thereAreCalibrations = true;
				break;
			}
		}
		
		
		
		// If there are calibrations (regardless of the clock rate) then set to calibrated (common use case)
		if (thereAreCalibrations) return "Calibrated.clock.model";
		
		// If no calibrations and the clock model is estimated (rare use case), then set to estimated
		if (clockRateEstimated) return "Estimated.clock.model";
		
		// No calibrations and no clock model estimation (common use case)
		return "Fixed.clock.model";
		
	}
	
	
	
	/**
	 * Try to find the tree prior model
		 * Yule.model
		 * CoalescentExp.model
		 * Coalescent.model
		 * BirthDeath.model
		 * Skyline.model
		 * ? if a different model or there is more than 1 tree prior on the same tree (rare use case)
	 * @param tree
	 * @return
	 */
	public static String getTreeModel(Tree tree) {
		
		
		// Find the tree priors
		List<TreeDistribution> dists = new ArrayList<>();
		for (BEASTInterface i : tree.getOutputs()) {
			if (i instanceof TreeDistribution) {
				TreeDistribution model = ((TreeDistribution)i);
				dists.add(model);
			}
		}
		
		// Multiple priors?
		if (dists.size() != 1) return null;
		TreeDistribution dist = dists.get(0);
		
		
		// Birth death
		if (dist instanceof BirthDeathGernhard08Model) return "BirthDeath.model";
		
		// Yule
		if (dist instanceof YuleModel) return "Yule.model";
		
		// Bayesian skyline
		if (dist instanceof BayesianSkyline) return "Skyline.model";
		
		// Coalescent
		if (dist instanceof Coalescent) {
			
			Coalescent c = (Coalescent) dist;
			
			// Coalescent with exponential growth
			if (c.popSizeInput.get() instanceof ExponentialGrowth)  return "CoalescentExp.model";
			
			// Coalescent with fixed pop size
			if (c.popSizeInput.get() instanceof ConstantPopulation)  return "Coalescent.model";
			
		}
		

		// Unknown tree prior
		return null;
		
	}
	
	
	/**
	 * Try to find the branch rate clock model for the tree
		 * Strict.model - strict clock
		 * RelaxedLN.model - relaxed clock
		 * Otherwise return missing value
	 * @param tree
	 * @return
	 */
	public static String getBranchRateModel(Tree tree) {
		
		
		// Find the likelihood(s) pointing to the tree and get their branchrate models
		List<BranchRateModel.Base> clockModels = new ArrayList<>();
		for (BEASTInterface i : tree.getOutputs()) {
			if (i instanceof GenericTreeLikelihood) {
				BranchRateModel.Base model = ((GenericTreeLikelihood)i).branchRateModelInput.get();
				if (!clockModels.contains(model)) clockModels.add(model);
			}
		}
		if (clockModels.size() != 1) return null;
		BranchRateModel.Base model = clockModels.get(0);
		
		
		// Strict clock
		if (model instanceof StrictClockModel) return "Strict.model";
		
		// Relaxed clock (likely lognormal but will also accept other distributions)
		if (model instanceof UCRelaxedClockModel) return "RelaxedLN.model";
		
		
		return null;
	}
	
	
	
	/**
	 * Try to find the site model for the tree
		 * JukesCantor.model
		 * HKY.model
		 * HKYf.model
		 * GTR.model
		 * BModelTest.model
		 * WAG.model
		 * Blosum62.model
		 * MTREV.model
		 * OBAMA.model
		 * Returns null if a) there is more than 1 type of site model, or b) an unknown site model is used
	 * @param tree
	 * @return
	 */
	public static String getSiteModel(Tree tree) {
		
		// Get the site models from the likelihoods
		List<SiteModelInterface> siteModels = new ArrayList<>();
		List<SubstitutionModel.Base> substModels = new ArrayList<>();
		for (BEASTInterface i : tree.getOutputs()) {
			if (i instanceof GenericTreeLikelihood) {
				SiteModelInterface siteModel = ((GenericTreeLikelihood)i).siteModelInput.get();
				if (siteModel instanceof SiteModelInterface.Base) {
					SubstitutionModel substModel = ((SiteModelInterface.Base)siteModel).substModelInput.get();
					if (substModel instanceof SubstitutionModel.Base) {
						SubstitutionModel.Base model = (SubstitutionModel.Base) substModel;
						if (!substModels.contains(model)) substModels.add(model);
						if (!siteModels.contains(siteModel)) siteModels.add(siteModel);
						
					}
				}
			}
		}
		if (substModels.isEmpty()) return null;
		
		
		// Are they all the same type of model?
		SubstitutionModel.Base model = substModels.get(0);
		for (int i = 2; i < substModels.size(); i ++) {
			SubstitutionModel.Base model2 = substModels.get(i);
			if (!model.getClass().equals(model2.getClass())) return null;
		}
		
		
		// Jukes cantor
		if (model instanceof JukesCantor) return "JukesCantor.model";
		
		// HKY
		if (model instanceof HKY) {
			
			// Frequencies empirical from alignment?
			if (((HKY) model).frequenciesInput.get().dataInput.get() != null) return "HKY.model";
			
			// Or estimated?
			return "HKYf.model";
		}
		
		// GTR
		if (model instanceof GTR) return "GTR.model";
		
		// BModelTest - look at site model instead of substitution model. String comparison to avoid dependencies
		if (siteModels.get(0).getClass().getSimpleName().equals("BEASTModelTestSiteModel")) return "BModelTest.model";
		
		// WAG
		if (model instanceof WAG) return "WAG.model";
		
		// BLOSUM62
		if (model instanceof Blosum62) return "Blosum62";
		
		// mtREV
		if (model instanceof MTREV) return "MTREV.model";
		
		// OBAMA - look at site model instead of substitution model
		if (siteModels.get(0).getClass().getSimpleName().equals("OBAMAModelTestSiteModel")) return "OBAMA.model";
		
		
		return null;
	}
	
	/**
	 * Try to find the site heterogeneity model from the tree
		 * NoHet.model - no site heterogeneity or if BModelTest/OBAMA is the site model
		 * GammaSite.model - gamma heterogeneity only
		 * Invariant.model - proportion invariant is estimated
		 * GammaInvariant.model - gamma and invariant
		 * Return null if unknown or if different site models employ different heterogeneity models
	 * @param tree
	 * @return
	 */
	public static String getSiteHetModel(Tree tree) {
		
		
		// Get the site models from the likelihoods
		List<SiteModel> siteModels = new ArrayList<>();
		for (BEASTInterface i : tree.getOutputs()) {
			if (i instanceof GenericTreeLikelihood) {
				SiteModelInterface siteModel = ((GenericTreeLikelihood)i).siteModelInput.get();
				if (siteModel instanceof SiteModel && !siteModels.contains(siteModel)) siteModels.add((SiteModel)siteModel);
			}
		}
		if (siteModels.isEmpty()) return null;
		
		
		// BModelTest / OBAMA -> return no het
		if (siteModels.get(0).getClass().getSimpleName().equals("BEASTModelTestSiteModel")) return "NoHet.model";
		if (siteModels.get(0).getClass().getSimpleName().equals("OBAMAModelTestSiteModel")) return "NoHet.model";
		
		
		boolean isInvariant = false;
		boolean isGamma = false;
		boolean first = true;
		
		
		// Confirm that all site models employ the same site heterogeneity model
		for (SiteModel model : siteModels) {
			
			// Is the invariant proportion estimated
			RealParameter invar = model.invarParameterInput.get();
			boolean isInvariant_model = isStateEstimated(invar);
			
			// Is the gamma shape estimated and used
			RealParameter shape = model.shapeParameterInput.get();
			boolean isGamma_model = model.getCategoryCount() > 1 && isStateEstimated(shape);
			
			if (first) {
				isInvariant = isInvariant_model;
				isGamma = isGamma_model;
				first = false;
			}else {
				
				// Check for inconsistencies between site models
				if (isInvariant != isInvariant_model) return null;
				if (isGamma != isGamma_model) return null;
				
			}
			
		}
		
		
		// 4 combinations
		if (!isInvariant && !isGamma) return "NoHet.model";
		if (!isInvariant && isGamma) return "GammaSite.model";
		if (isInvariant && !isGamma) return "Invariant.model";
		if (isInvariant && isGamma) return "GammaInvariant.model";
		
		return null;
	}
	
	
	
	/**
	 * Inquires whether the search mode is MCMCMC or not
	 * @param sampler
	 * @return
	 */
	public static String getSearchMode(WeightSampler sampler) {
		if (sampler.isMC3()) return "MCMCMC.search";
		return "MCMC.search";
	}
	
	
	
	/**
	 * Determines whether the state is estimated and also has operators
	 * @param param
	 * @return
	 */
	public static boolean isStateEstimated(StateNode state) {
		
		if (state == null) return false;
		if (!state.isEstimatedInput.get()) return false;
		
		for (BEASTInterface i : state.getOutputs()) {
			if (i instanceof Operator) {
				Operator operator = (Operator)i;
				if (operator.listStateNodes().contains(state)){
					//Log.warning(operator.getID() + " operates on " + state.getID());
					return true;
				}
			}
		}
		
		return false;
		
		
	}
	
	
	


	
}



