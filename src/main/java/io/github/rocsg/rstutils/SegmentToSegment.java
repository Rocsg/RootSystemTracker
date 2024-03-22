package io.github.rocsg.rstutils;

import java.util.ArrayList;

import io.github.rocsg.fijiyama.common.Timer;
import io.github.rocsg.fijiyama.registration.TransformUtils;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import io.github.rocsg.rsml.Node;
import io.github.rocsg.rsml.RootModel;
import io.github.rocsg.rsml.FSR;
import io.github.rocsg.rsml.Root;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.Duplicator;

public class SegmentToSegment {

	public static void main(String[]args) {
		ImageJ ij=new ImageJ();
		String mainDataDir="/home/rfernandez/Bureau/A_Test/RSML";
		FSR sr= (new FSR());
		sr.initialize();
		String ml="1";
		String boite="00001";
		double proximityThreshold=3;
		RootModel rm=RootModel.RootModelWildReadFromRsml(mainDataDir+"/4_RSML/ML"+ml+"_Boite_"+boite+".rsml");	
		ImagePlus seqReg=IJ.openImage("/home/rfernandez/Bureau/A_Test/RSML/1_Registered/ML"+ml+"_Boite_"+boite+".tif");
		ImagePlus img=drawComplexByTime(rm,seqReg,proximityThreshold);
		img.show();
	}
	
	
	public static ImagePlus drawComplexByTime(RootModel rm, ImagePlus imgSequence,double proximityThreshold) {
		int N=imgSequence.getStackSize();
		ImagePlus []slices=VitimageUtils.stackToSlices(imgSequence);
		for(int i=1;i<=N;i++) {
			System.out.println("\n\n\n\n\n\nSTEP "+i);
			Object[]complex=rootModelComplexity(rm, proximityThreshold,i);
			slices[i-1]=drawComplex(slices[i-1],(ArrayList<double[]>)complex[1],proximityThreshold);
			slices[i-1]=VitimageUtils.writeBlackTextOnGivenImage("N="+(Double)complex[0],slices[i-1], 20, 50, 50);
			//if(i==8)VitimageUtils.waitFor(500000);
		}
		ImagePlus comp=VitimageUtils.slicesToStack(slices);
		ImagePlus img=VitimageUtils.compositeNoAdjustOf(imgSequence,comp);
		return img;
	}
	
