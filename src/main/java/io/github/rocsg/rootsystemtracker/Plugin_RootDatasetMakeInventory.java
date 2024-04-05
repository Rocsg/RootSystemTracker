package io.github.rocsg.rootsystemtracker;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.frame.PlugInFrame;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import io.github.rocsg.fijiyama.common.VitiDialogs;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import io.github.rocsg.rstutils.QRcodeReader;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.stream.Stream;

/**
 * @author rfernandez
 * This class is a trial to simplify both handling and processing data
 * from unstructured or structured datasets, possibly containing QR
 * codes
 * The goal is that, at then end there is :
 * 1) An initial directory with the original dataset. In it, possibly
 * structured or not structured data, can be dirs of a specimen with a
 * series of image, or dirs, and subdirs, and subsubdirs, and at the
 * end, images
 * 2) Just along, the same but with Inventory_of_*, with :
 * A_Main_inventory.csv , describing global informations about the
 * experiment (starting and ending observation times, number of
 * different objects considered, number of images, list of specimens
 * with link to the csv)
 * boite001.csv, boite002.csv, each one containing
 * num obs date hour hours (double) since series start
 * relative-path-to-the-observation
 * 3) A processing directory (run by RootSystemTracker) with in it
 * InfoRootSystemTracker.csv with some informations, and especially the
 * path to the Inventory_of_* directory
 * Boitetruc
 * Boitemachin
 * Boitechose
 * <p>
 * <p>
 * When starting, we provide to RST, either a InfoRootSystemTracker.csv
 * file, or the initial directory with the original dataset.
 * In the first case, it goes on with it.
 * In the second case, it builds an inventory, then ask for building a
 * processing dir, and then build the csv and the arborescence, then
 * starts processing
 * <p>
 * To go from V1 to V2, there will be a need to create an inventory for
 * files, and to change a bit the output RSML
 */

public class Plugin_RootDatasetMakeInventory extends PlugInFrame {
    // Constant for code not found
    public final static String codeNotFound = "NOT_FOUND";
    // Constant for trash code
    public final static String codeTrash = "TRASH";

    // Serial version UID for serialization
    private static final long serialVersionUID = 1L;
    // Version flag
    public static String versionFlag = "Handsome honeysuckle   2022-07-07 09:45";
    // Flag for developer mode
    public boolean developerMode = false;
    // Current RST flag
    public String currentRstFlag = "1.0";

    //// Constructors

    /**
     * Default constructor for Plugin_RootDatasetMakeInventory.
     * Initializes the superclass with an empty string.
     */
    public Plugin_RootDatasetMakeInventory() {
        super("");
    }

    /**
     * Overloaded constructor for Plugin_RootDatasetMakeInventory.
     * Initializes the superclass with the provided argument.
     *
     * @param arg The argument to pass to the superclass constructor.
     */
    public Plugin_RootDatasetMakeInventory(String arg) {
        super(arg);
    }

    //// Launch method

    /**
     * This method creates an inventory of the input directory.
     *
     * @param inputDir The directory to create an inventory of.
     * @return The path to the output directory.
     */
    public static String makeInventory(String inputDir) {
        // Log the start of the inventory process
        IJ.log("Starting inventory in Plugin_RootData");

        // Define the output directory
        String outputDir = new File(new File(inputDir).getParent(), "Inventory_of_" + (new File(inputDir).getName()))
                .getAbsolutePath().replace("\\", "/");

        // Get the user's choice for the type of directory
        //// 1 for a data input dir with subdirs containing image series
        //// 2 for a messy bunch of dirs and subdirs containing images (tif, png, or
        // jpg), each one with a QR code
        int choice = VitiDialogs.getIntUI(
                "Select 1 for a data input dir with subdirs containing image series, 2 for a messy bunch of dirs and " +
						"subdirs containing images (tif, png, or jpg), each one with a QR code",
                1);

        // If the choice is not valid, stop the process and return null
        if (choice < 1 || choice > 2) {
            IJ.showMessage("Critical fail : malicious choice (" + choice + "). Stopping now.");
            return null;
        }

        // Define the expected paths for the inventory and processing directories
        String expectedInventoryPath = new File(new File(inputDir).getParent(),
                "Inventory_of_" + (new File(inputDir).getName())).getAbsolutePath().replace("\\", "/"); //
		// Inventory_of_*
        String expectedProcessingPath = new File(new File(inputDir).getParent(),
                "Processing_of_" + (new File(inputDir).getName())).getAbsolutePath().replace("\\", "/"); //
		// Processing_of_*

        // If the inventory or processing directory already exists, stop the process and
        // return an empty string
        if (new File(expectedInventoryPath).exists()) {
            IJ.showMessage("Directory already exists ! Please remove previous " + expectedInventoryPath);
            return "";
        }
        if (new File(expectedProcessingPath).exists()) {
            IJ.showMessage("Directory already exists ! Please remove previous " + expectedProcessingPath);
            return "";
        }

        // Create the inventory and processing directories
        new File(new File(new File(inputDir).getParent(), "Inventory_of_" + (new File(inputDir).getName()))
                .getAbsolutePath().replace("\\", "/")).mkdirs();
        new File(new File(new File(inputDir).getParent(), "Processing_of_" + (new File(inputDir).getName()))
                .getAbsolutePath().replace("\\", "/")).mkdirs();

        // Start the inventory process based on the user's choice
        if (choice == 1)
            Plugin_RootDatasetMakeInventory.startInventoryOfAlreadyTidyDir(inputDir, outputDir);
        if (choice == 2)
            Plugin_RootDatasetMakeInventory.startInventoryOfAMessyDirButAllTheImagesContainQRCodes(inputDir, outputDir);

        // Return the output directory
        return outputDir;
    }

