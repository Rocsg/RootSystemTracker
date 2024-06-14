package io.github.rocsg.rsmlparser;

import java.util.ArrayList;
import java.util.List;

// Define the interface
public interface IRootParser {
    String getId();

    String getLabel();

    String getPoAccession();

    int getOrder();

    List<Property> getProperties();

    List<Function> getFunctions();

    Geometry getGeometry();

    List<IRootParser> getChildren();

    void addChild(IRootParser child, IRootModelParser rootModel);

    IRootParser getParent();

    String getParentId();

    String getParentLabel();
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

    public Point4Parser(double x, double y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public String toString() {
        return "Point{" +
                "x=" + x +
                ", y=" + y +
                '}';
    }
}

