package fr.cirad.image.TimeLapseRhizo;

import java.awt.Rectangle;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.jgrapht.graph.SimpleWeightedGraph;

import com.goebl.simplify.Simplify;

import fr.cirad.image.common.Bord;
import fr.cirad.image.common.Pix;
import fr.cirad.image.common.TransformUtils;
import fr.cirad.image.common.VitimageUtils;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ImageProcessor;

public class CC implements Serializable{
	public CC lastCCinLat=null;
	public static double ratioFuiteBordSurLongueur=1;
	private static final long serialVersionUID = 1L;
	public boolean finalRS=false;
	public boolean finalRoot=false;
	public boolean isPrimStart=false;
	public boolean isPrimEnd=false;
	public boolean isLatStart=false;
	public boolean isLatEnd=false;
	public boolean isLateral=false;
	public CC associatePrev=null;
	public CC associateSuiv=null;
	public boolean changedRecently=false;
	public int deltaTimeFromStart=0;
	public int deltaTimeBefore=0;
	public double lengthFromStart=0;
	public double lengthBefore=0;
	public int surfaceFromStart=0;
	public boolean nonValidLatStart=false;
	public boolean trunk=false;
	public int goesToTheLeft=0;
	public CC ccPrev=null;
	public CC ccLateralStart=null;
	public ArrayList<CC>pathFromStart=null;
	public int nPixels;
	public  int day;
	public boolean isOut=false;
	public int lateralStamp=-1;
	public ImagePlus thisSeg=null;
	public CC incidentCC=null;
	public int n;
	public int count=0;
	public Roi r;
	double x;
	double y;
	int xB;
	int yB;
	public int stamp=0;
	public int stamp2=0;
	public double stampDist=0;
	public int componentLabel=0;
	public boolean illConnected=false;
	SimpleWeightedGraph<Pix,Bord>pixGraph;
	List<Pix>mainDjikstraPath;
	List<List<Pix>>secondaryDjikstraPath;
	ArrayList<CC>secondaryPathLookup;
	SimpleDirectedWeightedGraph<CC,ConnectionEdge>graph;
	
	public double getConnexionScore(CC cc,double x,double y,double expectedX,double expectedY,boolean debug,double vx,double vy) {
		double[]vectFace=new double[] {vx,vy,0};
		double[]vectDays= new double[] {cc.x()-this.x(),cc.y()-this.y(),0};
		double score0=TransformUtils.scalarProduct(vectFace, vectDays)/(TransformUtils.norm(vectFace)*TransformUtils.norm(vectDays));
		double score1=VitimageUtils.distance(this.x(),this.y(),x,y)-VitimageUtils.distance(cc.x(),cc.y(),x,y);//Foster being nearer to destination
		double cost1=VitimageUtils.distance(expectedX,expectedY,x,y);//Foster being near the expected point
		double score2=1E8;
		for(double dx=-10;dx<=10;dx+=0.5)for(double dy=-10;dy<=10;dy+=0.5){
			if((!cc.r.contains((int)Math.round(x+dx), (int)Math.round(y+dy))) && (!this.r.contains((int)Math.round(x+dx), (int)Math.round(y+dy)) ) && (score2>Math.sqrt(dx*dx+dy*dy))) {
				score2=Math.sqrt(dx*dx+dy*dy);
			}
		}
		if(debug)System.out.println(("X="+x+" Y="+y+" Total="+(2*score0/*+score1-cost1*/+2*score2)+" score0="+score0+"  score1="+score1+" cost1="+cost1+" score2="+score2+" with exp="+expectedX+","+expectedY));
		return (2*score0+/*score1-cost1*/+2*score2);
	}
	
	
	public void lightOffLateralRoot() {
		if(!isLatStart)return;
		CC ccOld=this;
		CC ccTmp=this;
		ConnectionEdge edge=null;
		while(ccTmp.bestOutgoingActivatedCC()!=null) {
			ccOld=ccTmp;
			ccTmp=ccOld.bestOutgoingActivatedCC();
			edge=ccTmp.bestIncomingActivatedEdge();
			graph.removeVertex(ccOld);
			graph.removeEdge(edge);
		}
		graph.removeVertex(ccTmp);
	}
	
	public void setOut() {
		this.isOut=true;
		for(ConnectionEdge edge : graph.outgoingEdgesOf(this))edge.isOut=true;
		for(ConnectionEdge edge : graph.incomingEdgesOf(this))edge.isOut=true;
	}
	
	
	public static CC fuseListOfCCIntoSingleCC(List<CC>list) {
		int nCC=list.size();
		CC ccFuse=new CC();
		ccFuse.secondaryDjikstraPath=new ArrayList<List<Pix>>();
		ccFuse.secondaryPathLookup=new ArrayList<CC>();
		ccFuse.day=-1;
		ccFuse.n=-1;
		ccFuse.graph=list.get(0).graph;

		//Build ROI
		int x=1000000,X=0,y=100000,Y=0;
		for(CC cc:list) {
			if(cc.r.getBoundingRect().x<x)x=cc.r.getBoundingRect().x;
			if(cc.r.getBoundingRect().y<y)y=cc.r.getBoundingRect().y;
			if((cc.r.getBoundingRect().x+cc.r.getBoundingRect().width)>X)X=cc.r.getBoundingRect().x+cc.r.getBoundingRect().width;
			if((cc.r.getBoundingRect().y+cc.r.getBoundingRect().height)>Y)Y=cc.r.getBoundingRect().y+cc.r.getBoundingRect().height;
		}
		ccFuse.r=new Roi(new Rectangle(x,y,X-x,Y-y));
		ccFuse.x=ccFuse.r.getContourCentroid()[0];
		ccFuse.y=ccFuse.r.getContourCentroid()[1];
		ccFuse.xB=ccFuse.r.getBounds().x;
		ccFuse.yB=ccFuse.r.getBounds().y;
		
		Roi[]rTab=new Roi[nCC];
		for(int i=0;i<nCC;i++)rTab[i]=list.get(i).r;
		ImagePlus imgSeg=VitimageUtils.projectRoiTabOnSubImage(rTab);
		ImagePlus dist=MorphoUtils.computeGeodesicInsideComponent(imgSeg,0.1);
		double val=VitimageUtils.maxOfImage(dist);
		dist=VitimageUtils.makeOperationOnOneImage(dist, 2, ratioFuiteBordSurLongueur/val, true);
		dist=VitimageUtils.makeOperationOnOneImage(dist, 1, 1, true);
		ccFuse.buildConnectionGraphOfComponent(imgSeg,dist,8);
		ccFuse.thisSeg=imgSeg;
		return ccFuse;
	}
	