    //// Inventory methods

    /**
     * This method starts the inventory process for a directory that contains a
     * messy
     * bunch of directories and subdirectories, each containing images with QR
     * codes.
     *
     * @param inputDir  The directory to create an inventory of.
     * @param outputDir The directory where the inventory will be stored.
     */
    public static void startInventoryOfAMessyDirButAllTheImagesContainQRCodes(String inputDir, String outputDir) {
        // Log the start of the inventory process
        IJ.log("Starting startInventoryOfAMessyDirButAllTheImagesContainQRCodes in Plugin_RootData");
        // Initialize the sum of parameters and the number of images
        double[] sumParams = new double[]{0, 0, 0, 0, 0, 0};
        int did = 0;

        // aggregate a list of relative path to all image files
        IJ.log("01 startInventoryOfAMessyDirButAllTheImagesContainQRCodes in Plugin_RootData");
        int patience = VitiDialogs.getIntUI("Please indicate your patience (in seconds) for each QR code detection",
                10);

        String[] allImgsPath = getRelativePathOfAllImageFilesInDir(inputDir);
        allImgsPath = sortFilesByModificationOrder(inputDir, allImgsPath);
        int NP = allImgsPath.length;
        String[] code = new String[NP];
        boolean reverse = VitiDialogs.getYesNoUI("Are the image mirrored ?", "Is mirrored ?");
        // double[]paramsQRcode=new double[] {4.0,472.0,2916,668,15.8,142.2};
        double[] paramsQRcode = askQRcodeParams(
                new File(inputDir, allImgsPath[(Math.min(allImgsPath.length, 6))]).getAbsolutePath(), reverse);
        IJ.log("02 startInventoryOfAMessyDirButAllTheImagesContainQRCodes in Plugin_RootData");

        // Initialize aggregator with original value
        for (int i = 0; i < paramsQRcode.length; i++)
            sumParams[i] += 10 * paramsQRcode[i];
        did += 10;
        int nNot = 0;
        IJ.log("03 startInventoryOfAMessyDirButAllTheImagesContainQRCodes in Plugin_RootData");

        IJ.log("Got QR params from user : ");
        for (double d : paramsQRcode)
            System.out.println(d);
        // decode the qr code of each image
        IJ.log("04 startInventoryOfAMessyDirButAllTheImagesContainQRCodes in Plugin_RootData");
        for (int n = 0; n < NP; n++) {
            for (int i = 0; i < paramsQRcode.length; i++)
                paramsQRcode[i] = sumParams[i] / did;
            double ratio = VitimageUtils.dou((did - 10) / (1.0 * did));
            IJ.log("\nNow decoding " + n + "/" + NP);

            IJ.log("...Opening " + allImgsPath[n]);
            ImagePlus img = IJ.openImage(new File(inputDir, allImgsPath[n]).getAbsolutePath());
            IJ.log(" ok.");
            IJ.log("Starting decoding with params inferred from user = " + VitimageUtils.dou(100 * (1 - ratio)) + " % "
                    + " . Inferred from data = " + VitimageUtils.dou(100 * ratio) + " % ");
            Object[] objs = QRcodeReader.decodeQRCodeRobust(img, reverse, (int) paramsQRcode[0], paramsQRcode[1],
                    paramsQRcode[2], paramsQRcode[3], paramsQRcode[4], paramsQRcode[5], patience);
            code[n] = (String) objs[0];
            double[] params = (double[]) objs[1];
            if (code[n].length() < 1 || code[n] == null) {
                code[n] = codeNotFound;
                nNot++;
            } else {
                for (int i = 0; i < params.length; i++)
                    sumParams[i] += params[i];
                did++;
            }
        }

        // extract the set of these qr codes by alphanumeric order
        Set<String> setNames = new HashSet<String>(Arrays.asList(code));

        String[] spec = setNames.toArray(new String[setNames.size()]);
        IJ.log("N specimens = " + spec.length);
        Arrays.sort(spec);
        int N = spec.length;

        // Process not found
        if (nNot > 0) {
            IJ.showMessage("Some QR codes have not been read.");
            IJ.log("DebCici Debug phase starts for Cici (in rootsystemtracker/Pluging_RootDatasetMakeInventory.java, " +
					"Line ~179");
            IJ.log("DebCici Here are additional information");
            IJ.log("DebCici The total number of detected image files is " + NP);
            IJ.log("DebCici Over these images, " + nNot + " images were too messy that the QR code could be read");
            IJ.log("DebCici For the images that have been read, I read the patterns of box names, and detected " + N
                    + " different boxes");
            IJ.showMessage("Now I will display the list of codes detected, as a reference for cleaning");
            for (String s : spec) IJ.log(s);
            IJ.selectWindow("Log");
            IJ.showMessage(
                    "Please set the ImageJ log window somewhere on you screen in order to be able to inspect it.");
            IJ.showMessage("I will show you all the images with QR code not found. With each image come a popup.");
            IJ.showMessage(
                    "For each image, find the corresponding code in the list (see log window), and copy it into the " +
							"prompt popup.");
            for (int i = 0; i < code.length; i++)
                if (code[i].equals(codeNotFound)) {
                    IJ.log("DebCici Now reading the image number " + i + " whose code was not found");
                    int subFactor = (int) paramsQRcode[0];
                    IJ.log("DebCici the subsampling for reading the QR was " + subFactor);
                    String guessedCode = guessCode(spec,
                            new File(inputDir, allImgsPath[i]).getName().replace("\\", "/"));
                    IJ.log("DebCici the guessedCode based on filename patterns and box names patterns is "
                            + guessedCode);

                    if (inputDir.contains("230403-SR-split")) {
                        IJ.log("DebCici activating the exp quick hack, and force-applying this code as the robot went" +
								" OK. It s over for this image.");
                        code[i] = guessedCode;
                        continue;
                    } // Hack for the first split serie that had perfect robot run but incorrect QR
                    // positioning
                    else {
                        IJ.log("DebCici no force-apply of code. If you are Cici working on split, that should not " +
								"happen, you should make a claim");
                    }

                    IJ.log("DebCici Cici, if you go to there and this message happens, we have a problem. You should " +
							"check if you kept the naming of the input dir. It have to contain 230403-SR-split");
                    ImagePlus img = IJ
                            .openImage(new File(inputDir, allImgsPath[i]).getAbsolutePath().replace("\\", "/"));
                    img = VitimageUtils.resize(img, img.getWidth() / subFactor, img.getHeight() / subFactor, 1);
                    if (reverse)
                        IJ.run(img, "Flip Horizontally", "");
                    img.show();
                    guessedCode = guessCode(spec, new File(inputDir, allImgsPath[i]).getName());
                    String newCode = VitiDialogs.getStringUI("Give the code (" + i + "/" + code.length + ")",
                            "Guess (change if needed):", guessedCode, false);
                    img.changes = false;
                    img.close();
                    code[i] = newCode;
                }

            IJ.log("DebCici Cici, all codes are now known");

            setNames = new HashSet<String>(Arrays.asList(code));
            spec = setNames.toArray(new String[setNames.size()]);
            IJ.log("N specimens = " + spec.length);
            Arrays.sort(spec);
            N = spec.length;
        }

        IJ.log("DebCici now I will write the CSV files in order to finish the inventory. The A_Main_inventory CSV " +
				"will be a CSV with size "
                + (N + 7) + " x " + (3));

        int header = 7;
        FileTime first = null;
        FileTime last = null;
        String[][] mainCSV = new String[N + header][3];

        mainCSV[2] = new String[]{"Number of different objects", "NA", "NA"};
        mainCSV[3] = new String[]{"Number of different images", "NA", "NA"};
        mainCSV[4] = new String[]{"Data dir", inputDir, "NA"};
        mainCSV[5] = new String[]{"Inventory dir", outputDir, "NA"};
        mainCSV[6] = new String[]{"Misc ", "NA", "NA"};
        IJ.log("DebCici main info is collected. Now processing box after box. Nboxes=" + N);

        for (int n = 0; n < N; n++) {
            IJ.log("DebCici processing box " + n);
            System.out.println(n);
            mainCSV[7 + n] = new String[]{"Object", "" + n, spec[n]};
            // Get the list of all images reporting this QR code. They will come by
            // acquisition order, according to the original order of the list
            ArrayList<String> liObs = new ArrayList<String>();
            for (int i = 0; i < NP; i++)
                if (code[i].equals(spec[n]))
                    liObs.add(allImgsPath[i]);
            String[] obs = liObs.toArray(new String[liObs.size()]);
            IJ.log("DebCici a little up");

            int Nobj = obs.length;
            String[][] objCSV = new String[Nobj + 1][4];
            String pathDir = new File(inputDir).getAbsolutePath().replace("\\", "/");
            String path0 = new File(pathDir, obs[0]).getAbsolutePath().replace("\\", "/");
            objCSV[0] = new String[]{"Num_obs", "DateThour(24h-format)", "Hours_since_series_start",
                    "Relative_path_to_the_img"};
            IJ.log("DebCici a little up again");
            for (int no = 0; no < Nobj; no++) {
                IJ.log("DebCici proceeding this box' image number " + no);
                String path = new File(pathDir, obs[no]).getAbsolutePath().replace("\\", "/");
                FileTime ft = getLastModifiedTime(path);
                String rtd = new File(inputDir).getAbsolutePath().replace("\\", "/");
                IJ.log("DebCici tiny up");
                objCSV[no + 1] = new String[]{"" + no, ft.toString(),
                        "" + VitimageUtils.dou(hoursBetween(path0, path)),
                        path.replace(rtd, "").substring(1).replace("\\", "/")};
                if (first == null)
                    first = getLastModifiedTime(path);
                if (last == null)
                    last = getLastModifiedTime(path);
                if (first.compareTo(ft) == 1)
                    first = getLastModifiedTime(path);
                if (last.compareTo(ft) == -1)
                    last = getLastModifiedTime(path);
                IJ.log("DebCici last up for this image, it is finished");
            }
            IJ.log("DebCici writing this box CSV");
            VitimageUtils.writeStringTabInCsv2(objCSV,
                    new File(outputDir, spec[n] + ".csv").getAbsolutePath().replace("\\", "/"));
            System.out
                    .println("Written : " + new File(outputDir, spec[n] + ".csv").getAbsolutePath().replace("\\", "/"));
            IJ.log("DebCici ok");
        }
        IJ.log("DebCici writing the main CSV");
        mainCSV[2][1] = "" + N;
        mainCSV[3][1] = "" + NP;
        mainCSV[0] = new String[]{"First observation time", first.toString(), "NA"};
        mainCSV[1] = new String[]{"Last observation time", last.toString(), "NA"};
        VitimageUtils.writeStringTabInCsv2(mainCSV,
                new File(outputDir, "A_main_inventory.csv").getAbsolutePath().replace("\\", "/"));
        System.out.println("Written : " + new File(outputDir, "A_main_inventory.csv").getAbsolutePath());
        IJ.log("DebCici Ok. Inventory should be finished.");
    }