	public static ImagePlus drawComplex(ImagePlus ref,ArrayList<double[]>pointsContact,double proximityThreshold) {
		ImagePlus img=VitimageUtils.nullImage(ref);
		IJ.run(img,"32-bit","");
		double radMin=4;
		double radMax=4;
		double valMin=3;
		double valMax=6;
		float[]tabVal=(float[])img.getStack().getPixels(1);
		int incr=0;
		int N=pointsContact.size();
		for(double[]contact:pointsContact) {
			//if((incr++)%20 ==0)System.out.println("drawComplex "+incr+"/"+N);			
			double dist=contact[0];
			double x=contact[1];
			double y=contact[2];
			double percent=dist/proximityThreshold;
			double rad=(radMax-percent*(radMax-radMin))*VitimageUtils.getVoxelSizes(img)[0];
			double color=valMax-percent*(valMax-valMin);
			VitimageUtils.drawCircleIntoImageFloat(img,rad , (int)x, (int)y, 0, color);
		}
		img.setDisplayRange(0, valMax);
		IJ.run(img,"Fire","");
		return img;
	}
	
	
	public static Object[] rootModelComplexity(RootModel rm,double proximityThreshold,int dayMax) {
		ArrayList<double[]>pointsContact=new ArrayList<double[]>();
		int totalAmbiguities=0;
		double dMin=proximityThreshold;
		int nRoot=rm.rootList.size();
		ArrayList<Root> arrRoot =  rm.rootList;
		Root[]tabRoot=new Root[arrRoot.size()];
		double[]xMin=new double[nRoot];
		double[]xMax=new double[nRoot];
		double[]yMin=new double[nRoot];
		double[]yMax=new double[nRoot];
		for(int i=0;i<nRoot;i++){
			tabRoot[i]=arrRoot.get(i);
			tabRoot[i].computeDistances();
			xMin[i]=tabRoot[i].getXMin();
			xMax[i]=tabRoot[i].getXMax();
			yMin[i]=tabRoot[i].getYMin();
			yMax[i]=tabRoot[i].getYMax();
		}
		
		ArrayList<Root> tabR1=new ArrayList<Root>();
		ArrayList<Root> tabR2=new ArrayList<Root>();
		int[]count=new int[] {0,0};
		for(int i1=0; i1<nRoot; i1++) {//100
			for(int i2=i1+1; i2<nRoot; i2++) {//100
				boolean touch=false;
				count[0]++;
				if(!couldIntersect(xMin[i1],xMin[i2],xMax[i1],xMax[i2],dMin))continue;
				if(!couldIntersect(yMin[i1],yMin[i2],yMax[i1],yMax[i2],dMin))continue;
				count[1]++;
				Root r1=tabRoot[i1];
				Root r2=tabRoot[i2];
				Node[]tabN1=(Node[]) r1.getNodesList().toArray(new Node[r1.getNodesList().size()]);
				Node[]tabN2=(Node[]) r2.getNodesList().toArray(new Node[r2.getNodesList().size()]);
				int N1=tabN1.length;
				int N2=tabN2.length;
				boolean []proxN1=new boolean[N1-1];
				boolean []proxN2=new boolean[N2-1];
				//System.out.println("Starting i1="+i1+" i2="+i2+" amb="+totalAmbiguities);
				for(int n1=1;n1<N1;n1++) {
					if(tabN1[n1].distance<2*dMin)continue;
					if(tabN1[n1].birthTime>dayMax)continue;
					for(int n2=1;n2<N2;n2++) {
						
						if(tabN2[n2].distance<2*dMin)continue;
						if(tabN2[n2].birthTime>dayMax)continue;
						double[]Astart=new double[] {tabN1[n1-1].x,tabN1[n1-1].y,0};
						double[]Astop=new double[] {tabN1[n1].x,tabN1[n1].y,0};
						double[]Bstart=new double[] {tabN2[n2-1].x,tabN2[n2-1].y,0};
						double[]Bstop=new double[] {tabN2[n2].x,tabN2[n2].y,0};
						double[] dist=distanceBetweenTwoSegments3D(Astart,Astop,Bstart,Bstop);
						if(dist[0]<=proximityThreshold) {
							pointsContact.add(dist);
							touch=true;
							proxN1[n1-1]=true;
							proxN2[n2-1]=true;
							System.out.println("Caught proximity \n - Root 1 "+r1+" \n - Root 2 "+r2+"\n    A : "+TransformUtils.stringVector(Astart, "Astart")+" , "+TransformUtils.stringVector(Astop, "Astop")+
																							   "\n    B : "+TransformUtils.stringVector(Bstart, "Bstart")+" , "+TransformUtils.stringVector(Bstop, "Bstop")+" .\n" );
						}
					}
				}
				
				//Extract connexe components (i.e. sequence a1-a2  , b-1-b2  where proxN1[a1-a2] 
				if(!touch)continue;
				//Traversal from start
				int nit=0;
				int amb1=0;
				int amb2=0;
				while(nit<(N1-1)) {
					while( (nit<(N1-1)) && proxN1[nit])nit++;//Get the first node not to be in proximity (mixing at start is not a point)
					while( (nit<(N1-1)) && !proxN1[nit])nit++;//Get the first node to be in proximity
					if(nit<(N1-1))amb1++;
				}
				nit=0;
				while(nit<(N1-1)) {
					while( (nit<(N1-1)) && proxN1[nit])nit++;//Get the first node not to be in proximity (mixing at start is not a point)
					while( (nit<(N1-1)) && !proxN1[nit])nit++;//Get the first node to be in proximity
					if(nit<(N1-1))amb2++;
				}
				//System.out.println("Amb1="+amb1);
				//System.out.println("Amb2="+amb2);
				totalAmbiguities+=(touch ? 1 : 0);//Math.max(amb1, amb2);
				System.out.println("\n---------NEXT---------\n");
			}
		}
		System.out.println("Total ambiguities="+totalAmbiguities);
		return new Object[] {new Double(totalAmbiguities),pointsContact};
	}

	
	
	public static double[] distanceBetweenTwoSegments2D (double[]Astart,double[]Astop,double[]Bstart,double[]Bstop ) {
		double[]newAstart=new double[] {Astart[0],Astart[1],0};
		double[]newAstop=new double[] {Astop[0],Astop[1],0};
		double[]newBstart=new double[] {Bstart[0],Bstart[1],0};
		double[]newBstop=new double[] {Bstop[0],Bstop[1],0};
		return distanceBetweenTwoSegments3D(newAstart, newAstop, newBstart, newBstop);
	}
		
