package io.github.rocsg.rstutils;


import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import io.github.rocsg.fijiyama.common.ItkImagePlusInterface;
import io.github.rocsg.fijiyama.common.Timer;
import io.github.rocsg.fijiyama.common.VitiDialogs;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import io.github.rocsg.fijiyama.fijiyamaplugin.RegistrationAction;
import io.github.rocsg.fijiyama.registration.BlockMatchingRegistration;
import io.github.rocsg.fijiyama.registration.ItkTransform;
import io.github.rocsg.fijiyama.registration.Transform3DType;
import io.github.rocsg.fijiyama.registration.TransformUtils;
import io.github.rocsg.rsml.RootModel;
import io.github.rocsg.rsml.FSR;
import math3d.Point3d;
import org.itk.simple.DisplacementFieldTransform;
import org.itk.simple.Image;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

public class BlockMatchingRegistrationRootModel extends BlockMatchingRegistration {

    private static final double EPSILON = 1E-8;
    /**
     * The is rsml.
     */
    public boolean isRsml = true;

    // tool copy
    boolean waitBeforeStart = false;
    Image[] currentField;
    int indField;
    /**
     * The root model.
     */
    private RootModel rM;

    /**
     * The main method.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        ImageJ ij = new ImageJ();
//		setupAndRunRsmlBlockMatchingRegistration("/home/rfernandez/Bureau/A_Test/BPMP/Reproduction_01_avec_reseau_morgan/Train/20200826-AC-PIP_azote_Seq 6_Boite 00005_IdentificationFailed-Visu.jpg");
        String dir = "/home/rfernandez/Bureau/DATA/Data_Morgan/myData/train/";
        String img = "20200826-AC-PIP_azote_Seq 4_Boite 00109_IdentificationFailed-Visu.jpg";
       // io.github.rocsg.fijiyama.registration.BlockMatchingRegistration.setupAndRunRsmlBlockMatchingRegistration(dir + img, true, true);
    }

    /**
     * Setup and run rsml block matching registration.
     *
     * @param pathToImgRef the path to img ref
     * @param display      the display
     * @param multiRsml    the multi rsml
     * @return the root model
     */
    public static RootModel setupAndRunRsmlBlockMatchingRegistrationFast(String pathToImgRef, boolean display, boolean multiRsml) {
        ImagePlus imgRef = IJ.openImage(pathToImgRef);
        RootModel rootModel = new RootModel(VitimageUtils.withoutExtension(pathToImgRef) + ".rsml");
        rootModel.refineDescription(10);
        rootModel.attachLatToPrime();
        ImagePlus imgMov = plongement(imgRef, rootModel, false);
        RegistrationAction regAct = RegistrationAction.defineSettingsForRSML(imgRef);
        BlockMatchingRegistrationRootModel bm = (BlockMatchingRegistrationRootModel) BlockMatchingRegistrationRootModel.setupBlockMatchingRegistration(imgRef, imgMov, regAct);
        if (!display) bm.imageJOutputActivated = false;
        //bm.waitBeforeStart=false;
        // bm.updateViews(0, 0, 0, "Start");
        bm.displayRegistration = display ? 2 : 0;
        bm.minBlockVariance = 0.05;
        bm.minBlockScore = 0.01;
        bm.isRsml = true;
        bm.rM = rootModel;
        bm.adjustZoomFactor(512.0 / imgRef.getWidth());
        bm.defaultCoreNumber = multiRsml ? 1 : VitimageUtils.getNbCores() / 2;
        bm.runBlockMatching(null, false);
        bm.closeLastImages();
        bm.freeMemory();
        RootModel rt = bm.rM;
        bm = null;
        imgRef = null;
        imgMov = null;
        regAct = null;
        return rt;
    }

    public static ImagePlus plongement(ImagePlus ref, RootModel rootModel, boolean addCrosses) {
        return rootModel.createGrayScaleImage(ref, 1, false, addCrosses, 1);
    }

    /**
     * Rsml files list.
     *
     * @param dirIn  the dir in
     * @param dirOut the dir out
     * @return the string[][]
     */
    public static String[][] rsmlFilesList(String dirIn, String dirOut) {
        boolean debug = true;
        String[] initNames = new File(dirIn).list();
        int Ninit = initNames.length;
        int Nrsml = Ninit / 2;
        int count = 0;
        if ((Ninit % 2) == 1) {
            IJ.showMessage("Fail in RSML file list : number of input files is not multiple of 2");
            return null;
        }
        for (String file : initNames) {
            if (file.substring(file.lastIndexOf('.')).equals(".rsml")) count++;
        }
        if (count != Nrsml) {
            IJ.showMessage("Fail in RSML file list : number of rsml files is not half the number of total input files");
            return null;
        }
        String[][] ret = new String[4][Nrsml];
        int incr = 0;
        for (String imgName : initNames) {
            if (!imgName.substring(imgName.lastIndexOf('.')).equals(".rsml")) {
                String rsmlName = VitimageUtils.withoutExtension(imgName) + ".rsml";
                ret[0][incr] = new File(dirIn, imgName).getAbsolutePath();
                ret[1][incr] = new File(dirOut, imgName).getAbsolutePath();
                ret[2][incr] = new File(dirIn, rsmlName).getAbsolutePath();
                ret[3][incr] = new File(dirOut, rsmlName).getAbsolutePath();
                if (!new File(dirIn, rsmlName).exists()) {
                    IJ.showMessage("Fail in RSML file list : there is no rsml file corresponding to image " + (new File(dirIn, imgName).getAbsolutePath()));
                    return null;
                }
                incr++;
            }
        }
        IJ.log("Ready to process " + Nrsml + " couples of (img, rsml) files");
        if (debug) {
            for (int i = 0; i < ret[0].length; i++) {
                System.out.println();
                System.out.println(ret[0][i]);
                System.out.println(ret[1][i]);
                System.out.println(ret[2][i]);
                System.out.println(ret[3][i]);
            }
        }
        return ret;
    }