	public CC() {		
	}
	
	
	public CC(int day,int n,Roi r,SimpleDirectedWeightedGraph<CC,ConnectionEdge>graph) {
		this.secondaryDjikstraPath=new ArrayList<List<Pix>>();
		this.secondaryPathLookup=new ArrayList<CC>();
		this.day=day;
		this.n=n;
		this.setRoi(r);
		this.x=r.getContourCentroid()[0];
		this.y=r.getContourCentroid()[1];
		this.xB=this.r.getBounds().x;
		this.yB=this.r.getBounds().y;
		this.graph=graph;
		ImagePlus imgSeg=VitimageUtils.projectRoiOnSubImage(this.r);
		ImagePlus dist=MorphoUtils.computeGeodesicInsideComponent(imgSeg,0.1);
		double val=VitimageUtils.maxOfImage(dist);
		dist=VitimageUtils.makeOperationOnOneImage(dist, 2, ratioFuiteBordSurLongueur/val, true);
		dist=VitimageUtils.makeOperationOnOneImage(dist, 1, 1, true);
		buildConnectionGraphOfComponent(imgSeg,dist,8);
	}
	
	public double x() {
		return this.x;
	}
	
	public double y() {
		return this.y;
	}
	
	public double xB() {
		return xB;
	}
	
	public double yB() {
		return yB;
	}
	
	public static ImagePlus setLowValueTo(ImagePlus img,double minVal,double maxVal,double replacement) {
		ImagePlus ret=img.duplicate();
		float[]tab=(float[])ret.getStack().getProcessor(1).getPixels();
		int xM=img.getWidth();
		int yM=img.getHeight();
		for(int x=0;x<xM;x++)for(int y=0;y<yM;y++) {
			if(tab[y*xM+x]>=minVal && tab[y*xM+x]<maxVal)tab[y*xM+x]=(float) replacement;
		}
		return ret;
	}
	
	public CC(CC source) {
		this.nPixels=source.nPixels;
		this.day=source.day;
		this.n=source.n;
		this.stamp=source.stamp;
		this.stamp2=source.stamp2;
		this.componentLabel=source.componentLabel;
		this.r=(Roi) source.r.clone();
	}


	
	
	public double euclidianDistanceToCC(CC cc2) {
		return VitimageUtils.distance(this.x(),this.y(),cc2.x(),cc2.y());
	}
	
	public String toString() {
		return "CC "+day+"-"+n+" : "+VitimageUtils.dou(r.getContourCentroid()[0])+","+VitimageUtils.dou(r.getContourCentroid()[1])+" ("+(int)(RegionAdjacencyGraphUtils.SIZE_FACTOR*r.getContourCentroid()[0])+" - "+(int)(RegionAdjacencyGraphUtils.SIZE_FACTOR*r.getContourCentroid()[1])+") "+(this.trunk ? " is trunk" : " ")+" stamp="+stamp;
	}
	
	public void setRoi(Roi r) {
		this.r=r;
		this.countPixels();
	}
	
	public void countPixels() {
		Rectangle R=this.r.getBounds();
		int xx=R.x;
		int XX=xx+R.width;
		int yy=R.y;
		int YY=yy+R.height;
		int count=0;
		for(int x=xx;x<=XX;x++) {
			for(int y=yy;y<=YY;y++) {
				if(r.contains(x, y))count++;
			}
		}
		this.nPixels=count;		
	}
	
	public double[] nFacets4connexe_V1(CC cc2) {
		if(!isPossibleNeighbour(cc2,false))return new double[] {0,0,0};
		Rectangle R1=this.r.getBounds();
		Rectangle R2=cc2.r.getBounds();
		Roi r1=this.r;
		Roi r2=cc2.r;
		int x1=R1.x;
		int x2=R2.x;
		int X1=x1+R1.width;
		int X2=x2+R2.width;
		int y1=R1.y;
		int y2=R2.y;
		int Y1=y1+R1.height;
		int Y2=y2+R2.height;
		int nF=0;
		double xSum=0;
		double ySum=0;
		if(R1.width*R1.height <R2.width*R2.height) {
			int xx=x1;int XX=X1; int yy=y1; int YY=Y1;
			for(int x=xx;x<=XX;x++) {
				for(int y=yy;y<=YY;y++) {
					if(!r1.contains(x, y))continue;
					if(r2.contains(x+1, y)){xSum+=(x+0.5);ySum+=y;nF++;}
					if(r2.contains(x-1, y)){xSum+=(x-0.5);ySum+=y;nF++;}
					if(r2.contains(x, y+1)){xSum+=x;ySum+=(y+0.5);nF++;}
					if(r2.contains(x, y-1)){xSum+=x;ySum+=(y-0.5);nF++;}
					if(r2.contains(x+1, y+1)){xSum+=(x+0.5);ySum+=(y+0.5);nF++;}
					if(r2.contains(x-1, y+1)){xSum+=(x-0.5);ySum+=(y+0.5);nF++;}
					if(r2.contains(x+1, y-1)){xSum+=(x+0.5);ySum+=(y-0.5);nF++;}
					if(r2.contains(x-1, y-1)){xSum+=(x-0.5);ySum+=(y-0.5);nF++;}
				}				
			}
			return new double[] {nF,xSum/nF+0.5,ySum/nF+0.5};
		}
		else {
			int xx=x2;int XX=X2; int yy=y2; int YY=Y2;
			for(int x=xx;x<=XX;x++) {
				for(int y=yy;y<=YY;y++) {
					
					if(!r2.contains(x, y))continue;
					if(r1.contains(x+1, y)){xSum+=(x+0.5);ySum+=y; nF++;}
					if(r1.contains(x-1, y)){xSum+=(x-0.5);ySum+=y;nF++;}
					if(r1.contains(x, y+1)){xSum+=x;ySum+=(y+0.5);nF++;}
					if(r1.contains(x, y-1)){xSum+=x;ySum+=(y-0.5);nF++;}
					if(r1.contains(x+1, y+1)){xSum+=(x+0.5);ySum+=(y+0.5);nF++;}
					if(r1.contains(x-1, y+1)){xSum+=(x-0.5);ySum+=(y+0.5);nF++;}
					if(r1.contains(x+1, y-1)){xSum+=(x+0.5);ySum+=(y-0.5);nF++;}
					if(r1.contains(x-1, y-1)){xSum+=(x-0.5);ySum+=(y-0.5);nF++;}
				}			
			}
			return new double[] {nF,xSum/nF+0.5,ySum/nF+0.5};
		}			
	}