	public static double[] distanceBetweenTwoSegments3D (double[]Astart,double[]Astop,double[]Bstart,double[]Bstop ) {
		double[]P1P0=TransformUtils.vectorialSubstraction(Astop, Astart);
		double[]Q1Q0=TransformUtils.vectorialSubstraction(Bstop, Bstart);
		double[]P0Q0=TransformUtils.vectorialSubstraction(Astart, Bstart);
		double a=TransformUtils.scalarProduct(P1P0,P1P0);
		double b=TransformUtils.scalarProduct(P1P0,Q1Q0);
		double c=TransformUtils.scalarProduct(Q1Q0,Q1Q0);
		double d=TransformUtils.scalarProduct(P1P0,P0Q0);
		double e=TransformUtils.scalarProduct(Q1Q0,P0Q0);
		double s=0;
		double t=0;
		double det=0;
		double bte=0;
		double ctd=0;
		double ate=0;
		double btd=0;
		double epsilon=0.00000001;
		double zero=epsilon;
		//	Compute a, b, c, d, e with dot products; 
		det = a*c - b*b;
		if (det > 0) /* nonparallel segments*/ { 
			bte = b*e;
			ctd = c*d;
			if (bte <= ctd) /* s <= 0 */	{ 
				if (e <= zero)  /*  t <= 0 (region 6) */ { 
					s = (-d >= a ? 1 : (-d > 0 ? -d/a : 0));
					t = 0; 
				}
				else if (e < c) /*  0 < t < 1 (region 5) */{
					s = 0; t = e/c; 
				} 
				else /*  t >= 1 (region 4) */{
					s = ((b-d) >= a ? 1 : ((b-d) > 0 ? (b-d)/a : 0)); t = 1; 
				}
			} else /*  s > 0*/		{ 
				s = bte - ctd;
				if (s >= det) /*  s >= 1 */			{ 
					if (b+e <= 0) /*  t <= 0 (region 8) */{
						s = (-d <= 0 ? 0 : (-d < a ? -d/a : 1));
						t = 0; 
					}
					else if (b+e < c) /*  < t < 1 (region 1) */	{ 
						s = 1; t = (b+e)/c;
					}
					else /*  t >= 1 (region 2) */			{ 
						s = ((b-d) <= 0 ? 0 : ((b-d) < a ? (b-d)/a : 1)); 
						t = 1; 
					} 
				} else /*  0 < s < 1 */		{ 
					ate = a*e;
					btd = b*d;
					if (ate <= btd) /*  t <= 0 (region 7) */					{
						s = (-d <= 0 ? 0 : (-d >= a ? 1 : -d/a));
						t = 0; 
					}
					else /*  t > 0 */			{ 
						t = ate - btd;
						if (t >= det) /*  t >= 1 (region 3) */			{ 
							s = ((b-d) <= 0 ? 0 : ((b-d) >= a ? 1 : (b-d)/a));
							t = 1; 
						}
						else /* 0 < t < 1 (region 0) */				{
							s /= det; t /= det; 
						}
					}
				}
			}
		}
		else /* parallel segments */	{ 
			if (e <= 0) {
				s = (-d <= 0 ? 0 : (-d >= a ? 1 : -d/a));
				t = 0; 
			} 
			else if (e >= c) {
				s = ((b-d) <= 0 ? 0 : ((b-d) >= a ? 1 : (b-d)/a)); t = 1; 
			}
			else { 
				s = 0; t = e/c;
			}
		}
		double[]ptA=TransformUtils.vectorialAddition(TransformUtils.multiplyVector(Astart, (1-s)),TransformUtils.multiplyVector(Astop, (s)));
		double[]ptB=TransformUtils.vectorialAddition(TransformUtils.multiplyVector(Bstart, (1-t)),TransformUtils.multiplyVector(Bstop, (t)));
//		System.out.println(TransformUtils.stringVector(ptA, "ptA"));
//		System.out.println(TransformUtils.stringVector(ptB, "ptB"));
		double distance=TransformUtils.norm(TransformUtils.vectorialSubstraction(ptA, ptB));
//		System.out.println("Distance="+distance);
		return new double[] {distance,ptA[0]*0.5+ptB[0]*0.5,ptA[1]*0.5+ptB[1]*0.5,ptA[2]*0.5+ptB[2]*0.5};
	}

	public static boolean couldIntersect(double valMin1,double valMin2,double valMax1,double valMax2,double threshold) {
		if((valMin1-threshold)>valMax2)return false;
		if((valMin2-threshold)>valMax1)return false;
		return true;
	}

}
