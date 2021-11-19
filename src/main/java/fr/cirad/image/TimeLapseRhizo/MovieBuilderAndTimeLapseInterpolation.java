package fr.cirad.image.TimeLapseRhizo;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Random;

import edu.mines.jtk.awt.ColorMap;
import fr.cirad.image.rsmlviewer.Node;
import fr.cirad.image.common.Timer;
import fr.cirad.image.common.VitimageUtils;
import fr.cirad.image.rsmlviewer.Root;
import fr.cirad.image.rsmlviewer.RootModel;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.TextRoi;
import ij.plugin.ChannelSplitter;
import ij.plugin.Duplicator;
import ij.process.ImageProcessor;

public class MovieBuilderAndTimeLapseInterpolation {

	private static final double umPerPixel = 76;
	static byte lisereVal=(byte)(1 & 0xff);
	static int vMaxDisplayed=8; //35 (pixels/8h) * 3  (8h/day)  * 76 (µm / pix) =   7980 µm/day
	static int vMaxInUse=35;
	static double startingBlockRatio=0.10;//time for a new root to appear, expressed as a ratio of the sequence length 
	static boolean smartHandyStart=true;
	static double timeStep=0.05;
	static int nTimeStepVirtualEnd=4;
	static double deltaPixelsSpeedInterpolation=4;
	
	public static void main(String[]args) {
		ImageJ ij=new ImageJ();	
		//testGridAndFire();
		//		testFullBehaviourOnSingleSimpleRoot();
		//testFullBehaviour();
//		testRootSpeed();
		runInterpolation_V2("1", "00003",vMaxInUse,timeStep);
		VitimageUtils.waitFor(200000);
		System.exit(0);
	}
	
	
	public static void testRootSpeed() {
		String specName="ML1_Boite_00001.rsml";
		String dataDir=TestImageSequence.mainDataDir;
		RootModel rm=RootModel.RootModelWildReadFromRsml(dataDir+"/4_RSML/"+specName);
		rm.computeSpeedVectors(deltaPixelsSpeedInterpolation);
		ImagePlus dates=IJ.openImage(dataDir+"/2_Date_maps/ML1_Boite_00001.tif");
		int TN=(int)Math.round( VitimageUtils.maxOfImage(dates));
		double dt=1;
		double[]t=getSamplesT(TN, dt);
		double vMax=20;
		ImagePlus[]gridAndFire=generateGridAndFireFromRootModel(rm, t,dates,1,vMax,0.2);
		System.out.println("Reading ok");
		System.out.println("SpeedVectors ok");
		gridAndFire[0].show();
		gridAndFire[1].show();
		gridAndFire[2].show();

	}
	
	public static double[]getSamplesT_V2(int TN,double dt){
		ArrayList<Double>t=new ArrayList<Double>();
		int nbPer=(int) (1/dt);
		double cur=0;
		double add0=dt/3;
		double add1=dt/3;
		double add2=dt/2;
		double add3=dt/1.5;
		double add4=dt/1.35;
		double add5=dt/1.25;
		double add6=dt/1.15;
		if(smartHandyStart) {
			while(cur<1) {t.add(cur);cur+=add0;}
			while(cur<1.2) {t.add(cur);cur+=add1;}
			while(cur<1.4) {t.add(cur);cur+=add2;}
			while(cur<1.6) {t.add(cur);cur+=add3;}
			while(cur<1.8) {t.add(cur);cur+=add4;}
			while(cur<2.0) {t.add(cur);cur+=add5;}
			while(cur<2.2) {t.add(cur);cur+=add6;}
		}
		while(cur<TN) {t.add(cur);cur+=dt;}
		for(int i=0;i<(int)(nTimeStepVirtualEnd/dt);i++)t.add(new Double(TN));

		int Nstep=t.size();
		double[]ret=new double[Nstep];
		for(int i=0;i<ret.length;i++) {
			ret[i]=t.get(i);
			if(ret[i]<1)ret[i]=1;
		}
		return ret;
	}
	
	public static double[]getSamplesT(int TN,double dt){
		int Nstep=(int)Math.floor(TN*1.0/dt);
		double[]t=new double[Nstep];
		for(int n=0;n<Nstep;n++) {t[n]=dt*n;if(t[n]<1)t[n]=1;}
		return t;
	}
	
