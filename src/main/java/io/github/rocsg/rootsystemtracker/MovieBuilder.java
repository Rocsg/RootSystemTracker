package io.github.rocsg.rootsystemtracker;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import io.github.rocsg.fijiyama.rsml.Node;
import io.github.rocsg.fijiyama.common.Timer;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import io.github.rocsg.fijiyama.rsml.Root;
import io.github.rocsg.fijiyama.rsml.RootModel;
import io.github.rocsg.rstutils.MorphoUtils;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.TextRoi;
import ij.measure.Calibration;
import ij.plugin.ChannelSplitter;
import ij.plugin.Duplicator;
import ij.plugin.filter.AVI_Writer;
import ij.process.ImageProcessor;

public class MovieBuilder {

	//Bug a la ligne 1774 dans 61grah.rsml   de la racine qui commence par <point coord_t="1.0" coord_th="0.0" coord_x="376.0" coord_y="178.0" diameter="0.0" vx="0.0" vy="0.0"/> (5.1.3)
	
	//Algorithm parameters
	static double umPerPixel = 76;
	static byte lisereVal=(byte)(1 & 0xff);
	static int vMaxDisplayed=8; //35 (pixels/8h) * 3  (8h/day)  * 76 (µm / pix) =   7980 µm/day//TODO : depends on parameters
	static double vMaxInUse=35/8.0;//TODO : depends on parameters
	static double startingBlockRatio=0.10;//time for a new root to appear, expressed as a ratio of the sequence length  //TODO : depends on parameters
	static boolean smartHandyStart=true;// Make a progressive start to make the movie more friendly
	static double timeStep=0.5;//It is hours/keyframe. The initial timeseries have a timeStep roughly equals to pph.nTypicalHours
	static int nTimeStepVirtualEnd=4; //Make a progressive end to make the movie more friendly //TODO : depends on parameters
	static double deltaPixelsSpeedInterpolation=4;// Compute the speed vectors over the interval [curPix-delta ; curPix + delta] //TODO : depends on parameters
	static PipelineParamHandler pph;
	
	static int primaryRadius=2;
	static int secondaryRadius=2;
	static double t0Fg=0.01;//These 4 numbers are relative to deltaHours between original keyframes;
	static double t1Fg=0.02;
	static double t0Bg=0.20;
	static double t1Bg=0.80;

	static int sizeFactor=1;
	private static boolean modelMode1Activated=false;
	private static boolean modelMode2Activated=true;
	private static int TN;
	
	private static double[]hoursExtremities;
	private static double[] t;
	private static double[] deltaRatioToBef;
	private static double[] deltaHoursToBef;
	private static double[] deltaHoursToAft;
	private static int[] indexImgBef;
	private static int[] indexImgAft;
	private static int endingAdditionalFrames=15;
	static Timer tim;

	
	
	//SAFE
	/*public static void main(String[]args) {
		ImageJ ij=new ImageJ();	
	}*/
		
	public static boolean buildMovie(int indexImg,String outputDataDir,PipelineParamHandler pph) {
		tim=new Timer();
		//Prepare params
		primaryRadius*=sizeFactor;
		secondaryRadius*=sizeFactor;
		umPerPixel=pph.subsamplingFactor*pph.getDouble("originalPixelSize")/sizeFactor;
		tim.print("Starting data importation");
		hoursExtremities=pph.getHoursExtremities(indexImg);
		TN=pph.imgSerieSize[indexImg];
		setSamplesT();

		//Prepare input image
		ImagePlus imgReg=IJ.openImage(new File(outputDataDir,"22_registered_stack.tif").getAbsolutePath()) ;
		//ImagePlus dates=IJ.openImage( new File(outputDataDir,"40_date_map.tif").getAbsolutePath());
		ImagePlus imgTimes=IJ.openImage(new File(outputDataDir,"65_times.tif").getAbsolutePath());
		ImagePlus maskUpLeaves=IJ.openImage(new File(outputDataDir,"32_mask_at_tN.tif").getAbsolutePath());
		maskUpLeaves=MorphoUtils.erosionCircle2D(maskUpLeaves, 65);
		
		
		//Compute successive masks of foreground and background to simulate continuous growth of the original image serie
		tim.print("Starting building mix mask ");
		ImagePlus maskFgBgGauss=generateFgBgMask(imgTimes);
		//maskFgBgGauss.show();
		
		
		//Mix using masks
		tim.print("Starting mixing root");
		ImagePlus mixFgBg=mixFgAndBgFromMaskAndStack(maskFgBgGauss,imgReg,maskUpLeaves);
		IJ.run(mixFgBg,"8-bit","");
		//mixFgBg.show();
		maskFgBgGauss.setDisplayRange(0, 1);
		IJ.run(maskFgBgGauss,"8-bit","");

		
		
		//Generate grid and fire TODO : and skeleton
		tim.print("Starting generating grid and fire");
		ImagePlus imgMaskRootInit=new Duplicator().run(maskFgBgGauss,1,1,1,1,1,1);
		RootModel rm=RootModel.RootModelWildReadFromRsml(new File(outputDataDir,"61_graph.rsml").getAbsolutePath());
		rm.computeSpeedVectors(deltaPixelsSpeedInterpolation);
		ImagePlus[]imgGridAndFire=generateGridAndFireFromRootModel(rm,imgTimes,imgMaskRootInit);//TODO should be here
		ImagePlus imgSkeleton=generateModelRGBFromRootModel(rm, imgMaskRootInit);
		/*imgGridAndFire[0].show();
		imgGridAndFire[1].show();
		imgGridAndFire[2].show();
		imgGridAndFire[3].show();
		*/
		
		//Assemble all info
		tim.print("\nStarting final assembling");
		mixFgBg=assembleRootGridAndFire(mixFgBg, maskFgBgGauss, imgGridAndFire[0],imgGridAndFire[1],imgGridAndFire[2],imgGridAndFire[3],imgSkeleton,imgReg,true,true);//TODO or maybe there
		tim.print("\nEnd. Now saving");

		
		
		//Wash memory
		imgGridAndFire=null;
		maskFgBgGauss=null;
		System.gc();

		saveAsMovie(mixFgBg, new File(outputDataDir,"70_growing_root_system.avi").getAbsolutePath());
		return true;
	}
	//SAFE
	
	
	
	
	
	
	
 
		
 
	

