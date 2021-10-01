package fr.cirad.image.TimeLapseRhizo;

import java.awt.Rectangle;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

//import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.jgrapht.graph.WeightedPseudograph;
import org.openxmlformats.schemas.presentationml.x2006.main.impl.CTApplicationNonVisualDrawingPropsImpl;
import org.jgrapht.alg.interfaces.SpanningTreeAlgorithm.SpanningTree;
import org.jgrapht.alg.spanning.KruskalMinimumSpanningTree;


import fr.cirad.image.common.MostRepresentedFilter;
import fr.cirad.image.common.Timer;
import fr.cirad.image.common.TransformUtils;
import fr.cirad.image.common.VitimageUtils;
import fr.cirad.image.fijiyama.RegistrationAction;
import fr.cirad.image.TimeLapseRhizo.HungarianAlgorithm;
import fr.cirad.image.registration.BlockMatchingRegistration;
import fr.cirad.image.registration.ItkTransform;
import fr.cirad.image.registration.Transform3DType;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.plugin.Duplicator;
import ij.plugin.Scaler;
import ij.plugin.frame.RoiManager;
import inra.ijpb.morphology.Morphology;
import inra.ijpb.morphology.Strel3D;
import net.imagej.table.DefaultByteTable;

public class TestImageSequence {	
	static boolean testing=true;
	static final String mainDataDir=testing ? "/home/rfernandez/Bureau/A_Test/RSML"
			: "/media/rfernandez/DATA_RO_A/Roots_systems/Data_BPMP/Second_dataset_2021_07/Processing";
	static int SIZE_FACTOR=8;
	static int TYPICAL_SPEED=100/8;//pixels/timestep. pix=19µm , timestep=8h, meaning TYPICAL=237 µm/h
	static double OUT_OF_SILHOUETTE_PENALTY=100;//5+2*daymax 50 100 1000
	static double REVERSE_TIME_PENALTY=100;//
	static double SEMI_PENALTY=50;
	static double IDENTITY_PENALTY=100;
	static int MIN_SIZE=5;

//TODO 1 : tester le truc pour retirer les feuilles en progressif	
//TODO 2 : tester le resultat sur le calcul des dates
//TODO 3 : tester le recalage daisy-chain
//TODO 4 : tester le resultat sur le calcul des dates
//TODO 5 : valider la solution technique pré graphe

//TODO 11 : Chercher API pour CC et autres dans un jgrapht
//TODO 12 : esquisser la solution de recherche de graphe. Lister les contraintes, et proposer un algo général
	
	
	/**
	 * Remove the falling stem of arabidopsis from a time lapse sequence imgMask contains all the root system, and imgMask2 only the part that cannot have a arabidopsis stem (the lower part)
	 * 	 */
	public static ImagePlus[] removeLeavesFromSequence(ImagePlus imgInit,ImagePlus imgMaskInit,ImagePlus imgMask2Init,boolean highres) {
		ImagePlus imgMask2=VitimageUtils.invertBinaryMask(imgMask2Init);
		int factor=highres ? 4:1;
		ImagePlus[]tabMaskOut=VitimageUtils.stackToSlices(imgInit);
		ImagePlus[]tabMaskIn=VitimageUtils.stackToSlices(imgInit);
		ImagePlus[]tabInit=VitimageUtils.stackToSlices(imgInit);
		ImagePlus[]tabTot=VitimageUtils.stackToSlices(imgInit);

		tabMaskOut[0]=VitimageUtils.nullImage(tabMaskOut[0]);
		tabMaskIn[0]=VitimageUtils.invertBinaryMask(tabMaskOut[0]);
		for(int z=1;z<tabInit.length;z++) {
			boolean blabla=false;
			if(z>105)blabla=true;
			System.out.println(z);
			//Get the big elements of object under the menisque
			ImagePlus img=VitimageUtils.makeOperationBetweenTwoImages(tabInit[z], imgMaskInit, 2, true);
			//VitimageUtils.showWithParams(img, "img", 0, 0, 255);
			//VitimageUtils.waitFor(5000);
			img=dilationCircle2D(img, 2*factor);
			img=VitimageUtils.gaussianFiltering(img, 3*factor, 3*factor, 0);
			ImagePlus biggas=VitimageUtils.thresholdImage(img, -100, 120);
			tabMaskOut[z]=VitimageUtils.binaryOperationBetweenTwoImages(imgMask2.duplicate(), biggas, 2);
			tabMaskOut[z]=VitimageUtils.binaryOperationBetweenTwoImages(imgMaskInit, tabMaskOut[z], 2);
			tabMaskOut[z]=dilationCircle2D(tabMaskOut[z], 2*factor);
			tabMaskOut[z].setDisplayRange(0, 1);
			tabMaskOut[z].setTitle(" "+z);
			
			//Combine this mask with the one of the previous image
			tabMaskOut[z]=VitimageUtils.binaryOperationBetweenTwoImages(tabMaskOut[z-1], tabMaskOut[z], 1);
			tabMaskIn[z]=VitimageUtils.invertBinaryMask(tabMaskOut[z]);

			//Replace area masked with surrounding areas
			ImagePlus imgInitDil=dilationLine2D(tabInit[z], 50*factor, true);
			ImagePlus imgPart1=VitimageUtils.makeOperationBetweenTwoImages(tabMaskIn[z], tabInit[z], 2, false);
			ImagePlus imgPart2=VitimageUtils.makeOperationBetweenTwoImages(tabMaskOut[z], imgInitDil, 2, false);
			tabTot[z]=VitimageUtils.makeOperationBetweenTwoImages(imgPart1, imgPart2, 1, false);
			VitimageUtils.showWithParams(tabTot[z], "tabTot "+z, 0, 0,255  );
		}
		return new ImagePlus [] {VitimageUtils.slicesToStack(tabTot),VitimageUtils.slicesToStack(tabMaskOut)};
	}
	
	
	
	
	public static void main(String[]args) {
		ImageJ ij=new ImageJ();
		//SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph=readGraphFromFile(mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite);
//		testGraph(graph);
//		VitimageUtils.waitFor(50000000);
/*		runRegisterSequences();
		VitimageUtils.waitFor(50000000);
		ImagePlus img=IJ.openImage(mainDataDir+"/1_Registered/ML1_Boite_00002.tif");
		ImagePlus imgMask1=IJ.openImage(mainDataDir+"/1_Mask/ML1_Boite_00002.tif");
		ImagePlus imgMask2=IJ.openImage(mainDataDir+"/1_Mask_Feuilles/ML1_Boite_00002.tif");
		img.show();
		ImagePlus img2=removeLeavesFromSequence(img, imgMask1, imgMask2);
		img2.show();
		VitimageUtils.compositeNoAdjustOf(img, img2).show();
	*/	
		//ImagePlus imgMask=IJ.openImage(mainDataDir+"/1_Mask/ML1_Boite_00002.tif");
		
		//removeLeaf(1,2);
		//VitimageUtils.waitFor(1000000);
/*		
 * 
 * testRegistration();
		IJ.showMessage("Done");
		VitimageUtils.waitFor(600000000);
 * ImagePlus img=IJ.openImage(mainDataDir+"/1_Registered/ML1_Boite_00002.tif");

		img2.show();
		VitimageUtils.waitFor(5000000);*/
/*		ImagePlus img=IJ.openImage("/home/rfernandez/Bureau/test.tif");
		img=VitimageUtils.convertFloatToByteWithoutDynamicChanges(img);
		img.setTitle("Before");
		img.setDisplayRange(0, 22);
		//img.show();
				
		ImagePlus img2=MostRepresentedFilter.mostRepresentedFilteringWithRadius(img,1.5,false,23,false);
		img2.setTitle("After");
		img2.setDisplayRange(0, 22);
		img2.show();
*/		
//		test();
		//runComputeMaskAndRemoveLeaves();
		//VitimageUtils.waitFor(50000000);
		for(int mli=1;mli<=1;mli++) {
			for(int boi=1;boi<=1;boi++) {
				String boite=(  (boi<10) ? ("0000"+boi) : ( (boi<100) ? ("000"+boi) : ("00"+boi) ) );
				String ml=""+mli;
				System.out.println("Processing ML"+ml+"_Boite_"+boite);
				//runImportSequences(ml,boite);
				//runRegisterSequences(ml,boite);
				//runComputeMaskAndRemoveLeaves(ml,boite);
				//runDateEstimation(ml,boite);
				buildAndProcessGraph(ml,boite);
				IJ.showMessage("Done");
				VitimageUtils.waitFor(600000000);
			}
		}
		//	runImportSequences();
		//runDateEstimation();
		//runRegisterSequences();
//		  runDateEstimation();
//		runDateEstimation() ;
	}

	public static void test() {
		ImagePlus img=IJ.openImage("/home/rfernandez/Bureau/test.tif");
		img=resizeNearest(img,200, 200, 1);
		img=VitimageUtils.nullImage(img);
		IJ.run(img,"8-bit","");
		int thick=1;
		double angle=5;
		VitimageUtils.drawCircleIntoImage(img, 10, 100, 100, 1,255);
		img.show();
		VitimageUtils.waitFor(10000000);
	}

	
	/** Main entry points --------------------------------------------------------------------------------------------------------------------------------------------------------*/
	public static void runImportSequences(String ml, String boite) {
		ImagePlus img=importTimeLapseSerie(""+ml, boite,".jpg",null,false);
		if(img!=null) {
			int X=img.getWidth();
			int Y=img.getHeight();
			int Z=img.getStackSize();
			System.out.print(" resize...");
			VitimageUtils.adjustImageCalibration(img, new double[] {19,19,19}, "µm");
			IJ.saveAsTiff(img, mainDataDir+"/0_Stacked_Highres/ML"+ml+"_Boite_"+boite);
			img=resize(img, X/4, X/4, Z);
			VitimageUtils.adjustImageCalibration(img, new double[] {19*4,19*4,19*4}, "µm");
			IJ.saveAsTiff(img, mainDataDir+"/0_Stacked/ML"+ml+"_Boite_"+boite);
		}				
	}
			
	public static void runRegisterSequences(String ml, String boite) {
		ImagePlus []imgs=registerImageSequence(""+ml,boite,4,false);
		System.out.println("Did !");
		System.out.println("Saving as "+mainDataDir+"/1_Registered/ML"+ml+"_Boite_"+boite+".tif");
		IJ.saveAsTiff(imgs[1], mainDataDir+"/1_Registered/ML"+ml+"_Boite_"+boite+".tif");				
		IJ.saveAsTiff(imgs[0], mainDataDir+"/1_Registered_High/ML"+ml+"_Boite_"+boite+".tif");				
	}

	public static void runComputeMaskAndRemoveLeaves(String ml, String boite) {
		boolean highRes=false;
		ImagePlus imgReg=IJ.openImage(mainDataDir+"/1_Registered"+(highRes ? "_High":"")+"/ML"+ml+"_Boite_"+boite+".tif");
		ImagePlus imgMask1=VitimageUtils.getBinaryMaskUnary(getInterestAreaMask(new Duplicator().run(imgReg,1,1,1,1,1,1),highRes),0.5);
		ImagePlus imgMaskN=VitimageUtils.getBinaryMaskUnary(getInterestAreaMask(new Duplicator().run(imgReg,1,1,imgReg.getStackSize(),imgReg.getStackSize(),1,1),highRes),0.5);
		IJ.saveAsTiff(imgMask1, mainDataDir+"/1_Mask_1/ML"+ml+"_Boite_"+boite+".tif");
		IJ.saveAsTiff(imgMask1, mainDataDir+"/1_Mask_N/ML"+ml+"_Boite_"+boite+".tif");
		ImagePlus imgMask2=	erosionCircle2D(imgMask1, 200*(highRes ? 4 : 1));
		IJ.saveAsTiff(imgMask2, mainDataDir+"/1_Mask_Feuilles/ML"+ml+"_Boite_"+boite+".tif");		
		ImagePlus []imgsOut=removeLeavesFromSequence(imgReg, imgMask1, imgMask2,highRes);
		IJ.saveAsTiff(imgsOut[0],mainDataDir+"/1_Remove_Leaves/ML"+ml+"_Boite_"+boite+".tif");
		IJ.saveAsTiff(imgsOut[1],mainDataDir+"/1_Mask_Of_Leaves/ML"+ml+"_Boite_"+boite+".tif");
	}	
	
	public static void runDateEstimation(String ml, String boite) {
		ImagePlus imgIn=IJ.openImage(mainDataDir+"/1_Remove_Leaves/ML"+ml+"_Boite_"+boite+".tif");
		ImagePlus imgMask1=IJ.openImage(mainDataDir+"/1_Mask_1/ML"+ml+"_Boite_"+boite+".tif");
		ImagePlus imgMaskN=IJ.openImage(mainDataDir+"/1_Mask_N/ML"+ml+"_Boite_"+boite+".tif");
		ImagePlus imgMaskOfLeaves=IJ.openImage(mainDataDir+"/1_Mask_Of_Leaves/ML"+ml+"_Boite_"+boite+".tif");
		
//				imgIn=new Duplicator().run(imgIn,1,1,1,15,1,1);
		ImagePlus mire=computeMire(imgIn);
		imgIn=VitimageUtils.addSliceToImage(mire, imgIn);
		if(imgIn==null)return;
		imgIn.show();
		//ImagePlus imgOut=projectTimeLapseSequenceInColorspace(imgIn, imgMask,30);
		ImagePlus imgOut=projectTimeLapseSequenceInColorspaceCombined(imgIn, imgMask1,imgMaskN,imgMaskOfLeaves,20);
		VitimageUtils.showWithParams(imgOut.duplicate(), "imgOut", 0, 0, 22);
		ImagePlus img2=VitimageUtils.thresholdImage(imgOut, 0.5, 100000);
		img2=VitimageUtils.connexe(img2, 1, 10000, 1000*VitimageUtils.getVoxelVolume(img2), 1E10*VitimageUtils.getVoxelVolume(img2), 4, 0, false);
		img2=VitimageUtils.thresholdImage(img2, 0.5, 1E8);
		img2=VitimageUtils.getBinaryMaskUnary(img2, 0.5);
		IJ.run(img2,"8-bit","");
		imgOut=VitimageUtils.makeOperationBetweenTwoImages(imgOut, img2, 2, true);
		IJ.run(imgOut,"Fire","");
		imgOut.setDisplayRange(-1, 22);
		VitimageUtils.showWithParams(imgOut.duplicate(), "imgOut2", 0, 0, 22);
		IJ.saveAsTiff(imgOut, mainDataDir+"/2_Date_maps/ML"+ml+"_Boite_"+boite+".tif");
	}

	public static void runBuildGraphs(String ml,String boite) {
		ImagePlus imgOut=buildAndProcessGraph(""+ml,boite);
		IJ.saveAsTiff(imgOut, mainDataDir+"/3_Graphs/ML"+ml+"_Boite_"+boite);
	}

