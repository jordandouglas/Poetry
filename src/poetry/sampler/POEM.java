package poetry.sampler;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import beast.core.BEASTObject;
import beast.core.Description;
import beast.core.Function;
import beast.core.Input;
import beast.core.StateNode;
import beast.util.Randomizer;
import poetry.functions.XMLFunction;
import poetry.operators.MetaOperator;
import poetry.util.ESSDerivative;
import poetry.util.XMLUtils;


/**
 * A Mapping between Parameters, Operators, and ESSes (a POEM)
 * @author jdou557
 *
 */
@Description("A Mapping between Parameters, Operators, and ESSes (a POEM)")
public class POEM extends BEASTObject implements XMLSampler {

	
	final public Input<MetaOperator> operatorInput = new Input<>("operator", "An operator");
	final public Input<List<Function>> logInput = new Input<>("log", "A parameter/logger to report", new ArrayList<>());
	final public Input<Double> alphaInput = new Input<>("alpha", "The Dirichlet alpha term for the prior probability of this operator", 1.0);
	final public Input<Integer> logEveryInput = new Input<>("logEvery", "How often to log", Input.Validate.REQUIRED);
	
	
	
	
	double weight;
	MetaOperator operator;
	String operatorID;
	double minESS;
	double alpha;
	boolean applicableToModel;
	
	@Override
	public void initAndValidate() {
		this.weight = 1;
		this.minESS = 0;
		this.alpha = alphaInput.get();
		this.operator = operatorInput.get();
		this.applicableToModel = true;
	}
	
	
	
	
	
	/**
	 * Return the id's of all objects which are logged
	 * @return
	 */
	public List<String> getLogIDs(){
		
		List<String> ids = new ArrayList<String>();
		for (Function fun : this.logInput.get()) {
			ids.add(((BEASTObject)fun).getID());
		}
		return ids;
	}
	
	
	/**
	 * Column name of this poem's minimum ess in the database file
	 * @return
	 */
	public String getESSColname() {
		return this.getID() + ".min.ESS";
	}
	
	/**
	 * Column name of this poem's oeprator weight in the database file
	 * @return
	 */
	public String getWeightColname() {
		return this.getID() + ".weight";
	}
	
	
	/**
	 * Dimension column name
	 * @return
	 */
	public String getDimColName() {
		return this.getID() + ".dim";
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
	 * Is this POEM applicable to the current model?
	 * @return
	 */
	public boolean isApplicableToModel() {
		return this.applicableToModel;
	}
	
	
	/**
	 * Get the Dirichlet alpha of this POEM
	 * Used for sampling a weight
	 * @return
	 */
	public double getAlpha() {
		double a = this.alpha;
		return a;
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
		this.operatorID = operator.getAttribute("id");
		List<Element> subOperators = XMLUtils.getElementsByName(operator, "operator");
		if (subOperators.isEmpty()) {
			operator.getParentNode().removeChild(operator);
			operator.setAttribute("weight", "0");
			this.applicableToModel = false;
			return;
		}
		this.applicableToModel = true;
		
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
			
			// ESS derivative logger
			if (log.hasAttribute("idref")) {
				Element dESS = doc.createElement("log");
				dESS.setAttribute("arg", "@" + log.getAttribute("idref"));
				dESS.setAttribute("spec", ESSDerivative.class.getCanonicalName());
				logger.appendChild(dESS);
			}
			
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
		if (this.applicableToModel) return "The operator weight (prob) for " + this.getID() + " will be sampled using a Dirichlet with alpha " + this.getAlpha();
		return this.getID() + " is not applicable to this model";
	}


	public String getOperatorID() {
		if (this.operator != null) return this.operator.getID();
		return this.operatorID;
	}

	
	public MetaOperator getOperator() {
		return this.operator;
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
		return "ESS.cov";
	}
	
	/**
	 * Name of mean ESS column
	 * @return
	 */
	public static String getMeanColumnName() {
		return "ESS.mean";
	}


	/**
	 * Name of standard deviation column
	 * @return
	 */
	public static String getStddevColumnName() {
		return "ESS.sd";
	}
	
	
	/**
	 * Number of states
	 * @return
	 */
	public static String getNumberOfStatesColumnName() {
		return "nstates";
	}
	
	
	/**
	 * Runtime after being smoothed
	 * @return
	 */
	public static String getRuntimeSmoothColumn() {
		return "runtime.smooth.hr";
	}
	
	
	/**
	 * Actual runtime
	 * @return
	 */
	public static String getRuntimeRawColumn() {
		return "runtime.raw.hr";
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



	/**
	 * Get the dimension of this POEM by counting the dimension of its operator's state nodes
	 * @return
	 */
	public int getDim() {
		
		if (this.operator == null) return 0;
		
		int ndim = 0;
		for (StateNode stateNode : this.operator.listStateNodes()){
			ndim += stateNode.getDimension();
		}
		return ndim;
		
	}





	
	
	
	

}
