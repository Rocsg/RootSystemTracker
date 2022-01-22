package fr.cirad.image.TimeLapseRhizo;

import java.io.Serializable;

import org.jgrapht.graph.DefaultWeightedEdge;

public class ConnectionEdge extends DefaultWeightedEdge implements Serializable {
	private static final long serialVersionUID = 1L;
	boolean hidden=false;
	double connectionX;
	double connectionY;	
	double distanceConnectionTrunk=0;
	boolean activated=true;
	public CC source;
	public boolean isOut=false;
	public CC target;
	public int axisX;
	public int axisY;
	int nFacets;
	public boolean trunk=false;
	
	
	
	
	public ConnectionEdge(double connectionX, double connectionY,int nFacets,CC source,CC target,double axisX,double axisY) {
		this.hidden=false;
		this.connectionX=connectionX;
		this.connectionY=connectionY;
		this.nFacets=nFacets;
		this.source=source;
		this.target=target;
		this.axisX=(int)Math.round(axisX);
		this.axisY=(int)Math.round(axisY);
	}
	public String toString() {
		return "Connection edge "+(this.hidden ? " hidden" : " non-hidden")+(this.trunk ? " trunk" : " non-trunk")+(this.activated ? " activated" : " non-activated")+" weight="+this.getWeight()+" conX="+connectionX+" conY="+connectionY+" distConTrunk="+distanceConnectionTrunk+"\n    source : "+this.source+"\n   target : "+this.target;
	}
	
	
}
