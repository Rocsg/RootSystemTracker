package fr.cirad.image.TimeLapseRhizo;

import java.awt.Rectangle;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.jgrapht.alg.interfaces.SpanningTreeAlgorithm.SpanningTree;
import org.jgrapht.alg.spanning.KruskalMinimumSpanningTree;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import fr.cirad.image.common.Timer;
import fr.cirad.image.common.TransformUtils;
import fr.cirad.image.common.VitimageUtils;
import fr.cirad.image.rsmlviewer.FSR;
import fr.cirad.image.rsmlviewer.Root;
import fr.cirad.image.rsmlviewer.RootModel;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;

public class RegionAdjacencyGraphUtils {

	public static int MIN_SIZE_CC=5;
	static int SIZE_FACTOR=8;
	static int TYPICAL_SPEED=100/8;//pixels/timestep. pix=19µm , timestep=8h, meaning TYPICAL=237 µm/h
	static double OUT_OF_SILHOUETTE_PENALTY=100;//5+2*daymax 50 100 1000
	static double REVERSE_TIME_PENALTY=100;//
	static double SEMI_PENALTY=50;
	static double IDENTITY_PENALTY=100;
	public static final boolean DO_DISTANCE=true;
	public static final boolean DO_TIME=false;


	
	/** Methods for building and improving the graph ------------------------------------------------------------------------------------------------------*/	
	public static SimpleDirectedWeightedGraph<CC,ConnectionEdge>  buildAndProcessGraphStraight(ImagePlus imgDatesTmp,String mainDataDir,String ml,String boite,boolean doImages,boolean compute) {
		double ray=5;
		int thickness=5;
		int sizeFactor=SIZE_FACTOR;
		int connexity=8;
	
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph=null;
		if(compute) {
			graph=buildGraphFromDateMap(imgDatesTmp,connexity);
			pruneGraph(graph, true);
			setFirstOrderCosts(graph);
			identifyTrunks(graph);//TODO : on selectionne 5, et puis voilou
			computeMinimumDirectedConnectedSpanningTree(graph);//TODO : les deux étapes là sont redondantes. Le disconnect suffit
			disconnectUnevenBranches(graph);
			reconnectDisconnectedBranches(graph,1,true,false);		//reconnectSingleBranches(graph4);
			writeGraphToFile(graph,mainDataDir+"/3_Graphs_Ser/"+"ML"+ml+"_Boite_"+boite+".ser");
		}
		else graph=readGraphFromFile(mainDataDir+"/3_Graphs_Ser/"+"ML"+ml+"_Boite_"+boite+".ser");
	
		if(doImages) {
			int nDays=1+(int)Math.round(VitimageUtils.maxOfImage(imgDatesTmp));
			ImagePlus imgDatesHigh=VitimageUtils.convertFloatToByteWithoutDynamicChanges(imgDatesTmp);
			IJ.run(imgDatesHigh,"Fire","");
			imgDatesHigh.setDisplayRange(0, nDays);		
			imgDatesHigh=VitimageUtils.resizeNearest(imgDatesTmp, imgDatesTmp.getWidth()*sizeFactor,imgDatesTmp.getHeight()*sizeFactor,1);
			ImagePlus imgG=drawGraph(imgDatesTmp, graph, ray, thickness,sizeFactor);		
			ImagePlus graphs=VitimageUtils.slicesToStack(new ImagePlus[] {imgG});
			graphs=VitimageUtils.convertFloatToByteWithoutDynamicChanges(graphs);
			ImagePlus []datesTab=new ImagePlus[graphs.getStackSize()];for(int i=0;i<datesTab.length;i++)datesTab[i]=imgDatesHigh;
			ImagePlus dates=VitimageUtils.convertFloatToByteWithoutDynamicChanges(VitimageUtils.slicesToStack(datesTab));
				
					
			//Compute the combined rendering
			ImagePlus glob=VitimageUtils.hyperStackingChannels(new ImagePlus[] {dates,graphs});
			glob.setDisplayRange(0, nDays);
			IJ.run(graphs,"Fire","");
			glob.show();
			IJ.saveAsTiff(glob, mainDataDir+"/3_Graphs_Tif/ML"+ml+"_Boite_"+boite+".tif");
		}
		return graph;
	}
	
