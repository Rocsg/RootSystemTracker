package fr.cirad.image.rootsystemtracker;

import java.io.File;
import java.util.Arrays;

import fr.cirad.image.common.VitimageUtils;
import ij.IJ;

public class PipelineParamHandler {
	String currentVersion="1.0";
	String pathToParameterFile="";
	String inputDir="";
	String outputDir="";
	int MAX_NUMBER_IMAGES=1000;
	int nMaxParams=100+MAX_NUMBER_IMAGES;
	int nParams=0;
	private String[][] params;

	final static int NO_PARAM_INT=-999999999;
	final static double NO_PARAM_DOUBLE=-99999999;
	public int numberPlantsInBox=5;
	int nbData=1;
	public int sizeFactorForGraphRendering=6;
	int minSizeCC=5;
	double rootTissueIntensityLevel=30;
	double backgroundIntensityLevel=130;
	double maxSpeedLateral=33;
	double meanSpeedLateral=10;
	double typicalSpeed=12;//pixels/hour
	double penaltyCost=0.5;
	double nbMADforOutlierRejection=25;
	double minDistanceBetweenLateralInitiation=4;
	double minLateralStuckedToOtherLateral=30;
	public int memorySaving=0;//if 1, don't save very big debug images;
	int xMinCrop=122;
	int yMinCrop=152;
	int dxCrop=1348;
	int dyCrop=1226;
	int maxLinear=4;
	int marginRegisterLeft=12;
	int marginRegisterUp=135;
	int marginRegisterRight=0;
	boolean applyFullPipelineImageAfterImage=true;
	double toleranceDistanceForBeuckerSimplification=0.9;
	String[]imgNames;
	int[]imgSteps;
	
	public static void main(String[]arg) {
	}
	
	
	public PipelineParamHandler(String inputDir,String outputDir) {
		this.inputDir=inputDir;
		this.outputDir=outputDir;
		this.pathToParameterFile=new File(outputDir,"Parameters.csv").getAbsolutePath(); 
		
		if(new File(this.pathToParameterFile).exists())   {  readParameters();                                   }
		else                                              {  getParametersForNewExperiment(); writeParameters(true); }
	}

	public PipelineParamHandler(String parametersFilePath) {
		outputDir=new File(parametersFilePath).getParent();
		readParameters();
	}

	
	public String[]getImgNames(){
		return imgNames;
	}
	
	public void readParameters() {
		params=VitimageUtils.readStringTabFromCsv(new File(outputDir,"Parameters.csv").getAbsolutePath());
		inputDir=getString("inputDir");
		outputDir=getString("outputDir");
		numberPlantsInBox=getInt("numberPlantsInBox");
		minSizeCC=getInt("minSizeCC");
		sizeFactorForGraphRendering=getInt("sizeFactorForGraphRendering");
		rootTissueIntensityLevel=getDouble("rootTissueIntensityLevel");
		backgroundIntensityLevel=getDouble("backgroundIntensityLevel");
		minDistanceBetweenLateralInitiation=getDouble("minDistanceBetweenLateralInitiation");
		minLateralStuckedToOtherLateral=getDouble("minLateralStuckedToOtherLateral");
		maxSpeedLateral=getDouble("maxSpeedLateral");
		meanSpeedLateral=getDouble("meanSpeedLateral");
		typicalSpeed=getDouble("typicalSpeed");
		penaltyCost=getDouble("penaltyCost");
		nbMADforOutlierRejection=getDouble("nbMADforOutlierRejection");
		nbData=getInt("nbData");
		
		imgNames=new String[nbData];
		imgSteps=new int[nbData];
		for(int i=0;i<nbData;i++) {
			imgNames[i]=getString("Img_"+i+"_name");
			imgSteps[i]=getInt("Img_"+i+"_step");
		}
	}
	
