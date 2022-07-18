package fr.cirad.image.rstutils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;

import fr.cirad.image.common.Timer;
import fr.cirad.image.common.VitimageUtils;
import ij.IJ;
import ij.ImagePlus;
import net.imagej.ImageMetadata;

//Serie of helpers to handle BPMP data
public class PrepareData {

	
	public static void main(String[]args) {
		prepareDataForDoi();
		//gatherDates();
	}
	
	
	public static void prepareDataForDoi() {
		int ml=1;
		String extension=".jpg";
		String outputDir="/home/rfernandez/Bureau/A_Test/RSML_For_DOI";
		String inputDir="/media/rfernandez/DATA_RO_A/Roots_systems/Data_BPMP/Second_dataset_2021_07/Data_Tidy/";
		if(new File(inputDir).exists()) {}
		else {
			inputDir="/Donnees/DD_CIRS626_DATA/Racines/Data_BPMP/Second_dataset_2021_07/Data_Tidy/";
			if(new File(inputDir).exists()) {}
			else {IJ.showMessage("No hard disk found for source data. ");}			
		}
		ArrayList<ImagePlus> listImg=new ArrayList<ImagePlus>();
		Timer t=new Timer();
		for(int b=54;b<=54;b++) {
			String boi="000"+(b<10 ?  "0" : "")+b;
			new File(outputDir,"ML"+ml+"_Boite_"+boi).mkdirs();
			t.print("Importing "+"ML"+ml+"_Boite_"+boi);
			for(int i=0;i<100;i++) {
				String seq=""+(i+1);
				t.print("         seq="+seq);
				String specName="ML"+ml+"_Seq_"+seq+"_Boite_"+boi;
				if(new File(inputDir+"IMG/ML"+ml+"/Seq_"+seq+"/"+specName+extension).exists() ) {
					ImagePlus img=IJ.openImage(inputDir+"IMG/ML"+ml+"/Seq_"+seq+"/"+specName+extension);
					VitimageUtils.adjustImageCalibration(img, new double[] {19,19,19}, "µm");
					img=VitimageUtils.resize(img, img.getWidth()/4,img.getHeight()/4,1);
					IJ.saveAsTiff(img,outputDir+"/ML"+ml+"_Boite_"+boi+"/img_t"+i+".tif");
					listImg.add(img);
				}
			}
		}
	}
	
	public static void gatherDates() {
		String dataDirSource="/media/rfernandez/DATA_RO_A/TEMP";
		String dataDirTarget="/media/rfernandez/DATA_RO_A/Roots_systems/Data_BPMP/Second_dataset_2021_07/Data_Tidy/IMG";
		String dataCsvTarget="/media/rfernandez/DATA_RO_A/Roots_systems/Data_BPMP/Second_dataset_2021_07/Data_Tidy/CSV";

		for (int ml=0;ml<5;ml++) {
			System.out.println("Doing ML "+ml);
			String pathMlSource=new File(dataDirSource,"ML"+ml).getAbsolutePath();
			String pathMlTarget=new File(dataDirTarget,"ML"+ml).getAbsolutePath();
			
			for(int i=0;i<100;i++) {
				if(new File(pathMlTarget,"Seq_"+i).exists()) {
					new File(dataCsvTarget+"/ML"+ml+"/Seq_"+i).mkdirs();
					String di="";
					if(ml==1)di="Visualisation - Sequence "+i+"";
					else if(ml==3)di="Seq"+i+" ML3";
					else if(ml==2)di="Seq"+i+"-ML2";
					else di="Seq"+i+" ML4";
					
					String dirSource=new File(pathMlSource,di).getAbsolutePath();
					String dirTarget=new File(pathMlTarget,"Seq_"+i).getAbsolutePath();
					System.out.println();
					System.out.println(dirTarget+(new File(dirTarget).exists()));
					File[]fileSource=new File(dirSource).listFiles();
					File[]fileTarget=new File(dirTarget).listFiles();
					Arrays.sort(fileSource, new FileArrayComparator());
					Arrays.sort(fileTarget, new FileArrayComparator());
					System.out.println(0.001*getCreationDateMillis(fileSource[fileSource.length-1].getAbsolutePath()) -0.001*getCreationDateMillis(fileSource[0].getAbsolutePath()) ) ;
					if((ml==3) && (i==3)){
						for(File f : fileSource) {
							System.out.println(getCreationDate(f.getAbsolutePath())+" "+f.getAbsolutePath());
						}
					}
					
					int NS=fileSource.length;
					int NT=fileTarget.length;
					String[][]csvTab=new String[NT][3];
					for(int j=0;j<NT;j++) {
						csvTab[NT-1-j][0]=fileTarget[NT-1-j].getName();
						csvTab[NT-1-j][1]=getCreationDate(fileSource[NS-1-j].getAbsolutePath());
						csvTab[NT-1-j][2]=""+getCreationDateMillis(fileSource[NS-1-j].getAbsolutePath());
					}
					
					VitimageUtils.writeStringTabInCsv(csvTab, new File(dataCsvTarget+"/ML"+ml+"/Seq_"+i+"/Timepoints.csv").getAbsolutePath());					
				}
			}			
		}
	}	
	
