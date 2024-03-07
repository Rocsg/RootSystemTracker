package io.github.rocsg.rootsystemtracker;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.RGBStackMerge;
import io.github.rocsg.fijiyama.common.VitiDialogs;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import io.github.rocsg.fijiyama.rsml.FSR;
import io.github.rocsg.fijiyama.rsml.Root;
import io.github.rocsg.fijiyama.rsml.RootModel;

import java.io.File;

public class RstBox {

    private final String path;
    private RootModel rootModel;
    private int Nt = 0;
    private ImagePlus imgReg = null;

    // Constructor of box object
    public RstBox(String path) {
        // If no path is provided, ask one to the user
        if (path == null || path.length() == 0)
            path = VitiDialogs.chooseDirectoryNiceUI("Choose dir box", "OK");

        // This line is for not being in trouble with windows weird path
        path = path.replace("\\", "/");
        this.path = path;

        // Verify if the path is an actual directory
        File f = new File(path);
        if (!f.isDirectory()) {
            IJ.showMessage("Error, the path provided is not a directory");
            return;
        }

        // Verify if the processing went to the step 65, i.e. there is a file named
        // 65_times.tif
        File f2 = new File(path + "/65_times.tif");
        if (!f2.exists()) {
            IJ.showMessage("Error, no file 65_times, meaning the processing is not finished");
            return;
        }

        // Catch the number of slices in the image 22_registered_stack.tif
        this.imgReg = IJ.openImage(path + "/22_registered_stack.tif");
        this.Nt = imgReg.getNSlices();

        // Initialize the object FSR, required for reading RSML models
        FSR sr = new FSR();
        sr.initialize();

        if (new File(path, "61_graph_expertized.rsml").exists()) {
            rootModel = RootModel.RootModelWildReadFromRsml(
                    new File(path, "61_graph_expertized.rsml").getAbsolutePath().replace("\\", "/"));
        } else if (new File(path, "61_graph.rsml").exists()) {
            rootModel = RootModel
                    .RootModelWildReadFromRsml(new File(path, "61_graph.rsml").getAbsolutePath().replace("\\", "/"));
        } else {
            IJ.showMessage("Error, no rsml file found in the box");
            return;
        }
        rootModel.resampleFlyingRoots();
    }

    public static void main(String[] args) {
        // Test function. Create an object, use the main functions
        ImageJ ij = new ImageJ();
        demo();

    }

    public static void demo() {

        /*
         * import ij.IJ;
         * import ij.ImageJ;
         * import ij.ImagePlus;
         * import ij.plugin.RGBStackMerge;
         * import io.github.rocsg.fijiyama.common.VitiDialogs;
         * import io.github.rocsg.fijiyama.common.VitimageUtils;
         * import io.github.rocsg.fijiyama.rsml.FSR;
         * import io.github.rocsg.fijiyama.rsml.Root;
         * import io.github.rocsg.fijiyama.rsml.RootModel;
         * import io.github.rocsg.rootsystemtracker.RstBox;
         */
        RstBox currentBox = null;
        int usecase = 2;

        if (usecase == 1) {
            // Test1, with split root
            currentBox = new RstBox(
                    "/home/rfernandez/Bureau/A_Test/RootSystemTracker/TestSplit/Processing_of_TEST1230403-SR-split" +
                            "/230403SR055");
        }
        if (usecase == 2) {
            // Test2, with standard box
            currentBox = new RstBox("/home/rfernandez/Bureau/A_Test/RootSystemTracker/ML1_Boite00001");
        }
        // Use the object to create a display of the box with the model through time
        ImagePlus imgView = currentBox.getImageOfTheRootModel();
        imgView.show();
        // Uncomment this if you want to wait for 5 seconds, then close the view
        // automatically
        // VitimageUtils.waitFor(5000);
        // imgView.close();

        // Extract the rsml model
        RootModel currentRootModel = currentBox.getRootModel();

        // In case the original rsml was messy for investigations (root order or things
        // like this, write a new version (it will be ordered, prim from left to right,
        // and lat from top to bottom)
        currentRootModel.writeRSML3D(
                new File("/home/rfernandez/Bureau/rsmlTest.rsml").getAbsolutePath().replace("\\", "/"), "", true,
                false);

        // Get the nb of laterals per plant
        int[] tab = currentRootModel.nbLatsPerPlant();
        // Display it in the console
        for (int i = 0; i < tab.length; i++)
            System.out.println("Plant " + i + " has " + tab[i] + " lateral roots");

        // Get the leftmost plant primary root
        Root primOfPlant0 = currentRootModel.getPrimaryRootOfPlant(0);// Can be used from plant 0 to plant 4
        // Compute some traits about it, and get it as an array [depth /
        // length][timepoint from 0 to Nt]
        double[][] traitsOfPrim0 = currentRootModel.getTipDepthAndRootLengthOverTimesteps(primOfPlant0);

        // Display the traits in the console
        System.out.println("\nNow displaying tip depth and total length of primary of plant 0 over time");
        for (int j = 0; j < traitsOfPrim0[0].length; j++) {
            System.out.print("At timepoint " + j + " : [");
            for (int i = 0; i < 2; i++) {
                System.out.print(traitsOfPrim0[i][j] + "  ");
            }
            System.out.println("]");
        }

        // Save the traits of the primary as a CSV file
        VitimageUtils.writeDoubleTabInCsv(traitsOfPrim0, "/home/rfernandez/Bureau/traitsTestPrimPlant0.csv");

        // Get the rightmost plant primary root
        Root primOfPlant4 = currentRootModel.getPrimaryRootOfPlant(4);
        // Get its children as an array of roots
        Root[] lateralsOfPlant4 = primOfPlant4.getChildsRootAsRootArray();
        int nbLatPlant4 = lateralsOfPlant4.length;
        // Print the first and the last laterals (ordered by insertion depth)
        System.out.println("\nNow displaying First and last lateral of plant 4");
        System.out.println(lateralsOfPlant4[0]);
        System.out.println(lateralsOfPlant4[nbLatPlant4 - 1]);

        // Compute some traits about it, and get it as an array [depth /
        // length][timepoint from 0 to Nt]
        double[][] traitsOfFirstLatOfPlant4 = currentRootModel
                .getTipDepthAndRootLengthOverTimesteps(lateralsOfPlant4[0]);

        // Display the traits in the console
        System.out.println("\nNow displaying tip depth and total length of first lateral of plant 4 over time");
        for (int j = 0; j < traitsOfFirstLatOfPlant4[0].length; j++) {
            System.out.print("At timepoint " + j + " : [");
            for (int i = 0; i < 2; i++) {
                System.out.print(traitsOfFirstLatOfPlant4[i][j] + "  ");
            }
            System.out.println("]");
        }

        // Save the traits of the primary as a CSV file
        VitimageUtils.writeDoubleTabInCsv(traitsOfFirstLatOfPlant4,
                "/home/rfernandez/Bureau/traitsTestFirstLatOfPlant4.csv");

    }

