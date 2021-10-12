package fr.cirad.image.TimeLapseRhizo;

import java.io.Serializable;

public class Pix implements Serializable{
	public boolean isSkeleton=false;
	public double distOut=0;
	public Pix previous=null;
	public double distanceToSkeleton=0;
	public double time;
	public double timeOut;
	public int x;
	public int y;
	public int stamp=0;
	public double wayFromPrim;
//	public double wayFromSkel;
	public double dist;
	public Pix source;
	public Pix(int x, int y,double dist) {
		this.x=x;
		this.y=y;	
		this.dist=dist;
	}
	public String toString(){
		return"Pix="+(x+","+y+") , way="+wayFromPrim+" , dist="+dist+" time="+time);
	}
}

