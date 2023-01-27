package io.github.rocsg.rootsystemtracker.test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.runners.MethodSorters;
import java.io.File;

import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.junit.FixMethodOrder;
import org.junit.jupiter.api.Test;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import io.github.rocsg.fijiyama.rsml.Root;
import io.github.rocsg.fijiyama.rsml.RootModel;
import io.github.rocsg.rootsystemtracker.PipelineActionsHandler;
import io.github.rocsg.rootsystemtracker.PipelineParamHandler;
import io.github.rocsg.topologicaltracking.CC;
import io.github.rocsg.topologicaltracking.ConnectionEdge;
import io.github.rocsg.topologicaltracking.RegionAdjacencyGraphPipeline;
import io.github.rocsg.fijiyama.common.VitimageUtils;

			
@FixMethodOrder( MethodSorters.NAME_ASCENDING ) // force name ordering
public class Test_RootSystemTracker {
	

	public Test_RootSystemTracker() {		
	}
	
	
	
	public static void main(String[]args) throws Exception {
		ImageJ ij=new ImageJ();
		Test_RootSystemTracker te=new Test_RootSystemTracker();		
		te.test_00_loadTestData();
		te.test_01_inventory();
		te.test_02_b_registerStack();
		te.test_03_computeMasks2();
		te.test_04_computeGraph();
		te.test_05_computeRsml();
		te.test_06_computeMovie();
	}
	

	/**
	 *  Test function for basic loading of data from the resource dir
	 * @throws Exception
	 */
	@Test
	public void test_00_loadTestData()throws Exception{
		System.out.println("\n\n\nRoot System Tracker\n -- RUNNING TEST 00 Test loading data from resource dir");
		try {
			String s=new File(getClass().getClassLoader().getResource("data/Source_ML1_0002/").getFile()).getAbsolutePath();
			ImagePlus img=IJ.openImage(s+"/40_date_map.tif");	
			String res=VitimageUtils.imageResume(img);
			int lenPos=res.length()>0 ? 1 : 0;
			assertEquals(lenPos, 1);
		}catch(Exception e) {System.out.println("Test failed at data loading"); 		assertEquals(0, 1);}
		System.out.println("Test 00 Ok");
	}
	
	/**
	 *  Test function for capabilities to build a proper inventory from a dir of data, including QR code reading
	 * @throws Exception
	 */
	@Test
	public void test_01_inventory()throws Exception{
		System.out.println("\n\n\n\n\n\nRoot System Tracker\n -- RUNNING TEST 01 make inventory of data");
		System.out.println("Test 01 Ok - NOTHING");
		assertEquals(0,0);
	}

	/**
	 *  Test function for capabilities to run the first masking step, before registration
	 * @throws Exception
	 */
	@Test
	public void test_02_a_computeMasks1()throws Exception{
		System.out.println("\n\n\n\n\n\nRoot System Tracker\n -- RUNNING TEST 02 a computemasks1 ");
		System.out.println("Test 02a Ok - NOTHING");
		assertEquals(0,0);
	
	}
	
	/**
	 *  Test function for registering stacks of time-lapse observations
	 * @throws Exception
	 */
	@Test
	public void test_02_b_registerStack()throws Exception{
		System.out.println("\n\n\n\n\n\nRoot System Tracker\n -- RUNNING TEST 02 b register stack");
		System.out.println("Test 02b Ok - NOTHING");	
		assertEquals(0,0);
	}

	
	/**
	 *  Test function for capabilities to run the masking step after registration
	 * @throws Exception
	 */
	@Test
	public void test_03_computeMasks2()throws Exception{
		System.out.println("\n\n\n\n\n\nRoot System Tracker\n -- RUNNING TEST 03 computemasks2 ");
		System.out.println("Test 03 Ok - NOTHING");
		assertEquals(0,0);
	
	}

	
	/**
	 *  Test function for testing capabilities of building the initial graph from the dates map
	 * @throws Exception
	 */
	
