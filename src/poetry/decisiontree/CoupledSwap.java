package poetry.decisiontree;

import java.util.ArrayList;
import java.util.List;

import beast.core.BEASTInterface;
import beast.core.Description;
import beast.core.Input;
import beast.core.Operator;
import beast.core.StateNode;
import beast.core.parameter.IntegerParameter;
import beast.core.parameter.RealParameter;
import beast.core.util.Log;
import beast.util.Randomizer;


@Description("Swaps elements within 2 or more vectors of the same lengths and uses the same swap indices for each vector")
public class CoupledSwap extends Operator {

	
	final public Input<List<RealParameter>> parameterInput = new Input<>("parameter", "A parameter vector", new ArrayList<RealParameter>());
	final public Input<List<IntegerParameter>> intparameterInput = new Input<>("intparameter", "An integer parameter vector", new ArrayList<IntegerParameter>());
	
	
	@Override
	public void initAndValidate() {
		
		
		int nparams = parameterInput.get().size() + intparameterInput.get().size();
		if (nparams < 1) throw new IllegalArgumentException("Error: please provide at least 1 parameter or int parameter");
		
		// Dimensions match?
		this.checkDimensions(true);
		
	}

	@Override
	public double proposal() {
		
		
		// Dimensions match?
		if (!this.checkDimensions(false)) return Double.NEGATIVE_INFINITY;
		
		// How many dimensions
		int ndim = this.getDimensions();
		if (ndim < 2) return Double.NEGATIVE_INFINITY;
		
		// Sample 2 indices to swap
		int index1 = Randomizer.nextInt(ndim);
		int index2 = index1;
		while(index2 == index1) index2 = Randomizer.nextInt(ndim);
		
		// Swap them
		for (RealParameter param : parameterInput.get()) {
			double val1 = param.getArrayValue(index1);
			double val2 = param.getArrayValue(index2);
			param.setValue(index1, val2);
			param.setValue(index2, val1);
			//Log.warning("Real swapping " + val1 + " with "  + val2);
		}
		for (IntegerParameter param : intparameterInput.get()) {
			int val1 = (int) param.getArrayValue(index1);
			int val2 = (int) param.getArrayValue(index2);
			param.setValue(index1, val2);
			param.setValue(index2, val1);
			//Log.warning("Int swapping " + val1 + " with "  + val2);
		}
		
		return 0;
	}
	
	
	/**
	 * Check that all vectors have matching dimensions. Throws error if this condition is not met (if throwError=T) or returns false otherwise
	 * @param throwError
	 * @return
	 */
	private boolean checkDimensions(boolean throwError) {
		
		// Dimensionality check
		int ndim = -1;
		for (RealParameter param : parameterInput.get()) {
			if (ndim == -1) ndim = param.getDimension();
			if (ndim != param.getDimension()) {
				if (throwError) throw new IllegalArgumentException("Error: " + param.getID() + " does not have the same number of dimensions as the other parameters " + ndim + " != " + param.getDimension());
				return false;
			}
			
		}
		for (IntegerParameter param : intparameterInput.get()) {
			if (ndim == -1) ndim = param.getDimension();
			if (ndim != param.getDimension()) {
				if (throwError)  throw new IllegalArgumentException("Error: " + param.getID() + " does not have the same number of dimensions as the other parameters " + ndim + " != " + param.getDimension());
				return false;
			}
		}
		
		
		return true;
		
	}
	
	/**
	 * Count the dimensions
	 * @return
	 */
	private int getDimensions() {
		
		for (RealParameter param : parameterInput.get()) {
			return param.getDimension();
		}
		for (IntegerParameter param : intparameterInput.get()) {
			return param.getDimension();
		}
		
		return 0;
		
	}
	
	
	@Override
    public List<StateNode> listStateNodes() {
        final List<StateNode> list = new ArrayList<>();
        for (RealParameter param : parameterInput.get()) list.add(param);
        for (IntegerParameter param : intparameterInput.get()) list.add(param);
        return list;
    }
	
	

}
















