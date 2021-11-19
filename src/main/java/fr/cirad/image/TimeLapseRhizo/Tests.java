package fr.cirad.image.TimeLapseRhizo;

import fr.cirad.image.rsmlviewer.Root;
import fr.cirad.image.rsmlviewer.RootModel;
import ij.IJ;
import ij.ImagePlus;

public class Tests {
	
	public static void main(String[]args) {
		System.out.println("Echo world");
		RootModel rm=new RootModel();
		rm.rootList.add(new Root(0, null, false, null, rm, null));
		ImagePlus img=IJ.openImage("/home/rfernandez/Bureau/Sogho_joli.tif");
//		,double sigmaSmooth,boolean convexHull,boolean skeleton,int width
		rm.createGrayScaleImage(img,1,false,true,1).show(); 
	}
}