	public static SimpleDirectedWeightedGraph<CC, ConnectionEdge> buildGraphFromDateMap(ImagePlus imgDates,int connexity) {
		int maxSizeConnexion=500000000;
		int nDays=1+(int)Math.round(VitimageUtils.maxOfImage(imgDates));
		Roi[][]roisCC=new Roi[nDays][];
		CC[][]tabCC=new CC[nDays][];
		SimpleDirectedWeightedGraph<CC,ConnectionEdge>graph=new SimpleDirectedWeightedGraph<>(ConnectionEdge.class);

		
		//Identify connected components with label 1-N
		roisCC[0]=new Roi[] {new Roi(new Rectangle(0,0,imgDates.getWidth(),imgDates.getHeight()))};
		tabCC[0]=new CC[] {new CC(0,0,roisCC[0][0],graph)};		
		System.out.print("Identifying connected components ");
		for(int d=1;d<nDays;d++) {
			System.out.print(d+" ");
			ImagePlus binD=VitimageUtils.thresholdImage(imgDates, d, d+0.99);
			ImagePlus ccD=VitimageUtils.connexeBinaryEasierParamsConnexitySelectvol(binD, connexity, 0);
			ImagePlus allConD=VitimageUtils.thresholdImageToFloatMask(ccD, 0.5, 10E8);
			VitimageUtils.waitFor(100);
			roisCC[d]=VitimageUtils.segmentationToRoi(allConD);
			if(roisCC[d]==null) {tabCC[d]=null;continue;}
			tabCC[d]=new CC[roisCC[d].length];
			for(int n=0;n<roisCC[d].length;n++) {
				CC cc=new CC(d,n,roisCC[d][n],graph);
				tabCC[d][n]=cc;
				graph.addVertex(cc);
			}
		}
		System.out.println();
		
		//Identify connexions
		System.out.print("Identifying connexions ");
		for(int d1=1;d1<nDays;d1++) {
			System.out.print(d1+" ");
			if(roisCC[d1]==null)continue;
			for(int n1=0;n1<roisCC[d1].length;n1++) {
				for(int d2=1;d2<nDays;d2++) {
					if(roisCC[d2]==null)continue;
					for(int n2=0;n2<roisCC[d2].length;n2++) {
						if((d2<d1) || ( (d2==d1) && (n2<=n1) ))continue;//TODO : normalement la partie deux ne se produit jamais. A verifier ?
						double[] tabConn=tabCC[d1][n1].nFacets4connexe_V3(tabCC[d2][n2]); // TODO : on cherche les 4-voisinages entre composantes 8 connexes. Quel est le sens de ceci ?
						int n=(int) Math.round(tabConn[0]);
						double x=tabConn[1];
						double y=tabConn[2];
						if(n>0 && n<maxSizeConnexion) {
							graph.addEdge(tabCC[d1][n1], tabCC[d2][n2],new ConnectionEdge(x, y, n,tabCC[d1][n1], tabCC[d2][n2],tabConn[3],tabConn[4]));
						}
					}
				}
			}
		}
		System.out.println();
		return graph;
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
		CC source=new CC(0,1,r,graph) ;
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
		for(CC cc:list)graph.addEdge(source, cc,new ConnectionEdge(source.r.getContourCentroid()[0], source.r.getContourCentroid()[1], 1,source, cc,0,0));
		
		int nbMov=1;
		while(nbMov>0) {
			nbMov=0;
			for(CC cc:graph.vertexSet()) {
				if(cc.stamp==2) {
					int lab=cc.componentLabel;
					for(ConnectionEdge edge:graph.edgesOf(cc)) {
						if(graph.getEdgeSource(edge).stamp==0) {//TODO : et il se passe quoi si je retire la recherche vers les sources ? Normalement c est pas necessaire
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
	
		
	//TODO : c'est surnuméraire de faire ici encore une priorisation des zones connectées sur les zones non connectées : normalement elles sont out, ou alors il aurait fallu le faire
	public static void computeMinimumDirectedConnectedSpanningTree(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph) {		
		System.out.print("Computing connected directed minimum spanning tree");
		int maxDay=0;int currentCC=1;
		for(CC cc:graph.vertexSet()) {cc.stamp=( (cc.day==0) ? currentCC : 0); if(cc.day>maxDay)maxDay=cc.day;}
		for(int i=1;i<=maxDay;i++) {
			for(CC cc:graph.vertexSet()) {
				if(cc.day!=i)continue;
				ConnectionEdge edgeMin=null;
				double minCost=1E16;
				ConnectionEdge edgeMinConnected=null;
				double minCostConnected=1E16;
				for(ConnectionEdge edge:graph.incomingEdgesOf(cc)) {
					if((graph.getEdgeWeight(edge)<minCostConnected) && (graph.getEdgeSource(edge).stamp==1) /*&& ((cc.day==(graph.getEdgeSource(edge).day+1))  || (graph.getEdgeSource(edge).trunk))*/) {
						minCostConnected=graph.getEdgeWeight(edge);
						edgeMinConnected=edge;
					}
					if((graph.getEdgeWeight(edge)<minCost)/* && ((cc.day==(graph.getEdgeSource(edge).day+1)) || (graph.getEdgeSource(edge).trunk))*/)  {
						minCost=graph.getEdgeWeight(edge);
						edgeMin=edge;
					}
				}
				if(edgeMinConnected!=null) {
					for(ConnectionEdge edge:graph.incomingEdgesOf(cc))edge.activated=(edge==edgeMinConnected);
					cc.stamp=1;
				}
				else if(edgeMin!=null) {
					for(ConnectionEdge edge:graph.incomingEdgesOf(cc))edge.activated=(edge==edgeMin);
					cc.stamp=graph.getEdgeSource(edgeMin).stamp;
				}
				else {
					for(ConnectionEdge edge:graph.incomingEdgesOf(cc))edge.activated=false;
					cc.stamp=(++currentCC);
				}
			}
		}				
	}

	public static void disconnectUnevenBranches(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph) {
		for(CC cc:graph.vertexSet()) {
			if(cc.trunk)continue;
			ConnectionEdge bestEdge=cc.bestOutgoingEdge();
			for(ConnectionEdge edge : graph.outgoingEdgesOf(cc))if(edge!=bestEdge)edge.activated=false;
		}
	}
	
	public static void reconnectDisconnectedBranches(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,int formalism,boolean workAlsoBranches,boolean hack) {
		System.out.println("Reconnection of disconnected branches");
		int Nalso=0;
		int[]associations=null;
		ArrayList<ConnectionEdge>tabKeepEdges=new ArrayList<ConnectionEdge>();
		ArrayList<CC[]>tabKeepCCStart=new ArrayList<CC[]>();

		//Disconnect branching on trunk
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
		
		//Identify the disconnected branches
		double thresholdScore=5; 
		ArrayList<CC>listStart=new ArrayList<CC>();
		for(CC cc: graph.vertexSet()) {
			cc.stamp=0;
			if(cc.trunk)continue;
			if(cc.day<2)continue;
			if(cc.bestIncomingActivatedEdge()==null) {
				CC cctmp=cc;
				int tot=cc.nPixels;
				while(cctmp.bestOutgoingActivatedEdge()!=null) {
					cctmp=cctmp.bestOutgoingActivatedEdge().target;
					tot+=cctmp.nPixels;
				}
				//Count size
				if(tot>=MIN_SIZE_CC && cc.y()>150) {
					listStart.add(cc);
					cc.stamp=tot;
				}
			}
		}

		
		//Identify the possible dead ends 
		ArrayList<CC>listStop=new ArrayList<CC>();
		for(CC cc: graph.vertexSet()) {
			if(cc.trunk)continue;
			if(cc.bestOutgoingActivatedEdge()==null) {
				CC cctmp=cc;
				int tot=cc.nPixels;
				while(cctmp.bestIncomingActivatedEdge()!=null && (!cctmp.bestIncomingActivatedEdge().trunk && (cctmp.bestIncomingActivatedEdge().source.day>1))) {
					cctmp=cctmp.bestIncomingActivatedEdge().source;
					tot+=cctmp.nPixels;
				}
				//Count size
				if(tot>=MIN_SIZE_CC && cc.y()>150) {
					listStop.add(cc);
					cc.stamp=tot;
				}
			}
		}
		int Nstart=listStart.size();
		int Nstop=listStop.size();
		if(Nalso>0)associations=new int[Nalso+Nstop];

		if(Nstart==0 || ((Nstop+Nalso==0)))return;
		Timer t=new Timer();
		t.print("Start");
		if(formalism==1) {
			System.out.println("Running hungarian algorithm on Matrix ["+Nstop+"+"+Nalso+"]["+Nstart+"]");
			//Algorithme hongrois
			double[][]costMatrix=new double[Nstop+Nalso][Nstart];
			double[][]costMatrix2=new double[Nstop+Nalso][Nstart];
			CC ccStartWant=getCC(graph,22,6222,3011); //graph,10,7270,1848);
			CC ccStopWant=getCC(graph,21,6422,2859  );
			for(int i=0;i<Nstop;i++) {
	            for(int j=0;j<Nstart;j++) {    
	            	boolean debug=false;
	            	if(listStart.get(j)==ccStartWant && listStop.get(i)==ccStopWant) {
	            		debug=true;
	            	}
	            	if(listStop.get(i)==listStart.get(j))costMatrix[i][j]=IDENTITY_PENALTY;
	            	else costMatrix[i][j]=weightingOfPossibleHiddenEdge(graph,listStop.get(i),listStart.get(j),debug);
	            	costMatrix2[i][j]=costMatrix[i][j]; //7026 2172 11 (got start)  7270 1848 10 (stop)   7304 1908 (wanted start)
	            	if(listStart.get(j)==ccStartWant && listStop.get(i)==ccStopWant) {
	            		debug=true;
	            		System.out.println("Cost start want="+costMatrix2[i][j]);
	            	}
	            }
	        }
			int in=0;
			for(int j=0;j<Nstart;j++)for(int i=Nstop;i<Nstop+Nalso;i++) costMatrix[i][j]=SEMI_PENALTY*2;//Cout pour une feuille ou un start de finir tout seul
			
			for(int j=0;j<Nstart;j++) {
				if(isIn(listStart.get(j),tabKeepCCStart)>=0) {
					costMatrix[Nstop+(in)][j]=thresholdScore;//Cout pour une feuille de finir de nouveau sur le tronc principal
					associations[Nstop+(in++)]=isIn(listStart.get(j),tabKeepCCStart);
				}
			}

			HungarianAlgorithm hung1=new HungarianAlgorithm(costMatrix);
			HungarianAlgorithm hung=hung1;
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
				ConnectionEdge edge=new ConnectionEdge( xCon,yCon, 0, ccStop,ccStart,0,0); 
				edge.activated=true;
				edge.hidden=true;
				edge.trunk=false;
				try {
					graph.addEdge(ccStop, ccStart,edge); 
					graph.setEdgeWeight(edge, minWeight);
					CC c=ccStop.getActivatedRoot();//TODO : set in this function an antiloop stuff for ml1b2, there should be a loop. To investigate ?
					if(c!=null)c.stamp+=ccStart.stamp;
	//				listStop.remove(ccStop);//Remove ccStop from list
				}catch(java.lang.IllegalArgumentException loops) {
					listStop.remove(ccStop);//Remove ccStop from list
				}
			}
			
			if(hack) {
				double xCon=ccStopWant.r.getContourCentroid()[0]*0.5+ccStartWant.r.getContourCentroid()[0]*0.5;
				double yCon=ccStopWant.r.getContourCentroid()[1]*0.5+ccStartWant.r.getContourCentroid()[1]*0.5;
				ConnectionEdge edge=new ConnectionEdge( xCon,yCon, 0, ccStopWant,ccStartWant,0,0); 
				edge.activated=true;
				edge.hidden=true;
				edge.trunk=false;
				graph.addEdge(ccStopWant, ccStartWant,edge);
				graph.setEdgeWeight(edge, 0);
				CC c=ccStopWant.getActivatedRoot();//TODO : set in this function an antiloop stuff for ml1b2, there should be a loop. To investigate ?
				if(c!=null)c.stamp+=ccStartWant.stamp;
			}

			
		 	System.out.println( "Mean score = "+(meanScore/N));
			for(int i=listStop.size();i<Nstop+Nalso;i++) {
				if(solutions[i]==-1) {
					continue;
				}
				graph.getEdge(tabKeepCCStart.get(associations[i])[0], tabKeepCCStart.get(associations[i])[1]).activated=true;
			}
			System.out.println("Algo ok.");
		}
		if(formalism==2) {
			int step=0;
			boolean finished=false;
			while(!finished) {
				System.out.println("Running progressive hungarian algorithm step "+(step++));
				//Algorithme hongrois
				double[][]costMatrix=new double[Nstop][Nstart];
				double[][]costMatrix2=new double[Nstop][Nstart];
//				CC ccStopTest=getCC(graph,22,4713,2562);
//				CC ccStartTest=getCC(graph,20,9074,4076);
				for(int i=0;i<Nstop;i++) {
		            for(int j=0;j<Nstart;j++) {
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
					ConnectionEdge edge=new ConnectionEdge( xCon,yCon, 0, ccStop,ccStart,0,0); 
					edge.activated=true;
					edge.hidden=true;
					edge.trunk=false;
					try {
					graph.addEdge(ccStop, ccStart,edge); 
					graph.setEdgeWeight(edge, minWeight);
					ccStop.getActivatedRoot().stamp+=ccStart.stamp;
	//				listStop.remove(ccStop);//Remove ccStop from list
					}catch(java.lang.IllegalArgumentException loops) {
						listStop.remove(ccStop);//Remove ccStop from list
					}
				}
			 	System.out.println( "Score moyen = "+(meanScore/N));
			 	if((listStart.size()==0 || listStop.size()==0))continue;
			}
			System.out.println("Algo ok.");
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
					ConnectionEdge edge=new ConnectionEdge( xCon,yCon, 0, ccStop,ccStart,0,0); 
					edge.activated=true;
					edge.hidden=true;
					edge.trunk=false;
					try {
					graph.addEdge(ccStop, ccStart,edge); 
					graph.setEdgeWeight(edge, minWeight);
					ccStop.getActivatedRoot().stamp+=ccStart.stamp;
					listStop.remove(ccStop);//Remove ccStop from list
					}catch(java.lang.IllegalArgumentException loops) {
						listStop.remove(ccStop);//Remove ccStop from list
					}
				}
				listStart.remove(ccStart);//Remove ccStart from list	
			}
			System.out.println("Algo ok.");

		}
		if(workAlsoBranches) {
			for(ConnectionEdge edge:tabKeepEdges) {
				if(edge.target.bestIncomingActivatedEdge()==null)edge.activated=true;
			}			
		}

		t.print("Stop");
	}

	public static RootModel refinePlongementOfCCGraph(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,ImagePlus distOut,double toleranceDistToCentralLine) {
		//Prepare dataOut
		FSR sr= (new FSR());
		sr.initialize();
		RootModel rm=new RootModel();

		//Identify some features
		for(CC cc : graph.vertexSet()) {
			if(cc.trunk) {
				if(cc.day==1)cc.isPrimStart=true;			
				if( cc.getPrimChild()==null) cc.isPrimEnd=true;
			}
			if(!cc.trunk) {
				if(cc.bestIncomingActivatedCC()!=null && cc.bestIncomingActivatedCC().trunk) cc.isLatStart=true;
				if( cc.getLatChild()==null) cc.isLatEnd=true;				
			}
		}
		ArrayList<Root>listRprim=new ArrayList<Root>();
		ArrayList<Integer>listNprim=new ArrayList<Integer>();
		ArrayList<Integer>listDprim=new ArrayList<Integer>();
		ArrayList<Root>listRlat=new ArrayList<Root>();
		ArrayList<Integer>listNlat=new ArrayList<Integer>();
		ArrayList<Integer>listDlat=new ArrayList<Integer>();
		
		boolean simplerSimplify=false;
		boolean debugPrim=false;
		boolean debugLat=false;
		
		//Processing primary roots
		//dessiner la somme de pathlines des racines et l'associer aux CC. 		
		for(CC cc : graph.vertexSet()) {
			if((cc.day!=1) || (!cc.trunk)) continue;
			if(debugPrim)System.out.println("\nPROCESSING PLANT primary, starting with CC "+cc);
			//Identification of connected part of the root
			CC ccNext=cc;
			ArrayList<ArrayList<CC>> llcc=new ArrayList<ArrayList<CC>>();
			ArrayList<ArrayList<Integer>>toKeep=new ArrayList<ArrayList<Integer>>();
			ArrayList<CC>lccFuse=new ArrayList<CC>();
			llcc.add(new ArrayList<CC>());
			llcc.get(0).add(ccNext);
			Root rPrim=new Root(null, rm, "",1);
			listRprim.add(rPrim);
			listNprim.add(llcc.get(0).get(0).n);
			listDprim.add(llcc.get(0).get(0).day);
			int ind=0;
			while(ccNext.getPrimChild()!=null) {
				if(ccNext.isHiddenPrimChild()) {
					if(debugPrim)System.out.println("Next CC is hidden");
					llcc.add(new ArrayList<CC>());
					ind++;
				}
				ccNext=ccNext.getPrimChild();
				listRprim.add(rPrim);
				listNprim.add(ccNext.n);
				listDprim.add(ccNext.day);
				llcc.get(ind).add(ccNext);
				if(debugPrim)System.out.println("Adding "+ccNext+" to array number "+ind);
			}
			
			
			//Separate dijkstra path processing of the respective parts
			//PRIMARY ROOTS
			//Compute starting distance (when be for lateral)
			double startingDistance=0;
			ArrayList<Double>distInter=new ArrayList<Double>();
			ArrayList<Double>timeInter=new ArrayList<Double>();
			double cumulatedDistance=startingDistance;//TODO : compute distance from stuff
			int[]nextSource=null;				
			int[]currentSource=null;
			int[]currentTarget=null;
			int[]previousTarget=null;
			
			for(int indl=0;indl<llcc.size();indl++) {
				toKeep.add(new ArrayList<Integer>());
				List<CC>lcc=llcc.get(indl);
				int nCC=lcc.size();
				CC ccFirst=lcc.get(0);
				CC ccLast=lcc.get(nCC-1);
				
				//Identify starting point
				if(indl>0) {//It is at least the second connected component
					currentSource=nextSource;
					cumulatedDistance+=VitimageUtils.distance(previousTarget[0], previousTarget[1], currentSource[0]+ccFirst.xB, currentSource[1]+ccFirst.yB);
				}
				else 	{
					currentSource=ccFirst.getExpectedSource();
				}
				
				//Identify target point
				if(ccLast.getPrimChild()==null) {
					//End of primary : Identify target in ccLast
					int[]coords=ccLast.getExpectedSource();
					currentTarget=ccLast.determineTargetGeodesicallyFarestFromTheSource(coords);
				}

				else {
					//Identify source in next, then target in this
					CC ccFirstNext=ccLast.getPrimChild();
					int[]coords=ccLast.getExpectedSource();
					int[][]sourceTarget=cc.findHiddenStartStopToInOtherCC(ccFirstNext, coords);
					nextSource=sourceTarget[1];
					currentTarget=sourceTarget[0];
					previousTarget=new int[] {sourceTarget[0][0],sourceTarget[0][1]};
					previousTarget[0]+=ccLast.xB;
					previousTarget[1]+=ccLast.yB;
				}
			
				//Compute dijkstra path
				CC ccFuse=CC.fuseListOfCCIntoSingleCC(llcc.get(indl));
				lccFuse.add(ccFuse);
				currentSource[0]+=(ccFirst.xB-ccFuse.xB);
				currentSource[1]+=(ccFirst.yB-ccFuse.yB);					
				currentTarget[0]+=(ccLast.xB-ccFuse.xB);
				currentTarget[1]+=(ccLast.yB-ccFuse.yB);					
				ccFuse.determineVoxelShortestPath(currentSource, currentTarget, 8, null);
				cumulatedDistance=ccFuse.setDistancesToMainDijkstraPath(cumulatedDistance);
				
				
				//Evaluate the timing along dijkstra path
				//Set first pixel to birthDate of root
				//Walking along dijkstraPath, and attribute to each a componentIndex
				int[]indices=new int[ccFuse.mainDjikstraPath.size()];
				for(int n=0;n<ccFuse.mainDjikstraPath.size();n++) {
					Pix p=ccFuse.mainDjikstraPath.get(n);
					int xx=p.x+ccFuse.xB;
					int yy=p.y+ccFuse.yB;
					for(int i=0;i<nCC;i++) {
						if(lcc.get(i).r.contains(xx, yy)) {
							indices[n]=i;
						}
					}
				}


				//Eventually add the point for the first if it is the first component in llcc
				if(indl==0) {
					timeInter.add(new Double( lcc.get(0).day-1 ));
					distInter.add(ccFuse.mainDjikstraPath.get(0).wayFromPrim);
					if(debugPrim)				System.out.println("Adding a point at indl="+indl+" indcc="+0+" time="+timeInter.get(timeInter.size()-1)+" dist="+distInter.get(timeInter.size()-1));
				}
				
				//For each component except the last, identify the last point of it and If necessary, add the last one (see the for loop condition)
				for(int i=0;i<(lcc.size()-1) ; i++) {
					double distMax=-1;
					int indMax=-1;
					for(int n=0;n<ccFuse.mainDjikstraPath.size();n++) {
						if(indices[n]==i) {
							distMax=ccFuse.mainDjikstraPath.get(n).wayFromPrim;
							indMax=n;
						}
					}
					if(indMax!=-1) {
						distInter.add(new Double(distMax));	
						timeInter.add(new Double(lcc.get(i).day));	
						toKeep.get(indl).add(indMax);
					}
					if(debugPrim)					System.out.println("Adding a point at indl="+indl+" indcc="+0+" time="+timeInter.get(timeInter.size()-1)+" dist="+distInter.get(timeInter.size()-1));
				}
				if(indl==llcc.size()-1) {
					distInter.add(new Double(ccFuse.mainDjikstraPath.get(ccFuse.mainDjikstraPath.size()-1).wayFromPrim));	
					timeInter.add(new Double(lcc.get(lcc.size()-1).day));	
				}

			}	
			//Convert results of correspondance into double tabs
			int N=distInter.size();double[]xPoints=new double[N];double[]yPoints=new double[N];for(int i=0;i<N;i++) {xPoints[i]=distInter.get(i);yPoints[i]=timeInter.get(i);}	
				

			
			//Evaluate time for all the respective dijkstraPath	and convert to RSML
			for(int li=0;li<lccFuse.size();li++) {
				CC ccF=lccFuse.get(li);
				List<CC>lcc=llcc.get(li);
				//Propagate distance into the ccFuse's	
				ccF.updateAllDistancesToTrunk();
				
				//Convert distance into time	
				for(Pix p:ccF.pixGraph.vertexSet()) {
					p.time=SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim,xPoints,yPoints);
					p.timeOut=SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim+p.distOut,xPoints,yPoints);
				}

				//Back copy to the initial CCs
				for(CC c : lcc) {
					for(Pix p:c.pixGraph.vertexSet()) {
						Pix p2=ccF.getPix(p.x+c.xB-ccF.xB, p.y+c.yB-ccF.yB);
						p.dist=p2.dist;
						p.distanceToSkeleton=p2.distanceToSkeleton;
						p.distOut=p2.distOut;
						p.isSkeleton=p2.isSkeleton;
						p.time=p2.time;
						p.timeOut=p2.timeOut;
						p.wayFromPrim=p2.wayFromPrim;
						p2.offX=c.xB;
						p2.offY=c.yB;
					}
				}

				//Subsample respective dijkstra path with beucker algorithm, and collect RSML points with speed and 
				List<Pix> list= simplerSimplify ? DouglasPeuckerSimplify.simplifySimpler(ccF.mainDjikstraPath,toKeep.get(li) ,3) :
					DouglasPeuckerSimplify.simplify(ccF.mainDjikstraPath,toKeep.get(li) ,toleranceDistToCentralLine);
				for(int i=0;i<list.size()-1;i++) {
					Pix p=list.get(i);
					rPrim.addNode(p.x+ccF.xB,p.y+ccF.yB,p.time,(i==0)&&(li==0));
				}

				Pix p=list.get(list.size()-1);
				rPrim.addNode(p.x+ccF.xB,p.y+ccF.yB,p.time,false);
				if(li!=(lccFuse.size()-1))rPrim.setLastNodeHidden();
			}
			rPrim.computeDistances();
			rm.rootList.add(rPrim);
		}
				
				
		

		
		
		
		
		//Processing lateral roots
		//dessiner la somme de pathlines des racines et l'associer aux CC. 
		int incrLat=1;
		for(CC cc : graph.vertexSet()) {
			
			if(!cc.isLatStart)continue;
			if(debugLat)	System.out.println("\nProcessing lateral root #"+(incrLat++)+" : "+cc);
			
			//Identification of correspondant primary root
			Root myRprim=null;
			for(int i=0;i<listRprim.size();i++) {
				if(listNprim.get(i)==cc.bestIncomingActivatedEdge().source.n)myRprim=listRprim.get(i);
			}
			if(myRprim==null) IJ.showMessage("Rprimnull at "+cc);

			
			
			//Identification of connected part of the root
			CC ccNext=cc;
			ArrayList<ArrayList<CC>> llcc=new ArrayList<ArrayList<CC>>();
			ArrayList<ArrayList<Integer>>toKeep=new ArrayList<ArrayList<Integer>>();
			ArrayList<CC>lccFuse=new ArrayList<CC>();
			llcc.add(new ArrayList<CC>());
			llcc.get(0).add(ccNext);
			int ind=0;
			while(ccNext.getLatChild()!=null) {
				if(ccNext.isHiddenLatChild()) {
					llcc.add(new ArrayList<CC>());
					ind++;
				}
				ccNext=ccNext.getLatChild();
				llcc.get(ind).add(ccNext);
				if(debugLat)	System.out.println("Adding "+ccNext+" to array number "+ind);
			}
		
			//Separate dijkstra path processing of the respective parts
			//LATERAL ROOTS//////////////////////////////////////////////////////////////////////////////////////
			//Compute starting distance (when be for lateral)
			double startingDistance=0; //TODO
			ArrayList<Double>distInter=new ArrayList<Double>();
			ArrayList<Double>timeInter=new ArrayList<Double>();
			double cumulatedDistance=startingDistance;
			int[]nextSource=null;				
			int[]previousTarget=null;
			int[]currentSource=null;
			int[]currentTarget=null;
			
			for(int indl=0;indl<llcc.size();indl++) {
				toKeep.add(new ArrayList<Integer>());
				List<CC>lcc=llcc.get(indl);
				int nCC=lcc.size();
				CC ccFirst=lcc.get(0);
				CC ccLast=lcc.get(nCC-1);
				CC ccFuse=CC.fuseListOfCCIntoSingleCC(llcc.get(indl));
				lccFuse.add(ccFuse);
				boolean debug=false;
				//if(ccLast==getCC(graph, 4245,4237))debug=true;
				//System.out.println("Debug !");
				//Identify starting point
				if(indl>0) {//It is at least the second connected component
					currentSource=nextSource;
					cumulatedDistance+=VitimageUtils.distance(previousTarget[0], previousTarget[1], currentSource[0]+ccFirst.xB, currentSource[1]+ccFirst.yB);
				}
				else 	currentSource=ccFirst.getExpectedSource();
				
				
				//Identify target point
				if(indl==(llcc.size()-1)) {
					if(debug)System.out.println("End of lateral : "+lcc.get(lcc.size()-1));
					//End of primary : Identify target in ccLast
//					int[]coords=ccLast.getNextSourceFromFacetConnexion(ccLast.bestIncomingActivatedEdge()); //currentSource;
					//currentTarget=ccLast.determineTargetGeodesicallyFarestFromTheSource(coords);
					int[]coords=ccFirst.getNextSourceFromFacetConnexion(ccFirst.bestIncomingActivatedEdge()); //currentSource;
					coords=new int[] {coords[0]+ccFirst.xB-ccFuse.xB,coords[1]+ccFirst.yB-ccFuse.yB};
					currentTarget=ccFuse.determineTargetGeodesicallyFarestFromTheSource(coords);
					currentTarget[0]+=(ccFuse.xB-ccLast.xB);
					currentTarget[1]+=(ccFuse.yB-ccLast.yB);
					if(debug)System.out.println("Coords target of last = "+currentTarget[0]+","+currentTarget[1]);
				}
				else {
					//Identify source in next, then target in this
					CC ccFirstNext=ccLast.getLatChild();
					int[]coords=ccLast.getExpectedSource();
					int[][]sourceTarget=ccLast.findHiddenStartStopToInOtherCC(ccFirstNext, coords);
					nextSource=sourceTarget[1];
					currentTarget=sourceTarget[0];
					previousTarget=new int[] {sourceTarget[0][0],sourceTarget[0][1]};
					previousTarget[0]+=ccLast.xB;
					previousTarget[1]+=ccLast.yB;
				}
			
				//Compute dijkstra path
				currentSource[0]+=(ccFirst.xB-ccFuse.xB);
				currentSource[1]+=(ccFirst.yB-ccFuse.yB);					
				currentTarget[0]+=(ccLast.xB-ccFuse.xB);
				currentTarget[1]+=(ccLast.yB-ccFuse.yB);					
				
				if(debug) {
					ccFuse.drawDist().show();
					System.out.println("Coords source of fuse = "+currentSource[0]+","+currentSource[1]);
					System.out.println("Coords target of fuse = "+currentTarget[0]+","+currentTarget[1]);
				}
				
				ccFuse.determineVoxelShortestPath(currentSource, currentTarget, 8, null);
				cumulatedDistance=ccFuse.setDistancesToMainDijkstraPath(cumulatedDistance);
				if(debug)System.out.println("Lenght of path="+ccFuse.mainDjikstraPath.size());
				if(debug)VitimageUtils.waitFor(60000000);
				
				//Evaluate the timing along dijkstra path
				//Set first pixel to birthDate of root
				//Walking along dijkstraPath, and attribute to each a componentIndex
				int[]indices=new int[ccFuse.mainDjikstraPath.size()];
				for(int n=0;n<ccFuse.mainDjikstraPath.size();n++) {
					Pix p=ccFuse.mainDjikstraPath.get(n);
					int xx=p.x+ccFuse.xB;
					int yy=p.y+ccFuse.yB;
					for(int i=0;i<nCC;i++) {
						if(lcc.get(i).r.contains(xx, yy)) {
							indices[n]=i;
						}
					}
				}
	
	
				//Eventually add the point for the first if it is the first component in llcc
				if(indl==0) {
					timeInter.add(new Double( lcc.get(0).day-1 ));
					distInter.add(ccFuse.mainDjikstraPath.get(0).wayFromPrim);
				}
				//For each component except the last, identify the last point of it and If necessary, add the last one (see the for loop condition)
				for(int i=0;i<lcc.size()-1 ; i++) {
					double distMax=-1;
					int indMax=-1;
					for(int n=0;n<ccFuse.mainDjikstraPath.size();n++) {
						if(indices[n]==i) {
							distMax=ccFuse.mainDjikstraPath.get(n).wayFromPrim;
							indMax=n;
						}
					}
					if(indMax>=0) {
						distInter.add(new Double(distMax));	
						timeInter.add(new Double(lcc.get(i).day));	
						toKeep.get(indl).add(indMax);
					}
				}
				if(indl==llcc.size()-1) {
					distInter.add(new Double(ccFuse.mainDjikstraPath.get(ccFuse.mainDjikstraPath.size()-1).wayFromPrim));	
					timeInter.add(new Double(lcc.get(lcc.size()-1).day));	
				}
			}	
			//Convert results of correspondance into double tabs
			int N=distInter.size();double[]xPoints=new double[N];double[]yPoints=new double[N];for(int i=0;i<N;i++) {xPoints[i]=distInter.get(i);yPoints[i]=timeInter.get(i);}	
				
			
			
			
			//Evaluate time for all the respective dijkstraPath	and convert to RPrimSML
			Root rLat=new Root(null, rm, "",2);
			listRlat.add(rLat);
			listNlat.add(llcc.get(0).get(0).n);
			listDlat.add(llcc.get(0).get(0).day);
			for(int li=0;li<lccFuse.size();li++) {
				if(debugLat)	System.out.println("Processing path for component "+li);
				CC ccF=lccFuse.get(li);
				List<CC>lcc=llcc.get(li);
				//Propagate distance into the ccFuse's	
				ccF.updateAllDistancesToTrunk();
				
				//Convert distance into time	
				for(Pix p:ccF.pixGraph.vertexSet()) {
					p.time=SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim,xPoints,yPoints);
					p.timeOut=SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim+p.distOut,xPoints,yPoints);
				}
	
				//Back copy to the initial CCs
				for(CC c : lcc) {
					for(Pix p:c.pixGraph.vertexSet()) {
						Pix p2=ccF.getPix(p.x+c.xB-ccF.xB, p.y+c.yB-ccF.yB);
						p.dist=p2.dist;
						p.distanceToSkeleton=p2.distanceToSkeleton;
						p.distOut=p2.distOut;
						p.isSkeleton=p2.isSkeleton;
						p.time=p2.time;
						p.timeOut=p2.timeOut;
						p.wayFromPrim=p2.wayFromPrim;
						p2.offX=c.xB;
						p2.offY=c.yB;
					}
				}
	
				//Subsample respective dijkstra path with beucker algorithm, and collect RSML points
				List<Pix> list= simplerSimplify ? DouglasPeuckerSimplify.simplifySimpler(ccF.mainDjikstraPath,toKeep.get(li) ,3) :
					DouglasPeuckerSimplify.simplify(ccF.mainDjikstraPath,toKeep.get(li) ,toleranceDistToCentralLine);
				if(debugLat)	System.out.println("Simplifying a list of "+ccF.mainDjikstraPath.size()+" to list of "+list.size());
				for(int i=0;i<list.size()-1;i++) {
					Pix p=list.get(i);
					rLat.addNode(p.x+ccF.xB,p.y+ccF.yB,p.time,(i==0)&&(li==0));
				}	
				Pix p=list.get(list.size()-1);
				rLat.addNode(p.x+ccF.xB,p.y+ccF.yB,p.time,false);
				if(li!=(lccFuse.size()-1))rLat.setLastNodeHidden();
			}
			rLat.computeDistances();
			rLat.computeDistances();
			myRprim.attachChild(rLat);
			rLat.attachParent(myRprim);
			rm.rootList.add(rLat);
		}
		return rm;
	}



	
	

	
	/** 
	 * Various helpers ----------------------------------------------------------------------------------------------------------------------------------------*/
	 //Determine if ccStop and ccStart are connected in the undirected region adjacency graph limited to ccStop, ccStart and older CC
	public static int areConnectedByPathOfCC(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,CC ccStop,CC ccStart,boolean debug) {
		int Nmax=10000;//ccStart.day-1;
		for(CC cc: graph.vertexSet()) cc.stamp2=0;			
		ccStop.stamp2=1;
		ArrayList<CC>visited=new ArrayList<CC>();
		ArrayList<CC>toVisit=new ArrayList<CC>();
		visited.add(ccStop);
		if(ccStop.bestIncomingActivatedCC()!=null)ccStop.bestIncomingActivatedCC().stamp=1;
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
		CC ccStartNext=ccStart.bestOutgoingActivatedCC();
		if(ccStartNext !=null && ccStartNext.trunk)ccStartNext=null;
		CC ccStopPrevious=ccStop.bestIncomingActivatedCC();
		if(ccStopPrevious !=null && ccStopPrevious.trunk)ccStopPrevious=null;

		//CONNECTED
		double connectedWeight=0;
		//if a way does not exist
		int way=areConnectedByPathOfCC(graph,ccStop,ccStart,debug);
		if(way<0)connectedWeight=OUT_OF_SILHOUETTE_PENALTY;
		else connectedWeight=way*2;
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
		angularWeight*=4;
		if(debug)System.out.println("Estimated speed="+speed+" and angweight="+angularWeight);
		

		//DISTANCE Is the pathway length likely ?
		double deltaDay=ccStart.day-ccStop.day;
		if (deltaDay<=0)deltaDay=0.75;
		double expectedDistance=speed*(deltaDay);
		double actualDistance=ccStop.euclidianDistanceToCC(ccStart);
		if(debug)System.out.println("H026 actualDist="+actualDistance);
		if(debug)System.out.println("H026 expectedDist="+expectedDistance);
		double distanceWeight=3*((1-VitimageUtils.similarity(expectedDistance, actualDistance))*deltaDay+Math.abs(actualDistance-expectedDistance)/(deltaDay*expectedDistance));
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
	
	public static int isIn(CC cc, ArrayList<CC[]>tabCC) {
		for(int i=0;i<tabCC.size();i++) {
			if(cc==tabCC.get(i)[1])return i;
		}
		return -1;
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


	
	
	
	
	/** Rendering helpers -------------------------------------------------------------------------------------------------------------------------------------------*/
	public static ImagePlus drawDistanceOrTime(ImagePlus source,SimpleDirectedWeightedGraph <CC,ConnectionEdge>graph,boolean trueDoDistFalseDoTime,boolean onlyDoSkeleton,int mode_1Total_2OutsideDistOrIntTime_3SourceDist) {
		ImagePlus imgDist=VitimageUtils.convertToFloat(VitimageUtils.nullImage(source));
		int X=imgDist.getWidth();
		float[]valDist=(float[])imgDist.getStack().getProcessor(1).getPixels();
		for(CC cc : graph.vertexSet()) {
			int x0=cc.r.getBounds().x;
			int y0=cc.r.getBounds().y;
			for(Pix p:cc.pixGraph.vertexSet()) {
				int index=X*(p.y+y0)+(p.x+x0);
				if(onlyDoSkeleton && (!p.isSkeleton))continue;
				if(mode_1Total_2OutsideDistOrIntTime_3SourceDist==1) 	valDist[index]=(trueDoDistFalseDoTime) ? ((float) (p.wayFromPrim+p.distOut)) : (float)p.timeOut ;
				else if(mode_1Total_2OutsideDistOrIntTime_3SourceDist==2) 	valDist[index]=(trueDoDistFalseDoTime) ? ((float) (p.distOut)) : (float)(cc.day) ;
				else                                             	valDist[index]=(trueDoDistFalseDoTime) ? ((float) (p.wayFromPrim)) : (float)(p.time) ;
			}
		}
		return imgDist;
	}
	
	public static ImagePlus drawDistanceTime(ImagePlus source,SimpleDirectedWeightedGraph <CC,ConnectionEdge>graph,int mode_1Skel_2All_3AllWithTipDistance) {
		ImagePlus imgDist=VitimageUtils.convertToFloat(VitimageUtils.nullImage(source));
		ImagePlus imgTime=imgDist.duplicate();
		ImagePlus imgTimeOut=imgDist.duplicate();
		ImagePlus imgDistOut=imgDist.duplicate();
		ImagePlus imgDistSum=imgDist.duplicate();
		int X=imgDist.getWidth();
		float[]valDist=(float[])imgDist.getStack().getProcessor(1).getPixels();
		float[]valDistOut=(float[])imgDistOut.getStack().getProcessor(1).getPixels();
		float[]valDistSum=(float[])imgDistSum.getStack().getProcessor(1).getPixels();
		float[]valTime=(float[])imgTime.getStack().getProcessor(1).getPixels();
		float[]valTimeOut=(float[])imgTimeOut.getStack().getProcessor(1).getPixels();
		for(CC cc : graph.vertexSet()) {
			int x0=cc.r.getBounds().x;
			int y0=cc.r.getBounds().y;
			for(Pix p:cc.pixGraph.vertexSet()) {
				int index=X*(p.y+y0)+(p.x+x0);
				if(mode_1Skel_2All_3AllWithTipDistance==1 && (!p.isSkeleton))continue;
				if(mode_1Skel_2All_3AllWithTipDistance<3) {
					valDist[index]=(float) p.wayFromPrim;
					valTime[index]=(float) p.time;					
				}
				if(mode_1Skel_2All_3AllWithTipDistance==3) {
					valDist[index]=(float) (p.wayFromPrim+p.distOut);
					valTime[index]=(float) (p.timeOut);
				}
				if(mode_1Skel_2All_3AllWithTipDistance==4 || mode_1Skel_2All_3AllWithTipDistance==0 ) {
					valDist[index]=(float) (p.wayFromPrim);
					valDistOut[index]=(float) (p.distOut);
					valDistSum[index]=(float) (p.wayFromPrim+p.distOut);
					valTime[index]=(float) (p.time);
					valTimeOut[index]=(float) (p.timeOut);
				}
			}
		}
		if(mode_1Skel_2All_3AllWithTipDistance==0)return imgTimeOut;
		else if(mode_1Skel_2All_3AllWithTipDistance<4) return VitimageUtils.slicesToStack(new ImagePlus[]{imgDist,imgTime});
		else return VitimageUtils.slicesToStack(new ImagePlus[]{imgDist,imgDistOut,imgDistSum,imgTime,imgTimeOut});
	}
		
	public static ImagePlus drawGraph(ImagePlus imgDates,SimpleDirectedWeightedGraph <CC,ConnectionEdge>graph,double circleRadius,int lineThickness,int sizeFactor) {
		System.out.println("Drawing graph");

		//Draw the silhouette
		ImagePlus contour=VitimageUtils.nullImage(imgDates);
		ImagePlus in=VitimageUtils.nullImage(imgDates);
		if(sizeFactor>1) {
			ImagePlus bin=VitimageUtils.thresholdImage(imgDates, 0.5, 100000);
			ImagePlus binResize=VitimageUtils.resizeNearest(bin, imgDates.getWidth()*sizeFactor,  imgDates.getHeight()*sizeFactor, 1);
			ImagePlus ero=MorphoUtils.erosionCircle2D(binResize, 1);
			ImagePlus dil=MorphoUtils.dilationCircle2D(binResize, 1);
			contour=VitimageUtils.makeOperationBetweenTwoImages(dil, ero, 4, false);

			ImagePlus nonBinResize=VitimageUtils.resizeNearest(imgDates, imgDates.getWidth()*sizeFactor,  imgDates.getHeight()*sizeFactor, 1);
			ImagePlus ero2=MorphoUtils.erosionCircle2D(nonBinResize, 1);
			//ImagePlus dil2=MorphoUtils.dilationCircle2D(nonBinResize, 1);
			ImagePlus contour2=VitimageUtils.makeOperationBetweenTwoImages(nonBinResize,ero2, 4, false);
			contour2=VitimageUtils.thresholdImage(contour2, 0.5, 1000);
			contour2=VitimageUtils.makeOperationOnOneImage(contour2, 2, 255, true);
			contour=VitimageUtils.binaryOperationBetweenTwoImages(contour, contour2, 1);
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
			double ccx=(cc.x());
			double ccy=(cc.y());
			double factor=0.3+0.7*Math.log10(cc.nPixels);
			if(((incr++)%decile)==0) {
				System.out.print(incr+" ");
			}
			boolean extremity =isExtremity(cc,graph);
			if(extremity) {
				if(cc.nPixels>=MIN_SIZE_CC)VitimageUtils.drawCircleIntoImage(imgGraph, vx*(factor*circleRadius+4+(cc.trunk ? 2 : 0)), (int)Math.round(ccx*sizeFactor), (int)Math.round(ccy*sizeFactor), 0,12);
			}
			else {
				VitimageUtils.drawCircleIntoImage(imgGraph, vx*(factor*circleRadius+2+(cc.trunk ? 2 : 0)), (int)Math.round(ccx*sizeFactor), (int)Math.round((ccy)*sizeFactor), 0,255);
				VitimageUtils.drawCircleIntoImage(imgGraph, vx*(factor*circleRadius+1+(cc.trunk ? 1 : 0)), (int)Math.round(ccx*sizeFactor), (int)Math.round((ccy)*sizeFactor), 0,0);
			}
			VitimageUtils.drawCircleIntoImage(imgGraph, vx*(factor*circleRadius), (int)Math.round((ccx)*sizeFactor), (int)Math.round((ccy)*sizeFactor), 0,cc.day);
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
			double xCon=edge.connectionX+0.5;
			double yCon=edge.connectionY+0.5;
			double cc1x=(cc1.x());
			double cc2x=(cc2.x());
			double cc1y=(cc1.y());
			double cc2y=(cc2.y());
			
			//if(cc1.day==0 || cc2.day==0)continue; 
			int val=(int)(22-3*(graph.getEdgeWeight(edge)+1));
			if(val<4)val=4;
			if(val>255)val=255;
			if(cc1.day>0) VitimageUtils.drawSegmentInto2DByteImage(imgGraph,lineThickness+(edge.trunk ? 8 : 0),val,(cc1x)*sizeFactor,(cc1y)*sizeFactor,xCon*sizeFactor,yCon*sizeFactor,edge.hidden); 
			if(cc1.day>0)VitimageUtils.drawSegmentInto2DByteImage(imgGraph,lineThickness+(edge.trunk ? 8 : 0),val,xCon*sizeFactor,yCon*sizeFactor,cc2x*sizeFactor,cc2y*sizeFactor,edge.hidden); 
			VitimageUtils.drawSegmentInto2DByteImage(imgGraph,1+(edge.trunk ? 3 : 0),0,cc1x*sizeFactor,cc1y*sizeFactor,xCon*sizeFactor,yCon*sizeFactor,edge.hidden); 
			VitimageUtils.drawSegmentInto2DByteImage(imgGraph,1+(edge.trunk ? 3 : 0),0,xCon*sizeFactor,yCon*sizeFactor,cc2x*sizeFactor,cc2y*sizeFactor,edge.hidden); 
			VitimageUtils.drawCircleIntoImage(imgGraph, 3, (int)Math.round (xCon*sizeFactor),(int)Math.round (yCon*sizeFactor), 0,12);
		}
		System.out.println(incrAct+" activated and "+incrNonAct+" non-activated");
		//Draw the vertices
		System.out.print("   3-Drawing central square for "+graph.vertexSet().size()+" vertices  ");
		incr=0;decile=graph.vertexSet().size()/10;
		for(CC cc:graph.vertexSet()) {
			double ccx=(cc.x());
			double ccy=(cc.y());
			if(((incr++)%decile)==0)System.out.print(incr+" ");
			boolean extremity =isExtremity(cc,graph);
			if(cc.nPixels>=MIN_SIZE_CC || !extremity)VitimageUtils.drawCircleIntoImage(imgGraph, vx*3, (int)Math.round(ccx*sizeFactor), (int)Math.round(ccy*sizeFactor), 0,extremity ? 12 : 0);
			if(cc.nPixels>=MIN_SIZE_CC || !extremity)VitimageUtils.drawCircleIntoImage(imgGraph, vx*2, (int)Math.round(ccx*sizeFactor), (int)Math.round(ccy*sizeFactor), 0,extremity ? 12 : 255);
		}
				
		imgDates.setDisplayRange(0, N+1);
		System.out.print("\n   4-High res misc drawing (100M +) ");

		System.out.print("1 ");//Build graphArea, a mask of all pixels where something is drawn (edges or vertices)
		ImagePlus graphArea=VitimageUtils.thresholdImage(imgGraph, 0.5, 1000000);
		graphArea=VitimageUtils.getBinaryMaskUnary(graphArea, 0.5);

		System.out.print("2 ");//Build contourArea, a mask of all contours, excepted pixels of graphArea
		ImagePlus contourArea=VitimageUtils.thresholdImage(contour, 0.5, 1000000000);
		contourArea=VitimageUtils.getBinaryMaskUnary(contourArea, 0.5);
		contourArea=VitimageUtils.binaryOperationBetweenTwoImages(contourArea, graphArea, 4);
		
		System.out.print("3 ");
		ImagePlus part1=VitimageUtils.makeOperationBetweenTwoImages(imgGraph, graphArea, 2, true);//Draw pixels of graph
		ImagePlus part2=VitimageUtils.makeOperationBetweenTwoImages(contour, contourArea, 2, true);//Draw pixels of contour

		System.out.print("4 ");
		imgGraph=VitimageUtils.makeOperationBetweenTwoImages(part1, part2, 1, false);
		imgGraph.setDisplayRange(0, N+1);
		System.out.print(" Ok.  ");
		
		return imgGraph;
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
		double cost=  (1-VitimageUtils.similarity(source.nPixels,target.nPixels)) + (dday<2 ? 0 : dday-1) + (source.day>1 ? -prodscal : 0); //TODO : source.day>0 ? C'est pas ça, on veut éviter les primaires !!!
		return cost;//TODO : on peut laisser dday-1, le cas ne sert pas, à tester
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

	public static CC getCC(SimpleDirectedWeightedGraph<CC,ConnectionEdge>graph,int x, int y) {
		//System.out.println("Looking for CC");
		CC ret=null;
		double minDist=1E8;
		for (CC cc: graph.vertexSet()) {
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

	
	
	
	/*
	public static RootModel refinePlongementOfCCGraphOLD(SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph,ImagePlus distOut,double toleranceDistToCentralLine) {
		//Prepare dataOut
		float[]valDist=(float[])distOut.getStack().getProcessor(1).getPixels();
		int X=distOut.getWidth();

		//Identifiy some features
		for(CC cc : graph.vertexSet()) {
			if(cc.trunk) {
				if(cc.day==1)cc.isPrimStart=true;			
				if( cc.getPrimChild()==null) cc.isPrimEnd=true;
			}
			if(!cc.trunk) {
				if(cc.bestIncomingActivatedCC()!=null && cc.bestIncomingActivatedCC().trunk) cc.isLatStart=true;
				if( getLatChild(cc, graph)==null) cc.isLatEnd=true;				
			}
		}
		ArrayList<double[]>distanceTimeSamples;
		ArrayList<CC>allCCofBranch;

		
		
		
		FSR sr= (new FSR());
		sr.initialize();
		RootModel rm=new RootModel();
		
		


		
		ArrayList<Root>listRprim=new ArrayList<Root>();
		ArrayList<Integer>listNprim=new ArrayList<Integer>();
		ArrayList<Integer>listDprim=new ArrayList<Integer>();
		
		
		//Processing primary roots
		//dessiner la somme de pathlines des racines et l'associer aux CC. 
		int incr=0;
		for(CC cc : graph.vertexSet()) {
			if(cc.day==1 && cc.trunk) {
				Root rPrim=new Root(null, rm, "",1);
				allCCofBranch=new ArrayList <CC>();
				distanceTimeSamples=new ArrayList<double[]>();
				incr++;
				System.out.println("PROCESSING PLANT "+cc);
				CC curCC=cc;
				double d=0;
				distanceTimeSamples.add(new double[] {0,0});
				while(curCC!=null) {	
					listRprim.add(rPrim);
					listNprim.add(curCC.n);
					listDprim.add(curCC.day);
					allCCofBranch.add(curCC);
					curCC.determineVoxelShortestPathTrunkRoot();
					d=curCC.setDistancesToShortestPathTrunk();
					distanceTimeSamples.add(new double[] {d,curCC.day});
					curCC.updateAllDistancesToTrunk();
					Pt2d[]tabPt=null;
					tabPt=DouglasPeuckerSimplify.simplify(curCC.mainDjikstraPath,new ArrayList<Integer>(), toleranceDistToCentralLine);
					if(curCC.day==1 && tabPt.length>1) rPrim.addNode(tabPt[0].getX()+curCC.xB(),tabPt[0].getY()+curCC.yB(),0,true);
					for(int i=1; i<tabPt.length-1; i++) 	rPrim.addNode(tabPt[i].getX()+curCC.xB(),tabPt[i].getY()+curCC.yB(),-1,false);
					rPrim.addNode(tabPt[tabPt.length-1].getX()+curCC.xB(),tabPt[tabPt.length-1].getY()+curCC.yB(),curCC.day,false);
					curCC=curCC.getPrimChild();
				}

				//Interpolate time for all pixels of the trunkg
				distanceTimeSamples=hackOnDistanceTimeSamples(distanceTimeSamples);
				double[]distTab=new double[distanceTimeSamples.size()];
				double[]timeTab=new double[distanceTimeSamples.size()];
				for(int i=0;i<distTab.length;i++) {distTab[i]=distanceTimeSamples.get(i)[0];timeTab[i]=distanceTimeSamples.get(i)[1];}	
				for(int i=0;i<distTab.length;i++) {distTab[i]=distanceTimeSamples.get(i)[0];timeTab[i]=distanceTimeSamples.get(i)[1];}		
				for(CC ccT:allCCofBranch) {
					int x0=ccT.r.getBounds().x;
					int y0=ccT.r.getBounds().y;
					for(Pix p:ccT.pixGraph.vertexSet()) {
						int index=X*(p.y+y0)+(p.x+x0);
						p.distOut=valDist[index];
					}
					try{
						//PolynomialSplineFunction psf=SplineAndPolyLineUtils.getInterpolator(distTab,timeTab,true);
						ccT.interpolateTimeFromReferencePointsUsingLinearInterpolator(distTab,timeTab);
					}catch(org.apache.commons.math3.exception.NonMonotonicSequenceException e) {
						e.printStackTrace();
					}
				}
				
				//Interpolate time for all points of the rsml
				rPrim.computeDistances();
				rPrim.interpolateTime();
				rm.rootList.add(rPrim);
			}
		}
		
		//Pour chacune des departs de racine latérale		
		for(CC cc : graph.vertexSet()) {
			if(!cc.isLatStart)continue;
			System.out.println("B 01 Processing a lateral root : "+cc);

			Root myRprim=null;
			for(int i=0;i<listRprim.size();i++) {
				if(listNprim.get(i)==cc.bestIncomingActivatedEdge().source.n)myRprim=listRprim.get(i);
			}

			if(myRprim==null) {
				IJ.showMessage("Rprimnull at "+cc);
				
			}
			Root rLat=new Root(null,rm,"",2);
			
			ConnectionEdge previousEdge=cc.bestIncomingActivatedEdge();
			if(previousEdge.hidden) {IJ.log("Warning, we got a lateral root emerging from being shadowed by another root. Situation is critical : no computation done for this root");continue;}
			//Parcourir successivement toutes les connexions, et determiner le plus d'infos possible de type target source, distance
			distanceTimeSamples=new ArrayList<double[]>();
			double[]coordsStart=new double[]{previousEdge.connectionX,previousEdge.connectionY};
			double currentDistance=previousEdge.distanceConnectionTrunk;
			int[]coordsS;
			//Si le start de la laterale provient d'un edge caché, rechercher le point de start
			if(previousEdge.hidden) {
				double xLat=cc.r.getContourCentroid()[0]-cc.r.getBounds().x;
				double yLat=cc.r.getContourCentroid()[1]-cc.r.getBounds().y;
				int[][]sourceTarget=cc.findHiddenStartStopToInOtherCC(previousEdge.source, new int[] {(int)xLat,(int)yLat});
				coordsStart=new double[] {sourceTarget[0][0],sourceTarget[0][1]};
				coordsS=sourceTarget[0];
				currentDistance=VitimageUtils.distance(sourceTarget[0][0]+cc.xB(),sourceTarget[0][1]+cc.yB(),sourceTarget[1][0]+previousEdge.source.xB(),sourceTarget[1][1]+previousEdge.source.yB());
			}
			else coordsS=cc.getNextSourceFromFacetConnexion(previousEdge);
			distanceTimeSamples.add(new double[] {currentDistance,cc.day-1});
			
			//Parcours de la branche
			CC curCC=cc;
			allCCofBranch=new ArrayList <CC>();
			boolean ending=false;
			String branch="";
			incr=0;
			while(!ending) {
				incr++;
				branch+="-"+curCC.day+"-"+curCC.n+" ";
				allCCofBranch.add(curCC);

		
				ConnectionEdge nextEdge=curCC.bestOutgoingActivatedEdge();
				if(nextEdge==null) {//end of root
					ending=true;
					int[]coordsT=curCC.determineTargetGeodesicallyFarestFromTheSource(coordsS);
					curCC.determineVoxelShortestPath(coordsS,coordsT,8,null);
					currentDistance=curCC.setDistanceToShortestPath(currentDistance);	
					curCC.updateAllDistancesToTrunk();
					distanceTimeSamples.add(new double[] {currentDistance,curCC.day});
				}
				else if(!nextEdge.hidden) {//No virtual ending, no problem
					double[]coordsTarget=new double[]{nextEdge.connectionX,nextEdge.connectionY};
					int[]coordsT=curCC.getPrevTargetFromFacetConnexion(nextEdge);
					double deltaEnd=VitimageUtils.distance(coordsTarget[0]-curCC.xB(),coordsTarget[1]-curCC.yB(),coordsT[0],coordsT[1]);
					curCC.determineVoxelShortestPath(coordsS,coordsT,8,null);
					currentDistance=curCC.setDistanceToShortestPath(currentDistance) + deltaEnd;				
					curCC.updateAllDistancesToTrunk();
					distanceTimeSamples.add(new double[] {currentDistance,curCC.day});
					coordsS=nextEdge.target.getNextSourceFromFacetConnexion(nextEdge);
				}
				else if(nextEdge.hidden) {			//If virtual ending
					//Find the bridge : 1) nearer source point Pst in the target component, and 2) the target point  Pts in the current CC that is the nearer to Pst
					int[][]targetSource=curCC.findHiddenStartStopToInOtherCC(nextEdge.target, coordsS);
					int[]coordsT=targetSource[0];
					curCC.determineVoxelShortestPath(coordsS,coordsT,8,null);
					currentDistance=curCC.setDistanceToShortestPath(currentDistance);
					currentDistance+= VitimageUtils.distance(targetSource[2][0],targetSource[2][1],targetSource[3][0],targetSource[3][1]);
					curCC.updateAllDistancesToTrunk();
					coordsS=targetSource[1];
				}				
				Pt2d[]tabPt=simplifyPolyLine(curCC.mainDjikstraPath, toleranceDistToCentralLine, true);
				if((incr==1 || previousEdge.hidden) && tabPt.length>1) rLat.addNode(tabPt[0].getX()+curCC.xB(),tabPt[0].getY()+curCC.yB(),incr==1 ? curCC.day-1 : -1,incr==1);
				for(int i=1; i<tabPt.length-1; i++) 	rLat.addNode(tabPt[i].getX()+curCC.xB(),tabPt[i].getY()+curCC.yB(),-1,false);
				rLat.addNode(tabPt[tabPt.length-1].getX()+curCC.xB(),tabPt[tabPt.length-1].getY()+curCC.yB(),curCC.day,false);
				if(nextEdge!=null && nextEdge.hidden)rLat.setLastNodeHidden();
				if(!ending) curCC=nextEdge.target;
				previousEdge=nextEdge;
			}
			
			//Une fois fini le parcours, reevaluer la correspondance distance-temps
			distanceTimeSamples=hackOnDistanceTimeSamples(distanceTimeSamples);
			double[]distTab=new double[distanceTimeSamples.size()];
			double[]timeTab=new double[distanceTimeSamples.size()];
			for(int i=0;i<distTab.length;i++) {distTab[i]=distanceTimeSamples.get(i)[0];timeTab[i]=distanceTimeSamples.get(i)[1];}			

			for(CC ccT:allCCofBranch) {
				int x0=ccT.r.getBounds().x;
				int y0=ccT.r.getBounds().y;
				for(Pix p:ccT.pixGraph.vertexSet()) {
					int index=X*(p.y+y0)+(p.x+x0);
					p.distOut=valDist[index];
				}
				try{
					//PolynomialSplineFunction psf=SplineAndPolyLineUtils.getInterpolator(distTab,timeTab,true);
					ccT.interpolateTimeFromReferencePointsUsingLinearInterpolator(distTab,timeTab);
				}catch(org.apache.commons.math3.exception.NonMonotonicSequenceException e) {
					e.printStackTrace();
				}
			
			}
			rLat.computeDistances();
			rLat.interpolateTime();
			myRprim.attachChild(rLat);
			rLat.attachParent(myRprim);
			rm.rootList.add(rLat);
		}		
		return rm;
	}
	*/

	/*
	public static SimpleDirectedWeightedGraph<CC,ConnectionEdge>  buildAndProcessGraph(ImagePlus imgDatesTmp,String mainDataDir,String ml,String boite,boolean makeNicePictures,boolean makeAllPictures) {
		double ray=5;
		int thickness=5;
		int sizeFactor=SIZE_FACTOR;
		int connexity=8;
	
		ImagePlus imgDatesHigh=VitimageUtils.convertFloatToByteWithoutDynamicChanges(imgDatesTmp);
		IJ.run(imgDatesHigh,"Fire","");
		int nDays=1+(int)Math.round(VitimageUtils.maxOfImage(imgDatesTmp));
		imgDatesHigh.setDisplayRange(0, nDays);		
		imgDatesHigh=VitimageUtils.resizeNearest(imgDatesTmp, imgDatesTmp.getWidth()*sizeFactor,imgDatesTmp.getHeight()*sizeFactor,1);
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph=null;
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph2=null;
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph3=null;
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph4=null;
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph5=null;
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph6=null;
		if(false && makeNicePictures) {
	
			//Build and duplicate initial graph
			boolean compute=true;			
			if(compute) {
				graph=buildGraphFromDateMap(imgDatesTmp,connexity);
				pruneGraph(graph, true);
				setFirstOrderCosts(graph);
				writeGraphToFile(graph,mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite+".ser");
			}
			else graph=readGraphFromFile(mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite);
	
						
			//Identify trunks 
			graph2=readGraphFromFile(mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite);	
			identifyTrunks(graph2);
			computeMinimumDirectedConnectedSpanningTree(graph2);
		
			//Identify trunks 
			graph3=readGraphFromFile(mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite);
			identifyTrunks(graph3);
			computeMinimumDirectedConnectedSpanningTree(graph3);
			disconnectUnevenBranches(graph3);
		
			
			//Identify trunks 
			graph4=readGraphFromFile(mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite);
			identifyTrunks(graph4);
			computeMinimumDirectedConnectedSpanningTree(graph4);
			disconnectUnevenBranches(graph4);
			reconnectDisconnectedBranches(graph4,0,false,false);		//reconnectSingleBranches(graph4);
		
			//Identify trunks 
			graph5=readGraphFromFile(mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite);
			identifyTrunks(graph5);
			computeMinimumDirectedConnectedSpanningTree(graph5);
			disconnectUnevenBranches(graph5);
			reconnectDisconnectedBranches(graph5,1,true,false);		//reconnectSingleBranches(graph4);
		}	
		//Identify trunks 
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graphP=buildGraphFromDateMap(imgDatesTmp,connexity);
		//graph6=readGraphFromFile(mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite);
		//graph6=readGraphFromFile(mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite);
		identifyTrunks(graphP);
		computeMinimumDirectedConnectedSpanningTree(graphP);
		disconnectUnevenBranches(graphP);
		reconnectDisconnectedBranches(graphP,1,true,boite.equals("00002"));		//reconnectSingleBranches(graph4);
		writeGraphToFile(graphP,mainDataDir+"/3_Graphs_Raw/"+"ML"+ml+"_Boite_"+boite);
		
		ImagePlus imgG=drawGraph(imgDatesTmp, graphP, ray, thickness,sizeFactor);		
		imgG.show();
		VitimageUtils.waitFor(600000);
		if(makeNicePictures) {
			//Render the graphs
			if(makeAllPictures) {
				ImagePlus imgG1=drawGraph(imgDatesTmp, graph, ray, thickness,sizeFactor);
				ImagePlus imgG2=drawGraph(imgDatesTmp, graph2, ray, thickness,sizeFactor);		
				ImagePlus imgG3=drawGraph(imgDatesTmp, graph3, ray, thickness,sizeFactor);		
				ImagePlus imgG4=drawGraph(imgDatesTmp, graph4, ray, thickness,sizeFactor);		
				ImagePlus imgG5=drawGraph(imgDatesTmp, graph5, ray, thickness,sizeFactor);		
				ImagePlus imgG6=drawGraph(imgDatesTmp, graph6, ray, thickness,sizeFactor);		
				IJ.saveAsTiff(imgG6, mainDataDir+"/3_Graphs_2/ML"+ml+"_Boite_"+boite+".tif");
				writeGraphToFile(graph6,  mainDataDir+"/3_Graphs_Ser/ML"+ml+"_Boite_"+boite+".tif");
				ImagePlus graphs=VitimageUtils.slicesToStack(new ImagePlus[] {imgG1,imgG2,imgG3,imgG4,imgG5,imgG6});
				graphs=VitimageUtils.convertFloatToByteWithoutDynamicChanges(graphs);
				ImagePlus []datesTab=new ImagePlus[graphs.getStackSize()];for(int i=0;i<datesTab.length;i++)datesTab[i]=imgDatesHigh;
				ImagePlus dates=VitimageUtils.convertFloatToByteWithoutDynamicChanges(VitimageUtils.slicesToStack(datesTab));
			
				
				//Compute the combined rendering
				ImagePlus glob=VitimageUtils.hyperStackingChannels(new ImagePlus[] {dates,graphs});
				glob.setDisplayRange(0, nDays);
				IJ.run(graphs,"Fire","");
				glob.show();
				IJ.saveAsTiff(glob, mainDataDir+"/3_Graphs_Raw/ML"+ml+"_Boite_"+boite+".tif");
			}
			else {
				ImagePlus imgG6=drawGraph(imgDatesTmp, graph6, ray, thickness,sizeFactor);		
				IJ.saveAsTiff(imgG6, mainDataDir+"/3_Graphs_2/ML"+ml+"_Boite_"+boite+".tif");
				writeGraphToFile(graph6,  mainDataDir+"/3_Graphs_Ser/ML"+ml+"_Boite_"+boite+".tif");
				ImagePlus graphs=VitimageUtils.slicesToStack(new ImagePlus[] {imgG6});
				graphs=VitimageUtils.convertFloatToByteWithoutDynamicChanges(graphs);
				ImagePlus []datesTab=new ImagePlus[graphs.getStackSize()];for(int i=0;i<datesTab.length;i++)datesTab[i]=imgDatesHigh;
				ImagePlus dates=VitimageUtils.convertFloatToByteWithoutDynamicChanges(VitimageUtils.slicesToStack(datesTab));
			
				
				//Compute the combined rendering
				ImagePlus glob=VitimageUtils.hyperStackingChannels(new ImagePlus[] {dates,graphs});
				glob.setDisplayRange(0, nDays);
				IJ.run(graphs,"Fire","");
				glob.show();
				IJ.saveAsTiff(glob, mainDataDir+"/3_Graphs_Raw/ML"+ml+"_Boite_"+boite+".tif");
				
			}
		}
		return graph6;
//		return glob;
	}
	*/

	/*
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
	}*/

	
}
