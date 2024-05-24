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

    @Override
    public String toString() {
        return "Geometry{" +
                "polylines=" + polylines +
                '}';
    }
}
