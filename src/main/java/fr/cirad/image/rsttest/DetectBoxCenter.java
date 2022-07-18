package fr.cirad.image.rsttest;

import fr.cirad.image.common.VitimageUtils;
import fr.cirad.image.rstutils.MorphoUtils;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.Duplicator;

public class DetectBoxCenter {

	public static void main(String[]args) {

		
		ImageJ ij=new ImageJ();
		for(int nb=3;nb<30;nb++) {
			String s="/home/rfernandez/Bureau/A_Test/RSML/0_Stacked/MLRootNav_Boite_000";
			String stot=s+(nb<10 ? "0" : "")+nb+".tif";
			ImagePlus imgStack=IJ.openImage(stot);
			for(int n=1;n<imgStack.getStackSize();n++) {
				System.out.println(stot+" "+n);
				ImagePlus img=new Duplicator().run(imgStack,1,1,n,n,1,1);
				//img.show();
				ImagePlus img2=MorphoUtils.dilationLine2D(img, 2, true);img2=MorphoUtils.erosionLine2D(img2, 15, true);//img2.show();VitimageUtils.waitFor(5000);
				img2=MorphoUtils.dilationLine2D(img2, 2, false);img2=MorphoUtils.erosionLine2D(img2, 15, false);img2=MorphoUtils.erosionCircle2D(img2, 3);
				img2=VitimageUtils.drawRectangleInImage(img2, 0, 0, 30, 1023, 0);
				img2=VitimageUtils.drawRectangleInImage(img2, 0, 0, 1023, 30, 0);
				img2=VitimageUtils.drawRectangleInImage(img2, 994, 0, 1023, 1023, 0);
				img2=VitimageUtils.drawRectangleInImage(img2, 0, 994, 1023, 1023, 0);
				ImagePlus con=VitimageUtils.connexe2dNoFuckWithVolume(img2, 150, 300, 10000, 1000000000, 4, 1, false);
//				img2.show();

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
				img.setRoi(new Roi(x0-450,y0-450,900,900));
				img.show();
				VitimageUtils.waitFor(1000);
				img.close();
			}
		}
	}
		
		
		
		
		
		
		
		
		
		
	
}