	//TODO : make it depends on sliceindices and delta between
	public static ImagePlus assembleRootGridAndFire(ImagePlus imgRoot,ImagePlus imgMaskRoot,ImagePlus imgFire, ImagePlus imgGrid,
			ImagePlus imgMaskGrid,ImagePlus imgIdent,ImagePlus imgSkeleton,ImagePlus initReg,boolean fireDisplay,boolean joinOpening) {
		int nRoots=(int) VitimageUtils.maxOfImage(imgIdent)+1;
		byte[][]colorMapFire=getColorMapFire();
		tim.print("\nFinal Assembling starting");
		VitimageUtils.garbageCollector();
		int N=imgRoot.getStackSize();
		int X=imgRoot.getWidth();
		int Y=imgRoot.getHeight();
		int delta=0;
		if(joinOpening) {
			delta=387;
		}
		int N2=N+delta;
		ImagePlus []resChan=new ImagePlus[] {IJ.createImage("", X, Y, N2, 8),IJ.createImage("", X, Y, N2, 8),IJ.createImage("", X, Y, N2, 8)};
		for(int c=0;c<3;c++) resChan[c].setDisplayRange(0, 255);
		byte[][]skel=new byte[N][];
		byte[][]resR=new byte[N][];
		byte[][]resG=new byte[N][];
		byte[][]resB=new byte[N][];
		byte[][]resInitR=new byte[delta][];
		byte[][]resInitG=new byte[delta][];
		byte[][]resInitB=new byte[delta][];
		byte[][]root=new byte[N][];
		byte[][]maskRoot=new byte[N][];
		byte[][]fire=new byte[N][];
		byte[]grid=new byte[N];
		byte[][]maskGrid=new byte[N][];
		byte[][]ident=new byte[N][];
		double weig;
		byte val;
		for(int n=0;n<delta;n++) {
			resInitR[n]=(byte[]) resChan[0].getStack().getProcessor(n+1).getPixels();
			resInitG[n]=(byte[]) resChan[1].getStack().getProcessor(n+1).getPixels();
			resInitB[n]=(byte[]) resChan[2].getStack().getProcessor(n+1).getPixels();
		}
		for(int n=0;n<N;n++) {
			resR[n]=(byte[]) resChan[0].getStack().getProcessor(n+1+delta).getPixels();
			resG[n]=(byte[]) resChan[1].getStack().getProcessor(n+1+delta).getPixels();
			resB[n]=(byte[]) resChan[2].getStack().getProcessor(n+1+delta).getPixels();
			skel[n]=(byte[]) imgSkeleton.getStack().getProcessor(n+1).getPixels();
			root[n]=(byte[]) imgRoot.getStack().getProcessor(n+1).getPixels();
			maskRoot[n]=(byte[]) imgMaskRoot.getStack().getProcessor(n+1).getPixels();
			fire[n]=(byte[]) imgFire.getStack().getProcessor(n+1).getPixels();
			maskGrid[n]=(byte[]) imgMaskGrid.getStack().getProcessor(n+1).getPixels();
			ident[n]=(byte[]) imgIdent.getStack().getProcessor(n+1).getPixels();
		}
		grid=(byte[]) imgGrid.getStack().getProcessor(1).getPixels();
		int indexSpace=0;
		int indexTime=0;
		byte lisereOutVal=toByte(200);

		int deltaX=-10;
		int deltaY=-2+50-60;
		int wid=150;
		Font font19 = new Font("SansSerif", Font.PLAIN, 19);		
		Font font20 = new Font("SansSerif", Font.PLAIN, 20);			
		Font font25 = new Font("SansSerif", Font.PLAIN, 25);			
		Font font27 = new Font("SansSerif", Font.PLAIN, 27);		
		Font font30 = new Font("SansSerif", Font.PLAIN, 30);			
		Font font35 = new Font("SansSerif", Font.PLAIN, 35);		
		Font font50 = new Font("SansSerif", Font.PLAIN, 50);			
		TextRoi roi1 = new TextRoi(X-137+deltaX,Y-450+deltaY, "Growing\n  speed", font30);			
		TextRoi roi2 = new TextRoi(X-115+deltaX,Y-365+deltaY, ""+vMaxDisplayed+"", font25);			
		TextRoi roi3 = new TextRoi(X-115+deltaX,Y-150+deltaY, "0", font25);			
		TextRoi roi4 = new TextRoi(X-122+deltaX,Y-325+deltaY, "-", font25);			
		TextRoi roi5 = new TextRoi(X-122+deltaX,Y-108+deltaY, "-", font25);			
		TextRoi roi6 = new TextRoi(X-117+deltaX,Y-105+deltaY, "(mm/day)", font19);			
		double lengBar=220;
		double widBar=30;
		int hei=200;
		int x0=X-147+deltaX;
		int y0=Y-460+deltaY-hei-10;
		int x1=x0+wid;
		int y1=y0+hei;
		TextRoi titleTime0 = new TextRoi(x0+15,y0+7, "Timesteps", font25);			
		TextRoi titleTime1 = new TextRoi(x0+35,y0+7, "Time (h)", font25);			
		TextRoi titleTimeBack = new TextRoi(x0+7,y0+170, "", font19);

		double tMax=VitimageUtils.max(t);
		int tM=(int) Math.ceil(tMax)-1;
		TextRoi[] roiTab1=new TextRoi[tM+1];
		TextRoi[] roiTab2=new TextRoi[tM];
		int t1wid=20;
		int t2wid=100;

		
		for(int n=0;n<N;n++) {
			if((n%50)==0)System.out.print(n+"/"+N+" ");
			for(int x=0;x<X;x++) {
				for(int y=0;y<Y;y++) {
					indexSpace=y*X+x;						
					
					
					if(((int)(maskRoot[n][indexSpace]  & 0xff)>140)){//If root segmentation
						resR[n][indexSpace]=root[n][indexSpace];
						resG[n][indexSpace]=root[n][indexSpace];
						resB[n][indexSpace]=root[n][indexSpace];

						//Draw skeleton, if any
						if(modelMode1Activated) {
							int vls=((int)(skel[n][indexSpace]  & 0xff));
							if(vls>0 ){
								if(vls==1) {//primary
									resR[n][indexSpace]=toByte(255);
									resG[n][indexSpace]=toByte(0);
									resB[n][indexSpace]=toByte(0);
								}
								if(vls==2) {//secondary
									resR[n][indexSpace]=toByte(0);
									resG[n][indexSpace]=toByte(255);
									resB[n][indexSpace]=toByte(0);
								}
								if(vls==3) {//nodes
									resR[n][indexSpace]=toByte(255);
									resG[n][indexSpace]=toByte(255);
									resB[n][indexSpace]=toByte(255);
								}
							}
						}
						if(modelMode2Activated) {
							int vls=((int)(skel[skel.length-1][indexSpace]  & 0xff));
							if(vls>0 ){
								if(vls==1) {//primary
									resR[n][indexSpace]=toByte(toInt(resR[n][indexSpace])*2);
								}
								if(vls==2) {//secondary
									resG[n][indexSpace]=toByte(toInt(resG[n][indexSpace])*2);
								}
							}
						}
					}
					else if(toInt(ident[n][indexSpace])>0){
						weig=toInt(ident[n][indexSpace])/255.0;
						if(fire[n][indexSpace]==lisereVal){//If included in lisere		
							val=toByte( (1-weig)*toInt(root[n][indexSpace]) + weig*toInt(lisereOutVal) );
							resR[n][indexSpace]=val;
							resG[n][indexSpace]=val;
							resB[n][indexSpace]=val;
						}
						else {
							byte[]col=colorMapFire[toInt( fire[n][indexSpace])];
							resR[n][indexSpace] = toByte( (1-weig)*toInt(root[n][indexSpace]) + weig*toInt(col[0]) );
							resG[n][indexSpace] = toByte( (1-weig)*toInt(root[n][indexSpace]) + weig*toInt(col[1]) );
							resB[n][indexSpace] = toByte( (1-weig)*toInt(root[n][indexSpace]) + weig*toInt(col[2]) );
						}
					}					
					else if( ((int)(maskGrid[n][indexSpace] & 0xff))!=0 && ((int)(grid[indexSpace] & 0xff))!=0){//Else if included in grid, weighted copy grid and root
						weig=((int)(maskGrid[n][indexSpace] & 0xff))/255.0;
						val=(byte)( (int)(weig*((int)(grid[indexSpace]  & 0xff))+(1-weig)*(int)(root[n][indexSpace]  & 0xff))  & 0xff);
						resR[n][indexSpace]=val;
						resG[n][indexSpace]=val;
						resB[n][indexSpace]=val;
						if(modelMode2Activated) {
							int vls=((int)(skel[skel.length-1][indexSpace]  & 0xff));
							if(vls>0 ){
								if(vls==1) {//primary
									resR[n][indexSpace]=toByte(toInt(resR[n][indexSpace])*2);
									resG[n][indexSpace]=toByte(toInt(resG[n][indexSpace])*0.8);
									resB[n][indexSpace]=toByte(toInt(resB[n][indexSpace])*0.8);
								}
								if(vls==2) {//secondary
									resG[n][indexSpace]=toByte(toInt(resG[n][indexSpace])*2);
									resR[n][indexSpace]=toByte(toInt(resR[n][indexSpace])*0.8);
									resB[n][indexSpace]=toByte(toInt(resB[n][indexSpace])*0.8);
								}
							}
						}
					}						
					else {
						resR[n][indexSpace]=root[n][indexSpace];
						resG[n][indexSpace]=root[n][indexSpace];
						resB[n][indexSpace]=root[n][indexSpace];
						if(modelMode2Activated) {
							int vls=((int)(skel[skel.length-1][indexSpace]  & 0xff));
							if(vls>0 ){
								if(vls==1) {//primary
									resR[n][indexSpace]=toByte(toInt(resR[n][indexSpace])*2);
								}
								if(vls==2) {//secondary
									resG[n][indexSpace]=toByte(toInt(resG[n][indexSpace])*2);
								}
							}
						}
					}
				}
			}			
			//Draw fire legend
			//draw rectangle
			for(int xx=X-147+deltaX;xx<X-147+wid+deltaX;xx++) {
				for(int yy=Y-455+deltaY;yy<Y-70+deltaY;yy++) {
					indexSpace=yy*X+xx;			
					resR[n][indexSpace]=toByte(toInt(resR[n][indexSpace])*0.3);
					resG[n][indexSpace]=toByte(toInt(resG[n][indexSpace])*0.3);
					resB[n][indexSpace]=toByte(toInt(resB[n][indexSpace])*0.3);
				}
			}
			for(int xx=X-80+deltaX;xx<X-80+deltaX+widBar;xx++) {
				for(int yy=Y-350+deltaY;yy<Y-350+deltaY+lengBar;yy++) {
					indexSpace=yy*X+xx;			
					int index=(int) ((Y-350+deltaY+lengBar-yy)/lengBar*255.0);
					resR[n][indexSpace]=colorMapFire [index][0];
					resG[n][indexSpace]=colorMapFire[index][1];
					resB[n][indexSpace]=colorMapFire[index][2];
				}
			}
			for(int c=0;c<3;c++) {
				ImageProcessor ip=resChan[c].getStack().getProcessor(n+1+delta);				
				ip.setAntialiasedText(true);ip.draw(roi1);ip.draw(roi2);ip.draw(roi3);ip.draw(roi6);
			}
			
			
			//Draw time legend
			double thisT=t[n]-1;
			if(thisT<0)thisT=0;
			//Draw rectangle
			for(int xx=x0;xx<x1;xx++) {
				for(int yy=y0;yy<y1;yy++) {
					indexSpace=yy*X+xx;			
					resR[n][indexSpace]=toByte(toInt(resR[n][indexSpace])*0.3);
					resG[n][indexSpace]=toByte(toInt(resG[n][indexSpace])*0.3);
					resB[n][indexSpace]=toByte(toInt(resB[n][indexSpace])*0.3);
				}
			}
			//Draw horizontal timeline
			for(int xx=x0+6;xx<x1-6;xx++) {
				for(int yy=y0+86+15;yy<y0+90+15;yy++) {
					indexSpace=yy*X+xx;			
					resR[n][indexSpace]=toByte(255);
					resG[n][indexSpace]=toByte(255);
					resB[n][indexSpace]=toByte(255);
				}
			}
			//Draw triangle
			int twid=25;
			int thei=24;
			int x0Tri=70;
			int y0Tri=106+15;
			int count1=0;int count2=0;
			for(int dx=-twid/2;dx<twid/2;dx++) {
				for(int dy=0;dy<thei;dy++) {
					count1++;
					//System.out.println(dx+","+dy+" et 1="+(Math.abs(dx*2.0)/twid)+" et 2="+(dy*1.0/thei));
					if((Math.abs(dx*2.0)/twid)<(dy*1.0/thei)) {
						count2++;
						int xx=dx+x0Tri+x0;
						int yy=dy+y0Tri+y0;
						indexSpace=yy*X+xx;			
						resR[n][indexSpace]=toByte(255);
						resG[n][indexSpace]=toByte(255);
						resB[n][indexSpace]=toByte(255);
					}
				}
			}
			for(int c=0;c<3;c++) {
				ImageProcessor ip=resChan[c].getStack().getProcessor(n+1+delta);				
				ip.setAntialiasedText(true);ip.draw(titleTime1);ip.draw(titleTimeBack);ip.draw(titleTimeBack);
			}
			
			//Draw the time values during the interpolated sequence
			double dtHours=20;
			while(tMax/dtHours > 20)dtHours*=2;
			while(tMax/dtHours < 5)dtHours/=2;
			int nR=(int) Math.ceil(tMax/dtHours);
			TextRoi textRoi;
			for(int r=0;r<nR;r++) {
				int tr=(int) (dtHours*r);
				double xR=(int) (15+wid/4+ x0 +(tr - t[n])*wid/15.0);//xr0=x0+wid/2 at t=0 and x0
				int yR=y0+52;
				//System.out.println("At r="+r+" coords="+xR+","+yR+" with wid="+wid+" and (r+1.5)*wid/2.0="+((r+1.5)*wid/2.0)+" and -tr*wid/20.0="+(-tr*wid/20.0));
				if(xR<x0)xR=-1000;
				if(xR>(x1-t1wid-20))xR=-1000;
				textRoi=new TextRoi(xR+2,yR-7+15,""+tr+"h", font27);			//draw time value
					
				//Write text				
				for(int c=0;c<3;c++) {
					ImageProcessor ip=resChan[c].getStack().getProcessor(n+1+delta);				
					ip.setAntialiasedText(true);
					ip.setColor(Color.white);
					ip.draw(textRoi);
				}
								
				//Vertical lines				
				yR=y0+80;
				if(xR<x0+2)continue;
				if(xR>x1-40)continue;
				for(int xx=(int) (xR-2)+20;xx<xR+2+20;xx++) {
					for(int yy=yR+15;yy<yR+17+15;yy++) {
						indexSpace=yy*X+xx;			
						resR[n][indexSpace]=toByte(225);
						resG[n][indexSpace]=toByte(225);
						resB[n][indexSpace]=toByte(225);
					}
				}
			}
			//VitimageUtils.waitFor(100000);
			//Draw scaleBar			
			int nPixCm=(int) (10000/umPerPixel);
			for(int xx=X-120;xx<X-115;xx++) {
				for(int yy=3*nPixCm;yy<4*nPixCm;yy++) {
					indexSpace=yy*X+xx;			
					resR[n][indexSpace]=toByte(0);
					resG[n][indexSpace]=toByte(0);
					resB[n][indexSpace]=toByte(0);
				}
			}
			TextRoi scaleText=new TextRoi(X-100,3.4*nPixCm,"1 cm", font30);	
			
			for(int c=0;c<3;c++) {
				ImageProcessor ip=resChan[c].getStack().getProcessor(n+1+delta);			
				ip.setColor(Color.black);
				ip.setAntialiasedText(true);ip.draw(scaleText);
				
			}			
		}

		
		//Generate first keyframes
		if(joinOpening) {
			int incr=-1;
			byte[]im;
			//Show image N and trailer during 3 seconds
			im=(byte[]) initReg.getStack().getPixels(1);
			for(int n=0;n<75;n++) {
				incr++;
				for(int xx=0;xx<X;xx++) for(int yy=0;yy<Y;yy++) {
					indexSpace=yy*X+xx;			
					resInitR[incr][indexSpace]=im[indexSpace];
					resInitG[incr][indexSpace]=im[indexSpace];
					resInitB[incr][indexSpace]=im[indexSpace];
				}
				for(int c=0;c<3;c++) {
					ImageProcessor ip=resChan[c].getStack().getProcessor(incr+1);				
					TextRoi titleArchi = new TextRoi(X/2-360,3*Y/4 ,"Observation of root systems", font50);
					ip.setColor(Color.white);
					ip.setAntialiasedText(true);ip.draw(titleArchi);
				}

			}
				
			//Show each image 1/2 second during 5 images, then each every 1/4 second
			for(int m=1;m<=TN;m++) {
				im=(byte[]) initReg.getStack().getPixels(m);
				int lim=20;
				if(m>5)lim=12;
				if(m>10)lim=7;
				if(m==TN)lim=75;
				for(int n=0;n<lim;n++) {
					incr++;
					for(int xx=0;xx<X;xx++) for(int yy=0;yy<Y;yy++) {
						indexSpace=yy*X+xx;			
						resInitR[incr][indexSpace]=im[indexSpace];
						resInitG[incr][indexSpace]=im[indexSpace];
						resInitB[incr][indexSpace]=im[indexSpace];
					}
					if(m==TN) {
						for(int c=0;c<3;c++) {
							ImageProcessor ip=resChan[c].getStack().getProcessor(incr+1);				
							TextRoi titleArchi = new TextRoi(X/2-320,3*Y/4 ,"Architecture reconstruction", font50);
							ip.setColor(Color.white);
							ip.setAntialiasedText(true);ip.draw(titleArchi);
						}
					}
					
					//Draw rectangle
					for(int xx=x0;xx<x1;xx++) {
						for(int yy=y0;yy<y1;yy++) {
							indexSpace=yy*X+xx;			
							resInitR[incr][indexSpace]=toByte(toInt(resInitR[incr][indexSpace])*0.3);
							resInitG[incr][indexSpace]=toByte(toInt(resInitG[incr][indexSpace])*0.3);
							resInitB[incr][indexSpace]=toByte(toInt(resInitB[incr][indexSpace])*0.3);
						}
					}
					//Draw horizontal timeline
					for(int xx=x0+6;xx<x1-6;xx++) {
						for(int yy=y0+86+15;yy<y0+90+15;yy++) {
							indexSpace=yy*X+xx;			
							resInitR[incr][indexSpace]=toByte(255);
							resInitG[incr][indexSpace]=toByte(255);
							resInitB[incr][indexSpace]=toByte(255);
						}
					}
					//Draw triangle
					int twid=25;
					int thei=24;
					int x0Tri=70;
					int y0Tri=106+15;
					int count1=0;int count2=0;
					for(int dx=-twid/2;dx<twid/2;dx++) {
						for(int dy=0;dy<thei;dy++) {
							count1++;
							//System.out.println(dx+","+dy+" et 1="+(Math.abs(dx*2.0)/twid)+" et 2="+(dy*1.0/thei));
							if((Math.abs(dx*2.0)/twid)<(dy*1.0/thei)) {
								count2++;
								int xx=dx+x0Tri+x0;
								int yy=dy+y0Tri+y0;
								indexSpace=yy*X+xx;			
								resInitR[incr][indexSpace]=toByte(255);
								resInitG[incr][indexSpace]=toByte(255);
								resInitB[incr][indexSpace]=toByte(255);
							}
						}
					}
					for(int c=0;c<3;c++) {
						ImageProcessor ip=resChan[c].getStack().getProcessor(incr+1);				
						ip.setAntialiasedText(true);ip.draw(titleTime0);ip.draw(titleTimeBack);ip.draw(titleTimeBack);
					}
					for(int r=0;r<tM+1;r++) {
						double xR=(int) (15+x0+(r+1.5)*wid/2.0-(m-1)*wid/2.0);//xr0=x0+wid/2 at t=0 and x0
						int yR=y0+52;
						if(xR<x0)xR=-1000;
						if(xR>(x1-t1wid-20))xR=-1000;
						roiTab1[r]=new TextRoi(xR+2,yR-7+15,"t"+r, font27);			
						xR=(int) (x0-40+(r+1.5)*wid/2.0+wid/4.0-(m-1)*wid/2.0);//xr0=x0+wid/2 at t=0 and x0
						if(xR<x0-t2wid)xR=-1000;
						if(xR>x1-t1wid)xR=-1000;
						if(r<(tM))roiTab2[r]=new TextRoi(xR,yR+4,"", font19);			
	
						//Write text				
						for(int c=0;c<3;c++) {
							ImageProcessor ip=resChan[c].getStack().getProcessor(incr+1);				
							ip.setAntialiasedText(true);ip.draw(roiTab1[r]);if(r<tM)ip.draw(roiTab2[r]);
						}
										
						//Vertical lines				
						xR=(int) (x0+15+(r+1.5)*wid/2-(m-1)*wid/2);//xr0=x0+wid/2 at t=0 and x0
						yR=y0+80;
						if(xR<x0+2)continue;
						if(xR>x1-40)continue;
						for(int xx=(int) (xR-2)+20;xx<xR+2+20;xx++) {
							for(int yy=yR+15;yy<yR+17+15;yy++) {
								indexSpace=yy*X+xx;			
								resInitR[incr][indexSpace]=toByte(225);
								resInitG[incr][indexSpace]=toByte(225);
								resInitB[incr][indexSpace]=toByte(225);
							}
						}
					}
				}
			}
//			IJ.showMessage(""+incr);
		}		
		return VitimageUtils.compositeRGBByte(resChan[0],resChan[1],resChan[2], 1, 1, 1);
	}
		
	
	
	
 
	
	
	
	
	
	
