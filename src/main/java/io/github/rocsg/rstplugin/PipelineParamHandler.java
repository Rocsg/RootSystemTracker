package io.github.rocsg.rstplugin;

import ij.IJ;
import ij.ImageJ;
import io.github.rocsg.fijiyama.common.VitimageUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;

public class PipelineParamHandler {
    // Constants for no parameter values
    final static int NO_PARAM_INT = -999999999;
    final static double NO_PARAM_DOUBLE = -99999999;
    // Name of the main CSV file
    public final String mainNameCsv = "InfoSerieRootSystemTracker.csv";
    // Number of plants in the box
    public int numberPlantsInBox = 5;
    // Size factor for graph rendering
    public int sizeFactorForGraphRendering = 6;
    // Memory saving mode, if 1, don't save very big debug images
    public int memorySaving = 0;
    // Subsampling factor
    public int subsamplingFactor = 1;
    // Tolerance distance for Beucker simplification
    public double toleranceDistanceForBeuckerSimplification = 0.9;
    // Times of the images
    public String[] imgTimes;
    // Sizes of the image series
    public int[] imgSerieSize;
    // Original pixel size in µm
    public double originalPixelSize = 19;
    // Typical hour delay
    public double typicalHourDelay = 8;
    // Minimum and maximum tree in x direction
    public int xMinTree = 90;
    public int xMaxTree = 1220;
    // Version of the current pipeline
    String currentVersion = "1.0";
    // Path to the parameter file
    String pathToParameterFile = "";
    // Time step for the movie in hours per keyframe
    double movieTimeStep = 1;
    // Directory of the inventory
    String inventoryDir = "";
    // Directory of the output
    String outputDir = "";
    // Maximum number of images
    int MAX_NUMBER_IMAGES = 100000;
    // Maximum number of parameters
    int nMaxParams = 100 + MAX_NUMBER_IMAGES;
    // Number of parameters
    int nParams = 0;
    // Number of data
    int nbData = 1;
    // Minimum size of connected components
    int minSizeCC = 5;
    // Intensity level of the root tissue
    double rootTissueIntensityLevel = 30;
    // Intensity level of the background
    double backgroundIntensityLevel = 130;
    // Maximum speed of lateral movement, defined as the number of pixels per
    // typical timestep
    double maxSpeedLateral = 33;
    // Mean speed of lateral movement, defined as the number of pixels per typical
    // timestep
    double meanSpeedLateral = 10;
    // Typical speed in pixels/hour
    double typicalSpeed = 12;
    // Penalty cost
    double penaltyCost = 0.5;
    // Number of Median Absolute Deviations for outlier rejection
    double nbMADforOutlierRejection = 25;
    // Minimum distance between lateral initiation
    double minDistanceBetweenLateralInitiation = 4;
    // Minimum lateral stucked to other lateral
    double minLateralStuckedToOtherLateral = 30;
    // Crop parameters
    /*int xMinCrop = 1;
    int yMinCrop = 1;
    int dxCrop = 430;
    int dyCrop = 500;*/
    int xMinCrop=122;
    int yMinCrop=212;
    int dxCrop=1348;
    int dyCrop=1166;
    // Maximum linear
    int maxLinear = 4;
    // Type of experiment
    String typeExp = "Simple";
    // Margin for registration
    int marginRegisterLeft = 20;
    int marginRegisterUp = 0;
    int marginRegisterRight = 20;
    // Flag to apply full pipeline image after image
    boolean applyFullPipelineImageAfterImage = true;
    // Names of the images
    String[] imgNames;
    // Steps of the images
    int[] imgSteps;
    // Array of parameters
    private String[][] params;
    // Acquisition times
    private double[][] acqTimes;
    // Unit of measurement
    private String unit = "µm";

    // Default constructor
    public PipelineParamHandler() {
    }

    //// Constructors

    /**
     * Constructor for the PipelineParamHandler class.
     *
     * @param inventoryDir The directory where the inventory files are located.
     * @param outputDir    The directory where the output files will be written.
     */

    



