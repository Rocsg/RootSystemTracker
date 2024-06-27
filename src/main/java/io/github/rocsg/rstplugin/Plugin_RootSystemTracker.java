package io.github.rocsg.rstplugin;

import ij.IJ;
import ij.ImageJ;
import ij.plugin.frame.PlugInFrame;
import io.github.rocsg.fijiyama.common.VitiDialogs;
import io.github.rocsg.fijiyama.common.VitimageUtils;

import java.io.File;

/**
 * @author rfernandez
 * Paper release : 5f010e7
 */
public class Plugin_RootSystemTracker extends PlugInFrame {

    private static final long serialVersionUID = 1L;
    public static String versionNumber = "v1.6.0";
    public static String versionFlag = "Handsome honeysuckle " + versionNumber + "  2023-04-24 16:27 Ordered RSML";
    public boolean developerMode = false;
    public String currentRstFlag = "1.0";

    public Plugin_RootSystemTracker() {
        super("");
    }

    public Plugin_RootSystemTracker(String arg) {
        super(arg);
    }

    public static void main(String[] args) {
        ImageJ ij = new ImageJ();
        Plugin_RootSystemTracker pl = new Plugin_RootSystemTracker();
        pl.run("Dev ML1");
    }

    public static void startNewExperimentFromInventoryAndProcessingDir(String inventoryDir, String processingDir) {
        PipelineParamHandler pph = new PipelineParamHandler(inventoryDir, processingDir);
        if (isZuluEndangered())
            return;
        PipelineActionsHandler.goOnExperiment(pph);
    }

    public static boolean isZuluEndangered() {
        if (VitimageUtils.isWindowsOS() && System.getProperties().toString().contains("zulu")) {
            IJ.showMessage("You run windows with zulu JDK. We are sorry, but this is unconvenient\n" +
                    " The plugin will close to let you adjust your setup (two operations to make). " + "\nTo do so, please check the windows installation instructions on the plugin page" +
                    "\nhttps://imagej.net/plugins/fijiyama (or find it by googling Fijiyama imagej");
            return true;
        }
        IJ.log("\nZulu check ok\n\n");
        return false;
    }

    public void run(String arg) {
        IJ.log("Starting RootSystemTracker version " + versionFlag);
        if (arg.equals("Dev ML1")) {
            String racine = "/data/Box1";
            String processingDir = racine + "/Processing/ML1";
            // startNewExperimentFromInventoryAndProcessingDir(inventoryDir,processingDir);
            goOnPipelineFromProcessingDir(processingDir);
        } else if (arg.equals("Dev ML2")) {
            String racine = "/media/rfernandez/DATA_RO_A/Roots_systems/BPMP/Third_dataset_2022_11";
            String processingDir = racine + "/Processing/ML2";
            goOnPipelineFromProcessingDir(processingDir);
        } else if (arg.equals("Dev QR")) {
            String racine = "/media/rfernandez/DATA_RO_A/Roots_systems/BPMP/Third_dataset_2022_11";
            String inventoryDir = racine + "/Source_data/Inventory_of_221125-CC-CO2";
            String processingDir = racine + "/Processing/221125-CC-CO2";
            startNewExperimentFromInventoryAndProcessingDir(inventoryDir, processingDir);
        } else if (arg.equals("Debug QR")) {
            String racine = "/home/rfernandez/Bureau/A_Test/TestDataForRootSystemTracker/QRCODE";
            String processingDir = racine + "/Processing_221125-CC-CO2";
            goOnPipelineFromProcessingDir(processingDir);
        } else {
            if (VitiDialogs.getYesNoUI("Go on a previous experiment ?", "Use an ongoing series ? ")) {
                String csvPath = VitiDialogs.chooseDirectoryNiceUI(
                                "Select the processing main directory containing InfoSerieRootSystemTracker.csv file"
                                , "OK")
                        .replace("\\", "/");
                csvPath = new File(csvPath, "InfoSerieRootSystemTracker.csv").getAbsolutePath();
                if (!new File(csvPath).exists()) {
                    IJ.showMessage("No csv file there");
                    return;
                }
                goOnPipelineFromProcessingDir(new File(csvPath).getParent().replace("\\", "/"));
            } else {
                String inventoryPath = "";
                if (VitiDialogs.getYesNoUI("Start from an existing inventory ?",
                        "Start from an existing inventory ?")) {
                    inventoryPath = VitiDialogs.chooseOneImageUIPath("Choose A_main_inventory.csv",
                            "A_main_inventory.csv");
                    if (!inventoryPath.contains("A_main_inventory.csv")) {
                        IJ.showMessage("No inventory csv there");
                        return;
                    }
                    inventoryPath = new File(inventoryPath).getParent().replace("\\", "/");
                } else {
                    String inputDir = VitiDialogs
                            .chooseDirectoryNiceUI("Please select your input non-inventoried data path", "OK")
                            .replace("\\", "/");
                    inventoryPath = Plugin_RootDatasetMakeInventory.makeInventory(inputDir).replace("\\", "/");
                }
                String processingPath = inventoryPath.replace("Inventory", "Processing");
                startNewExperimentFromInventoryAndProcessingDir(inventoryPath, processingPath);
            }
            // startPipeline();
        }
    }

    public void startNewExperimentFromNotInventoriedDataset(String inputDir, String outputDir) {
        String inventoryDir = Plugin_RootDatasetMakeInventory.makeInventory(inputDir).replace("\\", "/");
        if (inventoryDir == null)
            return;
        if (isZuluEndangered())
            return;
        startNewExperimentFromInventoryAndProcessingDir(inventoryDir, outputDir);
    }

    public void goOnPipelineFromProcessingDir(String processingDir) {
        PipelineParamHandler pph = new PipelineParamHandler(processingDir);
        if (isZuluEndangered())
            return;
        PipelineActionsHandler.goOnExperiment(pph);
    }

}
