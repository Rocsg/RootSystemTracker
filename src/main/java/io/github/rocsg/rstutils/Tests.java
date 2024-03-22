package io.github.rocsg.rstutils;

import java.util.ArrayList;

import io.github.rocsg.fijiyama.common.Timer;
import io.github.rocsg.fijiyama.registration.TransformUtils;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import io.github.rocsg.fijiyama.fijiyamaplugin.RegistrationAction;
import io.github.rocsg.fijiyama.registration.BlockMatchingRegistration;
import io.github.rocsg.fijiyama.registration.ItkTransform;
import io.github.rocsg.fijiyama.registration.Transform3DType;
import io.github.rocsg.rsml.RootModel;
import io.github.rocsg.rsml.Root;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;

public class Tests {

	public static void main(String []args) {
		runTestGaps();
	}

	
	public static void runTestGaps() {
		ImageJ ij=new ImageJ();
		String imgPathIn="/home/rfernandez/Bureau/A_Test/RootSystemTracker/Cici_V3/TestGaps/Gaps_1.tif";
		String imgPathOut="/home/rfernandez/Bureau/A_Test/RootSystemTracker/Cici_V3/TestGaps/ResultGaps_1.tif";
		String imgPathOut2="/home/rfernandez/Bureau/A_Test/RootSystemTracker/Cici_V3/TestGaps/Result2Gaps_1.tif";
		ImagePlus img=IJ.openImage(imgPathIn);
		img.show();
		img=VitimageUtils.convertByteToFloatWithoutDynamicChanges(img);
		img.setDisplayRange(0, 255);
		img=VitimageUtils.gaussianFiltering(img, 3, 3, 0);
		double valueUp=170;
		double valueMiddle=130;
		double valueDown=0;
		//10 end mid, 15 mid down 23 start up   37 end up   43 mid down  47 start mid 57 end mid
		int X=117;
		int x0down0=42;
		int x1down0=50;
		int x0down1=X-x1down0;
		int x1down1=X-x0down0;
		double[]pattern=new double[X];
		for(int i=0;i<X;i++)pattern[i]=valueMiddle;
		for(int i=x0down0 ; i<=x1down0;i++)pattern[i]=valueDown;
		for(int i=x0down1 ; i<=x1down1;i++)pattern[i]=valueDown;
		for(int i=x1down0+1 ; i<x0down1;i++)pattern[i]=valueUp;
		for(int i=0;i<X;i++)System.out.println("i="+i+" : "+pattern[i]);
		
		int W=img.getWidth();
		int H=img.getHeight();
		int X0=200;
		int X1=W-200;
		int Y0=200;
		int Y1=H-200;
		
		ImagePlus imgRes=img.duplicate();
		double[]valsImg=new double[X];

		for(int z=0;z<img.getStackSize()/2;z++) {
			IJ.log(""+z);
			float[]valsSource=(float [])img.getStack().getProcessor(z+1).getPixels();
			float[]valsDest=(float [])imgRes.getStack().getProcessor(z+1).getPixels();
			for(int x=X0;x<X1;x++)for(int y=Y0;y<Y1;y++) {
				for(int i=0;i<X;i++)valsImg[i]=valsSource[(y+i-X/2)*W+x];
				double val=correlationCoefficient(valsImg,pattern);
				valsDest[y*W+x]=(float)val;//(float) (0.5*valsSource[y*W+x]);//
			}			
		}

		double[]pattern2=new double[] {-0.01646,-0.01333,-0.01004,-0.00735,-0.01370,-0.01850,-0.02276,-0.02765,-0.03272,-0.03556,-0.03859,-0.03970,-0.04042,-0.04201,-0.04293,-0.04086,-0.03761,-0.01561,0.03729,0.09401,0.14953,0.22567,0.30522,0.36592,0.38790,0.36905,0.31766,0.22793,0.13069,0.03164,-0.09360,-0.22026,-0.32244,-0.37713,-0.38337,-0.37950,-0.37428,-0.36669,-0.35502,-0.34598,-0.33873,-0.32867,-0.29258,-0.21574,-0.11000,0.04267,0.23277,0.43660,0.62085,0.74449,0.77959,0.76684,0.71922,0.63593,0.48617,0.30215,0.10157,-0.08395,-0.21650,-0.26510,-0.28427,-0.30011,-0.31382,-0.32119,-0.33043,-0.34289,-0.35312,-0.36370,-0.36916,-0.34226,-0.25218,-0.15106,-0.03768,0.08019,0.18427,0.23923,0.27082,0.29548,0.29859,0.25378,0.20030,0.13544,0.06681,0.01337,-0.00359,-0.00946,-0.01375,-0.01938,-0.02222,-0.02557,-0.02961,-0.03163,-0.03666,-0.03995,-0.03273,-0.02401,-0.01591,-0.00672,0.00357,0.01137,0.01274};;
		X=pattern2.length;
		ImagePlus imgRes2=imgRes.duplicate();
		for(int z=0;z<img.getStackSize()/2;z++) {
			IJ.log(""+z);
			valsImg=new double[X];
			float[]valsSource=(float [])imgRes.getStack().getProcessor(z+1).getPixels();
			float[]valsDest=(float [])imgRes2.getStack().getProcessor(z+1).getPixels();
			for(int x=X0;x<X1;x++)for(int y=Y0;y<Y1;y++) {
				for(int i=0;i<X;i++)valsImg[i]=valsSource[(y+i-X/2)*W+x];
				double val=correlationCoefficient(valsImg,pattern2);
			//	if((x+y)%107==0)IJ.log(""+val);
				valsDest[y*W+x]=(float)val;//(float) (0.5*valsSource[y*W+x]);//
			}			
		}

		IJ.saveAsTiff(imgRes2, imgPathOut2);
		imgRes2=selectOnlyMaxY(imgRes2,40);
		imgRes=selectOnlyMaxY(imgRes,40);
/*		imgRes=MorphoUtils.dilationLine2D(imgRes, 2, true);
		imgRes=MorphoUtils.erosionLine2D(imgRes, 2, true);
		imgRes=MorphoUtils.dilationLine2D(imgRes, 5, true);
		imgRes=MorphoUtils.erosionLine2D(imgRes, 5, true);
		imgRes=MorphoUtils.dilationLine2D(imgRes, 10, true);
		imgRes=MorphoUtils.erosionLine2D(imgRes, 10, true);*/
		img.setTitle("Source");
		img.show();
		img.setDisplayRange(0, 255);
		imgRes.setTitle("Res");
		imgRes.show();
		imgRes.setDisplayRange(-1, 1);
		imgRes2.setTitle("Res2");
		imgRes2.show();
		imgRes2.setDisplayRange(-1, 1);
	}

	
	public static ImagePlus selectOnlyMaxY(ImagePlus img,int radius) {
		ImagePlus res=img.duplicate();
		int W=img.getWidth();
		int H=img.getHeight();
		for(int z=0;z<img.getStackSize()/2;z++) {
			IJ.log(""+z);
			float[]valsSource=(float [])img.getStack().getProcessor(z+1).getPixels();
			float[]valsDest=(float [])res.getStack().getProcessor(z+1).getPixels();
			for(int x=0;x<W;x++)for(int y=radius;y<H-radius;y++) {
				boolean isMax=true;
				for(int i=-radius;i<=radius;i++) {
					if(valsSource[(y)*W+x]<valsSource[(y+i)*W+x]){isMax=false;}
				}
				valsDest[y*W+x]=isMax ? 1 : 0;//(float) (0.5*valsSource[y*W+x]);//
			}			
		}
		return res;
	}	
	
	
	
	
	
	
	
	
	
	
	
	
	
