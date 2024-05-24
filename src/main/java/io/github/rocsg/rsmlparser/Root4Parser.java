package io.github.rocsg.rsmlparser;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Implement Root4Parser class
public class Root4Parser implements IRootParser {
    public static int numFunctions;
    final String id;
    final String poAccession;
    public final LocalDate currentTime;
    final List<Function> functions;
    private final String label;
    private final List<Property> properties;
    private final int order;
    public List<IRootParser> children;
    protected IRootParser parent;
    private Geometry geometry;

    public Root4Parser(String id, String label, String poAccession, Root4Parser parent, int order, LocalDate currentTime) {
        this.id = id;
        this.label = label;
        this.poAccession = poAccession;
        this.properties = new ArrayList<>();
        this.functions = new ArrayList<>();
        this.order = order;
        this.parent = parent;
        this.children = new ArrayList<>();
        if (parent != null) {
            parent.addChild(this, null);
        }
        numFunctions = 2;
        this.currentTime = currentTime;
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
    public String getPoAccession() {
        return poAccession;
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
    public void addChild(IRootParser child, IRootModelParser rootModel) {
        if (child instanceof Root4Parser) children.add(child);
        else {
            System.out.println("Only Root4Parser can be added as a child");
            //System.exit(1);
        }
    }

    @Override
    public IRootParser getParent() {
        return parent;
    }

    @Override
    public String getParentId() {
        return parent == null ? null : parent.getId();
    }

    @Override
    public String getParentLabel() {
        return parent == null ? null : parent.getLabel();
    }

    public void addProperty(Property property) {
        this.properties.add(property);
    }

    public void addFunction(Function function) {
        this.functions.add(function);
    }

    @Override
    public List<Property> getProperties() {
        return properties;
    }

    @Override
    public List<Function> getFunctions() {
        return functions;
    }

    @Override
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