	//SAFE
	/** Helpers ----------------------------------------------------------------------------------------------------------*/
	//Major helpers
	//TODO : make it depends on sliceindices and delta between
	public static ImagePlus[] generateGridAndFire(int X,int Y, double[][][]coords,ImagePlus maskRootInit) {
		System.out.println("\nGenerating grid and fire V2");
		tim.print("Start");
		ImagePlus rootInitArea=MorphoUtils.dilationCircle2D(maskRootInit, 50);
		rootInitArea=VitimageUtils.gaussianFiltering(rootInitArea, 40, 40, 0);
		rootInitArea.setDisplayRange(0, 255);
		IJ.run(rootInitArea,"8-bit","");
		tim.print("Prepa img ok.");


		int gridVal=172;
		double G= (1000/umPerPixel);//nb pixels per mm
		int N=coords.length;
		int P=coords[0].length;
		boolean []isPrimary=new boolean[P];
		int nPrim=0;
		for(int p=0;p<P;p++)if(coords[0][p][4]==1) {isPrimary[p]=true;nPrim++;}
		int sizeRatio=15;//Divide the image space to define the objects size (circles and arrow). The larger the factor, the smaller the objects
		int deltaN=(int)(N*startingBlockRatio);//Used for making appearing roots progressively
		int []nStart=new int[P];
		for(int p=0;p<P;p++) {
			nStart[p]=N-1;
			for(int n=N-1;n>=0;n--)if(coords[n][p][0]>=0)nStart[p]=n;
		}
		int R=X/sizeRatio;
		int Rcircle=R/12;
		
		int Rarrow=R/12;
		double lisere=3;
		double anisArrow=2;
		ImagePlus imgOut=IJ.createImage("", X, Y, N, 8);
		ImagePlus imgIdent=IJ.createImage("", X, Y, N, 8);
		ImagePlus imgGrid=IJ.createImage("", X, Y, N, 8);
		ImagePlus imgMaskGrid=IJ.createImage("", X, Y, N, 8);
		byte[][]tabOut=new byte[N][];
		byte[][]tabIdent=new byte[N][];
		byte[][]tabMaskGrid=new byte[N][];
		byte[]tabGrid;
		byte[]tabMaskInit;
		for(int n=0;n<N;n++) {
			tabOut[n]=(byte[]) imgOut.getStack().getProcessor(n+1).getPixels();
			tabIdent[n]=(byte[]) imgIdent.getStack().getProcessor(n+1).getPixels();
			tabMaskGrid[n]=(byte[]) imgMaskGrid.getStack().getProcessor(n+1).getPixels();
		}
		tabGrid=(byte[]) imgGrid.getStack().getProcessor(1).getPixels();
		tabMaskInit=(byte[]) rootInitArea.getStack().getProcessor(1).getPixels();
		int indexSpace=0;
		//int indexTime=0;
		int deltaDisplayN = N/20;
		tim.print("Starting stack video genesis");
		for(int n=0;n<N;n++) {
			if((n%deltaDisplayN)==0)tim.print(n+"/"+N+" ");
			for(int p=0;p<P;p++) {
				double x0=coords[n][p][0];
				double y0=coords[n][p][1];
				double vx=coords[n][p][2];
				double vy=coords[n][p][3];
				double normV=Math.sqrt(vx*vx+vy*vy);
				if(normV==0)normV=VitimageUtils.EPSILON;
				if(x0<=0)continue;
				if(nStart[p]==-1)nStart[p]=n;
				
				double alpha=1;//Transparency to make the root "appearing"
				if(n<nStart[p]+deltaN) 							alpha=(n-nStart[p])/(1.0*deltaN);

				//Preparing the appearing grid
				double xx0=(x0);
				double yy0=(y0);
				for(int x=(int) (xx0-R);x<=xx0+R;x++) {
					if(x<0 || x>=X)continue;
					for(int y=(int) (yy0-R);y<=yy0+R;y++) {
						if(y<0 || y>=Y)continue;
						boolean debug=false&&(x==607 && y==156);
						double normC=Math.sqrt((x-x0)*(x-x0)+(y-y0)*(y-y0));
						int valMask=(int) Math.min(255,255*  1.5*VitimageUtils.laplacian(normC, R/4) );
						indexSpace=y*X+x;				
						tabMaskGrid[n][indexSpace]=(byte)(((int)Math.max (valMask,(int)(tabMaskGrid[n][indexSpace]& 0xff) )) & 0xff);
					}
				}
				//Preparing the arrow
				double lisereSize=isPrimary[p] ? lisere*1.0 : lisere * 0.8;
				int Rcirc=( isPrimary[p]) ? (int)(Rcircle*1.7) : Rcircle;
				int Rar=(isPrimary[p] ) ? (int)(Rarrow*1.9) : (int)(Rarrow*1.2);
				double vxNorm=vx/normV;
				double vyNorm=vy/normV;
				xx0=(x0+(Rcirc-(isPrimary[p] ? 7:3))*vxNorm);
				yy0=(y0+(Rcirc-(isPrimary[p] ? 7:3))*vyNorm);
				double vxOrth=-vyNorm;
				double vyOrth=vxNorm;
				double targetDY=Rar*anisArrow;
				//double valVit=255*(normV/vMaxInUse);
				byte bVit=toByte(50+205*(normV/vMaxInUse));
				byte bP=isPrimary[p] ? toByte(1) : toByte(2);
				for(double dx=-Rar;dx<=Rar;dx+=0.5) {
					for(double dy=0;dy<=targetDY+1;dy+=0.5) {
						int x=(int) (xx0+dx*vxOrth+dy*vxNorm);
						int y=(int) (yy0+dx*vyOrth+dy*vyNorm);
						if(x<0 || x>=X)continue;
						if(y<0 || y>=Y)continue;
						indexSpace=y*X+x;						
						if(dy<=lisereSize) {						}
						else if(Math.abs(dx)>(lisereSize/10.0+(targetDY-dy)/anisArrow)) {}
						else if(Math.abs(dx)>(-lisereSize+(targetDY-dy)/anisArrow)) {
							double delt=Math.abs(dx)- ((-lisereSize+(targetDY-dy)/anisArrow))  ;
							double valFlou=VitimageUtils.laplacian(delt*delt,lisereSize*lisereSize/3);							
							tabIdent[n][indexSpace]=toByte(255*valFlou);
							tabOut[n][indexSpace]=bVit;
						}   
//						else if(Math.abs(dx)>((targetDY-dy)/anisArrow))tabOut[n][indexSpace]=lisereVal;
						else  {
							tabIdent[n][indexSpace]=toByte(255);
							tabOut[n][indexSpace]=bVit;
						}
						if(n<nStart[p]+deltaN)tabIdent[n][indexSpace]=toByte(alpha*toInt(tabIdent[n][indexSpace]));
					}
				}
				//Preparing the circle
				xx0=(x0);
				yy0=(y0);
				lisereSize=isPrimary[p] ? lisere*0.5 : lisere * 0.4;
				for(int ddx=(int) (-Rcirc-lisereSize);ddx<=Rcirc+lisereSize;ddx++) {
					int x=(int) (xx0+ddx);
					if(x<0 || x>=X)continue;
					for(int ddy=(int) (-Rcirc-lisereSize);ddy<=Rcirc+lisereSize;ddy++) {
						int y=(int) (yy0+ddy);
						double dx=x-xx0;
						double dy=y-yy0;
						if(y<0 || y>=Y)continue;
						double sqr=Math.sqrt(dx*dx+dy*dy);
						if(sqr>Rcirc+lisereSize*1.5)continue;
						indexSpace=y*X+x;				
						if(sqr<Rcirc-lisereSize) {//drawInside
							tabOut[n][indexSpace]=bVit;
							tabIdent[n][indexSpace]=toByte(10);}
						else{
							double delt=Math.abs(sqr-Rcirc);
							double valFlou=VitimageUtils.laplacian(delt*delt,lisereSize*lisereSize/2);
							tabOut[n][indexSpace]=bVit;// : lisereVal;
							double temp=Math.max(toDouble(toByte(255*valFlou)), toDouble(tabIdent[n][indexSpace]));
							tabIdent[n][indexSpace]=toByte(temp);
						}
						if(n<nStart[p]+deltaN)tabIdent[n][indexSpace]=toByte(alpha*toInt(tabIdent[n][indexSpace]));
					}
				}

			}
			
			if(n==0) {
				for(int x=0;x<X;x++) {
					for(int y=0;y<Y;y++) {
						indexSpace=y*X+x;						
						tabMaskGrid[n][indexSpace]=tabMaskInit[indexSpace];
						int nearGridX=(int)Math.round( x/G );
						int nearGridY=(int)Math.round( y/G );
						int dgx=(int) (x-nearGridX*G);
						int dgy=(int) (y-nearGridY*G);
						int dgAbsx=Math.abs(dgx);
						int dgAbsy=Math.abs(dgy);
						if(dgAbsx<4 && ((nearGridX%10)==0)) {tabGrid[indexSpace]=(byte)(gridVal &0xff);continue;}
						if(dgAbsy<4 && ((nearGridY%10)==0)) {tabGrid[indexSpace]=(byte)(gridVal &0xff);continue;}
						if(dgAbsx<3 && ((nearGridX%10)==0)) {tabGrid[indexSpace]=(byte)(gridVal &0xff);continue;}
						if(dgAbsy<3 && ((nearGridY%10)==0)) {tabGrid[indexSpace]=(byte)(gridVal &0xff);continue;}
						if(dgAbsx<2 && ((nearGridX%10)==0)) {tabGrid[indexSpace]=(byte)(gridVal &0xff);continue;}
						if(dgAbsy<2 && ((nearGridY%10)==0)) {tabGrid[indexSpace]=(byte)(gridVal &0xff);continue;}
						if(dgAbsx<1) {tabGrid[indexSpace]=(byte)(gridVal &0xff);continue;}
						if(dgAbsy<1) {tabGrid[indexSpace]=(byte)(gridVal &0xff);continue;}
					}
				}
			}
			else {
				for(int x=0;x<X;x++) {
					for(int y=0;y<Y;y++) {
						indexSpace=y*X+x;													
						int bef=(int)(tabMaskGrid[n-1][indexSpace]&0xff);
						int now=(int)(tabMaskGrid[n][indexSpace]&0xff);
						if(bef>now) {
							now=bef;
							tabMaskGrid[n][indexSpace]=(byte)(now & 0xff);
						}
					}
				}
			}
		}
		imgOut.setTitle("Fire");
		imgOut.setDisplayRange(0, 255);
		imgGrid.setTitle("Grid");
		imgGrid.setDisplayRange(0, 255);
		imgMaskGrid.setTitle("MaskGrid");
		imgMaskGrid.setDisplayRange(0, 255);
		IJ.run(imgOut,"Fire","");
		IJ.run(imgGrid,"Fire","");
		return new ImagePlus [] {imgOut,imgGrid,imgMaskGrid,imgIdent};
	}

	
	public static ImagePlus mixFgAndBgFromMaskAndStack(ImagePlus imgRootMask,ImagePlus regStack,ImagePlus maskUpLeaves) {
		System.out.println("Generating mix");

		ImagePlus imgInReg=VitimageUtils.convertToFloat(regStack);
		int X=imgInReg.getWidth();
		int Y=imgInReg.getHeight();
		int N=imgRootMask.getStackSize();
		int Nt=imgInReg.getStackSize();
		ImagePlus imgOut=IJ.createImage("", X, Y, N, 8);
		byte[][]tabOut=new byte[N][];
		byte[]tabMaskUp=new byte[N];
		float[][]tabInMask=new float[N][];
		float[][]tabInReg=new float[Nt][];
		for(int n=0;n<N;n++) {
			tabOut[n]=(byte[]) imgOut.getStack().getProcessor(n+1).getPixels();
			tabInMask[n]=(float[]) imgRootMask.getStack().getProcessor(n+1).getPixels();
		}
		for(int n=0;n<Nt;n++) {
			tabInReg[n]=(float[]) imgInReg.getStack().getProcessor(n+1).getPixels();
		}
		tabMaskUp=(byte[]) maskUpLeaves.getStack().getProcessor(1).getPixels();
		int indexSpace=0;
		double tt=0;
		double valMixFg;
		double valMixBg;
		double delta=0;
		double deltaBg=t1Bg-t0Bg;
		double deltaFg=t1Fg-t0Fg;
		int T0=0;
		
		for(int n=0;n<N;n++) {
			T0=indexImgBef[n];
			double deltaT=deltaRatioToBef[n];
			

			if((n%50)==0)System.out.print(n+"/"+N+" ");
			for(int x=0;x<X;x++) {
				for(int y=0;y<Y;y++) {
					indexSpace=y*X+x;
					
					////BUILDING Value for BG (not plant points)
					//If we are in the upper part of the image, smoothly between t0Bg and t1Bg (at start between T0 and T1)
					if(toInt(tabMaskUp[indexSpace])==0) {
						if(deltaT<=t0Bg)valMixBg=tabInReg[T0][indexSpace];
						else if(deltaT>=t1Bg) {
							valMixBg=tabInReg[((T0+1)>Nt-1) ? T0 : (T0+1)][indexSpace];
						}
						else {
							delta=(deltaT-t0Bg)/deltaBg;
							valMixBg=delta*tabInReg[T0+1][indexSpace]+(1-delta)*tabInReg[T0][indexSpace];
						}
					}
					else {
						//If we are in the plant, don't care, and display the original image // TODO : faire un test pour faire apparaitre la mousse de la nuit
						valMixBg=tabInReg[0][indexSpace];						
					}

					
					////BUILDING Value for FG
					int t1=T0+1;if(t1>=Nt-1)t1=Nt-1;
					int t2=T0+2;if(t2>=Nt-1)t2=Nt-1;
					int t3=T0+3;if(t3>=Nt-1)t3=Nt-1;
					if(deltaT<=t0Fg)valMixFg=tabInReg[t1][indexSpace];
					else if(deltaT>=t1Fg)valMixFg=tabInReg[t2][indexSpace];
					else {
						delta=(deltaT-t0Fg)/deltaFg;
						valMixFg=delta*tabInReg[t3][indexSpace]+(1-delta)*tabInReg[t2][indexSpace];
					}
					double deltaMix=tabInMask[n][indexSpace];
					tabOut[n][indexSpace]=toByte(valMixFg*deltaMix + valMixBg*(1-deltaMix));//(float) (valMixBg*(1-deltaMix));//
				}
			}
		}
		System.out.println();
		imgOut.setDisplayRange(0, 255);
		return imgOut;		
	}

