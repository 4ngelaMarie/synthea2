package org.mitre.synthea.engine;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.stream.XMLStreamException;
import org.apache.commons.math.ode.DerivativeException;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.SBMLDocument;
import org.sbml.jsbml.SBMLException;
import org.sbml.jsbml.validator.ModelOverdeterminedException;
import org.sbml.jsbml.xml.stax.SBMLReader;
import org.simulator.math.odes.AbstractDESSolver;
import org.simulator.math.odes.AdamsBashforthSolver;
import org.simulator.math.odes.AdamsMoultonSolver;
import org.simulator.math.odes.DormandPrince54Solver;
import org.simulator.math.odes.DormandPrince853Solver;
import org.simulator.math.odes.EulerMethod;
import org.simulator.math.odes.GraggBulirschStoerSolver;
import org.simulator.math.odes.HighamHall54Solver;
import org.simulator.math.odes.MultiTable;
import org.simulator.math.odes.MultiTable.Block.Column;
import org.simulator.math.odes.RosenbrockSolver;
import org.simulator.math.odes.RungeKutta_EventSolver;
import org.simulator.sbml.SBMLinterpreter;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 * Executes simulations of physiology models represented in SBML files.
 */
public class PhysiologySimulator {

  /** Map of user-facing strings to their corresponding Java classes. **/
  private static final Map<String, Class<?>> SOLVER_CLASSES;
  /** Path to the physiology SBML files. **/
  private static Path sbmlPath;
  /** Model to use for this simulator. **/
  private final Model model;
  /** List of fields for the model. **/
  private final String[] modelFields;
  /** List of default values for the model. **/
  private final double[] modelDefaults;
  /** User-defined name of the solver to use. **/
  private final String solverName;
  /** Duration of the simulation in seconds. **/
  private final double simDuration;
  /** Size of each time step for the solver in seconds. **/
  private final double stepSize;

  /** Enumeration of supported chart types. **/
  public enum ChartType {
    /** Scatter plot. **/
    SCATTER,
    /** Line plot. **/
    LINE
  }

  /** POJO configuration for the simulation. **/
  public static class SimConfig {
    /** Name for the simulator. **/
    private String name;
    /** User-defined model to use. **/
    private String model;
    /** Differential equation solver instance. **/
    private String solver;
    /** Step size to use for the simulation in seconds. **/
    private double stepSize;
    /** Duration of the simulation in seconds. **/
    private double duration;
    /** List of chart export configurations. **/
    private List<ChartConfig> charts;
    /** Map of parameters to their provided input values. **/
    private Map<String, Double> inputs;

    public final Map<String, Double> getInputs() {
      return inputs;
    }

    public final void setInputs(Map<String, Double> inputs) {
      this.inputs = inputs;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }

    public String getSolver() {
      return solver;
    }

    public void setSolver(String solver) {
      this.solver = solver;
    }

    public double getStepSize() {
      return stepSize;
    }

    public void setStepSize(double stepSize) {
      this.stepSize = stepSize;
    }

    public double getDuration() {
      return duration;
    }

    public void setDuration(double duration) {
      this.duration = duration;
    }

    public List<ChartConfig> getCharts() {
      return charts;
    }

    public void setCharts(List<ChartConfig> charts) {
      this.charts = charts;
    }
    
  }

  /**
   * POJO configuration for a chart.
   **/
  public static class ChartConfig {
    /** Name of the image file to export. **/
    private String filename;
    /** User input for the type of chart to render. **/
    private String type;
    /** Chart title. **/
    private String title;
    /** Parameter to render on the x axis. **/
    private String axisParamX;
    /** X axis label. **/
    private String axisLabelX;
    /** Y axis label. **/
    private String axisLabelY;
    /** List of series configurations for this chart. **/
    private List<SeriesConfig> series;
    /** Simulation time in seconds to start charting points. **/
    private double startTime;
    /** Simulation time in seconds to end charting points. **/
    private double endTime;
    
    public String getFilename() {
      return filename;
    }
    
    public void setFilename(String filename) {
      this.filename = filename;
    }
    
    public String getType() {
      return type;
    }
    
    public void setType(String type) {
      this.type = type;
    }
    
    public String getTitle() {
      return title;
    }
    
