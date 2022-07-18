package fr.cirad.image.rootsystemtracker;

import fr.cirad.image.common.Timer;
import fr.cirad.image.common.VitiDialogs;
import fr.cirad.image.common.VitimageUtils;
import java.io.File;
import ij.IJ;
import ij.ImageJ;
import ij.plugin.frame.PlugInFrame;

public class Plugin_RootSystemTracker extends PlugInFrame{

	private static final long serialVersionUID = 1L;
	public boolean developerMode=false;
	public String currentRstFlag="1.0";
	public static String versionFlag="Handsome honeysuckle   2022-07-07 09:45";
	public PipelineParamHandler pph;
	private Timer t;
	
	public static void main(String[]args) {
		ImageJ ij=new ImageJ();
		Plugin_RootSystemTracker pl=new Plugin_RootSystemTracker();
		//pl.developerMode=true;		
		pl.run("");
	}

	public Plugin_RootSystemTracker()       {		super("");	 	}

	public Plugin_RootSystemTracker(String arg)       {		super(arg);	 	}

	public void run(String arg) {
		startPipeline();
	}
	
	public void startPipeline(){
		IJ.log("Starting RootSystemTracker version "+versionFlag);

		String inputDir="";
		if(developerMode)inputDir="/home/rfernandez/Bureau/A_Test/RSML_For_DOI/Input_data";
		else inputDir=VitiDialogs.chooseDirectoryNiceUI("Choose data input dir", "OK");
		if((inputDir).contains(".csv")) {
			pph=new PipelineParamHandler(inputDir);
			PipelineActionsHandler.goOnExperiment(pph);
			return;
		}
		String outputDir=null;
		if(developerMode)outputDir="/home/rfernandez/Bureau/A_Test/RSML_For_DOI/Processing";
		else outputDir=VitiDialogs.chooseDirectoryNiceUI("Choose data output dir", "OK");

		if(new File(outputDir).list().length>0) {
			if(! new File(outputDir+"/Parameters.csv").exists()) {
				IJ.showMessage("Trying to use a non-empty output dir, containing no previous experiment.\nPlease provide empty dir or actual dir in use by RootSystemTracker");
				startPipeline();
			}
			else {
				if(! VitiDialogs.getYesNoUI("","Go on the experiment in "+outputDir+" ?"))return;
				pph=new PipelineParamHandler(inputDir,outputDir);
				PipelineActionsHandler.goOnExperiment(pph);
			}
		}
		else {
			pph=new PipelineParamHandler(inputDir,outputDir);
			prepareNewExperiment(inputDir,outputDir);
			PipelineActionsHandler.goOnExperiment(pph);			
		}
	}
	
	public void prepareNewExperiment(String inputDir,String outputDir) {
		new File(outputDir,"Processing").mkdirs();
		for(String imgName : pph.getImgNames())new File(outputDir,VitimageUtils.withoutExtension(imgName)).mkdirs();
	}
	
}
