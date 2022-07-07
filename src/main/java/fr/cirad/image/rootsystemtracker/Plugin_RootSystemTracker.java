package fr.cirad.image.rootsystemtracker;

import ij.IJ;
import ij.ImageJ;
import ij.plugin.frame.PlugInFrame;

public class Plugin_RootSystemTracker extends PlugInFrame{

	private static final long serialVersionUID = 1L;
	public boolean developerMode=false;
	public static String versionFlag="Handsome honeysuckle   2022-07-07 09:45";

	public static void main(String[]args) {
		ImageJ ij=new ImageJ();
		Plugin_RootSystemTracker pl=new Plugin_RootSystemTracker();
		pl.developerMode=true;		
		pl.run("");
	}

	public Plugin_RootSystemTracker()       {		super("");	 	}

	public void run(String arg) {
		startInitMenu();
//		startPlugin();
	}
	
	public void startInitMenu(){
		IJ.log("Starting RootSystemTracker version "+versionFlag);
	}
}
