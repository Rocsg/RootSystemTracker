package io.github.rocsg.rsmlparser;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

// Define the interface
interface IRootParser {
    String getId();

    String getLabel();

    int getOrder();

    List<IRootParser> getChildren();

    void addChild(IRootParser child);
}

// Implement TemporalRoot4Parser class
class TemporalRoot4Parser implements IRootParser {
    final String id;
    private final String label;
    private final int order;
    public List<Root4Parser> tRoots; // same root at different time
    protected IRootParser parent;
    protected HashMap<Integer, IRootParser> children; // must be TemporalRoot4Parser, the integer is the appearance time

    public TemporalRoot4Parser(List<Root4Parser> tRoots) { // assuming all the roots have the same id and label and are ordered by time
        this.id = tRoots.get(0).getId(); // assuming all the roots have the same id
        this.label = tRoots.get(0).getLabel(); // assuming all the roots have the same label
        this.order = tRoots.get(0).getOrder(); // assuming all the roots have the same order
        this.parent = tRoots.get(0).parent;
        this.tRoots = new ArrayList<>(tRoots);
        tRoots.addAll(tRoots);
        this.children = new HashMap<>(); // TODO
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public List<IRootParser> getChildren() {
        return new ArrayList<>(children.values());
    }

    @Override
    public void addChild(IRootParser child) {
        // TODO
    }

    @Override
    public String toString() {
        return "TemporalRoot4Parser{" +
                "id='" + id + '\'' +
                ", label='" + label + '\'' +
                ", order=" + order +
                ", parent=" + parent +
                ", children=" + children +
                ", troots=" + tRoots +
                '}';
    }
}

// Implement Root4Parser class
class Root4Parser implements IRootParser {
    public static int numFunctions;
    final String id;
    final List<Function> functions;
    private final String label;
    private final List<Property> properties;
    private final int order;
    public List<IRootParser> children;
    protected IRootParser parent;
    private Geometry geometry;

    public Root4Parser(String id, String label, Root4Parser parent, int order) {
        this.id = id;
        this.label = label;
        this.properties = new ArrayList<>();
        this.functions = new ArrayList<>();
        this.order = order;
        this.parent = parent;
        this.children = new ArrayList<>();
        if (parent != null) {
            parent.addChild(this);
        }
        numFunctions = 2;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public List<IRootParser> getChildren() {
        return children;
    }

    @Override
    public void addChild(IRootParser child) {
        if (child instanceof Root4Parser) children.add(child);
        else {
            System.out.println("Only Root4Parser can be added as a child");
            //System.exit(1);
        }
    }

    public void addProperty(Property property) {
        this.properties.add(property);
    }

    public void addFunction(Function function) {
        this.functions.add(function);
    }

    public List<Property> getProperties() {
        return properties;
    }

    public List<Function> getFunctions() {
        return functions;
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    @Override
    public String toString() {
        String indent = "";
        int order = this.order;
        for (indent = ""; order > 0; order--) {
            indent += "\t";
        }
        return "\n" + indent + "Root4Parser{" +
                "\n" + indent + "\tid='" + id + '\'' +
                "\n" + indent + "\tlabel='" + label + '\'' +
                "\n" + indent + "\tproperties=" + properties +
                "\n" + indent + "\tgeometry=" + geometry +
                "\n" + indent + "\tfunctions=" + functions +
                "\n" + indent + "\tparent=" + parent +
                childIDandLbel2String("\n" + indent + "\t\t") +
                "\n" + indent + "\torder=" + order +
                '}';
    }

    private String childIDandLbel2String(String indent) {
        StringBuilder childID = new StringBuilder();
        if (children != null) {
            for (IRootParser child : children) {
                childID.append(indent).append(child.getId()).append(" : ").append(child.getLabel());
            }
        }
        return childID.toString();
    }
}

// Define other classes used in Root4Parser
class Property {
    private final String name;
    private final double value;

    public Property(String name, String value) {
        this.name = name;
        this.value = Double.parseDouble(value);
    }

    public String getName() {
        return name;
    }

    public double getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "Property{" +
                "name='" + name + '\'' +
                ", value=" + value +
                '}';
    }
}

class Geometry {
    private final List<Polyline> polylines;

    public Geometry() {
        this.polylines = new ArrayList<>();
    }

    public void addPolyline(Polyline polyline) {
        this.polylines.add(polyline);
    }

    public List<Point2D> get2Dpt() {
        List<Point2D> nodes = new ArrayList<>();
        for (Polyline polyline : polylines) {
            for (Point4Parser point : polyline.getPoints()) {
                nodes.add(new Point2D.Double(point.x, point.y));
            }
        }
        return nodes;
    }

    @Override
    public String toString() {
        return "Geometry{" +
                "polylines=" + polylines +
                '}';
    }
}

class Polyline {
    private final List<Point4Parser> points;

    public Polyline() {
        this.points = new ArrayList<>();
    }

    public void addPoint(Point4Parser point) {
        this.points.add(point);
    }

    public List<Point4Parser> getPoints() {
        return points;
    }

    @Override
    public String toString() {
        return "Polyline{" +
                "points=" + points +
                '}';
    }
}

class Point4Parser {
    final double x;
    final double y;

    public Point4Parser(String x, String y) {
        this.x = Double.parseDouble(x);
        this.y = Double.parseDouble(y);
    }

    @Override
    public String toString() {
        return "Point{" +
                "x=" + x +
                ", y=" + y +
                '}';
    }
}

class Function {
    private final String name;
    private final List<Double> samples;

    public Function(String name) {
        this.name = name;
        this.samples = new ArrayList<>();
    }

    public void addSample(String sample) {
        this.samples.add(Double.parseDouble(sample));
    }

    public String getName() {
        return name;
    }

    public List<Double> getSamples() {
        return samples;
    }

    @Override
    public String toString() {
        return "Function{" +
                "name='" + name + '\'' +
                ", samples=" + samples +
                '}';
    }
}