    // Demo function for getting the length of the primary roots of a box with
    // respect to the successive time points
    public void demo1() {

        rootModel.writeRSML3D(new File("/home/rfernandez/Bureau/rsmlTest.rsml").getAbsolutePath().replace("\\", "/"),
                "", true, false);
        // Display some information about the Root System
        for (Root r : rootModel.rootList) {
            System.out
                    .println(r.order + " " + r.plantNumber + (r.getParent() != null ? r.getParent().plantNumber : ""));
        }

        // Get the nb of laterals per plant
        int[] tab = this.rootModel.nbLatsPerPlant();
        // Display it in the console
        for (int i = 0; i < tab.length; i++)
            System.out.println(tab[i]);

        // Get the leftmost plant primary root
        Root primOfPlant0 = this.rootModel.getPrimaryRootOfPlant(0);

        // Compute some traits about it, and get it as an array [trait from 0 to
        // 2][timepoint from 0 to Nt-1]
        double[][] traitsOfPrim0 = this.rootModel.getTipDepthAndRootLengthOverTimesteps(primOfPlant0);

        // Display the traits in the console
        for (int j = 0; j < traitsOfPrim0[0].length; j++) {
            System.out.print("At timepoint " + j + " : [");
            for (int i = 0; i < 2; i++) {
                System.out.print(traitsOfPrim0[i][j] + "  ");
            }
            System.out.println("]");
        }

        // Save the traits of the primary as a CSV file
        VitimageUtils.writeDoubleTabInCsv(traitsOfPrim0, "/home/rfernandez/Bureau/traitsTest.csv");

    }

    public RootModel getRootModel() {
        return this.rootModel;
    }

    // Method for viewing the superposition of the image and the model, in the way
    // that io.github.rocsg.fijiyama.rsml.RsmlExpert.java does
    public ImagePlus getImageOfTheRootModel() {
        // TODO Auto-generated method stub
        return projectRsmlOnImage(rootModel);
    }

    public ImagePlus projectRsmlOnImage(RootModel rm) {
        ImagePlus[] tabRes = new ImagePlus[Nt];
        ImagePlus[] tabReg = VitimageUtils.stackToSlices(imgReg);

        for (int i = 0; i < Nt; i++) {
            ImagePlus imgRSML = rootModel.createGrayScaleImageWithTime(imgReg, 1, false, (i + 1), true,
                    new boolean[]{true, true, true, false, true}, new double[]{2, 2});
            imgRSML.setDisplayRange(0, Nt + 3);
            tabRes[i] = RGBStackMerge.mergeChannels(new ImagePlus[]{tabReg[i], imgRSML}, true);
            IJ.run(tabRes[i], "RGB Color", "");
        }
        ImagePlus res = VitimageUtils.slicesToStack(tabRes);
        String chain = getBoxName();
        String nom = "Model_of_box_" + chain;
        res.setTitle(nom);
        return res;
    }

    public String getBoxName() {
        String[] tab;
        if (!this.path.contains("\\")) {
            tab = this.path.split("/");
        } else {
            tab = this.path.split("\\\\");
        }
        return tab[tab.length - 1];
    }
}