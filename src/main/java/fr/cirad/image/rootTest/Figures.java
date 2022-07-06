package fr.cirad.image.rootTest;

import fr.cirad.image.common.VitimageUtils;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;

/** This class contains helpers to produce the roottracker paper*/

public class Figures {
	public static void main(String []args) {
		ImageJ ij=new ImageJ();
		String imgName="/home/rfernandez/Bureau/A_Test/RSML/1_Registered/ML1_Boite_00002.tif";
		ImagePlus img=IJ.openImage(imgName);
		int tInit=0;
		int tLast=21;
		int N=tLast-tInit+1;
		ImagePlus[]imgs=new ImagePlus[N];
		ImagePlus[]slices=VitimageUtils.stackToSlices(img);
		for(int  t=tInit;t<=tLast;t++) {
			imgs[t-tInit]=slices[t];
		}
		ImagePlus res=VitimageUtils.meanOfImageArray(imgs);
		res.show();
	}
}
