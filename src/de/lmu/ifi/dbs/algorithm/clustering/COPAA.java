package de.lmu.ifi.dbs.algorithm.clustering;

import de.lmu.ifi.dbs.algorithm.AbstractAlgorithm;
import de.lmu.ifi.dbs.algorithm.Algorithm;
import de.lmu.ifi.dbs.algorithm.result.PartitionResults;
import de.lmu.ifi.dbs.algorithm.result.Result;
import de.lmu.ifi.dbs.data.RealVector;
import de.lmu.ifi.dbs.database.AssociationID;
import de.lmu.ifi.dbs.database.Database;
import de.lmu.ifi.dbs.pca.LocalPCA;
import de.lmu.ifi.dbs.preprocessing.CorrelationDimensionPreprocessor;
import de.lmu.ifi.dbs.utilities.Description;
import de.lmu.ifi.dbs.utilities.Progress;
import de.lmu.ifi.dbs.utilities.UnableToComplyException;
import de.lmu.ifi.dbs.utilities.Util;
import de.lmu.ifi.dbs.utilities.optionhandling.AttributeSettings;
import de.lmu.ifi.dbs.utilities.optionhandling.OptionHandler;
import de.lmu.ifi.dbs.utilities.optionhandling.ParameterValueException;

import java.util.*;

/**
 * Algorithm to partition a database according to the correlation dimension of
 * its objects and to then perform an clustering algorithm over the partitions.
 *
 * @author Elke Achtert (<a href="mailto:achtert@dbs.ifi.lmu.de">achtert@dbs.ifi.lmu.de</a>)
 */
public class COPAA extends AbstractAlgorithm<RealVector> {
  /**
   * Parameter for preprocessor.
   */
  public static final String PREPROCESSOR_P = "preprocessor";

  /**
   * Description for parameter preprocessor.
   */
  public static final String PREPROCESSOR_D = "<classname>preprocessor to derive partition criterion - must extend " + CorrelationDimensionPreprocessor.class.getName() + ".";

  /**
   * Parameter for partitioning algorithm.
   */
  public static final String PARTITION_ALGORITHM_P = "partAlg";

  /**
   * Description for parameter partitioning algorithm
   */
  public static final String PARTITION_ALGORITHM_D = "<classname>algorithm to apply to each partition - must implement " + Algorithm.class.getName() + ".";

  /**
   * Holds the preprocessor.
   */
  protected CorrelationDimensionPreprocessor preprocessor;

  /**
   * Holds the partitioning algorithm.
   */
  private Algorithm<RealVector> partitionAlgorithm;

  /**
   * Holds the result.
   */
  private PartitionResults<RealVector> result;

  /**
   * Sets the specific parameters additionally to the parameters set by the
   * super-class.
   */
  public COPAA() {
    super();
    parameterToDescription.put(COPAA.PREPROCESSOR_P + OptionHandler.EXPECTS_VALUE, COPAA.PREPROCESSOR_D);
    parameterToDescription.put(COPAA.PARTITION_ALGORITHM_P + OptionHandler.EXPECTS_VALUE, COPAA.PARTITION_ALGORITHM_D);
    optionHandler = new OptionHandler(parameterToDescription, this.getClass().getName());
  }

  /**
   * @see de.lmu.ifi.dbs.algorithm.Algorithm#run(de.lmu.ifi.dbs.database.Database)
   */
  protected void runInTime(Database<RealVector> database) throws IllegalStateException {
    // preprocessing
    if (isVerbose()) {
      System.out.println("\ndb size = " + database.size());
      System.out.println("dimensionality = " + database.dimensionality());
      System.out.println("\npreprocessing... ");
    }
    preprocessor.run(database, isVerbose());
    // partitioning
    if (isVerbose()) {
      System.out.println("\npartitioning... ");
    }
    Map<Integer, List<Integer>> partitionMap = new Hashtable<Integer, List<Integer>>();
    Progress partitionProgress = new Progress(database.size());
    int processed = 1;

    for (Iterator<Integer> dbiter = database.iterator(); dbiter.hasNext();) {
      Integer id = dbiter.next();
      Integer corrdim = ((LocalPCA) database.getAssociation(AssociationID.LOCAL_PCA, id)).getCorrelationDimension();

      if (!partitionMap.containsKey(corrdim)) {
        partitionMap.put(corrdim, new ArrayList<Integer>());
      }

      partitionMap.get(corrdim).add(id);
      if (isVerbose()) {
        partitionProgress.setProcessed(processed++);
        System.out.print("\r" + partitionProgress.toString());
      }
    }

    if (isVerbose()) {
      partitionProgress.setProcessed(database.size());
      System.out.print("\r" + partitionProgress.toString());
      System.out.println("");
      for (Integer corrDim : partitionMap.keySet()) {
        List<Integer> list = partitionMap.get(corrDim);
        System.out.println("Partition " + corrDim + " = " + list.size() + " objects.");
      }
    }

    // running partition algorithm
    result = runPartitionAlgorithm(database, partitionMap);
  }

