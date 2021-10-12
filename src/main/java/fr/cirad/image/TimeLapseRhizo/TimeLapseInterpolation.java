package fr.cirad.image.TimeLapseRhizo;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.jgrapht.graph.SimpleWeightedGraph;

import fr.cirad.image.common.VitimageUtils;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.Duplicator;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import inra.ijpb.binary.geodesic.GeodesicDistanceTransformFloat;

public class TimeLapseInterpolation {

	public static void main(String[]args) {
		ImageJ ij=new ImageJ();
		testGridAndFire();
		//		testFullBehaviourOnSingleSimpleRoot();
		//testFullBehaviour();
		//runInterpolation("1", "00002");
		VitimageUtils.waitFor(200000);
		System.exit(0);
	}
	
	/**Unitary test sequences*/
	public static void testGeod() {
		ImagePlus imgSeed=IJ.openImage("/home/rfernandez/Bureau/Temp/gg5/test1Seed.tif");
		ImagePlus imgMask=IJ.openImage("/home/rfernandez/Bureau/Temp/gg5/test1Mask.tif");
		VitimageUtils.compositeNoAdjustOf(imgSeed, imgMask).show();
		ImagePlus distance=VitimageUtils.computeGeodesic(imgSeed, imgMask,true);
		System.out.println("Min="+VitimageUtils.minOfImage(distance));
		System.out.println("Max="+VitimageUtils.maxOfImage(distance));
		distance.show();
	}
	
/*	public static void testPathway() {
		ImagePlus img=IJ.openImage("/home/rfernandez/Bureau/Temp/gg5/initSeg.tif");
		RoiManager rm=new RoiManager();
		rm.runCommand("Open", "/home/rfernandez/Bureau/Temp/gg5/roi21.roi");
		Roi r21=rm.getRoi(0);
		rm.close();
		rm=new RoiManager();
		rm.runCommand("Open", "/home/rfernandez/Bureau/Temp/gg5/roi22.roi");
		Roi r22=rm.getRoi(0);
		rm.close();
		rm=new RoiManager();
		rm.runCommand("Open", "/home/rfernandez/Bureau/Temp/gg5/roi20.roi");
		Roi r20=rm.getRoi(0);
		rm.close();
		
		ImagePlus proj=RegionAdjacencyGraphUtils.projectRoiTabOnSubImage(new Roi[] {r20,r22,r21});
		proj.show();
		int[]coordStart=new int[] {26,2};
		int[]coordStop=determineTargetFarestGeodesicallyFarestFromTheSource(proj,coordStart);
		List<Pix> listPixs=voxelShortestPath(proj,coordStart,coordStop,8);
		for(int i=0;i<listPixs.size();i++) {
			System.out.println(listPixs.get(i));
		}
		verifyPathway(proj,listPixs).show();		
		
	}
*/	
	
	
	public static void runInterpolation(String ml,String boite) {
		String specName="ML"+ml+"_Boite_"+boite+".tif";
		String dataDir=TestImageSequence.mainDataDir;
		boolean highRes=false;

		//Import proper data
		ImagePlus stackReg=highRes ? IJ.openImage(dataDir+"/1_Registered_High/ML"+ml+"_Boite_"+boite+".tif") : IJ.openImage(dataDir+"/1_Registered/ML"+ml+"_Boite_"+boite+".tif") ;
		ImagePlus dates=IJ.openImage(dataDir+"/2_Date_maps/ML"+ml+"_Boite_"+boite+".tif");
		if(highRes)stackReg=VitimageUtils.resize(stackReg, stackReg.getWidth()/2, stackReg.getHeight()/2, 1);
		
		//Import proper times
		ImagePlus times=IJ.openImage(dataDir+"/4_Times/ML"+ml+"_Boite_"+boite+".tif");
		//Parameters
		double t0Fg=0.01;
		double t1Fg=0.02;
		double t0Bg=0.80;
		double t1Bg=0.99;
		int TN=(int)Math.round( VitimageUtils.maxOfImage(dates));
		double dt=0.02;
		int Nstep=(int)Math.floor(TN*1.0/dt);
		double[]t=new double[Nstep];
		for(int n=0;n<Nstep;n++) {t[n]=dt*n;if(t[n]<1)t[n]=1;}

		
		//Compute successive masks
		ImagePlus maskFgBgGauss=generateFgBgMask(times,dt,Nstep);
		//ImagePlus maskFgBgGauss=maskFgBg;
		double[]voxSizes=VitimageUtils.getVoxelSizes(maskFgBgGauss);
		double sigX=1.5/voxSizes[0];
		double sigY=1.5/voxSizes[1];
		double sigZ=0/voxSizes[2];
		IJ.run(maskFgBgGauss, "Gaussian Blur 3D...", "x="+sigX+" y="+sigY+" z="+sigZ);	
		//ImagePlus maskFgBgGauss=VitimageUtils.gaussianFilteringIJ(maskFgBg, 2, 2, 0);
//		IJ.saveAsTiff(maskFgBgGauss, "/home/rfernandez/Bureau/test.tif");
		//ImagePlus maskFgBgGauss=IJ.openImage("/home/rfernandez/Bureau/test.tif");
		if(highRes)maskFgBgGauss=VitimageUtils.resize(maskFgBgGauss, stackReg.getWidth(), stackReg.getHeight(), 1);

		//Mix using masks
		ImagePlus mixFgBg=mixFgAndBgFromMaskAndStack(maskFgBgGauss,stackReg,t,t0Fg,t1Fg,t0Bg,t1Bg);
		if(highRes) {
			IJ.saveAsTiff(mixFgBg,dataDir+"/5_Timelapse_Highres/ML"+ml+"_Boite_"+boite+".tif");

		}
		else{
			IJ.saveAsTiff(mixFgBg,dataDir+"/5_Timelapse/ML"+ml+"_Boite_"+boite+".tif");
		}
		
		mixFgBg.show();
		VitimageUtils.waitFor(1000000);
	}

