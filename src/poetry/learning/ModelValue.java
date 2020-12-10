package poetry.learning;

import beast.core.BEASTObject;
import beast.core.Description;
import beast.core.Input;


@Description("Maps attributes to values for applying statistical models to the current phylogenetic model")
public class ModelValue extends BEASTObject {

	
	final public Input<String> modelInput = new Input<>("model", "The name of the attribute", Input.Validate.REQUIRED);
	final public Input<String> valueInput = new Input<>("value", "The value of the attribute", Input.Validate.REQUIRED);
	final public Input<Boolean> numericInput = new Input<>("numeric", "Is the attribute numeric? (default: false ie. nominal)", false);
	
	
	
	@Override
	public void initAndValidate() {
		// TODO Auto-generated method stub
		
	}
	
	
	public String toString() {
		return this.getModel() + "=" + this.getValue();
	}
	
	public String getModel() {
		return this.modelInput.get();
	}
	
	
	public String getValue() {
		return this.valueInput.get();
	}
	
	public boolean isNumeric() {
		return numericInput.get();
	}

}
