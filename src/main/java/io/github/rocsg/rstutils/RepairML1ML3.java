package io.github.rocsg.rstutils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Date;

import io.github.rocsg.fijiyama.common.VitimageUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;

public class RepairML1ML3 {

	
	public static void main(String []args) throws ParseException {
		applyCollectedDatesOnRawML3();
	}
	public static void applyCollectedDatesOnRawML3() {
		String dirToML3="/media/rfernandez/DATA_RO_A/Roots_systems/Data_BPMP/Third_dataset_2022_11/Raw/ML3";
		String dirToCSV3="/media/rfernandez/DATA_RO_A/Roots_systems/Data_BPMP/Third_dataset_2022_11/Raw/CSV/ML3";
		String[]nameSeqs=new File(dirToML3).list();
		
		for(String seq : nameSeqs) {
//			System.out.println(seq);
			if(seq.equals(".RData"))continue;
			if(seq.equals(".Rhistory"))continue;
			System.out.println("Processing "+seq);
			String pathSeq=dirToCSV3+"/"+seq;
			String[][]csvData=VitimageUtils.readStringTabFromCsv(pathSeq+"/Timepoints.csv");
			for(int line=0;line<csvData.length;line++) {
				System.out.print(line+" ");
				String imgName=csvData[line][0].replace(" ", "");
				String dateOrig=csvData[line][1];
				String yymmdd=dateOrig.split("_")[0].split("=")[1];
				String hhmmss=dateOrig.split("_")[1].split("=")[1].replace(" ", "");
				
				String pathImgToChange=dirToML3+"/"+seq+"/"+imgName;
				changeLastModificationTimeWithInventoryFormatting(pathImgToChange, yymmdd+"T"+hhmmss+"Z");		
				System.out.println("changeLastModificationTimeWithInventoryFormatting("+pathImgToChange+" , "+yymmdd+"T"+hhmmss+"Z )");
			}
		}
	}

	public static void test() {
//		changeLastModificationTimeWithInventoryFormatting("/home/rfernandez/Bureau/test.txt", "2021-03-24T09:16:45Z");
		changeLastModificationTimeWithInventoryFormatting("/media/rfernandez/DATA_RO_A/Roots_systems/Data_BPMP/Third_dataset_2022_11/Raw/ML1/Seq_17/ML1_Seq_17_Boite_00054.jpg", "2021-03-24T09:16:45Z");
		
	}
	
	//Format is "yyyy-dd-mm'T'HH:MM:SS'Z'"
	public static void changeLastModificationTimeWithInventoryFormatting(String path,String newTime){
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	    try {new File(path).setLastModified(sdf.parse(newTime).getTime());} catch (ParseException e) {e.printStackTrace();}
	}
		


}
