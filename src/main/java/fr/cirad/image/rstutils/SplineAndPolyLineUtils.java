package fr.cirad.image.rstutils;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import com.goebl.simplify.Simplify;
//import com.manyangled.snowball.analysis.interpolation.MonotonicSplineInterpolator;

import ij.ImageJ;


public class SplineAndPolyLineUtils {
	public static double[][]douglasPeuckerSimplification(double[][]ptsIn,double distanceMax){
		Pt2d[]tabPt=new Pt2d[ptsIn.length];
		for(int n=0;n<ptsIn.length;n++)tabPt[n]=new Pt2d(ptsIn[n][0],ptsIn[n][1]);
		Pt2d[]tabPtOut=new Simplify<Pt2d>(new Pt2d[0]).simplify(tabPt, distanceMax, true);
		double[][]tabOut=new double[tabPtOut.length][2];
		for(int n=0;n<tabPtOut.length;n++)tabOut[n]=new double[] {tabPtOut[n].getX(),tabPtOut[n].getY()};
		return tabOut;
	}

	
	public static void main(String[]args) {
		ImageJ ij=new ImageJ();
		testInterp2();
	}
	public static void testInterp2() {
		double[]xTab=new double[] {10,20,30,40,100,110,120,130};
		double[]yTab=new double[] {10,20,10,20,10,20,100,20};
		double[]xTry=new double[] {10,11,15,23,37,70,71,72,100,105,110,115,130};
		for(int i=0;i<xTry.length;i++){
			double xRes=linearInterpolation(xTry[i],xTab, yTab);
		  System.out.println("f1("+xTry[i]+")="+xRes);
		}
		System.out.println();
	}

	public static double linearInterpolation(double val,double[]xSource,double[]ySource) {
		int N=xSource.length;
		int indexUpper=0;
		if(val<=xSource[0])return ySource[0];
		if(val>=xSource[N-1])return ySource[N-1];
		while(xSource[indexUpper]<val)indexUpper++;
		int indexLower=indexUpper-1;
		double DX=xSource[indexUpper]-xSource[indexLower];
		double DY=ySource[indexUpper]-ySource[indexLower];
		double dx=(val-xSource[indexLower]);
		double dy=DY*dx/DX;
		return ySource[indexLower]+dy;
	}
	

	public static PolynomialSplineFunction getInterpolator2(double[]x,double[]y,boolean forceMonotonic) {
		double[]xd=x;
		double[]yd=y;
		if(x.length==2) {
			xd=new double[] {x[0],0.5*x[0]+0.5*x[1],x[1]};
			yd=new double[] {y[0],0.5*y[0]+0.5*y[1],y[1]};
		}
		else if(x.length==1) {
			xd=new double[] {x[0],x[0],x[0]};
			yd=new double[] {y[0],y[0],y[0]};
		}
		if(forceMonotonic && x.length<8){
			xd=new double[8];
			yd=new double[8];
			for(int n=0;n<x.length-1;n++) {xd[n]=x[n];yd[n]=y[n];}
			xd[7]=x[x.length-1];
			yd[7]=y[x.length-1];

			double DI=8-x.length;
			double DX=xd[7]-xd[x.length-1];
			double DY=yd[7]-yd[x.length-1];

			for(int n=x.length-1;n<7;n++) {
				double di=n-(x.length-1);
				double dx=DX*di/DI;
				double dy=DY*di/DI;
				xd[n]=x[x.length-1]+dx;
				yd[n]=y[y.length-1]+dy;
			}		
		}
		double[]xd2=new double[xd.length+1];
		double[]yd2=new double[yd.length+1];
		for(int i=0;i<xd.length;i++) {
			xd2[i]=xd[i];
			yd2[i]=yd[i];
		}
		for(int i=1;i<xd.length;i++) {
			if(yd2[i]<yd2[i-1]) {
				System.out.println("WARNING : DECREASING TIME :"+i+" : xd["+xd[i-1]+"->"+xd[i]+"] : yd["+yd[i-1]+"->"+yd[i]+"]" );
			};
		}
		double dx=(xd[xd.length-1]-xd[xd.length-2])*100000;
		double dy=(yd[xd.length-1]-yd[xd.length-2])*100000;
		xd2[xd.length]=xd[xd.length-1]+dx;
		yd2[xd.length]=yd[xd.length-1]+dy;
		for(int i=1;i<xd.length;i++) {
			if(xd2[i]==xd2[i-1]) {
				System.out.println("CRITICAL WARNING : : NON MONOTONIC SEQUENCE :"+i+" : xd["+xd[i-1]+"->"+xd[i]+"] : yd["+yd[i-1]+"->"+yd[i]+"]" );
				for(int j=0;j<xd2.length;j++) {xd2[j]+=j*0.1;System.out.println("|"+xd2[j]+"|");}
				
			};
		}
		
		PolynomialSplineFunction psf=null;
		//if(forceMonotonic)	psf=new MonotonicSplineInterpolator().interpolate(xd2, yd2);
		/*else */psf=new SplineInterpolator().interpolate(xd2, yd2);
		return psf;
	}
	
	public static void testInterp() {
		double[]xTab=new double[] {10,20,30,40,100,110,120,130};
		double[]yTab=new double[] {10,20,10,20,10,20,100,20};
		double[]xTry=new double[] {10,11,15,23,37,70,71,72,100,105,110,115,130};
		if(true) {
			xTab=new double[] {10,110,120,130};
			yTab=new double[] {10,100,110,120};
		}
		double[]xRes=splineInterpolation(xTab, yTab, xTry,false);
		for(int i=0;i<xTry.length;i++){
		  System.out.println("f1("+xTry[i]+")="+xRes[i]);
		}
		System.out.println();
		xRes=splineInterpolation(xTab, yTab, xTry,true);
		for(int i=0;i<xTry.length;i++){
		  System.out.println("f1("+xTry[i]+")="+xRes[i]);
		}
	}

	public static double[]splineInterpolation(double[]x,double[]y,double[]xTry,boolean forceMonotonic) {
		PolynomialSplineFunction spline =getInterpolator2(x, y,forceMonotonic);
		double[]xRes=new double[xTry.length];
		for(int i=0;i<xRes.length;i++)xRes[i]=spline.value(xTry[i]);
		return xRes;
	}	

}
