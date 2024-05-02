package io.github.rocsg.rootsystemtracker.test;

import io.github.rocsg.rstplugin.PipelineActionsHandler;
import io.github.rocsg.rstplugin.PipelineParamHandler;
import org.junit.Test;
import org.itk.simple.SimpleITK;

import java.io.File;

import static io.github.rocsg.rstplugin.PipelineActionsHandler.stackData;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPipelineActionsHandler {
    public static boolean doNotDoTests = false;

    @Test
    public void testStackData() {
        if (doNotDoTests) return;

        TestRootDatasetMakeInventory prev_test = new TestRootDatasetMakeInventory();

        prev_test.globalTestNoRun();


        // Define the path to the output folder
        String inputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\data\\Test\\Output\\Inventory\\";
        String outputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\data\\Test\\Output\\Process\\";

        File outputFolder = new File(outputFolderPath);
        //create the output folder if it does not exist
        if (!outputFolder.exists()) {
            outputFolder.mkdir();
        }

        // Initialize the PipelineParamHandler object with valid parameters
        PipelineParamHandler pph = new PipelineParamHandler(inputFolderPath, outputFolderPath);

        // Call the stackData method with valid parameters
        boolean result = stackData(0, pph);

        // Check if the method returned true
        assertTrue(result);
    }



    @Test
    public void testRegistrationData() {
        if (doNotDoTests) return;

        // Define the path to the output folder
        String inputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\data\\Test\\Output\\Inventory\\";
        String outputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\data\\Test\\Output\\Process\\";

        File outputFolder = new File(outputFolderPath);
        //create the output folder if it does not exist
        if (!outputFolder.exists()) {
            outputFolder.mkdir();
        }
        String outputFolderPath0 = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\data\\Test\\Output\\Process\\1";

        File outputFolderPath01 = new File(outputFolderPath0);
        if (!outputFolderPath01.exists()) {
            outputFolderPath01.mkdir();
        }
        // Initialize the PipelineParamHandler object with valid parameters
        PipelineParamHandler pph = new PipelineParamHandler(inputFolderPath, outputFolderPath);

        // Call the stackData method
        stackData(0, pph);
        // Call the Register method
        boolean result = PipelineActionsHandler.registerSerie(0, outputFolderPath0, pph);

        // Check if the method returned true
        assertTrue(result);
    }

    @Test
    public void testComputeMaskandRemoveLeaves() {
        if (doNotDoTests) return;

        String inputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker"
                +
                "\\data\\ordered_input\\output_inv";
        String outputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker"
                +
                "\\data\\ordered_input\\OUT";
        String outputdatadir;

        // Initialize the PipelineParamHandler object with valid parameters
        PipelineParamHandler pph = new PipelineParamHandler(inputFolderPath, outputFolderPath);

        // Call the stackData method
        stackData(0, pph);

        outputdatadir = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker" +
                "\\data\\ordered_input\\OUT\\Box1";

        // Call the Register method
        boolean result = PipelineActionsHandler.registerSerie(0, outputdatadir, pph);

        inputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker" +
                "\\data\\ordered_input\\OUT\\Box1";
        outputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker" +
                "\\data\\ordered_input\\OUT\\Box1";

        result = PipelineActionsHandler.computeMasksAndRemoveLeaves(0, inputFolderPath, pph);

        // Check if the method returned true
        assertTrue(result);
    }

    @Test
    public void testComputeGraph() {
        if (doNotDoTests) return;
        String inputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker"
                +
                "\\data\\ordered_input\\output_inv";
        String outputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker"
                +
                "\\data\\ordered_input\\OUT";
        String outputdatadir;

        // Initialize the PipelineParamHandler object with valid parameters
        PipelineParamHandler pph = new PipelineParamHandler(inputFolderPath, outputFolderPath);

        // Call the stackData method
        stackData(0, pph);

        outputdatadir = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker" +
                "\\data\\ordered_input\\OUT\\Box1";

        // Call the Register method
        boolean result = PipelineActionsHandler.registerSerie(0, outputdatadir, pph);

        inputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker" +
                "\\data\\ordered_input\\OUT\\Box1";
        result = PipelineActionsHandler.computeMasksAndRemoveLeaves(0, inputFolderPath, pph);

        outputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker" +
                "\\data\\ordered_input\\OUT\\Box1";
        result = PipelineActionsHandler.buildAndProcessGraph(0, outputFolderPath, pph);

        // Check if the method returned true
        assertTrue(result);
    }

    @Test
    public void doAll() {
        if (doNotDoTests) return;
        TestRootDatasetMakeInventory prev_test = new TestRootDatasetMakeInventory();

        prev_test.globalTestNoRun();

        String inputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker"
                +
                "\\data\\ordered_input\\output_inv";
        String outputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker"
                +
                "\\data\\ordered_input\\OUT";

        // Initialize the PipelineParamHandler object with valid parameters
        PipelineParamHandler pph = new PipelineParamHandler(inputFolderPath, outputFolderPath);
        boolean result = true;
        for (int step = 1; step < 9; step++) {
            result = PipelineActionsHandler.doStepOnImg(step, 0, pph);
            assertTrue(result);
        }
    }
}

class loadLib {
    static {
        System.loadLibrary("SimpleITK");
    }

    native void cfun();

    public static void main(String[] args) {
        loadLib l = new loadLib();

        l.cfun();
    }
}