	/**
	 * Correlation coefficient.
	 *
	 * @param X the x
	 * @param Y the y
	 * @return the double
	 */
	public static double correlationCoefficient(double X[], double Y[]) { 
		double epsilon=10E-20;
		if(X.length !=Y.length ) {return 0;}
		int n=X.length;
		double sum_X = 0, sum_Y = 0, sum_XY = 0; 
		double squareSum_X = 0, squareSum_Y = 0; 	
		for (int i = 0; i < n; i++) { 
			sum_X = sum_X + X[i]; 		
			sum_Y = sum_Y + Y[i]; 
			sum_XY = sum_XY + X[i] * Y[i]; 
			squareSum_X = squareSum_X + X[i] * X[i]; 
			squareSum_Y = squareSum_Y + Y[i] * Y[i]; 
		} 
		if(squareSum_X<epsilon || squareSum_Y<epsilon )return 0;
		// use formula for calculating correlation  
		// coefficient. 
		double result=(n * sum_XY - sum_X * sum_Y)/ (Math.sqrt((n * squareSum_X - sum_X * sum_X) * (n * squareSum_Y - sum_Y * sum_Y)));
		if(Math.abs((n * squareSum_X - sum_X * sum_X))<10E-10)return 0; //cas Infinity
		if(Math.abs((n * squareSum_Y - sum_Y * sum_Y))<10E-10)return 0; //cas Infinity
		return result;
	} 