	public double[] nFacets4connexe_V22(CC cc2) {
		if(!isPossibleNeighbour(cc2,false))return new double[] {0,0,0};
		double[]firstCalcul=nFacets4connexe_V1(cc2);		
		Rectangle R1=this.r.getBounds();
		Rectangle R2=cc2.r.getBounds();
		Roi r1=this.r;
		Roi r2=cc2.r;
		int x1=R1.x;
		int x2=R2.x;
		int X1=x1+R1.width;
		int X2=x2+R2.width;
		int y1=R1.y;
		int y2=R2.y;
		int Y1=y1+R1.height;
		int Y2=y2+R2.height;
		double nF=firstCalcul[0];
		double xSum=0;
		double ySum=0;
		double distMin=1E8;
		double dist=0;
		double xMin=0;
		double yMin=0;
		if(R1.width*R1.height <R2.width*R2.height) {
			int xx=x1;int XX=X1; int yy=y1; int YY=Y1;
			for(int x=xx;x<=XX;x++) {
				for(int y=yy;y<=YY;y++) {
					if(!r1.contains(x, y))continue;
					if(r2.contains(x+1, y)){
						dist=VitimageUtils.distance(firstCalcul[1], firstCalcul[2],x+0.5,y);
						if(dist<distMin) {distMin=dist;xMin=x+0.5;yMin=y;}
					}
					if(r2.contains(x-1, y)){
						dist=VitimageUtils.distance(firstCalcul[1], firstCalcul[2],x-0.5,y);
						if(dist<distMin) {distMin=dist;xMin=x-0.5;yMin=y;}
					}
					if(r2.contains(x, y+1)){
						dist=VitimageUtils.distance(firstCalcul[1], firstCalcul[2],x,y+0.5);
						if(dist<distMin) {distMin=dist;xMin=x;yMin=y+0.5;}
					}
					if(r2.contains(x, y-1)){
						dist=VitimageUtils.distance(firstCalcul[1], firstCalcul[2],x,y-0.5);
						if(dist<distMin) {distMin=dist;xMin=x;yMin=y-0.5;}
					}
					if(r2.contains(x+1, y+1)){
						dist=VitimageUtils.distance(firstCalcul[1], firstCalcul[2],x+0.5,y+0.5);
						if(dist<distMin) {distMin=dist;xMin=x+0.5;yMin=y+0.5;}
					}
					if(r2.contains(x-1, y+1)){
						dist=VitimageUtils.distance(firstCalcul[1], firstCalcul[2],x-0.5,y+0.5);
						if(dist<distMin) {distMin=dist;xMin=x-0.5;yMin=y+0.5;}
					}
					if(r2.contains(x+1, y-1)){
						dist=VitimageUtils.distance(firstCalcul[1], firstCalcul[2],x+0.5,y-0.5);
						if(dist<distMin) {distMin=dist;xMin=x+0.5;yMin=y-0.5;}
					}
					if(r2.contains(x-1, y-1)){
						dist=VitimageUtils.distance(firstCalcul[1], firstCalcul[2],x-0.5,y-0.5);
						if(dist<distMin) {distMin=dist;xMin=x-0.5;yMin=y-0.5;}
					}
				}				
			}
			return new double[] {nF,xMin,yMin};
		}
		else {
			int xx=x2;int XX=X2; int yy=y2; int YY=Y2;
			for(int x=xx;x<=XX;x++) {
				for(int y=yy;y<=YY;y++) {
					
					if(!r2.contains(x, y))continue;
					if(r1.contains(x+1, y)){
						dist=VitimageUtils.distance(firstCalcul[1], firstCalcul[2],x+0.5,y);
						if(dist<distMin) {distMin=dist;xMin=x+0.5;yMin=y;}
					}
					if(r1.contains(x-1, y)){
						dist=VitimageUtils.distance(firstCalcul[1], firstCalcul[2],x-0.5,y);
						if(dist<distMin) {distMin=dist;xMin=x-0.5;yMin=y;}
					}
					if(r1.contains(x, y+1)){
						dist=VitimageUtils.distance(firstCalcul[1], firstCalcul[2],x,y+0.5);
						if(dist<distMin) {distMin=dist;xMin=x;yMin=y+0.5;}
					}
					if(r1.contains(x, y-1)){
						dist=VitimageUtils.distance(firstCalcul[1], firstCalcul[2],x,y-0.5);
						if(dist<distMin) {distMin=dist;xMin=x;yMin=y-0.5;}
					}
					if(r1.contains(x+1, y+1)){
						dist=VitimageUtils.distance(firstCalcul[1], firstCalcul[2],x+0.5,y+0.5);
						if(dist<distMin) {distMin=dist;xMin=x+0.5;yMin=y+0.5;}
					}
					if(r1.contains(x-1, y+1)){
						dist=VitimageUtils.distance(firstCalcul[1], firstCalcul[2],x-0.5,y+0.5);
						if(dist<distMin) {distMin=dist;xMin=x-0.5;yMin=y+0.5;}
					}
					if(r1.contains(x+1, y-1)){
						dist=VitimageUtils.distance(firstCalcul[1], firstCalcul[2],x+0.5,y-0.5);
						if(dist<distMin) {distMin=dist;xMin=x+0.5;yMin=y-0.5;}
					}
					if(r1.contains(x-1, y-1)){
						dist=VitimageUtils.distance(firstCalcul[1], firstCalcul[2],x-0.5,y-0.5);
						if(dist<distMin) {distMin=dist;xMin=x-0.5;yMin=y-0.5;}
					}
				}			
			}
			return new double[] {nF,xMin,yMin};
		}			
	}

