package poetry.decisiontree;

import java.util.ArrayList;
import java.util.List;

import beast.core.BEASTObject;
import beast.core.Description;
import beast.core.Function;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.parameter.RealParameter;
import beast.core.util.Log;
import beast.util.Transform;
import poetry.util.WekaUtils;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;


@Description("A numeric attribute within a weka Instances object")
public class Feature extends RealParameter  {


	
	final public Input<String> nameInput = new Input<>("attr", "The name of the numeric attribute", Validate.REQUIRED);
	final public Input<WekaData> dataInput = new Input<>("data", "The data", Validate.REQUIRED);
	
	
	Instances data = null;
	String attrName;
	boolean isTransformed = false;
	
	public Feature(){
		this.valuesInput.setRule(Validate.OPTIONAL);
	}
	
	@Override
	public void initAndValidate() {
		
		
		
		this.data = dataInput.get().getTrainingData();
		this.attrName = nameInput.get();
		
		// Confirm that this variable is in the dataset
		int colnum = WekaUtils.getIndexOfColumn(data, attrName);
		if (colnum == -1) throw new IllegalArgumentException("Error: cannot find " + this.nameInput.get() + " in dataset");
		
		// Confirm that it is numeric
		if (!data.attribute(colnum).isNumeric()) throw new IllegalArgumentException("Error: " + this.nameInput.get() + " is not a numeric attribute");
		
		Attribute attr = data.attribute(colnum);
		
		
		// Values
		List<Double> vals = new ArrayList<>();
		for (int i = 0; i < this.data.size(); i ++) {
			Instance inst = this.data.instance(i);
			double val = inst.value(attr);
			vals.add(val);
		}
		this.valuesInput.set(vals);
		
		super.initAndValidate();
		
	}
	
	
	


	@Override
	public int getDimension() {
		if (this.data == null) return 0;
		return this.data.size();
	}


	@Override
	public double getArrayValue(int dim) {
		return this.data.instance(dim).value(this.getAttribute());
	}

	public Attribute getAttribute() {
		int colnum = WekaUtils.getIndexOfColumn(data, attrName);
		return data.attribute(colnum);
	}

	
	/**
	 * Transforms the feature or inverses the transformation if it is already transformed
	 * @param ttarget
	 */
	public void transform(Transform transform) {
		//if (true) return;
		
		Log.warning("Transforming " + this.attrName);
		
		
		
		// Get values
		double[] vals = new double[this.data.size()];
		for (int i = 0; i < this.data.size(); i ++) {
			Instance inst = this.data.instance(i);
			double val = inst.value(this.getAttribute());
			vals[i] = val;
		}
		
		// Transform
		double[] tvals = this.isTransformed ? transform.inverse(vals, 0, vals.length) :  transform.transform(vals, 0, vals.length);
		
		// Set values
		for (int i = 0; i < this.data.size(); i ++) {
			Instance inst = this.data.instance(i);
			double tval = tvals[i];
			if (Double.isNaN(tval)) tval = 0;
			inst.setValue(this.getAttribute(), tval);
		}
		
		this.isTransformed = !isTransformed; 
		
	}

	public String getAttrName() {
		return this.attrName;
	}
	


}




