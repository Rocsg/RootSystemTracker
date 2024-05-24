/*package io.github.rocsg.rsml;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import java.io.File;

public class RsmlParser {

    public static void main(String[] args) {
        try {
            File inputFile = new File("D:\\loaiu\\MAM5\\Stage\\data\\UC3\\Rootsystemtracker\\Original_Data\\B73_R05_01\\12_05_2018_HA01_R005_h037.rsml");
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputFile);
            doc.getDocumentElement().normalize();

            System.out.println("Root element: " + doc.getDocumentElement().getNodeName());

            NodeList sceneList = doc.getElementsByTagName("scene");

            for (int i = 0; i < sceneList.getLength(); i++) {

                org.w3c.dom.Node sceneNode = sceneList.item(i);

                if (sceneNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    Element sceneElement = (Element) sceneNode;
                    System.out.println("\nScene:");

                    NodeList plantList = sceneElement.getElementsByTagName("plant");
                    for (int j = 0; j < plantList.getLength(); j++) {
                        org.w3c.dom.Node plantNode = plantList.item(j);

                        if (plantNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                            Element plantElement = (Element) plantNode;
                            System.out.println(" Plant:");

                            NodeList rootList = plantElement.getElementsByTagName("root");
                            parseRoots(rootList, "\t");
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void parseRoots(NodeList rootList, String indent) {
        for (int i = 0; i < rootList.getLength(); i++) {
            org.w3c.dom.Node rootNode = rootList.item(i);

            if (rootNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                Element rootElement = (Element) rootNode;
                String rootID = rootElement.getAttribute("ID");
                String rootLabel = rootElement.getAttribute("label");
                System.out.println(indent + "Root ID: " + rootID + ", Label: " + rootLabel);

                NodeList propertiesList = rootElement.getElementsByTagName("properties");
                if (propertiesList.getLength() > 0) {
                    Element propertiesElement = (Element) propertiesList.item(0);
                    NodeList properties = propertiesElement.getChildNodes();
                    for (int j = 0; j < properties.getLength(); j++) {
                        org.w3c.dom.Node property = properties.item(j);
                        if (property.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                            Element propertyElement = (Element) property;
                            System.out.println(indent + "  Property: " + propertyElement.getNodeName() + " = " + propertyElement.getTextContent());
                        }
                    }
                }

                NodeList geometryList = rootElement.getElementsByTagName("geometry");
                if (geometryList.getLength() > 0) {
                    Element geometryElement = (Element) geometryList.item(0);
                    NodeList polylineList = geometryElement.getElementsByTagName("polyline");
                    for (int k = 0; k < polylineList.getLength(); k++) {
                        org.w3c.dom.Node polylineNode = polylineList.item(k);
                        if (polylineNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                            Element polylineElement = (Element) polylineNode;
                            NodeList pointList = polylineElement.getElementsByTagName("point");
                            System.out.println(indent + "  Polyline:");
                            for (int l = 0; l < pointList.getLength(); l++) {
                                org.w3c.dom.Node pointNode = pointList.item(l);
                                if (pointNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                                    Element pointElement = (Element) pointNode;
                                    String x = pointElement.getAttribute("x");
                                    String y = pointElement.getAttribute("y");
                                    System.out.println(indent + "    Point: x=" + x + ", y=" + y);
                                }
                            }
                        }
                    }
                }

                NodeList functionList = rootElement.getElementsByTagName("function");
                for (int m = 0; m < functionList.getLength(); m++) {
                    org.w3c.dom.Node functionNode = functionList.item(m);
                    if (functionNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                        Element functionElement = (Element) functionNode;
                        String functionName = functionElement.getAttribute("name");
                        NodeList sampleList = functionElement.getElementsByTagName("sample");
                        System.out.println(indent + "  Function: " + functionName);
                        for (int n = 0; n < sampleList.getLength(); n++) {
                            org.w3c.dom.Node sampleNode = sampleList.item(n);
                            if (sampleNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                                Element sampleElement = (Element) sampleNode;
                                System.out.println(indent + "    Sample: " + sampleElement.getTextContent());
                            }
                        }
                    }
                }

                // Recursively parse child roots
                NodeList childRoots = rootElement.getElementsByTagName("root");
                parseRoots(childRoots, indent + " \t");
            }
        }
    }
}*/
package io.github.rocsg.rsmlparser;

