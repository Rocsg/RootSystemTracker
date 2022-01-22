package fr.cirad.image.TimeLapseRhizo;

import fr.cirad.image.common.TransformUtils;
import fr.cirad.image.common.VitimageUtils;
import fr.cirad.image.rsmlviewer.Root;
import fr.cirad.image.rsmlviewer.RootModel;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;

public class Tests {
	
	public static void main(String[]args) {
		ImageJ ij=new ImageJ();
		ImagePlus img=IJ.openImage("/home/rfernandez/Bureau/Photo_identite_Sandrine_Carvalhosa.tif");
		System.out.println(VitimageUtils.isGe3d(img));
		
	}
}


