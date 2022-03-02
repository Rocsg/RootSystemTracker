package fr.cirad.image.TimeLapseRhizo;

import java.awt.Rectangle;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.alg.interfaces.SpanningTreeAlgorithm.SpanningTree;
import org.jgrapht.alg.spanning.KruskalMinimumSpanningTree;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

import distance.Euclidean;
import fr.cirad.image.common.MostRepresentedFilter;
import fr.cirad.image.common.Timer;
import fr.cirad.image.common.TransformUtils;
import fr.cirad.image.common.VitimageUtils;
import fr.cirad.image.common.TransformUtils.VolumeComparator;
import fr.cirad.image.rsmlviewer.FSR;
import fr.cirad.image.rsmlviewer.Root;
import fr.cirad.image.rsmlviewer.RootModel;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;

public class RegionAdjacencyGraphUtils {
	public static int minDistanceBetweenLateralInitiation=4;
	public static int minLateralStuckedToOtherLateral=30;
	public static int MIN_SIZE_CC=5;
	static int SIZE_FACTOR=6;
	static int MAX_SPEED_LAT=33;
	static int TYPICAL_SPEED=100/8;//pixels/timestep. pix=19µm , timestep=8h, meaning TYPICAL=237 µm/h
	static double PENALTY_COST=0.5;
	static double OUT_OF_SILHOUETTE_PENALTY=PENALTY_COST;//100;//5+2*daymax 50 100 1000
	static double REVERSE_TIME_PENALTY=PENALTY_COST;//0.5;//
	static double SEMI_PENALTY=PENALTY_COST;
	static double IDENTITY_PENALTY=PENALTY_COST;
	static int MAX_TIMESTEP=100;
	static int MEAN_SPEED_LAT=10;
	public static final boolean DO_DISTANCE=true;
	public static final boolean DO_TIME=false;


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
	
	public static int getDayMax(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph) {
		int max=0;
		for (CC cc:graph.vertexSet()) if(cc.day>max)max=cc.day;
		return max;
	}
	
