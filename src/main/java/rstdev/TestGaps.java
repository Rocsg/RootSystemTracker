package rstdev;

import java.util.ArrayList;

import org.itk.simple.Image;
import org.scijava.vecmath.Point2d;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.Duplicator;
import io.github.rocsg.fijiyama.common.ItkImagePlusInterface;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import io.github.rocsg.fijiyama.registration.BlockMatchingRegistration;
import io.github.rocsg.fijiyama.registration.ItkTransform;
import math3d.Point3d;

public class TestGaps {

	
	
	public static void main(String[]args) {
		//Test data
		String inputData="/home/rfernandez/Bureau/A_Test/RootSystemTracker/Cici_V3/TestGaps/Gaps_6_medium.tif";
		String inputMask="/home/rfernandez/Bureau/A_Test/RootSystemTracker/Cici_V3/TestGaps/Gaps_6_medium_mask.tif";
		ImagePlus img=IJ.openImage(inputData);
		ImagePlus imgMask=IJ.openImage(inputMask);
		img=VitimageUtils.convertByteToFloatWithoutDynamicChanges(img);
		imgMask=VitimageUtils.convertByteToFloatWithoutDynamicChanges(imgMask);
		int radiusX=4;//A root makes roughly 4 pixels. Thus it is well embodid withing a square block of 7 x 7
		int radiusY=2;//A root makes roughly 4 pixels. Thus it is well embodid withing a square block of 7 x 7
		int sigma=8;
		double minRsquared=0.3;
		double minVariance=5;
		double radiusSearch=20;

		//Get candidate correspondences
		Object[]obj=identifyBoundaryPatches(img,imgMask,radiusX,radiusY);
		ArrayList<Point2d>candidatePointTop=(ArrayList<Point2d>) obj[0];
		ArrayList<Point2d>candidatePointBot=(ArrayList<Point2d>) obj[1];
		
		
		//Establish pair voted correspondences as an indices vector		
		ArrayList<int[]>correspondencesIndices=getSmartCorrespondences(img,candidatePointTop,candidatePointBot,radiusX,radiusY,minRsquared,minVariance,radiusSearch,sigma);
		System.out.println("Smart corr have "+correspondencesIndices.size());
	

		//Interpolate the corresponding euclidean vectors to discrete vector field
		ImagePlus[]field=getTheDeformationField(img,correspondencesIndices,candidatePointTop,candidatePointBot,sigma);
		
		//Use it to interpolate the image
		ImagePlus result=rewriteUponGap(img,imgMask,field,candidatePointTop,radiusX,radiusY);
		
		img.resetDisplayRange();
		result.resetDisplayRange();
		img.show();
		result.show();
	
		/*		ArrayList<int[]>correspondencesIndices=getSmartCorrespondences(img,candidatePointTop,candidatePointBot,radiusX,radiusY,minRsquared,minVariance,radiusSearch,sigma);
			System.out.println("Smart corr have "+correspondencesIndices.size());
		
		//Interpolate the corresponding euclidean vectors to discrete vector field
		ImagePlus[]field=getTheDeformationField(img,correspondencesIndices,candidatePointTop,candidatePointBot,sigma);
		field[0].resetDisplayRange();
//		field[0].show();
//		VitimageUtils.waitFor(1000000);
			
		//Use it to interpolate the image
		ImagePlus result=rewriteUponGap(img,imgMask,field,candidatePointTop,radiusX,radiusY);
*/
		
	}

	
	public static ImagePlus rewriteUponGap(ImagePlus img,ImagePlus imgMask,ImagePlus[]field,ArrayList<Point2d>candidatePointTop,int radiusX,int radiusY) {
		ImagePlus result=img.duplicate();
		ImagePlus fieldX=field[0];
		ImagePlus fieldY=field[1];
		ImagePlus sumVals=VitimageUtils.nullImage(img);
		ImagePlus weightVals=VitimageUtils.nullImage(img);
		final int X=img.getWidth();
		final int Y=img.getHeight();
		final int Z=img.getNSlices();
		float[] valsX=(float[]) fieldX.getStack().getProcessor(1).getPixels();
		float[] valsY=(float[]) fieldY.getStack().getProcessor(1).getPixels();
		float[][] valsResult=new float[Z][];
		float[][] valsImg=new float[Z][];
		float[][] valsMask=new float[Z][];
		float[][] valsSumVals=new float[Z][];
		float[][] valsWeightVals=new float[Z][];
		for(int z=0;z<Z;z++) {
			valsResult[z]=(float[])result.getStack().getProcessor(z+1).getPixels();
			valsImg[z]=(float[])img.getStack().getProcessor(z+1).getPixels();
			valsMask[z]=(float[])imgMask.getStack().getProcessor(z+1).getPixels();
			valsSumVals[z]=(float[])sumVals.getStack().getProcessor(z+1).getPixels();
			valsWeightVals[z]=(float[])weightVals.getStack().getProcessor(z+1).getPixels();
		}

		
		
		//For each correspondence, write along the line all masked points
		int N=candidatePointTop.size();
		for(int i=0;i<N;i++){
//			System.out.println("Processing i="+i);
			//Get correspondences as vectors
			double xT=candidatePointTop.get(i).x;
			double yT=candidatePointTop.get(i).y;
			double vX=valsX[(int) (X*yT+xT)];
			double vY=valsY[(int) (X*yT+xT)];
			double vNorm=Math.sqrt(vX*vX+vY*vY);
//			System.out.println("V="+vX+","+vY+",   "+vNorm);
			double xB=xT+vX;
			double yB=yT+vY;		
		
			//Sample the vector from top to bottom
			for(double dv=0;dv<vNorm;dv+=0.2) {
				double coefTop=(vNorm-dv)/vNorm;
				double xH=xT+(dv/vNorm)*vX;
				double yH=yT+(dv/vNorm)*vY;
				
				//Around xY,yH, stamp with data from top
				for(int z=0;z<Z;z++) {
					for(int xx=-radiusX;xx<=radiusX;xx++) {
						for(int yy=-radiusY;yy<=radiusY;yy++) {
							if(valsMask[z][ (X*((int)(yH+yy))+(int)(xH+xx))]>0) {
								valsSumVals[z][ (X*((int)(yH+yy))+(int)(xH+xx))] += (coefTop) * valsImg[z][ (X*((int)(yT+yy))+(int)(xT+xx))];
								valsWeightVals[z][ (X*((int)(yH+yy))+(int)(xH+xx))] += (coefTop) ;
							}
						}
					}
				}
			}
			//Sample the vector from top to bottom
			for(double dv=0;dv<vNorm;dv+=0.2) {
				double coefBot=(vNorm-dv)/vNorm;
				double xH=xB-(dv/vNorm)*vX;
				double yH=yB-(dv/vNorm)*vY;
				
				//Around xY,yH, stamp with data from top
				for(int z=0;z<Z;z++) {
					for(int xx=-radiusX;xx<=radiusX;xx++) {
						for(int yy=-radiusY;yy<=radiusY;yy++) {
							if(valsMask[z][ (X*((int)(yH+yy))+(int)(xH+xx))]>0) {
								valsSumVals[z][ (X*((int)(yH+yy))+(int)(xH+xx))] += (coefBot) * valsImg[z][ (X*((int)(yB+yy))+(int)(xB+xx))];
								valsWeightVals[z][ (X*((int)(yH+yy))+(int)(xH+xx))] += (coefBot) ;
							}
						}
					}
				}
			}
		}
		
		
		//Divide summed values over mask by weight
		for(int x=0;x<X;x++) {
			for(int y=0;y<Y;y++) {
				for(int z=0;z<Z;z++) {
					if(valsMask[z][X*y+x]>0) {
						valsResult[z][X*y+x]=valsSumVals[z][X*y+x]/valsWeightVals[z][X*y+x];
					}
				}
			}
		}
		result.resetDisplayRange();
		return result;
	}

