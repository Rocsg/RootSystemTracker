package io.github.rocsg.rsmlparser;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.CurveFitter;
import ij.measure.SplineFitter;
import ij.process.ImageProcessor;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import io.github.rocsg.fijiyama.registration.ItkTransform;
import io.github.rocsg.rsml.FSR;
import io.github.rocsg.rsml.Node;
import io.github.rocsg.rsml.Root;
import io.github.rocsg.rsml.RootModel;
import io.github.rocsg.rstplugin.PipelineParamHandler;
import io.github.rocsg.rstutils.BlockMatchingRegistrationRootModel;
import math3d.Point3d;
import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DoublePoint;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.graphstream.graph.implementations.SingleGraph;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.github.rocsg.rstutils.BlockMatchingRegistrationRootModel.setupAndRunRsmlBlockMatchingRegistration;

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

            // Read all the file's content and check which ones are the same
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

        try {
            Files.list(folderPath)
                    .parallel()
                    .filter(path -> path.toString().matches(".*\\.(rsml|rsml01|rsml02|rsml03|rsml04)$"))
                    .forEach(path -> {
                        fileDates.put(path.toString(), Objects.requireNonNull(getDate(path.toString().split("\\.")[0])));
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

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

    final static int STANDART_DIST = 1;
    final List<RootModel> rootModels;
    public List<org.graphstream.graph.Graph> graphs;
    PipelineParamHandler pph;
    ImagePlus image;
    List<ItkTransform> transforms;

    public RootModelGraph() throws IOException {
        this("D:\\loaiu\\MAM5\\Stage\\data\\UC3\\Rootsystemtracker\\Original_Data\\B73_R04_01\\", "D:\\loaiu\\MAM5\\Stage\\data\\Test\\Output-Copie\\Process\\B73_R04_01\\Transforms_2\\", "D:\\loaiu\\MAM5\\Stage\\data\\Test\\Output\\Inventory\\", "D:\\loaiu\\MAM5\\Stage\\data\\Test\\Output\\Process\\", "D:\\loaiu\\MAM5\\Stage\\data\\Test\\Output-Copie\\Process\\B73_R04_01\\11_stack.tif", "D:\\loaiu\\MAM5\\Stage\\data\\Test\\Output-Copie\\Process\\B73_R04_01\\22_registered_stack.tif",
                "D:\\loaiu\\MAM5\\Stage\\data\\Test\\Output-Copie\\Process\\B73_R04_01\\12_stack_cropped.tif");
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
    public RootModelGraph(String path2RSMLs, String transformerPath, String inputPathPPH, String outputPathPPH, String originalScaledImagePath, String registeredImagePath, String cropedImage) throws IOException {
        this.rootModels = new ArrayList<>();
        this.graphs = new ArrayList<>();
        transforms = new ArrayList<>();

        // Getting the resizer factor
        pph = new PipelineParamHandler(inputPathPPH, outputPathPPH);

        // Reading all RSMLs and getting the RootModels
        Map<LocalDate, List<IRootModelParser>> result = parseRsmlFiles(path2RSMLs);

        // Initialize FSR and create RootModels
        FSR sr = new FSR();
        sr.initialize();
        RootModel rms = new RootModel();
        rms = (RootModel) rms.createRootModels(result, pph.subsamplingFactor);

        ImagePlus refImage = new ImagePlus(registeredImagePath);
        image = refImage;

        // Read all the transforms and apply them
        ImagePlus imgInitSize = new ImagePlus(originalScaledImagePath);
        //displayOnImage(createGraphFromRM(rms), imgInitSize, true).show();

        readAndApplyTransforms(transformerPath, rms, refImage, imgInitSize);
        //ImagePlus img2 = displayOnImage(createGraphFromRM(rms), refImage);
        //img2.show();

        rms.adjustRootModel();
        //ImagePlus img3 = displayOnImage(createGraphFromRM(rms), refImage);
        //img3.show();

        //setupAndRunRsmlBlockMatchingRegistration(rms, refImage);


        // Display the final graph
        //ImagePlus img2 = toDisplayTemporalGraph(createGraphFromRM(rms), res2.getWidth(), res2.getHeight(), 1, res2.getNSlices());

        //ImagePlus img2 = displayOnImage(createGraphFromRM(rms), refImage);
        //img2.show();

        //Map<Root, List<Node>> insertionPoints = rms.getInsertionPoints();
        //ImagePlus img3 = displayOnImage(createGraphFromRM(rms), refImage, insertionPoints);
        //img3.show();

        BlockMatchingRegistrationRootModel bm = new BlockMatchingRegistrationRootModel(rms);
        bm.setupAndRunRsmlBlockMatchingRegistration(rms, refImage);

        PlantReconstruction pr = new PlantReconstruction(rms);

        /*interpolatePointsSplineFitter(rms, displayOnImage(createGraphFromRM(rms), refImage, insertionPoints));

        interpolatePointsCurveFitter(rms, displayOnImage(createGraphFromRM(rms), refImage, insertionPoints));

        interpolatePoints(rms, displayOnImage(createGraphFromRM(rms), refImage, insertionPoints));

        interpolatePointsSpline(rms, displayOnImage(createGraphFromRM(rms), refImage, insertionPoints));

        interpolatePointsRBF(rms, displayOnImage(createGraphFromRM(rms), refImage, insertionPoints));

        interpolatePointsBezier(rms, displayOnImage(createGraphFromRM(rms), refImage, insertionPoints));*/
        System.out.println("RootModelGraph : " + rms);
        //interpolatePointsSplineFitter(rms, imag.duplicate());

        //interpolatePointsCurveFitter(rms, imag.duplicate());

        //interpolatePoints(rms, imag.duplicate());

        //interpolatePointsRBF(rms, imag.duplicate());

        //interpolatePointsBezier(rms, imag.duplicate());

//        rms.closestNodes();
//        rms.alignByMinDist();
//        //rms.align2Time();
//        ImagePlus img3 = toDisplayTemporalGraph(createGraphFromRM(rms), res2.getWidth(), res2.getHeight(), 1, res2.getNSlices());
//        img3.show();
    }

    /**
     * Function to create a display of a GraphStream graph as an ImagePlus image
     *
     * @param g                 The graph made of the Nodes and Edges
     * @param width             The width of the image
     * @param height            The height of the image
     * @param subsamplingFactor The subsampling factor
     * @param numSlices         The number of slices
     * @return The ImagePlus image of the graph
     */
    public static ImagePlus toDisplayTemporalGraph(org.graphstream.graph.Graph g, int width, int height, float subsamplingFactor, int numSlices) {
        ImageStack stack = new ImageStack();
        for (int i = 1; i <= numSlices; i++) {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();

            for (org.graphstream.graph.Node node : g) {
                Object[] xyz = node.getAttribute("xyz");
                double x = (float) xyz[0];
                double y = (float) xyz[1];
                double t = (float) xyz[2];
                if (t == i) g2d.fill(new Ellipse2D.Double(x - 2, y - 2, 4, 4));
            }

            for (org.graphstream.graph.Edge edge : g.getEdgeSet()) {
                Object[] xyz0 = edge.getNode0().getAttribute("xyz");
                Object[] xyz1 = edge.getNode1().getAttribute("xyz");
                double x1 = (float) xyz0[0];
                double y1 = (float) xyz0[1];
                double t1 = (float) xyz0[2];
                double x2 = (float) xyz1[0];
                double y2 = (float) xyz1[1];
                double t2 = (float) xyz1[2];
                if (t1 == i && t2 == i) g2d.draw(new Line2D.Double(x1, y1, x2, y2));
            }

            g2d.dispose();

            stack.addSlice(new ImagePlus("Graph", image).getProcessor());
        }
        return new ImagePlus("Root Model Graph", stack);
    }

    public static ImagePlus displayOnImage(org.graphstream.graph.Graph g, ImagePlus img) {
        return displayOnImage(g, img, false);
    }

    public static ImagePlus displayOnImage(org.graphstream.graph.Graph g, ImagePlus img, boolean justStack) {
        // convert to rgb image
        ImageStack rgbStack = new ImageStack(img.getWidth(), img.getHeight());
        int numSlices = img.getNSlices();
        for (int i = 1; i <= numSlices; i++) {
            rgbStack.addSlice(img.getStack().getProcessor(i).convertToRGB());
        }
        ImagePlus imag = new ImagePlus("RGB stack", rgbStack);
        ImageStack stack = imag.getImageStack();

        int numEdges = g.getEdgeSet().size();
        for (int i = 1; i <= numSlices; i++) {
            BufferedImage image = stack.getProcessor(i).getBufferedImage();
            Graphics2D g2d = image.createGraphics();
            g2d.setColor(Color.RED);

            for (org.graphstream.graph.Node node : g) {
                Object[] xyz = node.getAttribute("xyz?");
                double x = (float) xyz[0];
                double y = (float) xyz[1];
                double t = (float) xyz[2];
                boolean isInsertionPoint = (boolean) xyz[3];
                if (justStack && t == i) g2d.fill(new Ellipse2D.Double(x - 2, y - 2, 4, 4));
                else if (!justStack && t <= i) g2d.fill(new Ellipse2D.Double(x - 1, y - 1, 2, 2));
                if (!justStack && isInsertionPoint) {
                    g2d.setColor(Color.GREEN);
                    g2d.fill(new Ellipse2D.Double(x - 2, y - 2, 4, 4));
                    g2d.setColor(Color.RED);
                }
            }

            for (int j = 0; j < numEdges; j++) {
                org.graphstream.graph.Edge edge = g.getEdge(j);
                Object[] xyz0 = edge.getNode0().getAttribute("xyz?");
                Object[] xyz1 = edge.getNode1().getAttribute("xyz?");
                double x1 = (float) xyz0[0];
                double y1 = (float) xyz0[1];
                double t1 = (float) xyz0[2];
                double x2 = (float) xyz1[0];
                double y2 = (float) xyz1[1];
                double t2 = (float) xyz1[2];
                boolean isInsertionPoint = (boolean) xyz0[3];
                boolean isInsertionPoint1 = (boolean) xyz1[3];
                if (justStack && (t1 == i && t2 == i)) {
                    g2d.draw(new Line2D.Double(x1, y1, x2, y2));
                    // if line is superior length to a certain threshold, string plot
                    if (Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2)) > 100) {
                        g2d.drawString(edge.getId(), (float) (x1 + x2) / 2, (float) (y1 + y2) / 2);
                    }
                } else if (!justStack && (t1 <= i && t2 <= i)) {
                    g2d.draw(new Line2D.Double(x1, y1, x2, y2));
                    if (Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2)) > 100) {
                        g2d.drawString(edge.getId(), (float) (x1 + x2) / 2, (float) (y1 + y2) / 2);
                    }
                }
                if (!justStack && !isInsertionPoint && isInsertionPoint1 && t1 <= i && t2 <= i) {
                    g2d.setColor(Color.GREEN);
                    g2d.draw(new Line2D.Double(x1, y1, x2, y2));
                    g2d.setColor(Color.RED);
                }
            }

            g2d.dispose();
            stack.setProcessor(new ImagePlus("Graph", image).getProcessor(), i);
        }
        return new ImagePlus("Root Model Graph", stack);
    }

    public static ImagePlus displayOnImage(org.graphstream.graph.Graph g, ImagePlus img, Object o) {
        // if o is a Map<Root, List<Node>>
        if (o instanceof Map) {
            Map<Root, List<Node>> insertionPoints = (Map<Root, List<Node>>) o;
            // get all nodes position in a list
            List<Point3d> nodes = new ArrayList<>();
            for (Root r : insertionPoints.keySet()) {
                for (Node n : insertionPoints.get(r)) {
                    nodes.add(new Point3d(n.x, n.y, n.birthTime));
                }
            }
            // convert to rgb image
            ImageStack rgbStack = new ImageStack(img.getWidth(), img.getHeight());
            int numSlices = img.getNSlices();
            for (int i = 1; i <= numSlices; i++) {
                rgbStack.addSlice(img.getStack().getProcessor(i).convertToRGB());
            }
            ImagePlus imag = new ImagePlus("RGB stack", rgbStack);
            ImageStack stack = imag.getImageStack();

            int numEdges = g.getEdgeSet().size();
            for (int i = 1; i <= numSlices; i++) {
                BufferedImage image = stack.getProcessor(i).getBufferedImage();
                Graphics2D g2d = image.createGraphics();
                for (org.graphstream.graph.Node node : g) {
                    Object[] xyz = node.getAttribute("xyz?");
                    double x = (float) xyz[0];
                    double y = (float) xyz[1];
                    double t = (float) xyz[2];
                    boolean isInsertionPoint = (boolean) xyz[3];
                    if ((t == i) && (isInsertionPoint)) {
                        g2d.setColor(Color.GREEN);
                        g2d.fill(new Ellipse2D.Double(x - 2, y - 2, 4, 4));
                    } else if (t == i) {
                        g2d.setColor(Color.RED);
                        g2d.fill(new Ellipse2D.Double(x - 2, y - 2, 4, 4));
                    }
                }

                for (int j = 0; j < numEdges; j++) {
                    org.graphstream.graph.Edge edge = g.getEdge(j);
                    Object[] xyz0 = edge.getNode0().getAttribute("xyz?");
                    Object[] xyz1 = edge.getNode1().getAttribute("xyz?");
                    double x1 = (float) xyz0[0];
                    double y1 = (float) xyz0[1];
                    double t1 = (float) xyz0[2];
                    double x2 = (float) xyz1[0];
                    double y2 = (float) xyz1[1];
                    double t2 = (float) xyz1[2];
                    if (t1 == i && t2 == i) g2d.draw(new Line2D.Double(x1, y1, x2, y2));
                }

                g2d.dispose();
                stack.setProcessor(new ImagePlus("Graph", image).getProcessor(), i);
            }
            return new ImagePlus("Root Model Graph", stack);
        }
        return displayOnImage(g, img);
    }

    public static org.graphstream.graph.Graph createGraphFromRM(RootModel rootModel) {
        org.graphstream.graph.Graph g = new SingleGraph("RootModelGraph");

        for (Root r : rootModel.rootList) {
            io.github.rocsg.rsml.Node firstNode = r.firstNode;
            while (firstNode != null) {
                String nodeId = "Node_" + firstNode;
                if (g.getNode(nodeId) == null) {
                    org.graphstream.graph.Node node = g.addNode(nodeId);
                    node.setAttribute("xyz?", firstNode.x, firstNode.y, firstNode.birthTime, firstNode.isInsertionPoint);
                }
                firstNode = firstNode.child;
            }
        }

        for (Root r : rootModel.rootList) {
            io.github.rocsg.rsml.Node firstNode = r.firstNode;
            while (firstNode != null) {
                if (firstNode.child != null) {
                    String sourceId = "Node_" + firstNode;
                    String targetId = "Node_" + firstNode.child;
                    String edgeId = sourceId + "_" + targetId;
                    if (g.getEdge(edgeId) == null) {
                        g.addEdge(edgeId, sourceId, targetId);
                    }
                }
                firstNode = firstNode.child;
            }
        }

        return g;
    }

    /**
     * Function to parse the RSML files using the RsmlParser class
     *
     * @param path2RSMLs The path to the RSMLs
     * @return A Map with the date as key and the list of IRootModelParser as value
     * @throws IOException If an I/O error occurs
     */
    private Map<LocalDate, List<IRootModelParser>> parseRsmlFiles(String path2RSMLs) throws IOException {
        RsmlParser rsmlParser = new RsmlParser(path2RSMLs);
        Map<LocalDate, List<IRootModelParser>> result = RsmlParser.getRSMLsinfos(Paths.get(rsmlParser.path2RSMLs));
        result.forEach((date, rootModel4Parsers) -> {
            System.out.println("Date : " + date);
            rootModel4Parsers.forEach(System.out::println);
        });
        return result;
    }

    /**
     * Function to crop and resize the image using the specified parameters
     *
     * @param originalScaledImagePath The path to the original scaled image
     * @param subsamplingFactor       The subsampling factor
     * @param size                    The size of the image
     * @return The cropped and resized ImagePlus image
     */
    private ImagePlus cropAndResizeImage(String originalScaledImagePath, double subsamplingFactor, int size) {
        return VitimageUtils.cropImage(new ImagePlus(originalScaledImagePath), (int) (1400.0 / subsamplingFactor), (int) (350.0 / subsamplingFactor), 0, (int) ((10620.0 - 1400.0) / subsamplingFactor), (int) ((8783.0 - 350.0) / subsamplingFactor), size);
    }

    /**
     * Function to read and apply the transforms to the RootModel
     *
     * @param transformerPath The path to the transforms
     * @param rms             The RootModel
     * @param res2            The ImagePlus image
     * @param imgInitSize     The ImagePlus image of the initial size
     * @throws IOException If an I/O error occurs
     */
    private void readAndApplyTransforms(String transformerPath, RootModel rms, ImagePlus res2, ImagePlus imgInitSize) throws IOException {

        // Define these as class variables if the method is called multiple times
        final Pattern indexPattern = Pattern.compile("_(\\d+)\\.");
        final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:**.transform.tif");

        ConcurrentHashMap<Integer, ItkTransform> tempTransforms;
        try (Stream<Path> paths = Files.list(Paths.get(transformerPath))) {
            tempTransforms = (ConcurrentHashMap<Integer, ItkTransform>) paths
                    .parallel()
                    .filter(pathMatcher::matches)
                    .collect(Collectors.toConcurrentMap(
                            path -> {
                                Matcher matcher = indexPattern.matcher(path.getFileName().toString());
                                matcher.find();
                                return Integer.parseInt(matcher.group(1));
                            },
                            path -> new ItkTransform(ItkTransform.readAsDenseField(path.toString())),
                            (oldValue, newValue) -> newValue // In case of key collision
                    ));
        }

        // Add transforms to the list in the correct order
        IntStream.rangeClosed(1, tempTransforms.size())
                .forEach(i -> transforms.add(tempTransforms.get(i)));

        // creating a linear transform for the crop issue
        Point3d[] oldPos = new Point3d[1];
        Point3d[] newPos = new Point3d[1];
        oldPos[0] = new Point3d(0, 0, 0);
        newPos[0] = new Point3d(-pph.getxMinCrop(), -pph.getyMinCrop(), 0);
        ItkTransform linearTransform = ItkTransform.estimateBestTranslation3D(oldPos, newPos);


        for (ItkTransform transform : this.transforms) {
            rms.applyTransformToGeometry(linearTransform, transforms.indexOf(transform) + 1);
            rms.applyTransformToGeometry(transform, transforms.indexOf(transform) + 1);
        }
        rms.applyTransformToGeometry(linearTransform, transforms.size() + 1);
    }

    private void applySingleTransform(ItkTransform transform, RootModel rms) {
        for (Root root : rms.rootList) {
            Node firstNode = root.firstNode;
            while (firstNode != null) {
                Point3d point = new Point3d(firstNode.x, firstNode.y, firstNode.birthTime);
                point = transform.transformPoint(point);
                firstNode.x = (float) point.x;
                firstNode.y = (float) point.y;
                firstNode = firstNode.child;
            }
        }
    }

    // lookup for all the points of a root at time t
    private void showBlankWithPointOnRoot(List<Root> r, ImagePlus forSize) {
        ImagePlus blank = IJ.createImage("Blank", "RGB white", forSize.getWidth(), forSize.getHeight(), 1);
        List<DoublePoint> points;
        int birthTime = 0;
        int lastTime = forSize.getNSlices();

        for (int i = 0; i < r.size(); i++) {
            points = new ArrayList<>();
            Node firstnode = r.get(i).firstNode;
            birthTime = (int) firstnode.birthTime;
            // add the points to the blank image
            while (firstnode != null) {
                points.add(new DoublePoint(new double[]{firstnode.x, firstnode.y, firstnode.birthTime}));
                System.out.println("x : " + firstnode.x + " y : " + firstnode.y + " t : " + firstnode.birthTime);
                lastTime = (int) firstnode.birthTime;
                firstnode = firstnode.child;
            }

            // Perform clustering
            int k = r.get(i).getNodesList().size() / (lastTime - birthTime + 1);
            KMeansPlusPlusClusterer<DoublePoint> clusterer = new KMeansPlusPlusClusterer<>(k);
            List<CentroidCluster<DoublePoint>> clusters = clusterer.cluster(points);

            ImageProcessor ip = blank.getProcessor();
            for (int clusterIndex = 0; clusterIndex < clusters.size(); clusterIndex++) {
                Cluster<DoublePoint> cluster = clusters.get(clusterIndex);
                // random color
                ip.setColor(new Color((int) (Math.random() * 0x1000000)));
                for (DoublePoint point : cluster.getPoints()) {
                    //ip.drawDot((int) point.getPoint()[0], (int) point.getPoint()[1]);
                    ip.drawDot((int) clusters.get(clusterIndex).getCenter().getPoint()[0], (int) clusters.get(clusterIndex).getCenter().getPoint()[1]);
                    //ip.drawString(String.valueOf(clusters.get(clusterIndex).getCenter().getPoint()[2]), (int) clusters.get(clusterIndex).getCenter().getPoint()[0], (int) clusters.get(clusterIndex).getCenter().getPoint()[1]);
                    if (cluster.getPoints().get(0) == point) {
                        //ip.drawString(String.valueOf(point.getPoint()[2]), (int) point.getPoint()[0], (int) point.getPoint()[1]);
                        System.out.println("Cluster " + clusterIndex + " : " + point.getPoint()[0] + " " + point.getPoint()[1] + " " + point.getPoint()[2]);
                    }
                    if (point.getPoint()[2] < clusters.get(clusterIndex).getCenter().getPoint()[2]) {
                        // same but smaller string on image
                        //ip.drawString(String.valueOf(point.getPoint()[2]), (int) point.getPoint()[0], (int) point.getPoint()[1]);
                        System.out.println("Missclassified ? x : " + point.getPoint()[0] + " y : " + point.getPoint()[1] + " t : " + point.getPoint()[2]);
                    }
                }
            }
        }
        blank.show();

    }


    // Getting better points for interpolation
    public void getBetterPoints(RootModel rms) {
        Map<Root, List<List<Point3d>>> newPoints = new HashMap<>();
        Map<Root, List<Node>> insertionPoints = rms.getInsertionPoints();
        for (Root root : insertionPoints.keySet()) {
            newPoints.putIfAbsent(root, new ArrayList<>());
            for (Node node : insertionPoints.get(root)) {
                Node n = insertionPoints.get(root).get(insertionPoints.get(root).indexOf(node));
                newPoints.get(root).add(new ArrayList<>());
                newPoints.get(root).get(newPoints.get(root).size() - 1).add(new Point3d(n.x, n.y, n.birthTime));
                Node nChild = n.child;
                while ((nChild != null) && (nChild.birthTime == n.birthTime)) {
                    // there is a line between n and nChild, we want to get the points on this line
                    double x1 = n.x;
                    double y1 = n.y;
                    double x2 = nChild.x;
                    double y2 = nChild.y;
                    double dx = x2 - x1;
                    double dy = y2 - y1;
                    double d = Math.sqrt(dx * dx + dy * dy);
                    double step = 1;
                    while (step < d) {
                        double x = x1 + step * dx / d;
                        double y = y1 + step * dy / d;
                        newPoints.get(root).get(newPoints.get(root).size() - 1).add(new Point3d(x, y, n.birthTime));
                        step += STANDART_DIST;
                    }
                    n = nChild;
                    nChild = nChild.child;
                }
            }
        }
    }


    // interpolating points

    /**
     * Function to interpolate the points using the SplineFitter
     * Then display the interpolated points on the image
     *
     * @param rms The RootModel (source of the points)
     * @param img The ImagePlus image (destination of the points)
     */
    private void interpolatePoints(RootModel rms, ImagePlus img) {
        /*Map<Root, List<Node>> insertionPts = rms.getInsertionPoints();
        insertionPts.keySet().parallelStream().forEach(root -> {
            List<Node> nodes = insertionPts.get(root);
            nodes.forEach(node -> {
                // node is an insertion point
                // get its position and the positions of its childs that appeared at the same time
                if (root.order == 1)
                {
                    System.out.println("Root order 1");
                }
                List<Node> node2Interpolate = new ArrayList<>();
                node2Interpolate.add(node);
                while (node.child != null && node.child.birthTime == node.birthTime) {
                    node2Interpolate.add(node.child);
                    node = node.child;
                }
                try {
                    // find the best polynomial to interpolate the points
                    if (node2Interpolate.size() > 1) {
                        double[] x = new double[node2Interpolate.size()];
                        double[] y = new double[node2Interpolate.size()];
                        IntStream.range(0, node2Interpolate.size()).forEach(i -> {
                            x[i] = node2Interpolate.get(i).x;
                            y[i] = node2Interpolate.get(i).y;
                        });

                        CurveFitter curveFitter = new CurveFitter(x,y);
                        curveFitter.doFit(CurveFitter.POLY8);

                        // Display the interpolation on the image
                        ImageProcessor ip = img.getStack().getProcessor((int) node.birthTime).duplicate();
                        ip.setColor(Color.BLUE);
                        // node2Interpolate.forEach(n -> ip.drawOval((int) n.x, (int) n.y, 4, 4));
                        // draw full curve on the image TODO mayeb problematic plot
                        Node leftestNode = node2Interpolate.stream().min(Comparator.comparingDouble(n -> n.x)).get();
                        Node rightestNode = node2Interpolate.stream().max(Comparator.comparingDouble(n -> n.x)).get();
                        for (int i = (int) leftestNode.x; i < rightestNode.x; i++) {
                            ip.drawDot(i, (int) curveFitter.f(i));
                            // draw line between the points
                            if (i > leftestNode.x)
                                ip.drawLine((int) i - 1, (int) curveFitter.f(i - 1), (int) i, (int) curveFitter.f(i));
                        }

                        img.getStack().setProcessor(ip, (int) node.birthTime);
                        System.out.println("Interpolated points : " + node2Interpolate);
                    }
                } catch (Exception e) {
                    System.err.println("Interpolation failed: " + e.getMessage());
                }
            });
        });
        img.show();*/
        Map<Root, List<Node>> insertionPts = rms.getInsertionPoints();
        insertionPts.keySet().parallelStream().forEach(root -> {
            List<Node> nodes = insertionPts.get(root);
            nodes.forEach(node -> {
                // node is an insertion point
                // get its position and the positions of its children that appeared at the same time
                List<Node> node2Interpolate = new ArrayList<>();
                node2Interpolate.add(node);
                while (node.child != null && node.child.birthTime == node.birthTime) {
                    node2Interpolate.add(node.child);
                    node = node.child;
                }
                try {
                    // find the best polynomial to interpolate the points
                    if (node2Interpolate.size() > 1) {
                        double[] x = new double[node2Interpolate.size()];
                        double[] y = new double[node2Interpolate.size()];
                        IntStream.range(0, node2Interpolate.size()).forEach(i -> {
                            // Swap x and y coordinates
                            x[i] = node2Interpolate.get(i).y;
                            y[i] = node2Interpolate.get(i).x;
                        });

                        CurveFitter curveFitter = new CurveFitter(x, y);
                        curveFitter.doFit(CurveFitter.POLY8);

                        // Display the interpolation on the image
                        ImageProcessor ip = img.getStack().getProcessor((int) node.birthTime).duplicate();
                        ip.setColor(Color.BLUE);

                        // draw dots on interpolated points of x and draw line between these points
                        drawThing0(x, y, ip, curveFitter);

                        img.getStack().setProcessor(ip, (int) node.birthTime);
                        System.out.println("Interpolated points : " + node2Interpolate);
                    }
                } catch (Exception e) {
                    System.err.println("Interpolation failed: " + e.getMessage());
                }
            });
        });
        img.setTitle("Interpolated points - Polynomial");
        img.show();
    }

    private void ensureIncreasingXValues(double[] x, double[] y) {
        boolean allIncreasing = true;
        boolean allDecreasing = true;

        for (int i = 1; i < x.length; i++) {
            if (x[i] < x[i - 1]) {
                allIncreasing = false;
            }
            if (x[i] > x[i - 1]) {
                allDecreasing = false;
            }
        }

        if (allDecreasing) {
            reverseArray(x);
            reverseArray(y);
        } else if (!allIncreasing) {
            // Find segments that are increasing or decreasing
            int start = 0;
            for (int i = 1; i < x.length; i++) {
                if ((x[i] < x[i - 1] && x[start] < x[i - 1]) || (x[i] > x[i - 1] && x[start] > x[i - 1])) {
                    handleSegment(Arrays.copyOfRange(x, start, i), Arrays.copyOfRange(y, start, i));
                    start = i;
                }
            }
            handleSegment(Arrays.copyOfRange(x, start, x.length), Arrays.copyOfRange(y, start, y.length));
        }
    }

    private void handleSegment(double[] xSegment, double[] ySegment) {
        if (xSegment.length < 2) {
            return; // Not enough points to interpolate
        }
        if (xSegment[0] > xSegment[xSegment.length - 1]) {
            reverseArray(xSegment);
            reverseArray(ySegment);
        }
        ensureIncreasingXValues(xSegment, ySegment);
    }

    private void reverseArray(double[] array) {
        int n = array.length;
        for (int i = 0; i < n / 2; i++) {
            double temp = array[i];
            array[i] = array[n - 1 - i];
            array[n - 1 - i] = temp;
        }
    }

    /**
     * Function to interpolate the points using the RBFInterpolator class
     *
     * @param rms The RootModel (source of the points)
     * @param img The ImagePlus image (destination of the points)
     */
    private void interpolatePointsRBF(RootModel rms, ImagePlus img) {
        /*Map<Root, List<Node>> insertionPts = rms.getInsertionPoints();
        insertionPts.keySet().forEach(root -> {
            List<Node> nodes = insertionPts.get(root);
            nodes.forEach(node -> {
                // node is an insertion point
                // get its position and the positions of its childs that appeared at the same time
                List<Node> node2Interpolate = new ArrayList<>();
                node2Interpolate.add(node);
                while (node.child != null && node.child.birthTime == node.birthTime) {
                    node2Interpolate.add(node.child);
                    node = node.child;
                }
                try {
                    // use RBF interpolation if we have more than one point
                    if (node2Interpolate.size() > 1) {
                        double[] x = new double[node2Interpolate.size()];
                        double[] y = new double[node2Interpolate.size()];
                        IntStream.range(0, node2Interpolate.size()).forEach(i -> {
                            x[i] = node2Interpolate.get(i).x;
                            y[i] = node2Interpolate.get(i).y;
                        });

                        // Create an RBF interpolator
                        double epsilon = 1.0; // Set an appropriate epsilon value
                        RBFInterpolator rbfInterpolator = new RBFInterpolator(x, y, epsilon);

                        // Display the interpolation on the image
                        ImageProcessor ip = img.getStack().getProcessor((int) node.birthTime).duplicate();
                        ip.setColor(Color.BLUE);

                        // Find the range of x values to plot
                        double minX = Arrays.stream(x).min().getAsDouble();
                        double maxX = Arrays.stream(x).max().getAsDouble();

                        // Plot the interpolated curve
                        for (double i = minX; i <= maxX; i += 0.1) {
                            ip.drawDot((int) i, (int) rbfInterpolator.interpolate(i));

                            // draw line between the points
                            if (i > minX)
                                ip.drawLine((int) i - 1, (int) rbfInterpolator.interpolate(i - 1), (int) i, (int) rbfInterpolator.interpolate(i));
                        }

                        img.getStack().setProcessor(ip, (int) node.birthTime);
                        System.out.println("Interpolated points : " + node2Interpolate);
                    }
                } catch (Exception e) {
                    System.err.println("Interpolation failed: " + e.getMessage());
                }
            });
        });
        img.show();*/
        Map<Root, List<Node>> insertionPts = rms.getInsertionPoints();
        insertionPts.keySet().forEach(root -> {
            List<Node> nodes = insertionPts.get(root);
            nodes.forEach(node -> {
                // node is an insertion point
                // get its position and the positions of its children that appeared at the same time
                List<Node> node2Interpolate = new ArrayList<>();
                node2Interpolate.add(node);
                while (node.child != null && node.child.birthTime == node.birthTime) {
                    node2Interpolate.add(node.child);
                    node = node.child;
                }
                try {
                    // use RBF interpolation if we have more than one point
                    if (node2Interpolate.size() > 1) {
                        double[] x = new double[node2Interpolate.size()];
                        double[] y = new double[node2Interpolate.size()];
                        IntStream.range(0, node2Interpolate.size()).forEach(i -> {
                            // Swap x and y coordinates
                            x[i] = node2Interpolate.get(i).y;
                            y[i] = node2Interpolate.get(i).x;
                        });

                        // Create an RBF interpolator
                        double epsilon = 1.0; // Set an appropriate epsilon value
                        RBFInterpolator rbfInterpolator = new RBFInterpolator(x, y, epsilon);

                        // Display the interpolation on the image
                        ImageProcessor ip = img.getStack().getProcessor((int) node.birthTime).duplicate();
                        ip.setColor(Color.BLUE);

                        // draw dots on interpolated points of x and draw line between these points
                        drawThing0(x, y, ip, rbfInterpolator);

                        img.getStack().setProcessor(ip, (int) node.birthTime);
                        System.out.println("Interpolated points : " + node2Interpolate);
                    }
                } catch (Exception e) {
                    System.err.println("Interpolation failed: " + e.getMessage());
                }
            });
        });
        img.setTitle("Interpolated points - RBF");
        img.show();
    }

    /**
     * Function to interpolate the points using the Bezier function method
     *
     * @param rms The RootModel (source of the points)
     * @param img The ImagePlus image (destination of the points)
     */
    private void interpolatePointsBezier(RootModel rms, ImagePlus img) {
        Map<Root, List<Node>> insertionPts = rms.getInsertionPoints();
        insertionPts.keySet().forEach(root -> {
            List<Node> nodes = insertionPts.get(root);
            nodes.forEach(node -> {
                List<Node> node2Interpolate = new ArrayList<>();
                node2Interpolate.add(node);
                while (node.child != null && node.child.birthTime == node.birthTime) {
                    node2Interpolate.add(node.child);
                    node = node.child;
                }
                try {
                    if (node2Interpolate.size() > 2) {
                        double[] x = new double[node2Interpolate.size()];
                        double[] y = new double[node2Interpolate.size()];
                        for (int i = 0; i < node2Interpolate.size(); i++) {
                            x[i] = node2Interpolate.get(i).x;
                            y[i] = node2Interpolate.get(i).y;
                        }
                        // Interpolation par courbe de Bézier
                        ImageProcessor ip = img.getStack().getProcessor((int) node.birthTime);
                        ip.setColor(Color.BLUE);

                        for (double t = 0; t <= 1; t += 0.01) {
                            double xt = 0, yt = 0;
                            int n = node2Interpolate.size() - 1;
                            for (int i = 0; i <= n; i++) {
                                double binomialCoeff = binomialCoefficient(n, i) * Math.pow(1 - t, n - i) * Math.pow(t, i);
                                xt += binomialCoeff * x[i];
                                yt += binomialCoeff * y[i];
                            }
                            ip.drawDot((int) xt, (int) yt);

                            // draw line between the points
                            if (t > 0) {
                                double prevT = t - 0.01;
                                double prevX = 0, prevY = 0;
                                for (int i = 0; i <= n; i++) {
                                    double binomialCoeff = binomialCoefficient(n, i) * Math.pow(1 - prevT, n - i) * Math.pow(prevT, i);
                                    prevX += binomialCoeff * x[i];
                                    prevY += binomialCoeff * y[i];
                                }
                                ip.drawLine((int) prevX, (int) prevY, (int) xt, (int) yt);
                            }
                        }

                        img.getStack().setProcessor(ip, (int) node.birthTime);
                        System.out.println("Interpolated points : " + node2Interpolate);
                    }
                } catch (Exception e) {
                    System.err.println("Interpolation failed: " + e.getMessage());
                }
            });
        });
        img.setTitle("Interpolated points - Bezier");
        img.show();
    }

    private double binomialCoefficient(int n, int k) {
        double res = 1;
        if (k > n - k) k = n - k;
        for (int i = 0; i < k; ++i) {
            res *= (n - i);
            res /= (i + 1);
        }
        return res;
    }

    /**
     * Function to interpolate the points using the SplineFitter class
     *
     * @param rms The RootModel (source of the points)
     * @param img The ImagePlus image (destination of the points)
     */
    private void interpolatePointsSplineFitter(RootModel rms, ImagePlus img) {
        Map<Root, List<Node>> insertionPts = rms.getInsertionPoints();
        insertionPts.keySet().forEach(root -> {
            List<Node> nodes = insertionPts.get(root);
            nodes.forEach(node -> {
                List<Node> node2Interpolate = new ArrayList<>();
                node2Interpolate.add(node);
                while (node.child != null && node.child.birthTime == node.birthTime) {
                    node2Interpolate.add(node.child);
                    node = node.child;
                }
                try {
                    if (node2Interpolate.size() > 2) {
                        float[] x = new float[node2Interpolate.size()];
                        float[] y = new float[node2Interpolate.size()];
                        for (int i = 0; i < node2Interpolate.size(); i++) {
                            // Swap x and y coordinates
                            x[i] = node2Interpolate.get(i).y;
                            y[i] = node2Interpolate.get(i).x;
                        }

                        SplineFitter fitter = new SplineFitter(x, y, x.length);


                        // Display the interpolation on the image
                        ImageProcessor ip = img.getStack().getProcessor((int) node.birthTime);
                        ip.setColor(Color.BLUE);


                        // draw dots on interpolated points of x and draw line between these points
                        drawThing0(x, y, ip, fitter);

                        img.getStack().setProcessor(ip, (int) node.birthTime);
                        System.out.println("Interpolated points : " + node2Interpolate);
                    }
                } catch (Exception e) {
                    System.err.println("Interpolation failed: " + e.getMessage());
                }
            });
        });
        img.setTitle("Interpolated points - Spline Fitter");
        img.show();
    }

    private void interpolatePointsCurveFitter(RootModel rms, ImagePlus img) {
        Map<Root, List<Node>> insertionPts = rms.getInsertionPoints();
        final String[] name = {""};
        insertionPts.keySet().forEach(root -> {
            List<Node> nodes = insertionPts.get(root);
            nodes.forEach(node -> {
                List<Node> node2Interpolate = new ArrayList<>();
                node2Interpolate.add(node);
                while (node.child != null && node.child.birthTime == node.birthTime) {
                    node2Interpolate.add(node.child);
                    node = node.child;
                }
                try {
                    if (node2Interpolate.size() > 2) {
                        double[] x = new double[node2Interpolate.size()];
                        double[] y = new double[node2Interpolate.size()];
                        for (int i = 0; i < node2Interpolate.size(); i++) {
                            // Swap x and y coordinates
                            x[i] = node2Interpolate.get(i).y;
                            y[i] = node2Interpolate.get(i).x;
                        }

                        CurveFitter curveFitter = new CurveFitter(x, y);
                        curveFitter.doFit(CurveFitter.GAUSSIAN);
                        name[0] = curveFitter.getName();

                        // Display the interpolation on the image
                        ImageProcessor ip = img.getStack().getProcessor((int) node.birthTime);
                        ip.setColor(Color.BLUE);

                        // draw dots on interpolated points of x and draw line between these points
                        drawThing0(x, y, ip, curveFitter);

                        img.getStack().setProcessor(ip, (int) node.birthTime);
                        System.out.println("Interpolated points : " + node2Interpolate);

                    }
                } catch (Exception e) {
                    System.err.println("Interpolation failed: " + e.getMessage());
                }
            });
        });
        img.setTitle("Interpolated points - " + name[0] + " Curve Fitter");
        img.show();
    }

    // draw things
    private void drawOnBlackLines(double[] x, double[] y, ImageProcessor ip) {
        for (int i = 0; i < x.length - 1; i++) {
            double x1 = x[i];
            double y1 = y[i];
            double x2 = x[i + 1];
            double y2 = y[i + 1];

            // Calculate the distance between consecutive points
            double dx = x2 - x1;
            double dy = y2 - y1;

            // Sample points along the line segment at regular intervals
            for (double t = 0; t <= 1; t += 0.01) {
                double px = x1 + t * dx;
                double py = y1 + t * dy;

                // Check the intensity of surrounding pixels and change their color if necessary
                for (int xOffset = -1; xOffset <= 1; xOffset++) {
                    for (int yOffset = -1; yOffset <= 1; yOffset++) {
                        int intensity = ip.getPixel((int) px + xOffset, (int) py + yOffset);
                        if (intensity <= 30) { // Adjust threshold as needed
                            ip.setColor(Color.ORANGE);
                            ip.drawDot((int) py + yOffset, (int) px + xOffset);
                        }
                    }
                }
            }
        }
    }

    private void drawThing0(double[] x, double[] y, ImageProcessor ip, CurveFitter curveFitter) {
        for (int i = (int) x[0]; i < x[x.length - 1]; i++) {
            ip.drawDot((int) curveFitter.f(i), i);
            // draw line between the points
            if (i > x[0])
                ip.drawLine((int) curveFitter.f(i - 1), i - 1, (int) curveFitter.f(i), i);
        }
    }

    private void drawThing0(float[] x, float[] y, ImageProcessor ip, SplineFitter fitter) {
        for (float xPoint : x) {
            float interpolatedY = (float) fitter.evalSpline(xPoint);
            ip.drawDot((int) interpolatedY, (int) xPoint);
            // draw line between the points
            if (xPoint > 0) {
                float prevInterpolatedY = (float) fitter.evalSpline(xPoint - 1);
                ip.drawLine((int) prevInterpolatedY, (int) (xPoint - 1), (int) interpolatedY, (int) xPoint);
            }
        }
    }

    private void drawThing0(double[] x, double[] y, ImageProcessor ip, RBFInterpolator interpolator) {
        for (double xPoint : x) {
            double interpolatedY = interpolator.interpolate(xPoint);
            ip.drawDot((int) interpolatedY, (int) xPoint);
            if (xPoint > 0) {
                double prevInterpolatedY = interpolator.interpolate(xPoint - 1);
                ip.drawLine((int) prevInterpolatedY, (int) (xPoint - 1), (int) interpolatedY, (int) xPoint);
            }
        }
    }
}