    /**
     * Start batch rsml.
     *
     * @param dirIn       the dir in
     * @param dirOutTemp  the dir out temp
     * @param multiThread the multi thread
     */
    public static void startBatchRsml(String dirIn, String dirOutTemp, boolean multiThread) {
        if (dirIn == null) {
            dirIn = VitiDialogs.chooseDirectoryUI("Select input dir.", "Select an input directory with couples files (rsml , image)");
            dirOutTemp = VitiDialogs.chooseDirectoryUI("Select output dir", "Select an output directory to begin a new work");
        }
        final String dirOut = dirOutTemp;
        if (new File(dirOut).list().length > 0) {
            if (!VitiDialogs.getYesNoUI("Warning", "Warning: there seems to be other files in the output dir. Risk of erasing older files. Process anyway ?"))
                return;
        }
        final String[][] filesList = rsmlFilesList(dirIn, dirOut);
        for (int i = 0; i < filesList[0].length; i++) {
            System.out.println("At start, file[" + i + "]=" + filesList[0][i]);
        }
        if (filesList == null) {
            IJ.showMessage("Critical fail : bogus rsml file list, rsml and image files does not match. Next time, please provide a well-formed directory with couples (rsml, image) files, with the same basename");
            return;
        }
        int nImgs = filesList[0].length;
        int nMins = nImgs;
        boolean display = VitiDialogs.getYesNoUI("Dynamic display", "This procedure will register automatically " + nImgs + " rsml files. A very fancy live display of correction is available. Computation time : 3 mn / rsml with display, 1 mn without. Use the fancy display ?");
        IJ.log("Starting correction of a bunch of " + nImgs + " Rsml files. Multithreading on " + VitimageUtils.getNbCores());
        IJ.log("Expected total computation time = " + nMins + " minutes");
        Timer t = new Timer();
        int N = filesList[0].length;
        FSR sr = (new FSR());
        sr.initialize();

        if (display) multiThread = false;

        if (multiThread) {
            int Ncores = VitimageUtils.getNbCores() / 4;
            Thread[] tabThreads = VitimageUtils.newThreadArray(Ncores);
            final int[][] tab = VitimageUtils.listForThreads(N, Ncores);
            AtomicInteger atomNumThread = new AtomicInteger(0);

            for (int ithread = 0; ithread < Ncores; ithread++) {
                tabThreads[ithread] = new Thread() {
                    {
                        setPriority(Thread.NORM_PRIORITY);
                    }

                    public void run() {
                        int tInd = atomNumThread.getAndIncrement();
                        for (int i = 0; i < tab[tInd].length; i++) {
                            int imgInd = tab[tInd][i];
//							System.out.println("In thread "+tInd+" processing index "+tInd+" = "+);
                            IJ.log("\nStarting processing Rsml # " + (imgInd + 1) + " / " + N + " at " + t.toCuteString());
                            if (new File(filesList[1][imgInd]).exists()) {
                                IJ.log("Skipping rsml cause output file already exists : " + filesList[1][imgInd]);
                                continue;
                            }
                            RootModel r = setupAndRunRsmlBlockMatchingRegistrationFast(filesList[0][imgInd], display, true);
                            r.writeRSML(filesList[3][imgInd], dirOut);
                            ImagePlus img = IJ.openImage(filesList[0][imgInd]);
                            IJ.save(img, filesList[1][imgInd]);
                            IJ.log("\nFinished processing Rsml # " + (imgInd + 1) + " / " + N + " at " + t.toCuteString());
                            r.clearDatafile();
                            r = null;
                            img = null;

                        }
                    }
                };
            }
            VitimageUtils.startNoJoin(tabThreads);
        } else {
            for (int i = 0; i < N; i++) {
                IJ.log("\nStarting processing Rsml # " + (i + 1) + " / " + N + " at " + t.toCuteString());
                if (new File(filesList[1][i]).exists()) {
                    IJ.log("Skipping rsml cause output file already exists : " + filesList[1][i]);
                    continue;
                }
                RootModel r = setupAndRunRsmlBlockMatchingRegistrationFast(filesList[0][i], display, false);
                r.writeRSML(filesList[3][i], dirOut);
                ImagePlus img = IJ.openImage(filesList[0][i]);
                IJ.save(img, filesList[1][i]);
                IJ.log("\nFinished processing Rsml # " + (i + 1) + " / " + N + " at " + t.toCuteString());
                r.clearDatafile();
                r = null;
                img = null;
            }
        }
    }

    /**
     * Run algorithm from an initial situation (trInit), and return the final transform (including trInit).
     *
     * @param trInit     the tr init
     * @param stressTest the stress test
     * @return the itk transform
     */
    @SuppressWarnings("unchecked")
    public ItkTransform runBlockMatching(ItkTransform trInit, boolean stressTest) {
        double[] timesGlob = new double[20];
        double[][] timesLev = new double[nbLevels][20];
        double[][][] timesIter = new double[nbLevels][nbIterations][20];
        long t0 = System.currentTimeMillis();
        timesGlob[0] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);
        handleOutput("Absolute time at start=" + t0);
//		double progress=0;IJ.showProgress(progress);
        if (trInit == null) this.currentTransform = new ItkTransform();
        else this.currentTransform = new ItkTransform(trInit);
        handleOutput(new Date().toString());
        //Initialize various artifacts
        ImagePlus imgRefTemp = null;
        ImagePlus imgMovTemp = null;
        if (stressTest) {
            handleOutput("BlockMatching preparation stress test");
        } else {
            handleOutput("Standard blockMatching preparation");
        }
        handleOutput("------------------------------");
        handleOutput("| Block Matching registration|");
        handleOutput("------------------------------");
        handleOutput(" ");
        handleOutput(" ");
        handleOutput("   .-------.          ______");
        handleOutput("  /       /|         /\\     \\");
        handleOutput(" /_______/ |        /  \\     \\");
        handleOutput(" |       | |  -->  /    \\_____\\");
        handleOutput(" |       | /       \\    /     /");
        handleOutput(" |       |/         \\  /     /");
        handleOutput(" .-------.           \\/____ /");
        handleOutput("");
        handleOutput("Parameters Summary");
        handleOutput(" |  ");
        handleOutput(" |--* Transformation type = " + this.transformationType);
        handleOutput(" |--* Metric type = " + this.metricType);
        handleOutput(" |--* Min block variance = " + this.minBlockVariance);

        handleOutput(" |  ");
        handleOutput(" |--* Reference image initial size = " + this.imgRef.getWidth() + " X " + this.imgRef.getHeight() + " X " + this.imgRef.getStackSize() +
                "   with voxel size = " + VitimageUtils.dou(this.imgRef.getCalibration().pixelWidth) + " X " + VitimageUtils.dou(this.imgRef.getCalibration().pixelHeight) + " X " + VitimageUtils.dou(this.imgRef.getCalibration().pixelDepth) + "  , unit=" + this.imgRef.getCalibration().getUnit() + " . Mean background value=" + this.imgRefDefaultValue);
        handleOutput(" |--* Moving image initial size = " + this.imgMov.getWidth() + " X " + this.imgMov.getHeight() + " X " + this.imgMov.getStackSize() +
                "   with voxel size = " + VitimageUtils.dou(this.imgMov.getCalibration().pixelWidth) + " X " + VitimageUtils.dou(this.imgMov.getCalibration().pixelHeight) + " X " + VitimageUtils.dou(this.imgMov.getCalibration().pixelDepth) + "  , unit=" + this.imgMov.getCalibration().getUnit() + " . Mean background value=" + this.imgMovDefaultValue);
        handleOutput(" |--* Block sizes(pix) = [ " + this.blockSizeX + " X " + this.blockSizeY + " X " + this.blockSizeZ + " ] . Block neigbourhood(pix) = " + this.neighbourhoodSizeX + " X " + this.neighbourhoodSizeY + " X " + this.neighbourhoodSizeZ + " . Stride active, select one block every " + this.blocksStrideX + " X " + this.blocksStrideY + " X " + this.blocksStrideZ + " pix");
        handleOutput(" |  ");
        handleOutput(" |--* Blocks selected by variance sorting = " + this.percentageBlocksSelectedByVariance + " %");
        handleOutput(" |--* Blocks selected randomly = " + this.percentageBlocksSelectedRandomly + " %");
        handleOutput(" |--* Blocks selected by score = " + this.percentageBlocksSelectedByScore + " %");
        handleOutput(" |  ");
        handleOutput(" |--* Iterations for each level = " + this.nbIterations);
        handleOutput(" |--* Successive " + TransformUtils.stringVectorN(this.subScaleFactors, "subscale factors"));
        handleOutput(" |--* Successive " + TransformUtils.stringVectorN(this.successiveStepFactors, "step factors (in pixels)"));
        handleOutput(" |--* Successive sigma for dense field interpolation = " + TransformUtils.stringVectorN(this.successiveDenseFieldSigma, ""));
        handleOutput(" |--* Successive sigma for image resampling = " + TransformUtils.stringVectorN(this.successiveSmoothingSigma, ""));
        if (this.displayR2) {
            handleOutput(" |--* Beginning matching. Initial superposition with global R^2 = " + getGlobalRsquareWithActualTransform() + "\n\n");
        }
        String summaryUpdatesParameters = "Summary of updates=\n";
        this.updateViews(0, 0, 0, this.transformationType == Transform3DType.DENSE ? null : this.currentTransform.drawableString());
        if (this.waitBeforeStart) VitimageUtils.waitFor(20000);
        timesGlob[1] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);
        int nbProc = this.defaultCoreNumber;
        double timeFactor = 0.000000003;
        ImagePlus imgMaskTemp = null;
