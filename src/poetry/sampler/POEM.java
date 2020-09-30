package poetry.sampler;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import beast.core.BEASTObject;
import beast.core.Function;
import beast.core.Input;
import beast.core.Logger;
import beast.core.Operator;
import beast.evolution.alignment.Alignment;
import beast.evolution.alignment.FilteredAlignment;
import beast.math.distributions.Dirichlet;
import beast.util.Randomizer;
import poetry.functions.XMLFunction;
import poetry.operators.MetaOperator;
import poetry.util.XMLUtils;


/**
 * A Mapping between Parameters, Operators, and ESSes (a POEM)
 * @author jdou557
 *
 */
public class POEM extends BEASTObject implements XMLSampler {

	
	final public Input<MetaOperator> operatorInput = new Input<>("operator", "An operator");
	final public Input<List<Function>> logInput = new Input<>("log", "A parameter/logger to report", new ArrayList<>());
	final public Input<Double> alphaInput = new Input<>("alpha", "The Dirichlet alpha term for the prior probability of this operator", 1.0);
	final public Input<Integer> logEveryInput = new Input<>("logEvery", "How often to log", Input.Validate.REQUIRED);
	
	
	final public Input<String> operatorIDInput = new Input<>("operatorID", "The id of an operator. Use this when the operator is in a different xml file");
	final public Input<List<String>> logIDInput = new Input<>("logID", "The id of a function to log. Use this when the function is in a different xml file", new ArrayList<>());
	
	
	boolean useRealObjects;
	double weight;
	String operatorID;
	double minESS;
	double alpha;
	
	@Override
	public void initAndValidate() {
		
		this.useRealObjects = operatorInput.get() == null;
		if (this.useRealObjects) {
			if (this.operatorIDInput.get() == null || this.operatorIDInput.get().isEmpty()) {
				throw new IllegalArgumentException("Please provide either an operator or an operator ID");
			}
		}else {
			this.operatorID = this.operatorInput.get().getID();
		}
		
		this.weight = 1;
		this.minESS = 0;
		this.alpha = alphaInput.get();
	}
	
	
	/**
	 * Return the id's of all objects which are logged
	 * @return
	 */
	public List<String> getLogIDs(){
		
		List<String> ids = new ArrayList<String>();
		if (this.useRealObjects){
			for (String id: this.logIDInput.get()) {
				ids.add(id);
			}
		}else {
			for (Function fun : this.logInput.get()) {
				ids.add(((BEASTObject)fun).getID());
			}
		}
		
		
		return ids;
	}
	
	
	/**
	 * Column name of this poem's minimum ess in the database file
	 * @return
	 */
	public String getESSColname() {
		return this.getID() + ".min.ESS.M";
	}
	
	/**
	 * Column name of this poem's oeprator weight in the database file
	 * @return
	 */
	public String getWeightColname() {
		return this.getID() + ".weight";
	}
	
	
	/**
	 * The log file name where all terms in this poem are printed to
	 * @return
	 */
	public String getLoggerFileName() {
		return this.getID() + ".log";
	}
	
	
	/**
	 * Set the weight of this operator
	 * @param weight
	 */
	public void setWeight(double weight) {
		this.weight = weight;
	}
	
	
	/**
	 * Get the operator weight
	 * @return
	 */
	public double getWeight() {
		return this.weight;
	}
	
	
	/**
	 * Get the Dirichlet alpha of this POEM
	 * Used for sampling a weight
	 * @return
	 */
	public double getAlpha() {
		
		double a = this.alpha;
		//if (timesNInput.get()) a = a * aln.getTaxonCount();
		//if (timesPInput.get() && aln instanceof DatasetSampler) {
			//a = a * ((DatasetSampler) aln).getNumPartitions();
		//}
		return a;
	}
	
	
	/**
	 * Returns alpha, but if there are no sub-operators in the operator then returns zero
	 * @param doc
	 * @return
	 * @throws Exception
	 */
	private double getAlphaConditionalOnModel(Document doc) throws Exception {
		
		Element operator = this.getOperatorEle(doc);
		this.operatorID =  operator.getAttribute("id");
		List<Element> subOperators = XMLUtils.getElementsByName(operator, "operator");
		if (subOperators.isEmpty()) {
			return 0;
		}else {
			return this.getAlpha();
		}
		
	}
	
	
	
	/**
	 * Sample weights using a dirichlet distribution
	 * @param poems - list of poems
	 * @return
	 * @throws Exception 
	 */
	public static double[] sampleWeights(List<POEM> poems, Document doc) throws Exception {
		
		
		int dim = poems.size();
		double[] weights = new double[dim];
		
		
		// Sample a dirichlet
		double sum = 0.0;
		for (int j = 0; j < dim; j++) {
			POEM poem = poems.get(j);
			double a = poem.getAlphaConditionalOnModel(doc);
			if (a == 0) {
				weights[j] = a;
			}else {
				weights[j] = Randomizer.nextGamma(a, 1.0);
			}
			sum += weights[j];
		}
		for (int j = 0; j < dim; j++) {
			weights[j] = weights[j] / sum;
		}

		
		return weights;
		
	}


	@Override
	public void reset() {
		this.weight = 1;
	}

	
	