import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxCellRenderer;
import com.mxgraph.util.mxConstants;
import com.mxgraph.util.mxRectangle;
import com.mxgraph.view.mxGraph;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.RGBStackMerge;
import ij.process.ImageProcessor;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import io.github.rocsg.fijiyama.registration.ItkTransform;
import io.github.rocsg.rsml.FSR;
import io.github.rocsg.rsml.Node;
import io.github.rocsg.rsml.Root;
import io.github.rocsg.rsml.RootModel;
import io.github.rocsg.rstplugin.PipelineParamHandler;
import math3d.Point3d;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class RsmlParser {

    public String path2RSMLs;

    public RsmlParser(String path2RSMLs) {
        this.path2RSMLs = path2RSMLs;
    }

    public static void main(String[] args) throws IOException {
        /*try {
            File inputFile = new File("D:\\loaiu\\MAM5\\Stage\\data\\UC3\\Rootsystemtracker\\Original_Data\\B73_R04_01\\13_05_2018_HA01_R004_h053.rsml");
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputFile);
            doc.getDocumentElement().normalize();

            RootModel4Parser RootModel4Parser = new RootModel4Parser();

            NodeList sceneList = doc.getElementsByTagName("scene");

            for (int i = 0; i < sceneList.getLength(); i++) {

                org.w3c.dom.Node sceneNode = sceneList.item(i);

                if (sceneNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    Element sceneElement = (Element) sceneNode;
                    Scene scene = new Scene();

                    NodeList plantList = sceneElement.getElementsByTagName("plant");
                    for (int j = 0; j < plantList.getLength(); j++) {
                        org.w3c.dom.Node plantNode = plantList.item(j);

                        if (plantNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                            Element plantElement = (Element) plantNode;
                            Plant plant = new Plant();

                            NodeList rootList = plantElement.getElementsByTagName("root");
                            parseRoots(rootList, plant, null, 1);

                            scene.addPlant(plant);
                        }
                    }
                    RootModel4Parser.addScene(scene);
                }
            }

            // You can now use the RootModel4Parser object as needed
            System.out.println(RootModel4Parser);

        } catch (Exception e) {
            e.printStackTrace();
        }*/
        ImageJ ij = new ImageJ();
        RootModelGraph rootModelGraph = new RootModelGraph();
        ImagePlus imp = rootModelGraph.image;
        List<ItkTransform> itkTransforms = rootModelGraph.transforms;

    }

    /**
     * Function to parse the roots from the rsml files
     * The roots are parsed by iterating over the root elements in the rsml files
     * The root elements are parsed to extract the root ID, label, properties, geometry, and functions
     * The root elements are then recursively parsed to extract the child roots
     * The roots are added to the Plant object
     * The Plant object is added to the Scene object
     * The Scene object is added to the RootModel4Parser object
     *
     * @param rootList    The NodeList containing the root elements to parse
     * @param parentPlant The Plant object to which the roots are added
     * @param parentRoot  The Root4Parser object to which the child roots are added
     * @param order       The order of the root
     * @param dateOfFile  The date of the rsml file
     */
    private static void parseRoots(NodeList rootList, Plant parentPlant, Root4Parser parentRoot, int order, String dateOfFile) { // TODO : we assumed 2nd order root max
        for (int i = 0; i < rootList.getLength(); i++) {
            org.w3c.dom.Node rootNode = rootList.item(i);

            if (rootNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                Element rootElement = (Element) rootNode;
                String rootID = rootElement.getAttribute("ID");
                String rootLabel = rootElement.getAttribute("label");
                String poAccession = rootElement.getAttribute("po:accession");

                Root4Parser root = new Root4Parser(rootID, rootLabel, poAccession, parentRoot, order, getDate(dateOfFile));

                NodeList propertiesList = rootElement.getElementsByTagName("properties");
                if (propertiesList.getLength() > 0) {
                    Element propertiesElement = (Element) propertiesList.item(0);
                    NodeList properties = propertiesElement.getChildNodes();
                    for (int j = 0; j < properties.getLength(); j++) {
                        org.w3c.dom.Node property = properties.item(j);
                        if (property.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                            Element propertyElement = (Element) property;
                            root.addProperty(new Property(propertyElement.getNodeName(), propertyElement.getTextContent()));
                        }
                    }
                }

                NodeList geometryList = rootElement.getElementsByTagName("geometry");
                if (geometryList.getLength() > 0) {
                    Element geometryElement = (Element) geometryList.item(0);
                    Geometry geometry = new Geometry();
                    NodeList polylineList = geometryElement.getElementsByTagName("polyline");
                    for (int k = 0; k < polylineList.getLength(); k++) {
                        org.w3c.dom.Node polylineNode = polylineList.item(k);
                        if (polylineNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                            Element polylineElement = (Element) polylineNode;
                            Polyline polyline = new Polyline();
                            NodeList pointList = polylineElement.getElementsByTagName("point");
                            for (int l = 0; l < pointList.getLength(); l++) {
                                org.w3c.dom.Node pointNode = pointList.item(l);
                                if (pointNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                                    Element pointElement = (Element) pointNode;
                                    String x = pointElement.getAttribute("x");
                                    String y = pointElement.getAttribute("y");
                                    polyline.addPoint(new Point4Parser(x, y));
                                }
                            }
                            geometry.addPolyline(polyline);
                        }
                    }
                    root.setGeometry(geometry);
                }

                NodeList functionList = rootElement.getElementsByTagName("function");
                Root4Parser.numFunctions = Math.min(Root4Parser.numFunctions, functionList.getLength()); // TODO : assuming coherence between functions length
                for (int m = 0; m < functionList.getLength(); m++) {
                    org.w3c.dom.Node functionNode = functionList.item(m);
                    if (functionNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                        Element functionElement = (Element) functionNode;
                        String functionName = functionElement.getAttribute("name");
                        Function function = new Function(functionName);
                        NodeList sampleList = functionElement.getElementsByTagName("sample");
                        for (int n = 0; n < sampleList.getLength(); n++) {
                            org.w3c.dom.Node sampleNode = sampleList.item(n);
                            if (sampleNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                                Element sampleElement = (Element) sampleNode;
                                function.addSample(sampleElement.getTextContent());
                            }
                        }
                        root.addFunction(function);
                    }
                }
                if (order == 1) parentPlant.addRoot(root);

                // Recursively parse child roots
                NodeList childRoots = rootElement.getElementsByTagName("root");
                parseRoots(childRoots, parentPlant, root, order + 1, dateOfFile);
            }
        }

        List<String> listID = parentPlant.getListID();
        List<Root4Parser> list2Remove = new ArrayList<>();
        for (Root4Parser root : parentPlant.roots) {
            if (root.children != null) {
                for (IRootParser child : root.children) {
                    if (listID.contains(child.getId())) {
                        list2Remove.add(parentPlant.getRootByID(child.getId()));
                    }
                }
            }
        }
        parentPlant.roots.removeAll(list2Remove);

        // for the first order roots of plant, keep only first 2 functions
        for (Root4Parser root : parentPlant.roots) {
            if (root.functions.size() > Root4Parser.numFunctions) {
                root.functions.subList(2, root.functions.size()).clear();
            }
        }
    }

    /**
     * Function that checks the uniqueness of the rsml files in a folder (comparing their content)
     *
     * @param folderPath The path to the folder containing the rsml files
     * @return a stack of String containing the path of the rsml files that are unique
     */
    public static Stack<String> checkUniquenessRSMLs(Path folderPath) {
        try {
            // From the folder path, get the list of rsml files
            List<Path> rsmlFiles = Files.list(folderPath)
                    .filter(path -> path.toString().matches(".*\\.rsml\\d{2}$") || path.toString().matches(".*\\.rsml$"))
                    .collect(Collectors.toList());

            // data structure that does not keep duplicates
            Stack<String> keptRsmlFiles = new Stack<>();

            // Read all the files content and check which ones are the same
            rsmlFiles.forEach(rsmlFile -> {
                try {
                    // read the file content
                    List<String> content = Files.readAllLines(rsmlFile);

                    // check if the content is the same as the other files that have been added to the stack
                    Optional<String> duplicateFile = keptRsmlFiles.stream()
                            .filter(keptRsmlFile -> {
                                try {
                                    List<String> keptContent = Files.readAllLines(Paths.get(keptRsmlFile));
                                    return content.equals(keptContent) || rsmlFile.toString().split("\\.")[0].equals(keptRsmlFile.split("\\.")[0]);
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            })
                            .findFirst();

                    if (duplicateFile.isPresent()) {
                        System.out.println("Same file : " + rsmlFile + " and " + duplicateFile.get());
                    } else {
                        keptRsmlFiles.push(rsmlFile.toString());
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

            System.out.println("\nKept files : " + keptRsmlFiles);
            return keptRsmlFiles;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Function to get the information described in the rsml files
     * <p>
     * It checks for the different rsml files similarity, takes into account the unique ones
     * And iterate over the unique ones to get the information used to describe the roots
     *
     * @param folderPath The path to the folder containing the rsml files
     * @return A TreeMap with the date as key and the list of rsml infos as value
     * @throws IOException If an I/O error occurs
     */
    public static Map<LocalDate, List<IRootModelParser>> getRSMLsinfos(Path folderPath) throws IOException {
        // check the uniqueness of the rsml files
        Stack<String> keptRsmlFiles = checkUniquenessRSMLs(folderPath);

        // get Date of each rsml (that supposetly match the image date) // TODO generalize
        Pattern pattern = Pattern.compile("\\d{2}_\\d{2}_\\d{4}");
        ConcurrentHashMap<String, LocalDate> fileDates = new ConcurrentHashMap<>();

        Files.list(folderPath)
                .parallel()
                .filter(path -> path.toString().matches(".*\\.(rsml|rsml01|rsml02|rsml03|rsml04)$"))
                .forEach(path -> {
                    fileDates.put(path.toString(), Objects.requireNonNull(getDate(path.toString().split("\\.")[0])));
                });

        Map<LocalDate, List<IRootModelParser>> rsmlInfos = new TreeMap<>();

        // add dates as keys
        fileDates.values().forEach(date -> rsmlInfos.put(date, new ArrayList<>()));

        // get the information from the rsml files
        for (String rsmlFile : keptRsmlFiles) {
            try {
                File inputFile = new File(rsmlFile);
                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document doc = dBuilder.parse(inputFile);
                doc.getDocumentElement().normalize();

                NodeList metadataList = doc.getElementsByTagName("metadata");
                Metadata metadata = new Metadata();

                for (int i = 0; i < metadataList.getLength(); i++) {
                    org.w3c.dom.Node metadataNode = metadataList.item(i);

                    if (metadataNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                        Element metadataElement = (Element) metadataNode;
                        metadata.version = Float.parseFloat(metadataElement.getElementsByTagName("version").item(0).getTextContent());
                        metadata.unit = metadataElement.getElementsByTagName("unit").item(0).getTextContent();
                        metadata.resolution = Float.parseFloat(metadataElement.getElementsByTagName("resolution").item(0).getTextContent());
                        metadata.modifDate = (metadataElement.getElementsByTagName("last-modified").item(0).getTextContent().equals("today") ? LocalDate.now() : getDate(metadataElement.getElementsByTagName("last-modified").item(0).getTextContent()));
                        metadata.software = metadataElement.getElementsByTagName("software").item(0).getTextContent();
                        metadata.user = metadataElement.getElementsByTagName("user").item(0).getTextContent();
                        metadata.fileKey = metadataElement.getElementsByTagName("file-key").item(0).getTextContent();

                        NodeList propertydefList = metadataElement.getElementsByTagName("property-definition");
                        for (int j = 0; j < propertydefList.getLength(); j++) {
                            org.w3c.dom.Node propertydefNode = propertydefList.item(j);

                            if (propertydefNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                                Element propertydefElement = (Element) propertydefNode;
                                Metadata.PropertyDefinition propertyDefinition = metadata.new PropertyDefinition();
                                propertyDefinition.label = propertydefElement.getElementsByTagName("label").item(0).getTextContent();
                                propertyDefinition.type = propertydefElement.getElementsByTagName("type").item(0).getTextContent();
                                propertyDefinition.unit = propertydefElement.getElementsByTagName("unit").item(0).getTextContent();
                                metadata.propertiedef.add(propertyDefinition);
                            }
                        }
                        NodeList imageList = metadataElement.getElementsByTagName("image"); // TODO : generalize

                        // get the label node
                        for (int j = 0; j < imageList.getLength(); j++) {
                            org.w3c.dom.Node imageNode = imageList.item(j);

                            if (imageNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                                Element imageElement = (Element) imageNode;
                                metadata.date2Use = imageElement.getElementsByTagName("label").item(0).getTextContent();
                            }
                        }
                    }
                }

                RootModel4Parser RootModel4Parser = new RootModel4Parser(metadata);


                NodeList sceneList = doc.getElementsByTagName("scene");

                for (int i = 0; i < sceneList.getLength(); i++) {

                    org.w3c.dom.Node sceneNode = sceneList.item(i);

                    if (sceneNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                        Element sceneElement = (Element) sceneNode;
                        Scene scene = new Scene();

                        NodeList plantList = sceneElement.getElementsByTagName("plant");
                        for (int j = 0; j < plantList.getLength(); j++) {
                            org.w3c.dom.Node plantNode = plantList.item(j);

                            if (plantNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                                Element plantElement = (Element) plantNode;
                                Plant plant = new Plant();

                                NodeList rootList = plantElement.getElementsByTagName("root");
                                parseRoots(rootList, plant, null, 1, metadata.date2Use);
                                scene.addPlant(plant);
                            }
                        }
                        RootModel4Parser.addScene(scene);
                    }
                }
                // add the RootModel4Parser to the corresponding date
                rsmlInfos.get(fileDates.get(rsmlFile)).add(RootModel4Parser);

            } catch (ParserConfigurationException | SAXException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        System.out.println("rsmlInfos : " + rsmlInfos);
        return rsmlInfos;
    }

    /**
     * Function to get the date from a String
     * The date is extracted from the String using a regular expression
     * The date is returned as a LocalDate
     *
     * @param Date The String from which to extract the date
     * @return The LocalDate extracted from the String
     */
    private static LocalDate getDate(String Date) {
        // TODO generalize
        // detect date in a String
        //// For now pattern is dd_mm_yyyy
        //// Time is set to 00:00:00
        Pattern pattern = Pattern.compile("\\d{2}_\\d{2}_\\d{4}");
        Matcher matcher = pattern.matcher(Date);
        LocalDate date = null;
        if (matcher.find()) {
            int year = Integer.parseInt(matcher.group(0).split("_")[2]);
            int month = Integer.parseInt(matcher.group(0).split("_")[1]);
            int day = Integer.parseInt(matcher.group(0).split("_")[0]);
            date = LocalDate.of(year, month, day);
        }

        Pattern pattern4Time = Pattern.compile("\\d{2}:\\d{2}:\\d{2}");
        Matcher matcher4Time = pattern4Time.matcher(Date);
        if (matcher4Time.find()) {
            int hour = Integer.parseInt(matcher4Time.group(0).split(":")[0]);
            int minute = Integer.parseInt(matcher4Time.group(0).split(":")[1]);
            int second = Integer.parseInt(matcher4Time.group(0).split(":")[2]);
            date = Objects.requireNonNull(date).atTime(hour, minute, second).toLocalDate();
        }
        return date;
    }
}

class RootModelGraph {

    final List<RootModel> rootModels;
    public List<Graph<Node, DefaultEdge>> graphs;
    ImagePlus image;
    List<ItkTransform> transforms;

    public RootModelGraph() throws IOException {
        this("D:\\loaiu\\MAM5\\Stage\\data\\UC3\\Rootsystemtracker\\Original_Data\\B73_R04_01\\", "D:\\loaiu\\MAM5\\Stage\\data\\Test\\Output - Copie\\Process\\B73_R04_01\\Transforms_2\\", "D:\\loaiu\\MAM5\\Stage\\data\\Test\\Output\\Inventory\\", "D:\\loaiu\\MAM5\\Stage\\data\\Test\\Output\\Process\\","D:\\loaiu\\MAM5\\Stage\\data\\Test\\Output\\Process\\B73_R04_01\\11_stack.tif", "D:\\loaiu\\MAM5\\Stage\\data\\Test\\Output\\Process\\B73_R04_01\\22_registered_stack.tif");
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
    public RootModelGraph(String path2RSMLs, String transformerPath, String inputPathPPH, String outputPathPPH, String originalScaledImagePath, String registeredImagePath) throws IOException {
        this.rootModels = new ArrayList<>();
        this.graphs = new ArrayList<>();
        transforms = new ArrayList<>();

        // Getting the resizer factor
        PipelineParamHandler pph = new PipelineParamHandler(inputPathPPH, outputPathPPH); // assuming the resampling factor is correct TODO

        // Reading all RSMLs and getting the RootModels
        RsmlParser rsmlParser = new RsmlParser(path2RSMLs);
        Map<LocalDate, List<IRootModelParser>> result = RsmlParser.getRSMLsinfos(Paths.get(rsmlParser.path2RSMLs));
        result.forEach((date, rootModel4Parsers) -> {
            System.out.println("Date : " + date);
            rootModel4Parsers.forEach(System.out::println);
        });
        FSR sr;
        (sr = new FSR()).initialize();

        RootModel rms = new RootModel();
        rms = (RootModel) rms.createRootModels(result, pph.subsamplingFactor);

        // Operations with original image (from stack data)

        // Create an array to store processed images
        ImagePlus[] processedImages = new ImagePlus[13];
        ImagePlus imgInitSize = new ImagePlus(originalScaledImagePath);
        // Loop over each time point in the model
        RootModel finalRms = rms;
        IntStream.range(0, imgInitSize.getStackSize()).parallel().forEach(i -> {
            // Create a grayscale image of the RSML model at this time point
            ImagePlus imgRSML = finalRms.createGrayScaleImageWithTime(imgInitSize, 1, false, (i + 1), true,
                    new boolean[]{true, true, true, false, true}, new double[]{2, 2});


            // Set the display range of the image
            imgRSML.setDisplayRange(0, imgInitSize.getStackSize() + 3);

            // Merge the grayscale image with the registered stack image
            processedImages[i] = RGBStackMerge.mergeChannels(new ImagePlus[]{new ImagePlus("", imgInitSize.getStack().getProcessor(i+1)), imgRSML}, true);
            // Convert the image to RGB color
            IJ.run(processedImages[i], "RGB Color", "");
        });
        // Combine the images into a stack
        ImagePlus res = VitimageUtils.slicesToStack(processedImages);
        // Set the title of the image
        res.setTitle("yoho");
        res.show();
        
        // transform the image

        ImagePlus res2 = VitimageUtils.cropImage(new ImagePlus(originalScaledImagePath),  (int) 1400.0 / pph.subsamplingFactor, (int) 350.0 / pph.subsamplingFactor, 0,(int) (10620.0 - 1400.0) / pph.subsamplingFactor,(int) (8783.0 - 350.0) / pph.subsamplingFactor, res.getStackSize());
        // Read all the transforms
        Files.list(Paths.get(transformerPath))
                .filter(path -> path.toString().matches(".*transform.tif"))
                .sorted(Comparator.comparingInt(o -> Integer.parseInt(o.getFileName().toString().split("_")[1].split("\\.")[0])))
                .forEach(path -> {
                    ItkTransform itkTransform = new ItkTransform(ItkTransform.readAsDenseField(path.toString()));
                    transforms.add(itkTransform);
                });
        
        // transform the graph
        ImagePlus img0 = toDisplayTemporalGraph(createGraph(rms), res2.getWidth(), res2.getHeight(), 1, res2.getNSlices());
        img0.show();

        for (ItkTransform transform : this.transforms) {
            rms.applyTransformToGeometry(transform, transforms.indexOf(transform) + 1);
        }

        ImagePlus img2 = toDisplayTemporalGraph(createGraph(rms), res2.getWidth(), res2.getHeight(), 1, res2.getNSlices());
        img2.show();



        /*ImageStack stack = new ImageStack();
        result.forEach((date, rootModel4Parsers) -> {
            RootModel rm = new RootModel();
            rm = (RootModel) rm.createRootModel(rootModel4Parsers.get(0), new ArrayList<>(result.keySet()).indexOf(date));
            System.out.println(rm);

            // extract the graph from the root model
            Graph<Node, DefaultEdge> g = createGraph(rm);
            stack.addSlice(toDisplayGraph(g, 12383, 8783, 1).getProcessor()); // TODO : generalize

            this.rootModels.add(rm);
            this.graphs.add(g);
        });
        // Create the image
        this.image = new ImagePlus("Root Model Graph", stack);




        // Read all the transforms
        Files.list(Paths.get(transformerPath))
                .filter(path -> path.toString().matches(".*transform.tif"))
                .sorted(Comparator.comparingInt(o -> Integer.parseInt(o.getFileName().toString().split("_")[1].split("\\.")[0])))
                .forEach(path -> {
                    ItkTransform itkTransform = new ItkTransform(ItkTransform.readAsDenseField(path.toString()));
                    transforms.add(itkTransform);
                    System.out.println(itkTransform);
                });
        res = VitimageUtils.cropImage(res.duplicate(),  (int) 1400.0 / pph.subsamplingFactor, (int) 350.0 / pph.subsamplingFactor, 0,(int) (10620.0 - 1400.0) / pph.subsamplingFactor,(int) (8783.0 - 350.0) / pph.subsamplingFactor, res.getStackSize());
        ImageStack stack4 = new ImageStack();
        for (ItkTransform transform : this.transforms) {
            ImageProcessor slice = res.getStack().getProcessor(this.transforms.indexOf(transform));
            ImagePlus imp3 = new ImagePlus("slice", slice);
            imp3 = transform.transformImage(imp3, imp3);
            stack4.addSlice(imp3.getProcessor());
        }
       ImagePlus ok = new ImagePlus("Root Model Graph", stack4);
        ok.show();

        // Check the coherence of the data
        /*assert ((this.graphs.size() == this.rootModels.size()) && ((this.graphs.size() == this.image.getStackSize())));

        List<Graph<Node, DefaultEdge>> graph0 = resizeGraphsAsImage(this.graphs, 12383, 8783, 12383 / pph.subsamplingFactor, 8783 / pph.subsamplingFactor);
        ImageStack stack1 = new ImageStack();
        for (Graph<Node, DefaultEdge> graph : graph0) {
            ImagePlus imgTemp = toDisplayGraph(graph, this.image.getWidth() / pph.subsamplingFactor, this.image.getHeight() / pph.subsamplingFactor, pph.subsamplingFactor);
            stack1.addSlice(imgTemp.getProcessor());
        }
        ImagePlus imp2 = new ImagePlus("Root Model Graph base 1", stack1);
        //imp2.show();*/
    }

    public static ImagePlus toDisplayTemporalGraph(Graph<Node, DefaultEdge> g, int width, int height, float subsamplingFactor, int numSlices) {
        List<mxGraph> graphs = new ArrayList<>(numSlices);
        for (int i = 1; i< numSlices+1; i++) {
            mxGraph graph = new mxGraph();
            Object parent = graph.getDefaultParent();

            graph.getModel().beginUpdate();
            try {
                // Create a map to store the mxGraph vertices created for each Node
                Map<Node, Object> vertexMap = new HashMap<>();

                for (Node node : g.vertexSet()) {
                    // Insert the vertex into the graph at the position specified by the Node's x and y properties
                    // Pass an empty string "" as the label of the vertex
                    // Reduce the size of the vertex to make it appear as a point
                    if (node.birthTime == i) {
                        Object vertex = graph.insertVertex(parent, null, "", node.x, node.y, 1, 1);
                        vertexMap.put(node, vertex);
                    }
                }

                for (DefaultEdge edge : g.edgeSet()) {
                    Node sourceNode = g.getEdgeSource(edge);
                    Node targetNode = g.getEdgeTarget(edge);
                    if (sourceNode.birthTime <= i && targetNode.birthTime == i) {
                        // Get the mxGraph vertices corresponding to the source and target Nodes
                        Object sourceVertex = vertexMap.get(sourceNode);
                        Object targetVertex = vertexMap.get(targetNode);
                        // Insert the edge into the graph
                        Object edgeObject = graph.insertEdge(parent, null, "", sourceVertex, targetVertex);
                        // Remove the arrow from the edge
                        graph.setCellStyle(mxConstants.STYLE_ENDARROW + "=" + mxConstants.NONE, new Object[]{edgeObject});
                        // Set the stroke width
                        graph.setCellStyle(mxConstants.STYLE_STROKEWIDTH + "=" + (1.0 / subsamplingFactor), new Object[]{edgeObject});
                        // Add a small vertex at the end of the edge to represent it as a point
                        graph.insertVertex(parent, null, "", targetNode.x, targetNode.y, 1, 1);
                    }
                }
            } finally {
                graph.getModel().endUpdate();
            }


            mxGraphComponent graphComponent = new mxGraphComponent(graph);
            graphComponent.addMouseWheelListener(e -> {
                if (e.isControlDown()) {
                    if (e.getWheelRotation() < 0) {
                        graphComponent.zoomIn();
                    } else {
                        graphComponent.zoomOut();
                    }
                }
            });
            graphs.add(graph);
        }

        ImageStack stack = new ImageStack();
        for (mxGraph graph : graphs) {
            int i = graphs.indexOf(graph);
            mxGraph mxGraph = graphs.get(i);
            // get buffered image
            BufferedImage image = mxCellRenderer.createBufferedImage(mxGraph, null, 1, Color.WHITE, true, new mxRectangle(0, 0, width - 1, height - 1));
            ImagePlus imp = new ImagePlus("Graph", image);
            stack.addSlice("slice + " + i, imp.getProcessor());
        }
        return new ImagePlus("Root Model Graph", stack);
    }

    // OLD graphs

    static Graph<Node, DefaultEdge> resizeGraphAsImage(Graph<Node, DefaultEdge> graph, int originalWidth, int originalHeight, int newWidth, int newHeight) {
        for (Node node : graph.vertexSet()) {
            node.x = (node.x / originalWidth) * newWidth;
            node.y = (node.y / originalHeight) * newHeight;
        }
        return graph;
    }

    static List<Graph<Node, DefaultEdge>> resizeGraphsAsImage(List<Graph<Node, DefaultEdge>> graphs, int OriginalWidth, int OriginalHeight, int newWidth, int newHeight) {
        for (Graph<Node, DefaultEdge> graph : graphs) {
            graphs.set(graphs.indexOf(graph), resizeGraphAsImage(graph, OriginalWidth, OriginalHeight, newWidth, newHeight));
        }
        return graphs;
    }

    /**
     * Function to create a graph from a RootModel
     * The graph is created by adding the vertices and edges of the RootModel to a JGraphT Graph
     * The vertices are the Nodes of the RootModel
     * The edges are the connections between the Nodes
     *
     * @param rootModel The RootModel from which to create the graph
     * @return The JGraphT Graph created from the RootModel
     */
    public static Graph<Node, DefaultEdge> createGraph(RootModel rootModel) {
        Graph<Node, DefaultEdge> g = new SimpleGraph<>(DefaultEdge.class);

        // Add vertices to the graph
        for (Root r : rootModel.rootList) {
            Node firstNode = r.firstNode;
            while (firstNode != null) {
                g.addVertex(firstNode);
                firstNode = firstNode.child;
            }
        }

        // Add edges to the graph
        for (Root r : rootModel.rootList) {
            Node firstNode = r.firstNode;
            while (firstNode != null) {
                if (firstNode.child != null) {
                    g.addEdge(firstNode, firstNode.child);
                }
                firstNode = firstNode.child;
            }
        }

        return g;
    }

    /**
     * Function to transform the graphs
     * The graphs are transformed by applying the transform to the 2D coordinates of each node
     * The transform is applied to the 2D coordinates of each node
     * The 2D coordinates are extracted from the 3D coordinates of each node (3D points (x,y,t))
     * The 3D coordinates are transformed by the transform
     * The 2D coordinates are updated with the transformed 3D coordinates
     *
     * @return The transformed graphs
     */
    public static List<Graph<Node, DefaultEdge>> transformGraphsList(List<Graph<Node, DefaultEdge>> graphs, List<ItkTransform> transforms) {
        // the graph of nodes contains 2D coordinates + t (the time coordinate), we will extract the 2D coordinates of each node (3D points (x,y,t)) and apply the transform to them (transformPoint)
        List<Graph<Node, DefaultEdge>> transformedGraphs = new ArrayList<>();
        for (Graph<Node, DefaultEdge> graph : graphs) {
            int graphIndex = graphs.indexOf(graph);
            if (graphIndex >= transforms.size()) {
                break;
            }
            ItkTransform transform = transforms.get(graphs.indexOf(graph));
            transformedGraphs.add(transformGraph(graph, transform));
        }
        return transformedGraphs;
    }

    public static Graph<Node, DefaultEdge> transformGraph(Graph<Node, DefaultEdge> graph, ItkTransform transform) {
        for (Node node : graph.vertexSet()) {
            Point3d point = new Point3d(node.x, node.y, node.birthTime);
            point = transform.transformPoint(point);
            node.x = (float) point.x;
            node.y = (float) point.y;
        }
        return graph;
    }

    /**
     * Function to convert a JGraphT Graph to an ImagePlus
     * The graph is displayed as a point graph with edges connecting the points
     * The vertices are represented as points in the image
     * The edges are represented as lines in the image
     * The image is created by using the mxGraph library to create a buffered image of the graph
     * The buffered image is then converted to an ImagePlus
     *
     * @param g The JGraphT Graph to convert to an ImagePlus
     * @return The ImagePlus created from the JGraphT Graph
     */
    public static ImagePlus toDisplayGraph(Graph<Node, DefaultEdge> g, int width, int height, float subsamplingFactor) {
        mxGraph graph = new mxGraph();
        Object parent = graph.getDefaultParent();

        graph.getModel().beginUpdate();
        try {
            // Create a map to store the mxGraph vertices created for each Node
            Map<Node, Object> vertexMap = new HashMap<>();

            for (Node node : g.vertexSet()) {
                // Insert the vertex into the graph at the position specified by the Node's x and y properties
                // Pass an empty string "" as the label of the vertex
                // Reduce the size of the vertex to make it appear as a point
                Object vertex = graph.insertVertex(parent, null, "", node.x, node.y, 1, 1);
                vertexMap.put(node, vertex);
            }

            for (DefaultEdge edge : g.edgeSet()) {
                Node sourceNode = g.getEdgeSource(edge);
                Node targetNode = g.getEdgeTarget(edge);
                // Get the mxGraph vertices corresponding to the source and target Nodes
                Object sourceVertex = vertexMap.get(sourceNode);
                Object targetVertex = vertexMap.get(targetNode);
                // Insert the edge into the graph
                Object edgeObject = graph.insertEdge(parent, null, "", sourceVertex, targetVertex);
                // Remove the arrow from the edge
                graph.setCellStyle(mxConstants.STYLE_ENDARROW + "=" + mxConstants.NONE, new Object[]{edgeObject});
                // Set the stroke width
                graph.setCellStyle(mxConstants.STYLE_STROKEWIDTH + "=" + (10.0 / subsamplingFactor), new Object[]{edgeObject});
                // Add a small vertex at the end of the edge to represent it as a point
                graph.insertVertex(parent, null, "", targetNode.x, targetNode.y, 1, 1);
            }
        } finally {
            graph.getModel().endUpdate();
        }


        mxGraphComponent graphComponent = new mxGraphComponent(graph);
        graphComponent.addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                if (e.getWheelRotation() < 0) {
                    graphComponent.zoomIn();
                } else {
                    graphComponent.zoomOut();
                }
            }
        });

        // get buffered image
        BufferedImage image = mxCellRenderer.createBufferedImage(graph, null, 1, Color.WHITE, true, new mxRectangle(0, 0, width - 1, height - 1));

        return new ImagePlus("Root Model Graph", image);
    }

}