	public static int getIndex(double t,double dt) {
		if(t<=0)return -1;
		if(t<1)return 0;
		return ((int)Math.floor(t/dt));
	}
	
	
	public static ImagePlus mixFgAndBgFromMaskAndStack(ImagePlus imgInMask,ImagePlus regStack,double[]ts,double t0Fg,double t1Fg,double t0Bg,double t1Bg) {
		System.out.println("Generating mix");
		ImagePlus imgInReg=VitimageUtils.convertToFloat(regStack);
		int X=imgInReg.getWidth();
		int Y=imgInReg.getHeight();
		int N=imgInMask.getStackSize();
		int Nt=imgInReg.getStackSize();
		ImagePlus imgOut=IJ.createImage("", X, Y, N, 32);
		float[][]tabOut=new float[N][];
		float[][]tabInMask=new float[N][];
		float[][]tabInReg=new float[Nt][];
		for(int n=0;n<N;n++) {
			tabOut[n]=(float[]) imgOut.getStack().getProcessor(n+1).getPixels();
			tabInMask[n]=(float[]) imgInMask.getStack().getProcessor(n+1).getPixels();
		}
		for(int n=0;n<Nt;n++) {
			tabInReg[n]=(float[]) imgInReg.getStack().getProcessor(n+1).getPixels();
		}
		int indexSpace=0;
		int indexTime=0;
		double t=0;
		double valMixFg;
		double valMixBg;
		double delta=0;
		double deltaBg=t1Bg-t0Bg;
		double deltaFg=t1Fg-t0Fg;
		int T0=0;int Tprev=0;int T1=0; int Tnext=0;double dt;
		
		for(int n=0;n<N;n++) {
			t=ts[n];
			T0=(int)Math.floor(t);
			T1=T0+2;
			Tnext=T0+3;
			Tprev=T0-1;
			dt=t-T0;
			if((n%50)==0)System.out.println("Processing "+n+" : t="+t+" , dt="+dt+" , Tprev="+Tprev+" , T0="+T0+" , T1="+T1+" , Tnext="+Tnext+" BG=de "+Tprev+" a "+T0+" , FG=de"+T1+" a "+Tnext);
			
			if((n%50)==0)System.out.print(n+"/"+N+" ");
			for(int x=0;x<X;x++) {
				for(int y=0;y<Y;y++) {
					indexSpace=y*X+x;
					
					//BG
					if(true || Tprev<0)valMixBg=tabInReg[0][indexSpace];
					else {
						if(dt<=t0Bg)valMixBg=tabInReg[Tprev][indexSpace];
						else if(dt>=t1Bg)valMixBg=tabInReg[T0][indexSpace];
						else {
							delta=(dt-t0Bg)/deltaBg;
							valMixBg=delta*tabInReg[T0][indexSpace]+(1-delta)*tabInReg[Tprev][indexSpace];
						}
					}
					
					//FG
					if(Tnext>=Nt)valMixFg=tabInReg[Nt-1][indexSpace];
					else {
						if(dt<=t0Fg)valMixFg=tabInReg[T1][indexSpace];
						else if(dt>=t1Fg)valMixFg=tabInReg[Tnext][indexSpace];
						else {
							delta=(dt-t0Fg)/deltaFg;
							valMixFg=delta*tabInReg[Tnext][indexSpace]+(1-delta)*tabInReg[T1][indexSpace];
						}
					}
					
					double deltaMix=tabInMask[n][indexSpace];
					tabOut[n][indexSpace]=(float)(  valMixFg*deltaMix + valMixBg*(1-deltaMix) );//(float) (valMixBg*(1-deltaMix));//
				}
			}
		}
		imgOut.setDisplayRange(0, 255);
		return imgOut;		
	}
	
	
	public static ImagePlus generateFgBgMask(ImagePlus imgIn,double dt,int N) {
		System.out.println("Generating FgBgMask");
		int X=imgIn.getWidth();
		int Y=imgIn.getHeight();
		ImagePlus imgOut=IJ.createImage("", X, Y, N, 32);
		float[][]tabOut=new float[N][];
		float[]tabIn=(float[]) imgIn.getStack().getProcessor(1).getPixels();
		for(int n=0;n<N;n++)tabOut[n]=(float[]) imgOut.getStack().getProcessor(n+1).getPixels();
		int indexSpace=0;
		int indexTime=0;
		for(int x=0;x<X;x++) {
			if((x%50)==0)System.out.print(x+"/"+X+" ");
			for(int y=0;y<Y;y++) {
				indexSpace=y*X+x;
				indexTime=getIndex(tabIn[indexSpace],dt);
				if(indexTime==-1)continue;
				for(int n=indexTime;n<N;n++) tabOut[n][indexSpace]=1;
			}
		}
		imgOut.setDisplayRange(0, 1);
		System.out.println();
		return imgOut;
	}
	
	
	
