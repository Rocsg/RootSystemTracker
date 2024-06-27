package io.github.rocsg.rootsystemtracker.test;

import org.junit.Test;

import io.github.rocsg.rstplugin.PipelineActionsHandler;
import io.github.rocsg.rstplugin.PipelineParamHandler;

import static io.github.rocsg.rstplugin.PipelineActionsHandler.stackData;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPipelineActionsHandler {
        public static boolean doNotDoTests=true;

        @Test
    public void testStackData() {
        if(doNotDoTests)return;
  
        TestRootDatasetMakeInventory prev_test = new TestRootDatasetMakeInventory();

        prev_test.globalTestNoRun();

        String inputFolderPath = "data/Output/Inventory";
        String outputFolderPath = "data/Output/Process";

        // Initialize the PipelineParamHandler object with valid parameters
        PipelineParamHandler pph = new PipelineParamHandler(inputFolderPath, outputFolderPath);

        // Call the stackData method with valid parameters
        boolean result = stackData(0, pph);

        // Check if the method returned true
        assertTrue(result);
    }

    @Test
    public void testRegistrationData() {
        if(doNotDoTests)return;
  
        String inputFolderPath = "../data/Output/Inventory";
        String outputFolderPath0 = "../data/Output/Process";
        String outputFolderPath = "../data/Output/Process/data1";

        // Initialize the PipelineParamHandler object with valid parameters
        PipelineParamHandler pph = new PipelineParamHandler(inputFolderPath, outputFolderPath0);

        // Call the stackData method
        stackData(0, pph);
        // Call the Register method
        boolean result = PipelineActionsHandler.registerSerie(0, outputFolderPath, pph);

        // Check if the method returned true
        assertTrue(result);
    }

    @Test
    public void testComputeMaskandRemoveLeaves() {
        if(doNotDoTests)return;
  
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
        if(doNotDoTests)return;
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
        if(doNotDoTests)return;
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