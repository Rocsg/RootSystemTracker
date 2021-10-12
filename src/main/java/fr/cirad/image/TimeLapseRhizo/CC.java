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

import fr.cirad.image.common.VitimageUtils;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ImageProcessor;

public class CC implements Serializable{
	public double ratioFuiteBordSurLongueur=1;
	private static final long serialVersionUID = 1L;
	public boolean finalRS=false;
	public boolean finalRoot=false;
	public boolean isPrimStart=false;
	public boolean isPrimEnd=false;
	public boolean isLatStart=false;
	public boolean isLatEnd=false;
	public boolean trunk=false;
	public CC ccPrev=null;
	public int nPixels;
	public  int day;
	public int n;
	public Roi r;
	public int stamp=0;
	public int stamp2=0;
	public int componentLabel=0;
	public boolean illConnected=false;
	SimpleWeightedGraph<Pix,Bord>pixGraph;
	List<Pix>mainDjikstraPath;
	List<List<Pix>>secondaryDjikstraPath;
	ArrayList<CC>secondaryPathLookup;
	SimpleDirectedWeightedGraph<CC,ConnectionEdge>graph;
	
	public CC(int day,int n,Roi r,SimpleDirectedWeightedGraph<CC,ConnectionEdge>graph) {
		this.secondaryDjikstraPath=new ArrayList<List<Pix>>();
		this.secondaryPathLookup=new ArrayList<CC>();
		this.day=day;
		this.n=n;
		this.setRoi(r);
		this.graph=graph;
		ImagePlus imgSeg=VitimageUtils.projectRoiOnSubImage(this.r);
		ImagePlus dist=VitimageUtils.computeGeodesicInsideComponent(imgSeg,0.1);
		double val=VitimageUtils.maxOfImage(dist);
		dist=VitimageUtils.makeOperationOnOneImage(dist, 2, ratioFuiteBordSurLongueur/val, true);
		dist=VitimageUtils.makeOperationOnOneImage(dist, 1, 1, true);
		buildConnectionGraphOfComponent(imgSeg,dist,8);
	}
	
	public double x() {
		return this.r.getContourCentroid()[0];
	}
	
	public double y() {
		return this.r.getContourCentroid()[1];
	}
	
	public double xB() {
		return this.r.getBounds().x;
	}
	
