package fr.cirad.image.rootsystemtracker;
public class Pt2d implements com.goebl.simplify.Point{
	private double x;
	private double y;
	public Pt2d(double x,double y) {
		this.x=x;
		this.y=y;
	}
	public double getX() {
		return this.x;
	}
	public double getY() {
		return this.y;
	}
}