	public void test_04_computeGraph()throws Exception{
		
		System.out.println("\n\n\n \n\n\nRoot System Tracker\n -- RUNNING TEST 04 compute graph");
		String outputDataDir=new File(getClass().getClassLoader().getResource("data/Source_ML1_0002/").getFile()).getAbsolutePath();
		PipelineParamHandler pph=createPipeline0();
		int indexImg=0;

		//Create the graph and save it
		PipelineActionsHandler.buildAndProcessGraph(indexImg,outputDataDir,pph);
		SimpleDirectedWeightedGraph<CC,ConnectionEdge>  gg=RegionAdjacencyGraphPipeline.readGraphFromFile(new File(outputDataDir,"50_graph.ser").getAbsolutePath());
		System.out.println("There3");
		IJ.openImage(new File(outputDataDir,"51_graph_rendering.tif").getAbsolutePath());
		//Verify graph parameters
		int countVertex=gg.vertexSet().size();
		int countEdgeActivated=0;
		for(ConnectionEdge e : gg.edgeSet())if(e.activated)countEdgeActivated++;
		int countTotEdge=gg.edgeSet().size();
		System.out.println("Vertices count : "+countVertex+" expected : 1347");
		System.out.println("Edges count : "+countTotEdge+" expected : 1786");
		System.out.println("Edges activated count : "+countEdgeActivated+" expected : 1158");
		assertEquals(countVertex,1347);
		assertEquals(countTotEdge,1786);
		assertEquals(countEdgeActivated,1158);
		
		System.out.println("Test 04 Ok\n\n");
	}

	
	/**
	 *  Test function for testing capabilities of cleaning the graph and interpolate the morphodynamics of the root system architecture as a RSML
	 * @throws Exception
	 */
	
	public void test_05_computeRsml()throws Exception{
		System.out.println("\n\n\n \n\n\nRoot System Tracker\n -- RUNNING TEST 05 compute Rsml");
		String outputDataDir=new File(getClass().getClassLoader().getResource("data/Source_ML1_0002/").getFile()).getAbsolutePath();
		PipelineParamHandler pph=createPipeline0();
		pph.imgSerieSize=new int[] {22};
		int indexImg=0;
		PipelineActionsHandler.computeRSML(indexImg,outputDataDir,pph);
		RootModel rm=RootModel.RootModelWildReadFromRsml(new File(outputDataDir,"61_graph.rsml").getAbsolutePath());//TODO

		//Verify RSML parameters
		rm.setPlantNumbers();
		int[]nbLats=rm.nbLatsPerPlant();
		int[]expectedNbLats=new int[] {16,25,17,24,17};
		for(int i=0;i<nbLats.length;i++) {
			System.out.println("#Lat "+i+" : "+nbLats[i]+" , expected : "+expectedNbLats[i]);
		}
		for(int i=0;i<nbLats.length;i++) {
			assertEquals(nbLats[i],expectedNbLats[i]);
		}

		double []expectedLens=new double[] {2038.25,4354.05,2465.39,2974.94,2344.42};
		double []measuredLens=new double[] {0,0,0,0,0};
		for(Root r: rm.rootList)measuredLens[r.plantNumber]+=r.computeRootLength();
		for(int i=0;i<nbLats.length;i++) {
			System.out.println("#Plant "+i+" : "+measuredLens[i]+" , expected : "+expectedLens[i]);
		}
		for(int i=0;i<nbLats.length;i++) {
			assertEquals(measuredLens[i]/expectedLens[i],1,0.01);
		}

		
		System.out.println("Test 05 Ok");
	}

	
	/**
	 *  Test function for movie building capabilities
	 * @throws Exception
	 */
	
	public void test_06_computeMovie()throws Exception{
		System.out.println("\n\n\n \n\n\nRoot System Tracker\n -- RUNNING TEST 06 compute Movie");
		System.out.println("Test 06 Ok");
	
	}

	
	
	public PipelineParamHandler createPipeline0() {
		PipelineParamHandler pph=new PipelineParamHandler();
		pph.addAllParametersToTab();
		pph.acqTimes=new double[][] { { 0,8,16,24,32,10,48,56,54,71,80,88,96,104,112,120,128,136,144,152,160,168 } };
		pph.memorySaving=0;
		return pph;
	}
	

	
	
	
}