	public static void runInterpolation_V2(String ml,String boite,double vMax,double dt) {
		Timer tim=new Timer();
		tim.print("Starting movie rendering");
		String specName="ML"+ml+"_Boite_"+boite+".tif";
		String dataDir=TestImageSequence.mainDataDir;
		boolean highRes=false;
		int sizeFactor=highRes ? 4 : 1;
		
		tim.print("Starting data importation");
		//Import proper data
		ImagePlus stackReg=highRes ? IJ.openImage(dataDir+"/1_Registered_High/ML"+ml+"_Boite_"+boite+".tif") : IJ.openImage(dataDir+"/1_Registered/ML"+ml+"_Boite_"+boite+".tif") ;
		ImagePlus dates=IJ.openImage(dataDir+"/2_Date_maps/ML"+ml+"_Boite_"+boite+".tif");
		if(highRes)stackReg=VitimageUtils.resize(stackReg, stackReg.getWidth()/2, stackReg.getHeight()/2, 1);
		ImagePlus times=IJ.openImage(dataDir+"/4_Times/ML"+ml+"_Boite_"+boite+".tif");
		ImagePlus maskUpLeaves=IJ.openImage(dataDir+"/1_Mask_N/ML"+ml+"_Boite_"+boite+".tif");
		maskUpLeaves=MorphoUtils.erosionCircle2D(maskUpLeaves, 65);
		//Algorithm parameters
		double t0Fg=0.01;
		double t1Fg=0.02;
		double t0Bg=0.20;
		double t1Bg=0.80;
		int TN=(int)Math.round( VitimageUtils.maxOfImage(dates));
		double[]t=getSamplesT_V2(TN, dt);
		int Nstep=t.length;

		
		//Compute successive masks
		tim.print("Starting building mix mask ");
		ImagePlus maskFgBgGauss=generateFgBgMask_V2(times,t);
		double[]voxSizes=VitimageUtils.getVoxelSizes(maskFgBgGauss);
		double sigX=1.5/voxSizes[0];
		double sigY=1.5/voxSizes[1];
		double sigZ=0/voxSizes[2];
		IJ.run(maskFgBgGauss, "Gaussian Blur 3D...", "x="+sigX+" y="+sigY+" z="+sigZ);	
		if(highRes)maskFgBgGauss=VitimageUtils.resize(maskFgBgGauss, stackReg.getWidth(), stackReg.getHeight(), 1);
		
		
		//Mix using masks
		tim.print("Starting mixing root");
//		ImagePlus mixFgBg=mixFgAndBgFromMaskAndStack_V2(maskFgBgGauss,stackReg,maskUpLeaves,t,t0Fg,t1Fg,t0Bg,t1Bg);
		ImagePlus mixFgBg=mixFgAndBgFromMaskAndStack_V2(maskFgBgGauss,stackReg,maskUpLeaves,t,t0Fg,t1Fg,t0Bg,t1Bg);
		IJ.run(mixFgBg,"8-bit","");
		maskFgBgGauss.setDisplayRange(0, 1);
		IJ.run(maskFgBgGauss,"8-bit","");
		boolean doGridAndFire=true;
		if(doGridAndFire) {
			tim.print("Starting rsml setup");
			//Generate grid and fire
			ImagePlus imgMaskRootInit=new Duplicator().run(maskFgBgGauss,1,1,1,1,1,1);
			RootModel rm=RootModel.RootModelWildReadFromRsml(dataDir+"/4_RSML/ML"+ml+"_Boite_"+boite+".rsml");
			rm.computeSpeedVectors(deltaPixelsSpeedInterpolation);
			tim.print("Starting generating grid and fire");
			ImagePlus[]gridAndFire=generateGridAndFireFromRootModel_V3(rm, t,times,sizeFactor,vMax,startingBlockRatio,imgMaskRootInit);
			tim.print("\nStarting final assembling");
			mixFgBg=assembleRootGridAndFire_V3(mixFgBg, maskFgBgGauss, gridAndFire[0],gridAndFire[1],gridAndFire[2],gridAndFire[3],true,t);
			tim.print("\nEnd. Now saving");
		}
		if(highRes)   IJ.saveAsTiff(mixFgBg,dataDir+"/5_Timelapse_Highres/ML"+ml+"_Boite_"+boite+".tif");
		else          IJ.saveAsTiff(mixFgBg,dataDir+"/5_Timelapse/ML"+ml+"_Boite_"+boite+"_V3.tif");		
		tim.print("Saved");
		mixFgBg.show();
		VitimageUtils.waitFor(1000000);
	}

	
	public static void runInterpolation(String ml,String boite,double vMax,double dt) {
		Timer tim=new Timer();
		tim.print("Starting movie rendering");
		String specName="ML"+ml+"_Boite_"+boite+".tif";
		String dataDir=TestImageSequence.mainDataDir;
		boolean highRes=false;
		int sizeFactor=highRes ? 4 : 1;
		
		tim.print("Starting data importation");
		//Import proper data
		ImagePlus stackReg=highRes ? IJ.openImage(dataDir+"/1_Registered_High/ML"+ml+"_Boite_"+boite+".tif") : IJ.openImage(dataDir+"/1_Registered/ML"+ml+"_Boite_"+boite+".tif") ;
		ImagePlus dates=IJ.openImage(dataDir+"/2_Date_maps/ML"+ml+"_Boite_"+boite+".tif");
		if(highRes)stackReg=VitimageUtils.resize(stackReg, stackReg.getWidth()/2, stackReg.getHeight()/2, 1);
		ImagePlus times=IJ.openImage(dataDir+"/4_Times/ML"+ml+"_Boite_"+boite+".tif");

		//Algorithm parameters
		double t0Fg=0.01;
		double t1Fg=0.02;
		double t0Bg=0.80;
		double t1Bg=0.99;
		int TN=(int)Math.round( VitimageUtils.maxOfImage(dates));
		double[]t=getSamplesT(TN, dt);
		int Nstep=t.length;

		
		//Compute successive masks
		tim.print("Starting building mix mask ");
		ImagePlus maskFgBgGauss=generateFgBgMask(times,Nstep,dt);
		double[]voxSizes=VitimageUtils.getVoxelSizes(maskFgBgGauss);
		double sigX=1.5/voxSizes[0];
		double sigY=1.5/voxSizes[1];
		double sigZ=0/voxSizes[2];
		IJ.run(maskFgBgGauss, "Gaussian Blur 3D...", "x="+sigX+" y="+sigY+" z="+sigZ);	
		if(highRes)maskFgBgGauss=VitimageUtils.resize(maskFgBgGauss, stackReg.getWidth(), stackReg.getHeight(), 1);
		
		
		//Mix using masks
		tim.print("Starting mixing root");
		ImagePlus mixFgBg=mixFgAndBgFromMaskAndStack(maskFgBgGauss,stackReg,t,t0Fg,t1Fg,t0Bg,t1Bg);
		IJ.run(mixFgBg,"8-bit","");
		maskFgBgGauss.setDisplayRange(0, 1);
		IJ.run(maskFgBgGauss,"8-bit","");
		boolean doGridAndFire=true;
		if(doGridAndFire) {
			tim.print("Starting rsml setup");
			//Generate grid and fire
			ImagePlus imgMaskRootInit=new Duplicator().run(maskFgBgGauss,1,1,1,1,1,1);
			RootModel rm=RootModel.RootModelWildReadFromRsml(dataDir+"/4_RSML/ML"+ml+"_Boite_"+boite+".rsml");
			rm.computeSpeedVectors(deltaPixelsSpeedInterpolation);
			tim.print("Starting generating grid and fire");
			ImagePlus[]gridAndFire=generateGridAndFireFromRootModel_V3(rm, t,times,sizeFactor,vMax,startingBlockRatio,imgMaskRootInit);
			tim.print("Starting final assembling");
			mixFgBg=assembleRootGridAndFire_V3(mixFgBg, maskFgBgGauss, gridAndFire[0],gridAndFire[1],gridAndFire[2],gridAndFire[3],true,t);

		}
		if(highRes)   IJ.saveAsTiff(mixFgBg,dataDir+"/5_Timelapse_Highres/ML"+ml+"_Boite_"+boite+".tif");
		else          IJ.saveAsTiff(mixFgBg,dataDir+"/5_Timelapse/ML"+ml+"_Boite_"+boite+"_V3.tif");		
		mixFgBg.show();
		VitimageUtils.waitFor(1000000);
	}
	
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
	
	public static byte[][]getColorTab(int nRoots){
		if(nRoots<4)nRoots=4;
		byte[][]colors=new byte[nRoots][3];
		Random rand=new Random();
		colors[1]=new byte[] {toByte(255),toByte(0),toByte(0)};
		colors[2]=new byte[] {toByte(0),toByte(255),toByte(0)};
		colors[3]=new byte[] {toByte(0),toByte(255),toByte(255)};
		for(int c=4;c<nRoots;c++) {
			double r = rand.nextDouble() / 2f + 0.5;
			double g = rand.nextDouble() / 2f + 0.5;
			double b = rand.nextDouble() / 2f + 0.5;
			colors[c]=new byte[] {toByte(r*255),toByte(g*255),toByte(b*255)};
		}
		return colors;
	}
	