//		progress=0.05;IJ.showProgress(progress);

        //for each scale
        timesGlob[2] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);
        for (int lev = 0; lev < nbLevels; lev++) {
            timesLev[lev][0] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);
            handleOutput("");
            int[] curDims = successiveDimensions[lev];
            double[] curVoxSizes = successiveVoxSizes[lev];
            int[] subSamplingFactors = new int[]{(int) Math.round(this.imgRef.getWidth() * 1.0 / curDims[0]), (int) Math.round(this.imgRef.getHeight() * 1.0 / curDims[1]), (int) Math.round(this.imgRef.getStackSize() * 1.0 / curDims[2])};
            double stepFactorN = this.successiveStepFactors[lev];
            double voxMin = Math.min(curVoxSizes[0], Math.min(curVoxSizes[1], curVoxSizes[2]));
            double stepFactorX = stepFactorN * voxMin / curVoxSizes[0];
            double stepFactorY = stepFactorN * voxMin / curVoxSizes[1];
            double stepFactorZ = stepFactorN * voxMin / curVoxSizes[2];

            int levelStrideX = (int) Math.round(Math.max(1, -EPSILON + this.blocksStrideX / Math.pow(subSamplingFactors[0], 1.0 / 3)));
            int levelStrideY = (int) Math.round(Math.max(1, -EPSILON + this.blocksStrideY / Math.pow(subSamplingFactors[1], 1.0 / 3)));
            int levelStrideZ = (int) Math.round(Math.max(1, -EPSILON + this.blocksStrideZ / Math.pow(subSamplingFactors[2], 1.0 / 3)));
            handleOutput("--> Level " + (lev + 1) + "/" + nbLevels + " . Dims=(" + curDims[0] + "x" + curDims[1] + "x" + curDims[2] +
                    "), search step factors =(" + stepFactorX + "," + stepFactorY + "," + stepFactorZ + ")" + " pixels." +
                    " Subsample factors=" + subSamplingFactors[0] + "," + subSamplingFactors[1] + "," + subSamplingFactors[2] + " Stride=" + levelStrideX + "," + levelStrideY + "," + levelStrideZ);

            // blocks from fixed
            int nbBlocksX = 1 + (curDims[0] - this.blockSizeX - 2 * this.neighbourhoodSizeX * strideMoving) / levelStrideX;
            int nbBlocksY = 1 + (curDims[1] - this.blockSizeY - 2 * this.neighbourhoodSizeY * strideMoving) / levelStrideY;
            int nbBlocksZ = 1;
            if (curDims[2] > 1)
                nbBlocksZ = 1 + (curDims[2] - this.blockSizeZ - 2 * this.neighbourhoodSizeZ * strideMoving) / levelStrideZ;
            int nbBlocksTotal = nbBlocksX * nbBlocksY * nbBlocksZ;
            double nbBlocksActually = nbBlocksTotal * 1.0 * this.percentageBlocksSelectedByScore * this.percentageBlocksSelectedByVariance * this.percentageBlocksSelectedRandomly / (1E6);
            long lo1 = ((int) nbBlocksActually) * (this.neighbourhoodSizeX * 2L + 1) * (this.neighbourhoodSizeY * 2L + 1) * (this.neighbourhoodSizeZ * 2L + 1) * nbIterations;
            double d01 = lo1 / 1000.0;
            double d02 = d01 * this.blockSizeX * this.blockSizeY * this.blockSizeZ / 1000.0;
            double levelTime = (1.0 / nbProc) * (this.neighbourhoodSizeX * 2 + 1) * (this.neighbourhoodSizeY * 2 + 1) * (this.neighbourhoodSizeZ * 2 + 1) * this.blockSizeX * this.blockSizeY * this.blockSizeZ *
                    nbBlocksActually * timeFactor * nbIterations;
            handleOutput("    At this level : # Blocks comparison=" + VitimageUtils.dou(d01 / 1000.0) + " Mega-Ops.     # of voxelwise operations=" + VitimageUtils.dou(d02 / 1000.0) + " Giga-Ops.    Expected computation time=" + VitimageUtils.dou(levelTime) + " seconds.");


            final double voxSX = curVoxSizes[0];
            final double voxSY = curVoxSizes[1];
            final double voxSZ = curVoxSizes[2];
            final int bSX = this.blockSizeX;
            final int bSY = this.blockSizeY;
            final int bSZ = this.blockSizeZ;
            final int bSXHalf = this.blockSizeHalfX;
            final int bSYHalf = this.blockSizeHalfY;
            final int bSZHalf = this.blockSizeHalfZ;
            final int nSX = this.neighbourhoodSizeX;
            final int nSY = this.neighbourhoodSizeY;
            final int nSZ = this.neighbourhoodSizeZ;

            //resample and smooth the fixed image, at the scale and with the smoothing sigma chosen
            this.resampler.setDefaultPixelValue(this.imgRefDefaultValue);
            this.resampler.setTransform(new ItkTransform());
            this.resampler.setOutputSpacing(ItkImagePlusInterface.doubleArrayToVectorDouble(this.successiveVoxSizes[lev]));
            this.resampler.setSize(ItkImagePlusInterface.intArrayToVectorUInt32(this.successiveDimensions[lev]));
            imgRefTemp = VitimageUtils.gaussianFilteringIJ(this.imgRef, this.successiveSmoothingSigma[lev], this.successiveSmoothingSigma[lev], this.successiveSmoothingSigma[lev]);
            double[] voxSizes = VitimageUtils.getVoxelSizes(imgRefTemp);
            timesLev[lev][1] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);
            imgRefTemp = ItkImagePlusInterface.itkImageToImagePlus(resampler.execute(ItkImagePlusInterface.imagePlusToItkImage(imgRefTemp)));
            //VitimageUtils.adjustVoxelSize(imgRefTemp, voxSizes);
            timesLev[lev][2] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);

            //resample the mask image
            if (this.mask != null) {
                this.resampler.setDefaultPixelValue(1);
                voxSizes = VitimageUtils.getVoxelSizes(this.mask);
                imgMaskTemp = ItkImagePlusInterface.itkImageToImagePlus(resampler.execute(ItkImagePlusInterface.imagePlusToItkImage(this.mask)));
                //VitimageUtils.adjustVoxelSize(imgMaskTemp, voxSizes);
            }
            timesLev[lev][3] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);


            //for each iteration
            System.out.println("ImageType");
            for (int iter = 0; iter < nbIterations; iter++) {
                IJ.showProgress((nbIterations * lev + iter) / (1.0 * nbIterations * nbLevels));
                timesLev[lev][4] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);
                timesIter[lev][iter][0] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);
