package fr.cirad.image.rsttest;
import java.util.ArrayList;
import java.util.Random;

import fr.cirad.image.common.Timer;
import fr.cirad.image.common.VitimageUtils;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.Duplicator;
import trainableSegmentation.*;
public class TestWeka {

	public static void main(String []args) {
		ImageJ ij=new ImageJ();
		//doWeka();
		String boite="00002";
		//doWeka(boite);
		//interpretWeka().show();
		produceTrainData();
	}		
		
	
	public static void produceTrainData() {
		String mainDataDir="/home/rfernandez/Bureau/A_Test/RSML";
		ArrayList<ImagePlus>insights=new ArrayList<ImagePlus>();
		Random rand=new Random();
		for(int mli=1;mli<=1;mli++) {
			for(int boi=1;boi<=20;boi++) {
				String boite=(  (boi<10) ? ("0000"+boi) : ( (boi<100) ? ("000"+boi) : ("00"+boi) ) );
				System.out.println(boite);
				ImagePlus img=IJ.openImage( "/home/rfernandez/Bureau/A_Test/RSML/1_Remove_Leaves/ML1_Boite_"+boite+".tif" );
				double ratio=0.15;
				for(int i=0;i<img.getStackSize();i++) {
					System.out.println(i);
					if(rand.nextDouble()<ratio) {
						int xPlus=rand.nextInt(200);
						int yPlus=rand.nextInt(200);
						ImagePlus extract=new Duplicator().run(img,1,1,i+1,i+1,1,1);
						extract=VitimageUtils.cropImage(extract, 300+xPlus, 50+yPlus, 0, 400, 400, 1);
						insights.add(extract);
						System.out.println("---> "+insights.size());
					}
				}
			}
		}
		ImagePlus[]extTab=insights.toArray(new ImagePlus[insights.size()]);
		ImagePlus train=VitimageUtils.slicesToStack(extTab);
		train.show();
	}
	
	public static void doWeka(String boite)		{			
		String clasifierPath="/home/rfernandez/Bureau/A_Test/RSML/N_Others/ML/classifier_v4.model";
		ImagePlus input  = IJ.openImage( "/home/rfernandez/Bureau/A_Test/RSML/1_Remove_Leaves/ML1_Boite_"+boite+".tif" );
		// create Weka Segmentation object
		WekaSegmentation segmentator = new WekaSegmentation( input );
		segmentator.loadClassifier(clasifierPath);
//		segmentator.applyClassifier( false );
		ImagePlus []inputTab=VitimageUtils.stackToSlices(input);
		int incr=0;
		int N=inputTab.length;
		Timer t=new Timer();
		for(int i=0;i<N;i++) {
			System.out.print("Processing image "+(i+1)+" / "+N);
			inputTab[i]=segmentator.applyClassifier(inputTab[i],16,true);
			System.out.println(" Ok."+t);
		}
//		ImagePlus result = segmentator.applyClassifier(input,16,true);
//		ImagePlus result = segmentator.getClassifiedImage(true);
		ImagePlus result=VitimageUtils.slicesToStack(inputTab);
		result.show();
		result.setDisplayRange(0, 1);
		IJ.run(result,"Fire","");
		IJ.saveAsTiff(result, "/home/rfernandez/Bureau/test.tif");
	}

