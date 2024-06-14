package io.github.rocsg.rsmlparser;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class Geometry {
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

    public void addPoint(Point2D point) {
        Polyline polyline = new Polyline();
        polyline.addPoint(new Point4Parser(point.getX(), point.getY()));
        this.polylines.add(polyline);
    }

    @Override
    public String toString() {
        return "Geometry{" +
                "polylines=" + polylines +
                '}';
    }
}
