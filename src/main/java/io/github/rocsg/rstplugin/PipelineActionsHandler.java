package io.github.rocsg.rstplugin;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.Duplicator;
import ij.plugin.RGBStackMerge;
import ij.process.ImageProcessor;
import io.github.rocsg.fijiyama.common.Timer;
import io.github.rocsg.fijiyama.common.*;
import io.github.rocsg.fijiyama.fijiyamaplugin.RegistrationAction;
import io.github.rocsg.fijiyama.registration.BlockMatchingRegistration;
import io.github.rocsg.fijiyama.registration.ItkTransform;
import io.github.rocsg.fijiyama.registration.Transform3DType;
import io.github.rocsg.rsml.Node;
import io.github.rocsg.rsml.Root;
import io.github.rocsg.rsml.RootModel;
import io.github.rocsg.rstutils.MorphoUtils;
import io.github.rocsg.topologicaltracking.CC;
import io.github.rocsg.topologicaltracking.ConnectionEdge;
import io.github.rocsg.topologicaltracking.RegionAdjacencyGraphPipeline;
import org.apache.commons.io.FileUtils;
import org.itk.simple.SmoothingRecursiveGaussianImageFilter;
import org.jgrapht.GraphPath;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static io.github.rocsg.rstplugin.PipelineParamHandler.configurePipelineParams;


public class PipelineActionsHandler {
    // Flag indicating the pipeline has finished processing
    public static final int flagFinished = 8;
    // Flag indicating the last image in the pipeline
    public static final int flagLastImage = 200;
    // Boolean flag to determine if the pipeline should proceed image after image
    public static final boolean proceedFullPipelineImageAfterImage = true;
    // The first step to be executed in the pipeline
    public static final int firstStepToDo = 0;
    // The last step to be executed in the pipeline
    public static final int lastStepToDo = flagFinished;
    // The first image to be processed in the pipeline
    public static final int firstImageToDo = 0;
    // The last image to be processed in the pipeline
    public static final int lastImageToDo = flagLastImage; // flagFinished;
    // The maximum Y-coordinate for the stamp, relative to the crop
    public static final int yMaxStamp = 50; // TODO. It is relative value Y, after the crop
    private static final boolean needUpscale = false;
    private static final String inputFolder = "D:\\loaiu\\MAM5\\Stage\\data\\Test\\Input\\";
    private static final String outputFolder = "D:\\loaiu\\MAM5\\Stage\\data\\Test\\Output\\";
    private static final String outputFolderProcess = "D:\\loaiu\\MAM5\\Stage\\data\\Test\\Output\\Process\\";
    private static final String inventoryFolder = "D:\\loaiu\\MAM5\\Stage\\data\\Test\\Output\\Inventory\\";
    // Timer object for tracking time-related operations in the pipeline
    public static Timer t;

    public static void main(String[] args) {
        // SimpleITK test
        testSimpleITK();

        // Parse configuration from arguments
        Map<String, String> config = parseArgs(args);

        // Initialize input and output paths
        String inputFolderPath = config.getOrDefault("input", inputFolder);
        String outputFolderPath = config.getOrDefault("output", outputFolder);
        String outputFolderPath0 = config.getOrDefault("processOutput", outputFolderProcess);
        String inventoryFolderPath = config.getOrDefault("inventoryOutput", inventoryFolder);
        // Create output directories if they don't exist
        createOutputDirectory(outputFolderPath);
        createOutputDirectory(outputFolderPath0);
        createOutputDirectory(inventoryFolderPath);

        // Inventory step
        if (config.containsKey("inventoryOutput")) {
            Plugin_RootDatasetMakeInventory.startInventoryOfAlreadyTidyDir(inputFolderPath, inventoryFolderPath, config);
        }

        // Process step
        configurePipelineParams(config);
        PipelineParamHandler pph = new PipelineParamHandler(inventoryFolderPath, outputFolderPath0);

        // imagej
        IJ.log("Starting the pipeline");
        ImageJ ij = new ImageJ();

        t = new Timer();
        for (int i = 1; i < 9; i++) {
            pph.imgSteps[0] = i;
            doStepOnImg(i, 0, pph);
        }

        System.exit(0);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> config = new HashMap<>();
        for (String arg : args) {
            String[] splitArg = arg.split("=", 2);
            if (splitArg.length == 2) {
                config.put(splitArg[0].toLowerCase(), splitArg[1]);
            }
        }
        if (config.isEmpty()) {
            config.put("input", inputFolder);
            config.put("output", outputFolder);
            config.put("processOutput", outputFolderProcess);
            config.put("inventoryOutput", inventoryFolder);
        }
        return config;
    }