    public void setTitle(String title) {
      this.title = title;
    }
    
    public String getAxisParamX() {
      return axisParamX;
    }
    
    public void setAxisParamX(String axisParamX) {
      this.axisParamX = axisParamX;
    }
    
    public String getAxisLabelX() {
      return axisLabelX;
    }
    
    public void setAxisLabelX(String axisLabelX) {
      this.axisLabelX = axisLabelX;
    }
    
    public String getAxisLabelY() {
      return axisLabelY;
    }
    
    public void setAxisLabelY(String axisLabelY) {
      this.axisLabelY = axisLabelY;
    }
    
    public List<SeriesConfig> getSeries() {
      return series;
    }
    
    public void setSeries(List<SeriesConfig> series) {
      this.series = series;
    }
    
    public double getStartTime() {
      return startTime;
    }
    
    public void setStartTime(double startTime) {
      this.startTime = startTime;
    }
    
    public double getEndTime() {
      return endTime;
    }
    
    public void setEndTime(double endTime) {
      this.endTime = endTime;
    }
  }

  /**
   * POJO configuration for a chart series.
   */
  public static class SeriesConfig {
    /** Which parameter to plot on this series. **/
    private String param;
    /** Series label in the legend. **/
    private String label;

    public String getParam() {
      return param;
    }

    public void setParam(String param) {
      this.param = param;
    }

    public String getLabel() {
      return label;
    }

    public void setLabel(String label) {
      this.label = label;
    }

  }

  static {
    // Initialize our static map of solvers
    Map<String, Class<?>> initSolvers = new HashMap<String, Class<?>>();

    // Add all currently available solvers from the SBSCL library
    initSolvers.put("adams_bashforth", AdamsBashforthSolver.class);
    initSolvers.put("adams_moulton", AdamsMoultonSolver.class);
    initSolvers.put("dormand_prince_54", DormandPrince54Solver.class);
    initSolvers.put("dormand_prince_853", DormandPrince853Solver.class);
    initSolvers.put("euler", EulerMethod.class);
    initSolvers.put("gragg_bulirsch_stoer", GraggBulirschStoerSolver.class);
    initSolvers.put("higham_hall_54", HighamHall54Solver.class);
    initSolvers.put("rosenbrock", RosenbrockSolver.class);
    initSolvers.put("runge_kutta", RungeKutta_EventSolver.class);

    // Make unmodifiable so it doesn't change after initialization
    SOLVER_CLASSES = Collections.unmodifiableMap(initSolvers);
    
    // Get the path to our physiology models directory containing SBML files
    URL physiologyFolder = ClassLoader.getSystemClassLoader().getResource("physiology");
    try {
      sbmlPath = Paths.get(physiologyFolder.toURI());
    } catch (URISyntaxException ex) {
      throw new RuntimeException(ex);
    }
  }
  
  /**
   * PhysiologySimulator constructor.
   * @param modelPath Path to the SBML file to load relative to resources/physiology
   * @param solverName Name of the solver to use
   * @param stepSize Time step for the simulation
   * @param simDuration Amount of time to simulate
   */
  public PhysiologySimulator(String modelPath, String solverName, double stepSize,
      double simDuration) {
    this(modelPath, solverName, stepSize, simDuration, 0);
  }

  /**
   * PhysiologySimulator constructor.
   * @param modelPath Path to the SBML file to load relative to resources/physiology
   * @param solverName Name of the solver to use
   * @param stepSize Time step for the simulation
   * @param simDuration Amount of time to simulate
   * @param leadTime Amount of time to run the simulation before capturing results
   */
  public PhysiologySimulator(String modelPath, String solverName, double stepSize,
      double simDuration, double leadTime) {
    Path modelFilepath = Paths.get(sbmlPath.toString(), modelPath);
    SBMLReader reader = new SBMLReader();
    File inputFile = new File(modelFilepath.toString());
    SBMLDocument doc;
    try {
      doc = reader.readSBML(inputFile);
    } catch (IOException | XMLStreamException ex) {
      throw new RuntimeException(ex);
    }
    model = doc.getModel();
    SBMLinterpreter interpreter = getInterpreter(model);
    modelFields = interpreter.getIdentifiers();
    modelDefaults = interpreter.getInitialValues();
    this.solverName = solverName;
    this.stepSize = stepSize;
    this.simDuration = simDuration;
  }
  