	public static ImagePlus generateModelRGBFromRootModel(RootModel rm,ImagePlus img) {
		return rm.createGrayScaleImageTimeLapse(img,t,  new double[] {primaryRadius,secondaryRadius},0);
	}

	public static ImagePlus[] generateGridAndFireFromRootModel(RootModel rm, ImagePlus img,ImagePlus maskRootInit) {
		int X=img.getWidth();
		int Y=img.getHeight();
		double[][][]coords=getAsTimeLapseCoords(rm);
		return generateGridAndFire(X,Y,coords,maskRootInit);		
	}
	
	public static ImagePlus generateFgBgMask(ImagePlus imgIn) {
		System.out.println("\nGenerating mix mask");
		int X=imgIn.getWidth();
		int Y=imgIn.getHeight();
		int N=t.length;
		
		ImagePlus imgOut=IJ.createImage("", X, Y, N, 32);
		float[][]tabOut=new float[N][];
		float[]tabIn=(float[]) imgIn.getStack().getProcessor(1).getPixels();
		for(int n=0;n<N;n++)tabOut[n]=(float[]) imgOut.getStack().getProcessor(n+1).getPixels();
		int indexSpace=0;
		int indexTime=0;
		for(int x=0;x<X;x++) {
			if((x%200)==0)System.out.print(x+"/"+X+" ");
			for(int y=0;y<Y;y++) {
				indexSpace=y*X+x;
				double val=(tabIn[indexSpace]);
				if(val<0)indexTime=-1;
				else indexTime=getIndex(val);
				if(indexTime==-1)continue;
				for(int n=indexTime;n<N;n++) tabOut[n][indexSpace]=1;
			}
		}
		imgOut.setDisplayRange(0, 1);
		System.out.println();
		double sigX=1.5;
		double sigY=1.5;
		double sigZ=1.5;
		IJ.run(imgOut, "Gaussian Blur 3D...", "x="+sigX+" y="+sigY+" z="+sigZ);			
		return imgOut;
	}