//				progress=0.1+0.9*(lev*1.0/nbLevels+(iter*1.0/nbIterations)*1.0/nbLevels);IJ.showProgress(progress);
                handleOutput("\n   --> Iteration " + (iter + 1) + "/" + this.nbIterations);

                this.resampler.setTransform(this.currentTransform);
                this.resampler.setDefaultPixelValue(this.imgMovDefaultValue);

                if (this.isRsml) imgMovTemp = plongement(this.imgRef, this.rM, false);
                else {
                    imgMovTemp = VitimageUtils.gaussianFilteringIJ(this.imgMov, this.successiveSmoothingSigma[lev], this.successiveSmoothingSigma[lev], this.successiveSmoothingSigma[lev]);
                    voxSizes = VitimageUtils.getVoxelSizes(imgMovTemp);
                    imgMovTemp = ItkImagePlusInterface.itkImageToImagePlus(resampler.execute(ItkImagePlusInterface.imagePlusToItkImage(imgMovTemp)));
                    //VitimageUtils.adjustVoxelSize(imgMovTemp, voxSizes);
                }
                timesIter[lev][iter][1] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);

                //Prepare a coordinate summary tabs for this blocks, compute and store their sigma
                int indexTab = 0;
                nbBlocksTotal = nbBlocksX * nbBlocksY * nbBlocksZ;
                if (nbBlocksTotal < 0) {
                    IJ.showMessage("Bad parameters. Nb blocks=0. nbBlocksX=" + nbBlocksX + " , nbBlocksY=" + nbBlocksY + " , nbBlocksZ=" + nbBlocksZ);
                    if (this.returnComposedTransformationIncludingTheInitialTransformationGiven)
                        return this.currentTransform;
                    else return new ItkTransform();
                }
                double[][] blocksRefTmp = new double[nbBlocksTotal][4];
                handleOutput("       # Total population of possible blocks = " + nbBlocksTotal);


                timesIter[lev][iter][2] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);
                for (int blX = 0; blX < nbBlocksX; blX++) {
                    for (int blY = 0; blY < nbBlocksY; blY++) {
                        for (int blZ = 0; blZ < nbBlocksZ; blZ++) {
                            double[] valsBlock = VitimageUtils.valuesOfBlock(imgRefTemp,
                                    blX * levelStrideX + this.neighbourhoodSizeX * strideMoving, blY * levelStrideY + this.neighbourhoodSizeY * strideMoving, blZ * levelStrideZ + this.neighbourhoodSizeZ * strideMoving,
                                    blX * levelStrideX + this.blockSizeX + this.neighbourhoodSizeX * strideMoving - 1, blY * levelStrideY + this.blockSizeY + this.neighbourhoodSizeY * strideMoving - 1, blZ * levelStrideZ + this.blockSizeZ + this.neighbourhoodSizeZ * strideMoving - 1);
                            double[] stats = VitimageUtils.statistics1D(valsBlock);
                            blocksRefTmp[indexTab++] = new double[]{stats[1], blX * levelStrideX + this.neighbourhoodSizeX * strideMoving, blY * levelStrideY + this.neighbourhoodSizeY * strideMoving, blZ * levelStrideZ + this.neighbourhoodSizeZ * strideMoving};
                        }
                    }
                }
                timesIter[lev][iter][3] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);


                double[][] blocksRef;
                //Trim the ones outside the mask
                handleOutput("Starting trim with " + nbBlocksTotal + " blocks");
                if (this.mask != null) blocksRefTmp = this.trimUsingMaskNEW(blocksRefTmp, imgMaskTemp, bSX, bSY, bSZ);
                int nbMeasured = blocksRefTmp.length;
                handleOutput(" --> After considering the mask, " + nbMeasured + " remaining");

                nbBlocksTotal = blocksRefTmp.length;
                Arrays.sort(blocksRefTmp, new VarianceComparator());
                timesIter[lev][iter][4] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);
                int lastRemoval = (nbBlocksTotal * (100 - this.percentageBlocksSelectedByVariance)) / 100;
                for (int bl = 0; bl < lastRemoval; bl++) blocksRefTmp[bl][0] = -1;
                handleOutput("Sorting " + nbBlocksTotal + " blocks using variance then eliminating blocks from 0 to  " + lastRemoval + " / " + blocksRefTmp.length);


                nbBlocksTotal = 0;
                for (int bl = 0; bl < blocksRefTmp.length; bl++)
                    if (blocksRefTmp[bl][0] >= this.minBlockVariance) nbBlocksTotal++;
                blocksRef = new double[nbBlocksTotal][4];
                nbBlocksTotal = 0;
                for (int bl = 0; bl < blocksRefTmp.length; bl++)
                    if (blocksRefTmp[bl][0] >= this.minBlockVariance) {
                        blocksRef[nbBlocksTotal][3] = blocksRefTmp[bl][0];
                        blocksRef[nbBlocksTotal][0] = blocksRefTmp[bl][1];
                        blocksRef[nbBlocksTotal][1] = blocksRefTmp[bl][2];
                        blocksRef[nbBlocksTotal++][2] = blocksRefTmp[bl][3];
                    }
                handleOutput("       # blocks after trimming=" + nbBlocksTotal);
                timesIter[lev][iter][5] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);


                if (randomKeepingSelectedBlock) {
                    blocksRef = randomSelection(blocksRef);
                    nbMeasured = blocksRef.length;
                    handleOutput(" --> After random selection, " + nbMeasured + " remaining");
                }

                this.correspondanceProvidedAtStart = null;
                nbBlocksTotal = blocksRef.length;


                // Multi-threaded exectution of the algorithm core (a block-matching)
                final ImagePlus imgRefTempThread;
                final ImagePlus imgMovTempThread;
                final double minBS = this.minBlockScore;
                final double[][][][] correspondances = new double[nbProc][][][];
                final double[][][] blocksProp = createBlockPropsFromBlockList(blocksRef, nbProc);
                imgRefTempThread = imgRefTemp.duplicate();
                imgMovTempThread = imgMovTemp.duplicate();


                AtomicInteger atomNumThread = new AtomicInteger(0);
                AtomicInteger curProcessedBlock = new AtomicInteger(0);
                AtomicInteger flagAlert = new AtomicInteger(0);
                final int nbTotalBlock = blocksRef.length;
                this.threads = VitimageUtils.newThreadArray(nbProc);
                timesIter[lev][iter][6] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);
                for (int ithread = 0; ithread < nbProc; ithread++) {
                    this.threads[ithread] = new Thread() {
                        {
                            setPriority(Thread.NORM_PRIORITY);
                        }


                        public void run() {

                            try {
                                int numThread = atomNumThread.getAndIncrement();
                                double[][] blocksPropThread = blocksProp[numThread];
                                double[][][] correspondancesThread = new double[blocksProp[numThread].length][][];
                                //for each fixed block
                                for (int fixBl = 0; fixBl < blocksProp[numThread].length && !interrupted(); fixBl++) {
                                    if (fixBl == 0 && numThread == 0) {

                                    }
                                    //extract ref block data in moving image
                                    int x0 = (int) Math.round(blocksPropThread[fixBl][0]);
                                    int y0 = (int) Math.round(blocksPropThread[fixBl][1]);
                                    int z0 = (int) Math.round(blocksPropThread[fixBl][2]);
                                    int x1 = x0 + bSX - 1;
                                    int y1 = y0 + bSY - 1;
                                    int z1 = z0 + bSZ - 1;
                                    double[] valsFixedBlock = VitimageUtils.valuesOfBlock(imgRefTempThread, x0, y0, z0, x1, y1, z1);
                                    double scoreMax = -10E100;
                                    double distMax = 0;
                                    int xMax = 0;
                                    int yMax = 0;
                                    int zMax = 0;
                                    //for each moving block
                                    int numBl = curProcessedBlock.getAndIncrement();
//								if(nbTotalBlock>1000 && (numBl%(nbTotalBlock/20)==0))handleOutputNoNewline((" "+((numBl*100)/nbTotalBlock)+"%"+VitimageUtils.dou((System.currentTimeMillis()-t0)/1000.0)));
                                    if (nbTotalBlock > 1000 && (numBl % (nbTotalBlock / 10) == 0))
                                        handleOutput((" " + ((numBl * 100) / nbTotalBlock) + "%" + VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0)));
                                    for (int xPlus = -nSX * strideMoving; xPlus <= nSX * strideMoving; xPlus += strideMoving) {
                                        for (int yPlus = -nSY * strideMoving; yPlus <= nSY * strideMoving; yPlus += strideMoving) {
                                            for (int zPlus = -nSZ * strideMoving; zPlus <= nSZ * strideMoving; zPlus += strideMoving) {
                                                //compute similarity between blocks, according to the metric

                                                double[] valsMovingBlock = curDims[2] == 1 ?
                                                        VitimageUtils.valuesOfBlockDoubleSlice(imgMovTempThread, x0 + xPlus * stepFactorX, y0 + yPlus * (stepFactorY), x1 + xPlus * (stepFactorX), y1 + yPlus * (stepFactorY)) :
                                                        VitimageUtils.valuesOfBlockDouble(imgMovTempThread, x0 + xPlus * stepFactorX, y0 + yPlus * (stepFactorY), z0 + zPlus * (stepFactorZ), x1 + xPlus * (stepFactorX), y1 + yPlus * (stepFactorY), z1 + zPlus * (stepFactorZ));

                                                double score = computeBlockScore(valsFixedBlock, valsMovingBlock);
                                                double distance = Math.sqrt((xPlus * voxSX * stepFactorX * (xPlus * voxSX * stepFactorX) +
                                                        (yPlus * voxSY * stepFactorY) * (yPlus * voxSY * stepFactorZ) +
                                                        (zPlus * voxSZ * stepFactorZ) * (zPlus * voxSZ * stepFactorZ)));

                                                if (Math.abs(score) > 10E10) {
                                                    final int flagA = flagAlert.getAndIncrement();
                                                    if (flagA < 1) {
                                                        handleOutput("THREAD ALERT");
                                                        handleOutput("SCORE > 10E20 between (" + x0 + "," + y0 + "," + z0 + ") and (" + (x0 + xPlus * stepFactorX) + "," + (y0 + yPlus * stepFactorY) + "," + (z0 + zPlus * stepFactorZ) + ")");
                                                        handleOutput("Corr=" + correlationCoefficient(valsFixedBlock, valsMovingBlock));
                                                        handleOutput(TransformUtils.stringVectorN(valsFixedBlock, "Vals fixed"));
                                                        handleOutput(TransformUtils.stringVectorN(valsMovingBlock, "Vals moving"));
                                                        System.exit(0);//
                                                        //VitimageUtils.waitFor(10000);
                                                    }
                                                }
                                                //keep the best one
                                                if ((score > scoreMax) || ((score == scoreMax) && (distance < distMax))) {
                                                    xMax = xPlus;
                                                    yMax = yPlus;
                                                    zMax = zPlus;
                                                    scoreMax = score;
                                                    distMax = distance;
                                                }
                                            }
                                        }
                                    }
                                    correspondancesThread[fixBl] = new double[][]{
                                            new double[]{blocksPropThread[fixBl][0] + bSXHalf, blocksPropThread[fixBl][1] + bSYHalf, blocksPropThread[fixBl][2] + bSZHalf},
                                            new double[]{blocksPropThread[fixBl][0] + bSXHalf + xMax * stepFactorX, blocksPropThread[fixBl][1] + bSYHalf + yMax * stepFactorZ, blocksPropThread[fixBl][2] + bSZHalf + zMax * stepFactorZ},
                                            new double[]{scoreMax, 1}};
                                }
                                int nbKeep = 0;
                                for (int i = 0; i < correspondancesThread.length; i++)
                                    if (correspondancesThread[i][2][0] >= minBS) nbKeep++;
                                double[][][] correspondancesThread2 = new double[nbKeep][][];
                                nbKeep = 0;
                                for (int i = 0; i < correspondancesThread.length; i++) {
                                    if (correspondancesThread[i][2][0] >= minBS) {
                                        correspondancesThread2[nbKeep] = new double[][]{{0, 0, 0}, {0, 0, 0}, {0, 0}};
                                        for (int l = 0; l < 3; l++)
                                            System.arraycopy(correspondancesThread[i][l], 0, correspondancesThread2[nbKeep][l], 0, (l == 2 ? 2 : 3));
                                        nbKeep++;
                                    }
                                }
                                if (numThread == 0)
                                    handleOutput("Sorting blocks using correspondance score. Threshold= " + minBS + " . Nb blocks before=" + nbProc * correspondancesThread.length + " and after=" + nbProc * nbKeep);

                                correspondances[numThread] = correspondancesThread2;
                            } catch (Exception ie) {
                            }
                        }

                        @SuppressWarnings("unused")
                        public void cancel() {
                            interrupt();
                        }
                    };
                }
                timesIter[lev][iter][7] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);
                if (stressTest) {
                    System.out.println("Stress test area");
                    VitimageUtils.startNoJoin(threads);
                    VitimageUtils.waitFor(10000);
                    for (int th = 0; th < this.threads.length; th++) this.threads[th].interrupt();
                    VitimageUtils.waitFor(200);
                    bmIsInterrupted = true;
                    handleOutput("Stress test passed.\n");
                    System.out.println("Out from stress test area");
                } else VitimageUtils.startAndJoin(threads);

                if (bmIsInterrupted) {
                    System.out.println("BM Is INt zone");
                    bmIsInterruptedSucceeded = true;
                    VitimageUtils.waitFor(200);
                    int nbAlive = 1;
                    while (nbAlive > 0) {
                        nbAlive = 0;
                        for (int th = 0; th < this.threads.length; th++) if (this.threads[th].isAlive()) nbAlive++;
                        handleOutput("Trying to stop blockmatching. There is still " + nbAlive + " threads running over " + this.threads.length);
                    }
                    this.closeLastImages();
                    this.freeMemory();
                    System.out.println("UNTIL THERE");
                    return null;
                }
                for (int i = 0; i < threads.length; i++) threads[i] = null;
                threads = null;
                timesIter[lev][iter][8] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);
                handleOutput("");
                //Convert the correspondance from each thread correspondance list to a main list for the whole image
                ArrayList<double[][]> listCorrespondances = new ArrayList<double[][]>();
                for (int i = 0; i < correspondances.length; i++) {
                    for (int j = 0; j < correspondances[i].length; j++) {
                        listCorrespondances.add(correspondances[i][j]);
                    }
                }
                timesIter[lev][iter][9] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);


                // Selection step 1 : select correspondances by score
                ItkTransform transEstimated = null;
                int nbPts1 = listCorrespondances.size();
                Object[] ret = getCorrespondanceListAsTrimmedPointArray(listCorrespondances, this.successiveVoxSizes[lev], this.percentageBlocksSelectedByScore, 100, transEstimated);
                Point3d[][] correspondancePoints = (Point3d[][]) ret[0];
                listCorrespondances = (ArrayList<double[][]>) ret[2];
                int nbPts2 = listCorrespondances.size();
                this.lastValueBlocksCorr = (Double) ret[1];
                timesIter[lev][iter][10] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);

                //if affine
                if (this.transformationType != Transform3DType.DENSE) {
                    switch (this.transformationType) {
                        case VERSOR:
                            transEstimated = ItkTransform.estimateBestRigid3D(correspondancePoints[1], correspondancePoints[0]);
                            break;
                        case AFFINE:
                            transEstimated = ItkTransform.estimateBestAffine3D(correspondancePoints[1], correspondancePoints[0]);
                            break;
                        case SIMILARITY:
                            transEstimated = ItkTransform.estimateBestSimilarity3D(correspondancePoints[1], correspondancePoints[0]);
                            break;
                        case TRANSLATION:
                            transEstimated = ItkTransform.estimateBestTranslation3D(correspondancePoints[1], correspondancePoints[0]);
                            break;
                        default:
                            transEstimated = ItkTransform.estimateBestRigid3D(correspondancePoints[1], correspondancePoints[0]);
                            break;
                    }
                    timesIter[lev][iter][11] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);
                    if (correspondancePoints[1].length < 5) {
                        handleOutput("Warning : less than 5 correspondance points. Setting up identity transform in replacement");
                        transEstimated = new ItkTransform();
                    } else {
                        ret = getCorrespondanceListAsTrimmedPointArray(listCorrespondances, this.successiveVoxSizes[lev], 100, this.percentageBlocksSelectedByLTS, transEstimated);
                        timesIter[lev][iter][12] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);
                        correspondancePoints = (Point3d[][]) ret[0];
                        listCorrespondances = (ArrayList<double[][]>) ret[2];
                        this.lastValueBlocksCorr = (Double) ret[1];
                        int nbPts3 = listCorrespondances.size();
                        handleOutput("Nb pairs : " + nbPts1 + " , after score selection : " + nbPts2 + " , after LTS selection : " + nbPts3);

                        transEstimated = null;
                        switch (this.transformationType) {
                            case VERSOR:
                                transEstimated = ItkTransform.estimateBestRigid3D(correspondancePoints[1], correspondancePoints[0]);
                                break;
                            case AFFINE:
                                transEstimated = ItkTransform.estimateBestAffine3D(correspondancePoints[1], correspondancePoints[0]);
                                break;
                            case SIMILARITY:
                                transEstimated = ItkTransform.estimateBestSimilarity3D(correspondancePoints[1], correspondancePoints[0]);
                                break;
                            case TRANSLATION:
                                transEstimated = ItkTransform.estimateBestTranslation3D(correspondancePoints[1], correspondancePoints[0]);
                                break;
                            default:
                                transEstimated = ItkTransform.estimateBestRigid3D(correspondancePoints[1], correspondancePoints[0]);
                                break;
                        }
                        timesIter[lev][iter][13] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);

                        if (displayRegistration == 2) {
                            Object[] obj = VitimageUtils.getCorrespondanceListAsImagePlus(imgRef, listCorrespondances, curVoxSizes, this.sliceInt,
                                    levelStrideX * subSamplingFactors[0], levelStrideY * subSamplingFactors[1], levelStrideZ * subSamplingFactors[2],
                                    blockSizeHalfX * subSamplingFactors[0], blockSizeHalfY * subSamplingFactors[1], blockSizeHalfZ * subSamplingFactors[2], false);
                            this.correspondancesSummary = (ImagePlus) obj[0];
                            this.sliceIntCorr = 1 + (int) obj[1];
                        }
                        timesIter[lev][iter][14] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);
                    }
                    //Finally, add it to the current stack of transformations
                    handleOutput("  Update to transform = \n" + transEstimated.drawableString());
                    double[] vector = transEstimated.from2dMatrixto1dVector();
                    handleOutput("The estimated angles in degrees: thetaX= " + VitimageUtils.dou(vector[0] * 180 / Math.PI) + ", thetaY= " + VitimageUtils.dou(vector[1] * 180 / Math.PI) + ", thetaZ= " + VitimageUtils.dou(vector[2] * 180 / Math.PI));
                    handleOutput("The translation in voxels is: Tx = " + VitimageUtils.dou(vector[3] / voxSX) + ", Ty = " + VitimageUtils.dou(vector[4] / voxSY) + ", Tz = " + VitimageUtils.dou(vector[5] / voxSZ));
                    //Build the displayed vector
                    String str = "Level" + (lev + 1) + "/" + nbLevels + "Iteration " + (iter + 1) + "/" + this.nbIterations;
                    String theta = "[thetaX, thetaY,thetaZ] = " + "[" + VitimageUtils.dou(vector[0] * 180 / Math.PI) + "," + VitimageUtils.dou(vector[1] * 180 / Math.PI) + "," + VitimageUtils.dou(vector[2] * 180 / Math.PI) + "]";
                    String trans = "[Tx,Ty,Tz] = " + "[" + VitimageUtils.dou(vector[3] / voxSX) + "," + VitimageUtils.dou(vector[4] / voxSY) + "," + VitimageUtils.dou(vector[5] / voxSZ) + "]";
                    summaryUpdatesParameters += str + " Angles(in degrees) : " + theta + ", Translation : " + trans + " \n";


                    if (!transEstimated.isIdentityAffineTransform(1E-6, 0.05 * Math.min(Math.min(voxSX, voxSY), voxSZ))) {
                        this.currentTransform.addTransform(new ItkTransform(transEstimated));
                        //this.additionalTransform.addTransform(new ItkTransform(transEstimated));
                        handleOutput("Global transform after this step =\n" + this.currentTransform.drawableString());
                    } else {
                        handleOutput("Last transformation computed was identity. Convergence seems to be attained. Going to next level");
                        iter = nbIterations;
                        continue;
                    }
                    timesIter[lev][iter][15] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);
                } else {
                    handleOutput("       Field interpolation from " + correspondancePoints[0].length + " correspondances with sigma=" + this.successiveDenseFieldSigma[lev] + " " + imgRefTemp.getCalibration().getUnit() +
                            " ( " + ((int) (Math.round(this.successiveDenseFieldSigma[lev] / voxSX))) + " voxSX , " + ((int) (Math.round(this.successiveDenseFieldSigma[lev] / voxSY))) + " voxSY , " + ((int) (Math.round(this.successiveDenseFieldSigma[lev] / voxSZ))) + " voxSZ )");

                    //compute the field
                    this.currentField[this.indField++] = ItkTransform.computeDenseFieldFromSparseCorrespondancePoints(correspondancePoints, imgRefTemp, this.successiveDenseFieldSigma[lev], false);
                    timesIter[lev][iter][11] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);
                    timesIter[lev][iter][12] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);
                    timesIter[lev][iter][13] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);
                    timesIter[lev][iter][14] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);

                    //Finally, add it to the current stack of transformations
                    if (this.isRsml) {
                        //System.out.println("YE"+(this.indField-1)+"\n"+this.currentField[this.indField-1]);

                        ItkTransform tr = new ItkTransform(new DisplacementFieldTransform(new Image(this.currentField[this.indField - 1])));
                        System.out.println("Mean distance after trans=" + tr.meanDistanceAfterTrans(imgRefTemp, 100, 100, 1, true)[0]);
                        rM.applyTransformToGeometry(tr);
                        this.currentTransform.addTransform(tr);
                    } else {
                        this.currentTransform.addTransform(new ItkTransform(new DisplacementFieldTransform(this.currentField[this.indField - 1])));
                    }
                    timesIter[lev][iter][15] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);

                }
                if (displayRegistration == 2) {
                    Object[] obj = VitimageUtils.getCorrespondanceListAsImagePlus(imgRef, listCorrespondances, curVoxSizes, this.sliceInt,
                            levelStrideX * subSamplingFactors[0], levelStrideY * subSamplingFactors[1], levelStrideZ * subSamplingFactors[2],
                            blockSizeHalfX * subSamplingFactors[0], blockSizeHalfY * subSamplingFactors[1], blockSizeHalfZ * subSamplingFactors[2], false);
                    this.correspondancesSummary = (ImagePlus) obj[0];

                    this.sliceIntCorr = 1 + (int) obj[1];
                }
                if (displayR2) {
                    globalR2Values[incrIter] = getGlobalRsquareWithActualTransform();
                    timesIter[lev][iter][16] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);
                    this.lastValueCorr = globalR2Values[incrIter];
                    handleOutput("Global R^2 after iteration=" + globalR2Values[incrIter++]);
                }
                this.updateViews(lev, iter, (this.levelMax - lev) >= 1 ? 0 : (1 - this.levelMax + lev), this.transformationType == Transform3DType.DENSE ? null : this.currentTransform.drawableString());
                timesIter[lev][iter][17] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);
            }// Back for another iteration
            timesLev[lev][4] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);
        } // Back for another level
        timesGlob[3] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);
        handleOutput("Block matching finished, date=" + new Date());
        if (this.transformationType != Transform3DType.DENSE)
            handleOutput("\nMatrice finale block matching : \n" + this.currentTransform.drawableString());
        if (this.transformationType != Transform3DType.DENSE) handleOutput(summaryUpdatesParameters);

        if (displayR2) {
            handleOutput("Successive R2 values :");
            for (int i = 0; i < incrIter; i++) handleOutput(" -> " + globalR2Values[i]);
        }
        timesGlob[4] = VitimageUtils.dou((System.currentTimeMillis() - t0) / 1000.0);


        if (this.timingMeasurement) {
            handleOutput("\n\n\n\n\n###################################################\n\nDebrief timing");
            handleOutput("Parametres : ");
            handleOutput(" |--* Transformation type = " + this.transformationType);
            handleOutput(" |--* Metric type = " + this.metricType);
            handleOutput(" |--* Min block variance = " + this.minBlockVariance);

            handleOutput(" |  ");
            handleOutput(" |--* Reference image initial size = " + this.imgRef.getWidth() + " X " + this.imgRef.getHeight() + " X " + this.imgRef.getStackSize() +
                    "   with voxel size = " + VitimageUtils.dou(this.imgRef.getCalibration().pixelWidth) + " X " + VitimageUtils.dou(this.imgRef.getCalibration().pixelHeight) + " X " + VitimageUtils.dou(this.imgRef.getCalibration().pixelDepth) + "  , unit=" + this.imgRef.getCalibration().getUnit() + " . Mean background value=" + this.imgRefDefaultValue);
            handleOutput(" |--* Moving image initial size = " + this.imgMov.getWidth() + " X " + this.imgMov.getHeight() + " X " + this.imgMov.getStackSize() +
                    "   with voxel size = " + VitimageUtils.dou(this.imgMov.getCalibration().pixelWidth) + " X " + VitimageUtils.dou(this.imgMov.getCalibration().pixelHeight) + " X " + VitimageUtils.dou(this.imgMov.getCalibration().pixelDepth) + "  , unit=" + this.imgMov.getCalibration().getUnit() + " . Mean background value=" + this.imgMovDefaultValue);
            handleOutput(" |--* Block sizes(pix) = [ " + this.blockSizeX + " X " + this.blockSizeY + " X " + this.blockSizeZ + " ] . Block neigbourhood(pix) = " + this.neighbourhoodSizeX + " X " + this.neighbourhoodSizeY + " X " + this.neighbourhoodSizeZ + " . Stride active, select one block every " + this.blocksStrideX + " X " + this.blocksStrideY + " X " + this.blocksStrideZ + " pix");
            handleOutput(" |  ");
            handleOutput(" |--* Blocks selected by variance sorting = " + this.percentageBlocksSelectedByVariance + " %");
            handleOutput(" |--* Blocks selected randomly = " + this.percentageBlocksSelectedRandomly + " %");
            handleOutput(" |--* Blocks selected by score = " + this.percentageBlocksSelectedByScore + " %");
            handleOutput(" |  ");
            handleOutput(" |--* Iterations for each level = " + this.nbIterations);
            handleOutput(" |--* Successive " + TransformUtils.stringVectorN(this.subScaleFactors, "subscale factors"));
            handleOutput(" |--* Successive " + TransformUtils.stringVectorN(this.successiveStepFactors, "step factors (in pixels)"));
            handleOutput(" |--* Successive sigma for dense field interpolation = " + TransformUtils.stringVectorN(this.successiveDenseFieldSigma, ""));
            handleOutput(" |--* Successive sigma for image resampling = " + TransformUtils.stringVectorN(this.successiveSmoothingSigma, ""));
            handleOutput(" |--* Successive sigma for image resampling = " + TransformUtils.stringVectorN(this.successiveSmoothingSigma, ""));
            handleOutput("\n\n");
            handleOutput("Times globaux : start=" + timesGlob[0] + "  fin update view=" + timesGlob[1] + "  fin prepa=" + timesGlob[2] + "  fin levels=" + timesGlob[3] + "  fin return=" + timesGlob[3]);
            for (int lev = 0; lev < this.nbLevels; lev++) {
                handleOutput("    Times level " + lev + " : start=" + timesLev[lev][0] + "  fin gaussRef=" + timesLev[lev][1] + "  fin transRef=" + timesLev[lev][2] + "  fin prepa3=" + timesLev[lev][3] + "fin iters=" + timesLev[lev][4]);

            }

            handleOutput("Summary computation times for Block matching");
            double d = 0;
            double dSum = 0;
            d += (timesGlob[1] - timesGlob[0]);
            for (int lev = 0; lev < this.nbLevels; lev++)
                for (int it = 0; it < this.nbIterations; it++) d += (timesIter[lev][it][17] - timesIter[lev][it][14]);
            handleOutput("time used for view updating (s)=" + VitimageUtils.dou(d));

            d = 0;
            for (int lev = 0; lev < this.nbLevels; lev++) d += (timesLev[lev][2] - timesLev[lev][1]);
            for (int lev = 0; lev < this.nbLevels; lev++)
                for (int it = 0; it < this.nbIterations; it++) d += (timesIter[lev][it][1] - timesIter[lev][it][0]);
            handleOutput("time used for resampling reference (one time), and moving (at each iteration) (s)=" + VitimageUtils.dou(d));
            dSum += d;

            d = 0;
            for (int lev = 0; lev < this.nbLevels; lev++)
                for (int it = 0; it < this.nbIterations; it++) d += (timesIter[lev][it][3] - timesIter[lev][it][2]);
            handleOutput("time used to compute blocks variances (s)=" + VitimageUtils.dou(d));
            dSum += d;

            d = 0;
            for (int lev = 0; lev < this.nbLevels; lev++)
                for (int it = 0; it < this.nbIterations; it++) d += (timesIter[lev][it][8] - timesIter[lev][it][7]);
            handleOutput("time used to compute correspondences between blocks (s)=" + VitimageUtils.dou(d));
            dSum += d;


            d = timesGlob[3] - timesGlob[0] - dSum;
            handleOutput("time used for side events (s) =" + VitimageUtils.dou(d));

            handleOutput("Total time (s)=" + VitimageUtils.dou(timesGlob[3] - timesGlob[0]));
        }
        //glob       0               1            2               3
