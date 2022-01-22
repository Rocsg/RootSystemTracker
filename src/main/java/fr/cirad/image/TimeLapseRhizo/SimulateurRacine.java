package fr.cirad.image.TimeLapseRhizo;

import java.io.File;

import fr.cirad.image.common.VitimageUtils;
import fr.cirad.image.rsmlviewer.FSR;
import fr.cirad.image.rsmlviewer.RSML_reader;
import fr.cirad.image.rsmlviewer.Root;
import fr.cirad.image.rsmlviewer.RootModel;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;

public class SimulateurRacine {
	static String imgTest="/home/rfernandez/Bureau/A_Test/RSML/1_Registered/ML1_Boite_00002.tif";
	static String imgExtract="/home/rfernandez/Bureau/A_Test/Stage/Extract.tif";

	
	static int largeurTrait=2;
	int nombreDeCibles=1;
	boolean activerLesLaterales=true;
	double angleRacinePrimaire=0;
	double angleRacineSecondaire=0;
		
	
	//Quand on fait CTRL + F11 ça démarre cette fonction
	public static void main(String[]args) {
		ImageJ ij=new ImageJ();
		montrerImageCible();
		
	}
	
	
	
	//Une fonction de test
	public static void testCoucou() {
		IJ.showMessage("Coucou ! ");
	}
	
	
	public static void montrerImageCible() {
		ImagePlus img=IJ.openImage(imgTest);
		img=IJ.openImage(imgExtract);
		img.show();
	}
	
	

	
	
	
}