	/*
	public static void editGraph(SimpleDirectedWeightedGraph<CC,ConnectionEdge>graph) {
		ArrayList<int[]>listToAdd=new ArrayList<int[]>();
		listToAdd.add( {   ,  ,  ,  ,  ,  });
		listToAdd.add( {   ,  ,  ,  ,  ,  });
		listToAdd.add( {   ,  ,  ,  ,  ,  });
		listToAdd.add( {   ,  ,  ,  ,  ,  });
		listToAdd.add( {   ,  ,  ,  ,  ,  });
		listToAdd.add( {   ,  ,  ,  ,  ,  });
		listToAdd.add( {   ,  ,  ,  ,  ,  });
		for(int[]tab : listToAdd) {
			CC source=getCC(graph,tab[0],tab[1]/SIZE_FACTOR,tab[2]/SIZE_FACTOR);
			CC target=getCC(graph,tab[3],tab[4]/SIZE_FACTOR,tab[5]/SIZE_FACTOR);
			graph.addEdge(source, target);
		}
		
		ArrayList<int[]>listToRemove=new ArrayList<int[]>();
		listToRemove.add( {   ,  ,  ,  ,  ,  });
		listToRemove.add( {   ,  ,  ,  ,  ,  });
		listToRemove.add( {   ,  ,  ,  ,  ,  });
		listToRemove.add( {   ,  ,  ,  ,  ,  });
		listToRemove.add( {   ,  ,  ,  ,  ,  });
		listToRemove.add( {   ,  ,  ,  ,  ,  });
		listToRemove.add( {   ,  ,  ,  ,  ,  });
		for(int[]tab : listToRemove) {
			CC source=getCC(graph,tab[0],tab[1]/SIZE_FACTOR,tab[2]/SIZE_FACTOR);
			CC target=getCC(graph,tab[3],tab[4]/SIZE_FACTOR,tab[5]/SIZE_FACTOR);
			graph.removeEdge(source, target);
		}

	}
	*/
	
	
	
	
	
	/// Various helpers for graph manipulation //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	@SuppressWarnings("unchecked")
	public static ImagePlus buildAndProcessGraph(String ml, String boite) {
		double ray=5;
		int thickness=5;
		int sizeFactor=SIZE_FACTOR;
		int connexity=8;

		//Import and oversample image of dates
		ImagePlus imgDatesTmp=IJ.openImage(mainDataDir+"/2_Date_maps/ML"+ml+"_Boite_"+boite+".tif");
		if(imgDatesTmp==null)return null;
		ImagePlus imgDatesHigh=VitimageUtils.convertFloatToByteWithoutDynamicChanges(imgDatesTmp);
		IJ.run(imgDatesHigh,"Fire","");
		int nDays=1+(int)Math.round(VitimageUtils.maxOfImage(imgDatesTmp));
		imgDatesHigh.setDisplayRange(0, nDays);		
		imgDatesHigh=resizeNearest(imgDatesTmp, imgDatesTmp.getWidth()*sizeFactor,imgDatesTmp.getHeight()*sizeFactor,1);

	
		//Build and duplicate initial graph
		boolean compute=false;
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph;
		if(compute) {
			graph=buildGraphFromDateMap(imgDatesTmp,connexity);
			pruneGraph(graph, true);
			setFirstOrderCosts(graph);
			writeGraphToFile(graph,mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite);
		}
		else graph=readGraphFromFile(mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite);

					
		//Identify trunks 
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph2=readGraphFromFile(mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite);	
		identifyTrunks(graph2);
		computeMinimumDirectedConnectedSpanningTree(graph2);
	
		//Identify trunks 
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph3=readGraphFromFile(mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite);
		identifyTrunks(graph3);
		computeMinimumDirectedConnectedSpanningTree(graph3);
		disconnectUnevenBranches(graph3);

		
		//Identify trunks 
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph4=readGraphFromFile(mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite);
		identifyTrunks(graph4);
		computeMinimumDirectedConnectedSpanningTree(graph4);
		disconnectUnevenBranches(graph4);
		reconnectDisconnectedBranches(graph4,0,false);		//reconnectSingleBranches(graph4);

		//Identify trunks 
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph5=readGraphFromFile(mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite);
		identifyTrunks(graph5);
		computeMinimumDirectedConnectedSpanningTree(graph5);
		disconnectUnevenBranches(graph5);
		reconnectDisconnectedBranches(graph5,1,false);		//reconnectSingleBranches(graph4);
		//Process along trunks
		
		//Identify trunks 
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph6=readGraphFromFile(mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite);
		identifyTrunks(graph6);
		computeMinimumDirectedConnectedSpanningTree(graph6);
		disconnectUnevenBranches(graph6);
		reconnectDisconnectedBranches(graph6,1,true);		//reconnectSingleBranches(graph4);
		
		//Render the graphs
		ImagePlus imgG1=drawGraph(imgDatesTmp, graph, ray, thickness,sizeFactor);
		ImagePlus imgG2=drawGraph(imgDatesTmp, graph2, ray, thickness,sizeFactor);		
		ImagePlus imgG3=drawGraph(imgDatesTmp, graph3, ray, thickness,sizeFactor);		
		ImagePlus imgG4=drawGraph(imgDatesTmp, graph4, ray, thickness,sizeFactor);		
		ImagePlus imgG5=drawGraph(imgDatesTmp, graph5, ray, thickness,sizeFactor);		
		ImagePlus imgG6=drawGraph(imgDatesTmp, graph6, ray, thickness,sizeFactor);		
		ImagePlus graphs=VitimageUtils.slicesToStack(new ImagePlus[] {imgG1,imgG2,imgG3,imgG4,imgG5,imgG6});
		graphs=VitimageUtils.convertFloatToByteWithoutDynamicChanges(graphs);
		ImagePlus []datesTab=new ImagePlus[graphs.getStackSize()];for(int i=0;i<datesTab.length;i++)datesTab[i]=imgDatesHigh;
		ImagePlus dates=VitimageUtils.convertFloatToByteWithoutDynamicChanges(VitimageUtils.slicesToStack(datesTab));

		
		//Compute the combined rendering
		ImagePlus glob=VitimageUtils.hyperStackingChannels(new ImagePlus[] {dates,graphs});
		glob.setDisplayRange(0, nDays);
		IJ.run(graphs,"Fire","");
		glob.show();
		return imgG1;
	}
	
	/** From an image with pixel labels from 0-N, build the region adjacency graph of the components with label from 1 to N*/	
	public static SimpleDirectedWeightedGraph buildGraphFromDateMap(ImagePlus imgDates,int connexity) {
		int maxSizeConnexion=500000000;
		int minSizeCCVoxels=20;
		int nDays=1+(int)Math.round(VitimageUtils.maxOfImage(imgDates));
		Roi[][]roisCC=new Roi[nDays][];
		CC[][]tabCC=new CC[nDays][];
		SimpleDirectedWeightedGraph<CC,ConnectionEdge>graph=new SimpleDirectedWeightedGraph<>(ConnectionEdge.class);

		
		//Identify connected components with label 1-N
		roisCC[0]=new Roi[] {new Roi(new Rectangle(0,0,imgDates.getWidth(),imgDates.getHeight()))};
		tabCC[0]=new CC[] {new CC(0,0,roisCC[0][0])};		
		System.out.print("Identifying connected components ");
		for(int d=1;d<nDays;d++) {
			System.out.print(d+" ");
			ImagePlus binD=VitimageUtils.thresholdImage(imgDates, d, d+0.99);
			ImagePlus ccD=VitimageUtils.connexeBinaryEasierParamsConnexitySelectvol(binD, connexity, 0);
			ImagePlus allConD=VitimageUtils.thresholdImageToFloatMask(ccD, 0.5, 10E8);
			VitimageUtils.waitFor(100);
			roisCC[d]=segmentationToRoi(allConD);
			if(roisCC[d]==null) {tabCC[d]=null;continue;}
			tabCC[d]=new CC[roisCC[d].length];
			for(int n=0;n<roisCC[d].length;n++) {
				CC cc=new CC(d,n,roisCC[d][n]);
				tabCC[d][n]=cc;
				graph.addVertex(cc);
			}
		}
		System.out.println();
		
		//Identify connexions
		System.out.print("Identifying connexions ");
		for(int d1=1;d1<nDays;d1++) {
			System.out.print(d1+" ");
			for(int n1=0;n1<roisCC[d1].length;n1++) {
				for(int d2=1;d2<nDays;d2++) {
					for(int n2=0;n2<roisCC[d2].length;n2++) {
						if((d2<d1) || ( (d2==d1) && (n2<=n1) ))continue;
						double[] tabConn=tabCC[d1][n1].nFacets4connexe(tabCC[d2][n2]);
						int n=(int) Math.round(tabConn[0]);
						double x=tabConn[1];
						double y=tabConn[2];
						if(n>0 && n<maxSizeConnexion) {
							graph.addEdge(tabCC[d1][n1], tabCC[d2][n2],new ConnectionEdge(x, y, n,tabCC[d1][n1], tabCC[d2][n2]));
						}
					}
				}
			}
		}
		System.out.println();
		return graph;
	}
		
	public static void testGraph(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph) {
		int number=48;
		CC cc=null;
		for(CC cct :graph.vertexSet())if((number--)==0) {cc=cct;break;}
		System.out.println(cc);
		for(ConnectionEdge edge : graph.incomingEdgesOf(cc)) {
			System.out.println("Incoming : "+edge);
			CC target=graph.getEdgeSource(edge);
			System.out.println(graph.containsEdge(cc, target));
			System.out.println(graph.containsEdge(target,cc));
		}
		for(ConnectionEdge edge : graph.outgoingEdgesOf(cc)) {
			System.out.println("Outgoing : "+edge);
			CC source=graph.getEdgeTarget(edge);
			System.out.println(graph.containsEdge(cc, source));
			System.out.println(graph.containsEdge(source,cc));
		}
	}
		
	//Remove components not connected to any root
	public static void pruneGraph(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,boolean removeUnconnectedParts) {
		//Find border of the tree, and define center
		double minX=10000000;
		double maxX=0;
		for(CC cc : graph.vertexSet()) {
			if(cc.r.getContourCentroid()[0]>maxX)maxX=cc.r.getContourCentroid()[0];
			if(cc.r.getContourCentroid()[0]<minX)minX=cc.r.getContourCentroid()[0];
		}
		
		//Connect roots to a central point
		Roi r=new Roi(new Rectangle((int) ((minX+maxX)/2),10,1,1));
		CC source=new CC(0,1,r) ;
		source.stamp=1;
		source.componentLabel=1;
		graph.addVertex(source);		


		//Initialize CC search
		int incr=0;
		ArrayList<CC>list=new ArrayList<CC>();
		for(CC cc :graph.vertexSet()) {
			if(cc.day!=0) {
				cc.stamp=0;
				cc.componentLabel=0;			
				if(cc.day==1 && cc.nPixels>200) {incr++;list.add(cc);}
			}
		}
		System.out.println("Identified "+incr+" roots systems");
		for(CC cc:list)graph.addEdge(source, cc,new ConnectionEdge(source.r.getContourCentroid()[0], source.r.getContourCentroid()[1], 1,source, cc));
		
		int nbMov=1;int iter=0;
		while(nbMov>0) {
			nbMov=0;
			for(CC cc:graph.vertexSet()) {
				if(cc.stamp==2) {
					int lab=cc.componentLabel;
					for(ConnectionEdge edge:graph.edgesOf(cc)) {
						if(graph.getEdgeSource(edge).stamp==0) {
							graph.getEdgeSource(edge).stamp=1;
							graph.getEdgeSource(edge).componentLabel=lab;
						}
						if(graph.getEdgeTarget(edge).stamp==0) {
							graph.getEdgeTarget(edge).stamp=1;
							graph.getEdgeTarget(edge).componentLabel=lab;
						}
					}
					cc.stamp=3;
				}
			}
			for(CC cc:graph.vertexSet()) {
				if(cc.stamp==1) {
					nbMov++;
					cc.stamp=2;
				}
			}
		}		
		if(removeUnconnectedParts) {
			//Clean vertices and edges isolated from the root systems
			ArrayList<CC>ar=new ArrayList<CC>();
			for(CC cc:graph.vertexSet()) if(cc.stamp!=3) ar.add(cc);
			for(CC cc : ar)graph.removeVertex(cc);
		}
	}		//Connect roots
	
	public static SpanningTree<ConnectionEdge> computeMinimumSpanningTreeGraph(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph) {		
		System.out.println("Start spanning tree");
		SpanningTree<ConnectionEdge> tree=new KruskalMinimumSpanningTree<>(graph).getSpanningTree();
		System.out.println("Ok.");
		return tree;
	}
	
	public static SimpleDirectedWeightedGraph<CC,ConnectionEdge> computeMinimumDirectedSpanningTree(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graphInit) {		
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph=(SimpleDirectedWeightedGraph<CC,ConnectionEdge>)(graphInit.clone());
		for(CC cc:graph.vertexSet()) {
			if(cc.day==0)continue;
			ConnectionEdge edgeMin=null;
			double minCost=1E8;
			for(ConnectionEdge edge:graph.incomingEdgesOf(cc)) {
				if(graph.getEdgeWeight(edge)<minCost) {
					minCost=graph.getEdgeWeight(edge);
					edgeMin=edge;
				}
			}
			ArrayList<ConnectionEdge>list=new ArrayList<ConnectionEdge>();
			for(ConnectionEdge edge:graph.incomingEdgesOf(cc)) {
				if(edge!=edgeMin)list.add( edge  );
			}
			for(ConnectionEdge edge : list)graph.removeEdge(edge);
		}
		return graph;
	}
		
	public static void computeMinimumDirectedConnectedSpanningTree(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph) {		
		System.out.print("Computing connected directed minimum spanning tree");
		int maxDay=0;int currentCC=1;
		for(CC cc:graph.vertexSet()) {cc.stamp=( (cc.day==0) ? currentCC : 0); if(cc.day>maxDay)maxDay=cc.day;}
		int CCcon=0;
		int CCnoncon=0;
		int CCother=0;
		for(int i=1;i<=maxDay;i++) {
			for(CC cc:graph.vertexSet()) {
				if(cc.day!=i)continue;
				ConnectionEdge edgeMin=null;
				double minCost=1E16;
				ConnectionEdge edgeMinConnected=null;
				double minCostConnected=1E16;
				for(ConnectionEdge edge:graph.incomingEdgesOf(cc)) {
					if((graph.getEdgeWeight(edge)<minCostConnected) && (graph.getEdgeSource(edge).stamp==1) && ((cc.day==(graph.getEdgeSource(edge).day+1))  || (graph.getEdgeSource(edge).trunk))) {
						minCostConnected=graph.getEdgeWeight(edge);
						edgeMinConnected=edge;
					}
					if((graph.getEdgeWeight(edge)<minCost) && ((cc.day==(graph.getEdgeSource(edge).day+1)) || (graph.getEdgeSource(edge).trunk)))  {
						minCost=graph.getEdgeWeight(edge);
						edgeMin=edge;
					}
				}
				if(edgeMinConnected!=null) {
					for(ConnectionEdge edge:graph.incomingEdgesOf(cc))edge.activated=(edge==edgeMinConnected);
					cc.stamp=1;
					CCcon++;
				}
				else if(edgeMin!=null) {
					for(ConnectionEdge edge:graph.incomingEdgesOf(cc))edge.activated=(edge==edgeMin);
					cc.stamp=graph.getEdgeSource(edgeMin).stamp;
					CCnoncon++;
				}
				else {
					for(ConnectionEdge edge:graph.incomingEdgesOf(cc))edge.activated=false;
					cc.stamp=(++currentCC);
					CCother++;
				}
			}
		}				
		int nbAct=0;int nbInact=0;
		for(ConnectionEdge edge:graph.edgeSet()) {
			if(edge.activated)nbAct++;
			else nbInact++;
		}
		System.out.println(" , after do it : "+CCcon+" con , "+CCnoncon+" non-con , "+CCother+" other , "+nbAct+" activated , "+nbInact+" inactivated");
	}