	public double[] nFacets4connexe_V3(CC cc2) {
		if(!isPossibleNeighbour(cc2,false))return new double[] {0,0,0};
		double[]firstCalcul=nFacets4connexe_V1(cc2);		
		Rectangle R1=this.r.getBounds();
		Rectangle R2=cc2.r.getBounds();
		Roi r1=this.r;
		Roi r2=cc2.r;
		int x1=R1.x;
		int x2=R2.x;
		int X1=x1+R1.width;
		int X2=x2+R2.width;
		int y1=R1.y;
		int y2=R2.y;
		int Y1=y1+R1.height;
		int Y2=y2+R2.height;
		double axisX=0;
		double axisY=0;
		double nF=firstCalcul[0];
		double xSum=0;
		double ySum=0;
		double distMin=-1E8;
		double dist=0;
		double xMin=0;
		double yMin=0;
		double xExp=firstCalcul[1];
		double yExp=firstCalcul[2];
		boolean debug=false;
		int SI=RegionAdjacencyGraphUtils.SIZE_FACTOR;
		 if((this==RegionAdjacencyGraphUtils.getCC(graph,  21,5084,2032)) && (cc2==RegionAdjacencyGraphUtils.getCC(graph,22,5130,2080 )))debug=true;
		 if(debug) {
			 System.out.println(r1.contains(636, 256));
			 System.out.println(r2.contains(636, 257));
		 }
		if(R1.width*R1.height <R2.width*R2.height) {
			int xx=x1-1;int XX=X1+1; int yy=y1-1; int YY=Y1+1;
			for(int x=xx;x<=XX;x++) {
				for(int y=yy;y<=YY;y++) {
					if(!r1.contains(x, y))continue;
					if(r2.contains(x+1, y)){
						dist=getConnexionScore(cc2,x+0.5,y,xExp,yExp,debug,1,0); 
						if(dist>distMin) {distMin=dist;xMin=x+0.5;yMin=y;axisX=1;axisY=0;}
					}
					if(r2.contains(x-1, y)){
						dist=getConnexionScore(cc2,x-0.5,y,xExp,yExp,debug,-1,0);
						if(dist>distMin) {distMin=dist;xMin=x-0.5;yMin=y;axisX=-1;axisY=0;}
					}
					if(r2.contains(x, y+1)){
						dist=getConnexionScore(cc2,x,y+0.5,xExp,yExp,debug,0,1);
						if(dist>distMin) {distMin=dist;xMin=x;yMin=y+0.5;axisX=0;axisY=1;}
					}
					if(r2.contains(x, y-1)){
						dist=getConnexionScore(cc2,x,y-0.5,xExp,yExp,debug,0,-1);
						if(dist>distMin) {distMin=dist;xMin=x;yMin=y-0.5;axisX=0;axisY=-1;}
					}
					if(r2.contains(x+1, y+1)){
						dist=getConnexionScore(cc2,x+0.5,y+0.5,xExp,yExp,debug,1,1);
						if(dist>distMin) {distMin=dist;xMin=x+0.5;yMin=y+0.5;axisX=1;axisY=1;}
					}
					if(r2.contains(x-1, y+1)){
						dist=getConnexionScore(cc2,x-0.5,y+0.5,xExp,yExp,debug,-1,1);
						if(dist>distMin) {distMin=dist;xMin=x-0.5;yMin=y+0.5;axisX=-1;axisY=1;}
					}
					if(r2.contains(x+1, y-1)){
						dist=getConnexionScore(cc2,x+0.5,y-0.5,xExp,yExp,debug,1,-1);
						if(dist>distMin) {distMin=dist;xMin=x+0.5;yMin=y-0.5;axisX=1;axisY=-1;}
					}
					if(r2.contains(x-1, y-1)){
						dist=getConnexionScore(cc2,x-0.5,y-0.5,xExp,yExp,debug,-1,-1);
						if(dist>distMin) {distMin=dist;xMin=x-0.5;yMin=y-0.5;axisX=-1;axisY=-1;}
					}
				}				
			}
			if(debug)System.out.println("Got distMin="+distMin+" and stuff="+xMin+","+yMin);
			//if(debug)VitimageUtils.waitFor(500000);
			return new double[] {nF,xMin,yMin,axisX,axisY};
		}
		else {
			int xx=x2-1;int XX=X2+1; int yy=y2-1; int YY=Y2+1;
			for(int x=xx;x<=XX;x++) {
				for(int y=yy;y<=YY;y++) {
					
					if(!r2.contains(x, y))continue;
					if(r1.contains(x+1, y)){
						dist=getConnexionScore(cc2,x+0.5,y,xExp,yExp,debug,-1,0);
						if(dist>distMin) {distMin=dist;xMin=x+0.5;yMin=y;axisX=-1;axisY=0;}
					}
					if(r1.contains(x-1, y)){
						dist=getConnexionScore(cc2,x-0.5,y,xExp,yExp,debug,1,0);
						if(dist>distMin) {distMin=dist;xMin=x-0.5;yMin=y;axisX=1;axisY=0;}
					}
					if(r1.contains(x, y+1)){
						dist=getConnexionScore(cc2,x,y+0.5,xExp,yExp,debug,0,-1);
						if(dist>distMin) {distMin=dist;xMin=x;yMin=y+0.5;axisX=0;axisY=-1;}
					}
					if(r1.contains(x, y-1)){
						dist=getConnexionScore(cc2,x,y-0.5,xExp,yExp,debug,0,1);
						if(dist>distMin) {distMin=dist;xMin=x;yMin=y-0.5;axisX=0;axisY=1;}
					}
					if(r1.contains(x+1, y+1)){
						dist=getConnexionScore(cc2,x+0.5,y+0.5,xExp,yExp,debug,-1,-1);
						if(dist>distMin) {distMin=dist;xMin=x+0.5;yMin=y+0.5;axisX=-1;axisY=-1;}
					}
					if(r1.contains(x-1, y+1)){
						dist=getConnexionScore(cc2,x-0.5,y+0.5,xExp,yExp,debug,1,-1);
						if(dist>distMin) {distMin=dist;xMin=x-0.5;yMin=y+0.5;axisX=1;axisY=-1;}
					}
					if(r1.contains(x+1, y-1)){
						dist=getConnexionScore(cc2,x+0.5,y-0.5,xExp,yExp,debug,-1,1);
						if(dist>distMin) {distMin=dist;xMin=x+0.5;yMin=y-0.5;axisX=-1;axisY=1;}
					}
					if(r1.contains(x-1, y-1)){
						dist=getConnexionScore(cc2,x-0.5,y-0.5,xExp,yExp,debug,1,1);
						if(dist>distMin) {distMin=dist;xMin=x-0.5;yMin=y-0.5;axisX=1;axisY=1;}
					}
				}			
			}
			if(debug)System.out.println("Got distMin="+distMin);
			//if(debug)VitimageUtils.waitFor(500000);
			return new double[] {nF,xMin,yMin,axisX,axisY};
		}			
	}

	
	
	
	
	
	
	public SimpleWeightedGraph<Pix,Bord>buildConnectionGraphOfComponent(ImagePlus imgSeg,ImagePlus distToExt,int connexity){
		this.pixGraph=new SimpleWeightedGraph<>(Bord.class);
		Pix[][]tabPix=new Pix[imgSeg.getWidth()][imgSeg.getHeight()];
		int xM=imgSeg.getWidth();
		int yM=imgSeg.getHeight();
		float[]tabData=(float[])imgSeg.getStack().getPixels(1);
		float[]tabDist=(float[])distToExt.getStack().getPixels(1);
		for(int x=0;x<xM;x++)for(int y=0;y<yM;y++) if(tabData[y*xM+x]>0) {
			tabPix[x][y]=new Pix(x,y,tabDist[y*xM+x]);
			pixGraph.addVertex(tabPix[x][y]);
		}
		for(int x=0;x<xM;x++)for(int y=0;y<yM;y++) {
			if(tabPix[x][y]==null)continue;
			if( (x<(xM-1)) && (y<(yM-1)) && (connexity==8) ) if(tabPix[x+1][y+1]!=null)this.pixGraph.addEdge(tabPix[x][y], tabPix[x+1][y+1],new Bord(tabPix[x][y], tabPix[x+1][y+1]));
			if( (x<(xM-1)) && (y>0) && (connexity==8) ) if(tabPix[x+1][y-1]!=null)this.pixGraph.addEdge(tabPix[x][y], tabPix[x+1][y-1],new Bord(tabPix[x][y], tabPix[x+1][y-1]));
			if( (x<(xM-1)) ) if(tabPix[x+1][y]!=null)this.pixGraph.addEdge(tabPix[x][y], tabPix[x+1][y],new Bord(tabPix[x][y], tabPix[x+1][y]));
			if( (y<(yM-1)) ) if(tabPix[x][y+1]!=null)this.pixGraph.addEdge(tabPix[x][y], tabPix[x][y+1],new Bord(tabPix[x][y], tabPix[x][y+1]));
		}			
		return this.pixGraph;
	}

