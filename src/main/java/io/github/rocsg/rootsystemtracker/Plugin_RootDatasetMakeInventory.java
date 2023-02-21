package io.github.rocsg.rootsystemtracker;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.frame.PlugInFrame;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import io.github.rocsg.fijiyama.common.VitiDialogs;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import io.github.rocsg.rstutils.QRcodeReader;

/**
 * @author rfernandez
 * This class is a trial to simplify both handling and processing data from unstructured or structured datasets, possibly containing QR codes
 * The goal is that, at then end there is :
 * 1) An initial directory with the original dataset. In it, possibly structured or not structured data, can be dirs of a specimen with a series of image, or dirs, and subdirs, and subsubdirs, and at the end, images
 * 2) Just along, the same but with Inventory_of_*, with :
 * 		A_Main_inventory.csv , describing global informations about the experiment (starting and ending observation times, number of different objects considered, number of images, list of specimens with link to the csv)
 *      boite001.csv, boite002.csv, each one containing
 *      num obs    date   hour     hours (double) since series start   relative-path-to-the-observation
 * 3) A processing directory (run by RootSystemTracker) with in it
 *      InfoRootSystemTracker.csv with some informations, and especially the path to the Inventory_of_* directory
 *      Boitetruc
 *      Boitemachin
 *      Boitechose
 *      
 *      
 * When starting, we provide to RST, either a InfoRootSystemTracker.csv file, or the initial directory with the original dataset. 
 *      In the first case, it goes on with it. 
 *      In the second case, it builds an inventory, then ask for building a processing dir, and then build the csv and the arborescence, then starts processing
 *      
 * To go from V1 to V2, there will be a need to create an inventory for files, and to change a bit the output RSML
 */


public class Plugin_RootDatasetMakeInventory  extends PlugInFrame{
	private static final long serialVersionUID = 1L;
	public boolean developerMode=false;
	public String currentRstFlag="1.0";
	public static String versionFlag="Handsome honeysuckle   2022-07-07 09:45";
	

	public static void main(String[]args) {
		ImageJ ij=new ImageJ();
		String inputDir="/media/rfernandez/DATA_RO_A/Roots_systems/Data_BPMP/Third_dataset_2022_11/Source_data/ML3";
		String outputDir="/media/rfernandez/DATA_RO_A/Roots_systems/Data_BPMP/Third_dataset_2022_11/Source_data/Inventory_of_ML3";
		//extract the set of these qr codes by alphanumeric order
		//startInventoryOfAMessyDirButAllTheImagesContainQRCodes(inputDir,outputDir);
		startInventoryOfAlreadyTidyDir(inputDir, outputDir);
	}

	
	public static void main2(String[]args) {
		Plugin_RootDatasetMakeInventory pl=new Plugin_RootDatasetMakeInventory();
		pl.developerMode=true;		
		pl.run("");
	}

	public Plugin_RootDatasetMakeInventory()       {		super("");	 	}

	public Plugin_RootDatasetMakeInventory(String arg)       {		super(arg);	 	}

	public void run(String arg) {
		startInventory();
	}
	
	public static String makeInventory(String inputDir) {
		String outputDir=new File(new File(inputDir).getParent(),"Inventory_of_"+(new File(inputDir).getName())).getAbsolutePath();
		int choice=VitiDialogs.getIntUI("Select 1 for a data input dir with subdirs containing image series, or 2 for a messy bunch of dirs and subdirs containing images (tif, png, or jpg), each one with a QR code describing the object ", 1);
		if(choice<1 || choice >2) {IJ.showMessage("Critical fail : malicious choice ("+choice+"). Stopping now.");return null;}
		if(choice==1)Plugin_RootDatasetMakeInventory.startInventoryOfAlreadyTidyDir(inputDir,outputDir);
		if(choice==2)Plugin_RootDatasetMakeInventory.startInventoryOfAMessyDirButAllTheImagesContainQRCodes(inputDir,outputDir);
		return outputDir;
	}
	
