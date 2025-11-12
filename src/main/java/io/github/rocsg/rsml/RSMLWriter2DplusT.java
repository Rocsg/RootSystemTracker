package io.github.rocsg.rsml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.time.LocalDateTime;

/**
 * The RSMLWriter2DT class provides methods to write RSML files from RootModel objects.
 */
public class RSMLWriter2DplusT {

    /**
     * Writes the given RootModel to an RSML file at the specified file path.
     *
     * @param rootModel The RootModel object to write.
     * @param filePath  The path to the output RSML file.
     */
    public static void writeRSML(RootModel rootModel, String filePath) {
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element
            Document doc = docBuilder.newDocument();
            Element rsmlElement = doc.createElement("rsml");
            doc.appendChild(rsmlElement);

            // Metadata element
            Element metadataElement = doc.createElement("metadata");
            rsmlElement.appendChild(metadataElement);
            writeMetadata(rootModel, metadataElement, doc);

            // Scene element
            Element sceneElement = doc.createElement("scene");
            rsmlElement.appendChild(sceneElement);

            // the number of plants corresponds to the number of primaries
            int count = 1;
            for (Root r : rootModel.rootList) {
                if (r.order != 1) continue;
                Plant plant = new Plant();
                plant.addRoot(r);
                plant.id = String.valueOf(count);
                plant.label = "Plant " + count;
                writePlant(plant, sceneElement, doc);
                count++;
            }

            // Write the content into an XML file
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty("indent", "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.setOutputProperty("encoding", "UTF-8");
            //transformer.setOutputProperty("doctype-system", "http://www.plant-image-analysis.org/rsml/rsml-1.0.dtd");
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(filePath));

            transformer.transform(source, result);

            System.out.println("File saved to " + filePath);

        } catch (ParserConfigurationException | TransformerException e) {
            e.printStackTrace();
        }
    }

    /**
     * Writes the metadata of the given RootModel to the specified metadata element.
     *
     * @param rootModel       The RootModel object containing the metadata.
     * @param metadataElement The metadata element to populate.
     * @param doc             The XML document being constructed.
     */
    private static void writeMetadata(RootModel rootModel, Element metadataElement, Document doc) {
        Metadata metadata = rootModel.metadata;

        createElementAndAppend("version", String.valueOf(metadata.getVersion()), metadataElement, doc);
        createElementAndAppend("unit", metadata.getUnit(), metadataElement, doc);
        createElementAndAppend("size", String.valueOf(metadata.getSize()), metadataElement, doc);
        createElementAndAppend("last-modified", LocalDateTime.now().toString(), metadataElement, doc);
        createElementAndAppend("software", metadata.getSoftware(), metadataElement, doc);
        createElementAndAppend("user", metadata.getUser(), metadataElement, doc);
        createElementAndAppend("file-key", metadata.getFileKey(), metadataElement, doc);

        // Observation hours
        StringBuilder observationHoursStr = new StringBuilder();
        double[] observationHours = metadata.getObservationHours();
        for (int i = 1; i < observationHours.length; i++) {
            observationHoursStr.append(observationHours[i]);
            if (i < observationHours.length - 1) {
                observationHoursStr.append(",");
            }
        }
        createElementAndAppend("observation-hours", observationHoursStr.toString(), metadataElement, doc);

        // Image element
        Element imageElement = doc.createElement("image");
        metadataElement.appendChild(imageElement);
        createElementAndAppend("label", rootModel.imgName, imageElement, doc);
        createElementAndAppend("sha256", "Nothing there", imageElement, doc);
    }

    /**
     * Writes the given Plant object to the specified scene element.
     *
     * @param plant        The Plant object to write.
     * @param sceneElement The scene element to populate.
     * @param doc          The XML document being constructed.
     */
    private static void writePlant(Plant plant, Element sceneElement, Document doc) {
        Element plantElement = doc.createElement("plant");
        plantElement.setAttribute("ID", plant.id);
        plantElement.setAttribute("label", plant.label);
        sceneElement.appendChild(plantElement);

        // Iterate over all roots in the plant
        int count = 1;
        for (Root root : plant.getRoots()) {
            root.rootID = String.valueOf(count); // mmm
            writeRoot(root, plantElement, doc);
            count++;
        }
    }

    /**
     * Writes the given Root object to the specified parent element.
     *
     * @param root          The Root object to write.
     * @param parentElement The parent element to populate.
     * @param doc           The XML document being constructed.
     */
    private static void writeRoot(Root root, Element parentElement, Document doc) {
        Element rootElement = doc.createElement("root");
        rootElement.setAttribute("ID", parentElement.getAttribute("ID") + "." + root.getId());
        rootElement.setAttribute("label", root.getLabel());
        parentElement.appendChild(rootElement);

        // Geometry and Polyline
        Element geometryElement = doc.createElement("geometry");
        rootElement.appendChild(geometryElement);

        Element polylineElement = doc.createElement("polyline"); // TODO : generalize
        geometryElement.appendChild(polylineElement);

        // Iterate over all points in the root
        Node firstnode = root.firstNode;
        while (firstnode != null) {
            Element pointElement = doc.createElement("point");
            pointElement.setAttribute("coord_x", String.valueOf(firstnode.x));
            pointElement.setAttribute("coord_y", String.valueOf(firstnode.y));
            pointElement.setAttribute("coord_t", String.valueOf(firstnode.birthTime));
            pointElement.setAttribute("coord_th", String.valueOf(firstnode.birthTimeHours));
            pointElement.setAttribute("diameter", String.valueOf(firstnode.diameter));
            pointElement.setAttribute("vx", String.valueOf(firstnode.vx));
            pointElement.setAttribute("vy", String.valueOf(firstnode.vy));
            polylineElement.appendChild(pointElement);
            firstnode = firstnode.child;
        }

        // Recursively write child roots
        int count = 1;
        for (Root childRoot : root.getChildren()) {
            childRoot.rootID = String.valueOf(count); // mmm
            writeRoot(childRoot, rootElement, doc);
            count++;
        }
    }

    /**
     * Creates an element with the specified tag name and text content, and appends it to the parent element.
     *
     * @param tagName       The tag name of the element to create.
     * @param textContent   The text content of the element.
     * @param parentElement The parent element to append the new element to.
     * @param doc           The XML document being constructed.
     */
    private static void createElementAndAppend(String tagName, String textContent, Element parentElement, Document doc) {
        Element element = doc.createElement(tagName);
        element.appendChild(doc.createTextNode(textContent));
        parentElement.appendChild(element);
    }
}