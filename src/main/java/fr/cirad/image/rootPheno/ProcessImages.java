package fr.cirad.image.rootPheno;
import java.awt.List;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

import fr.cirad.image.common.VitiDialogs;
import fr.cirad.image.common.VitimageUtils;
import fr.cirad.image.rootsystemtracker.MorphoUtils;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Binarizer;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.datamatrix.encoder.SymbolShapeHint;
public class ProcessImages {
	static String mainDataPath="/home/rfernandez/Bureau/A_Test/Perin_Root/";
	final static double voxSize=200/3436.378;
	static int Ndir=0;//Set to 0 to process all
	static int Nimg=0;//Set to 0 to process all
	public static void main(String[]args) {
		ImageJ ij=new ImageJ();
/*		ImagePlus img=IJ.openImage("/Donnees/Perin_Root/Fail/R4B2-100418DDSC_0002.jpg");
		img.show();
		ImagePlus img2=extractAreaBarCode(img);
		img2.show();
		VitimageUtils.waitFor(1000000);
;,*/		prepareDirs();
		int nBarcodeGood=0;
		int nBarcode=0;
		String[]dirNames=new File(mainDataPath,"0_Source").list();
		System.out.println("dirNames list ok");
		if(Ndir==0)Ndir=dirNames.length;
		for(int nd=0;nd<Ndir;nd++) {
			String[]imgNames=new File(mainDataPath,"0_Source"+"/"+dirNames[nd]).list();
			if(Nimg==0 || Nimg>imgNames.length)Nimg=imgNames.length;
			for(int ni=0;ni<Nimg;ni++) {
				double[]res=process(dirNames[nd],imgNames[ni],2,2);
				nBarcodeGood+=res[0];
				nBarcode+=res[1];
				System.out.println("Barcodes : total="+nBarcode+" succeeded="+nBarcodeGood);
			}
		}
	}
	

	public static void prepareDirs() {
		System.out.println("Preparing dirs");
		String []listDirs=new String[] {"1_Vert","2_Meta","2_Sub"};
		String []listSubDirs=new File(mainDataPath,"0_Source").list();
		for(String s1:listDirs) {
			for(String s2:listSubDirs) {
				new File(mainDataPath+"/"+s1+"/"+s2).mkdirs();
			}
		}
		System.out.println("Ok.");
	}
	
	
	public static double[] process(String dir,String img,int stepMin,int stepMax) {
		String spec=dir+"/"+img;
		double[]res=new double[10];
		System.out.println("\nProcessing steps "+stepMin+" to "+stepMax+" for specimen "+spec);
		if(stepMin<=1 && stepMax>=1)step_1(dir,img);
		if(stepMin<=2 && stepMax>=2) {res[0]=step_2(dir,img);res[1]=2;}
		return res;
	}

	public static void step_1(String dir,String img) {
		String spec=dir+"/"+img;
		System.out.println(spec+" step_1, preparing data");
		ImagePlus im=IJ.openImage(mainDataPath+"0_Source/"+spec);
		IJ.run(im, "Rotate 90 Degrees Right", "");
		VitimageUtils.adjustVoxelSize(im, new double[] {voxSize,voxSize,voxSize},"mm");
//		IJ.save(im, mainDataPath+"1_Vert/"+spec);
		im=VitimageUtils.resize(im, 2000, 3000, 1);
		IJ.run(im,"8-bit","");
		IJ.save(im, mainDataPath+"2_Sub/"+spec);
	}
	
