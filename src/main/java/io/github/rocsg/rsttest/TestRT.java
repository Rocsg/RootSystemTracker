package io.github.rocsg.rsttest;

import java.awt.Window;

import io.github.rocsg.fijiyama.common.VitimageUtils;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.text.TextPanel;

public class TestRT {
	public static void main(String[]args) {
		ImageJ ij=new ImageJ();
		ImagePlus img=IJ.openImage("/home/rfernandez/Bureau/test.tif");
		img.show();
		IJ.setTool("multipoint");

		//VitimageUtils.waitFor(5000);
		//wait ok
		
		IJ.run(img, "Measure", "");
		String[] s=IJ.getTextPanel().getText().split("\n");
		int nbPts=s.length-1;
		IJ.deleteRows(0, nbPts);
		IJ.selectWindow("Results"); 
		IJ.run("Close" );

		for(int i=0;i<nbPts;i++) {
			double x=Double.parseDouble ( s[i+1].split("\t")[5]);
			double y=Double.parseDouble ( s[i+1].split("\t")[6]);
			double z=Double.parseDouble ( s[i+1].split("\t")[7]);
		
			System.out.println("Point "+i+" : ("+x+","+y+","+z+")");
		}		
		
		VitimageUtils.waitFor(5000);

	}
}