	public void determineVoxelShortestPathTrunkRoot(){
		int[]coordsT=new int[] {10000000,-1};
		int[]coordsS=new int[] {1000000,1000000};
		if(!(getPrimChild()==null)) {
			CC nextPrim = getPrimChild();
			ConnectionEdge edge=graph.getEdge(this, nextPrim);
			coordsT=getPrevTargetFromFacetConnexion(edge);			
		}
		else {
			for(Pix p:this.pixGraph.vertexSet()) {
				if(p.y>coordsT[1]) {coordsT[0]=p.x;coordsT[1]=p.y;};
			}
		}
		if(this.day>1) {
			CC prevPrim =bestIncomingActivatedCC();
			ConnectionEdge edge=graph.getEdge(prevPrim,this);
			coordsS=getNextSourceFromFacetConnexion(edge);
		}
		else {
			for(Pix p:this.pixGraph.vertexSet())if(p.y<coordsS[1]) {coordsS[0]=p.x;coordsS[1]=p.y;};
		}

		determineVoxelShortestPath (coordsS,coordsT,8,null);
		if(!RegionAdjacencyGraphUtils.isExtremity(this, graph)) {
			for(ConnectionEdge edges : graph.outgoingEdgesOf(this)) {
				if(edges.target.trunk)continue;
				if(!edges.activated)continue;
				coordsT=getPrevTargetFromFacetConnexion(edges);
				determineVoxelShortestPath (coordsS,coordsT,8,edges.target);
			}
		}
	}
	
	
	public List<Pix>determineVoxelShortestPath (int[]coordStart,int[]coordStop,int connexity,CC setHereNextCCIfItIsLatDeterminationForTrunk) {
		Pix pixStart=this.getPix(coordStart[0],coordStart[1]);
		Pix pixStop=this.getPix(coordStop[0],coordStop[1]);
		setWeightsToDistExt();
		DijkstraShortestPath<Pix, Bord>djik=new DijkstraShortestPath<Pix, Bord>(this.pixGraph);
		GraphPath<Pix, Bord> path = djik.getPath(pixStart, pixStop);
		if(setHereNextCCIfItIsLatDeterminationForTrunk==null) {
			this.mainDjikstraPath=path.getVertexList();
			for(Pix p:this.mainDjikstraPath)p.isSkeleton=true;
		}
		else {
			List<Pix>temp=path.getVertexList();
			List<Pix>definitive=path.getVertexList();
			definitive.clear();
			for(Pix p:temp) {if(!this.mainDjikstraPath.contains(p))definitive.add(p);}
			this.secondaryDjikstraPath.add(definitive);
			for(Pix p:definitive)p.isSkeleton=true;
			this.secondaryPathLookup.add(setHereNextCCIfItIsLatDeterminationForTrunk);
		}
		return path.getVertexList();			
	}

	public double setDistancesToShortestPathTrunk() {
		double curDist=0;
		if(this.day>1 && bestIncomingActivatedEdge()!=null) {
			Pix p=this.mainDjikstraPath.get(0);
			double xEd=bestIncomingActivatedEdge().connectionX-this.xB();
			double yEd=bestIncomingActivatedEdge().connectionY-this.yB();
			double delta=VitimageUtils.distance(xEd, yEd, p.x,p.y);
			curDist=bestIncomingActivatedEdge().distanceConnectionTrunk+delta;
		}
		this.mainDjikstraPath.get(0).wayFromPrim=curDist;
		for(int i=1;i<this.mainDjikstraPath.size();i++) {
			Pix p=this.mainDjikstraPath.get(i);
			Pix pBef=this.mainDjikstraPath.get(i-1);
			curDist+=this.pixGraph.getEdge(p, pBef).len;			
			p.wayFromPrim=curDist;
		}
		CC nextPrim = getPrimChild();
		if(nextPrim!=null) {
			Pix p=this.mainDjikstraPath.get(this.mainDjikstraPath.size()-1);
			ConnectionEdge edge=graph.getEdge(this, nextPrim);
			double delta=VitimageUtils.distance(edge.connectionX-this.xB(), edge.connectionY-this.yB(), p.x,p.y);
			edge.distanceConnectionTrunk=curDist+delta;
		}
		
		if(this.secondaryDjikstraPath==null)return curDist;
		double curDist2=0;
		for(int j=0;j<this.secondaryDjikstraPath.size();j++) {
			curDist2=0;
			List<Pix>sec=secondaryDjikstraPath.get(j);
			for(int i=1;i<sec.size();i++) {
				Pix p=sec.get(i);
				Pix pBef=sec.get(i-1);
				curDist2+=this.pixGraph.getEdge(p, pBef).len;			
				p.wayFromPrim=curDist2;
			}
			CC nextLat = this.secondaryPathLookup.get(j);
			ConnectionEdge edge2=graph.getEdge(this, nextLat);
			double xEd=edge2.connectionX-this.xB();
			double yEd=edge2.connectionY-this.yB();
			double delta=(sec.size()<1 ? 0 : VitimageUtils.distance(xEd, yEd, sec.get(sec.size()-1).x,sec.get(sec.size()-1).y));
			edge2.distanceConnectionTrunk=curDist2;
			
		}
		return curDist;
	}
	
	public double setDistancesToMainDijkstraPath(double d0) {
		double tot=d0;
		this.mainDjikstraPath.get(0).wayFromPrim=tot;
		for(int i=1;i<this.mainDjikstraPath.size();i++) {
			Pix p=this.mainDjikstraPath.get(i);
			Pix pBef=this.mainDjikstraPath.get(i-1);
			tot+=this.pixGraph.getEdge(p, pBef).len;			
			p.wayFromPrim=tot;
		}
		return tot;
	}
	
	
	public int[]getExpectedSource(){
		if(this.day==1 && this.trunk) {
			Pix pp=null;
			int yMin=100000;
			for(Pix p:pixGraph.vertexSet()) {
				if(p.y<yMin) {
					pp=p;
					yMin=p.y;
				}
			}
			return new int[] {pp.x,pp.y};
		}
		ConnectionEdge edge=bestIncomingActivatedEdge();
		return getNextSourceFromFacetConnexion(edge);
	}
	
	public int[]getExpectedTarget(){
		ConnectionEdge edge=bestOutgoingActivatedEdge();
		return getPrevTargetFromFacetConnexion(edge);
	}
	
	public ConnectionEdge bestCountOutgoingEdge() {
		double maxPix=-1;
		double minCost=1E18;
		ConnectionEdge bestEdge=null;
		if(graph.outgoingEdgesOf(this).size()>0) {
			for(ConnectionEdge edge : graph.outgoingEdgesOf(this)) {
				if(  (graph.getEdgeTarget(edge).count==maxPix && graph.getEdgeWeight(edge)<minCost)  ||
					 (graph.getEdgeTarget(edge).count>maxPix) ) {
					minCost=graph.getEdgeWeight(edge);
					maxPix=graph.getEdgeTarget(edge).count;
					bestEdge=edge;
				}
			}
		}
		return bestEdge;
	}
	public ConnectionEdge bestCountOutgoingEdge_v2() {
		double maxVal=-100000000;
		ConnectionEdge bestEdge=null;
		if(graph.outgoingEdgesOf(this).size()>0) {
			for(ConnectionEdge edge : graph.outgoingEdgesOf(this)) {
				double val=graph.getEdgeTarget(edge).count*1.0/(1+VitimageUtils.EPSILON+graph.getEdgeWeight(edge));
				if(  val>maxVal) {
					maxVal=val;
					bestEdge=edge;
				}
			}
		}
		return bestEdge;
	}
	
