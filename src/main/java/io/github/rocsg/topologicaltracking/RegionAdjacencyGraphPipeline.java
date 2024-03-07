package io.github.rocsg.topologicaltracking;

import java.awt.Rectangle;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.poi.ddf.EscherColorRef.SysIndexProcedure;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.alg.interfaces.SpanningTreeAlgorithm.SpanningTree;
import org.jgrapht.alg.spanning.KruskalMinimumSpanningTree;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import io.github.rocsg.fijiyama.common.DouglasPeuckerSimplify;
import io.github.rocsg.fijiyama.common.MostRepresentedFilter;
import io.github.rocsg.fijiyama.common.Pix;
import io.github.rocsg.fijiyama.common.Timer;
import io.github.rocsg.fijiyama.registration.TransformUtils;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import io.github.rocsg.fijiyama.rsml.FSR;
import io.github.rocsg.fijiyama.rsml.Root;
import io.github.rocsg.fijiyama.rsml.RootModel;
import io.github.rocsg.rootsystemtracker.PipelineParamHandler;
import io.github.rocsg.rstutils.HungarianAlgorithm;
import io.github.rocsg.rstutils.MorphoUtils;
import io.github.rocsg.rstutils.SplineAndPolyLineUtils;
import it.unimi.dsi.fastutil.bytes.ByteSortedSets.SynchronizedSortedSet;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;

public class RegionAdjacencyGraphPipeline {

	
	public static int MIN_SIZE_CC=5;
	static double PENALTY_COST=0.5;
	public static final boolean DO_DISTANCE=true;
	public static final boolean DO_TIME=false;
	public static final int minFinalDepthForAcceptingLatRoot=300;
	

	
	
	
	
	
	///////////////////////////
	//ROUGHLY SAFE ZONE.///////
	///////////////////////////
	//There could be an improvement by drawing the actual time, in hours, but not needed at this point
	/** Rendering helpers -------------------------------------------------------------------------------------------------------------------------------------------*/
	public static ImagePlus drawDistanceOrTime(ImagePlus source,SimpleDirectedWeightedGraph <CC,ConnectionEdge>graph,boolean trueDoDistFalseDoTime,boolean onlyDoSkeleton,int mode_1Total_2OutsideDistOrIntTime_3SourceDist) {
		ImagePlus imgDist=VitimageUtils.convertToFloat(VitimageUtils.nullImage(source));
		imgDist=VitimageUtils.makeOperationOnOneImage(imgDist, 1, -VitimageUtils.EPSILON, true);
		int X=imgDist.getWidth();
		float[]valDist=(float[])imgDist.getStack().getProcessor(1).getPixels();
		for(CC cc : graph.vertexSet()) {
			int x0=cc.r.getBounds().x;
			int y0=cc.r.getBounds().y;
			for(Pix p:cc.pixGraph.vertexSet()) {
				int index=X*(p.y+y0)+(p.x+x0);
				if(onlyDoSkeleton && (!p.isSkeleton))continue;
				if(mode_1Total_2OutsideDistOrIntTime_3SourceDist==1) 	valDist[index]=(trueDoDistFalseDoTime) ? ((float) (p.wayFromPrim+p.distOut)) : (float)p.timeOutHours ;
				else if(mode_1Total_2OutsideDistOrIntTime_3SourceDist==2) 	valDist[index]=(trueDoDistFalseDoTime) ? ((float) (p.distOut)) : (float)(cc.hour) ;
				else                                             	valDist[index]=(trueDoDistFalseDoTime) ? ((float) (p.wayFromPrim)) : (float)(p.timeHours) ;
				if(!trueDoDistFalseDoTime && valDist[index]<0)valDist[index]=0;
			}
		}
		return imgDist;
	}
	
	public static ImagePlus drawDistanceTime(ImagePlus source,SimpleDirectedWeightedGraph <CC,ConnectionEdge>graph,int mode_1Skel_2All_3AllWithTipDistance) {
		ImagePlus imgDist=VitimageUtils.convertToFloat(VitimageUtils.nullImage(source));
		ImagePlus imgTime=imgDist.duplicate();
		ImagePlus imgTimeOut=imgDist.duplicate();
		ImagePlus imgDistOut=imgDist.duplicate();
		ImagePlus imgDistSum=imgDist.duplicate();
		int X=imgDist.getWidth();
		float[]valDist=(float[])imgDist.getStack().getProcessor(1).getPixels();
		float[]valDistOut=(float[])imgDistOut.getStack().getProcessor(1).getPixels();
		float[]valDistSum=(float[])imgDistSum.getStack().getProcessor(1).getPixels();
		float[]valTime=(float[])imgTime.getStack().getProcessor(1).getPixels();
		float[]valTimeOut=(float[])imgTimeOut.getStack().getProcessor(1).getPixels();
		for(CC cc : graph.vertexSet()) {
			int x0=cc.r.getBounds().x;
			int y0=cc.r.getBounds().y;
			for(Pix p:cc.pixGraph.vertexSet()) {
				int index=X*(p.y+y0)+(p.x+x0);
				if(mode_1Skel_2All_3AllWithTipDistance==1 && (!p.isSkeleton))continue;
				if(mode_1Skel_2All_3AllWithTipDistance<3) {
					valDist[index]=(float) p.wayFromPrim;
					valTime[index]=(float) p.time;					
				}
				if(mode_1Skel_2All_3AllWithTipDistance==3) {
					valDist[index]=(float) (p.wayFromPrim+p.distOut);
					valTime[index]=(float) (p.timeOut);
				}
				if(mode_1Skel_2All_3AllWithTipDistance==4 || mode_1Skel_2All_3AllWithTipDistance==0 ) {
					valDist[index]=(float) (p.wayFromPrim);
					valDistOut[index]=(float) (p.distOut);
					valDistSum[index]=(float) (p.wayFromPrim+p.distOut);
					valTime[index]=(float) (p.time);
					valTimeOut[index]=(float) (p.timeOut);
				}
			}
		}
		if(mode_1Skel_2All_3AllWithTipDistance==0)return imgTimeOut;
		else if(mode_1Skel_2All_3AllWithTipDistance<4) return VitimageUtils.slicesToStack(new ImagePlus[]{imgDist,imgTime});
		else return VitimageUtils.slicesToStack(new ImagePlus[]{imgDist,imgDistOut,imgDistSum,imgTime,imgTimeOut});
	}
		