	public static void disconnectUnevenBranches(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph) {
		boolean debug=false;
		for(CC cc:graph.vertexSet()) {
			if(cc.trunk)continue;
			ConnectionEdge bestEdge=bestOutgoingEdge(cc,graph);
			for(ConnectionEdge edge : graph.outgoingEdgesOf(cc))if(edge!=bestEdge)edge.activated=false;
		}
	}
	
	
	public static CC getActivatedLeafOfCC(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,CC cc) {
		CC ccNext=cc;
		while(bestOutgoingActivatedEdge(ccNext, graph)!=null)ccNext=bestOutgoingActivatedEdge(ccNext, graph).target;
		return ccNext;
	}
	
	public static CC getActivatedRootOfCC(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,CC cc) {
		CC ccPrev=cc;
		while((bestIncomingActivatedEdge(ccPrev, graph)!=null) && ((!bestIncomingActivatedEdge(ccPrev, graph).source.trunk)) && (!(bestIncomingActivatedEdge(ccPrev, graph).source.day<2)))ccPrev=bestIncomingActivatedEdge(ccPrev, graph).source;
		return ccPrev;
	}

	public static int isIn(CC cc, ArrayList<CC[]>tabCC) {
		for(int i=0;i<tabCC.size();i++) {
			if(cc==tabCC.get(i)[1])return i;
		}
		return -1;
	}
	
	public static void reconnectDisconnectedBranches(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,int formalism,boolean workAlsoBranches) {
		int Nalso=0;
		int[]associations=null;
		ArrayList<ConnectionEdge>tabKeepEdges=new ArrayList<ConnectionEdge>();
		ArrayList<CC[]>tabKeepCCStart=new ArrayList<CC[]>();
		if(workAlsoBranches) {
			for(CC cc:graph.vertexSet()) {
				if(!cc.trunk)continue;
				for(ConnectionEdge edge : graph.outgoingEdgesOf(cc)) {
					if(edge.activated) {
						edge.activated=false;
						tabKeepEdges.add(edge);
						tabKeepCCStart.add(new CC[] {cc,edge.target});
						Nalso++;
					}
				}
			}			
		}
		
		System.out.println("Reconnection of disconnected branches");
		//Identify the disconnected branches
		double thresholdScore=20;
		ArrayList<CC>listStart=new ArrayList<CC>();
		for(CC cc: graph.vertexSet()) {
			cc.stamp=0;
			if(cc.trunk)continue;
			if(cc.day<2)continue;
			if(bestIncomingActivatedEdge(cc, graph)==null) {
//				System.out.println("Found a disconnected branch : "+cc);
				CC cctmp=cc;
				int tot=cc.nPixels;
				while(bestOutgoingActivatedEdge(cctmp, graph)!=null) {
					cctmp=bestOutgoingActivatedEdge(cctmp, graph).target;
					tot+=cctmp.nPixels;
				}
				//Count size
//				System.out.println(" with "+tot+" elements");
				if(tot>=MIN_SIZE && cc.y()>150) {
					listStart.add(cc);
					cc.stamp=tot;
				}
			}
		}

		
		//Identify the possible dead ends 
		ArrayList<CC>listStop=new ArrayList<CC>();
		for(CC cc: graph.vertexSet()) {
			if(cc.trunk)continue;
			if(bestOutgoingActivatedEdge(cc, graph)==null) {
//				System.out.print("Found a disconnected stop : "+cc);
				CC cctmp=cc;
				int tot=cc.nPixels;
				while(bestIncomingActivatedEdge(cctmp, graph)!=null && (!bestIncomingActivatedEdge(cctmp, graph).trunk && (bestIncomingActivatedEdge(cctmp, graph).source.day>1))) {
//					System.out.print(tot+" ");
//					VitimageUtils.waitFor(500);
					cctmp=bestIncomingActivatedEdge(cctmp, graph).source;
					tot+=cctmp.nPixels;
				}
				//Count size
//				System.out.println("\n with "+tot+" elements");
				if(tot>=MIN_SIZE && cc.y()>150) {
					listStop.add(cc);
					cc.stamp=tot;
				}
			}
		}
		int Nstart=listStart.size();
		int Nstop=listStop.size();
		if(Nalso>0)associations=new int[Nalso+Nstop];

		Timer t=new Timer();
		t.print("Start");
		
		if(formalism==1) {
			System.out.println("Running hungarian algorithm");
			int incr=0;
			//Algorithme hongrois
			double[][]costMatrix=new double[Nstop+Nalso][Nstart];
			double[][]costMatrix2=new double[Nstop+Nalso][Nstart];
			int nTot=Nstart*Nstop;
			int incr2=0;
			CC ccStopTest=getCC(graph,22,4713,2562);
			CC ccStartTest=getCC(graph,20,9074,4076);
			for(int i=0;i<Nstop;i++) {
	            for(int j=0;j<Nstart;j++) {
	            	incr++;
	            	if(listStop.get(i)==listStart.get(j))costMatrix[i][j]=IDENTITY_PENALTY;
	            	else costMatrix[i][j]=weightingOfPossibleHiddenEdge(graph,listStop.get(i),listStart.get(j),false);
	            	costMatrix2[i][j]=costMatrix[i][j];
	            }
	        }
			int in=0;
			for(int j=0;j<Nstart;j++) {
				for(int i=Nstop;i<Nstop+Nalso;i++) costMatrix[i][j]=1E8;
				if(isIn(listStart.get(j),tabKeepCCStart)>=0) {
					costMatrix[Nstop+(in)][j]=SEMI_PENALTY;
					associations[Nstop+(in++)]=isIn(listStart.get(j),tabKeepCCStart);
				}
			}
			HungarianAlgorithm hung=new HungarianAlgorithm(costMatrix);
			int []solutions=hung.execute();
			double meanScore=0;
			int N=0;
			for(int i=0;i<listStop.size();i++) {
				CC ccStop=listStop.get(i);
				if(solutions[i]==-1)continue;
				int j=solutions[i];
				CC ccStart=listStart.get(j);
				double minWeight=costMatrix[i][j];	
		    	meanScore+=minWeight;
		    	N++;
		    	
		    	System.out.println("\nGiven by hungarian at step "+N+" with weight "+minWeight+": \n "+  ccStop+"\n    "+ccStart);
				if(minWeight>thresholdScore)continue;
				//Connect ccStop et ccStart
				double xCon=ccStop.r.getContourCentroid()[0]*0.5+ccStart.r.getContourCentroid()[0]*0.5;
				double yCon=ccStop.r.getContourCentroid()[1]*0.5+ccStart.r.getContourCentroid()[1]*0.5;
				//System.out.println("At iteration "+incr+"choice between "+listStart.size()+" starts and "+listStop.size()+" stops.\n   for selected start="+ccStart+"\n   found corresponding stop="+ccStop+" nwith score "+minWeight);
				ConnectionEdge edge=new ConnectionEdge( xCon,yCon, 0, ccStop,ccStart); 
				edge.activated=true;
				edge.hidden=true;
				edge.trunk=false;
				try {
				graph.addEdge(ccStop, ccStart,edge); 
				graph.setEdgeWeight(edge, minWeight);
				getActivatedRootOfCC( graph,ccStop).stamp+=ccStart.stamp;
//				listStop.remove(ccStop);//Remove ccStop from list
				}catch(java.lang.IllegalArgumentException loops) {
					int iii=0;
					listStop.remove(ccStop);//Remove ccStop from list
				}
			}
		 	System.out.println( "Score moyen = "+(meanScore/N));
			for(int i=listStop.size();i<Nstop+Nalso;i++) {
				if(solutions[i]==-1) {System.out.println("Extinction de l edge "+i+"->"+associations[i]+" : "+graph.getEdge(tabKeepCCStart.get(associations[i])[0], tabKeepCCStart.get(associations[i])[1]));continue;}
				graph.getEdge(tabKeepCCStart.get(associations[i])[0], tabKeepCCStart.get(associations[i])[1]).activated=true;
				System.out.println("Rallumage de l edge "+i+"->"+associations[i]+" : "+graph.getEdge(tabKeepCCStart.get(associations[i])[0], tabKeepCCStart.get(associations[i])[1]));
			}
		}
		if(formalism==2) {
			int incr=0;
			int step=0;
			boolean finished=false;
			while(!finished) {
				System.out.println("Running progressive hungarian algorithm step "+(step++));
				//Algorithme hongrois
				double[][]costMatrix=new double[Nstop][Nstart];
				double[][]costMatrix2=new double[Nstop][Nstart];
				int nTot=Nstart*Nstop;
				int incr2=0;
				CC ccStopTest=getCC(graph,22,4713,2562);
				CC ccStartTest=getCC(graph,20,9074,4076);
				for(int i=0;i<Nstop;i++) {
		            for(int j=0;j<Nstart;j++) {
		            	incr++;
		            	if(listStop.get(i)==listStart.get(j))costMatrix[i][j]=IDENTITY_PENALTY;
		            	else costMatrix[i][j]=weightingOfPossibleHiddenEdge(graph,listStop.get(i),listStart.get(j),false);
		            	costMatrix2[i][j]=costMatrix[i][j];
		            }
		        }
				HungarianAlgorithm hung=new HungarianAlgorithm(costMatrix);
				int []solutions=hung.execute();
				double meanScore=0;
				int N=0;
				for(int i=0;i<listStop.size();i++) {
					CC ccStop=listStop.get(i);
					if(solutions[i]==-1)continue;
					int j=solutions[i];
					CC ccStart=listStart.get(j);
					double minWeight=costMatrix[i][j];	
			    	meanScore+=minWeight;
			    	N++;
			    	
			    	System.out.println("\nGiven by hungarian at step "+N+" with weight "+minWeight+": \n "+  ccStop+"\n    "+ccStart);
					if(minWeight>thresholdScore)continue;
					//Connect ccStop et ccStart
					double xCon=ccStop.r.getContourCentroid()[0]*0.5+ccStart.r.getContourCentroid()[0]*0.5;
					double yCon=ccStop.r.getContourCentroid()[1]*0.5+ccStart.r.getContourCentroid()[1]*0.5;
					//System.out.println("At iteration "+incr+"choice between "+listStart.size()+" starts and "+listStop.size()+" stops.\n   for selected start="+ccStart+"\n   found corresponding stop="+ccStop+" nwith score "+minWeight);
					ConnectionEdge edge=new ConnectionEdge( xCon,yCon, 0, ccStop,ccStart); 
					edge.activated=true;
					edge.hidden=true;
					edge.trunk=false;
					try {
					graph.addEdge(ccStop, ccStart,edge); 
					graph.setEdgeWeight(edge, minWeight);
					getActivatedRootOfCC( graph,ccStop).stamp+=ccStart.stamp;
	//				listStop.remove(ccStop);//Remove ccStop from list
					}catch(java.lang.IllegalArgumentException loops) {
						int iii=0;
						listStop.remove(ccStop);//Remove ccStop from list
					}
				}
			 	System.out.println( "Score moyen = "+(meanScore/N));
			 	if((listStart.size()==0 || listStop.size()==0))continue;
			}
		
		}
		else if(formalism==0) {
			System.out.println("Running greedy algorithm");
			int incr=50;
			while(listStart.size()>0 && (incr--)>0) {			
				//Traiter les branches en ordre de taille décroissante
				int bestVolume=0;
				CC ccStart=null;
				for(CC cc:listStart)if(cc.stamp>bestVolume) {bestVolume=cc.stamp;ccStart=cc;}
				
				boolean debug=false;//(ccStart==getCC(graph,18, 1586, 1741));
				double[]weight=new double[listStop.size()];
				for(int i=0;i<weight.length;i++) {
					CC ccTrialStop=listStop.get(i);
					if(ccTrialStop==ccStart)continue;
					weight[i]=weightingOfPossibleHiddenEdge(graph,ccTrialStop,ccStart,debug && (ccStart.euclidianDistanceToCC(ccTrialStop)<1000));//Mettre ici fonction de calcul
				}
				CC ccStop=null;
				double minWeight=1E18;
				for(int i=0;i<weight.length;i++) {
					if((weight[i]<minWeight)) {
						minWeight=weight[i];
						ccStop=listStop.get(i);
					}			
				}
	 
				
				if(minWeight<=thresholdScore) { 
					//Connect ccStop et ccStart
					double xCon=ccStop.r.getContourCentroid()[0]*0.5+ccStart.r.getContourCentroid()[0]*0.5;
					double yCon=ccStop.r.getContourCentroid()[1]*0.5+ccStart.r.getContourCentroid()[1]*0.5;
					//System.out.println("At iteration "+incr+"choice between "+listStart.size()+" starts and "+listStop.size()+" stops.\n   for selected start="+ccStart+"\n   found corresponding stop="+ccStop+" nwith score "+minWeight);
					ConnectionEdge edge=new ConnectionEdge( xCon,yCon, 0, ccStop,ccStart); 
					edge.activated=true;
					edge.hidden=true;
					edge.trunk=false;
					try {
					graph.addEdge(ccStop, ccStart,edge); 
					graph.setEdgeWeight(edge, minWeight);
					getActivatedRootOfCC( graph,ccStop).stamp+=ccStart.stamp;
					listStop.remove(ccStop);//Remove ccStop from list
					}catch(java.lang.IllegalArgumentException loops) {
						listStop.remove(ccStop);//Remove ccStop from list
					}
				}
				listStart.remove(ccStart);//Remove ccStart from list	
			}
		}
		if(workAlsoBranches) {
			for(ConnectionEdge edge:tabKeepEdges) {
				if(bestIncomingActivatedEdge(edge.target,graph)==null)edge.activated=true;
			}			
		}

		t.print("Stop");
	}

