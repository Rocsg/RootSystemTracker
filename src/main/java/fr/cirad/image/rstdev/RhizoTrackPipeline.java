package fr.cirad.image.rstdev;

//Import from std libs
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.spi.FileSystemProvider;

//Import from ImageJ libs
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.Duplicator;
import ij.plugin.RGBStackMerge;
import ij.plugin.frame.PlugInFrame;

import org.jgrapht.GraphPath;
//Import from Jgrapht libs
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

import fr.cirad.image.common.Bord;
import fr.cirad.image.common.DouglasPeuckerSimplify;
import fr.cirad.image.common.Pix;
//Import from Fijiyama libs
import fr.cirad.image.common.Timer;
import fr.cirad.image.common.VitiDialogs;
import fr.cirad.image.common.VitimageUtils;
import fr.cirad.image.fijiyama.RegistrationAction;
import fr.cirad.image.registration.BlockMatchingRegistration;
import fr.cirad.image.registration.ItkTransform;
import fr.cirad.image.registration.Transform3DType;
import fr.cirad.image.rsml.Node;
import fr.cirad.image.rsml.Root;
import fr.cirad.image.rsml.RootModel;
//import fr.cirad.image.rsmlviewer.RootModel;
import fr.cirad.image.rstutils.MorphoUtils;
import fr.cirad.image.topologicaltracking.CC;
import fr.cirad.image.topologicaltracking.ConnectionEdge;

//Work in progress : parameters list of the pipeline
//XMINCROP, YMINCROP, DXCROP, DYCROP : define the area of interest in the image, excluding the petri box, and including all root parts    
//MAXLINEAR                          : a scale factor for the registration. Registration starts with a subsampling factor of 2^4=16     
//isRootnavData                      : when set to true, pipeline parameters are adapted to process such data    
//:    
//:    
//:    

//TODO : when registering, do the crop partly before, then registration, then partly after.
//			In order to avoid to have bogus reconstructed BG at the extremities, what can have an impact
//TODO : times all is an image name where distance is displayed in pixel. It should be avoided
//TODO : list the steps and the step parameters settings in order to identify parameterizable operations, in order to adapt to new datasets



public class RhizoTrackPipeline extends PlugInFrame{	
	public static int XMINCROP=122;
	public static int YMINCROP=152;
	public static int DXCROP=1348;
	public static int DYCROP=1226;
	public static int MAXLINEAR=4;

	private static final long serialVersionUID = 1L;
	static boolean isRootnavData=false;
	public boolean testing=false;
	public String inputDataDir="/home/rfernandez/Bureau/A_Test/RSML";
	public String processingDataDir="/home/rfernandez/Bureau/A_Test/RSML";
	private static double TOLERANCE_DISTANCE_TO_CENTRAL_LINE_FOR_DOUGLAS_SIMPLIFICATION=0.9;

	/*public static void setRootnavParams() {
		isRootnavData=true;
		XMINCROP=0;
		YMINCROP=0;
		DXCROP=1024;
		DYCROP=1024;
		MAXLINEAR=4;
		
	}
	*/
	static boolean debugGraphConstruction=false;
	public static String mainDataDir="/home/rfernandez/Bureau/A_Test/RSML";

	public static void thatStuff() {
		int ml=1;
		ArrayList<Double>li=new ArrayList<Double>();
		for(int boi=1;boi<=18;boi++) {
			String boite=(  (boi<10) ? ("0000"+boi) : ( (boi<100) ? ("000"+boi) : ("00"+boi) ) );
			System.out.println("Processing ML"+ml+"_Boite_"+boite);
			IJ.log("Processing ML"+ml+"_Boite_"+boite);
			String imgName="ML"+ml+"_Boite_"+boite;
			RootModel rmGot=RootModel.RootModelWildReadFromRsml(mainDataDir+"/4_RSML_BACKTRACK/"+imgName+".rsml");
			String []files=new File( mainDataDir+"/Retour Amandine/"+imgName+"/Expertized_models/").list();
			Arrays.sort(files);
			RootModel rmExpert=RootModel.RootModelWildReadFromRsml(mainDataDir+"/Retour Amandine/"+imgName+"/Expertized_models/"+files[files.length-1]);
			Root[]rPred=rmGot.getPrimaryRoots();
			Root[]rExp=rmExpert.getPrimaryRoots();
			for(int i=0;i<5;i++){
				System.out.println(rPred[i].firstNode.x+","+rPred[i].firstNode.y+" and "+rExp[i].firstNode.x+","+rExp[i].firstNode.y);
				li.add(1.0*rExp[i].firstNode.y-rPred[i].firstNode.y);
			}
		}
		double[]tab=VitimageUtils.statistics1D(li);
		System.out.println(tab[0]+","+tab[1]);
	}
	
	
	public static void main(String[]args) {
		ImageJ ij=new ImageJ();
		String imgName2="ML1_Boite_00021";
		RhizoTrackPipeline test2= new RhizoTrackPipeline("");
		//test2.runDateEstimation(imgName2);
		//test2.buildAndProcessGraph(imgName2);//60s
		//test2.computeTimes(imgName2); //9s
		backTrackPrimaries(imgName2);
		System.exit(0);
		int ml=1;
		for(int boi=21;boi<=36;boi++) {
			String boite=(  (boi<10) ? ("0000"+boi) : ( (boi<100) ? ("000"+boi) : ("00"+boi) ) );
			String imgName="ML"+ml+"_Boite_"+boite;
			backTrackPrimaries(imgName);
		}
		System.exit(0);
		thatStuff();
		RhizoTrackPipeline test= new RhizoTrackPipeline("");
		test.run("TEST_ON_RFERNAND");
	}