	public void startInventory(){
		IJ.log("Starting RootDatasetMakeInventory version "+versionFlag);

		String inputDir="";
		if(developerMode)inputDir="/media/rfernandez/DATA_RO_A/Roots_systems/Data_BPMP/Third_dataset_2022_11/Source_data_after_renumbering/ML2";
		int choice=VitiDialogs.getIntUI("Select 1 for a data input dir with subdirs containing image series, or 2 for a messy bunch of dirs and subdirs containing images (tif, png, or jpg), each one with a QR code describing the object ", 1);
		if(choice<1 || choice >2) {
			IJ.showMessage("Critical fail : malicious choice ("+choice+"). Stopping now.");return;
		}
		inputDir=VitiDialogs.chooseDirectoryNiceUI("Choose this input data dir", "OK");

		String outputDir="";
		if(developerMode)outputDir="/media/rfernandez/DATA_RO_A/Roots_systems/Data_BPMP/Third_dataset_2022_11/Source_data_after_renumbering/ML2";
		else outputDir=VitiDialogs.chooseDirectoryNiceUI("Build and choose data output dir. Suggested : next to the first one, with name Inventory_of_(name of the original folder)", "OK");
		if(new File(outputDir).list().length>0) {
			IJ.showMessage("Critical fail : output dir is not empty. Stopping now.");return;
		}
		
		if(choice==1)startInventoryOfAlreadyTidyDir(inputDir,outputDir);
		if(choice==2)startInventoryOfAMessyDirButAllTheImagesContainQRCodes(inputDir,outputDir);
		
	}		

