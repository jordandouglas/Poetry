package poetry.learning;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

import org.apache.commons.math.MathException;
import org.apache.commons.math.special.Erf;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import beast.core.Description;
import beast.core.Input;
import beast.core.Logger;
import beast.core.StateNode;
import beast.core.util.Log;
import beast.evolution.alignment.Alignment;
import beast.evolution.tree.Tree;
import beast.util.PackageManager;
import poetry.PoetryAnalyser;
import poetry.sampler.POEM;
import poetry.tools.MinESS;
import poetry.util.BEAST2Weka;
import poetry.util.WekaUtils;
import weka.classifiers.functions.GaussianProcesses;
import weka.classifiers.functions.supportVector.PolyKernel;
import weka.classifiers.functions.supportVector.RBFKernel;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffSaver;
import weka.core.converters.ConverterUtils.DataSource;



@Description("Samples weights using Gaussian Processes. Prior distribution must be specified.")
public class GaussianProcessSampler extends WeightSampler {

	
	
	public enum AcquisitionFunction {
		EI, POI, UCB
	}
	
	final public Input<String> poetryFileInput = new Input<>("poetry", "File to store poetry meta-information in", Input.Validate.REQUIRED);
	
	final public Input<WeightSampler> priorInput = new Input<>("prior", "Prior weight sampler");
	final public Input<String> datasetInput = new Input<>("dataset", "Weka arff database to train GP on as prior distribution");
	final public Input<Tree> treeInput = new Input<>("tree", "The phylogenetic tree (used for machine learning)");
	final public Input<Alignment> dataInput = new Input<>("data", "The alignment (used for machine learning)");
	
	final public Input<Double> noiseInput = new Input<>("noise", "The noise to use in Gaussian Processes", 0.8);
	final public Input<Double> explorativityInitInput = new Input<>("explorativity", "Initial explorativity (log space) in Gaussian Processes for iteration 1", 3.0);
	final public Input<Double> explorativityDecayInput = new Input<>("decay", "Decay in explorativity on each iteration", 0.6);
	final public Input<Double> explorativityMinInput = new Input<>("minExplorativity", "Minimum explorativity after decay", 0.02);
	final public Input<Double> minWeightInput = new Input<>("min", "Minimum value that all operator weights must surpass", 1e-4);
	final public Input<Double> priorWeightInput = new Input<>("priorWeight", "The total observational weight of the prior distribution, where each run of this xml file has a weight of 1", 5.0);
	
	final public Input<AcquisitionFunction> acquisitionFunctionInput = new Input<>("acquisition", "The acquisition function for Bayesian optimisation", AcquisitionFunction.EI, AcquisitionFunction.values());
	
	
	
	
	final private static String distClassName = "Pmean";
	final private static String jsonNtrialsName = "ntrials";
	final private static String jsonSamplesName = "samples";
	final private static String jsonNlogName = "nstates";
	final private static String jsonExplorativityName = "epsilon";
	final private static String jsonESSsumName = "ESSsum";
	
	
	AcquisitionFunction acquisition;
	
	boolean resumingPoetry;
	
	WeightSampler prior;
	
	File poetryFile;
	double minWeight;
	double priorWeight;
	double explorativity;
	
	// Poetry json before 
	JSONObject initialPoetry;
	int iterationNum;
	
	
	@Override
	public void initAndValidate() {
		
		
		//List<String> locations = PackageManager.getBeastDirectories();
		
		
		
		// Statistical learning weight for prior
		this.priorWeight = this.priorWeightInput.get();
		if (this.priorWeight <= 0) this.priorWeight = 0;
		
		
		// Prior / database
		this.prior = priorInput.get();
		if (this.prior != null && datasetInput.get() != null) {
			throw new IllegalArgumentException("Please provide either a prior or a dataset but not both");
		}
		if (this.prior == null && (datasetInput.get() == null || this.priorWeight == 0)) {
			this.prior = new DimensionalSampler();
			this.prior.initByName("scale", -1.0);
		}
		
		
		// If there is a database then make sure the tree/alignment are provided
		if (datasetInput.get() != null) {
			if (treeInput.get() == null) throw new IllegalArgumentException("Please specify the tree 'tree' so that the GP can learn from the database");
			if (dataInput.get() == null) throw new IllegalArgumentException("Please specify the alignment 'data' so that the GP can learn from the database");
		}
		
		
		// State file
		this.poetryFile = new File(poetryFileInput.get());
		if (this.poetryFile.exists() && !this.poetryFile.canWrite()) {
			throw new IllegalArgumentException("Error: cannot write to " + this.poetryFile.getPath());
		}
		
		
		// Minimum weight
		this.minWeight = this.minWeightInput.get();
		if (this.minWeight <= 0) this.minWeight = 0;
		

		
		try {
			this.initialPoetry = this.readPoetry();
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Error: encountered problem while parsing " + this.poetryFile.getPath());
		}
		//poetry.get("poetry");
		
		this.acquisition = acquisitionFunctionInput.get();
		
		
		// Resume if there exists a valid state file
		this.iterationNum = 0;
		resumingPoetry = true;
		try {
			if (this.initialPoetry == null ) resumingPoetry = false;
			else if (this.initialPoetry.getJSONArray(jsonSamplesName) == null) resumingPoetry = false;
			else if (this.initialPoetry.getJSONArray(jsonSamplesName).length() == 0) resumingPoetry = false;
			
			if (this.resumingPoetry) {
				this.iterationNum = this.initialPoetry.getInt(jsonNtrialsName);
			}

			
		} catch (JSONException e) {}
		
		if (!this.resumingPoetry) this.initialPoetry = null;
		
		
		
		// Current explorativity
		double initE = this.explorativityInitInput.get();
		double decay = this.explorativityDecayInput.get();
		double finalE = Math.min(initE, this.explorativityMinInput.get());
		if (this.iterationNum > 0) this.explorativity = Math.max((initE-finalE) * Math.pow(decay, this.iterationNum-1)+finalE, finalE);
		else this.explorativity = initE;
		
		
		
	}
	
	
	
