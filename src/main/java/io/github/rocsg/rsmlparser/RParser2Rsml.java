package io.github.rocsg.rsmlparser;

import io.github.rocsg.rsml.FSR;
import io.github.rocsg.rsml.Node;
import io.github.rocsg.rsml.Root;
import io.github.rocsg.rsml.RootModel;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static io.github.rocsg.rsmlparser.RsmlParser.getRSMLsinfos;

public interface RParser2Rsml {
    RootModel transform(RootModel4Parser rootModel4Parser);
}

class RMTransformerImpl implements RParser2Rsml {

    private static int time = 0;
    public final int Nobs = Integer.MAX_VALUE;
    public RootModel rootModel;
    public boolean hasHours = false;
    private int numRSML = 1;

    public static void main(String[] args) throws IOException {
        FSR sr = (new FSR());
        sr.initialize();
        Map<Date, List<RootModel4Parser>> result = getRSMLsinfos(Paths.get("D:\\loaiu\\MAM5\\Stage\\data\\UC3\\Rootsystemtracker\\Original_Data\\B73_R04_01\\"));

        // time 0
        RMTransformerImpl rootModelTransformer = new RMTransformerImpl();
        RootModel4Parser rootModel4Parser = result.get(result.keySet().iterator().next()).get(0);
        rootModelTransformer.transform(rootModel4Parser);
        time++;

        // time 1 to N


    }


    @Override
    public RootModel transform(RootModel4Parser rootModel4Parser) {
        RootModel rootModel = new RootModel();

        rootModel.imgName = "Build from Rsmls " + numRSML;
        numRSML++;
        rootModel.pixelSize = (float) (2.54 / rootModel4Parser.getResolution());
        // TODO hashours


        for (Scene scene : rootModel4Parser.getScenes()) {
            for (Plant plant : scene.getPlants()) {
                for (Root4Parser root4Parser : plant.getRoots()) {
                    Root root = convertRoot(root4Parser, null, rootModel);
                    rootModel.rootList.add(root);
                }
            }
        }
        rootModel.standardOrderingOfRoots();
        return rootModel;
    }

    private Root convertRoot(Root4Parser root4Parser, Root parentRoot, RootModel rootModel) {
        Root root = new Root(parentRoot, rootModel, root4Parser.getId(), root4Parser.getOrder());

        root.setLabel(root4Parser.getLabel());

        for (Property property : root4Parser.getProperties()) {
            root.properties.put(property.getName(), property.getValue());
        }

        for (Function function : root4Parser.getFunctions()) {
            root.functions.put(function.getName(), function.getSamples());
        }

        if (root4Parser.getGeometry() != null) {
            root.setGeometry(convertGeometry(root4Parser.getGeometry(), root));
        }

        if (root4Parser.getChildren() != null) {
            for (IRootParser childRoot4Parser : root4Parser.getChildren()) {
                Root childRoot = convertRoot((Root4Parser) childRoot4Parser, null, rootModel);
                childRoot.computeDistances();
                root.attachChild(childRoot);
                childRoot.attachParent(root);
                rootModel.rootList.add(childRoot);
            }
        }
        return root;
    }

    private List<Node> convertGeometry(Geometry geometry, Root root) { // TODO revoir avec addNode {
        List<Node> nodes = new ArrayList<>();
        // get diameter
        List<Double> diameters = root.functions.get("diameter");
        List<Point2D> points = geometry.get2Dpt();
        Node previousNode = null;
        for (Point2D point : points) {
            // x, y, d, parent or child node, after
            // // if after parent = n
            // // else child = n
            Node node = new Node(
                    (float) point.getX(),
                    (float) point.getY(),
                    diameters.get(points.indexOf(point)).floatValue(),
                    previousNode, true);
            nodes.add(node);
            previousNode = node;
        }
        root.firstNode = nodes.get(0);
        root.lastNode = nodes.get(nodes.size() - 1);
        return nodes;
    }

}