	public static double prodScal(CC cc1,CC cc2, CC cc3,CC cc4) {
		double[]vect1=new double[] {cc1.x(),cc1.y(),0};
		double[]vect2=new double[] {cc2.x(),cc2.y(),0};
		double[]vect3=new double[] {cc3.x(),cc3.y(),0};
		double[]vect4=new double[] {cc4.x(),cc4.y(),0};
		double[]vect12=TransformUtils.vectorialSubstraction(vect2, vect1);
		double[]vect34=TransformUtils.vectorialSubstraction(vect4, vect3);
		vect12=TransformUtils.normalize(vect12);
		vect34=TransformUtils.normalize(vect34);
		return TransformUtils.scalarProduct(vect12, vect34);
	}
	
	
	/** Determine if ccStop and ccStart are connected in the undirected region adjacency graph limited to ccStop, ccStart and older CC*/
	public static int areConnectedByPathOfOlderCC(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,CC ccStop,CC ccStart,boolean debug) {
		int Nmax=ccStart.day-1;
		for(CC cc: graph.vertexSet()) cc.stamp2=0;			
		ccStop.stamp2=1;
		ArrayList<CC>visited=new ArrayList<CC>();
		ArrayList<CC>toVisit=new ArrayList<CC>();
		visited.add(ccStop);
		if(bestIncomingActivatedCC(ccStop, graph)!=null)bestIncomingActivatedCC(ccStop, graph).stamp=1;
		boolean finished=false;
		int iter=-1;
		while(!finished) {
			iter++;
			if(debug)System.out.print("iter="+iter+" : "+visited.size());
			for(int i=0;i<visited.size();i++) {				
				CC ccTemp=visited.get(i);
				for(ConnectionEdge edge : graph.outgoingEdgesOf(ccTemp)) {
					CC ccTrial=edge.target;
					if( ccTrial.stamp2>0)continue;
					if (ccTrial.day<1)continue; 
					if( (ccTrial.day<=Nmax) || (ccTrial==ccStart) ) {
						toVisit.add(ccTrial);
						ccTrial.ccPrev=ccTemp;
						ccTrial.stamp2=1;
					}
				}
				for(ConnectionEdge edge : graph.incomingEdgesOf(ccTemp)) {
					CC ccTrial=edge.source;
					if( ccTrial.stamp2>0)continue;
					if (ccTrial.day<1)continue; 
					if( (ccTrial.day<=Nmax) || (ccTrial==ccStart) ) {
						toVisit.add(ccTrial);
						ccTrial.stamp2=1;
						ccTrial.ccPrev=ccTemp;
					}
				}			
			}
			if(toVisit.size()==0)finished=true;
			if(ccStart.stamp2==1)finished=true;
			visited=toVisit;
			toVisit=new ArrayList<CC>();
		}
		if(debug) {
			if(ccStart.stamp2==0)System.out.println("\nPath not found !");
			else {
				System.out.println("\nPath found");
				CC ccT=ccStart;
				while(ccT!=ccStop) {ccT=ccT.ccPrev;System.out.println(ccT);}
			}
		}
		if(ccStart.stamp2>0)return iter;
		else return -1;
	}
	
	
	public static double weightingOfPossibleHiddenEdge(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,CC ccStop,CC ccStart,boolean debug) {
		if(debug)System.out.println("\nH021 weighting with "+ccStop);
		if(ccStop.day>ccStart.day)return REVERSE_TIME_PENALTY;
		double weight=0;//Start at 0
		CC ccStartNext=bestOutgoingActivatedCC(ccStart, graph);
		if(ccStartNext !=null && ccStartNext.trunk)ccStartNext=null;
		CC ccStopPrevious=bestIncomingActivatedCC(ccStop, graph);
		if(ccStopPrevious !=null && ccStopPrevious.trunk)ccStopPrevious=null;

		//CONNECTED
		double connectedWeight=0;
		boolean pathFound=false;
		//if a way does not exist
		int way=areConnectedByPathOfOlderCC(graph,ccStop,ccStart,debug);
		if(way<0)connectedWeight=OUT_OF_SILHOUETTE_PENALTY;
		else connectedWeight=way;
		if(debug)System.out.println("H022 establishing connexity weight="+connectedWeight);

		
		//ESTIMATE SPEED AND ANGULAR CONFORMATION
		double speed=TYPICAL_SPEED;
		double angularWeight=0;
		if(ccStartNext!=null && ccStopPrevious !=null) {
			double speedStop=ccStop.euclidianDistanceToCC(ccStopPrevious)/(Math.min (1,ccStop.day-ccStopPrevious.day));
			double speedStart=ccStart.euclidianDistanceToCC(ccStartNext)/(Math.min (1,ccStartNext.day-ccStart.day));
			speed=0.5*(speedStart+speedStop);
			angularWeight-=prodScal(ccStopPrevious,ccStop,ccStart,ccStartNext);
			angularWeight-=prodScal(ccStopPrevious,ccStartNext,ccStop,ccStart);
			angularWeight-=prodScal(ccStopPrevious,ccStop,ccStop,ccStart);
			angularWeight-=prodScal(ccStop,ccStart,ccStart,ccStartNext);
		}
		else if(ccStartNext!=null){
			double speedStart=ccStart.euclidianDistanceToCC(ccStartNext)/(Math.min (1,ccStartNext.day-ccStart.day));
			speed=0.5*(speedStart+TYPICAL_SPEED);
			angularWeight-=prodScal(ccStop,ccStart,ccStart,ccStartNext)*4;
		}
		else if(ccStopPrevious!=null){
			double speedStop=ccStop.euclidianDistanceToCC(ccStopPrevious)/(Math.min (1,ccStop.day-ccStopPrevious.day));
			speed=0.5*(speedStop+TYPICAL_SPEED);
			angularWeight-=prodScal(ccStopPrevious,ccStop,ccStop,ccStart)*4;
		}
		angularWeight*=2;
		if(debug)System.out.println("Estimated speed="+speed+" and angweight="+angularWeight);
		

		
		//DISTANCE Is the pathway length likely ?
		double deltaDay=ccStart.day-ccStop.day;
		if (deltaDay<=0)deltaDay=0.75;
		double expectedDistance=speed*(deltaDay);
		double actualDistance=ccStop.euclidianDistanceToCC(ccStart);
		if(debug)System.out.println("H026 actualDist="+actualDistance);
		if(debug)System.out.println("H026 expectedDist="+expectedDistance);
		double distanceWeight=(1-similarity(expectedDistance, actualDistance))*deltaDay+Math.abs(actualDistance-expectedDistance)/(deltaDay*expectedDistance);
		if(debug)System.out.println("H026 distweight="+distanceWeight);

		
		//OVERALL ORIENTATION Is it pointing downwards ?
		double[]vectStart=new double[] {ccStart.x(),ccStart.y(),0};
		double[]vectStop=new double[] {ccStop.x(),ccStop.y(),0};
		double[]vectStopToStart=TransformUtils.vectorialSubstraction(vectStart, vectStop);
		vectStopToStart=TransformUtils.normalize(vectStopToStart);
		double orientationWeight=-vectStopToStart[1];
		if(debug)System.out.println("H027 orientweight="+orientationWeight);
		double finalWeight=connectedWeight + angularWeight + distanceWeight + orientationWeight;
		if(debug)System.out.println("H028 finalWeight="+finalWeight);
		return finalWeight;
	}
	
	
	public static ConnectionEdge bestOutgoingEdge(CC cc,SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph) {
		double minCost=1E18;
		ConnectionEdge bestEdge=null;
		if(graph.outgoingEdgesOf(cc).size()>0) {
			for(ConnectionEdge edge : graph.outgoingEdgesOf(cc)) {
				if(graph.getEdgeWeight(edge)<minCost) {
					minCost=graph.getEdgeWeight(edge);
					bestEdge=edge;
				}
			}
		}
		return bestEdge;
	}
	
	public static CC bestOutgoingActivatedCC(CC cc,SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph) {
		ConnectionEdge edge=bestOutgoingActivatedEdge(cc, graph);
		if(edge !=null)return edge.target;
		return null;
	}

	public static CC bestIncomingActivatedCC(CC cc,SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph) {
		ConnectionEdge edge=bestIncomingActivatedEdge(cc, graph);
		if(edge !=null)return edge.source;
		return null;
	}

	
	public static ConnectionEdge bestIncomingEdge(CC cc,SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph) {
		double minCost=1E18;
		ConnectionEdge bestEdge=null;
		if(graph.incomingEdgesOf(cc).size()>0) {
			for(ConnectionEdge edge : graph.incomingEdgesOf(cc)) {
				if(graph.getEdgeWeight(edge)<minCost) {
					minCost=graph.getEdgeWeight(edge);
					bestEdge=edge;
				}
			}
		}
		return bestEdge;
	}

	public static ConnectionEdge bestOutgoingActivatedEdge(CC cc,SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph) {
		double minCost=1E18;
		ConnectionEdge bestEdge=null;
		if(graph.outgoingEdgesOf(cc).size()>0) {
			for(ConnectionEdge edge : graph.outgoingEdgesOf(cc)) {
				if(edge.activated && graph.getEdgeWeight(edge)<minCost) {
					minCost=graph.getEdgeWeight(edge);
					bestEdge=edge;
				}
			}
		}
		return bestEdge;
	}
	
	public static ConnectionEdge bestIncomingActivatedEdge(CC cc,SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph) {
		double minCost=1E18;
		ConnectionEdge bestEdge=null;
		if(graph.incomingEdgesOf(cc).size()>0) {
			for(ConnectionEdge edge : graph.incomingEdgesOf(cc)) {
				if(edge.activated && graph.getEdgeWeight(edge)<minCost) {
					minCost=graph.getEdgeWeight(edge);
					bestEdge=edge;
				}
			}
		}
		return bestEdge;
	}

	
	public static CC getRoot(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph) {
		for(CC cc : graph.vertexSet())if(cc.day==0)return cc;
		return null;
	}
	
	public static void setFirstOrderCosts(SimpleDirectedWeightedGraph<CC,ConnectionEdge>graph) {
		for(ConnectionEdge edge:graph.edgeSet()) {
			graph.setEdgeWeight(edge, getCostFirstOrderConnection(edge));
		}
	}

	public static void identifyTrunks(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph) {
		//Go from roots to down 
		CC root=getRoot(graph);	
		root.trunk=true;
		ArrayList<CC>nextCC=new ArrayList<CC>();
		ArrayList<CC>curCC=new ArrayList<CC>();
		for(CC cc : graph.vertexSet()) if((cc.day==1) && graph.containsEdge(root, cc)) {cc.trunk=true;curCC.add(cc);}
		int iter=0;
		System.out.print("\nCalcul des troncs.");
		while(curCC.size()>0) {
			for(int i=0; i<curCC.size();i++) {
				CC cc=curCC.get(i);
				cc.trunk=true;
				if(graph.outgoingEdgesOf(cc).size()==0)continue;
				double minCost=1E18;
				ConnectionEdge bestEdge=null;
				for(ConnectionEdge edge : graph.outgoingEdgesOf(cc)) {
					if(graph.getEdgeWeight(edge)<minCost) {minCost=graph.getEdgeWeight(edge);bestEdge=edge;}
				}
				nextCC.add(graph.getEdgeTarget(bestEdge));
				bestEdge.trunk=true;
			}
			curCC=nextCC;
			nextCC=new ArrayList<CC>();
		}
		System.out.println();
	}

	
	public static void identifyRootSystems(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph) {

	}

	public static boolean isExtremity(CC cc,SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph) {
		if(graph.incomingEdgesOf(cc).size()<1)return true;
		if(graph.outgoingEdgesOf(cc).size()<1)return true;
		boolean hasParent=false;
		boolean hasChild=false;
		for(ConnectionEdge edge :graph.incomingEdgesOf(cc))if(edge.activated)hasParent=true;
		for(ConnectionEdge edge :graph.outgoingEdgesOf(cc))if(edge.activated)hasChild=true;
		return !(hasParent && hasChild);
	}
	
	public static void lookForSimplifyingPath() {
		//Identify the branching
		
		//Identify the most Costy branching backwards
	}
		
	public static SimpleDirectedWeightedGraph<CC,ConnectionEdge> treeAsGraph(SpanningTree<ConnectionEdge> tree){
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph=new SimpleDirectedWeightedGraph<>(ConnectionEdge.class);
		for(ConnectionEdge edge : tree) {
			if(!graph.containsVertex(edge.source))graph.addVertex(edge.source);
			if(!graph.containsVertex(edge.target))graph.addVertex(edge.target);
			graph.addEdge(edge.source,edge.target,edge);
		}
		return graph;
	}
	
