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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
        RsmlParser rsmlParser = new RsmlParser("D:\\loaiu\\MAM5\\Stage\\data\\UC3\\Rootsystemtracker\\Original_Data\\B73_R04_01\\");
        Map<Date, List<RootModel4Parser>> result = getRSMLsinfos(Paths.get(rsmlParser.path2RSMLs));
        result.forEach((date, rootModel4Parsers) -> {
            System.out.println("Date : " + date);
            rootModel4Parsers.forEach(System.out::println);
        });
    }

    private static void parseRoots(NodeList rootList, Plant parentPlant, Root4Parser parentRoot, int order) { // TODO : we assumed 2nd order root max
        for (int i = 0; i < rootList.getLength(); i++) {
            org.w3c.dom.Node rootNode = rootList.item(i);

            if (rootNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                Element rootElement = (Element) rootNode;
                String rootID = rootElement.getAttribute("ID");
                String rootLabel = rootElement.getAttribute("label");

                Root4Parser root = new Root4Parser(rootID, rootLabel, parentRoot, order);

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
                parseRoots(childRoots, parentPlant, root, order + 1);
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
    public static Map<Date, List<RootModel4Parser>> getRSMLsinfos(Path folderPath) throws IOException {
        // check the uniqueness of the rsml files
        Stack<String> keptRsmlFiles = checkUniquenessRSMLs(folderPath);

        // get Date of each rsml (that supposetly match the image date) // TODO generalize
        Pattern pattern = Pattern.compile("\\d{2}_\\d{2}_\\d{4}");
        ConcurrentHashMap<String, Date> fileDates = new ConcurrentHashMap<>();

        Files.list(folderPath)
                .parallel()
                .filter(path -> path.toString().matches(".*\\.(rsml|rsml01|rsml02|rsml03|rsml04)$"))
                .forEach(path -> {
                    String file = path.toString();
                    Matcher matcher = pattern.matcher(file);
                    if (matcher.find()) {
                        int year = Integer.parseInt(matcher.group(0).split("_")[2]) - 1900;
                        int month = Integer.parseInt(matcher.group(0).split("_")[1]) - 1;
                        int day = Integer.parseInt(matcher.group(0).split("_")[0]);
                        fileDates.put(file, new Date(year, month, day));
                    }
                });

        Map<Date, List<RootModel4Parser>> rsmlInfos = new TreeMap<>();

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
                        metadata.modifDate = metadataElement.getElementsByTagName("last-modified").item(0).getTextContent();
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
                                parseRoots(rootList, plant, null, 1);
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

}
// TODO : handle properties by metadata definition in rsml file

class RootModel4Parser {
    public final List<Scene> scenes;
    public Metadata metadatas;

    public RootModel4Parser() {
        this.scenes = new ArrayList<>();
        this.metadatas = new Metadata();
    }

    public RootModel4Parser(Metadata metadata) {
        this.scenes = new ArrayList<>();
        this.metadatas = metadata;
    }

    public void addScene(Scene scene) {
        this.scenes.add(scene);
    }

    // get metadata elements
    public float getVersion() {
        return metadatas.version;
    }

    public String getUnit() {
        return metadatas.unit;
    }

    public float getResolution() {
        return metadatas.resolution;
    }

    public String getModifDate() {
        return metadatas.modifDate;
    }

    public String getSoftware() {
        return metadatas.software;
    }

    public String getUser() {
        return metadatas.user;
    }

    public String getFileKey() {
        return metadatas.fileKey;
    }

    public List<Scene> getScenes() {
        return scenes;
    }

    @Override
    public String toString() {
        return "RootModel4Parser{" +
                "scenes=" + scenes +
                '}';
    }
}

// Metadata.java
class Metadata {
    public List<PropertyDefinition> propertiedef;
    float version;
    String unit;
    float resolution;
    String modifDate;
    String software;
    String user;
    String fileKey;

    public Metadata() {
        this.version = 0;
        this.unit = "";
        this.resolution = 0;
        this.modifDate = "";
        this.software = "";
        this.user = "";
        this.fileKey = "";
        this.propertiedef = new ArrayList<>();
    }

    public Metadata(Metadata metadata) {
        this.version = metadata.version;
        this.unit = metadata.unit;
        this.resolution = metadata.resolution;
        this.modifDate = metadata.modifDate;
        this.software = metadata.software;
        this.user = metadata.user;
        this.fileKey = metadata.fileKey;
        this.propertiedef = new ArrayList<>();
        for (PropertyDefinition propertiedef : metadata.propertiedef) {
            this.propertiedef.add(new PropertyDefinition(propertiedef.label, propertiedef.type, propertiedef.unit));
        }
    }

    class PropertyDefinition {
        // Mapping label - type - unit
        public String label;
        public String type;
        public String unit;

        public PropertyDefinition() {
            this.label = "";
            this.type = "";
            this.unit = "";
        }

        public PropertyDefinition(String label, String type, String unit) {
            this.label = label;
            this.type = type;
            this.unit = unit;
        }
    }
}

// Scene.java
class Scene {
    private final List<Plant> plants;

    public Scene() {
        this.plants = new ArrayList<>();
    }

    public void addPlant(Plant plant) {
        this.plants.add(plant);
    }

    public List<Plant> getPlants() {
        return plants;
    }

    @Override
    public String toString() {
        return "Scene{" +
                "plants=" + plants +
                '}';
    }
}

// Plant.java
class Plant {
    final List<Root4Parser> roots;

    public Plant() {
        this.roots = new ArrayList<>();
    }

    public void addRoot(Root4Parser root) {
        this.roots.add(root);
    }

    public List<String> getListID() {
        List<String> listID = new ArrayList<>();
        for (Root4Parser root : roots) {
            listID.add(root.id);
        }
        return listID;
    }

    public Root4Parser getRootByID(String id) {
        for (Root4Parser root : roots) {
            if (root.id.equals(id)) {
                return root;
            }
        }
        return null;
    }

    public List<Root4Parser> getRoots() {
        return roots;
    }

    @Override
    public String toString() {
        return "\nPlant{" +
                "roots=" + roots +
                "}\n";
    }
} // TODO : strong assumption : No new plant is created in the same scene