	public static ImagePlus[] getTheDeformationField(ImagePlus img,ArrayList<int[]>correspondencesIndices,ArrayList<Point2d>candidatePointTop,ArrayList<Point2d>candidatePointBot,double sigma) {
		int yMin=10000,yMax=0;		int N=correspondencesIndices.size();
		for(int i=0;i<N;i++) {
			int y=(int) candidatePointTop.get( correspondencesIndices.get(i)[0] ).y;
			int x=(int) candidatePointTop.get( correspondencesIndices.get(i)[0] ).x;
			System.out.println("We got x="+x);
			if( y<yMin )yMin=y;
			if( y>yMax )yMax=y;
		}
		int Y=yMax-yMin+1;
		System.out.println("At the end, yMin="+yMin+" and yMax="+yMax);
		
		
		ImagePlus imgRef=ij.gui.NewImage.createImage("Mask",img.getWidth(),Y,1,8,ij.gui.NewImage.FILL_BLACK);	
		Point3d[][]correspondancePoints=new Point3d[2][N];
		for(int i=0;i<N;i++){
			double xT=candidatePointTop.get(correspondencesIndices.get(i)[0]).x;
			double yT=candidatePointTop.get(correspondencesIndices.get(i)[0]).y-yMin;
			double xB=candidatePointBot.get(correspondencesIndices.get(i)[1]).x;
			double yB=candidatePointBot.get(correspondencesIndices.get(i)[1]).y-yMin;
			correspondancePoints[0][i]=new Point3d(xT,yT,0);
			correspondancePoints[1][i]=new Point3d(xB,yB,0);
			System.out.println("New correspondence : "+correspondancePoints[0][i]+", "+correspondancePoints[1][i]);
		}
		Image defField = ItkTransform.computeDenseFieldFromSparseCorrespondancePoints(correspondancePoints,imgRef,sigma,false);
		ImagePlus[] field=ItkImagePlusInterface.convertDisplacementFieldToImagePlusArrayAndNorm(defField);
		field[0]=VitimageUtils.uncropImageFloat(field[0], 0, yMin, 0, img.getWidth(), img.getHeight(), 1);
		field[1]=VitimageUtils.uncropImageFloat(field[1], 0, yMin, 0, img.getWidth(), img.getHeight(), 1);
		return field;
	}
		