	/**
	 *     Render the graph as a 2D Image   
	 */	
	public static ImagePlus drawGraph(ImagePlus imgDates,SimpleDirectedWeightedGraph <CC,ConnectionEdge>graph,double circleRadius,int lineThickness,int sizeFactor) {
		System.out.println("Drawing graph");

		//Draw the silhouette
		ImagePlus contour=VitimageUtils.nullImage(imgDates);
		ImagePlus in=VitimageUtils.nullImage(imgDates);
		if(sizeFactor>1) {
			ImagePlus bin=VitimageUtils.thresholdImage(imgDates, 0.5, 100000);
			ImagePlus binResize=resizeNearest(bin, imgDates.getWidth()*sizeFactor,  imgDates.getHeight()*sizeFactor, 1);
			ImagePlus ero=erosionCircle2D(binResize, 1);
			ImagePlus dil=dilationCircle2D(binResize, 1);
			contour=VitimageUtils.makeOperationBetweenTwoImages(dil, ero, 4, false);
			in =VitimageUtils.makeOperationOnOneImage(ero, 3, 255, true);
			contour=VitimageUtils.makeOperationBetweenTwoImages(contour, in, 1, true);
		}
		contour =VitimageUtils.makeOperationOnOneImage(contour, 2, 255, true);
		ImagePlus imgGraph=imgDates.duplicate();
		int N=(int) Math.round( VitimageUtils.maxOfImage(imgDates));
		imgGraph=VitimageUtils.makeOperationOnOneImage(imgGraph, 2, 0, true);
		imgGraph=VitimageUtils.convertFloatToByteWithoutDynamicChanges(imgGraph);
		if(sizeFactor>1) {
			int dimX=imgDates.getWidth();
			int dimY=imgDates.getHeight();
			imgGraph=VitimageUtils.uncropImageByte(imgGraph, 0, 0, 0, dimX*sizeFactor, dimY*sizeFactor, 1);
		}
		
		//Draw the vertices
		double vx=VitimageUtils.getVoxelSizes(imgGraph)[0];
		System.out.print(" 1-Drawing circles for "+graph.vertexSet().size()+" vertices  ");
		int incr=0;int decile=graph.vertexSet().size()/10;
		if(sizeFactor>=4)circleRadius*=2;
		for(CC cc:graph.vertexSet()) {
			double factor=0.3+0.7*Math.log10(cc.nPixels);
			if(((incr++)%decile)==0) {
				System.out.print(incr+" ");
			}
			boolean extremity =isExtremity(cc,graph);
			if(extremity) {
				if(cc.nPixels>=MIN_SIZE)VitimageUtils.drawCircleIntoImage(imgGraph, vx*(factor*circleRadius+4+(cc.trunk ? 2 : 0)), (int)Math.round(cc.r.getContourCentroid()[0]*sizeFactor), (int)Math.round(cc.r.getContourCentroid()[1]*sizeFactor), 0,12);
			}
			else {
				VitimageUtils.drawCircleIntoImage(imgGraph, vx*(factor*circleRadius+2+(cc.trunk ? 2 : 0)), (int)Math.round(cc.r.getContourCentroid()[0]*sizeFactor), (int)Math.round(cc.r.getContourCentroid()[1]*sizeFactor), 0,255);
				VitimageUtils.drawCircleIntoImage(imgGraph, vx*(factor*circleRadius+1+(cc.trunk ? 1 : 0)), (int)Math.round(cc.r.getContourCentroid()[0]*sizeFactor), (int)Math.round(cc.r.getContourCentroid()[1]*sizeFactor), 0,0);
			}
			VitimageUtils.drawCircleIntoImage(imgGraph, vx*(factor*circleRadius), (int)Math.round(cc.r.getContourCentroid()[0]*sizeFactor), (int)Math.round(cc.r.getContourCentroid()[1]*sizeFactor), 0,cc.day);
		}
		System.out.println();
		
		//Draw the edges
		System.out.print("   2-Drawing "+graph.edgeSet().size()+" edges  ");
		incr=0;decile=graph.edgeSet().size()/10;
		int incrAct=0;
		int incrNonAct=0;
		for(ConnectionEdge edge:graph.edgeSet()) {
			if(!edge.activated) incrNonAct++;
			if(!edge.activated) continue;
			incrAct++;
			if(((incr++)%decile)==0)System.out.print(incr+" ");
			CC cc1=graph.getEdgeSource(edge);
			CC cc2=graph.getEdgeTarget(edge);
			double xCon=edge.connectionX;
			double yCon=edge.connectionY;
			//if(cc1.day==0 || cc2.day==0)continue; 
			int val=(int)(22-3*(graph.getEdgeWeight(edge)+1));
			if(val<4)val=4;
			if(val>255)val=255;
			if(cc1.day>0) drawSegmentInto2DByteImage(imgGraph,lineThickness+(edge.trunk ? 8 : 0),val,cc1.r.getContourCentroid()[0]*sizeFactor,cc1.r.getContourCentroid()[1]*sizeFactor,xCon*sizeFactor,yCon*sizeFactor,edge.hidden); 
			if(cc1.day>0)drawSegmentInto2DByteImage(imgGraph,lineThickness+(edge.trunk ? 8 : 0),val,xCon*sizeFactor,yCon*sizeFactor,cc2.r.getContourCentroid()[0]*sizeFactor,cc2.r.getContourCentroid()[1]*sizeFactor,edge.hidden); 
			drawSegmentInto2DByteImage(imgGraph,1+(edge.trunk ? 3 : 0),0,cc1.r.getContourCentroid()[0]*sizeFactor,cc1.r.getContourCentroid()[1]*sizeFactor,xCon*sizeFactor,yCon*sizeFactor,edge.hidden); 
			drawSegmentInto2DByteImage(imgGraph,1+(edge.trunk ? 3 : 0),0,xCon*sizeFactor,yCon*sizeFactor,cc2.r.getContourCentroid()[0]*sizeFactor,cc2.r.getContourCentroid()[1]*sizeFactor,edge.hidden); 
		}
		System.out.println(incrAct+" activated and "+incrNonAct+" non-activated");
		//Draw the vertices
		System.out.print("   3-Drawing central square for "+graph.vertexSet().size()+" vertices  ");
		incr=0;decile=graph.vertexSet().size()/10;
		for(CC cc:graph.vertexSet()) {
			if(((incr++)%decile)==0)System.out.print(incr+" ");
			boolean extremity =isExtremity(cc,graph);
			if(cc.nPixels>=MIN_SIZE || !extremity)VitimageUtils.drawCircleIntoImage(imgGraph, vx*3, (int)Math.round(cc.r.getContourCentroid()[0]*sizeFactor), (int)Math.round(cc.r.getContourCentroid()[1]*sizeFactor), 0,extremity ? 12 : 0);
			if(cc.nPixels>=MIN_SIZE || !extremity)VitimageUtils.drawCircleIntoImage(imgGraph, vx*2, (int)Math.round(cc.r.getContourCentroid()[0]*sizeFactor), (int)Math.round(cc.r.getContourCentroid()[1]*sizeFactor), 0,extremity ? 12 : 255);
		}
				
		imgDates.setDisplayRange(0, N+1);
		System.out.print("\n   4-High res misc drawing (100M +) ");

		System.out.print("1 ");
		ImagePlus graphArea=VitimageUtils.thresholdImage(imgGraph, 0.5, 1000000);
		graphArea=VitimageUtils.getBinaryMaskUnary(graphArea, 0.5);

		System.out.print("2 ");
		ImagePlus contourArea=VitimageUtils.thresholdImage(contour, 0.5, 1000000000);
		contourArea=VitimageUtils.getBinaryMaskUnary(contourArea, 0.5);
		contourArea=VitimageUtils.binaryOperationBetweenTwoImages(contourArea, graphArea, 4);
		
		System.out.print("3 ");
		ImagePlus outArea=VitimageUtils.binaryOperationBetweenTwoImages(graphArea, contourArea, 1);
		outArea=VitimageUtils.invertBinaryMask(outArea);
		
		System.out.print("4 ");
		ImagePlus part1=VitimageUtils.makeOperationBetweenTwoImages(imgGraph, graphArea, 2, true);
		ImagePlus part2=VitimageUtils.makeOperationBetweenTwoImages(contour, contourArea, 2, true);

		System.out.print("5 ");
		imgGraph=VitimageUtils.makeOperationBetweenTwoImages(part1, part2, 1, false);
		imgGraph.setDisplayRange(0, N+1);
		System.out.print(" Ok.  ");
		
		return imgGraph;
	}
	



	public static SimpleDirectedWeightedGraph<CC,ConnectionEdge>readGraphFromFile(String path){
	    try {
		FileInputStream streamIn = new FileInputStream(path);
	    ObjectInputStream objectinputstream = new ObjectInputStream(streamIn);
	    @SuppressWarnings("unchecked")
	    SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph = (SimpleDirectedWeightedGraph<CC,ConnectionEdge>) objectinputstream.readObject();
	    objectinputstream.close();
			streamIn.close();
		    return graph;
		} catch (ClassNotFoundException | IOException c) {
			c.printStackTrace();
		}
	    return null;
	}
		
	public static void writeGraphToFile(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,String path) {
		FileOutputStream fout;
		try {
			fout = new FileOutputStream(path, false);
			ObjectOutputStream oos = new ObjectOutputStream(fout);
			oos.writeObject(graph);
			oos.close();
			fout.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static double getCostOfDistance(CC cc1,CC cc2) {
		int deltaDay=Math.abs(cc1.day-cc2.day);
		double deltaDist=VitimageUtils.distance(cc1.r.getContourCentroid()[0], cc1.r.getContourCentroid()[1], cc2.r.getContourCentroid()[0],cc1.r.getContourCentroid()[1]);
		double targetSpeed=100; //pix/step
		return 0;
	}
	
	public static double getCostOfAlignmentOfLastPointGivenTwoFirst(CC cc1,CC cc2,CC cc3,boolean debug) {
		//7117 3389
		if(debug) {
		System.out.println(cc1);
		System.out.println(cc2);
		System.out.println(cc3);
		}
		int delta12Day=Math.abs(cc1.day-cc2.day);
		int delta23Day=Math.abs(cc3.day-cc2.day);
		if(delta12Day==0)delta12Day=1;
		if(delta23Day==0)delta23Day=1;
		double[]vect12=new double[] {cc2.r.getContourCentroid()[0]-cc1.r.getContourCentroid()[0],cc2.r.getContourCentroid()[1]-cc1.r.getContourCentroid()[1],0};
		double[]vect12PerDay=TransformUtils.multiplyVector(vect12, 1/delta12Day);
		double distPerDay=TransformUtils.norm(vect12PerDay);
		double xExpected=cc2.r.getContourCentroid()[0]+vect12PerDay[0]*delta23Day;
		double yExpected=cc2.r.getContourCentroid()[1]+vect12PerDay[1]*delta23Day;
		double xReel=cc3.r.getContourCentroid()[0];
		double yReel=cc3.r.getContourCentroid()[1];
		double relativeDistInDayPerDay=VitimageUtils.distance(xReel, yReel, xExpected, yExpected)/(distPerDay*delta23Day);
		return relativeDistInDayPerDay;
	}
	
	public static double getCostFirstOrderConnection(ConnectionEdge edge) {
		CC source=edge.source;
		CC target=edge.target;
		if(source.day==0)return (-1000);
		if(source.day>target.day)return (10000);
		double dx=target.r.getContourCentroid()[0]-source.r.getContourCentroid()[0];
		double dy=target.r.getContourCentroid()[1]-source.r.getContourCentroid()[1];
		double dday=Math.abs(target.day-source.day);
		double prodscal=dy/Math.sqrt(dx*dx+dy*dy);
		double cost=  (1-similarity(source.nPixels,target.nPixels)) + (dday<2 ? 0 : dday-1) + (source.day>1 ? -prodscal : 0); 
		return cost;
	}
	
	
	public static CC getCC(SimpleDirectedWeightedGraph<CC,ConnectionEdge>graph,int day, int x, int y) {
		//System.out.println("Looking for CC");
		CC ret=null;
		double minDist=1E8;
		for (CC cc: graph.vertexSet()) {
			if(cc.day!=day)continue;
			double dist=VitimageUtils.distance(x/SIZE_FACTOR, y/SIZE_FACTOR, cc.r.getContourCentroid()[0], cc.r.getContourCentroid()[1]);
			if(dist<minDist) {
				minDist=dist;
				ret=cc;
			}
		}
		return ret;
	}	
	
/*


	public static void setSecondOrderCosts(SimpleDirectedWeightedGraph<CC,ConnectionEdge>graph) {
		ArrayList <Object[]>list=new ArrayList<Object[]>();
		int []incrViews=new int[6];
		for(CC cc : graph.vertexSet()) {
			if(cc.day<2)continue;

			//Identify best next node, if any
			CC ccBestNext=null;
			double minWeightNext=1E8;
			if(graph.outgoingEdgesOf(cc).size()>0) {
				for(ConnectionEdge edgeNext:graph.outgoingEdgesOf(cc)) {
					if(graph.getEdgeWeight(edgeNext)<minWeightNext) {
						ccBestNext=edgeNext.target;
						minWeightNext=graph.getEdgeWeight(edgeNext);
					}
				}				
			}
			
			//N-1 parents depth-first search
			for(ConnectionEdge edgePar : graph.incomingEdgesOf(cc)) {
				CC ccPar=graph.getEdgeSource(edgePar);
				for(ConnectionEdge edge2 : graph.outgoingEdgesOf(cc2)) {
					if(edge==edge2)continue;
					CC cc3=graph.getEdgeTarget(edge2);
					if(graph.incomingEdgesOf(cc3).size()<2)continue;

					CC bestParent=null;
					double minWeight=1E8;
					for(ConnectionEdge edge4:graph.incomingEdgesOf(cc3)) {
						if(graph.getEdgeWeight(edge4)<minWeight) {
							bestParent=edge4.source;
							minWeight=graph.getEdgeWeight(edge4);
						}
					}
					
					
					
					incrViews[4]++;
					if(cc.day<cc3.day)continue;
					incrViews[5]++;
					double conX=edge.connectionX/2.0+edge2.connectionX/2.0;
					double conY=edge.connectionY/2.0+edge2.connectionY/2.0;
					ConnectionEdge edge3=new ConnectionEdge(conX, conY, 0, cc3, cc);
					edge3.hidden=true;
					
					double cost=getCostFirstOrderConnection(edge3)+getCostOfAlignmentOfLastPointGivenTwoFirst(bestParent, cc3, cc,false);
					list.add(new Object[] {cc3,cc,edge3,new Double(cost)});
				}		
			}
		}
		for(Object[]tab:list) {
			graph.addEdge((CC)tab[0], (CC)tab[1],(ConnectionEdge)tab[2]);
			graph.setEdgeWeight((ConnectionEdge)tab[2], (Double)tab[3]);
		}
		for(int i=0;i<6;i++)System.out.println("INCR "+i+" : "+incrViews[i]);
		//VitimageUtils.waitFor(500000000);
	}
*/

	
	
	/// Various helpers for image manipulation //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public static  ImagePlus erosionLine2D(ImagePlus img, int radius,boolean horizontal) {
		Strel3D str2=null;
		if(horizontal)str2=inra.ijpb.morphology.strel.LinearHorizontalStrel.fromRadius(radius);
		else str2=inra.ijpb.morphology.strel.LinearVerticalStrel.fromRadius(radius);
		return new ImagePlus("",Morphology.erosion(img.getImageStack(),str2));
	}
	
	public static ImagePlus dilationLine2D(ImagePlus img, int radius,boolean horizontal) {
		Strel3D str2=null;
		if(horizontal)str2=inra.ijpb.morphology.strel.LinearHorizontalStrel.fromRadius(radius);
		else str2=inra.ijpb.morphology.strel.LinearVerticalStrel.fromRadius(radius);
		return new ImagePlus("",Morphology.dilation(img.getImageStack(),str2));
	}

	public static ImagePlus dilationCircle2D(ImagePlus img, int radius) {
		Strel3D str2=null;
		str2=inra.ijpb.morphology.strel.DiskStrel.fromRadius(radius);
		return new ImagePlus("",Morphology.dilation(img.getImageStack(),str2));
	}
	
	public static ImagePlus erosionCircle2D(ImagePlus img, int radius) {
		Strel3D str2=null;
		str2=inra.ijpb.morphology.strel.DiskStrel.fromRadius(radius);
		return new ImagePlus("",Morphology.erosion(img.getImageStack(),str2));
	}
	
	
	
	
	
	/// Other helpers /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public static double similarity(double d1,double d2) {
		double diff=Math.abs(d1-d2);
		double mean=(d1+d2)/2;
		return 1-(0.5*diff/mean);
	}

