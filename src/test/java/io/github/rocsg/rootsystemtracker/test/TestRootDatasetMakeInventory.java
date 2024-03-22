package io.github.rocsg.rootsystemtracker.test;

import io.github.rocsg.rootsystemtracker.Plugin_RootDatasetMakeInventory;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

public class TestRootDatasetMakeInventory {

    public TestRootDatasetMakeInventory() {
    }

    @Test
    void globalTestNoRun() {
        // Define the path to the output folder
        String inputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker" +
                "\\RootSystemTracker\\data\\ordered_input\\input";
        String outputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker" +
                "\\RootSystemTracker\\data\\ordered_input\\output_inv";

        // Create a File object for the output folder
        File outputFolder = new File(outputFolderPath);

        // Check if the output folder is not empty
        if (Objects.requireNonNull(outputFolder.list()).length > 0) {
            // Use a FileVisitor to delete all files in the output folder
            try {
                Files.walkFileTree(outputFolder.toPath(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });

                // Create again the output empty folder
                outputFolder.mkdir();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Choice 1
        Plugin_RootDatasetMakeInventory.startInventoryOfAlreadyTidyDir(inputFolderPath, outputFolderPath);
    }

    /*
     * @Test
     * void globalTest() {
     * // Define the path to the output folder
     * String outputFolderPath =
     * "C:\\Users\\loaiu\\OneDrive - Université Nice Sophia
     * Antipolis\\MAM5\\S2\\Stage\\Travaux\\Git_clones\\RootSystemTracker\\data\\ordered_input\\output"
     * ;
     *
     * // Create a File object for the output folder
     * File outputFolder = new File(outputFolderPath);
     *
     * // Check if the output folder is not empty
     * if (outputFolder.list().length > 0) {
     * // Use a FileVisitor to delete all files in the output folder
     * try {
     * Files.walkFileTree(outputFolder.toPath(), new SimpleFileVisitor<Path>() {
     *
     * @Override
     * public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws
     * IOException {
     * Files.delete(file);
     * return FileVisitResult.CONTINUE;
     * }
     *
     * @Override
     * public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws
     * IOException {
     * Files.delete(dir);
     * return FileVisitResult.CONTINUE;
     * }
     * });
     *
     * // Create again the output empty folder
     * outputFolder.mkdir();
     * } catch (IOException e) {
     * e.printStackTrace();
     * }
     * }
     *
     * Plugin_RootDatasetMakeInventory plugin = new
     * Plugin_RootDatasetMakeInventory();
     * plugin.run("");
     * }
     *
     * @Test
     * void makeInventory_validInputDir_returnOutputDir() {
     * // Create a temporary directory for testing
     * String inputDir =
     * "C:\\Users\\loaiu\\OneDrive - Université Nice Sophia
     * Antipolis\\MAM5\\S2\\Stage\\Travaux\\Git_clones\\RootSystemTracker\\data\\ordered_input\\input"
     * ;
     * String outputDir = Plugin_RootDatasetMakeInventory.makeInventory(inputDir);
     *
     * assertNotNull(outputDir);
     * // You may want to add more assertions based on your specific requirements
     * System.out.println("Output directory: " + outputDir);
     *
     * // try making an other inventory with the same input directory (try catch)
     * String outputDir2 = Plugin_RootDatasetMakeInventory.makeInventory(inputDir);
     * assert (outputDir2 == "");
     * System.out.println("Cannot do it twice");
     * }
     *
     * @Test
     * void commonSubStringAtTheBeginning_validStrings_returnCommonSubstring() {
     * String s1 = "abc123";
     * String s2 = "abc456";
     *
     * String commonSubstring =
     * Plugin_RootDatasetMakeInventory.commonSubStringAtTheBeginning(s1, s2);
     *
     * assertEquals("abc", commonSubstring);
     * System.out.println("Common substring: " + commonSubstring);
     * }
     */
}

// QR code tests to make ...