	public static long getCreationDateMillis(String path) {
		BasicFileAttributes attrs;
		try {
		    attrs = Files.readAttributes(new File(path).toPath(), BasicFileAttributes.class);
		    FileTime time = attrs.creationTime();
		    String pattern = "yyyy-MM-dd_HH:mm:ss";
		    return time.toMillis();
		} catch (IOException e) {		    e.printStackTrace();	}
		return 0;
	}

	public static String getCreationDate(String path) {
		BasicFileAttributes attrs;
		try {
		    attrs = Files.readAttributes(new File(path).toPath(), BasicFileAttributes.class);
		    FileTime time = attrs.creationTime();
		    String pattern = "yyyy-MM-dd_HH:mm:ss";
		    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
		    return "Date="+simpleDateFormat.format( new Date( time.toMillis() ) ).replace("_","_Time=");
		} catch (IOException e) {		    e.printStackTrace();	}
		return null;
	}
	
	public static void addInfoToMetadata(String path,String targetPath,String add) {
		org.apache.commons.imaging.common.ImageMetadata metadata = null;
		String name;
 	    try {
			metadata = Imaging.getMetadata(new File(path));
		} catch (ImageReadException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if (metadata instanceof JpegImageMetadata) {
		   final JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;
		   final TiffImageMetadata exif = jpegMetadata.getExif();
//		   TiffOutputSet outputSet = exif.getOutputSet();//Then add
//		   new ExifRewriter().updateExifMetadataLossless(new File(path), os, outputSet);
	    }
	}
	
	public static void printFileAr(File[]ar) {
		for(int i=0;i<ar.length;i++) {
			System.out.println("I="+i+getCreationDate(ar[i].getAbsolutePath())+" | "+ar[i].getAbsolutePath());
		}
	}
	
	
}

class FileArrayComparator implements Comparator{
	
	@Override
	public int compare(Object arg0, Object arg1) {
		int i0=0;
		int i1=1;
		if(((File)arg0).getAbsolutePath().contains("Boite_")) {
			i0=Integer.parseInt((((File)arg0).getAbsolutePath().split("Boite_")[1]).substring(0, 5));
			i1=Integer.parseInt((((File)arg1).getAbsolutePath().split("Boite_")[1]).substring(0, 5));			
		}
		else {
			i0=Integer.parseInt((((File)arg0).getAbsolutePath().split("Boite ")[1]).substring(0, 5));
			i1=Integer.parseInt((((File)arg1).getAbsolutePath().split("Boite ")[1]).substring(0, 5));			
		}
		return Integer.compare(i0, i1);
	}
	
	public static long getCreationDateMillis(String path) {
		BasicFileAttributes attrs;
		try {
		    attrs = Files.readAttributes(new File(path).toPath(), BasicFileAttributes.class);
		    FileTime time = attrs.creationTime();
		    String pattern = "yyyy-MM-dd_HH:mm:ss";
		    return time.toMillis();
		} catch (IOException e) {		    e.printStackTrace();	}
		return 0;
	}


	
}