	public static ImagePlus drawGraph(ImagePlus imgDates,SimpleDirectedWeightedGraph <CC,ConnectionEdge>graph,double circleRadius,int lineThickness,int sizeFactor) {
		System.out.println("Drawing graph");

		//Draw the silhouette
		ImagePlus contour=VitimageUtils.nullImage(imgDates);
		ImagePlus in=VitimageUtils.nullImage(imgDates);
		if(sizeFactor>1) {
			ImagePlus bin=VitimageUtils.thresholdImage(imgDates, 0.5, 100000);
			ImagePlus binResize=VitimageUtils.resizeNearest(bin, imgDates.getWidth()*sizeFactor,  imgDates.getHeight()*sizeFactor, 1);
			ImagePlus ero=MorphoUtils.erosionCircle2D(binResize, 1);
			ImagePlus dil=MorphoUtils.dilationCircle2D(binResize, 1);
			contour=VitimageUtils.makeOperationBetweenTwoImages(dil, ero, 4, false);

			ImagePlus nonBinResize=VitimageUtils.resizeNearest(imgDates, imgDates.getWidth()*sizeFactor,  imgDates.getHeight()*sizeFactor, 1);
			ImagePlus ero2=MorphoUtils.erosionCircle2D(nonBinResize, 1);
			//ImagePlus dil2=MorphoUtils.dilationCircle2D(nonBinResize, 1);
			ImagePlus contour2=VitimageUtils.makeOperationBetweenTwoImages(nonBinResize,ero2, 4, false);
			contour2=VitimageUtils.thresholdImage(contour2, 0.5, 1000);
			contour2=VitimageUtils.makeOperationOnOneImage(contour2, 2, 255, true);
			contour=VitimageUtils.binaryOperationBetweenTwoImages(contour, contour2, 1);
			in =VitimageUtils.makeOperationOnOneImage(ero, 3, 255, true);
			contour=VitimageUtils.makeOperationBetweenTwoImages(contour, in, 1, true);
		}
		contour =VitimageUtils.makeOperationOnOneImage(contour, 2, 255, true);
		ImagePlus imgGraph=imgDates.duplicate();
		int N=(int) Math.round( VitimageUtils.maxOfImage(imgDates));
		imgGraph=VitimageUtils.makeOperationOnOneImage(imgGraph, 2, 0, true);
		imgGraph=VitimageUtils.convertFloatToByteWithoutDynamicChanges(imgGraph);
		if(sizeFactor>1) {
			int dimX=imgDates.getWidth();
			int dimY=imgDates.getHeight();
			imgGraph=VitimageUtils.uncropImageByte(imgGraph, 0, 0, 0, dimX*sizeFactor, dimY*sizeFactor, 1);
		}
		
		//Draw the vertices
		double vx=VitimageUtils.getVoxelSizes(imgGraph)[0];
		System.out.print(" 1-Drawing circles for "+graph.vertexSet().size()+" vertices  ");
		int incr=0;int decile=graph.vertexSet().size()/10;
		if(sizeFactor>4)circleRadius*=2;
		for(CC cc:graph.vertexSet()) {
			if(cc.isOut)continue;
			double ccx=(cc.x());
			double ccy=(cc.y());
			double factor=0.3+0.7*Math.log10(cc.nPixels);
			if(((incr++)%decile)==0) {
				System.out.print(incr+" ");
			}
			boolean extremity =isExtremity(cc,graph);
			if(extremity) {
				if(cc.nPixels>=2)VitimageUtils.drawCircleIntoImage(imgGraph, vx*(factor*circleRadius+3+(cc.trunk ? 2 : 0)), (int)Math.round(ccx*sizeFactor), (int)Math.round(ccy*sizeFactor), 0,12);
			}
			else {
				VitimageUtils.drawCircleIntoImage(imgGraph, vx*(factor*circleRadius+2+(cc.trunk ? 2 : 0)), (int)Math.round(ccx*sizeFactor), (int)Math.round((ccy)*sizeFactor), 0,255);
				VitimageUtils.drawCircleIntoImage(imgGraph, vx*(factor*circleRadius+1+(cc.trunk ? 1 : 0)), (int)Math.round(ccx*sizeFactor), (int)Math.round((ccy)*sizeFactor), 0,0);
			}
			if((int)Math.round((ccx)*sizeFactor)>0)VitimageUtils.drawCircleIntoImage(imgGraph, vx*(factor*circleRadius), (int)Math.round((ccx)*sizeFactor), (int)Math.round((ccy)*sizeFactor), 0,cc.day);
		}
		System.out.println();
		
		//Draw the edges
		System.out.print("   2-Drawing "+graph.edgeSet().size()+" edges  ");
		incr=0;decile=graph.edgeSet().size()/10;
		int incrAct=0;
		int incrNonAct=0;
		for(ConnectionEdge edge:graph.edgeSet()) {
			if(edge.isOut)continue;
			if(!edge.activated) incrNonAct++;
			if(!edge.activated) continue;
			incrAct++;
			if(((incr++)%decile)==0)System.out.print(incr+" ");
			CC cc1=graph.getEdgeSource(edge);
			CC cc2=graph.getEdgeTarget(edge);
			double xCon=edge.connectionX+0.5;
			double yCon=edge.connectionY+0.5;
			double cc1x=(cc1.x());
			double cc2x=(cc2.x());
			double cc1y=(cc1.y());
			double cc2y=(cc2.y());
			
			//if(cc1.day==0 || cc2.day==0)continue; 
			int val=(int)(22-3*(graph.getEdgeWeight(edge)+1));
			if(val<4)val=4;
			if(val>255)val=255;
			if(cc1.day>0) VitimageUtils.drawSegmentInto2DByteImage(imgGraph,lineThickness+(edge.trunk ? 8 : 0),val,(cc1x)*sizeFactor,(cc1y)*sizeFactor,xCon*sizeFactor,yCon*sizeFactor,edge.hidden); 
			if(cc1.day>0)VitimageUtils.drawSegmentInto2DByteImage(imgGraph,lineThickness+(edge.trunk ? 8 : 0),val,xCon*sizeFactor,yCon*sizeFactor,cc2x*sizeFactor,cc2y*sizeFactor,edge.hidden); 
			VitimageUtils.drawSegmentInto2DByteImage(imgGraph,1+(edge.trunk ? 3 : 0),0,cc1x*sizeFactor,cc1y*sizeFactor,xCon*sizeFactor,yCon*sizeFactor,edge.hidden); 
			VitimageUtils.drawSegmentInto2DByteImage(imgGraph,1+(edge.trunk ? 3 : 0),0,xCon*sizeFactor,yCon*sizeFactor,cc2x*sizeFactor,cc2y*sizeFactor,edge.hidden); 
			VitimageUtils.drawCircleIntoImage(imgGraph, 3, (int)Math.round (xCon*sizeFactor),(int)Math.round (yCon*sizeFactor), 0,12);
		}
		System.out.println(incrAct+" activated and "+incrNonAct+" non-activated");
		//Draw the vertices
		System.out.print("   3-Drawing central square for "+graph.vertexSet().size()+" vertices  ");
		incr=0;decile=graph.vertexSet().size()/10;
		for(CC cc:graph.vertexSet()) {
			if(cc.isOut)continue;
			double ccx=(cc.x());
			double ccy=(cc.y());
			if(((incr++)%decile)==0)System.out.print(incr+" ");
			boolean extremity =isExtremity(cc,graph);
			if(cc.nPixels>=MIN_SIZE_CC || !extremity)VitimageUtils.drawCircleIntoImage(imgGraph, vx*3, (int)Math.round(ccx*sizeFactor), (int)Math.round(ccy*sizeFactor), 0,extremity ? 12 : 0);
			if(cc.nPixels>=MIN_SIZE_CC || !extremity)VitimageUtils.drawCircleIntoImage(imgGraph, vx*2, (int)Math.round(ccx*sizeFactor), (int)Math.round(ccy*sizeFactor), 0,extremity ? 12 : 255);
		}
				
		imgDates.setDisplayRange(0, N+1);
		System.out.print("\n   4-High res misc drawing (100M +) ");

		System.out.print("1 ");//Build graphArea, a mask of all pixels where something is drawn (edges or vertices)
		ImagePlus graphArea=VitimageUtils.thresholdImage(imgGraph, 0.5, 1000000);
		graphArea=VitimageUtils.getBinaryMaskUnary(graphArea, 0.5);

		System.out.print("2 ");//Build contourArea, a mask of all contours, excepted pixels of graphArea
		ImagePlus contourArea=VitimageUtils.thresholdImage(contour, 0.5, 1000000000);
		contourArea=VitimageUtils.getBinaryMaskUnary(contourArea, 0.5);
		contourArea=VitimageUtils.binaryOperationBetweenTwoImages(contourArea, graphArea, 4);
		
		System.out.print("3 ");
		ImagePlus part1=VitimageUtils.makeOperationBetweenTwoImages(imgGraph, graphArea, 2, true);//Draw pixels of graph
		ImagePlus part2=VitimageUtils.makeOperationBetweenTwoImages(contour, contourArea, 2, true);//Draw pixels of contour

		System.out.print("4 ");
		imgGraph=VitimageUtils.makeOperationBetweenTwoImages(part1, part2, 1, false);
		imgGraph.setDisplayRange(0, N+1);
		System.out.print(" Ok.  ");
		
		return imgGraph;
	}
	

	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	/////////////////////////////////
	///// BELOW IS SAFE ZONE ////////
	/////////////////////////////////
	public static RootModel refinePlongementOfCCGraph(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,ImagePlus distOut,PipelineParamHandler pph,int indexImg) {
		System.out.println("Running the plongement");

		//Prepare output data storage
		FSR sr= (new FSR());
		sr.initialize();
		RootModel rm=new RootModel();
		rm.pixelSize=pph.originalPixelSize*pph.subsamplingFactor;
		double[]hoursExtremities=pph.getHoursExtremities(indexImg);
		hoursExtremities[0]=hoursExtremities[1];
		rm.setHoursFromPph(pph.getHoursExtremities(indexImg));
		double toleranceDistToCentralLine=pph.toleranceDistanceForBeuckerSimplification;

		
		//Exclude cc that were outed
		ArrayList<CC>cctoExclude=new ArrayList<CC>();
		for(CC cc : graph.vertexSet()) if(cc.isOut)  cctoExclude.add(cc);
		for(CC cc : cctoExclude      ) graph.removeVertex(cc);

		
		//Identify some features of vertices
		for(CC cc : graph.vertexSet()) {
			if(cc.trunk) {
				if(cc.day==1)cc.isPrimStart=true;			
				if( cc.getPrimChild()==null) cc.isPrimEnd=true;
			}
			if(!cc.trunk) {
				if(cc.bestIncomingActivatedCC()!=null && cc.bestIncomingActivatedCC().trunk) cc.isLatStart=true;
				if( cc.getLatChild()==null) cc.isLatEnd=true;				
			}
		}
		ArrayList<Root>listRprim=new ArrayList<Root>();
		ArrayList<Integer>listNprim=new ArrayList<Integer>();
		ArrayList<Integer>listDprim=new ArrayList<Integer>();
		ArrayList<Root>listRlat=new ArrayList<Root>();
		ArrayList<Integer>listNlat=new ArrayList<Integer>();
		ArrayList<Integer>listDlat=new ArrayList<Integer>();		
		boolean simplerSimplify=false;
		boolean debugPrim=false;
		boolean debugLat=false;
		
		
		
		
		//////////////////////////////////////////
		//Processing primary roots
		//dessiner la somme des pathlines des racines et l'associer aux CC. 		
		for(CC cc : graph.vertexSet()) {
			if((cc.day!=1) || (!cc.trunk)) continue;
			if(debugPrim)System.out.println("\nPROCESSING PLANT primary, starting with CC "+cc);
			//Identification of connected part of the root
			CC ccNext=cc;
			ArrayList<ArrayList<CC>> llcc=new ArrayList<ArrayList<CC>>();
			ArrayList<ArrayList<Integer>>toKeep=new ArrayList<ArrayList<Integer>>();
			ArrayList<CC>lccFuse=new ArrayList<CC>();
			llcc.add(new ArrayList<CC>());
			llcc.get(0).add(ccNext);
			Root rPrim=new Root(null, rm, "",1);
			listRprim.add(rPrim);
			listNprim.add(llcc.get(0).get(0).n);
			listDprim.add(llcc.get(0).get(0).day);
			int ind=0;
			while(ccNext.getPrimChild()!=null) {
				if(ccNext.isHiddenPrimChild()) {
					if(debugPrim)System.out.println("Next CC is hidden");
					llcc.add(new ArrayList<CC>());
					ind++;
				}
				ccNext=ccNext.getPrimChild();
				listRprim.add(rPrim);
				listNprim.add(ccNext.n);
				listDprim.add(ccNext.day);
				llcc.get(ind).add(ccNext);
				if(debugPrim)System.out.println("Adding "+ccNext+" to array number "+ind);
			}
			
			
			//Dijkstra path processing of the respective parts separated
			//Compute starting distance (when be for lateral)
			double startingDistance=0;
			ArrayList<Double>distInter=new ArrayList<Double>();
			ArrayList<Double>timeInter=new ArrayList<Double>();
			double cumulatedDistance=startingDistance;
			int[]nextSource=null;				
			int[]currentSource=null;
			int[]currentTarget=null;
			int[]previousTarget=null;
			
			for(int indl=0;indl<llcc.size();indl++) {
				toKeep.add(new ArrayList<Integer>());
				List<CC>lcc=llcc.get(indl);
				int nCC=lcc.size();
				CC ccFirst=lcc.get(0);
				CC ccLast=lcc.get(nCC-1);
				
				//Identify starting point
				if(indl>0) {//It is at least the second connected component
					currentSource=nextSource;
					cumulatedDistance+=VitimageUtils.distance(previousTarget[0], previousTarget[1], currentSource[0]+ccFirst.xB, currentSource[1]+ccFirst.yB);
				}
				else{
					currentSource=ccFirst.getExpectedSource();
					
				}
				//Identify target point
				if(ccLast.getPrimChild()==null) {
					//End of primary : Identify target in ccLast
					int[]coords=ccLast.getExpectedSource();
					currentTarget=ccLast.determineTargetGeodesicallyFarestFromTheSource(coords);
				}
				else {
					//Identify source in next, then target in this
					CC ccFirstNext=ccLast.getPrimChild();
					int[]coords=ccLast.getExpectedSource();
					int[][]sourceTarget=cc.findHiddenStartStopToInOtherCC(ccFirstNext, coords);
					nextSource=sourceTarget[1];
					currentTarget=sourceTarget[0];
					previousTarget=new int[] {sourceTarget[0][0],sourceTarget[0][1]};
					previousTarget[0]+=ccLast.xB;
					previousTarget[1]+=ccLast.yB;
				}
			
				//Compute dijkstra path
				CC ccFuse=CC.fuseListOfCCIntoSingleCC(llcc.get(indl));
				lccFuse.add(ccFuse);
				currentSource[0]+=(ccFirst.xB-ccFuse.xB);
				currentSource[1]+=(ccFirst.yB-ccFuse.yB);					
				currentTarget[0]+=(ccLast.xB-ccFuse.xB);
				currentTarget[1]+=(ccLast.yB-ccFuse.yB);					
				ccFuse.determineVoxelShortestPath(currentSource, currentTarget, 8, null);
				cumulatedDistance=ccFuse.setDistancesToMainDijkstraPath(cumulatedDistance);
				
				
				//Evaluate the timing along dijkstra path
				//Set first pixel to birthDate of root
				//Walking along dijkstraPath, and attribute to each a componentIndex
				int[]indices=new int[ccFuse.mainDjikstraPath.size()];
				for(int n=0;n<ccFuse.mainDjikstraPath.size();n++) {
					Pix p=ccFuse.mainDjikstraPath.get(n);
					int xx=p.x+ccFuse.xB;
					int yy=p.y+ccFuse.yB;
					for(int i=0;i<nCC;i++) {
						if(lcc.get(i).r.contains(xx, yy)) {
							indices[n]=i;
						}
					}
				}


				//Eventually add the point for the first if it is the first component in llcc
				if(indl==0) {
					timeInter.add((double)( lcc.get(0).day-1 ));
					distInter.add(ccFuse.mainDjikstraPath.get(0).wayFromPrim);
					if(debugPrim)				System.out.println("Adding a point at indl="+indl+" indcc="+0+" time="+timeInter.get(timeInter.size()-1)+" dist="+distInter.get(timeInter.size()-1));
				}
				
				//For each component except the last, identify the last point of it and If necessary, add the last one (see the for loop condition)
				for(int i=0;i<(lcc.size()-1) ; i++) {
					double distMax=-1;
					int indMax=-1;
					for(int n=0;n<ccFuse.mainDjikstraPath.size();n++) {
						if(indices[n]==i) {
							distMax=ccFuse.mainDjikstraPath.get(n).wayFromPrim;
							indMax=n;
						}
					}
					if(indMax!=-1) {
						distInter.add(distMax);	
						timeInter.add((double)(lcc.get(i).day));	
						toKeep.get(indl).add(indMax);
					}
					if(debugPrim)					System.out.println("Adding a point at indl="+indl+" indcc="+0+" time="+timeInter.get(timeInter.size()-1)+" dist="+distInter.get(timeInter.size()-1));
				}
				if(indl==llcc.size()-1) {
					distInter.add((double)(ccFuse.mainDjikstraPath.get(ccFuse.mainDjikstraPath.size()-1).wayFromPrim));	
					timeInter.add((double)(lcc.get(lcc.size()-1).day));	
				}
 
			}	
			//Convert results of correspondance into double tabs
			int N=distInter.size();double[]xPoints=new double[N];double[]yPointsHours=new double[N];double[]yPoints=new double[N];for(int i=0;i<N;i++) {xPoints[i]=distInter.get(i);yPoints[i]=timeInter.get(i);yPointsHours[i]=hoursExtremities[(int)Math.round(timeInter.get(i))];}	
				

			
			//Evaluate time for all the respective dijkstraPath	and convert to RSML
			for(int li=0;li<lccFuse.size();li++) {
				CC ccF=lccFuse.get(li);
				List<CC>lcc=llcc.get(li);
				//Propagate distance into the ccFuse's	
				ccF.updateAllDistancesToTrunk();
				
				//Convert distance into time	
				for(Pix p:ccF.pixGraph.vertexSet()) {
					p.time=SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim,xPoints,yPoints);
					p.timeOut=SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim+p.distOut,xPoints,yPoints);
					p.timeHours=SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim,xPoints,yPointsHours);
					p.timeOutHours=SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim+p.distOut,xPoints,yPointsHours);
				}

				//Back copy to the initial CCs
				for(CC c : lcc) {
					for(Pix p:c.pixGraph.vertexSet()) {
						Pix p2=ccF.getPix(p.x+c.xB-ccF.xB, p.y+c.yB-ccF.yB);
						p.dist=p2.dist;
						p.distanceToSkeleton=p2.distanceToSkeleton;
						p.distOut=p2.distOut;
						p.isSkeleton=p2.isSkeleton;
						p.time=p2.time;
						p.timeOut=p2.timeOut;
						p.timeHours=p2.timeHours;
						p.timeOutHours=p2.timeOutHours;
						p.wayFromPrim=p2.wayFromPrim;
						p2.offX=c.xB;
						p2.offY=c.yB;
					}
				}

				//Subsample respective dijkstra path with beucker algorithm, and collect RSML points with speed and 
				
				List<Pix> list=null;

				list= simplerSimplify ? DouglasPeuckerSimplify.simplifySimpler(ccF.mainDjikstraPath,toKeep.get(li) ,3) :
					DouglasPeuckerSimplify.simplify(ccF.mainDjikstraPath,toKeep.get(li) ,toleranceDistToCentralLine);
				//}
				for(int i=0;i<list.size()-1;i++) {
					Pix p=list.get(i);
					rPrim.addNode(p.x+ccF.xB,p.y+ccF.yB,p.time,p.timeHours,(i==0)&&(li==0));
				}

				Pix p=list.get(list.size()-1);
				rPrim.addNode(p.x+ccF.xB,p.y+ccF.yB,p.time,p.timeHours,false);
				if(li!=(lccFuse.size()-1))rPrim.setLastNodeHidden();
			}
			rPrim.computeDistances();
			rm.rootList.add(rPrim);
		}
				
				
		

		
		
		
		//Processing lateral roots
		//dessiner la somme de pathlines des racines et l'associer aux CC. 
		int incrLat=1;
		for(CC cc : graph.vertexSet()) {
			if(!cc.isLatStart)continue;
			debugLat=false;
			if(debugLat)	System.out.println("\n\n\nProcessing lateral root #"+(incrLat++)+" : "+cc+" whose source.n="+cc.bestIncomingActivatedEdge().source.n);
			
			//Identification of correspondant primary root
			Root myRprim=null;
			for(int i=0;i<listRprim.size();i++) {
				if(debugLat)System.out.println("Looking for "+cc.bestIncomingActivatedEdge().source+"    testing a Dprim-Nprim"+listDprim.get(i)+"-"+listNprim.get(i));				
				if(listNprim.get(i)==cc.bestIncomingActivatedEdge().source.n && listDprim.get(i)==cc.bestIncomingActivatedEdge().source.day) {myRprim=listRprim.get(i);if(debugLat)System.out.println(" \n\nTHIS ONE ! ");}
			}
			if(myRprim==null) {
				System.out.println("Here in RAG L1373");
				System.out.println("Processing a new latstart that is "+cc);
				for(ConnectionEdge con : graph.edgesOf(cc))System.out.println(con);
				System.out.println("En effet,"+cc.ccPrev);
				System.out.println("Rprimnull at "+cc);
				
			}
			CC lookCC=getCC(graph,377,182);
			
			boolean debugCC=false;
			debugLat=debugCC;
			//Identification of connected part of the root
			CC ccNext=cc;
			ArrayList<ArrayList<CC>> llcc=new ArrayList<ArrayList<CC>>();
			ArrayList<ArrayList<Integer>>toKeep=new ArrayList<ArrayList<Integer>>();
			ArrayList<CC>lccFuse=new ArrayList<CC>();
			llcc.add(new ArrayList<CC>());
			llcc.get(0).add(ccNext);
			int ind=0;
			while(ccNext.getLatChild()!=null) {
				if(debugCC)System.out.println("DEB 01 "+ind+" - "+ccNext);
				if(ccNext.isHiddenLatChild()) {
					llcc.add(new ArrayList<CC>());
					ind++;
				}
				ccNext=ccNext.getLatChild();
				llcc.get(ind).add(ccNext);
				//if(debugCC)	System.out.println(" -> Adding "+ccNext+" to array number "+ind);
			}
			if(debugCC)System.out.println("DEB 02 insertion ok");			
			//Separate dijkstra path processing of the respective parts
			//LATERAL ROOTS//////////////////////////////////////////////////////////////////////////////////////
			//Compute starting distance (when be for lateral)
			double startingDistance=0;
			ArrayList<Double>distInter=new ArrayList<Double>();
			ArrayList<Double>timeInter=new ArrayList<Double>();
			double cumulatedDistance=startingDistance;
			int[]nextSource=null;				
			int[]previousTarget=null;
			int[]currentSource=null;
			int[]currentTarget=null;
			
			for(int indl=0;indl<llcc.size();indl++) {
				toKeep.add(new ArrayList<Integer>());
				List<CC>lcc=llcc.get(indl);
				int nCC=lcc.size();
				CC ccFirst=lcc.get(0);
				CC ccLast=lcc.get(nCC-1);
				CC ccFuse=CC.fuseListOfCCIntoSingleCC(llcc.get(indl));
				lccFuse.add(ccFuse);
				boolean debug=false;
				//if(ccLast==getCC(graph, 4245,4237))debug=true;
				//System.out.println("Debug !");
				//Identify starting point
				if(indl>0) {//It is at least the second connected component
					currentSource=nextSource;
					cumulatedDistance+=VitimageUtils.distance(previousTarget[0], previousTarget[1], currentSource[0]+ccFirst.xB, currentSource[1]+ccFirst.yB);
				}
				else 	currentSource=ccFirst.getExpectedSource();
				
				
				if(indl==(llcc.size()-1)) {
					//Identify target point
					if(debug)System.out.println("End of lateral : "+lcc.get(lcc.size()-1));
					int[]coords=ccFirst.getNextSourceFromFacetConnexion(ccFirst.bestIncomingActivatedEdge()); //currentSource;
					coords=new int[] {coords[0]+ccFirst.xB-ccFuse.xB,coords[1]+ccFirst.yB-ccFuse.yB};
					currentTarget=ccFuse.determineTargetGeodesicallyFarestFromTheSource(coords);
					currentTarget[0]+=(ccFuse.xB-ccLast.xB);
					currentTarget[1]+=(ccFuse.yB-ccLast.yB);
					if(debug)System.out.println("Coords target of last = "+currentTarget[0]+","+currentTarget[1]);
				}
				else {
					//Identify source in next, then target in this
					CC ccFirstNext=ccLast.getLatChild();
					int[]coords=ccLast.getExpectedSource();
					int[][]sourceTarget=ccLast.findHiddenStartStopToInOtherCC(ccFirstNext, coords);
					nextSource=sourceTarget[1];
					currentTarget=sourceTarget[0];
					previousTarget=new int[] {sourceTarget[0][0],sourceTarget[0][1]};
					previousTarget[0]+=ccLast.xB;
					previousTarget[1]+=ccLast.yB;
				}
			
				//Compute dijkstra path
				currentSource[0]+=(ccFirst.xB-ccFuse.xB);
				currentSource[1]+=(ccFirst.yB-ccFuse.yB);					
				currentTarget[0]+=(ccLast.xB-ccFuse.xB);
				currentTarget[1]+=(ccLast.yB-ccFuse.yB);					
				
				if(debug) {
					ccFuse.drawDist().show();
					System.out.println("Coords source of fuse = "+currentSource[0]+","+currentSource[1]);
					System.out.println("Coords target of fuse = "+currentTarget[0]+","+currentTarget[1]);
				}
				
				ccFuse.determineVoxelShortestPath(currentSource, currentTarget, 8, null);
				cumulatedDistance=ccFuse.setDistancesToMainDijkstraPath(cumulatedDistance);
				if(debug)System.out.println("Lenght of path="+ccFuse.mainDjikstraPath.size());
				if(debug)VitimageUtils.waitFor(60000000);
				
				//Evaluate the timing along dijkstra path
				//Set first pixel to birthDate of root
				//Walking along dijkstraPath, and attribute to each a componentIndex
				int[]indices=new int[ccFuse.mainDjikstraPath.size()];
				for(int n=0;n<ccFuse.mainDjikstraPath.size();n++) {
					Pix p=ccFuse.mainDjikstraPath.get(n);
					int xx=p.x+ccFuse.xB;
					int yy=p.y+ccFuse.yB;
					for(int i=0;i<nCC;i++) {
						if(lcc.get(i).r.contains(xx, yy)) {
							indices[n]=i;
						}
					}
				}
	
	
				//Eventually add the point for the first if it is the first component in llcc
				if(indl==0) {
					timeInter.add((double)( lcc.get(0).day-1 ));
					distInter.add(ccFuse.mainDjikstraPath.get(0).wayFromPrim);
				}
				//For each component except the last, identify the last point of it and If necessary, add the last one (see the for loop condition)
				for(int i=0;i<lcc.size()-1 ; i++) {
					double distMax=-1;
					int indMax=-1;
					for(int n=0;n<ccFuse.mainDjikstraPath.size();n++) {
						if(indices[n]==i) {
							distMax=ccFuse.mainDjikstraPath.get(n).wayFromPrim;
							indMax=n;
						}
					}
					if(indMax>=0) {
						distInter.add((double)(distMax));	
						timeInter.add((double)(lcc.get(i).day));	
						toKeep.get(indl).add(indMax);
					}
				}
				if(indl==llcc.size()-1) {
					distInter.add((double)(ccFuse.mainDjikstraPath.get(ccFuse.mainDjikstraPath.size()-1).wayFromPrim));	
					timeInter.add((double)(lcc.get(lcc.size()-1).day));	
				}
			}	
			//Convert results of correspondance into double tabs
			int N=distInter.size();
			double[]xPoints=new double[N];
			double[]yPoints=new double[N];
			double[]yPointsHours=new double[N];
			for(int i=0;i<N;i++) {
				xPoints[i]=distInter.get(i);
				yPoints[i]=timeInter.get(i);
				yPointsHours[i]=hoursExtremities[(int)Math.round (timeInter.get(i))];
			}	
				
			
			//Convert results of correspondance into double tabs
//			int N=distInter.size();double[]xPoints=new double[N];double[]yPointsHours=new double[N];double[]yPoints=new double[N];for(int i=0;i<N;i++) {xPoints[i]=distInter.get(i);yPoints[i]=timeInter.get(i);yPointsHours[i]=hoursExtremities[(int)Math.round(timeInter.get(i))];}	
	
			
			//Evaluate time for all the respective dijkstraPath	and convert to RPrimSML
			Root rLat=new Root(null, rm, "",2);
			listRlat.add(rLat);
			listNlat.add(llcc.get(0).get(0).n);
			listDlat.add(llcc.get(0).get(0).day);
			for(int li=0;li<lccFuse.size();li++) {
				if(debugLat)	System.out.println("Processing path for component "+li);
				CC ccF=lccFuse.get(li);
				List<CC>lcc=llcc.get(li);
				//Propagate distance into the ccFuse's	
				ccF.updateAllDistancesToTrunk();
				
				//Convert distance into time	
				for(Pix p:ccF.pixGraph.vertexSet()) {
					p.time=SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim,xPoints,yPoints);
					p.timeOut=SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim+p.distOut,xPoints,yPoints);
					p.timeHours=SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim,xPoints,yPointsHours);
					p.timeOutHours=SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim+p.distOut,xPoints,yPointsHours);
				}
	
				//Back copy to the initial CCs
				for(CC c : lcc) {
					for(Pix p:c.pixGraph.vertexSet()) {
						Pix p2=ccF.getPix(p.x+c.xB-ccF.xB, p.y+c.yB-ccF.yB);
						p.dist=p2.dist;
						p.distanceToSkeleton=p2.distanceToSkeleton;
						p.distOut=p2.distOut;
						p.isSkeleton=p2.isSkeleton;
						p.time=p2.time;
						p.timeOut=p2.timeOut;
						p.timeHours=p2.timeHours;
						p.timeOutHours=p2.timeOutHours;
						p.wayFromPrim=p2.wayFromPrim;
						p2.offX=c.xB;
						p2.offY=c.yB;
					}
				}
	
				//Subsample respective dijkstra path with beucker algorithm, and collect RSML points
				List<Pix> list= simplerSimplify ? DouglasPeuckerSimplify.simplifySimpler(ccF.mainDjikstraPath,toKeep.get(li) ,3) :
					DouglasPeuckerSimplify.simplify(ccF.mainDjikstraPath,toKeep.get(li) ,toleranceDistToCentralLine);
				if(debugLat)	System.out.println("Simplifying a list of "+ccF.mainDjikstraPath.size()+" to list of "+list.size());
				for(int i=0;i<list.size()-1;i++) {
					Pix p=list.get(i);
					rLat.addNode(p.x+ccF.xB,p.y+ccF.yB,p.time,p.timeHours,(i==0)&&(li==0));
				}	
				Pix p=list.get(list.size()-1);
				rLat.addNode(p.x+ccF.xB,p.y+ccF.yB,p.time,p.timeHours,false);
				if(li!=(lccFuse.size()-1))rLat.setLastNodeHidden();
			}
			rLat.computeDistances();
			rLat.resampleFlyingPoints(rm.hoursCorrespondingToTimePoints);
			myRprim.attachChild(rLat);
			rLat.attachParent(myRprim);
			rm.rootList.add(rLat);
		}
		rm.standardOrderingOfRoots();
		return rm;
	}
	
	
	
	
	
	

	
	
	
	
	
	

	public static void reconnectDisconnectedBranches_v2(ImagePlus img2, SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,PipelineParamHandler pph,int formalism,boolean workAlsoBranches,boolean hack) {
		ImagePlus img=getDistanceMapsToDateMaps(img2);

		System.out.println("\nReconnection of disconnected branches");
		Timer t=new Timer();
		t.print("Start");
		ArrayList<CC>listStart=new ArrayList<CC>();
		ArrayList<CC>listStop=new ArrayList<CC>();
		double thresholdScore=PENALTY_COST;

		int Nalso=0;
		ArrayList<ConnectionEdge>tabKeepEdges=new ArrayList<ConnectionEdge>();
		ArrayList<CC[]>tabKeepCCStart=new ArrayList<CC[]>();

		//Disconnect branching on trunk
		if(workAlsoBranches) {
			for(CC cc:graph.vertexSet()) {
				if(!cc.trunk)continue;
				for(ConnectionEdge edge : graph.outgoingEdgesOf(cc)) {
					if(edge.activated && (!edge.target.trunk)) {
						edge.activated=false;
						tabKeepEdges.add(edge);
						tabKeepCCStart.add(new CC[] {cc,edge.target});
						Nalso++;
					}
				}
			}			
		}
		
		//Identify the disconnected branches
		for(CC cc: graph.vertexSet()) {
			cc.stamp=0;
			if(cc.trunk)continue;
			if(cc.day<2)continue;//Do not process parent nodes of system
			if(cc.bestIncomingActivatedEdge()==null) {
				CC cctmp=cc;
				int tot=cc.nPixels;
				while(cctmp.bestOutgoingActivatedEdge()!=null) {
					cctmp=cctmp.bestOutgoingActivatedEdge().target;
					tot+=cctmp.nPixels;
				}
				//Count size
				if(tot>=MIN_SIZE_CC) {
					listStart.add(cc);
					cc.stamp=tot;
				}
			}
		}

		
		//Identify the possible dead ends 
		for(CC cc: graph.vertexSet()) {
			if(cc.trunk)continue;
			if(cc.bestOutgoingActivatedEdge()==null){
				CC cctmp=cc;
				int tot=cc.nPixels;
				while(cctmp.bestIncomingActivatedEdge()!=null && (!cctmp.bestIncomingActivatedEdge().source.trunk)) {
					cctmp=cctmp.bestIncomingActivatedEdge().source;
					tot+=cctmp.nPixels;
				}
				//Count size
				if(tot>=MIN_SIZE_CC ) {
					listStop.add(cc);
					cc.stamp=tot;
				}
			}
		}

		
		//Algorithme hongrois
		int Nstart=listStart.size();
		int Nstop=listStop.size();
		if(Nstart==0 || ((Nstop+Nalso==0)))return;
		System.out.println("Running hungarian algorithm on Matrix [stops="+Nstop+"][starts="+Nstart+"]");

		
		boolean finished=false;
		int stepHung=0;
		CC ccStopWant=getCC(graph,19,4074,2628); 
		CC ccStartWant=getCC(graph,19,4067,2731  );

		System.out.println("STOPWANT="+ccStopWant);
		System.out.println("STARTWANT="+ccStartWant);
		for(int i=0;i<Nstop;i++) {
			if(listStop.get(i)==ccStopWant)System.out.println("DEBUGGGGGGG stop");
			listStop.get(i).changedRecently=true;
		}
        for(int j=0;j<Nstart;j++) {
			if(listStart.get(j)==ccStartWant)System.out.println("DEBUGGGGGGG start");
			listStart.get(j).changedRecently=true;   
        }
		double[][]costMatrix=new double[Nstop][Nstart];
		Timer t2=new Timer();
		boolean isTheFirstStep=true;
		while(!finished) {

			CC ccstoptest=getCC(graph,2026,2498);
			CC ccstarttest=getCC(graph,2025,2550);
			
			//Build score matrix
			int nStill=0;
			for(int i=0;i<Nstop;i++) {
				if(isTheFirstStep)if((i%10)==0)t2.print("Hungarian algo : building initial score matrix, line  "+i+" / "+Nstop);
	            for(int j=0;j<Nstart;j++) {    
	            	boolean debug=false &&( (i==33) && (j==119));
	            	if(listStart.get(j)==ccStartWant && listStop.get(i)==ccStopWant) { debug=false;System.out.println("DEBUGGG");}
	            	if(listStop.get(i)==listStart.get(j))costMatrix[i][j]=PENALTY_COST;
	            	else {
	            		if(listStop.get(i).associateSuiv==listStart.get(j))costMatrix[i][j]=-VitimageUtils.EPSILON;
	            		else {
	            			if(listStop.get(i).associateSuiv!=null || listStart.get(j).associatePrev!=null)costMatrix[i][j]=10000;
	            			else{
	            				nStill++;
	            				if(listStop.get(i).changedRecently || listStart.get(j).changedRecently) {	
	            					if(listStop.get(i)==ccstoptest && listStart.get(j)==ccstarttest) {debug=true;}
	            					costMatrix[i][j]=weightingOfPossibleHiddenEdge_v2(img,graph,listStop.get(i),listStart.get(j),pph, debug);
	            					if(Double.isNaN(costMatrix[i][j]))	System.out.println("I="+i+" J="+j);
	            					
	            				}
	            			}
	            		}
	            	}
	            	if(debug)System.out.println("FINAL VAL="+costMatrix[i][j]);
	            }
	        }
			isTheFirstStep=false;
			for(int i=0;i<Nstop;i++) listStop.get(i).changedRecently=false;
            for(int j=0;j<Nstart;j++)listStart.get(j).changedRecently=false;   
            if(nStill==0) {
            	finished=true;
            	continue;
            }
		
			//Execute algorithm
			HungarianAlgorithm hung=new HungarianAlgorithm(costMatrix);
			int []solutions=hung.execute();
			double meanScore=0;
			int N=0;
			int bestI=-1;
			int bestJ=-1;
			double bestW=10000;
			stepHung++;
			
			if(formalism==1) {
				//Find the best association
				for(int i=0;i<listStop.size();i++) {
					if(solutions[i]==-1)continue;
					int j=solutions[i];
					double weight=costMatrix[i][j];	
					if(weight<0)continue;
			    	meanScore+=weight;
			    	N++;
			    	if(weight<bestW) {bestW=weight;bestI=i;bestJ=j;}
				}		    	
			}
			else {
				IJ.showMessage("?");
				for(int i=0;i<Nstop;i++) {
		            for(int j=0;j<Nstart;j++) {    
						double weight=costMatrix[i][j];	
				    	meanScore+=weight;
				    	N++;
				    	if(weight<bestW) {bestW=weight;bestI=i;bestJ=j;}
		            }
				}
			}

			//Test the new arc, and if so, Connect ccStop et ccStart with a virtual arc
			if(bestI==-1) {finished=true;continue;}
			CC ccStop=listStop.get(bestI);
			CC ccStart=listStart.get(bestJ);
			boolean deb=(ccStop==getCC(graph,953,4070 ));
		    System.out.println("\n\nHungarian iterative at step "+stepHung+" N associated="+N+" meanscore="+(meanScore/N)+".\n   Chosen link with score="+bestW+" : "+ccStop+" --> "+ccStart);
		    weightingOfPossibleHiddenEdge_v2(img,graph,listStop.get(bestI),listStart.get(bestJ),pph,true);
		    if(deb)IJ.showMessage("There");
		    System.out.println("H2");
			//if(testCycle(graph,ccStop,ccStart)) {finished=true;continue;}
		    if(bestW>=thresholdScore) {System.out.println("Best exceeds. Break loop");finished=true;continue;}
		    
		    
		    
		    double xCon=ccStop.r.getContourCentroid()[0]*0.5+ccStart.r.getContourCentroid()[0]*0.5;
			double yCon=ccStop.r.getContourCentroid()[1]*0.5+ccStart.r.getContourCentroid()[1]*0.5;
			ConnectionEdge edge=new ConnectionEdge( xCon,yCon, 0, ccStop,ccStart,0,0); 
			edge.activated=true;
			edge.hidden=true;
			edge.trunk=false;
			graph.addEdge(ccStop, ccStart,edge); 
			CycleDetector<CC, ConnectionEdge> cyc=new CycleDetector<>(graph);
			if(cyc.detectCycles()) {
				System.out.println("WARNING : CYCLES AFTER HUNGARIAN ! BREAK !\n was attempting to connect stop=\n"+ccStop+" \nwith start=\n"+ccStart);
				finished=true;continue;
			}
			graph.setEdgeWeight(edge, bestW);
			ccStop.associateSuiv=ccStart;
			ccStart.associatePrev=ccStop;
			CC ct=ccStop.bestIncomingActivatedCC();
			if(ct !=null) {
				ct.changedRecently=true;
				CC ct2=ct.bestIncomingActivatedCC();
				if(ct2 !=null) {
					ct2.changedRecently=true;
				}
			}
			ct=ccStart.bestOutgoingActivatedCC();
			if(ct !=null) {
				ct.changedRecently=true;
				CC ct2=ct.bestOutgoingActivatedCC();
				if(ct2 !=null) {
					ct2.changedRecently=true;
				}
			}
		}
				
		//Reconnect all branches that have not been connected
		if(workAlsoBranches) {
			for(ConnectionEdge edge:tabKeepEdges) {
				if(edge.target.bestIncomingActivatedEdge()==null)edge.activated=true;
			}			
		}
		t.print("End hungarian");
		//VitimageUtils.waitFor(500000000);
		
	}

	public static SimpleDirectedWeightedGraph<CC, ConnectionEdge> buildGraphFromDateMap(ImagePlus imgDates,int connexity,double[]hours) {
		trickOnImgDates(imgDates);
		int maxSizeConnexion=500000000;
		int nDays=1+(int)Math.round(VitimageUtils.maxOfImage(imgDates));
		Roi[][]roisCC=new Roi[nDays][];
		CC[][]tabCC=new CC[nDays][];
		SimpleDirectedWeightedGraph<CC,ConnectionEdge>graph=new SimpleDirectedWeightedGraph<>(ConnectionEdge.class);

		
		//Identify connected components with label 1-N
		roisCC[0]=new Roi[] {new Roi(new Rectangle(0,0,imgDates.getWidth(),imgDates.getHeight()))};
		tabCC[0]=new CC[] {new CC(0,hours[0],0,roisCC[0][0],graph)};		
		System.out.print("Identifying connected components ");
		for(int d=1;d<nDays;d++) {
			System.out.print(d+" ");
			ImagePlus binD=VitimageUtils.thresholdImage(imgDates, d, d+0.99);
			ImagePlus ccD=VitimageUtils.connexeBinaryEasierParamsConnexitySelectvol(binD, connexity, 0);
			VitimageUtils.printImageResume(ccD);
			ImagePlus allConD=VitimageUtils.thresholdImageToFloatMask(ccD, 0.5, 10E8);
			VitimageUtils.waitFor(100);
			roisCC[d]=VitimageUtils.segmentationToRoi(allConD);
			if(roisCC[d]==null) {tabCC[d]=null;continue;}
			tabCC[d]=new CC[roisCC[d].length];
			for(int n=0;n<roisCC[d].length;n++) {
				CC cc=new CC(d,hours[d-1],n,roisCC[d][n],graph);
				tabCC[d][n]=cc;
				graph.addVertex(cc);
			}
		}
		System.out.println();

		//Identify connexions
		System.out.print("Identifying connexions ");
		for(int d1=1;d1<nDays;d1++) {
			System.out.print(d1+" ");
			if(roisCC[d1]==null)continue;
			for(int n1=0;n1<roisCC[d1].length;n1++) {
				for(int d2=1;d2<nDays;d2++) {
					if(roisCC[d2]==null)continue;
					for(int n2=0;n2<roisCC[d2].length;n2++) {
						if((d2<d1) || ( (d2==d1) && (n2<=n1) ))continue;
						double[] tabConn=tabCC[d1][n1].nFacets4connexe_V3(tabCC[d2][n2]); 
						int n=(int) Math.round(tabConn[0]);
						double x=tabConn[1];
						double y=tabConn[2];
						if(n>0 && n<maxSizeConnexion) {
							graph.addEdge(tabCC[d1][n1], tabCC[d2][n2],new ConnectionEdge(x, y, n,tabCC[d1][n1], tabCC[d2][n2],tabConn[3],tabConn[4]));
						}
					}
				}
			}
		}
		System.out.println();

		return graph;
	}
				
	//Compute how much the straight computed path is far away from expected structures.
	public static double costDistanceOfPathToObject(ImagePlus img,double x0,double y0,double x1,double y1) {
		//For path going lower than 5 pixels, path is at no cost
		//For 5-10, each pixel contributes to mean from 0.0 to 1.0
		//For 10-20, each pixel contributes to mean from 1.0 to 3.0
		int X=img.getWidth();
		int Y=img.getHeight();
		if(x0<1)x0=1;
		if(y0<1)y0=1;
		if(x1<1)x1=1;
		if(y1<1)y1=1;
		if(x0>(X-2))x0=X-2;
		if(y0>(Y-2))y0=Y-2;
		if(x1>(X-2))x1=X-2;
		if(y1>(Y-2))y1=Y-2;
		float[]tab=(float[]) img.getStack().getPixels(1);
		double dt=0.33;
		double xx=x0;
		double yy=y0;
		int nb=(int) (Math.sqrt((x1-x0)*(x1-x0)+(y1-y0)*(y1-y0))/dt);
		int N=nb;
		double total=0;
		double[]vect=new double[]{x1-x0,y1-y0,0};
		vect=TransformUtils.normalize(vect);
		vect=TransformUtils.multiplyVector(vect, dt);
		while((nb--)>=0) {
			xx+=vect[0];
			yy+=vect[1];
			int x=toInt(xx);
			int y=toInt(yy);
			int val=toInt(tab[y*X+x]);
			if(val<2)total+=0;
			else total+=(val-1)*2;//0.5;
		}
		if(N==0)return 0.5;
		else return (total/N);
	}
		
	public static void updateCostAndDisconnectNonOptimalLateralBranches_V2(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph){
		//The idea : coming from N-1, for each CC that is no trunk, select the successor with the biggest number of followers, counted in pixels
		CC cctest=getCC(graph,371,300);//To test : 2192 1820 7   2240 2010 7       2231 1494 9
		
		for(ConnectionEdge edge : graph.outgoingEdgesOf(cctest))System.out.println(edge);
		int maxDay=getMaxDay(graph);
		for(int i=maxDay;i>0;i--) {
			for(CC cc:graph.vertexSet()) {
			if(cc.trunk || cc.day!= i)continue;
				if(i==maxDay) {cc.count=cc.nPixels;continue;}
				
				
				ConnectionEdge bestEdge=cc.bestCountOutgoingEdge_v2();
				cc.count=cc.nPixels;
				for(ConnectionEdge edge : graph.outgoingEdgesOf(cc)){
					cc.count+=graph.getEdgeTarget(edge).count;
				}
				for(ConnectionEdge edge : graph.outgoingEdgesOf(cc)) {
					//if(debug)System.out.println("Processing an edge "+edge);
					if(edge!=bestEdge)  {
						//if(debug)System.out.println("Disactivating this." );
						edge.activated=false;
					}
				}
			}
		}		
		
	}

	
	public static double getCostFirstOrderConnection(ConnectionEdge edge,int nDays,double[]hoursExtremities) {
		CC source=edge.source;
		CC target=edge.target;
		if(source.day==0)return (-1000);
		if(source.day>target.day)return (10000);
		double dx=target.r.getContourCentroid()[0]-source.r.getContourCentroid()[0];
		double dy=target.r.getContourCentroid()[1]-source.r.getContourCentroid()[1];
		double dday=Math.abs(target.day-source.day);//TODO : count in hours ? Not sure, in fact
		double prodscal=dy/Math.sqrt(dx*dx+dy*dy);
		double cost=0;
		double deltaHoursSource=hoursExtremities[source.day]-hoursExtremities[source.day-1];
		double deltaHoursTarget=hoursExtremities[target.day]-hoursExtremities[target.day-1];
		if((source.trunk) && (!target.trunk)) {
			cost+=(1-VitimageUtils.similarity(source.nPixels/deltaHoursSource,target.nPixels/deltaHoursTarget)) + (nDays-1) + 0.5 + VitimageUtils.EPSILON;
			if(cost<=-1) {
				System.out.println("\nDEBUG FIRST CONNECTION SOURCE TRUNK");
				System.out.println("A="+0.5*(2-2*VitimageUtils.similarity(source.nPixels/deltaHoursSource,target.nPixels/deltaHoursTarget)));
				System.out.println("B="+(nDays-1));
				System.out.println("C="+(1+VitimageUtils.EPSILON));
				System.out.println("Sum="+cost);
			}
		}
		else{			
			cost+=(1-VitimageUtils.similarity(source.nPixels/deltaHoursSource,target.nPixels/deltaHoursTarget)) + (dday<2 ? 0 : dday-1)  -0.5*prodscal +VitimageUtils.EPSILON/2 ; 
			if(cost<=-1) {
				System.out.println("\nDEBUG FIRST CONNECTION OTHER");
				System.out.println("A="+0.5*(2-2*VitimageUtils.similarity(source.nPixels/deltaHoursSource,target.nPixels/deltaHoursTarget)));
				System.out.println("B="+(dday<2 ? 0 : dday-1));
				System.out.println("C="+(-prodscal));
				System.out.println("Sum="+cost);
			}
		}
		return cost;
	}
	
	public static double getCostFirstOrderConnection_phase2(ConnectionEdge edge,int nDays,double[]hoursExtremities) {
		CC source=edge.source;
		CC target=edge.target;
		if(source.day==0)return (-1000);
		if(source.day>target.day)return (10000);
		double dx=target.r.getContourCentroid()[0]-source.r.getContourCentroid()[0];
		double dy=target.r.getContourCentroid()[1]-source.r.getContourCentroid()[1];
		double dday=Math.abs(target.day-source.day);//TODO : count in hours ? Not sure, in fact
		double prodscal=dy/Math.sqrt(dx*dx+dy*dy);
		double cost=0;
		double deltaHoursSource=hoursExtremities[source.day]-hoursExtremities[source.day-1];
		double deltaHoursTarget=hoursExtremities[target.day]-hoursExtremities[target.day-1];
		if((source.trunk) && (!target.trunk)) {
			cost+=(1-VitimageUtils.similarity(source.nPixels/deltaHoursSource,target.nPixels/deltaHoursTarget)) + 0.5 + VitimageUtils.EPSILON;
		}
		else{			
			cost+=(1-VitimageUtils.similarity(source.nPixels/deltaHoursSource,target.nPixels/deltaHoursTarget)) + (dday<2 ? 0 : dday-1)  +VitimageUtils.EPSILON/2/*-0.5*/-prodscal*0.5  ; 
		}
		return cost;
	}


	public static void reconnectLateralThatWereThereFromTheStart(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,ImagePlus sampleImgForDims,PipelineParamHandler pph,int indexBox) {
		System.out.println("\n-------------------------------------------------------\nStarting outlier detection, rebranching forgotten branches and that folks in RAG\n-------------------------------------------------------");
		int nTimesSeries=pph.imgSerieSize[indexBox];

		//1) Identify forgotten axes.
		//These are the roots already existing at start.
		//If they went on growing, they are now disconnected from the primary
		for (CC ccStart:graph.vertexSet()) {
			if(!ccStart.trunk && ccStart.bestIncomingActivatedCC()==null) {//If start of lateral with no entering arc from a trunk
				//Count total number of successors components and reject if less than four 
				//As we are focusing on roots present from the start, thus there should be a lot of successors)
				int nSuccessors=1;
				CC cEnd=ccStart;
				while(cEnd.bestOutgoingActivatedCC()!=null) {
					cEnd=cEnd.bestOutgoingActivatedCC();
					nSuccessors++;
				}
				if(nSuccessors<4)continue;
				
				
				
				//This one was selected. Now looking for a possible connexion to the trunk
				ConnectionEdge edge=null;
				for(ConnectionEdge ed : graph.incomingEdgesOf(ccStart)) {
					if(ed.source.trunk && ed.source.day<=ccStart.day)edge=ed;
				}
				if(edge!=null) {// We found a possible conexion to the trunk. Let's just activate it, and it's on.
					edge.activated=true;
					continue;
				}

				
				//From there, the root is an actual lateral root, grew for the series, but there is no straightforward attachment, by a physical connexion
				//Let s test if this thing has grew a bit
				if(cEnd.euclidianDistanceToCC(ccStart)>(pph.getMeanSpeedLateral()*nTimesSeries/4.0)  &&  nSuccessors>(nTimesSeries/4.0) ) {
					//This is a forgotten root of a certain size : a quarter of the mean size in length and number of successors

					//Identify the nearer trunk that was present the instant before this root, and do the connexion
					double distMin=1000000;
					CC findOut=null;
					for (CC cctr:graph.vertexSet()) {
						if (!cctr.trunk || cctr.day==0)continue;
						if (cctr.day>ccStart.day)continue;
						double dist=cctr.euclidianDistanceToCC(ccStart);
						if(dist<distMin) {distMin=dist;findOut=cctr;}
					}
					if(findOut==null)continue;
					ConnectionEdge edd=new ConnectionEdge( findOut.x*0.5 + ccStart.x*0.5, findOut.y*0.5 + ccStart.y*0.5, 0, findOut,ccStart,findOut.x>ccStart.x ? -1 : 1 ,findOut.y>ccStart.y ? -1 : 1); 
					edd.activated=true;
					edd.hidden=true;
					edd.trunk=false;
					graph.addEdge(findOut,ccStart,edd); 
				}
			}
		}
	}
	
	
	/** Outliers detection
	 * 
	 * Steps are : 
	 * 1) Identify forgotten axes. These are the roots already existing at start. If they went on growing, they are now disconnected from the primary
	 * 2)
	 * 3)
	 * 4)
	 */
	public static void detectObviouslyOutlierOrganAndProlongateIncidentStopped(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,ImagePlus sampleImgForDims,PipelineParamHandler pph,int indexBox) {
		for(int i=0;i<5;i++)System.out.println();
		System.out.println("\n-------------------------------------------------------\nStarting outlier detection, rebranching forgotten branches and that folks in RAG\n-------------------------------------------------------");

		double[]hours=pph.getHours(indexBox);		
		int Xmax=sampleImgForDims.getWidth()-1;
		int Ymax=sampleImgForDims.getHeight()-1;
		int dMax=getDayMax(graph);
		int deltaTimeMax=dMax;
		int []nEach=new int[deltaTimeMax];//This tab will count organs lying in cases by apparition timestep
		int []iter=new int[deltaTimeMax];
		double ratioMinVisible=0.4;//If a root is visible less than 40 % of its path, we reject it
		
		int nLateral=0;
		int nAccepted=1;//Tf ? 0 ?
		int nRejected=1;
		int nTmpTot=0;
		int nTmpKo=0;
		int nTmpOk=0;
		int []warns=new int[] {0,0,0};
		
		//Detect some obviously outlying laterals, and exclude them from the topology
		//Based on the portion of their path that is hidden
		System.out.println("-- Outliers Part1 detect portions hidden and remove corresponding roots" );
		for (CC ccTrunk:graph.vertexSet()) {
			if(ccTrunk.trunk) {
				for(ConnectionEdge edge:graph.outgoingEdgesOf(ccTrunk)) {
					if(edge.activated) {
						CC ccStart=edge.target;
						if(!ccStart.trunk) {//Here is a lateral start
							System.out.println("  -- Part1 processing root starting at "+ccStart);
							if(ccStart.isLateral) {IJ.log("WARNING Part1 : setting lateral an already lateral : "+ccStart);warns[1]++;}
							
							nTmpTot++;
							nLateral++;
							nEach[0]++;

							double lenHid=0;
							double lenVis=0;
							double lengthFromStart=0;
							int surfaceFromStart=ccStart.nPixels;
							int deltaTimeFromStart=0;
							int deltaTimeHoursFromStart=0;

							ccStart.isLatStart=true;
							ccStart.ccLateralStart=ccStart;
							ccStart.pathFromStart=new ArrayList<CC>();
							ccStart.pathFromStart.add(ccStart);
							ccStart.isLateral=true;
							ccStart.surfaceFromStart=surfaceFromStart;
							ccStart.deltaTimeBefore=0;//TODO one day when reviewing MADe exclusion. This should be by bin of delta hours
							ccStart.deltaTimeFromStart=0;
							ccStart.deltaTimeHoursBefore=0;//TODO one day when reviewing MADe exclusion. This should be by bin of delta hours
							ccStart.deltaTimeHoursFromStart=0;
							ccStart.lengthBefore=0;
							ccStart.lengthFromStart=0;
							ccStart.lateralStamp=nLateral;
							
						
						
							//Root traversal to measure some info about it
							CC ccTmp=ccStart;
							CC ccOld;
							while(ccTmp.bestOutgoingActivatedCC()!=null) {
								//Traversal of the current component, and updating hidden lenght or visible length
								ConnectionEdge curEdge=ccTmp.bestOutgoingActivatedEdge();
								if(curEdge.hidden)lenHid+=ccTmp.euclidianDistanceToCC(ccTmp.bestOutgoingActivatedCC());
								else lenVis+=ccTmp.euclidianDistanceToCC(ccTmp.bestOutgoingActivatedCC());
								ccOld=ccTmp;
								ccTmp=ccTmp.bestOutgoingActivatedCC();

								//Eventually detect silly things with trunks
								if(!ccOld.trunk && ccTmp.trunk) {IJ.showMessage("WARNING : lateral "+ccOld+" incoming to trunk "+ccTmp);warns[2]++;}
								ccStart.pathFromStart.add(ccTmp);
								if(ccTmp.trunk) {IJ.log("WARNING 2 : setting lateral a trunk : "+ccTmp);warns[2]++;}
								if(ccTmp.isLateral) {IJ.log("WARNING 2 : setting lateral an already lateral : "+ccTmp);warns[1]++;}

								//Propagate belonging info from the ccStart
								ccTmp.isLateral=true;
								ccTmp.ccLateralStart=ccStart;
								ccTmp.lateralStamp=ccStart.lateralStamp;

								//Count the distance, time and time in hours from the original root
								ccTmp.lengthBefore=ccTmp.euclidianDistanceToCC(ccOld);
								ccTmp.deltaTimeBefore=ccTmp.day-ccOld.day;
								ccTmp.deltaTimeHoursBefore=ccTmp.hour-ccOld.hour;

								lengthFromStart+=ccTmp.lengthBefore;
								deltaTimeFromStart+=ccTmp.deltaTimeBefore;
								deltaTimeHoursFromStart+=ccTmp.deltaTimeBefore;
								surfaceFromStart+=ccTmp.nPixels;
								
								ccTmp.lengthFromStart=lengthFromStart;
								ccTmp.deltaTimeFromStart=deltaTimeFromStart;
								ccTmp.deltaTimeHoursFromStart=deltaTimeHoursFromStart;
								ccTmp.surfaceFromStart=surfaceFromStart;

	//							int tmp=(int) (deltaTimeHoursFromStart/pph.typicalHourDelay);
//								if(tmp>nEach.length-1)tmp=nEach.length-1;
								//nEach[tmp]++;
								
								//Say that there is one more component of lateral in this bin
								nEach[deltaTimeFromStart]++;
								
								
							}
							
							//Declare not valid lateral start if the ratio is not good
							if(lenVis/(lenVis+lenHid)<ratioMinVisible) {
								ccStart.nonValidLatStart=true;
								nTmpKo++;
							}
							else {nTmpOk++;}
						}
					}
				}
			}
		}
		System.out.println("After part 1 : summary. TotLatRootsProcessed="+nTmpTot+" , ko="+nTmpKo+" ok="+nTmpOk);
		System.out.println("Warns were : "+warns[0]+" "+warns[1]+" "+warns[2]);
		warns=new int[] {0,0,0};nTmpKo=0;nTmpOk=0;nTmpTot=0;

		
		//Set a rejection flag to all incident and outgoing arcs of component that are not stamped 1 trunk or 2 lateral
		for (CC cc:graph.vertexSet()){
			nTmpTot++;
			if(!cc.trunk && !cc.isLateral) {cc.setOut();nTmpKo++;}
		}
		System.out.println("Number of set out's = "+nTmpKo+" over "+nTmpTot);
		nTmpKo=0;nTmpTot=0;
		
		
		
		//Compute outlier exclusion based on double sided MAD-e
		System.out.println("-- Outliers Part2 compute median average deviation and process components based on this" );
		int nExcludeCosOnlyOneNode=0;
		int nExcludeCosRootTipTooHighInTheImage=0;
		int nExcludeCosDieEarlyAndIsShort=0;
		int nExcludeBeforeStartingStats=0;

		ArrayList<CC>listStart=new ArrayList<CC>();
		for (CC ccStart:graph.vertexSet()) if(ccStart.isLatStart ) listStart.add(ccStart);
		for (CC ccStart:listStart) {
			CC ccLast=ccStart.pathFromStart.get(ccStart.pathFromStart.size()-1);				
			nTmpTot++;
			boolean debug=false && ccStart.day==11 && ccStart.n==20;
			String rejectionCause="";
			rejectionCause+="\n\n ** Computing outlier exclusion on lateral starting at "+ccStart+"\n";
			if(ccStart.nonValidLatStart) {
				nExcludeBeforeStartingStats++;
				rejectionCause+=" Rejected in part 1 ! ";
				continue;
			}
			
			//Testing a potential lonely node
			if(ccStart.pathFromStart.size()==1) {
				ccStart.nonValidLatStart=true;
				nExcludeCosOnlyOneNode++;
				rejectionCause+=" Have a single node ! ";
				continue;
			}

			//Testing a potential weird root at top of tissue that goes to the top
			if(ccLast.y<minFinalDepthForAcceptingLatRoot /*&& (ccStart.pathFromStart.get(ccStart.pathFromStart.size()-1).y<ccStart.y)*/) {
				ccStart.nonValidLatStart=true;
				nExcludeCosRootTipTooHighInTheImage++;
				rejectionCause+=" Finish too high in the image ! "+ccLast.y;
				continue;
			}

			
			/*Testing a potential false start of lateral based on the alternance left/right
			for(CC ccs:listStart) {
				if(ccs==ccStart || ccStart.nonValidLatStart)continue;//Not anymore active. In fact this have no meaning
				if(ccs.goesToTheLeft<2 && ccStart.goesToTheLeft<2 && ccs.goesToTheLeft!=ccStart.goesToTheLeft)continue;
				if(ccs.euclidianDistanceToCC(ccStart)<=pph.getMinDistanceBetweenLateralInitiation()) {
					int curSurf=ccStart.pathFromStart.get(ccStart.pathFromStart.size()-1).surfaceFromStart;
					int otherSurf=ccs.pathFromStart.get(ccs.pathFromStart.size()-1).surfaceFromStart;
					if(otherSurf>=curSurf && curSurf<pph.getMinLateralStuckedToOtherLateral()) {
						ccStart.nonValidLatStart=true;
						
						rejectionCause+=" this goes "+ccStart.goesToTheLeft+" is starting at proximity of a bigger root that is \n"+ccs+" with goes="+ccs.goesToTheLeft;
					}
				}
			}
			*/

			//			Is this lateral died, having no footprint on the last day and being only one component long ?					
			if(ccLast.day<(dMax)) {
				if(ccStart.pathFromStart.size()<2) {
					nExcludeCosDieEarlyAndIsShort++;
					rejectionCause+="Ending too early (have no dayMax footprint), and is only one component long";
					ccStart.nonValidLatStart=true;
					continue;
				}
			}
				

			//The same, but can the end of the root being hidden under another lateral ? We check the global topological context
			if(ccLast.day<(dMax)) {
				boolean couldBeIncident=false;
				if(debug)System.out.println("Debug : setting false incidence");
				int nAddFirst=0;
				int nAddSecond=0;

				//Step 3-1 : Test if it hides below a root that was there in first. Check neighbouring comp. before (c1), if from another branch, and their parent and son (c2).
				ArrayList<CC>incidencialCC=new ArrayList<CC>();
				ArrayList<Double>incidencialCost=new ArrayList<Double>();
				ArrayList<CC>ccToTest=new ArrayList<CC>();
				for(ConnectionEdge edge1 : graph.incomingEdgesOf(ccLast)) {
					CC c1=edge1.source;//A CC neighbour of ccLast that have its apparition day lower (been there in first)
					if(c1.isLateral && c1.lateralStamp==ccLast.lateralStamp)continue;
					if(c1.isLateral && c1.lateralStamp != ccLast.lateralStamp) ccToTest.add(c1);
					
					for(ConnectionEdge edge2 : graph.incomingEdgesOf(c1)) {
						CC c2=edge2.source;
						if(c2.isLateral && c2.lateralStamp==ccLast.lateralStamp)continue;
						if(c2.isLateral && c2.lateralStamp != ccLast.lateralStamp) ccToTest.add(c2);
					}
					for(ConnectionEdge edge2 : graph.outgoingEdgesOf(c1)) {
						CC c2=edge2.target;
						if(c2.isLateral && c2.lateralStamp==ccLast.lateralStamp)continue;
						if(c2.isLateral && c2.lateralStamp != ccLast.lateralStamp) ccToTest.add(c2);
					}
				}
				System.out.println("After 3-1, ccToTest len="+ccToTest.size());
				
				
				//Step 3-2 : Test if it hides below a root that arrived at the same time. Check neighbouring comp. after (c1), if from another branch, and their parent and son (c2).
				for(ConnectionEdge edge1 : graph.outgoingEdgesOf(ccLast)) {
					CC c1=edge1.target;
					if(c1.isLateral && c1.lateralStamp==ccLast.lateralStamp)continue;
					if(c1.isLateral && c1.lateralStamp != ccLast.lateralStamp) ccToTest.add(c1);
					for(ConnectionEdge edge2 : graph.incomingEdgesOf(c1)) {
						CC c2=edge2.source;
						if(c2.isLateral && c2.lateralStamp==ccLast.lateralStamp)continue;
						if(c2.isLateral && c2.lateralStamp != ccLast.lateralStamp) ccToTest.add(c2);
					}
					for(ConnectionEdge edge2 : graph.outgoingEdgesOf(c1)) {
						CC c2=edge2.target;
						if(c2.isLateral && c2.lateralStamp==ccLast.lateralStamp)continue;
						if(c2.isLateral && c2.lateralStamp != ccLast.lateralStamp) ccToTest.add(c2);
					}
				}
				System.out.println("After 3-2, ccToTest len="+ccToTest.size());

				
				//Step 3-3 : FOr all possible collected CC to test, compute the cost, based on orientation, and reject if it is waaay too long. If not, add it to list incidencial 
				for(CC ccTest : ccToTest) {
					double cost=-prodScal(ccLast.bestIncomingActivatedCC(), ccLast, ccLast, ccTest);
					if(ccLast.euclidianDistanceToCC(ccTest)>2*pph.getMaxSpeedLateral())continue;
					incidencialCC.add(ccTest);
					incidencialCost.add(cost);
				}
				couldBeIncident=incidencialCC.size()>0;
				
				//Reject the possible incident that could not be incident, and which are smaller than 3*average root speed
				if(true || (! couldBeIncident)) {//TODO : incidence lookup desactivated
					if(ccLast.lengthFromStart<(3*pph.getMeanSpeedLateral())) {
						ccStart.nonValidLatStart=true;
						rejectionCause+=" reject cause ending too early, and too small (3*pph.meanSpeed)";
					}
				}
				else {//Possible Incidence, deactivated for the moment
					//Find the best possible incidence, and check if it is concluding
					CC bestIncidenceCC=null;
					double lowerCost=1000000;
					for(int i=0;i<incidencialCost.size();i++) {
						if(incidencialCost.get(i)<lowerCost) {
							bestIncidenceCC=incidencialCC.get(i);
							lowerCost=incidencialCost.get(i);
						}
					}
				
					//Locate if at left or right side of growing incidence
					double []v1=new double[] {bestIncidenceCC.x-bestIncidenceCC.bestIncomingActivatedCC().x, -bestIncidenceCC.y+bestIncidenceCC.bestIncomingActivatedCC().y, 0};
					double []v2=new double[] {bestIncidenceCC.x-ccLast.x,-bestIncidenceCC.y+ccLast.y,0};
					double []z=new double[] {0,0,1};
					double []toLeft=TransformUtils.vectorialProduct(z, v1);
					toLeft=TransformUtils.normalize(toLeft);
					double sign=TransformUtils.scalarProduct(toLeft,v2);
					boolean arrivesFromLeft=(sign<0);//Inverted because y axis points to the bottom

					//Make best estimation possible of speed
					double dt=ccLast.hour-ccLast.bestIncomingActivatedCC().bestIncomingActivatedCC().hour;
					if(dt<=0)dt=0.7*pph.typicalHourDelay;
					double dl=(ccLast.lengthBefore+ccLast.bestIncomingActivatedCC().lengthBefore);
					double speed=dl/dt;
					
					//Estimate expected length in more
					double additionalLenExpected=speed*(hours[dMax-1]-hours[ccLast.day]+0.5*(pph.typicalHourDelay));
					double wayStill=additionalLenExpected;
					CC lastIncidenceCC=bestIncidenceCC.ccLateralStart.pathFromStart.get(bestIncidenceCC.ccLateralStart.pathFromStart.size()-1);
					CC ccToAdd=null;
					
					
					//Estimate position
					if(wayStill<=ccLast.euclidianDistanceToCC(bestIncidenceCC)){
						//If before join
						double []v=new double[] {bestIncidenceCC.x-ccLast.x,bestIncidenceCC.y-ccLast.y,0};
						v=TransformUtils.normalize(v);
						if(debug)System.out.println("Vect directeur="+v[0]+" "+v[1]);
						v=TransformUtils.multiplyVector(v, wayStill);
						Rectangle r=new Rectangle((int)Math.min(Xmax,Math.max(0, (ccLast.x+v[0]))),(int)Math.min(Ymax,Math.max(0, (ccLast.y+v[1]))),1,1);
						ccToAdd=new CC(dMax,hours[dMax-1],maxCCIndexOfDay(graph, dMax),new Roi(r),graph);
					}
					else if(wayStill>=ccLast.euclidianDistanceToCC(bestIncidenceCC)-bestIncidenceCC.lengthFromStart+lastIncidenceCC.lengthFromStart){
						wayStill-=(ccLast.euclidianDistanceToCC(bestIncidenceCC)-bestIncidenceCC.lengthFromStart+lastIncidenceCC.lengthFromStart);
						//If longer than incidence one
						double[]vectDir=new double[] {lastIncidenceCC.x-lastIncidenceCC.bestIncomingActivatedCC().x,lastIncidenceCC.y-lastIncidenceCC.bestIncomingActivatedCC().y,0};
						vectDir=TransformUtils.normalize(vectDir);
						double []vectRab=TransformUtils.multiplyVector(vectDir, wayStill);
						double[]vectToNewLat;
						if(arrivesFromLeft)vectToNewLat=toLeft;
						else vectToNewLat=TransformUtils.multiplyVector(toLeft, -1);
						vectToNewLat[1]=-vectToNewLat[1];
						double estimateRay=lastIncidenceCC.nPixels*2.0/lastIncidenceCC.lengthBefore;
						if(estimateRay>3 || estimateRay<=0)estimateRay=3;
						vectToNewLat=TransformUtils.multiplyVector(vectToNewLat, estimateRay);
						vectToNewLat=TransformUtils.vectorialAddition(vectToNewLat, vectRab);
						Rectangle r=new Rectangle((int)Math.min(Xmax,Math.max(0, (lastIncidenceCC.x+vectToNewLat[0]))),(int)Math.min(Ymax,Math.max(0, (lastIncidenceCC.y+vectToNewLat[1]))),1,1);
						ccToAdd=new CC(dMax,hours[dMax-1],maxCCIndexOfDay(graph, dMax),new Roi(r),graph);
					}
					else {
						//A point somewhere between bestIncidenceCC and lastIncidenceCC
						//Determine before and after
						CC ccBef=null;CC ccAft=null;
						CC ccTmp=bestIncidenceCC;
						CC ccOld=bestIncidenceCC;
						while(ccBef==null) {
							ccOld=ccTmp;
							ccTmp=ccTmp.bestOutgoingActivatedCC();
							double lenBef=ccLast.euclidianDistanceToCC(bestIncidenceCC)-bestIncidenceCC.lengthFromStart+ccOld.lengthFromStart;
							double lenAft=ccLast.euclidianDistanceToCC(bestIncidenceCC)-bestIncidenceCC.lengthFromStart+ccTmp.lengthFromStart;
							if(wayStill<lenAft && wayStill>lenBef) {
								ccBef=ccOld;
								ccAft=ccTmp;
							}
						}
						wayStill-=(ccLast.euclidianDistanceToCC(bestIncidenceCC)-bestIncidenceCC.lengthFromStart+ccBef.lengthFromStart);
						double[]vectDir=new double[] {ccAft.x-ccBef.x,ccAft.y-ccBef.y,0};
						vectDir=TransformUtils.normalize(vectDir);
						double[]vectPos=TransformUtils.multiplyVector(vectDir, wayStill);
						vectPos=TransformUtils.vectorialAddition(vectPos,new double[] {ccBef.x,ccBef.y,0});
						double[]vectToNewLat;
						if(arrivesFromLeft)vectToNewLat=toLeft;
						else vectToNewLat=TransformUtils.multiplyVector(toLeft, -1);
						vectToNewLat[1]=-vectToNewLat[1];
						double estimateRay=ccBef.nPixels/(2.0*ccBef.lengthBefore);
						if(estimateRay>3 || estimateRay<=0)estimateRay=3;
						vectToNewLat=TransformUtils.multiplyVector(vectToNewLat, estimateRay);
						vectPos=TransformUtils.vectorialAddition(vectPos, vectToNewLat);
						Rectangle r=new Rectangle((int)Math.min(Xmax,Math.max(0, (vectPos[0]))),(int)Math.min(Ymax,Math.max(0, (vectPos[1]))),1,1);
						ccToAdd=new CC(dMax,hours[dMax-1],maxCCIndexOfDay(graph, dMax),new Roi(r),graph);
					}
					//Actualize it.
					graph.addVertex(ccToAdd);
					ccToAdd.ccLateralStart=ccLast.ccLateralStart;
					ccLast.ccLateralStart.pathFromStart.add(ccToAdd);
					ConnectionEdge edge=new ConnectionEdge(ccLast.x*0.5+ccToAdd.x*0.5, ccLast.y*0.5+ccToAdd.y*0.5, 0, ccLast, ccToAdd,0 ,0 );
					edge.activated=true;
					edge.hidden=true;
					graph.addEdge(ccLast, ccToAdd,edge);
				}
			}
		}
	}
	
	
	public static void detectSubtleOutliersBasedOnStatistics(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,ImagePlus sampleImgForDims,PipelineParamHandler pph,int indexBox) {
		for(int i=0;i<5;i++)System.out.println();
		System.out.println("\n-------------------------------------------------------\nStarting outlier detection based on statistics\n-------------------------------------------------------");

		double[]hours=pph.getHours(indexBox);		
		int Xmax=sampleImgForDims.getWidth()-1;
		int Ymax=sampleImgForDims.getHeight()-1;
		double nbMADforOutlierRejection=25;//Number of median absolute deviation around the median that is considered outlying
		int dMax=getDayMax(graph);
		int deltaTimeMax=dMax;
		int []nEach=new int[deltaTimeMax];//This tab will count organs lying in cases by apparition timestep
		int []iter=new int[deltaTimeMax];
		double ratioMinVisible=0.4;//If a root is visible less than 40 % of its path, we reject it
		
		int nLateral=0;
		int nAccepted=1;//Tf ? 0 ?
		int nRejected=1;
		int nTmpTot=0;
		int nTmpKo=0;
		int nTmpOk=0;
		int []warns=new int[] {0,0,0};
		ArrayList<CC>listStart=new ArrayList<CC>();
		for (CC ccStart:graph.vertexSet()) if(ccStart.isLatStart && (!ccStart.nonValidLatStart)) listStart.add(ccStart);

		
		//Collect data for computing statistics over nodes, binned according to time latence from lateral initiation
		System.out.println("\n\n-- Outliers Part3 collect statistics over nodes" );
		//Prepare data structures to collect population values
		for(CC ccStart : listStart ) {				
			for(CC cc : ccStart.pathFromStart ) {				
				int nCur=(int) (cc.deltaTimeFromStart);
				nEach[nCur]++;
			}
		}
		for(int i=0;i<deltaTimeMax;i++) iter[i]=nEach[i];
		double[][][]data=new double[6][deltaTimeMax+1][];//collected data for every node : lenbefore,lentotal,speedbef,speedtot,surfthis,surftot
		double[][][]stats=new double[6][deltaTimeMax+1][3];//lenbef,lentot,speedbef,speedtot,surfthis,surftot
		for(int i=0;i<6;i++)for(int j=0;j<deltaTimeMax;j++)data[i][j]=new double[nEach[j]];		

		
		//Effectively read data along the root path
		for(CC ccStart : listStart ) {				
			for(CC cc : ccStart.pathFromStart ) {				
				int nCur=(int) (cc.deltaTimeFromStart);
				data[0][nCur][iter[nCur]-1]=cc.lengthBefore;
				data[1][nCur][iter[nCur]-1]=cc.lengthFromStart;
				data[2][nCur][iter[nCur]-1]=cc.lengthBefore/(Math.max(0.7*pph.typicalHourDelay,cc.deltaTimeHoursBefore));
				data[3][nCur][iter[nCur]-1]=cc.lengthFromStart/(Math.max(0.7*pph.typicalHourDelay,cc.deltaTimeHoursFromStart));//Security in case there is no delta (=0)
				data[4][nCur][iter[nCur]-1]=cc.nPixels;
				data[5][nCur][iter[nCur]-1]=cc.surfaceFromStart;
				iter[nCur]--;
			}
		}
			
		//Compute the Mad-e statistics
		for(int i=0;i<6;i++)for(int j=0;j<deltaTimeMax;j++) {
			if(nEach[j]==0)continue;//No object in this bin
			stats[i][j]=VitimageUtils.MADeStatsDoubleSided(data[i][j], null);	
			if(stats[i][j][1]==stats[i][j][0])stats[i][j][1]=stats[i][j][0]-VitimageUtils.EPSILON;
			if(stats[i][j][2]==stats[i][j][0])stats[i][j][2]=stats[i][j][0]+VitimageUtils.EPSILON;
		}
		double[]tempVals=new double[6];
		double[]tempStd=new double[6];
		for (CC ccStart:listStart) {
			CC ccLast=ccStart.pathFromStart.get(ccStart.pathFromStart.size()-1);				
			nTmpTot++;
			boolean debug=false && ccStart.day==11 && ccStart.n==20;
			String rejectionCause="";
			rejectionCause+="\n\n ** Computing outlier exclusion on lateral starting at "+ccStart+"\n";
			
			//Testing statistics
			for(CC cc : ccStart.pathFromStart ) {				
				tempVals=new double[] {
						cc.lengthBefore,
						cc.lengthFromStart,
						cc.lengthBefore/(Math.max(0.7*pph.typicalHourDelay,cc.deltaTimeHoursBefore)),
						cc.lengthFromStart/(Math.max(0.7*pph.typicalHourDelay,cc.deltaTimeHoursFromStart)),
						cc.nPixels,
						cc.surfaceFromStart};
				
				int nCur= (cc.deltaTimeFromStart);
			
				rejectionCause+="Stats at node "+nCur+" "+cc.x+","+cc.y+" : ";
				for(int i=0;i<6;i++) {
					if(nCur<2 || cc==ccLast || i==0 || i==2) {//TODO  check these exclusion cases. Could be good to make some statistics over
						tempStd[i]=-1;
						continue;
					}
					if(stats[i][nCur][0]>tempVals[i])tempStd[i]=-1;
					else tempStd[i]= (tempVals[i]-stats[i][nCur][0])/(stats[i][nCur][2]-stats[i][nCur][0]);//Compute the number of median average dev. upon the median
				}
				for(int i=0;i<6;i++)rejectionCause+=(" ["+tempStd[i]+"] ");
				rejectionCause+="\n";
				for(int i=0;i<6;i++) {
					if(tempStd[i]>=nbMADforOutlierRejection) {
						rejectionCause+=("\n Rejection at node "+nCur+" for criterion "+i+" with nb MADe= "+tempStd[i]);
						ccStart.nonValidLatStart=true;
					}
				}
			}					
			if(ccStart.nonValidLatStart) {
				nRejected++;
				System.out.println(rejectionCause);
			}
			else nAccepted++;
		}
		for (CC ccStart:graph.vertexSet()) {
			if(ccStart.isLatStart) {
				if(ccStart.nonValidLatStart)for(CC c:ccStart.pathFromStart)c.setOut();// tabDel.add(ccStart);
			}
		}
		System.out.println("\n\nEnd of statistics MADE.");
		System.out.println("Accepted="+nAccepted);
		System.out.println("Rejected="+nRejected+"\n\n");
	}
						

	
	//Evaluating the reconnexion of ccStop and ccStart, two secondary nodes
	public static double weightingOfPossibleHiddenEdge_v2(ImagePlus img,SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,CC ccStop,CC ccStart,PipelineParamHandler pph,boolean debug) {
		//if(ccStop.day==17 && ccStart.day==17 && ccStop.n==54 && ccStart.n==66)debug=true;
		if(ccStop.day>ccStart.day)return PENALTY_COST;
		if(ccStop==ccStart)return PENALTY_COST;
		if(ccStop.bestIncomingActivatedCC()==ccStart)return PENALTY_COST;
		if(ccStart.bestIncomingActivatedCC()==ccStop)return PENALTY_COST;
		int nStop=1;
		int nStart=1;
		CC ccPrec=null;
		CC ccSuiv=null;
		if(debug) {
			System.out.println("DEBUG IN WEIGHTING OF POSSIBLE");
			System.out.println("Ccstop="+ccStop);
			System.out.println("Ccstart="+ccStart);
		}
		
		
		if(ccStart.bestOutgoingActivatedCC()==null) {}
		else {
			ccSuiv=ccStart.bestOutgoingActivatedCC();
			nStart=2;
			if(ccSuiv.bestOutgoingActivatedCC()==null) {}
			else {
				ccSuiv=ccSuiv.bestOutgoingActivatedCC();
				nStart=3;
			}
		}
		if(debug)System.out.println("Nstart="+nStart);

		if(ccStop.bestIncomingActivatedCC()==null) {}
		else {
			ccPrec=ccStop.bestIncomingActivatedCC();
			nStop=2;
			if(ccPrec.bestIncomingActivatedCC()==null) {}
			else {
				ccPrec=ccPrec.bestIncomingActivatedCC();
				nStop=3;
			}
		}
		if(debug)System.out.println("Nstop="+nStop);

		double valWstop=(nStop==3 ? 1 : (nStop==2 ? 0.5 : 1));
		double valWstart=(nStart==3 ? 1 : (nStart==2 ? 0.5 : 1));
		double[]tabW=new double[] {valWstop,valWstart,valWstop,valWstart,1,1,1,1,1,1};
		double[]tabGamma=new double[10];
		

		//Angle score
		if(nStop>1)  tabGamma[0]=2*(1-prodScal(ccPrec, ccStop, ccStop, ccStart));
		else tabGamma[0]=0.5;
		if(nStart>1)tabGamma[1]=2*(1-prodScal(ccStop, ccStart, ccStart, ccSuiv));
		else tabGamma[1]=0.5;
		if(Double.isNaN(tabGamma[0]))tabGamma[0]=0.5;
		if(Double.isNaN(tabGamma[1]))tabGamma[1]=0.5;
		
		
		
		//Speed score
		double dtCrossDay=ccStart.day-ccStop.day;//TODO : This should be delta hours
		if(dtCrossDay==0)dtCrossDay=0.7;
		double dtCross=ccStart.hour-ccStop.hour;//TODO : This should be delta hours
		if(dtCross==0) {
			int ind=0;
			if(ccStop.bestIncomingActivatedCC()!=null) {ind++;dtCross+=ccStop.hour-ccStop.bestIncomingActivatedCC().hour;}
			if(ccStart.bestOutgoingActivatedCC()!=null) {ind++;dtCross+=ccStart.bestOutgoingActivatedCC().hour-ccStart.hour;}
			if(ind==0 || dtCross<=0)dtCross=pph.typicalHourDelay;
			else dtCross=dtCross/ind;
		}
		double crossSpeed=ccStop.euclidianDistanceToCC(ccStart)/dtCross;
		double dtStop=0;
		double stopSpeed=0;
		double dtStart=0;
		double startSpeed=0;
		if(nStop>1) {
			dtStop=ccStop.hour-ccPrec.hour;
			if(dtStop==0) {
				int ind=0;
				if(ccPrec.bestIncomingActivatedCC()!=null) {ind++;dtStop+=ccStop.hour-ccStop.bestIncomingActivatedCC().hour;}
				if(ind==0 || dtStop<=0)dtStop=pph.typicalHourDelay;
			}
			stopSpeed=ccStop.euclidianDistanceToCC(ccPrec)/dtStop;
			tabGamma[2]=1-VitimageUtils.similarity(stopSpeed,crossSpeed);
		}
		else tabGamma[2]=0.5;
		
		if(nStart>1) {
			dtStart=ccSuiv.hour-ccStart.hour;
			if(dtStart<=0) {
				int ind=0;
				if(ccSuiv.bestOutgoingActivatedCC()!=null) {ind++;dtStart+=ccSuiv.bestOutgoingActivatedCC().hour-ccSuiv.hour;}
				if(ind==0 || dtStart<=0)dtStart=pph.typicalHourDelay;
			}
			startSpeed=ccStart.euclidianDistanceToCC(ccSuiv)/dtStart;
			tabGamma[3]=1-VitimageUtils.similarity(startSpeed,crossSpeed);
		}
		else tabGamma[3]=0.5;
		
		
		//Orientation score
		tabGamma[4]=(1-0.5*orientationDownwards(ccStop, ccStart));
		if(Double.isNaN(tabGamma[4]))tabGamma[4]=1;
		
		//Connection score
		int nbSteps=areConnectedByPathOfCC_v2(graph,ccStop,ccStart,pph,debug);
		int lNorm=3;
		tabGamma[5]=(1.0/lNorm)*Math.abs(nbSteps-dtCrossDay-2.5)+(dtCrossDay<2 ? 0 : 0.15*(dtCrossDay-1));
		
		//Glob Orientation score
		if(nStop==1 || nStart==1) {tabGamma[6]=0.5;tabGamma[7]=0.5;tabW[6]=0.5;tabW[7]=0.5;}
		else {
			if(nStop==3 && nStart==3) {tabW[6]=1.0;tabW[7]=1.0;}
			else {tabW[6]=0.75;tabW[7]=0.75;}
			tabGamma[6]= (1-prodScal(ccPrec, ccStop, ccStart, ccSuiv));
			tabGamma[7]=1-VitimageUtils.similarity(startSpeed,stopSpeed);
		}

		tabGamma[8]=costDistanceOfPathToObject(img,ccStop.x ,ccStop.y,ccStart.x,ccStart.y);
		tabGamma[9]=0.5;
		double maxSpeedLat=pph.getMaxSpeedLateral()/pph.typicalHourDelay;
		if(  ((ccStop.euclidianDistanceToCC(ccStart))/(dtCross)) >maxSpeedLat) {
			tabGamma[9]+=5*(  ((ccStop.euclidianDistanceToCC(ccStart))/(dtCross))-maxSpeedLat)/maxSpeedLat;
		}
		
		
		double globScore=0;
		double globWeight=0;

//10 si nPixels=1 , 7 si ça vaut 2 , 5 si ça vaut 3, 4 si ça vaut 4, 3 si ça vaut 5, 		
		for(int i=0;i<10;i++) {
			if(debug)System.out.println("Score "+i+"="+tabGamma[i]+" weight="+tabW[i]);
			if(Double.isNaN( tabGamma[i]))tabGamma[i]=1;
			globScore+=(tabGamma[i]*tabW[i]);
			globWeight+=tabW[i];
		}
		if(debug)System.out.println("Result="+(globScore/globWeight));
		return globScore/globWeight;
	}
	
	
	public static double getCostOfAlignmentOfLastPointGivenTwoFirst(CC cc1,CC cc2,CC cc3,boolean debug) {
		double delta12Day=Math.abs(cc1.hour-cc2.hour);//TODO : using real time ?
		double delta23Day=Math.abs(cc3.hour-cc2.hour);//TODO : using real time ?
		if(delta12Day==0)delta12Day=1;
		if(delta23Day==0)delta23Day=1;
		double[]vect12=new double[] {cc2.r.getContourCentroid()[0]-cc1.r.getContourCentroid()[0],cc2.r.getContourCentroid()[1]-cc1.r.getContourCentroid()[1],0};
		double[]vect12PerDay=TransformUtils.multiplyVector(vect12, 1/delta12Day);
		double distPerDay=TransformUtils.norm(vect12PerDay);
		double xExpected=cc2.r.getContourCentroid()[0]+vect12PerDay[0]*delta23Day;
		double yExpected=cc2.r.getContourCentroid()[1]+vect12PerDay[1]*delta23Day;
		double xReel=cc3.r.getContourCentroid()[0];
		double yReel=cc3.r.getContourCentroid()[1];
		double relativeDistInDayPerDay=VitimageUtils.distance(xReel, yReel, xExpected, yExpected)/(distPerDay*delta23Day);//TODO : using real time ?
		return relativeDistInDayPerDay;
	}

	public static void computeMinimumDirectedConnectedSpanningTree(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph) {		
		System.out.print("Computing connected directed minimum spanning tree");
		CC cctest=null;
		boolean debug=false;
		if(debug) {
			cctest=getCCWithResolution(graph,2192,1820,6);//To test : 2192 1820 7   2240 2010 7       2231 1494 9
			System.out.println("DEBUG in MIN SPANNING TREE 0");
			System.out.println(cctest);
			System.out.println("INCOMING 1");
			for(ConnectionEdge edge : graph.incomingEdgesOf(cctest)) {
				System.out.println(edge);
			}
			System.out.println("DEBUG in MIN SPANNING TREE 1");
		}
		
		//Initialize connexe traversal from the virtual start (day=0)
		int maxDay=0;int currentCC=1;
		for(CC cc:graph.vertexSet()) {
			cc.stamp=( (cc.day==0) ? currentCC : 0);
			if(cc.day>maxDay)maxDay=cc.day;
		}

		
		
		//From day to day, select for each CC the best supplying edge
		for(int i=1;i<=maxDay;i++) {
			for(CC cc:graph.vertexSet()) {
				if(cc.day!=i)continue;
				if(cc.trunk)continue;
				boolean debugCC=(cc==cctest);
				ConnectionEdge edgeMin=null;
				double minCost=1E16;
				double minCostConnected=1E16;
				if(debugCC)System.out.println("DEBUG in MIN SPANNING TREE 2 Processing "+cc);
				for(ConnectionEdge edge:graph.incomingEdgesOf(cc)) {
					if((graph.getEdgeWeight(edge)<minCostConnected) && (graph.getEdgeSource(edge).stamp==1) /*&& ((cc.day==(graph.getEdgeSource(edge).day+1))  || (graph.getEdgeSource(edge).trunk))*/) {
						minCostConnected=graph.getEdgeWeight(edge);
					}
					if((graph.getEdgeWeight(edge)<minCost)/* && ((cc.day==(graph.getEdgeSource(edge).day+1)) || (graph.getEdgeSource(edge).trunk))*/)  {
						minCost=graph.getEdgeWeight(edge);
						edgeMin=edge;
					}
				}
				if(debugCC)System.out.println("DEBUG in MIN SPANNING TREE 3 Selected edgeMin="+edgeMin);
				//if(edgeMinConnected!=null) {
				//	if(debug)System.out.println("H1, with edgeMinCon="+edgeMinConnected);
				//	for(ConnectionEdge edge:graph.incomingEdgesOf(cc))edge.activated=(edge==edgeMinConnected);
				//	cc.stamp=1;
				//	if(debug)System.out.println("H11, with edgeMinCon="+edgeMinConnected);
				//}
				if(edgeMin!=null) {
					if(debugCC)System.out.println("DEBUG in MIN SPANNING TREE 31 first");
					for(ConnectionEdge edge:graph.incomingEdgesOf(cc))edge.activated=(edge==edgeMin);
					cc.stamp=graph.getEdgeSource(edgeMin).stamp;
				}
				else {
					if(debugCC)System.out.println("DEBUG in MIN SPANNING TREE 31 second");
					for(ConnectionEdge edge:graph.incomingEdgesOf(cc))edge.activated=false;
					cc.stamp=(++currentCC);
				}
				if(debugCC) {
					System.out.println("\nINCOMING 2");
					for(ConnectionEdge edge : graph.incomingEdgesOf(cctest)) {
						System.out.println(edge);
					}
				}

				
			}
		}				
		if(debug) {
			for(ConnectionEdge edge : graph.outgoingEdgesOf(cctest)) {
//			System.out.println("DEBUG in MIN SPANNING TREE 4 outgoing edge="+edge);
			}
		}
		
		updateCostAndDisconnectNonOptimalLateralBranches_V2(graph);
		if(debug) {
			for(ConnectionEdge edge : graph.outgoingEdgesOf(cctest)) {
				System.out.println(edge);
			}
		}
		System.out.println("\n -> End of minimum directed connected spanning tree. Total number of components = "+currentCC+" (higher than expected nTrees means probably disconnected laterals emerged)");

		if(debug) {
			System.out.println("DEBUG in MIN SPANNING TREE 8");
			System.out.println(cctest);
			System.out.println("\nINCOMING 3");
			for(ConnectionEdge edge : graph.incomingEdgesOf(cctest)) {
				System.out.println(edge);
			}
			System.out.println("DEBUG in MIN SPANNING TREE 9");
		}
	}


	 //Determine if ccStop and ccStart are connected in the undirected region adjacency graph limited to ccStop, ccStart and older CC
	public static int areConnectedByPathOfCC_v2(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,CC ccStop,CC ccStart,PipelineParamHandler pph,boolean debug) {
		int maxIter=30;
		for(CC cc: graph.vertexSet()) cc.stampDist=10000;			
		ccStop.stampDist=0;
		ArrayList<CC>visited=new ArrayList<CC>();
		ArrayList<CC>toVisit=new ArrayList<CC>();
		visited.add(ccStop);
		boolean finished=false;
		int iter=-1;
		while(!finished) {
			iter++;
			//if(debug)System.out.print("iter="+iter+" : "+visited.size());
			for(int i=0;i<visited.size();i++) {				
				CC ccTemp=visited.get(i);
				for(ConnectionEdge edge : graph.outgoingEdgesOf(ccTemp)) {
					CC ccTrial=edge.target;
					double deltaT=ccTrial.hour-ccTemp.hour;
					if(deltaT<0)deltaT=-deltaT;
					if(deltaT==0)deltaT=0.7*pph.typicalHourDelay;
					
					if( ccTrial.day<1)continue;
					if(!ccTrial.trunk && !ccTemp.trunk) {
						if( ccTrial.stampDist> (ccTemp.stampDist+1) ) {
							toVisit.add(ccTrial);
							ccTrial.stampDist=ccTemp.stampDist+1;
						}
					}					
					if(ccTrial.trunk && !ccTemp.trunk) {
						if( ccTrial.stampDist> (ccTemp.stampDist+0) ) {
							toVisit.add(ccTrial);
							ccTrial.stampDist=ccTemp.stampDist+0;
							ccTrial.lastCCinLat=ccTemp;
						}
					}
					if(ccTrial.trunk && ccTemp.trunk) {
						if( ccTrial.stampDist> (ccTemp.stampDist+0) ) {
							toVisit.add(ccTrial);
							ccTrial.stampDist=ccTemp.stampDist+0;
							ccTrial.lastCCinLat=ccTemp.lastCCinLat;
						}
					}
					if(!ccTrial.trunk && ccTemp.trunk) {
						double scoreAdd=ccTrial.euclidianDistanceToCC(ccTemp.lastCCinLat)/(pph.getMeanSpeedLateral()*(deltaT)/pph.typicalHourDelay);//TODO : make it depends to actual delay
						if( ccTrial.stampDist> (ccTemp.stampDist+scoreAdd) ) {
							toVisit.add(ccTrial);
							ccTrial.stampDist=ccTemp.stampDist+scoreAdd;
						}
					}
				}
				for(ConnectionEdge edge : graph.incomingEdgesOf(ccTemp)) {
					CC ccTrial=edge.source;
					double deltaT=ccTrial.hour-ccTemp.hour;
					if(deltaT<0)deltaT=-deltaT;
					if(deltaT==0)deltaT=0.7*pph.typicalHourDelay;
					if( ccTrial.day<1)continue;
					if(!ccTrial.trunk && !ccTemp.trunk) {
						if( ccTrial.stampDist> (ccTemp.stampDist+2) ) {
							toVisit.add(ccTrial);
							ccTrial.stampDist=ccTemp.stampDist+2;
						}
					}					
					if(ccTrial.trunk && !ccTemp.trunk) {
						if( ccTrial.stampDist> (ccTemp.stampDist+0) ) {
							toVisit.add(ccTrial);
							ccTrial.stampDist=ccTemp.stampDist+0;
							ccTrial.lastCCinLat=ccTemp;
						}
					}
					if(ccTrial.trunk && ccTemp.trunk) {
						if( ccTrial.stampDist> (ccTemp.stampDist+0) ) {
							toVisit.add(ccTrial);
							ccTrial.stampDist=ccTemp.stampDist+0;
							ccTrial.lastCCinLat=ccTemp.lastCCinLat;
						}
					}
					if(!ccTrial.trunk && ccTemp.trunk) {
						double scoreAdd=ccTrial.euclidianDistanceToCC(ccTemp.lastCCinLat)/(pph.getMeanSpeedLateral()*(deltaT)/pph.typicalHourDelay);//TODO : make it depends to actual delay
						if( ccTrial.stampDist> (ccTemp.stampDist+scoreAdd) ) {
							toVisit.add(ccTrial);
							ccTrial.stampDist=ccTemp.stampDist+scoreAdd;
						}
					}
				}			
			}
			if(toVisit.size()==0 || iter>maxIter)finished=true;
			visited=toVisit;
			toVisit=new ArrayList<CC>();
		}
		if(debug) {
			if(ccStart.stampDist>=10000)System.out.println("\nPath not found !");
			else {
				System.out.println("\nPath found with stampDist="+ccStart.stampDist);
			}
		}
		if(ccStart.stampDist<10000)return (int) Math.round(ccStart.stampDist);
		else return 30;
	}
	

	
	

	
	/** Every pixel surrounded with data different from zero will be replaced by the most represented value in its surroundings*/
	public static void trickOnImgDates(ImagePlus img) {
		int X=img.getWidth();
		int Y=img.getHeight();
		float[]tabData=(float[]) img.getStack().getPixels(1);
		for(int x=1;x<X-1;x++)for(int y=1;y<Y-1;y++) {
			if(toInt(tabData[y*X+x])!=0)continue;
			int[]vals=new int[] {
					toInt(tabData[ (y-1) *X+ (x-1) ]) , toInt(tabData[ (y-1) *X+ (x) ]) , toInt(tabData[ (y-1) *X+ (x+1) ]) , 
							toInt(tabData[ (y) *X+ (x-1) ]) , 			toInt(tabData[ (y) *X+ (x+1) ]) ,
									toInt(tabData[ (y+1) *X+ (x-1) ]) , toInt(tabData[ (y+1) *X+ (x) ]) , toInt(tabData[ (y+1) *X+ (x+1) ]) };
			int b=1;for(int i=0;i<8;i++)b=b*vals[i];
			if(b==0)continue;
			b=MostRepresentedFilter.mostRepresentedValue(vals, new double[8], 256);
			tabData[y*X+x]=(b);			
		}
	}
	
	public static void pruneGraph(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,int nbTrees,int xMinTree,int xMaxTree,boolean removeUnconnectedParts,double []hours) {
		//* If nbTrees>=1, select nbTrees elements of day 1  
		//* Build element of day 0, and connect all selected elements of day 1
		//* Remove all elements not connected
		//Find border of the tree, and define center
		double minX=10000000;
		double maxX=0;
		for(CC cc : graph.vertexSet()) {
			if(cc.r.getContourCentroid()[0]>maxX)maxX=cc.r.getContourCentroid()[0];
			if(cc.r.getContourCentroid()[0]<minX)minX=cc.r.getContourCentroid()[0];
		}
		
		//Connect roots to a central point
		Roi r=new Roi(new Rectangle((int) ((minX+maxX)/2),10,1,1));
		CC source=new CC(0,hours[0],1,r,graph) ;
		source.stamp=1;
		source.componentLabel=1;
		graph.addVertex(source);		


		//Initialize CC search
		
		int incr=0;
		ArrayList<CC>list=new ArrayList<CC>();
		for(CC cc :graph.vertexSet()) {
			if(cc.day!=0) {
				cc.stamp=0;
				cc.componentLabel=0;			
				if(nbTrees<1) {if(cc.day==1 && cc.nPixels>200) {incr++;list.add(cc);}}
			}
		}
	
		if(nbTrees>=1) { 
			CC[]tabCC=new CC[nbTrees];
			for(int i=0;i<nbTrees;i++) {
				int max=-1;
				for(CC cc :graph.vertexSet()) if(cc.day==1) {
					boolean found=false;				
					for(int j=0;j<nbTrees;j++)if(tabCC[j]==cc)found=true;
					if(found)continue;
					if(cc.nPixels>max && cc.x >= xMinTree && cc.x<=xMaxTree ) {
						max=cc.nPixels;
						tabCC[i]=cc;
					}
				}
				if(tabCC[i]!=null) {incr++;list.add(tabCC[i]);}
			}
		}
		System.out.println("Identified "+incr+" roots systems");
		for(CC cc:list) {
			System.out.println("* Start CC : "+cc);
			graph.addEdge(source, cc,new ConnectionEdge(source.r.getContourCentroid()[0], source.r.getContourCentroid()[1], 1,source, cc,0,0));
		}
		System.out.println();
		
		int nbMov=1;
		while(nbMov>0) {
			nbMov=0;
			for(CC cc:graph.vertexSet()) {
				if(cc.stamp==2) {
					int lab=cc.componentLabel;
					for(ConnectionEdge edge:graph.edgesOf(cc)) {
						if(graph.getEdgeSource(edge).stamp==0) {
							graph.getEdgeSource(edge).stamp=1;
							graph.getEdgeSource(edge).componentLabel=lab;
						}
						if(graph.getEdgeTarget(edge).stamp==0) {
							graph.getEdgeTarget(edge).stamp=1;
							graph.getEdgeTarget(edge).componentLabel=lab;
						}
					}
					cc.stamp=3;
				}
			}
			for(CC cc:graph.vertexSet()) {
				if(cc.stamp==1) {
					nbMov++;
					cc.stamp=2;
				}
			}
		}		
		if(removeUnconnectedParts) {
			//Clean vertices and edges isolated from the root systems
			ArrayList<CC>ar=new ArrayList<CC>();
			for(CC cc:graph.vertexSet()) if(cc.stamp!=3) ar.add(cc);
			for(CC cc : ar)graph.removeVertex(cc);
		}
	}		 
	
	public static ImagePlus getDistanceMapsToDateMaps(ImagePlus img) {
		ImagePlus seedImage=VitimageUtils.thresholdFloatImage(img, 0.5, 10000);
		seedImage.setDisplayRange(0, 1);
		IJ.run(seedImage,"8-bit","");
		ImagePlus segImage=VitimageUtils.thresholdFloatImage(img, -0.5, 0.5);
		segImage.setDisplayRange(0, 1);
		IJ.run(segImage,"8-bit","");
		seedImage=MorphoUtils.dilationCircle2D(seedImage, 1);		
		ImagePlus distance=MorphoUtils.computeGeodesic(seedImage, segImage,false);
		VitimageUtils.makeOperationOnOneImage(distance, 2, 1/1000.0, false);
		return distance;
	}
	

	
	
	
	public static int maxCCIndexOfDay(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph,int d) {
		int max=0;
		for(CC cc:graph.vertexSet())if(cc.day==d && cc.n>max)max=cc.n;
		return max;
	}

	/** Methods for building and improving the graph ------------------------------------------------------------------------------------------------------*/	
	public static boolean buildAndProcessGraphStraight(ImagePlus imgDatesTmp,String outputDataDir,PipelineParamHandler pph,int indexBox) {
		double ray=5;
		int thickness=5;
		int sizeFactor=pph.sizeFactorForGraphRendering;
		int connexity=8;
		int nbTrees=pph.numberPlantsInBox;
		boolean doImages=(pph.memorySaving==0);
		boolean doDebugImages=(pph.memorySaving==0);
		
//		imgDatesTmp.show();
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph=null;
		graph=buildGraphFromDateMap(imgDatesTmp,connexity,pph.getHours(indexBox));
		pruneGraph(graph, nbTrees,pph.xMinTree,pph.xMaxTree,true,pph.getHours(indexBox));
		setFirstOrderCosts_phase1(graph,pph.getHoursExtremities(indexBox));
		int upTen=0;
		int upTwo=0;
		int upOne=0;
		int upZero=0;
		int upMinus1=0;
		int upMinus2=0;
		int upMinus4=0;
		int lower=0;
		for(ConnectionEdge edge :graph.edgeSet()) {
			double cost=graph.getEdgeWeight(edge);
			if(cost>10)upTen++;
			else if(cost>2)upTwo++;
			else if(cost>1)upOne++;
			else if(cost>0)upZero++;
			else if(cost>-1)upMinus1++;
			else if(cost>-2)upMinus2++;
			else if(cost>-4)upMinus4++;
			else lower++;
		}
		System.out.println("upTen="+upTen);
		System.out.println("upTwo="+upTwo);
		System.out.println("upOne="+upOne);
		System.out.println("upZero="+upZero);
		System.out.println("upMinus1="+upMinus1);
		System.out.println("upMinus2="+upMinus2);
		System.out.println("upMinus4="+upMinus4);
		System.out.println("lower="+lower);
		
		identifyTrunks(graph);
		setFirstOrderCosts_phase2(graph,pph.getHoursExtremities(indexBox));
		if(doDebugImages) writeGraphToFile(graph,new File(outputDataDir,"50_graph_step_3.ser").getAbsolutePath());	
		
		computeMinimumDirectedConnectedSpanningTree(graph);

		if(doDebugImages) writeGraphToFile(graph,new File(outputDataDir,"50_graph_step_4.ser").getAbsolutePath());
		reconnectDisconnectedBranches_v2(imgDatesTmp,graph,pph,1,true,false);

		if(doDebugImages) writeGraphToFile(graph,new File(outputDataDir,"50_graph_step_5.ser").getAbsolutePath());
		postProcessTopology(graph,imgDatesTmp,pph,indexBox);
		if(doDebugImages) writeGraphToFile(graph,new File(outputDataDir,"50_graph_step_6.ser").getAbsolutePath());
		if(doDebugImages) writeGraphToFile(graph,new File(outputDataDir,"50_graph.ser").getAbsolutePath());

		if(doImages) {
			int nDays=1+(int)Math.round(VitimageUtils.maxOfImage(imgDatesTmp));
			ImagePlus imgDatesHigh=VitimageUtils.convertFloatToByteWithoutDynamicChanges(imgDatesTmp);
			IJ.run(imgDatesHigh,"Fire","");
			imgDatesHigh.setDisplayRange(0, nDays);		
			imgDatesHigh=VitimageUtils.resizeNearest(imgDatesTmp, imgDatesTmp.getWidth()*sizeFactor,imgDatesTmp.getHeight()*sizeFactor,1);
			ImagePlus dates;
			ImagePlus graphs;
			if(doDebugImages) {
				ImagePlus[]graphsImgs=new ImagePlus[4];
				ImagePlus[]backImgs=new ImagePlus[4];
				SimpleDirectedWeightedGraph<CC,ConnectionEdge>  gg=readGraphFromFile(new File(outputDataDir,"50_graph_step_3.ser").getAbsolutePath());
				graphsImgs[0]=drawGraph(imgDatesTmp, gg, ray, thickness,sizeFactor);		

				gg=readGraphFromFile(new File(outputDataDir,"50_graph_step_4.ser").getAbsolutePath());
				graphsImgs[1]=drawGraph(imgDatesTmp, gg, ray, thickness,sizeFactor);							
				
				gg=readGraphFromFile(new File(outputDataDir,"50_graph_step_5.ser").getAbsolutePath());
				graphsImgs[2]=drawGraph(imgDatesTmp, gg, ray, thickness,sizeFactor);		

				gg=readGraphFromFile(new File(outputDataDir,"50_graph_step_6.ser").getAbsolutePath());
				graphsImgs[3]=drawGraph(imgDatesTmp, gg, ray, thickness,sizeFactor);		

				backImgs[0]=imgDatesHigh.duplicate();
				backImgs[1]=imgDatesHigh.duplicate();
				backImgs[2]=imgDatesHigh.duplicate();
				backImgs[3]=imgDatesHigh.duplicate();
				graphs=VitimageUtils.slicesToStack(graphsImgs);
				graphs=VitimageUtils.convertFloatToByteWithoutDynamicChanges(graphs);
				dates=VitimageUtils.convertFloatToByteWithoutDynamicChanges(VitimageUtils.slicesToStack(backImgs));
			}
			else {
				ImagePlus imgG=drawGraph(imgDatesTmp, graph, ray, thickness,sizeFactor);		
				graphs=VitimageUtils.slicesToStack(new ImagePlus[] {imgG});
				graphs=VitimageUtils.convertFloatToByteWithoutDynamicChanges(graphs);
				ImagePlus []datesTab=new ImagePlus[graphs.getStackSize()];for(int i=0;i<datesTab.length;i++)datesTab[i]=imgDatesHigh;
				dates=VitimageUtils.convertFloatToByteWithoutDynamicChanges(VitimageUtils.slicesToStack(datesTab));
			}			

			//Compute the combined rendering
			ImagePlus glob=VitimageUtils.hyperStackingChannels(new ImagePlus[] {dates,graphs});
			glob.setDisplayRange(0, nDays);
			IJ.run(glob,"Fire","");
			System.out.println("Writing TIF to "+new File(outputDataDir,"51_graph_rendering.tif").getAbsolutePath());
			IJ.saveAsTiff(glob, new File(outputDataDir,"51_graph_rendering.tif").getAbsolutePath());
		}
		return true;
	}
	
	//update costs and disactivateNonOptimalLateralBranches	
	public static void updateCostAndDisconnectNonOptimalLateralBranches_V1(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph){
		for(CC cc:graph.vertexSet()) {
			if(cc.trunk)continue;
			ConnectionEdge bestEdge=cc.bestOutgoingEdge();
			for(ConnectionEdge edge : graph.outgoingEdgesOf(cc)) {
				if(edge!=bestEdge)  {
					edge.activated=false;
				}
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public static ArrayList<CC>sortCC(ArrayList<CC>arIn){
		Object[]tabIn=new Object[arIn.size()];
		for(int i=0;i<arIn.size();i++)tabIn[i]=arIn.get(i);
		Arrays.sort(tabIn,new CCComparator());
		ArrayList<CC>arOut=new ArrayList<CC>();
		for(int i=0;i<arIn.size();i++)arOut.add((CC) tabIn[i]);
		return arOut;
	}
	
	@SuppressWarnings("rawtypes")
	static class CCComparator implements java.util.Comparator {
	   public int compare(Object o1, Object o2) {
	      return ((Double) ((CC) o1).x).compareTo((Double)((CC) o2).x);
	   }
	}

	public static int toInt(double f) {
		return (int)Math.round(f);
	}
	public static int toInt(float f) {
		return (int)Math.round(f);
	}
	public static int toInt(byte b) {
		return (byte)( b & 0xff);
	}
	public static byte toByte(int i) {
		if(i>255)i=255;
		if(i<0)i=0;
		return (byte)i;
	}
		
	public static int getDayMax(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph) {
		int max=0;
		for (CC cc:graph.vertexSet()) if(cc.day>max)max=cc.day;
		return max;
	}
		
	public static void postProcessTopology(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,ImagePlus img,PipelineParamHandler pph,int indexBox) {
		reconnectLateralThatWereThereFromTheStart(graph,img,pph,indexBox);
		detectObviouslyOutlierOrganAndProlongateIncidentStopped(graph,img,pph,indexBox);
		detectSubtleOutliersBasedOnStatistics(graph,img,pph,indexBox);
	}
		
	public static SpanningTree<ConnectionEdge> computeMinimumSpanningTreeGraph(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph) {		
		System.out.println("Start spanning tree");
		SpanningTree<ConnectionEdge> tree=new KruskalMinimumSpanningTree<>(graph).getSpanningTree();
		System.out.println("Ok.");
		return tree;
	}
	
	public static double ratioInObject(ImagePlus img,double x0,double y0,double x1,double y1) {
		int X=img.getWidth();
		img.getHeight();
		float[]tab=(float[]) img.getStack().getPixels(1);
		double dt=0.33;
		double xx=x0;
		double yy=y0;
		int nb=(int) (Math.sqrt((x1-x0)*(x1-x0)+(y1-y0)*(y1-y0))/dt);
		int countIn=0;
		int countOut=0;
		double[]vect=new double[]{x1-x0,y1-y0,0};
		vect=TransformUtils.normalize(vect);
		vect=TransformUtils.multiplyVector(vect, dt);
		while((nb--)>=0) {
			xx+=vect[0];
			yy+=vect[1];
			int x=toInt(xx);
			int y=toInt(yy);
			int val=toInt(tab[y*X+x]);
			if(val==0)countOut++;
			else countIn++;
		}
		if((countIn+countOut)==0)return 1;
		return countIn/(countIn+countOut);
	}

	public static double prodScal(CC cc1,CC cc2, CC cc3,CC cc4) {
		double[]vect1=new double[] {cc1.x(),cc1.y(),0};
		double[]vect2=new double[] {cc2.x(),cc2.y(),0};
		double[]vect3=new double[] {cc3.x(),cc3.y(),0};
		double[]vect4=new double[] {cc4.x(),cc4.y(),0};
		double[]vect12=TransformUtils.vectorialSubstraction(vect2, vect1);
		double[]vect34=TransformUtils.vectorialSubstraction(vect4, vect3);
		vect12=TransformUtils.normalize(vect12);
		vect34=TransformUtils.normalize(vect34);
		return TransformUtils.scalarProduct(vect12, vect34);
	}
	
	public static double orientationDownwards(CC cc1,CC cc2) {
		double[]vect1=new double[] {cc1.x(),cc1.y(),0};
		double[]vect2=new double[] {cc2.x(),cc2.y(),0};
		double[]vect12=TransformUtils.vectorialSubstraction(vect2, vect1);
		vect12=TransformUtils.normalize(vect12);
		double[]vect34=new double[] {0,1,0};
		return TransformUtils.scalarProduct(vect12, vect34);
	}
	
	public static int isIn(CC cc, ArrayList<CC[]>tabCC) {
		for(int i=0;i<tabCC.size();i++) {
			if(cc==tabCC.get(i)[1])return i;
		}
		return -1;
	}
		
	public static CC getRoot(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph) {
		for(CC cc : graph.vertexSet())if(cc.day==0)return cc;
		return null;
	}
	
	public static void setFirstOrderCosts_phase2(SimpleDirectedWeightedGraph<CC,ConnectionEdge>graph,double[]hoursExtremities) {
		int nDays=getMaxDay(graph);
		for(ConnectionEdge edge:graph.edgeSet()) {
			graph.setEdgeWeight(edge, getCostFirstOrderConnection_phase2(edge,nDays,hoursExtremities));
		}
	}
	
	public static void setFirstOrderCosts_phase1(SimpleDirectedWeightedGraph<CC,ConnectionEdge>graph,double[]hoursExtremities) {
		int nDays=getMaxDay(graph);
		for(ConnectionEdge edge:graph.edgeSet()) {
			graph.setEdgeWeight(edge, getCostFirstOrderConnection(edge,nDays,hoursExtremities));
		}
	}
	
	public static int getMaxDay(SimpleDirectedWeightedGraph<CC,ConnectionEdge>graph) {
		int max=0;
		for(CC cc : graph.vertexSet()) {
			if(cc.day>max)max=cc.day;
		}
		return max;
	}
	
	public static void identifyTrunks(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph) {
		//Go from roots to down 
		CC root=getRoot(graph);	
		root.trunk=true;
		ArrayList<CC>nextCC=new ArrayList<CC>();
		ArrayList<CC>curCC=new ArrayList<CC>();
		for(CC cc : graph.vertexSet()) if((cc.day==1) && graph.containsEdge(root, cc)) {cc.trunk=true;curCC.add(cc);}
		System.out.print("\nTrunk computation");
		while(curCC.size()>0) {
			for(int i=0; i<curCC.size();i++) {
				CC cc=curCC.get(i);
				cc.trunk=true;
				if(graph.outgoingEdgesOf(cc).size()==0)continue;
				double minCost=1E18;
				ConnectionEdge bestEdge=null;
				for(ConnectionEdge edge : graph.outgoingEdgesOf(cc)) {
					if(graph.getEdgeWeight(edge)<minCost) {minCost=graph.getEdgeWeight(edge);bestEdge=edge;}
					
				}
				nextCC.add(graph.getEdgeTarget(bestEdge));
				bestEdge.trunk=true;
			}
			curCC=nextCC;
			nextCC=new ArrayList<CC>();
		}
		ArrayList<ConnectionEdge>edges=new ArrayList<ConnectionEdge>();
		for(CC cc : graph.vertexSet()) {
			if(!cc.trunk)continue;
			for (ConnectionEdge edge: graph.incomingEdgesOf(cc)) {
				if(!edge.source.trunk)edges.add(edge);
			}
		}
		for(ConnectionEdge edge:edges)graph.removeEdge(edge);
	}
	
	public static boolean isExtremity(CC cc,SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph) {
		if(graph.incomingEdgesOf(cc).size()<1)return true;
		if(graph.outgoingEdgesOf(cc).size()<1)return true;
		boolean hasParent=false;
		boolean hasChild=false;
		for(ConnectionEdge edge :graph.incomingEdgesOf(cc))if(edge.activated)hasParent=true;
		for(ConnectionEdge edge :graph.outgoingEdgesOf(cc))if(edge.activated)hasChild=true;
		return !(hasParent && hasChild);
	}
	
	public static SimpleDirectedWeightedGraph<CC,ConnectionEdge> treeAsGraph(SpanningTree<ConnectionEdge> tree){
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph=new SimpleDirectedWeightedGraph<>(ConnectionEdge.class);
		for(ConnectionEdge edge : tree) {
			if(!graph.containsVertex(edge.source))graph.addVertex(edge.source);
			if(!graph.containsVertex(edge.target))graph.addVertex(edge.target);
			graph.addEdge(edge.source,edge.target,edge);
		}
		return graph;
	}
	
	public static SimpleDirectedWeightedGraph<CC,ConnectionEdge>readGraphFromFile(String path){
	    try {
		FileInputStream streamIn = new FileInputStream(path);
	    ObjectInputStream objectinputstream = new ObjectInputStream(streamIn);
	    @SuppressWarnings("unchecked")
	    SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph = (SimpleDirectedWeightedGraph<CC,ConnectionEdge>) objectinputstream.readObject();
	    objectinputstream.close();
			streamIn.close();
		    return graph;
		} catch (ClassNotFoundException | IOException c) {
			c.printStackTrace();
		}
	    return null;
	}	
	
	public static void writeGraphToFile(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,String path) {
		FileOutputStream fout;
		try {
			fout = new FileOutputStream(path, false);
			ObjectOutputStream oos = new ObjectOutputStream(fout);
			oos.writeObject(graph);
			oos.close();
			fout.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static CC getCC(SimpleDirectedWeightedGraph<CC,ConnectionEdge>graph,int day, int x, int y) {
		//System.out.println("Looking for CC");
		CC ret=null;
		double minDist=1E8;
		for (CC cc: graph.vertexSet()) {
			if(cc.day!=day)continue;
			double dist=VitimageUtils.distance(x, y, cc.r.getContourCentroid()[0], cc.r.getContourCentroid()[1]);
			if(dist<minDist) {
				minDist=dist;
				ret=cc;
			}
		}
		return ret;
	}	
	
	public static CC getCC(SimpleDirectedWeightedGraph<CC,ConnectionEdge>graph,int x, int y) {
		//System.out.println("Looking for CC");
		CC ret=null;
		double minDist=1E8;
		for (CC cc: graph.vertexSet()) {
			double dist=VitimageUtils.distance(x, y, cc.r.getContourCentroid()[0], cc.r.getContourCentroid()[1]);
			if(dist<minDist) {
				minDist=dist;
				ret=cc;
			}
		}
		return ret;
	}	

	public static CC getCCWithResolution(SimpleDirectedWeightedGraph<CC,ConnectionEdge>graph,int x, int y,int res) {
		//System.out.println("Looking for CC");
		CC ret=null;
		double minDist=1E8;
		for (CC cc: graph.vertexSet()) {
			double dist=VitimageUtils.distance(x/res, y/res, cc.r.getContourCentroid()[0], cc.r.getContourCentroid()[1]);
			if(dist<minDist) {
				minDist=dist;
				ret=cc;
			}
		}
		return ret;
	}	


	
	
	
	}
