package fr.cirad.image.TimeLapseRhizo;

import java.io.File;
import java.util.ArrayList;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import fr.cirad.image.common.Timer;
import fr.cirad.image.common.VitimageUtils;
import fr.cirad.image.fijiyama.RegistrationAction;
import fr.cirad.image.registration.BlockMatchingRegistration;
import fr.cirad.image.registration.ItkTransform;
import fr.cirad.image.registration.Transform3DType;
import fr.cirad.image.rsmlviewer.Node;
import fr.cirad.image.rsmlviewer.Root;
import fr.cirad.image.rsmlviewer.RootModel;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.Duplicator;
import ij.plugin.RGBStackMerge;

//TODO : when registering, do the crop partly before, then registration, then partly after. In order to avoid to have bogus reconstructed BG at the extremities, what can have an impact
//TODO : times all is an image name where distance is displayed in pixel. It should be avoided
//TODO : list the steps and the step parameters settings in order to identify parameterizable operations, in order to adapt to new datasets


public class TestImageSequence {	
	static boolean testing=true;
	static final String mainDataDir=testing ? "/home/rfernandez/Bureau/A_Test/RSML"
			: "/media/rfernandez/DATA_RO_A/Roots_systems/Data_BPMP/Second_dataset_2021_07/Processing";

	
	public static void main(String[]args) {
		//VitimageUtils.waitFor(50000);
		ImageJ ij=new ImageJ();
		//SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph=readGraphFromFile(mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite);
//		testGraph(graph);
//		VitimageUtils.waitFor(50000000);
/*		runRegisterSequences();
		VitimageUtils.waitFor(50000000);
		ImagePlus img=IJ.openImage(mainDataDir+"/1_Registered/ML1_Boite_00002.tif");
		ImagePlus imgMask1=IJ.openImage(mainDataDir+"/1_Mask/ML1_Boite_00002.tif");
		ImagePlus imgMask2=IJ.openImage(mainDataDir+"/1_Mask_Feuilles/ML1_Boite_00002.tif");
		img.show();
		ImagePlus img2=removeLeavesFromSequence(img, imgMask1, imgMask2);
		img2.show();
		VitimageUtils.compositeNoAdjustOf(img, img2).show();
	*/	
		//ImagePlus imgMask=IJ.openImage(mainDataDir+"/1_Mask/ML1_Boite_00002.tif");
		
		//removeLeaf(1,2);
		//VitimageUtils.waitFor(1000000);
/*		
 * 
 * testRegistration();
		IJ.showMessage("Done");
		VitimageUtils.waitFor(600000000);
 * ImagePlus img=IJ.openImage(mainDataDir+"/1_Registered/ML1_Boite_00002.tif");

		img2.show();
		VitimageUtils.waitFor(5000000);*/
/*		ImagePlus img=IJ.openImage("/home/rfernandez/Bureau/test.tif");
		img=VitimageUtils.convertFloatToByteWithoutDynamicChanges(img);
		img.setTitle("Before");
		img.setDisplayRange(0, 22);
		//img.show();
				
		ImagePlus img2=MostRepresentedFilter.mostRepresentedFilteringWithRadius(img,1.5,false,23,false);
		img2.setTitle("After");
		img2.setDisplayRange(0, 22);
		img2.show();
*/		
//		test();
		//runComputeMaskAndRemoveLeaves();
		//VitimageUtils.waitFor(50000000);
		for(int mli=1;mli<=1;mli++) {
			for(int boi=5;boi<=5;boi++) {
				String boite=(  (boi<10) ? ("0000"+boi) : ( (boi<100) ? ("000"+boi) : ("00"+boi) ) );
				String ml=""+mli;
				Timer t=new Timer();
				t.print("Start"+mli+"-"+boi);
				System.out.println("Processing ML"+ml+"_Boite_"+boite);
				//runImportSequences(ml,boite);
//				runRegisterSequences(ml,boite);
//				runComputeMaskAndRemoveLeaves(ml,boite);//9 secondes
//				runDateEstimation(ml,boite);//2.61 secondes
				buildAndProcessGraph(ml,boite);
				computeTimes(ml, boite);
				t.print("Stop"+mli+"-"+boi);
			}
		}
		IJ.showMessage("Done");
		VitimageUtils.waitFor(600000000);
		System.exit(0);
	}

	
	
	/**Test sequences ***********************************************************************************************************************/	
	public static void testDateEstimation() {
		int ml=1;
		String boite="00002";
		System.out.println("Processing ML"+ml+"_Boite_"+boite);
		ImagePlus imgIn=IJ.openImage(mainDataDir+"/1_Registered/ML"+ml+"_Boite_"+boite+".tif");
		ImagePlus imgMask=IJ.openImage(mainDataDir+"/1_Mask/ML"+ml+"_Boite_"+boite+".tif");
		ImagePlus mire=computeMire(imgIn);
		imgIn=VitimageUtils.addSliceToImage(mire, imgIn);
		imgIn.show();
		ImagePlus[]tabImg=VitimageUtils.stackToSlices(imgIn);
		ImagePlus[]tabImg2=VitimageUtils.stackToSlices(imgIn);
		for(int i=1;i<tabImg.length;i++)tabImg[i]=VitimageUtils.makeOperationBetweenTwoImages(tabImg2[i-1],tabImg2[i],4,true);
		ImagePlus out=VitimageUtils.slicesToStack(tabImg);
		out.show();
		

	}
	