    public PipelineParamHandler(String inventoryDir, String outputDir) {
        // Check if a "NOT_FOUND.csv" file exists in the inventory directory
        if (new File(inventoryDir, "NOT_FOUND.csv").exists()) {
            // If it does, display a warning message and run the cleaning assistant
            IJ.showMessage("Warning. Found a NOT_FOUND.csv in dir " + inventoryDir
                    + " . The cleaning assistant will open now.");
            runCleaningAssistant(inventoryDir);
        }

        // Normalize the directory paths by replacing backslashes with forward slashes
        this.inventoryDir = inventoryDir.replace("\\", "/");
        this.outputDir = outputDir.replace("\\", "/");

        // Construct the path to the parameter file
        this.pathToParameterFile = new File(outputDir, mainNameCsv).getAbsolutePath().replace("\\", "/");

        // Check if the parameter file exists
        if (new File(this.pathToParameterFile).exists()) {
            // If it does, read the parameters from the file
            readParameters();
        } else {
            // If it doesn't, propose default parameters and give the user the opportunity
            // to edit them
            getParametersForNewExperiment();
            writeParameters(true);
            IJ.showMessage("Please edit parameters if needed, save file, then click ok.\n File="
                    + this.pathToParameterFile);
            readParameters();

            // Create a new directory for each image
            for (String imgName : getImgNames())
                new File(outputDir, VitimageUtils.withoutExtension(imgName)).mkdirs();
        }
    }

    /**
     * Constructor for the PipelineParamHandler class.
     *
     * @param processingDir The directory where the output files will be written.
     */
    public PipelineParamHandler(String processingDir) {
        this.outputDir = processingDir.replace("\\", "/");
        readParameters();
    }

    // Main method
    public static void main(String[] arg) {
    }

    //// Methods

    /**
     * Method to run the cleaning assistant
     *
     * @param inventoryDir The inventory directory path
     */
    public void runCleaningAssistant(String inventoryDir) {
        String[][] dataClean = VitimageUtils
                .readStringTabFromCsv(new File(inventoryDir, "NOT_FOUND.csv").getAbsolutePath());
        IJ.showMessage("Please open an explorer window to help the cleaning");

    }

    public boolean isSplit() {
        return typeExp.contains("Split_V01");
    }


    public boolean isGaps() {
        
        return typeExp.contains("HaveNoSurface_");

    }


    public String[] getImgNames() {
        return imgNames;
    }

    /**
     * Function to read parameters and extract information from the csv file
     */
    public void readParameters() {
        // Print the absolute path of the main CSV file
        System.out.println(new File(outputDir, mainNameCsv).getAbsolutePath());

        IJ.log(outputDir);
        // Read the CSV file into a string array
        params = VitimageUtils
                .readStringTabFromCsv(new File(outputDir, mainNameCsv).getAbsolutePath().replace("\\", "/"));

        // Log the name of the opened CSV file
        IJ.log("The main CSV is opened with name : |"
                + new File(outputDir, mainNameCsv).getAbsolutePath().replace("\\", "/") + "|");

        // Read parameters from the CSV file
        inventoryDir = getString("inventoryDir");
        xMinCrop = getInt("xMinCrop");
        yMinCrop = getInt("yMinCrop");
        dxCrop = getInt("dxCrop");
        dyCrop = getInt("dyCrop");
        typeExp = getString("typeExp");
        outputDir = getString("outputDir");
        movieTimeStep = getDouble("movieTimeStep");
        numberPlantsInBox = getInt("numberPlantsInBox");
        minSizeCC = getInt("minSizeCC");
        originalPixelSize = getDouble("originalPixelSize");
        unit = getString("unit");
        sizeFactorForGraphRendering = getInt("sizeFactorForGraphRendering");
        rootTissueIntensityLevel = getDouble("rootTissueIntensityLevel");
        backgroundIntensityLevel = getDouble("backgroundIntensityLevel");
        minDistanceBetweenLateralInitiation = getDouble("minDistanceBetweenLateralInitiation");
        minLateralStuckedToOtherLateral = getDouble("minLateralStuckedToOtherLateral");
        maxSpeedLateral = getDouble("maxSpeedLateral");
        meanSpeedLateral = getDouble("meanSpeedLateral");
        typicalSpeed = getDouble("typicalSpeed");
        penaltyCost = getDouble("penaltyCost");
        nbMADforOutlierRejection = getDouble("nbMADforOutlierRejection");
        subsamplingFactor = getInt("subsamplingFactor");
        nbData = getInt("nbData");
        typicalHourDelay = getDouble("typicalHourDelay");

        // Initialize arrays to hold image data
        imgNames = new String[nbData];
        imgSteps = new int[nbData];
        acqTimes = new double[nbData][0];
        imgSerieSize = new int[nbData];

        // Loop over each image
        for (int i = 0; i < nbData; i++) {
            // Read image-specific parameters from the CSV file
            imgNames[i] = getString("Img_" + i + "_name");
            imgSteps[i] = getInt("Img_" + i + "_step");
            IJ.log("We have inventoryDir=" + inventoryDir);
            IJ.log("We have outputDir=" + outputDir);
            System.out.println("And making inventory of |"
                    + new File(inventoryDir, imgNames[i] + ".csv").getAbsolutePath().replace("\\", "/") + "|");
            IJ.log("Did inventory of "
                    + (new File(inventoryDir, imgNames[i] + ".csv").getAbsolutePath().replace("\\", "/")));
            IJ.log("Testing " + (new File(inventoryDir, imgNames[i] + ".csv").getAbsolutePath().replace("\\", "/")));
            String[][] paramsImg = VitimageUtils.readStringTabFromCsv(
                    new File(inventoryDir, imgNames[i] + ".csv").getAbsolutePath().replace("\\", "/"));
            IJ.log("And the String tab initialized is null ? " + (paramsImg == null));
            IJ.log("Or it has a number of lines = " + (paramsImg.length));
            IJ.log("Or imgSerieSize is null ? " + (imgSerieSize == null));
            IJ.log("Or imgSerieSize len is not good ? = " + (imgSerieSize.length));
            imgSerieSize[i] = paramsImg.length - 1;
            acqTimes[i] = new double[imgSerieSize[i]];
            for (int j = 0; j < imgSerieSize[i]; j++) {
                acqTimes[i][j] = Double.parseDouble(paramsImg[j + 1][2]);
            }
        }
        // Calculate the typical delay between image acquisitions
        int ind = 0;
        double sum = 0;
        for (int i = 0; i < nbData; i++) {
            for (int j = 1; j < imgSerieSize[i]; j++) {
                sum += acqTimes[i][j] - acqTimes[i][j - 1];
                ind++;
            }
        }
        this.typicalHourDelay = sum / ind;
    }