	/**
	 * Loads the arff file into weka and creates the class if it does not already exist
	 * @param database
	 * @return
	 * @throws Exception
	 */
	public Instances getInstancesFromDatabase(File database, List<POEM> poems) throws Exception {
		
		
		// Read in file
		if (!database.canRead()) throw new IllegalArgumentException("Cannot open file " + database.getPath());
		Instances instances = new DataSource(database.getAbsolutePath()).getDataSet();

		
		// Apply transforms
		for (int i = 0; i < instances.size(); i ++) {
			Instance instance = instances.get(i);
			
			
			// Ntaxa
			Attribute attr = instances.attribute(BEAST2Weka.getNtaxaAttr().name());
			double val = Math.log(instance.value(attr));
			instance.setValue(attr, val);
			
			// Nsites
			attr = instances.attribute(BEAST2Weka.getNsitesAttr().name());
			val = Math.log(instance.value(attr));
			instance.setValue(attr, val);
			
			
			// Npatterns
			attr = instances.attribute(BEAST2Weka.getNpatternsAttr().name());
			val = Math.log(instance.value(attr));
			instance.setValue(attr, val);
			
			// Npartitions
			attr = instances.attribute(BEAST2Weka.getNpartitionsAttr().name());
			val = Math.log(instance.value(attr));
			instance.setValue(attr, val);
			
			// Tree height
			attr = instances.attribute(BEAST2Weka.getTreeHeightAttr().name());
			val = instance.value(attr);
			instance.setValue(attr, val);
			
			
		}
		
		
		
		// Create class attribute
		if (instances.attribute(distClassName) == null) {
			Attribute classAttr = new Attribute(distClassName);
			instances.insertAttributeAt(classAttr, instances.numAttributes());
		}
		instances.setClass(instances.attribute(distClassName));
		
		return instances;
		
	}
	
	
	@Override
	public void initialise(List<POEM> poems, File database, StateNode placeholder, PoetryAnalyser poetry, boolean isMC3) {
		if (this.prior != null) this.prior.initialise(poems, database, placeholder, poetry, isMC3);
		super.initialise(poems, database, placeholder, poetry, isMC3);
	}
	

	@Override
	public void sampleWeights() throws Exception {
		double[] weights = this.sampleWeights(this.poems);
		this.setWeights(weights);
	}

	
	