	public static void testRegistration() {
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

	public static void test() {
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

	
	
	/** Main entry points --------------------------------------------------------------------------------------------------------------------------------------------------------*/
	public static void runImportSequences(String ml, String boite) {
		ImagePlus img=importTimeLapseSerie(""+ml, boite,".jpg",null,false);
		if(img!=null) {
			int X=img.getWidth();
			int Y=img.getHeight();
			int Z=img.getStackSize();
			System.out.print(" resize...");
			VitimageUtils.adjustImageCalibration(img, new double[] {19,19,19}, "µm");
			IJ.saveAsTiff(img, mainDataDir+"/0_Stacked_Highres/ML"+ml+"_Boite_"+boite);
			img=VitimageUtils.resize(img, X/4, X/4, Z);
			VitimageUtils.adjustImageCalibration(img, new double[] {19*4,19*4,19*4}, "µm");
			IJ.saveAsTiff(img, mainDataDir+"/0_Stacked/ML"+ml+"_Boite_"+boite);
		}				
	}
			
	public static void runRegisterSequences(String ml, String boite) {
		ImagePlus []imgs=registerImageSequence(""+ml,boite,4,false);
		System.out.println("Did !");
		System.out.println("Saving as "+mainDataDir+"/1_Registered/ML"+ml+"_Boite_"+boite+".tif");
		IJ.saveAsTiff(imgs[1], mainDataDir+"/1_Registered/ML"+ml+"_Boite_"+boite+".tif");				
		if(imgs[0]!=null)	IJ.saveAsTiff(imgs[0], mainDataDir+"/1_Registered_High/ML"+ml+"_Boite_"+boite+".tif");				
	}

	public static void runComputeMaskAndRemoveLeaves(String ml, String boite) {
		boolean highRes=false;
		ImagePlus imgReg=IJ.openImage(mainDataDir+"/1_Registered"+(highRes ? "_High":"")+"/ML"+ml+"_Boite_"+boite+".tif");
		ImagePlus imgMask1=VitimageUtils.getBinaryMaskUnary(getInterestAreaMask(new Duplicator().run(imgReg,1,1,1,1,1,1),highRes),0.5);
		imgMask1.setDisplayRange(0, 1);
		IJ.saveAsTiff(imgMask1, mainDataDir+"/1_Mask_1/ML"+ml+"_Boite_"+boite+".tif");

		ImagePlus imgMaskN=VitimageUtils.getBinaryMaskUnary(getInterestAreaMask(new Duplicator().run(imgReg,1,1,imgReg.getStackSize(),imgReg.getStackSize(),1,1),highRes),0.5);
		imgMaskN.setDisplayRange(0, 1);
		IJ.saveAsTiff(imgMaskN, mainDataDir+"/1_Mask_N/ML"+ml+"_Boite_"+boite+".tif");

		ImagePlus imgMask2=	MorphoUtils.erosionCircle2D(imgMask1, 250*(highRes ? 4 : 1));
		imgMask2.setDisplayRange(0, 1);
		IJ.saveAsTiff(imgMask2, mainDataDir+"/1_Mask_Feuilles/ML"+ml+"_Boite_"+boite+".tif");		

		ImagePlus []imgsOut=removeLeavesFromSequence(imgReg, imgMask1, imgMask2,highRes);
		imgsOut[0].setDisplayRange(0, 255);

		IJ.saveAsTiff(imgsOut[0],mainDataDir+"/1_Remove_Leaves/ML"+ml+"_Boite_"+boite+".tif");
		imgsOut[1].setDisplayRange(0, 1);
		IJ.saveAsTiff(imgsOut[1],mainDataDir+"/1_Mask_Of_Leaves/ML"+ml+"_Boite_"+boite+".tif");
	}	
	
	public static void runDateEstimation(String ml, String boite) {
		ImagePlus imgIn=IJ.openImage(mainDataDir+"/1_Remove_Leaves/ML"+ml+"_Boite_"+boite+".tif");
		ImagePlus imgMask1=IJ.openImage(mainDataDir+"/1_Mask_1/ML"+ml+"_Boite_"+boite+".tif");
		ImagePlus imgMaskN=IJ.openImage(mainDataDir+"/1_Mask_N/ML"+ml+"_Boite_"+boite+".tif");
		ImagePlus imgMaskOfLeaves=IJ.openImage(mainDataDir+"/1_Mask_Of_Leaves/ML"+ml+"_Boite_"+boite+".tif");
		
		ImagePlus mire=computeMire(imgIn);
		imgIn=VitimageUtils.addSliceToImage(mire, imgIn);
		if(imgIn==null)return;
		imgIn.show();
		ImagePlus imgOut=projectTimeLapseSequenceInColorspaceCombined(imgIn, imgMask1,imgMaskN,imgMaskOfLeaves,20,20);
		imgOut=VitimageUtils.makeOperationBetweenTwoImages(imgOut, imgMaskN, 2, true);
		ImagePlus img2=VitimageUtils.thresholdImage(imgOut, 0.5, 100000);
		img2=VitimageUtils.connexeNoFuckWithVolume(img2, 1, 10000, 3000, 1E10, 4, 0, true);
		img2=VitimageUtils.thresholdImage(img2, 0.5, 1E8);
		img2=VitimageUtils.getBinaryMaskUnary(img2, 0.5);
		IJ.run(img2,"8-bit","");
		imgOut=VitimageUtils.makeOperationBetweenTwoImages(imgOut, img2, 2, true);
		IJ.run(imgOut,"Fire","");
		imgOut.setDisplayRange(-1, 22);
		VitimageUtils.showWithParams(imgOut.duplicate(), "imgOut", 0, 0, 22);
		IJ.saveAsTiff(imgOut, mainDataDir+"/2_Date_maps/ML"+ml+"_Boite_"+boite+".tif");
	}

	public static void runBuildGraphs(String ml,String boite) {
		//ImagePlus imgOut=buildAndProcessGraph(""+ml,boite);
		//IJ.saveAsTiff(imgOut, mainDataDir+"/3_Graphs/ML"+ml+"_Boite_"+boite);
	}
	


	
	/**
	 * Remove the falling stem of arabidopsis from a time lapse sequence imgMask contains all the root system, and imgMask2 only the part that cannot have a arabidopsis stem (the lower part)
	 * 	 */
	public static ImagePlus[] removeLeavesFromSequence(ImagePlus imgInit,ImagePlus imgMaskInit,ImagePlus imgMask2Init,boolean highres) {
		ImagePlus imgMask2=VitimageUtils.invertBinaryMask(imgMask2Init);
		int factor=highres ? 4:1;
		ImagePlus[]tabMaskOut=VitimageUtils.stackToSlices(imgInit);
		ImagePlus[]tabMaskIn=VitimageUtils.stackToSlices(imgInit);
		ImagePlus[]tabInit=VitimageUtils.stackToSlices(imgInit);
		ImagePlus[]tabTot=VitimageUtils.stackToSlices(imgInit);

		tabMaskOut[0]=VitimageUtils.nullImage(tabMaskOut[0]);
		tabMaskIn[0]=VitimageUtils.invertBinaryMask(tabMaskOut[0]);
		for(int z=1;z<tabInit.length;z++) {
			//Get the big elements of object under the menisque
			ImagePlus img=VitimageUtils.makeOperationBetweenTwoImages(tabInit[z], imgMaskInit, 2, true);
			img=MorphoUtils.dilationCircle2D(img, 2*factor);
			img=VitimageUtils.gaussianFiltering(img, 3*factor, 3*factor, 0);
			ImagePlus biggas=VitimageUtils.thresholdImage(img, -100, 120);
			tabMaskOut[z]=VitimageUtils.binaryOperationBetweenTwoImages(imgMask2.duplicate(), biggas, 2);
			tabMaskOut[z]=VitimageUtils.binaryOperationBetweenTwoImages(imgMaskInit, tabMaskOut[z], 2);
			tabMaskOut[z]=MorphoUtils.dilationCircle2D(tabMaskOut[z], 2*factor);
//			tabMaskOut[z]=VitimageUtils.dilationCircle2D(tabMaskOut[z], 20);
//			tabMaskOut[z]=VitimageUtils.erosionCircle2D(tabMaskOut[z], 10);
			tabMaskOut[z].setDisplayRange(0, 1);
			tabMaskOut[z].setTitle(" "+z);
			
			//Combine this mask with the one of the previous image
			tabMaskOut[z]=VitimageUtils.binaryOperationBetweenTwoImages(tabMaskOut[z-1], tabMaskOut[z], 1);
			tabMaskOut[z]=VitimageUtils.connexe2dNoFuckWithVolume(tabMaskOut[z], 0.5, 1000, 1000, 1000000, 4, 0, true);
			tabMaskOut[z]=VitimageUtils.thresholdImage(tabMaskOut[z], 0.5, 1000);
			tabMaskOut[z]=VitimageUtils.getBinaryMaskUnary(tabMaskOut[z], 0.5);			
			tabMaskIn[z]=VitimageUtils.invertBinaryMask(tabMaskOut[z]);

			//Replace area masked with surrounding areas
			ImagePlus imgInitDil=MorphoUtils.dilationLine2D(tabInit[z], 50*factor, true);
			ImagePlus imgPart1=VitimageUtils.makeOperationBetweenTwoImages(tabMaskIn[z], tabInit[z], 2, false);
			ImagePlus imgPart2=VitimageUtils.makeOperationBetweenTwoImages(tabMaskOut[z], imgInitDil, 2, false);
			tabTot[z]=VitimageUtils.makeOperationBetweenTwoImages(imgPart1, imgPart2, 1, false);
			VitimageUtils.showWithParams(tabTot[z], "tabTot "+z, 0, 0,255  );
		}
		ImagePlus img1=VitimageUtils.slicesToStack(tabTot);
		img1.setDisplayRange(0, 255);
		ImagePlus img2=VitimageUtils.slicesToStack(tabMaskOut);
		img2.setDisplayRange(0, 1);
		return new ImagePlus [] {img1,img2};
	}
			
	public static void computeTimes(String ml, String boite) {
		ImagePlus dates=IJ.openImage(mainDataDir+"/2_Date_maps/ML"+ml+"_Boite_"+boite+".tif");
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph=RegionAdjacencyGraphUtils.readGraphFromFile(mainDataDir+"/3_Graphs_Ser/"+"ML"+ml+"_Boite_"+boite+".ser");
		ImagePlus distOut=MorphoUtils.getDistOut(dates,false);
		RootModel rm=RegionAdjacencyGraphUtils.refinePlongementOfCCGraph(graph,distOut,0.9);
		rm.writeRSML3D(mainDataDir+"/4_RSML/ML"+ml+"_Boite_"+boite+".rsml", "",true);
		rm=RootModel.RootModelWildReadFromRsml(mainDataDir+"/4_RSML/ML"+ml+"_Boite_"+boite+".rsml");
		ImagePlus skeletonTime=RegionAdjacencyGraphUtils.drawDistanceOrTime(dates,graph,false,true,3);
		ImagePlus skeletonDay=RegionAdjacencyGraphUtils.drawDistanceOrTime(dates,graph,false,true,2);
		ImagePlus allTimes=RegionAdjacencyGraphUtils.drawDistanceOrTime(dates,graph,false,false,1);
		//rm.createGrayScaleImage(skeletonTime,0,false,true,1).show(); 


	//	rm.writeRSML3D(mainDataDir+"/4_RSML/ML"+ml+"_Boite_"+boite+".rsml","TEST",true);

		createTimeSequenceSuperposition(ml,boite,rm,skeletonTime,false);
		
		skeletonDay.setDisplayRange(0, 23);
		skeletonTime.setDisplayRange(0, 23);
		allTimes.setDisplayRange(0, 23);
		IJ.saveAsTiff(skeletonTime, mainDataDir+"/4_Times_skeleton/ML"+ml+"_Boite_"+boite+".tif");
		IJ.saveAsTiff(skeletonTime, mainDataDir+"/4_Times_skeleton/ML"+ml+"_Boite_"+boite+".tif");
		IJ.saveAsTiff(skeletonDay, mainDataDir+"/4_Day_skeleton/ML"+ml+"_Boite_"+boite+".tif");
		IJ.saveAsTiff(allTimes, mainDataDir+"/4_Times/ML"+ml+"_Boite_"+boite+".tif");
	}
	
	public static void createTimeSequenceSuperposition(String ml, String boite,RootModel rm,ImagePlus refSize,boolean highRes){
		ImagePlus imgReg=(highRes) ? IJ.openImage(mainDataDir+"/1_Registered"+(highRes ? "_High" : "")+"/ML"+ml+"_Boite_"+boite+".tif") :  IJ.openImage(mainDataDir+"/1_Registered/ML"+ml+"_Boite_"+boite+".tif");
		ImagePlus[]tabRes=VitimageUtils.stackToSlices(imgReg);
		Timer t=new Timer();
		for(int i=0;i<tabRes.length;i++) {
			t.print("I="+i);
			ImagePlus imgRSML=rm.createGrayScaleImageWithTime(refSize,(highRes ? 4 : 1),false,(i+1),true,new boolean[] {true,true,true,false,true},new double[] {highRes ? 6 : 1,highRes ? 4 : 1});
			tabRes[i]=RGBStackMerge.mergeChannels(new ImagePlus[] {tabRes[i],imgRSML}, false);
			IJ.run(tabRes[i],"RGB Color","");
		}
		//ImagePlus res=VitimageUtils.slicesWithChannelsToStackWithChannels(tabRes);
		ImagePlus res=VitimageUtils.slicesToStack(tabRes);
		res.show();
	}

	
	
	
	
	/**
    STEP 00 : Import a 2D time-lapse series as a 3D volume (Time=Z axis, from 0 to N-1)
    If no dataDir is given, open the image into rfernandez's DATA drive
    */
	public static ImagePlus importTimeLapseSerie(String ml, String boite,String extension,String dataDir,boolean verbose) {
		String hardDiskPath="/media/rfernandez/DATA_RO_A/Roots_systems/Data_BPMP/Second_dataset_2021_07/Data_Tidy/";
		String dirData=( (dataDir==null) ? (mainDataDir+"/Data_Tidy/") : dataDir );
		ArrayList<ImagePlus> listImg=new ArrayList<ImagePlus>();
		ArrayList<String>listLabels=new ArrayList<String>();
		for(int i=0;i<100;i++) {
			String seq=""+(i+1);
			String specName="ML"+ml+"_Seq_"+seq+"_Boite_"+boite;
			if(new File(hardDiskPath+"IMG/ML"+ml+"/Seq_"+seq+"/"+specName+extension).exists() ) {
				String date=findFullDateInCsv(hardDiskPath+"CSV/ML"+ml+"/Seq_"+seq+"/Timepoints.csv",specName);
				
				if(verbose)System.out.print(" "+seq+" with found date="+date);
				ImagePlus img=IJ.openImage(hardDiskPath+"IMG/ML"+ml+"/Seq_"+seq+"/"+specName+extension);
				listLabels.add(date);
				listImg.add(img);
			}
		}
		if(verbose)System.out.println();
		if(listImg.size()<1) {
			if(verbose)System.out.println("Nothing");return null;
		}
		ImagePlus[]imgSequence=listImg.toArray(new ImagePlus[listImg.size()]);

		ImagePlus imgStack= VitimageUtils.slicesToStack(imgSequence);
		int h0=Integer.parseInt(listLabels.get(0).split("Hours=")[1]);
		for(int i=0;i<imgStack.getStackSize();i++) {
			int hi=Integer.parseInt(listLabels.get(i).split("Hours=")[1]);
			hi=hi-h0;
			String go=listLabels.get(i).split("Hours=")[0]+"Hours="+hi;
			imgStack.getStack().setSliceLabel(go, i+1);
		}
		return imgStack;

	}
		
	 /**
    STEP 01 : Register stack comprising successive 2D images of root systems.
    If no dataDir is given, open the image into rfernandez's DATA drive
    */
	public static ImagePlus []registerImageSequence(String ml, String boite,int additionnalIterationsUsingMeanImage,boolean viewRegistrations) {
		boolean makeHighRes=false;
		ImagePlus mask=IJ.openImage(mainDataDir+"/N_Others/maskNewLong.tif");
		ImagePlus imgInit2=IJ.openImage(mainDataDir+"/0_Stacked/ML"+ml+"_Boite_"+boite+".tif");
		ImagePlus imgInit2High=null;
		if(makeHighRes)imgInit2High=IJ.openImage(mainDataDir+"/0_Stacked_Highres/ML"+ml+"_Boite_"+boite+".tif");
		ImagePlus imgInit=VitimageUtils.cropImage(imgInit2, 122,152,0,1348,1226,imgInit2.getStackSize());
		ImagePlus imgInitHigh=null;
		if(makeHighRes)imgInitHigh=VitimageUtils.cropImage(imgInit2High, 122*4,152*4,0,1348*4,1226*4,imgInit2High.getStackSize());
		ImagePlus imgOut=imgInit.duplicate();
		IJ.run(imgOut,"32-bit","");

		int N=imgInit.getStackSize();
		ImagePlus []tabImg=VitimageUtils.stackToSlices(imgInit);
		ImagePlus []tabImg2=VitimageUtils.stackToSlices(imgInit);
		ImagePlus []tabImgHigh=null;
		if(makeHighRes)tabImgHigh=VitimageUtils.stackToSlices(imgInitHigh);
		ImagePlus []tabImgSmall=VitimageUtils.stackToSlices(imgInit);
		ItkTransform []tr=new ItkTransform[N];
		ItkTransform []trComposed=new ItkTransform[N];
		for(int i=0;i<tabImgSmall.length;i++) {tabImgSmall[i]=VitimageUtils.cropImage(tabImgSmall[i], 0, 0,0, tabImgSmall[i].getWidth(),(tabImgSmall[i].getHeight()*2)/3,1);}

		
		boolean doit=true;
		if(doit) {
		//First step : daisy-chain rigid registration
		Timer t=new Timer();
		t.log("Start");
		for(int n=0;(n<N-1);n++) {
			t.log("n="+n);
			RegistrationAction regAct=new RegistrationAction().defineSettingsFromTwoImages(tabImg[n],tabImg[n+1],null,false);
			regAct.setLevelMaxLinear(4);			regAct.setLevelMinLinear(0);
			regAct.strideX=8;			regAct.strideY=8;			regAct.neighX=3;			regAct.neighY=3;
			regAct.selectLTS=90;
			regAct.setIterationsBM(8);
			BlockMatchingRegistration bm= BlockMatchingRegistration.setupBlockMatchingRegistration(tabImgSmall[n+1], tabImgSmall[n], regAct);
			bm.mask=mask.duplicate();
		    bm.defaultCoreNumber=VitimageUtils.getNbCores();
		    bm.minBlockVariance/=4;
		    viewRegistrations=false;
			if(viewRegistrations) {
				bm.displayRegistration=2;
				bm.adjustZoomFactor(512.0/tabImg[n].getWidth());
				bm.flagSingleView=true;
			}
			bm.displayR2=false;
		    tr[n]=bm.runBlockMatching(null, false);		
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
			trComposed[n1]=new ItkTransform(tr[n1]);
			for(int n2=n1+1;n2<N-1;n2++) {
				trComposed[n1].addTransform(tr[n2]);
			}
			tabImg[n1]=trComposed[n1].transformImage(tabImg[n1], tabImg[n1]);
		}
		ImagePlus result1=VitimageUtils.slicesToStack(tabImg);
		result1.setTitle("step 1");
		IJ.saveAsTiff(result1, "/home/rfernandez/Bureau/Temp/gg3/step_1_after_rig.tif");
		ImagePlus result2=null;
		
		
		
		//Second step : daisy-chain dense registration  
		ArrayList<ImagePlus>listAlreadyRegistered=new ArrayList<ImagePlus>();
		listAlreadyRegistered.add(tabImg2 [N-1]);
		for(int n1=N-2;n1>=0;n1--) {
			ImagePlus imgRef=listAlreadyRegistered.get(listAlreadyRegistered.size()-1);//VitimageUtils.meanOfImageArray(listAlreadyRegistered.toArray(new ImagePlus[N-n1-1]));
			RegistrationAction regAct2=new RegistrationAction().defineSettingsFromTwoImages(tabImg[0],tabImg[0],null,false);				
			regAct2.setLevelMaxNonLinear(1);			regAct2.setLevelMinNonLinear(-1);			regAct2.setIterationsBMNonLinear(4);
			regAct2.typeTrans=Transform3DType.DENSE;
			regAct2.strideX=4;			regAct2.strideY=4;			regAct2.neighX=2;			regAct2.neighY=2;			regAct2.bhsX-=3;			regAct2.bhsY-=3;
			regAct2.sigmaDense/=6;
			regAct2.selectLTS=80;
			BlockMatchingRegistration bm2= BlockMatchingRegistration.setupBlockMatchingRegistration(imgRef, tabImg2[n1], regAct2);
			bm2.mask=mask.duplicate();
		    bm2.defaultCoreNumber=VitimageUtils.getNbCores();
		    bm2.minBlockVariance=10;
		    bm2.minBlockScore=0.10;
		    bm2.displayR2=false;
		    viewRegistrations=false;
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
			tabImg[n1]=trComposed[n1].transformImage(tabImg2[n1], tabImg2[n1]);
			VitimageUtils.writeIntInFile("/home/rfernandez/Bureau/Temp/gg2/den_"+n1, n1);
			if(makeHighRes)tabImgHigh[n1]=trComposed[n1].transformImage(tabImgHigh[n1],tabImgHigh[n1]);
			listAlreadyRegistered.add(tabImg[n1]);
		}
		ImagePlus resultHigh=null;
		if(makeHighRes)resultHigh=VitimageUtils.slicesToStack(tabImgHigh);
		result2=VitimageUtils.slicesToStack(tabImg);
		result2.setTitle("step 3");
		IJ.saveAsTiff(result2, "/home/rfernandez/Bureau/Temp/gg3/step_3_after_rig.tif");
		result2=VitimageUtils.slicesToStack(tabImg);
		result2.setTitle("final result");

		return new ImagePlus[] {resultHigh,result2};
	}
		
	/**
    STEP 02 : Identify pixel-wise the first date of presence of any root. Build a datemap
    */
	public static ImagePlus projectTimeLapseSequenceInColorspaceCombined(ImagePlus imgSeq,ImagePlus interestMask1,ImagePlus interestMaskN,ImagePlus maskOfLeaves,int thresholdRupture,int thresholdSlope) {
		ImagePlus result1=projectTimeLapseSequenceInColorspaceMaxRuptureDown(imgSeq,interestMask1,interestMaskN,maskOfLeaves,thresholdRupture);
		ImagePlus result2=projectTimeLapseSequenceInColorspaceMaxSlope(imgSeq,interestMask1,interestMaskN,thresholdSlope);
		ImagePlus out=VitimageUtils.thresholdImage(result1, -0.5, 0.5);
		out=VitimageUtils.invertBinaryMask(out);
		ImagePlus mask=VitimageUtils.getBinaryMaskUnary(out, 0.5);
		result2=VitimageUtils.makeOperationBetweenTwoImages(result2, mask, 2, false);
		return result2;
	}

	/**
	 *  STEP 03 : Compute graphs
	 */
	@SuppressWarnings("unchecked")
	public static void buildAndProcessGraph(String ml, String boite) {
		//Import and oversample image of dates
		ImagePlus imgDatesTmp=IJ.openImage(mainDataDir+"/2_Date_maps/ML"+ml+"_Boite_"+boite+".tif");
		RegionAdjacencyGraphUtils.buildAndProcessGraphStraight(imgDatesTmp,mainDataDir,ml,boite,true,true);
	}

	
	
	
	
	
	
	
	

	/// Other helpers /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public static String findDateInCsv(String path,String pattern) {
		System.out.println(path);
		String[][]tab=VitimageUtils.readStringTabFromCsv(path);
		for(int i=0;i<tab.length;i++) {
			if(tab[i][0].contains(pattern))return tab[i][1];
		}
		return null;
	}

	public static String findFullDateInCsv(String path,String pattern) {
		System.out.println(path);
		String[][]tab=VitimageUtils.readStringTabFromCsv(path);
		for(int i=0;i<tab.length;i++) {
			if(tab[i][0].contains(pattern))return tab[i][1].replace(" ", "")+"_Hours="+Math.round(Long.parseLong(tab[i][2].replace(" ",""))/(3600*1000));
		}
		return null;
	}
	
	public static ImagePlus indOfFirstLowerThan(ImagePlus []imgs,double threshold) {
		double max=0;
		int xM=imgs[0].getWidth();
		int yM=imgs[0].getHeight();
		int zM=imgs[0].getStackSize();
		ImagePlus retVal=VitimageUtils.nullImage(imgs[0].duplicate());
		ImagePlus retInd=VitimageUtils.nullImage(imgs[0].duplicate());
		float[]valsInd;
		float[]valsVal;
		float[][]valsImg=new float[imgs.length][];
		for(int z=0;z<zM;z++) {
			valsInd=(float [])retInd.getStack().getProcessor(z+1).getPixels();
			valsVal=(float [])retVal.getStack().getProcessor(z+1).getPixels();
			for(int i=0;i<imgs.length;i++) {
				valsImg[i]=(float [])imgs[i].getStack().getProcessor(z+1).getPixels();
			}
			for(int x=0;x<xM;x++) {
				for(int y=0;y<yM;y++) {
					for(int i=0;i<imgs.length && valsInd[xM*y+x]==0;i++) {
						if( (valsImg[i][xM*y+x])< threshold) {
						valsInd[xM*y+x]=i; 
						continue;
					}
				}
			}			
			}
		}
		return retInd;
	}
	
	public static ImagePlus indMaxOfImageArrayDouble(ImagePlus []imgs,int minThreshold) {
		double max=0;
		int xM=imgs[0].getWidth();
		int yM=imgs[0].getHeight();
		int zM=imgs[0].getStackSize();
		ImagePlus retVal=VitimageUtils.nullImage(imgs[0].duplicate());
		ImagePlus retInd=VitimageUtils.nullImage(imgs[0].duplicate());
		float[]valsInd;
		float[]valsVal;
		float[]valsImg;
		for(int z=0;z<zM;z++) {
			valsInd=(float [])retInd.getStack().getProcessor(z+1).getPixels();
			valsVal=(float [])retVal.getStack().getProcessor(z+1).getPixels();
			for(int i=0;i<imgs.length;i++) {
				valsImg=(float [])imgs[i].getStack().getProcessor(z+1).getPixels();
				for(int x=0;x<xM;x++) {
					for(int y=0;y<yM;y++) {
						if( ( (valsImg[xM*y+x])> valsVal[xM*y+x]) && ((valsImg[xM*y+x])> minThreshold) ) {
							valsVal[xM*y+x]=valsImg[xM*y+x]; 
							valsInd[xM*y+x]=i; 
						}
					}
				}			
			}
		}
		return retInd;
	}

	public static ImagePlus projectTimeLapseSequenceInColorspaceSimpler(ImagePlus imgSeq,int threshold) {
		ImagePlus imgSeq2=VitimageUtils.convertByteToFloatWithoutDynamicChanges(imgSeq);
		ImagePlus[]imgTab=VitimageUtils.stackToSlices(imgSeq2);
		ImagePlus res=indOfFirstLowerThan(imgTab,threshold);
		return res;
	}
	
	public static ImagePlus projectTimeLapseSequenceInColorspaceMaxSlope(ImagePlus imgSeq,ImagePlus interestMask1,ImagePlus interestMaskN,int threshold) {
		int N=imgSeq.getStackSize();
		ImagePlus[]imgTab=VitimageUtils.stackToSlices(imgSeq);
		ImagePlus[]imgs=new ImagePlus[N];
		for(int i=0;i<N-1;i++) {
			imgs[i+1]=VitimageUtils.makeOperationBetweenTwoImages(imgTab[i], imgTab[i+1], 4, true);
			imgs[i+1]=VitimageUtils.makeOperationBetweenTwoImages(imgs[i+1],i==0 ? interestMask1:interestMaskN, 2, true);
		}
		imgs[0]=VitimageUtils.nullImage(imgs[1]);
		ImagePlus res=VitimageUtils.indMaxOfImageArrayDouble(imgs,threshold);
		return res;
	}

	public static ImagePlus projectTimeLapseSequenceInColorspaceMaxRuptureDown(ImagePlus imgSeq,ImagePlus interestMask1,ImagePlus interestMaskN,ImagePlus maskOutLeaves,int threshold) {
		ImagePlus[]tab=VitimageUtils.stackToSlices(imgSeq);
		IJ.run(maskOutLeaves,"32-bit","");
		ImagePlus[]tabLeavesOut=VitimageUtils.stackToSlices(maskOutLeaves);
		for(int i=0;i<tab.length;i++) {
			tab[i]=VitimageUtils.makeOperationBetweenTwoImages(tab[i],i<2 ? interestMask1 : interestMaskN, 2, true);
		}
		ImagePlus res=indRuptureDownOfImageArrayDouble(tab,tabLeavesOut,threshold);
		return res;
	}
	
	public static ImagePlus computeMire(ImagePlus imgIn) {
		ImagePlus img=new Duplicator().run(imgIn,1,1,1,1,1,1);
		IJ.run(img, "Median...", "radius=9 stack");
		return img;
	}
	
	public static ImagePlus indRuptureDownOfImageArrayDouble(ImagePlus []imgs,ImagePlus []maskLeavesOut,int minThreshold) {
		int xM=imgs[0].getWidth();
		int yM=imgs[0].getHeight();
		int zM=imgs[0].getStackSize();
		ImagePlus retInd=VitimageUtils.nullImage(imgs[0].duplicate());
		float[]valsInd;
		float[][]valsImg=new float[imgs.length][];
		float[][]valsMask=new float[imgs.length][];
		double[]valsToDetect;
		double[]valsToMask;
		for(int z=0;z<zM;z++) {
			valsInd=(float [])retInd.getStack().getProcessor(z+1).getPixels();
			for(int i=0;i<imgs.length;i++) {
				valsImg[i]=(float [])imgs[i].getStack().getProcessor(z+1).getPixels();
				valsMask[i]=(float [])maskLeavesOut[((i<2) ? 0 : i-1)].getStack().getProcessor(z+1).getPixels();
			}
			for(int x=0;x<xM;x++) {
				for(int y=0;y<yM;y++) {
					int last=0;
					valsToDetect=new double[imgs.length];
					valsToMask=new double[imgs.length];
					for(int i=0;i<imgs.length;i++) {
						valsToDetect[i]=valsImg[i][xM*y+x];
						valsToMask[i]=valsMask[i][xM*y+x];
						if(valsToMask[i]<1)last=i;
					}
					boolean blabla=false;
					if(x==377 && y==133)blabla=true;
					double[]newTab=new double[last+1];
					for(int i=0;i<=last;i++) {
						newTab[i]=valsToDetect[i];
					}
					int rupt=ruptureDetectionDown(newTab, minThreshold,blabla);
					if(blabla)System.out.println("Rupture="+rupt);
					valsInd[xM*y+x]=rupt; 
				}			
			}
		}
		return retInd;
	}
	
	public static ImagePlus getMenisque(ImagePlus img,boolean highRes) {
		//Calculer la difference entre une ouverture horizontale et une ouverture verticale
		int factor=highRes ? 4 : 1;
		ImagePlus img2=MorphoUtils.dilationLine2D(img, 8*factor,false);
		img2=MorphoUtils.erosionLine2D(img2, 8*factor,false);
		ImagePlus img3=MorphoUtils.dilationLine2D(img, 8*factor,true);
		img3=MorphoUtils.erosionLine2D(img3, 8*factor,true);
		ImagePlus img4=VitimageUtils.makeOperationBetweenTwoImages(img2, img3, 4, true);
		
		//Ouvrir cette difference, la binariser et la dilater, puis selectionner la plus grande CC > 70, et la dilater un peu
		ImagePlus img5=MorphoUtils.dilationLine2D(img4, 15*factor,true);
		img5=MorphoUtils.erosionLine2D(img5, 15*factor,true);
		ImagePlus img6=VitimageUtils.thresholdImage(img5, 20, 500);
		img6=MorphoUtils.dilationLine2D(img6, 50, true);
		img6=MorphoUtils.dilationLine2D(img6, 2*factor,true);
		img6=MorphoUtils.dilationLine2D(img6, 1*factor,false);
		img6=VitimageUtils.connexeBinaryEasierParamsConnexitySelectvol(img6, 4, 1);
		img6=MorphoUtils.dilationLine2D(img6, 3*factor,false);
		IJ.run(img6,"8-bit","");
		return img6;
	}
		
	public static ImagePlus getInterestAreaMask(ImagePlus img,boolean highRes) {
		ImagePlus img2=getMenisque(img,highRes);
		ImagePlus img3=VitimageUtils.invertBinaryMask(img2);
		ImagePlus img4=VitimageUtils.connexeBinaryEasierParamsConnexitySelectvol(img3, 4, 1);
		IJ.run(img4,"8-bit","");
		return img4;
	}

	public static double meanBetweenIncludedIndices(double[]tab,int ind1,int ind2) {
		double tot=0;
		for(int i=ind1;i<=ind2;i++)tot+=tab[i];
		return (tot/(ind2-ind1+1));
	}
				
	public static int ruptureDetectionDown(int[]vals,double threshold,boolean blabla) {
		double[]d=new double[vals.length];
		for(int i=0;i<d.length;i++)d[i]=vals[i];
		return ruptureDetectionDown(d,threshold,blabla);
	}

	//Return the index which is the first point of the second distribution
	public static int ruptureDetectionDown(double[]vals,double threshold,boolean blabla) {
		int indMax=0;
		double diffMax=-10000000;
		int N=vals.length;
		for(int i=1;i<N;i++) {
			double m1=meanBetweenIncludedIndices(vals, 0, i-1);
			double m2=meanBetweenIncludedIndices(vals, i, N-1);
			double diff=m1-m2;
			if(diff>diffMax) {
				indMax=i;
				diffMax=diff;
			}
			if(blabla) {
//				System.out.println("Apres i="+i+" : indMax="+indMax+" diffMax="+diffMax+" et on avait m1="+m1+" et m2="+m2);
			}
		}		
		return (diffMax>threshold ? indMax : 0);
	}
	

}



