package io.github.rocsg.rootsystemtracker.test;

import org.junit.jupiter.api.Test;

import io.github.rocsg.rstplugin.PipelineParamHandler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestPipelineParamHandler {
    public static boolean doNotDoTests=true;

    @Test
    public void testRunCleaningAssistant() {
        if(doNotDoTests)return;

        TestRootDatasetMakeInventory prev_test = new TestRootDatasetMakeInventory();

        prev_test.globalTestNoRun();

        // Define the path to the output folder
        String inputFolderPath = "..\\data\\Output\\Inventory\\";
        String outputFolderPath = "..\\data\\Output\\Process\\";

        PipelineParamHandler handler = new PipelineParamHandler(inputFolderPath, outputFolderPath);
        assertDoesNotThrow(() -> handler.runCleaningAssistant(inputFolderPath));
    }

    @Test
    void testDefaultConstructor() {
        if(doNotDoTests)return;
        PipelineParamHandler handler = new PipelineParamHandler();
        assertNotNull(handler);
    }

    
    /*@Test
    void testConstructorWithParametersFile() {
        String parametersFile = "C:\\Users\\loaiu\\OneDrive - Université Nice Sophia
        Antipolis\\MAM5\\S2\\Stage\\Travaux\\Git_clones\\RootSystemTracker\\data\\ordered_input\\output_pph
        \\InfoSerieRootSystemTracker.csv";
        PipelineParamHandler handler = new PipelineParamHandler();
        handler.setParameters(parametersFile);
        // assert something has been created in the parametersFile location 
        File file = new File(parametersFile);
        assertTrue(file.exists());
    }*/

}