	public static double[][][]getAsTimeLapseCoords(RootModel rm){
		int N=t.length;
		int P=rm.rootList.size();
		double[][][]coords=new double[N][P][5];
		for(int p=0;p<P;p++) {
			double[][]coordRoots=getAsTimeLapseCoords(rm.rootList.get(p));
			for(int n=0;n<N;n++)for(int c=0;c<5;c++)coords[n][p][c]=coordRoots[n][c];
		}
		return coords;
	}
				 
	public static double[][]getAsTimeLapseCoords(Root r){
		int N=t.length;
		ArrayList<Node>nodes=r.getNodesList();
		int nNodes=nodes.size();
		double[][]coords=new double[N][5];
		double tMin=nodes.get(0).birthTimeHours;
		double tMax=nodes.get(nNodes-1).birthTimeHours;
		for(int n=0;n<N;n++) {
			boolean isPrimary=(r.getParent()==null);
			if(t[n]<=tMin)coords[n]=new double[] {-1,-1,-1,-1,isPrimary?1:2};
			else if(t[n]>tMax)coords[n]=new double[] {-1,-1,-1,-1,isPrimary?1:2};
			else {
				//Find before and after
				int indBef=-1;
				int indAft=-1;
				for(int nn=0;nn<nNodes-1;nn++) {
					if(nodes.get(nn).birthTimeHours<t[n] && nodes.get(nn+1).birthTimeHours>=t[n]) {
						indBef=nn;indAft=nn+1;
						break;
					}
				}
				Node n0=nodes.get(indBef);
				Node n1=nodes.get(indAft);				
				double t0=n0.birthTimeHours;
				double t1=n1.birthTimeHours;
				double alpha=(t[n]-t0)/(t1-t0);
				coords[n][0]=n1.x*alpha+(1-alpha)*n0.x;
				coords[n][1]=n1.y*alpha+(1-alpha)*n0.y;
				coords[n][2]=n1.vx*alpha+(1-alpha)*n0.vx;
				coords[n][3]=n1.vy*alpha+(1-alpha)*n0.vy;
				coords[n][4]=isPrimary?1:2;
			}
		}		
		return coords;
	}	