	@Override
	public double[] sampleWeights(List<POEM> poems) throws Exception {
		
		double[] weights;
		
		
		Log.warning("Starting iteration " + this.iterationNum);
		Log.warning("Bayesian optimisation explorativity = " + this.explorativity);
		
		// Is this the first round?
		boolean resumeFromPrior = this.prior == null && this.priorWeight > 0;
		if (!this.resumingPoetry && !resumeFromPrior) {
			
			Log.warning("Sampling weights using a " + this.prior.getClass().getCanonicalName() + "...");
			
			// Sample from prior
			weights = this.prior.sampleWeights(poems);
			
		}else {
			
			Log.warning("Resuming weight sampling as a Gaussian Process...");
			
			// Update posterior
			weights = this.getGPWeights();
			
		}
		
		
		// Ensure that all weights are above a minimum
		double weightSum = 0;
		for (int i = 0; i < weights.length; i ++) {
			weights[i] = Math.max(weights[i], this.minWeight);
			weightSum += weights[i];
		}
		for (int i = 0; i < weights.length; i ++) weights[i] /= weightSum;
		
		
		return weights;
		
	}
	
	
	
	
	/**
	 * Read in the poetry file
	 * @return
	 * @throws JSONException 
	 * @throws IOException 
	 */
	protected JSONObject readPoetry() throws JSONException, IOException {
		if (!this.poetryFile.exists()) return null;
		String jsonStr = new String (Files.readAllBytes(Paths.get(this.poetryFile.getCanonicalPath())));
		if (jsonStr.isEmpty()) return null;
		JSONObject json = new JSONObject(jsonStr);
		return json;
	}
	
	
	/**
	 * Store this as a json
	 * @return
	 * @throws Exception 
	 */
	public JSONObject getPoetryJSON() throws Exception {
		
		JSONObject json = new JSONObject();
		
		// How many trials?
		int ntrials = 0; //this.initialPoetry == null ? 1 : this.initialPoetry.getInt(jsonNtrialsName) + 1;
		
		// Create samples array
		JSONArray samples = new JSONArray();
		
		
		
		// Append prior information to array
		if (this.initialPoetry != null) {
			JSONArray samplesInit = (JSONArray) this.initialPoetry.get(jsonSamplesName);
			for (int i = 0; i < samplesInit.length(); i ++) {
				
				// The last session is the current session if resuming
				if (Logger.FILE_MODE == Logger.LogFileMode.resume && i == samplesInit.length()-1) {
					break;
				}
				
				samples.add(samplesInit.get(i));
				ntrials ++;
			}
		}
		
		
		// Add the current state
		JSONObject sampleThis = this.getCurrentStateJSON();
		if (sampleThis != null) {
			samples.add(sampleThis);
			ntrials ++;
		}
		
		
		// Add to json
		json.put(jsonNtrialsName, ntrials);
		json.put(jsonSamplesName, samples);
		
		
		
		return json;
	}
	
	
	
	/**
	 * Current state as a JSON
	 * @return
	 * @throws Exception
	 */
	public JSONObject getCurrentStateJSON()  throws Exception {
		
		JSONObject sampleThis = new JSONObject();

		// Store the weights
		for (POEM poem : this.poems) {
			sampleThis.put(poem.getWeightColname(), poem.getWeight());
		}
		
		// Calculate fractional ESSes
		double ESSsum = 0;
		double[] ESSes = new double[this.poems.size()];
		int meanNLogs = 0;
		MinESS calculator;
		try {
			for (int i = 0; i < this.getNumPoems(); i ++) {
				POEM poem = this.poems.get(i);
				calculator = new MinESS(new File(poem.getLoggerFileName()), "treePrior"); //tmp
				calculator.run();
				
				// Estimates are too volatile early in the chain
				if (calculator.getNLogs() < 30) return null;
				double ess = calculator.getMeanESS();
				
				
				// -Inf becomes 0
				if (Double.isInfinite(ess) && ess < 0) ess = 0;
				
				// NaN or +Inf becomes max value
				if (Double.isNaN(ess)) ess = calculator.getNLogs();
				if (Double.isInfinite(ess) && ess > 0) ess = calculator.getNLogs();
				
				
				ESSes[i] = ess;
				ESSsum += ESSes[i];
				meanNLogs += calculator.getNLogs();
			}
		} catch(Exception e) {
			return null;
		}
		meanNLogs = meanNLogs / ESSes.length;
		
		// Store the fractional ESSes
		for (int i = 0; i < this.getNumPoems(); i ++) {
			POEM poem = this.poems.get(i);
			sampleThis.put(poem.getESSColname(), ESSes[i]/ESSsum);
		}
		
		
		// ESSsum
		sampleThis.put(jsonESSsumName, ESSsum);
		
		
		// N logged states
		sampleThis.put(jsonNlogName, meanNLogs);
		
		
		// Explorativity
		sampleThis.put(jsonExplorativityName, this.explorativity);
		
		// Store the distance
		
		double totalDist = calculateTargetDistanceFunction(ESSes);
		
		
		
		// Min ESS
		/*
		double minPoemESS = 1;
		for (int i = 0; i < this.getNumPoems(); i ++) {
			double ess = ESSes[i]/ESSsum;
			if (ess < minPoemESS) minPoemESS = ess;
		}
		totalDist = minPoemESS; 
		*/
		
		
		sampleThis.put(distClassName, totalDist);
		
		
		
		return sampleThis;
		
	}
	
	
	/**
	 * Calculate Pmean from ESSes
	 * @param esses
	 * @return
	 */
	public static double calculateTargetDistanceFunction(double[] ESSes) {

		double targetFractionalESS = 1.0 / ESSes.length;
		
		// ESS sum
		double ESSsum = 0;
		for (int i = 0; i < ESSes.length; i ++) {
			ESSsum += ESSes[i];
		}
		
		
		// Calculate distance
		double totalDist = 0;
		for (int i = 0; i < ESSes.length; i ++) {
			double dist = Math.pow(ESSes[i]/ESSsum - targetFractionalESS, 2);
			totalDist += dist;
		}
		
		
		
		totalDist = Math.sqrt(totalDist);
		totalDist = -logit(totalDist);
		
		return totalDist;
	}
	
	
	/**
	 * Update poetry state file
	 * @throws IOException 
	 */
	@Override
	public void log() throws Exception {
		
		// Build json
		JSONObject jsonOut = this.getPoetryJSON();
		
		// (Over)write to file
		FileWriter fw = new FileWriter(this.poetryFile);
		fw.write(jsonOut.toString());
		fw.close();
		
	}
	
	
	/**
	 * Load the current beast2 session as an instance
	 * @return
	 */
	public Instances getCurrentSession() {
		
		//Current session
		return BEAST2Weka.getInstance(dataInput.get(), treeInput.get(), this, null);
		
	}
	
	