  /**
   * Returns a list of all model parameters.
   * @return list of model parameters
   */
  public List<String> getParameters() {
    return Arrays.asList(modelFields);
  }
  
  /**
   * Solves the model at each time step for the specified duration using the provided inputs
   * as initial parameters. Provides the results as a map of value lists where each key is
   * a model parameter. In addition to the model parameters is a "Time" field which provides
   * a list of all simulated time points.
   * 
   * <p>Note that this method will throw a DerivativeException if the model encounters an error
   * while attempting to solve the system.
   * @param inputs Map of model parameter inputs. For any parameters which are not provided
   *               the default value from the model will be used. If null, all default
   *               parameter values will be used.
   * @return map of parameter names to value lists
   * @throws DerivativeException Exception if the solver encounters errors while computing the
   *        solution to differential equations
   */
  public MultiTable run(Map<String, Double> inputs) throws DerivativeException {
    // Reset the solver to its initial state
    SBMLinterpreter interpreter = getInterpreter(model);
    AbstractDESSolver solver = getSolver(solverName);
    solver.setStepSize(stepSize);
    try {
      // Reinitialize the interpreter to prevent old values from affecting the new simulation
      interpreter.init(true);
    } catch (ModelOverdeterminedException | SBMLException ex) {
      // This shouldn't ever happen here since the interpreter has already been instantiated
      // at least once
      Logger.getLogger(PhysiologySimulator.class.getName()).log(Level.SEVERE,
          "Error reinitializing SBML interpreter", ex);
    }
    
    // Create a copy of the default parameters to use
    double[] params = Arrays.copyOf(modelDefaults, modelDefaults.length);

    // Overwrite model defaults with the provided input parameters, if present
    if (inputs != null) {
      for (int i = 0; i < modelFields.length; i++) {
        String field = modelFields[i];
        if (inputs.containsKey(field)) {
          params[i] = inputs.get(field);
        }
      }
    }
    
    // Solve the ODE for the specified duration and return the results
    MultiTable results = solver.solve(interpreter, params, 0, simDuration);
    
    return results;
  }

  /**
   * Checks whether a string is a valid solver name.
   * @param solverName solver name string to check
   * @return true if valid false otherwise.
   */
  public static boolean checkValidSolver(String solverName) {
    return SOLVER_CLASSES.containsKey(solverName);
  }

  /**
   * Gets the set of valid solver names.
   * @return set of valid solver name strings
   */
  public static Set<String> getSolvers() {
    return SOLVER_CLASSES.keySet();
  }

  /**
   * Retrieves the solver for the given solver name. If the provided string is
   * invalid, a RuntimeException will be thrown
   * @param solverName user-facing name of the solver to instantiate
   * @return solver instance
   */
  private static AbstractDESSolver getSolver(String solverName) {

    // If the provided solver name doesn't exist in our map, it's an invalid
    // value that the programmer needs to correct.
    if (!checkValidSolver(solverName)) {
      throw new RuntimeException("Invalid Solver: \"" + solverName + "\"");
    }

    // Attempt to instantiate the solver.
    try {
      return (AbstractDESSolver) SOLVER_CLASSES.get(solverName).newInstance();
    } catch (InstantiationException | IllegalAccessException ex) {
      throw new RuntimeException("Unable to instantiate " + solverName + " solver");
    }
  }
  
  /**
   * Retrieves the interpreter for a given SBML Model.
   * @param bioModel SBML model to interpret
   * @return interpreter instance
   */
  private static SBMLinterpreter getInterpreter(Model bioModel) {
    try {
      SBMLinterpreter interpreter = new SBMLinterpreter(bioModel);
      return interpreter;
    } catch (ModelOverdeterminedException | SBMLException ex) {
      // If there are problems with the model, we can't proceed
      throw new RuntimeException(ex);
    }
  }
  
