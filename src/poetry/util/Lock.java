package poetry.util;
 
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import beast.core.util.Log;



/**
 * A crude method for preventing multiple instances from writing to the same file
 * Should work the use cases considered here and is easier to set up than an actual database
 * @author jdou557
 *
 */
public class Lock {
	
	// Time to sleep between checking for file unlocks
    private final static int sleepTime = 200;
    
    // If another process has locked the database for more than this long, it is assumed to have crashed and the file is automatically unlocked
    private final static int timeOut = 120000; 
    
    // This process number
    private String process;
    private File file;
    private boolean verbose;
    
    public Lock(String process, File database) {
    	this.process = process;
    	this.file = new File(database.getPath() + ".lock");
    	this.verbose = true;
    }
    
    
    public Lock(String process, File database, boolean verbose) {
    	this.process = process;
    	this.file = new File(database.getPath() + ".lock");
    	this.verbose = verbose;
    }
 
    
    /**
     * Try to lock the file by creating a temporary lock file
     * Does not return until the file has been locked
     * @throws InterruptedException
     */
    public void lock() throws InterruptedException {
    	
    	
        try {
     
            
            if (this.file.exists()) {
            	
            	
            	if (verbose) Log.warning("Process " + this.process + ": database is locked. Waiting for unlock...");
            	
            	
            	// What is the process row number?
            	BufferedReader br = new BufferedReader(new FileReader(file));
            	String processNum1 = br.readLine();
	            br.close();
            	
            	// Repeat until either the lock file has been deleted 
	            // or the first line of the file has not changed in a long time (in which case timeout is assumed)
            	int timeElapsed = 0;
            	while (true) {
	            

		            // Wait
		            Thread.sleep(Lock.sleepTime);
		            timeElapsed += Lock.sleepTime;
		            
		            if ( timeElapsed % 10000 == 0) {
		            	if (verbose) Log.warning("Time elapsed: " + timeElapsed + "ms. File is being used by process " + processNum1);
		            }
		            
		            
		            // If the lock is gone, then this is free
		            if (!file.exists()) {
		            	break;
		            }
		            
		            
		            // Check it again
		            br = new BufferedReader(new FileReader(file));
		            String processNum2 = br.readLine();
		            br.close();
		            
		            
		            // Parse line in file
		            if (processNum2 == null || processNum2.isEmpty()) continue;
		           
		            
		            
		            // Same number? The other program may have crashed
		            if (processNum1.equals(processNum2)) {
		            	
		            	if (timeElapsed > Lock.timeOut) {
		            		if (verbose) Log.warning("Timeout reached. Unlocking database.");
		            		break;
		            	}
		            	
		            }
		            
		            // Different number? Someone else must have gotten there first. Reset the timer
		            else {
		            	timeElapsed = 0;
		            }
		            
		            
		            // Repeat
		            processNum1 = processNum2;
		            
            	}
            
            
            }
            
            
            // Lock the file
            try {
            	Files.createFile(file.toPath());
            } catch (FileAlreadyExistsException e) {
            	if (verbose) Log.warning("File already exists. Trying again...");
            	Thread.sleep(Lock.sleepTime);
            	this.lock();
            	return;
            }
        	PrintWriter pw = new PrintWriter(file);
        	pw.print(this.process);
        	pw.close();
        	if (verbose) Log.warning("Unlock successful. Database is now being locked by process " + this.process);
            
            
 
        } catch(IOException e)  {
        	throw new RuntimeException("Could not open file", e);
        }
    }
    
    
    /**
     * Unlock the file by deleting the lock file
     */
    public void unlock() {
    	this.file.delete();
    	if (verbose) Log.warning("Unlocking database.");
    }
 
}