	public static ArrayList<int[]> getSmartCorrespondences(ImagePlus img,ArrayList<Point2d>arTop,ArrayList<Point2d>arBot, int radiusX,int radiusY, double minRsquared,double minVariance,double radiusSearch, double sigma) {
		int N=arTop.size();
		int Z=img.getNSlices();
		ImagePlus imgSlice=new Duplicator().run(img,1,1,img.getNSlices(),img.getNSlices(),1,1);
		
		//Extract the patches top and bottom
		double[][]blocksTop=new double[N][(radiusX*2+1)*(radiusY*2+1)]; 
		double[][]blocksBot=new double[N][(radiusX*2+1)*(radiusY*2+1)]; 
		double[]varTop=new double[N];
		double[]varBot=new double[N];
		double[][]corr=new double[N][4];
		for(int i=0;i<N;i++) { 
//			blocksBot[i]=VitimageUtils.valuesOfBlockDoubleSlice(imgSlice,arBot.get(i).x-radiusX,arBot.get(i).y-radiusY/*,Z-1*/,arBot.get(i).x+radiusX,arBot.get(i).y+radiusY/*,Z-1*/);
//			blocksTop[i]=VitimageUtils.valuesOfBlockDoubleSlice(imgSlice,arTop.get(i).x-radiusX,arTop.get(i).y-radiusY/*,Z-1*/,arTop.get(i).x+radiusX,arTop.get(i).y+radiusY/*,Z-1*/);
			blocksBot[i]=VitimageUtils.valuesOfBlockDouble(img,arBot.get(i).x-radiusX,arBot.get(i).y-radiusY,Z-1,arBot.get(i).x+radiusX,arBot.get(i).y+radiusY,Z-1);
			blocksTop[i]=VitimageUtils.valuesOfBlockDouble(img,arTop.get(i).x-radiusX,arTop.get(i).y-radiusY,Z-1,arTop.get(i).x+radiusX,arTop.get(i).y+radiusY,Z-1);
/*			System.out.println(blocksBot[i][0]);
			System.out.println(VitimageUtils.statistics1D(blocksBot[i])[1]);
			VitimageUtils.waitFor(100000);*/
		}

		//For each top, select the best bottom
		for(int i=0;i<N;i++) {
			double maxCorr=-2;
			for(int ind=(int) (Math.max(0,i-radiusSearch));ind<Math.min(N,i+radiusSearch+1);ind++) {
				double score=correlationCoefficient(blocksTop[i],blocksBot[ind]);
				if(score>maxCorr) {
					maxCorr=score;
					corr[i]=new double[] {ind,maxCorr,VitimageUtils.statistics1D(blocksTop[i])[1],VitimageUtils.statistics1D(blocksBot[i])[1]};
				}
			}
		}
		
		//inspect all found correspondences
		ArrayList<int[]>correspondencesTmp=new ArrayList<int[]>();
		for(int i=0;i<N;i++) { 
			System.out.print("Corr between top "+i+" and bottom "+corr[i][0]+" gives corr="+VitimageUtils.dou(corr[i][1])+" with stddevs="+VitimageUtils.dou(corr[i][2])+" and "+VitimageUtils.dou(corr[i][3]));
			if(corr[i][2]<minVariance || corr[i][3]<minVariance) {System.out.print("Removed for variance");}
			else if(corr[i][1]<minRsquared) {System.out.print("Removed for correlation");}			
			else {correspondencesTmp.add(new int[] {i,(int) corr[i][0]});}
			System.out.println();
		}
		
		//Add weird correspondences for data far away from any information
		ArrayList<int[]>correspondences=new ArrayList<int[]>();
		int lastX=(int) (-1.5*sigma);
		int nextX=0;
		for(int curInd=0;curInd<correspondencesTmp.size();curInd++) {
			nextX=correspondencesTmp.get(curInd)[0];
			//if nextX and last X are too distant, add intermediary points
			if(nextX-lastX > 4*sigma) {
				for(double delta=1.5*sigma; delta<(nextX-lastX-1.5*sigma);delta+=1.5*sigma) {
					int insertInd=(int) (lastX+delta);
					correspondences.add(new int[] {insertInd,insertInd});
				}
			}
			correspondences.add(correspondencesTmp.get(curInd));
			lastX=nextX;
		}
		nextX=arTop.size()-1;
		if(nextX-lastX > 4*sigma) {
			for(double delta=1.5*sigma; delta<(nextX-lastX-1.5*sigma);delta+=1.5*sigma) {
				int insertInd=(int) (lastX+delta);
				correspondences.add(new int[] {insertInd,insertInd});
			}
		}
		
		for(int i=0;i<correspondences.size();i++)System.out.println("COCO "+correspondences.get(i)[0]+" -> "+correspondences.get(i)[1]);
		return correspondences;
	}
	    
	
	public static double correlationCoefficient(double X[], double Y[]) { 
		double epsilon=10E-20;
		if(X.length !=Y.length ) {return 0;}
		int n=X.length;
		double sum_X = 0, sum_Y = 0, sum_XY = 0; 
		double squareSum_X = 0, squareSum_Y = 0; 	
		for (int i = 0; i < n; i++) { 
			sum_X = sum_X + X[i]; 		
			sum_Y = sum_Y + Y[i]; 
			sum_XY = sum_XY + X[i] * Y[i]; 
			squareSum_X = squareSum_X + X[i] * X[i]; 
			squareSum_Y = squareSum_Y + Y[i] * Y[i]; 
		} 
		if(squareSum_X<epsilon || squareSum_Y<epsilon )return 0;
		// use formula for calculating correlation  
		// coefficient. 
		double result=(n * sum_XY - sum_X * sum_Y)/ (Math.sqrt((n * squareSum_X - sum_X * sum_X) * (n * squareSum_Y - sum_Y * sum_Y)));
		if(Math.abs((n * squareSum_X - sum_X * sum_X))<10E-10)return 0; //cas Infinity
		if(Math.abs((n * squareSum_Y - sum_Y * sum_Y))<10E-10)return 0; //cas Infinity
		return result;
	} 
	
	
	public static ArrayList<int[]> getDumbCorrespondences(ImagePlus img,ArrayList<Point2d>arTop,ArrayList<Point2d>arBot) {
		int N=arTop.size();
		ArrayList<int[]>ret=new ArrayList<int[]>();
		for(int i=0;i<N;i++)ret.add(new int[] {i,i});
		return ret;
	}

	
	