	//Here we go for a messy bunch of dirs with at least QR code. Let's hope we can read them yet.
	public static void startInventoryOfAMessyDirButAllTheImagesContainQRCodes(String inputDir,String outputDir){
		double[]sumParams=new double[] {0,0,0,0,0,0};
		int did=0;
		new File(outputDir).mkdirs();
		//aggregate a list of relative path to all image files
		String[]allImgsPath=getRelativePathOfAllImageFilesInDir(inputDir);
		allImgsPath=sortFilesByModificationOrder(inputDir,allImgsPath);
		int NP=allImgsPath.length;
		String[]code=new String[NP];
		boolean reverse=VitiDialogs.getYesNoUI("Are the image mirrored ?", "Is mirrored ?");
		//double[]paramsQRcode=new double[] {4.0,472.0,2916,668,15.8,142.2};
		double[]paramsQRcode=askQRcodeParams(new File(inputDir,allImgsPath[0]).getAbsolutePath(),reverse);

		//Initialize aggregator with original value
		for(int i=0;i<paramsQRcode.length;i++)sumParams[i]+=10*paramsQRcode[i];
		did+=10;
		int nNot=0;
		
		System.out.println("Got QR params from user : ");
		for(double d:paramsQRcode)System.out.println(d);
		//decode the qr code of each image
		String codeNotFound="NOT_FOUND";
		for(int n=0;n<NP;n++) {
			for(int i=0;i<paramsQRcode.length;i++)paramsQRcode[i]=sumParams[i]/did;
			double ratio=VitimageUtils.dou ((did-10)/(1.0*did));
			System.out.print("\nNow decoding "+n+"/"+NP);
			
			System.out.print("...Opening "+allImgsPath[n]);
			ImagePlus img=IJ.openImage(new File(inputDir,allImgsPath[n]).getAbsolutePath());
			System.out.println(" ok.");
			System.out.println("Starting decoding with params inferred from user = "+VitimageUtils.dou (100*(1-ratio))+" % "+" . Inferred from data = "+VitimageUtils.dou (100*ratio)+" % ");
			Object[]objs=QRcodeReader.decodeQRCodeRobust(img,reverse,(int)paramsQRcode[0],paramsQRcode[1],paramsQRcode[2],paramsQRcode[3],paramsQRcode[4],paramsQRcode[5]); 
			code[n]=(String)objs[0];
			double[]params=(double[])objs[1];
			if(code[n].length()<1 || code[n]==null) {
				code[n]=codeNotFound;
				nNot++;
			}
			else {
				for(int i=0;i<params.length;i++)sumParams[i]+=params[i];
				did++;
			}
		}

		//extract the set of these qr codes by alphanumeric order
		Set<String> setNames = new HashSet<String>(Arrays.asList(code));
		
		String[]spec=setNames.toArray(new String[setNames.size()]);
		System.out.println("N specimens = "+spec.length);
		Arrays.sort(spec);
		int N=spec.length;

		
		//Process not found
		if(nNot>0) {
			IJ.showMessage("Some QR codes have not been read.");
			IJ.showMessage("Now I will display the list of codes detected, as a reference for cleaning");
			for(int i=0;i<spec.length;i++)IJ.log(spec[i]);
			IJ.selectWindow("Log");
			IJ.showMessage("Please set the ImageJ log window somewhere on you screen in order to be able to inspect it.");
			IJ.showMessage("I will show you all the images with QR code not found. With each image come a popup.");
			IJ.showMessage("For each image, find the corresponding code in the list (see log window), and copy it into the prompt popup.");
			for(int i=0;i<code.length;i++) if(code[i].equals(codeNotFound)){
				int subFactor=(int)paramsQRcode[0];
				ImagePlus img=IJ.openImage(new File(inputDir,allImgsPath[i]).getAbsolutePath());				
				img=VitimageUtils.resize(img, img.getWidth()/subFactor, img.getHeight()/subFactor, 1);
				if(reverse) IJ.run(img, "Flip Horizontally", "");
				img.show();
				String newCode=VitiDialogs.getStringUI("Give the code", "Code", "Type here", false);
				img.close();
				code[i]=newCode;
			}
			
			setNames = new HashSet<String>(Arrays.asList(code));
			spec=setNames.toArray(new String[setNames.size()]);
			System.out.println("N specimens = "+spec.length);
			Arrays.sort(spec);
			N=spec.length;
		}
		
		
		
		
		
		int header=7;
		FileTime first=null;
		FileTime last=null;
		String [][]mainCSV=new String[N+header][3];

		mainCSV[2]=new String[] {"Number of different objects","NA","NA"};
		mainCSV[3]=new String[] {"Number of different images","NA","NA"};
		mainCSV[4]=new String[] {"Data dir",inputDir,"NA"};
		mainCSV[5]=new String[] {"Inventory dir",outputDir,"NA"};
		mainCSV[6]=new String[] {"Misc ","NA","NA"};
		
		for(int n=0;n<N;n++) {
			System.out.println(n);
			mainCSV[7+n]=new String[] {"Object",""+n,spec[n]};
			//Get the list of all images reporting this QR code. They will come by acquisition order, according to the original order of the list
			ArrayList<String>liObs=new ArrayList<String>();
			for(int i=0;i<NP;i++)if(code[i].equals(spec[n]))liObs.add(allImgsPath[i]);
			String[]obs= liObs.toArray(new String[liObs.size()]); 
			
			int Nobj=obs.length;
			String [][]objCSV=new String[Nobj+1][4];
			String pathDir=new File(inputDir).getAbsolutePath();
			String path0=new File(pathDir,obs[0]).getAbsolutePath();
			objCSV[0]=new String[] {"Num_obs","DateThour(24h-format)","Hours_since_series_start","Relative_path_to_the_img"};
			for(int no=0;no<Nobj;no++) {
				String path=new File(pathDir,obs[no]).getAbsolutePath();
				FileTime ft=getTime(path);
				String rtd=new File(inputDir).getAbsolutePath();
				objCSV[no+1]=new String[] {""+no,ft.toString(),""+VitimageUtils.dou(hoursBetween(path0, path)),path.replace(rtd,"").substring(1)};				
				if(first==null)first=getTime(path);
				if(last==null)last=getTime(path);
				if(first.compareTo(ft)==1)first=getTime(path);
				if(last.compareTo(ft)==-1)last=getTime(path);
			}
			VitimageUtils.writeStringTabInCsv2(objCSV, new File(outputDir,spec[n]+".csv").getAbsolutePath());
			System.out.println("Written : "+new File(outputDir,spec[n]+".csv").getAbsolutePath());
		}
		mainCSV[2][1]=""+N;
		mainCSV[3][1]=""+NP;
		mainCSV[0]=new String[] {"First observation time",first.toString(),"NA"};
		mainCSV[1]=new String[] {"Last observation time",last.toString(),"NA"};			
		VitimageUtils.writeStringTabInCsv2(mainCSV, new File(outputDir,"A_main_inventory.csv").getAbsolutePath());
		System.out.println("Written : "+new File(outputDir,"A_main_inventory.csv").getAbsolutePath());
	}

	
	public static void startInventoryOfAlreadyTidyDir(String inputDir,String outputDir){
		new File(outputDir).mkdirs();
		//Here we go for a data input dir with subdirs containing image series
		//list the data
		String []spec= new File(inputDir).list();
		Arrays.sort(spec);
		int N=spec.length;
		int header=7;
		FileTime first=null;
		FileTime last=null;
		String [][]mainCSV=new String[N+header][3];
		mainCSV[2]=new String[] {"Number of different objects","NA","NA"};
		mainCSV[3]=new String[] {"Number of different images","NA","NA"};
		mainCSV[4]=new String[] {"Data dir",inputDir,"NA"};
		mainCSV[5]=new String[] {"Inventory dir",outputDir,"NA"};
		mainCSV[6]=new String[] {"Misc ","NA","NA"};
		int incrImg=0;
		for(int n=0;n<N;n++) {
			mainCSV[7+n]=new String[] {"Object",""+n,spec[n]};
			String[]obs=new File(inputDir,spec[n]).list();
			obs=sortFilesByModificationOrder(new File(inputDir,spec[n]).getAbsolutePath(),obs);
			int Nobj=obs.length;
			incrImg+=Nobj;
			String [][]objCSV=new String[Nobj+1][4];
			String pathDir=new File(inputDir,spec[n]).getAbsolutePath();
			String path0=new File(pathDir,obs[0]).getAbsolutePath();
			objCSV[0]=new String[] {"Num_obs","DateThour(24h-format)","Hours_since_series_start","Relative_path_to_the_img"};
			for(int no=0;no<Nobj;no++) {
				String path=new File(pathDir,obs[no]).getAbsolutePath();
				FileTime ft=getTime(path);
				String rtd=new File(inputDir).getAbsolutePath();
				objCSV[no+1]=new String[] {""+no,ft.toString(),""+VitimageUtils.dou(hoursBetween(path0, path)),path.replace(rtd,"").substring(1)};			
				if(first==null)first=getTime(path);
				if(last==null)last=getTime(path);
				if(first.compareTo(ft)==1)first=getTime(path);
				if(last.compareTo(ft)==-1)last=getTime(path);
			}
			VitimageUtils.writeStringTabInCsv2(objCSV, new File(outputDir,spec[n]+".csv").getAbsolutePath());
		}
		mainCSV[0]=new String[] {"First observation time",first.toString(),"NA"};
		mainCSV[1]=new String[] {"Last observation time",last.toString(),"NA"};			
		mainCSV[2][1]=""+N;
		mainCSV[3][1]=""+incrImg;
		VitimageUtils.writeStringTabInCsv2(mainCSV, new File(outputDir,"A_main_inventory.csv").getAbsolutePath());
		System.out.println("Inventory of tidy dir ok");
	}

	
	/**
	 * This function is a Dialog utility to get insight from the user about the disposition of the qr codes in images of the dataset.
	 * The user draw a rectangle around the qr code in the example image, then the function estimate the parameters to know for performing the qr code mining
	 * 
	 * @param imgPath The path to the example image to get info about the QR code
	 * @return the params expected for QR code mining : double[]{int subsamplingFactor (which res to work on), QR size, Xcenter, Ycenter, threshMin, threshMax}
	 */
	public static double[]askQRcodeParams(String imgPath,boolean reverse){
		ImagePlus img=IJ.openImage(imgPath);
		if(reverse) IJ.run(img, "Flip Horizontally", "");
		IJ.showMessage("Please draw a rectangle that fits precisely the QRcode, then add to Roi Manager");
		RoiManager rm=RoiManager.getRoiManager();

		//Ask user to draw a rectangle around the QR. Then go on since it is added to RoiManager
		img.show();
		rm.reset();
		IJ.setTool("rectangle");
		boolean finished =false;
		do {
			try {java.util.concurrent.TimeUnit.MILLISECONDS.sleep(200);} catch (InterruptedException e) {e.printStackTrace();}
			if(rm.getCount()==1)finished=true;
			System.out.println(rm.getCount());
		} while (!finished);	

		//get rectangle coordinates
		Roi rect=rm.getRoi(0);
		Rectangle r=rect.getBounds();		
		int x0=(int) r.x;
		int y0=(int) r.y;
		int dx=(int) r.width;
		int dy=(int) r.height;
		System.out.println(r);
		
		//Computing the subsampling factor in order to work with an image where the QRcode is at least 116 x 116
		int subsamplingFactor=Math.min(dx,dy)/116;
		System.out.println("Subs="+subsamplingFactor);
		
		//Computing the ranging interval for possible thresholds
		ImagePlus imgCrop=VitimageUtils.cropImage(img, x0, y0, 0, dx, dy, 1);
		img.changes=false;
		img.close();
		ImageProcessor ip=imgCrop.getProcessor();
		ip.setAutoThreshold("Otsu dark");
		double valMin=ip.getMinThreshold();

		//Gathering results
		return new double[] {subsamplingFactor,Math.max(dx, dy),x0+dx/2,y0+dy/2,valMin*0.2,valMin*1.8};
	}	
		 