	public static void setSamplesT(){
		ArrayList<double[]>ar=new ArrayList<double[]>();
		ArrayList<double[]>arT=new ArrayList<double[]>();
		int curFrame=0;	
		double curT=0;
		double maxT=hoursExtremities[TN];
		int valindexAft,valindexBef;
		double valdeltaHoursToAft,valdeltaHoursToBef,valdeltaRatioToBef;
		int incr=0;
		while(curT<maxT) {
			incr++;
			double alpha=1;
			if(smartHandyStart) {//Progressive starting of the growth with acceleration driven by the slowing factor alpha
				if(curT<(maxT/30))alpha=3;
				else if(curT<(maxT/22))alpha=2.5;
				else if(curT<(maxT/18))alpha=2.2;
				else if(curT<(maxT/15))alpha=2.0;
				else if(curT<(maxT/13))alpha=1.8;
				else if(curT<(maxT/11))alpha=1.6;
				else if(curT<(maxT/9))alpha=1.4;
				else if(curT<(maxT/8))alpha=1.2;
				else alpha=1;
			}
			curT+=(timeStep*1.0/alpha);
			if(curT>=maxT)break;
			
			valindexAft=getIndexAft(curT);
			if(valindexAft==0) {
				valindexBef=0;
				valdeltaHoursToAft=maxT/TN;
				valdeltaHoursToBef=0;
				valdeltaRatioToBef=0;
				
			}
			else {
				valindexBef=valindexAft-1;
				valdeltaHoursToAft=hoursExtremities[valindexAft+1]-curT;
				valdeltaHoursToBef=curT-hoursExtremities[valindexBef+1];
				valdeltaRatioToBef=valdeltaHoursToBef/(valdeltaHoursToBef+valdeltaHoursToAft);
			}
			ar.add(new double[] {curT,valdeltaRatioToBef,valdeltaHoursToBef,valdeltaHoursToAft,valindexBef,valindexAft});

		}
		for(int i=0;i<endingAdditionalFrames;i++) {
			ar.add(new double[] {hoursExtremities[TN],1,maxT/TN,0,TN-1,TN});
		}

		//Gather computed values in class static field
		t=new double[ar.size()];
		deltaRatioToBef=new double[ar.size()];
		deltaHoursToBef=new double[ar.size()];
		deltaHoursToAft=new double[ar.size()];
		indexImgBef=new int[ar.size()];
		indexImgAft=new int[ar.size()];
		for(int i=0;i<ar.size();i++) {
			t[i]=ar.get(i)[0];
			deltaRatioToBef[i]=ar.get(i)[1];
			deltaHoursToBef[i]=ar.get(i)[2];
			deltaHoursToAft[i]=ar.get(i)[3];
			indexImgBef[i]=(int)ar.get(i)[4];
			indexImgAft[i]=(int)ar.get(i)[5];
		}
	}

