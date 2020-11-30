package poetry.decisiontree;

import beast.core.BEASTObject;
import beast.core.Description;
import beast.core.Function;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.parameter.RealParameter;
import beast.util.Transform;
import poetry.util.WekaUtils;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;


@Description("A numeric attribute within a weka Instances object")
public class Feature extends RealParameter  {


	
	final public Input<String> nameInput = new Input<>("attr", "The name of the numeric attribute", Validate.REQUIRED);
	
	
	Instances data = null;
	Attribute attr = null;
	boolean isTransformed = false;
	
	@Override
	public void initAndValidate() {
		// TODO Auto-generated method stub
		
	}
	
	public void setDataset(Instances data) {
		
		this.data = data;
		
		// Confirm that this variable is in the dataset
		int colnum = WekaUtils.getIndexOfColumn(data, this.nameInput.get());
		if (colnum == -1) throw new IllegalArgumentException("Error: cannot find " + this.nameInput.get() + " in dataset");
		
		// Confirm that it is numeric
		if (!data.attribute(colnum).isNumeric()) throw new IllegalArgumentException("Error: " + this.nameInput.get() + " is not a numeric attribute");
		
		this.attr = data.attribute(colnum);
	}


	@Override
	public int getDimension() {
		if (this.data == null) return 0;
		return this.data.size();
	}


	@Override
	public double getArrayValue(int dim) {
		return this.data.instance(dim).value(this.attr);
	}

	public Attribute getAttribute() {
		return this.attr;
	}

	
	/**
	 * Transforms the feature or inverses the transformation if it is already transformed
	 * @param ttarget
	 */
	public void transform(Transform transform) {
		
		for (int i = 0; i < this.data.size(); i ++) {
			Instance inst = this.data.instance(i);
			double val = inst.value(this.attr);
			double tval = this.isTransformed ? transform.inverse(val) : transform.transform(val);
			inst.setValue(this.attr, tval);
		}
		
		this.isTransformed = !isTransformed; 
		
	}
	


}