	/**
    STEP 01 : Import a 2D time-lapse series as a 3D volume (Time=Z axis, from 0 to N-1)
    If no dataDir is given, open the image into rfernandez's DATA drive
    */
	public static ImagePlus importTimeLapseSerie(String ml, String boite,String extension,String dataDir,boolean verbose) {
		String hardDiskPath="/media/rfernandez/DATA_RO_A/Roots_systems/Data_BPMP/Second_dataset_2021_07/Data_Tidy/";
		String dirData=( (dataDir==null) ? (mainDataDir+"/Data_Tidy/") : dataDir );
		ArrayList<ImagePlus> listImg=new ArrayList<ImagePlus>();
		ArrayList<String>listLabels=new ArrayList<String>();
		for(int i=0;i<100;i++) {
			String seq=""+(i+1);
			String specName="ML"+ml+"_Seq_"+seq+"_Boite_"+boite;
			if(new File(hardDiskPath+"IMG/ML"+ml+"/Seq_"+seq+"/"+specName+extension).exists() ) {
				String date=findFullDateInCsv(hardDiskPath+"CSV/ML"+ml+"/Seq_"+seq+"/Timepoints.csv",specName);
				
				if(verbose)System.out.print(" "+seq+" with found date="+date);
				ImagePlus img=IJ.openImage(hardDiskPath+"IMG/ML"+ml+"/Seq_"+seq+"/"+specName+extension);
				listLabels.add(date);
				listImg.add(img);
			}
		}
		if(verbose)System.out.println();
		if(listImg.size()<1) {
			if(verbose)System.out.println("Nothing");return null;
		}
		ImagePlus[]imgSequence=listImg.toArray(new ImagePlus[listImg.size()]);

		ImagePlus imgStack= VitimageUtils.slicesToStack(imgSequence);
		int h0=Integer.parseInt(listLabels.get(0).split("Hours=")[1]);
		for(int i=0;i<imgStack.getStackSize();i++) {
			int hi=Integer.parseInt(listLabels.get(i).split("Hours=")[1]);
			hi=hi-h0;
			String go=listLabels.get(i).split("Hours=")[0]+"Hours="+hi;
			imgStack.getStack().setSliceLabel(go, i+1);
		}
		return imgStack;

	}
	
	 /**
    STEP 02 : Register stack comprising successive 2D images of root systems.
    If no dataDir is given, open the image into rfernandez's DATA drive
    */
	public static ImagePlus []registerImageSequence(String ml, String boite,int additionnalIterationsUsingMeanImage,boolean viewRegistrations) {
		ImagePlus mask=IJ.openImage(mainDataDir+"/N_Others/maskNewLong.tif");
		ImagePlus imgInit2=IJ.openImage(mainDataDir+"/0_Stacked/ML"+ml+"_Boite_"+boite+".tif");
		ImagePlus imgInit2High=IJ.openImage(mainDataDir+"/0_Stacked_Highres/ML"+ml+"_Boite_"+boite+".tif");
		ImagePlus imgInit=VitimageUtils.cropImage(imgInit2, 122,152,0,1348,1226,imgInit2.getStackSize());
		ImagePlus imgInitHigh=VitimageUtils.cropImage(imgInit2High, 122*4,152*4,0,1348*4,1226*4,imgInit2High.getStackSize());
		ImagePlus imgOut=imgInit.duplicate();
		IJ.run(imgOut,"32-bit","");

		int N=imgInit.getStackSize();
		ImagePlus []tabImg=VitimageUtils.stackToSlices(imgInit);
		ImagePlus []tabImg2=VitimageUtils.stackToSlices(imgInit);
		ImagePlus []tabImgHigh=VitimageUtils.stackToSlices(imgInitHigh);
		ImagePlus []tabImgSmall=VitimageUtils.stackToSlices(imgInit);
		ItkTransform []tr=new ItkTransform[N];
		ItkTransform []trComposed=new ItkTransform[N];
		for(int i=0;i<tabImgSmall.length;i++) {tabImgSmall[i]=VitimageUtils.cropImage(tabImgSmall[i], 0, 0,0, tabImgSmall[i].getWidth(),(tabImgSmall[i].getHeight()*2)/3,1);}

		
		boolean doit=true;
		if(doit) {
		//First step : daisy-chain rigid registration
		Timer t=new Timer();
		t.log("Start");
		for(int n=0;(n<N-1);n++) {
			t.log("n="+n);
			RegistrationAction regAct=new RegistrationAction().defineSettingsFromTwoImages(tabImg[n],tabImg[n+1],null,false);
			regAct.setLevelMaxLinear(4);			regAct.setLevelMinLinear(0);
			regAct.strideX=8;			regAct.strideY=8;			regAct.neighX=3;			regAct.neighY=3;
			regAct.selectLTS=90;
			regAct.setIterationsBM(8);
			BlockMatchingRegistration bm= BlockMatchingRegistration.setupBlockMatchingRegistration(tabImgSmall[n+1], tabImgSmall[n], regAct);
			bm.mask=mask.duplicate();
		    bm.defaultCoreNumber=VitimageUtils.getNbCores();
		    bm.minBlockVariance/=4;
		    viewRegistrations=false;
			if(viewRegistrations) {
				bm.displayRegistration=2;
				bm.adjustZoomFactor(512.0/tabImg[n].getWidth());
				bm.flagSingleView=true;
			}
			bm.displayR2=false;
		    tr[n]=bm.runBlockMatching(null, false);		
		    tr[n].writeMatrixTransformToFile("/home/rfernandez/Bureau/Temp/gg4/tr_"+n+".txt");
			if(viewRegistrations) {
			    bm.closeLastImages();
			    bm.freeMemory();
			}
			VitimageUtils.writeIntInFile("/home/rfernandez/Bureau/Temp/gg/"+(n), n);
		}
		}
		else {
			for(int n=0;(n<N-1);n++) {
				tr[n]=ItkTransform.readTransformFromFile("/home/rfernandez/Bureau/Temp/gg4/tr_"+n+".txt");
			}
		}
		
		for(int n1=0;n1<N-1;n1++) {
			trComposed[n1]=new ItkTransform(tr[n1]);
			for(int n2=n1+1;n2<N-1;n2++) {
				trComposed[n1].addTransform(tr[n2]);
			}
			tabImg[n1]=trComposed[n1].transformImage(tabImg[n1], tabImg[n1]);
		}
		ImagePlus result1=VitimageUtils.slicesToStack(tabImg);
		result1.setTitle("step 1");
		IJ.saveAsTiff(result1, "/home/rfernandez/Bureau/Temp/gg3/step_1_after_rig.tif");
		ImagePlus result2=null;
		
		
		
		//Second step : daisy-chain dense registration  
		ArrayList<ImagePlus>listAlreadyRegistered=new ArrayList<ImagePlus>();
		listAlreadyRegistered.add(tabImg2 [N-1]);
		for(int n1=N-2;n1>=0;n1--) {
			ImagePlus imgRef=listAlreadyRegistered.get(listAlreadyRegistered.size()-1);//VitimageUtils.meanOfImageArray(listAlreadyRegistered.toArray(new ImagePlus[N-n1-1]));
			RegistrationAction regAct2=new RegistrationAction().defineSettingsFromTwoImages(tabImg[0],tabImg[0],null,false);				
			regAct2.setLevelMaxNonLinear(1);			regAct2.setLevelMinNonLinear(-1);			regAct2.setIterationsBMNonLinear(4);
			regAct2.typeTrans=Transform3DType.DENSE;
			regAct2.strideX=4;			regAct2.strideY=4;			regAct2.neighX=2;			regAct2.neighY=2;			regAct2.bhsX-=3;			regAct2.bhsY-=3;
			regAct2.sigmaDense/=6;
			regAct2.selectLTS=80;
			BlockMatchingRegistration bm2= BlockMatchingRegistration.setupBlockMatchingRegistration(imgRef, tabImg2[n1], regAct2);
			bm2.mask=mask.duplicate();
		    bm2.defaultCoreNumber=VitimageUtils.getNbCores();
		    bm2.minBlockVariance=10;
		    bm2.minBlockScore=0.10;
		    bm2.displayR2=false;
		    viewRegistrations=false;
			if(viewRegistrations) {
				bm2.displayRegistration=2;
				bm2.adjustZoomFactor(512.0/tabImg[n1].getWidth());
			}
//				bm2.minBlockVariance/=2;
			trComposed[n1]=bm2.runBlockMatching(trComposed[n1], false);			
			if(viewRegistrations) {
			    bm2.closeLastImages();
			    bm2.freeMemory();
			}
			tabImg[n1]=trComposed[n1].transformImage(tabImg2[n1], tabImg2[n1]);
			VitimageUtils.writeIntInFile("/home/rfernandez/Bureau/Temp/gg2/den_"+n1, n1);
			tabImgHigh[n1]=trComposed[n1].transformImage(tabImgHigh[n1],tabImgHigh[n1]);
			listAlreadyRegistered.add(tabImg[n1]);
		}
		ImagePlus resultHigh=VitimageUtils.slicesToStack(tabImgHigh);
		result2=VitimageUtils.slicesToStack(tabImg);
		result2.setTitle("step 3");
		IJ.saveAsTiff(result2, "/home/rfernandez/Bureau/Temp/gg3/step_3_after_rig.tif");
		result2=VitimageUtils.slicesToStack(tabImg);
		result2.setTitle("final result");

		return new ImagePlus[] {resultHigh,result2};
	}
	
	public static ImagePlus []registerImageSequenceOLD(ImagePlus imgInit2,int additionnalIterationsUsingMeanImage,boolean viewRegistrations,ImagePlus mask) {
		mask=IJ.openImage(mainDataDir+"/N_Others/maskNew.tif");
		ImagePlus imgInit=VitimageUtils.cropImage(imgInit2, 122,152,0,1388,1226,imgInit2.getStackSize());
		boolean makeDense=true;
		viewRegistrations=false;
		int N=imgInit.getStackSize();
		ImagePlus imgOut=imgInit.duplicate();
		IJ.run(imgOut,"32-bit","");
		ImagePlus []tabImg=VitimageUtils.stackToSlices(imgInit);
		ImagePlus []tabImgNoMove=VitimageUtils.stackToSlices(imgInit);
		ImagePlus []tabImg2=VitimageUtils.stackToSlices(imgInit);
		ImagePlus []tabImgSmall=VitimageUtils.stackToSlices(imgInit);
		for(int i=0;i<tabImgSmall.length;i++) {tabImgSmall[i]=VitimageUtils.cropImage(tabImgSmall[i], 0, 0,0, tabImgSmall[i].getWidth(),tabImgSmall[i].getHeight()/2,1);}
		ItkTransform []tr=new ItkTransform[N];
		ItkTransform []trComposed=new ItkTransform[N];
		Timer t=new Timer();
		t.log("Start");
		for(int n=0;(n<N-1);n++) {
			t.log("n="+n);
			RegistrationAction regAct=new RegistrationAction().defineSettingsFromTwoImages(tabImg[n],tabImg[n+1],null,false);
			regAct.setLevelMaxLinear(4);
			regAct.setLevelMinLinear(1);
			regAct.strideX=8;
			regAct.strideY=8;
			regAct.neighX=3;
			regAct.neighY=3;
			regAct.selectLTS=90;
			regAct.setIterationsBM(8);
			BlockMatchingRegistration bm= BlockMatchingRegistration.setupBlockMatchingRegistration(tabImgSmall[n+1], tabImgSmall[n], regAct);
			bm.mask=mask.duplicate();
		    bm.defaultCoreNumber=VitimageUtils.getNbCores();
		    bm.minBlockVariance/=4;
			if(viewRegistrations) {
				bm.displayRegistration=2;
				bm.adjustZoomFactor(512.0/tabImg[n].getWidth());
				bm.flagSingleView=true;
			}
		    tr[n]=bm.runBlockMatching(null, false);			
			if(viewRegistrations) {
			    bm.closeLastImages();
			    bm.freeMemory();
			}
			VitimageUtils.writeIntInFile("/home/rfernandez/Bureau/Temp/gg/"+(n), n);
		}
		for(int n1=0;n1<N-1;n1++) {
			trComposed[n1]=new ItkTransform(tr[n1]);
			for(int n2=n1+1;n2<N-1;n2++) {
				trComposed[n1].addTransform(tr[n2]);
			}
			tabImg[n1]=trComposed[n1].transformImage(tabImg[n1], tabImg[n1]);
		}
		ImagePlus result1=VitimageUtils.slicesToStack(tabImg);
		result1.setTitle("step 1");
		IJ.saveAsTiff(result1, "/home/rfernandez/Bureau/Temp/gg3/step_1_after_rig.tif");
		ImagePlus result2=null;
		
		//Second step 
		if(additionnalIterationsUsingMeanImage>0) {
			viewRegistrations=false;
			for(int n1=0;n1<N-1;n1++) {
				RegistrationAction regAct=new RegistrationAction().defineSettingsFromTwoImages(tabImg[0],tabImg[0],null,false);
				regAct.setLevelMaxLinear(2);
				regAct.setLevelMinLinear(-1);
				regAct.strideX=5;
				regAct.strideY=5;
				regAct.setIterationsBM(6);
				regAct.selectLTS=90;
				regAct.selectRandom=90;
				regAct.setIterationsBM(6);
				BlockMatchingRegistration bm= BlockMatchingRegistration.setupBlockMatchingRegistration(tabImg2[N-1], tabImg2[n1], regAct);
				bm.mask=mask.duplicate();
			    bm.defaultCoreNumber=VitimageUtils.getNbCores();
				if(viewRegistrations) {
					bm.displayRegistration=2;
					bm.adjustZoomFactor(512.0/tabImg[0].getWidth());
				}
				bm.minBlockVariance/=2;
				trComposed[n1]=bm.runBlockMatching(trComposed[n1], false);			
				if(viewRegistrations) {
				    bm.closeLastImages();
				    bm.freeMemory();
				}
				tabImg[n1]=trComposed[n1].transformImage(tabImg2[n1], tabImg2[n1]);
				VitimageUtils.writeIntInFile("/home/rfernandez/Bureau/Temp/gg2/rig_"+n1, n1);
			}				
			result2=VitimageUtils.slicesToStack(tabImg);
			result2.setTitle("step 2");
			IJ.saveAsTiff(result2, "/home/rfernandez/Bureau/Temp/gg3/step_2_after_rig.tif");

			if(makeDense) {
				viewRegistrations=false;
				for(int n1=0;n1<N;n1++) {
					RegistrationAction regAct2=new RegistrationAction().defineSettingsFromTwoImages(tabImg[0],tabImg[0],null,false);				
					regAct2.setLevelMaxNonLinear(1);
					regAct2.setLevelMinNonLinear(-1);
					regAct2.setIterationsBMNonLinear(4);
					regAct2.typeTrans=Transform3DType.DENSE;
					regAct2.strideX=4;
					regAct2.strideY=4;
					regAct2.neighX=2;
					regAct2.neighY=2;
					regAct2.bhsX-=2; 
					regAct2.bhsY-=2;
					regAct2.sigmaDense/=6;
					regAct2.selectLTS=80;
					BlockMatchingRegistration bm2= BlockMatchingRegistration.setupBlockMatchingRegistration(tabImg2[N-1], tabImg2[n1], regAct2);
					bm2.mask=mask.duplicate();
				    bm2.defaultCoreNumber=VitimageUtils.getNbCores();
				    bm2.minBlockVariance=10;
				    bm2.minBlockScore=0.10;
					if(viewRegistrations) {
						bm2.displayRegistration=2;
						bm2.adjustZoomFactor(512.0/tabImg[n1].getWidth());
					}
	//				bm2.minBlockVariance/=2;
					trComposed[n1]=bm2.runBlockMatching(trComposed[n1], false);			
					if(viewRegistrations) {
					    bm2.closeLastImages();
					    bm2.freeMemory();
					}
					tabImg[n1]=trComposed[n1].transformImage(tabImg2[n1], tabImg2[n1]);
					VitimageUtils.writeIntInFile("/home/rfernandez/Bureau/Temp/gg2/den_"+n1, n1);
				}
				result2=VitimageUtils.slicesToStack(tabImg);
				result2.setTitle("step 3");
				IJ.saveAsTiff(result2, "/home/rfernandez/Bureau/Temp/gg3/step_3_after_rig.tif");
			}
		}
		result2=VitimageUtils.slicesToStack(tabImg);
		result2.setTitle("final result");

		return new ImagePlus[] {result1,result2};
	}
	
	
	/**
    STEP 03 : Identify pixel-wise the first date of presence of any root. Build a datemap
    */
	public static ImagePlus projectTimeLapseSequenceInColorspace(ImagePlus imgSeq,ImagePlus interestMask1,ImagePlus interestMaskN,ImagePlus maskOutLeaves,int threshold) {
		ImagePlus[]tab=VitimageUtils.stackToSlices(imgSeq);
		IJ.run(maskOutLeaves,"32-bit","");
		ImagePlus[]tabLeavesOut=VitimageUtils.stackToSlices(maskOutLeaves);
		for(int i=0;i<tab.length;i++) {
			tab[i]=VitimageUtils.makeOperationBetweenTwoImages(tab[i],i<2 ? interestMask1 : interestMaskN, 2, true);
		}
		ImagePlus res=indRuptureDownOfImageArrayDouble(tab,tabLeavesOut,threshold);
		return res;
	}
	