	/**Integration test sequences*/
	public static void testFullBehaviourOnSingleSimpleRoot() {
		ImagePlus dates=generateDatesFromRoiTestSet5();
		int N=(int)Math.round( VitimageUtils.maxOfImage(dates) );
		dates.show();
		dates.setDisplayRange(0, N);
		IJ.run(dates,"32-bit","");
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph;
		boolean generate=true;
		if(generate) {
			graph=generateGraph(dates);
			RegionAdjacencyGraphUtils.writeGraphToFile(graph, "/home/rfernandez/Bureau/temp.graph");
		}
		else {
			graph=RegionAdjacencyGraphUtils.readGraphFromFile("/home/rfernandez/Bureau/temp.graph");
		}
		//RegionAdjacencyGraphUtils.drawGraph(dates, graph, 5, 5, RegionAdjacencyGraphUtils.SIZE_FACTOR).show();
		ImagePlus distOut=RegionAdjacencyGraphUtils.getDistOut(dates,false);
		RegionAdjacencyGraphUtils.refinePlongementOfCCGraph(graph,distOut);
		ImagePlus allInfo=RegionAdjacencyGraphUtils.drawDistanceTime(dates,graph,4,false);
		ImagePlus times=RegionAdjacencyGraphUtils.drawDistanceTime(dates,graph,1,false);
		allInfo.show();
		times.show();
		VitimageUtils.waitFor(200000);
		System.exit(0);
	}
	
	public static void testFullBehaviour(String ml, String boite) {
		String specName="ML"+ml+"_Boite_"+boite+".tif";
		String dataDir=TestImageSequence.mainDataDir;


		//check proper data
		ImagePlus dates=IJ.openImage();
		
	}
	

