package fr.cirad.image.rsttest;

import java.io.File;

import fr.cirad.image.common.VitimageUtils;
import ij.IJ;
import ij.ImagePlus;

public class Utils {
	public static void main(String[]args) {
		prepareTestDataSet();
	}
	public static void prepareTestDataSet() {
		String inDir="/Donnees/DD_CIRS626_DATA/Racines/Data_BPMP/Second_dataset_2021_07/Data_Tidy/IMG/ML1/";
		String outDir="/home/rfernandez/Bureau/Releases/RootSystemTracker_DOI/Data/Input";
		String outCodeSpec="Serie_1";

		int nTimes=22;
		new File(outDir+"/"+outCodeSpec).mkdirs();
		for(int i=1;i<=nTimes;i++) {
			System.out.println(inDir+"Seq_"+i+"/ML1_Seq_"+i+"_Boite_00005.jpg");
			ImagePlus img=IJ.openImage(inDir+"Seq_"+i+"/ML1_Seq_"+i+"_Boite_00005.jpg");
			img=VitimageUtils.resize(img, img.getWidth()/4, img.getHeight()/4, img.getStackSize());
			IJ.saveAsTiff(img,outDir+"/"+outCodeSpec+"/img_"+i+".jpg");
		}
	}
}