	/**
	 * Select weights using gaussian process
	 * @return
	 * @throws Exception
	 */
	public double[] getGPWeights() throws Exception {
		
		
		Instances instances = null;
		Instance currentSession = null;
		if (datasetInput.get() != null) {
			instances = this.getCurrentSession();
			
			// Remove final poem weight
			String finalPoemWeightName = this.poems.get(this.getNumPoems()-1).getWeightColname();
			instances.deleteAttributeAt(WekaUtils.getIndexOfColumn(instances, finalPoemWeightName));
			
			currentSession = instances.firstInstance();
			instances.clear();
		} else {
			
			// Create Instances object
			ArrayList<Attribute> attributes = new ArrayList<>();
			for (int i = 0; i < this.getNumPoems()-1; i ++) {
				Attribute tweight = new Attribute("tweight" + i);
				attributes.add(tweight);
			}
			attributes.add(new Attribute(distClassName));
			
			instances = new Instances("dirichlet", attributes,  attributes.size());
			
		}
		instances.setClass(instances.attribute(distClassName));
		int nattr = instances.numAttributes();
		
		double bestMean = Double.NEGATIVE_INFINITY;
		
		
		// Read in json file and add instances
		if (this.initialPoetry != null) {
			JSONArray previousTrials = this.initialPoetry.getJSONArray(jsonSamplesName);
			if (previousTrials != null) {
				for (int i = 0; i < previousTrials.length(); i ++) {
					
					
					JSONObject json = previousTrials.getJSONObject(i);
					
					// One instance per row in database
					Instance instance;
					
					
					// Copy current session over
					if (currentSession != null) {
						instance = new DenseInstance(currentSession);
					}
					
					// Create new one
					else {
						instance = new DenseInstance(nattr);
					}
					instance.setDataset(instances);
					
					
					// One column per weight
					double[] weights = new double[this.poems.size()];
					for (int p = 0; p < this.getNumPoems(); p ++) {
						POEM poem = this.poems.get(p);
						String weightCol = poem.getWeightColname();
						if (json.get(weightCol) == null) {
							throw new Exception("Cannot find column " + weightCol + " in poetry file");
						}
						double weight = json.getDouble(weightCol);
						weights[p] = weight;
						
					}
					
					
					// Set breaks
					double[] tweights = breakSticks(weights);
					for (int p = 0; p < this.getNumPoems()-1; p ++) {
						
						// Current session
						if (currentSession != null) {
							POEM poem = this.poems.get(p);
							instance.setValue(instances.attribute(poem.getWeightColname()), tweights[p]);
						}
						
						// Use new names
						else {
							instance.setValue(instances.attribute("tweight" + p), tweights[p]);
						}
						
					}
					
					
					// Class value (distance) as Pmean
					if (json.get(distClassName) == null) {
						throw new Exception("Cannot find column " + distClassName + " in poetry file");
					}
					double dist = json.getDouble(distClassName);
					//dist = -Math.log(dist);
					instance.setValue(instances.attribute(distClassName), dist);
					instances.add(instance);
					
				}
			}
			
			
			bestMean = getBestDistance(instances);
			
		}
		int nIter = instances.size() + 1;
		
		// Add boundary distances to avoid the GP from assigning weights of 0 or 1
		this.addBoundaryInstances(instances, currentSession);
		

		// Add prior database?
		if (datasetInput.get() != null && this.priorWeight > 0) {
			
			
			// Load the database
			Instances priorDatabase = this.getInstancesFromDatabase(new File(datasetInput.get()), poems);

			int ninstances = 2000; //priorDatabase.size();
			
			
			
			

			
			// Stick breaking on database 
			for (int i = 0; i < ninstances; i++) {
				
				Instance inst = priorDatabase.get(i);
				
				// Get the poems weights and ESSes
				double[] poemWeights = new double[poems.size()];
				double[] poemESSes = new double[poems.size()];
				double weightSum = 0;
				double ESSsum = 0;
				for (int poemNum = 0; poemNum < poems.size(); poemNum++) {
					
					POEM poem = poems.get(poemNum);
					Attribute weightAttr = priorDatabase.attribute(poem.getWeightColname());
					Attribute essAttr = priorDatabase.attribute(poem.getESSColname());
					if (essAttr == null) essAttr = priorDatabase.attribute(poem.getESSColname() + ".p");
					
					if (weightAttr == null || essAttr == null) {
						if (i == 0) Log.warning("Warning: cannot locate '" + poem.getWeightColname() + "' and '" + poem.getESSColname() + "' in database");
						continue;
					}
					
					
					// To prevent numerical issues
					double weight = inst.value(weightAttr);
					double ess = inst.value(essAttr);
					if (weight <= 0) {
						weight = 1e-8;
						ess = 1e-8;
					}
					
					// Store weights and ESSes
					poemWeights[poemNum] = weight;
					poemESSes[poemNum] = ess;
					weightSum += poemWeights[poemNum];
					ESSsum += poemESSes[poemNum];
					
				}
				
				
				// Ensure weights/esses sum to 1 
				for (int poemNum = 0; poemNum < poems.size(); poemNum++) {
					poemWeights[poemNum] /= weightSum;
					poemESSes[poemNum] /= ESSsum;
				}
				
				
				// Break weight sticks and set attribute values
				double[] breaks = breakSticks(poemWeights);
				for (int poemNum = 0; poemNum < poems.size()-1; poemNum++) {
					POEM poem = poems.get(poemNum);
					Attribute weightAttr = priorDatabase.attribute(poem.getWeightColname());
					inst.setValue(weightAttr, breaks[poemNum]);
				}
				
				
				// Calculate class value
				double pmean = calculateTargetDistanceFunction(poemESSes);
				inst.setValue(priorDatabase.attribute(distClassName), pmean);
				
				
			}
			

			
			// Add the instances to the main list of instances, but with a smaller learning weight
			Log.warning("Adding " + ninstances + " prior instances to GP model with a total weight of " + this.priorWeight);
			double weightPerInst = this.priorWeight / ninstances;
			for (int i = 0; i < ninstances; i++) {
				Instance priorInst = priorDatabase.get(i);
				Instance copy = new DenseInstance(currentSession);
				
				// Copy all values over
				for (int a = 0; a < copy.numAttributes(); a++) {
					
					Attribute attr = instances.attribute(a);
					
					int priorAttrIndex = WekaUtils.getIndexOfColumn(priorInst, attr.name());
					
					// Missing
					if (priorAttrIndex == -1) {
						copy.setMissing(attr);
					}
					
					// Numeric
					else if (attr.isNumeric()) {
						double value = priorInst.value(priorAttrIndex);
						copy.setValue(attr, value);
					}
					
					// Nominal
					else if (attr.isNominal()) {
						String value = priorInst.toString(priorDatabase.attribute(priorAttrIndex));
						copy.setValue(attr, value);
						
					}
					
					
				}
				
				
				copy.setWeight(weightPerInst);
				copy.setDataset(instances);
				
				// Negative class
				//double classVal = -priorInst.classValue(); 
				//priorInst.setClassValue(classVal);
				
				instances.add(copy);
				
			}
			
			
			if (bestMean == Double.NEGATIVE_INFINITY) bestMean = getBestDistance(instances);
			
		
				
		
		
		}
		
		
		/*
		// Save the dataset
		ArffSaver saver = new ArffSaver();
		saver.setInstances(instances);
		saver.setFile(new File("/home/jdou557/Documents/Marsden2019/Months/January2021/kernel30.arff"));
		saver.writeBatch();
		*/
		
		
		// Train the kernel
		// No normalisation or standardisation. RBFKernel. Noise
		GaussianProcesses kernel  = new GaussianProcesses();
		kernel.setOptions(new String[] { "-N", "2", "-K", RBFKernel.class.getCanonicalName(), "-L", "" + this.noiseInput.get() });
		kernel.buildClassifier(instances);
		
		
		//Evaluation evaluation = new Evaluation(instances);
		//evaluation.crossValidateModel(kernel, instances, 10, new Random(1));
		//System.out.println(evaluation.toSummaryString());
		

		
		// Optimise the expected improvement function (minimisation)
		MultivariateFunction fn = null;
		switch (this.acquisition) {
			case EI:{
				fn = new ExpectedImprovementFunction(instances, currentSession, kernel,  this.poems, bestMean, this.explorativity, this.minWeight);
				Log.warning("Computing maximum expected improvement...");
				break;
			}
			
			case POI:{
				fn = new ProbabilityOfImprovement(instances, currentSession, kernel, this.poems, bestMean, this.explorativity, this.minWeight);
				Log.warning("Computing maximum probability of improvement...");
				break;
			}
			
			case UCB:{
				fn = new UCBFunction(instances, currentSession, kernel, this.poems, bestMean, this.explorativity, this.minWeight, nIter);
				Log.warning("Computing upper confidence bound...");
				break;
			}
			
			
		
		}
		
		
		double[] opt2 = new double[] { -0.6, -0.6, -0.6 }; 
		double[] weights2 = repairSticks(opt2);
		System.out.print(this.acquisition.toString() + " random: ");
		for (double o : weights2) System.out.print(o + ", ");
		System.out.println(" eval: " + fn.value(opt2));

		
		// Optimise acquisition function to get next iteration's weights
		double[] opt = optimiseSimplex(fn, this.poems.size(), true);
		double[] weights = repairSticks(opt);
		System.out.print(this.acquisition.toString() + " max: ");
		for (double o : weights) System.out.print(o + ", ");
		System.out.println(" eval: " + fn.value(opt));
		
		
		return weights;
		
		
	}
	
	
	