	public static double laplacian(double val) {
		return 1/(1+(val*val));
	}

	public static double laplacian(double val,double ray) {
		return laplacian (val/ray);
	}
	
	public static double[]getMaskAndFireValue(double dx,double dy, double vx,double vy,double R,double vMax){		
		double radMask=R/3;
		double radFire=R/4;
		double v=Math.sqrt(vx*vx+vy*vy);
		double r=Math.sqrt(dx*dx+dy*dy);
		double prodScalNorm=(dx*vx+dy*vy)/(r*v);
		if((r*v)<=0)prodScalNorm=1;
		double lapAng=laplacian(3*(1-prodScalNorm));//quand prodScal varie de 1 a -1, laplacien varie de 1 a 1/27e
		double lapMask=laplacian(r*r,radMask*radMask)*lapAng;//quand l'eloignement au point central varie de 0 a R, lap varie de 1 a 1/81
		double lapFire=((Math.min(v,vMax)/vMax))*230*laplacian(r,radFire)*lapAng;//Quand v varie de 0 a vMax, lap varie de
		return new double[] {lapMask,lapFire};
	}
	
	
	public static ImagePlus[] generateGridAndFire(int X,int Y, double[][][]coords,double vMax) {
		System.out.println("Generating grid and fire");
		int G=1000/78;
		int N=coords.length;
		int P=coords[0].length;
		int sizeRatio=10;
		int R=X/sizeRatio;
		ImagePlus imgOut=IJ.createImage("", X, Y, N, 32);
		ImagePlus imgWeight=IJ.createImage("", X, Y, N, 32);
		ImagePlus imgMask=IJ.createImage("", X, Y, N, 32);
		ImagePlus imgGrid=IJ.createImage("", X, Y, N, 32);
		float[][]tabOut=new float[N][];
		float[][]tabWeight=new float[N][];
		float[][]tabMask=new float[N][];
		float[][]tabGrid=new float[N][];
		for(int n=0;n<N;n++) {
			tabOut[n]=(float[]) imgOut.getStack().getProcessor(n+1).getPixels();
			tabWeight[n]=(float[]) imgWeight.getStack().getProcessor(n+1).getPixels();
			tabMask[n]=(float[]) imgMask.getStack().getProcessor(n+1).getPixels();
			tabGrid[n]=(float[]) imgGrid.getStack().getProcessor(n+1).getPixels();
		}
		int indexSpace=0;
		int indexTime=0;
		for(int n=0;n<N;n++) {
			if((n%20)==0)System.out.print(n+"/"+N+" ");
			for(int p=0;p<P;p++) {
				double x0=coords[n][p][0];
				double y0=coords[n][p][1];
				double vx=coords[n][p][2];
				double vy=coords[n][p][3];
				if(x0<=0)continue;
				int al=1;
				for(int x=(int)Math.round(x0-al*R);x<=x0+al*R;x++) {
					if(x<0 || x>=X)continue;
					for(int y=(int)Math.round(y0-al*R);y<=y0+al*R;y++) {
						if(y<0 || y>=Y)continue;
						indexSpace=y*X+x;						
						double finalVal=0;
						double []maskFire =getMaskAndFireValue(x-x0,y-y0, vx,vy,R,vMax);
						int nAlready=(int) tabWeight[n][indexSpace];
						if(nAlready==0) {
							tabMask[n][indexSpace]=(float) maskFire[0];
							tabOut[n][indexSpace]=(float) maskFire[1];
							tabWeight[n][indexSpace]=1;
						}
						else{
							tabMask[n][indexSpace]=(float) Math.max( maskFire[0],tabMask[n][indexSpace]);
							tabOut[n][indexSpace]=(float) Math.max( maskFire[1],tabOut[n][indexSpace]);
							tabWeight[n][indexSpace]+=1;
						}
						
						int nearGridX=(int)Math.round( x/G );
						int nearGridY=(int)Math.round( y/G );
						int dgx=x-nearGridX*G;
						int dgy=y-nearGridY*G;
						int dgAbsx=Math.abs(dgx);
						int dgAbsy=Math.abs(dgy);
						if(dgAbsx<3 && ((nearGridX%10)==0)) {tabGrid[n][indexSpace]=255;continue;}
						if(dgAbsy<3 && ((nearGridY%10)==0)) {tabGrid[n][indexSpace]=255;continue;}
						if(dgAbsx<2 && ((nearGridX%5)==0)) {tabGrid[n][indexSpace]=255;continue;}
						if(dgAbsy<2 && ((nearGridY%5)==0)) {tabGrid[n][indexSpace]=255;continue;}
						if(dgAbsx<1) {tabGrid[n][indexSpace]=255;continue;}
						if(dgAbsy<1) {tabGrid[n][indexSpace]=255;continue;}
					}
				}
			}
		}
		imgOut.setTitle("Fire");
		imgOut.setDisplayRange(0, 255);
		imgMask.setTitle("Mask");
		imgMask.setDisplayRange(0, 1);
		imgGrid.setTitle("Grid");
		imgGrid.setDisplayRange(0, 255);
		imgMask.show();
		imgOut.show();
		IJ.run(imgGrid,"8-bit","");
		IJ.run(imgOut,"Fire","");
		IJ.run(imgMask,"Fire","");
		IJ.run(imgGrid,"Fire","");
		return new ImagePlus [] {imgOut,imgGrid,imgMask};
	}

	
	
