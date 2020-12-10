package poetry.learning;


import beast.core.Description;
import beast.core.Input;
import beast.util.Randomizer;
import poetry.sampler.POEM;

@Description("Assign weights to operators proportionally to their number of dimensions and prior expert knowledge")
public class DimensionalSampler extends WeightSampler {

	
	final public Input<Double> scaleInput = new Input<>("scale", "Number to multiply (normalised) dimensions by when assigning Dirichlet alphas. "
			+ "Large scale = small variance. Set to -1 for deterministic sampling", 20.0);
	
	@Override
	public void initAndValidate() {
		// TODO Auto-generated method stub
		
	}

	
	/**
	 * Sample weights using a dirichlet distribution
	 * The alpha of each operator is specified in the poem
	 * @throws Exception 
	 */
	@Override
	public void sampleWeights() throws Exception {
		
		
		if (this.poems == null || this.poems.isEmpty()) return;
		
		
		int dim = poems.size();
		double[] weights = new double[dim];
		
		
		// Calculate dimension sum so it can be normalised
		double dimensionSum = 0;
		for (int j = 0; j < dim; j++) {
			
			POEM poem = poems.get(j);
			int poemDim = poem.getDim();
			if (poem.getAlpha() == 0) poemDim = 0;
			weights[j] = modifyDimension(poem.getID(), poemDim);;
			dimensionSum += weights[j];
		}
		
		
		// Convert dimensions into alphas
		double scale = scaleInput.get();
		for (int j = 0; j < dim; j++) {	
			
			double val = weights[j] / dimensionSum;
			if (scale > 0) val = val * scale;
			
			
			System.out.println(poems.get(j).getID() + ": alpha = " + val);
			
			if (val == 0 || scale <= 0) {
				weights[j] = val;
			}else {
				weights[j] = Randomizer.nextGamma(val, 1.0);
			}
		}
		

		this.setWeights(weights);
		
		
	}


	
	/**
	 * Incorporate prior knowledge about the poem to adjust for its effective dimension
	 * @param poemID
	 * @param dimension
	 * @return
	 */
	public static double modifyDimension(String poemID, int dimension) {
		
		
		switch (poemID) {
		
			
			// Number of topology dimensions is the number of non-root nodes
			case "TopologyPOEM":{
				double nnodes = dimension;
				return nnodes-1;
			}
			
			// Number of node height dimensions is the number of non-leaf nodes, potentially plus the clock rate too
			case "NodeHeightPOEM":{
				double nnodesTimes2 = dimension;
				return Math.round((nnodesTimes2+1.0)/4);
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
				double nnodesTimes2 = dimension;
				return Math.round((nnodesTimes2+1.0)/2);
			}
			
			
			// Clock model SD mixes poorly so assign 10, despite it being just 1 parameter
			case "ClockModelSDPOEM":{
				return 10;
			}
		
		}
		
		return dimension;
		
	}





	




}
