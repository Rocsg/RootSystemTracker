package io.github.rocsg.rsmlparser;

import io.github.rocsg.rsml.FSR;
import io.github.rocsg.rsml.RootModel;
import io.github.rocsg.rstplugin.PipelineParamHandler;
import org.graphstream.graph.*;
import org.graphstream.graph.implementations.*;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RootModelGraph2 {

    public RootModelGraph2() throws IOException {
        this("D:\\loaiu\\MAM5\\Stage\\data\\UC3\\Rootsystemtracker\\Original_Data\\B73_R04_01\\", "D:\\loaiu\\MAM5\\Stage\\data\\Test\\Output - Copie\\Process\\B73_R04_01\\Transforms_2\\", "D:\\loaiu\\MAM5\\Stage\\data\\Test\\Output\\Inventory\\", "D:\\loaiu\\MAM5\\Stage\\data\\Test\\Output\\Process\\", "D:\\loaiu\\MAM5\\Stage\\data\\Test\\Output\\Process\\B73_R04_01\\11_stack.tif", "D:\\loaiu\\MAM5\\Stage\\data\\Test\\Output\\Process\\B73_R04_01\\22_registered_stack.tif");
    }

    /**
     * Constructor of the RootModelGraph class
     * The RootModelGraph is created by reading the RSMLs from the specified path
     * The RootModels are extracted from the RSMLs
     * The RootModels are then converted to JGraphT Graphs
     * The JGraphT Graphs are then converted to ImagePlus images
     * The images are then resized using the resampling factor specified in the PipelineParamHandler
     * The transforms are read from the specified path
     *
     * @param path2RSMLs      The path to the RSMLs
     * @param transformerPath The path to the transforms
     * @param inputPathPPH    The path to the input PipelineParamHandler
     * @param outputPathPPH   The path to the output PipelineParamHandler
     * @throws IOException If an I/O error occurs
     */
    public RootModelGraph2(String path2RSMLs, String transformerPath, String inputPathPPH, String outputPathPPH, String originalScaledImagePath, String registeredImagePath) throws IOException {

        // Getting the resizer factor
        PipelineParamHandler pph = new PipelineParamHandler(inputPathPPH, outputPathPPH);

        // Reading all RSMLs and getting the RootModels
        Map<LocalDate, List<IRootModelParser>> result = parseRsmlFiles(path2RSMLs);

        // Initialize FSR and create RootModels
        FSR sr = new FSR();
        sr.initialize();
        RootModel rms = new RootModel();
        rms = (RootModel) rms.createRootModels(result, pph.subsamplingFactor);

    }

    private Map<LocalDate, List<IRootModelParser>> parseRsmlFiles(String path2RSMLs) throws IOException {
        RsmlParser rsmlParser = new RsmlParser(path2RSMLs);
        Map<LocalDate, List<IRootModelParser>> result = RsmlParser.getRSMLsinfos(Paths.get(rsmlParser.path2RSMLs));
        result.forEach((date, rootModel4Parsers) -> {
            System.out.println("Date : " + date);
            rootModel4Parsers.forEach(System.out::println);
        });
        return result;
    }

    public static void main(String[] args) throws IOException {
        System.setProperty("org.graphstream.ui", "swing"); // Use Swing
        RootModelGraph2 rootModelGraph = new RootModelGraph2();
    }
}