	public static void testGridAndFire() {
		int X=800;
		int Y=800;
		int N=1000;
		double vMax=2;
		int P=4;
		double[]ang0=new double[] {0,1/2.0,2/2.0,3/2.0};
		double[]vAng=new double[] {1,2,3,4};
		double[]x0=new double[] {400,400,400,400};
		double[]y0=new double[] {400,400,400,400};
		double[]r=new double[] {312,232,152,112};
		double factAng=1E-2/3;
		double[][][]coords=new double[N][P][4];
		for(int n=0;n<N;n++) {
			for(int p=0;p<P;p++) {
				double ang=ang0[p]+(2*vAng[p]*(n*n*1.0/N)*factAng);
				coords[n][p]=new double[] {x0[p]+r[p]*Math.cos(ang),y0[p]+r[p]*Math.sin(ang),0,0};					
				if(n>0) {
					coords[n][p][2]=coords[n][p][0]-coords[n-1][p][0];
					coords[n][p][3]=coords[n][p][1]-coords[n-1][p][1];
					if((n%100)==0) {
						System.out.println("N="+n+" Point "+p+" vitesse="+VitimageUtils.distance(0, 0, coords[n][p][2],coords[n][p][3]));
					}
				}
			}
		}
		
		ImagePlus[]res= generateGridAndFire(X,Y, coords,vMax);
		/*res[0].show();
		res[1].show();
		res[2].show();*/
		ImagePlus maskRoot=VitimageUtils.nullImage(res[0]);
		ImagePlus root=VitimageUtils.makeOperationOnOneImage(maskRoot, 1, 122, true);
		IJ.run(root,"8-bit","");
		IJ.run(maskRoot,"8-bit","");
		maskRoot=VitimageUtils.drawRectangleInImage(maskRoot,400,340,800,400,255);
		root=VitimageUtils.drawRectangleInImage(root,400,340,800,400,30);
		//		root=VitimageUtils.gaussianFiltering(root, 1.0, 1.0, 0);
		ImagePlus result=assembleRootGridAndFire(root, maskRoot, res[0],res[1],res[2]);
		result.show();
	}
	
	
	public static ImagePlus assembleRootGridAndFire(ImagePlus imgRoot,ImagePlus imgMaskRoot,ImagePlus imgFire, ImagePlus imgGrid,ImagePlus imgMaskFuse) {
		IJ.run(imgFire,"RGB Color","");
		ImagePlus []resChan=VitimageUtils.channelSplitter(imgFire);
		
		int N=imgRoot.getStackSize();
		int X=imgRoot.getWidth();
		int Y=imgRoot.getHeight();
		byte[][]resR=new byte[N][];
		byte[][]resG=new byte[N][];
		byte[][]resB=new byte[N][];
		byte[][]root=new byte[N][];
		byte[][]maskRoot=new byte[N][];
		byte[][]grid=new byte[N][];
		float[][]maskFuse=new float[N][];
		double weig;
		byte val;
		for(int n=0;n<N;n++) {
			resR[n]=(byte[]) resChan[0].getStack().getProcessor(n+1).getPixels();
			resG[n]=(byte[]) resChan[1].getStack().getProcessor(n+1).getPixels();
			resB[n]=(byte[]) resChan[2].getStack().getProcessor(n+1).getPixels();
			root[n]=(byte[]) imgRoot.getStack().getProcessor(n+1).getPixels();
			maskRoot[n]=(byte[]) imgMaskRoot.getStack().getProcessor(n+1).getPixels();
			grid[n]=(byte[]) imgGrid.getStack().getProcessor(n+1).getPixels();
			maskFuse[n]=(float[]) imgMaskFuse.getStack().getProcessor(n+1).getPixels();
		}
		int indexSpace=0;
		int indexTime=0;
		for(int n=0;n<N;n++) {
			if((n%20)==0)System.out.print(n+"/"+N+" ");
			for(int x=0;x<X;x++) {
				for(int y=0;y<Y;y++) {
					indexSpace=y*X+x;						
					if((maskFuse[n][indexSpace]<=0) || (maskRoot[n][indexSpace]!=0)) {//If included in root segmentation or no mask present, leave intact
						resR[n][indexSpace]=root[n][indexSpace];
						resG[n][indexSpace]=root[n][indexSpace];
						resB[n][indexSpace]=root[n][indexSpace];
					}
					else if(grid[n][indexSpace]!=0){//Else if included in grid, weighted copy grid and root
						weig=maskFuse[n][indexSpace];
						val=(byte)( (int)(weig*((int)(grid[n][indexSpace]  & 0xff))+(1-weig)*(int)(root[n][indexSpace]  & 0xff))  & 0xff);
						resR[n][indexSpace]=val;
						resG[n][indexSpace]=val;
						resB[n][indexSpace]=val;
					}
					else {//MaskFuse not null, and not in grid
						weig=maskFuse[n][indexSpace];
						resR[n][indexSpace]=(byte)( (int)(weig*(resR[n][indexSpace]  & 0xff)+(1-weig)*(root[n][indexSpace]  & 0xff))  & 0xff);
						resG[n][indexSpace]=(byte)( (int)(weig*(resG[n][indexSpace]  & 0xff)+(1-weig)*(root[n][indexSpace]  & 0xff))  & 0xff);
						resB[n][indexSpace]=(byte)( (int)(weig*(resB[n][indexSpace]  & 0xff)+(1-weig)*(root[n][indexSpace]  & 0xff))  & 0xff);
					}
				}
			}
		}
		return VitimageUtils.compositeRGBByte(resChan[0],resChan[1],resChan[2], 1, 1, 1);
	}
	
	
	
	
	
	
	public static SimpleDirectedWeightedGraph<CC,ConnectionEdge>  generateGraph(ImagePlus imgDatesTmp ){		
		System.out.println("Generating graph...");
		double ray=5;
		int thickness=5;
		int sizeFactor=RegionAdjacencyGraphUtils.SIZE_FACTOR;
		int connexity=8;
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph;
		
		//Import and oversample image of dates
		ImagePlus imgDatesHigh=VitimageUtils.convertFloatToByteWithoutDynamicChanges(imgDatesTmp);
		IJ.run(imgDatesHigh,"Fire","");
		int nDays=1+(int)Math.round(VitimageUtils.maxOfImage(imgDatesTmp));
		imgDatesHigh.setDisplayRange(0, nDays);		
		imgDatesHigh=VitimageUtils.resizeNearest(imgDatesTmp, imgDatesTmp.getWidth()*sizeFactor,imgDatesTmp.getHeight()*sizeFactor,1);

		graph=RegionAdjacencyGraphUtils.buildGraphFromDateMap(imgDatesTmp,connexity);
		RegionAdjacencyGraphUtils.pruneGraph(graph, true);
		RegionAdjacencyGraphUtils.setFirstOrderCosts(graph);
		RegionAdjacencyGraphUtils.identifyTrunks(graph);
		RegionAdjacencyGraphUtils.computeMinimumDirectedConnectedSpanningTree(graph);
		RegionAdjacencyGraphUtils.disconnectUnevenBranches(graph);
		RegionAdjacencyGraphUtils.reconnectDisconnectedBranches(graph,1,false,false);		//reconnectSingleBranches(graph4);
		System.out.println("Generate graph ok.\n");
		
		/*ImagePlus imgG1=RegionAdjacencyGraphUtils.drawGraph(imgDatesTmp, graph, ray, thickness,sizeFactor);
		ImagePlus dates=VitimageUtils.convertFloatToByteWithoutDynamicChanges(imgDatesHigh);

	
		//Compute the combined rendering
		ImagePlus glob=VitimageUtils.slicesToStack(new ImagePlus[] {dates,imgG1});
		glob.setDisplayRange(0, nDays);
		IJ.run(glob,"Fire","");
		glob.show();*/
		return graph;
	}
	
	
	