  /**
   * Writes the contents of a MultiTable to a CSV file.
   * @param table MultiTable to write
   * @param outputPath path to the output CSV file
   */
  private static void multiTableToCsvFile(MultiTable table, Path outputPath) {
    PrintWriter writer;
    // Open our output file for writing
    try {
      writer = new PrintWriter(outputPath.toString(), "UTF-8");
    } catch (FileNotFoundException | UnsupportedEncodingException ex) {
      throw new RuntimeException("Unable to open output file:" + outputPath);
    }

    // Get the number of rows and columns
    int numRows = table.getRowCount();
    int numCols = table.getColumnCount();

    // Write the header line
    for (int colIdx = 0; colIdx < numCols; colIdx++) {
      writer.print(table.getColumnIdentifier(colIdx));
      if (colIdx < numCols - 1) {
        writer.print(",");
      }
    }
    writer.println();

    // Write each of the row values in sequence
    for (int rowIdx = 0; rowIdx < numRows; rowIdx++) {
      for (int colIdx = 0; colIdx < numCols; colIdx++) {
        writer.print(table.getValueAt(rowIdx, colIdx));
        if (colIdx < numCols - 1) {
          writer.print(",");
        }
      }
      writer.println();
    }
    writer.close();
  }
  
  /**
   * Draw a JFreeChart to an image based on values from a MultiTable.
   * @param table MultiTable to retrieve values from
   * @param config chart configuration options
   */
  private static void drawChart(MultiTable table, ChartConfig config) {
    
    // If there's only one series, and there's a title, hide the legend
    
    double lastTimePoint = table.getTimePoint(table.getRowCount() - 1);
    
    // Set the chart end time if not specified
    if (config.getEndTime() == 0) {
      config.setEndTime(lastTimePoint);
    }
    
    // Check that the start time is valid
    if (config.getStartTime() < 0) {
      throw new IllegalArgumentException("Chart start time must not be negative");
    }
    
    // Check the chart end time is valid
    if (config.getEndTime() > lastTimePoint) {
      throw new IllegalArgumentException("Invalid chart end time: " + config.getEndTime()
          + " is greater than final time point " + lastTimePoint);
    }
    
    // Check the time range is valid
    if (config.getStartTime() > config.getEndTime()) {
      throw new IllegalArgumentException("Invalid chart range: " + config.getStartTime()
          + " to " + config.getEndTime());
    }
    
    // Get the list of x values. Time is treated specially since it doesn't have a param identifier
    boolean axisXIsTime = "time".equalsIgnoreCase(config.getAxisParamX());
    List<Double> valuesX = new ArrayList<Double>(table.getRowCount());
    double[] timePoints = table.getTimePoints();
    Column colX = table.getColumn(config.getAxisParamX());
    
    // Check that the x axis identifier is valid
    if (!axisXIsTime && colX == null) {
      throw new RuntimeException("Invalid X axis identifier: " + config.getAxisParamX());
    }
    
    int startIndex = Arrays.binarySearch(timePoints, config.getStartTime());
    int endIndex = Arrays.binarySearch(timePoints, config.getEndTime());
    
    // Add the table values to the list of x axis values within the provided time range
    for (int i = startIndex; i < endIndex; i++) {
      if (axisXIsTime) {
        valuesX.add(timePoints[i]);
      } else {
        valuesX.add(colX.getValue(i));
      }
    }
    
    XYSeriesCollection dataset = new XYSeriesCollection();

    // Add each series to the dataset
    for (SeriesConfig seriesConfig : config.getSeries()) {
      // don't auto-sort the series
      XYSeries series = new XYSeries(seriesConfig.getLabel(), false);

      Column col = table.getColumn(seriesConfig.getParam());
      
      // Check that the series identifier is valid
      if (col == null) {
        throw new RuntimeException("Invalid series identifier: " + seriesConfig.getParam());
      }

      int indexX = 0;
      for (int i = startIndex; i < endIndex; i++) {
        series.add((double) valuesX.get(indexX++), col.getValue(i));
      }

      dataset.addSeries(series);
    }
    
    // Instantiate our renderer to draw the chart
    XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
    JFreeChart chart;
    
    // Determine the appropriate Chart from the configuration options
    switch (ChartType.valueOf(config.getType().toUpperCase())) {
      default:
      case LINE:
        chart = ChartFactory.createXYLineChart(
            config.getTitle(), 
            config.getAxisLabelX(), 
            config.getAxisLabelY(), 
            dataset, 
            PlotOrientation.VERTICAL,
            true, 
            true, 
            false 
        );
        for (int i = 0; i < dataset.getSeriesCount(); i++) {
          renderer.setSeriesShapesVisible(i, false);
        }
        break;
      case SCATTER:
        chart = ChartFactory.createScatterPlot(
            config.getTitle(), 
            config.getAxisLabelX(),
            config.getAxisLabelY(),
            dataset
        );
        break;
    }
    
    // If there's only one series, and there's a chart title, the legend is unnecessary
    if (config.getTitle() != null && !config.getTitle().isEmpty()
        && config.getSeries().size() == 1) {
      chart.removeLegend();
    } else {
      chart.getLegend().setFrame(BlockBorder.NONE);
    }
    
    // Instantiate the plot and set some reasonable styles
    // TODO eventually we can make these more configurable if desired
    XYPlot plot = chart.getXYPlot();
    
    plot.setRenderer(renderer);
    plot.setBackgroundPaint(Color.white);
    plot.setRangeGridlinesVisible(true);
    plot.setDomainGridlinesVisible(true);
    
    // Save the chart as a PNG image to the file system
    try {
      ChartUtils.saveChartAsPNG(new File(config.getFilename()), chart, 600, 300);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Executes a physiology simulation according to a given configuration file.
   * @param args command line arguments
   * @throws DerivativeException Error while solving differential equations
   */
  public static void main(String [] args) throws DerivativeException {

    if (args.length < 1 || args[0].isEmpty()) {
      System.out.println("YAML simulation configuration file path must be provided.");
      System.exit(1);
      return;
    }
    
    // Open the config file
    Path configFilePath = Paths.get(args[0]);
    
    File configFile = new File(configFilePath.toString());
    FileInputStream inputStream;
    
    // Try to open the configuration file as an input stream
    try {
      inputStream = new FileInputStream(configFile);
    } catch (FileNotFoundException ex) {
      System.out.println("Configuration file not found: \""
          + configFilePath.toAbsolutePath() + "\".");
      System.exit(2);
      return;
    }
    
    // Add type descriptions so Yaml knows how to instantiate our Lists
    Constructor constructor = new Constructor(SimConfig.class);
    TypeDescription simConfigDescription = new TypeDescription(SimConfig.class);
    simConfigDescription.addPropertyParameters("charts", ChartConfig.class);
    constructor.addTypeDescription(simConfigDescription);
    TypeDescription chartConfigDescription = new TypeDescription(ChartConfig.class);
    chartConfigDescription.addPropertyParameters("series", SeriesConfig.class);
    constructor.addTypeDescription(chartConfigDescription);
    
    // Parse the SimConfig from the yaml file
    Yaml yaml = new Yaml(constructor);
    SimConfig config = (SimConfig) yaml.load(inputStream);
    
    // Instantiate our simulator
    PhysiologySimulator simulator = new PhysiologySimulator(
        config.getModel(),
        config.getSolver(),
        config.getStepSize(),
        config.getDuration()
    );
    
    // Create the output directory if it doesn't already exist
    Path outputDir = Paths.get("output", config.getName());
    if (Files.notExists(outputDir)) {
      try {
        Files.createDirectories(outputDir);
      } catch (IOException ex) {
        System.out.println("Unable to write output directory. Check user permissions.");
      }
    }
    
    try {
      
      // Run with all default parameters
      MultiTable results = simulator.run(config.getInputs());
      
      // Write CSV data file
      multiTableToCsvFile(results, Paths.get(outputDir.toString(), config.getName() + ".csv"));
      
      // Draw all of the configured charts
      if (config.getCharts() != null) {
        int chartId = 1;
        for (ChartConfig chartConfig : config.getCharts()) {
          if (chartConfig.getFilename() == null || chartConfig.getFilename().isEmpty()) {
            chartConfig.setFilename("chart" + chartId + ".png");
          }
          chartConfig.setFilename(Paths.get(outputDir.toString(),
              chartConfig.getFilename()).toString());
          PhysiologySimulator.drawChart(results, chartConfig);
        }
      }

    } catch (DerivativeException ex) {
      throw new RuntimeException(ex);
    }
  }
}