	/**
	 * Find the operator element in an xml document
	 * @param doc
	 * @return
	 * @throws Exception
	 */
	protected Element getOperatorEle(Document doc) throws Exception {
		

		// Get the operator ID of this POEM (may have been added to the doc)
		Element thisEle = XMLUtils.getElementById(doc, this.getID());
		if (thisEle == null) throw new Exception("Error: cannot locate " + this.getID());
		List<Element> children = XMLUtils.getElementsByName(thisEle, "operator");
		String operatorID;
		if (thisEle.hasAttribute("operator")) {
			operatorID = thisEle.getAttribute("operator");
			operatorID = operatorID.replace("@", "");
		}else if (!children.isEmpty()) {
			operatorID = children.get(0).getAttribute("idref");
		}else {
			throw new Exception("Error: " + this.getID() + " does not have an operator");
		}
		Element operator = XMLUtils.getElementById(doc, operatorID);
		
		if (operator == null) {
			throw new IllegalArgumentException("Error: cannot locate operator " + operatorID);
		}
		
		return operator;
		
		
		
	}
	
	

	@Override
	public void tidyXML(Document doc, Element runnable, List<XMLFunction> functions) throws Exception {
		
		
		Element thisEle = XMLUtils.getElementById(doc, this.getID());
		if (thisEle == null) throw new Exception("Error: cannot locate " + this.getID());
		

		// Set the weight of that operator in the XML to the appropriate weight, or remove if the operator has no target
		Element operator = this.getOperatorEle(doc);
		this.operatorID =  operator.getAttribute("id");
		if (this.getWeight() == 0) {
			operator.getParentNode().removeChild(operator);
			return;
		}else {
			operator.setAttribute("weight", "" + this.getWeight());
		}
		
		
		
		// Create a logger if applicable
		Element logger = doc.createElement("logger");
		logger.setAttribute("id", this.getID() + "Logger");
		logger.setAttribute("fileName", this.getLoggerFileName());
		logger.setAttribute("logEvery", "" + this.logEveryInput.get());
		runnable.appendChild(logger);
		
		// Add all this elements loggables into the logger
		List<Element> logs = XMLUtils.getElementsByName(thisEle, "log");
		for (Element log : logs) {
			logger.appendChild(log.cloneNode(true));
		}
		
		/*
		Element operator = this.getOperatorEle(doc);
		this.operatorID =  operator.getAttribute("id");
		List<Element> subOperators = XMLUtils.getElementsByName(operator, "operator");
		if (subOperators.isEmpty()) {
			operator.getParentNode().removeChild(operator);
			//this.setWeight(0);
		}else {
			operator.setAttribute("weight", "" + this.getWeight());
		}
		*/
		
		
		/*
		
		// Ensure that all logged terms in this operator are also part of the logger
		Element logger = XMLUtils.getElementById(doc, this.logger.getID());
		
		for (Element log : logs) {
			
			// Is this thing being logged?
			boolean foundMatch = false;
			if (log.hasAttribute("id")){
				if (XMLUtils.getElementById(logger, log.getAttribute("idref")) != null) {
					foundMatch = true;
				}
			}
			
			
			else if (log.hasAttribute("idref")) {
				if (XMLUtils.getElementById(logger, log.getAttribute("idref")) != null) {
					foundMatch = true;
				}
				else if (XMLUtils.getElementByAttrValue(logger, "idref", log.getAttribute("idref")).size() > 0) {
					foundMatch = true;
				}
			}
			
			
			// Add it to the log
			if (!foundMatch) {
				logger.appendChild(log.cloneNode(true));
			}
			
			
		}
		
		*/
		
		
	}


	@Override
	public String getComments() {
		return "The operator weight (prob) for " + this.getID() + " is " + this.getWeight();
	}


	public String getOperatorID() {
		return this.operatorID;
	}


	@Override
	public String getSampledID() {
		return "NA";
	}

	/**
	 * Set the minimum ESS
	 * @param minESS
	 */
	public void setMinESS(double minESS) {
		this.minESS = minESS;
	}
	
	
	/**
	 * Get the minimum ESS
	 * @return
	 */
	public double getMinESS() {
		return this.minESS;
	}


	/**
	 * Name of c.o.v. column
	 * @return
	 */
	public static String getCoefficientOfVariationColumnName() {
		return "ESS.hr.cov";
	}
	
	/**
	 * Name of mean ESS column
	 * @return
	 */
	public static String getMeanColumnName() {
		return "ESS.hr.mean";
	}


	/**
	 * Name of standard deviation column
	 * @return
	 */
	public static String getStddevColumnName() {
		return "ESS.hr.sd";
	}
	
	
	/**
	 * Compute the mean, sd, and coefficient of variation of the minimum ESSes
	 * @param poems
	 * @return a double[] containing [mean, sd, cov]
	 */
	public static double[] getESSStats (List<POEM> poems) {
		
		
		// Calculate mean (but exclude non infinities)
		int numNonInf = 0;
		double meanESS = 0;
		for (POEM poem : poems) {
			double ESS = poem.getMinESS();
			if (ESS != Double.POSITIVE_INFINITY) {
				meanESS += ESS;
				numNonInf ++;
			}
		}
		meanESS /= numNonInf;
		
		
		// Calculate SD
		double sdESS = 0;
		for (POEM poem : poems) {
			double ESS = poem.getMinESS();
			if (ESS != Double.POSITIVE_INFINITY) {
				sdESS += Math.sqrt(Math.pow(ESS - meanESS, 2)) / numNonInf;
			}
		}
		
		
		// Mean, sd, coefficient of variation
		return new double[] {meanESS, sdESS, sdESS / meanESS};
	}



	
	
	
	

}