	public static Object[] identifyBoundaryPatches(ImagePlus img,ImagePlus imgMask,int radiusX,int radiusY) {
		ImageJ ij=new ImageJ();
		String outputData="";
		final int X=img.getWidth();
		final int Y=img.getHeight();
		final int Z=img.getNSlices();
		float[][] valsMask=new float[Z][];
		float[][] valsImg=new float[Z][];

		for(int z=0;z<Z;z++) {
			valsImg[z]=(float[])img.getStack().getProcessor(z+1).getPixels();
			valsMask[z]=(float[])imgMask.getStack().getProcessor(z+1).getPixels();
		}
		ArrayList<Point2d>ptTop=new ArrayList<Point2d>();
		ArrayList<Point2d>ptBot=new ArrayList<Point2d>();
		
		//Compute the centers of last blocks from the top
		for(int x=radiusX;x<X-radiusX;x++) {
			System.out.println("Processing x="+x);
			boolean found=false;
			int y=2;
			int yT=-1;
			while( ((y)<(Y-(radiusY+1))) && (!found) ) {
				y++;
				//System.out.println("To bottom, testing "+y);
				//Testing the cube centered at x,y,zMed
				for(int xx=-radiusX;xx<=radiusX;xx++) {
					for(int yy=-radiusY;yy<=radiusY;yy++) {
						for(int z=0;z<Z;z++) {
							if(valsMask[z][X*(y+yy)+(x+xx)]>0) {found=true;}
						}
						if(!found){yT=y-1;}
					}
				}
			}

			found=false;
			y=Y-radiusY;
			int yB=-1;
			while( ((y)>(radiusY+1)) && (!found) ) {
				y--;
				//System.out.println("To top, testing "+y);
				//Testing the cube centered at x,y,zMed
				for(int xx=-radiusX;xx<=radiusX;xx++) {
					for(int yy=-radiusY;yy<=radiusY;yy++) {
						for(int z=0;z<Z;z++) {
							if(valsMask[z][X*(y+yy)+(x+xx)]>0) {found=true;}
						}
						if(!found){yB=y+1;}
					}
				}
			}
			if(yB==-1 || yT==-1) {
				IJ.showMessage("Warning : yB,yT"+yB+" , "+yT);
				return null;
			}
			ptTop.add(new Point2d(x,yT));
			ptBot.add(new Point2d(x,yB));
			System.out.println("Added : "+new Point2d(x,yT)+" , "+new Point2d(x,yB));
		}			
		/*Debug
		for(int i=0;i<ptTop.size();i++) {
			valsImg[0][(int) (X*(ptTop.get(i).y)+(ptTop.get(i).x))]=i;
			valsImg[0][(int) (X*(ptBot.get(i).y)+(ptBot.get(i).x))]=i;
		}
		img.resetDisplayRange();
		img.show();
		 */
		return new Object[] {ptTop,ptBot};
	}
}