	public void run(String arg) {
		IJ.log("RUN");

		if(arg.equals("TEST_ON_RFERNAND")) {
			testing=false;
			for(int mli=1;mli<=1;mli++) {
				for(int boi=37;boi<=54;boi++) {
					debugGraphConstruction=false;
///					if(mli==5)setRootnavParams();				
					String boite=(  (boi<10) ? ("0000"+boi) : ( (boi<100) ? ("000"+boi) : ("00"+boi) ) );
					String ml=""+(mli==5 ? "RootNav" : mli);
					System.out.println("Processing ML"+ml+"_Boite_"+boite);
					IJ.log("Processing ML"+ml+"_Boite_"+boite);

					
					Timer t=new Timer();
					t.print("Start"+mli+"-"+boi);
					//runImportSequences(ml,boite);
					String imgName="ML"+ml+"_Boite_"+boite;
					//runRegisterSequences(imgName);
					//runComputeMaskAndRemoveLeaves(imgName);//9 secondes
					//computeML(imgName);
					runDateEstimation(imgName);//2.61 secondes
					//buildAndProcessGraph(imgName);//60s
					//computeTimes(imgName); //9s
					backTrackPrimaries(imgName);
//					exportToExpertize(imgName);
					t.print("Stop"+mli+"-"+boi);
				}
			}
			IJ.showMessage("Done");
			System.exit(0);
		}
		else {
			inputDataDir=VitiDialogs.chooseDirectoryUI("Choose an input directory. Inside, one dir per box, each dir contains a sequence of images which is a temporal series)","OK");
			processingDataDir=VitiDialogs.chooseDirectoryUI("Choose a directory where to process data and to store results.","OK");
			if(!verifyDirs())return;
			
			
		}
	}
	
	public RhizoTrackPipeline() {		super("");}
	public RhizoTrackPipeline(String title) {		super(title);}

	
	
	
	
	/**Test sequences ***********************************************************************************************************************/	
	
	
	
	
	
	
	/** Main entry points --------------------------------------------------------------------------------------------------------------------------------------------------------*/
	public boolean verifyDirs() {
		if (!new File(inputDataDir).exists()) {IJ.showMessage("Wrong inputDataDir, does not exist : "+inputDataDir);return false;}
		if (!new File(processingDataDir).exists()) {IJ.showMessage("Wrong processingDataDir, does not exist : "+inputDataDir);return false;}
		return true;
	}
	
	
	public void runImportSequences(String ml, String boite) {
		ImagePlus img=importTimeLapseSerie(""+ml, boite,".jpg",null,false);
		String imgName="ML"+ml+"_Boite_"+boite;
		if(img!=null) {
			int X=img.getWidth();
			int Y=img.getHeight();
			int Z=img.getStackSize();
			System.out.print(" resize...");
			//VitimageUtils.adjustImageCalibration(img, new double[] {19,19,19}, "µm");
			//IJ.saveAsTiff(img, processingDataDir+"/0_Stacked_Highres/ML"+ml+"_Boite_"+boite);
			img=VitimageUtils.resize(img, X/4, Y/4, Z);
			VitimageUtils.adjustImageCalibration(img, new double[] {19*4,19*4,19*4}, "µm");
			IJ.saveAsTiff(img, processingDataDir+"/0_Stacked/"+imgName+".tif");
			}				
	}
			
	public void runRegisterSequences(String imgName) {
		ImagePlus []imgs=null;
		imgs=registerImageSequence(imgName,4,false);
		System.out.println("Did !");
		System.out.println("Saving as "+processingDataDir+"/1_Registered/"+imgName+".tif");
		IJ.saveAsTiff(imgs[1], processingDataDir+"/1_Registered/"+imgName+".tif");				
		if(imgs[0]!=null)	IJ.saveAsTiff(imgs[0], processingDataDir+"/1_Registered_High/"+imgName+".tif");				
	}

