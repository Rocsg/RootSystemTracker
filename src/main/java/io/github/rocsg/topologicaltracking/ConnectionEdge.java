package io.github.rocsg.topologicaltracking;

import org.jgrapht.graph.DefaultWeightedEdge;

import java.io.Serializable;
import java.util.ArrayList;

public class ConnectionEdge extends DefaultWeightedEdge implements Serializable {
    private static final long serialVersionUID = 1L;
    public boolean hidden = false; // if the connection is hidden (by another root or something else)
    public double connectionX;
    public double connectionY;
    public double distanceConnectionTrunk = 0;
    public boolean activated = false;
    public CC source;
    public boolean isOut = false;
    public CC target;
    public int axisX;
    public int axisY;
    public int nFacets;
    public boolean trunk = false;
    public double[]hintVector;
    public double hintDistance;
    double cost=0;
    ArrayList<CC> pathOfCC;
    public int stepOfActivation;
    public int stunningLevel = 0; // if the connection is stunning (suspicious)
    
    // Détails des problèmes détectés (pour debugging et résolution)
    public ArrayList<String> stunningReasons = new ArrayList<>(); // Types de problèmes détectés
    public ArrayList<Double> stunningMADValues = new ArrayList<>(); // Valeurs de MAD associées (si applicable)
    public ArrayList<double[]> hiddenConnectingFacets= new ArrayList<>(); // Facets cachées traversant les CC

    public ConnectionEdge(double connectionX, double connectionY, int nFacets, CC source, CC target, double axisX,
                          double axisY) {
        this.hidden = false;
        this.connectionX = connectionX;
        this.connectionY = connectionY;
        this.nFacets = nFacets;
        this.source = source;
        this.target = target;
        this.axisX = (int) Math.round(axisX);
        this.axisY = (int) Math.round(axisY);
    }

    public String toString() {
        return "Connection edge " + (this.hidden ? " hidden" : " non-hidden") +
                (this.trunk ? " trunk" : " non-trunk") + (this.activated ? " activated" : " non-activated") +
                " weight=" + this.getWeight() + " conX=" + connectionX + " conY=" + connectionY + " distConTrunk="
                + distanceConnectionTrunk + "\n    source : " + this.source + "\n   target : " + this.target;
    }


}