	static String[]sortFilesByModificationOrder(String parent,String[] tab){
		String rdt=new File(parent).getAbsolutePath();//Without the / at the end
		String[]ret=Arrays.copyOf(tab,tab.length);
		File[]fTab=Arrays.stream(ret).map(s -> new File(parent,s)).toArray(File[]::new);
		Arrays.sort(fTab,Comparator.comparingLong(File::lastModified));
		return (Arrays.stream(fTab)).map(f -> f.getAbsolutePath()).map(s -> s.replace(rdt,"").substring(1) ).toArray(String[]::new);
	}
	
	static String[]sortFilesByModificationOrder(String[] tab){
		String[]ret=Arrays.copyOf(tab,tab.length);
		File[]fTab=Arrays.stream(ret).map(s -> new File(s)).toArray(File[]::new);
		Arrays.sort(fTab,Comparator.comparingLong(File::lastModified));
		return (Arrays.stream(fTab)).map(f -> f.getAbsolutePath()).toArray(String[]::new);
	}
	
	
	static String[]getRelativePathOfAllImageFilesInDirByTimeOrder(String rootDir){		
		String rdt=new File(rootDir).getAbsolutePath();//Without the / at the end
		File[]init =Arrays.stream(searchImagesInDir(rootDir)).map(x -> new File(x) ).toArray(File[]::new);
		Arrays.sort(init,Comparator.comparingLong(File::lastModified));
		return (Stream.of(init).map(f -> f.getAbsolutePath()).map(s -> s.replace(rdt,"").substring(1) )).toArray(String[]::new);
	}

	
	static String[]getRelativePathOfAllImageFilesInDir(String rootDir){		
		String rdt=new File(rootDir).getAbsolutePath();//Without the / at the end
		return Arrays.stream(searchImagesInDir(rootDir)).map( p -> p.replace(rdt,"").substring(1)).toArray(String[]::new);
	}