	public static int getIndexAft(double tt) {
		if(tt<=hoursExtremities[0])return 0;
		if(tt>=hoursExtremities[TN])return TN;
		for(int i=0;i<hoursExtremities.length-1;i++)if((tt>=hoursExtremities[i]) && (tt<hoursExtremities[i+1]))return (i);
		return 100000;
	}

	public static byte[][] getColorMapFire() {
		ImagePlus img=IJ.createImage("", 256, 1, 1, 8);
		byte[]tab=(byte[]) img.getStack().getProcessor(1).getPixels();
		for(int i=0;i<256;i++)tab[i]=toByte(i);
		IJ.run(img,"Fire","");
		IJ.run(img,"RGB Color","");
		ImagePlus[]imgs=ChannelSplitter.split(img);
		byte[][]vals=new byte[][] {(byte[]) imgs[0].getStack().getProcessor(1).getPixels(),(byte[]) imgs[1].getStack().getProcessor(1).getPixels(),(byte[]) imgs[2].getStack().getProcessor(1).getPixels()};
		byte[][]res=new byte[256][3];
		for(int i=0;i<3;i++)for(int j=0;j<256;j++)res[j][i]=vals[i][j];
		return res;
	}

	public static void saveAsMovie(ImagePlus mixFgBg,String outputPath) {		//Save the movie
		Calibration cal=mixFgBg.getCalibration();
		cal.fps=25;
		mixFgBg.setCalibration(cal);
		AVI_Writer av= new AVI_Writer();
		try {
			av.writeImage (mixFgBg,outputPath, AVI_Writer.JPEG_COMPRESSION, 100);
		} catch (IOException e) {	e.printStackTrace();}
	}
	