    /**
     * This method finds the longest common substring at the beginning of two
     * strings.
     *
     * @param s1 The first string.
     * @param s2 The second string.
     * @return The longest common substring at the beginning of the two strings.
     */
    public static String commonSubStringAtTheBeginning(String s1, String s2) {
        int maxLength = Math.min(s1.length(), s2.length());
        StringBuilder commonSubstring = new StringBuilder();
        for (int i = 0; i < maxLength; i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                commonSubstring.append(s1.charAt(i)); // Append matching characters to the common substring
            } else {
                break; // Break the loop when a mismatch is found
            }
        }
        return commonSubstring.toString();
    }

    ////// Messy dir inventory

    // Here we go for a messy bunch of dirs with at least QR code. Let's hope we can
    // read them yet.

    /**
     * This method finds the longest common substring at the beginning of all
     * strings in the provided array.
     *
     * @param tab           The array of strings to process.
     * @param ignoreLastOne A flag indicating whether to ignore the last string in
     *                      the array.
     * @return The longest common substring at the beginning of the strings.
     */
    public static String commonSubStringAtTheBeginning(String[] tab, boolean ignoreLastOne) {
        if ((tab == null) || tab.length < 1) {
            IJ.log("Tab null or not long enough. Return void");
            return "";
        }
        if (tab.length < (1 + (ignoreLastOne ? 1 : 0))) {
            IJ.log("Tab not long enough. Return weird pattern");
            return tab[0].substring(0, tab[0].length() - 3);
        }
        String ret = tab[0];
        for (int i = 1; i < tab.length + (ignoreLastOne ? -1 : 0); i++) {
            IJ.log("Processing " + i + " : " + ret + " against " + tab[i]);
            if ((!tab[i].contains(codeTrash)) && (!tab[i].contains(codeNotFound)))
                ret = commonSubStringAtTheBeginning(ret, tab[i]);
        }
        IJ.log("Found chain " + ret);
        return ret;
    }

    /**
     * This method counts the number of occurrences of a substring in a main string.
     *
     * @param substring  The substring to count occurrences of.
     * @param mainString The main string to search in.
     * @return The number of occurrences of the substring in the main string.
     */
    public static int nbOccurences(String substring, String mainString) {
        if (mainString == null || mainString.length() < 1)
            return 0;
        if (substring == null || substring.length() < 1)
            return 0;
        int count = 0;
        int index = 0;
        boolean found = true;
        while (found) {
            index = mainString.indexOf(substring, index);
            if (index == -1) {
                found = false; // Set found to false when no more occurrences are found
            } else {
                count++;
                index += substring.length();
            }
        }
        return count;
    }

    /**
     * This function is a Dialog utility to get insight from the user about the
     * disposition of the qr codes in images of the dataset.
     * The user draw a rectangle around the qr code in the example image, then the
     * function estimate the parameters to know for performing the qr code mining
     *
     * @param imgPath The path to the example image to get info about the QR code
     * @return the params expected for QR code mining : double[]{int
     * subsamplingFactor (which res to work on), QR size, Xcenter, Ycenter,
     * threshMin, threshMax}
     */
    public static double[] askQRcodeParams(String imgPath, boolean reverse) {
        ImagePlus img = IJ.openImage(imgPath);
        if (reverse)
            IJ.run(img, "Flip Horizontally", "");
        IJ.showMessage("Please draw a rectangle that fits precisely the QRcode, then add to Roi Manager");
        RoiManager rm = RoiManager.getRoiManager();

        // Ask user to draw a rectangle around the QR. Then go on since it is added to
        // RoiManager
        img.show();
        rm.reset();
        IJ.setTool("rectangle");
        boolean finished = false;
        do {
            try {
                java.util.concurrent.TimeUnit.MILLISECONDS.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (rm.getCount() == 1)
                finished = true;
            System.out.println(rm.getCount());
        } while (!finished);

        // get rectangle coordinates
        Roi rect = rm.getRoi(0);
        Rectangle r = rect.getBounds();
        int x0 = r.x;
        int y0 = r.y;
        int dx = r.width;
        int dy = r.height;
        System.out.println(r);

        // Computing the subsampling factor in order to work with an image where the
        // QRcode is at least 116 x 116
        int subsamplingFactor = Math.min(dx, dy) / 116;
        System.out.println("Subs=" + subsamplingFactor);

        // Computing the ranging interval for possible thresholds
        ImagePlus imgCrop = VitimageUtils.cropImage(img, x0, y0, 0, dx, dy, 1);
        img.changes = false;
        img.close();
        ImageProcessor ip = imgCrop.getProcessor();
        ip.setAutoThreshold("Otsu dark");
        double valMin = ip.getMinThreshold();

        // Gathering results
        return new double[]{subsamplingFactor, Math.max(dx, dy), x0 + dx / 2, y0 + dy / 2, valMin * 0.2,
                valMin * 1.8};
    }

    /**
     * This method attempts to guess the QR code for a given image name.
     *
     * @param specs The array of unique QR codes.
     * @param name  The name of the image.
     * @return The guessed QR code.
     */
    public static String guessCode(String[] specs, String name) {
        IJ.log("Guessing code for name=" + name);
        if (specs == null || specs.length < 1)
            return "";
        // Find the common substring at the beginning of all QR codes
        String common = commonSubStringAtTheBeginning(specs, true);
        IJ.log("Common=" + common);
        // Remove the common substring from each QR code
        String[] vals = new String[specs.length];
        for (int i = 0; i < specs.length; i++)
            vals[i] = specs[i].replace(common, "");
        int countMax = 0;
        int indMax = 0;
        // Find the QR code that occurs the most in the image name
        for (int i = 0; i < vals.length; i++) {
            IJ.log("Testing " + i);
            IJ.log("Vals=" + vals[i]);
            IJ.log("nbOcc=" + nbOccurences(vals[i], name));
            if (nbOccurences(vals[i], name) > countMax) {
                countMax = nbOccurences(vals[i], name);
                indMax = i;
            }
        }
        IJ.log("ind=" + indMax);
        IJ.log("Thus" + specs[indMax]);
        if (countMax > 0)
            return specs[indMax];
        else {
            // If no QR code was found, guess from a pattern in the image name
            String[] tab = name.split("Boite 000");
            if (tab == null)
                return specs[indMax];
            if (tab.length < 1)
                return specs[indMax];
            String pat = tab[1].substring(0, 2);
            return (common + pat);
        }
    }

    /**
     * This method starts the inventory process for a directory that is already
     * tidy, meaning it contains subdirectories each containing a series of images.
     * <p>
     * Such that :
     * InputDir
     * |_Object1
     * |__Image1
     * |__Image2
     * |__Image3
     * |_Object2
     * |__Image1
     * |__Image2
     * |__Image3
     *
     * @param inputDir0  The directory to create an inventory of.
     * @param outputDir0 The directory where the inventory will be stored.
     */
    public static void startInventoryOfAlreadyTidyDir(String inputDir0, String outputDir0) {
        String inputDir = inputDir0.replace("\\", "/");
        String outputDir = outputDir0.replace("\\", "/");
        // List the data
        String[] spec = new File(inputDir).list(); // List of subdirectories
        Arrays.sort(Objects.requireNonNull(spec)); // Sort the list of subdirectories
        int N = spec.length; // Count the number of subdirectories
        int header = 7; // Header size for the main CSV file
        FileTime first = null; // First observation time
        FileTime last = null; // Last observation time
        String[][] mainCSV = new String[N + header][3]; // Main CSV file
        mainCSV[2] = new String[]{"Number of different objects", "NA", "NA"}; // Number of different objects
        mainCSV[3] = new String[]{"Number of different images", "NA", "NA"}; // Number of different images
        mainCSV[4] = new String[]{"Data dir", inputDir, "NA"}; // Data directory
        mainCSV[5] = new String[]{"Inventory dir", outputDir, "NA"}; // Inventory directory
        mainCSV[6] = new String[]{"Misc ", "NA", "NA"}; // Miscellaneous
        int incrImg = 0; // Incremental image count
        // For each subdirectory (referred to as an "object"), create a separate CSV

        System.out.println("Recap : " + spec.length + " objects" + " in " + inputDir + " to be processed");
        // file
        for (int n = 0; n < N; n++) {
            // Registered in the old csv
            mainCSV[7 + n] = new String[]{"Object", "" + n, spec[n]};
            String[] obs = new File(inputDir, spec[n]).list();
            System.out.println("File " + new File(inputDir, spec[n]).getAbsolutePath());
            System.out.println("Processing " + spec[n] + " with " + Objects.requireNonNull(obs).length + " images");

            obs = sortFilesByModificationOrder(new File(inputDir, spec[n]).getAbsolutePath(), obs);
            int Nobj = obs.length;
            incrImg += Nobj;

            // Create the new csv
            String[][] objCSV = new String[Nobj + 1][4];
            String pathDir = new File(inputDir, spec[n]).getAbsolutePath(); // Path to the subdirectory
            String path0 = new File(pathDir, obs[0]).getAbsolutePath(); // Path to the first image in the subdirectory
            objCSV[0] = new String[]{"Num_obs", "DateThour(24h-format)", "Hours_since_series_start",
                    "Relative_path_to_the_img"};
            // For each image in the subdirectory, add a row to the CSV file
            for (int no = 0; no < Nobj; no++) {
                String path = new File(pathDir, obs[no]).getAbsolutePath(); //
                FileTime ft = getLastModifiedTime(path);
                String rtd = new File(inputDir).getAbsolutePath();
                objCSV[no + 1] = new String[]{"" + no, ft.toString(),
                        "" + VitimageUtils.dou(hoursBetween(path0, path)),
                        path.replace(rtd, "").substring(1).replace("\\", "/")};
                if (first == null)
                    first = getLastModifiedTime(path);
                if (last == null)
                    last = getLastModifiedTime(path);
                if (first.compareTo(ft) == 1)
                    first = getLastModifiedTime(path);
                if (last.compareTo(ft) == -1)
                    last = getLastModifiedTime(path);
            }
            VitimageUtils.writeStringTabInCsv2(objCSV,
                    new File(outputDir, spec[n] + ".csv").getAbsolutePath().replace("\\", "/")); // Write the CSV file
            // for
            // the subdirectory
        }
        // Update the main CSV file with the total number of objects and images
        mainCSV[0] = new String[]{"First observation time", first.toString(), "NA"};
        mainCSV[1] = new String[]{"Last observation time", last.toString(), "NA"};
        mainCSV[2][1] = "" + N;
        mainCSV[3][1] = "" + incrImg;
        VitimageUtils.writeStringTabInCsv2(mainCSV,
                new File(outputDir, "A_main_inventory.csv").getAbsolutePath().replace("\\", "/"));
        System.out.println("Inventory of tidy dir ok");
    }

    /**
     * This method sorts an array of filenames based on their modification time.
     * The filenames are relative to a parent directory.
     *
     * @param parent The parent directory.
     * @param tab    The array of filenames to sort.
     * @return The sorted array of filenames.
     */
    static String[] sortFilesByModificationOrder(String parent, String[] tab) {
        String rdt = new File(parent).getAbsolutePath(); // Without the / at the end
        String[] ret = Arrays.copyOf(tab, tab.length);
        // Convert the filenames to File objects
        File[] fTab = Arrays.stream(ret).map(s -> new File(parent, s)).toArray(File[]::new);
        // Sort the File objects by their modification time
        Arrays.sort(fTab, Comparator.comparingLong(File::lastModified));
        // Convert the File objects back to filenames
        return (Arrays.stream(fTab)).map(f -> f.getAbsolutePath()).map(s -> s.replace(rdt, "").substring(1))
                .toArray(String[]::new);
    }

    ////// Tidy dir inventory

    /**
     * This method sorts an array of file paths based on their modification time.
     *
     * @param tab The array of file paths to sort.
     * @return The sorted array of file paths.
     */
    static String[] sortFilesByModificationOrder(String[] tab) {
        String[] ret = Arrays.copyOf(tab, tab.length);
        // Convert the file paths to File objects
        File[] fTab = Arrays.stream(ret).map(s -> new File(s)).toArray(File[]::new);
        // Sort the File objects by their modification time
        Arrays.sort(fTab, Comparator.comparingLong(File::lastModified));
        // Convert the File objects back to file paths
        return (Arrays.stream(fTab)).map(f -> f.getAbsolutePath()).toArray(String[]::new);
    }

    /**
     * This method returns the relative paths of all image files in a directory,
     * sorted by modification time.
     *
     * @param rootDir The directory to search in.
     * @return The sorted array of relative paths.
     */
    static String[] getRelativePathOfAllImageFilesInDirByTimeOrder(String rootDir) {
        String rdt = new File(rootDir).getAbsolutePath(); // Without the / at the end
        File[] init = Arrays.stream(searchImagesInDir(rootDir)).map(x -> new File(x)).toArray(File[]::new);
        Arrays.sort(init, Comparator.comparingLong(File::lastModified));
        return (Stream.of(init).map(f -> f.getAbsolutePath())
                .map(s -> s.replace(rdt, "").substring(1).replace("\\", "/"))).toArray(String[]::new);
    }

    /**
     * This method returns the relative paths of all image files in a directory.
     *
     * @param rootDir The directory to search in.
     * @return The array of relative paths.
     */
    static String[] getRelativePathOfAllImageFilesInDir(String rootDir) {
        String rdt = new File(rootDir).getAbsolutePath(); // Without the / at the end
        return Arrays.stream(searchImagesInDir(rootDir)).map(p -> p.replace(rdt, "").substring(1).replace("\\", "/"))
                .toArray(String[]::new);
    }

    /**
     * This method searches a directory for image files.
     *
     * @param rootDir The directory to search in.
     * @return An array of file paths to the image files.
     */
    static String[] searchImagesInDir(String rootDir) {
        try {
            Stream<Path> paths = Files.find(Paths.get(rootDir), Integer.MAX_VALUE,
                    (path, file) -> file.isRegularFile());
            String[] tab = paths.map(p -> p.toString()).filter(s -> isImagePath(s)).toArray(String[]::new);
            paths.close();
            return tab;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * This method checks if a file path points to an image file.
     *
     * @param x The file path to check.
     * @return True if the file path points to an image file, false otherwise.
     */
    public static boolean isImagePath(String x) {
        String[] okFileExtensions = new String[]{"jpg", "jpeg", "png", "tif", "tiff"};
        for (String extension : okFileExtensions) {
            if (x.toLowerCase().endsWith(extension))
                return true;
        }
        return false;
    }

    /**
     * This method calculates the number of hours between the last modification
     * times of two files.
     *
     * @param f1 The first file.
     * @param f2 The second file.
     * @return The number of hours between the last modification times of the two
     * files.
     */
    public static double hoursBetween(File f1, File f2) {
        return hoursBetween(f1.getAbsolutePath(), f2.getAbsolutePath());
    }

    /**
     * This method calculates the number of hours between the last modification
     * times of two files.
     *
     * @param path1 The path to the first file.
     * @param path2 The path to the second file.
     * @return The number of hours between the last modification times of the two
     * files.
     */
    public static double hoursBetween(String path1, String path2) {
        return VitimageUtils
                .dou((getLastModifiedTime(path2).toMillis() - getLastModifiedTime(path1).toMillis()) / (3600 * 1000.0));
    }

    /**
     * This method gets the last modification time of a file.
     *
     * @param path The path to the file.
     * @return The last modification time of the file.
     */
    public static FileTime getLastModifiedTime(String path) {
        try {
            return Files.readAttributes(Paths.get(path), BasicFileAttributes.class).lastModifiedTime();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) {
        // String a = guessCode(new String[] { "DSC01", "DSC02", "DSC03", "DSC88" },
        // "imgcecicela03");
        // System.out.println(a);

        // Test for data dir with subdirs containing image series ressource/data
        String inputDir = "C:\\Users\\loaiu\\OneDrive - Université Nice Sophia " +
				"Antipolis\\MAM5\\S2\\Stage\\Travaux\\Git_clones\\RootSystemTracker\\src\\main\\resources\\data";
        String outputDir = "C:\\Users\\loaiu\\OneDrive - Université Nice Sophia " +
				"Antipolis\\MAM5\\S2\\Stage\\Travaux\\Git_clones\\RootSystemTracker\\src\\main\\resources\\data" +
				"\\output";
        // run function
        IJ.log("Starting RootDatasetMakeInventory version " + versionFlag);

        int choice = VitiDialogs.getIntUI(
                "Select 1 for a data input dir with subdirs containing image series, or 2 for a messy bunch of dirs " +
						"and subdirs containing images (tif, png, or jpg), each one with a QR code describing the " +
						"object ",
                1);
        System.out.println("Choice=" + choice);
        if (choice < 1 || choice > 2) {
            IJ.showMessage("Critical fail : malicious choice (" + choice + "). Stopping now.");
            return;
        }
        inputDir = VitiDialogs.chooseDirectoryNiceUI("Choose this input data dir", "OK").replace("\\", "/");
        System.out.println("Input dir=" + inputDir);

        // outputDir will be the current dir
        outputDir = VitiDialogs.chooseDirectoryNiceUI(
                "Build and choose data output dir. Suggested : next to the first one, with name Inventory_of_(name of" +
						" the original folder)",
                "OK").replace("\\", "/");

        if (new File(outputDir).list().length > 0) {
            IJ.showMessage("Critical fail : output dir is not empty. Stopping now.");
            return;
        }

        switch (choice) {
            case 1:
                startInventoryOfAlreadyTidyDir(inputDir, outputDir);
                break;

            case 2:
                startInventoryOfAMessyDirButAllTheImagesContainQRCodes(inputDir, outputDir);
                break;

            default:
                System.out.println("What to do for the inventory ?");
        }
    }

    /**
     * This method starts the inventory process.
     *
     * @param arg The argument to pass to the startInventory method.
     */
    public void run(String arg) {
        startInventory();
    }

    public void startInventory() {
        IJ.log("Starting RootDatasetMakeInventory version " + versionFlag);

        String inputDir = "";
        if (developerMode)
            inputDir = "/media/rfernandez/DATA_RO_A/Roots_systems/Data_BPMP/Third_dataset_2022_11" +
					"/Source_data_after_renumbering/ML2";
        int choice = VitiDialogs.getIntUI(
                "Select 1 for a data input dir with subdirs containing image series, or 2 for a messy bunch of dirs " +
						"and subdirs containing images (tif, png, or jpg), each one with a QR code describing the " +
						"object ",
                1);
        if (choice < 1 || choice > 2) {
            IJ.showMessage("Critical fail : malicious choice (" + choice + "). Stopping now.");
            return;
        }
        inputDir = VitiDialogs.chooseDirectoryNiceUI("Choose this input data dir", "OK").replace("\\", "/");
        String outputDir = "";
        if (developerMode)
            outputDir = "/media/rfernandez/DATA_RO_A/Roots_systems/Data_BPMP/Third_dataset_2022_11" +
					"/Source_data_after_renumbering/ML2";
        else
            outputDir = VitiDialogs.chooseDirectoryNiceUI(
                    "Build and choose data output dir. Suggested : next to the first one, with name Inventory_of_" +
							"(name of the original folder)",
                    "OK").replace("\\", "/");
        if (new File(outputDir).list().length > 0) {
            IJ.showMessage("Critical fail : output dir is not empty. Stopping now.");
            return;
        }

        switch (choice) {
            case 1:
                startInventoryOfAlreadyTidyDir(inputDir, outputDir);
                break;
            case 2:
                startInventoryOfAMessyDirButAllTheImagesContainQRCodes(inputDir, outputDir);
                break;
            default:
                IJ.log("No chosen inventory");
                break;
        }
    }

}
