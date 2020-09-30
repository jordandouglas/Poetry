package poetry.util;


import java.io.PrintStream;
import java.time.LocalDateTime;
import beast.core.CalculationNode;
import beast.core.Function;
import beast.core.Input;
import beast.core.Loggable;

import java.time.temporal.ChronoUnit;

public class RuntimeLoggable extends CalculationNode implements Loggable, Function {
	
	final public Input<Boolean> staticInput = new Input<>("static", "Use static times? This will mean runtimes are shared between parallel chains"
			+ "and makes the class suitable for coupled MCMC. But it will cause interference if there is more than one being logged. Default: false", false);
	
	
	// Shared among all instances to enable compatibility with coupled MCMC, which swaps loggers
	protected LocalDateTime dateTimeFirst_l;
	protected LocalDateTime dateTimeLast_l;
	
	
	// Shared among all instances to enable compatibility with coupled MCMC, which swaps loggers
	protected static LocalDateTime dateTimeFirst_g;
	protected static LocalDateTime dateTimeLast_g;
	
	
	boolean isStatic;
	
	@Override
    public void initAndValidate() {
		
		LocalDateTime now = LocalDateTime.now();
		
		this.isStatic = staticInput.get();
		
		if (this.isStatic) {
			RuntimeLoggable.dateTimeFirst_g = now;
			RuntimeLoggable.dateTimeLast_g = now;
		}else {
			this.dateTimeFirst_l = now;
			this.dateTimeLast_l = now;
		}
		
	}
	

	@Override
	public void init(PrintStream out) {
		out.print(getIncrementalColname() + "\t" + getCumulativeColname() + "\t");
	}
	
	
	/**
	 * The name of the incremental runtime column in a logfile
	 * @return
	 */
	public static String getIncrementalColname() {
		return "runtime.incr.ms";
	}
	
	/**
	 * The name of the cumulative runtime column in a logfile
	 * @return
	 */
	public static String getCumulativeColname() {
		return "runtime.cumu.s";
	}
	
	@Override
	public void log(long sample, PrintStream out) {

		// Initialise times
		LocalDateTime dateTimeFirst, dateTimeLast;
		if (this.isStatic) {
			dateTimeFirst = RuntimeLoggable.dateTimeFirst_g;
			dateTimeLast = RuntimeLoggable.dateTimeLast_g;
		}else {
			dateTimeFirst = this.dateTimeFirst_l;
			dateTimeLast = this.dateTimeLast_l;
		}
		
		
		
		// Current time
		LocalDateTime dateTimeNow = LocalDateTime.now();
		
		
		// Number of ms since last log
		long incremental = ChronoUnit.MILLIS.between(dateTimeLast, dateTimeNow);
		
		// Cumulative number of seconds (large units = less chance of overflow)
		long cumulative = ChronoUnit.SECONDS.between(dateTimeFirst, dateTimeNow);
		

		// Log
		out.print(incremental + "\t");
		out.print(cumulative + "\t");
		
		// Update time
		if (this.isStatic) {
			RuntimeLoggable.dateTimeLast_g = dateTimeNow;
		}else {
			this.dateTimeLast_l = dateTimeNow;
		}
		
    }

	
	@Override
	public void close(PrintStream out) {
		
	}


	@Override
	public int getDimension() {
		return 2;
	}
	
	@Override
	public double getArrayValue() {
	   return getArrayValue(0);
	}


	@Override
	public double getArrayValue(int dim) {
		
		
		// Get time, either global or local 
		LocalDateTime dateTimeFirst, dateTimeLast;
		if (this.isStatic) {
			dateTimeFirst = RuntimeLoggable.dateTimeFirst_g;
			dateTimeLast = RuntimeLoggable.dateTimeLast_g;
		}else {
			dateTimeFirst = this.dateTimeFirst_l;
			dateTimeLast = this.dateTimeLast_l;
		}
		
		
		// Return time
		if (dateTimeLast == null || dateTimeFirst == null) return 0;
		LocalDateTime dateTimeNow = LocalDateTime.now();
		if (dim == 0) {
			return ChronoUnit.MILLIS.between(dateTimeLast, dateTimeNow); 
		}else {
			return ChronoUnit.SECONDS.between(dateTimeFirst, dateTimeNow);
		}
	}
	
	
	
	

}
