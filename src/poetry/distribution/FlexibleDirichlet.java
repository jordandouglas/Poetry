package poetry.distribution;

import beast.core.util.Log;

// https://www.sciencedirect.com/science/article/pii/S0047259X12001753
public class FlexibleDirichlet {


	
	/**
	 * Return the mean value of index i
	 * @param index
	 * @param alpha
	 * @param p
	 * @param tau
	 * @return
	 */
	public static double getExpectation(int index, double[] alpha, double[] p, double tau) {
		
		// Eqn 9
		double alphaSum = 0;
		for (int i = 0; i < alpha.length; i ++) alphaSum += alpha[i];
		
		double mean;
		if (p == null) {
			mean = alpha[index] / alphaSum;
		}else {
			mean = (alpha[index] + p[index]*tau) / (alphaSum + tau);
		}
		return mean;
	}
	
	
	/**
	 * Return log probability
	 * @param X
	 * @param alpha
	 * @param p
	 * @param tau
	 * @return
	 */
    public static double calcLogP(double[] X, double[] alpha, double[] p, double tau) {
    	
    	if (p != null && alpha.length != p.length) {
			 throw new IllegalArgumentException("Dimensions of alpha and p should be the same " + alpha.length + " != " + p.length);
    	}
    	if (alpha.length != X.length) {
			 throw new IllegalArgumentException("Dimensions of alpha and x should be the same " + alpha.length + " != " + X.length);
    	}
       
    	
    	if (p != null && tau <= 0) return Double.NEGATIVE_INFINITY;
    	
    	// Standard dirichlet stuff (eqn 5; LHS)
        double logP = 0;
        double sumAlpha = 0;
        for (int i = 0; i < X.length; i++) {
            double x = X[i];
            if (x == 0 || x == 1) continue;
            logP += (alpha[i] - 1) * Math.log(x);
            logP -= org.apache.commons.math.special.Gamma.logGamma(alpha[i]);
            sumAlpha += alpha[i];
        }
        logP += org.apache.commons.math.special.Gamma.logGamma(sumAlpha);
        
        
        // Flexible dirichlet (eqn 5; RHS)
        if (p != null) {
        	
        	double sum = 0;
        	for (int i = 0; i < p.length; i ++) {
        		
        		double x = X[i];
        		if (x == 0 || x == 1) continue;
        		double prob = p[i];
        		if (prob < 0 || prob > 1) return Double.NEGATIVE_INFINITY;
        		double a = alpha[i];
        		double y =  Math.exp(Math.log(prob) + org.apache.commons.math.special.Gamma.logGamma(a) - org.apache.commons.math.special.Gamma.logGamma(a + tau));
        		sum += y * Math.pow(x, tau);
        		
        		
        	}
        	
        	 
        	
        	logP += Math.log(sum);
        	
        }
        
        
        
        return logP;
    }
	
	
	
}