	public ConnectionEdge bestOutgoingEdge() {
		double minCost=1E18;
		ConnectionEdge bestEdge=null;
		if(graph.outgoingEdgesOf(this).size()>0) {
			for(ConnectionEdge edge : graph.outgoingEdgesOf(this)) {
				if(graph.getEdgeWeight(edge)<minCost) {
					minCost=graph.getEdgeWeight(edge);
					bestEdge=edge;
				}
			}
		}
		return bestEdge;
	}
	
	public CC bestOutgoingActivatedCC() {
		ConnectionEdge edge=bestOutgoingActivatedEdge();
		if(edge !=null)return edge.target;
		return null;
	}

	public CC bestIncomingActivatedCC() {
		ConnectionEdge edge=bestIncomingActivatedEdge();
		if(edge !=null)return edge.source;
		return null;
	}


	
	public CC getActivatedLeafOfCC(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,CC cc) {
		CC ccNext=cc;
		while(ccNext.bestOutgoingActivatedEdge()!=null)ccNext=bestOutgoingActivatedEdge().target;
		return ccNext;
	}
	
	public CC getActivatedRoot() {
		CC ccPrev=this;
		while( (ccPrev.bestIncomingActivatedEdge()!=null) && ((!ccPrev.bestIncomingActivatedEdge().source.trunk)) && (!(ccPrev.bestIncomingActivatedEdge().source.day<2))) {
			ccPrev=ccPrev.bestIncomingActivatedEdge().source;
			if(ccPrev==null)return null;
			if(ccPrev==this)return null;
		}
		return ccPrev;
	}


	
	public CC getLatChild() {
		if(this.bestOutgoingActivatedCC()==null)return null;
		return this.bestOutgoingActivatedCC();
	}
	
	public boolean isHiddenLatChild() {
		if(this.bestOutgoingActivatedCC()==null)return false;
		return this.bestOutgoingActivatedEdge().hidden;
	}


	
	public CC getPrimChild() {
		if(!trunk)return null;
		if(this.bestOutgoingActivatedCC()==null)return null;
		CC ccsel=null;
		for(ConnectionEdge edge :graph.outgoingEdgesOf(this)) {
			if(edge.target.trunk)return edge.target;
		}
		return null;
	}
	
	public boolean isHiddenPrimChild() {
		if(!trunk)return false;
		if(this.bestOutgoingActivatedCC()==null)return false;
		CC ccsel=null;
		for(ConnectionEdge edge :graph.outgoingEdgesOf(this)) {
			if(edge.target.trunk)return edge.hidden;
		}
		return false;
	}

	
	public ImagePlus drawDist() {
		ImagePlus seg= VitimageUtils.convertToFloat(VitimageUtils.projectRoiOnSubImage(r));		
		ImageProcessor ip=seg.getStack().getProcessor(1);
		for (Pix p:pixGraph.vertexSet()) ip.setf(p.x, p.y, (float) p.dist);
		seg.setProcessor(ip);
		seg.resetDisplayRange();
		seg.setTitle("Dist");
		IJ.run(seg,"Fire","");
		seg.setDisplayRange(0, 100);
		return seg;
	}
	
	public ImagePlus drawDistToSkeleton() {
		ImagePlus seg= VitimageUtils.convertToFloat(VitimageUtils.projectRoiOnSubImage(r));		
		ImageProcessor ip=seg.getStack().getProcessor(1);
		for (Pix p:pixGraph.vertexSet()) ip.setf(p.x, p.y, (float) p.distanceToSkeleton);
		seg.setProcessor(ip);
		seg.setTitle("DistToSkeleton");
		IJ.run(seg,"Fire","");
		seg.setDisplayRange(0, 30);
		return seg;
	}
	
	public ImagePlus drawWayFromPrim() {
		ImagePlus seg= VitimageUtils.convertToFloat(VitimageUtils.projectRoiOnSubImage(r));		
		ImageProcessor ip=seg.getStack().getProcessor(1);
		for (Pix p:pixGraph.vertexSet()) ip.setf(p.x, p.y, (float) p.wayFromPrim);
		seg.setProcessor(ip);
		seg.setTitle("WayFromPrim");
		IJ.run(seg,"Fire","");
		seg.setDisplayRange(0, 500);
		return seg;
	}

	
	public void updateAllDistancesToTrunk() {
		int N=pixGraph.vertexSet().size();
		ArrayList<Pix>tabVisited=new ArrayList<Pix>();
		ArrayList<Pix>tabToVisit=new ArrayList<Pix>();
		int countSkel=0;
		int countNonSkel=0;
		for(Pix p:pixGraph.vertexSet()) {
			if(mainDjikstraPath.contains(p)) {
				countSkel++;
				tabVisited.add(p);p.distanceToSkeleton=0;p.previous=p;
			}
			else {
				countNonSkel++;
				p.distanceToSkeleton=10000000;
			}
		}
		while(tabVisited.size()>0) {
			N-=tabVisited.size();
			for(Pix p:tabVisited) {
				for(Bord bord:pixGraph.edgesOf(p)) {
					Pix p1=pixGraph.getEdgeSource(bord);
					if(p1!=p)if(p1.distanceToSkeleton>(p.distanceToSkeleton+pixGraph.getEdge(p1, p).len)) {
						p1.previous=p.previous;
						p1.distanceToSkeleton=p.distanceToSkeleton+pixGraph.getEdge(p1, p).len;
						tabToVisit.add(p1);
					}
					Pix p2=pixGraph.getEdgeTarget(bord);
					if(p2!=p)if(p2.distanceToSkeleton>p.distanceToSkeleton+pixGraph.getEdge(p2, p).len) {
						p2.previous=p.previous;
						p2.distanceToSkeleton=p.distanceToSkeleton+pixGraph.getEdge(p2, p).len;
						tabToVisit.add(p2);
					}
				}
			}
			tabVisited=tabToVisit;
			tabToVisit=new ArrayList<Pix>();
		}
		for(Pix p:pixGraph.vertexSet())if(!mainDjikstraPath.contains(p))p.wayFromPrim=p.previous.wayFromPrim;
		//TODO : eventually a little blur there, because virage makes silly things
	}
	
	
	public void interpolateTimeFromReferencePointsUsingSplineInterpolator(PolynomialSplineFunction psf,double[]espaceSource) {
		for(Pix p:pixGraph.vertexSet()) {
			if(p.wayFromPrim>espaceSource[espaceSource.length-1])p.time=psf.value(espaceSource[espaceSource.length-1]);
			else p.time=psf.value(p.wayFromPrim);
			if((p.wayFromPrim+p.distOut)>espaceSource[espaceSource.length-1])p.time=psf.value(espaceSource[espaceSource.length-1]);
			else p.timeOut=psf.value(p.wayFromPrim+p.distOut);
			if(p.time<=0)p.time=0.00001;
			if(p.timeOut<=0)p.timeOut=0.00001;
		}
	}
	