	public static byte[]getColor(byte[]colorMax,byte ratio){
		byte[]ret=new byte[] { toByte( toDouble(colorMax[0])*toDouble(ratio)/255.0  ), toByte( toDouble(colorMax[1])*toDouble(ratio)/255.0  ) , toByte( toDouble(colorMax[2])*toDouble(ratio)/255.0  ) };
		return ret;
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
	
	public static ImagePlus generateFgBgMask(ImagePlus imgIn,int N,double dt) {
		System.out.println("\nGenerating mix mask");
		int X=imgIn.getWidth();
		int Y=imgIn.getHeight();
		
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
				if(val<=0)indexTime=-1;
				else {indexTime=(int) (val/dt);}
				if(indexTime==-1)continue;
				for(int n=indexTime;n<N;n++) tabOut[n][indexSpace]=1;
			}
		}
		imgOut.setDisplayRange(0, 1);
		System.out.println();
		return imgOut;
	}
	
	public static ImagePlus generateFgBgMask_V2(ImagePlus imgIn,double[]t) {
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
				if(val<=0)indexTime=-1;
				else indexTime=getIndex(val,t);
				if(indexTime==-1)continue;
				for(int n=indexTime;n<N;n++) tabOut[n][indexSpace]=1;
			}
		}
		imgOut.setDisplayRange(0, 1);
		System.out.println();
		return imgOut;
	}
	
	
		public static int getIndex(double t,double []tTab) {
			if(t>=tTab[tTab.length-1])return tTab.length-1;
			if(t<=tTab[0])return 0;
			
			int lowRange=0;int highRange=tTab.length;int medRange;
			boolean found=false;
			while(!found) {
				medRange=(lowRange+highRange)/2;
				if(tTab[medRange]<t)lowRange=medRange;
				else highRange=medRange;
				if((highRange-lowRange)==1)found=true;
			}
			//fin de boucle : lowRange is lower or equal, highRange is upper
			if(t<=0)return -1;
			if(t<1)return 0;
			return highRange;
	}

	
	
	
	public static ImagePlus mixFgAndBgFromMaskAndStack(ImagePlus imgRootMask,ImagePlus regStack,double[]ts,double t0Fg,double t1Fg,double t0Bg,double t1Bg) {
		System.out.println("Generating mix");
		ImagePlus imgInReg=VitimageUtils.convertToFloat(regStack);
		int X=imgInReg.getWidth();
		int Y=imgInReg.getHeight();
		int N=imgRootMask.getStackSize();
		int Nt=imgInReg.getStackSize();
		ImagePlus imgOut=IJ.createImage("", X, Y, N, 8);
		byte[][]tabOut=new byte[N][];
		float[][]tabInMask=new float[N][];
		float[][]tabInReg=new float[Nt][];
		for(int n=0;n<N;n++) {
			tabOut[n]=(byte[]) imgOut.getStack().getProcessor(n+1).getPixels();
			tabInMask[n]=(float[]) imgRootMask.getStack().getProcessor(n+1).getPixels();
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
					tabOut[n][indexSpace]=(byte)( ((int)(0.5+  valMixFg*deltaMix + valMixBg*(1-deltaMix)) & 0xff) );//(float) (valMixBg*(1-deltaMix));//
				}
			}
		}
		System.out.println();
		imgOut.setDisplayRange(0, 255);
		return imgOut;		
	}

	
	public static ImagePlus mixFgAndBgFromMaskAndStack_V2(ImagePlus imgRootMask,ImagePlus regStack,ImagePlus maskUpLeaves,double[]ts,double t0Fg,double t1Fg,double t0Bg,double t1Bg) {
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
			
			if((n%50)==0)System.out.print(n+"/"+N+" ");
			for(int x=0;x<X;x++) {
				for(int y=0;y<Y;y++) {
					indexSpace=y*X+x;
					if(toInt(tabMaskUp[indexSpace])==0) {
						if(dt<=t0Bg)valMixBg=tabInReg[Tprev][indexSpace];
						else if(dt>=t1Bg)valMixBg=tabInReg[T0][indexSpace];
						else {
							delta=(dt-t0Bg)/deltaBg;
							valMixBg=delta*tabInReg[T0][indexSpace]+(1-delta)*tabInReg[Tprev][indexSpace];
						}
					}
					else valMixBg=tabInReg[0][indexSpace];						
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
					tabOut[n][indexSpace]=toByte(valMixFg*deltaMix + valMixBg*(1-deltaMix));//(float) (valMixBg*(1-deltaMix));//
				}
			}
		}
		System.out.println();
		imgOut.setDisplayRange(0, 255);
		return imgOut;		
	}

	
	public static ImagePlus assembleRootGridAndFire(ImagePlus imgRoot,ImagePlus imgMaskRoot,ImagePlus imgFire, ImagePlus imgGrid,ImagePlus imgMaskFuse) {
		IJ.run(imgFire,"RGB Color","");
		ImagePlus []resChan=VitimageUtils.channelSplitter(imgFire);
		imgFire=null;
		VitimageUtils.garbageCollector();
		int N=imgRoot.getStackSize();
		int X=imgRoot.getWidth();
		int Y=imgRoot.getHeight();
		byte[][]resR=new byte[N][];
		byte[][]resG=new byte[N][];
		byte[][]resB=new byte[N][];
		byte[][]root=new byte[N][];
		byte[][]maskRoot=new byte[N][];
		byte[][]grid=new byte[N][];
		byte[][]maskFuse=new byte[N][];
		double weig;
		byte val;
		for(int n=0;n<N;n++) {
			resR[n]=(byte[]) resChan[0].getStack().getProcessor(n+1).getPixels();
			resG[n]=(byte[]) resChan[1].getStack().getProcessor(n+1).getPixels();
			resB[n]=(byte[]) resChan[2].getStack().getProcessor(n+1).getPixels();
			root[n]=(byte[]) imgRoot.getStack().getProcessor(n+1).getPixels();
			maskRoot[n]=(byte[]) imgMaskRoot.getStack().getProcessor(n+1).getPixels();
			grid[n]=(byte[]) imgGrid.getStack().getProcessor(n+1).getPixels();
			maskFuse[n]=(byte[]) imgMaskFuse.getStack().getProcessor(n+1).getPixels();
		}
		int indexSpace=0;
		int indexTime=0;
		for(int n=0;n<N;n++) {
			if((n%20)==0)System.out.print(n+"/"+N+" ");
			for(int x=0;x<X;x++) {
				for(int y=0;y<Y;y++) {
					boolean debug=false &&(x==562 && y==310);
					indexSpace=y*X+x;						
					if(debug) {
						System.out.println("\nN="+n+" maskFuse,trans="+maskFuse[n][indexSpace]+","+((int)(maskFuse[n][indexSpace]  & 0xff)));
					}
					if((((int)(maskFuse[n][indexSpace] & 0xff))<=0) || ((int)(maskRoot[n][indexSpace]  & 0xff)>140)) {//If included in root segmentation or no mask present, leave intact
						if(debug)System.out.println("Cond 0");
						resR[n][indexSpace]=root[n][indexSpace];
						resG[n][indexSpace]=root[n][indexSpace];
						resB[n][indexSpace]=root[n][indexSpace];
						if(debug)System.out.println("ResR,G,B="+((int)(resR[n][indexSpace]&0xff))+","+((int)(resG[n][indexSpace]&0xff))+","+((int)(resB[n][indexSpace]&0xff)));
											}
					else if( ((int)(grid[n][indexSpace] & 0xff))!=0){//Else if included in grid, weighted copy grid and root
						if(debug)System.out.println("Cond 1");
						weig=((int)(maskFuse[n][indexSpace] & 0xff))/255.0;;
						val=(byte)( (int)(weig*((int)(grid[n][indexSpace]  & 0xff))+(1-weig)*(int)(root[n][indexSpace]  & 0xff))  & 0xff);
						resR[n][indexSpace]=val;
						resG[n][indexSpace]=val;
						resB[n][indexSpace]=val;
						if(debug)System.out.println("ResR,G,B="+((int)(resR[n][indexSpace]&0xff))+","+((int)(resG[n][indexSpace]&0xff))+","+((int)(resB[n][indexSpace]&0xff)));
											}
					else {//MaskFuse not null, and not in grid
						if(debug)System.out.println("Cond 2");
						weig=((int)(maskFuse[n][indexSpace] & 0xff))/255.0;
//						if(debug)System.out.println("weig="+weig);
//						if(debug)System.out.println("R1="+(int) ((weig)*(int)(resR[n][indexSpace]  & 0xff)));
//						if(debug)System.out.println("R2="+(1-weig)*(int)(root[n][indexSpace]  & 0xff));
						resR[n][indexSpace]= (byte) ( (int) ((weig)*(int)(resR[n][indexSpace]  & 0xff)+(1-weig)*(int)(root[n][indexSpace]  & 0xff)));
						resG[n][indexSpace]=(byte) ( (int)((weig)*(int)(resG[n][indexSpace]  & 0xff)+(1-weig)*(int)(root[n][indexSpace]  & 0xff)));
						resB[n][indexSpace]=(byte) ( (int)((weig)*(int)(resB[n][indexSpace]  & 0xff)+(1-weig)*(int)(root[n][indexSpace]  & 0xff)));
						if(debug)System.out.println("ResR,G,B="+((int)(resR[n][indexSpace]&0xff))+","+((int)(resG[n][indexSpace]&0xff))+","+((int)(resB[n][indexSpace]&0xff)));
					}
				}
			}
		}
		return VitimageUtils.compositeRGBByte(resChan[0],resChan[1],resChan[2], 1, 1, 1);
	}
	
	public static ImagePlus assembleRootGridAndFire_V2(ImagePlus imgRoot,ImagePlus imgMaskRoot,ImagePlus imgFire, ImagePlus imgGrid,ImagePlus imgMaskFuse,ImagePlus imgMaskGrid) {
		System.out.println("Assemble V2");
		ImagePlus imgFi=imgFire.duplicate();
		IJ.run(imgFire,"RGB Color","");
		ImagePlus []resChan=VitimageUtils.channelSplitter(imgFire);
		imgFire=null;
		VitimageUtils.garbageCollector();
		int N=imgRoot.getStackSize();
		int X=imgRoot.getWidth();
		int Y=imgRoot.getHeight();
		byte[][]resR=new byte[N][];
		byte[][]resG=new byte[N][];
		byte[][]resB=new byte[N][];
		byte[][]root=new byte[N][];
		byte[][]maskRoot=new byte[N][];
		byte[][]grid=new byte[N][];
		byte[][]maskFuse=new byte[N][];
		byte[][]maskGrid=new byte[N][];
		byte[][]fire=new byte[N][];
		double weig;
		byte val;
		for(int n=0;n<N;n++) {
			resR[n]=(byte[]) resChan[0].getStack().getProcessor(n+1).getPixels();
			resG[n]=(byte[]) resChan[1].getStack().getProcessor(n+1).getPixels();
			resB[n]=(byte[]) resChan[2].getStack().getProcessor(n+1).getPixels();
			root[n]=(byte[]) imgRoot.getStack().getProcessor(n+1).getPixels();
			maskRoot[n]=(byte[]) imgMaskRoot.getStack().getProcessor(n+1).getPixels();
			grid[n]=(byte[]) imgGrid.getStack().getProcessor(n+1).getPixels();
			maskFuse[n]=(byte[]) imgMaskFuse.getStack().getProcessor(n+1).getPixels();
			maskGrid[n]=(byte[]) imgMaskGrid.getStack().getProcessor(n+1).getPixels();
			fire[n]=(byte[]) imgFi.getStack().getProcessor(n+1).getPixels();
		}
		int indexSpace=0;
		int indexTime=0;
		for(int n=0;n<N;n++) {
			if((n%20)==0)System.out.print(n+"/"+N+" ");
			for(int x=0;x<X;x++) {
				for(int y=0;y<Y;y++) {
					boolean debug=(x==770 && y==407);
					indexSpace=y*X+x;						
					if(debug) {
						System.out.println("N="+n+" val of maskGrid="+((int)(maskGrid[n][indexSpace] & 0xff)));
					}
					if(((int)(maskRoot[n][indexSpace]  & 0xff)>140)){//If root segmentation
						resR[n][indexSpace]=root[n][indexSpace];
						resG[n][indexSpace]=root[n][indexSpace];
						resB[n][indexSpace]=root[n][indexSpace];
					}
					else if( ((int)(maskGrid[n][indexSpace] & 0xff))!=0 && ((int)(grid[n][indexSpace] & 0xff))!=0){//Else if included in grid, weighted copy grid and root
						weig=((int)(maskGrid[n][indexSpace] & 0xff))/255.0;
						val=(byte)( (int)(weig*((int)(grid[n][indexSpace]  & 0xff))+(1-weig)*(int)(root[n][indexSpace]  & 0xff))  & 0xff);
						resR[n][indexSpace]=val;
						resG[n][indexSpace]=val;
						resB[n][indexSpace]=val;
					}						
					else if((((int)(maskFuse[n][indexSpace] & 0xff)) >0)){//If included in fire
						weig=((int)(maskFuse[n][indexSpace] & 0xff))/255.0*((int)(fire[n][indexSpace] & 0xff))/255.0;
						resR[n][indexSpace]= (byte) ( (int) ((weig)*(int)(resR[n][indexSpace]  & 0xff)+(1-weig)*(int)(root[n][indexSpace]  & 0xff)));
						resG[n][indexSpace]=(byte) ( (int)((weig)*(int)(resG[n][indexSpace]  & 0xff)+(1-weig)*(int)(root[n][indexSpace]  & 0xff)));
						resB[n][indexSpace]=(byte) ( (int)((weig)*(int)(resB[n][indexSpace]  & 0xff)+(1-weig)*(int)(root[n][indexSpace]  & 0xff)));
					}	
					else {
						resR[n][indexSpace]=root[n][indexSpace];
						resG[n][indexSpace]=root[n][indexSpace];
						resB[n][indexSpace]=root[n][indexSpace];
					}
				}
			}
		}
		return VitimageUtils.compositeRGBByte(resChan[0],resChan[1],resChan[2], 1, 1, 1);
	}

	public static ImagePlus assembleRootGridAndFire_V3(ImagePlus imgRoot,ImagePlus imgMaskRoot,ImagePlus imgFire, ImagePlus imgGrid,ImagePlus imgMaskGrid,ImagePlus imgIdent,boolean fireDisplay,double[]t) {
		int nRoots=(int) VitimageUtils.maxOfImage(imgIdent)+1;
		byte[][]colors=getColorTab(nRoots);	
		byte[][]colorMapFire=getColorMapFire();
		System.out.println("Assemble V3");
		VitimageUtils.garbageCollector();
		int N=imgRoot.getStackSize();
		int X=imgRoot.getWidth();
		int Y=imgRoot.getHeight();
		ImagePlus []resChan=new ImagePlus[] {IJ.createImage("", X, Y, N, 8),IJ.createImage("", X, Y, N, 8),IJ.createImage("", X, Y, N, 8)};
		for(int c=0;c<3;c++) resChan[c].setDisplayRange(0, 255);
		byte[][]resR=new byte[N][];
		byte[][]resG=new byte[N][];
		byte[][]resB=new byte[N][];
		byte[][]root=new byte[N][];
		byte[][]maskRoot=new byte[N][];
		byte[][]fire=new byte[N][];
		byte[]grid=new byte[N];
		byte[][]maskGrid=new byte[N][];
		byte[][]ident=new byte[N][];
		double weig;
		byte val;
		for(int n=0;n<N;n++) {
			resR[n]=(byte[]) resChan[0].getStack().getProcessor(n+1).getPixels();
			resG[n]=(byte[]) resChan[1].getStack().getProcessor(n+1).getPixels();
			resB[n]=(byte[]) resChan[2].getStack().getProcessor(n+1).getPixels();
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
		for(int n=0;n<N;n++) {
			if((n%50)==0)System.out.print(n+"/"+N+" ");
			for(int x=0;x<X;x++) {
				for(int y=0;y<Y;y++) {
					boolean debug=false &&(x==770 && y==407);
					indexSpace=y*X+x;						
					if(debug) {
						System.out.println("N="+n+" val of maskGrid="+((int)(maskGrid[n][indexSpace] & 0xff)));
					}
					if(((int)(maskRoot[n][indexSpace]  & 0xff)>140)){//If root segmentation
						resR[n][indexSpace]=root[n][indexSpace];
						resG[n][indexSpace]=root[n][indexSpace];
						resB[n][indexSpace]=root[n][indexSpace];
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
					}						
					else {
						resR[n][indexSpace]=root[n][indexSpace];
						resG[n][indexSpace]=root[n][indexSpace];
						resB[n][indexSpace]=root[n][indexSpace];
					}
				}
			}			
			//Draw fire legend
			//draw rectangle
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
				ImageProcessor ip=resChan[c].getStack().getProcessor(n+1);				
				ip.setAntialiasedText(true);ip.draw(roi1);ip.draw(roi2);ip.draw(roi3);ip.draw(roi6);
			}
			
			
			//Draw time legend
			int hei=200;
			int x0=X-147+deltaX;
			int y0=Y-460+deltaY-hei-10;
			int x1=x0+wid;
			int y1=y0+hei;
			TextRoi titleTime = new TextRoi(x0+35,y0+7, "Time", font30);			
			TextRoi titleTimeBack = new TextRoi(x0+7,y0+170, "Time step=8h", font19);

			double tMax=VitimageUtils.max(t);
			int tM=(int) Math.ceil(tMax)-1;
			TextRoi[] roiTab1=new TextRoi[tM+1];
			TextRoi[] roiTab2=new TextRoi[tM];
			int t1wid=20;
			int t2wid=100;
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
				ImageProcessor ip=resChan[c].getStack().getProcessor(n+1);				
				ip.setAntialiasedText(true);ip.draw(titleTime);ip.draw(titleTimeBack);ip.draw(titleTimeBack);
			}
			for(int r=0;r<tM+1;r++) {
				double xR=(int) (15+x0+(r+1.5)*wid/2.0-t[n]*wid/2.0);//xr0=x0+wid/2 at t=0 and x0
				int yR=y0+52;
				if(xR<x0)xR=-1000;
				if(xR>(x1-t1wid-20))xR=-1000;
				roiTab1[r]=new TextRoi(xR+2,yR-7+15,"t"+r, font27);			
				xR=(int) (x0-40+(r+1.5)*wid/2.0+wid/4.0-t[n]*wid/2.0);//xr0=x0+wid/2 at t=0 and x0
				if(xR<x0-t2wid)xR=-1000;
				if(xR>x1-t1wid)xR=-1000;
				if(r<(tM))roiTab2[r]=new TextRoi(xR,yR+4,"", font19);			

				//Write text				
				for(int c=0;c<3;c++) {
					ImageProcessor ip=resChan[c].getStack().getProcessor(n+1);				
					ip.setAntialiasedText(true);ip.draw(roiTab1[r]);if(r<tM)ip.draw(roiTab2[r]);
				}
								
				//Vertical lines				
				xR=(int) (x0+15+(r+1.5)*wid/2-t[n]*wid/2);//xr0=x0+wid/2 at t=0 and x0
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
				ImageProcessor ip=resChan[c].getStack().getProcessor(n+1);			
				ip.setColor(Color.black);
				ip.setAntialiasedText(true);ip.draw(scaleText);
				
			}			
		}
		return VitimageUtils.compositeRGBByte(resChan[0],resChan[1],resChan[2], 1, 1, 1);
	}
	

	
	public static ImagePlus[] generateGridAndFireFromRootModel(RootModel rm, double []t,ImagePlus img,int sizeFactor,double vMax,double startingBlockRatio) {
		int X=img.getWidth();
		int Y=img.getHeight();
		double[][][]coords=getAsTimeLapseCoords(rm,t,sizeFactor);
		return generateGridAndFire(X,Y,coords,vMax,startingBlockRatio);		
	}

	public static ImagePlus[] generateGridAndFireFromRootModel_V2(RootModel rm, double []t,ImagePlus img,int sizeFactor,double vMax,double startingBlockRatio,ImagePlus maskRootInit) {
		int X=img.getWidth();
		int Y=img.getHeight();
		double[][][]coords=getAsTimeLapseCoords(rm,t,sizeFactor);
		return generateGridAndFire_V2(X,Y,coords,vMax,startingBlockRatio,maskRootInit);		
	}
	
	public static ImagePlus[] generateGridAndFireFromRootModel_V3(RootModel rm, double []t,ImagePlus img,int sizeFactor,double vMax,double startingBlockRatio,ImagePlus maskRootInit) {
		int X=img.getWidth();
		int Y=img.getHeight();
		double[][][]coords=getAsTimeLapseCoords(rm,t,sizeFactor);
		return generateGridAndFire_V3(X,Y,coords,vMax,startingBlockRatio,maskRootInit);		
	}
	
	
	
	public static double[]getMaskAndFireValue_V2(double dx,double dy, double vx,double vy,double R,double vMax,boolean debug){		
		double radMask=R/2;
		double radFire=R/20;
		if(debug)System.out.println("\nradMask="+radMask);
		if(debug)System.out.println("radFire="+radFire);
		double v=Math.sqrt(vx*vx+vy*vy);
		double r=Math.sqrt(dx*dx+dy*dy);
		if(debug)System.out.println("v="+v);
		if(debug)System.out.println("r="+r);
		double prodScalNorm=(dx*vx+dy*vy)/(r*v);
		if((r*v)<=0)prodScalNorm=1;
		if(debug)System.out.println("prodscalnorm="+prodScalNorm);
		double lapAng=VitimageUtils.laplacian(0*(1-prodScalNorm));//quand prodScal varie de 1 a -1, laplacien varie de 1 a 1/27e
		double lapMask=VitimageUtils.laplacian(Math.pow(r, 3.5),Math.pow(radMask, 3.5))*lapAng;//quand l'eloignement au point central varie de 0 a R, lap varie de 1 a 1/81
		double lapMask2=VitimageUtils.laplacian(Math.pow(r, 0.8),Math.pow(radMask, 0.8))*lapAng;//quand l'eloignement au point central varie de 0 a R, lap varie de 1 a 1/81
		double lapFire=((Math.min(v,vMax)/vMax))*400*VitimageUtils.laplacian(Math.pow(r, 2.5),Math.pow(radFire, 2.5))*lapAng;//Quand v varie de 0 a vMax, lap varie de
		if(debug)System.out.println("lapAng="+lapAng);
		if(debug)System.out.println("lapMask="+lapMask);
		if(debug)System.out.println("lapFire="+lapFire);
		return new double[] {lapMask,lapFire,lapMask2};
	}

	
	public static double[]getMaskAndFireValue(double dx,double dy, double vx,double vy,double R,double vMax,boolean debug){		
		double radMask=R/1.5;
		double radFire=R/3;
		if(debug)System.out.println("\nradMask="+radMask);
		if(debug)System.out.println("radFire="+radFire);
		double v=Math.sqrt(vx*vx+vy*vy);
		double r=Math.sqrt(dx*dx+dy*dy);
		if(debug)System.out.println("v="+v);
		if(debug)System.out.println("r="+r);
		double prodScalNorm=(dx*vx+dy*vy)/(r*v);
		if((r*v)<=0)prodScalNorm=1;
		if(debug)System.out.println("prodscalnorm="+prodScalNorm);
		double lapAng=VitimageUtils.laplacian(10*(1-prodScalNorm));//quand prodScal varie de 1 a -1, laplacien varie de 1 a 1/27e
		double lapMask=VitimageUtils.laplacian(Math.pow(r, 3.5),Math.pow(radMask, 3.5))*lapAng;//quand l'eloignement au point central varie de 0 a R, lap varie de 1 a 1/81
		double lapMask2=VitimageUtils.laplacian(Math.pow(r, 0.8),Math.pow(radMask, 0.8))*lapAng;//quand l'eloignement au point central varie de 0 a R, lap varie de 1 a 1/81
		double lapFire=((Math.min(v,vMax)/vMax))*400*VitimageUtils.laplacian(Math.pow(r, 2.5),Math.pow(radFire, 2.5))*lapAng;//Quand v varie de 0 a vMax, lap varie de
		if(debug)System.out.println("lapAng="+lapAng);
		if(debug)System.out.println("lapMask="+lapMask);
		if(debug)System.out.println("lapFire="+lapFire);
		return new double[] {lapMask,lapFire,lapMask2};
	}
		
	public static ImagePlus[] generateGridAndFire(int X,int Y, double[][][]coords,double vMax,double startingBlockRatio) {
		System.out.println("\nGenerating grid and fire");
		int gridVal=172;
		int G=1000/76;//nb pixels per mm
		int N=coords.length;
		int P=coords[0].length;
		int sizeRatio=20;
		int deltaN=(int)(N*startingBlockRatio);
		int []nStart=new int[P];
		for(int p=0;p<P;p++)nStart[p]=-1;
		int R=X/sizeRatio;
		ImagePlus imgOut=IJ.createImage("", X, Y, N, 8);
		ImagePlus imgWeight=IJ.createImage("", X, Y, N, 8);
		ImagePlus imgMask=IJ.createImage("", X, Y, N, 8);
		ImagePlus imgGrid=IJ.createImage("", X, Y, N, 8);
		byte[][]tabOut=new byte[N][];
		byte[][]tabWeight=new byte[N][];
		byte[][]tabMask=new byte[N][];
		byte[][]tabGrid=new byte[N][];
		for(int n=0;n<N;n++) {
			tabOut[n]=(byte[]) imgOut.getStack().getProcessor(n+1).getPixels();
			tabWeight[n]=(byte[]) imgWeight.getStack().getProcessor(n+1).getPixels();
			tabMask[n]=(byte[]) imgMask.getStack().getProcessor(n+1).getPixels();
			tabGrid[n]=(byte[]) imgGrid.getStack().getProcessor(n+1).getPixels();
		}
		int indexSpace=0;
		int indexTime=0;
		for(int n=0;n<N;n++) {
			if((n%20)==0)System.out.print(n+"/"+N+" ");
			for(int p=0;p<P;p++) {
				double vx=coords[n][p][2];
				double vy=coords[n][p][3];
				double norm=Math.sqrt(vx*vx+vy*vy);
				double x0=coords[n][p][0];
				if(x0<=0)continue;
				x0-=vx*12/norm;
				double y0=coords[n][p][1]-vy*12/norm;
				if(nStart[p]==-1)nStart[p]=n;
				int al=1;
				for(int x=(int)Math.round(x0-al*R);x<=x0+al*R;x++) {
					if(x<0 || x>=X)continue;
					for(int y=(int)Math.round(y0-al*R);y<=y0+al*R;y++) {
						if(y<0 || y>=Y)continue;
							boolean debug=false&&(x==607 && y==156);
						indexSpace=y*X+x;						
						double finalVal=0;
						double []maskFire =getMaskAndFireValue(x-x0,y-y0, vx,vy,R,vMax,false &&debug);
						if(debug) {
							System.out.println("Nstart="+nStart[p]+" deltaN="+deltaN);
							System.out.println("1 At n="+n+" got ="+maskFire[0]+","+maskFire [1]);
						}
						if(n<nStart[p]+deltaN) {
							double alpha=(n-nStart[p])/(1.0*deltaN);
							alpha=alpha*alpha;
							maskFire[0]=alpha*maskFire[0];
							maskFire[1]=alpha*maskFire[1];
						}
						if(debug) {
							System.out.println("2 At n="+n+" got ="+maskFire[0]+","+maskFire [1]);
						}
						int nAlready=(int) (tabWeight[n][indexSpace]&0xff);
						if(nAlready==0) {
							tabMask[n][indexSpace]=(byte)(((int) Math.min(255,(255*maskFire[0]+0.5))) & 0xff);
							tabOut[n][indexSpace]=(byte)(((int) Math.min(255,(maskFire[1]+0.5))) & 0xff);
							tabWeight[n][indexSpace]=(byte)(1 &0xff);
						}
						else{
							tabMask[n][indexSpace]=(byte)(((int) (Math.min(255,Math.max( maskFire[0]*255,(int)(tabMask[n][indexSpace]& 0xff)))) & 0xff));
							tabOut[n][indexSpace]= (byte)(((int) (Math.min(255,Math.max( maskFire[1],(int)(tabOut[n][indexSpace] & 0xff)))) & 0xff));
						}
						
						int nearGridX=(int)Math.round( x/G );
						int nearGridY=(int)Math.round( y/G );
						int dgx=x-nearGridX*G;
						int dgy=y-nearGridY*G;
						int dgAbsx=Math.abs(dgx);
						int dgAbsy=Math.abs(dgy);
						//if(dgAbsx<3 && ((nearGridX%10)==0)) {tabGrid[n][indexSpace]=(byte)(gridVal &0xff);continue;}
						//if(dgAbsy<3 && ((nearGridY%10)==0)) {tabGrid[n][indexSpace]=(byte)(gridVal &0xff);continue;}
						//if(dgAbsx<2 && ((nearGridX%5)==0)) {tabGrid[n][indexSpace]=(byte)(gridVal &0xff);continue;}
						//if(dgAbsy<2 && ((nearGridY%5)==0)) {tabGrid[n][indexSpace]=(byte)(gridVal &0xff);continue;}
						//if(dgAbsx<1) {tabGrid[n][indexSpace]=(byte)(gridVal &0xff);continue;}
						//if(dgAbsy<1) {tabGrid[n][indexSpace]=(byte)(gridVal &0xff);continue;}
					}
				}
			}
		}
		imgOut.setTitle("Fire");
		imgOut.setDisplayRange(0, 255);
		imgMask.setTitle("Mask");
		imgMask.setDisplayRange(0, 255);
		imgGrid.setTitle("Grid");
		imgGrid.setDisplayRange(0, 255);
		imgMask.show();
		imgOut.show();
		IJ.run(imgOut,"Fire","");
		IJ.run(imgMask,"Fire","");
		IJ.run(imgGrid,"Fire","");
		imgWeight=null;
		return new ImagePlus [] {imgOut,imgGrid,imgMask};
	}
	
	public static ImagePlus[] generateGridAndFire_V2(int X,int Y, double[][][]coords,double vMax,double startingBlockRatio,ImagePlus maskRootInit) {
		ImagePlus rootInitArea=MorphoUtils.dilationCircle2D(maskRootInit, 50);
		rootInitArea=VitimageUtils.gaussianFiltering(rootInitArea, 40, 40, 0);
		rootInitArea.setDisplayRange(0, 255);
		IJ.run(rootInitArea,"8-bit","");
		System.out.println("\nGenerating grid and fire V2");
		int gridVal=172;
		int G=1000/76;//nb pixels per mm
		int N=coords.length;
		int P=coords[0].length;
		boolean []isPrimary=new boolean[P];
		for(int p=0;p<P;p++)if(coords[0][p][0]>0)isPrimary[p]=true;
		int sizeRatio=15;
		int deltaN=(int)(N*startingBlockRatio);
		int []nStart=new int[P];
		for(int p=0;p<P;p++)nStart[p]=-1;
		int R=X/sizeRatio;
		ImagePlus imgOut=IJ.createImage("", X, Y, N, 8);
		ImagePlus imgWeight=IJ.createImage("", X, Y, N, 8);
		ImagePlus imgMask=IJ.createImage("", X, Y, N, 8);
		ImagePlus imgGrid=IJ.createImage("", X, Y, N, 8);
		ImagePlus imgMaskGrid=IJ.createImage("", X, Y, N, 8);
		byte[][]tabOut=new byte[N][];
		byte[][]tabWeight=new byte[N][];
		byte[][]tabMask=new byte[N][];
		byte[][]tabGrid=new byte[N][];
		byte[][]tabMaskGrid=new byte[N][];
		byte[]tabMaskInit;
		for(int n=0;n<N;n++) {
			tabOut[n]=(byte[]) imgOut.getStack().getProcessor(n+1).getPixels();
			tabWeight[n]=(byte[]) imgWeight.getStack().getProcessor(n+1).getPixels();
			tabMask[n]=(byte[]) imgMask.getStack().getProcessor(n+1).getPixels();
			tabGrid[n]=(byte[]) imgGrid.getStack().getProcessor(n+1).getPixels();
			tabMaskGrid[n]=(byte[]) imgMaskGrid.getStack().getProcessor(n+1).getPixels();
		}
		tabMaskInit=(byte[]) rootInitArea.getStack().getProcessor(1).getPixels();
		int indexSpace=0;
		int indexTime=0;
		for(int n=0;n<N;n++) {
			if((n%5)==0)System.out.print(n+"/"+N+" ");
			for(int p=0;p<P;p++) {
				double vx=coords[n][p][2];
				double vy=coords[n][p][3];
				double norm=Math.sqrt(vx*vx+vy*vy);
				double x0=coords[n][p][0];
				if(x0<=0)continue;
				x0+=vx*(isPrimary[p] ? 30 : 12)/norm;
				double y0=coords[n][p][1]+vy*(isPrimary[p] ? 20 : 10)/norm;
				if(nStart[p]==-1)nStart[p]=n;
				int al=1;
				for(int x=(int)Math.round(x0-al*R);x<=x0+al*R;x++) {
					if(x<0 || x>=X)continue;
					for(int y=(int)Math.round(y0-al*R);y<=y0+al*R;y++) {
						if(y<0 || y>=Y)continue;
							boolean debug=false&&(x==607 && y==156);
						indexSpace=y*X+x;						
						double finalVal=0;
						double []maskFire =getMaskAndFireValue_V2(x-x0,y-y0, vx,vy,R,vMax,false &&debug);
						if(debug) {
							System.out.println("Nstart="+nStart[p]+" deltaN="+deltaN);
							System.out.println("1 At n="+n+" got ="+maskFire[0]+","+maskFire [1]);
						}
						if(n<nStart[p]+deltaN) {
							double alpha=(n-nStart[p])/(1.0*deltaN);
							alpha=alpha*alpha;
							maskFire[0]=alpha*maskFire[0];
							maskFire[1]=alpha*maskFire[1];
							maskFire[2]=alpha*maskFire[2];
						}
						if(debug) {
							System.out.println("2 At n="+n+" got ="+maskFire[0]+","+maskFire [1]);
						}
						int nAlready=(int) (tabWeight[n][indexSpace]&0xff);
						if(nAlready==0) {
							tabMask[n][indexSpace]=(byte)(((int) Math.min(255,(255*maskFire[0]+0.5))) & 0xff);
							tabOut[n][indexSpace]=(byte)(((int) Math.min(255,(maskFire[1]+0.5))) & 0xff);
							tabWeight[n][indexSpace]=(byte)(1 &0xff);
						}
						else{
							tabMask[n][indexSpace]=(byte)(((int) (Math.min(255,Math.max( maskFire[0]*255,(int)(tabMask[n][indexSpace]& 0xff)))) & 0xff));
							tabOut[n][indexSpace]= (byte)(((int) (Math.min(255,Math.max( maskFire[1],(int)(tabOut[n][indexSpace] & 0xff)))) & 0xff));
						}
						tabMaskGrid[n][indexSpace]=(byte)(((int) Math.min(255,(255*2*maskFire[2]+0.5))) & 0xff);
						
						int nearGridX=(int)Math.round( x/G );
						int nearGridY=(int)Math.round( y/G );
						int dgx=x-nearGridX*G;
						int dgy=y-nearGridY*G;
						int dgAbsx=Math.abs(dgx);
						int dgAbsy=Math.abs(dgy);
						if(dgAbsx<3 && ((nearGridX%10)==0)) {tabGrid[n][indexSpace]=(byte)(gridVal &0xff);continue;}
						if(dgAbsy<3 && ((nearGridY%10)==0)) {tabGrid[n][indexSpace]=(byte)(gridVal &0xff);continue;}
						if(dgAbsx<2 && ((nearGridX%5)==0)) {tabGrid[n][indexSpace]=(byte)(gridVal &0xff);continue;}
						if(dgAbsy<2 && ((nearGridY%5)==0)) {tabGrid[n][indexSpace]=(byte)(gridVal &0xff);continue;}
						if(dgAbsx<1) {tabGrid[n][indexSpace]=(byte)(gridVal &0xff);continue;}
						if(dgAbsy<1) {tabGrid[n][indexSpace]=(byte)(gridVal &0xff);continue;}
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
						int dgx=x-nearGridX*G;
						int dgy=y-nearGridY*G;
						int dgAbsx=Math.abs(dgx);
						int dgAbsy=Math.abs(dgy);
						if(dgAbsx<3 && ((nearGridX%10)==0)) {tabGrid[n][indexSpace]=(byte)(gridVal &0xff);continue;}
						if(dgAbsy<3 && ((nearGridY%10)==0)) {tabGrid[n][indexSpace]=(byte)(gridVal &0xff);continue;}
						if(dgAbsx<2 && ((nearGridX%5)==0)) {tabGrid[n][indexSpace]=(byte)(gridVal &0xff);continue;}
						if(dgAbsy<2 && ((nearGridY%5)==0)) {tabGrid[n][indexSpace]=(byte)(gridVal &0xff);continue;}
						if(dgAbsx<1) {tabGrid[n][indexSpace]=(byte)(gridVal &0xff);continue;}
						if(dgAbsy<1) {tabGrid[n][indexSpace]=(byte)(gridVal &0xff);continue;}
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
						bef=(int)(tabGrid[n-1][indexSpace]&0xff);
						now=(int)(tabGrid[n][indexSpace]&0xff);
						if(bef>now) {
							now=bef;
							tabGrid[n][indexSpace]=tabGrid[n-1][indexSpace];
						}
					}
				}
			}
		}
		imgOut.setTitle("Fire");
		imgOut.setDisplayRange(0, 255);
		imgMask.setTitle("Mask");
		imgMask.setDisplayRange(0, 255);
		imgGrid.setTitle("Grid");
		imgGrid.setDisplayRange(0, 255);
		imgMaskGrid.setTitle("MaskGrid");
		imgMaskGrid.setDisplayRange(0, 255);
		IJ.run(imgOut,"Fire","");
		IJ.run(imgMask,"Fire","");
		IJ.run(imgGrid,"Fire","");
		imgWeight=null;
		return new ImagePlus [] {imgOut,imgGrid,imgMask,imgMaskGrid};
	}

	public static ImagePlus[] generateGridAndFire_V3(int X,int Y, double[][][]coords,double vMax,double startingBlockRatio,ImagePlus maskRootInit) {
		ImagePlus rootInitArea=MorphoUtils.dilationCircle2D(maskRootInit, 50);
		rootInitArea=VitimageUtils.gaussianFiltering(rootInitArea, 40, 40, 0);
		rootInitArea.setDisplayRange(0, 255);
		IJ.run(rootInitArea,"8-bit","");
		System.out.println("\nGenerating grid and fire V2");
		int gridVal=172;
		double G= (1000/umPerPixel);//nb pixels per mm
		int N=coords.length;
		int P=coords[0].length;
		boolean []isPrimary=new boolean[P];
		for(int p=0;p<P;p++)if(coords[0][p][0]>0)isPrimary[p]=true;
		int sizeRatio=15;
		int deltaN=(int)(N*startingBlockRatio);
		int []nStart=new int[P];
		for(int p=0;p<P;p++)nStart[p]=-1;
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
		int indexTime=0;
		for(int n=0;n<N;n++) {
			if((n%50)==0)System.out.print(n+"/"+N+" ");
			for(int p=0;p<P;p++) {
				double x0=coords[n][p][0];
				double y0=coords[n][p][1];
				double vx=coords[n][p][2];
				double vy=coords[n][p][3];
				double normV=Math.sqrt(vx*vx+vy*vy);
				if(normV==0)normV=VitimageUtils.EPSILON;
				if(x0<=0)continue;
				if(nStart[p]==-1)nStart[p]=n;
				isPrimary[p]=(nStart[p]==0);
				double alpha=1;
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
				double valVit=+255*(normV/vMax);
				byte bVit=toByte(50+205*(normV/vMax));
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

	
	
	/** Helpers ----------------------------------------------------------------------------------------------------------*/
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
		
		ImagePlus[]res= generateGridAndFire(X,Y, coords,vMax,0.05);
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
		

	
	
	
	

	
	
	
	
	
	
	

	public static double[][][]getAsTimeLapseCoords(RootModel rm, double[]t,int sizeFactor){
		int N=t.length;
		int P=rm.rootList.size();
		double[][][]coords=new double[N][P][4];
		for(int p=0;p<P;p++) {
			double[][]coordRoots=getAsTimeLapseCoords(rm.rootList.get(p),t);
			for(int n=0;n<N;n++)for(int c=0;c<4;c++)coords[n][p][c]=coordRoots[n][c];
		}
		return coords;
	}
			
	public static double[][]getAsTimeLapseCoords(Root r,double[]t){
		int N=t.length;
		ArrayList<Node>nodes=r.getNodesList();
		int nNodes=nodes.size();
		double[][]coords=new double[N][4];
		double tMin=nodes.get(0).birthTime;
		double tMax=nodes.get(nNodes-1).birthTime;
		for(int n=0;n<N;n++) {
			if(t[n]<=tMin)coords[n]=new double[] {-1,-1,-1,-1};
			else if(t[n]>tMax)coords[n]=new double[] {-1,-1,-1,-1};
			else {
				//Find before and after
				int indBef=-1;
				int indAft=-1;
				for(int nn=0;nn<nNodes-1;nn++) {
					if(nodes.get(nn).birthTime<t[n] && nodes.get(nn+1).birthTime>=t[n]) {
						indBef=nn;indAft=nn+1;
						break;
					}
				}
				Node n0=nodes.get(indBef);
				Node n1=nodes.get(indAft);				
				double t0=n0.birthTime;
				double t1=n1.birthTime;
				double alpha=(t[n]-t0)/(t1-t0);
				coords[n][0]=n1.x*alpha+(1-alpha)*n0.x;
				coords[n][1]=n1.y*alpha+(1-alpha)*n0.y;
				coords[n][2]=n1.vx*alpha+(1-alpha)*n0.vx;
				coords[n][3]=n1.vy*alpha+(1-alpha)*n0.vy;
			}
		}		
		return coords;
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