    ///// Additions

    public void addParam(String tit, String val, String info) {
        params[nParams++] = new String[]{tit, val, info};
    }

    public void addParam(String tit, double val, String info) {
        params[nParams++] = new String[]{tit, "" + val, info};
    }

    public void addParam(String tit, int val, String info) {
        params[nParams++] = new String[]{tit, "" + val, info};
    }

    /**
     * This method is used to add all parameters to the params array.
     */
    public void addAllParametersToTab() {
        // Initialize the params array with a specific size
        params = new String[40 + 2 * nbData][3];
        nParams = 0;

        // Add a header to the params array
        addParam("## Parameters for RootSystemTracker experiment ##", "", "");

        // Add various parameters to the params array
        addParam("inventoryDir", inventoryDir, "");
        addParam("outputDir", outputDir, "");

        addParam("nbData", nbData, "");
        addParam("numberPlantsInBox", numberPlantsInBox, "");
        addParam("minSizeCC", minSizeCC, "");
        addParam("sizeFactorForGraphRendering", sizeFactorForGraphRendering, "");

        addParam("rootTissueIntensityLevel", rootTissueIntensityLevel, "");
        addParam("backgroundIntensityLevel", backgroundIntensityLevel, "");
        addParam("minDistanceBetweenLateralInitiation", minDistanceBetweenLateralInitiation, "");
        addParam("minLateralStuckedToOtherLateral", minLateralStuckedToOtherLateral, "");
        addParam("maxSpeedLateral", maxSpeedLateral, "");
        addParam("meanSpeedLateral", meanSpeedLateral, "");
        addParam("typicalSpeed", typicalSpeed, "");
        addParam("penaltyCost", penaltyCost, "");
        addParam("typicalSpeed", typicalSpeed, "");
        addParam("nbMADforOutlierRejection", nbMADforOutlierRejection, "");
        addParam("xMinCrop", xMinCrop, "");
        addParam("yMinCrop", yMinCrop, "");
        addParam("dxCrop", dxCrop, "");
        addParam("dyCrop", dyCrop, "");

        // Add parameters with descriptions
        addParam("maxLinear", maxLinear, "Used to define the max level");
        addParam("subsamplingFactor", subsamplingFactor, "");
        addParam("originalPixelSize", originalPixelSize, "");
        addParam("unit", unit, "");
        addParam("typicalHourDelay", typicalHourDelay, "");

        // Add parameters with default values
        addParam("typeExp", typeExp, "-");
        addParam("movieTimeStep", movieTimeStep, "-");
    }