	public static void establishDataFromStartForEachNodeAndExcludeBasedOnStatistics(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,ImagePlus sampleImgForDims) {
		int Xmax=sampleImgForDims.getWidth()-1;
		int Ymax=sampleImgForDims.getHeight()-1;
		//Identify forgotten axes
		System.out.println("\n\n\n\n\n\n\n\n\n\nStarting outlier detection, rebranching and that folks in RAG");
		CC debugCC=null;
		System.out.println(debugCC);
		for(int i=0;i<20;i++)System.out.println();
		System.out.println("REBRANCHING FORGOTTEN ONES");
		for (CC cc:graph.vertexSet()) {
			boolean debug=(cc==debugCC);
			if(debug)System.out.println("Going for debug");
			if(!cc.trunk && cc.bestIncomingActivatedCC()==null) {
				if(debug)System.out.println("Normally we go there");
				//Count successors
				int succ=1;
				CC ct=cc;
				while(ct.bestOutgoingActivatedCC()!=null) {
					ct=ct.bestOutgoingActivatedCC();
					succ++;
				}
				if(succ<4)continue;
				//Look for a connexion to trunk
				System.out.println("Testing a potential root to attach back. It is a lat going from : "+cc+" to "+ct);
				ConnectionEdge edge=null;
				for(ConnectionEdge ed : graph.incomingEdgesOf(cc)) {
					if(ed.source.trunk)edge=ed;
				}
				if(edge!=null) {
					System.out.println("Rebranching on trunk 1");
					edge.activated=true;
					continue;
				}
				if(ct.euclidianDistanceToCC(cc)>MEAN_SPEED_LAT*10 && (cc.bestIncomingEdge()==null || (succ>9) ) ) {
					System.out.println("Rebranching on trunk 2");
					//This is a forgotten big root

					//Identify the nearer trunk
					double distMin=1000000;
					CC no=null;
					for (CC cctr:graph.vertexSet()) {
						if (!cctr.trunk || cctr.day==0)continue;
						double dist=cctr.euclidianDistanceToCC(cc);
						if(dist<distMin) {distMin=dist;no=cctr;}
					}
					if(no==null)continue;
					System.out.println("  selected branching point = "+no);
					ConnectionEdge edd=new ConnectionEdge( no.x*0.5 + cc.x*0.5, no.y*0.5 + cc.y*0.5, 0, no,cc,no.x>cc.x ? -1 : 1 ,no.y>cc.y ? -1 : 1); 
					edd.activated=true;
					edd.hidden=true;
					edd.trunk=false;
					graph.addEdge(no,cc,edd); 
					System.out.println("   branching edd="+edd);
				}
			}
		}
		
		
		//Identify laterals
		int nLateral=0;
		int nMaxSigma=25;
		int dMax=getDayMax(graph);
		int deltaTimeMax=dMax;
		int []nEach=new int[deltaTimeMax+1];
		int []iter=new int[deltaTimeMax+1];
		int nAccepted=1;
		int nRejected=1;
		double ratioMinVisible=0.4;
		
		//Build list of lateral 
		//ArrayList<CC>tabDel=new ArrayList<CC>();
		for (CC cc:graph.vertexSet()) {
			if(cc.trunk) {
				for(ConnectionEdge edge:graph.outgoingEdgesOf(cc)) {
					if(edge.activated) {
						CC ccStart=edge.target;
						if(!ccStart.trunk) {//On a un début de laterale
							double lenHid=0;
							double lenVis=0;
							ccStart.isLatStart=true;
							ccStart.ccLateralStart=ccStart;
							ccStart.pathFromStart=new ArrayList<CC>();
							ccStart.pathFromStart.add(ccStart);
							if(ccStart.isLateral)IJ.showMessage("WARNING : setting lateral an already lateral : "+ccStart);
							ccStart.isLateral=true;nLateral++;
							CC ccTmp=ccStart;
							CC ccOld;
							double lengthFromStart=0;
							int surfaceFromStart=ccStart.nPixels;
							int deltaTimeFromStart=0;
							nEach[0]++;
							ccTmp.surfaceFromStart=surfaceFromStart;
							ccTmp.deltaTimeBefore=0;
							ccTmp.deltaTimeFromStart=0;
							ccTmp.lengthBefore=0;
							ccTmp.lengthFromStart=0;
							ccTmp.lateralStamp=nLateral;
							while(ccTmp.bestOutgoingActivatedCC()!=null) {
								ConnectionEdge curEdge=ccTmp.bestOutgoingActivatedEdge();
								if(curEdge.hidden)lenHid+=ccTmp.euclidianDistanceToCC(ccTmp.bestOutgoingActivatedCC());
								else lenVis+=ccTmp.euclidianDistanceToCC(ccTmp.bestOutgoingActivatedCC());
								ccOld=ccTmp;
								ccTmp=ccTmp.bestOutgoingActivatedCC();
								ccStart.pathFromStart.add(ccTmp);
								if(ccTmp.isLateral)IJ.showMessage("WARNING : setting lateral an already lateral : "+ccTmp);
								ccTmp.isLateral=true;
								ccTmp.ccLateralStart=ccStart;
								ccTmp.lateralStamp=nLateral;

								ccTmp.lengthBefore=ccTmp.euclidianDistanceToCC(ccOld);
								lengthFromStart+=ccTmp.lengthBefore;
								ccTmp.lengthFromStart=lengthFromStart;

								ccTmp.deltaTimeBefore=ccTmp.day-ccOld.day;
								deltaTimeFromStart+=ccTmp.deltaTimeBefore;
								ccTmp.deltaTimeFromStart=deltaTimeFromStart;
								nEach[deltaTimeFromStart]++;
								
								surfaceFromStart+=ccTmp.nPixels;
								ccTmp.surfaceFromStart=surfaceFromStart;
							}
							if(lenVis/(lenVis+lenHid)<ratioMinVisible)ccStart.nonValidLatStart=true;
						}
					}
				}
			}
		}
		for (CC cc:graph.vertexSet()) if(!cc.trunk && !cc.isLateral)cc.setOut();
		
		for(int i=0;i<deltaTimeMax;i++) {
			System.out.println("CUREACH["+i+"]="+nEach[i]);
			iter[i]=nEach[i];
		}
		//Collect data for computing statistics over nodes, binned according to time latence from lateral initiation
		double[][][]data=new double[6][deltaTimeMax+1][];//lenbef,lentot,speedbef,speedtot,surfthis,surftot
		double[][][]stats=new double[6][deltaTimeMax+1][3];//lenbef,lentot,speedbef,speedtot,surfthis,surftot
		for(int i=0;i<6;i++)for(int j=0;j<deltaTimeMax+1;j++)data[i][j]=new double[nEach[j]];		
		ArrayList<CC>listStart=new ArrayList<CC>();
		for (CC ccStart:graph.vertexSet()) {
			if(ccStart.isLatStart ) {
				listStart.add(ccStart);
				for(CC cc : ccStart.pathFromStart ) {				
					int nCur=cc.deltaTimeFromStart;
					data[0][nCur][iter[nCur]-1]=cc.lengthBefore;
					data[1][nCur][iter[nCur]-1]=cc.lengthFromStart;
					data[2][nCur][iter[nCur]-1]=cc.lengthBefore/(Math.max(0.7,cc.deltaTimeBefore));
					data[3][nCur][iter[nCur]-1]=cc.lengthFromStart/(Math.max(0.7,cc.deltaTimeFromStart));
					data[4][nCur][iter[nCur]-1]=cc.nPixels;
					data[5][nCur][iter[nCur]-1]=cc.surfaceFromStart;
					iter[nCur]--;
				}
				if(ccStart.bestOutgoingActivatedCC()==null)ccStart.goesToTheLeft=2;
				else if(ccStart.x<ccStart.pathFromStart.get(ccStart.pathFromStart.size()-1).x)ccStart.goesToTheLeft=1;
				else ccStart.goesToTheLeft=0;
			}
		}
		for(int i=0;i<deltaTimeMax+1;i++)if(iter[i]!=0)IJ.showMessage("WARNING AT RAG in establish, at i="+i+", residual="+nEach[i]);

		
		//Sort listStart
		listStart=sortCC(listStart);
		
		
		
		//Compute outlier exclusion based on double sided MADE
		for(int i=0;i<6;i++)for(int j=0;j<deltaTimeMax+1;j++) {
			if(nEach[j]==0)continue;
			stats[i][j]=VitimageUtils.MADeStatsDoubleSided(data[i][j], null);	
			if(stats[i][j][1]==stats[i][j][0])stats[i][j][1]=stats[i][j][0]-VitimageUtils.EPSILON;
			if(stats[i][j][2]==stats[i][j][0])stats[i][j][2]=stats[i][j][0]+VitimageUtils.EPSILON;
			//System.out.println("STATS "+i+" - "+j+" : "+"["+stats[i][j][0]+","+stats[i][j][1]+","+stats[i][j][2]);
		}
		double[]tempVals=new double[6];
		double[]tempStd=new double[6];
		for (CC ccStart:listStart) {
			if(true) {
				boolean debug=ccStart.day==11 && ccStart.n==20;
				String rejectionCause="";
				boolean rejected=false;
				rejectionCause+="\n\n ** Computing outlier exclusion on lateral starting at "+ccStart+"\n";
				
				//Testing a potential lonely node
				if(ccStart.pathFromStart.size()==1)ccStart.nonValidLatStart=true;

				//Testing a potential weird root at top of tissue that goes to the top
				if(ccStart.pathFromStart.get(ccStart.pathFromStart.size()-1).y<300 && (ccStart.pathFromStart.get(ccStart.pathFromStart.size()-1).y<ccStart.y))ccStart.nonValidLatStart=true;

				
				//Testing a potential false start of lateral
				for(CC ccs:listStart) {
					if(ccs==ccStart || ccStart.nonValidLatStart)continue;
					if(ccs.goesToTheLeft<2 && ccStart.goesToTheLeft<2 && ccs.goesToTheLeft!=ccStart.goesToTheLeft)continue;
					if(ccs.euclidianDistanceToCC(ccStart)<=minDistanceBetweenLateralInitiation) {
						int curSurf=ccStart.pathFromStart.get(ccStart.pathFromStart.size()-1).surfaceFromStart;
						int otherSurf=ccs.pathFromStart.get(ccs.pathFromStart.size()-1).surfaceFromStart;
						if(otherSurf>=curSurf && curSurf<minLateralStuckedToOtherLateral) {
							ccStart.nonValidLatStart=true;
							
							rejectionCause+=" this goes "+ccStart.goesToTheLeft+" is starting at proximity of a bigger root that is \n"+ccs+" with goes="+ccs.goesToTheLeft;
						}
					}
				}
							
				CC ccs=ccStart;
				if(ccs.nonValidLatStart==false) {
					CC ccLast=ccs.pathFromStart.get(ccs.pathFromStart.size()-1);
					if(ccLast.day<(dMax)) {
						System.out.println("\nDebug : tracking lateral false stop from"+ccLast);
						
						if(ccStart.pathFromStart.size()<2) {
							rejectionCause+="Ending too early, and too small";
							ccStart.nonValidLatStart=true;
							System.out.println("B REJECT Concluding too small");
							continue;
						}

						
						//Lateral does not grow until end of observation sequence. Is it hidden below another lateral ?
						//Is this root incident to another lateral ?
						boolean incident=false;
						if(debug)System.out.println("Debug : setting false incidence");
						
						ArrayList<CC>incidencialCC=new ArrayList<CC>();
						ArrayList<Double>incidencialCost=new ArrayList<Double>();
						ArrayList<CC>ccToTest=new ArrayList<CC>();
						for(ConnectionEdge edge1 : graph.incomingEdgesOf(ccLast)) {
							CC c1=edge1.source;
							if(debug)System.out.println("N="+ccToTest.size());
							if(debug)System.out.println("Processing 1 "+c1);
							if(c1.isLateral && c1.lateralStamp==ccLast.lateralStamp)continue;
							if(c1.isLateral && c1.lateralStamp != ccLast.lateralStamp) ccToTest.add(c1);
							for(ConnectionEdge edge2 : graph.incomingEdgesOf(c1)) {
								CC c2=edge2.source;
								if(debug)System.out.println("N="+ccToTest.size());
								if(debug)System.out.println("Processing 1-1 "+c2);
								if(c2.isLateral && c2.lateralStamp==ccLast.lateralStamp)continue;
								if(c2.isLateral && c2.lateralStamp != ccLast.lateralStamp) ccToTest.add(c2);
							}
							for(ConnectionEdge edge2 : graph.outgoingEdgesOf(c1)) {
								CC c2=edge2.target;
								if(debug)System.out.println("N="+ccToTest.size());
								if(debug)System.out.println("Processing 1-1 "+c2);
								if(c2.isLateral && c2.lateralStamp==ccLast.lateralStamp)continue;
								if(c2.isLateral && c2.lateralStamp != ccLast.lateralStamp) ccToTest.add(c2);
							}
						}
						for(ConnectionEdge edge1 : graph.outgoingEdgesOf(ccLast)) {
							CC c1=edge1.target;
							if(debug)System.out.println("N="+ccToTest.size());
							if(debug)System.out.println("Processing 2 "+c1);
							if(c1.isLateral && c1.lateralStamp==ccLast.lateralStamp)continue;
							if(c1.isLateral && c1.lateralStamp != ccLast.lateralStamp) ccToTest.add(c1);
							for(ConnectionEdge edge2 : graph.incomingEdgesOf(c1)) {
								CC c2=edge2.source;
								if(debug)System.out.println("N="+ccToTest.size());
								if(debug)System.out.println("Processing 2-2 "+c2);
								if(c2.isLateral && c2.lateralStamp==ccLast.lateralStamp)continue;
								if(c2.isLateral && c2.lateralStamp != ccLast.lateralStamp) ccToTest.add(c2);
							}
							for(ConnectionEdge edge2 : graph.outgoingEdgesOf(c1)) {
								CC c2=edge2.target;
								if(debug)System.out.println("N="+ccToTest.size());
								if(debug)System.out.println("Processing 2-2 "+c2);
								if(c2.isLateral && c2.lateralStamp==ccLast.lateralStamp)continue;
								if(c2.isLateral && c2.lateralStamp != ccLast.lateralStamp) ccToTest.add(c2);
							}
						}
						for(CC cc : ccToTest) {
							double cost=-prodScal(ccLast.bestIncomingActivatedCC(), ccLast, ccLast, cc);
							if(ccLast.euclidianDistanceToCC(cc)>2*MAX_SPEED_LAT)continue;
							incidencialCC.add(cc);
							incidencialCost.add(cost);
						}
						incident=incidencialCC.size()>0;
						System.out.println("Incidence ? "+incident);
						
						//No incidence
						int minLenForBranchThatStop=30;
						if(! incident) {
							CC ccl=ccStart.pathFromStart.get(ccStart.pathFromStart.size()-1);
							if(ccl.lengthFromStart<minLenForBranchThatStop) {
								rejectionCause+="Ending too early, and too small";
								ccStart.nonValidLatStart=true;
								rejectionCause+="A REJECT Concluding too small";
							}
						}
						else {//Incidence
							//Find the best possible incidence
							System.out.println("Concluding incidence");
							CC bestIncidenceCC=null;
							double lowerCost=1000000;
							for(int i=0;i<incidencialCost.size();i++) {
								if(incidencialCost.get(i)<lowerCost) {
									bestIncidenceCC=incidencialCC.get(i);
									lowerCost=incidencialCost.get(i);
								}
							} 
							System.out.println("Found best cost = "+lowerCost+" at incidence on "+bestIncidenceCC);
						
							//Locate if at left or right side of growing incidence
							double []v1=new double[] {bestIncidenceCC.x-bestIncidenceCC.bestIncomingActivatedCC().x, -bestIncidenceCC.y+bestIncidenceCC.bestIncomingActivatedCC().y, 0};
							double []v2=new double[] {bestIncidenceCC.x-ccLast.x,-bestIncidenceCC.y+ccLast.y,0};
							System.out.println("V1="+v1[0]+","+v1[1]);
							System.out.println("V2="+v2[0]+","+v2[1]);
							double []z=new double[] {0,0,1};
							double []toLeft=TransformUtils.vectorialProduct(z, v1);
							toLeft=TransformUtils.normalize(toLeft);
							System.out.println("Vector to left is : "+toLeft[0]+","+toLeft[1]);
							double sign=TransformUtils.scalarProduct(toLeft,v2);
							System.out.println("sign="+sign);
							boolean arrivesFromLeft=(sign<0);//Inverted because y axis points to the bottom
							System.out.println("Arrives from left is "+arrivesFromLeft);

							//Make best estimation possible of speed
							double dt=ccLast.day-ccLast.bestIncomingActivatedCC().bestIncomingActivatedCC().day;
							if(dt<1)dt=0.7;
							double dl=(ccLast.lengthBefore+ccLast.bestIncomingActivatedCC().lengthBefore);
							double speed=dl/dt;
							System.out.println("Estimated speed="+speed);
							
							//Estimate expected length in more
							double additionalLenExpected=speed*(dMax-ccLast.day+0.5);
							double wayStill=additionalLenExpected;
							System.out.println("Needed len in more="+wayStill);
							boolean found=false;
							System.out.println("BEST IS : "+bestIncidenceCC);
							System.out.println("START OF BEST IS : "+bestIncidenceCC.ccLateralStart);
							//System.out.println("C : "+bestIncidenceCC.ccLateralStart.pathFromStart);
							System.out.println("NB NODES OF BEST IS : "+bestIncidenceCC.ccLateralStart.pathFromStart.size());
							CC lastIncidenceCC=bestIncidenceCC.ccLateralStart.pathFromStart.get(bestIncidenceCC.ccLateralStart.pathFromStart.size()-1);
							CC startOfIncidence=lastIncidenceCC.ccLateralStart;
							CC ccToAdd=null;
							
							
							//Estimate position
							if(wayStill<=ccLast.euclidianDistanceToCC(bestIncidenceCC)){
								System.out.println("Case where no attempt new lat, en effet : "+"CCLAST="+ccLast+"  BESTCC="+bestIncidenceCC+" WAY="+wayStill);
								//If before join
								double []v=new double[] {bestIncidenceCC.x-ccLast.x,bestIncidenceCC.y-ccLast.y,0};
								v=TransformUtils.normalize(v);
								if(debug)System.out.println("Vect directeur="+v[0]+" "+v[1]);
								v=TransformUtils.multiplyVector(v, wayStill);
								Rectangle r=new Rectangle((int)Math.min(Xmax,Math.max(0, (ccLast.x+v[0]))),(int)Math.min(Ymax,Math.max(0, (ccLast.y+v[1]))),1,1);
								ccToAdd=new CC(dMax,maxCCIndexOfDay(graph, dMax),new Roi(r),graph);								
								System.out.println("Adding "+ccToAdd);
							}
							else if(wayStill>=ccLast.euclidianDistanceToCC(bestIncidenceCC)-bestIncidenceCC.lengthFromStart+lastIncidenceCC.lengthFromStart){
								System.out.println("Case where goes over new lat ");
								wayStill-=(ccLast.euclidianDistanceToCC(bestIncidenceCC)-bestIncidenceCC.lengthFromStart+lastIncidenceCC.lengthFromStart);
								System.out.println("Having to add len="+wayStill+" from the selected incidence "+lastIncidenceCC);
								//If longer than incidence one
//								double[]vectFromLast=new double[3];
								double[]vectDir=new double[] {lastIncidenceCC.x-lastIncidenceCC.bestIncomingActivatedCC().x,lastIncidenceCC.y-lastIncidenceCC.bestIncomingActivatedCC().y,0};
								vectDir=TransformUtils.normalize(vectDir);
								System.out.println("Vectdir of incidence from last bef to last="+vectDir[0]+" , "+vectDir[1]);
								double []vectRab=TransformUtils.multiplyVector(vectDir, wayStill);
								System.out.println("So will add from last="+vectRab[0]+" , "+vectRab[1]);
								double[]vectZ=new double[] {0,0,1};
								double[]vectToNewLat;
								if(arrivesFromLeft)vectToNewLat=toLeft;
								else vectToNewLat=TransformUtils.multiplyVector(toLeft, -1);
								vectToNewLat[1]=-vectToNewLat[1];
								double estimateRay=lastIncidenceCC.nPixels*2.0/lastIncidenceCC.lengthBefore;
								if(estimateRay>3 || estimateRay<=0)estimateRay=3;
								vectToNewLat=TransformUtils.multiplyVector(vectToNewLat, estimateRay);
								System.out.println("And to handle to be left or not, will add in more="+vectToNewLat[0]+" , "+vectToNewLat[1]);
								vectToNewLat=TransformUtils.vectorialAddition(vectToNewLat, vectRab);
								Rectangle r=new Rectangle((int)Math.min(Xmax,Math.max(0, (lastIncidenceCC.x+vectToNewLat[0]))),(int)Math.min(Ymax,Math.max(0, (lastIncidenceCC.y+vectToNewLat[1]))),1,1);
								ccToAdd=new CC(dMax,maxCCIndexOfDay(graph, dMax),new Roi(r),graph);								
								System.out.println("At the end, adding "+ccToAdd);
							}
							else {
								System.out.println("Case where in between ");
								//A point somewhere between bestIncidenceCC and lastIncidenceCC
								//Determine before and after
								CC ccBef=null;CC ccAft=null;
								CC ccTmp=bestIncidenceCC;
								CC ccOld=bestIncidenceCC;
								while(ccBef==null) {
									System.out.println("Trying...");
									ccOld=ccTmp;
									ccTmp=ccTmp.bestOutgoingActivatedCC();
									double lenBef=ccLast.euclidianDistanceToCC(bestIncidenceCC)-bestIncidenceCC.lengthFromStart+ccOld.lengthFromStart;
									double lenAft=ccLast.euclidianDistanceToCC(bestIncidenceCC)-bestIncidenceCC.lengthFromStart+ccTmp.lengthFromStart;
									if(wayStill<lenAft && wayStill>lenBef) {
										System.out.println("We got the in between : "+lenBef+" "+wayStill+" "+lenAft);
										ccBef=ccOld;
										ccAft=ccTmp;
									}
								}
								wayStill-=(ccLast.euclidianDistanceToCC(bestIncidenceCC)-bestIncidenceCC.lengthFromStart+ccBef.lengthFromStart);
								System.out.println("Now wayStill="+wayStill);
								double[]vectDir=new double[] {ccAft.x-ccBef.x,ccAft.y-ccBef.y,0};
								vectDir=TransformUtils.normalize(vectDir);
								System.out.println("Vectdir="+vectDir[0]+" , "+vectDir[1]);
								double[]vectPos=TransformUtils.multiplyVector(vectDir, wayStill);
								vectPos=TransformUtils.vectorialAddition(vectPos,new double[] {ccBef.x,ccBef.y,0});
								System.out.println("Before radius add : "+vectPos[0]+" , " +vectPos[1]);
								double[]vectZ=new double[] {0,0,1};
								double[]vectToNewLat;
								if(arrivesFromLeft)vectToNewLat=toLeft;
								else vectToNewLat=TransformUtils.multiplyVector(toLeft, -1);
								vectToNewLat[1]=-vectToNewLat[1];
								double estimateRay=ccBef.nPixels/(2.0*ccBef.lengthBefore);
								if(estimateRay>3 || estimateRay<=0)estimateRay=3;
								vectToNewLat=TransformUtils.multiplyVector(vectToNewLat, estimateRay);
								vectPos=TransformUtils.vectorialAddition(vectPos, vectToNewLat);
								Rectangle r=new Rectangle((int)Math.min(Xmax,Math.max(0, (vectPos[0]))),(int)Math.min(Ymax,Math.max(0, (vectPos[1]))),1,1);
								ccToAdd=new CC(dMax,maxCCIndexOfDay(graph, dMax),new Roi(r),graph);		
								System.out.println("Adding "+ccToAdd);
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

				//Testing statistics
				for(CC cc : ccStart.pathFromStart ) {				
					
					//Criterion 1 : no big jumps visible
					if(false && !cc.isLatStart && ((cc.day-cc.bestIncomingActivatedCC().day)>2)) {//Exclusion car saut de 3 sur un arc visible
						System.out.println("Criterion 0 (visible jump of > 2) unvalidating lateral. Event happened at "+cc);
						ccStart.nonValidLatStart=true;
					}
					tempVals=new double[] {
							cc.lengthBefore,cc.lengthFromStart,cc. lengthBefore/(Math.max(0.7,cc.deltaTimeBefore)),
							cc.lengthFromStart/(Math.max(0.7,cc.deltaTimeFromStart)),cc.nPixels,cc.surfaceFromStart};
					
					int nCur=cc.deltaTimeFromStart;
				
					boolean fillDebug=(ccStart.day==7 && ccStart.n==4);
					rejectionCause+="Stats at node "+nCur+" "+cc.x+","+cc.y+" : ";
					for(int i=0;i<6;i++) {
						if(nCur<2 || i==0 || i==2) {
							tempStd[i]=-1;
							continue;
						}
						if(stats[i][nCur][0]>tempVals[i])tempStd[i]=-1;
						else tempStd[i]= (tempVals[i]-stats[i][nCur][0])/(stats[i][nCur][2]-stats[i][nCur][0]);
						if(fillDebug)rejectionCause+=("DEBUG "+i+" : "+tempVals[i]+" vers "+stats[i][nCur][0]+" - "+stats[i][nCur][1]+" - "+stats[i][nCur][2]);
					}
					for(int i=0;i<6;i++)rejectionCause+=(" ["+tempStd[i]+"] ");
					rejectionCause+="\n";
					if(cc !=ccStart.pathFromStart.get(ccStart.pathFromStart.size()-1 ) )for(int i=0;i<6;i++) {
						if(tempStd[i]>=nMaxSigma) {
							rejectionCause+=("\n Rejection at node "+nCur+" for cause "+i+" = "+tempStd[i]);
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
		}
		//tabDel=new ArrayList<CC>();
		for (CC ccStart:graph.vertexSet()) {
			if(ccStart.isLatStart) {
				if(ccStart.nonValidLatStart)for(CC c:ccStart.pathFromStart)c.setOut();// tabDel.add(ccStart);
			}
		}
//		for(CC cc:tabDel)cc.lightOffLateralRoot();
		System.out.println("Accepted="+nAccepted);
		System.out.println("Rejected="+nRejected);
	}
					

	public static ArrayList<CC>sortCC(ArrayList<CC>arIn){
//		System.out.println("\n\n\n\n\nTab at input :");
//		for(int i=0;i<arIn.size();i++)System.out.println(arIn.get(i));
		Object[]tabIn=new Object[arIn.size()];
		for(int i=0;i<arIn.size();i++)tabIn[i]=arIn.get(i);
		Arrays.sort(tabIn,new CCComparator());
		ArrayList<CC>arOut=new ArrayList<CC>();
		for(int i=0;i<arIn.size();i++)arOut.add((CC) tabIn[i]);
//		System.out.println("\n\n\n\n\nTab at output :");
//		for(int i=0;i<arIn.size();i++)System.out.println(arOut.get(i));
		return arOut;
	}
	
	static class CCComparator implements java.util.Comparator {
		   public int compare(Object o1, Object o2) {
		      return ((Double) ((CC) o1).x).compareTo((Double)((CC) o2).x);
		   }
		}
	public static void postProcessOutliers(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,ImagePlus img) {
		establishDataFromStartForEachNodeAndExcludeBasedOnStatistics(graph,img);
	}
	
	
	/** Methods for building and improving the graph ------------------------------------------------------------------------------------------------------*/	
	public static SimpleDirectedWeightedGraph<CC,ConnectionEdge>  buildAndProcessGraphStraight(ImagePlus imgDatesTmp,String mainDataDir,String ml,String boite,boolean doImages,boolean compute,boolean doDebugImages) {
		double ray=5;
		int thickness=5;
		int sizeFactor=SIZE_FACTOR;
		int connexity=8;
		int nSteps=4;
		int nbTrees=5;
		boolean goStraightPlease=false;
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph=null;
		if(compute) {		
			
			graph=buildGraphFromDateMap(imgDatesTmp,connexity);
			pruneGraph(graph, nbTrees,true);
			setFirstOrderCosts_phase1(graph);
//			if(doDebugImages) writeGraphToFile(graph,mainDataDir+"/3_Graphs_Ser/"+"ML"+ml+"_Boite_"+boite+"_step1.ser");
			identifyTrunks(graph);
//			if(doDebugImages) writeGraphToFile(graph,mainDataDir+"/3_Graphs_Ser/"+"ML"+ml+"_Boite_"+boite+"_step2.ser");
			setFirstOrderCosts_phase2(graph);
			if(doDebugImages) writeGraphToFile(graph,mainDataDir+"/3_Graphs_Ser/"+"ML"+ml+"_Boite_"+boite+"_step3.ser");
			CC cctest=getCC(graph,5767,1735);
			System.out.println("DEB HHH");
			for(ConnectionEdge edge : graph.outgoingEdgesOf(cctest)) {
				System.out.println(edge);
			}
			computeMinimumDirectedConnectedSpanningTree(graph);//TODO : les deux étapes là sont redondantes. Le disconnect suffit

			CC ccTest=getCC(graph,5767,1735);
			System.out.println("DEB HHHHHHH");
			for(ConnectionEdge edge : graph.outgoingEdgesOf(ccTest)) {
				System.out.println(edge);
			}

			//VitimageUtils.waitFor(50000);
			if(doDebugImages) writeGraphToFile(graph,mainDataDir+"/3_Graphs_Ser/"+"ML"+ml+"_Boite_"+boite+"_step4.ser");
			reconnectDisconnectedBranches_v2(imgDatesTmp,graph,1,true,false);		//reconnectSingleBranches(graph4);
			if(doDebugImages) writeGraphToFile(graph,mainDataDir+"/3_Graphs_Ser/"+"ML"+ml+"_Boite_"+boite+"_step5.ser");
			postProcessOutliers(graph,imgDatesTmp);		//reconnectSingleBranches(graph4);
			if(doDebugImages) writeGraphToFile(graph,mainDataDir+"/3_Graphs_Ser/"+"ML"+ml+"_Boite_"+boite+"_step6.ser");
			writeGraphToFile(graph,mainDataDir+"/3_Graphs_Ser/"+"ML"+ml+"_Boite_"+boite+".ser");
		}
		else graph=readGraphFromFile(mainDataDir+"/3_Graphs_Ser/"+"ML"+ml+"_Boite_"+boite+".ser");
	
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
				if(goStraightPlease) {
					graphsImgs=new ImagePlus[3];
					backImgs=new ImagePlus[3];
					SimpleDirectedWeightedGraph<CC,ConnectionEdge>  gg=readGraphFromFile(mainDataDir+"/3_Graphs_Ser/"+"ML"+ml+"_Boite_"+boite+"_step"+(1)+".ser");
					graphsImgs[0]=drawGraph(imgDatesTmp, gg, ray, thickness,sizeFactor);		

					gg=readGraphFromFile(mainDataDir+"/3_Graphs_Ser/"+"ML"+ml+"_Boite_"+boite+"_step"+(5)+".ser");
					graphsImgs[1]=drawGraph(imgDatesTmp, gg, ray, thickness,sizeFactor);		
					
					gg=readGraphFromFile(mainDataDir+"/3_Graphs_Ser/"+"ML"+ml+"_Boite_"+boite+"_step"+(6)+".ser");
					graphsImgs[2]=drawGraph(imgDatesTmp, gg, ray, thickness,sizeFactor);		
					backImgs[0]=imgDatesHigh.duplicate();
					backImgs[1]=imgDatesHigh.duplicate();
					backImgs[2]=imgDatesHigh.duplicate();
				}
				else {
					for(int i=0;i<nSteps;i++) {
						SimpleDirectedWeightedGraph<CC,ConnectionEdge>  gg=readGraphFromFile(mainDataDir+"/3_Graphs_Ser/"+"ML"+ml+"_Boite_"+boite+"_step"+(i+3)+".ser");
						graphsImgs[i]=drawGraph(imgDatesTmp, gg, ray, thickness,sizeFactor);		
						backImgs[i]=imgDatesHigh.duplicate();
					}
					//VitimageUtils.waitFor(5000000);
				}
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
			IJ.run(graphs,"Fire","");
			//glob.show();
			IJ.saveAsTiff(glob, mainDataDir+"/3_Graphs_Tif/ML"+ml+"_Boite_"+boite+".tif");
		}
		return graph;
	}
	
	public static int maxCCIndexOfDay(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph,int d) {
		int max=0;
		for(CC cc:graph.vertexSet())if(cc.day==d && cc.n>max)max=cc.n;
		return max;
	}
	
	
	
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
	
	public static SimpleDirectedWeightedGraph<CC, ConnectionEdge> buildGraphFromDateMap(ImagePlus imgDates,int connexity) {
		trickOnImgDates(imgDates);
		int maxSizeConnexion=500000000;
		int nDays=1+(int)Math.round(VitimageUtils.maxOfImage(imgDates));
		Roi[][]roisCC=new Roi[nDays][];
		CC[][]tabCC=new CC[nDays][];
		SimpleDirectedWeightedGraph<CC,ConnectionEdge>graph=new SimpleDirectedWeightedGraph<>(ConnectionEdge.class);

		
		//Identify connected components with label 1-N
		roisCC[0]=new Roi[] {new Roi(new Rectangle(0,0,imgDates.getWidth(),imgDates.getHeight()))};
		tabCC[0]=new CC[] {new CC(0,0,roisCC[0][0],graph)};		
		System.out.print("Identifying connected components ");
		for(int d=1;d<nDays;d++) {
			System.out.print(d+" ");
			ImagePlus binD=VitimageUtils.thresholdImage(imgDates, d, d+0.99);
			ImagePlus ccD=VitimageUtils.connexeBinaryEasierParamsConnexitySelectvol(binD, connexity, 0);
			ImagePlus allConD=VitimageUtils.thresholdImageToFloatMask(ccD, 0.5, 10E8);
			VitimageUtils.waitFor(100);
			roisCC[d]=VitimageUtils.segmentationToRoi(allConD);
			if(roisCC[d]==null) {tabCC[d]=null;continue;}
			tabCC[d]=new CC[roisCC[d].length];
			for(int n=0;n<roisCC[d].length;n++) {
				CC cc=new CC(d,n,roisCC[d][n],graph);
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
						if((d2<d1) || ( (d2==d1) && (n2<=n1) ))continue;//TODO : normalement la partie deux ne se produit jamais. A verifier ?
						double[] tabConn=tabCC[d1][n1].nFacets4connexe_V3(tabCC[d2][n2]); // TODO : on cherche les 4-voisinages entre composantes 8 connexes. Quel est le sens de ceci ?
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
				
	//* If nbTrees>=1, select nbTrees elements of day 1  
	//* Build element of day 0, and connect all selected elements of day 1
	//* Remove all elements not connected
	public static void pruneGraph(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,int nbTrees,boolean removeUnconnectedParts) {
		//Find border of the tree, and define center
		double minX=10000000;
		double maxX=0;
		for(CC cc : graph.vertexSet()) {
			if(cc.r.getContourCentroid()[0]>maxX)maxX=cc.r.getContourCentroid()[0];
			if(cc.r.getContourCentroid()[0]<minX)minX=cc.r.getContourCentroid()[0];
		}
		
		//Connect roots to a central point
		Roi r=new Roi(new Rectangle((int) ((minX+maxX)/2),10,1,1));
		CC source=new CC(0,1,r,graph) ;
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
					if(cc.nPixels>max) {
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
						if(graph.getEdgeSource(edge).stamp==0) {//TODO : et il se passe quoi si je retire la recherche vers les sources ? Normalement c est pas necessaire
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
	
	
	
	
	public static SpanningTree<ConnectionEdge> computeMinimumSpanningTreeGraph(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph) {		
		System.out.println("Start spanning tree");
		SpanningTree<ConnectionEdge> tree=new KruskalMinimumSpanningTree<>(graph).getSpanningTree();
		System.out.println("Ok.");
		return tree;
	}
	
		
	public static void computeMinimumDirectedConnectedSpanningTree(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph) {		
		System.out.print("Computing connected directed minimum spanning tree");
		CC cctest=getCC(graph,5767,1735);
		System.out.println("DEB 1");
		for(ConnectionEdge edge : graph.outgoingEdgesOf(cctest)) {
			System.out.println(edge);
		}
		System.out.println("DEB 2");
		
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
				boolean debug=(cc==getCC(graph, 18, 5719, 1874));
				ConnectionEdge edgeMin=null;
				double minCost=1E16;
				ConnectionEdge edgeMinConnected=null;
				double minCostConnected=1E16;
				if(debug)System.out.println("Processing "+cc);
				for(ConnectionEdge edge:graph.incomingEdgesOf(cc)) {
					if((graph.getEdgeWeight(edge)<minCostConnected) && (graph.getEdgeSource(edge).stamp==1) /*&& ((cc.day==(graph.getEdgeSource(edge).day+1))  || (graph.getEdgeSource(edge).trunk))*/) {
						minCostConnected=graph.getEdgeWeight(edge);
						edgeMinConnected=edge;
					}
					if((graph.getEdgeWeight(edge)<minCost)/* && ((cc.day==(graph.getEdgeSource(edge).day+1)) || (graph.getEdgeSource(edge).trunk))*/)  {
						minCost=graph.getEdgeWeight(edge);
						edgeMin=edge;
					}
				}
				//if(edgeMinConnected!=null) {
				//	if(debug)System.out.println("H1, with edgeMinCon="+edgeMinConnected);
				//	for(ConnectionEdge edge:graph.incomingEdgesOf(cc))edge.activated=(edge==edgeMinConnected);
				//	cc.stamp=1;
				//	if(debug)System.out.println("H11, with edgeMinCon="+edgeMinConnected);
				//}
				if(edgeMin!=null) {
					if(debug)System.out.println("H2");
					for(ConnectionEdge edge:graph.incomingEdgesOf(cc))edge.activated=(edge==edgeMin);
					cc.stamp=graph.getEdgeSource(edgeMin).stamp;
				}
				else {
					if(debug)System.out.println("H3");
					for(ConnectionEdge edge:graph.incomingEdgesOf(cc))edge.activated=false;
					cc.stamp=(++currentCC);
				}
			}
		}				
		System.out.println("DEB 2");
		for(ConnectionEdge edge : graph.outgoingEdgesOf(cctest)) {
			System.out.println(edge);
		}
		System.out.println("DEB 2");
		updateCostAndDisconnectNonOptimalLateralBranches_V2(graph);
		System.out.println("DEB 3");
		for(ConnectionEdge edge : graph.outgoingEdgesOf(cctest)) {
			System.out.println(edge);
		}
		System.out.println("DEB 3");
		System.out.println("\n -> End of minimum directed connected spanning tree. Total number of components = "+currentCC+" (higher than expected nTrees means probably disconnected laterals emerged)");
		//VitimageUtils.waitFor(40000000);
	}

	
	
	public static void updateCostAndDisconnectNonOptimalLateralBranches_V1(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph){
		//update costs and disactivateNonOptimalLateralBranches
	
		for(CC cc:graph.vertexSet()) {
			boolean debug=(cc==getCC(graph, 17, 5605, 1796));
			if(debug)System.out.println("Disactivate neighbours of ?"+cc);
			if(cc.trunk)continue;
			ConnectionEdge bestEdge=cc.bestOutgoingEdge();
			for(ConnectionEdge edge : graph.outgoingEdgesOf(cc)) {
				if(debug)System.out.println("Processing an edge "+edge);
				if(edge!=bestEdge)  {
					if(debug)System.out.println("Disactivating this." );
					edge.activated=false;
				}
			}
		}
	}
	
	public static void updateCostAndDisconnectNonOptimalLateralBranches_V2(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph){
		//The idea : coming from N-1, for each CC that is no trunk, select the successor with the biggest number of followers, counted in pixels
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

	
	
	public static void reconnectDisconnectedBranches_v2(ImagePlus img2, SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,int formalism,boolean workAlsoBranches,boolean hack) {
		ImagePlus img=getDistanceMapsToDateMaps(img2);

		System.out.println("\nReconnection of disconnected branches");
		Timer t=new Timer();
		t.print("Start");
		ArrayList<CC>listStart=new ArrayList<CC>();
		ArrayList<CC>listStop=new ArrayList<CC>();
		int[]associations=null;
		double thresholdScore=PENALTY_COST;

		int Nalso=0;
		ArrayList<ConnectionEdge>tabKeepEdges=new ArrayList<ConnectionEdge>();
		ArrayList<CC[]>tabKeepCCStart=new ArrayList<CC[]>();

		//Disconnect branching on trunk
		if(workAlsoBranches) {
			for(CC cc:graph.vertexSet()) {
				if(!cc.trunk)continue;
				for(ConnectionEdge edge : graph.outgoingEdgesOf(cc)) {
					if(edge.activated && (!edge.target.trunk)) {//TODO : made modif here (detect prim to lat)
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
				if(tot>=MIN_SIZE_CC) {//TODO : pourquoi ne pas classer par ordre, et trier les priorités aussi par taille. BTW, removed  && cc.y()>150
					listStart.add(cc);
					cc.stamp=tot;
				}
			}
		}

		
		//Identify the possible dead ends 
		for(CC cc: graph.vertexSet()) {
			boolean deb=cc.day==19 && cc.n==35;
			if(deb)System.out.println("DEBB 0");
			if(cc.trunk)continue;
			if(deb)System.out.println("DEBB 1");
			if(cc.bestOutgoingActivatedEdge()==null){
				if(deb)System.out.println("DEBB 2");
				//Si pas de outgoing, et un incoming seulement, mais venant de latéral et date est max
				//if( graph.incomingEdgesOf(cc).size()==1 && graph.outgoingEdgesOf(cc).size()==0 && ! cc.bestIncomingEdge().source.trunk) continue;
				if(deb)System.out.println("DEBB 3");

				CC cctmp=cc;
				int tot=cc.nPixels;
				if(deb)System.out.println("DEBB 4");
				while(cctmp.bestIncomingActivatedEdge()!=null && (!cctmp.bestIncomingActivatedEdge().source.trunk)) {//TODO : removed && (!cctmp.bestIncomingActivatedEdge().trunk 
					cctmp=cctmp.bestIncomingActivatedEdge().source;
					tot+=cctmp.nPixels;
				}
				if(deb)System.out.println("DEBB 5 : "+tot);
				//Count size
				if(tot>=MIN_SIZE_CC ) {//TODO  : removed && cc.y()>150 that seemed not useful
					listStop.add(cc);
					cc.stamp=tot;
				}
			}
		}

		
		//Algorithme hongrois
		int Nstart=listStart.size();
		int Nstop=listStop.size();
		if(Nstart==0 || ((Nstop+Nalso==0)))return;
		associations=new int[Nstop];
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
		while(!finished) {

			CC ccstoptest=getCC(graph,2026,2498);
			CC ccstarttest=getCC(graph,2025,2550);
			
			Timer t2=new Timer();
			t2.print("1");
			//Build score matrix
			int nStill=0;
			for(int i=0;i<Nstop;i++) {
	            for(int j=0;j<Nstart;j++) {    
	            	boolean debug=((i==47) && (j==205));
	            	if(listStart.get(j)==ccStartWant && listStop.get(i)==ccStopWant) { debug=true;System.out.println("DEBUGGG");}
	            	if(listStop.get(i)==listStart.get(j))costMatrix[i][j]=IDENTITY_PENALTY;
	            	else {
	            		if(listStop.get(i).associateSuiv==listStart.get(j))costMatrix[i][j]=-VitimageUtils.EPSILON;
	            		else {
	            			if(listStop.get(i).associateSuiv!=null || listStart.get(j).associatePrev!=null)costMatrix[i][j]=10000;
	            			else{
	            				nStill++;
	            				if(listStop.get(i).changedRecently || listStart.get(j).changedRecently) {	
	            					if(listStop.get(i)==ccstoptest && listStart.get(j)==ccstarttest) {debug=true;}
	            					costMatrix[i][j]=weightingOfPossibleHiddenEdge_v2(img,graph,listStop.get(i),listStart.get(j),debug);
	            					if(debug) {
	            						//IJ.showMessage("Cost="+costMatrix[i][j]);
	            						//VitimageUtils.waitFor(50000);
	            					}
	            					if(Double.isNaN(costMatrix[i][j]))	System.out.println("I="+i+" J="+j);
	            					
	            				}
	            			}
	            		}
	            	}
	            	if(debug)System.out.println("FINAL VAL="+costMatrix[i][j]);
	            }
	        }
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
		    weightingOfPossibleHiddenEdge_v2(img,graph,listStop.get(bestI),listStart.get(bestJ),true);
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

	
	
	public static RootModel refinePlongementOfCCGraph(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,ImagePlus distOut,double toleranceDistToCentralLine) {
		System.out.println("Running the plongement");
		//Prepare dataOut
		FSR sr= (new FSR());
		sr.initialize();
		RootModel rm=new RootModel();

		
		//Exclude cc that were outed
		ArrayList<CC>cctoExclude=new ArrayList<CC>();
		for(CC cc : graph.vertexSet()) if(cc.isOut)  cctoExclude.add(cc);
		for(CC cc : cctoExclude      ) graph.removeVertex(cc);

		
		//Identify some features
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
		
		//Processing primary roots
		//dessiner la somme de pathlines des racines et l'associer aux CC. 		
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
			
			
			//Separate dijkstra path processing of the respective parts
			//PRIMARY ROOTS
			//Compute starting distance (when be for lateral)
			double startingDistance=0;
			ArrayList<Double>distInter=new ArrayList<Double>();
			ArrayList<Double>timeInter=new ArrayList<Double>();
			double cumulatedDistance=startingDistance;//TODO : compute distance from stuff
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
				else 	{
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
					timeInter.add(new Double( lcc.get(0).day-1 ));
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
						distInter.add(new Double(distMax));	
						timeInter.add(new Double(lcc.get(i).day));	
						toKeep.get(indl).add(indMax);
					}
					if(debugPrim)					System.out.println("Adding a point at indl="+indl+" indcc="+0+" time="+timeInter.get(timeInter.size()-1)+" dist="+distInter.get(timeInter.size()-1));
				}
				if(indl==llcc.size()-1) {
					distInter.add(new Double(ccFuse.mainDjikstraPath.get(ccFuse.mainDjikstraPath.size()-1).wayFromPrim));	
					timeInter.add(new Double(lcc.get(lcc.size()-1).day));	
				}
 
			}	
			//Convert results of correspondance into double tabs
			int N=distInter.size();double[]xPoints=new double[N];double[]yPoints=new double[N];for(int i=0;i<N;i++) {xPoints[i]=distInter.get(i);yPoints[i]=timeInter.get(i);}	
				

			
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
						p.wayFromPrim=p2.wayFromPrim;
						p2.offX=c.xB;
						p2.offY=c.yB;
					}
				}

				//Subsample respective dijkstra path with beucker algorithm, and collect RSML points with speed and 
				List<Pix> list= simplerSimplify ? DouglasPeuckerSimplify.simplifySimpler(ccF.mainDjikstraPath,toKeep.get(li) ,3) :
					DouglasPeuckerSimplify.simplify(ccF.mainDjikstraPath,toKeep.get(li) ,toleranceDistToCentralLine);
				for(int i=0;i<list.size()-1;i++) {
					Pix p=list.get(i);
					rPrim.addNode(p.x+ccF.xB,p.y+ccF.yB,p.time,(i==0)&&(li==0));
				}

				Pix p=list.get(list.size()-1);
				rPrim.addNode(p.x+ccF.xB,p.y+ccF.yB,p.time,false);
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
			if(debugLat)	System.out.println("\nProcessing lateral root #"+(incrLat++)+" : "+cc);
			
			//Identification of correspondant primary root
			Root myRprim=null;
			for(int i=0;i<listRprim.size();i++) {
				if(listNprim.get(i)==cc.bestIncomingActivatedEdge().source.n)myRprim=listRprim.get(i);
			}
			if(myRprim==null) {
				System.out.println("Here in RAG L1373");
				System.out.println("Processing a new latstart that is "+cc);
				for(ConnectionEdge con : graph.edgesOf(cc))System.out.println(con);
				System.out.println("En effet,"+cc.ccPrev);
				IJ.showMessage("Rprimnull at "+cc);
				
			}

			
			
			//Identification of connected part of the root
			CC ccNext=cc;
			ArrayList<ArrayList<CC>> llcc=new ArrayList<ArrayList<CC>>();
			ArrayList<ArrayList<Integer>>toKeep=new ArrayList<ArrayList<Integer>>();
			ArrayList<CC>lccFuse=new ArrayList<CC>();
			llcc.add(new ArrayList<CC>());
			llcc.get(0).add(ccNext);
			int ind=0;
			while(ccNext.getLatChild()!=null) {
				if(ccNext.isHiddenLatChild()) {
					llcc.add(new ArrayList<CC>());
					ind++;
				}
				ccNext=ccNext.getLatChild();
				llcc.get(ind).add(ccNext);
				if(debugLat)	System.out.println("Adding "+ccNext+" to array number "+ind);
			}
		
			//Separate dijkstra path processing of the respective parts
			//LATERAL ROOTS//////////////////////////////////////////////////////////////////////////////////////
			//Compute starting distance (when be for lateral)
			double startingDistance=0; //TODO
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
				
				
				//Identify target point
				if(indl==(llcc.size()-1)) {
					if(debug)System.out.println("End of lateral : "+lcc.get(lcc.size()-1));
					//End of primary : Identify target in ccLast
//					int[]coords=ccLast.getNextSourceFromFacetConnexion(ccLast.bestIncomingActivatedEdge()); //currentSource;
					//currentTarget=ccLast.determineTargetGeodesicallyFarestFromTheSource(coords);
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
					timeInter.add(new Double( lcc.get(0).day-1 ));
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
						distInter.add(new Double(distMax));	
						timeInter.add(new Double(lcc.get(i).day));	
						toKeep.get(indl).add(indMax);
					}
				}
				if(indl==llcc.size()-1) {
					distInter.add(new Double(ccFuse.mainDjikstraPath.get(ccFuse.mainDjikstraPath.size()-1).wayFromPrim));	
					timeInter.add(new Double(lcc.get(lcc.size()-1).day));	
				}
			}	
			//Convert results of correspondance into double tabs
			int N=distInter.size();double[]xPoints=new double[N];double[]yPoints=new double[N];for(int i=0;i<N;i++) {xPoints[i]=distInter.get(i);yPoints[i]=timeInter.get(i);}	
				
			
			
			
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
					rLat.addNode(p.x+ccF.xB,p.y+ccF.yB,p.time,(i==0)&&(li==0));
				}	
				Pix p=list.get(list.size()-1);
				rLat.addNode(p.x+ccF.xB,p.y+ccF.yB,p.time,false);
				if(li!=(lccFuse.size()-1))rLat.setLastNodeHidden();
			}
			rLat.computeDistances();
			rLat.computeDistances();
			myRprim.attachChild(rLat);
			rLat.attachParent(myRprim);
			rm.rootList.add(rLat);
		}
		return rm;
	}



	
	

	
	/** 
	 * Various helpers ----------------------------------------------------------------------------------------------------------------------------------------*/
	 //Determine if ccStop and ccStart are connected in the undirected region adjacency graph limited to ccStop, ccStart and older CC
	public static int areConnectedByPathOfCC(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,CC ccStop,CC ccStart,boolean debug) {
		int Nmax=10000;//ccStart.day-1;
		for(CC cc: graph.vertexSet()) cc.stamp2=0;			
		ccStop.stamp2=1;
		ArrayList<CC>visited=new ArrayList<CC>();
		ArrayList<CC>toVisit=new ArrayList<CC>();
		visited.add(ccStop);
		if(ccStop.bestIncomingActivatedCC()!=null)ccStop.bestIncomingActivatedCC().stamp=1;
		boolean finished=false;
		int iter=-1;
		while(!finished) {
			iter++;
			if(debug)System.out.print("iter="+iter+" : "+visited.size());
			for(int i=0;i<visited.size();i++) {				
				CC ccTemp=visited.get(i);
				for(ConnectionEdge edge : graph.outgoingEdgesOf(ccTemp)) {
					CC ccTrial=edge.target;
					if( ccTrial.stamp2>0)continue;
					if (ccTrial.day<1)continue; 
					if( (ccTrial.day<=Nmax) || (ccTrial==ccStart) ) {
						toVisit.add(ccTrial);
						ccTrial.ccPrev=ccTemp;
						ccTrial.stamp2=1;
					}
				}
				for(ConnectionEdge edge : graph.incomingEdgesOf(ccTemp)) {
					CC ccTrial=edge.source;
					if( ccTrial.stamp2>0)continue;
					if (ccTrial.day<1)continue; 
					if( (ccTrial.day<=Nmax) || (ccTrial==ccStart) ) {
						toVisit.add(ccTrial);
						ccTrial.stamp2=1;
						ccTrial.ccPrev=ccTemp;
					}
				}			
			}
			if(toVisit.size()==0)finished=true;
			if(ccStart.stamp2==1)finished=true;
			visited=toVisit;
			toVisit=new ArrayList<CC>();
		}
		if(debug) {
			if(ccStart.stamp2==0)System.out.println("\nPath not found !");
			else {
				System.out.println("\nPath found");
				CC ccT=ccStart;
				while(ccT!=ccStop) {ccT=ccT.ccPrev;System.out.println(ccT);}
			}
		}
		if(ccStart.stamp2>0)return iter;
		else return -1;
	}

	 //Determine if ccStop and ccStart are connected in the undirected region adjacency graph limited to ccStop, ccStart and older CC
	public static int areConnectedByPathOfCC_v2(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,CC ccStop,CC ccStart,boolean debug) {
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
						double scoreAdd=ccTrial.euclidianDistanceToCC(ccTemp.lastCCinLat)/MEAN_SPEED_LAT;
						if( ccTrial.stampDist> (ccTemp.stampDist+scoreAdd) ) {
							toVisit.add(ccTrial);
							ccTrial.stampDist=ccTemp.stampDist+scoreAdd;
						}
					}
				}
				for(ConnectionEdge edge : graph.incomingEdgesOf(ccTemp)) {
					CC ccTrial=edge.source;
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
						double scoreAdd=ccTrial.euclidianDistanceToCC(ccTemp.lastCCinLat)/MEAN_SPEED_LAT;
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
	
	
	
	public static double ratioInObject(ImagePlus img,double x0,double y0,double x1,double y1) {
		int X=img.getWidth();
		int Y=img.getHeight();
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
	

	//Compute how much the straight computed path is far away from expected structures.
	//For path going lower than 5 pixels, path is at no cost
	//For 5-10, each pixel contributes to mean from 0.0 to 1.0
	//For 10-20, each pixel contributes to mean from 1.0 to 3.0
	public static double costDistanceOfPathToObject(ImagePlus img,double x0,double y0,double x1,double y1) {
		//System.out.println("Starting from "+x0+","+y1+" to "+x1+","+y1);
		//System.out.println(x0+","+y0+" to "+x1+","+y1);
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
	
	
	
	
	
	//Evaluating the reconnexion of ccStop and ccStart, two secondary nodes
	public static double weightingOfPossibleHiddenEdge_v2(ImagePlus img,SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,CC ccStop,CC ccStart,boolean debug) {
		//if(ccStop.day==17 && ccStart.day==17 && ccStop.n==54 && ccStart.n==66)debug=true;
		if(ccStop.day>ccStart.day)return REVERSE_TIME_PENALTY;
		if(ccStop==ccStart)return REVERSE_TIME_PENALTY;
		if(ccStop.bestIncomingActivatedCC()==ccStart)return REVERSE_TIME_PENALTY;
		if(ccStart.bestIncomingActivatedCC()==ccStop)return REVERSE_TIME_PENALTY;
		int nStop=1;
		int nStart=1;
		CC ccPrec=null;
		CC ccSuiv=null;
		
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
		double dtCross=ccStart.day-ccStop.day;
		if(dtCross==0)dtCross=0.7;
		double crossSpeed=ccStop.euclidianDistanceToCC(ccStart)/dtCross;
		double dtStop=0;
		double stopSpeed=0;
		double dtStart=0;
		double startSpeed=0;
		if(nStop>1) {
			dtStop=ccStop.day-ccPrec.day;
			if(dtStop==0)dtStop=0.7;
			stopSpeed=ccStop.euclidianDistanceToCC(ccPrec)/dtStop;
			tabGamma[2]=1-VitimageUtils.similarity(stopSpeed,crossSpeed);
		}
		else tabGamma[2]=0.5;
		
		if(nStart>1) {
			dtStart=ccSuiv.day-ccStart.day;
			if(dtStart==0)dtStart=0.7;
			startSpeed=ccStart.euclidianDistanceToCC(ccSuiv)/dtStart;
			tabGamma[3]=1-VitimageUtils.similarity(startSpeed,crossSpeed);
		}
		else tabGamma[3]=0.5;
		
		
		//Orientation score
		tabGamma[4]=(1-0.5*orientationDownwards(ccStop, ccStart));
		if(Double.isNaN(tabGamma[4]))tabGamma[4]=1;
		
		//Connection score
		int nbSteps=areConnectedByPathOfCC_v2(graph,ccStop,ccStart,debug);
		int lNorm=3;
		tabGamma[5]=(1.0/lNorm)*Math.abs(nbSteps-dtCross-2.5)+(dtCross<2 ? 0 : 0.15*(dtCross-1));
		
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
		double maxSpeedLat=MAX_SPEED_LAT;
		double meanSpeedLat=10;
		if(  ((ccStop.euclidianDistanceToCC(ccStart))/(Math.max(0.7, ccStart.day-ccStop.day))) >maxSpeedLat) {
			tabGamma[9]=5*(  ((ccStop.euclidianDistanceToCC(ccStart))/(Math.max(0.7, ccStart.day-ccStop.day)))-maxSpeedLat)/maxSpeedLat;
		}
		
//		tabGamma[10]=  20.0/ccStop.nPixels;
//		tabGamma[11]=  20.0/ccStart.nPixels;
		
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

	//Evaluating the reconnexion of ccStop and ccStart, two secondary nodes
	public static double weightingOfPossibleHiddenEdge_v3(ImagePlus img,SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,CC ccStop,CC ccStart,boolean debug) {
		//if(ccStop.day==17 && ccStart.day==17 && ccStop.n==54 && ccStart.n==66)debug=true;
		if(ccStop.day>ccStart.day)return REVERSE_TIME_PENALTY;
		if(ccStop==ccStart)return REVERSE_TIME_PENALTY;
		if(ccStop.bestIncomingActivatedCC()==ccStart)return REVERSE_TIME_PENALTY;
		if(ccStart.bestIncomingActivatedCC()==ccStop)return REVERSE_TIME_PENALTY;
		int nStop=1;
		int nStart=1;
		CC ccPrec=null;
		CC ccSuiv=null;
		
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
		if(nStop>1)  tabGamma[0]=(1-prodScal(ccPrec, ccStop, ccStop, ccStart));
		else tabGamma[0]=0.5;
		if(nStart>1)tabGamma[1]=(1-prodScal(ccStop, ccStart, ccStart, ccSuiv));
		else tabGamma[1]=0.5;
		if(Double.isNaN(tabGamma[0]))tabGamma[0]=0.5;
		if(Double.isNaN(tabGamma[1]))tabGamma[1]=0.5;
		
		
		
		//Speed score
		double dtCross=ccStart.day-ccStop.day;
		if(dtCross==0)dtCross=0.7;
		double crossSpeed=ccStop.euclidianDistanceToCC(ccStart)/dtCross;
		double dtStop=0;
		double stopSpeed=0;
		double dtStart=0;
		double startSpeed=0;
		if(nStop>1) {
			dtStop=ccStop.day-ccPrec.day;
			if(dtStop==0)dtStop=0.7;
			stopSpeed=ccStop.euclidianDistanceToCC(ccPrec)/dtStop;
			tabGamma[2]=1-VitimageUtils.similarity(stopSpeed,crossSpeed);
		}
		else tabGamma[2]=0.5;
		
		if(nStart>1) {
			dtStart=ccSuiv.day-ccStart.day;
			if(dtStart==0)dtStart=0.7;
			startSpeed=ccStart.euclidianDistanceToCC(ccSuiv)/dtStart;
			tabGamma[3]=1-VitimageUtils.similarity(startSpeed,crossSpeed);
		}
		else tabGamma[3]=0.5;
		
		
		//Orientation score
		tabGamma[4]=(1-orientationDownwards(ccStop, ccStart));
		if(Double.isNaN(tabGamma[4]))tabGamma[4]=1;
		
		//Connection score
		int nbSteps=areConnectedByPathOfCC_v2(graph,ccStop,ccStart,debug);
		int lNorm=3;
		tabGamma[5]=(1.0/lNorm)*Math.abs(nbSteps-dtCross-2.5);
		
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
		double maxSpeedLat=MAX_SPEED_LAT;
		double meanSpeedLat=10;
		if(  ((ccStop.euclidianDistanceToCC(ccStart))/(Math.max(0.7, ccStart.day-ccStop.day))) >maxSpeedLat) {
			tabGamma[9]=5*(  ((ccStop.euclidianDistanceToCC(ccStart))/(Math.max(0.7, ccStart.day-ccStop.day)))-maxSpeedLat)/maxSpeedLat;
		}
		
		
		double globScore=0;
		double globWeight=0;

		for(int i=0;i<10;i++) {
			if(debug)System.out.println("Score "+i+"="+tabGamma[i]+" weight="+tabW[i]);
			if(Double.isNaN( tabGamma[i]))tabGamma[i]=1;
			globScore+=(tabGamma[i]*tabW[i]);
			globWeight+=tabW[i];
		}
		if(debug)System.out.println("Result="+(globScore/globWeight));
		return globScore/globWeight;
	}



	public static double weightingOfPossibleHiddenEdge(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,CC ccStop,CC ccStart,boolean debug) {
		if(debug)System.out.println("\nH021 weighting with "+ccStop);
		if(ccStop.day>ccStart.day)return REVERSE_TIME_PENALTY;
		CC ccStartNext=ccStart.bestOutgoingActivatedCC();
		if(ccStartNext !=null && ccStartNext.trunk)ccStartNext=null;
		CC ccStopPrevious=ccStop.bestIncomingActivatedCC();
		if(ccStopPrevious !=null && ccStopPrevious.trunk)ccStopPrevious=null;

		//CONNECTED
		double connectedWeight=0;
		//if a way does not exist
		int way=areConnectedByPathOfCC(graph,ccStop,ccStart,debug);
		if(way<0)connectedWeight=OUT_OF_SILHOUETTE_PENALTY;
		else connectedWeight=way*2;
		if(debug)System.out.println("H022 establishing connexity weight="+connectedWeight);

		
		//ESTIMATE SPEED AND ANGULAR CONFORMATION
		double speed=TYPICAL_SPEED;
		double angularWeight=0;
		if(ccStartNext!=null && ccStopPrevious !=null) {
			double speedStop=ccStop.euclidianDistanceToCC(ccStopPrevious)/(Math.min (1,ccStop.day-ccStopPrevious.day));
			double speedStart=ccStart.euclidianDistanceToCC(ccStartNext)/(Math.min (1,ccStartNext.day-ccStart.day));
			speed=0.5*(speedStart+speedStop);
			angularWeight-=prodScal(ccStopPrevious,ccStop,ccStart,ccStartNext);
			angularWeight-=prodScal(ccStopPrevious,ccStartNext,ccStop,ccStart);
			angularWeight-=prodScal(ccStopPrevious,ccStop,ccStop,ccStart);
			angularWeight-=prodScal(ccStop,ccStart,ccStart,ccStartNext);
		}
		else if(ccStartNext!=null){
			double speedStart=ccStart.euclidianDistanceToCC(ccStartNext)/(Math.min (1,ccStartNext.day-ccStart.day));
			speed=0.5*(speedStart+TYPICAL_SPEED);
			angularWeight-=prodScal(ccStop,ccStart,ccStart,ccStartNext)*4;
		}
		else if(ccStopPrevious!=null){
			double speedStop=ccStop.euclidianDistanceToCC(ccStopPrevious)/(Math.min (1,ccStop.day-ccStopPrevious.day));
			speed=0.5*(speedStop+TYPICAL_SPEED);
			angularWeight-=prodScal(ccStopPrevious,ccStop,ccStop,ccStart)*4;
		}
		angularWeight*=4;
		if(debug)System.out.println("Estimated speed="+speed+" and angweight="+angularWeight);
		

		//DISTANCE Is the pathway length likely ?
		double deltaDay=ccStart.day-ccStop.day;
		if (deltaDay<=0)deltaDay=0.75;
		double expectedDistance=speed*(deltaDay);
		double actualDistance=ccStop.euclidianDistanceToCC(ccStart);
		if(debug)System.out.println("H026 actualDist="+actualDistance);
		if(debug)System.out.println("H026 expectedDist="+expectedDistance);
		double distanceWeight=3*((1-VitimageUtils.similarity(expectedDistance, actualDistance))*deltaDay+Math.abs(actualDistance-expectedDistance)/(deltaDay*expectedDistance));
		if(debug)System.out.println("H026 distweight="+distanceWeight);

		
		//OVERALL ORIENTATION Is it pointing downwards ?
		double[]vectStart=new double[] {ccStart.x(),ccStart.y(),0};
		double[]vectStop=new double[] {ccStop.x(),ccStop.y(),0};
		double[]vectStopToStart=TransformUtils.vectorialSubstraction(vectStart, vectStop);
		vectStopToStart=TransformUtils.normalize(vectStopToStart);
		double orientationWeight=-vectStopToStart[1];
		if(debug)System.out.println("H027 orientweight="+orientationWeight);
		double finalWeight=connectedWeight + angularWeight + distanceWeight + orientationWeight;
		if(debug)System.out.println("H028 finalWeight="+finalWeight);
		return finalWeight;
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
	
	public static void setFirstOrderCosts_phase2(SimpleDirectedWeightedGraph<CC,ConnectionEdge>graph) {
		int nDays=getMaxDay(graph);
		for(ConnectionEdge edge:graph.edgeSet()) {
			graph.setEdgeWeight(edge, getCostFirstOrderConnection_phase2(edge,nDays));
		}
	}
	public static void setFirstOrderCosts_phase1(SimpleDirectedWeightedGraph<CC,ConnectionEdge>graph) {
		int nDays=getMaxDay(graph);
		for(ConnectionEdge edge:graph.edgeSet()) {
			graph.setEdgeWeight(edge, getCostFirstOrderConnection(edge,nDays));
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
		System.out.print("\nCalcul des troncs.");
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
		System.out.println();
	}

	
	
	
	public static void identifyRootSystems(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph) {

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
	
	public static void lookForSimplifyingPath() {
		//Identify the branching
		
		//Identify the most Costy branching backwards
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
				boolean debug=(index==1822499);
				if(debug)System.out.println("Debugging in drawDistanceOrTime in RAG");
				if(debug)System.out.println("The CC is "+cc);
				if(debug)System.out.println("The Pix is "+p);
				if(debug)System.out.println("The x0 y0 is "+x0+" , "+y0);
				
				if(debug)VitimageUtils.printImageResume(imgDist);
				if(onlyDoSkeleton && (!p.isSkeleton))continue;
				if(mode_1Total_2OutsideDistOrIntTime_3SourceDist==1) 	valDist[index]=(trueDoDistFalseDoTime) ? ((float) (p.wayFromPrim+p.distOut)) : (float)p.timeOut ;
				else if(mode_1Total_2OutsideDistOrIntTime_3SourceDist==2) 	valDist[index]=(trueDoDistFalseDoTime) ? ((float) (p.distOut)) : (float)(cc.day) ;
				else                                             	valDist[index]=(trueDoDistFalseDoTime) ? ((float) (p.wayFromPrim)) : (float)(p.time) ;
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
	


	
	public static double getCostOfAlignmentOfLastPointGivenTwoFirst(CC cc1,CC cc2,CC cc3,boolean debug) {
		//7117 3389
		if(debug) {
		System.out.println(cc1);
		System.out.println(cc2);
		System.out.println(cc3);
		}
		int delta12Day=Math.abs(cc1.day-cc2.day);
		int delta23Day=Math.abs(cc3.day-cc2.day);
		if(delta12Day==0)delta12Day=1;
		if(delta23Day==0)delta23Day=1;
		double[]vect12=new double[] {cc2.r.getContourCentroid()[0]-cc1.r.getContourCentroid()[0],cc2.r.getContourCentroid()[1]-cc1.r.getContourCentroid()[1],0};
		double[]vect12PerDay=TransformUtils.multiplyVector(vect12, 1/delta12Day);
		double distPerDay=TransformUtils.norm(vect12PerDay);
		double xExpected=cc2.r.getContourCentroid()[0]+vect12PerDay[0]*delta23Day;
		double yExpected=cc2.r.getContourCentroid()[1]+vect12PerDay[1]*delta23Day;
		double xReel=cc3.r.getContourCentroid()[0];
		double yReel=cc3.r.getContourCentroid()[1];
		double relativeDistInDayPerDay=VitimageUtils.distance(xReel, yReel, xExpected, yExpected)/(distPerDay*delta23Day);
		return relativeDistInDayPerDay;
	}
	
	public static double getCostFirstOrderConnection(ConnectionEdge edge,int nDays) {
		CC source=edge.source;
		CC target=edge.target;
		if(source.day==0)return (-1000);
		if(source.day>target.day)return (10000);
		double dx=target.r.getContourCentroid()[0]-source.r.getContourCentroid()[0];
		double dy=target.r.getContourCentroid()[1]-source.r.getContourCentroid()[1];
		double dday=Math.abs(target.day-source.day);
		double prodscal=dy/Math.sqrt(dx*dx+dy*dy);
		double cost=0;
		if((source.trunk) && (!target.trunk)) {
			cost+=(1-VitimageUtils.similarity(source.nPixels,target.nPixels)) + (nDays-1) + 1 + VitimageUtils.EPSILON;
		}
		else{			
			cost+=(1-VitimageUtils.similarity(source.nPixels,target.nPixels)) + (dday<2 ? 0 : dday-1)  -prodscal  ; 
		}
		return cost;//TODO : on peut laisser dday-1, le cas ne sert pas, à tester
	}
	
	public static double getCostFirstOrderConnection_phase2(ConnectionEdge edge,int nDays) {
		CC source=edge.source;
		CC target=edge.target;
		if(source.day==0)return (-1000);
		if(source.day>target.day)return (10000);
		double dx=target.r.getContourCentroid()[0]-source.r.getContourCentroid()[0];
		double dy=target.r.getContourCentroid()[1]-source.r.getContourCentroid()[1];
		double dday=Math.abs(target.day-source.day);
		double prodscal=dy/Math.sqrt(dx*dx+dy*dy);
		double cost=0;
		if((source.trunk) && (!target.trunk)) {
			cost+=(1-VitimageUtils.similarity(source.nPixels,target.nPixels)) + (nDays-1) + 1 + VitimageUtils.EPSILON;
		}
		else{			
			cost+=(1-VitimageUtils.similarity(source.nPixels,target.nPixels)) + (dday<2 ? 0 : dday-1)  -0.5-prodscal*0.5  ; 
		}
		return cost;//TODO : on peut laisser dday-1, le cas ne sert pas, à tester
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
			double dist=VitimageUtils.distance(x/SIZE_FACTOR, y/SIZE_FACTOR, cc.r.getContourCentroid()[0], cc.r.getContourCentroid()[1]);
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
			double dist=VitimageUtils.distance(x/SIZE_FACTOR, y/SIZE_FACTOR, cc.r.getContourCentroid()[0], cc.r.getContourCentroid()[1]);
			if(dist<minDist) {
				minDist=dist;
				ret=cc;
			}
		}
		return ret;
	}	

	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
/*


	public static void setSecondOrderCosts(SimpleDirectedWeightedGraph<CC,ConnectionEdge>graph) {
		ArrayList <Object[]>list=new ArrayList<Object[]>();
		int []incrViews=new int[6];
		for(CC cc : graph.vertexSet()) {
			if(cc.day<2)continue;

			//Identify best next node, if any
			CC ccBestNext=null;
			double minWeightNext=1E8;
			if(graph.outgoingEdgesOf(cc).size()>0) {
				for(ConnectionEdge edgeNext:graph.outgoingEdgesOf(cc)) {
					if(graph.getEdgeWeight(edgeNext)<minWeightNext) {
						ccBestNext=edgeNext.target;
						minWeightNext=graph.getEdgeWeight(edgeNext);
					}
				}				
			}
			
			//N-1 parents depth-first search
			for(ConnectionEdge edgePar : graph.incomingEdgesOf(cc)) {
				CC ccPar=graph.getEdgeSource(edgePar);
				for(ConnectionEdge edge2 : graph.outgoingEdgesOf(cc2)) {
					if(edge==edge2)continue;
					CC cc3=graph.getEdgeTarget(edge2);
					if(graph.incomingEdgesOf(cc3).size()<2)continue;

					CC bestParent=null;
					double minWeight=1E8;
					for(ConnectionEdge edge4:graph.incomingEdgesOf(cc3)) {
						if(graph.getEdgeWeight(edge4)<minWeight) {
							bestParent=edge4.source;
							minWeight=graph.getEdgeWeight(edge4);
						}
					}
					
					
					
					incrViews[4]++;
					if(cc.day<cc3.day)continue;
					incrViews[5]++;
					double conX=edge.connectionX/2.0+edge2.connectionX/2.0;
					double conY=edge.connectionY/2.0+edge2.connectionY/2.0;
					ConnectionEdge edge3=new ConnectionEdge(conX, conY, 0, cc3, cc);
					edge3.hidden=true;
					
					double cost=getCostFirstOrderConnection(edge3)+getCostOfAlignmentOfLastPointGivenTwoFirst(bestParent, cc3, cc,false);
					list.add(new Object[] {cc3,cc,edge3,new Double(cost)});
				}		
			}
		}
		for(Object[]tab:list) {
			graph.addEdge((CC)tab[0], (CC)tab[1],(ConnectionEdge)tab[2]);
			graph.setEdgeWeight((ConnectionEdge)tab[2], (Double)tab[3]);
		}
		for(int i=0;i<6;i++)System.out.println("INCR "+i+" : "+incrViews[i]);
		//VitimageUtils.waitFor(500000000);
	}
*/

	
	
	
	/*
	public static RootModel refinePlongementOfCCGraphOLD(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,ImagePlus distOut,double toleranceDistToCentralLine) {
		//Prepare dataOut
		float[]valDist=(float[])distOut.getStack().getProcessor(1).getPixels();
		int X=distOut.getWidth();

		//Identifiy some features
		for(CC cc : graph.vertexSet()) {
			if(cc.trunk) {
				if(cc.day==1)cc.isPrimStart=true;			
				if( cc.getPrimChild()==null) cc.isPrimEnd=true;
			}
			if(!cc.trunk) {
				if(cc.bestIncomingActivatedCC()!=null && cc.bestIncomingActivatedCC().trunk) cc.isLatStart=true;
				if( getLatChild(cc, graph)==null) cc.isLatEnd=true;				
			}
		}
		ArrayList<double[]>distanceTimeSamples;
		ArrayList<CC>allCCofBranch;

		
		
		
		FSR sr= (new FSR());
		sr.initialize();
		RootModel rm=new RootModel();
		
		


		
		ArrayList<Root>listRprim=new ArrayList<Root>();
		ArrayList<Integer>listNprim=new ArrayList<Integer>();
		ArrayList<Integer>listDprim=new ArrayList<Integer>();
		
		
		//Processing primary roots
		//dessiner la somme de pathlines des racines et l'associer aux CC. 
		int incr=0;
		for(CC cc : graph.vertexSet()) {
			if(cc.day==1 && cc.trunk) {
				Root rPrim=new Root(null, rm, "",1);
				allCCofBranch=new ArrayList <CC>();
				distanceTimeSamples=new ArrayList<double[]>();
				incr++;
				System.out.println("PROCESSING PLANT "+cc);
				CC curCC=cc;
				double d=0;
				distanceTimeSamples.add(new double[] {0,0});
				while(curCC!=null) {	
					listRprim.add(rPrim);
					listNprim.add(curCC.n);
					listDprim.add(curCC.day);
					allCCofBranch.add(curCC);
					curCC.determineVoxelShortestPathTrunkRoot();
					d=curCC.setDistancesToShortestPathTrunk();
					distanceTimeSamples.add(new double[] {d,curCC.day});
					curCC.updateAllDistancesToTrunk();
					Pt2d[]tabPt=null;
					tabPt=DouglasPeuckerSimplify.simplify(curCC.mainDjikstraPath,new ArrayList<Integer>(), toleranceDistToCentralLine);
					if(curCC.day==1 && tabPt.length>1) rPrim.addNode(tabPt[0].getX()+curCC.xB(),tabPt[0].getY()+curCC.yB(),0,true);
					for(int i=1; i<tabPt.length-1; i++) 	rPrim.addNode(tabPt[i].getX()+curCC.xB(),tabPt[i].getY()+curCC.yB(),-1,false);
					rPrim.addNode(tabPt[tabPt.length-1].getX()+curCC.xB(),tabPt[tabPt.length-1].getY()+curCC.yB(),curCC.day,false);
					curCC=curCC.getPrimChild();
				}

				//Interpolate time for all pixels of the trunkg
				distanceTimeSamples=hackOnDistanceTimeSamples(distanceTimeSamples);
				double[]distTab=new double[distanceTimeSamples.size()];
				double[]timeTab=new double[distanceTimeSamples.size()];
				for(int i=0;i<distTab.length;i++) {distTab[i]=distanceTimeSamples.get(i)[0];timeTab[i]=distanceTimeSamples.get(i)[1];}	
				for(int i=0;i<distTab.length;i++) {distTab[i]=distanceTimeSamples.get(i)[0];timeTab[i]=distanceTimeSamples.get(i)[1];}		
				for(CC ccT:allCCofBranch) {
					int x0=ccT.r.getBounds().x;
					int y0=ccT.r.getBounds().y;
					for(Pix p:ccT.pixGraph.vertexSet()) {
						int index=X*(p.y+y0)+(p.x+x0);
						p.distOut=valDist[index];
					}
					try{
						//PolynomialSplineFunction psf=SplineAndPolyLineUtils.getInterpolator(distTab,timeTab,true);
						ccT.interpolateTimeFromReferencePointsUsingLinearInterpolator(distTab,timeTab);
					}catch(org.apache.commons.math3.exception.NonMonotonicSequenceException e) {
						e.printStackTrace();
					}
				}
				
				//Interpolate time for all points of the rsml
				rPrim.computeDistances();
				rPrim.interpolateTime();
				rm.rootList.add(rPrim);
			}
		}
		
		//Pour chacune des departs de racine latérale		
		for(CC cc : graph.vertexSet()) {
			if(!cc.isLatStart)continue;
			System.out.println("B 01 Processing a lateral root : "+cc);

			Root myRprim=null;
			for(int i=0;i<listRprim.size();i++) {
				if(listNprim.get(i)==cc.bestIncomingActivatedEdge().source.n)myRprim=listRprim.get(i);
			}

			if(myRprim==null) {
				IJ.showMessage("Rprimnull at "+cc);
				
			}
			Root rLat=new Root(null,rm,"",2);
			
			ConnectionEdge previousEdge=cc.bestIncomingActivatedEdge();
			if(previousEdge.hidden) {IJ.log("Warning, we got a lateral root emerging from being shadowed by another root. Situation is critical : no computation done for this root");continue;}
			//Parcourir successivement toutes les connexions, et determiner le plus d'infos possible de type target source, distance
			distanceTimeSamples=new ArrayList<double[]>();
			double[]coordsStart=new double[]{previousEdge.connectionX,previousEdge.connectionY};
			double currentDistance=previousEdge.distanceConnectionTrunk;
			int[]coordsS;
			//Si le start de la laterale provient d'un edge caché, rechercher le point de start
			if(previousEdge.hidden) {
				double xLat=cc.r.getContourCentroid()[0]-cc.r.getBounds().x;
				double yLat=cc.r.getContourCentroid()[1]-cc.r.getBounds().y;
				int[][]sourceTarget=cc.findHiddenStartStopToInOtherCC(previousEdge.source, new int[] {(int)xLat,(int)yLat});
				coordsStart=new double[] {sourceTarget[0][0],sourceTarget[0][1]};
				coordsS=sourceTarget[0];
				currentDistance=VitimageUtils.distance(sourceTarget[0][0]+cc.xB(),sourceTarget[0][1]+cc.yB(),sourceTarget[1][0]+previousEdge.source.xB(),sourceTarget[1][1]+previousEdge.source.yB());
			}
			else coordsS=cc.getNextSourceFromFacetConnexion(previousEdge);
			distanceTimeSamples.add(new double[] {currentDistance,cc.day-1});
			
			//Parcours de la branche
			CC curCC=cc;
			allCCofBranch=new ArrayList <CC>();
			boolean ending=false;
			String branch="";
			incr=0;
			while(!ending) {
				incr++;
				branch+="-"+curCC.day+"-"+curCC.n+" ";
				allCCofBranch.add(curCC);

		
				ConnectionEdge nextEdge=curCC.bestOutgoingActivatedEdge();
				if(nextEdge==null) {//end of root
					ending=true;
					int[]coordsT=curCC.determineTargetGeodesicallyFarestFromTheSource(coordsS);
					curCC.determineVoxelShortestPath(coordsS,coordsT,8,null);
					currentDistance=curCC.setDistanceToShortestPath(currentDistance);	
					curCC.updateAllDistancesToTrunk();
					distanceTimeSamples.add(new double[] {currentDistance,curCC.day});
				}
				else if(!nextEdge.hidden) {//No virtual ending, no problem
					double[]coordsTarget=new double[]{nextEdge.connectionX,nextEdge.connectionY};
					int[]coordsT=curCC.getPrevTargetFromFacetConnexion(nextEdge);
					double deltaEnd=VitimageUtils.distance(coordsTarget[0]-curCC.xB(),coordsTarget[1]-curCC.yB(),coordsT[0],coordsT[1]);
					curCC.determineVoxelShortestPath(coordsS,coordsT,8,null);
					currentDistance=curCC.setDistanceToShortestPath(currentDistance) + deltaEnd;				
					curCC.updateAllDistancesToTrunk();
					distanceTimeSamples.add(new double[] {currentDistance,curCC.day});
					coordsS=nextEdge.target.getNextSourceFromFacetConnexion(nextEdge);
				}
				else if(nextEdge.hidden) {			//If virtual ending
					//Find the bridge : 1) nearer source point Pst in the target component, and 2) the target point  Pts in the current CC that is the nearer to Pst
					int[][]targetSource=curCC.findHiddenStartStopToInOtherCC(nextEdge.target, coordsS);
					int[]coordsT=targetSource[0];
					curCC.determineVoxelShortestPath(coordsS,coordsT,8,null);
					currentDistance=curCC.setDistanceToShortestPath(currentDistance);
					currentDistance+= VitimageUtils.distance(targetSource[2][0],targetSource[2][1],targetSource[3][0],targetSource[3][1]);
					curCC.updateAllDistancesToTrunk();
					coordsS=targetSource[1];
				}				
				Pt2d[]tabPt=simplifyPolyLine(curCC.mainDjikstraPath, toleranceDistToCentralLine, true);
				if((incr==1 || previousEdge.hidden) && tabPt.length>1) rLat.addNode(tabPt[0].getX()+curCC.xB(),tabPt[0].getY()+curCC.yB(),incr==1 ? curCC.day-1 : -1,incr==1);
				for(int i=1; i<tabPt.length-1; i++) 	rLat.addNode(tabPt[i].getX()+curCC.xB(),tabPt[i].getY()+curCC.yB(),-1,false);
				rLat.addNode(tabPt[tabPt.length-1].getX()+curCC.xB(),tabPt[tabPt.length-1].getY()+curCC.yB(),curCC.day,false);
				if(nextEdge!=null && nextEdge.hidden)rLat.setLastNodeHidden();
				if(!ending) curCC=nextEdge.target;
				previousEdge=nextEdge;
			}
			
			//Une fois fini le parcours, reevaluer la correspondance distance-temps
			distanceTimeSamples=hackOnDistanceTimeSamples(distanceTimeSamples);
			double[]distTab=new double[distanceTimeSamples.size()];
			double[]timeTab=new double[distanceTimeSamples.size()];
			for(int i=0;i<distTab.length;i++) {distTab[i]=distanceTimeSamples.get(i)[0];timeTab[i]=distanceTimeSamples.get(i)[1];}			

			for(CC ccT:allCCofBranch) {
				int x0=ccT.r.getBounds().x;
				int y0=ccT.r.getBounds().y;
				for(Pix p:ccT.pixGraph.vertexSet()) {
					int index=X*(p.y+y0)+(p.x+x0);
					p.distOut=valDist[index];
				}
				try{
					//PolynomialSplineFunction psf=SplineAndPolyLineUtils.getInterpolator(distTab,timeTab,true);
					ccT.interpolateTimeFromReferencePointsUsingLinearInterpolator(distTab,timeTab);
				}catch(org.apache.commons.math3.exception.NonMonotonicSequenceException e) {
					e.printStackTrace();
				}
			
			}
			rLat.computeDistances();
			rLat.interpolateTime();
			myRprim.attachChild(rLat);
			rLat.attachParent(myRprim);
			rm.rootList.add(rLat);
		}		
		return rm;
	}
	*/

	/*
	public static SimpleDirectedWeightedGraph<CC,ConnectionEdge>  buildAndProcessGraph(ImagePlus imgDatesTmp,String mainDataDir,String ml,String boite,boolean makeNicePictures,boolean makeAllPictures) {
		double ray=5;
		int thickness=5;
		int sizeFactor=SIZE_FACTOR;
		int connexity=8;
	
		ImagePlus imgDatesHigh=VitimageUtils.convertFloatToByteWithoutDynamicChanges(imgDatesTmp);
		IJ.run(imgDatesHigh,"Fire","");
		int nDays=1+(int)Math.round(VitimageUtils.maxOfImage(imgDatesTmp));
		imgDatesHigh.setDisplayRange(0, nDays);		
		imgDatesHigh=VitimageUtils.resizeNearest(imgDatesTmp, imgDatesTmp.getWidth()*sizeFactor,imgDatesTmp.getHeight()*sizeFactor,1);
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph=null;
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph2=null;
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph3=null;
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph4=null;
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph5=null;
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph6=null;
		if(false && makeNicePictures) {
	
			//Build and duplicate initial graph
			boolean compute=true;			
			if(compute) {
				graph=buildGraphFromDateMap(imgDatesTmp,connexity);
				pruneGraph(graph, true);
				setFirstOrderCosts(graph);
				writeGraphToFile(graph,mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite+".ser");
			}
			else graph=readGraphFromFile(mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite);
	
						
			//Identify trunks 
			graph2=readGraphFromFile(mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite);	
			identifyTrunks(graph2);
			computeMinimumDirectedConnectedSpanningTree(graph2);
		
			//Identify trunks 
			graph3=readGraphFromFile(mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite);
			identifyTrunks(graph3);
			computeMinimumDirectedConnectedSpanningTree(graph3);
			disconnectUnevenBranches(graph3);
		
			
			//Identify trunks 
			graph4=readGraphFromFile(mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite);
			identifyTrunks(graph4);
			computeMinimumDirectedConnectedSpanningTree(graph4);
			disconnectUnevenBranches(graph4);
			reconnectDisconnectedBranches(graph4,0,false,false);		//reconnectSingleBranches(graph4);
		
			//Identify trunks 
			graph5=readGraphFromFile(mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite);
			identifyTrunks(graph5);
			computeMinimumDirectedConnectedSpanningTree(graph5);
			disconnectUnevenBranches(graph5);
			reconnectDisconnectedBranches(graph5,1,true,false);		//reconnectSingleBranches(graph4);
		}	
		//Identify trunks 
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graphP=buildGraphFromDateMap(imgDatesTmp,connexity);
		//graph6=readGraphFromFile(mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite);
		//graph6=readGraphFromFile(mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite);
		identifyTrunks(graphP);
		computeMinimumDirectedConnectedSpanningTree(graphP);
		disconnectUnevenBranches(graphP);
		reconnectDisconnectedBranches(graphP,1,true,boite.equals("00002"));		//reconnectSingleBranches(graph4);
		writeGraphToFile(graphP,mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite);
		
		ImagePlus imgG=drawGraph(imgDatesTmp, graphP, ray, thickness,sizeFactor);		
		imgG.show();
		VitimageUtils.waitFor(600000);
		if(makeNicePictures) {
			//Render the graphs
			if(makeAllPictures) {
				ImagePlus imgG1=drawGraph(imgDatesTmp, graph, ray, thickness,sizeFactor);
				ImagePlus imgG2=drawGraph(imgDatesTmp, graph2, ray, thickness,sizeFactor);		
				ImagePlus imgG3=drawGraph(imgDatesTmp, graph3, ray, thickness,sizeFactor);		
				ImagePlus imgG4=drawGraph(imgDatesTmp, graph4, ray, thickness,sizeFactor);		
				ImagePlus imgG5=drawGraph(imgDatesTmp, graph5, ray, thickness,sizeFactor);		
				ImagePlus imgG6=drawGraph(imgDatesTmp, graph6, ray, thickness,sizeFactor);		
				IJ.saveAsTiff(imgG6, mainDataDir+"/3_Graphs_2/ML"+ml+"_Boite_"+boite+".tif");
				writeGraphToFile(graph6,  mainDataDir+"/3_Graphs_Ser/ML"+ml+"_Boite_"+boite+".tif");
				ImagePlus graphs=VitimageUtils.slicesToStack(new ImagePlus[] {imgG1,imgG2,imgG3,imgG4,imgG5,imgG6});
				graphs=VitimageUtils.convertFloatToByteWithoutDynamicChanges(graphs);
				ImagePlus []datesTab=new ImagePlus[graphs.getStackSize()];for(int i=0;i<datesTab.length;i++)datesTab[i]=imgDatesHigh;
				ImagePlus dates=VitimageUtils.convertFloatToByteWithoutDynamicChanges(VitimageUtils.slicesToStack(datesTab));
			
				
				//Compute the combined rendering
				ImagePlus glob=VitimageUtils.hyperStackingChannels(new ImagePlus[] {dates,graphs});
				glob.setDisplayRange(0, nDays);
				IJ.run(graphs,"Fire","");
				glob.show();
				IJ.saveAsTiff(glob, mainDataDir+"/3_Graphs_Raw/ML"+ml+"_Boite_"+boite+".tif");
			}
			else {
				ImagePlus imgG6=drawGraph(imgDatesTmp, graph6, ray, thickness,sizeFactor);		
				IJ.saveAsTiff(imgG6, mainDataDir+"/3_Graphs_2/ML"+ml+"_Boite_"+boite+".tif");
				writeGraphToFile(graph6,  mainDataDir+"/3_Graphs_Ser/ML"+ml+"_Boite_"+boite+".tif");
				ImagePlus graphs=VitimageUtils.slicesToStack(new ImagePlus[] {imgG6});
				graphs=VitimageUtils.convertFloatToByteWithoutDynamicChanges(graphs);
				ImagePlus []datesTab=new ImagePlus[graphs.getStackSize()];for(int i=0;i<datesTab.length;i++)datesTab[i]=imgDatesHigh;
				ImagePlus dates=VitimageUtils.convertFloatToByteWithoutDynamicChanges(VitimageUtils.slicesToStack(datesTab));
			
				
				//Compute the combined rendering
				ImagePlus glob=VitimageUtils.hyperStackingChannels(new ImagePlus[] {dates,graphs});
				glob.setDisplayRange(0, nDays);
				IJ.run(graphs,"Fire","");
				glob.show();
				IJ.saveAsTiff(glob, mainDataDir+"/3_Graphs_Raw/ML"+ml+"_Boite_"+boite+".tif");
				
			}
		}
		return graph6;
//		return glob;
	}
	*/

	/*
	public static SimpleDirectedWeightedGraph<CC,ConnectionEdge> computeMinimumDirectedSpanningTree(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graphInit) {		
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph=(SimpleDirectedWeightedGraph<CC,ConnectionEdge>)(graphInit.clone());
		for(CC cc:graph.vertexSet()) {
			if(cc.day==0)continue;
			ConnectionEdge edgeMin=null;
			double minCost=1E8;
			for(ConnectionEdge edge:graph.incomingEdgesOf(cc)) {
				if(graph.getEdgeWeight(edge)<minCost) {
					minCost=graph.getEdgeWeight(edge);
					edgeMin=edge;
				}
			}
			ArrayList<ConnectionEdge>list=new ArrayList<ConnectionEdge>();
			for(ConnectionEdge edge:graph.incomingEdgesOf(cc)) {
				if(edge!=edgeMin)list.add( edge  );
			}
			for(ConnectionEdge edge : list)graph.removeEdge(edge);
		}
		return graph;
	}*/
	


	
	/*	public static void reconnectDisconnectedBranches(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,int formalism,boolean workAlsoBranches,boolean hack) {
		System.out.println("Reconnection of disconnected branches");
		int Nalso=0;
		int[]associations=null;
		ArrayList<ConnectionEdge>tabKeepEdges=new ArrayList<ConnectionEdge>();
		ArrayList<CC[]>tabKeepCCStart=new ArrayList<CC[]>();

		//Disconnect branching on trunk
		if(workAlsoBranches) {
			for(CC cc:graph.vertexSet()) {
				if(!cc.trunk)continue;
				for(ConnectionEdge edge : graph.outgoingEdgesOf(cc)) {
					if(edge.activated) {
						edge.activated=false;
						tabKeepEdges.add(edge);
						tabKeepCCStart.add(new CC[] {cc,edge.target});
						Nalso++;
					}
				}
			}			
		}
		
		//Identify the disconnected branches
		double thresholdScore=5; 
		ArrayList<CC>listStart=new ArrayList<CC>();
		for(CC cc: graph.vertexSet()) {
			cc.stamp=0;
			if(cc.trunk)continue;
			if(cc.day<2)continue;
			if(cc.bestIncomingActivatedEdge()==null) {
				CC cctmp=cc;
				int tot=cc.nPixels;
				while(cctmp.bestOutgoingActivatedEdge()!=null) {
					cctmp=cctmp.bestOutgoingActivatedEdge().target;
					tot+=cctmp.nPixels;
				}
				//Count size
				if(tot>=MIN_SIZE_CC && cc.y()>150) {
					listStart.add(cc);
					cc.stamp=tot;
				}
			}
		}

		
		//Identify the possible dead ends 
		ArrayList<CC>listStop=new ArrayList<CC>();
		for(CC cc: graph.vertexSet()) {
			if(cc.trunk)continue;
			if(cc.bestOutgoingActivatedEdge()==null) {
				CC cctmp=cc;
				int tot=cc.nPixels;
				while(cctmp.bestIncomingActivatedEdge()!=null && (!cctmp.bestIncomingActivatedEdge().trunk && (cctmp.bestIncomingActivatedEdge().source.day>1))) {
					cctmp=cctmp.bestIncomingActivatedEdge().source;
					tot+=cctmp.nPixels;
				}
				//Count size
				if(tot>=MIN_SIZE_CC && cc.y()>150) {
					listStop.add(cc);
					cc.stamp=tot;
				}
			}
		}
		int Nstart=listStart.size();
		int Nstop=listStop.size();
		if(Nalso>0)associations=new int[Nalso+Nstop];

		if(Nstart==0 || ((Nstop+Nalso==0)))return;
		Timer t=new Timer();
		t.print("Start");
		if(formalism==1) {
			System.out.println("Running hungarian algorithm on Matrix ["+Nstop+"+"+Nalso+"]["+Nstart+"]");
			//Algorithme hongrois
			double[][]costMatrix=new double[Nstop+Nalso][Nstart];
			double[][]costMatrix2=new double[Nstop+Nalso][Nstart];
			CC ccStartWant=getCC(graph,22,6222,3011); //graph,10,7270,1848);
			CC ccStopWant=getCC(graph,21,6422,2859  );
			for(int i=0;i<Nstop;i++) {
	            for(int j=0;j<Nstart;j++) {    
	            	boolean debug=false;
	            	if(false && listStart.get(j)==ccStartWant && listStop.get(i)==ccStopWant) {
	            		debug=true;
	            	}
	            	if(listStop.get(i)==listStart.get(j))costMatrix[i][j]=IDENTITY_PENALTY;
	            	else costMatrix[i][j]=weightingOfPossibleHiddenEdge(graph,listStop.get(i),listStart.get(j),debug);
	            	costMatrix2[i][j]=costMatrix[i][j]; //7026 2172 11 (got start)  7270 1848 10 (stop)   7304 1908 (wanted start)
	            	if(false   && listStart.get(j)==ccStartWant && listStop.get(i)==ccStopWant) {
	            		debug=true;
	            		System.out.println("Cost start want="+costMatrix2[i][j]);
	            	}
	            }
	        }
			int in=0;
			for(int j=0;j<Nstart;j++)for(int i=Nstop;i<Nstop+Nalso;i++) costMatrix[i][j]=SEMI_PENALTY*2;//Cout pour une feuille ou un start de finir tout seul
			
			for(int j=0;j<Nstart;j++) {
				if(isIn(listStart.get(j),tabKeepCCStart)>=0) {
					costMatrix[Nstop+(in)][j]=thresholdScore;//Cout pour une feuille de finir de nouveau sur le tronc principal
					associations[Nstop+(in++)]=isIn(listStart.get(j),tabKeepCCStart);
				}
			}

			HungarianAlgorithm hung1=new HungarianAlgorithm(costMatrix);//HungarianAlgorithm hung1=new HungarianAlgorithm(costMatrix);
			HungarianAlgorithm hung=hung1;
			int []solutions=hung.execute();
			double meanScore=0;
			int N=0;
			for(int i=0;i<listStop.size();i++) {
				CC ccStop=listStop.get(i);
				if(solutions[i]==-1)continue;
				int j=solutions[i];
				CC ccStart=listStart.get(j);
				double minWeight=costMatrix[i][j];	
		    	meanScore+=minWeight;
		    	N++;
		    	
		    	System.out.println("\nGiven by hungarian at step "+N+" with weight "+minWeight+": \n "+  ccStop+"\n    "+ccStart);
		    	if(minWeight>thresholdScore)continue;
				//Connect ccStop et ccStart
				double xCon=ccStop.r.getContourCentroid()[0]*0.5+ccStart.r.getContourCentroid()[0]*0.5;
				double yCon=ccStop.r.getContourCentroid()[1]*0.5+ccStart.r.getContourCentroid()[1]*0.5;
				ConnectionEdge edge=new ConnectionEdge( xCon,yCon, 0, ccStop,ccStart,0,0); 
				edge.activated=true;
				edge.hidden=true;
				edge.trunk=false;
				try {
					graph.addEdge(ccStop, ccStart,edge); 
					graph.setEdgeWeight(edge, minWeight);
					CC c=ccStop.getActivatedRoot();//TODO : set in this function an antiloop stuff for ml1b2, there should be a loop. To investigate ?
					if(c!=null)c.stamp+=ccStart.stamp;
	//				listStop.remove(ccStop);//Remove ccStop from list
				}catch(java.lang.IllegalArgumentException loops) {
					listStop.remove(ccStop);//Remove ccStop from list
				}
			}
			
			if(hack) {
				double xCon=ccStopWant.r.getContourCentroid()[0]*0.5+ccStartWant.r.getContourCentroid()[0]*0.5;
				double yCon=ccStopWant.r.getContourCentroid()[1]*0.5+ccStartWant.r.getContourCentroid()[1]*0.5;
				ConnectionEdge edge=new ConnectionEdge( xCon,yCon, 0, ccStopWant,ccStartWant,0,0); 
				edge.activated=true;
				edge.hidden=true;
				edge.trunk=false;
				graph.addEdge(ccStopWant, ccStartWant,edge);
				graph.setEdgeWeight(edge, 0);
				CC c=ccStopWant.getActivatedRoot();//TODO : set in this function an antiloop stuff for ml1b2, there should be a loop. To investigate ?
				if(c!=null)c.stamp+=ccStartWant.stamp;
			}

			
		 	System.out.println( "Mean score = "+(meanScore/N));
			for(int i=listStop.size();i<Nstop+Nalso;i++) {
				if(solutions[i]==-1) {
					continue;
				}
				graph.getEdge(tabKeepCCStart.get(associations[i])[0], tabKeepCCStart.get(associations[i])[1]).activated=true;
			}
			System.out.println("Algo ok.");
		}
		if(formalism==2) {
			int step=0;
			boolean finished=false;
			while(!finished) {
				System.out.println("Running progressive hungarian algorithm step "+(step++));
				//Algorithme hongrois
				double[][]costMatrix=new double[Nstop][Nstart];
				double[][]costMatrix2=new double[Nstop][Nstart];
//				CC ccStopTest=getCC(graph,22,4713,2562);
//				CC ccStartTest=getCC(graph,20,9074,4076);
				for(int i=0;i<Nstop;i++) {
		            for(int j=0;j<Nstart;j++) {
		            	if(listStop.get(i)==listStart.get(j))costMatrix[i][j]=IDENTITY_PENALTY;
		            	else costMatrix[i][j]=weightingOfPossibleHiddenEdge(graph,listStop.get(i),listStart.get(j),false);
		            	costMatrix2[i][j]=costMatrix[i][j];
		            }
		        }
				HungarianAlgorithm hung=new HungarianAlgorithm(costMatrix);
				int []solutions=hung.execute();
				double meanScore=0;
				int N=0;
				for(int i=0;i<listStop.size();i++) {
					CC ccStop=listStop.get(i);
					if(solutions[i]==-1)continue;
					int j=solutions[i];
					CC ccStart=listStart.get(j);
					double minWeight=costMatrix[i][j];	
			    	meanScore+=minWeight;
			    	N++;
			    	
			    	System.out.println("\nGiven by hungarian at step "+N+" with weight "+minWeight+": \n "+  ccStop+"\n    "+ccStart);
					if(minWeight>thresholdScore)continue;
					//Connect ccStop et ccStart
					double xCon=ccStop.r.getContourCentroid()[0]*0.5+ccStart.r.getContourCentroid()[0]*0.5;
					double yCon=ccStop.r.getContourCentroid()[1]*0.5+ccStart.r.getContourCentroid()[1]*0.5;
					ConnectionEdge edge=new ConnectionEdge( xCon,yCon, 0, ccStop,ccStart,0,0); 
					edge.activated=true;
					edge.hidden=true;
					edge.trunk=false;
					try {
					graph.addEdge(ccStop, ccStart,edge); 
					graph.setEdgeWeight(edge, minWeight);
					ccStop.getActivatedRoot().stamp+=ccStart.stamp;
	//				listStop.remove(ccStop);//Remove ccStop from list
					}catch(java.lang.IllegalArgumentException loops) {
						listStop.remove(ccStop);//Remove ccStop from list
					}
				}
			 	System.out.println( "Score moyen = "+(meanScore/N));
			 	if((listStart.size()==0 || listStop.size()==0))continue;
			}
			System.out.println("Algo ok.");
		}
		else if(formalism==0) {
			System.out.println("Running greedy algorithm");
			int incr=50;
			while(listStart.size()>0 && (incr--)>0) {			
				//Traiter les branches en ordre de taille décroissante
				int bestVolume=0;
				CC ccStart=null;
				for(CC cc:listStart)if(cc.stamp>bestVolume) {bestVolume=cc.stamp;ccStart=cc;}
				
				boolean debug=false;//(ccStart==getCC(graph,18, 1586, 1741));
				double[]weight=new double[listStop.size()];
				for(int i=0;i<weight.length;i++) {
					CC ccTrialStop=listStop.get(i);
					if(ccTrialStop==ccStart)continue;
					weight[i]=weightingOfPossibleHiddenEdge(graph,ccTrialStop,ccStart,debug && (ccStart.euclidianDistanceToCC(ccTrialStop)<1000));//Mettre ici fonction de calcul
				}
				CC ccStop=null;
				double minWeight=1E18;
				for(int i=0;i<weight.length;i++) {
					if((weight[i]<minWeight)) {
						minWeight=weight[i];
						ccStop=listStop.get(i);
					}			
				}
	 
				
				if(minWeight<=thresholdScore) { 
					//Connect ccStop et ccStart
					double xCon=ccStop.r.getContourCentroid()[0]*0.5+ccStart.r.getContourCentroid()[0]*0.5;
					double yCon=ccStop.r.getContourCentroid()[1]*0.5+ccStart.r.getContourCentroid()[1]*0.5;
					//System.out.println("At iteration "+incr+"choice between "+listStart.size()+" starts and "+listStop.size()+" stops.\n   for selected start="+ccStart+"\n   found corresponding stop="+ccStop+" nwith score "+minWeight);
					ConnectionEdge edge=new ConnectionEdge( xCon,yCon, 0, ccStop,ccStart,0,0); 
					edge.activated=true;
					edge.hidden=true;
					edge.trunk=false;
					try {
					graph.addEdge(ccStop, ccStart,edge); 
					graph.setEdgeWeight(edge, minWeight);
					ccStop.getActivatedRoot().stamp+=ccStart.stamp;
					listStop.remove(ccStop);//Remove ccStop from list
					}catch(java.lang.IllegalArgumentException loops) {
						listStop.remove(ccStop);//Remove ccStop from list
					}
				}
				listStart.remove(ccStart);//Remove ccStart from list	
			}
			System.out.println("Algo ok.");

		}
		if(workAlsoBranches) {
			for(ConnectionEdge edge:tabKeepEdges) {
				if(edge.target.bestIncomingActivatedEdge()==null)edge.activated=true;
			}			
		}

		t.print("Stop");
	}
*/
	
}