	static String[]searchImagesInDir(String rootDir){
	    try {
	    	Stream<Path> paths = Files.find(Paths.get(rootDir),Integer.MAX_VALUE, (path, file) -> file.isRegularFile());
	    	String[]tab=paths.map(p -> p.toString()).filter(s -> isImagePath(s)).toArray(String[]::new);
	    	paths.close();
	    	return tab;
		} catch (IOException e) {
		    e.printStackTrace();
		    return null;
		}
	}

	public static boolean isImagePath(String x) {
		String[] okFileExtensions = new String[] { "jpg", "jpeg", "png", "tif","tiff"};
    	for (String extension : okFileExtensions) {
            if (x.toLowerCase().endsWith(extension))     return true;  
    	}
    	return false;
	}
	
	
	public static double hoursBetween(File f1, File f2) {
		return hoursBetween (f1.getAbsolutePath(),f2.getAbsolutePath());
	}
	
	public static double hoursBetween(String path1, String path2) {
		return VitimageUtils.dou((getTime(path2).toMillis()-getTime(path1).toMillis())/(3600*1000.0));
	}

	public static FileTime getTime(String path) {
		try {
			return Files.readAttributes(Paths.get(path), BasicFileAttributes.class).lastModifiedTime();
		} catch (IOException e) {			e.printStackTrace();
			return null;
		} 
	}

		
}











