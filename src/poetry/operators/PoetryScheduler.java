package poetry.operators;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import beast.core.BEASTInterface;
import beast.core.BEASTObject;
import beast.core.Description;
import beast.core.Input;
import beast.core.MCMC;
import beast.core.Operator;
import beast.core.OperatorSchedule;
import beast.core.StateNode;
import beast.core.util.Log;
import beast.coupledMCMC.CoupledMCMC;
import beast.coupledMCMC.HeatedChain;
import poetry.PoetryAnalyser;
import poetry.learning.WeightSampler;
import poetry.sampler.POEM;


@Description("An operator schedule which assigns operater weights at the start of MCMC and then updates a central database at the end of MCMC."
		+ " Also accepts a proper operator schedule for sampling weights during MCMC")
public class PoetryScheduler extends OperatorSchedule {

	
	
	final public Input<WeightSampler> weightSamplerInput = new Input<>("sampler", "A method for setting weights at the beginning of MCMC");
	
	
	final public Input<Integer> updateEveryInput = new Input<>("updateEvery", "How often to update the database (i.e. number of operator "
			+ "calls in between each update). Set to the chain length to only do it once at the end.", Input.Validate.REQUIRED);
	
	
	final public Input<String> databaseFileInput = new Input<>("database", "The location of a the database (tsv)", Input.Validate.REQUIRED);
	final public Input<Integer> sampleNumInput = new Input<>("number", "The row number in the database of this sample", Input.Validate.REQUIRED);
	final public Input<List<POEM>> poemsInput = new Input<>("poem", "A map between operators and log outputs", new ArrayList<>());
	final public Input<String> runtimeLoggerInput = new Input<>("runtime", "Log file containing runtimes");
	final public Input<Integer> burninInput = new Input<>("burnin", "Burnin percentage for ESS computation (default 10)", 10);
	
	final public Input<StateNode> placeholderInput = new Input<>("placeholder", "A temporary state node which will be removed from all operators when MCMC "
			+ "begins. This enables operators to have no state nodes without MCMC throwing an error.");
	
	final public Input<Boolean> noMCMC = new Input<>("noMCMC", "Set to true to only run the poetry analyser (and update the database) without"
			+ " actually doing any MCMC. BEAST2 will exit afterwards (default false)", false);
	
	
	long numCalls;
	int updateEvery;
	WeightSampler sampler;
	PoetryAnalyser poetry;
	File database;
	
	
	 @Override
     public void initAndValidate() {
		 
		 super.initAndValidate(); 

		 this.sampler = weightSamplerInput.get();
		 this.numCalls = 0;
		 this.updateEvery = updateEveryInput.get();
		 if (this.updateEvery <= 0) throw new IllegalArgumentException("Please set updateEvery to at least 1. "
		 		+ "Set to the the MCMC chain length to only do it once at the end" );
		 
		 // Files
		 this.database = new File(this.databaseFileInput.get());
		 File runtimeLog = new File(this.runtimeLoggerInput.get());

		
		 this.poetry = new PoetryAnalyser(this.sampleNumInput.get(),  this.database, this.poemsInput.get(), runtimeLog, this.burninInput.get());
		
		
		 // Update the database and then exit
		 if (noMCMC.get()) {
			try {
				this.poetry.run();
				Log.warning("PoetryScheduler: " +  this.database.getPath() + " has been updated. Exiting now.");
				Log.warning("\t\t\t\t If you wanted to run MCMC, set 'noMCMC' to 'false' in the xml file");
				
			} catch (Exception e) {
				e.printStackTrace();
				Log.warning("PoetryScheduler: failed to update " + database.getPath() + ". Exiting now.");
			}
			System.exit(1);
			
		 }
		 
		 
		 // Sample the weights but don't set them just yet
		 // If this is static mode, the sampled weights will override each other between chains
		 if (this.sampler != null) {
			 sampler.initialise(poemsInput.get(), this.database, this.placeholderInput.get());
			 if (this.isColdChain()) sampler.sampleWeights();
		 }
		 
		 
	 }
	
	 
	 
	 /**
	  * Is this chain a cold chain? 
	  * If using MCMC then this is true
	  * If using MCMCMC, then check if the chain associated with this schedule has temperature of 0
	  * @return
	  */
	 public boolean isColdChain() {
		 
		 for (BEASTInterface obj : this.getOutputs()) {
			 
			 if (obj instanceof MCMC) {
				 if (obj instanceof HeatedChain) {
					 HeatedChain mc3 = (HeatedChain) obj;
					 return mc3.isColdChain();
				 }else {
					 return true;
				 }
			 }
		 }
		 
		 return true;
		 
	 }
	 
	 
	 
	 @Override
	 public Operator selectOperator() {
		 
		 // Set the weights
		 if (this.numCalls == 0 && this.sampler != null ) {
			 sampler.setOperators(this.operators);
			 sampler.applyWeights();
			 sampler.report();
			 this.reweightOperators();
		 }
		 
		 
		 this.numCalls ++;
		
		 
		 // Update the database if this is the cold chain
		 if (this.numCalls % updateEvery == 0 && this.isColdChain() ) {
			 try {
				this.poetry.run();
			} catch (Exception e) {
				e.printStackTrace();
			}
		 }
		 
		 return super.selectOperator();
		 
	 }
	 
	 
	
	
	
	
}