	public void getParametersForNewExperiment(){
		nbData=new File(inputDir).list().length;
		if(nbData>MAX_NUMBER_IMAGES) {
			IJ.showMessage("Critical warning : number of images is too high : "+nbData+" > "+MAX_NUMBER_IMAGES);
		}
		
		//Eventuelly ask for parameters
	}

	
	public void writeParameters(boolean firstWrite) {
		params=new String[nMaxParams][3];
		nParams=0;
		addParam("## Parameters for RootSystemTracker experiment ##","","");
		addParam("inputDir",inputDir,"");
		addParam("outputDir",outputDir ,"");

		addParam("nbData",nbData ,"");
		addParam("numberPlantsInBox",numberPlantsInBox ,"");
		addParam("minSizeCC",minSizeCC ,"");
		addParam("sizeFactorForGraphRendering",sizeFactorForGraphRendering ,"");
		
		addParam("rootTissueIntensityLevel",rootTissueIntensityLevel ,"");
		addParam("backgroundIntensityLevel",backgroundIntensityLevel ,"");
		addParam("minDistanceBetweenLateralInitiation",minDistanceBetweenLateralInitiation ,"");
		addParam("minLateralStuckedToOtherLateral",minLateralStuckedToOtherLateral ,"");
		addParam("maxSpeedLateral",maxSpeedLateral ,"");
		addParam("meanSpeedLateral", meanSpeedLateral,"");
		addParam("typicalSpeed",typicalSpeed ,"");
		addParam("penaltyCost", penaltyCost,"");
		addParam("typicalSpeed", typicalSpeed,"");
		addParam("nbMADforOutlierRejection",nbMADforOutlierRejection ,"");
		addParam("xMinCrop",xMinCrop,"");
		addParam("yMinCrop",yMinCrop,"");
		addParam("dxCrop",dxCrop,"");
		addParam("dyCrop",dyCrop,"");
		addParam("maxLinear",maxLinear,"Used to define the max level");
		addParam("-","-","-");
		addParam("-","-","-");
		addParam("-","-","-");
		addParam("-","-","-");
		addParam("-","-","-");
		addParam("-","-","-");

		if(firstWrite) {
			imgNames=new String[nbData];
			imgSteps=new int[nbData];
			String[]listImgs=new File(inputDir).list();
			Arrays.sort(listImgs);
			for(int i=0;i<nbData;i++) {
				imgNames[i]=listImgs[i];
				imgSteps[i]=0;
			}
		}
		for(int i=0;i<nbData;i++) {
			addParam("Img_"+i+"_name",imgNames[i],"");
			addParam("Img_"+i+"_step",imgSteps[i],"");
		}
		VitimageUtils.writeStringTabInCsv2(params,new File(outputDir,"Parameters.csv").getAbsolutePath());		
	}
	
	
	public String getString(String tit) {
		for(int i=0;i<nMaxParams;i++)if(params[i][0].equals(tit))return params[i][1];
		IJ.showMessage("Parameter not found : "+tit+" in param file of "+outputDir);
		return null;
	}
	
	public double getDouble(String tit) {
		for(int i=0;i<nMaxParams;i++)if(params[i][0].equals(tit))return Double.parseDouble( params[i][1] );
		IJ.showMessage("Parameter not found : "+tit+" in param file of "+outputDir);
		return NO_PARAM_DOUBLE;
	}

	public int getInt(String tit) {
		for(int i=0;i<nMaxParams;i++)if(params[i][0].equals(tit))return Integer.parseInt( params[i][1] );
		IJ.showMessage("Parameter not found : "+tit+" in param file of "+outputDir);
		return NO_PARAM_INT;
	}
	
	public void addParam(String tit,String val,String info){
		params[nParams++]=new String[] {tit,val,info};
	}

	public void addParam(String tit,double val,String info){
		params[nParams++]=new String[] {tit,""+val,info};
	}

	public void addParam(String tit,int val,String info){
		params[nParams++]=new String[] {tit,""+val,info};
	}

	
	
}
