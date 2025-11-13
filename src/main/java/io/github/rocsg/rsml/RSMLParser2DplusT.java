package io.github.rocsg.rsml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * The RSMLParser2DT class provides methods to parse RSML files and extract root models.
 */
public class RSMLParser2DplusT {

    /**
     * The main method to execute the RSML parsing and writing process.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        String rsmlFile = "/home/loai/Images/DataTest/230629PN016/61_graph_expertized.rsml";
        RootModel rm = rootModelReadFromRsml(rsmlFile);
        RSMLWriter2DplusT.writeRSML(rm, "/home/loai/Images/DataTest/230629PN016/71_graph_expertized-Rewritten.rsml");
        System.out.println("Done");
    }

    /**
     * Reads an RSML file and returns a RootModel object.
     *
     * @param rsmlFile The path to the RSML file.
     * @return A RootModel object containing the parsed data.
     */
    public static RootModel rootModelReadFromRsml(String rsmlFile) {
        RootModel rootModels = new RootModel();
        try {
            File file = new File(rsmlFile);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file);
            doc.getDocumentElement().normalize();

            Element rootElement = doc.getDocumentElement();
            NodeList sceneNodes = rootElement.getElementsByTagName("scene");
            RootModel rootModel = new RootModel();
            for (int i = 0; i < sceneNodes.getLength(); i++) {
                Element sceneElement = (Element) sceneNodes.item(i);
                parseScene(sceneElement, rootModel);
                parseMetadata(rootElement, rootModel);
                rootModel.standardOrderingOfRoots();
                rootModel.resampleFlyingRoots();
                return rootModel;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Error in reading RSML file");
        return rootModels;
    }

    /**
     * Parses the metadata from the RSML file and populates the RootModel object.
     *
     * @param rootElement The root element of the RSML document.
     * @param rm          The RootModel object to populate.
     */
    private static void parseMetadata(Element rootElement, RootModel rm) {
        Element metadata = (Element) rootElement.getElementsByTagName("metadata").item(0);
        rm.initializeMetadata();
        Metadata rootMetadata = rm.metadata;

        rootMetadata.setVersion(Float.parseFloat(metadata.getElementsByTagName("version").item(0).getTextContent()));
        rootMetadata.setUnit(metadata.getElementsByTagName("unit").item(0).getTextContent());
        rootMetadata.setSize(Double.parseDouble(metadata.getElementsByTagName("size").item(0).getTextContent()));
        rm.pixelSize = (float) rootMetadata.getSize();
        rootMetadata.setSoftware(metadata.getElementsByTagName("software").item(0).getTextContent());
        rootMetadata.setUser(metadata.getElementsByTagName("user").item(0).getTextContent());
        rootMetadata.setFileKey(metadata.getElementsByTagName("file-key").item(0).getTextContent());

        String[] observationHours = metadata.getElementsByTagName("observation-hours").item(0).getTextContent().split(",");
        double[] observationHoursDouble = new double[observationHours.length + 1];
        observationHoursDouble[0] = 0.0;
        for (int i = 0; i < observationHours.length; i++) {
            observationHoursDouble[i + 1] = Double.parseDouble(observationHours[i]);
        }
        rootMetadata.setObservationHours(observationHoursDouble);
        rm.hoursCorrespondingToTimePoints = rootMetadata.getObservationHours();

        Element image = (Element) metadata.getElementsByTagName("image").item(0);
        rootMetadata.addImageInfo("label", image.getElementsByTagName("label").item(0).getTextContent());
        rootMetadata.addImageInfo("sha256", image.getElementsByTagName("sha256").item(0).getTextContent());
    }

    /**
     * Parses the scene element from the RSML file and populates the RootModel object.
     * Find all plant elements in the scene and for each plant, extract its roots (primaries) and their geometries recursively.
     *
     * @param sceneElement The scene element to parse.
     * @param rootModel    The RootModel object to populate.
     */
    private static void parseScene(Element sceneElement, RootModel rootModel) {
        NodeList plantNodes = sceneElement.getElementsByTagName("plant");

        for (int i = 0; i < plantNodes.getLength(); i++) {
            Element plantElement = (Element) plantNodes.item(i);
            Plant plant = new Plant();
            plant.id = (plantElement.getAttribute("ID"));
            plant.label = (plantElement.getAttribute("label"));

            rootModel.imgName = plantElement.getAttribute("label");

            NodeList rootNodes = plantElement.getElementsByTagName("root");
            for (int j = 0; j < rootNodes.getLength(); j++) {
                Element rootElement = (Element) rootNodes.item(j);
                parseRoot(rootElement, null, rootModel, 1, new HashSet<String>());
            }
        }
    }

    /**
     * Parses the root element from the RSML file and populates the RootModel object.
     *
     *
     * @param rootElement The root element to parse.
     * @param parentRoot  The parent root, if any.
     * @param rm          The RootModel object to populate.
     * @param order       The order of the root.
     * @param rootsLabel  A set of root labels.
     */
    private static void parseRoot(Element rootElement, Root parentRoot, RootModel rm, int order, Set<String> rootsLabel) {
        int ord = rootElement.getAttribute("ID").split("\\.").length - 1;
        if (ord != order) return;
        Root root = new Root(null, rm, rootElement.getAttribute("label"), order);
        parseRootGeometry(rootElement, root);
        root.computeDistances(); // Associate to each node its distance from the root origin (local only)
        if (order > 1) { // assuming primary roots have order 1
            root.attachParent(parentRoot);
            parentRoot.attachChild(root);
        }
        rootsLabel.add(root.rootID);
        NodeList childRootNodes = rootElement.getElementsByTagName("root");
        rm.rootList.add(root);
        for (int i = 0; i < childRootNodes.getLength(); i++) { // loop over child roots
            Element childRootElement = (Element) childRootNodes.item(i);
            parseRoot(childRootElement, root, rm, order + 1, rootsLabel);
        }
    }

    /**
     * Parses the geometry of the root element and populates the Root object.
     *
     * @param rootElement The root element to parse.
     * @param root        The Root object to populate.
     */
    private static void parseRootGeometry(Element rootElement, Root root) {
        Element geometryElement = (Element) rootElement.getElementsByTagName("geometry").item(0);
        Element polylineElement = (Element) geometryElement.getElementsByTagName("polyline").item(0);
        NodeList pointNodes = polylineElement.getElementsByTagName("point");
        for (int k = 0; k < pointNodes.getLength(); k++) {
            Element pointElement = (Element) pointNodes.item(k);
            root.addNode(
                    Float.parseFloat(pointElement.getAttribute("coord_x")),
                    Float.parseFloat(pointElement.getAttribute("coord_y")),
                    Float.parseFloat(pointElement.getAttribute("coord_t")),
                    Float.parseFloat(pointElement.getAttribute("coord_th")),
                    Float.parseFloat(pointElement.getAttribute("diameter")),
                    Float.parseFloat(pointElement.getAttribute("vx")),
                    Float.parseFloat(pointElement.getAttribute("vy")),
                    k == 0);
        }
    }
}