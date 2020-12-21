package poetry.learning;


import java.util.List;

import beast.core.Description;
import beast.core.Operator;
import beast.util.Randomizer;
import poetry.operators.MetaOperator;
import poetry.sampler.POEM;

@Description("Assign weights to operators by sampling from a Dirichlet distribution")
public class DirichletSampler extends WeightSampler {

	
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
		double[] weights = sampleWeights(this.poems);
		if (weights != null) this.setWeights(weights);
	}

	
	
	/**
	 * Return an array of sampled weights
	 * @param poems
	 */
	@Override
	public double[] sampleWeights(List<POEM> poems) {
		
		
		if (poems == null || poems.isEmpty()) return null;
		
		
		int dim = poems.size();
		double[] weights = new double[dim];
		
		
		// Sample a dirichlet vector
		double weightSum = 0;
		for (int j = 0; j < dim; j++) {
			POEM poem = poems.get(j);
			double a = poem.getAlpha();
			if (a == 0) {
				weights[j] = a;
			}else {
				weights[j] = Randomizer.nextGamma(a, 1.0);
			}
			weightSum += weights[j];
		}
		
		
		// Normalise
		for (int j = 0; j < dim; j++) {
			weights[j] /= weightSum;
		}
		
		return weights;
		
		
	}

	





	




}