	//TODO 2 : feature de creation de piste dynamique
		//Interpolate : ok
		
	public static ImagePlus generateDatesFromRoiTestSet1() {
		//case 1:simple, case2: with outers, case3 : with jump and outers 
		ImagePlus ground=IJ.openImage("/home/rfernandez/Bureau/Temp/gg5/Init.tif");
		ground.show();
		RoiManager rm=new RoiManager();
		rm.runCommand("Open", "/home/rfernandez/Bureau/Temp/gg5/RoiSet.zip");
		Roi[]rois=rm.getRoisAsArray();
		rm.close();
		Roi rt=rois[17];
		rois[17]=rois[18];
		rois[18]=rt;
		byte[]tabData=(byte[])ground.getStack().getPixels(1);
		for(int x=0;x<ground.getWidth();x++)for(int y=0;y<ground.getHeight();y++)for(int r=0;r<rois.length;r++) if(rois[r].contains(x, y) && r!=17)tabData[ground.getWidth()*y+x]=(byte) (r+1);
		IJ.run(ground,"Fire","");
		ground.setDisplayRange(0, rois.length);
		ground.updateAndRepaintWindow();
		return ground;
	}


	public static ImagePlus generateDatesFromRoiTestSet2() {
		//case 1:simple, case2: with outers, case3 : with jump and outers 
		ImagePlus ground=generateDatesFromRoiTestSet1();
		ground.show();
		RoiManager rm=new RoiManager();
		rm.runCommand("Open", "/home/rfernandez/Bureau/Temp/gg5/RoiSet2.zip");
		Roi[]rois=rm.getRoisAsArray();
		rm.close();
		byte[]tabData=(byte[])ground.getStack().getPixels(1);
		for(int x=0;x<ground.getWidth();x++)for(int y=0;y<ground.getHeight();y++)for(int r=0;r<rois.length;r++) if(rois[r].contains(x, y) && r!=17)tabData[ground.getWidth()*y+x]=(byte) (r+2);
		IJ.run(ground,"Fire","");
		ground.setDisplayRange(0, 22);
		ground.updateAndRepaintWindow();
		return ground;
	}