	public static ImagePlus projectTimeLapseSequenceInColorspaceCombined(ImagePlus imgSeq,ImagePlus interestMask1,ImagePlus interestMaskN,ImagePlus maskOfLeaves,int threshold) {
		ImagePlus result1=projectTimeLapseSequenceInColorspace(imgSeq,interestMask1,interestMaskN,maskOfLeaves,threshold);
//		VitimageUtils.showWithParams(maskOfLeaves.duplicate(), "maskOfLeaves", 0,-1,10);		VitimageUtils.waitFor(3000);
//		VitimageUtils.showWithParams(result1.duplicate(), "result1", 0,-1,10);		VitimageUtils.waitFor(3000);

		ImagePlus result2=projectTimeLapseSequenceInColorspaceOLD(imgSeq,interestMask1,interestMaskN,threshold);
//		VitimageUtils.showWithParams(result2.duplicate(), "result2", 0,-1,10);		VitimageUtils.waitFor(3000);
		ImagePlus out=VitimageUtils.thresholdImage(result1, -0.5, 0.5);
		out=VitimageUtils.invertBinaryMask(out);
		ImagePlus mask=VitimageUtils.getBinaryMaskUnary(out, 0.5);
//		VitimageUtils.showWithParams(result2.duplicate(), "result22", 0,0.5,0.5);		VitimageUtils.waitFor(3000);
//		VitimageUtils.showWithParams(mask.duplicate(), "mask", 0,0.5,0.5);		VitimageUtils.waitFor(3000);
		result2=VitimageUtils.makeOperationBetweenTwoImages(result2, mask, 2, false);
//		VitimageUtils.showWithParams(result2.duplicate(), "resultEnd", 0,-1,10);		VitimageUtils.waitFor(3000);
		return result2;
	}



	
	
	
	
	
	
	public static void drawSegmentInto2DByteImage(ImagePlus img,double thickness,int valToPrint,double x0,double y0,double x1,double y1,boolean dotPoint) {
		int xM=img.getWidth();
		int yM=img.getHeight();
		int zM=img.getStackSize();
		double chemin=0;
		double[]vectAB=new double[] {x1-x0,y1-y0,0};
		double cheminTarget=TransformUtils.norm(vectAB);
		vectAB=TransformUtils.normalize(vectAB);
		double[]vectOrth=new double[] {vectAB[1],-vectAB[0]};
		byte[] valsImg=(byte [])img.getStack().getProcessor(1).getPixels();
		double delta=0.2;
		int nDec=((int)Math.round(thickness/(delta*2)));
		double xx=x0;
		double yy=y0;
		double deltax=delta*vectAB[0];
		double deltay=delta*vectAB[1];
		double deltaOx=delta*vectOrth[0];
		double deltaOy=delta*vectOrth[1];
		while(chemin<cheminTarget) {
			xx+=deltax;
			yy+=deltay;			
			chemin+=delta;
			//System.out.println(chemin+" : "+( (((int)(chemin))/2)%2));
			if(dotPoint && ( ( (((int)(chemin))/2)%3)==1))continue;
			for(int dec=-nDec;dec<=nDec;dec++) {
				if((yy+deltaOy*dec>=0) && (yy+deltaOy*dec<=(yM-1)) && (xx+deltaOx*dec>=0) && (xx+deltaOx*dec<=(xM-1)) ) {
					valsImg[ xM *((int)Math.round(yy+deltaOy*dec))+((int)Math.round(xx+deltaOx*dec))]=  (byte)( valToPrint & 0xff);
				}
			}			
		}
	}

	public static String findDateInCsv(String path,String pattern) {
		System.out.println(path);
		String[][]tab=VitimageUtils.readStringTabFromCsv(path);
		for(int i=0;i<tab.length;i++) {
			if(tab[i][0].contains(pattern))return tab[i][1];
		}
		return null;
	}

	public static String findFullDateInCsv(String path,String pattern) {
		System.out.println(path);
		String[][]tab=VitimageUtils.readStringTabFromCsv(path);
		for(int i=0;i<tab.length;i++) {
			if(tab[i][0].contains(pattern))return tab[i][1].replace(" ", "")+"_Hours="+Math.round(Long.parseLong(tab[i][2].replace(" ",""))/(3600*1000));
		}
		return null;
	}
	
	public static Roi[]segmentationToRoi(ImagePlus seg){
		   
	    	ImagePlus imgSeg=VitimageUtils.getBinaryMask(seg, 0.5);
	    	if(VitimageUtils.isNullImage(imgSeg))return null;
	    	RoiManager rm=RoiManager.getRoiManager();
	    	rm.reset();
	    	imgSeg.show();
	    	imgSeg.resetRoi();
	    	IJ.setRawThreshold(imgSeg, 127, 255, null);
	        VitimageUtils.waitFor(10);
	    	
	        IJ.run("Create Selection");
	        //VitimageUtils.printImageResume(IJ.getImage(),"getImage");
	        Roi r=IJ.getImage().getRoi();
	       // System.out.println(r);
	        if(r==null)return null;
	        Roi[]rois;
	        if(r.getClass()==PolygonRoi.class)rois=new Roi[] {r};
	        else if(r.getClass()==ShapeRoi.class)rois = ((ShapeRoi)r).getRois();
	        else if(r.getClass()==Roi.class)rois=new Roi[] {r};
	        else try{
	        	rois = ((ShapeRoi)r).getRois();
	        }catch(java.lang.ClassCastException cce) {System.out.println(r.getClass());seg.show();seg.setTitle("Bug");cce.printStackTrace();VitimageUtils.waitFor(10000);rm.reset();rm.close(); return null;}
	        IJ.getImage().close();
	        rm.reset();
	        rm.close();
	        return rois;
	    }

	public static ImagePlus indOfFirstLowerThan(ImagePlus []imgs,double threshold) {
		double max=0;
		int xM=imgs[0].getWidth();
		int yM=imgs[0].getHeight();
		int zM=imgs[0].getStackSize();
		ImagePlus retVal=VitimageUtils.nullImage(imgs[0].duplicate());
		ImagePlus retInd=VitimageUtils.nullImage(imgs[0].duplicate());
		float[]valsInd;
		float[]valsVal;
		float[][]valsImg=new float[imgs.length][];
		for(int z=0;z<zM;z++) {
			valsInd=(float [])retInd.getStack().getProcessor(z+1).getPixels();
			valsVal=(float [])retVal.getStack().getProcessor(z+1).getPixels();
			for(int i=0;i<imgs.length;i++) {
				valsImg[i]=(float [])imgs[i].getStack().getProcessor(z+1).getPixels();
			}
			for(int x=0;x<xM;x++) {
				for(int y=0;y<yM;y++) {
					for(int i=0;i<imgs.length && valsInd[xM*y+x]==0;i++) {
						if( (valsImg[i][xM*y+x])< threshold) {
						valsInd[xM*y+x]=i; 
						continue;
					}
				}
			}			
			}
		}
		return retInd;
	}
	
	public static ImagePlus indMaxOfImageArrayDouble(ImagePlus []imgs,int minThreshold) {
		double max=0;
		int xM=imgs[0].getWidth();
		int yM=imgs[0].getHeight();
		int zM=imgs[0].getStackSize();
		ImagePlus retVal=VitimageUtils.nullImage(imgs[0].duplicate());
		ImagePlus retInd=VitimageUtils.nullImage(imgs[0].duplicate());
		float[]valsInd;
		float[]valsVal;
		float[]valsImg;
		for(int z=0;z<zM;z++) {
			valsInd=(float [])retInd.getStack().getProcessor(z+1).getPixels();
			valsVal=(float [])retVal.getStack().getProcessor(z+1).getPixels();
			for(int i=0;i<imgs.length;i++) {
				valsImg=(float [])imgs[i].getStack().getProcessor(z+1).getPixels();
				for(int x=0;x<xM;x++) {
					for(int y=0;y<yM;y++) {
						if( ( (valsImg[xM*y+x])> valsVal[xM*y+x]) && ((valsImg[xM*y+x])> minThreshold) ) {
							valsVal[xM*y+x]=valsImg[xM*y+x]; 
							valsInd[xM*y+x]=i; 
						}
					}
				}			
			}
		}
		return retInd;
	}

	public static ImagePlus projectTimeLapseSequenceInColorspaceSimpler(ImagePlus imgSeq,int threshold) {
		ImagePlus imgSeq2=VitimageUtils.convertByteToFloatWithoutDynamicChanges(imgSeq);
		ImagePlus[]imgTab=VitimageUtils.stackToSlices(imgSeq2);
		ImagePlus res=indOfFirstLowerThan(imgTab,threshold);
		return res;
	}
	

	public static ImagePlus projectTimeLapseSequenceInColorspaceOLD(ImagePlus imgSeq,ImagePlus interestMask1,ImagePlus interestMaskN,int threshold) {
		int N=imgSeq.getStackSize();
		ImagePlus[]imgTab=VitimageUtils.stackToSlices(imgSeq);
		ImagePlus[]imgs=new ImagePlus[N];
		for(int i=0;i<N-1;i++) {
			imgs[i+1]=VitimageUtils.makeOperationBetweenTwoImages(imgTab[i], imgTab[i+1], 4, true);
			imgs[i+1]=VitimageUtils.makeOperationBetweenTwoImages(imgs[i+1],i==0 ? interestMask1:interestMaskN, 2, true);
		}
		imgs[0]=VitimageUtils.nullImage(imgs[1]);
		ImagePlus res=VitimageUtils.indMaxOfImageArrayDouble(imgs,threshold);
		return res;
	}
	
	public static ImagePlus computeMire(ImagePlus imgIn) {
		ImagePlus img=new Duplicator().run(imgIn,1,1,1,1,1,1);
		IJ.run(img, "Median...", "radius=9 stack");
		return img;
	}
	
	public static ImagePlus indRuptureDownOfImageArrayDouble(ImagePlus []imgs,ImagePlus []maskLeavesOut,int minThreshold) {
		int xM=imgs[0].getWidth();
		int yM=imgs[0].getHeight();
		int zM=imgs[0].getStackSize();
		ImagePlus retInd=VitimageUtils.nullImage(imgs[0].duplicate());
		float[]valsInd;
		float[][]valsImg=new float[imgs.length][];
		float[][]valsMask=new float[imgs.length][];
		double[]valsToDetect;
		double[]valsToMask;
		for(int z=0;z<zM;z++) {
			valsInd=(float [])retInd.getStack().getProcessor(z+1).getPixels();
			for(int i=0;i<imgs.length;i++) {
				valsImg[i]=(float [])imgs[i].getStack().getProcessor(z+1).getPixels();
				valsMask[i]=(float [])maskLeavesOut[((i<2) ? 0 : i-1)].getStack().getProcessor(z+1).getPixels();
			}
			for(int x=0;x<xM;x++) {
				for(int y=0;y<yM;y++) {
					int last=0;
					valsToDetect=new double[imgs.length];
					valsToMask=new double[imgs.length];
					for(int i=0;i<imgs.length;i++) {
						valsToDetect[i]=valsImg[i][xM*y+x];
						valsToMask[i]=valsMask[i][xM*y+x];
						if(valsToMask[i]<1)last=i;
					}
					boolean blabla=false;
					if(x==377 && y==133)blabla=true;
					double[]newTab=new double[last+1];
					for(int i=0;i<=last;i++) {
						newTab[i]=valsToDetect[i];
					}
					int rupt=ruptureDetectionDown(newTab, minThreshold,blabla);
					if(blabla)System.out.println("Rupture="+rupt);
					valsInd[xM*y+x]=rupt; 
				}			
			}
		}
		return retInd;
	}
	

	//Return the index which is the first point of the second distribution
	public static int ruptureDetectionDown(double[]vals,double threshold,boolean blabla) {
		int indMax=0;
		double diffMax=-10000000;
		int N=vals.length;
		for(int i=1;i<N;i++) {
			double m1=meanBetweenIncludedIndices(vals, 0, i-1);
			double m2=meanBetweenIncludedIndices(vals, i, N-1);
			double diff=m1-m2;
			if(diff>diffMax) {
				indMax=i;
				diffMax=diff;
			}
			if(blabla) {
//				System.out.println("Apres i="+i+" : indMax="+indMax+" diffMax="+diffMax+" et on avait m1="+m1+" et m2="+m2);
			}
		}		
		return (diffMax>threshold ? indMax : 0);
	}
	
	

	public static void testDateEstimation() {
		int ml=1;
		String boite="00002";
		System.out.println("Processing ML"+ml+"_Boite_"+boite);
		ImagePlus imgIn=IJ.openImage(mainDataDir+"/1_Registered/ML"+ml+"_Boite_"+boite+".tif");
		ImagePlus imgMask=IJ.openImage(mainDataDir+"/1_Mask/ML"+ml+"_Boite_"+boite+".tif");
		
//		imgIn=new Duplicator().run(imgIn,1,1,1,15,1,1);
		ImagePlus mire=computeMire(imgIn);
		imgIn=VitimageUtils.addSliceToImage(mire, imgIn);
		imgIn.show();
		ImagePlus[]tabImg=VitimageUtils.stackToSlices(imgIn);
		ImagePlus[]tabImg2=VitimageUtils.stackToSlices(imgIn);
		for(int i=1;i<tabImg.length;i++)tabImg[i]=VitimageUtils.makeOperationBetweenTwoImages(tabImg2[i-1],tabImg2[i],4,true);
		ImagePlus out=VitimageUtils.slicesToStack(tabImg);
		out.show();
		

	}
	