	/**
	 * Add one (0,...,0,1,0,...,0) instance for each boundary and set its target function to negative infinity
	 * @param instances
	 */
	private void addBoundaryInstances(Instances instances, Instance currentSession) {
		
		
		
		// Do not actually assign weights of 0 or 1 or the stick breaking will give -Inf 
		final double wall = this.minWeight == 0 ? 1e-4 : this.minWeight;
		double maxPMean = Math.pow(1 - 1.0/this.getNumPoems(), 2) + Math.pow(1.0/this.getNumPoems(), 2)*(1-this.getNumPoems());
		maxPMean = Math.sqrt(maxPMean);
		final double minVal = -logit(maxPMean);
		
		for (int poemNum = 0; poemNum < this.getNumPoems(); poemNum ++) {
			
			
			// One boundary instance per poem
			Instance boundary = currentSession == null ? new DenseInstance(instances.numAttributes()) : new DenseInstance(currentSession);
			boundary.setDataset(instances);
			
			
			
			// One column per weight
			double[] weights = new double[this.poems.size()];
			for (int p = 0; p < this.getNumPoems(); p ++) {
				double weight = p == poemNum ? 1 - wall*(this.poems.size()-1) : wall;
				weights[p] = weight;
			}
			
			
			// Stick breaking
			double[] tweights = breakSticks(weights);
			if (poemNum < this.getNumPoems()-1) {
				for (int p = 0; p < this.getNumPoems()-1; p ++) {
					POEM poem = this.poems.get(p);
					Attribute attr = instances.attribute(poem.getWeightColname());
					boundary.setValue(attr, tweights[p]);
					//if (p == poemNum) boundary.setValue(attr, tweights[p]);
					//else boundary.setMissing(attr);
				}
				POEM poem = this.poems.get(poemNum);
				boundary.setValue(instances.attribute(poem.getWeightColname()), tweights[poemNum]);
			}else {
				for (int p = 0; p < this.getNumPoems()-1; p ++) {
					POEM poem = this.poems.get(p);
					Attribute attr = instances.attribute(poem.getWeightColname());
					boundary.setValue(attr, tweights[p]);
				}
			}
			
			
			// Minimal class value
			boundary.setClassValue(minVal);
			//boundary.setWeight(1.0 / this.getNumPoems());
			
			instances.add(boundary);
			
			
			
			// A second boundary instance per poem where its weight is 0 and everything else is missing
			boundary = currentSession == null ? new DenseInstance(instances.numAttributes()) : new DenseInstance(currentSession);
			boundary.setDataset(instances);
			if (poemNum < this.getNumPoems()-1) {
				for (int p = 0; p < this.getNumPoems()-1; p ++) {
					POEM poem = this.poems.get(p);
					Attribute attr = instances.attribute(poem.getWeightColname());
					if (p == poemNum) boundary.setValue(attr, -5);
					else boundary.setMissing(attr);
				}
				POEM poem = this.poems.get(poemNum);
				boundary.setValue(instances.attribute(poem.getWeightColname()), tweights[poemNum]);
			}
			boundary.setClassValue(minVal);
			instances.add(boundary);
		}
		
	}
	
	