    //// Getters and Setters

    /**
     * Method to set parameters from a file
     *
     * @param parametersFile The path for the parameters file
     */
    public void setParameters(String parametersFile) {
        this.pathToParameterFile = parametersFile;
        this.outputDir = new File(parametersFile).getParent();
        readParameters();
    }

    public void setAcqTimesForTest(double[][] tab) {
        this.acqTimes = tab;
    }

    public void getParametersForNewExperiment() {
        System.out.println(inventoryDir);
        System.out.println(new File(inventoryDir).exists());
        for (String s : new File(inventoryDir).list())
            System.out.println(s);
        nbData = new File(inventoryDir).list().length - 1;
        if (nbData > MAX_NUMBER_IMAGES) {
            IJ.showMessage("Critical warning : number of images is too high : " + nbData + " > " + MAX_NUMBER_IMAGES);
        }
    }

    public double[] getHoursExtremities(int indexBox) {
        double[] ret = new double[acqTimes[indexBox].length + 1];
        if (acqTimes[indexBox].length - 1 >= 0)
            System.arraycopy(acqTimes[indexBox], 1, ret, 2, acqTimes[indexBox].length - 1);
        ret[0] = ret[1] - this.typicalHourDelay;
        return ret;
    }

    public double[] getHours(int indexBox) {
        double[] ret = new double[acqTimes[indexBox].length + 1];
        for (int i = 1; i < acqTimes[indexBox].length; i++) {
            ret[i + 1] = acqTimes[indexBox][i] * 0.5 + acqTimes[indexBox][i - 1] * 0.5;
        }
        double delta0 = ret[3] - ret[2];
        ret[1] = ret[2] - delta0;
        ret[0] = ret[1] - delta0;
        return ret;
    }

    public double getMaxSpeedLateral() {
        return getDouble("maxSpeedLateral");
    }

    public double getMinDistanceBetweenLateralInitiation() {
        return getDouble("minDistanceBetweenLateralInitiation");
    }

    public double getMinLateralStuckedToOtherLateral() {
        return getDouble("minLateralStuckedToOtherLateral");
    }

    public double getMeanSpeedLateral() {
        return getDouble("meanSpeedLateral");
    }

    public double getTypicalSpeed() {
        return getDouble("typicalSpeed");
    }       

    public double getMovieTimeStep() {
        double d = getDouble("movieTimeStep");
        if (d < 0)
            return 1;
        return d;
    }

    public void writeParameters(boolean firstWrite) {

        addAllParametersToTab();

        if (firstWrite) {
            imgNames = new String[nbData];
            imgSteps = new int[nbData];
            imgTimes = new String[nbData];
            imgSerieSize = new int[nbData];
            String[] listImgs = new File(inventoryDir).list(new FilenameFilter() {
                @Override
                public boolean accept(File arg0, String arg1) {
                    if (arg1.equals("A_main_inventory.csv"))
                        return false;
                    return !arg1.equals("NOT_FOUND.csv");
                }
            });
            Arrays.sort(listImgs);

            for (int i = 0; i < nbData; i++) {
                imgNames[i] = listImgs[i].replace(".csv", "").replace("\\", "/");
                imgSteps[i] = 0;
            }
        }
        for (int i = 0; i < nbData; i++) {
            addParam("Img_" + i + "_name", imgNames[i], "");
            addParam("Img_" + i + "_step", imgSteps[i], "");
        }
        VitimageUtils.writeStringTabInCsv2(params,
                new File(outputDir, mainNameCsv).getAbsolutePath().replace("\\", "/"));
    }

    public String getString(String tit) {
        for (int i = 0; i < params.length; i++)
            if (params[i][0].equals(tit))
                return params[i][1].replace("\\", "/");
        // IJ.showMessage("Parameter not found : "+tit+" in param file of "+outputDir);
        return "";
    }

    public double getDouble(String tit) {
        for (int i = 0; i < params.length; i++)
            if (params[i][0].equals(tit))
                return Double.parseDouble(params[i][1]);
        IJ.showMessage("Parameter not found : " + tit + " in param file of " + outputDir);
        return NO_PARAM_DOUBLE;
    }

    public int getInt(String tit) {
        for (int i = 0; i < params.length; i++)
            if (params[i][0].equals(tit))
                return Integer.parseInt(params[i][1]);
        IJ.showMessage("Parameter not found : " + tit + " in param file of " + outputDir);
        return NO_PARAM_INT;
    }

}