class PlantReconstruction {
    RootModel rms;
    float maxTime;
    Map<Root, List<Node>> insertionPoints;
    Map<Root, List<Double>> rootLengths;
    Map<Root, List<Double>> speedGrowth;

    public PlantReconstruction(RootModel rm) {
        this.rms = rm;
        this.maxTime = 0;
        for (Root root : rms.rootList) {
            Node firstNode = root.firstNode;
            while (firstNode != null) {
                if (firstNode.birthTime > maxTime) {
                    maxTime = firstNode.birthTime;
                }
                firstNode = firstNode.child;
            }
        }
        this.insertionPoints = rms.getInsertionPoints();

        this.rootLengths = new HashMap<>();
        this.speedGrowth = new HashMap<>();
        for (Root root : rms.rootList) {
            rootLengths.putIfAbsent(root, new ArrayList<>());
            speedGrowth.putIfAbsent(root, new ArrayList<>());
            double length = 0;
            double speed = 0;
            for (int i = 1; i < maxTime + 1; i++) {
                length = root.lenghtRootAtTimeT(i);
                rootLengths.get(root).add(length);
                if (rootLengths.get(root).size() > 1) {
                    speed = (rootLengths.get(root).get(rootLengths.get(root).size() - 1) - rootLengths.get(root).get(rootLengths.get(root).size() - 2)) / 24.0; // TODO : put write time steps
                    // TODO : verify assumption no retracting roots
                    speed = (speed < 0 ? 0 : speed);
                    speedGrowth.get(root).add(speed);
                }
            }
        }
        System.out.println("Root lengths : " + rootLengths);
    }
}

class RBFInterpolator {
    private final double[][] points;
    private final double[] values;
    private final double epsilon;

    public RBFInterpolator(double[] x, double[] y, double epsilon) {
        this.points = new double[x.length][2];
        this.values = new double[y.length];
        this.epsilon = epsilon;
        for (int i = 0; i < x.length; i++) {
            this.points[i][0] = x[i];
            this.points[i][1] = 0;
            this.values[i] = y[i];
        }
    }

    public double interpolate(double x) {
        double result = 0;
        double normFactor = 0;
        for (int i = 0; i < points.length; i++) {
            double distance = Math.abs(x - points[i][0]);
            double weight = gaussianRBF(distance);
            result += weight * values[i];
            normFactor += weight;
        }
        return result / normFactor;
    }

    private double gaussianRBF(double distance) {
        return Math.exp(-Math.pow(distance / epsilon, 2));
    }
}
