package io.github.rocsg.rootsystemtracker.test;

import io.github.rocsg.rootsystemtracker.PipelineParamHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestPipelineParamHandler {

    @Test
    public void testRunCleaningAssistant() {

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