	public double yB() {
		return this.r.getBounds().y;
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
		return "CC "+day+"-"+n+" : "+VitimageUtils.dou(r.getContourCentroid()[0])+","+VitimageUtils.dou(r.getContourCentroid()[1])+" ("+(int)(RegionAdjacencyGraphUtils.SIZE_FACTOR*r.getContourCentroid()[0])+" - "+(int)(RegionAdjacencyGraphUtils.SIZE_FACTOR*r.getContourCentroid()[1])+") "+(trunk ? " is trunk" : " ")+" stamp="+stamp;
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
	
	public double[] nFacets4connexe(CC cc2) {
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
		if(!(RegionAdjacencyGraphUtils.getPrimChild(this, graph)==null)) {
			CC nextPrim = RegionAdjacencyGraphUtils.getPrimChild(this, graph);
			ConnectionEdge edge=graph.getEdge(this, nextPrim);
			coordsT=getSeedFromFacetConnexion(edge);			
		}
		else {
			for(Pix p:this.pixGraph.vertexSet()) {
				if(p.y>coordsT[1]) {coordsT[0]=p.x;coordsT[1]=p.y;};
			}
		}
		if(this.day>1) {
			CC prevPrim =RegionAdjacencyGraphUtils.bestIncomingActivatedCC(this, graph);
			ConnectionEdge edge=graph.getEdge(prevPrim,this);
			coordsS=getSeedFromFacetConnexion(edge);
		}
		else {
			for(Pix p:this.pixGraph.vertexSet())if(p.y<coordsS[1]) {coordsS[0]=p.x;coordsS[1]=p.y;};
		}
		determineVoxelShortestPath (coordsS,coordsT,8,null);
		if(!RegionAdjacencyGraphUtils.isExtremity(this, graph)) {
			for(ConnectionEdge edges : graph.outgoingEdgesOf(this)) {
				if(edges.target.trunk)continue;
				coordsT=getSeedFromFacetConnexion(edges);
				System.out.println("\n\nTHIS="+this+"\nEDGE="+edges+"\nTARGET="+edges.target);
				System.out.println(coordsS[0]+","+coordsS[1]);
				System.out.println(coordsT[0]+","+coordsT[1]);
				determineVoxelShortestPath (coordsS,coordsT,8,edges.target);
			}
		}
	}
	
	public List<Pix>determineVoxelShortestPath (int[]coordStart,int[]coordStop,int connexity,CC setHereNextCCIfItIsLatDeterminationForTrunk) {
		Pix pixStart=this.getPix(coordStart[0],coordStart[1]);
		Pix pixStop=this.getPix(coordStop[0],coordStop[1]);
		setWeightsToDistExt(this.pixGraph);
		DijkstraShortestPath<Pix, Bord>djik=new DijkstraShortestPath<Pix, Bord>(this.pixGraph);
		if(this.day==1 && this.n==4)this.drawDist();
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
		if(this.day>1 && RegionAdjacencyGraphUtils.bestIncomingActivatedEdge(this, graph)!=null) {
			Pix p=this.mainDjikstraPath.get(0);
			double xEd=RegionAdjacencyGraphUtils.bestIncomingActivatedEdge(this, graph).connectionX-this.xB();
			double yEd=RegionAdjacencyGraphUtils.bestIncomingActivatedEdge(this, graph).connectionY-this.yB();
			double delta=VitimageUtils.distance(xEd, yEd, p.x,p.y);
			curDist=RegionAdjacencyGraphUtils.bestIncomingActivatedEdge(this, graph).distanceConnectionTrunk+delta;
		}
		this.mainDjikstraPath.get(0).wayFromPrim=curDist;
		for(int i=1;i<this.mainDjikstraPath.size();i++) {
			Pix p=this.mainDjikstraPath.get(i);
			Pix pBef=this.mainDjikstraPath.get(i-1);
			curDist+=this.pixGraph.getEdge(p, pBef).len;			
			p.wayFromPrim=curDist;
		}
		CC nextPrim = RegionAdjacencyGraphUtils.getPrimChild(this, graph);
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

	
	public ImagePlus drawDist() {
		ImagePlus seg= VitimageUtils.convertToFloat(VitimageUtils.projectRoiOnSubImage(r));		
		ImageProcessor ip=seg.getStack().getProcessor(1);
		for (Pix p:pixGraph.vertexSet()) ip.setf(p.x, p.y, (float) p.dist);
		seg.setProcessor(ip);
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
	

	public static void setWeightsToDistExt(SimpleWeightedGraph<Pix,Bord>graph) {
		for(Bord bord:graph.edgeSet()) graph.setEdgeWeight(bord, bord.getWeightDistExt());
	}

	public static void setWeightsToEuclidian(SimpleWeightedGraph<Pix,Bord>graph) {
		for(Bord bord:graph.edgeSet()) graph.setEdgeWeight(bord, bord.getWeightEuclidian());
	}

	public int[]getSeedFromFacetConnexion(double[]coords,boolean justDebug){
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
	
	public int[]getSeedFromFacetConnexion(ConnectionEdge e){
		double x0=e.connectionX-this.r.getBounds().x;
		double y0=e.connectionY-this.r.getBounds().y;
		return getSeedFromFacetConnexion(new double[] {x0,y0},true);
	}

	
	public int[]determineTargetGeodesicallyFarestFromTheSource(int[]start){
		int x0=start[0];
		int y0=start[1];
		ImagePlus imgSeg=VitimageUtils.projectRoiOnSubImage(this.r);
		ImagePlus seedImage=VitimageUtils.convertToFloat(VitimageUtils.nullImage(imgSeg));
		((float[])(seedImage.getStack().getPixels(1)))[imgSeg.getWidth()*y0+x0]=255;
		ImagePlus distance=VitimageUtils.computeGeodesic(seedImage, imgSeg,false);
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
		int x0=start[0]+this.r.getBounds().x-cc2.r.getBounds().x;
		int y0=start[1]+this.r.getBounds().y-cc2.r.getBounds().y;
		double distMin=1E8;int xMin=0;int yMin=0;
		for(Pix p:cc2.pixGraph.vertexSet()) {
			if(VitimageUtils.distance(x0,y0,p.x,p.y)<distMin) {
				distMin=VitimageUtils.distance(x0,y0,p.x,p.y);
				xMin=p.x;yMin=p.y;
			}
		}
		res[1]=new int[] {xMin,yMin};
		res[3]=new int[] {xMin+cc2.r.getBounds().x,yMin+cc2.r.getBounds().y};
		distMin=1E8;xMin=0;yMin=0;
		x0=xMin+cc2.r.getBounds().x-this.r.getBounds().x;
		y0=yMin+cc2.r.getBounds().y-this.r.getBounds().y;
		for(Pix p:this.pixGraph.vertexSet()) {
			if(VitimageUtils.distance(x0,y0,p.x,p.y)<distMin) {
				distMin=VitimageUtils.distance(x0,y0,p.x,p.y);
				xMin=p.x;yMin=p.y;
			}
		}
		res[0]=new int[] {xMin,yMin};
		res[2]=new int[] {xMin+this.r.getBounds().x,yMin+this.r.getBounds().y};
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
