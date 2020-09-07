package xmlsimulator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

import beast.core.BEASTObject;
import beast.core.Input;

public class XMLSimulatorLogger extends BEASTObject {

	final public Input<File> fileInput = new Input<>("fileName", "The location of the file (can be zipped)", Input.Validate.REQUIRED);

	File fileOut;
	
	@Override
	public void initAndValidate() {
		

		this.fileOut = fileInput.get();
		
		
	}
	
	
	
	
	public void log() {
		
		try {
			PrintStream out = new PrintStream(this.fileOut);
			out.println("hi");
			out.close();
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
	}
	
}