	//Detect barcodes
	public static int step_2(String dir,String img) {
		String spec=dir+"/"+img;
		int nGotGood=2;
		System.out.println(""+spec+" step_2, gathering barcodes");
/*		ImagePlus im=IJ.openImage(mainDataPath+"0_Source/"+spec);
		IJ.run(im, "Rotate 90 Degrees Right", "");*/
		ImagePlus im=IJ.openImage(mainDataPath+"2_Sub/"+spec);
		//im.setDisplayRange(180, 180);
		IJ.run(im,"8-bit","");
		System.out.println(mainDataPath+"2_Sub/"+spec);
		ImagePlus temp=VitimageUtils.cropImage(im, 100, 2200, 0, 900, 500, 1);
		temp=extractAreaBarCode(temp);
		int valG= Integer.parseInt (readBarCode(temp));
		if(valG==0) {nGotGood--;valG=askBarCode(temp);storeImage(temp,dir+"G",img,false);}
		else storeImage(temp,dir,img,true);
		temp=VitimageUtils.cropImage(im, 1100, 2200, 0, 900, 500, 1);
		temp=extractAreaBarCode(temp);
		int valD= Integer.parseInt (readBarCode(temp));
		if(valD==0) {nGotGood--;valD=askBarCode(temp);storeImage(temp,dir+"D",img,false);}
		else storeImage(temp,dir,img,true);
		System.out.println("Barcode gauche="+valG+" Barcode droite="+valD);
		String text="BARCODE_LEFT="+valG+"\nBARCODE_RIGHT="+valD+"\n";
		VitimageUtils.writeStringInFile(text, mainDataPath+"2_Meta/"+VitimageUtils.withoutExtension(spec)+".txt");
		return nGotGood;
	}
	

	public static ImagePlus extractAreaBarCode(ImagePlus img) {
		ImagePlus img2=MorphoUtils.dilationLine2D(img, 8, true);
		img2=MorphoUtils.erosionLine2D(img2, 4, false);
		img2=VitimageUtils.thresholdImage(img2, 240, 256);
		img2=VitimageUtils.connexeBinaryEasierParamsConnexitySelectvol(img2, 4, 1);
		IJ.run(img2,"8-bit","");
		img2=MorphoUtils.dilationLine2D(img2, 4, false);
		img2=MorphoUtils.erosionLine2D(img2, 8, true);
		ImagePlus maskOut=VitimageUtils.invertBinaryMask(img2);
		IJ.run(maskOut, "Fill Holes", "");
		img2=VitimageUtils.invertBinaryMask(maskOut);
		img2=VitimageUtils.getBinaryMask(img2, 0.5);
		img2=VitimageUtils.makeOperationBetweenTwoImages(img, img2, 2,true);
		IJ.run(img2,"8-bit","");
		img2=VitimageUtils.makeOperationBetweenTwoImages(img2, maskOut,1,true);
		return img2;
	}
	
	public static void storeImage(ImagePlus im,String dir, String img,boolean good) {
		if(good)IJ.save(im,mainDataPath+"Success/"+dir+""+img);
		else IJ.save(im,mainDataPath+"Fail/"+dir+""+img);
	}
	
	public static int askBarCode(ImagePlus temp) {
		temp.show();
		int ret=VitiDialogs.getIntUI("Pleas enter barcode", 0);
		temp.hide();
		return ret;
	}
	
	public static String readBarCode(ImagePlus temp) {
		ImageProcessor ip=temp.getProcessor();		
		MultiFormatReader multiFormatReader = new MultiFormatReader();
	    Rectangle r = ip.getRoi();
	    BufferedImage myimg = ip.crop().getBufferedImage();
	    BufferedImageLuminanceSource bufferedImageLuminanceSource = new BufferedImageLuminanceSource(myimg);
	    BinaryBitmap bitmap = new BinaryBitmap((Binarizer)new HybridBinarizer((LuminanceSource)bufferedImageLuminanceSource));
	    String resultText = null;
	    BarcodeFormat bf = null;
	    Map<DecodeHintType, Object> hints = null;
	    hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
	    ArrayList<BarcodeFormat>ar=new ArrayList<BarcodeFormat>();
	    ar.add(BarcodeFormat.CODE_39);
	    hints.put(DecodeHintType.POSSIBLE_FORMATS, ar);
	    hints.put(DecodeHintType.TRY_HARDER,Boolean.TRUE);
	    try {
	      Result result = multiFormatReader.decode(bitmap, hints);
	      resultText = result.getText();
	      bf = result.getBarcodeFormat();
	    } catch (NotFoundException e) {
	      IJ.log("Error : No corresponding encoder found (" + e.getMessage() + ")"); return "0";
	    } 
	    if (resultText != null) {
	    	//IJ.showMessage(resultText);
	    	return resultText;
	    } 
	    //IJ.showMessage("0");
	    return "0";
	}
}
