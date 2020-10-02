package poetry.learning;


import beast.core.Description;
import beast.core.Operator;
import beast.util.Randomizer;
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
	 */
	@Override
	public void sampleWeights() {
		
		
		if (this.poems == null || this.poems.isEmpty()) return;
		
		
		int dim = poems.size();
		double[] weights = new double[dim];
		
		
		// Sample a dirichlet vector
		double sum = 0.0;
		for (int j = 0; j < dim; j++) {
			POEM poem = poems.get(j);
			double a = poem.getAlpha();
			if (a == 0) {
				weights[j] = a;
			}else {
				weights[j] = Randomizer.nextGamma(a, 1.0);
			}
			sum += weights[j];
		}
		
		// Normalise
		for (int j = 0; j < dim; j++) {
			weights[j] = weights[j] / sum;
		}
		this.setWeights(weights);
		
		
	}





	




}
