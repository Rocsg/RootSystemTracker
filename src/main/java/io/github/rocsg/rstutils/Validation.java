package io.github.rocsg.rstutils;

import io.github.rocsg.fijiyama.common.VitimageUtils;
import io.github.rocsg.fijiyama.rsml.FSR;
import io.github.rocsg.fijiyama.rsml.RootModel;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.Duplicator;
import ij.plugin.frame.PlugInFrame;

public class Validation extends PlugInFrame{
	private static final long serialVersionUID = 1L;

	public Validation() {
		super("");
	}
	public Validation(String arg) {
		super(arg);
	}

	public static void main(String[]args) {
		ImageJ ij=new ImageJ();
		Validation val=new Validation();
		val.run("");
	}

	public void run() {
		run("");
	}
	
	public void run(String arg) {
		//fuck();
		visualValidationOfAnnotationFacingTimeDate();
	}
	
	
	//1
	
	public void fuck() {		
		String rep="/Donnees/DD_CIRS626_DATA/Racines/Data_Rootnav";
		int N=270;
		ImagePlus[]tab=new ImagePlus[N];
		for(int ii=0;ii<N;ii++) {
			System.out.println(ii);
			int i=ii+3526;
			String nbString=(i<100 ? "0" : "")+(i<100 ? "0" : "")+(i<10 ? "0" : "")+i;
//			System.out.println("Opening "+);
			tab[ii]=IJ.openImage(rep+"/"+nbString+"/image_"+nbString+".tif");
			tab[ii]=VitimageUtils.resize(tab[ii],800,800,1);
		}
		ImagePlus img=VitimageUtils.slicesToStack(tab);
		img.show();
		IJ.saveAsTiff(img, "/home/rfernandez/Bureau/testRoot.tif");
	}
	
	
	
	/** Comments to set*/
	public void visualValidationOfAnnotationFacingTimeDate() {
		String boite="00010";
		String ML="ML1";
		int seq=16;

		System.out.println("Part 1");
		//Open the target RSML
		//Open the corresponding source data
		//Fuse them in a view
		String dirSource="/Donnees/DD_CIRS626_DATA/Racines/Data_BPMP/Second_dataset_2021_07/Data_Tidy/";
		String pathRSML=dirSource+"RSML/"+ML+"/Seq_"+seq+"/"+ML+"_Seq_"+seq+"_Boite_"+boite+".rsml";
		String pathImg=dirSource+"IMG/"+ML+"/Seq_"+seq+"/"+ML+"_Seq_"+seq+"_Boite_"+boite+".jpg";
		ImagePlus imgSource=IJ.openImage(pathImg);
		int X=imgSource.getWidth();
		int Y=imgSource.getHeight();
		imgSource=VitimageUtils.resize(imgSource, (int)Math.round( X*166.6*0.96135/1000.0),  (int)Math.round(Y*166.6*0.96135/1000.0), 1);
//		imgSource.show();
		
		
		FSR sr=new FSR();
		System.out.println(pathRSML);
		RootModel rm=RootModel.RootModelWildReadAnnotationFromRsml(pathRSML);
		
		ImagePlus compositeSource=rm.createGrayScaleImage(imgSource,0,false,true,2); 
		compositeSource.setTitle("Composite source");
//		compositeSource.show();
		compositeSource=VitimageUtils.compositeRGBByte( imgSource, compositeSource,imgSource, 1.0, 0.5, 0);
		compositeSource.show();
		
		VitimageUtils.waitFor(58000000);
		System.out.println("Part 2");
		//Open the corresponding registered data
		//Open the computed time data
		String dirComputed="/home/rfernandez/Bureau/A_Test/RSML";
		String pathImgComputed=dirComputed+"/1_Registered/ML1_Boite_00001.tif";
		ImagePlus imgComputed=IJ.openImage(pathImgComputed);
		imgComputed=new Duplicator().run(imgComputed,1,1,seq,seq,1,1);
				
		String pathSkelComputed=dirComputed+"/4_Times_skeleton/"+ML+"_Boite_"+boite+".tif";
		ImagePlus skelComputed=IJ.openImage(pathSkelComputed);
		System.out.println(pathSkelComputed);
		VitimageUtils.printImageResume(skelComputed);
		skelComputed=VitimageUtils.thresholdImage(skelComputed, VitimageUtils.EPSILON,seq);
		skelComputed.setDisplayRange(0, 0.5);
		IJ.run(skelComputed,"8-bit","");
		
		ImagePlus compositeComputed=VitimageUtils.compositeNoAdjustOf(imgComputed, skelComputed);
		compositeComputed.setTitle("Composite computed");
		compositeComputed.show();
		System.out.println("Ok");
	}
	
}