	public void runComputeMaskAndRemoveLeaves(String imgName) {
		boolean highRes=false;
		ImagePlus imgReg=IJ.openImage(processingDataDir+"/1_Registered"+(highRes ? "_High":"")+"/"+imgName+".tif");
		ImagePlus imgMask1=VitimageUtils.getBinaryMaskUnary(getInterestAreaMask(new Duplicator().run(imgReg,1,1,1,1,1,1),highRes),0.5);
		imgMask1.setDisplayRange(0, 1);
		IJ.saveAsTiff(imgMask1, processingDataDir+"/1_Mask_1/"+imgName+".tif");

		ImagePlus imgMaskN=VitimageUtils.getBinaryMaskUnary(getInterestAreaMask(new Duplicator().run(imgReg,1,1,imgReg.getStackSize(),imgReg.getStackSize(),1,1),highRes),0.5);
		imgMaskN.setDisplayRange(0, 1);
		IJ.saveAsTiff(imgMaskN, processingDataDir+"/1_Mask_N/"+imgName+".tif");

		ImagePlus imgMask2=	MorphoUtils.erosionCircle2D(imgMask1, 250*(highRes ? 4 : 1));
		imgMask2.setDisplayRange(0, 1);
		IJ.saveAsTiff(imgMask2, processingDataDir+"/1_Mask_Feuilles/"+imgName+".tif");		

		ImagePlus []imgsOut=removeLeavesFromSequence_v2(imgReg, imgMask1, imgMask2,highRes);
		imgsOut[0].setDisplayRange(0, 255);

		IJ.saveAsTiff(imgsOut[0],processingDataDir+"/1_Remove_Leaves/"+imgName+".tif");
		imgsOut[1].setDisplayRange(0, 1);
		IJ.saveAsTiff(imgsOut[1],processingDataDir+"/1_Mask_Of_Leaves/"+imgName+".tif");
	}	
	
/*
	public void computeML(String ml, String boite) {
		ImagePlus imgIn=IJ.openImage(processingDataDir+"/1_Registered/ML"+ml+"_Boite_"+boite+".tif");
		String clasifierPath="/home/rfernandez/Bureau/A_Test/RSML/N_Others/ML/classifier_v5.model";
		ImagePlus[]mlResult=new ImagePlus[imgIn.getStackSize()];
		int N=imgIn.getStackSize();
		ImagePlus[]imgTab=VitimageUtils.stackToSlices(imgIn);
		WekaSegmentation segmentator = new WekaSegmentation( imgTab[0] );
		segmentator.loadClassifier(clasifierPath);
		Timer t=new Timer();
		for(int i=0;i<N;i++) {
			System.out.print("Processing image "+(i+1)+" / "+N);
			mlResult[i]=segmentator.applyClassifier(imgTab[i],16,true);
			System.out.println(" Ok."+t);			
		}
		ImagePlus mlRes=VitimageUtils.slicesToStack(mlResult);
		IJ.saveAsTiff(mlRes,processingDataDir+"/2_ML/ML"+ml+"_Boite_"+boite+".tif");
	}
	*/
	
	
	public void runDateEstimation(String imgName) {
		System.out.println(processingDataDir+"/1_Remove_Leaves/"+imgName+".tif");
		ImagePlus imgIn=IJ.openImage(processingDataDir+"/1_Remove_Leaves/"+imgName+".tif");
		ImagePlus imgMask1=IJ.openImage(processingDataDir+"/1_Mask_1/"+imgName+".tif");
		ImagePlus imgMaskN=IJ.openImage(processingDataDir+"/1_Mask_N/"+imgName+".tif");
		ImagePlus imgMaskOfLeaves=IJ.openImage(processingDataDir+"/1_Mask_Of_Leaves/"+imgName+".tif");

		ImagePlus mire=computeMire(imgIn);
		imgIn=VitimageUtils.addSliceToImage(mire, imgIn);
		if(imgIn==null)return;
		int threshRupt=25;
		int threshSlope=10;
		ImagePlus imgOut=projectTimeLapseSequenceInColorspaceCombined(imgIn, imgMask1,imgMaskN,imgMaskOfLeaves,threshRupt,threshSlope);
		imgOut=VitimageUtils.makeOperationBetweenTwoImages(imgOut, imgMaskN, 2, true);
		ImagePlus img2=VitimageUtils.thresholdImage(imgOut, 0.5, 100000);
		ImagePlus img3=img2.duplicate();
		//img2=MorphoUtils.dilationCircle2D(img2, 1);
		//img2=MorphoUtils.erosionCircle2D(img2, 1);
/*		img2.show();
		img3.show();
		VitimageUtils.waitFor(500000);*/
		img2=VitimageUtils.connexeNoFuckWithVolume(img2, 1, 10000, 2000, 1E10, 4, 0, true);
		img2=VitimageUtils.thresholdImage(img2, 0.5, 1E8);
		img2=VitimageUtils.getBinaryMaskUnary(img2, 0.5);
		IJ.run(img2,"8-bit","");
		imgOut=VitimageUtils.makeOperationBetweenTwoImages(imgOut, img2, 2, true);
		IJ.run(imgOut,"Fire","");
		imgOut.setDisplayRange(-1, 22);
		IJ.saveAsTiff(imgOut, processingDataDir+"/2_Date_maps/"+imgName+".tif");
	}