	public void interpolateTimeFromReferencePointsUsingLinearInterpolator(double[]espaceSource,double []timeSource) {
		for(Pix p:pixGraph.vertexSet()) {
			p.time=SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim, espaceSource, timeSource);//  SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim,espaceSource,timeSource);
			p.timeOut=SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim+p.distOut,espaceSource,timeSource);
		}
	}
	

	
	public ConnectionEdge bestIncomingEdge() {
		double minCost=1E18;
		ConnectionEdge bestEdge=null;
		if(graph.incomingEdgesOf(this).size()>0) {
			for(ConnectionEdge edge : graph.incomingEdgesOf(this)) {
				if(graph.getEdgeWeight(edge)<minCost) {
					minCost=graph.getEdgeWeight(edge);
					bestEdge=edge;
				}
			}
		}
		return bestEdge;
	}

	public ConnectionEdge bestOutgoingActivatedEdge() {
		double minCost=1E18;
		ConnectionEdge bestEdge=null;
		if(graph.outgoingEdgesOf(this).size()>0) {
			for(ConnectionEdge edge : graph.outgoingEdgesOf(this)) {
				if(edge.activated && graph.getEdgeWeight(edge)<minCost) {
					minCost=graph.getEdgeWeight(edge);
					bestEdge=edge;
				}
			}
		}
		return bestEdge;
	}
	
	public ConnectionEdge bestIncomingActivatedEdge() {
		double minCost=1E18;
		ConnectionEdge bestEdge=null;
		if(graph.incomingEdgesOf(this).size()>0) {
			for(ConnectionEdge edge : graph.incomingEdgesOf(this)) {
				if(edge.activated && graph.getEdgeWeight(edge)<minCost) {
					minCost=graph.getEdgeWeight(edge);
					bestEdge=edge;
				}
			}
		}
		return bestEdge;
	}

	

	
	
	public double setDistanceToShortestPath(double distanceAtStart) {
		double curDist=distanceAtStart;
		this.mainDjikstraPath.get(0).wayFromPrim=curDist;
		for(int i=1;i<this.mainDjikstraPath.size();i++) {
			Pix p=this.mainDjikstraPath.get(i);
			Pix pBef=this.mainDjikstraPath.get(i-1);
			curDist+=this.pixGraph.getEdge(p, pBef).len;			
			p.wayFromPrim=curDist;
		}
		return curDist;
	}
	
	
	public Pix getPix(int x, int y) {
		for(Pix p:pixGraph.vertexSet()) {
			if(p.x==x && p.y==y)return p;
		}
		return null;
	}
	

	public void setWeightsToDistExt() {
		for(Bord bord:pixGraph.edgeSet()) pixGraph.setEdgeWeight(bord, bord.getWeightDistExt());
	}

	public void setWeightsToEuclidian() {
		for(Bord bord:pixGraph.edgeSet()) pixGraph.setEdgeWeight(bord, bord.getWeightEuclidian());
	}

	public int[]getSeedFromFacetConnexionOLD(double[]coords,boolean justDebug){
		double x0=coords[0]+this.xB();
		double y0=coords[1]+this.yB();
		int[]coInt=new int[] {(int)Math.round(x0),(int)Math.round(y0)};
		double min=10;int xMin=coInt[0];int yMin=coInt[1];
		for(int dx=-3;dx<=3;dx++)for(int dy=-3;dy<=3;dy++) {
			int xf=coInt[0]+dx;
			int yf=coInt[1]+dy;
			if(this.r.contains(xf,yf) && VitimageUtils.distance(x0, y0, xf, yf)<min) {
				min= VitimageUtils.distance(x0, y0, xf, yf);
				xMin=xf;
				yMin=yf;
			}
		}
		return new int[] {(int) (xMin-this.xB()),(int) (yMin-this.yB())};
	}

	public int[]getSeedFromFacetConnexion(double[]coords,double []vectPrev,boolean justDebug){
		double x0=coords[0];
		double y0=coords[1];
		System.out.println("In GetSeed [],bool Recherche de "+x0+","+y0);
		if(x0==(int)Math.round(x0)) {
			System.out.println("Cas 1-X");
			if(y0==(int)Math.round(y0)) {			System.out.println("Cas 1-1");System.out.println("Pb 1 dans CC");}
			else {//Facette suivant y
				if(this.r.contains((int)x0, (int)Math.round(y0-0.5)))y0-=0.5;
				else if(this.r.contains((int)x0, (int)Math.round(y0+0.5)))y0+=0.5;
				else System.out.println("Pb 2 dans CC");
			}
		}
		else {//
			System.out.println("Cas 2-X");
			if(y0==(int)Math.round(y0)) {
				if(this.r.contains((int)Math.round(x0-0.5), (int)y0))x0-=0.5;
				else if(this.r.contains((int)Math.round(x0+0.5), (int)y0))x0+=0.5;
				else System.out.println("Pb 3 dans CC");
			}
			else {
				System.out.println("Cas 2-2");
				System.out.println("Running x0y0="+x0+","+y0);
				if(this.r.contains((int)Math.round(x0-0.5), (int)Math.round(y0-0.5))) {System.out.println("v1");x0-=0.5;y0-=0.5;}
				else if(this.r.contains((int)Math.round(x0-0.5), (int)Math.round(y0+0.5))) {System.out.println("v2");x0-=0.5;y0+=0.5;}
				else if(this.r.contains((int)Math.round(x0+0.5), (int)Math.round(y0-0.5))) {System.out.println("v3");x0+=0.5;y0-=0.5;}
				else if(this.r.contains((int)Math.round(x0+0.5), (int)Math.round(y0+0.5))) {System.out.println("v4"); x0+=0.5;y0+=0.5;}
				else System.out.println("Pb 4 dans CC");
			}
		}
		System.out.println("Got x0y0="+x0+","+y0);
		System.out.println("Give="+(int)Math.round(x0-this.xB())+","+(int)Math.round(y0-this.yB()));
		return new int[] {(int)Math.round(x0-this.xB()),(int)Math.round(y0-this.yB())};
	}

	

	
	public static double[]determineFacet(int[]sNext,int[]tCur,CC ccNext,CC ccCur){
		double xTar=sNext[0]+ccNext.xB();
		double yTar=sNext[1]+ccNext.yB();
		double xSou=tCur[0]+ccCur.xB();
		double ySou=tCur[1]+ccCur.yB();
		double []vectNorm=TransformUtils.normalize( new double[] {xTar-xSou,yTar-ySou,0} );
		if(vectNorm[0]>0.707)return new double[] {sNext[0]-0.5,sNext[1]};
		if(vectNorm[0]<(-0.707))return new double[] {sNext[0]+0.5,sNext[1]};
		if(vectNorm[1]>0.707)return new double[] {sNext[0],sNext[1]-0.5};
		if(vectNorm[1]<(-0.707))return new double[] {sNext[0],sNext[1]+0.5};
		if(vectNorm[0]>0 && vectNorm[1]>0)return new double[] {sNext[0]-0.5,sNext[1]-0.5};
		if(vectNorm[0]>0 && vectNorm[1]<0)return new double[] {sNext[0]-0.5,sNext[1]+0.5};
		if(vectNorm[0]<0 && vectNorm[1]>0)return new double[] {sNext[0]+0.5,sNext[1]-0.5};
		if(vectNorm[0]<0 && vectNorm[1]<0)return new double[] {sNext[0]+0.5,sNext[1]+0.5};
		return null;
	}
	