    private static void createOutputDirectory(String path) {
        File outputFolder = new File(path);
        if (!outputFolder.exists()) {
            outputFolder.mkdir();
        }
        if (Objects.requireNonNull(outputFolder.list()).length > 0) {
            try {
                Files.walkFileTree(outputFolder.toPath(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
                outputFolder.mkdir();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Testing if the SimpleITK library is loaded correctly
     */
    private static void testSimpleITK() {
        try {
            // test loading import org.itk.simple.*;
            SmoothingRecursiveGaussianImageFilter itkGauss = new SmoothingRecursiveGaussianImageFilter();
            itkGauss.delete();
        } catch (UnsatisfiedLinkError e) {
            System.err.println("La bibliothèque SimpleITKJava n'est pas chargée");
            System.err.println(e);
            System.exit(0);
        }
    }

    /**
     * This function allows the user to select the first and last steps and the way
     * to process boxes/images to process in a pipeline.
     * It provides an option to process everything box by box or to refine the
     * selection.
     *
     * @param pph The PipelineParamHandler object that contains the number of data
     *            images to be processed.
     * @return An array of integers representing the first and last steps and boxes
     * to be processed, and the order of processing.
     */
    public static int[] selectFirstAndLast(PipelineParamHandler pph) {
        // Prompt message for processing everything box by box
        final String PROCESS_EVERYTHING_PROMPT = "Process everything box after box (select no to refine)?";
        // Array of actions representing the steps in the pipeline
        final String[] ACTIONS = {"Step 0: setup part 1", "Step 1:image stacking", "Step 2: stack registration",
                "Step 3 : mask computation, leaves removal", "Step 4: spatio-temporal segmentation",
                "Step 5 : graph computation", "Step 6: RSML building until expertize",
                "Step 7: RSML building after expertize", "Step 8: Movie building"};
        // Array of orders for processing the steps
        final String[] ORDER = {"Box after box", "Step after step"};

        // If the user chooses to process everything box by box, return the
        // corresponding indices
        if (VitiDialogs.getYesNoUI(PROCESS_EVERYTHING_PROMPT, PROCESS_EVERYTHING_PROMPT))
            return new int[]{0, flagFinished, 0, pph.nbData - 1, 0};

        // Create a dialog for the user to refine their selection
        GenericDialog gd = new GenericDialog("Expert mode for RootSystemTracker");
        gd.addMessage("Choose the steps to execute");
        gd.addChoice("First step to run", ACTIONS, ACTIONS[0]);
        gd.addChoice("Last step to run", ACTIONS, ACTIONS[0]);
        gd.addMessage("Choose the indices of box to be processed (from 0 to " + (pph.nbData - 1) + ")");
        gd.addNumericField("First box index to process", 0, 0, 6, "");
        gd.addNumericField("Last box index to process", pph.nbData - 1, 0, 6, "");
        gd.addMessage("Choose the order : box after box (all steps) or step after step (all boxes)");
        gd.addChoice("Order", ORDER, ORDER[0]);
        gd.showDialog();

        // If the dialog was canceled, return the default indices
        if (gd.wasCanceled())
            return new int[]{0, flagFinished, 0, flagLastImage, 0};

        // Get the user's selections from the dialog
        int st1 = Math.max(0, gd.getNextChoiceIndex());
        int st2 = Math.max(0, gd.getNextChoiceIndex());
        int im1 = Math.max(0, (int) Math.round(gd.getNextNumber()));
        int im2 = Math.max(0, (int) Math.round(gd.getNextNumber()));
        int ord = gd.getNextChoiceIndex();

        // Ensure the first step/box is not greater than the last step/box
        st1 = Math.min(Math.min(st1, st2), flagFinished);
        im1 = Math.min(Math.min(im1, im2), flagLastImage);

        // Return the user's selections
        return new int[]{st1, st2, im1, im2, ord};
    }

    /**
     * Intermediary function of the pipeline
     * This function manages the execution of an image processing experiment.
     * It determines the range of images and steps to process, checks if any steps
     * need to be recomputed,
     * and then processes the images in either image order or step order based on
     * the provided parameters.
     *
     * @param pph A PipelineParamHandler object that contains the parameters for the
     *            image processing pipeline.
     */
    public static void goOnExperiment(PipelineParamHandler pph) {
        // Print a message to the console indicating the start of the process
        System.out.println("Going on  !");

        // Call the selectFirstAndLast method to get the first and last steps and images
        // to be processed, and the order of processing
        int[] vals = selectFirstAndLast(pph);
        int indFirstImageToDo = vals[2];
        int indLastImageToDo = vals[3];
        int indFirstStepToDo = vals[0];
        int indLastStepToDo = vals[1];
        int order = vals[4];

        // Display a message with the parameters of the processing
        IJ.showMessage("Params of processing=imgs " + indFirstImageToDo + "-" + indLastImageToDo + ", Steps "
                + indFirstStepToDo + "-" + indLastStepToDo + ", order " + order);

        // Initialize a new Timer object
        t = new Timer();

        // Initialize a flag to check if a rewrite is needed
        boolean rewriteNeeded = false;

        // Check if any images need to be reprocessed
        for (int in = indFirstImageToDo; in <= indLastImageToDo; in++)
            if (pph.imgSteps[in] > indFirstStepToDo) {
                rewriteNeeded = true;
                break;
            }

        // If a rewrite is needed, ask the user if they want to recompute all steps from
        // the first step or only compute missing steps
        if (rewriteNeeded) {
            if (VitiDialogs.getYesNoUI("Rewriting current step ?",
                    "Some box to process are more advanced than step " + indFirstStepToDo
                            + ".\n Yes: recompute all the steps from step " + indFirstStepToDo
                            + "  or   No: only compute missing steps")) {
                for (int in = indFirstImageToDo; in <= indLastImageToDo; in++)
                    if (pph.imgSteps[in] > indFirstStepToDo)
                        pph.imgSteps[in] = indFirstStepToDo;
                pph.writeParameters(false);
            }
        }

        // Depending on the order, process each image until all steps are done (order =
        // 0), or process each step on all images before moving to the next step (order
        // != 0)
        if (order == 0) {
            for (int i = indFirstImageToDo; i <= Math.min(indLastImageToDo, pph.nbData - 1); i++) {
                while (((pph.imgSteps[i] + 1) >= indFirstStepToDo && pph.imgSteps[i] <= indLastStepToDo)) {
                    doNextStep(i, pph);
                }
            }
        } else {
            for (int s = indFirstStepToDo; s <= indLastStepToDo; s++) {
                for (int i = indFirstImageToDo; i <= Math.min(indLastImageToDo, pph.nbData - 1); i++) {
                    if (pph.imgSteps[i] == (s - 1))
                        doNextStep(i, pph);
                }
            }
        }

        // Log a message indicating the end of the processing
        IJ.log("Processing finished !");
    }

    /**
     * This function performs the next step of image processing on the image at the
     * given index.
     * If the image name contains a specific code and the step to do is greater than
     * 1, it increments the image step.
     * Otherwise, it performs the step on the image and if successful, increments
     * the image step.
     * Finally, it writes the updated parameters.
     *
     * @param indexImg The index of the image to process.
     * @param pph      A PipelineParamHandler object that contains the parameters
     *                 for the image processing pipeline.
     */
    public static void doNextStep(int indexImg, PipelineParamHandler pph) {
        // Print a message to the console indicating the start of the next step for the
        // given image index
        System.out.println("Doing next step of img index " + indexImg);

        // Get the next step to do for the image at the given index
        int stepToDo = pph.imgSteps[indexImg];

        // If the image name contains a specific code and the step to do is greater than
        // 1, increment the image step
        if (pph.imgNames[indexImg].contains(Plugin_RootDatasetMakeInventory.codeTrash) && (stepToDo > 1))
            pph.imgSteps[indexImg]++;

            // Otherwise, perform the step on the image and if successful, increment the
            // image step
        else if (doStepOnImg(stepToDo, indexImg, pph))
            pph.imgSteps[indexImg]++;

        // Write the updated parameters
        pph.writeParameters(false);
    }

    /**
     * This function redirects to a specific step of image processing on the image
     * at
     * the given index.
     * The step to be performed is determined by the 'step' parameter.
     * The function returns a boolean indicating whether the step was executed
     * successfully.
     *
     * @param step     The step to be performed.
     * @param indexImg The index of the image to process.
     * @param pph      A PipelineParamHandler object that contains the parameters
     *                 for the image processing pipeline.
     * @return A boolean indicating whether the step was executed successfully.
     */
    public static boolean doStepOnImg(int step, int indexImg, PipelineParamHandler pph) {
        //Where processing data is saved
        String outputDataDir = new File(pph.outputDir, pph.imgNames[indexImg]).getAbsolutePath();
        boolean executed = true;
        if (step == 1) {//Stack data -O-
            t.print("Starting step 1, stacking -  on img index " + step + " : " + pph.imgNames[indexImg]);
            executed = PipelineActionsHandler.stackData(indexImg, pph);
        }
        if (step == 2) {//Registration
            t.print("Starting step 2, registration -  on img " + pph.imgNames[indexImg]);
            executed = PipelineActionsHandler.registerSerie(indexImg, outputDataDir, pph);
        }
        if (step == 3) {//Compute mask, find leaves falling in the ground and remove them
            t.print("Starting step 3, masking -  on img " + pph.imgNames[indexImg]);
            executed = PipelineActionsHandler.computeMasksAndRemoveLeaves(indexImg, outputDataDir, pph);
        }
        if (step == 4) {//Compute graph
            t.print("Starting step 4, space/time segmentation -  on img " + pph.imgNames[indexImg]);
            executed = PipelineActionsHandler.spaceTimeMeanShiftSegmentation(indexImg, outputDataDir, pph);
        }
        if (step == 5) {//Compute graph
            t.print("Starting step 5 -  on img " + pph.imgNames[indexImg]);
            executed = PipelineActionsHandler.buildAndProcessGraph(indexImg, outputDataDir, pph);
        }
        if (step == 6) {//RSML building
            t.print("Starting step 6 -  on img " + pph.imgNames[indexImg]);
            executed = PipelineActionsHandler.computeRSMLUntilExpertize(indexImg, outputDataDir, pph);
        }
        if (step == 7) {//RSML building
            t.print("Starting step 7 -  on img " + pph.imgNames[indexImg]);
            executed = PipelineActionsHandler.computeRSMLAfterExpertize(indexImg, outputDataDir, pph);
        }
        if (step == 8) {//MovieBuilding -O-
            t.print("Starting step 8  -  on img " + pph.imgNames[indexImg]);
            executed = MovieBuilder.buildMovie(indexImg, outputDataDir, pph);
        }
		/*
		if(step==9) {//Phene extraction
			t.print("Starting step 9  -  on img "+pph.imgNames[indexImg]);
			executed=extractPhenes(indexImg,outputDataDir,pph);
		}
		*/
        return executed;
    }

    /**
     * This function stacks image data based on the provided parameters.
     *
     * @param indexImg The index of the image in the imgNames array in the
     *                 PipelineParamHandler object.
     * @param pph      An instance of the PipelineParamHandler class containing
     *                 various parameters.
     * @return A boolean indicating whether the operation was successful.
     */
    public static boolean stackData(int indexImg, PipelineParamHandler pph) {
        //Open the csv describing the experience
        String[][] csvDataExpe = VitimageUtils.readStringTabFromCsv(new File(pph.inventoryDir, "A_main_inventory.csv").getAbsolutePath());
        String mainDataDir = csvDataExpe[4][1];
        System.out.println("Maindatadir=" + mainDataDir);

        //Open the csv describing the box
        String outputDataDir = new File(pph.outputDir, pph.imgNames[indexImg]).getAbsolutePath();
        String[][] csvDataImg = VitimageUtils.readStringTabFromCsv(new File(pph.inventoryDir, pph.imgNames[indexImg] + ".csv").getAbsolutePath());
        int N = csvDataImg.length - 1;

        ImagePlus stack = new ImagePlus(new File(mainDataDir, csvDataImg[1][3]).getAbsolutePath());
        if (stack.getStackSize() < 2) {
            //Open, stack and time stamp the corresponding images
            ImagePlus[] tabImg = new ImagePlus[N];
            for (int n = 0; n < N; n++) {
                String date = csvDataImg[1 + n][1];
                double hours = Double.parseDouble(csvDataImg[1 + n][2]);
                IJ.log("Opening image " + new File(mainDataDir, csvDataImg[1 + n][3]).getAbsolutePath());
                tabImg[n] = IJ.openImage(new File(mainDataDir, csvDataImg[1 + n][3]).getAbsolutePath());
                tabImg[n].getStack().setSliceLabel(date + "_ = h0 + " + hours + " h", 1);
            }
            stack = VitimageUtils.slicesToStack(tabImg);
            if (stack == null || stack.getStackSize() == 0) {
                IJ.showMessage("In PipelineSteps.stackData : no stack imported ");
                return false;
            }
        }

        //Size conversion and saving. No bitdepth conversion to handle here, supposing that everything is 8-bit there
        stack = VitimageUtils.resize(stack, (int) (stack.getWidth() / PipelineParamHandler.subsamplingFactor), (int) (stack.getHeight() / PipelineParamHandler.subsamplingFactor), stack.getStackSize());
        IJ.saveAsTiff(stack, new File(outputDataDir, "11_stack.tif").getAbsolutePath());
        return true;
    }

    /**
     * This function registers a series of images based on the provided parameters.
     *
     * @param indexImg      The index of the image in the imgNames array in the
     *                      PipelineParamHandler object.
     * @param outputDataDir The directory where the output data will be stored.
     * @param pph           An instance of the PipelineParamHandler class containing
     *                      various parameters.
     * @return A boolean indicating whether the operation was successful.
     */
    public static boolean registerSerie(int indexImg, String outputDataDir, PipelineParamHandler pph) {
        ImagePlus imgInit = IJ.openImage(new File(outputDataDir, "11_stack.tif").getAbsolutePath());
        int N = imgInit.getStackSize();
        if (imgInit.getBitDepth() == 24) {
            // converting to 32, 64, 16 or 8 bits (the closest to the original bit depth)
            // stack.getProcessor().convertToColorProcessor();
            ImageProcessor ip = imgInit.getProcessor().convertToByte(true);
            // convert but keep the original colors
            ip = imgInit.getProcessor().convertToFloatProcessor();
            imgInit.setProcessor(ip);
        }
        //ImagePlus imgInit = VitimageUtils.cropImage(imgInit2, pph.xMinCrop, pph.yMinCrop, 0, pph.dxCrop, pph.dyCrop, N);
        IJ.save(imgInit, new File(outputDataDir, "12_stack_cropped.tif").getAbsolutePath());
        ImagePlus imgOut = imgInit.duplicate();
        IJ.run(imgOut, "32-bit", "");
        //Create mask
        ImagePlus mask = new Duplicator().run(imgInit, 1, 1, 1, 1, 1, 1);
        mask = VitimageUtils.nullImage(mask);
        mask = VitimageUtils.drawRectangleInImage(mask, 0, 0, mask.getWidth() - 1, mask.getHeight() - 1, 255);
        IJ.saveAsTiff(mask, new File(outputDataDir, "20_mask_for_registration.tif").getAbsolutePath());

        ImagePlus[] tabImg = VitimageUtils.stackToSlices(imgInit);
        ImagePlus[] tabImg2 = VitimageUtils.stackToSlices(imgInit);
        ImagePlus[] tabImgSmall = VitimageUtils.stackToSlices(imgInit);
        ItkTransform[] tr = new ItkTransform[N];
        ItkTransform[] trComposed = new ItkTransform[N];
        /* CROP
        for (int i = 0; i < tabImgSmall.length; i++) {

            tabImgSmall[i].setRoi(0, 0, tabImgSmall[i].getWidth(), (tabImgSmall[i].getHeight() * 2) / 3);
            tabImgSmall[i] = tabImgSmall[i].crop();

        }*/
        int count = 0;

        //First step : daisy-chain rigid registration
        Timer t = new Timer();
        t.log("Starting registration");
        for (int n = 0; (n < N - 1); n++) {
            t.log("n=" + n);
            ItkTransform trRoot = null;
            RegistrationAction regAct = new RegistrationAction().defineSettingsFromTwoImages(tabImg[n], tabImg[n + 1], null, false);
            regAct.setLevelMaxLinear(pph.maxLinear - 1);//Test for helenen before there was a "+1"
            regAct.setLevelMinLinear(0);
            regAct.strideX = 8;
            regAct.strideY = 8;
            regAct.neighX = 3;
            regAct.neighY = 3;
            regAct.selectLTS = 90;
            regAct.setIterationsBM(8);
            BlockMatchingRegistration bm = BlockMatchingRegistration.setupBlockMatchingRegistration(tabImgSmall[n + 1], tabImgSmall[n], regAct);
            bm.mask = mask.duplicate();
            bm.defaultCoreNumber = VitimageUtils.getNbCores();
            bm.minBlockVariance /= 20;//Test for helene, before there was "4"
            boolean viewRegistrations = true;//Useful for debugging
            if (viewRegistrations) {
                bm.displayRegistration = 2;
                bm.adjustZoomFactor(((512.0)) / tabImg[n].getWidth());
                bm.flagSingleView = true;
            }
            bm.displayR2 = true;
            tr[n] = bm.runBlockMatching(trRoot, false);
            if (viewRegistrations) {
                bm.closeLastImages();
                bm.freeMemory();
            }
        }

        for (int n1 = 0; n1 < N - 1; n1++) {
            trComposed[n1] = new ItkTransform(tr[n1]);
            for (int n2 = n1 + 1; n2 < N - 1; n2++) {
                trComposed[n1].addTransform(tr[n2]);
            }
            tabImg[n1] = trComposed[n1].transformImage(tabImg[n1], tabImg[n1]);
        }

        ImagePlus result1 = VitimageUtils.slicesToStack(tabImg);
        result1.setTitle("step 1");
        IJ.saveAsTiff(result1, new File(outputDataDir, "21_midterm_registration.tif").getAbsolutePath());


        // save transform in .txt file i n a folder (Transforms_1)
        // create folder if it does not exist
        String transformPath1 = outputDataDir + File.separator + "Transforms_1";
        File transformFolder1 = new File(transformPath1);
        if (!transformFolder1.exists()) {
            transformFolder1.mkdir();
        }
        count = 0;
        for (ItkTransform itkTransform : tr) {
            if (itkTransform == null) continue;
            System.out.println("Writing transform to file: " + transformPath1);
            System.out.println("Transform" + count + ":" + itkTransform);
            count++;
            itkTransform.writeMatrixTransformToFile(transformPath1 + File.separator + "transform_" + count + ".txt");
        }

        //Second step : daisy-chain dense registration
        ImagePlus result2 = null;
        ArrayList<ImagePlus> listAlreadyRegistered = new ArrayList<ImagePlus>();
        listAlreadyRegistered.add(tabImg2[N - 1]);
        ImagePlus[] imageRefRecc = new ImagePlus[N - 1];
        for (int n1 = N - 2; n1 >= 0; n1--) { // WARNING
            ImagePlus imgRef = listAlreadyRegistered.get(listAlreadyRegistered.size() - 1);
            imageRefRecc[n1] = imgRef;
            RegistrationAction regAct2 = new RegistrationAction().defineSettingsFromTwoImages(tabImg[0], tabImg[0], null, false);
            regAct2.setLevelMaxNonLinear(1);
            regAct2.setLevelMinNonLinear(-1);
            regAct2.setIterationsBMNonLinear(4);
            regAct2.typeTrans = Transform3DType.DENSE;
            regAct2.strideX = 4;
            regAct2.strideY = 4;
            regAct2.neighX = 2;
            regAct2.neighY = 2;
            regAct2.bhsX -= 3;
            regAct2.bhsY -= 3;
            regAct2.sigmaDense /= 6;
            regAct2.selectLTS = 80;
            BlockMatchingRegistration bm2 = BlockMatchingRegistration.setupBlockMatchingRegistration(imgRef, tabImg2[n1], regAct2);
            bm2.mask = mask.duplicate();
            bm2.defaultCoreNumber = VitimageUtils.getNbCores();
            bm2.minBlockVariance = 10;
            bm2.minBlockScore = 0.10;
            bm2.displayR2 = true;
            boolean viewRegistrations = true;
            if (viewRegistrations) {
                bm2.displayRegistration = 2;
                bm2.adjustZoomFactor(512.0 / tabImg[n1].getWidth());
            }

            trComposed[n1] = bm2.runBlockMatching(trComposed[n1], false);

            if (viewRegistrations) {
                bm2.closeLastImages();
                bm2.freeMemory();
            }
            tabImg[n1] = trComposed[n1].transformImage(tabImg2[n1], tabImg2[n1]);
            listAlreadyRegistered.add(tabImg[n1]);
        }
        result2 = VitimageUtils.slicesToStack(tabImg);
        result2.setTitle("Registered stack");
        IJ.saveAsTiff(result2, new File(outputDataDir, "22_registered_stack.tif").getAbsolutePath());

        // save transform in .txt file in a folder (Transforms_2)
        // create folder if it does not exist
        String transformPath2 = outputDataDir + File.separator + "Transforms_2";
        File transformFolder2 = new File(transformPath2);
        if (!transformFolder2.exists()) {
            transformFolder2.mkdir();
        }
        count = 0;

        for (ItkTransform itkTransform : trComposed) {
            if (itkTransform == null) continue;
            System.out.println("Writing transform to file: " + transformPath2);
            System.out.println("Transform" + count + ":" + itkTransform);

            if (itkTransform.isDense()) {
                itkTransform.writeAsDenseField(transformPath2 + File.separator + "transform_" + (count + 1) + ".txt", imageRefRecc[count]);
            } else {
                itkTransform.writeMatrixTransformToFile(transformPath2 + File.separator + "transform_" + (count + 1) + ".txt");
            }
            count++;
        }

        /*ItkTransform ttk = new ItkTransform(trComposed[0]);

        for (ItkTransform itkTransform : trComposed) {
            if (itkTransform == null) continue;
            if (itkTransform.equals(trComposed[0])) continue;
            ttk.addTransform(itkTransform);
        }

        // stack imageRef in a new ImagePlus
        ImagePlus[] imageRef_moins_1 = new ImagePlus[N - 1];
        System.arraycopy(imageRefRecc, 0, imageRef_moins_1, 0, N - 1);
        ImagePlus imageRefStack = VitimageUtils.slicesToStack(imageRef_moins_1);

        ttk.writeAsDenseField(transformPath2 + File.separator + "transform_final", imageRefStack);*/


        return true;
    }

    /**
     * This function computes masks for an image and removes leaves based on the
     * masks.
     *
     * @param indexImg      The index of the image in the imgNames array in the
     *                      PipelineParamHandler object.
     * @param outputDataDir The directory where the output data will be stored.
     * @param pph           An instance of the PipelineParamHandler class containing
     *                      various parameters.
     * @return A boolean indicating whether the operation was successful.
     */
    public static boolean computeMasksAndRemoveLeaves(int indexImg, String outputDataDir, PipelineParamHandler pph) {
        // Open the registered image
        ImagePlus imgReg = IJ.openImage(new File(outputDataDir, "22_registered_stack_Hist_Match.tif").getAbsolutePath());

        //imgReg.getProcessor().invert();

        // Compute the mask for the area of interest at the first time point
        ImagePlus imgMask1 = getMaskOfAreaInterestAtTime(imgReg, 1, false);

        // new blank  image of size of imgReg except everything is equal to 1 above  y = 184, the rest is 0
        ImagePlus imgMask1Helene = VitimageUtils.nullImage(imgReg);
        ImageProcessor processor = imgMask1Helene.getProcessor();
        int width = imgMask1Helene.getWidth();
        int height = imgMask1Helene.getHeight();

        for (int y = 0; y < height; y++) {
            int value = y > 106 ? 1 : 0;//184 ? 1 : 0;
            for (int x = 0; x < width; x++) {
                processor.set(x + y * width, value);
            }
        }

        // put a rectangle from x01 = 537, y0 = 84 to xf=612 yf = 183 to 0
        for (int y = 84; y < 183; y++) {
            for (int x = 537; x < 612; x++) {
                processor.set(x + y * width, 0);
            }
        }
        for (int y = 78; y < 144; y++) {
            for (int x = 332; x < 430; x++) {
                processor.set(x + y * width, 0);
            }
        }
        for (int y = 879; y < imgReg.getHeight(); y++) {
            for (int x = 0; x < imgReg.getWidth(); x++) {
                processor.set(x + y * width, 0);
            }
        }

        // Save the mask as a TIFF file
        IJ.saveAsTiff(imgMask1Helene, new File(outputDataDir, "31_mask_at_t1.tif").getAbsolutePath());

        // Compute the mask for the area of interest at the last time point
        ImagePlus imgMaskN = getMaskOfAreaInterestAtTime(imgReg, imgReg.getStackSize(), false);
        // Save the mask as a TIFF file
        IJ.saveAsTiff(imgMask1Helene, new File(outputDataDir, "32_mask_at_tN.tif").getAbsolutePath());

        // Erode the first mask using a circular structuring element with a radius of
        // 250 pixels
        // TODO: The value 250 should be replaced with a value that has a geometrical
        // meaning in the context of the images
        ImagePlus imgMask2 = MorphoUtils.erosionCircle2D(imgMask1, 250);
        imgMask2.setDisplayRange(0, 1);
        // Save the eroded mask as a TIFF file
        IJ.saveAsTiff(imgMask1Helene, new File(outputDataDir, "33_mask_feuilles").getAbsolutePath());

        // Remove leaves from the registered image using the first and second masks
        ImagePlus[] imgsOut = removeLeavesFromSequence(imgReg, imgMask1, imgMask2);

        // Set the display range of the first output image
        // TODO: The display range should be set adaptively based on the image data
        imgsOut[0].setDisplayRange(0, 255);
        // If the split option is enabled in pph, remove the split central line from the
        // first output image
        if (pph.isSplit()) {
            imgsOut[0] = removeSplitCentralLine(imgsOut[0]);
        }
        // If the gaps option is enabled in pph, perform additional processing
        // TODO: Implement the processing for gaps
        if (pph.isGaps()) {
        }
        if (pph.isOhOh()) {

        }
        // Save the first output image as a TIFF file
        IJ.saveAsTiff(imgsOut[0], new File(outputDataDir, "34_leaves_removed").getAbsolutePath());

        // Set the display range of the second output image
        // TODO: The display range should be set adaptively based on the image data
        imgsOut[1].setDisplayRange(0, 1);
        // Save the second output image as a TIFF file
        IJ.saveAsTiff(imgsOut[1], new File(outputDataDir, "35_mask_of_removed_leaves").getAbsolutePath());

        return true;
    }

    public static ImagePlus removeSplitCentralLine(ImagePlus img) {
        ImagePlus[] imgs = VitimageUtils.stackToSlices(img);
        int N = imgs.length;
        IJ.log("" + N);
        ImagePlus img2 = MorphoUtils.dilationCircle2D(imgs[N - 1], 2);
        ImagePlus img3 = MorphoUtils.dilationLine2D(img2, 15, true);
        ImagePlus img4 = VitimageUtils.thresholdImage(img3, 152, 256);
        ImagePlus imgTrench = VitimageUtils.getBinaryMaskUnary(img4, 127);
        ImagePlus imgRest = VitimageUtils.invertBinaryMask(imgTrench);
        ImagePlus trench = VitimageUtils.makeOperationBetweenTwoImages(img3, imgTrench, 2, false);

        for (int i = 0; i < N; i++) {
            ImagePlus rest = VitimageUtils.makeOperationBetweenTwoImages(imgs[i], imgRest, 2, false);
            imgs[i] = VitimageUtils.makeOperationBetweenTwoImages(rest, trench, 1, false);
        }
        return VitimageUtils.slicesToStack(imgs);
    }

    /**
     * This function computes a date map for a time-lapse sequence of images.
     *
     * @param indexImg
     * @param outputDataDir
     * @param pph
     * @return
     */
    public static boolean spaceTimeMeanShiftSegmentation(int indexImg, String outputDataDir, PipelineParamHandler pph) {
        ImagePlus imgIn = IJ.openImage(new File(outputDataDir, "34_leaves_removed.tif").getAbsolutePath());
        // invert the image
        ImagePlus imgMask1 = IJ.openImage(new File(outputDataDir, "31_mask_at_t1.tif").getAbsolutePath());
        ImagePlus imgMaskN = IJ.openImage(new File(outputDataDir, "32_mask_at_tN.tif").getAbsolutePath());
        ImagePlus imgMaskOfLeaves = IJ
                .openImage(new File(outputDataDir, "35_mask_of_removed_leaves.tif").getAbsolutePath());

        // Insert a first slice with no roots in it. That way, the roots already present
        // will be detected as roots appearing at time 1 (for convention, as background
        // has label 0)
        ImagePlus mire = computeMire(imgIn);
        imgIn = VitimageUtils.addSliceToImage(mire, imgIn);
        int threshRupt = 2; // 25 Helene - best 9 /2
        int threshSlope = 2; // 10 Helene - best 5  /4
        ImagePlus imgOut = projectTimeLapseSequenceInColorspaceCombined(imgIn, imgMask1, imgMaskN, imgMaskOfLeaves,
                threshRupt, threshSlope);
        imgOut = VitimageUtils.makeOperationBetweenTwoImages(imgOut, imgMaskN, 2, true);
        ImagePlus img2 = VitimageUtils.thresholdImage(imgOut, 0.5, 100000);
        img2 = VitimageUtils.connexeNoFuckWithVolume(img2, 1, 10000, 2000, 1E10, 4, 0, true);
        img2 = VitimageUtils.thresholdImage(img2, 0.5, 1E8);
        img2 = VitimageUtils.getBinaryMaskUnary(img2, 0.5);
        IJ.run(img2, "8-bit", "");
        imgOut = VitimageUtils.makeOperationBetweenTwoImages(imgOut, img2, 2, true);
        IJ.run(imgOut, "Fire", "");
        imgOut.setDisplayRange(0, pph.imgSerieSize[indexImg] + 1);
        IJ.saveAsTiff(imgOut, new File(outputDataDir, "40_date_map.tif").getAbsolutePath());
        return true;
    }

    public static boolean buildAndProcessGraph(int indexImg, String outputDataDir, PipelineParamHandler pph) {
        System.out.println("index = " + indexImg + " output_dir = " + outputDataDir + " pph = " + pph);
        ImagePlus imgDates = IJ.openImage(new File(outputDataDir, "40_date_map.tif").getAbsolutePath());
        RegionAdjacencyGraphPipeline.buildAndProcessGraphStraight(imgDates, outputDataDir, pph, indexImg);
        return true;
    }

    /**
     * This method computes the Root System Markup Language (RSML) until expertize for a given image.
     * It performs several operations such as opening the image, applying masks, reading the graph from a file,
     * refining the graph, cleaning the RSML, resampling flying roots, cleaning negative thresholds, and writing the RSML.
     * If the split option is not enabled, it backtracks the primaries. Otherwise, it copies the file.
     *
     * @param indexImg      The index of the image in the imgNames array in the PipelineParamHandler object.
     * @param outputDataDir The directory where the output data will be stored.
     * @param pph           An instance of the PipelineParamHandler class containing various parameters.
     * @return A boolean indicating whether the operation was successful.
     */
    public static boolean computeRSMLUntilExpertize(int indexImg, String outputDataDir, PipelineParamHandler pph) {
        // Open the mask image
        ImagePlus mask = IJ.openImage(new File(outputDataDir, "31_mask_at_t1.tif").getAbsolutePath());
        // Dilate the mask image
        mask = MorphoUtils.dilationCircle2D(mask, 9);
        // Open the date image
        ImagePlus dates = IJ.openImage(new File(outputDataDir, "40_date_map.tif").getAbsolutePath());
        // Read the graph from a file
        SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph = RegionAdjacencyGraphPipeline
                .readGraphFromFile(new File(outputDataDir, "50_graph.ser").getAbsolutePath());
        // Compute the distance transform of the date image
        ImagePlus distOut = MorphoUtils.getDistOut(dates, false);
        // Open the registered image
        ImagePlus reg = IJ.openImage(new File(outputDataDir, "22_registered_stack.tif").getAbsolutePath());

        // Refine the graph
        RootModel rm = RegionAdjacencyGraphPipeline.refinePlongementOfCCGraph(Objects.requireNonNull(graph), distOut, pph, indexImg);
        // Clean the RSML
        rm.cleanWildRsml();
        // Resample flying roots
        rm.resampleFlyingRoots();
        // Clean negative thresholds
        rm.cleanNegativeTh();

        // Write the RSML
        rm.writeRSML3D(new File(outputDataDir, "60_graph_no_backtrack.rsml").getAbsolutePath(), "", true, false);
        // If the split option is not enabled, backtrack the primaries
        if (!pph.isSplit()) {
            backTrackPrimaries(new File(outputDataDir, "60_graph_no_backtrack.rsml").getAbsolutePath(),
                    new File(outputDataDir, "61_graph.rsml").getAbsolutePath(), mask, reg,
                    pph.toleranceDistanceForBeuckerSimplification);
        } else {
            // If the split option is enabled, copy the file
            try {
                FileUtils.copyFile(new File(outputDataDir, "60_graph_no_backtrack.rsml"),
                        new File(outputDataDir, "61_graph.rsml"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    /**
     * This method computes the Root System Markup Language (RSML) after expertize for a given image.
     * It performs several operations such as opening the image, applying masks, reading the graph from a file,
     * checking if the RSML file exists, drawing distance or time, creating time sequence superposition, and saving the images.
     *
     * @param indexImg      The index of the image in the imgNames array in the PipelineParamHandler object.
     * @param outputDataDir The directory where the output data will be stored.
     * @param pph           An instance of the PipelineParamHandler class containing various parameters.
     * @return A boolean indicating whether the operation was successful.
     */
    public static boolean computeRSMLAfterExpertize(int indexImg, String outputDataDir, PipelineParamHandler pph) {
        // Open the mask image
        ImagePlus mask = IJ.openImage(new File(outputDataDir, "31_mask_at_t1.tif").getAbsolutePath());
        // Dilate the mask image
        mask = MorphoUtils.dilationCircle2D(mask, 9);
        // Open the dates image
        ImagePlus dates = IJ.openImage(new File(outputDataDir, "40_date_map.tif").getAbsolutePath());
        // Read the graph from a file
        SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph = RegionAdjacencyGraphPipeline
                .readGraphFromFile(new File(outputDataDir, "50_graph.ser").getAbsolutePath());
        // Open the registered image
        ImagePlus reg = IJ.openImage(new File(outputDataDir, "22_registered_stack.tif").getAbsolutePath());

        // Initialize RootModel
        RootModel rm = null;
        // Check if the RSML file exists
        if (new File(outputDataDir, "61_graph_expertized.rsml").exists()) {
            rm = RootModel
                    .RootModelWildReadFromRsml(new File(outputDataDir, "61_graph_expertized.rsml").getAbsolutePath());
        } else {
            rm = RootModel.RootModelWildReadFromRsml(new File(outputDataDir, "61_graph.rsml").getAbsolutePath());
        }
        // Draw distance or time
        ImagePlus skeletonTime = RegionAdjacencyGraphPipeline.drawDistanceOrTime(dates, Objects.requireNonNull(graph), false, true, 3);
        ImagePlus skeletonDay = RegionAdjacencyGraphPipeline.drawDistanceOrTime(dates, graph, false, true, 2);
        ImagePlus allTimes = RegionAdjacencyGraphPipeline.drawDistanceOrTime(dates, graph, false, false, 1);

        // Check if memory saving is disabled
        if (pph.memorySaving == 0) {
            // Create time sequence superposition
            ImagePlus timeRSMLimg = createTimeSequenceSuperposition(reg, rm);
            // Save the image
            IJ.saveAsTiff(timeRSMLimg,
                    new File(outputDataDir, "62_rsml_2dt_rendered_over_image_sequence.tif").getAbsolutePath());
        }
        // Set display range and save the images
        skeletonDay.setDisplayRange(0, pph.imgSerieSize[indexImg] + 1);
        skeletonTime.setDisplayRange(0, pph.imgSerieSize[indexImg] + 1);
        allTimes.setDisplayRange(0, pph.imgSerieSize[indexImg] + 1);
        IJ.saveAsTiff(skeletonTime, new File(outputDataDir, "63_time_skeleton.tif").getAbsolutePath());
        IJ.saveAsTiff(skeletonDay, new File(outputDataDir, "64_day_skeleton.tif").getAbsolutePath());
        IJ.saveAsTiff(allTimes, new File(outputDataDir, "65_times.tif").getAbsolutePath());
        return true;
    }

    // TODO
    public static boolean extractPhenes(int indexImg, String outputDataDir, PipelineParamHandler pph) {

        return true;
    }

    public static ImagePlus createTimeSequenceSuperposition(ImagePlus imgReg, RootModel rm) {
        ImagePlus[] tabRes = VitimageUtils.stackToSlices(imgReg);
        for (int i = 0; i < tabRes.length; i++) {
            ImagePlus imgRSML = rm.createGrayScaleImageWithTime(tabRes[i], 1, false, (i + 1), true,
                    new boolean[]{true, true, true, false, true}, new double[]{2, 2});
            tabRes[i] = RGBStackMerge.mergeChannels(new ImagePlus[]{tabRes[i], imgRSML}, false);
            IJ.run(tabRes[i], "RGB Color", "");
        }
        return VitimageUtils.slicesToStack(tabRes);
    }

    /**
     * This method backtracks the primary roots in a Root System Markup Language (RSML) file.
     * It reads the RSML file, identifies the primary roots, and for each root, it identifies the first coordinates.
     * It then identifies the mean height of the region to attain in this area and extracts a rectangle around the first coordinate at time 0.
     * It extracts a minimum Djikstra path to this region and finds in this path the last point that is not in the interest area.
     * It then subsamples the new path and inserts the corresponding coordinates, updating the time value along the root.
     * Finally, it writes the updated RSML file.
     *
     * @param pathToInputRsml            The path to the input RSML file.
     * @param pathToOutputRsml           The path to the output RSML file.
     * @param mask                       The mask image used to identify the region of interest.
     * @param imgRegT0                   The registered image at time 0.
     * @param toleranceDistToCentralLine The tolerance distance to the central line used in the Djikstra path extraction.
     */
    public static void backTrackPrimaries(String pathToInputRsml, String pathToOutputRsml, ImagePlus mask,
                                          ImagePlus imgRegT0, double toleranceDistToCentralLine) {
        RootModel rmInit = RootModel.RootModelWildReadFromRsml(pathToInputRsml);
        Root[] prRoots = rmInit.getPrimaryRoots();
        int X = mask.getWidth();
        // int Y=mask.getHeight();
        int xTolerance = X / 20;

        for (Root r : prRoots) {
            // System.out.println("\nDebugging Root ");
            // System.out.println(r);
            // Identify the first coordinates
            Node oldFirst = r.firstNode;
            int xMid = (int) oldFirst.x; // x coordinate of the first node
            int yMid = (int) oldFirst.y; // y coordinate of the first node
            System.out.println("Identified coordinates start=" + xMid + "," + yMid);

            // Identify the mean height of region to attain in this area
            int upperPix = 0;
            for (int i = yMid; i >= 0; i--) {
                if (mask.getPixel(xMid, i)[0] > 0)
                    upperPix = i;
            }
            upperPix -= 10;// TODO

            // Extract a rectangle around the first coordinate at time 0
            ImagePlus imgExtractMask = VitimageUtils.cropImage(mask, Math.max(0, xMid - xTolerance),
                    Math.max(0, upperPix), 0, xTolerance * 2 + 1, yMid - upperPix + 1, 1);
            imgExtractMask.setDisplayRange(0, 1);
            ImagePlus imgExtract = VitimageUtils.cropImage(imgRegT0, Math.max(0, xMid - xTolerance),
                    Math.max(0, upperPix), 0, xTolerance * 2 + 1, yMid - upperPix + 1, 1);

            // Extract a min djikstra path to this region
            GraphPath<Pix, Bord> graph = VitimageUtils.getShortestAndDarkestPathInImage(imgExtract, 8,
                    new Pix(xTolerance, 0, 0), new Pix(xTolerance, yMid - upperPix, 0));
            List<Pix> liInit = graph.getVertexList();

            // Find in this path the last point that is not in the interest area
            int indFirst = 0;
            for (int i = 0; i < graph.getLength(); i++) {
                Pix p = liInit.get(i);
                // System.out.println("Testing "+p+ " = "+imgExtractMask.getPixel(p.x, p.y)[0]);
                if (imgExtractMask.getPixel(p.x, p.y)[0] < 1)
                    indFirst = i;
            }
            indFirst += 7;// Fit to the mean
            if (indFirst >= graph.getLength() - 1)
                continue;// No path to add
            List<Pix> liSecond = new ArrayList<Pix>();
            for (int i = indFirst; i < graph.getLength(); i++) {
                liSecond.add(liInit.get(i));
            }

            // subsample the new path
            List<Integer> liNull = new ArrayList<Integer>();
            List<Pix> list = DouglasPeuckerSimplify.simplify(liSecond, liNull, toleranceDistToCentralLine);
            list.remove(list.size() - 1);
            int x0 = xMid - xTolerance;
            int y0 = upperPix;

            // Insert the corresponding coordinates update time value along the root
            Pix p = list.get(0);
            Node n = new Node(p.x + x0, p.y + y0, 0, null, false);
            r.firstNode = n;
            for (int i = 1; i < list.size() - 1; i++) {
                p = list.get(i);
                n = new Node(p.x + x0, p.y + y0, 0.01f, n, true);
            }
            n.child = oldFirst;
            oldFirst.parent = n;
            r.updateNnodes();
            r.computeDistances();
            r.resampleFlyingPoints(rmInit.hoursCorrespondingToTimePoints);
        }
        rmInit.writeRSML3D(pathToOutputRsml, "", true, false);
    }


    //////////////////// HELPERS OF COMPUTEMASKS ////////////////////////
    public static ImagePlus computeMire(ImagePlus imgIn) {
        ImagePlus img = new Duplicator().run(imgIn, 1, 1, 1, 1, 1, 1);
        IJ.run(img, "Median...", "radius=9 stack");
        // img=concatPartsOfImages(img,imgIn,"Y",0.5); In case of, when will be the time
        return img;
    }

    // Remove the falling stem of arabidopsis from a time lapse sequence imgMask
    // contains all the root system, and imgMask2 only the part that cannot have a
    // arabidopsis stem (the lower part)
    // In this new version, we replace big elements of object with what was at last
    // image
    public static ImagePlus[] removeLeavesFromSequence(ImagePlus imgInit, ImagePlus imgMaskRoot,
                                                       ImagePlus imgMask2Init) {
        int factor = 1;
        ImagePlus[] tabInit = VitimageUtils.stackToSlices(imgInit);
        ImagePlus[] tabTot = VitimageUtils.stackToSlices(imgInit);
        ImagePlus[] tabMaskOut = VitimageUtils.stackToSlices(imgInit);
        ImagePlus[] tabMaskIn = VitimageUtils.stackToSlices(imgInit);
        tabMaskOut[0] = VitimageUtils.nullImage(tabMaskOut[0]);
        tabMaskIn[0] = VitimageUtils.invertBinaryMask(tabMaskOut[0]);
        ImagePlus replacement = VitimageUtils.nullImage(tabInit[0]);

        ImagePlus imgMaskAerialNot = VitimageUtils.invertBinaryMask(imgMask2Init);
        for (int z = 1; z < tabInit.length; z++) {
            // Get the mask of the big elements of object under the menisque
            ImagePlus img = VitimageUtils.makeOperationBetweenTwoImages(tabInit[z], imgMaskRoot, 2, true);
            img = MorphoUtils.dilationCircle2D(img, 2 * factor);
            img = VitimageUtils.gaussianFiltering(img, 3 * factor, 3 * factor, 0);
            ImagePlus biggas = VitimageUtils.thresholdImage(img, -100, 120);

            tabMaskOut[z] = VitimageUtils.binaryOperationBetweenTwoImages(imgMaskAerialNot.duplicate(), biggas, 2);
            tabMaskOut[z] = VitimageUtils.binaryOperationBetweenTwoImages(imgMaskRoot, tabMaskOut[z], 2);
            tabMaskOut[z] = MorphoUtils.dilationCircle2D(tabMaskOut[z], 2 * factor);
            tabMaskOut[z].setDisplayRange(0, 1);
            tabMaskOut[z].setTitle(" " + z);

            // Combine this mask with the one of the previous image
            tabMaskOut[z] = VitimageUtils.binaryOperationBetweenTwoImages(tabMaskOut[z - 1], tabMaskOut[z], 1);
            tabMaskOut[z] = VitimageUtils.connexe2dNoFuckWithVolume(tabMaskOut[z], 0.5, 1000, 1000, 1000000, 4, 0,
                    true);
            tabMaskOut[z] = VitimageUtils.thresholdImage(tabMaskOut[z], 0.5, 1000);
            tabMaskOut[z] = VitimageUtils.getBinaryMaskUnary(tabMaskOut[z], 0.5);

            tabMaskIn[z] = VitimageUtils.invertBinaryMask(tabMaskOut[z]);
            ImagePlus maskNewArea = VitimageUtils.getBinaryMaskUnary(
                    VitimageUtils.binaryOperationBetweenTwoImages(tabMaskOut[z], tabMaskOut[z - 1], 4), 0.5);
            replacement = VitimageUtils.addition(replacement,
                    VitimageUtils.multiply(maskNewArea, tabInit[z - 1], false), false);

            ImagePlus imgPart1 = VitimageUtils.makeOperationBetweenTwoImages(tabMaskIn[z], tabInit[z], 2, false);
            ImagePlus imgPart2 = VitimageUtils.makeOperationBetweenTwoImages(tabMaskOut[z], replacement, 2, false);
            tabTot[z] = VitimageUtils.makeOperationBetweenTwoImages(imgPart1, imgPart2, 1, false);
        }
        ImagePlus img1 = VitimageUtils.slicesToStack(tabTot);
        img1.setDisplayRange(0, 255);
        ImagePlus img2 = VitimageUtils.slicesToStack(tabMaskOut);
        img2.setDisplayRange(0, 1);
        return new ImagePlus[]{img1, img2};
    }

    /**
     * This function computes the mask of the area of interest at a given time point.
     * The mask of the area of interest is computed by applying a series of morphological operations on the input image.
     *
     * @param imgReg The registered image.
     * @param time   The time point at which the mask of the area of interest is computed.
     * @param debug  A boolean indicating whether to display debug information.
     * @return The mask of the area of interest at the given time point.
     */
    public static ImagePlus getMaskOfAreaInterestAtTime(ImagePlus imgReg, int time, boolean debug) {
        ImagePlus imgMask1 = new Duplicator().run(imgReg, 1, 1, time, time, 1, 1);
        if (debug)
            imgMask1.duplicate().show();
        imgMask1 = getMenisque(imgMask1, debug);
        if (debug)
            imgMask1.duplicate().show();
        imgMask1 = VitimageUtils.invertBinaryMask(imgMask1);
        imgMask1 = VitimageUtils.connexeBinaryEasierParamsConnexitySelectvol(imgMask1, 4, 1);
        IJ.run(imgMask1, "8-bit", "");
        if (debug)
            imgMask1.duplicate().show();
        imgMask1 = VitimageUtils.getBinaryMaskUnary(imgMask1, 0.5);
        imgMask1.setDisplayRange(0, 1);
        if (debug)
            imgMask1.duplicate().show();
        return imgMask1;
    }

    /**
     * Draw rectangle in image.
     *
     * @param imgIn the img in
     * @param x0    the x 0
     * @param y0    the y 0
     * @param xf    the xf
     * @param yf    the yf
     * @param value the value
     * @return the image plus
     */
    /*
     * Helper functions to watermark various things in 3d image : Strings,
     * rectangles, cylinders...
     */
    public static ImagePlus drawRectangleInImage(ImagePlus imgIn, int x0, int y0, int xf, int yf, float value) {
        if (imgIn.getType() != ImagePlus.GRAY32)
            return imgIn;
        ImagePlus img = new Duplicator().run(imgIn);
        int xM = img.getWidth();
        int zM = img.getStackSize();
        float[][] valsImg = new float[zM][];
        for (int z = 0; z < zM; z++) {
            valsImg[z] = (float[]) img.getStack().getProcessor(z + 1).getPixels();
            for (int x = x0; x <= xf; x++) {
                for (int y = y0; y <= yf; y++) {
                    valsImg[z][xM * y + x] = value;
                }
            }
        }
        return img;
    }

    // TODO : give a geometrical meaning to the various params

    /**
     * This method is used to process an image and return a modified version of it.
     * The processing involves a series of morphological operations and thresholding.
     * The method is part of an image processing pipeline, specifically designed for processing medical or biological images.
     *
     * @param img   The original ImagePlus object to be processed.
     * @param debug A boolean flag that, when true, enables the display of intermediate processing steps for debugging purposes.
     * @return The processed ImagePlus object.
     */
    public static ImagePlus getMenisque(ImagePlus img, boolean debug) {
        // Factor for dilation and erosion operations
        int factor = 1;

        // If debug is true, create a duplicate of the original image and display it
        if (debug) {
            ImagePlus im = img.duplicate();
            im.setTitle("Init");
            im.show();
            IJ.showMessage("Original image for get menisque");
        }

        // Perform vertical dilation on the image
        ImagePlus img2 = MorphoUtils.dilationLine2D(img, 8 * factor, false);

        // If debug is true, create a duplicate of the image after dilation and display it
        if (debug) {
            ImagePlus im2 = img2.duplicate();
            im2.setTitle("Im2");
            im2.show();
            IJ.showMessage("After vertical dilation");
        }

        // Perform vertical erosion on the image
        img2 = MorphoUtils.erosionLine2D(img2, 8 * factor, false);

        // If debug is true, create a duplicate of the image after erosion and display it
        if (debug) {
            ImagePlus im25 = img2.duplicate();
            im25.setTitle("Im25");
            im25.show();
            IJ.showMessage("After vertical erosion");
        }

        // Perform horizontal dilation on the image
        ImagePlus img3 = MorphoUtils.dilationLine2D(img, 8 * factor, true);

        // If debug is true, create a duplicate of the image after dilation and display it
        if (debug) {
            ImagePlus im3 = img3.duplicate();
            im3.setTitle("Im3");
            im3.show();
            IJ.showMessage("After horizontal dilation");
        }

        // Perform horizontal erosion on the image
        img3 = MorphoUtils.erosionLine2D(img3, 8 * factor, true);

        // If debug is true, create a duplicate of the image after erosion and display it
        if (debug) {
            ImagePlus im35 = img3.duplicate();
            im35.setTitle("Im35");
            im35.show();
            IJ.showMessage("After horizontal erosion");
        }

        // Compute the difference between the vertically and horizontally processed images
        ImagePlus img4 = VitimageUtils.makeOperationBetweenTwoImages(img2, img3, 4, true);

        // Draw a rectangle in the image
        img4 = drawRectangleInImage(img4, 0, 0, img4.getWidth(), yMaxStamp, 0);

        // If debug is true, create a duplicate of the image after drawing the rectangle and display it
        if (debug) {
            ImagePlus im4 = img4.duplicate();
            im4.setTitle("Im4");
            im4.show();
            IJ.showMessage("After diff of 2 and 3 and removal of the stamp qr possible place");
        }

        // Perform horizontal closing on the image
        ImagePlus img5 = MorphoUtils.dilationLine2D(img4, 100 * factor, true);
        img5 = MorphoUtils.erosionLine2D(img5, 15 * factor, true);

        // If debug is true, create a duplicate of the image after closing and display it
        if (debug) {
            ImagePlus im5 = img5.duplicate();
            im5.setTitle("Im5");
            im5.show();
            IJ.showMessage("After closing horizontally");
        }

        // Threshold the image
        ImagePlus img6 = VitimageUtils.thresholdImage(img5, 20, 500);

        // If debug is true, create a duplicate of the image after thresholding and display it
        if (debug) {
            ImagePlus im6 = img6.duplicate();
            im6.setTitle("Im6");
            im6.show();
            IJ.showMessage("After thresholding from 20 to 500");
        }

        // Perform dilation on the image
        img6 = MorphoUtils.dilationLine2D(img6, 50, true);
        img6 = MorphoUtils.dilationLine2D(img6, 2 * factor, true);
        img6 = MorphoUtils.dilationLine2D(img6, factor, false);

        // Select the largest connected component in the image
        img6 = VitimageUtils.connexeBinaryEasierParamsConnexitySelectvol(img6, 4, 1);

        // Perform dilation on the image
        img6 = MorphoUtils.dilationLine2D(img6, 3 * factor, false);

        // Convert the image to 8-bit
        IJ.run(img6, "8-bit", "");

        // If debug is true, create a duplicate of the final image and display it
        if (debug) {
            ImagePlus im8 = img6.duplicate();
            im8.setTitle("Im8");
            im8.show();
            IJ.showMessage("After many dilations and selection of best component");
        }

        // Return the processed image
        return img6;
    }

    //////////////////// HELPERS OF SPACETIMEMEANSHIFTSEGMENTATION
    //////////////////// ////////////////////////
    public static ImagePlus projectTimeLapseSequenceInColorspaceCombined(ImagePlus imgSeq, ImagePlus interestMask1,
                                                                         ImagePlus interestMaskN,
                                                                         ImagePlus maskOfLeaves, int thresholdRupture,
                                                                         int thresholdSlope) {
        // imgSeq.show();
        IJ.run(imgSeq, "Gaussian Blur...", "sigma=0.8");
        // IJ.run(imgSeq, "Mean...", "radius=1 stack");
        ImagePlus result1 = projectTimeLapseSequenceInColorspaceMaxRuptureDown(imgSeq, interestMask1, interestMaskN,
                maskOfLeaves, thresholdRupture);
        ImagePlus result2 = projectTimeLapseSequenceInColorspaceMaxSlope(imgSeq, interestMask1, interestMaskN,
                thresholdSlope);
        // result1.show();
        // result1.setTitle("result1Rupt");
        // result2.show();
        // VitimageUtils.waitFor(5000000);
        ImagePlus out = VitimageUtils.thresholdImage(result1, -0.5, 0.5);
        out = VitimageUtils.invertBinaryMask(out);
        ImagePlus mask = VitimageUtils.getBinaryMaskUnary(out, 0.5);
        result2 = VitimageUtils.makeOperationBetweenTwoImages(result2, mask, 2, false);
        result2.setTitle("result2Slope");
        result2.show();
        return result2;
    }

    public static ImagePlus projectTimeLapseSequenceInColorspaceMaxSlope(ImagePlus imgSeq, ImagePlus interestMask1,
                                                                         ImagePlus interestMaskN, int threshold) {
        int N = imgSeq.getStackSize();
        ImagePlus[] imgTab = VitimageUtils.stackToSlices(imgSeq);
        ImagePlus[] imgs = new ImagePlus[N];

        for (int i = 0; i < N - 1; i++) {
            imgs[i + 1] = VitimageUtils.makeOperationBetweenTwoImages(imgTab[i], imgTab[i + 1], 4, true);
            imgs[i + 1] = VitimageUtils.makeOperationBetweenTwoImages(imgs[i + 1],
                    i == 0 ? interestMask1 : interestMaskN, 2, true);
        }
        imgs[0] = VitimageUtils.nullImage(imgs[1]);
        return VitimageUtils.indMaxOfImageArrayDouble(imgs, threshold);
    }

    public static ImagePlus projectTimeLapseSequenceInColorspaceMaxRuptureDown(ImagePlus imgSeq,
                                                                               ImagePlus interestMask1,
                                                                               ImagePlus interestMaskN,
                                                                               ImagePlus maskOutLeaves,
                                                                               int threshold) {
        ImagePlus[] tab = VitimageUtils.stackToSlices(imgSeq);
        IJ.run(maskOutLeaves, "32-bit", "");
        ImagePlus[] tabLeavesOut = VitimageUtils.stackToSlices(maskOutLeaves);
        for (int i = 0; i < tab.length; i++) {
            tab[i] = VitimageUtils.makeOperationBetweenTwoImages(tab[i], i < 2 ? interestMask1 : interestMaskN, 2,
                    true);
        }
        return indRuptureDownOfImageArrayDouble(tab, tabLeavesOut, threshold);
    }

    /**
     * This function calculates the index of the first significant downward rupture in an array of images (meaning the first point which indicates a significant change in the intensity values).
     * It uses a threshold to determine what constitutes a significant rupture.
     * The function is designed to work with 3D images, iterating over each pixel in each slice of the image stack.
     * It returns an ImagePlus object where each pixel's value is the index of the first significant downward rupture in the corresponding pixel's intensity values over time.
     *
     * @param imgs          An array of ImagePlus objects representing the time series of images.
     * @param maskLeavesOut An array of ImagePlus objects representing masks that exclude certain regions from the analysis.
     * @param minThreshold  The minimum difference in intensity values to be considered a significant rupture.
     * @return An ImagePlus object where each pixel's value is the index of the first significant downward rupture in the corresponding pixel's intensity values over time.
     * <p>
     * public static ImagePlus indRuptureDownOfImageArrayDouble(ImagePlus[] imgs, ImagePlus[] maskLeavesOut,
     * int minThreshold) {
     * // Get the dimensions of the images
     * int xM = imgs[0].getWidth();
     * int yM = imgs[0].getHeight();
     * int zM = imgs[0].getStackSize();
     * <p>
     * // Initialize the output image with the same dimensions as the input images
     * ImagePlus retInd = VitimageUtils.nullImage(imgs[0].duplicate());
     * <p>
     * // Initialize arrays to hold the pixel values of the images and masks
     * float[] valsInd;
     * float[][] valsImg = new float[imgs.length][];
     * float[][] valsMask = new float[imgs.length][];
     * <p>
     * // Initialize arrays to hold the pixel values to be detected and masked
     * double[] valsToDetect;
     * double[] valsToMask;
     * <p>
     * // Iterate over each slice in the image stack
     * for (int z = 0; z < zM; z++) {
     * // Get the pixel values of the current slice in the output image
     * valsInd = (float[]) retInd.getStack().getProcessor(z + 1).getPixels();
     * <p>
     * // Get the pixel values of the current slice in each input image and mask
     * for (int i = 0; i < imgs.length; i++) {
     * valsImg[i] = (float[]) imgs[i].getStack().getProcessor(z + 1).getPixels();
     * valsMask[i] = (float[]) maskLeavesOut[((i < 2) ? 0 : i - 1)].getStack().getProcessor(z + 1).getPixels();
     * }
     * <p>
     * // Iterate over each pixel in the current slice
     * for (int x = 0; x < xM; x++) {
     * for (int y = 0; y < yM; y++) {
     * // Initialize the last significant rupture index to 0
     * int last = 0;
     * <p>
     * // Initialize the arrays to hold the pixel values to be detected and masked
     * valsToDetect = new double[imgs.length];
     * valsToMask = new double[imgs.length];
     * <p>
     * // Get the pixel values to be detected and masked
     * for (int i = 0; i < imgs.length; i++) {
     * valsToDetect[i] = valsImg[i][xM * y + x];
     * valsToMask[i] = valsMask[i][xM * y + x];
     * <p>
     * // Update the last significant rupture index if the current mask pixel value is less than 1
     * if (valsToMask[i] < 1)
     * last = i;
     * }
     * <p>
     * // Debugging flag ?
     * boolean blabla = x == 377 && y == 133;
     * <p>
     * // Create a new array with the pixel values up to the last significant rupture index
     * double[] newTab = new double[last + 1];
     * System.arraycopy(valsToDetect, 0, newTab, 0, last + 1);
     * <p>
     * // Calculate the index of the first significant downward rupture
     * int rupt = ruptureDetectionDown(newTab, minThreshold, blabla);
     * <p>
     * // Set the pixel value in the output image to the rupture index
     * valsInd[xM * y + x] = rupt;
     * }
     * }
     * }
     * <p>
     * // Return the output image
     * return retInd;
     * }
     */
    public static ImagePlus indRuptureDownOfImageArrayDouble(ImagePlus[] imgs, ImagePlus[] maskLeavesOut, int minThreshold) {
        int xM = imgs[0].getWidth();
        int yM = imgs[0].getHeight();
        int zM = imgs[0].getStackSize();

        ImagePlus retInd = VitimageUtils.nullImage(imgs[0].duplicate());
        float[][] valsImg = new float[imgs.length][];
        float[][] valsMask = new float[imgs.length][];

        for (int z = 0; z < zM; z++) {
            float[] valsInd = (float[]) retInd.getStack().getProcessor(z + 1).getPixels();

            for (int i = 0; i < imgs.length; i++) {
                valsImg[i] = (float[]) imgs[i].getStack().getProcessor(z + 1).getPixels();
                valsMask[i] = (float[]) maskLeavesOut[(i < 2) ? 0 : i - 1].getStack().getProcessor(z + 1).getPixels();
            }

            // Use parallel processing if available (Java 8+)
            java.util.stream.IntStream.range(0, xM * yM).parallel().forEach(index -> {
                int x = index % xM;
                int y = index / xM;

                double[] valsToDetect = new double[imgs.length];
                int last = 0;

                for (int i = 0; i < imgs.length; i++) {
                    valsToDetect[i] = valsImg[i][index];
                    if (valsMask[i][index] < 1) last = i;
                }

                double[] newTab = new double[last + 1];
                System.arraycopy(valsToDetect, 0, newTab, 0, last + 1);

                int rupt = ruptureDetectionDown(newTab, minThreshold, false);
                valsInd[index] = rupt;
            });
        }
        return retInd;
    }


    // Return the index which is the first point of the second distribution
    public static int ruptureDetectionDown(double[] vals, double threshold, boolean blabla) {
        int indMax = 0;
        double diffMax = -Double.MAX_VALUE;
        int N = vals.length;
        for (int i = 1; i < N; i++) {
            double m1 = meanBetweenIncludedIndices(vals, 0, i - 1);
            double m2 = meanBetweenIncludedIndices(vals, i, N - 1);
            double diff = m1 - m2;
            if (diff > diffMax) {
                indMax = i;
                diffMax = diff;
            }
            if (blabla) {
                // System.out.println("Apres i="+i+" : indMax="+indMax+" diffMax="+diffMax+" et
                // on avait m1="+m1+" et m2="+m2);
            }
        }
        return (diffMax > threshold ? indMax : 0);
    }


    public static double meanBetweenIncludedIndices(double[] tab, int ind1, int ind2) {
        double tot = 0;
        for (int i = ind1; i <= ind2; i++)
            tot += tab[i];
        return (tot / (ind2 - ind1 + 1));
    }
}