  /**
   * @see de.lmu.ifi.dbs.algorithm.Algorithm#getResult()
   */
  public Result<RealVector> getResult() {
    return result;
  }

  /**
   * @see de.lmu.ifi.dbs.algorithm.Algorithm#getDescription()
   */
  public Description getDescription() {
    return new Description("COPAA", "Correlation Partitioning", "Partitions a database according to the correlation dimension of its objects and performs an arbitrary algorithm over the partitions.", "unpublished");
  }

  /**
   * Returns the the partitioning algorithm.
   *
   * @return the the partitioning algorithm
   */
  public Algorithm<RealVector> getPartitionAlgorithm() {
    return partitionAlgorithm;
  }

  /**
   * @see de.lmu.ifi.dbs.utilities.optionhandling.Parameterizable#description()
   */
  @Override
  public String description() {
    StringBuffer description = new StringBuffer();
    description.append(optionHandler.usage("", false));
    description.append('\n');
    description.append("Remaining parameters are firstly given to the partition algorithm, then to the preprocessor.");
    description.append('\n');
    description.append('\n');
    return description.toString();
  }

  /**
   * Passes remaining parameters first to the partition algorithm, then to the
   * preprocessor.
   *
   * @see de.lmu.ifi.dbs.utilities.optionhandling.Parameterizable#setParameters(String[])
   */
  @Override
  public String[] setParameters(String[] args) throws IllegalArgumentException {
    String[] remainingParameters = super.setParameters(args);

    // partition algorithm
    try {
      // noinspection unchecked
      partitionAlgorithm = Util.instantiate(Algorithm.class, optionHandler.getOptionValue(PARTITION_ALGORITHM_P));
    }
    catch (IllegalArgumentException e) {
      ParameterValueException pfe = new ParameterValueException(PARTITION_ALGORITHM_P, optionHandler.getOptionValue(PARTITION_ALGORITHM_P), PARTITION_ALGORITHM_D);
      pfe.fillInStackTrace();
      throw pfe;
    }

    // preprocessor
    try {
      preprocessor = Util.instantiate(CorrelationDimensionPreprocessor.class, optionHandler.getOptionValue(COPAA.PREPROCESSOR_P));
    }
    catch (IllegalArgumentException e) {
      ParameterValueException pfe = new ParameterValueException(PREPROCESSOR_P, optionHandler.getOptionValue(PREPROCESSOR_P));
      pfe.fillInStackTrace();
      throw pfe;
    }

    remainingParameters = preprocessor.setParameters(remainingParameters);
    return partitionAlgorithm.setParameters(remainingParameters);
  }

  /**
   * Returns the parameter setting of the attributes.
   *
   * @return the parameter setting of the attributes
   */
  public List<AttributeSettings> getAttributeSettings() {
    List<AttributeSettings> result = super.getAttributeSettings();

    AttributeSettings settings = result.get(0);
    settings.addSetting(COPAA.PREPROCESSOR_P, preprocessor.getClass().getName());
    settings.addSetting(COPAA.PARTITION_ALGORITHM_P, partitionAlgorithm.getClass().getName());

    result.addAll(preprocessor.getAttributeSettings());
    result.addAll(partitionAlgorithm.getAttributeSettings());

    return result;
  }

  /**
   * Runs the partition algorithm and creates the result.
   *
   * @param database     the database to run this algorithm on
   * @param partitionMap the map of partition IDs to object ids
   */
  protected PartitionResults<RealVector> runPartitionAlgorithm(Database database, Map<Integer, List<Integer>> partitionMap) {
    try {
      Map<Integer, Database<RealVector>> databasePartitions = database.partition(partitionMap);
      Map<Integer, Result<RealVector>> results = new Hashtable<Integer, Result<RealVector>>();
      for (Integer partitionID : databasePartitions.keySet()) {
        if (isVerbose()) {
          System.out.println("\nRunning " + partitionAlgorithm.getDescription().getShortTitle() + " on partition " + partitionID);
        }
        partitionAlgorithm.run(databasePartitions.get(partitionID));
        results.put(partitionID, partitionAlgorithm.getResult());
      }
      return new PartitionResults<RealVector>(database, results);
    }
    catch (UnableToComplyException e) {
      throw new IllegalStateException(e);
    }
  }
}