	/**
	 * Return the class value of the instance with the largest class value (ie. distance)
	 * @param instances
	 * @return
	 */
	private double getBestDistance(Instances instances) {
		
		

		// What is the largest observed mean so far?
		double bestMean = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < instances.size(); i ++) {
			//Log.warning(instances.get(i).toString());
			double val = instances.get(i).classValue(); // instances.get(i).value(instances.attribute(distClassName));
			if (val > bestMean) bestMean = val;
		}
		Log.warning("Cumulative optimal value " + bestMean);
		
		return bestMean;
		
		
	}
	
	
	/**
	 * Normal density (log probability)
	 * @param x
	 * @param mean
	 * @param sd
	 * @return
	 */
	private double logP(double x, double mean, double sd) {
		
		double a = -Math.log(sd) + Math.log(2*Math.PI)/2;
		double b = -1/2 * (x-mean)*(x-mean) / sd;
		return a + b;
	}
	
	
	/**
	 * Normal cdf (log probability)
	 * @param x
	 * @param mean
	 * @param sd
	 * @return
	 */
	private double logCumulativeP(double x, double mean, double sd) {
		
		double q = (x-mean)/(sd*Math.sqrt(2));
		double a = Math.log(0.5);
		double b = 0;
		try {
			b = Math.log(0.5) + Math.log(Erf.erf(q));
		} catch (MathException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return a + b;
	}
	
	
	/**
	 * Get the probability of improvement
	 *
	 */
	private class ProbabilityOfImprovement implements MultivariateFunction {

		
		 double epsilon;
		 GaussianProcesses gp;
		 NormalDistribution normal;
		 double bestMean;
		 Instances instances;
		 Instance instance;
		 double minWeight;
		 List<POEM> poems;
		
		public ProbabilityOfImprovement(Instances instances, Instance currentSession, GaussianProcesses gp, List<POEM> poems, double bestMean, double epsilon, double minWeight) {
			 this.epsilon = epsilon;
			 this.instance = currentSession == null ? new DenseInstance(instances.numAttributes()) : new DenseInstance(currentSession);
			 this.instances = instances;
			 this.bestMean = bestMean;
			 instance.setDataset(instances);
			 this.gp = gp;
			 this.minWeight = minWeight;
			 this.poems = poems;
		 }
		
		
		@Override
		public double value(double[] tweights) {
			
			// Confirm that all weights are above the minimum
			 double[] weights = repairSticks(tweights);
			 for (int j = 0; j < weights.length; j ++) {
				 if (weights[j] < this.minWeight) return 0;
			 }
			 
			 
			// Set the transformed weight (ie. the broken stick)
			for (int j = 0; j < tweights.length; j ++) {
				POEM poem = this.poems.get(j);
				//Log.warning("" + tweights[j]);
				instance.setValue(instances.attribute(poem.getWeightColname()), tweights[j]);
			}
			
			// Get mean and standard deviation (in log space)
			double sd, mean;
			try {
				sd = gp.getStandardDeviation(instance);
				mean = gp.classifyInstance(instance);
			} catch (Exception e1) {
				e1.printStackTrace();
				return Double.NEGATIVE_INFINITY;
			}
			
			double delta = mean - (bestMean + epsilon);
			//normal = new NormalDistribution(mean, sd);
			
			// Want to maximise
			double x = delta/sd;
			//double poi = normal.cumulativeProbability(x);
			double poi = logCumulativeP(x, mean, sd);
			
			
			
			//Log.warning("Evaluating " + instance.toString() + " -> " + mean + "," + sd + "," + delta + "," + poi);
			
			
			return poi;
			
		}
		
		
		
		
	}
	
	
	
	/**
	 * Get the expected improvement of the weights (ie. expected value above the bestMean)
	 *
	 */
	 private class ExpectedImprovementFunction implements MultivariateFunction {
		 
		 double epsilon;
		 GaussianProcesses gp;
		 NormalDistribution normal;
		 double bestMean;
		 Instances instances;
		 Instance instance;
		 double minWeight;
		 List<POEM> poems;
		 
		 public ExpectedImprovementFunction(Instances instances, Instance currentSession, GaussianProcesses gp, List<POEM> poems, double bestMean, double epsilon, double minWeight) {
			 this.epsilon = epsilon;
			 this.instance = currentSession == null ? new DenseInstance(instances.numAttributes()) : new DenseInstance(currentSession);
			 this.instances = instances;
			 this.bestMean = bestMean;
			 instance.setDataset(instances);
			 this.gp = gp;
			 this.minWeight = minWeight;
			 this.poems = poems;
		 }
		 

		 
		 @Override
		 public double value(double[] tweights) {
			 
			 // Confirm that all weights are above the minimum
			 double[] weights = repairSticks(tweights);
			 for (int j = 0; j < weights.length; j ++) {
				 if (weights[j] < this.minWeight) return 0;
			 }
			 
			 
			// Set the transformed weight (ie. the broken stick)
			for (int j = 0; j < tweights.length; j ++) {
				POEM poem = this.poems.get(j);
				instance.setValue(instances.attribute(poem.getWeightColname()), tweights[j]);
			}
			
			
			//instance = instances.firstInstance();
			
			
			// Get mean and standard deviation (in log space)
			double sd, mean;
			try {
				sd = gp.getStandardDeviation(instance); // + gp.getNoise();
				mean = gp.classifyInstance(instance);
			} catch (Exception e1) {
				e1.printStackTrace();
				return Double.NEGATIVE_INFINITY;
			}
			
			if (sd == 0) return 0;
			
			double delta = mean - (bestMean + epsilon);
			//delta = -delta; // Minimise
			normal = new NormalDistribution(mean, sd);
			
			 
			// Expected improvement
			double e = delta*normal.cumulativeProbability(delta / sd) + sd*normal.density(delta / sd);
			
			//Log.warning("Evaluating " + instance.toString() + " -> " + mean + "," + sd + "," + delta + ","
			//+ normal.cumulativeProbability(delta / sd) + ", " + normal.density(delta / sd));
			
			/*
			// Dirichlet prior
			double alpha = 10;
	        double logP = 0;
	        double sumAlpha = 0;
	        for (int i = 0; i < weights.length; i++) {
	            double w = weights[i];
	            logP += (alpha - 1) * Math.log(w);
	            logP -= org.apache.commons.math.special.Gamma.logGamma(alpha);
	            sumAlpha += alpha;
	        }
	        logP += org.apache.commons.math.special.Gamma.logGamma(sumAlpha);
			*/
	        
	       // Log.warning(e + ", " + logP);
	        //Log.warning("EI: " + e + ", " + mean + ", " + sd );
	        
	        /*
	        try {
				Thread.sleep(500);
			} catch (InterruptedException es) {
				// TODO Auto-generated catch block
				es.printStackTrace();
			}
			*/
	        
			
			// Want to maximise
			return e;// + logP;
			
		 }
		 
		 
	 }


	 
	 

		/**
		 * Upper confidence bound
		 *
		 */
		 private class UCBFunction implements MultivariateFunction {
			 
			 double epsilon;
			 GaussianProcesses gp;
			 NormalDistribution normal;
			 double bestMean;
			 Instances instances;
			 Instance instance;
			 double minWeight;
			 List<POEM> poems;
			 int nIter;
			 double alpha = 1.1;
			 
			 
			 public UCBFunction(Instances instances, Instance currentSession, GaussianProcesses gp, List<POEM> poems, double bestMean, double epsilon, double minWeight, int nIter) {
				 this.epsilon = epsilon;
				 this.instance = currentSession == null ? new DenseInstance(instances.numAttributes()) : new DenseInstance(currentSession);
				 this.instances = instances;
				 this.bestMean = bestMean;
				 instance.setDataset(instances);
				 this.gp = gp;
				 this.minWeight = minWeight;
				 this.poems = poems;
				 this.nIter = nIter;
			 }
			 

			 
			 @Override
			 public double value(double[] tweights) {
				 
				 // Confirm that all weights are above the minimum
				 double[] weights = repairSticks(tweights);
				 for (int j = 0; j < weights.length; j ++) {
					 if (weights[j] < this.minWeight) return 0;
				 }
				 
				 
				// Set the transformed weight (ie. the broken stick)
				for (int j = 0; j < tweights.length; j ++) {
					POEM poem = this.poems.get(j);
					Log.warning("" + tweights[j]);
					instance.setValue(instances.attribute(poem.getWeightColname()), tweights[j]);
				}
				
				
				//instance = instances.firstInstance();
				
				
				// Get mean and standard deviation (in log space)
				double sd, mean;
				try {
					sd = gp.getStandardDeviation(instance); // + gp.getNoise();
					mean = gp.classifyInstance(instance);
				} catch (Exception e1) {
					e1.printStackTrace();
					return Double.NEGATIVE_INFINITY;
				}
				
				if (sd == 0) return 0;
				
				 
				// UCB
				//int D = tweights.length;
				//double t = Math.pow(this.nIter, 1.0*D + 2) * Math.PI * Math.PI;
				//double kappa = Math.sqrt(2 * Math.log(t / (3*epsilon)));
				
				
				double kappa = epsilon;
				double u = mean + kappa*sd;
				
				//Log.warning("Evaluating " + instance.toString() + " -> " + mean + "," + sd + "," + u);
				
				
				
				// Dirichlet prior
		        double logP = 0;
		        double sumAlpha = 0;
		        for (int i = 0; i < weights.length; i++) {
		            double w = weights[i];
		            logP += (alpha - 1) * Math.log(w);
		            logP -= org.apache.commons.math.special.Gamma.logGamma(alpha);
		            sumAlpha += alpha;
		        }
		        logP += org.apache.commons.math.special.Gamma.logGamma(sumAlpha);
				
				//Log.warning("UCB: " + u + ", " + mean + ", " + sd + ", " + kappa);
				
				/*
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				*/
				
				// Want to maximise
				return Math.log(u);// + logP;
				
			 }
			 
			 
		 }


	

	
	
	
}
