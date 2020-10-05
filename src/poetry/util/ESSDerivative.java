package poetry.util;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import beast.core.BEASTObject;
import beast.core.Description;
import beast.core.Input;
import beast.core.util.ESS;


@Description("Logs the ESS per nstates of a parameter")
public class ESSDerivative extends ESS {

	
	 final public Input<Integer> windowSizeInput = new Input<>("window", "number of most-recently logged values to compute ESS for", 50);

	 
	 
 
 
	 protected List<List<Double>> traces;
	 protected double[] sums;
	 protected List<List<Double>> squareLaggedSumss;

	 
	 
	// boolean includeFn;
	 int window;
	 int ndim;
	 
	 final static int MAX_LAG = 2000;
	 
	 @Override
	 public void initAndValidate() {
		 this.window = windowSizeInput.get();
		 this.ndim = functionInput.get().getDimension();

		 this.traces = new ArrayList<>();
		 this.sums = new double[ndim];
		 this.squareLaggedSumss = new ArrayList<>();
		 for (int i = 0; i < ndim; i ++) {
			 List<Double> arr = new ArrayList<>();
			 this.traces.add(arr);
			 arr = new ArrayList<>();
			 this.squareLaggedSumss.add(arr);
		 }
		 
		 
	 }

	 @Override
	 public void init(PrintStream out) {
		 for (int i = 0; i < this.ndim; i ++) {
			 String id = ((BEASTObject) functionInput.get()).getID();
			 if (functionInput.get().getDimension() > 1) id += "." + i;
			 out.print("dESS(" + id + ")\t");
		 }
		 
	 }
	 
	 @Override
	 public void log(final long sample, PrintStream out) {
		 
		 
		 for (int i = 0; i < this.ndim; i ++) {
		 
			 List<Double> trace = this.traces.get(i);
			 //List<Double> squareLaggedSums = this.squareLaggedSumss.get(i);
			 
			 
			 final Double newValue = functionInput.get().getArrayValue(i);
			 trace.add(newValue);
			 
			 // Clear oldest trace entry if applicable
			 while (trace.size() > this.window) {
				 trace.remove(0);
			 }
			 
			 
			 
			 
			 
			 double ess = calcESS(trace);
			 String str;
			 if (Double.isNaN(ess)) {
		        str = "0.0";
		     }else {
				str = ess + "";
				str = str.substring(0, str.indexOf('.') + 2);
		     }
		        
			 out.print(str + "\t");
	        
		 }
		 
	 }
	
}