	/*
	RhizoTrackPipeline rtp;
	public static void main(String[]args) {
		ImageJ ij=new ImageJ();
		ImagePlus img=IJ.openImage("/home/rfernandez/Bureau/Photo_identite_Sandrine_Carvalhosa.tif");
		System.out.println(VitimageUtils.isGe3d(img));
		RhizoTrackPipeline rtp= new RhizoTrackPipeline();
		//TODO : To be set depending on tests to run
		rtp.inputDataDir="";
		rtp.processingDataDir="";
	}
	
	
	public	double[]getRootnavBoxCenter(ImagePlus img){
		ImagePlus im=VitimageUtils.convertByteToFloatWithoutDynamicChanges(img);
		float[]data=(float[]) im.getStack().getPixels(1);
		double nInt=0;
		int X=im.getWidth();
		int Y=im.getHeight();
		double[]agr=new double[] {0,0};
		for(int x=0;x<X;x++)		for(int y=0;y<Y;y++) {
			if(data[X*y+x]<60) {
				nInt+=(60-data[X*y+x]);
				agr[0]+=x*(60-data[X*y+x]);
				agr[1]+=y*(60-data[X*y+x]);
			}
		}
	
		return new double[] {agr[0]/nInt,agr[1]/nInt};
	}
	
	public	double[]getRootnavBoxCenter2(ImagePlus img){
		ImagePlus im=img.duplicate();
		IJ.run(im,"Invert","");
		im=VitimageUtils.convertByteToFloatWithoutDynamicChanges(im);
		
		double i=VitimageUtils.getOtsuThreshold(im);
		System.out.println(i);
		ImagePlus im2=VitimageUtils.thresholdFloatImage(im, 80, 500);
		im2.setDisplayRange(0, 1);
		ImagePlus con=VitimageUtils.connexe2dNoFuckWithVolume(im2, 0, 0.5, 0.5, 1000000000, 4, 1, true);
		con.show();
		con=VitimageUtils.convertShortToFloatWithoutDynamicChanges(con);
		float[]data=(float[]) con.getStack().getPixels(1);
		double nInt=0;
		int X=im.getWidth();
		int Y=im.getHeight();
		double[]agr=new double[] {0,0};
		for(int x=0;x<X;x++)		for(int y=0;y<Y;y++) {
			nInt+=(data[X*y+x]);
			agr[0]+=x*(data[X*y+x]);
			agr[1]+=y*(data[X*y+x]);
		}
	
		return new double[] {agr[0]/nInt,agr[1]/nInt};
	}
	
	public static int[] detectBoxCenterInRootnavSlice(ImagePlus img) {
		//Enhance horizontal and vertical lines
		ImagePlus img2=MorphoUtils.dilationLine2D(img, 2, true);img2=MorphoUtils.erosionLine2D(img2, 15, true);
		img2=MorphoUtils.dilationLine2D(img2, 2, false);img2=MorphoUtils.erosionLine2D(img2, 15, false);img2=MorphoUtils.erosionCircle2D(img2, 3);
		//Remove white borders and get biggest component
		img2=VitimageUtils.drawRectangleInImage(img2, 0, 0, 30, 1023, 0);
		img2=VitimageUtils.drawRectangleInImage(img2, 0, 0, 1023, 30, 0);
		img2=VitimageUtils.drawRectangleInImage(img2, 994, 0, 1023, 1023, 0);
		img2=VitimageUtils.drawRectangleInImage(img2, 0, 994, 1023, 1023, 0);
		ImagePlus con=VitimageUtils.connexe2dNoFuckWithVolume(img2, 150, 300, 10000, 1000000000, 4, 1, false);

		//Compute mass center
		con=VitimageUtils.convertShortToFloatWithoutDynamicChanges(con);
		float[]data=(float[]) con.getStack().getPixels(1);
		double nInt=0;
		int X=con.getWidth();
		int Y=con.getHeight();
		double[]agr=new double[] {0,0};
		for(int x=0;x<X;x++)		for(int y=0;y<Y;y++) {
			nInt+=(data[X*y+x]);
			agr[0]+=x*(data[X*y+x]);
			agr[1]+=y*(data[X*y+x]);
		}

		int x0=(int) (agr[0]/nInt);
		int y0=(int) (agr[1]/nInt);
		return new int[] {x0,y0};
	}

	public ImagePlus []registerImageSequenceRootnav(String ml, String boite,boolean viewRegistrations) {
		boolean makeHighRes=false;
		ImagePlus mask=IJ.openImage(rtp.processingDataDir+"/N_Others/maskNewLargerLong.tif");
		ImagePlus imgInit2=IJ.openImage(rtp.processingDataDir+"/0_Stacked/ML"+ml+"_Boite_"+boite+".tif");
		ImagePlus imgInit=VitimageUtils.cropImage(imgInit2, RhizoTrackPipeline.XMINCROP,RhizoTrackPipeline.YMINCROP,0,RhizoTrackPipeline.DXCROP,RhizoTrackPipeline.DYCROP,imgInit2.getStackSize());
		ImagePlus imgOut=imgInit.duplicate();
		IJ.run(imgOut,"32-bit","");

		int N=imgInit.getStackSize();
		ImagePlus []tabImg=VitimageUtils.stackToSlices(imgInit);
		ImagePlus []tabImgTest=VitimageUtils.stackToSlices(imgInit);
		ImagePlus []tabImg2=VitimageUtils.stackToSlices(imgInit);
		ImagePlus []tabImgSmall=VitimageUtils.stackToSlices(imgInit);
		ItkTransform []tr=new ItkTransform[N];
		ItkTransform []trStart=new ItkTransform[N];
		ItkTransform []trComposed=new ItkTransform[N];

		for(int n=0;(n<N);n++) {
			tabImg2[n]=tabImg[n].duplicate();
			int[]tab=detectBoxCenterInRootnavSlice(tabImg[n]);
			trStart[n]=ItkTransform.array16ElementsToItkTransform(new double[] {1,0,0, -512+tab[0], 0,1,0,  -512+tab[1],  0,0,1,0,  0,0,0,1});
			tabImg[n]=trStart[n].transformImage(tabImg[n],tabImg[n]);
		}
		boolean doit=false;
		if(doit) {
			//First step : daisy-chain rigid registration
			Timer t=new Timer();
			t.log("Start");
			
			
			for(int n=0;(n<N-1);n++) {
				t.log("n="+n);
				RegistrationAction regAct=new RegistrationAction().defineSettingsFromTwoImages(tabImg[n].duplicate(),tabImg[n+1].duplicate(),null,false);
				regAct.setLevelMaxLinear(RhizoTrackPipeline.MAXLINEAR);			regAct.setLevelMinLinear(0);
				regAct.strideX=8;			regAct.strideY=8;			regAct.neighX=3;			regAct.neighY=3;
				regAct.selectLTS=90;
				regAct.typeTrans=Transform3DType.TRANSLATION;
				BlockMatchingRegistration bm= BlockMatchingRegistration.setupBlockMatchingRegistration(tabImg[n].duplicate(), tabImg[n+1].duplicate(), regAct);
				bm.defaultCoreNumber=VitimageUtils.getNbCores();
			    //bm.minBlockVariance/=4;
			    viewRegistrations=false;
				if(viewRegistrations) {
					bm.displayRegistration=2;
					bm.adjustZoomFactor(((512.0))/tabImg[n].getWidth());
					bm.flagSingleView=true;
				}
				bm.displayR2=false;
				bm.nbIterations=6;
				bm.mask=IJ.openImage(rtp.processingDataDir+"/N_Others/maskRootnavUp.tif");	
				//Run translation
				tr[n]=bm.runBlockMatching(null, false);		
				tr[n]=tr[n].simplify();
	//			IJ.showMessage("AFTER FIRST tr["+n+"]="+tr[n]);
			    if(viewRegistrations) { bm.closeLastImages();		    bm.freeMemory();		    }
	
				
				
			    
			    
			    
			    
			    
				regAct=new RegistrationAction().defineSettingsFromTwoImages(tabImg[n].duplicate(),tabImg[n+1].duplicate(),null,false);
				regAct.setLevelMaxLinear(1);			regAct.setLevelMinLinear(0);
				regAct.strideX=8;			regAct.strideY=8;			regAct.neighX=3;			regAct.neighY=3;
				regAct.selectLTS=90;
				regAct.typeTrans=Transform3DType.RIGID;
				bm= BlockMatchingRegistration.setupBlockMatchingRegistration(tabImg[n].duplicate(), tabImg[n+1].duplicate(), regAct);
				bm.defaultCoreNumber=VitimageUtils.getNbCores();
			    //bm.minBlockVariance/=4;
			    viewRegistrations=true;
				if(viewRegistrations) {
					bm.displayRegistration=2;
					bm.adjustZoomFactor(((512.0))/tabImg[n].getWidth());
					bm.flagSingleView=true;
				}
				bm.displayR2=false;
				bm.nbIterations=6;
				bm.minBlockVariance/=4;
			    bm.mask=IJ.openImage(rtp.processingDataDir+"/N_Others/maskRootnav.tif");
				//Run rigid
				tr[n]=tr[n].simplify();
	//			IJ.showMessage("BEFORE SECOND tr["+n+"]="+tr[n]);
				tr[n].addTransform(bm.runBlockMatching(tr[n], false));		
				tr[n]=tr[n].simplify();
	//			IJ.showMessage("AFTER SECOND tr["+n+"]="+tr[n]);
	
				if(viewRegistrations) { bm.closeLastImages();		    bm.freeMemory();		    }
			    	
	
			    tr[n].writeMatrixTransformToFile("/home/rfernandez/Bureau/Temp/gg4/tr_"+n+".txt");
				if(viewRegistrations) {
				    bm.closeLastImages();
				    bm.freeMemory();
				}
				VitimageUtils.writeIntInFile("/home/rfernandez/Bureau/Temp/gg/"+(n), n);
			}
		}
		else {
			for(int n=0;(n<N-1);n++) {
				tr[n]=ItkTransform.readTransformFromFile("/home/rfernandez/Bureau/Temp/gg4/tr_"+n+".txt");
			}
		}
		
		for(int n1=0;n1<N-1;n1++) {
			trComposed[n1]=tr[n1];
			for(int n2=n1+1;n2<N-1;n2++) {
				trComposed[n1].addTransform(tr[n2]);
			}
			tabImg[n1]=trComposed[n1].transformImage(tabImg[n1], tabImg[n1]);
		}
		ImagePlus result1=VitimageUtils.slicesToStack(tabImg);
		result1.setTitle("step 1");
		IJ.saveAsTiff(result1, "/home/rfernandez/Bureau/Temp/gg3/step_1_after_rigOt.tif");
		
		
		
		//Second step : daisy-chain dense registration  
		ArrayList<ImagePlus>listAlreadyRegistered=new ArrayList<ImagePlus>();
		listAlreadyRegistered.add(tabImg [N-1]);
		for(int n1=N-2;n1>=0;n1--) {
			ImagePlus imgRef=listAlreadyRegistered.get(listAlreadyRegistered.size()-1);//VitimageUtils.meanOfImageArray(listAlreadyRegistered.toArray(new ImagePlus[N-n1-1]));
			RegistrationAction regAct2=new RegistrationAction().defineSettingsFromTwoImages(tabImg[0],tabImg[0],null,false);				
			regAct2.setLevelMaxNonLinear(1);			regAct2.setLevelMinNonLinear(-1);			regAct2.setIterationsBMNonLinear(4);
			regAct2.typeTrans=Transform3DType.DENSE;
			regAct2.strideX=4;			regAct2.strideY=4;			regAct2.neighX=2;			regAct2.neighY=2;			regAct2.bhsX-=3;			regAct2.bhsY-=3;
			regAct2.sigmaDense/=6;
			regAct2.selectLTS=80;
			BlockMatchingRegistration bm2= BlockMatchingRegistration.setupBlockMatchingRegistration(imgRef, tabImg[n1], regAct2);
			bm2.mask=mask.duplicate();
		    bm2.defaultCoreNumber=VitimageUtils.getNbCores();
		    bm2.minBlockVariance=10;
		    bm2.minBlockScore=0.10;
		    bm2.displayR2=false;
		    bm2.mask=IJ.openImage(rtp.processingDataDir+"/N_Others/maskRootnavDown.tif");
		    viewRegistrations=true;
			if(viewRegistrations) {
				bm2.displayRegistration=2;
				bm2.adjustZoomFactor(512.0/tabImg[n1].getWidth());
			}
//				bm2.minBlockVariance/=2;
			trComposed[n1]=bm2.runBlockMatching(trComposed[n1], false);			
			if(viewRegistrations) {
			    bm2.closeLastImages();
			    bm2.freeMemory();
			}
			tabImg[n1]=trComposed[n1].transformImage(tabImg[n1], tabImg[n1]);
			VitimageUtils.writeIntInFile("/home/rfernandez/Bureau/Temp/gg2/den_"+n1, n1);
			listAlreadyRegistered.add(tabImg[n1]);
		}

		for(int n1=0;n1<N;n1++) {
			ItkTransform trTot=trStart[n1];
			trTot.addTransform(trComposed[n1]);
			tabImg[n1]=trTot.transformImage(tabImg2[n1],tabImg2[n1]);
		}
		
		
		ImagePlus resultHigh=null;
		ImagePlus result2=VitimageUtils.slicesToStack(tabImg);
		result2.setTitle("step 3");
		IJ.saveAsTiff(result2, "/home/rfernandez/Bureau/Temp/gg3/step_3_after_rig.tif");
		result2=VitimageUtils.slicesToStack(tabImg);
		result2.setTitle("final result");

		return new ImagePlus[] {resultHigh,result2};
	}

	
	public void testDateEstimation() {
		int ml=1;
		String boite="00002";
		System.out.println("Processing ML"+ml+"_Boite_"+boite);
		ImagePlus imgIn=IJ.openImage(rtp.processingDataDir+"/1_Registered/ML"+ml+"_Boite_"+boite+".tif");
		ImagePlus imgMask=IJ.openImage(rtp.processingDataDir+"/1_Mask/ML"+ml+"_Boite_"+boite+".tif");
		ImagePlus mire=new RhizoTrackPipeline().computeMire(imgIn);
		imgIn=VitimageUtils.addSliceToImage(mire, imgIn);
		imgIn.show();
		ImagePlus[]tabImg=VitimageUtils.stackToSlices(imgIn);
		ImagePlus[]tabImg2=VitimageUtils.stackToSlices(imgIn);
		for(int i=1;i<tabImg.length;i++)tabImg[i]=VitimageUtils.makeOperationBetweenTwoImages(tabImg2[i-1],tabImg2[i],4,true);
		ImagePlus out=VitimageUtils.slicesToStack(tabImg);
		out.show();
		

	}
	
	public void testRegistration() {
		ImagePlus imgMov=IJ.openImage("/home/rfernandez/Bureau/t0.tif");
		ImagePlus imgRef=IJ.openImage("/home/rfernandez/Bureau/t1.tif");
		imgMov=VitimageUtils.resize(imgMov, imgMov.getWidth()/4, imgMov.getHeight()/4, 1);
		imgRef=VitimageUtils.resize(imgRef, imgRef.getWidth()/4, imgRef.getHeight()/4, 1);
		ImagePlus img=IJ.openImage("/home/rfernandez/Bureau/Temp/gg3/step_2_after_rig.tif");
		ImagePlus mask=null;

		if(true) {
		boolean viewRegistrations=true;
			RegistrationAction regAct=new RegistrationAction().defineSettingsFromTwoImages(imgRef,imgMov,null,false);				
			regAct.setLevelMaxLinear(3);
			regAct.setLevelMinLinear(1);
			regAct.setIterationsBMLinear(6);
			regAct.typeTrans=Transform3DType.RIGID;
			regAct.strideX=4;
			regAct.strideY=4;
			regAct.neighX=1;
			regAct.neighY=1;
			regAct.sigmaDense/=6;
			regAct.selectLTS=99;
			BlockMatchingRegistration bm= BlockMatchingRegistration.setupBlockMatchingRegistration(imgRef, imgMov, regAct);
			bm.mask=mask;
			viewRegistrations=false;
		    bm.defaultCoreNumber=VitimageUtils.getNbCores();
		    bm.minBlockVariance=0.5;
		    bm.minBlockScore=0.001;
		    bm.percentageBlocksSelectedByScore=99;
		    bm.percentageBlocksSelectedByVariance=99;
		    if(viewRegistrations) {
				bm.displayRegistration=2; 
				bm.adjustZoomFactor(512.0/imgRef.getWidth());
			}
			ItkTransform tr=bm.runBlockMatching(null, false);			
			IJ.showMessage("\n\n\n\nDONE\n");
 			if(viewRegistrations) {
			    bm.closeLastImages();
			    bm.freeMemory();
			}
 			tr.writeMatrixTransformToFile("/home/rfernandez/temp.txt");
		}
		ItkTransform tr=ItkTransform.readTransformFromFile("/home/rfernandez/temp.txt");
		
		int N=img.getStackSize();
		int x=10;
		ImagePlus[]tabImg=VitimageUtils.stackToSlices(img);
		ItkTransform[]trComposed=new ItkTransform[N];
		boolean viewRegistrations=true;
		for(int n1=x;n1<x+1;n1++) {
			RegistrationAction regAct2=new RegistrationAction().defineSettingsFromTwoImages(imgRef, imgMov,null,false);				
			regAct2.setLevelMaxNonLinear(0);
			regAct2.setLevelMinNonLinear(-2);
			regAct2.setIterationsBMNonLinear(6);
			regAct2.typeTrans=Transform3DType.DENSE;
			regAct2.strideX=4;
			regAct2.strideY=4;
			regAct2.neighX=2;
			regAct2.neighY=2;
			regAct2.sigmaDense/=12;
			regAct2.selectLTS=99;
			BlockMatchingRegistration bm2= BlockMatchingRegistration.setupBlockMatchingRegistration(imgRef, imgMov, regAct2);
			bm2.mask=mask;
		    bm2.defaultCoreNumber=VitimageUtils.getNbCores();
		    bm2.minBlockVariance=0.5;
		    bm2.minBlockScore=0.001;
		    bm2.percentageBlocksSelectedByScore=99;
		    bm2.percentageBlocksSelectedByVariance=99;
		    if(viewRegistrations) {
				bm2.displayRegistration=2;
				bm2.adjustZoomFactor(512.0/tabImg[n1].getWidth());
			}
			trComposed[n1]=bm2.runBlockMatching(tr, false);			
			IJ.showMessage("\n\n\n\nDONE\n");
			VitimageUtils.waitFor(600000000);
			if(viewRegistrations) {
			    bm2.closeLastImages();
			    bm2.freeMemory();
			}

		}
	}

	public void test() {
		ImagePlus img=IJ.openImage("/home/rfernandez/Bureau/test.tif");
		img=VitimageUtils.resizeNearest(img,200, 200, 1);
		img=VitimageUtils.nullImage(img);
		IJ.run(img,"8-bit","");
		int thick=1;
		double angle=5;
		VitimageUtils.drawCircleIntoImage(img, 10, 100, 100, 1,255);
		img.show();
		VitimageUtils.waitFor(10000000);
	}

	
	*/



	
}


