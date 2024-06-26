package io.github.rocsg.rootsystemtracker.test;

import io.github.rocsg.rstplugin.PipelineParamHandler;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestPipelineParamHandler {
    public static boolean doNotDoTests = false;

    @Test
    public void testRunCleaningAssistant() {
        if (doNotDoTests) return;

        TestRootDatasetMakeInventory prev_test = new TestRootDatasetMakeInventory();

        prev_test.globalTestNoRun();

        // Define the path to the output folder
        String inputFolderPath = "D:\\loaiu\\MAM5\\Stage\\data\\Test\\Output\\Inventory\\";
        String outputFolderPath = "D:\\loaiu\\MAM5\\Stage\\data\\Test\\Output\\Process\\";

        File outputFolder = new File(outputFolderPath);
        //create the output folder if it does not exist
        if (!outputFolder.exists()) {
            outputFolder.mkdir();
        }

        PipelineParamHandler handler = new PipelineParamHandler(inputFolderPath, outputFolderPath);
        assertDoesNotThrow(() -> handler.runCleaningAssistant(inputFolderPath));
    }

    @Test
    void testDefaultConstructor() {
        if (doNotDoTests) return;
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