	public static ImagePlus generateDatesFromRoiTestSet4() {
		//case 1:simple, case2: with outers, case3 : with jump and outers 
		ImagePlus ground=generateDatesFromRoiTestSet3();
		ground.show();
		RoiManager rm=new RoiManager();
		rm.runCommand("Open", "/home/rfernandez/Bureau/Temp/gg5/RoiSet4.zip");
		Roi[]rois=rm.getRoisAsArray();
		rm.close();
		byte[]tabData=(byte[])ground.getStack().getPixels(1);
		for(int x=0;x<ground.getWidth();x++)for(int y=0;y<ground.getHeight();y++)for(int r=0;r<rois.length;r++) if(rois[r].contains(x, y) && r!=17)tabData[ground.getWidth()*y+x]=(byte) (r+14);
		IJ.run(ground,"Fire","");
		ground.setDisplayRange(0, 22);
		ground.updateAndRepaintWindow();
		return ground;
	}

	public static ImagePlus generateDatesFromRoiTestSet5() {
		//case 1:simple, case2: with outers, case3 : with jump and outers 
		ImagePlus ground=generateDatesFromRoiTestSet4();
		ground.show();
		RoiManager rm=new RoiManager();
		rm.runCommand("Open", "/home/rfernandez/Bureau/Temp/gg5/RoiSet5.zip");
		Roi[]rois=rm.getRoisAsArray();
		rm.close();
		byte[]tabData=(byte[])ground.getStack().getPixels(1);
		for(int x=0;x<ground.getWidth();x++)for(int y=0;y<ground.getHeight();y++)for(int r=0;r<rois.length;r++) if(rois[r].contains(x, y) && r!=17)tabData[ground.getWidth()*y+x]=(byte) (r+22);
		IJ.run(ground,"Fire","");
		ground.setDisplayRange(0, 22);
		ground.updateAndRepaintWindow();
		return ground;
	}