	public static void testRegistration() {
		ImagePlus imgMov=IJ.openImage("/home/rfernandez/Bureau/t0.tif");
		ImagePlus imgRef=IJ.openImage("/home/rfernandez/Bureau/t1.tif");
		imgMov=resize(imgMov, imgMov.getWidth()/4, imgMov.getHeight()/4, 1);
		imgRef=resize(imgRef, imgRef.getWidth()/4, imgRef.getHeight()/4, 1);
		//		VitimageUtils.compositeNoAdjustOf(imgMov, imgRef).show();
//		VitimageUtils.waitFor(500000);
		ImagePlus img=IJ.openImage("/home/rfernandez/Bureau/Temp/gg3/step_2_after_rig.tif");
		ImagePlus mask=null;//IJ.openImage(mainDataDir+"/N_Others/maskNew.tif");

		if(true) {
		boolean viewRegistrations=true;
			RegistrationAction regAct=new RegistrationAction().defineSettingsFromTwoImages(imgRef,imgMov,null,false);				
			regAct.setLevelMaxLinear(3);
			regAct.setLevelMinLinear(1);
			regAct.setIterationsBMLinear(6);
			regAct.typeTrans=Transform3DType.RIGID;
			regAct.strideX=4;
			regAct.strideY=4;
			regAct.neighX=1;
			regAct.neighY=1;
//			regAct2.bhsX-=2;
//			regAct2.bhsY-=2;
			regAct.sigmaDense/=6;
			regAct.selectLTS=99;
			BlockMatchingRegistration bm= BlockMatchingRegistration.setupBlockMatchingRegistration(imgRef, imgMov, regAct);
			bm.mask=mask;
			viewRegistrations=false;
		    bm.defaultCoreNumber=VitimageUtils.getNbCores();
		    bm.minBlockVariance=0.5;
		    bm.minBlockScore=0.001;
		    bm.percentageBlocksSelectedByScore=99;
		    bm.percentageBlocksSelectedByVariance=99;
		    if(viewRegistrations) {
				bm.displayRegistration=2; 
				bm.adjustZoomFactor(512.0/imgRef.getWidth());
			}
//				bm2.minBlockVariance/=2;
			ItkTransform tr=bm.runBlockMatching(null, false);			
			IJ.showMessage("\n\n\n\nDONE\n");
 			if(viewRegistrations) {
			    bm.closeLastImages();
			    bm.freeMemory();
			}
 			tr.writeMatrixTransformToFile("/home/rfernandez/temp.txt");
		}
		ItkTransform tr=ItkTransform.readTransformFromFile("/home/rfernandez/temp.txt");
		
		int N=img.getStackSize();
		int x=10;
		ImagePlus[]tabImg=VitimageUtils.stackToSlices(img);
		ItkTransform[]trComposed=new ItkTransform[N];
		boolean viewRegistrations=true;
		for(int n1=x;n1<x+1;n1++) {
			RegistrationAction regAct2=new RegistrationAction().defineSettingsFromTwoImages(imgRef, imgMov,null,false);				
			regAct2.setLevelMaxNonLinear(0);
			regAct2.setLevelMinNonLinear(-2);
			regAct2.setIterationsBMNonLinear(6);
			regAct2.typeTrans=Transform3DType.DENSE;
			regAct2.strideX=4;
			regAct2.strideY=4;
			regAct2.neighX=2;
			regAct2.neighY=2;
//			regAct2.bhsX-=2;
//			regAct2.bhsY-=2;
			regAct2.sigmaDense/=12;
			regAct2.selectLTS=99;
			BlockMatchingRegistration bm2= BlockMatchingRegistration.setupBlockMatchingRegistration(imgRef, imgMov, regAct2);
			bm2.mask=mask;
		    bm2.defaultCoreNumber=VitimageUtils.getNbCores();
		    bm2.minBlockVariance=0.5;
		    bm2.minBlockScore=0.001;
		    bm2.percentageBlocksSelectedByScore=99;
		    bm2.percentageBlocksSelectedByVariance=99;
		    if(viewRegistrations) {
				bm2.displayRegistration=2;
				bm2.adjustZoomFactor(512.0/tabImg[n1].getWidth());
			}
//				bm2.minBlockVariance/=2;
			trComposed[n1]=bm2.runBlockMatching(tr, false);			
			IJ.showMessage("\n\n\n\nDONE\n");
			VitimageUtils.waitFor(600000000);
			if(viewRegistrations) {
			    bm2.closeLastImages();
			    bm2.freeMemory();
			}

		}
	}

	public static ImagePlus resizeNearest(ImagePlus img, int targetX,int targetY,int targetZ) {
		ImagePlus temp=img.duplicate();
        temp=Scaler.resize(temp, targetX,targetY, targetZ, "none"); 		
        if( (temp.getStackSize()==img.getStackSize()) && (temp.getStackSize()>1) && (img.getStackSize()>1) ) {
        	for(int i=0;i<temp.getStackSize();i++)temp.getStack().setSliceLabel(img.getStack().getSliceLabel(i+1), i+1);        	
        }
        VitimageUtils.adjustImageCalibration(temp,img);
        double[]voxSizes=VitimageUtils.getVoxelSizes(img);
        voxSizes[0]*=(img.getWidth()/targetX);
        voxSizes[1]*=(img.getHeight()/targetY);
        voxSizes[2]*=(img.getStackSize()/targetZ);
        VitimageUtils.adjustVoxelSize(temp, voxSizes);
        return temp;
	}

	public static ImagePlus getMenisque(ImagePlus img,boolean highRes) {
		//Calculer la difference entre une ouverture horizontale et une ouverture verticale
		int factor=highRes ? 4 : 1;
		ImagePlus img2=dilationLine2D(img, 8*factor,false);
		img2=erosionLine2D(img2, 8*factor,false);
		ImagePlus img3=dilationLine2D(img, 8*factor,true);
		img3=erosionLine2D(img3, 8*factor,true);
		ImagePlus img4=VitimageUtils.makeOperationBetweenTwoImages(img2, img3, 4, true);
		
		//Ouvrir cette difference, la binariser et la dilater, puis selectionner la plus grande CC > 70, et la dilater un peu
		ImagePlus img5=dilationLine2D(img4, 15*factor,true);
		img5=erosionLine2D(img5, 15*factor,true);
		ImagePlus img6=VitimageUtils.thresholdImage(img5, 70, 500);
		img6=dilationLine2D(img6, 2*factor,true);
		img6=dilationLine2D(img6, 1*factor,false);
		img6=VitimageUtils.connexeBinaryEasierParamsConnexitySelectvol(img6, 4, 1);
		img6=dilationLine2D(img6, 3*factor,false);
		IJ.run(img6,"8-bit","");
		return img6;
	}
	
	
	public static ImagePlus getInterestAreaMask(ImagePlus img,boolean highRes) {
		ImagePlus img2=getMenisque(img,highRes);
		ImagePlus img3=VitimageUtils.invertBinaryMask(img2);
		ImagePlus img4=VitimageUtils.connexeBinaryEasierParamsConnexitySelectvol(img3, 4, 1);
		IJ.run(img4,"8-bit","");
		return img4;
	}
	

	
	
	public static double meanBetweenIncludedIndices(double[]tab,int ind1,int ind2) {
		double tot=0;
		for(int i=ind1;i<=ind2;i++)tot+=tab[i];
		return (tot/(ind2-ind1+1));
	}
				

	public static int ruptureDetectionDown(int[]vals,double threshold,boolean blabla) {
		double[]d=new double[vals.length];
		for(int i=0;i<d.length;i++)d[i]=vals[i];
		return ruptureDetectionDown(d,threshold,blabla);
	}

	public static ImagePlus resize(ImagePlus img, int targetX,int targetY,int targetZ) {
        ImagePlus temp=Scaler.resize(img, targetX,targetY, targetZ, " interpolation=Bilinear average create"); 		
        if( (temp.getStackSize()==img.getStackSize()) && (temp.getStackSize()>1) && (img.getStackSize()>1) ) {
        	for(int i=0;i<temp.getStackSize();i++)temp.getStack().setSliceLabel(img.getStack().getSliceLabel(i+1), i+1);        	
        }
        return temp;
	}

}
	
	
class CC implements Serializable{
	private static final long serialVersionUID = 1L;
	public boolean trunk=false;
	public CC ccPrev=null;
	public int nPixels;
	public  int day;
	public int n;
	public Roi r;
	public int stamp=0;
	public int stamp2=0;
	public int componentLabel=0;
	public boolean illConnected=false;
	
public CC(int day,int n,Roi r) {
		this.day=day;
		this.n=n;
		this.setRoi(r);
	}

	public double x() {
		return this.r.getContourCentroid()[0];
	}

	public double y() {
		return this.r.getContourCentroid()[1];
	}


	
	public CC(CC source) {
		this.nPixels=source.nPixels;
		this.day=source.day;
		this.n=source.n;
		this.stamp=source.stamp;
		this.stamp2=source.stamp2;
		this.componentLabel=source.componentLabel;
		this.r=(Roi) source.r.clone();
	}
	
	public double euclidianDistanceToCC(CC cc2) {
		return VitimageUtils.distance(this.x(),this.y(),cc2.x(),cc2.y());
	}

	public String toString() {
		return "CC "+day+"-"+n+" : "+VitimageUtils.dou(r.getContourCentroid()[0])+","+VitimageUtils.dou(r.getContourCentroid()[1])+" ("+(int)(TestImageSequence.SIZE_FACTOR*r.getContourCentroid()[0])+" - "+(int)(TestImageSequence.SIZE_FACTOR*r.getContourCentroid()[1])+") "+(trunk ? " is trunk" : " ")+" stamp="+stamp;
	}

	public void setRoi(Roi r) {
		this.r=r;
		this.countPixels();
	}

	public void countPixels() {
		Rectangle R=this.r.getBounds();
		int xx=R.x;
		int XX=xx+R.width;
		int yy=R.y;
		int YY=yy+R.height;
		int count=0;
		for(int x=xx;x<=XX;x++) {
			for(int y=yy;y<=YY;y++) {
				if(r.contains(x, y))count++;
			}
		}
		this.nPixels=count;		
	}
	
	public double[] nFacets4connexe(CC cc2) {
		if(!isPossibleNeighbour(cc2,false))return new double[] {0,0,0};
		Rectangle R1=this.r.getBounds();
		Rectangle R2=cc2.r.getBounds();
		Roi r1=this.r;
		Roi r2=cc2.r;
		int x1=R1.x;
		int x2=R2.x;
		int X1=x1+R1.width;
		int X2=x2+R2.width;
		int y1=R1.y;
		int y2=R2.y;
		int Y1=y1+R1.height;
		int Y2=y2+R2.height;
		int nF=0;
		double xSum=0;
		double ySum=0;
		if(R1.width*R1.height <R2.width*R2.height) {
			int xx=x1;int XX=X1; int yy=y1; int YY=Y1;
			for(int x=xx;x<=XX;x++) {
				for(int y=yy;y<=YY;y++) {
					if(!r1.contains(x, y))continue;
					if(r2.contains(x+1, y)){xSum+=(x+0.5);ySum+=y;nF++;}
					if(r2.contains(x-1, y)){xSum+=(x-0.5);ySum+=y;nF++;}
					if(r2.contains(x, y+1)){xSum+=x;ySum+=(y+0.5);nF++;}
					if(r2.contains(x, y-1)){xSum+=x;ySum+=(y-0.5);nF++;}
					if(r2.contains(x+1, y+1)){xSum+=(x+0.5);ySum+=(y+0.5);nF++;}
					if(r2.contains(x-1, y+1)){xSum+=(x-0.5);ySum+=(y+0.5);nF++;}
					if(r2.contains(x+1, y-1)){xSum+=(x+0.5);ySum+=(y-0.5);nF++;}
					if(r2.contains(x-1, y-1)){xSum+=(x-0.5);ySum+=(y-0.5);nF++;}
				}				
			}
			return new double[] {nF,xSum/nF+0.5,ySum/nF+0.5};
		}
		else {
			int xx=x2;int XX=X2; int yy=y2; int YY=Y2;
			for(int x=xx;x<=XX;x++) {
				for(int y=yy;y<=YY;y++) {
					
					if(!r2.contains(x, y))continue;
					if(r1.contains(x+1, y)){xSum+=(x+0.5);ySum+=y; nF++;}
					if(r1.contains(x-1, y)){xSum+=(x-0.5);ySum+=y;nF++;}
					if(r1.contains(x, y+1)){xSum+=x;ySum+=(y+0.5);nF++;}
					if(r1.contains(x, y-1)){xSum+=x;ySum+=(y-0.5);nF++;}
					if(r1.contains(x+1, y+1)){xSum+=(x+0.5);ySum+=(y+0.5);nF++;}
					if(r1.contains(x-1, y+1)){xSum+=(x-0.5);ySum+=(y+0.5);nF++;}
					if(r1.contains(x+1, y-1)){xSum+=(x+0.5);ySum+=(y-0.5);nF++;}
					if(r1.contains(x-1, y-1)){xSum+=(x-0.5);ySum+=(y-0.5);nF++;}
				}			
			}
			return new double[] {nF,xSum/nF+0.5,ySum/nF+0.5};
		}			
	}
	
	

	public boolean isPossibleNeighbour(CC cc2,boolean debug) {
		Rectangle R1=this.r.getBounds();
		Rectangle R2=cc2.r.getBounds();
		if(debug) {
			System.out.println(R1);
			System.out.println(R2);
		}
		int x1=R1.x;
		int x2=R2.x;
		int X1=x1+R1.width;
		int X2=x2+R2.width;
		int y1=R1.y;
		int y2=R2.y;
		int Y1=y1+R1.height;
		int Y2=y2+R2.height;
		if(x1>X2+1)return false;
		if(x2>X1+1)return false;
		if(y1>Y2+1)return false;
		if(y2>Y1+1)return false;
		return true;
	}
}


class ConnectionEdge extends DefaultWeightedEdge implements Serializable {
	private static final long serialVersionUID = 1L;
	boolean hidden=false;
	double connectionX;
	double connectionY;	
	boolean activated=true;
	public CC source;
	public CC target;
	int nFacets;
	public boolean trunk=false;
	
	
	
	
	public ConnectionEdge(double connectionX, double connectionY,int nFacets,CC source,CC target) {
		this.hidden=false;
		this.connectionX=connectionX;
		this.connectionY=connectionY;
		this.nFacets=nFacets;
		this.source=source;
		this.target=target;
	}
	public String toString() {
		return "Connection edge "+(this.hidden ? " hidden" : " non-hidden")+(this.trunk ? " trunk" : " non-trunk")+(this.activated ? " activated" : " non-activated")+"\n    source : "+this.source+"\n   target : "+this.target;
	}
	
	
}



