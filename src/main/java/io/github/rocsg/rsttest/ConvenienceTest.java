package io.github.rocsg.rsttest;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.Duplicator;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import io.github.rocsg.rstutils.MorphoUtils;

public class ConvenienceTest {
	
	public static void main(String[]args) {
		ImageJ ij=new ImageJ();
		int i=55;
		ImagePlus img=IJ.openImage("/home/rfernandez/Bureau/A_Test/RootSystemTracker/TestSplit/Processing_of_TEST1230403-SR-split/230403SR0"+i+"/11_stack.tif");
		img=new Duplicator().run(img,1,1,30,30,1,1);
		img.show();
		ImagePlus img2=MorphoUtils.dilationCircle2D(img, 2);
		img2.show();
		ImagePlus img3=MorphoUtils.dilationLine2D(img2, 15, true);
		img3.show();
		ImagePlus img4=VitimageUtils.thresholdImage(img3, 152, 256);
		img4.show();
		ImagePlus imgTrench=VitimageUtils.getBinaryMaskUnary(img4, 127);
		ImagePlus imgRest=VitimageUtils.invertBinaryMask(imgTrench);
		ImagePlus rest=VitimageUtils.makeOperationBetweenTwoImages(img, imgRest, 2, false);
		ImagePlus trench=VitimageUtils.makeOperationBetweenTwoImages(img3, imgTrench, 2, false);
		ImagePlus glob=VitimageUtils.makeOperationBetweenTwoImages(rest, trench, 1, false);
		glob.show();
	}
	public static void main2(String[]args) {
		ImageJ ij=new ImageJ();
		int N=80-55;
		ImagePlus []tab=new ImagePlus[N];
		for(int i=55;i<80;i++) {
			System.out.println(i);
			ImagePlus img=IJ.openImage("/home/rfernandez/Bureau/A_Test/RootSystemTracker/TestSplit/Processing_of_TEST1230403-SR-split/230403SR0"+i+"/11_stack.tif");
			tab[i-55]=new Duplicator().run(img,1,1,1,1,1,1);
		}
		ImagePlus assemble=VitimageUtils.slicesToStack(tab);
		assemble.show();
	}
		
}