	public static ImagePlus generateDatesFromRoiTestSet3() {
		//case 1:simple, case2: with outers, case3 : with jump and outers 
		ImagePlus ground=generateDatesFromRoiTestSet2();
		ground.show();
		RoiManager rm=new RoiManager();
		rm.runCommand("Open", "/home/rfernandez/Bureau/Temp/gg5/RoiSet3.zip");
		Roi[]rois=rm.getRoisAsArray();
		rm.close();
		byte[]tabData=(byte[])ground.getStack().getPixels(1);
		for(int x=0;x<ground.getWidth();x++)for(int y=0;y<ground.getHeight();y++)for(int r=0;r<rois.length;r++) if(rois[r].contains(x, y) && r!=17)tabData[ground.getWidth()*y+x]=(byte) (r+4);
		IJ.run(ground,"Fire","");
		ground.setDisplayRange(0, 22);
		ground.updateAndRepaintWindow();
		return ground;
	}


		public static ImagePlus verifyPathway(ImagePlus img,List<Pix> listPixs) {
			ImagePlus res=VitimageUtils.convertToFloat(img);
			ImageProcessor ip=img.getStack().getProcessor(1);
			for(int i=0;i<listPixs.size();i++) {
				ip.set(listPixs.get(i).x, listPixs.get(i).y,255);
			}
			ImagePlus imRes=new ImagePlus("",ip);
			return imRes;
		}
		
		
/*		public static void identifyDistancesOnTheWay(SimpleWeightedGraph<Pix,Bord>graph,List<Pix>listPixs,double distanceStart) {
			int nbMov=0;
			setWeightsToEuclidian(graph);
			ArrayList<Pix>pixToVisit=new ArrayList<Pix>();
			ArrayList<Pix>pixVisited=new ArrayList<Pix>();
			for(Pix p:graph.vertexSet()) {
				p.wayFromPrim=100000;
			}
			double curDist=distanceStart+1;//TODO : douteux
			Pix pStart=listPixs.get(0);
			pStart.wayFromPrim=curDist;
			pixVisited.add(p);p.source=p;p.wayFromPrim=curDist;p.wayFromSkel=0;
			
			for(int i=1;i<listPixs.size();i++) {
				Pix p=listPixs.get(i);
				Pix pBef=listPixs.get(i-1);
				curDist+=graph.getEdge(pBef, p).len;
				pixVisited.add(p);p.source=p;p.wayFromPrim=curDist;p.wayFromSkel=0;}
			}
			int incr=0;
			while(pixVisited.size()>0) {
				System.out.println("Dijkstra step "+(incr++)+" N="+pixVisited.size());
				for(Pix p:pixVisited) {
					for(Bord bord:graph.edgesOf(p)) {
						double nextWay=p.wayFromPrim+bord.getWeightEuclidian();
						if(bord.pix1.wayFromPrim>nextWay) {bord.pix1.wayFromPrim=nextWay;bord.pix1.source=p;pixToVisit.add(p);}
						if(bord.pix2.wayFromPrim>nextWay) {bord.pix2.wayFromPrim=nextWay;bord.pix2.source=p;pixToVisit.add(p);}	
					}
				}
				pixVisited=pixToVisit;
				pixToVisit=new ArrayList<Pix>();
			}
		}
		*/
		
		
}


