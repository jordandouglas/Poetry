package poetry.decisiontree;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import beast.core.Description;
import beast.core.Distribution;
import beast.core.Input;
import beast.core.State;
import beast.core.Input.Validate;
import beast.core.parameter.RealParameter;



@Description("Places a geometric prior on the number of leaves in a decision tree")
public class GeometricTreePrior extends Distribution {

	
	
	final public Input<DecisionTree> treeInput = new Input<>("tree", "The decision tree", Validate.REQUIRED);
	final public Input<RealParameter> leafSizeMeanInput = new Input<>("mean", "Geometric mean number of leaves in the tree", Validate.REQUIRED);
	
	
	@Override
	public void initAndValidate() {
		
		
		if (leafSizeMeanInput.get().getArrayValue() <= 1.0) {
			throw new IllegalArgumentException("Geometric-mean leaf count must be greater than 1.0");
		}
		leafSizeMeanInput.get().setLower(1.0);

	}
	
	
	@Override
	public double calculateLogP() {
		 
		 logP = 0;
		 
		 
		 // Geometric distribution on leaf count
		 int treeSize = treeInput.get().getLeafCount();
		 double p = 1.0/(leafSizeMeanInput.get().getArrayValue());
		 if (p <= 0 || p >= 1) return Double.NEGATIVE_INFINITY;
		 logP += Math.log(p) + (treeSize-1)*Math.log(1-p);
		 
		 


		 
		 return logP;
	}
	
	
	@Override
	public List<String> getArguments() {
		List<String> args = new ArrayList<>();
		args.add(treeInput.get().getID());
		return args;
	}

	@Override
	public List<String> getConditions() {
		List<String> conds = new ArrayList<>();
		conds.add(leafSizeMeanInput.get().getID());
		return conds;
	}

	@Override
	public void sample(State state, Random random) {
		// TODO Auto-generated method stub
		
	}

}