//		  st    prep         levels          return
//		timesLev     0            1           2            3          4             5                6                7              8                9                10                 11
//				     st   prep        tr ref      iters

        //			timesIter     0            1           2            3          4             5                6                7              8                9                10                 11
        //                        st   tr mov     maketab      compvar      sortvar     trimvar          prep bm             prejoin       join          buildcorrtab	  trim score
        //                         10                    11                    12                         13                        14                  15          16
        //                               firstestimate           LTS                 second estimate             correspImage               add              R2
        if (this.returnComposedTransformationIncludingTheInitialTransformationGiven) return this.currentTransform;
        else {
            if (trInit.isDense()) {
                ItkTransform trInv = (trInit.getFlattenDenseField(imgRefTemp).getInverseOfDenseField());
                return new ItkTransform(trInv.addTransform(this.currentTransform));
            } else return new ItkTransform(trInit.getInverse().addTransform(this.currentTransform));
        }
    }

    double computeBlockScore(double[] valsFixedBlock, double[] valsMovingBlock) {
        //if(valsFixedBlock.length!=valsMovingBlock.length)return -10E10;
        switch (this.metricType) {
            case CORRELATION:
                return correlationCoefficient(valsFixedBlock, valsMovingBlock);
            case SQUARED_CORRELATION:
                double score = correlationCoefficient(valsFixedBlock, valsMovingBlock);
                return (score * score);
            case MEANSQUARE:
                return -1 * meanSquareDifference(valsFixedBlock, valsMovingBlock);
            default:
                return -10E10;
        }
    }

    /**
     * Mean square difference.
     *
     * @param X the x
     * @param Y the y
     * @return the double
     */
    double meanSquareDifference(double[] X, double[] Y) {
        if (X.length != Y.length) {
            IJ.log("In meanSquareDifference in BlockMatching, blocks length does not match");
            return 1E8;
        }
        double sum = 0;
        double diff;
        int n = X.length;
        for (int i = 0; i < n; i++) {
            diff = X[i] - Y[i];
            sum += (diff * diff);
        }
        return (sum / n);
    }

    /**
     * Helper functions for trimming the correspondences.
     *
     * @param list                the list
     * @param voxSizes            the vox sizes
     * @param percentageKeepScore the percentage keep score
     * @param percentageKeepLTS   the percentage keep LTS
     * @param transform           the transform
     * @return the correspondance list as trimmed point array
     */
    @SuppressWarnings("unchecked")
    Object[] getCorrespondanceListAsTrimmedPointArray(ArrayList<double[][]> list, double[] voxSizes, int percentageKeepScore, int percentageKeepLTS, ItkTransform transform) {
        //Convert voxel space correspondances in real space vectors
        boolean isLTS = (transform != null);
        int n = list.size();
        int ind = 0;
        double distance = 0;
        for (int i = 0; i < n; i++) if (list.get(i)[2][1] < 0) n--;
        Point3d[][] tabPt = new Point3d[3][n];
        for (int i = 0; i < n; i++)
            if (list.get(i)[2][1] > 0) {
                double[][] tabInit = list.get(i);
                tabPt[0][ind] = new Point3d((tabInit[0][0]) * voxSizes[0], (tabInit[0][1]) * voxSizes[1], (tabInit[0][2]) * voxSizes[2]);
                tabPt[1][ind] = new Point3d((tabInit[1][0]) * voxSizes[0], (tabInit[1][1]) * voxSizes[1], (tabInit[1][2]) * voxSizes[2]);
                //If Least Trimmed Squared selection activated, compute distance from reference to moving using transform
                if (isLTS) {
                    Point3d pt0Trans = transform.transformPoint(tabPt[0][ind]);
                    distance = TransformUtils.norm(new double[]{pt0Trans.x - tabPt[1][ind].x, pt0Trans.y - tabPt[1][ind].y, pt0Trans.z - tabPt[1][ind].z});
                }
                tabPt[2][ind] = new Point3d((tabInit[2][0]), tabInit[2][1], -distance);
                ind++;
            }
        //Compute mean val of the selection variable, before selecting
        double meanVar = 0;
        for (int i = 0; i < tabPt[0].length; i++) meanVar += (isLTS ? tabPt[2][i].z : tabPt[2][i].x);
        double meanBef = meanVar / tabPt[0].length;


        //Reorganize tab for the sorting
        Point3d[][] tmp = new Point3d[tabPt[0].length][3];
        for (int i = 0; i < tabPt.length; i++) for (int j = 0; j < tabPt[0].length; j++) tmp[j][i] = tabPt[i][j];

        //Sort and reorganize back
        if (isLTS) Arrays.sort(tmp, new PointTabComparatorByDistanceLTS());
        else Arrays.sort(tmp, new PointTabComparatorByScore());
        for (int i = 0; i < tabPt.length; i++) for (int j = 0; j < tabPt[0].length; j++) tabPt[i][j] = tmp[j][i];

        //Keep this.percentageBlocksSelectedByVariance
        int lastRemoval = (int) Math.round(tabPt[0].length * ((100 - (isLTS ? percentageKeepLTS : percentageKeepScore)) / 100.0));
        Point3d[][] ret = new Point3d[3][tabPt[0].length - lastRemoval];
        ArrayList<double[][]> listRet = new ArrayList<double[][]>();
        for (int bl = lastRemoval; bl < tabPt[0].length; bl++) {
            ret[0][bl - lastRemoval] = tabPt[0][bl];
            ret[1][bl - lastRemoval] = tabPt[1][bl];
            ret[2][bl - lastRemoval] = tabPt[2][bl];
            listRet.add(new double[][]{{ret[0][bl - lastRemoval].x / voxSizes[0], ret[0][bl - lastRemoval].y / voxSizes[1], ret[0][bl - lastRemoval].z / voxSizes[2]},
                    {ret[1][bl - lastRemoval].x / voxSizes[0], ret[1][bl - lastRemoval].y / voxSizes[1], ret[1][bl - lastRemoval].z / voxSizes[2]},
                    {ret[2][bl - lastRemoval].x, ret[2][bl - lastRemoval].y, ret[2][bl - lastRemoval].z}});
        }

        //Compute mean val of the selection variable, after selecting
        meanVar = 0;
        for (int i = 0; i < ret[0].length; i++) {
            meanVar += (isLTS ? ret[2][i].z : ret[2][i].x);
        }
        if (isLTS)
            handleOutput("   Mean resulting distance between transformed corresponding points before trimming / after trimming : " + VitimageUtils.dou(-meanBef) + " / " + VitimageUtils.dou(-meanVar / (ret[0].length)));
        else
            handleOutput("   Mean correspondance score before / after : " + VitimageUtils.dou(meanBef) + " / " + VitimageUtils.dou(meanVar / (ret[0].length)));
        return new Object[]{ret, meanVar / (ret[0].length), listRet};


    }

    public ImagePlus displayDistanceMapOnGrid(ItkTransform tr, ImagePlus grid) {
        ImagePlus distMap = tr.distanceMap(imgRef, true);//goes from 0 to 1, with a lot between 0 and 0.1
        double factorMult = 70;
        double factorAdd = 7.5;
        IJ.run(distMap, "Log", "");//goes from -inf to 0, with a lot between -inf and -2
        IJ.run(distMap, "Add...", "value=" + factorAdd);//goes from -inf to 10, with a lot between -inf and 8
        IJ.run(distMap, "Multiply...", "value=" + factorMult);//goes from -inf to 250, with a lot between -inf and 200
        distMap.setDisplayRange(0, 255);
        distMap = VitimageUtils.convertToFloat(distMap);
        distMap = VitimageUtils.convertFloatToByteWithoutDynamicChanges(distMap);
        distMap = VitimageUtils.convertToFloat(distMap);

        ImagePlus gridTemp = grid.duplicate();
        gridTemp = VitimageUtils.convertToFloat(gridTemp);

        ImagePlus maskGrid = VitimageUtils.makeOperationOnOneImage(gridTemp, 2, 1 / 255.0, true);
        ImagePlus gridResidual = VitimageUtils.makeOperationOnOneImage(gridTemp, 2, 1 / 15.0, true);
        ImagePlus test = VitimageUtils.makeOperationBetweenTwoImages(maskGrid, distMap, 2, true);
        ImagePlus maskRoot = VitimageUtils.getBinaryMask(plongement(this.imgRef, this.rM, false), 10);
        ImagePlus maskOutRoot = VitimageUtils.gaussianFiltering(maskRoot, 10, 10, 0);
        maskOutRoot = VitimageUtils.getBinaryMaskUnary(maskOutRoot, 4);

//		maskRoot.duplicate().show();
//		VitimageUtils.waitFor(10000);
        test = VitimageUtils.makeOperationBetweenTwoImages(test, maskRoot, 1, true);
        test = VitimageUtils.makeOperationBetweenTwoImages(test, maskOutRoot, 2, true);
        test = VitimageUtils.makeOperationBetweenTwoImages(test, gridResidual, 1, true);
        test.setDisplayRange(0, 255);
        test = VitimageUtils.convertToFloat(test);
        test = VitimageUtils.convertFloatToByteWithoutDynamicChanges(test);
        IJ.run(test, "Fire", "");
        distMap.setDisplayRange(0, 255);
        ImagePlus maskOutGrid = VitimageUtils.invertBinaryMask(maskGrid);
        ImagePlus finalMapOfGrid = VitimageUtils.makeOperationBetweenTwoImages(maskGrid, gridTemp, 2, true);
        ImagePlus finalMapOfOutGrid = VitimageUtils.makeOperationBetweenTwoImages(maskOutGrid, distMap, 2, true);
        distMap = VitimageUtils.makeOperationBetweenTwoImages(finalMapOfGrid, finalMapOfOutGrid, 1, true);
		/*finalMapOfOutGrid.duplicate().show();
		finalMapOfGrid.duplicate().show();
		VitimageUtils.waitFor(20000000);*/
//		ImagePlus maskOutGrid=VitimageUtils.getBinaryMaskUnary(gridTemp, 1);

        distMap.setDisplayRange(0, 255);
        distMap = VitimageUtils.convertToFloat(distMap);
        distMap = VitimageUtils.convertFloatToByteWithoutDynamicChanges(distMap);
        IJ.run(distMap, "Fire", "");
        //distMap.duplicate().show();
        //VitimageUtils.waitFor(50000);

        return test/*distMap*/;
    }

}

@SuppressWarnings("rawtypes")
class VarianceComparator implements java.util.Comparator {
    public int compare(Object o1, Object o2) {
        return Double.compare(((double[]) o1)[0], ((double[]) o2)[0]);
    }
}

class PointTabComparatorByDistanceLTS implements java.util.Comparator {
    public int compare(Object o1, Object o2) {
        return Double.compare(((Point3d[]) o1)[2].z, ((Point3d[]) o2)[2].z);
    }
}

/**
 * Comparators used for sorting data when trimming by score, variance or distance to computed transform
 */
@SuppressWarnings("rawtypes")
class PointTabComparatorByScore implements java.util.Comparator {
    public int compare(Object o1, Object o2) {
        return Double.compare(((Point3d[]) o1)[2].x, ((Point3d[]) o2)[2].x);
    }
}
