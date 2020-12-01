package poetry.decisiontree;

import beast.core.BEASTObject;
import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import poetry.util.WekaUtils;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;


@Description("Weka Instances, including a test/training split")
public class WekaData extends BEASTObject {

	
	final public Input<String> arffInput = new Input<>("arff", "The .arff file which contains data", Validate.REQUIRED);
	final public Input<Double> dataSplitInput = new Input<>("dataSplit", "Split the data into a training set with this percentage of the data", 70.0);
	final public Input<Boolean> dataSplitByXMLInput = new Input<>("xmlSplit", "Split the data into training/test sets using the XML number?", true);
	
	
	Instances data;
	double dataSplitProportion;
	
	
	// The data
	Instances trainingData;
	Instances testData;
	
	@Override
	public void initAndValidate() {
		
		// Read the arff file
		this.data = null;
		try {
			data = new DataSource(arffInput.get()).getDataSet();
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Error opening arff file " + arffInput.get());
		}
		
		// Training / test split
		this.dataSplitProportion = dataSplitInput.get() / 100;
		if (this.dataSplitProportion < 0) this.dataSplitProportion = 0;
		if (this.dataSplitProportion > 1) this.dataSplitProportion = 1;
		Instances[] res = WekaUtils.splitData(data, this.dataSplitProportion, dataSplitByXMLInput.get());
		this.trainingData = res[0];
		this.testData = res[1];
		
	}


	/**
	 * The training data
	 * @return
	 */
	public Instances getTrainingData() {
		return this.trainingData;
	}
	
	
	/**
	 * The test data
	 * @return
	 */
	public Instances getTestData() {
		return this.testData;
	}
	

}