	//public void runBuildGraphs(String imgName) {
		//ImagePlus imgOut=buildAndProcessGraph(""+ml,boite);
		//IJ.saveAsTiff(imgOut, processingDataDir+"/3_Graphs/ML"+ml+"_Boite_"+boite);
	//}
	
	


	
	/**
	 * Remove the falling stem of arabidopsis from a time lapse sequence imgMask contains all the root system, and imgMask2 only the part that cannot have a arabidopsis stem (the lower part)
	 * 	 */
	public ImagePlus[] removeLeavesFromSequence(ImagePlus imgInit,ImagePlus imgMaskInit,ImagePlus imgMask2Init,boolean highres) {
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
	
	//In this new version, we replace big elements of object with what was at last image
	public ImagePlus[] removeLeavesFromSequence_v2(ImagePlus imgInit,ImagePlus imgMaskRoot,ImagePlus imgMask2Init,boolean highres) {
		int factor=highres ? 4:1;
		ImagePlus[]tabInit=VitimageUtils.stackToSlices(imgInit);
		ImagePlus[]tabTot=VitimageUtils.stackToSlices(imgInit);
		ImagePlus[]tabMaskOut=VitimageUtils.stackToSlices(imgInit);
		ImagePlus[]tabMaskIn=VitimageUtils.stackToSlices(imgInit);
		tabMaskOut[0]=VitimageUtils.nullImage(tabMaskOut[0]);
		tabMaskIn[0]=VitimageUtils.invertBinaryMask(tabMaskOut[0]);
		ImagePlus replacement=VitimageUtils.nullImage(tabInit[0]);
		
		ImagePlus imgMaskAerialNot=VitimageUtils.invertBinaryMask(imgMask2Init);
		for(int z=1;z<tabInit.length;z++) {
			//Get the mask of the big elements of object under the menisque
			ImagePlus img=VitimageUtils.makeOperationBetweenTwoImages(tabInit[z], imgMaskRoot, 2, true);
			img=MorphoUtils.dilationCircle2D(img, 2*factor);
			img=VitimageUtils.gaussianFiltering(img, 3*factor, 3*factor, 0);
			ImagePlus biggas=VitimageUtils.thresholdImage(img, -100, 120);
			
			tabMaskOut[z]=VitimageUtils.binaryOperationBetweenTwoImages(imgMaskAerialNot.duplicate(), biggas, 2);
			tabMaskOut[z]=VitimageUtils.binaryOperationBetweenTwoImages(imgMaskRoot, tabMaskOut[z], 2);
			tabMaskOut[z]=MorphoUtils.dilationCircle2D(tabMaskOut[z], 2*factor);
			tabMaskOut[z].setDisplayRange(0, 1);
			tabMaskOut[z].setTitle(" "+z);
			
			//Combine this mask with the one of the previous image
			tabMaskOut[z]=VitimageUtils.binaryOperationBetweenTwoImages(tabMaskOut[z-1], tabMaskOut[z], 1);
			tabMaskOut[z]=VitimageUtils.connexe2dNoFuckWithVolume(tabMaskOut[z], 0.5, 1000, 1000, 1000000, 4, 0, true);
			tabMaskOut[z]=VitimageUtils.thresholdImage(tabMaskOut[z], 0.5, 1000);
			tabMaskOut[z]=VitimageUtils.getBinaryMaskUnary(tabMaskOut[z], 0.5);		
			
			tabMaskIn[z]=VitimageUtils.invertBinaryMask(tabMaskOut[z]);
			ImagePlus maskNewArea=VitimageUtils.getBinaryMaskUnary(VitimageUtils.binaryOperationBetweenTwoImages(tabMaskOut[z], tabMaskOut[z-1], 4), 0.5);		
			replacement=VitimageUtils.addition(replacement, VitimageUtils.multiply(maskNewArea,tabInit[z-1],false),false);

			ImagePlus imgPart1=VitimageUtils.makeOperationBetweenTwoImages(tabMaskIn[z], tabInit[z], 2, false);
			ImagePlus imgPart2=VitimageUtils.makeOperationBetweenTwoImages(tabMaskOut[z], replacement, 2, false);
			tabTot[z]=VitimageUtils.makeOperationBetweenTwoImages(imgPart1, imgPart2, 1, false);
		}
		ImagePlus img1=VitimageUtils.slicesToStack(tabTot);
		img1.setDisplayRange(0, 255);
		ImagePlus img2=VitimageUtils.slicesToStack(tabMaskOut);
		img2.setDisplayRange(0, 1);
		return new ImagePlus [] {img1,img2};
	}
			
	
	public  void exportToExpertize(String imgName) {
		String exportDir=processingDataDir+"/Processing_by_box";
		new File(exportDir).mkdir();
		String mlDir=exportDir+"/"+imgName;
		new File(mlDir).mkdir();

		//Copy rsml image
		Path source=FileSystems.getDefault().getPath(new File(processingDataDir+"/4_RSML/"+imgName+".rsml").getAbsolutePath());
		Path target=FileSystems.getDefault().getPath(new File(mlDir,"4_2_Model.rsml").getAbsolutePath());
		try {Files.copy(source,target,StandardCopyOption.REPLACE_EXISTING);} catch (IOException e) {e.printStackTrace();}
		
		//Copy rsml image
		source=FileSystems.getDefault().getPath(new File(processingDataDir+"/1_Registered/"+imgName+".tif").getAbsolutePath());
		target=FileSystems.getDefault().getPath(new File(mlDir,"1_5_RegisteredSequence.tif").getAbsolutePath());
		try {Files.copy(source,target,StandardCopyOption.REPLACE_EXISTING);} catch (IOException e) {e.printStackTrace();}
	}
	
	public  void computeTimes(String imgName) {
		ImagePlus mask=IJ.openImage(processingDataDir+"/1_Mask_1/"+imgName+".tif");
		ImagePlus reg=IJ.openImage(mainDataDir+"/1_Registered/"+imgName+".tif");
		reg=new Duplicator().run(reg,1,1,1,1,1,1);
		ImagePlus dates=IJ.openImage(processingDataDir+"/2_Date_maps/"+imgName+".tif");
		mask=MorphoUtils.dilationCircle2D(mask, 9);
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph=RegionAdjacencyGraphUtils.readGraphFromFile(processingDataDir+"/3_Graphs_Ser/"+imgName+".ser");
		ImagePlus distOut=MorphoUtils.getDistOut(dates,false);

		RootModel rm=RegionAdjacencyGraphUtils.refinePlongementOfCCGraph(graph,distOut,TOLERANCE_DISTANCE_TO_CENTRAL_LINE_FOR_DOUGLAS_SIMPLIFICATION);
		rm.cleanWildRsml();
		rm.resampleFlyingRoots();
		rm.writeRSML3D(processingDataDir+"/4_RSML/"+imgName+".rsml", "",true,false);
		backTrackPrimaries(processingDataDir+"/4_RSML/"+imgName+".rsml",processingDataDir+"/4_RSML_BACKTRACK/"+imgName+".rsml",mask,reg,TOLERANCE_DISTANCE_TO_CENTRAL_LINE_FOR_DOUGLAS_SIMPLIFICATION);
		rm=RootModel.RootModelWildReadFromRsml(processingDataDir+"/4_RSML_BACKTRACK/"+imgName+".rsml");
		ImagePlus skeletonTime=RegionAdjacencyGraphUtils.drawDistanceOrTime(dates,graph,false,true,3);
		ImagePlus skeletonDay=RegionAdjacencyGraphUtils.drawDistanceOrTime(dates,graph,false,true,2);
		ImagePlus allTimes=RegionAdjacencyGraphUtils.drawDistanceOrTime(dates,graph,false,false,1);
		//rm.createGrayScaleImage(skeletonTime,0,false,true,1).show(); 

		

	//	rm.writeRSML3D(processingDataDir+"/4_RSML/"+imgName+".rsml","TEST",true);
		System.out.println("H3");

		if(debugGraphConstruction) {
			ImagePlus timeRSMLimg=createTimeSequenceSuperposition(imgName,rm,skeletonTime,false);
			IJ.saveAsTiff(timeRSMLimg, processingDataDir+"/4_RSML_img/"+imgName+".tif");
		}
		skeletonDay.setDisplayRange(0, 23);
		skeletonTime.setDisplayRange(0, 23);
		allTimes.setDisplayRange(0, 23);
		IJ.saveAsTiff(skeletonTime, processingDataDir+"/4_Times_skeleton/"+imgName+".tif");
		IJ.saveAsTiff(skeletonTime, processingDataDir+"/4_Times_skeleton/"+imgName+".tif");
		IJ.saveAsTiff(skeletonDay, processingDataDir+"/4_Day_skeleton/"+imgName+".tif");
		IJ.saveAsTiff(allTimes, processingDataDir+"/4_Times/"+imgName+".tif");
		System.out.println("H4");
	}
	
	public ImagePlus createTimeSequenceSuperposition(String imgName,RootModel rm,ImagePlus refSize,boolean highRes){
		ImagePlus imgReg=(highRes) ? IJ.openImage(processingDataDir+"/1_Registered"+(highRes ? "_High" : "")+"/"+imgName+".tif") :  IJ.openImage(processingDataDir+"/1_Registered/"+imgName+".tif");
		ImagePlus[]tabRes=VitimageUtils.stackToSlices(imgReg);
		Timer t=new Timer();
		for(int i=0;i<tabRes.length;i++) {
			tabRes[i]=VitimageUtils.resize(tabRes[i], tabRes[i].getWidth()*2, tabRes[i].getHeight()*2, 1);
			ImagePlus imgRSML=rm.createGrayScaleImageWithTime(tabRes[i],2,false,(i+1),true,new boolean[] {true,true,true,false,true},new double[] {highRes ? 6 : 2,highRes ? 4 : 2});
			tabRes[i]=RGBStackMerge.mergeChannels(new ImagePlus[] {tabRes[i],imgRSML}, false);
			IJ.run(tabRes[i],"RGB Color","");
		}
		//ImagePlus res=VitimageUtils.slicesWithChannelsToStackWithChannels(tabRes);
		ImagePlus res=VitimageUtils.slicesToStack(tabRes);
		return res;
	}

	
	public static void backTrackPrimaries(String imgName) {
		ImagePlus mask=IJ.openImage(mainDataDir+"/1_Mask_1/"+imgName+".tif");
		ImagePlus reg=IJ.openImage(mainDataDir+"/1_Registered/"+imgName+".tif");
		reg=new Duplicator().run(reg,1,1,1,1,1,1);
		mask=MorphoUtils.dilationCircle2D(mask, 9);
		backTrackPrimaries(mainDataDir+"/4_RSML/"+imgName+".rsml",mainDataDir+"/4_RSML_BACKTRACK/"+imgName+".rsml",mask,reg,TOLERANCE_DISTANCE_TO_CENTRAL_LINE_FOR_DOUGLAS_SIMPLIFICATION);
		ImagePlus imgModelAfter=RootModel.createSuperpositionTimeLapseFromPath(mainDataDir+"/1_Registered/"+imgName+".tif",mainDataDir+"/4_RSML_BACKTRACK/"+imgName+".rsml");
		ImagePlus imgModelInit=RootModel.createSuperpositionTimeLapseFromPath(mainDataDir+"/1_Registered/"+imgName+".tif",mainDataDir+"/4_RSML/"+imgName+".rsml");
		String[]paths=new File(mainDataDir+"/Retour Amandine/"+imgName+"/Expertized_models/").list();
		Arrays.sort(paths);
		String filePath=mainDataDir+"/Retour Amandine/"+imgName+"/Expertized_models/"+paths[paths.length-1];
		ImagePlus imgModelExpert=RootModel.createSuperpositionTimeLapseFromPath(mainDataDir+"/1_Registered/"+imgName+".tif",filePath);
		imgModelInit.setTitle("Init");
		imgModelAfter.setTitle("After");
		imgModelExpert.setTitle("Expert");
		//imgModelInit.show();
		//imgModelAfter.show();
		//imgModelExpert.show();
	}	

	
	public static void backTrackPrimaries(String pathToInputRsml,String pathToOutputRsml,ImagePlus mask,ImagePlus imgRegT0,double toleranceDistToCentralLine) {
		RootModel rmInit=RootModel.RootModelWildReadFromRsml(pathToInputRsml);
		Root[]prRoots=rmInit.getPrimaryRoots();
		int X=mask.getWidth();
		int Y=mask.getHeight();
		int xTolerance=X/20;

		for(Root r : prRoots) {
			//Identify the first coordinates
			Node oldFirst=r.firstNode;
			int xMid=(int) oldFirst.x;
			int yMid=(int) oldFirst.y;
		
			//Identify the mean height of region to attain in this area
			int upperPix=0;
			for(int i=yMid;i>=0;i--) {
				if(mask.getPixel(xMid, i)[0]>0)upperPix=i;
			}
			//TODO
			upperPix-=10;
			upperPix=0;
			if(upperPix<0)upperPix=0;
			
			//Extract a rectangle around the first coordinate at time 0
			ImagePlus imgExtractMask=VitimageUtils.cropImage(mask, xMid-xTolerance, upperPix, 0, xTolerance*2+1, yMid-upperPix+1, 1);
			imgExtractMask.setDisplayRange(0, 1);
			ImagePlus imgExtract=VitimageUtils.cropImage(imgRegT0, xMid-xTolerance, upperPix, 0, xTolerance*2+1, yMid-upperPix+1, 1);
			
			//Extract a min djikstra path to this region
			GraphPath<Pix,Bord>graph=VitimageUtils.getShortestAndDarkestPathInImage(imgExtract,8,new Pix(xTolerance,0,0),new Pix (xTolerance,yMid-upperPix,0));
			List<Pix>liInit=graph.getVertexList();
			
			
			//Find in this path the last point that is not in the interest area
			int indFirst=0;
			for(int i=0;i<graph.getLength() ;i++) {
				Pix p=liInit.get(i);
//				System.out.println("Testing "+p+ " = "+imgExtractMask.getPixel(p.x, p.y)[0]);
				if(imgExtractMask.getPixel(p.x, p.y)[0]<1)indFirst=i;
			}
			indFirst+=7;//Fit to the mean
			if(indFirst>=graph.getLength()-1)continue;//No path to add
			List<Pix>liSecond=new ArrayList<Pix>();
			for(int i=indFirst;i<graph.getLength() ;i++) {
				liSecond.add( liInit.get(i) );
			}
			
			//subsample the new path
			List<Integer>liNull=new ArrayList<Integer>();
			List<Pix>list= DouglasPeuckerSimplify.simplify(liSecond,liNull ,toleranceDistToCentralLine);
			list.remove(list.size()-1);
			System.out.println("After second step");
			int x0=xMid-xTolerance;
			int y0=upperPix;
			
			//Insert the corresponding coordinates update time value along the root		
			Pix p=list.get(0);
			Node n=new Node(p.x+x0,p.y+y0,0,null,false);
			r.firstNode=n;
			for(int i=1;i<list.size()-1;i++) {				
				p=list.get(i);
				Node n2=new Node(p.x+x0,p.y+y0,0.01f,n,true);
				n=n2;
			}
			n.child=oldFirst;
			oldFirst.parent=n;
			r.updateNnodes();
			r.computeDistances();
			r.resampleFlyingPoints();
		}
		rmInit.writeRSML3D(pathToOutputRsml, "", true,false);
	}

	
	
	/**
    STEP 00 : Import a 2D time-lapse series as a 3D volume (Time=Z axis, from 0 to N-1)
    If no dataDir is given, open the image into rfernandez's DATA drive
    */
	public ImagePlus importTimeLapseSerie(String ml, String boite,String extension,String dataDir,boolean verbose) {
		String hardDiskPath="/media/rfernandez/DATA_RO_A/Roots_systems/Data_BPMP/Second_dataset_2021_07/Data_Tidy/";
		if(new File(hardDiskPath).exists()) {}
		else {
			hardDiskPath="/Donnees/DD_CIRS626_DATA/Racines/Data_BPMP/Second_dataset_2021_07/Data_Tidy/";
			if(new File(hardDiskPath).exists()) {}
			else {IJ.showMessage("No hard disk found for source data. ");return null;}			
		}
		String dirData=( (dataDir==null) ? (processingDataDir+"/Data_Tidy/") : dataDir );
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
	public ImagePlus []registerImageSequence(String imgName,int additionnalIterationsUsingMeanImage,boolean viewRegistrations) {
		boolean makeHighRes=false;
		ImagePlus mask=IJ.openImage(processingDataDir+"/N_Others/maskNewLargerLong.tif");
		ImagePlus imgInit2=IJ.openImage(processingDataDir+"/0_Stacked/"+imgName+".tif");
		ImagePlus imgInit2High=null;
		if(makeHighRes)imgInit2High=IJ.openImage(processingDataDir+"/0_Stacked_Highres/"+imgName+".tif");
		ImagePlus imgInit=VitimageUtils.cropImage(imgInit2, XMINCROP,YMINCROP,0,DXCROP,DYCROP,imgInit2.getStackSize());
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
		if(!isRootnavData) for(int i=0;i<tabImgSmall.length;i++) {tabImgSmall[i]=VitimageUtils.cropImage(tabImgSmall[i], 0, 0,0, tabImgSmall[i].getWidth(),(tabImgSmall[i].getHeight()*2)/3,1);}

		
		
		
		boolean doit=true;
		if(doit) {
		//First step : daisy-chain rigid registration
		Timer t=new Timer();
		t.log("Start");
		for(int n=0;(n<N-1);n++) {
			t.log("n="+n);

			ItkTransform trRoot=null;
			RegistrationAction regAct=new RegistrationAction().defineSettingsFromTwoImages(tabImg[n],tabImg[n+1],null,false);
			regAct.setLevelMaxLinear(MAXLINEAR);			regAct.setLevelMinLinear(0);
			regAct.strideX=8;			regAct.strideY=8;			regAct.neighX=3;			regAct.neighY=3;
			regAct.selectLTS=90;
			if(isRootnavData)regAct.typeTrans=Transform3DType.TRANSLATION;
			regAct.setIterationsBM(8);
			BlockMatchingRegistration bm= BlockMatchingRegistration.setupBlockMatchingRegistration(tabImgSmall[n+1], tabImgSmall[n], regAct);
			if(isRootnavData)bm.minBlockVariance*=4;
			if(!isRootnavData)bm.mask=mask.duplicate();
		    bm.defaultCoreNumber=VitimageUtils.getNbCores();
		    bm.minBlockVariance/=4;
		    viewRegistrations=false;
			if(viewRegistrations) {
				bm.displayRegistration=2;
				bm.adjustZoomFactor(((512.0))/tabImg[n].getWidth());
				bm.flagSingleView=true;
			}
			bm.displayR2=false;
			if(isRootnavData)bm.nbIterations=8;
		    tr[n]=bm.runBlockMatching(trRoot, false);		
		    if(viewRegistrations) { bm.closeLastImages();
		    bm.freeMemory();
		    }
		    if(isRootnavData) {
				bm= BlockMatchingRegistration.setupBlockMatchingRegistration(tabImgSmall[n+1], tabImgSmall[n], regAct);
		    	bm.displayRegistration=2;
				bm.adjustZoomFactor(((512.0))/tabImg[n].getWidth());
				bm.flagSingleView=true;
		    	bm.levelMin=1;
		    	bm.levelMax=1;
		    	bm.transformationType=Transform3DType.RIGID;
			    tr[n]=bm.runBlockMatching(tr[n], false);		
		    }
		    	
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
	public ImagePlus projectTimeLapseSequenceInColorspaceCombined(ImagePlus imgSeq,ImagePlus interestMask1,ImagePlus interestMaskN,ImagePlus maskOfLeaves,int thresholdRupture,int thresholdSlope) {
		//imgSeq.show();
		IJ.run(imgSeq, "Gaussian Blur...", "sigma=0.8");
		//IJ.run(imgSeq, "Mean...", "radius=1 stack");
		ImagePlus result1=projectTimeLapseSequenceInColorspaceMaxRuptureDown(imgSeq,interestMask1,interestMaskN,maskOfLeaves,thresholdRupture);
		ImagePlus result2=projectTimeLapseSequenceInColorspaceMaxSlope(imgSeq,interestMask1,interestMaskN,thresholdSlope);
		result1.show();
		result1.setTitle("result1Rupt");
		result2.show();
		//VitimageUtils.waitFor(5000000);
		ImagePlus out=VitimageUtils.thresholdImage(result1, -0.5, 0.5);
		out=VitimageUtils.invertBinaryMask(out);
		ImagePlus mask=VitimageUtils.getBinaryMaskUnary(out, 0.5);
		result2=VitimageUtils.makeOperationBetweenTwoImages(result2, mask, 2, false);
		return result2;
	}

	/**
	STEP 03 : Compute graphs
	 */
	public  void buildAndProcessGraph(String imgName) {
		//Import and oversample image of dates
		ImagePlus imgDatesTmp=IJ.openImage(processingDataDir+"/2_Date_maps/"+imgName+".tif");
		RegionAdjacencyGraphUtils.buildAndProcessGraphStraight(imgDatesTmp,processingDataDir,imgName,debugGraphConstruction,true,debugGraphConstruction);
	}

	
	
	
	
	
	
	
	

	/// Other helpers /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public String findDateInCsv(String path,String pattern) {
		System.out.println(path);
		String[][]tab=VitimageUtils.readStringTabFromCsv(path);
		for(int i=0;i<tab.length;i++) {
			if(tab[i][0].contains(pattern))return tab[i][1];
		}
		return null;
	}

	public static String findFullDateInCsv(String path,String pattern) {
		String[][]tab=VitimageUtils.readStringTabFromCsv(path);
		for(int i=0;i<tab.length;i++) {
			if(tab[i][0].contains(pattern))return tab[i][1].replace(" ", "")+"_Hours="+Math.round(Long.parseLong(tab[i][2].replace(" ",""))/(3600*1000));
		}
		return null;
	}
	
	public ImagePlus indOfFirstLowerThan(ImagePlus []imgs,double threshold) {
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
	
	public ImagePlus indMaxOfImageArrayDouble(ImagePlus []imgs,int minThreshold) {
		int xM=imgs[0].getWidth();
		int yM=imgs[0].getHeight();
		int zM=imgs[0].getStackSize();
		ImagePlus retVal=VitimageUtils.nullImage(imgs[0].duplicate());
		ImagePlus retInd=VitimageUtils.nullImage(imgs[0].duplicate());
		float[]valsInd;
		float[]valsVal;
		float[]valsImg;
		//float[]valsProba;
		for(int z=0;z<zM;z++) {
			valsInd=(float [])retInd.getStack().getProcessor(z+1).getPixels();
			valsVal=(float [])retVal.getStack().getProcessor(z+1).getPixels();
			for(int i=0;i<imgs.length;i++) {
				valsImg=(float [])imgs[i].getStack().getProcessor(z+1).getPixels();
			//	valsProba=(float [])probaRoot[i].getStack().getProcessor(z+1).getPixels();
				for(int x=0;x<xM;x++) {
					for(int y=0;y<yM;y++) {
						if( ( (valsImg[xM*y+x])> valsVal[xM*y+x]) && ((valsImg[xM*y+x])> minThreshold)  ) {
							valsVal[xM*y+x]=valsImg[xM*y+x]; 
							valsInd[xM*y+x]=i; 
						}
					}
				}			
			}
		}
		return retInd;
	}

	public ImagePlus projectTimeLapseSequenceInColorspaceSimpler(ImagePlus imgSeq,int threshold) {
		ImagePlus imgSeq2=VitimageUtils.convertByteToFloatWithoutDynamicChanges(imgSeq);
		ImagePlus[]imgTab=VitimageUtils.stackToSlices(imgSeq2);
		ImagePlus res=indOfFirstLowerThan(imgTab,threshold);
		return res;
	}
	
	public ImagePlus projectTimeLapseSequenceInColorspaceMaxSlope(ImagePlus imgSeq,ImagePlus interestMask1,ImagePlus interestMaskN,int threshold) {
		int N=imgSeq.getStackSize();
		ImagePlus[]imgTab=VitimageUtils.stackToSlices(imgSeq);
		ImagePlus[]imgs=new ImagePlus[N];
		
		for(int i=0;i<N-1;i++) {
			imgs[i+1]=VitimageUtils.makeOperationBetweenTwoImages(imgTab[i], imgTab[i+1], 4, true);
			imgs[i+1]=VitimageUtils.makeOperationBetweenTwoImages(imgs[i+1],i==0 ? interestMask1:interestMaskN, 2, true);
		}
		imgs[0]=VitimageUtils.nullImage(imgs[1]);
		ImagePlus res=indMaxOfImageArrayDouble(imgs,threshold);
		return res;
	}

	public ImagePlus projectTimeLapseSequenceInColorspaceMaxRuptureDown(ImagePlus imgSeq,ImagePlus interestMask1,ImagePlus interestMaskN,ImagePlus maskOutLeaves,int threshold) {
		ImagePlus[]tab=VitimageUtils.stackToSlices(imgSeq);
		IJ.run(maskOutLeaves,"32-bit","");
		ImagePlus[]tabLeavesOut=VitimageUtils.stackToSlices(maskOutLeaves);
		for(int i=0;i<tab.length;i++) {
			tab[i]=VitimageUtils.makeOperationBetweenTwoImages(tab[i],i<2 ? interestMask1 : interestMaskN, 2, true);
		}
		ImagePlus res=indRuptureDownOfImageArrayDouble(tab,tabLeavesOut,threshold);
		return res;
	}
	
	public ImagePlus computeMire(ImagePlus imgIn) {
		ImagePlus img=new Duplicator().run(imgIn,1,1,1,1,1,1);
		IJ.run(img, "Median...", "radius=9 stack");
		//img=concatPartsOfImages(img,imgIn,"Y",0.5); In case of, when will be the time
		return img;
	}
	
	
	public static ImagePlus concatPartsOfImages(ImagePlus img1,ImagePlus img2,String axis,double ratio) {
		if(img1.getType()!=ImagePlus.GRAY8 || img1.getType()!=ImagePlus.GRAY8)return null;
		int X=img1.getWidth();
		int Y=img2.getHeight();
		ImagePlus imgOut=new Duplicator().run(img1);
		byte[] valsImg1=(byte[])img1.getStack().getProcessor(1).getPixels();
		byte[] valsImg2=(byte[])img2.getStack().getProcessor(1).getPixels();
		byte[] valsOut=(byte[])imgOut.getStack().getProcessor(1).getPixels();

		if(axis=="X") {
			int xMax=(int) (X*ratio);
			for(int x=0;x<X;x++) {
				for(int y=0;y<Y;y++) {
					valsOut[X*(y)+(x)]=(x<xMax) ? (valsImg1[X*(y)+(x)]) : (valsImg2[X*(y)+(x)]);
				}			
			}
		}
		else {
			int yMax=(int) (Y*ratio);
			for(int x=0;x<X;x++) {
				for(int y=0;y<Y;y++) {
					valsOut[X*(y)+(x)]=(y<yMax) ? (valsImg1[X*(y)+(x)]) : (valsImg2[X*(y)+(x)]);
				}			
			}
		}
		return imgOut;
	}
	
	
	public ImagePlus indRuptureDownOfImageArrayDouble(ImagePlus []imgs,ImagePlus []maskLeavesOut,int minThreshold) {
		
		
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
					valsInd[xM*y+x]=rupt; 
				}			
			}
		}
		return retInd;
	}
	
	public  ImagePlus getMenisque(ImagePlus img,boolean highRes) {
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
		
	public ImagePlus getInterestAreaMask(ImagePlus img,boolean highRes) {
		ImagePlus img2=getMenisque(img,highRes);
		ImagePlus img3=VitimageUtils.invertBinaryMask(img2);
		ImagePlus img4=VitimageUtils.connexeBinaryEasierParamsConnexitySelectvol(img3, 4, 1);
		IJ.run(img4,"8-bit","");
		return img4;
	}

	public double meanBetweenIncludedIndices(double[]tab,int ind1,int ind2) {
		double tot=0;
		for(int i=ind1;i<=ind2;i++)tot+=tab[i];
		return (tot/(ind2-ind1+1));
	}
				
	public int ruptureDetectionDown(int[]vals,double threshold,boolean blabla) {
		double[]d=new double[vals.length];
		for(int i=0;i<d.length;i++)d[i]=vals[i];
		return ruptureDetectionDown(d,threshold,blabla);
	}

	//Return the index which is the first point of the second distribution
	public int ruptureDetectionDown(double[]vals,double threshold,boolean blabla) {
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




