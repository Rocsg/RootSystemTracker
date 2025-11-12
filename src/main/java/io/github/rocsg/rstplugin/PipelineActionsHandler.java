package io.github.rocsg.rstplugin;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.Duplicator;
import ij.plugin.RGBStackMerge;
import io.github.rocsg.fijiyama.common.*;
import io.github.rocsg.fijiyama.fijiyamaplugin.RegistrationAction;
import io.github.rocsg.fijiyama.registration.BlockMatchingRegistration;
import io.github.rocsg.fijiyama.registration.ItkTransform;
import io.github.rocsg.fijiyama.registration.Transform3DType;
import io.github.rocsg.rsml.RootModel;
import io.github.rocsg.topologicaltracking.CC;
import io.github.rocsg.topologicaltracking.ConnectionEdge;
import io.github.rocsg.topologicaltracking.RegionAdjacencyGraphPipelineV2;

import org.jgrapht.graph.SimpleDirectedWeightedGraph;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

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
    // Timer object for tracking time-related operations in the pipeline
    public static Timer t;
    static double THRESH_RUPT_RATIO = 0.40;
    static double THRESH_SLOPE_RATIO = 0.10;

    /**
     * This method allows the user to select the first and last steps and images to
     * process in the pipeline.
     * It presents a dialog to the user with options to select the steps and images.
     * If the user chooses to process everything, it returns default values.
     * If the user chooses to refine the selection, it returns the user's
     * selections.
     *
     * @param pph The PipelineParamHandler object that contains the pipeline
     *            parameters.
     * @return An array of integers representing the first and last steps and images
     *         to process, and the order of processing.
     *
     *         public static int[] selectFirstAndLast(PipelineParamHandler pph) {
     *         if (VitiDialogs.getYesNoUI("Process everything box after box (select
     *         no to refine)?",
     *         "Process everything box after box (select no to refine)?"))
     *         return new int[] { 0, flagFinished, 0, pph.nbData - 1, 0 };
     *         else {
     *         String[] actions = new String[] { "Step 0: setup part 1", "Step
     *         1:image stacking",
     *         "Step 2: stack registration",
     *         "Step 3 : mask computation, leaves removal", "Step 4: spatio-temporal
     *         segmentation",
     *         "Step 5 : graph computation", "Step 6: RSML building until
     *         expertize",
     *         "Step 7: RSML building after expertize", "Step 8: Movie building" };
     *         String[] order = new String[] { "Box after box", "Step after step" };
     *         GenericDialog gd = new GenericDialog("Expert mode for
     *         RootSystemTracker");
     *         gd.addMessage("Choose the steps to execute");
     *         gd.addChoice("First step to run", actions, actions[0]);
     *         gd.addChoice("Last step to run", actions, actions[0]);
     *         gd.addMessage("Choose the indices of box to be processed (from 0 to "
     *         + (pph.nbData - 1) + ")");
     *         gd.addNumericField("First box index to process", 0, 0, 6, "");
     *         gd.addNumericField("Last box index to process", pph.nbData - 1, 0, 6,
     *         "");
     *         gd.addMessage("Choose the order : box after box (all steps) or step
     *         after step (all boxes)");
     *         gd.addChoice("Order", order, order[0]);
     *         gd.showDialog();
     *         if (gd.wasCanceled())
     *         return new int[] { 0, flagFinished, 0, flagLastImage, 0 };
     *         int st1 = gd.getNextChoiceIndex();
     *         int st2 = gd.getNextChoiceIndex();
     *         int im1 = (int) Math.round(gd.getNextNumber());
     *         int im2 = (int) Math.round(gd.getNextNumber());
     *         int ord = gd.getNextChoiceIndex();
     *         if (st1 < 0)
     *         st1 = 0;
     *         if (st2 < 0)
     *         st2 = 0;
     *         if (st2 > flagFinished)
     *         st1 = flagFinished;
     *         if (st1 > st2)
     *         st1 = st2;
     *         if (im1 < 0)
     *         im1 = 0;
     *         if (im2 < 0)
     *         im2 = 0;
     *         if (im2 > flagLastImage)
     *         st1 = flagLastImage;
     *         if (im1 > im2)
     *         im1 = im2;
     *         return new int[] { st1, st2, im1, im2, ord };
     *         }
     *         }
     */
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

    /*
     * public static boolean doStepOnImg(int step, int indexImg,
     * PipelineParamHandler pph) {
     * // Where processing data is saved
     * String outputDataDir = new File(pph.outputDir,
     * pph.imgNames[indexImg]).getAbsolutePath();
     * boolean executed = true;
     * if (step == 1) {// Stack data -O-
     * t.print("Starting step 1, stacking -  on img index " + step + " : " +
     * pph.imgNames[indexImg]);
     * executed = PipelineActionsHandler.stackData(indexImg, pph);
     * }
     * if (step == 2) {// Registration
     * t.print("Starting step 2, registration -  on img " + pph.imgNames[indexImg]);
     * executed = PipelineActionsHandler.registerSerie(indexImg, outputDataDir,
     * pph);
     * }
     * if (step == 3) {// Compute mask, find leaves falling in the ground and remove
     * them
     * t.print("Starting step 3, masking -  on img " + pph.imgNames[indexImg]);
     * executed = PipelineActionsHandler.computeMasksAndRemoveLeaves(indexImg,
     * outputDataDir, pph);
     * }
     * if (step == 4) {// Compute graph
     * t.print("Starting step 4, space/time segmentation -  on img " +
     * pph.imgNames[indexImg]);
     * executed = PipelineActionsHandler.spaceTimeMeanShiftSegmentation(indexImg,
     * outputDataDir, pph);
     * }
     * if (step == 5) {// Compute graph
     * t.print("Starting step 5 -  on img " + pph.imgNames[indexImg]);
     * executed = PipelineActionsHandler.buildAndProcessGraph(indexImg,
     * outputDataDir, pph);
     * }
     * if (step == 6) {// RSML building
     * t.print("Starting step 6 -  on img " + pph.imgNames[indexImg]);
     * executed = PipelineActionsHandler.computeRSMLUntilExpertize(indexImg,
     * outputDataDir, pph);
     * }
     * if (step == 7) {// RSML building
     * t.print("Starting step 7 -  on img " + pph.imgNames[indexImg]);
     * executed = PipelineActionsHandler.computeRSMLAfterExpertize(indexImg,
     * outputDataDir, pph);
     * }
     * if (step == 8) {// MovieBuilding -O-
     * t.print("Starting step 8  -  on img " + pph.imgNames[indexImg]);
     * executed = MovieBuilder.buildMovie(indexImg, outputDataDir, pph);
     * }
     * /*
     * if(step==9) {//Phene extraction
     * t.print("Starting step 9  -  on img "+pph.imgNames[indexImg]);
     * executed=extractPhenes(indexImg,outputDataDir,pph);
     * }
     *
     * return executed;
     * }
     */

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
        // Define the directory where the processing data is saved
        String outputDataDir = new File(pph.outputDir, pph.imgNames[indexImg]).getAbsolutePath();
        boolean executed = true;



        System.out.println("Starting with step " + step + " on img index " + indexImg + " : " + pph.imgNames[indexImg]);
        t = new Timer();
        // Perform the specified step
        switch (step) {
            case 1: // Stack data
                t.print("Starting step 1, stacking -  on img index " + step + " : " + pph.imgNames[indexImg]);
                executed = PipelineActionsHandler.stackData(indexImg, pph);
                break;
            case 2: // Registration
                IJ.log("doStepOnImg : pph.outputDir = " + pph.outputDir);
                IJ.log("doStepOnImg : pph.imgNames[indexImg] = " + pph.imgNames[indexImg]);
                t.print("Starting step 2, registration -  on img " + pph.imgNames[indexImg]);
                executed = PipelineActionsHandler.registerSerie(indexImg, outputDataDir, pph);
                break;
            case 3: // Compute mask, find leaves falling in the ground and remove them
                t.print("Starting step 3, masking -  on img " + pph.imgNames[indexImg]);
                executed = true;/*PipelineActionsHandler.computeMasksAndRemoveLeaves(indexImg, outputDataDir, pph);*/
                break;
            case 4: // mean shift computation
                t.print("Starting step 4, space/time segmentation -  on img " + pph.imgNames[indexImg]);
                executed = PipelineActionsHandler.spaceTimeMeanShiftSegmentation(indexImg, outputDataDir, pph);
                break;
            case 5: // Compute graph
                t.print("Starting step 5 -  on img " + pph.imgNames[indexImg]);
                executed = PipelineActionsHandler.buildAndProcessGraphV2(indexImg, outputDataDir, pph);
                break;
            case 6: // RSML building
                t.print("Starting step 6 -  on img " + pph.imgNames[indexImg]);
                executed = PipelineActionsHandler.cleanRSMLForExpertize(indexImg, outputDataDir, pph);
                break;
            case 7: // RSML building
                t.print("Starting step 7 -  on img " + pph.imgNames[indexImg]);
                executed = PipelineActionsHandler.computeRSMLAfterExpertize(indexImg, outputDataDir, pph);
                break;
            case 8: // MovieBuilding
                t.print("Starting step 8  -  on img " + pph.imgNames[indexImg]);
                executed = MovieBuilder.buildMovie(indexImg, outputDataDir, pph);
                break;
        }
        /*
         * if(step==9) {//Phene extraction
         * t.print("Starting step 9  -  on img "+pph.imgNames[indexImg]);
         * executed=extractPhenes(indexImg,outputDataDir,pph);
         * }
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

        // Open the csv file that describes the experiment
        String[][] csvDataExpe = VitimageUtils
                .readStringTabFromCsv(new File(pph.inventoryDir, "A_main_inventory.csv").getAbsolutePath());
        String mainDataDir = csvDataExpe[4][1];
        System.out.println("Maindatadir=" + mainDataDir);

        // Print all the content of the csv file
        for (String[] row : csvDataExpe) {
            System.out.println(Arrays.toString(row));
        }

        // Open the csv file that describes the box
        String outputDataDir = new File(pph.outputDir, pph.imgNames[indexImg]).getAbsolutePath();
        String[][] csvDataImg = VitimageUtils
                .readStringTabFromCsv(new File(pph.inventoryDir, pph.imgNames[indexImg] + ".csv").getAbsolutePath());
        int N = csvDataImg.length - 1;

        // Open, stack and time stamp the corresponding images
        ImagePlus[] tabImg = new ImagePlus[N];
        for (int n = 0; n < N; n++) {
            String date = csvDataImg[1 + n][1];
            double hours = Double.parseDouble(csvDataImg[1 + n][2]);
            IJ.log("Opening image " + new File(mainDataDir, csvDataImg[1 + n][3]).getAbsolutePath());
            tabImg[n] = IJ.openImage(new File(mainDataDir, csvDataImg[1 + n][3]).getAbsolutePath());
            tabImg[n].getStack().setSliceLabel(date + "_ = h0 + " + hours + " h", 1);
        }
        ImagePlus stack = VitimageUtils.slicesToStack(tabImg);
        if (stack == null || stack.getStackSize() == 0) {
            IJ.showMessage("In PipelineSteps.stackData : no stack imported ");
            return false;
        }

        // Convert the size and save the image. No bitdepth conversion is needed here,
        // assuming
        // that everything is 8-bit
        // ?
        stack = VitimageUtils.resize(stack, stack.getWidth() / pph.subsamplingFactor,
                stack.getHeight() / pph.subsamplingFactor, stack.getStackSize());

        VitimageUtils.printImageResume(stack, "target geometry");
        VitimageUtils.printImageResume(tabImg[0], "original geometry");

        // Replace the subsampling factor with the original width and height
        //stack = VitimageUtils.resize(stack, originalWidth, originalHeight, stack.getStackSize());
        IJ.saveAsTiff(stack, new File(outputDataDir, "11_stack.tif").getAbsolutePath());
        System.out.println("Stack saved in " + new File(outputDataDir, "11_stack.tif").getAbsolutePath());
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
        // Open the image stack
        ImagePlus stack = IJ.openImage(new File(outputDataDir, "11_stack.tif").getAbsolutePath());
        int N = stack.getStackSize();
        ImagePlus imgInit2 = stack.duplicate();
        imgInit2.show();

        // Convert the image to 8-bit grayscale
        IJ.run(imgInit2, "8-bit", "");

        // Crop the image based on the parameters in pph
        // So, the crop operation will start at the point (pph.xMinCrop, pph.yMinCrop,
        // z0) and end at the point (x0 + pph.dxCrop, y0 + pph.dyCrop, z0 + N).
        System.out.println("pph.xMinCrop = " + pph.xMinCrop + " pph.yMinCrop = " + pph.yMinCrop + " pph.dxCrop = "
                + pph.dxCrop + " pph.dyCrop = " + pph.dyCrop + " N = " + N + " Image type = " + imgInit2.getType());
        System.out.println("image types : " + ImagePlus.GRAY8 + " " + ImagePlus.GRAY16 + " " + ImagePlus.GRAY32);

        // Crop the image based on the parameters in pph
        ImagePlus imgInit = VitimageUtils.cropImage(imgInit2, pph.xMinCrop, pph.yMinCrop, 0, pph.dxCrop, pph.dyCrop, N);
        // Duplicate the cropped image
        ImagePlus imgOut = imgInit.duplicate();
        // Convert the duplicated image to 32-bit
        IJ.run(imgOut, "32-bit", "");
        // Create a mask from the cropped image
        ImagePlus mask = new Duplicator().run(imgInit, 1, 1, 1, 1, 1, 1);
        // Set all pixels in the mask to 0
        mask = VitimageUtils.nullImage(mask);
        // Draw a rectangle in the mask based on the parameters in pph
        System.out.println("pph.marginRegisterLeft = " + pph.marginRegisterLeft + " pph.marginRegisterUp = "
                + pph.marginRegisterUp + " pph.dxCrop = " + pph.dxCrop + " pph.dyCrop = " + pph.dyCrop);
        mask = VitimageUtils.drawRectangleInImage(mask, pph.marginRegisterLeft, pph.marginRegisterUp,
                pph.dxCrop - pph.marginRegisterLeft - pph.marginRegisterRight, pph.dyCrop - 1, 255);

        IJ.saveAsTiff(mask, new File(outputDataDir, "20_mask_for_registration.tif").getAbsolutePath());

        // Convert the cropped image to a series of slices
        ImagePlus[] tabImg = VitimageUtils.stackToSlices(imgInit);
        // Duplicate the series of slices
        ImagePlus[] tabImg2 = VitimageUtils.stackToSlices(imgInit);
        // Create a smaller version of the series of slices
        ImagePlus[] tabImgSmall = VitimageUtils.stackToSlices(imgInit);
        // Initialize the transformations
        ItkTransform[] tr = new ItkTransform[N];
        ItkTransform[] trComposed = new ItkTransform[N];
        // Crop each slice in the smaller series
        for (int i = 0; i < tabImgSmall.length; i++) {
            tabImgSmall[i] = VitimageUtils.cropImage(tabImgSmall[i], 0, 0, 0, tabImgSmall[i].getWidth(),
                    (tabImgSmall[i].getHeight() * 2) / 3, 1);
        }

        // First step : daisy-chain rigid registration
        Timer t = new Timer();
        t.log("Starting registration");
        for (int n = 0; (n < N - 1); n++) {
            t.log("n=" + n);
            ItkTransform trRoot = null;
            RegistrationAction regAct = new RegistrationAction().defineSettingsFromTwoImages(tabImg[n], tabImg[n + 1],
                    null, false);
            regAct.setLevelMaxLinear(pph.maxLinear);
            regAct.setLevelMinLinear(0);
            regAct.strideX = 8;
            regAct.strideY = 8;
//            regAct.levelMaxLinear++;
            regAct.neighX = 3;
            regAct.neighY = 3;
            regAct.selectLTS = 90;
            regAct.setIterationsBM(8);
            BlockMatchingRegistration bm = BlockMatchingRegistration.setupBlockMatchingRegistration(tabImgSmall[n + 1],
                    tabImgSmall[n], regAct);
            bm.mask = mask.duplicate();
            bm.defaultCoreNumber = VitimageUtils.getNbCores();
            bm.minBlockVariance /= 4;
            boolean viewRegistrations = false;// Useful for debugging
            if (viewRegistrations) {
                bm.displayRegistration = 2;
                bm.adjustZoomFactor(((512.0)) / tabImg[n].getWidth());
                bm.flagSingleView = true;
            }
            bm.displayR2 = false;
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
            // Apply the composed transformations to the slices
            tabImg[n1] = trComposed[n1].transformImage(tabImg[n1], tabImg[n1]);
        }

        // Convert the transformed slices back to a stack
        ImagePlus result1 = VitimageUtils.slicesToStack(tabImg);
        result1.setTitle("step 1");
        IJ.saveAsTiff(result1, new File(outputDataDir, "21_midterm_registration.tif").getAbsolutePath());

        // Second step : daisy-chain dense registration
        ImagePlus result2 = null;
        ArrayList<ImagePlus> listAlreadyRegistered = new ArrayList<ImagePlus>();
        listAlreadyRegistered.add(tabImg2[N - 1]);

        // Perform the second step of the registration
        for (int n1 = N - 2; n1 >= 0; n1--) {
            ImagePlus imgRef = listAlreadyRegistered.get(listAlreadyRegistered.size() - 1);
            RegistrationAction regAct2 = new RegistrationAction().defineSettingsFromTwoImages(tabImg[0], tabImg[0],
                    null, false);
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
            BlockMatchingRegistration bm2 = BlockMatchingRegistration.setupBlockMatchingRegistration(imgRef,
                    tabImg2[n1], regAct2);
            bm2.mask = mask.duplicate();
            bm2.defaultCoreNumber = VitimageUtils.getNbCores();
            bm2.minBlockVariance = 10;
            bm2.minBlockScore = 0.10;
            bm2.displayR2 = false;
            boolean viewRegistrations = false;
            if (viewRegistrations) {
                bm2.displayRegistration = 2;
                bm2.adjustZoomFactor(512.0 / tabImg[n1].getWidth());
            }

            trComposed[n1] = bm2.runBlockMatching(trComposed[n1], false);

            if (viewRegistrations) {
                bm2.closeLastImages();
                bm2.freeMemory();
            }
            // Apply the composed transformations to the slices
            tabImg[n1] = trComposed[n1].transformImage(tabImg2[n1], tabImg2[n1]);
            // Add the transformed slice to the list of already registered slices
            listAlreadyRegistered.add(tabImg[n1]);
        }
        // Convert the registered slices back to a stack
        result2 = VitimageUtils.slicesToStack(tabImg);
        result2.setTitle("Registered stack");
        IJ.saveAsTiff(result2, new File(outputDataDir, "22_registered_stack.tif").getAbsolutePath());
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

    public static boolean spaceTimeMeanShiftSegmentation(int indexImg, String outputDataDir, PipelineParamHandler pph) {
        ImagePlus imgIn= IJ.openImage(new File(outputDataDir, "22_registered_stack.tif").getAbsolutePath());
        double expectedDiff=Math.abs(pph.rootTissueIntensityLevel-pph.backgroundIntensityLevel);
        int threshRupt = (int) (expectedDiff * THRESH_RUPT_RATIO);
        int threshSlope = (int) (expectedDiff * THRESH_SLOPE_RATIO);
        ImagePlus imgOut = projectTimeLapseSequenceInColorspaceCombined(imgIn,
                threshRupt, threshSlope,outputDataDir,pph);
        //        imgOut = VitimageUtils.makeOperationBetweenTwoImages(imgOut, 2, true);
        ImagePlus img2 = VitimageUtils.thresholdImage(imgOut, 0.5, 100000);
        img2 = VitimageUtils.connexeNoFuckWithVolume(img2, 1, 10000, 2000, 1E10, 4, 0, true);
        img2 = VitimageUtils.thresholdImage(img2, 0.5, 1E8);
        img2 = VitimageUtils.getBinaryMaskUnary(img2, 0.5);
        ImagePlus imgMaskRoi=IJ.openImage(new File(outputDataDir, "31_MaskHandmadeForRootAreaSelection.tif").getAbsolutePath());
        imgMaskRoi=VitimageUtils.getBinaryMaskUnary(imgMaskRoi, 0.5);
        img2=VitimageUtils.makeOperationBetweenTwoImages(img2, imgMaskRoi, 2, true);
        img2=VitimageUtils.convertFloatToByteWithoutDynamicChanges(img2);

        IJ.run(img2, "8-bit", "");
        imgOut = VitimageUtils.makeOperationBetweenTwoImages(imgOut, img2, 2, true);

        IJ.run(imgOut, "Fire", "");
        imgOut.setDisplayRange(0, pph.imgSerieSize[indexImg] + 1);
        System.out.println("Saving date map to " + new File(outputDataDir, "40_date_map.tif").getAbsolutePath());
        System.out.println("With colormap range 0 to " + (pph.imgSerieSize[indexImg] + 1) + ")");
        IJ.saveAsTiff(imgOut, new File(outputDataDir, "40_date_map.tif").getAbsolutePath());
        return true;
    }





    public static boolean buildAndProcessGraphV2(int indexImg, String outputDataDir, PipelineParamHandler pph) {
        System.out.println("index = " + indexImg + " output_dir = " + outputDataDir + " pph = " + pph);
        ImagePlus imgDates = IJ.openImage(new File(outputDataDir, "40_date_map.tif").getAbsolutePath());
        RegionAdjacencyGraphPipelineV2.buildAndProcessGraphStraight(imgDates, outputDataDir, pph, indexImg);
        return true;
    }

    public static boolean buildAndProcessGraph(int indexImg, String outputDataDir, PipelineParamHandler pph) {
        System.out.println("index = " + indexImg + " output_dir = " + outputDataDir + " pph = " + pph);
        ImagePlus imgDates = IJ.openImage(new File(outputDataDir, "40_date_map.tif").getAbsolutePath());
        RegionAdjacencyGraphPipelineV2.buildAndProcessGraphStraight(imgDates, outputDataDir, pph, indexImg);
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
    public static boolean cleanRSMLForExpertize(int indexImg, String outputDataDir, PipelineParamHandler pph) {
        SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph = RegionAdjacencyGraphPipelineV2.readGraphFromFile(new File(outputDataDir, "50_graph.ser").getAbsolutePath());
        RootModel rm = RegionAdjacencyGraphPipelineV2.buildStep9RefinePlongement(graph/* , distOut*/, pph, indexImg);
        rm.cleanWildRsml();
        rm.resampleFlyingRoots();
        rm.cleanNegativeTh();
        rm.writeRSML3D(new File(outputDataDir, "60_graph_no_backtrack.rsml").getAbsolutePath(), "", true, false);
        rm.writeRSML3D(new File(outputDataDir, "61_graph.rsml").getAbsolutePath(), "", true, false);
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
        // Open the dates image
        ImagePlus dates = IJ.openImage(new File(outputDataDir, "40_date_map.tif").getAbsolutePath());

        // Read the graph from a file
        SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph = RegionAdjacencyGraphPipelineV2
                .readGraphFromFile(new File(outputDataDir, "50_graph.ser").getAbsolutePath());
        // Open the registered image
        ImagePlus reg = IJ.openImage(new File(outputDataDir, "22_registered_stack.tif").getAbsolutePath());

        // Initialize RootModel
        RootModel rm = null;
        // Check if the RSML file exists
        if (new File(outputDataDir, "61_graph_expertized.rsml").exists()) {
            rm = RootModel.RootModelWildReadFromRsml(new File(outputDataDir, "61_graph_expertized.rsml").getAbsolutePath());
        } else {rm = RootModel.RootModelWildReadFromRsml(new File(outputDataDir, "61_graph.rsml").getAbsolutePath());        }
        // Draw distance or time

        ImagePlus allTimes = RegionAdjacencyGraphPipelineV2.drawDistanceOrTime(dates, graph, false, false, 1);
        ImagePlus skeletonTime = RegionAdjacencyGraphPipelineV2.drawDistanceOrTime(dates, graph, false, true, 3);
        ImagePlus skeletonDay = RegionAdjacencyGraphPipelineV2.drawDistanceOrTime(dates, graph, false, true, 2);

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
                    new boolean[]{true, true, true, false, true}, new double[]{2, 2,2,2,2,2});
            tabRes[i] = RGBStackMerge.mergeChannels(new ImagePlus[]{tabRes[i], imgRSML}, false);
            IJ.run(tabRes[i], "RGB Color", "");
        }
        return VitimageUtils.slicesToStack(tabRes);
    }



    public static void main(String[] args) {
        ImageJ ij=new ImageJ();
        String inventoryDir="/home/rfernandez/Bureau/A_Test/RootSystemTracker/JeanTrap/test2/Jean_trap-test/Inventory_of_jean_trap_out";
        String processingDir="/home/rfernandez/Bureau/A_Test/RootSystemTracker/JeanTrap/test2/Jean_trap-test/Processing";
        PipelineParamHandler pph=new PipelineParamHandler(inventoryDir, processingDir);
        //doStepOnImg(5, 1, pph);
        doStepOnImg(4, 1, pph);
        System.out.println("Done");
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



    //////////////////// HELPERS OF SPACETIMEMEANSHIFTSEGMENTATION
    //////////////////// ////////////////////////
    public static ImagePlus projectTimeLapseSequenceInColorspaceCombined(ImagePlus imgSeq,int thresholdRupture,
                                                                         int thresholdSlope,String outputDataDir,PipelineParamHandler pph) {
        // imgSeq.show();
        if(new File(outputDataDir, "MaskHandmadeForRootAreaSelection.tif").exists()){
            ImagePlus imgMask = IJ.openImage(new File(outputDataDir, "MaskHandmadeForRootAreaSelection.tif").getAbsolutePath());
            IJ.saveAsTiff(imgMask, new File(outputDataDir, "32_mask_at_tN.tif").getAbsolutePath());

                   
        }
                    IJ.run(imgSeq, "Gaussian Blur...", "sigma=0.8");


        ImagePlus[] imgTab = projectTimeLapseSequenceInColorspaceMaxRuptureSlopeProduct(imgSeq,thresholdRupture, thresholdSlope,outputDataDir,pph);
        ImagePlus dateIdx = imgTab[0];      // meilleur i (1..N-1)
        ImagePlus score   = imgTab[1];
        

        return dateIdx;//result2;
    }





    // add Loai 
        // --- NEW: produit (ruptureDown × slope) et meilleur indice ---
    public static ImagePlus[] projectTimeLapseSequenceInColorspaceMaxRuptureSlopeProduct(
            ImagePlus imgSeq,
            int minThresholdRupture,
            int minThresholdSlope, String outputDataDir,PipelineParamHandler pph) {

        // 1) Prépare les tranches et applique les masques d’intérêt
        ImagePlus[] tab = VitimageUtils.stackToSlices(imgSeq);
        
        
        // 3) Calcule, pour chaque pixel, le produit (ruptureDownScore[i] × slope[i]) et choisit i* qui le maximise
        ImagePlus[] out = valRuptureDownTimesSlopeProductOfImageArray(
                tab, minThresholdRupture, minThresholdSlope,pph.backgroundIntensityLevel, pph.rootTissueIntensityLevel);

        // Retourne { indices_du_max, valeur_du_produit_au_max }
        return out;
    }


    // --- NEW: helper pour calculer le max du produit par pixel ---
    public static ImagePlus[] valRuptureDownTimesSlopeProductOfImageArray(
            ImagePlus[] imgs,
            int minThresholdRupture,
            int minThresholdSlope,
            double valMeanExpectedBefore,
            double valMeanExpectedAfter) {

        final int xM = imgs[0].getWidth();
        final int yM = imgs[0].getHeight();
        final int zM = imgs[0].getStackSize();
        final int N = imgs.length;

        ImagePlus retInd  = VitimageUtils.nullImage(imgs[0].duplicate()); // index du max-produit
        ImagePlus retProd = VitimageUtils.nullImage(imgs[0].duplicate()); // valeur du max-produit
        retProd = VitimageUtils.convertToFloat(retProd);

        float[] valsInd;
        float[] valsProd;

        boolean rootPassingImpliesHyperSignal=(valMeanExpectedAfter>valMeanExpectedBefore);
        System.out.println("rootPassingImpliesHyperSignal = "+rootPassingImpliesHyperSignal);
        System.out.println("valMeanExpectedBefore = "+valMeanExpectedBefore);
        System.out.println("valMeanExpectedAfter = "+valMeanExpectedAfter);
        System.out.println("minThresholdRupture = "+minThresholdRupture);
        System.out.println("minThresholdSlope = "+minThresholdSlope);
        // Buffers temporels
        float[][] valsImg  = new float[imgs.length][];
        float[][] valsMask = new float[imgs.length][];

        for (int z = 0; z < zM; z++) {
            valsInd  = (float[]) retInd.getStack().getProcessor(z + 1).getPixels();
            valsProd = (float[]) retProd.getStack().getProcessor(z + 1).getPixels();
            for (int i = 0; i < imgs.length; i++) {
                valsImg[i]  = (float[]) imgs[i].getStack().getProcessor(z + 1).getPixels();
            }

            for (int x = 0; x < xM; x++) {
                for (int y = 0; y < yM; y++) {

                    boolean debug=false && (x==1026) && (y==227);
                    // Série temporelle du pixel
                    double[] s = new double[N];
                    for (int i = 0; i < N; i++) s[i] = valsImg[i][xM * y + x];
                    if(debug){
                        System.out.println("Pixel ("+x+","+y+") : time series = "+Arrays.toString(s));
                    }
                    // Scores "rupture down" pour chaque coupure i (i>=1)
                    double[] rupt = ruptureDownScores(s,valMeanExpectedBefore); // longueur = last+1, rupt[0]=0
                    double[] diff= localSlopeScore(s,valMeanExpectedBefore);
                    int    argMax = 0;
                    double maxP   = 0.0;
                    if(debug){
                        System.out.println("Pixel ("+x+","+y+") : rupt = "+Arrays.toString(rupt));
                        System.out.println("Pixel ("+x+","+y+") : diff = "+Arrays.toString(diff));
                    }
                    for (int i = 0; i <N; i++) {
                        double r=0;
                        double m=0;
                        //
                        if(rootPassingImpliesHyperSignal){
                            //No need of inverting the values. Eliminating negative slopes and ruptures
                            r = Math.max(0, rupt[i]-minThresholdRupture);
                            m = Math.max(0, diff[i]-minThresholdSlope);
                        }
                        else{
                            //Inverting the values to detect negative slopes and ruptures
                            r = Math.max(0, -rupt[i]-minThresholdRupture);
                            m = Math.max(0, -diff[i]-minThresholdSlope);
                        }

                        //TODO
                        double prod = r * m;
                        if (prod > maxP) { maxP = prod; argMax = (i+1); }
                    }

                    valsInd [xM * y + x] = argMax;
                    valsProd[xM * y + x] = (float) maxP;
                }
            }
        }
        return new ImagePlus[]{retInd, retProd};
    }

    // for i =0..N-1, gives the mean of the difference of the points 0...i-1 and points i..N-1
    // Specific case : i=0 => mean of all - meanExpectedValueBefore
    public static double[] ruptureDownScores(double[] vals,
                                             double meanExpectedValueBefore) {
        int N = vals.length;
        double[] scores = new double[N];
        double []meanBefore=new double[N];
        double []meanAfter=new double[N];

        for (int i = 1; i < N; i++) {
            double avgBef=0;
            double avgAft=0;
            for(int j=0;j<i;j++) {
                avgBef+=vals[j];
            }
            for(int j=i;j<N;j++) {
                avgAft+=vals[j];
            }
            avgBef/=(i);
            avgAft/=(N-i);
            scores[i]=avgAft-avgBef;            
        }
        scores[0]=VitimageUtils.statistics1D(vals)[0]-meanExpectedValueBefore;
        return scores;
    }


    public static double[] localSlopeScore(double[] vals,double meanExpectedValueBefore) {
        int N = vals.length;
        double[] diffs = new double[N];
        diffs[0] = vals[0]-meanExpectedValueBefore;
        for (int i = 1; i < N; i++) {
            diffs[i] = vals[i] - vals[i - 1];
        }
        return diffs;
    }
}