	//Minor helpers
	public static byte toByte(int i) {
		if(i<0)return (byte)(0 & 0xff);
		if(i>255)return (byte)(255 & 0xff);
		return (byte)(i & 0xff);
	}

	public static byte toByte(double d) {
		return toByte((int)Math.round(d));
	}
	
	public static int toInt(byte b) {
		return (int)(b & 0xff);
	}

	public static int toDouble(byte b) {
		return (int)(b & 0xff);
	}

	//TODO : make it depends on sliceindices and delta between
	public static int getIndex(double tt) {
		if(tt>=t[t.length-1])return t.length-1;
		if(tt<=t[0])return 0;
		
		int lowRangeIndex=0;int highRangeIndex=t.length-1;int medRangeIndex;
		boolean found=false;
		while(!found) {
			medRangeIndex=(lowRangeIndex+highRangeIndex)/2;
			if(t[medRangeIndex]<tt) lowRangeIndex=medRangeIndex;
			else highRangeIndex=medRangeIndex;
			if((highRangeIndex-lowRangeIndex)==1)found=true;
		}
		//fin de boucle : lowRange is lower or equal, highRange is upper
		if(tt<=0)return -1;
		if(tt<1)return 0;
		return highRangeIndex;
}
	/*
	//TODO : make it depends on sliceindices and delta between
*/

	
}