/*	
	public int[]getSeedFromFacetConnexion20(ConnectionEdge e){
		double x0=e.connectionX;
		double y0=e.connectionY;
		System.out.println("Econ="+e.connectionX+","+e.connectionY);
		return getSeedFromFacetConnexion(new double[] {x0,y0},new double[],true);
	}

	public int[]getSeedFromFacetConnexion3(ConnectionEdge e){
		double x0=e.connectionX;
		double y0=e.connectionY;
		System.out.println("Econ="+e.connectionX+","+e.connectionY);
		return getSeedFromFacetConnexion(new double[] {x0,y0},new double[],true);
	}
*/
	public int[]getPrevTargetFromFacetConnexion(ConnectionEdge e){
		double x0=e.connectionX-e.source.xB();
		double y0=e.connectionY-e.source.yB();
		double dx=e.axisX/2.0;
		double dy=e.axisY/2.0;
		return new int[] { (int)Math.round(x0-dx) ,  (int)Math.round(y0-dy) };
	}

	public int[]getNextSourceFromFacetConnexion(ConnectionEdge e){
		double x0=e.connectionX-e.target.xB();
		double y0=e.connectionY-e.target.yB();
		double dx=e.axisX/2.0;
		double dy=e.axisY/2.0;
		if(getPix((int)Math.round(x0+dx), (int)Math.round(y0+dy))==null) {
			int targX=(int)Math.round(x0+dx);
			int targY=(int)Math.round(y0+dy);
			double minDist=1000000;
			Pix pp=null;
			for(Pix p: pixGraph.vertexSet()) {
				double dist=VitimageUtils.distance(targX, targY, p.x, p.y);
				if(dist<minDist) {
					minDist=dist;
					pp=p;
				}
			}
			return new int[] {pp.x,pp.y};
		}
		else return new int[] { (int)Math.round(x0+dx) ,  (int)Math.round(y0+dy) };
	}

	
	
	public int[]getSeedFromFacetConnexionOLD(ConnectionEdge e,double []vectPrev){
		double x0=e.connectionX-this.r.getBounds().x;
		double y0=e.connectionY-this.r.getBounds().y;
		System.out.println("Econ="+e.connectionX+","+e.connectionY+" equivalent "+x0+","+y0);
		return getSeedFromFacetConnexion(new double[] {x0,y0},vectPrev,true);
	}

	
	public int[]determineTargetGeodesicallyFarestFromTheSource(int[]start){
		int x0=start[0];
		int y0=start[1];
		ImagePlus imgSeg=null;
		if(thisSeg==null)imgSeg=VitimageUtils.projectRoiOnSubImage(this.r);
		else imgSeg=thisSeg;
		ImagePlus seedImage=VitimageUtils.convertToFloat(VitimageUtils.nullImage(imgSeg));
		((float[])(seedImage.getStack().getPixels(1)))[imgSeg.getWidth()*y0+x0]=255;
		ImagePlus distance=MorphoUtils.computeGeodesic(seedImage, imgSeg,false);
		float[]tabData=(float[])distance.getStack().getPixels(1);
		int xMax=0;int yMax=0;double distMax=-1000;double eucDistMax=-1000;
		for(int x=0;x<distance.getWidth();x++)for(int y=0;y<distance.getHeight();y++) {
			if(tabData[distance.getWidth()*y+x]==distMax) {
				if(VitimageUtils.distance(x, y, x0, y0)>eucDistMax) {
					eucDistMax=VitimageUtils.distance(x, y, x0,y0);
					xMax=x;yMax=y;
				}
			}
			else if(tabData[distance.getWidth()*y+x]>distMax) {
				distMax=tabData[distance.getWidth()*y+x];
				eucDistMax=VitimageUtils.distance(x, y, x0,y0);
				xMax=x;yMax=y;
			}
		}
		return new int[] {xMax,yMax};
	}

	public int[][]findHiddenStartStopToInOtherCC(CC cc2,int[]start){
		int[][]res=new int[4][2];
		int x0=start[0]+this.xB-cc2.xB;
		int y0=start[1]+this.yB-cc2.yB;
		double distMin=1E8;int xMin=0;int yMin=0;
		for(Pix p:cc2.pixGraph.vertexSet()) {
			if(VitimageUtils.distance(x0,y0,p.x,p.y)<distMin) {
				distMin=VitimageUtils.distance(x0,y0,p.x,p.y);
				xMin=p.x;yMin=p.y;
			}
		}
		res[1]=new int[] {xMin,yMin};
		res[3]=new int[] {xMin+cc2.xB,yMin+cc2.yB};
		distMin=1E8;xMin=0;yMin=0;
		x0=xMin+cc2.xB-this.xB;
		y0=yMin+cc2.yB-this.yB;
		for(Pix p:this.pixGraph.vertexSet()) {
			if(VitimageUtils.distance(x0,y0,p.x,p.y)<distMin) {
				distMin=VitimageUtils.distance(x0,y0,p.x,p.y);
				xMin=p.x;yMin=p.y;
			}
		}
		res[0]=new int[] {xMin,yMin};
		res[2]=new int[] {xMin+this.xB,yMin+this.yB};
		return res;
	}
	
	public String getStringDijkstraMainPath() {
		String ret="";
		for(Pix p: mainDjikstraPath) {
			ret+=(p+"\n");
		}
		return ret;
	}
	
	public boolean isPossibleNeighbour(CC cc2,boolean debug) {
		Rectangle R1=this.r.getBounds();
		Rectangle R2=cc2.r.getBounds();
		if(debug) {
			System.out.println(R1);
			System.out.println(R2);
		}
		int x1=R1.x;
		int x2=R2.x;
		int X1=x1+R1.width;
		int X2=x2+R2.width;
		int y1=R1.y;
		int y2=R2.y;
		int Y1=y1+R1.height;
		int Y2=y2+R2.height;
		if(x1>X2+1)return false;
		if(x2>X1+1)return false;
		if(y1>Y2+1)return false;
		if(y2>Y1+1)return false;
		return true;
	}

}