	public static ImagePlus interpretWeka() {
		ImagePlus imgIn=IJ.openImage("/home/rfernandez/Bureau/test.tif");
		imgIn.show();
		IJ.run(imgIn, "Mean...", "radius=1 stack");
		ImagePlus[]imgs=VitimageUtils.stackToSlices(imgIn);
		int X=imgs[0].getWidth();
		int Y=imgs[0].getHeight();
		int N=imgs.length;
		ImagePlus retIndSlope=VitimageUtils.nullImage(imgs[0].duplicate());
		ImagePlus retIndRupt=VitimageUtils.nullImage(imgs[0].duplicate());
		ImagePlus retIndGlob=VitimageUtils.nullImage(imgs[0].duplicate());
		float[]valsIndRupt;
		float[]valsIndSlope;
		float[]valsIndGlob;
		float[][]valsImg=new float[imgs.length][];
		float[][]valsMask=new float[imgs.length][];
		double[]valsToDetect;
		double[]valsToMask;
		valsIndRupt=(float [])retIndRupt.getStack().getProcessor(1).getPixels();
		valsIndSlope=(float [])retIndSlope.getStack().getProcessor(1).getPixels();
		valsIndGlob=(float [])retIndGlob.getStack().getProcessor(1).getPixels();
		for(int z=0;z<N;z++) {
			valsImg[z]=(float [])imgs[z].getStack().getProcessor(1).getPixels();
				//valsMask[i]=(float [])maskLeavesOut[((i<2) ? 0 : i-1)].getStack().getProcessor(z+1).getPixels();
		}
		for(int x=0;x<X;x++) {
			for(int y=0;y<Y;y++) {
				int last=0;
				valsToDetect=new double[imgs.length];
//					valsToMask=new double[imgs.length];
				for(int i=0;i<imgs.length;i++) {
					valsToDetect[i]=valsImg[i][X*y+x];
//						valsToMask[i]=valsMask[i][xM*y+x];
//						if(valsToMask[i]<1)last=i;
					last=i;
				}
				boolean blabla=false;
				if(x==377 && y==133)blabla=true;
				double[]newTab=new double[last+1];
				for(int i=0;i<=last;i++) {
					newTab[i]=valsToDetect[i];
				}
				double thresholdRupt=0.3;
				double thresholdSlope=0.1;
				int rupt=ruptureDetectionUp(newTab, thresholdRupt,blabla);
				int slope=slopeDetectionUp(newTab, thresholdSlope,blabla);
				valsIndRupt[X*y+x]=rupt; 
				valsIndSlope[X*y+x]=slope;
				valsIndGlob[X*y+x]=(rupt==0 || slope==0) ? 0 : rupt;
				if(newTab[0]>=0.5)valsIndGlob[X*y+x]=1;
			}			
		}
		retIndRupt.setDisplayRange(-1, N);
		retIndSlope.setDisplayRange(-1, N);
		retIndGlob.setDisplayRange(-1, N);
		retIndRupt.setTitle("Rupt");retIndRupt.show();
		retIndSlope.setTitle("Slope");retIndSlope.show();
		
		return retIndGlob;		//Pour chaque voxel,
	}
	//detecter la best rupture
	//meilleur tel que vals in diff vals sup > 0.5
	public static int ruptureDetectionUp(int[]vals,double threshold,boolean blabla) {
		double[]d=new double[vals.length];
		for(int i=0;i<d.length;i++)d[i]=vals[i];
		return ruptureDetectionUp(d,threshold,blabla);
	}


	//Return the index which is the first point of the second distribution
	public static int boundDetectionUp(double[]vals,double threshold,boolean blabla) {
		int indMax=0;
		if(vals[vals.length-1]<threshold)return 0;
		for(int i=vals.length-2;i>=0;i--) {
			if(vals[i]<threshold)return (i+1);
		}
		return 1;
	}

	//Return the index which is the first point of the second distribution
	public static int slopeDetectionUp(double[]vals,double threshold,boolean blabla) {
		int indMax=0;
		double valMax=-100000000;
		for(int i=0;i<vals.length-1;i++) {
			double val=vals[i+1]-vals[i];
			if(val>valMax) {
				valMax=val;
				indMax=i+1;
			}
			if(val>threshold)return (indMax);
		}
		return 0;
	}
	
	//Return the index which is the first point of the second distribution
	public static int ruptureDetectionUp(double[]vals,double threshold,boolean blabla) {
		int indMax=0;
		double diffMax=-10000000;
		int N=vals.length;
		for(int i=1;i<N;i++) {
			double m1=meanBetweenIncludedIndices(vals, 0, i-1);
			double m2=meanBetweenIncludedIndices(vals, i, N-1);
			double diff=m2-m1;
			if(diff>diffMax) {
				indMax=i;
				diffMax=diff;
			}
			if(blabla) {
//					System.out.println("Apres i="+i+" : indMax="+indMax+" diffMax="+diffMax+" et on avait m1="+m1+" et m2="+m2);
			}
		}		
		return (diffMax>threshold ? indMax : 0);
	}

	public static double meanBetweenIncludedIndices(double[]tab,int ind1,int ind2) {
		double tot=0;
		for(int i=ind1;i<=ind2;i++)tot+=tab[i];
		return (tot/(ind2-ind1+1));
	}

}
