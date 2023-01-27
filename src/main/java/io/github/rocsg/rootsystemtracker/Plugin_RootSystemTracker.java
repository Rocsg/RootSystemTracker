package io.github.rocsg.rootsystemtracker;

import io.github.rocsg.fijiyama.common.VitiDialogs;
import java.io.File;
import ij.IJ;
import ij.ImageJ;
import ij.plugin.frame.PlugInFrame;


/**
 * 
 * @author rfernandez
 * Paper release : 5f010e7
 */
public class Plugin_RootSystemTracker extends PlugInFrame{

	private static final long serialVersionUID = 1L;
	public boolean developerMode=false;
	public String currentRstFlag="1.0";
	public static String versionFlag="Handsome honeysuckle v1.5.0  2023-01-27 15:41";
	
	public static void main(String[]args) {
		ImageJ ij=new ImageJ();
		Plugin_RootSystemTracker pl=new Plugin_RootSystemTracker();
		pl.run("Dev ML1");
	}

	public Plugin_RootSystemTracker()       {		super("");	 	}

	public Plugin_RootSystemTracker(String arg)       {		super(arg);	 	}

	public void run(String arg) {
		IJ.log("Starting RootSystemTracker version 2.0.0alpha"+versionFlag);
		if(arg.equals("Dev ML1")) {
			String racine="/media/rfernandez/DATA_RO_A/Roots_systems/BPMP/Third_dataset_2022_11";
			String processingDir=racine+"/Processing/ML1";
			//startNewExperimentFromInventoryAndProcessingDir(inventoryDir,processingDir);
			goOnPipelineFromProcessingDir(processingDir);
		}
		else if(arg.equals("Dev ML2")) {
			String racine="/media/rfernandez/DATA_RO_A/Roots_systems/BPMP/Third_dataset_2022_11";
			String processingDir=racine+"/Processing/ML2";
			goOnPipelineFromProcessingDir(processingDir);
		}
		else if(arg.equals("Dev QR")) {
			String racine="/media/rfernandez/DATA_RO_A/Roots_systems/BPMP/Third_dataset_2022_11";
			String inventoryDir=racine+"/Source_data/Inventory_of_221125-CC-CO2";
			String processingDir=racine+"/Processing/221125-CC-CO2";
			startNewExperimentFromInventoryAndProcessingDir(inventoryDir,processingDir);
		}
		else {
			if(VitiDialogs.getYesNoUI("Ongoing series ?", "")) {
				String csvPath=VitiDialogs.chooseDirectoryNiceUI("Select a InfoSerieRootSystemTracker.csv file", "OK");
				if(!csvPath.contains(".csv")){IJ.showMessage("No csv file there");return;}
				goOnPipelineFromProcessingDir(new File(csvPath).getParent());
			}
			else {
				String inventoryPath="";
				if(VitiDialogs.getYesNoUI("Start from an existing inventory ?", "")) {
					inventoryPath=VitiDialogs.chooseDirectoryNiceUI("Select a A_main_inventory.csv file (in a Inventory_of... dir)", "OK");
					if(!inventoryPath.contains("A_main_inventory.csv")) {IJ.showMessage("No inventory csv there");return;}
					inventoryPath=new File(inventoryPath).getParent();
				}
				else {
					String inputDir=VitiDialogs.chooseDirectoryNiceUI("Please select your data path", "OK");
					inventoryPath=Plugin_RootDatasetMakeInventory.makeInventory(inputDir);
				}
				String processingPath=VitiDialogs.chooseDirectoryNiceUI("Select an output path (Processing_..bla..)", "OK");
				if(new File(processingPath).list().length>0){IJ.showMessage("There are already files there.");return;}
				startNewExperimentFromInventoryAndProcessingDir(inventoryPath,processingPath);			
			}
			//startPipeline();
		}
	}
	
	
	public void startNewExperimentFromNotInventoriedDataset(String inputDir,String outputDir) {
		String inventoryDir=Plugin_RootDatasetMakeInventory.makeInventory(inputDir);
		if(inventoryDir==null)return;
		startNewExperimentFromInventoryAndProcessingDir(inventoryDir,outputDir);
	}
	
	public static void startNewExperimentFromInventoryAndProcessingDir(String inventoryDir,String processingDir) {
		PipelineParamHandler pph=new PipelineParamHandler(inventoryDir,processingDir); 
		PipelineActionsHandler.goOnExperiment(pph);		
	}

	public void goOnPipelineFromProcessingDir(String processingDir){
		PipelineParamHandler pph=new PipelineParamHandler(processingDir);
		PipelineActionsHandler.goOnExperiment(pph);		
	}


	

}
