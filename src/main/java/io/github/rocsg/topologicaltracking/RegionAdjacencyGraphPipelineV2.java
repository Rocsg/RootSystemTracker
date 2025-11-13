package io.github.rocsg.topologicaltracking;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.Duplicator;
import ij.process.ImageProcessor;
import io.github.rocsg.fijiyama.common.*;
import io.github.rocsg.fijiyama.registration.TransformUtils;
import io.github.rocsg.rsml.Root;
import io.github.rocsg.rsml.RootModel;
import io.github.rocsg.rstplugin.PipelineParamHandler;
import io.github.rocsg.rstplugin.PipelineActionsHandler;
import io.github.ro.stringNodes()csg.rstutils.MorphoUtils;
import io.github.rocsg.rstutils.SplineAndPolyLineUtils;

import org.apache.commons.io.FileUtils;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RegionAdjacencyGraphPipelineV2 {

    
    //Flags for telling if debug image are generated
//    public static final boolean DO_DISTANCE = true;
//    public static final boolean DO_TIME = false;

    public static final boolean GO_FAST_FOR_DEBUG=true;

    //Use algorithmic tricks (or formal proof)
    public static final boolean USE_CONJONCTURE_A_IF_SOURCE_ONLY_ONE_OUTPUT_AND_TARGET_ONLY_ONE_INPUT = true;
    public static final boolean USE_CONJONCTURE_B_IF_SOURCE_ONLY_ONE_OUTPUT_PER_DAYAND_TARGET_ONLY_ONE_INPUT_PER_DAY = true;
    public static final boolean USE_CONJONCTURE_C_LOSSY_ISOLATED_DEAD_ENDS_GET_OUT_FROM_GRAPH_AND_WILL_BE_COUNTED_LATER = false;//Does not work out for tips
    public static final boolean USE_CONJONCTURE_D_VERY_SMALL_CC_HAVE_TO_BE_IGNORED = true;
    public static final boolean USE_CONJONCTURE_E_EXCLUDE_CC_THAT_ARE_TOTALLY_INCLUDED_IN_ANOTHER_CC = true;
    public static final boolean USE_CONJONCTURE_F_EXCLUDE_CC_THAT_START_ORGANS_AND_THAT_ARE_VERY_SMALL_RELATIVE_TO_THEIR_UNIQUE_POSSIBLE_SUCCESSOR = true;
    public static final boolean USE_CONJONCTURE_G_DO_NOT_TRY_TO_FIND_HIDDEN_EDGES_EMERGING_FROM_ISOLATED_TIPS_WITH_ONLY_A_POSSIBLE_SOURCE_AND_A_POSSIBLE_TARGET= true;
    public static final boolean USE_CONJONCTURE_H_IF_STARTING_CC_THAT_HAVE_ONLY_NEIGHBOUR_VERY_LONG_TIME_AFTER_THEN_IT_IS_OUTLIER=true;
    public static int MIN_SIZE_CC = 2;
    public static final double ALPHA_FUZZY_DAYPERDAY_CONNECTION=3.0;
    public static final double ALPHA_FUZZY_LOSSY_DEADEND_CONNECTION=10.0;
    public static final double ALPHA_FUZZY_START_VERY_SMALL_CONNECTION=10.0;

    //Outliers detection parameters in pre and post processing
    public static final int minFinalDepthForAcceptingLatRoot = 300;

    //Cost definitions
//    static double PENALTY_COST = 0.5;
    private static double HUGE_COST=100;
    private static double STANDARD_COST=1;
    private static double MAX_COST_TIME=STANDARD_COST*1;//Dissensual : penalize laterals emerging from primary but penalize false connexions between adjacent roots
    private static double MAX_COST_CON=STANDARD_COST*10;//Dissensual : in fact we care more about if all this is coherent, not the hidden length
    private static double MAX_COST_SPACE=STANDARD_COST*3;//But... to the computing errors possible ?
    private static double MAX_COST_ORIENT=STANDARD_COST*2;//Important, while at start the orientation is not known, later it is
    private static double MAX_COST_SURF=STANDARD_COST*1;//Dissensual : penalizing laterals emerging from primaries but penalizing false connexions between adjacent roots
    
    private static double COST_START=(MAX_COST_TIME+MAX_COST_CON+MAX_COST_SPACE+MAX_COST_ORIENT+MAX_COST_SURF)*1.2;
    private static double COST_END=COST_START;
    private static double ALPHA_DELTA_TIME=1.0;
    private static double ALPHA_CON=1;
    private static double MAD_E_FACTEUR_CORRECTIF=1.4826;//To be consistent with std if normal distribution, see the MAD-e paper
    private static double ALPHA_MAD_COST_DIST=1.0;//1 means the cost is 0 at 0 MAD, 0.99 at 3 MAD, 0.9999 at 6 MAD. 0.5 means the same but by halfing the number of MAD
    
    private static int sizeFactor=Utils.sizeFactorForGraphRendering;
    private static int nCategoriesSpeedStats=7;
    private static double[]maxCatS0=new double[nCategoriesSpeedStats-1];
    private static double[][]maxCatS0OverS1=new double[nCategoriesSpeedStats][nCategoriesSpeedStats];
    private static double[][][]statsOfCategories=new double[nCategoriesSpeedStats][nCategoriesSpeedStats][3];
    private static ArrayList<double[]>statsAllFirstEdges=new ArrayList<>();

        
    // Structure pour stocker les arbres TreeCC : une liste de racines (une par plante)
    static List<TreeCC> treeRoots = new ArrayList<>();
    // Map pour retrouver rapidement le TreeCC correspondant à un CC
    static Map<CC, TreeCC> ccToTreeMap = new HashMap<>();
    // Points initiaux (racines primaires) - nécessaires pour reconstruire les TreeCC
    static ArrayList<CC> initialPoints = new ArrayList<>();

    // Structures pour conserver les éléments exclus (potentiellement récupérables)
    static ArrayList<CC> excludedCCFullTrees = new ArrayList<>(); // Arbres complets supprimés step 5
    static ArrayList<ConnectionEdge> excludedEdgesNonActivated = new ArrayList<>(); // Edges non activées step 5
    static HashMap<CC, String> exclusionReasons = new HashMap<>(); // Raisons d'exclusion pour debug/analyse
    static HashMap<ConnectionEdge, String> edgeExclusionReasons = new HashMap<>(); // Raisons d'exclusion des edges


/*bug
penser à regler la vitesse typique, ca doit etre foireux
est ce qu on pourrait pas estimer plein de choses au debut a partir des CC 
est ce que la vitesse typique et le rayon et la longueur ne peuvent pas etre liés ?*/
    /**
     * -------------------------------------------------------------------------------------------------------------------------------------------
     * -------------------------------------------------------------------------------------------------------------------------------------------
     * Entry points *************************************************************************************
     * -------------------------------------------------------------------------------------------------------------------------------------------
     * -------------------------------------------------------------------------------------------------------------------------------------------
     */

    private static int nDays;
    private static Timer t;

    public RegionAdjacencyGraphPipelineV2() {
    }   


    public static void main(String[] args) {
            
        new ImageJ();
        String inventoryDir="/home/rfernandez/Bureau/A_Test/RootSystemTracker/JeanTrap/test2/Jean_trap-test/Inventory_of_jean_trap_out";
        String processingDir="/home/rfernandez/Bureau/A_Test/RootSystemTracker/JeanTrap/test2/Jean_trap-test/Processing";
        PipelineParamHandler pph=new PipelineParamHandler(inventoryDir, processingDir);
        PipelineActionsHandler.doStepOnImg(4, 1, pph);
        PipelineActionsHandler.doStepOnImg(5, 1, pph);
        PipelineActionsHandler.doStepOnImg(7, 1, pph);
        System.out.println("Done");
    }






    /**
     * This method is responsible for building and processing a graph in a straight manner.
     * It takes an ImagePlus object, a directory path for output data, a PipelineParamHandler object, and an index box as inputs.
     * It returns a boolean value indicating the success of the operation.
     *
     * @param imgDatesTmp The ImagePlus object that contains the image data to be processed.
     * @param outputDataDir The directory path where the output data will be stored.
     * @param pph The PipelineParamHandler object that contains the parameters for the pipeline.
     * @param indexBox The index of the box in the pipeline.
     * @return boolean Returns true if the operation is successful, false otherwise.
     */
    public static boolean buildAndProcessGraphStraight(ImagePlus imgDates, String outputDataDir,
                                                       PipelineParamHandler pph, int indexBox) {
     // ???????????????????
        double ray = 5;
        int thickness = 5;
        sizeFactor = Utils.sizeFactorForGraphRendering;
        int connexity = 4;
     // ???????????????????

        //Do the initial graph by telling which are the connected components directly connected in space and time
        //Compute plongement of connections between components (identify center of connection)
        //And set the corresponding costs
        SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph = null;
        imgDates=VitimageUtils.convertFloatToShortWithoutDynamicChanges(imgDates);        

        t=new Timer();                                                
        System.out.println("\nGraph building and processing step 1 : direct connections");                  

        //First steps of graph building : CC and edges, then validate formally, then set costs, then best incoming
        t.print("step 1 start : CC and edges");                              
        graph = buildStep1OriginalConnexionGraphFromDateMap(imgDates, connexity, pph.getHours(indexBox));
        t.print("Writing ser 1..");                              
        //writeGraphToFile(graph, new File(outputDataDir, "50_graph_v2_step_1.ser").getAbsolutePath());

        t.print("step 2 start : Validate formally the obvious edges and add hint vectors");                              
        buildStep2ValidateFormallyTheObviousEdgesAndAddHintVectors(graph, pph);

        t.print("step 3 start : Set costs with speed information");                              
        buildStep3SetCostsWithSpeedInformation(graph, pph);
        t.print("Writing ser 3..");                              
        //writeGraphToFile(graph, new File(outputDataDir, "50_graph_v2_step_3.ser").getAbsolutePath());
  
        t.print("step 4 start : Set best incoming except for the ones already activated that have been formally validated earlier");                              
        buildStep4SetBestIncomingExceptForTheOnesAlreadyActivatedThatHaveBeenFormallyValidatedEarlier(graph,pph);
        t.print("Writing ser 4..");                              
        //writeGraphToFile(graph, new File(outputDataDir, "50_graph_v2_step_4.ser").getAbsolutePath());

        //Second steps : pruning outliers, then identify crossings to switch and try opportunities
        System.out.println("\nGraph building and processing step 5 : prune outliers");
        t.print("step 5 start : prune outliers");
        buildStep5PruneOutliersFirstPhase(graph, pph);
        //writeGraphToFile(graph, new File(outputDataDir, "50_graph_v2_step_5.ser").getAbsolutePath());


        System.out.println("\nGraph building and processing step 6 : detect stunning");
        t.print("step 6 start : detect stunning");
        double[]stunNb=buildStep6DetectStunning(graph, pph);
        //writeGraphToFile(graph, new File(outputDataDir, "50_graph_v2_step_6.ser").getAbsolutePath());

        System.out.println("\nGraph building and processing step 7 : iterative stunning resolution");
        t.print("step 7 start : iterative stunning resolution");
        buildStep7IterativeStunningResolution(graph, pph);
        System.out.println("Original number of stunning was "+stunNb[0]+" CCs and "+stunNb[1]+" edges.");
        //writeGraphToFile(graph, new File(outputDataDir, "50_graph_v2_step_7.ser").getAbsolutePath());

        //Save the final graph and compute the display of the graph
        t.print("Writing ser final..");                              
        //writeGraphToFile(graph, new File(outputDataDir, "50_graph.ser").getAbsolutePath());
        System.out.println("Graph building and processing step 9 : debug images");
        t.print("step 9 start");
        //produceDebugImagesOfGraph(graph, imgDates, ray, thickness, sizeFactor, outputDataDir);

        

        //Build a root model of it
        RootModel rm = buildStep9RefinePlongement(graph, pph,indexBox);
        rm.cleanWildRsml();
        rm.resampleFlyingRoots();
        rm.cleanNegativeTh();
        rm.writeRSML3D(new File(outputDataDir, "60_graph_no_backtrack.rsml").getAbsolutePath(), "", true, false);
        try {  FileUtils.copyFile(new File(outputDataDir, "60_graph_no_backtrack.rsml"), new File(outputDataDir, "61_graph.rsml"));} catch (IOException e) { e.printStackTrace(); }

        // Create a display of the time lapse root model
        ImagePlus dates = IJ.openImage(new File(outputDataDir, "40_date_map.tif").getAbsolutePath());
        ImagePlus reg = IJ.openImage(new File(outputDataDir, "22_registered_stack.tif").getAbsolutePath());
        ImagePlus allTimes = RegionAdjacencyGraphPipelineV2.drawDistanceOrTime(dates, graph, false, false, 1);
        ImagePlus skeletonTime = RegionAdjacencyGraphPipelineV2.drawDistanceOrTime(dates, graph, false, true, 3);
        ImagePlus skeletonDay = RegionAdjacencyGraphPipelineV2.drawDistanceOrTime(dates, graph, false, true, 2);
        
        ImagePlus timeRSMLimg = PipelineActionsHandler.createTimeSequenceSuperposition(reg, rm);
        IJ.saveAsTiff(timeRSMLimg,            new File(outputDataDir, "62_rsml_2dt_rendered_over_image_sequence.tif").getAbsolutePath());
        
        // Set display range and save the images
        skeletonDay.setDisplayRange(0, pph.imgSerieSize[indexBox] + 1);
        skeletonTime.setDisplayRange(0, pph.imgSerieSize[indexBox] + 1);
        allTimes.setDisplayRange(0, pph.imgSerieSize[indexBox] + 1);
        IJ.saveAsTiff(skeletonTime, new File(outputDataDir, "63_time_skeleton.tif").getAbsolutePath());
        IJ.saveAsTiff(skeletonDay, new File(outputDataDir, "64_day_skeleton.tif").getAbsolutePath());
        IJ.saveAsTiff(allTimes, new File(outputDataDir, "65_times.tif").getAbsolutePath());
        return true;
    }    
 

    //Step6 : identifier les zones ou il semble y avoir des croisements, des opportunités de switch, des choses étonnantes
    //Formellement :
    //D'abord estimer le rayon de chaque CC et la vitesse surfacique horaire, et les stocker dans deux champs dediés de l'objet CC (estimatedRadius, estimatedSpeed).
    //La vitesse surfacique peut etre calculée avec la fonction estimatedSurfacicGrowthPerHour
    //Pour estimer le rayon, on calcule la longueur de la ligne centrale, et on divise la surface en pixels par cette longueur. 
        //on prend comme longueur le double de la distance entre le centre de la CC x() y() et le point de connexion entrante (edge.connectionX/Y). Si c'est un point intermédiaire, on prend la distance entre les deux points de connexion.
        //sauf si il n'a pas de predecesseur (noeud origine), meme chose mais entre le centre de la CCN x() y() et le point de connexion sortante de la fille du meme organe (du meme ordre).
   

    

    /**
     * Détection des situations stunning dans le graphe.
     * Identifie 4 types de stunning :
     * - RADIUS_OUTLIER : rayon anormal dans un organe
     * - SHARP_ANGLE : angle brusque sur un chemin principal
     * - WCOSE (Weird Child Organ Surface Emergence) : surface d'organe latéral anormale
     * - FINAL_TIME_OUTLIER : temps final d'organe anormal
     */
    public static double[] detectStunningSituations(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph,
                                                                               PipelineParamHandler pph,boolean print) {
        
        // Étape 0 : RESET - Effacer tous les stunning marks précédents pour éviter l'accumulation
        for (CC cc : graph.vertexSet()) {
            cc.stunningLevel = 0;
            cc.stunningReasons.clear();
            cc.stunningMADValues.clear();
            cc.stunningLevelSumNeighbours = 0;
        }
        for (ConnectionEdge edge : graph.edgeSet()) {
            edge.stunningLevel = 0;
            edge.stunningReasons.clear();
            edge.stunningMADValues.clear();
        }
        
        // Étape 1 : Estimer le rayon de chaque CC et la vitesse surfacique horaire
        for (CC cc : graph.vertexSet()) {
            // Calculer la vitesse surfacique horaire
            cc.estimatedSpeed = estimatedSurfacicGrowthPerHour(cc, pph);
            
            // Calculer le rayon estimé
            double centralLineLength = 0;
            ConnectionEdge incomingEdge = cc.incomingEdgeInOrgan();
            ConnectionEdge outgoingEdge = cc.followingEdgeInOrgan();

            if (incomingEdge != null && outgoingEdge != null) {
                // Nœud intermédiaire : distance entre point de connexion entrante et sortante
                centralLineLength = Utils.distance(incomingEdge.connectionX, incomingEdge.connectionY,
                                                   outgoingEdge.connectionX, outgoingEdge.connectionY);
            } else if (incomingEdge == null && outgoingEdge != null) {
                // Nœud origine : distance entre centre et point de connexion sortante vers fille du même ordre
                centralLineLength = 2 * Utils.distance(cc.xCentralPixAbsolu, cc.yCentralPixAbsolu,
                                                       outgoingEdge.connectionX, outgoingEdge.connectionY);
            } else if (incomingEdge != null && outgoingEdge == null) {
                // Feuille : distance entre centre et point de connexion entrante
                centralLineLength = 2 * Utils.distance(cc.xCentralPixAbsolu, cc.yCentralPixAbsolu,
                                                       incomingEdge.connectionX, incomingEdge.connectionY);
            } else {
                // Nœud isolé
                centralLineLength = Math.sqrt(cc.nPixels); // Approximation
            }
            
            if (centralLineLength > 0) {
                cc.estimatedRadius = cc.nPixels / centralLineLength;
            } else {
                cc.estimatedRadius = Math.sqrt(cc.nPixels / Math.PI); // Approximation circulaire
            }
        }
        
        // Étape 2 : Pour chaque organe, calculer les MADeStats des radius et identifier les outliers
        for (TreeCC rootTree : treeRoots) {
            // Traiter tous les organes de cet arbre (primaires et latérales)
            processOrganForRadiusOutliers(rootTree, graph);
        }
        
        // Étape 3 : Calculer les MADeStats des temps finaux de tous les organes
        ArrayList<Double> finalTimes = new ArrayList<>();
        ArrayList<TreeCC> organStarts = new ArrayList<>();
        
        for (TreeCC rootTree : treeRoots) {
            // Collecter les temps finaux de tous les organes
            collectOrganFinalTimes(rootTree, finalTimes, organStarts);
        }
        
        if (finalTimes.size() > 3) {
            double[] finalTimesArray = new double[finalTimes.size()];
            for (int i = 0; i < finalTimes.size(); i++) {
                finalTimesArray[i] = finalTimes.get(i);
            }
            
            double[] stats = MADeStatsDoubleSidedWithEpsilon(finalTimesArray);
            double median = stats[0];
            double madMinus = (stats[0] - stats[1]) * MAD_E_FACTEUR_CORRECTIF;
            double madPlus = (stats[2] - stats[0]) * MAD_E_FACTEUR_CORRECTIF;
            
            // Marquer les organes avec temps final étonnant
            for (int i = 0; i < finalTimes.size(); i++) {
                double finalTime = finalTimes.get(i);
                double nMAD = 0;
                if (finalTime < median) {
                    nMAD = Math.abs(finalTime - median) / madMinus;
                } else {
                    nMAD = Math.abs(finalTime - median) / madPlus;
                }
                
                if (nMAD > 3.0) {
                    // Marquer le CC final comme étonnant
                    TreeCC organStart = organStarts.get(i);
                    List<TreeCC> organPath = organStart.getOrganPath();
                    if (!organPath.isEmpty()) {
                        TreeCC lastNode = organPath.get(organPath.size() - 1);
                        CC lastCC = lastNode.getCc();
                        lastCC.stunningLevel++;
                        lastCC.stunningReasons.add("FINAL_TIME_OUTLIER");
                        lastCC.stunningMADValues.add(nMAD);
                    }
                }
            }
        }
        
        // Étape 4 : Détecter les angles soudains sur les chemins principaux
        for (TreeCC rootTree : treeRoots) {
            detectSharpAnglesInOrgan(rootTree, graph);
        }
        

        // Étape 5 : Détecter les surfaces anormales des organes filles (latérales)
        detectWeirdChildOrganSurfaces(graph, pph);
        // Afficher statistiques
        int stunningCountCC = 0;
        int stunningCountEdge = 0;
        for (CC cc : graph.vertexSet()) {
            if (cc.stunningLevel > 0) stunningCountCC++;
        }
        for (ConnectionEdge edge : graph.edgeSet()) {
            if (edge.stunningLevel > 0) stunningCountEdge++;
        }

        //computeStunningLevelSumNeighbours(graph);
        if(print)System.out.println("  Stunning detection complete: " + stunningCountCC + " CCs, " + stunningCountEdge + " edges marked as stunning");
        return new double[]{(double)stunningCountCC, (double)stunningCountEdge};
    }
    
    /**
     * Traiter un organe pour identifier les CC avec radius outliers
     */
    private static void processOrganForRadiusOutliers(TreeCC organRoot, SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph) {
        // Récupérer le chemin de l'organe principal
        List<TreeCC> organPath = organRoot.getOrganPath();
        
        if (organPath.size() < 3) return; // Pas assez de données
        
        // Collecter les radius de l'organe
        ArrayList<Double> radiusList = new ArrayList<>();
        for (TreeCC node : organPath) {
            radiusList.add(node.getCc().estimatedRadius);
        }
        
        double[] radiusArray = new double[radiusList.size()];
        for (int i = 0; i < radiusList.size(); i++) {
            radiusArray[i] = radiusList.get(i);
        }
        
        // Calculer les MADeStats
        double[] stats = MADeStatsDoubleSidedWithEpsilon(radiusArray);
        double median = stats[0];
        double madMinus = (stats[0] - stats[1]) * MAD_E_FACTEUR_CORRECTIF+VitimageUtils.EPSILON;
        double madPlus = (stats[2] - stats[0]) * MAD_E_FACTEUR_CORRECTIF+VitimageUtils.EPSILON;
        
        // Vérifier chaque CC pour outliers de radius
        for (int i = 0; i < organPath.size(); i++) {
            TreeCC node = organPath.get(i);
            CC cc = node.getCc();
            double radius = cc.estimatedRadius; 
            
            double nMAD = 0;
            if (radius < median) {
                nMAD = Math.abs(radius - median) / madMinus;
            } else {
                nMAD = Math.abs(radius - median) / madPlus;
            }
            
            // Si radius outlier > 3 MAD
            if (nMAD > (organPath.size() > 7 ? 6.0 : organPath.size()>5 ? 8 : 100)) {
                cc.stunningLevel++;
                cc.stunningReasons.add("RADIUS_OUTLIER");
                cc.stunningMADValues.add(nMAD);

                boolean debug=(cc==getCCWithResolution(graph, 2929, 5467));
                if(debug) {
                    System.out.println("Debugging CC: " + cc);
                    System.out.println("  Radius: " + radius + ", Median: " + median + ", MAD-: " + madMinus + ", MAD+: " + madPlus + ", nMAD: " + nMAD);
                    System.out.println("Car la série était "+Arrays.toString(radiusArray));
                    waitFor(1000000);
                }

                // Marquer les edges avant et après comme étonnants
                ConnectionEdge inEdge = cc.bestIncomingActivatedEdge();
                if (inEdge != null) {
                    inEdge.stunningLevel++;
                    inEdge.stunningReasons.add("RADIUS_OUTLIER_IN_TARGET");
                    inEdge.stunningMADValues.add(nMAD);
                }
                
                ConnectionEdge outEdge = cc.bestOutgoingActivatedEdge();
                if (outEdge != null) {
                    outEdge.stunningLevel++;
                    outEdge.stunningReasons.add("RADIUS_OUTLIER_IN_SOURCE");
                    outEdge.stunningMADValues.add(nMAD);
                }
                
                // System.out.println("  CC " + cc + " marked as stunning (radius outlier: " + nMAD + " MAD)");
            }
            
            // Vérifier rupture brusque de tendance (seulement si racine > 5 CC)
            if (organPath.size() > 5 && i >= 2 && i < organPath.size() - 1) {
                // CC a au moins deux prédécesseurs et un suiveur
                if (graph.incomingEdgesOf(cc).size() >= 2 && cc.bestOutgoingActivatedEdge() != null) {
                    
                    // Calculer radius moyen avant (i-2, i-1)
                    double []vals=new double[]{organPath.get(i - 2).getCc().estimatedRadius, organPath.get(i - 1).getCc().estimatedRadius};
                    double[]stat=VitimageUtils.statistics1D(vals);
                    double radiusBefore = stat[0];
                    double stddevBefore = stat[1];


                    if(i + 2 < organPath.size()){
                        vals=new double[]{organPath.get(i).getCc().estimatedRadius, organPath.get(i + 1).getCc().estimatedRadius,
                                     organPath.get(i + 2).getCc().estimatedRadius};
                    }
                    else{
                        vals=new double[]{organPath.get(i).getCc().estimatedRadius, organPath.get(i + 1).getCc().estimatedRadius};
                    }
                    stat=VitimageUtils.statistics1D(vals);
                    double radiusAfter = stat[0];
                    double stddevAfter = stat[1];

                    // Calculer écarts
                    double diffBeforeAfter = Math.abs(radiusAfter - radiusBefore);
                    
                    // Approximation de sigma (MAD/0.6745)
                    double threshold = (stddevAfter + stddevBefore)/0.6745;
                    
                    if (diffBeforeAfter > threshold) {
                        cc.stunningLevel++;
                        cc.stunningReasons.add("RADIUS_SUDDEN_CHANGE");
                        cc.stunningMADValues.add(diffBeforeAfter / threshold); // Ratio au seuil
                        
                        ConnectionEdge inEdge = cc.bestIncomingActivatedEdge();
                        if (inEdge != null) {
                            inEdge.stunningLevel++;
                            inEdge.stunningReasons.add("RADIUS_SUDDEN_CHANGE_AT_TARGET");
                            inEdge.stunningMADValues.add(diffBeforeAfter / threshold);
                        }
                        
                        ConnectionEdge outEdge = cc.bestOutgoingActivatedEdge();
                        if (outEdge != null) {
                            outEdge.stunningLevel++;
                            outEdge.stunningReasons.add("RADIUS_SUDDEN_CHANGE_AT_SOURCE");
                            outEdge.stunningMADValues.add(diffBeforeAfter / threshold);
                        }
                    }
                }
            }
        }
        
        // Traiter récursivement les organes latéraux
        for (List<TreeCC> lateralOrgan : organRoot.getAllLateralOrgansEmergingDirectlyFromThisOrganPath()) {
            if (!lateralOrgan.isEmpty()) {
                processOrganForRadiusOutliers(lateralOrgan.get(0), graph);
            }
        }
    }
    
    /**
     * Collecter les temps finaux de tous les organes
     */
    private static void collectOrganFinalTimes(TreeCC organRoot, ArrayList<Double> finalTimes, ArrayList<TreeCC> organStarts) {
        List<TreeCC> organPath = organRoot.getOrganPath();
        
        if (!organPath.isEmpty()) {
            TreeCC lastNode = organPath.get(organPath.size() - 1);
            finalTimes.add(lastNode.getCc().hourGuessedOfTip);
            organStarts.add(organRoot);
        }
        
        // Récursivement pour les latérales
        for (List<TreeCC> lateralOrgan : organRoot.getAllLateralOrgansEmergingDirectlyFromThisOrganPath()) {
            if (!lateralOrgan.isEmpty()) {
                collectOrganFinalTimes(lateralOrgan.get(0), finalTimes, organStarts);
            }
        }
    }
    
    /**
     * Détecter les surfaces anormales des organes filles (latérales)
     * Compare la surface des organes latéraux avec un modèle basé sur la surface du parent
     */
    private static void detectWeirdChildOrganSurfaces(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, PipelineParamHandler pph) {
        
        // Collecter tous les couples (log(surfacePerHourParent), log(surfacePerHourFille))
        ArrayList<double[]> parentChildSurfacePairs = new ArrayList<>();
        ArrayList<ConnectionEdge> correspondingEdges = new ArrayList<>();
        
        for (CC cc : graph.vertexSet()) {
            if(cc.isOut) continue; // NE PAS COMPTER LES CC EXCLUES
            
            // Identifier la fille officielle (following in organ)
            ConnectionEdge officialFollowingEdge = cc.followingEdgeInOrgan();
            
            // Pour chaque edge sortant qui n'est pas la fille officielle
            for (ConnectionEdge edge : graph.outgoingEdgesOf(cc)) {
                if(edge.isOut) continue; // NE PAS COMPTER LES EDGES EXCLUES
                if (edge.activated && edge != officialFollowingEdge) {
                    // C'est un organe latéral
                    CC parent = edge.source;
                    CC child = edge.target;
                    
                    if(child.isOut) continue; // NE PAS COMPTER LES ENFANTS EXCLUS
                    
                    // Calculer surface per hour du parent
                    double parentDeltaHour = parent.hourGuessedOfTip - parent.hourGuessedOfStart+(parent.hourGuessedOfTip==parent.hourGuessedOfStart?pph.typicalHourDelay:VitimageUtils.EPSILON);
                    double parentSurfacePerHour = parent.nPixels / parentDeltaHour;
                    
                    // Calculer surface per hour de la fille
                    double childDeltaHour = child.hourGuessedOfTip - child.hourGuessedOfStart+(child.hourGuessedOfTip==child.hourGuessedOfStart?pph.typicalHourDelay:VitimageUtils.EPSILON);
                    double childSurfacePerHour = child.nPixels / childDeltaHour;
                    
                    // Ajouter le couple (log parent, log child)
                    parentChildSurfacePairs.add(new double[]{
                        Math.log(parentSurfacePerHour + VitimageUtils.EPSILON),
                        Math.log(childSurfacePerHour + VitimageUtils.EPSILON)
                    });
                    correspondingEdges.add(edge);
                }
            }
        }
        
        if (parentChildSurfacePairs.size() < 10) {
            System.out.println("  Not enough lateral organs to perform analysis (" + parentChildSurfacePairs.size() + " found)");
            return;
        }
        
        
        // Trier les couples en fonction de la surface parente (premier élément)
        ArrayList<Integer> sortedIndices = new ArrayList<>();
        for (int i = 0; i < parentChildSurfacePairs.size(); i++) {
            sortedIndices.add(i);
        }
        sortedIndices.sort((i1, i2) -> Double.compare(parentChildSurfacePairs.get(i1)[0], parentChildSurfacePairs.get(i2)[0]));
        
        // Tester chaque edge
        for (int idx = 0; idx < parentChildSurfacePairs.size(); idx++) {
            ConnectionEdge edgeToTest = correspondingEdges.get(idx);
            boolean debug=false&&(edgeToTest.target==getCCWithResolution(graph, 4285, 3338));
            if(debug) {
                System.out.println("Debugging edge: " + edgeToTest);
            }
            double[] pairToTest = parentChildSurfacePairs.get(idx);
            double logParentSurfaceToTest = pairToTest[0];
            double logChildSurfaceToTest = pairToTest[1];
            
            // Trouver l'index de ce couple dans le tableau trié
            int sortedIdx = sortedIndices.indexOf(idx);

            // Sélectionner les 25 voisins les plus proches (12 avant, 12 après)
            int windowSize = 25;
            int halfWindow = 12;
            int startIdx = Math.max(0, sortedIdx - halfWindow);
            int endIdx = Math.min(sortedIndices.size() - 1, sortedIdx + halfWindow);

            // Ajuster pour avoir 25 éléments si possible
            while (endIdx - startIdx + 1 < windowSize && (startIdx > 0 || endIdx < sortedIndices.size() - 1)) {
                if (startIdx > 0) startIdx--;
                if (endIdx - startIdx + 1 < windowSize && endIdx < sortedIndices.size() - 1) endIdx++;
            }
            
            // Collecter les valeurs de surface fille pour les voisins
            ArrayList<Double> neighborChildSurfaces = new ArrayList<>();
            ArrayList<Double> neighborParentSurfaces = new ArrayList<>();
            ArrayList<ConnectionEdge> neighborEdges = new ArrayList<>();
            for (int i = startIdx; i <= endIdx; i++) {
                int originalIdx = sortedIndices.get(i);
                if (originalIdx != idx) { // Exclure l'élément testé lui-même
                    neighborChildSurfaces.add(parentChildSurfacePairs.get(originalIdx)[1]);
                    neighborParentSurfaces.add(parentChildSurfacePairs.get(originalIdx)[0]);
                    neighborEdges.add(correspondingEdges.get(originalIdx));
                }
            }
            
            if (neighborChildSurfaces.size() < 5) continue; // Pas assez de données
            
            // Calculer les MADeStats sur les surfaces filles des voisins
            double[] neighborArray = new double[neighborChildSurfaces.size()];
            for (int i = 0; i < neighborChildSurfaces.size(); i++) {
                neighborArray[i] = neighborChildSurfaces.get(i);
            }
            
            double[] stats = MADeStatsDoubleSidedWithEpsilon(neighborArray);
            double median = stats[0];
            double madMinus = (stats[0] - stats[1]) * MAD_E_FACTEUR_CORRECTIF + VitimageUtils.EPSILON;
            double madPlus = (stats[2] - stats[0]) * MAD_E_FACTEUR_CORRECTIF + VitimageUtils.EPSILON;

            

            // Calculer le nombre de MAD pour la surface fille testée
            double nMAD = 0;
            if (logChildSurfaceToTest < median) {
                nMAD = Math.abs(logChildSurfaceToTest - median) / madMinus;
            } else {
                nMAD = Math.abs(logChildSurfaceToTest - median) / madPlus;
            }
            
            /* DEBUG WCOSE
            if(debug) {
                System.out.println("  Edge " + edgeToTest + ": logParentSurface=" + logParentSurfaceToTest +
                                   ", logChildSurface=" + logChildSurfaceToTest +
                                   ", median=" + median + ", mad-=" + madMinus + ", mad+=" + madPlus +
                                   ", nMAD=" + nMAD);
                                   System.out.println("Car les voisins filles étaient : "+Arrays.toString(neighborArray));
                                   System.out.println("Car les surfaces parentes étaient : "+Arrays.toString(neighborParentSurfaces.toArray()));
                                   System.out.println("Car les arêtes étaient : "+Arrays.toString(neighborEdges.toArray()));
                                   System.out.println("Et, affiché de maniere lisible, en sautant une ligne entre chaque arete, et en indiquant d'abord la surface parente, puis fille puis l'arete :");
                                   for (int i = 0; i < neighborEdges.size(); i++) {
                                       System.out.println("  \nParent Surface: " + neighborParentSurfaces.get(i) +
                                                          ", Child Surface: " + neighborArray[i] +
                                                          ", Edge: " + neighborEdges.get(i));
                                   }
                waitFor(1000000);
            }
            */

            // Si dépasse 5 MAD, marquer comme stunning
            if (nMAD > 2) {
                edgeToTest.stunningLevel++;
                edgeToTest.stunningReasons.add("WEIRD_CHILD_ORGAN_SURFACE");
                edgeToTest.stunningMADValues.add(nMAD);
                
                // System.out.println("  Edge " + edgeToTest.source + " -> " + edgeToTest.target + 
                //                  " marked as stunning (weird child surface: " + nMAD + " MAD)");
            }
        }

    }
    
    /**
     * Détecter les angles soudains dans un organe et ses latérales
     * Un angle est considéré comme suspect si > 45° (produit scalaire normalisé < sqrt(2)/2)
     */
    private static void detectSharpAnglesInOrgan(TreeCC organRoot, SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph) {
        List<TreeCC> organPath = organRoot.getOrganPath();
        final double THRESHOLD_COS_ANGLE = 1 / 2.0; // cos(30°)
        
        // Parcourir chaque CC du chemin principal
        for (int i = 0; i < organPath.size(); i++) {
            TreeCC node = organPath.get(i);
            CC cc = node.getCc();
            boolean debug=false &&(cc==getCCWithResolution(graph, 5030,1621));
            
            ConnectionEdge inEdge = cc.incomingEdgeInOrgan();
            ConnectionEdge outEdge = cc.followingEdgeInOrgan();
            
            if(debug) {
                System.out.println("Debugging CC: " + cc);
                System.out.println("  inEdge: " + inEdge);
                System.out.println("  outEdge: " + outEdge);
            }
            // Cas 1 : CC intermédiaire avec incoming et outgoing edges
            if (inEdge != null && outEdge != null) {
                // Vecteur 1 : connectionIn -> centre CC
                double v1x = cc.x() - inEdge.connectionX;
                double v1y = cc.y() - inEdge.connectionY;
                double norm1 = Math.sqrt(v1x * v1x + v1y * v1y);
                
                // Vecteur 2 : centre CC -> connectionOut
                double v2x = outEdge.connectionX - cc.x();
                double v2y = outEdge.connectionY - cc.y();
                double norm2 = Math.sqrt(v2x * v2x + v2y * v2y);
                
                if (norm1 > 0 && norm2 > 0) {
                    // Produit scalaire normalisé
                    double cosAngle = (v1x * v2x + v1y * v2y) / (norm1 * norm2);
                    
                    if (cosAngle < (i==0 ? -THRESHOLD_COS_ANGLE : THRESHOLD_COS_ANGLE)) { //Cas particulier pour le démarrage qui est autorisé a faire des grands angles
                        double angleDegrees = Math.toDegrees(Math.acos(cosAngle));
                        
                        inEdge.stunningLevel++;
                        inEdge.stunningReasons.add("SHARP_ANGLE_AT_TARGET");
                        inEdge.stunningMADValues.add(angleDegrees);
                        
                        outEdge.stunningLevel++;
                        outEdge.stunningReasons.add("SHARP_ANGLE_AT_SOURCE");
                        outEdge.stunningMADValues.add(angleDegrees);
                        /* DEBUG ANGLES
                        if(debug) {
                            System.out.println("  CC " + cc + " marked as stunning (sharp intermediate angle: " + 
                                             angleDegrees + "°)");
                            waitForLong();
                        }
                        */
                    }
                }
            }
            // Cas 2 : CC terminale (leaf) avec incoming edge seulement
            else if (inEdge != null && outEdge == null && i > 0) {
                // Vecteur 1 : CCprec -> centre CC
                TreeCC prevNode = organPath.get(i - 1);
                CC ccPrev = prevNode.getCc();
                double v1x = cc.x() - ccPrev.x();
                double v1y = cc.y() - ccPrev.y();
                double norm1 = Math.sqrt(v1x * v1x + v1y * v1y);
                
                // Vecteur 2 : connectionIn -> centre CC
                double v2x = cc.x() - inEdge.connectionX;
                double v2y = cc.y() - inEdge.connectionY;
                double norm2 = Math.sqrt(v2x * v2x + v2y * v2y);
                
                if (norm1 > 0 && norm2 > 0) {
                    // Produit scalaire normalisé
                    double cosAngle = (v1x * v2x + v1y * v2y) / (norm1 * norm2);
                    
                    if (cosAngle < THRESHOLD_COS_ANGLE) {
                        double angleDegrees = Math.toDegrees(Math.acos(cosAngle));
                        cc.stunningLevel++;
                        cc.stunningReasons.add("SHARP_ANGLE_TERMINAL");
                        cc.stunningMADValues.add(angleDegrees);
                        
                        inEdge.stunningLevel++;
                        inEdge.stunningReasons.add("SHARP_ANGLE_AT_TERMINAL_TARGET");
                        inEdge.stunningMADValues.add(angleDegrees);
                        
                        // System.out.println("  CC " + cc + " marked as stunning (sharp terminal angle: " + 
                        //                  angleDegrees + "°)");
                    }
                }
            }
            
            
            // Vérifier l'angle au niveau de l'edge (si edge est dans un chemin d'organe unique)
            if (inEdge != null && i > 0) {
                TreeCC prevNode = organPath.get(i - 1);
                CC ccSource = prevNode.getCc();
                
                // Vérifier que l'edge relie deux CC du même organe
                if (ccSource.order == cc.order) {
                    // Vecteur 1 : centre source -> connectionIn
                    double v1x = inEdge.connectionX - ccSource.x();
                    double v1y = inEdge.connectionY - ccSource.y();
                    double norm1 = Math.sqrt(v1x * v1x + v1y * v1y);
                    
                    // Vecteur 2 : connectionIn -> centre target
                    double v2x = cc.x() - inEdge.connectionX;
                    double v2y = cc.y() - inEdge.connectionY;
                    double norm2 = Math.sqrt(v2x * v2x + v2y * v2y);
                    
                    if (norm1 > 0 && norm2 > 0) {
                        // Produit scalaire normalisé
                        double cosAngle = (v1x * v2x + v1y * v2y) / (norm1 * norm2);
                        
                        if (cosAngle < THRESHOLD_COS_ANGLE) {
                            double angleDegrees = Math.toDegrees(Math.acos(cosAngle));
                            inEdge.stunningLevel++;
                            inEdge.stunningReasons.add("SHARP_EDGE_ANGLE");
                            inEdge.stunningMADValues.add(angleDegrees);
                            
                            // System.out.println("  Edge " + ccSource + " -> " + cc + 
                            //                  " marked as stunning (sharp edge angle: " + 
                            //                  angleDegrees + "°)");
                        }
                    }
                }
            }
        }
        
        // Traiter récursivement les organes latéraux
        for (List<TreeCC> lateralOrgan : organRoot.getAllLateralOrgansEmergingDirectlyFromThisOrganPath()) {
            if (!lateralOrgan.isEmpty()) {
                detectSharpAnglesInOrgan(lateralOrgan.get(0), graph);
            }
        }
    }
    

    /**
     * Step 6 : Détection initiale des situations de stunning.
     * 
     * Détecte toutes les situations de stunning (radius outliers, angles, WCOSE, etc.)
     * sans appliquer de corrections. Utilisé pour l'analyse et la sauvegarde de l'état initial.
     */
    public static double[] buildStep6DetectStunning(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, 
                                                 PipelineParamHandler pph) {
        return detectStunningSituations(graph, pph,true);
    }


    /**
     * Step 7 : Résolution itérative des situations de stunning.
     * 
     * Boucle itérative qui :
     * A) Détecte les stunning situations
     * B) Propose et évalue des micro-modifications (avec simulation complète)
     * C) Applique la meilleure modification
     * D) Recommence jusqu'à ce qu'aucune amélioration ne soit possible
     * 
     * Les modifications sont évaluées par simulation complète (TreeCC + stunning recalculation).
     */
    public static void buildStep7IterativeStunningResolution(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, 
                                                              PipelineParamHandler pph) {
        System.out.println("Step 7: Iterative stunning resolution");
        
        final int MAX_ITERATIONS = 10;
        int iteration = 0;
        boolean improvementFound = true;
        
        while (improvementFound && iteration < MAX_ITERATIONS) {
            iteration++;
            System.out.println("\n========================================");
            t.print("ITERATION " + iteration + " / " + MAX_ITERATIONS);
            System.out.println("========================================\n");
            
            
            // A) Détecter les stunning
            detectStunningSituations(graph, pph,true);
            
            // Phase 1 : Inventorier toutes les situations de stunning avec leur contexte
            ArrayList<StunningContext> stunningContexts = inventoryStunningSituations(graph, pph);
            
            System.out.println("  Found " + stunningContexts.size() + " stunning contexts to analyze");
            
            if (stunningContexts.isEmpty()) {
                System.out.println("  No more stunning situations. Resolution complete!");
                improvementFound = false;
                break;
            }
            
            // Phase 2 : Pour chaque situation, proposer des micro-modifications possibles
            ArrayList<ProposedModification> proposedModifications = new ArrayList<>();
            
            for (StunningContext context : stunningContexts) {
                ArrayList<ProposedModification> mods = proposeModificationsForContext(context, graph, pph);
                proposedModifications.addAll(mods);
            }
            
            System.out.println("  Proposed " + proposedModifications.size() + " potential modifications");
            
            if (proposedModifications.isEmpty()) {
                System.out.println("  No modifications to propose. Resolution complete!");
                improvementFound = false;
                break;
            }
            
            // Phase 3 : Évaluer chaque modification avec SIMULATION COMPLÈTE
            System.out.println("  Evaluating " + proposedModifications.size() + " modifications (with full simulation)...");
            
            for (int i = 0; i < proposedModifications.size(); i++) {
                ProposedModification mod = proposedModifications.get(i);
                // System.out.println("    Simulating modification " + (i+1) + "/" + proposedModifications.size() + "...");
                
                // Simulation complète avec reconstruction TreeCC
                double realImprovement = simulateAndEvaluateModification(mod, graph, pph);
                mod.stunningImprovement = realImprovement;
            }
            
            
            // Phase 4 : Filtrer les modifications avec amélioration > 0
            ArrayList<ProposedModification> beneficialMods = new ArrayList<>();
            for (ProposedModification mod : proposedModifications) {
                if (mod.stunningImprovement > 0) {
                    beneficialMods.add(mod);
                }
            }
            
            System.out.println("  Found " + beneficialMods.size() + " modifications with positive improvement");
            
            if (beneficialMods.isEmpty()) {
                System.out.println("  No beneficial modifications found. Resolution complete!");
                improvementFound = false;
                break;
            }
            
            // Phase 5 : Prioriser les modifications
            // Critère 1 : Privilégier celles sans WCOSE downstream
            // Critère 2 : Puis par taille (downstreamPixels)
            // Critère 3 : Puis par amélioration de stunning
            
            beneficialMods.sort((m1, m2) -> {
                // TODO: Ajouter le comptage des WCOSE downstream
                // Pour l'instant, on trie juste par stunning improvement puis taille
                int cmp = Double.compare(m2.stunningImprovement, m1.stunningImprovement);
                if (cmp != 0) return cmp;
                return Integer.compare(m2.downstreamPixels, m1.downstreamPixels);
            });
            
            // Phase 6 : Afficher les top modifications
            System.out.println("\n  === TOP BENEFICIAL MODIFICATIONS ===");
            int displayCount = Math.min(20, beneficialMods.size());
            for (int i = 0; i < displayCount; i++) {
                ProposedModification mod = beneficialMods.get(i);
                System.out.println("  #" + (i+1) + ": Type=" + mod.getString() + 
                                 ", Improvement=" + dou(mod.stunningImprovement) + 
                                 ", Downstream=" + mod.downstreamImpact + " CCs (" + mod.downstreamPixels + " px)");
            }
            
            // Phase 7 : Appliquer la meilleure modification
            ProposedModification bestMod = beneficialMods.get(0);
            System.out.println("\n  >>> APPLYING BEST MODIFICATION <<<");
            System.out.println("  Type: " + bestMod.modificationType);
            System.out.println("  Edge to disconnect: " + bestMod.edgeToDisconnect);
            System.out.println("  New predecessor: " + bestMod.newPredecessor);
            System.out.println("  Expected improvement: " + dou(bestMod.stunningImprovement) + " points");
            
            ConnectionEdge edge=applyModification(bestMod, graph);
            edge.activated=true;
            
            // Reconstruire les TreeCC après la modification réelle
            rebuildTreeCCStructures(graph, initialPoints);
            
            // Vérifier la cohérence des structures TreeCC
            int inconsistencies = validateTreeCCConsistency(graph);
            if (inconsistencies > 0) {
                System.out.println("  WARNING: Found " + inconsistencies + " inconsistencies after TreeCC rebuild!");
            }
            
            detectStunningSituations(graph, pph,true);
            
        }
        
        System.out.println("\n========================================");
        System.out.println("STUNNING RESOLUTION COMPLETE");
        System.out.println("Total iterations: " + iteration);
        System.out.println("========================================\n");
        
        // Recalcul final des stunning pour afficher l'état final
        t.print("Step 7 - Final stunning recalculation...");
        detectStunningSituations(graph, pph,true);
        
        // Validation finale de la cohérence des structures TreeCC
        int finalInconsistencies = validateTreeCCConsistency(graph);
        if (finalInconsistencies > 0) {
            System.out.println("!!! FINAL WARNING: " + finalInconsistencies + " TreeCC inconsistencies remain after step 7 !!!");
        } else {
        }
    }
    
    /**
     * Classe interne pour stocker le contexte d'une situation de stunning
     */
    private static class StunningContext {
        CC stunningCC;
        ConnectionEdge stunningEdge;
        ArrayList<String> stunningReasons;
        ArrayList<Double> stunningMADValues;
        double totalStunningLevel;
        
        StunningContext(CC cc, ConnectionEdge edge) {
            this.stunningCC = cc;
            this.stunningEdge = edge;
            if (cc != null) {
                this.stunningReasons = new ArrayList<>(cc.stunningReasons);
                this.stunningMADValues = new ArrayList<>(cc.stunningMADValues);
                this.totalStunningLevel = cc.stunningLevel;
            }
            if (edge != null) {
                if (this.stunningReasons == null) this.stunningReasons = new ArrayList<>();
                if (this.stunningMADValues == null) this.stunningMADValues = new ArrayList<>();
                this.stunningReasons.addAll(edge.stunningReasons);
                this.stunningMADValues.addAll(edge.stunningMADValues);
                this.totalStunningLevel += edge.stunningLevel;
            }
        }
    }
    
    /**
     * Classe interne pour stocker une modification proposée
     */
    private static class ProposedModification {
        String modificationType; // "DISCONNECT_AND_RECONNECT_VIA_HIDDEN"
        ConnectionEdge edgeToDisconnect;
        CC newPredecessor;
        CC targetCC;
        double stunningImprovement; // Diminution attendue du niveau de stunning
        int downstreamImpact; // Nombre de CC affectées en aval
        int downstreamPixels; // Nombre de pixels affectés en aval
        String reasoning; // Explication de la proposition
        
        public String getString() {
            if(this.modificationType.equals("DISCONNECT_AND_RECONNECT_VIA_HIDDEN")) {
                String ccFin="CC "+this.edgeToDisconnect.target.day+"-"+this.edgeToDisconnect.target.n;
                ccFin+=" ("+doudou(this.edgeToDisconnect.target.xCentralPixAbsolu* Utils.sizeFactorForGraphRendering)+",";
                ccFin+=doudou(this.edgeToDisconnect.target.yCentralPixAbsolu* Utils.sizeFactorForGraphRendering)+")";

                String ccMed="CC "+this.edgeToDisconnect.source.day+"-"+this.edgeToDisconnect.source.n;
                ccMed+=" ("+doudou(this.edgeToDisconnect.source.xCentralPixAbsolu* Utils.sizeFactorForGraphRendering)+",";
                ccMed+=doudou(this.edgeToDisconnect.source.yCentralPixAbsolu* Utils.sizeFactorForGraphRendering)+")";

                String ccDeb="CC "+this.newPredecessor.day+"-"+this.newPredecessor.n;
                ccDeb+=" ("+doudou(this.newPredecessor.xCentralPixAbsolu* Utils.sizeFactorForGraphRendering)+",";
                ccDeb+=doudou(this.newPredecessor.yCentralPixAbsolu* Utils.sizeFactorForGraphRendering)+")";

                return "Disconnect [" + ccFin + "] from [" + ccMed + "] and reconnect it to [" + ccDeb + "] via hidden CC";
            }
            else return "Unknown Modification Type";
        }

        ProposedModification(String type, ConnectionEdge edge, CC newPred, CC target) {
            this.modificationType = type;
            this.edgeToDisconnect = edge;
            this.newPredecessor = newPred;
            this.targetCC = target;
            this.stunningImprovement = 0;
            this.downstreamImpact = 0;
            this.downstreamPixels = 0;
            this.reasoning = "";
        }
    }
    
    /**
     * Inventorier toutes les situations de stunning dans le graphe
     */
    private static ArrayList<StunningContext> inventoryStunningSituations(
            SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, PipelineParamHandler pph) {
        
        ArrayList<StunningContext> contexts = new ArrayList<>();
        
        // Parcourir tous les CC et edges stunning
        for (CC cc : graph.vertexSet()) {
            if (cc.stunningLevel > 0 && !cc.isOut) {
                contexts.add(new StunningContext(cc, null));
            }
        }
        
        for (ConnectionEdge edge : graph.edgeSet()) {
            if (edge.stunningLevel > 0 && edge.activated && !edge.isOut) {
                contexts.add(new StunningContext(null, edge));
            }
        }
        
        return contexts;
    }
    
    /**
     * Proposer des micro-modifications pour un contexte de stunning donné
     * Focus initial : cas WEIRD_CHILD_ORGAN_SURFACE (WCOSE)
     */
    private static ArrayList<ProposedModification> proposeModificationsForContext(
            StunningContext context, SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, PipelineParamHandler pph) {
        
        ArrayList<ProposedModification> modifications = new ArrayList<>();
        
        // Cas 1 : Edge stunning de type WEIRD_CHILD_ORGAN_SURFACE
        if (context.stunningEdge != null && context.stunningReasons.contains("WEIRD_CHILD_ORGAN_SURFACE")) {
            modifications.addAll(proposeModificationsForWCOSE(context, graph, pph));
        }
        
        // Cas 2 : CC stunning avec RADIUS_OUTLIER et edge incoming stunning
        if (false && context.stunningCC != null && context.stunningReasons.contains("RADIUS_OUTLIER")) {
            ConnectionEdge inEdge = context.stunningCC.bestIncomingActivatedEdge();
            if (inEdge != null && inEdge.stunningLevel > 0) {
                modifications.addAll(proposeModificationsForRadiusOutlier(context, graph, pph));
            }
        }
        
        return modifications;
    }
    
    /**
     * Proposer des modifications pour un cas WEIRD_CHILD_ORGAN_SURFACE
     * Stratégie : chercher un autre prédécesseur potentiel de l'autre côté de la CC source
     */
    private static ArrayList<ProposedModification> proposeModificationsForWCOSE(
            StunningContext context, SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, PipelineParamHandler pph) {
        
        boolean debug=false &&((context.stunningEdge!=null) && (context.stunningEdge.source == getCCWithResolution(graph, 5357, 3180)));
        if(debug)System.out.println("-----------------------------------\nDebug WCOSE");
        ArrayList<ProposedModification> modifications = new ArrayList<>();
        
        if(debug)System.out.println("Starting WCOSE proposal for edge: " + context.stunningEdge);
        ConnectionEdge stunningEdge = context.stunningEdge;
        CC source = stunningEdge.source;
        CC target = stunningEdge.target;
        
        // Chercher des CC candidates comme nouveaux prédécesseurs
        // Critères : même jour que source, ou jour précédent, spatially proche de l'autre côté de source
        
        ArrayList<CC> candidates = new ArrayList<>();
        
        // Fusionner les CC depuis outgoingEdges (targets) et incomingEdges (sources)
        CC[] candidateArray = Stream.concat(
            graph.outgoingEdgesOf(source).stream().map(e -> e.target),
            graph.incomingEdgesOf(source).stream().map(e -> e.source)
        ).toArray(CC[]::new);
        
        for (CC candidate : candidateArray) {
            if(debug)System.out.println("Evaluating candidate: " + candidate);
            if(debug)System.out.println("Pass 1");
            if (candidate.isOut) continue;
            if(debug)System.out.println("Pass 2");
            if (candidate == source || candidate == target) continue;
            if(debug)System.out.println("Pass 3");
            if(!graph.containsEdge(source,candidate)&& !graph.containsEdge(candidate,source)) continue; // Ne proposer pour le moment que les adjacences directes à l'occlusion //TODO
            if(debug)System.out.println("Pass 4");
            
            // Même jour ou jour précédent
            if (candidate.day > target.day) continue;

            // Vérifier proximité spatiale : candidate doit être de l'autre côté de source par rapport à target
            // Distance raisonnable (< 2x la distance actuelle source-target)
            if(debug)System.out.println("Pass 5");
            double distSourceTarget = 2*Utils.distance(stunningEdge.connectionX, stunningEdge.connectionY, target.x(), target.y());
            double distCandidateTarget = Utils.distance(candidate.x(), candidate.y(), target.x(), target.y());
            
            if (distCandidateTarget > 3 * distSourceTarget) continue;
            if(debug)System.out.println("Pass 6");
            
            // Vérifier que candidate est "de l'autre côté" de source
            // Angle entre (source->target) et (source->candidate) doit être > 90°
            double vecSTx = target.x() - stunningEdge.connectionX;
            double vecSTy = target.y() - stunningEdge.connectionY;
            double vecSCx = candidate.x() - stunningEdge.connectionX;
            double vecSCy = candidate.y() - stunningEdge.connectionY;
            double dotProduct = vecSTx * vecSCx + vecSTy * vecSCy;
            
            if (dotProduct > 0) continue; // Même côté, on cherche l'autre côté
            if(debug)System.out.println("Pass 7");

            //Vérifier que l'angle entre (source->target) et (candidate -> target) est faible (inférieur à 45 degrés)
            vecSTx = target.x() - stunningEdge.connectionX;
            vecSTy = target.y() - stunningEdge.connectionY;
            vecSCx = target.x() - candidate.x();
            vecSCy = target.y() - candidate.y();
            if(debug)System.out.println("Checking angle between source->target and candidate->target: ");
            if(debug)System.out.println(" - "+vecSTx+" * "+vecSCx+" + "+vecSTy+" * "+vecSCy);
            dotProduct = vecSTx * vecSCx + vecSTy * vecSCy;
            double normST = Math.sqrt(vecSTx * vecSTx + vecSTy * vecSTy);
            double normCS = Math.sqrt(vecSCx * vecSCx + vecSCy * vecSCy);
            double cosAngle = dotProduct / (normST * normCS + VitimageUtils.EPSILON);
            if(debug)System.out.println(" res= "+cosAngle);
            if (cosAngle < Math.sqrt(2)/2) continue; // Angle supérieur à 45 degrés
            if(debug)System.out.println("Pass 8");


            // Vérifier que candidate a elle-même des problèmes de stunning (edge sortant ou CC elle-même)
            boolean candidateHasStunning = false;
            if (candidate.stunningLevel > 0) candidateHasStunning = true;
            for (ConnectionEdge outEdge : graph.outgoingEdgesOf(candidate)) {
                if (outEdge.activated && outEdge.stunningLevel > 0) {
                    candidateHasStunning = true;
                    break;
                }
            }
            for (ConnectionEdge inEdge : graph.incomingEdgesOf(candidate)) {
                if (inEdge.activated && inEdge.stunningLevel > 0) {
                    candidateHasStunning = true;
                    break;
                }
            }
            if(debug)System.out.println("Pass 9");

            if (!candidateHasStunning) continue;
            if(debug)System.out.println("Pass 10. Candidate accepted: " + candidate);

            candidates.add(candidate);
        }
        
        // Pour chaque candidate, créer une proposition de modification
        for (CC candidate : candidates) {
            ProposedModification mod = new ProposedModification(
                "DISCONNECT_AND_RECONNECT_VIA_HIDDEN",
                stunningEdge,
                candidate,
                target
            );
            
            mod.reasoning = "WCOSE detected. Current source " + source + " has suspicious lateral connection. " +
                          "Candidate " + candidate + " is on opposite side and also has stunning issues. " +
                          "Propose to disconnect current edge and create hidden connection through source CC.";
            
            modifications.add(mod);
        }
        
        return modifications;
    }
    
    /**
     * Proposer des modifications pour un cas RADIUS_OUTLIER avec edge incoming stunning
     */
    private static ArrayList<ProposedModification> proposeModificationsForRadiusOutlier(
            StunningContext context, SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, PipelineParamHandler pph) {
        
        ArrayList<ProposedModification> modifications = new ArrayList<>();
        
        CC stunningCC = context.stunningCC;
        ConnectionEdge inEdge = stunningCC.bestIncomingActivatedEdge();
        
        if (inEdge == null) return modifications;
        
        // Chercher d'autres prédécesseurs potentiels
        for (ConnectionEdge alternativeEdge : graph.incomingEdgesOf(stunningCC)) {
            if (alternativeEdge == inEdge) continue;
            if (alternativeEdge.isOut) continue;//TODO later : ça n'arrivera pas car si il n'est pas out il est activé. Mais chaque CC ne peut avoir qu'un seul edge entrant activé
            
            CC alternativeSource = alternativeEdge.source;
            if (alternativeSource.isOut) continue;
            
            // Vérifier que l'alternative a moins de stunning
            if (alternativeEdge.stunningLevel >= inEdge.stunningLevel) continue;
            
            ProposedModification mod = new ProposedModification(
                "SWITCH_TO_ALTERNATIVE_INCOMING",
                inEdge,
                alternativeSource,
                stunningCC
            );
            
            mod.reasoning = "RADIUS_OUTLIER detected at CC " + stunningCC + ". Current incoming edge " + inEdge +
                          " has stunning level " + inEdge.stunningLevel + ". Alternative edge from " + alternativeSource +
                          " has lower stunning level " + alternativeEdge.stunningLevel + ".";
            
            modifications.add(mod);
        }
        
        return modifications;
    }
    
    /**
     * Évaluer une modification proposée : calculer la diminution de stunning et l'impact downstream
     */
    private static void evaluateModification(ProposedModification mod, 
                                            SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, 
                                            PipelineParamHandler pph) {
        
        // Calcul de la diminution de stunning locale
        double currentStunning = 0;
        double projectedStunning = 0;
        
        // Stunning actuel de l'edge à déconnecter
        if (mod.edgeToDisconnect != null) {
            currentStunning += mod.edgeToDisconnect.stunningLevel;
        }
        
        // Stunning actuel de la target CC
        if (mod.targetCC != null) {
            currentStunning += mod.targetCC.stunningLevel;
        }
        
        // Stunning projeté : assume qu'on réduit de 50% le stunning en reconnectant
        // (heuristique simple pour commencer)
        projectedStunning = currentStunning * 0.5;
        
        mod.stunningImprovement = currentStunning - projectedStunning;
        
        // Calcul de l'impact downstream : combien de CC et pixels sont affectés en aval
        if (mod.targetCC != null) {
            mod.downstreamImpact = mod.targetCC.nbSuccessors;
            mod.downstreamPixels = mod.targetCC.nbPixSuccessors;
        }
    }
    
    /**
     * ========================================
     * SECTION : SIMULATION ET APPLICATION DES MODIFICATIONS
     * ========================================
     */
    
    /**
     * Classe pour stocker un snapshot léger de l'état du graphe
     */
    private static class ModificationSnapshot {
        HashMap<ConnectionEdge, Boolean> edgeActivationStatus = new HashMap<>();
        ConnectionEdge createdHiddenEdge = null; // Edge créé pendant la modification
        
        void saveEdgeStatus(ConnectionEdge edge) {
            edgeActivationStatus.put(edge, edge.activated);
        }
        
        void restoreEdgeStatus() {
            for (Map.Entry<ConnectionEdge, Boolean> entry : edgeActivationStatus.entrySet()) {
                entry.getKey().activated = entry.getValue();
            }
        }
    }
    
    /**
     * Appliquer une modification (déconnecter un edge, reconnecter via hidden edge)//TODO : gere seulement l'ordre 1 (voir la fonction appelante et la ligne pathOfCC 10 lignes plus bas qu ici)
     */
    private static ConnectionEdge applyModification(ProposedModification mod, 
                                                   SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph) {
        // Debug activé si la targetCC est la CC problématique (5724, 2870)
        boolean debug = (mod.targetCC == getCCWithResolution(graph, 5824, 2870));
        
        if (debug) {
            System.out.println("\n=== APPLYING MODIFICATION ===");
            System.out.println("Edge to disconnect: " + mod.edgeToDisconnect);
            System.out.println("  Source: CC " + mod.edgeToDisconnect.source.day + "-" + mod.edgeToDisconnect.source.n);
            System.out.println("  Target: CC " + mod.targetCC.day + "-" + mod.targetCC.n + 
                             " at (" + doudou(mod.targetCC.x() * sizeFactor) + ", " + doudou(mod.targetCC.y() * sizeFactor) + ")");
            System.out.println("New predecessor: CC " + mod.newPredecessor.day + "-" + mod.newPredecessor.n);
            
            // État initial des incoming edges de targetCC
            System.out.println("\nTargetCC incoming edges BEFORE modification:");
            for (ConnectionEdge e : graph.incomingEdgesOf(mod.targetCC)) {
                System.out.println("  - " + e + " | activated=" + e.activated + " | hidden=" + e.hidden + " | cost=" + dou(e.cost));
            }
        }
        
        // 1. Désactiver l'ancien edge
        mod.edgeToDisconnect.activated = false;
        
        if (debug) {
            System.out.println("\nAfter disabling old edge:");
            for (ConnectionEdge e : graph.incomingEdgesOf(mod.targetCC)) {
                System.out.println("  - " + e + " | activated=" + e.activated + " | hidden=" + e.hidden + " | cost=" + dou(e.cost));
            }
        }
        
        // 2. Chercher s'il existe déjà un edge entre newPredecessor et targetCC
        ConnectionEdge hiddenOrNotEdge = graph.getEdge(mod.newPredecessor, mod.targetCC);
        
        // 3. Si pas d'edge hidden existant, en créer un
        if (hiddenOrNotEdge == null) {
            if (debug) System.out.println("\nCreating new hidden edge...");
            
            // Créer un nouveau hidden edge (comme dans buildStep5IdentifyPossibleHiddenConnections)
            hiddenOrNotEdge = new ConnectionEdge(mod.newPredecessor.x()*0.5+mod.targetCC.x()*0.5, mod.newPredecessor.y()*0.5+mod.targetCC.y()*0.5, -1, mod.newPredecessor, mod.targetCC, -1, -1);
            hiddenOrNotEdge.hidden = true;

            // IMPORTANT : Calculer un coût pour le hidden edge
            // On utilise le coût de l'edge déconnecté comme référence, ou un coût minimal
            hiddenOrNotEdge.cost = Math.min(mod.edgeToDisconnect.cost, COST_START * 0.5);
            
            if (debug) System.out.println("  Hidden edge cost: " + dou(hiddenOrNotEdge.cost));
            
            // Calculer et ajouter les facettes pour le hidden edge
            hiddenOrNotEdge.pathOfCC = new ArrayList<>();
            hiddenOrNotEdge.pathOfCC.add(mod.newPredecessor);
            hiddenOrNotEdge.pathOfCC.add(mod.edgeToDisconnect.source);
            hiddenOrNotEdge.pathOfCC.add(mod.targetCC);

            double firstConX=0;
            double firstConY=0;
            if(graph.getEdge(mod.edgeToDisconnect.source, mod.newPredecessor)!=null) {
                ConnectionEdge edgeBetween=graph.getEdge(mod.edgeToDisconnect.source, mod.newPredecessor);
                firstConX=edgeBetween.connectionX;
                firstConY=edgeBetween.connectionY;
            }
            else {
                firstConX=(mod.edgeToDisconnect.source.x()+mod.newPredecessor.x())/2;
                firstConY=(mod.edgeToDisconnect.source.y()+mod.newPredecessor.y())/2;
            }
            hiddenOrNotEdge.hiddenConnectingFacets.add(new double[] {firstConX, firstConY});
            hiddenOrNotEdge.hiddenConnectingFacets.add(new double[] {mod.edgeToDisconnect.connectionX, mod.edgeToDisconnect.connectionY});

            if (debug) System.out.println("  Adding hidden edge to graph...");
            boolean added = graph.addEdge(mod.newPredecessor, mod.targetCC, hiddenOrNotEdge);
            if (debug) {
                System.out.println("  Edge added successfully: " + added);
                if (!added) {
                    System.out.println("  ERROR: Failed to add hidden edge - an edge already exists!");
                    System.out.println("  This indicates a BUG in step 1: edge should not exist between non-touching CC!");
                }
            }
        }
        else {
            if (debug) System.out.println("\nReusing existing edge...");
            
            
        }
        
         
        if (debug) {
            System.out.println("\nAfter activating hidden edge:");
            for (ConnectionEdge e : graph.incomingEdgesOf(mod.targetCC)) {
                System.out.println("  - " + e + " | activated=" + e.activated + " | hidden=" + e.hidden + " | cost=" + dou(e.cost));
            }
            
            ConnectionEdge best = mod.targetCC.bestIncomingActivatedEdge();
            System.out.println("\nBest incoming activated edge: " + best);
            if (best != null) {
                System.out.println("  Source: CC " + best.source.day + "-" + best.source.n);
                System.out.println("  Is it the hidden edge? " + (best == hiddenOrNotEdge));
            }
            
            System.out.println("=== END APPLYING MODIFICATION ===\n");
        }
        
        return hiddenOrNotEdge;
    }
    
    /**
     * Annuler une modification (rollback)
     */
    private static void rollbackModification(ModificationSnapshot snapshot, 
                                            SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph) {
        // Restaurer les statuts d'activation
        snapshot.restoreEdgeStatus();
        
        // Si un edge hidden a été créé, le supprimer
        if (snapshot.createdHiddenEdge != null && graph.containsEdge(snapshot.createdHiddenEdge)) {
            graph.removeEdge(snapshot.createdHiddenEdge);
        }
    }
    
    /**
     * Identifier les CC affectées par une modification (voisinage direct)
     */
    private static Set<CC> identifyAffectedCCs(ProposedModification mod, 
                                               SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph) {
        Set<CC> affected = new HashSet<>();
        
        // La target CC
        affected.add(mod.targetCC);
        
        // L'ancienne source
        affected.add(mod.edgeToDisconnect.source);
        
        // La nouvelle source
        affected.add(mod.newPredecessor);
        
        // Les voisins directs de la target (avant et après modification)
        for (ConnectionEdge edge : graph.incomingEdgesOf(mod.targetCC)) {
            affected.add(edge.source);
        }
        for (ConnectionEdge edge : graph.outgoingEdgesOf(mod.targetCC)) {
            affected.add(edge.target);
        }
        
        // Les voisins de l'ancienne source
        for (ConnectionEdge edge : graph.outgoingEdgesOf(mod.edgeToDisconnect.source)) {
            affected.add(edge.target);
        }
        
        // Les voisins de la nouvelle source
        for (ConnectionEdge edge : graph.outgoingEdgesOf(mod.newPredecessor)) {
            affected.add(edge.target);
        }
        
        return affected;
    }
    
    /**
     * Identifier les edges affectés par une modification
     */
    private static Set<ConnectionEdge> identifyAffectedEdges(ProposedModification mod, 
                                                             SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph) {
        Set<ConnectionEdge> affected = new HashSet<>();
        
        // L'edge à déconnecter
        affected.add(mod.edgeToDisconnect);
        
        // Tous les edges des CC affectées
        Set<CC> affectedCCs = identifyAffectedCCs(mod, graph);
        for (CC cc : affectedCCs) {
            affected.addAll(graph.incomingEdgesOf(cc));
            affected.addAll(graph.outgoingEdgesOf(cc));
        }
        
        return affected;
    }
    
    /**
     * Calculer la somme du stunning dans un ensemble de CC et edges
     */
    private static double sumStunning(Set<CC> ccs, Set<ConnectionEdge> edges) {
        double sum = 0;
        
        for (CC cc : ccs) {
            if (!cc.isOut) {
                sum += cc.stunningLevel;
            }
        }
        
        for (ConnectionEdge edge : edges) {
            if (!edge.isOut) {
                sum += edge.stunningLevel;
            }
        }
        
        return sum;
    }
    
    /**
     * Reconstruction complète des structures TreeCC à partir du graphe actuel.
     * Utilisé pendant la simulation pour maintenir la cohérence des structures.
     * 
     * @param graph Le graphe
     * @param initialPoints Les points initiaux (racines primaires)
     */
    private static void rebuildTreeCCStructures(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, 
                                                ArrayList<CC> initialPoints) {
        // Réinitialiser les structures TreeCC
        treeRoots.clear();
        ccToTreeMap.clear();
        
        // Réinitialiser les ordres et index dans tous les CC
        for (CC cc : graph.vertexSet()) {
            cc.order = 0;
            cc.indexInOrgan = 0;
            cc.indexOfCorrespondingPlant = -1;
            cc.indexOfCorrespondingOrganRelativeToParentStructure.clear();
        }
        
        // IMPORTANT : Calculer nbPixSuccessors et nbSuccessors AVANT de construire les TreeCC
        // (nécessaire pour choisir le bon chemin principal lors de la construction)
        // D'abord réinitialiser
        for (CC cc : graph.vertexSet()) {
            cc.nbPixSuccessors = 0;
            cc.nbSuccessors = 0;
        }
        
        // Calculer depuis les feuilles en remontant, en se basant uniquement sur les edges activés
        for (CC cc : graph.vertexSet()) {
            if (cc.isOut) continue; // Ignorer les CC exclues
            
            // Vérifier si c'est une feuille (aucun edge sortant activé)
            boolean isLeaf = true;
            for (ConnectionEdge outEdge : graph.outgoingEdgesOf(cc)) {
                if (outEdge.activated && !outEdge.target.isOut) {
                    isLeaf = false;
                    break;
                }
            }
            if (!isLeaf) continue; // Pas une feuille
            
            // Remonter depuis cette feuille
            CC ccTmp = cc;
            int countPix = 0;
            int countSucc = 0;
            while (ccTmp != null) {
                countPix += ccTmp.nPixels;
                countSucc += 1;
                
                // Trouver le meilleur edge entrant activé
                ConnectionEdge bestIncoming = null;
                double minCost = 1E18;
                for (ConnectionEdge inEdge : graph.incomingEdgesOf(ccTmp)) {
                    if (inEdge.activated && inEdge.cost < minCost && !inEdge.source.isOut) {
                        minCost = inEdge.cost;
                        bestIncoming = inEdge;
                    }
                }
                
                if (bestIncoming != null) {
                    ccTmp = bestIncoming.source;
                    ccTmp.nbPixSuccessors += countPix;
                    ccTmp.nbSuccessors += countSucc;
                } else {
                    ccTmp = null;
                }
            }
        }
        
        // Racines d'ordre 1 (chemins principaux)
        for (CC cc : initialPoints.stream()
                .sorted(Comparator.comparingDouble(c -> c.xCentralPixAbsolu))
                .collect(Collectors.toList())) {
            
            int i = initialPoints.indexOf(cc);
            CC ccTmp = cc;
            CC ccLast = ccTmp;
            ccTmp.indexOfCorrespondingPlant = i;
            ccTmp.indexInOrgan = 0;
            ccTmp.order = 1;
            
            // Créer le noeud racine de l'arbre TreeCC pour cette plante
            TreeCC rootNode = new TreeCC(ccTmp, i, 1, 0, true, true);
            treeRoots.add(rootNode);
            ccToTreeMap.put(ccTmp, rootNode);
            TreeCC lastTreeNode = rootNode;
            
            while (ccTmp != null) {
                int maxVal = -1;
                CC ccBestNext = null;
                for (CC ccNext : graph.outgoingEdgesOf(ccTmp).stream()
                        .filter(e -> e.activated) // Filtre pour ne considérer que les edges activés (y compris hidden)
                        .map(e -> e.target).collect(Collectors.toList())) {
                    if (ccNext.nbPixSuccessors > maxVal) {
                        maxVal = ccNext.nbPixSuccessors;
                        ccBestNext = ccNext;
                    }
                }
                if (ccBestNext == null) break;
                
                ccLast = ccTmp;
                ccTmp = ccBestNext;
                ccTmp.indexInOrgan = ccLast.indexInOrgan + 1;
                ccTmp.indexOfCorrespondingPlant = i;
                ccTmp.order = 1;
                ccTmp.indexOfCorrespondingOrganRelativeToParentStructure.add(new Integer(0));
                
                // Ajouter ce CC au TreeCC
                TreeCC newTreeNode = new TreeCC(ccTmp, i, 1, ccTmp.indexInOrgan, false, true);
                lastTreeNode.addChild(newTreeNode);
                ccToTreeMap.put(ccTmp, newTreeNode);
                lastTreeNode = newTreeNode;
            }
        }
        
        // Racines d'ordre 2 et plus
        for (int order = 2; order < Utils.MAX_ORDER_ROOTS; order++) {
            for (CC cc : graph.vertexSet().stream()
                    .sorted(Comparator.comparingDouble(c -> c.yCentralPixAbsolu))
                    .collect(Collectors.toList())) {
                
                if (cc.isOut) continue; // Ignorer les CC exclues
                CC ccTmp = cc;
                if (ccTmp.order != 0) continue;
                if (ccTmp.bestIncomingActivatedCC() == null) continue;
                if (ccTmp.bestIncomingActivatedCC().order != order - 1) continue;
                
                CC ccLast = ccTmp.bestIncomingActivatedCC();
                ccTmp.order = order;
                ccTmp.indexOfCorrespondingPlant = ccLast.indexOfCorrespondingPlant;
                ccTmp.indexInOrgan = 0;
                
                // Créer le nœud TreeCC pour ce début d'organe latéral
                TreeCC parentTreeNode = ccToTreeMap.get(ccLast);
                TreeCC newOrganStart = new TreeCC(ccTmp, ccLast.indexOfCorrespondingPlant, order, 0, true, false);
                if (parentTreeNode != null) {
                    parentTreeNode.addChild(newOrganStart);
                }
                ccToTreeMap.put(ccTmp, newOrganStart);
                TreeCC lastTreeNode = newOrganStart;
                
                while (ccTmp != null) {
                    int maxVal = -1;
                    CC ccBestNext = null;
                    
                    for (CC ccNext : graph.outgoingEdgesOf(ccTmp).stream()
                            .filter(e -> e.activated)
                            .map(e -> e.target)
                            .collect(Collectors.toList())) {
                        if (ccNext.nbPixSuccessors > maxVal) {
                            maxVal = ccNext.nbPixSuccessors;
                            ccBestNext = ccNext;
                        }
                    }
                    if (ccBestNext == null) break;
                    
                    ccLast = ccTmp;
                    ccTmp = ccBestNext;
                    ccTmp.indexInOrgan = ccLast.indexInOrgan + 1;
                    ccTmp.indexOfCorrespondingPlant = ccLast.indexOfCorrespondingPlant;
                    ccTmp.order = order;
                    
                    // Ajouter ce CC au TreeCC
                    TreeCC newTreeNode = new TreeCC(ccTmp, ccTmp.indexOfCorrespondingPlant, order, 
                                                   ccTmp.indexInOrgan, false, false);
                    lastTreeNode.addChild(newTreeNode);
                    ccToTreeMap.put(ccTmp, newTreeNode);
                    lastTreeNode = newTreeNode;
                }
            }
        }
        
        // DIAGNOSTIC : Vérifier s'il reste des CC avec order = 0 (orphelines)
        ArrayList<CC> orphanCCs = new ArrayList<>();
        for (CC cc : graph.vertexSet()) {
            if (cc.isOut) continue; // Ignorer les CC déjà exclues
            if (cc.order == 0) {
                orphanCCs.add(cc);
            }
        }
        
        if (false && !orphanCCs.isEmpty()) {
            System.out.println("  WARNING: Found " + orphanCCs.size() + " orphan CCs (order = 0) after TreeCC rebuild!");
            System.out.println("  These CCs are not reachable from initialPoints via activated edges.");
            
            // Afficher quelques exemples pour debug
            int displayCount = Math.min(5, orphanCCs.size());
            for (int i = 0; i < displayCount; i++) {
                CC orphan = orphanCCs.get(i);
                
                // Vérifier si elle a des edges entrants/sortants activés
                int activatedIn = 0;
                int activatedOut = 0;
                for (ConnectionEdge e : graph.incomingEdgesOf(orphan)) {
                    if (e.activated && !e.source.isOut) activatedIn++;
                }
                for (ConnectionEdge e : graph.outgoingEdgesOf(orphan)) {
                    if (e.activated && !e.target.isOut) activatedOut++;
                }
                
                System.out.println("    - CC " + orphan.day + "-" + orphan.n + 
                                 " at (" + doudou(orphan.x() * sizeFactor) + ", " + doudou(orphan.y() * sizeFactor) + ")" +
                                 " has " + activatedIn + " activated incoming, " + activatedOut + " activated outgoing edges");
            }
            
            // Option : marquer ces CC comme isOut pour les exclure du graphe
            // (décommenter si on veut les exclure automatiquement)
            /*
            for (CC orphan : orphanCCs) {
                orphan.isOut = true;
                exclusionReasons.put(orphan, "ORPHAN_AFTER_REBUILD");
            }
            System.out.println("  --> Marked all orphan CCs as isOut=true");
            */
        }
    }
    
    /**
     * Validation de la cohérence des structures TreeCC après reconstruction.
     * Vérifie que chaque CC avec des successeurs a au moins un edge sortant de même ordre (chemin principal).
     * 
     * @param graph Le graphe
     * @return Nombre d'incohérences détectées
     */
    private static int validateTreeCCConsistency(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph) {
        int inconsistencyCount = 0;
        ArrayList<String> warnings = new ArrayList<>();
        
        for (CC cc : graph.vertexSet()) {
            if (cc.isOut) continue; // Ignorer les CC exclues
            if (cc.order == 0) continue; // Ignorer les CC pas encore assignées à un ordre
            
            // Si cette CC a des successeurs (nbSuccessors > 0), elle DOIT avoir au moins
            // un edge sortant activé qui continue le même organe (même ordre)
            if (cc.nbSuccessors > 0) {
                boolean hasMainPathOutgoing = false;
                int activatedOutgoingCount = 0;
                int sameOrderOutgoingCount = 0;
                
                for (ConnectionEdge edge : graph.outgoingEdgesOf(cc)) {
                    if (edge.activated && !edge.target.isOut) {
                        activatedOutgoingCount++;
                        if (edge.target.order == cc.order) {
                            hasMainPathOutgoing = true;
                            sameOrderOutgoingCount++;
                        }
                    }
                }
                
                if (!hasMainPathOutgoing) {
                    inconsistencyCount++;
                    String warning = String.format(
                        "INCONSISTENCY: CC %s (order=%d, nbSuccessors=%d, nbPixSuccessors=%d) " +
                        "has successors but NO outgoing edge of same order! " +
                        "(activatedOutgoing=%d, sameOrderOutgoing=%d)",
                        cc, cc.order, cc.nbSuccessors, cc.nbPixSuccessors,
                        activatedOutgoingCount, sameOrderOutgoingCount
                    );
                    warnings.add(warning);
                    
                    // Debug : afficher tous les edges sortants
                    for (ConnectionEdge edge : graph.outgoingEdgesOf(cc)) {
                        String edgeInfo = String.format(
                            "  - Outgoing edge to %s: activated=%b, hidden=%b, target.order=%d, target.isOut=%b",
                            edge.target, edge.activated, edge.hidden, edge.target.order, edge.target.isOut
                        );
                        warnings.add(edgeInfo);
                    }
                }
            }
        }
        
        // Afficher les warnings
        if (inconsistencyCount > 0) {
            System.out.println("\n!!! WARNING: TreeCC consistency check found " + inconsistencyCount + " inconsistencies !!!");
            for (String warning : warnings) {
                System.out.println(warning);
            }
            System.out.println();
        }
        
        return inconsistencyCount;
    }
    
    /**
     * Simuler et évaluer une modification pour mesurer son impact réel sur le stunning
     * Applique temporairement la modification, recalcule les TreeCC et stunning, mesure l'amélioration, puis rollback
     */
    private static double simulateAndEvaluateModification(ProposedModification mod,
                                                         SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph,
                                                         PipelineParamHandler pph) {
        
        // Debug activé si la targetCC est la CC 10-13
        boolean debug = false && (mod.targetCC != null && mod.targetCC.day == 10 && mod.targetCC.n == 13 && mod.newPredecessor.day == 8 && mod.newPredecessor.n == 4);
        
        if (debug) {
            IJ.log("\n>>> SIMULATING MODIFICATION for CC " + mod.targetCC.day + "-" + mod.targetCC.n);
            IJ.log("  New predecessor: CC " + mod.newPredecessor.day + "-" + mod.newPredecessor.n);
            IJ.log("  Edge to disconnect: from CC " + mod.edgeToDisconnect.source.day + "-" + mod.edgeToDisconnect.source.n);
        }
        
        // A) Capturer le stunning AVANT (TOUT le graphe, car effets de cascade possibles)
        double stunningBefore = sumStunningTotal(graph);
        
        if (debug) {
            IJ.log("  Stunning BEFORE: " + dou(stunningBefore));
            IJ.log("\n=== LISTE DES STUNNING AVANT MODIFICATION ===");
            
            // Liste des CC stunning
            IJ.log("\n--- CC STUNNING ---");
            for (CC cc : graph.vertexSet()) {
                if (!cc.isOut && cc.stunningLevel > 0) {
                    IJ.log("CC " + cc.day + "-" + cc.n + " at (" + doudou(cc.x() * sizeFactor) + ", " + doudou(cc.y() * sizeFactor) + ")");
                    IJ.log("  Stunning level: " + cc.stunningLevel);
                    IJ.log("  Reasons: " + cc.stunningReasons.toString());
                    IJ.log("  MAD values: " + cc.stunningMADValues.toString());
                }
            }
            
            // Liste des edges stunning
            IJ.log("\n--- EDGES STUNNING ---");
            for (ConnectionEdge edge : graph.edgeSet()) {
                if (!edge.isOut && edge.activated && edge.stunningLevel > 0) {
                    IJ.log("Edge from CC " + edge.source.day + "-" + edge.source.n + " at (" + doudou(edge.source.x() * sizeFactor) + ", " + doudou(edge.source.y() * sizeFactor) + ")" +
                          " to CC " + edge.target.day + "-" + edge.target.n + " at (" + doudou(edge.target.x() * sizeFactor) + ", " + doudou(edge.target.y() * sizeFactor) + ")");
                    IJ.log("  Stunning level: " + edge.stunningLevel);
                    IJ.log("  Reasons: " + edge.stunningReasons.toString());
                    IJ.log("  MAD values: " + edge.stunningMADValues.toString());
                    IJ.log("  Activated: " + edge.activated + ", Hidden: " + edge.hidden);
                }
            }
        }
        
        // B) Créer un snapshot pour rollback
        ModificationSnapshot snapshot = new ModificationSnapshot();
        snapshot.saveEdgeStatus(mod.edgeToDisconnect);
        



        // C) Appliquer la modification POUR DE VRAI
        ConnectionEdge createdEdge = applyModification(mod, graph);
        if(createdEdge.hidden)snapshot.createdHiddenEdge = createdEdge;
        else snapshot.saveEdgeStatus(createdEdge);
        createdEdge.activated = true;
        
        
        // D) Recalculer les structures TreeCC avec le graphe modifié
        rebuildTreeCCStructures(graph, initialPoints);
        
        // E) Recalculer TOUS les stunning (on ne peut pas faire que local pour WCOSE)
        // Réinitialiser d'abord tous les stunning
        for (CC cc : graph.vertexSet()) {
            cc.stunningLevel = 0;
            cc.stunningReasons.clear();
            cc.stunningMADValues.clear();
        }
        for (ConnectionEdge edge : graph.edgeSet()) {
            edge.stunningLevel = 0;
            edge.stunningReasons.clear();
            edge.stunningMADValues.clear();
        }
        
        // Recalculer les stunning
        detectStunningSituations(graph, pph,false);
        
        // F) Mesurer le stunning APRÈS (TOUT le graphe)
        double stunningAfter = sumStunningTotal(graph);
        
        if (debug) {
            IJ.log("\n=== LISTE DES STUNNING APRES MODIFICATION ===");
            
            // Liste des CC stunning
            IJ.log("\n--- CC STUNNING ---");
            for (CC cc : graph.vertexSet()) {
                if (!cc.isOut && cc.stunningLevel > 0) {
                    IJ.log("CC " + cc.day + "-" + cc.n + " at (" + doudou(cc.x() * sizeFactor) + ", " + doudou(cc.y() * sizeFactor) + ")");
                    IJ.log("  Stunning level: " + cc.stunningLevel);
                    IJ.log("  Reasons: " + cc.stunningReasons.toString());
                    IJ.log("  MAD values: " + cc.stunningMADValues.toString());
                }
            }
            
            // Liste des edges stunning
            IJ.log("\n--- EDGES STUNNING ---");
            for (ConnectionEdge edge : graph.edgeSet()) {
                if (!edge.isOut && edge.activated && edge.stunningLevel > 0) {
                    IJ.log("Edge from CC " + edge.source.day + "-" + edge.source.n + " at (" + doudou(edge.source.x() * sizeFactor) + ", " + doudou(edge.source.y() * sizeFactor) + ")" +
                          " to CC " + edge.target.day + "-" + edge.target.n + " at (" + doudou(edge.target.x() * sizeFactor) + ", " + doudou(edge.target.y() * sizeFactor) + ")");
                    IJ.log("  Stunning level: " + edge.stunningLevel);
                    IJ.log("  Reasons: " + edge.stunningReasons.toString());
                    IJ.log("  MAD values: " + edge.stunningMADValues.toString());
                    IJ.log("  Activated: " + edge.activated + ", Hidden: " + edge.hidden);
                }
            }
            
            IJ.log("\n  Stunning AFTER: " + dou(stunningAfter));
            IJ.log("  >>> IMPROVEMENT: " + dou(stunningBefore - stunningAfter) + " stunning points saved!");
            

        }
        
        // G) ROLLBACK complet
        rollbackModification(snapshot, graph);
        
        // Reconstruire TreeCC avec état original
        rebuildTreeCCStructures(graph, initialPoints);
        
        // Restaurer les stunning (recalcul complet)
        for (CC cc : graph.vertexSet()) {
            cc.stunningLevel = 0;
            cc.stunningReasons.clear();
            cc.stunningMADValues.clear();
        }
        for (ConnectionEdge edge : graph.edgeSet()) {
            edge.stunningLevel = 0;
            edge.stunningReasons.clear();
            edge.stunningMADValues.clear();
        }
        detectStunningSituations(graph, pph,false);
        
        if (debug) {
            IJ.log("  Rollback complete. Graph restored to original state.\n");
        }
        
        // H) Calculer le downstreamImpact de la modification (nombre de CC et pixels affectés en aval)
        if (mod.targetCC != null) {
            mod.downstreamImpact = mod.targetCC.nbSuccessors;
            mod.downstreamPixels = mod.targetCC.nbPixSuccessors;
        }
        
        // I) Retourner l'amélioration (positif = bien)
        double improvement = stunningBefore - stunningAfter;
        return improvement;
    }
    
    /**
     * Calculer la somme TOTALE du stunning dans tout le graphe
     */
    private static double sumStunningTotal(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph) {
        double sum = 0;
        
        for (CC cc : graph.vertexSet()) {
            if (!cc.isOut) {
                sum += cc.stunningLevel;
            }
        }
        
        for (ConnectionEdge edge : graph.edgeSet()) {
            if (!edge.isOut && edge.activated) {
                sum += edge.stunningLevel;
            }
        }
        
        return sum;
    }
    
    /**
     * Réinitialiser à 0 tous les stunningLevelSumNeighbours
     */
    public static void resetStunningLevelSumNeighbours(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph) {
        for (CC cc : graph.vertexSet()) {
            cc.stunningLevelSumNeighbours = 0;
        }
    }
    
    /**
     * Calculer pour chaque vertex non-out la somme du stunningLevel dans le voisinage direct
     * Voisinage = edges actifs entrants + edges actifs sortants + CC elle-même + CC voisines (via edges actifs ou non, hidden ou non)
     */
    public static void computeStunningLevelSumNeighbours(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph) {
        // D'abord réinitialiser
        resetStunningLevelSumNeighbours(graph);

        for (CC cc : graph.vertexSet()) {
            if (cc.isOut) continue; // Ignorer les CC exclues
            int sum = 0;
            // 1. Stunning level du vertex lui-même
            sum += cc.stunningLevel;
            
            // 2. Edges entrants (actifs ou non, hidden ou non)
            for (ConnectionEdge inEdge : graph.incomingEdgesOf(cc)) {
                if(inEdge.activated) sum += inEdge.stunningLevel;
                // Ajouter le stunning du vertex source (si pas déjà compté et pas out)
                CC sourceCC = inEdge.source;
                if (!sourceCC.isOut) {
                    sum += sourceCC.stunningLevel;
                }
            }
            
            // 3. Edges sortants (actifs ou non, hidden ou non)
            for (ConnectionEdge outEdge : graph.outgoingEdgesOf(cc)) {
                if(outEdge.activated) sum += outEdge.stunningLevel;
                // Ajouter le stunning du vertex target (si pas déjà compté et pas out)
                CC targetCC = outEdge.target;
                if (!targetCC.isOut) {
                    sum += targetCC.stunningLevel;
                }
            }
            cc.stunningLevelSumNeighbours = sum;
        }
    }

    public static void waitForLong() {
        waitFor(1000000);
    }




    /**
     * -------------------------------------------------------------------------------------------------------------------------------------------
      * -------------------------------------------------------------------------------------------------------------------------------------------
     * Pre processing : construction of original graph, connections plongement identifiation, and CC path finder *************************************************************************************
      * -------------------------------------------------------------------------------------------------------------------------------------------
     * -------------------------------------------------------------------------------------------------------------------------------------------
     */
    public static SimpleDirectedWeightedGraph<CC, ConnectionEdge> buildStep1OriginalConnexionGraphFromDateMap(ImagePlus imgDates, int connexity, double[] hours) {
//        ImagePlus imgDates=new Duplicator().run(imgDatesTmp);
        int maxSizeConnexion = 500000000;//????????????????
        nDays = 1 + Math.round(VitimageUtils.maxOfImage(imgDates));
        imgDates.setDisplayRange(0,nDays);
        IJ.run(imgDates,"Fire","");
        medianFilteringOfImageDates(imgDates);
        //imgDates.show();
        CC[][] tabCC = new CC[nDays][];
        SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph = new SimpleDirectedWeightedGraph<>(ConnectionEdge.class);

        //Identify connected components with label 1-N
        //roisCC[0] = new Roi[]{new Roi(new Rectangle(0, 0, 1, 1))};
        //tabCC[0] = new CC[]{new CC(0, new double[]{hours[0],hours[0],hours[0],hours[0]}, 0, roisCC[0][0], graph)};
        System.out.println("Identifying connected components ");
        
        for (int d = 1; d < nDays; d++) {
            System.out.print(d + " ");
            ImagePlus binD = VitimageUtils.thresholdImage(imgDates, d, d + 0.99);
            ImagePlus ccD = VitimageUtils.connexeBinaryEasierParamsConnexitySelectvol(binD, connexity, 0);
            ccD = VitimageUtils.connexeNoFuckWithVolume(binD,0.5,256,MIN_SIZE_CC,10E9,connexity,0,true);
            int labelMax = (int) VitimageUtils.maxOfImage(ccD);
            tabCC[d] = new CC[labelMax];
            //Extract the bounding box for each connected component
            //By testing all x and y of the image, for each label, keep the min and max x and y
            int[] minX = new int[labelMax + 1];
            int[] minY = new int[labelMax + 1];
            int[] maxX = new int[labelMax + 1];
            int[] maxY = new int[labelMax + 1];
            Arrays.fill(minX, Integer.MAX_VALUE);
            Arrays.fill(minY, Integer.MAX_VALUE);
            Arrays.fill(maxX, Integer.MIN_VALUE);
            Arrays.fill(maxY, Integer.MIN_VALUE);
            for (int x = 0; x < ccD.getWidth(); x++) {
                for (int y = 0; y < ccD.getHeight(); y++) {
                    int label = ccD.getProcessor().getPixel(x, y);
                    if (label > 0) {
                        minX[label] = Math.min(minX[label], x);
                        minY[label] = Math.min(minY[label], y);
                        maxX[label] = Math.max(maxX[label], x);
                        maxY[label] = Math.max(maxY[label], y);
                    }
                }
            }
            for(int ccIndex=1;ccIndex<=labelMax;ccIndex++){
                //System.out.println("->Processing CC "+ccIndex+"/"+labelMax+" of day "+d);
                double hourGuessedOfTip=hours[d-1];//TODO : 
                double hourGuessedOfStart=hours[(d-2>=0)?d-2: (d-1>=0)?d-1:d];
                double hourGuessedOfCentroid=0.5*(hourGuessedOfStart+hourGuessedOfTip);
                double []hoursTmp=new double[]{hours[d - 1],hourGuessedOfStart,hourGuessedOfCentroid,hourGuessedOfTip};
                CC cc=new CC(minX[ccIndex], minY[ccIndex], maxX[ccIndex], maxY[ccIndex], d, hoursTmp, ccIndex-1, graph, ccD, imgDates);
                double size=cc.nPixels;
                if(USE_CONJONCTURE_D_VERY_SMALL_CC_HAVE_TO_BE_IGNORED && (size<MIN_SIZE_CC))cc.isOutlierTooSmall=true;
                tabCC[d][ccIndex-1] = cc;
                // boolean debug=false&&(cc==getCCWithResolution(graph,6277,3297));
                if(USE_CONJONCTURE_E_EXCLUDE_CC_THAT_ARE_TOTALLY_INCLUDED_IN_ANOTHER_CC && cc.isArtifactIncludedIntoAnotherCCWithoutTouchingTheBorders){
                    System.out.println("Warning : CC "+cc.toString()+" is included into another CC without touching the borders. It will be ignored in the tracking.");
                }
                // if(debug)System.out.println(" -> adding vertex "+cc);
                // if(debug)System.out.println(" INCLUDED ? -> "+cc.isArtifactIncludedIntoAnotherCCWithoutTouchingTheBorders);
                graph.addVertex(cc);
            }
        }


                
        System.out.println();
        double costTot=0;
        double nbConnexions=0;

        //Identify connexions
        System.out.print("Identifying connexions ");
        CC debTarCC=getCCWithResolution(graph, 4716,3156);//4  4716.0 - 3156.->  4734.0 - 3234.0  ->         716.0 3156.0    4726 3525
        CC debSourCC=getCCWithResolution(graph, 4680,3120);//4  4716.0 - 3156.->  4734.0 - 3234.0  ->         716.0 3156.0    4726 3525
        System.out.println("\nDebug CCSour "+debSourCC);
        System.out.println("\nDebug CCTarget "+debTarCC);
        for (int d1 = 1; d1 < nDays; d1++) {
            System.out.print(d1 + " ");
            if (tabCC[d1] == null) continue;
            for (int n1 = 0; n1 < tabCC[d1].length; n1++) {
                for (int d2 = 1; d2 < nDays; d2++) {
                    if (tabCC[d2] == null) continue;
                    for (int n2 = 0; n2 < tabCC[d2].length; n2++) {
                        if (USE_CONJONCTURE_E_EXCLUDE_CC_THAT_ARE_TOTALLY_INCLUDED_IN_ANOTHER_CC ) {
                            if (tabCC[d1][n1].isArtifactIncludedIntoAnotherCCWithoutTouchingTheBorders) continue;
                            if (tabCC[d2][n2].isArtifactIncludedIntoAnotherCCWithoutTouchingTheBorders) continue;
                        }
                        if ((d2 <= d1))continue;
                        //if ((d2 < d1) || ((d2 == d1) && (n2 <= n1))) continue; TODO : the fuck ?
                        
                        // Debug spécifique pour la paire CC 9-2 et CC 10-13
                        
                        
                        boolean debug=false && (tabCC[d1][n1]==debSourCC) && (tabCC[d2][n2]==debTarCC);
                        if(debug)System.out.println(" -> considering connexion aaa between "+tabCC[d1][n1]+" and "+tabCC[d2][n2]);

                        if(debug)System.out.println("Toto1");


                        double[] tabConn = tabCC[d1][n1].nFacets4connexe_V3(tabCC[d2][n2]);
                        
                        
                        if(debug)System.out.println("Toto2");
                        int n = (int) Math.round(tabConn[0]);
                        double x = tabConn[5];
                        double y = tabConn[6];
                        if (n > 0 && n < maxSizeConnexion) {
                           
                            ConnectionEdge edge=new ConnectionEdge(x, y, n, tabCC[d1][n1],
                                    tabCC[d2][n2], tabConn[3], tabConn[4]);
                            graph.addEdge(tabCC[d1][n1], tabCC[d2][n2], edge);
                            nbConnexions++;
                        }
                    }
                }

                if(USE_CONJONCTURE_H_IF_STARTING_CC_THAT_HAVE_ONLY_NEIGHBOUR_VERY_LONG_TIME_AFTER_THEN_IT_IS_OUTLIER){
                    if((graph.incomingEdgesOf(tabCC[d1][n1]).size()==0)){
                        double minDeltaTime=Double.MAX_VALUE;
                        for(ConnectionEdge edgeTmp:graph.outgoingEdgesOf(tabCC[d1][n1])){
                            double deltaTime=edgeTmp.target.day-edgeTmp.source.day;
                            if(deltaTime<minDeltaTime)minDeltaTime=deltaTime;
                        }
                        if(minDeltaTime>1){
                            graph.removeVertex(tabCC[d1][n1]);
                            //System.out.println(" -> removing starting CC "+tabCC[d1][n1]+" that have only neighbour very long time after (delta time = "+minDeltaTime+")");
                        }
                    }
                }
            }
        }

        if(USE_CONJONCTURE_C_LOSSY_ISOLATED_DEAD_ENDS_GET_OUT_FROM_GRAPH_AND_WILL_BE_COUNTED_LATER){
            for(CC cc:graph.vertexSet()){
                boolean debug=(cc==getCCWithResolution(graph,4907,8374));
                if(debug)System.out.println("\nConsidering CC "+cc);
                if((graph.outgoingEdgesOf(cc).size()==0) && (graph.incomingEdgesOf(cc).size()==1) && graph.outDegreeOf(cc.bestIncomingEdge().source)>1){
                    ConnectionEdge edge=cc.bestIncomingEdge();
                    int S0perHour= (int)Math.round(edge.source.nPixels/(edge.source.hourGuessedOfTip-edge.source.hourGuessedOfStart+(edge.source.hourGuessedOfTip==edge.source.hourGuessedOfStart?VitimageUtils.EPSILON:0)));
                    int S1perHour= (int)Math.round(edge.target.nPixels/(edge.target.hourGuessedOfTip-edge.target.hourGuessedOfStart+(edge.target.hourGuessedOfTip==edge.target.hourGuessedOfStart?VitimageUtils.EPSILON:0)));
                    boolean surfaceHourIsFuzzy=false;
                    if(S0perHour>S1perHour*ALPHA_FUZZY_LOSSY_DEADEND_CONNECTION)surfaceHourIsFuzzy=true;
                    if(! surfaceHourIsFuzzy){
                        continue;
                    }

                    cc.primaryOfAnEmergingRootAtLastTimeOrFuzzyRegistration=edge.source;
                    cc.facetsMakingItAnEmergingRootAtLastTimeOrFuzzyRegistration= new double[]{edge.connectionX,edge.connectionY};
                    if(debug)System.out.println(" -> marking as excluded: "+edge);
                    
                    // Marquer comme exclus (mais garder dans le graphe)
                    cc.isOut = true;
                    edge.isOut = true;
                    exclusionReasons.put(cc, "DEAD_END_FUZZY_SURFACE");
                    edgeExclusionReasons.put(edge, "DEAD_END_FUZZY_SURFACE");
                }
            }
        }

        if(USE_CONJONCTURE_F_EXCLUDE_CC_THAT_START_ORGANS_AND_THAT_ARE_VERY_SMALL_RELATIVE_TO_THEIR_UNIQUE_POSSIBLE_SUCCESSOR){
            for(CC cc:graph.vertexSet()){
                boolean debug=(cc==getCCWithResolution(graph,6277,3297));
                if(debug)System.out.println("\nConsidering CC "+cc);
                if((graph.incomingEdgesOf(cc).size()==0) && (graph.outgoingEdgesOf(cc).size()==1)){
                    ConnectionEdge edge=cc.bestOutgoingEdge();
                    int S0perHour= (int)Math.round(edge.source.nPixels/(edge.source.hourGuessedOfTip-edge.source.hourGuessedOfStart+(edge.source.hourGuessedOfTip==edge.source.hourGuessedOfStart?VitimageUtils.EPSILON:0)));
                    int S1perHour= (int)Math.round(edge.target.nPixels/(edge.target.hourGuessedOfTip-edge.target.hourGuessedOfStart+(edge.target.hourGuessedOfTip==edge.target.hourGuessedOfStart?VitimageUtils.EPSILON:0)));
                    boolean surfaceHourIsFuzzy=false;
                    if(S1perHour>S0perHour*ALPHA_FUZZY_START_VERY_SMALL_CONNECTION)surfaceHourIsFuzzy=true;
                    if(! surfaceHourIsFuzzy){
                        continue;
                    }

                    cc.isArtifactMakingBelieveOfAnOrganStartButIsVerySmallRelativelyToSuccessor=true;
                    if(debug)System.out.println(" -> marking as excluded: "+edge);
                    
                    // Marquer comme exclus (mais garder dans le graphe)
                    cc.isOut = true;
                    edge.isOut = true;
                    exclusionReasons.put(cc, "START_VERY_SMALL_RELATIVE_TO_SUCCESSOR");
                    edgeExclusionReasons.put(edge, "START_VERY_SMALL_RELATIVE_TO_SUCCESSOR");
                }
            }
        }



        
        System.out.println("End of step 1 . Nb of vertices : " + graph.vertexSet().size() + ", nb of edges : " + graph.edgeSet().size());
        System.out.println("Average cost of connexions : " + (costTot/nbConnexions) + " , nb connexions : " + nbConnexions);
        System.out.println();
        return graph;
    }

    public static ConnectionEdge getEdgeWithResolution(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, int daySource, int xSource, int ySource,int dayTarget, int xTarget, int yTarget){
        ConnectionEdge edgeFound=null;
        double minDist=Double.MAX_VALUE;
        for(ConnectionEdge edge:graph.edgeSet()){
            double dD1=daySource-edge.source.day;
            double dD2=dayTarget-edge.target.day;
            double dX1=xSource*1.0/sizeFactor-edge.source.xCentralPixAbsolu;
            double dX2=xTarget*1.0/sizeFactor-edge.target.xCentralPixAbsolu;
            double dY1=ySource*1.0/sizeFactor-edge.source.yCentralPixAbsolu;
            double dY2=yTarget*1.0/sizeFactor-edge.target.yCentralPixAbsolu;
            double dist=Math.sqrt(Math.pow(dD1,2)+Math.pow(dX1,2)+Math.pow(dY1,2)+Math.pow(dD2,2)+Math.pow(dX2,2)+Math.pow(dY2,2));
            if(dist<minDist){
                minDist=dist;
                edgeFound=edge;
            }
        }
        return edgeFound;
    }






    public static void buildStep2ValidateFormallyTheObviousEdgesAndAddHintVectors(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, PipelineParamHandler pph) {
        boolean debug=false;
        ArrayList<ConnectionEdge> edgesToActivate=new ArrayList<>();
        for(ConnectionEdge edge : graph.edgeSet()){
            CC source=graph.getEdgeSource(edge);
            CC target=graph.getEdgeTarget(edge);

            if(USE_CONJONCTURE_A_IF_SOURCE_ONLY_ONE_OUTPUT_AND_TARGET_ONLY_ONE_INPUT){   
                if (graph.outgoingEdgesOf(source).size() <= 1 && graph.incomingEdgesOf(target).size() <= 1) {
                    edgesToActivate.add(edge);
                }
            }
            
            
           if(USE_CONJONCTURE_B_IF_SOURCE_ONLY_ONE_OUTPUT_PER_DAYAND_TARGET_ONLY_ONE_INPUT_PER_DAY){   
                if( (source.day==target.day-1)){
                    if(debug)System.out.println(" -> applying conjecture B");
                    int nbDayPerDay=0;

                    boolean sourceOutOk=false;
                    for(ConnectionEdge edgeOut:graph.outgoingEdgesOf(source)){
                        if(edgeOut.target.day==source.day+1)nbDayPerDay++;
                    }
                    if(nbDayPerDay==1)sourceOutOk=true;

                    boolean targetInOk=false;
                    nbDayPerDay=0;
                    for(ConnectionEdge edgeOut:graph.incomingEdgesOf(target)){
                        if(edgeOut.source.day==target.day-1)nbDayPerDay++;
                    }
                    if(nbDayPerDay==1)targetInOk=true;

                    boolean surfaceHourIsFuzzy=false;
                    double v0=source.nPixels/(source.hourGuessedOfTip-source.hourGuessedOfStart+VitimageUtils.EPSILON);
                    double v1=target.nPixels/(target.hourGuessedOfTip-target.hourGuessedOfStart+VitimageUtils.EPSILON);
                    if(v1>v0*ALPHA_FUZZY_DAYPERDAY_CONNECTION)surfaceHourIsFuzzy=true;
                    if(v0>v1*ALPHA_FUZZY_DAYPERDAY_CONNECTION)surfaceHourIsFuzzy=true;
                    if(sourceOutOk && targetInOk && (!surfaceHourIsFuzzy)){
                        edgesToActivate.add(edge);
                    }
                }
            }
        
        }

        for(ConnectionEdge edge:edgesToActivate){
            edge.activated=true;
            edge.stepOfActivation=2;
            //Compter le nombre d edges activés dont edge.activated.target est la target
            int nbActivatedIncomingOfTarget=0;
            for(ConnectionEdge e:graph.incomingEdgesOf(edge.target)){
                if(e.activated)nbActivatedIncomingOfTarget++;
            }
            if(nbActivatedIncomingOfTarget>1){
                for(int i=0;i<1000;i++);
                System.out.println("Warning : validated target "+edge.target+" of validated edge "+edge+" has already "+(nbActivatedIncomingOfTarget-1)+" activated incoming edges.");
            }
            // Add hint vectors
            edge.hintVector=new double[]{edge.target.xCentralPixAbsolu-edge.connectionX, edge.target.yCentralPixAbsolu-edge.connectionY};
            edge.hintDistance=Math.sqrt(Math.pow(edge.hintVector[0], 2) + Math.pow(edge.hintVector[1], 2));
            edge.hintVector=new double[]{edge.hintVector[0]/edge.hintDistance, edge.hintVector[1]/edge.hintDistance};
            edge.cost=0;
        }
    }

    public static void buildStep3SetCostsWithNoSpeedInformation(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, PipelineParamHandler pph) {
        double costTotal=0;
        double nbConnexions=0;
        for(ConnectionEdge edge : graph.edgeSet()){
            CC source=graph.getEdgeSource(edge);
            CC target=graph.getEdgeTarget(edge);
            // Compute cost of connection between source and target
            
            double cost=computeAndSetCostOfNonHiddenEdgeWithoutVelocityInformation(edge);
            if(Double.isNaN(cost)){
                System.out.println("Strange cost = "+cost+"for connection between "+source+" and "+target);
                continue;
            }
            costTotal+=cost;
            nbConnexions++;
        }
        System.out.println("End of step 3 (first cost computation). Nb of vertices : " + graph.vertexSet().size() + ", nb of edges : " + graph.edgeSet().size());
        System.out.println("Average cost of connexions : " + (costTotal/nbConnexions) + " , nb connexions : " + nbConnexions);
        System.out.println();
    }


    public static void buildStep4SetBestIncomingExceptForTheOnesAlreadyActivatedThatHaveBeenFormallyValidatedEarlier(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, PipelineParamHandler pph) {
        for(CC cc:graph.vertexSet()){
            /*boolean debug=(cc==getCCWithResolution(graph,6300,2253));
            if(debug){
                System.out.println("\nConsidering CC "+cc);
                System.out.println("With bestIncomingactivatedEdge = "+cc.bestIncomingActivatedEdge());
                //Displaying all the incoming edges, with their cost
                for(ConnectionEdge edgeTmp:graph.incomingEdgesOf(cc)){
                    System.out.println(" -> incoming edge from "+edgeTmp.source+" with cost "+edgeTmp.cost+" , activated = "+edgeTmp.activated);
                }
                System.out.println("And so best incoming edge = "+cc.bestIncomingEdge()); 
                VitimageUtils.waitFor(1000000000);
            }*/
            if(cc.bestIncomingActivatedEdge()!=null)continue;
            ConnectionEdge edge=cc.bestIncomingEdge();
            if(edge==null)continue;
            edge.activated=true;
            edge.stepOfActivation=4;
        }
        return;
    }

    public static void waitFor(int ms){
       VitimageUtils.waitFor(ms);
    }

    public static double dou(double d){
        return VitimageUtils.dou(d);
    }   

    public static double doudou(double d){
        if(d<0)return (-dou(-d));
		if (d<0.01)return 0;
		return (double)(Math.round(d * 100)/100.0);
	}

    public static double[] MADeStatsDoubleSidedWithEpsilon(double[]tabTmp) {
        double[] tab=new double[tabTmp.length];
        for(int i=0;i<tabTmp.length;i++){
            tab[i]=tabTmp[i]+VitimageUtils.EPSILON*i;
        }
        return VitimageUtils.MADeStatsDoubleSided(tab,null);
    }
   

    public static double[] getSpeedStatisticsOfNearestNeighbours(double logS0,double logS0OverS1){
        int nSelected=20;
        ArrayList<double[]> vals = new ArrayList<>();
        double[]valsSel = new double[nSelected];
        for(int i=0;i<statsAllFirstEdges.size();i++){
            double[] tab = statsAllFirstEdges.get(i);
            double d0 = tab[0]-logS0;
            double d1 = tab[1]-logS0OverS1;
            double dist = Math.sqrt(d0*d0+d1*d1);
            vals.add(new double[]{i,dist});
        }

        for(int i=0;i<nSelected;i++){
            double minDist=Double.MAX_VALUE;
            int indexMin=-1;
            for(int j=i;j<vals.size();j++){
                if(vals.get(j)[1]<minDist){
                    minDist=vals.get(j)[1];
                    indexMin=j;
                }
            }
            valsSel[i]=statsAllFirstEdges.get((int)Math.round(vals.get(indexMin)[0]))[2];
            vals.get(indexMin)[1]=Double.MAX_VALUE;
        }

        /*Slow
        Collections.sort(vals, new Comparator<double[]>() {
            @Override
            public int compare(double[] o1, double[] o2) {
                return Double.compare(o1[1], o2[1]);
            }
        }); 
        double[]valsSel=new double[nSelected];
        for(int i=0;i<nSelected;i++){
            valsSel[i]=statsAllFirstEdges.get((int)Math.round(vals.get(i)[0]))[2];
        }*/
        double[] stats=MADeStatsDoubleSidedWithEpsilon(valsSel);
        return stats;
    }


    public static void buildStep5IdentifyPossibleHiddenConnections(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, PipelineParamHandler pph) {
        System.out.print("Identifying hidden connexions and setting their costs ");
        int tot=0;
        int tot_AFTER_CONJ_G=0;
        double costTot=0;
        double nbConnexions=0;
        for (CC cc1 : graph.vertexSet()) {
            tot++;
            if(cc1.day==0)continue;
            if(USE_CONJONCTURE_G_DO_NOT_TRY_TO_FIND_HIDDEN_EDGES_EMERGING_FROM_ISOLATED_TIPS_WITH_ONLY_A_POSSIBLE_SOURCE_AND_A_POSSIBLE_TARGET && graph.outgoingEdgesOf(cc1).size()<=1 && graph.incomingEdgesOf(cc1).size()<=1)continue;
            tot_AFTER_CONJ_G++;
            //System.out.println("Processing vertex " + (++nVertexDone) + " / " + nVertexToTest + " , nb edges tested " + nEdgesTested + " , nb edges added " + nEdgesAdded);
            // For each cc1, perform a Dijkstra search to all other CCs to find possible hidden connections
            // Only follow paths through CCs that are connected (share an edge) and whose day <= cc1.day
            int dayMax=cc1.day;
            Map<CC, Integer> minSteps = new HashMap<>();
            Map<CC, CC> previousCC = new HashMap<>();
            Map<CC, ConnectionEdge> previousEdge = new HashMap<>();
            ArrayList<CC>reachedCC=new ArrayList<>();
            LinkedList<CC> queue = new LinkedList<>();
            minSteps.put(cc1, 0);
            queue.add(cc1);
            while (!queue.isEmpty()) {
                CC current = queue.poll();
                int steps = minSteps.get(current);
                int newSteps = steps + 1;
                for (ConnectionEdge edge : graph.outgoingEdgesOf(current)) {
                    if(edge.target.day==0)continue;
                    if(edge.hidden)continue;
                    CC neighbor = edge.target;
                    if (neighbor.day > dayMax){
                        minSteps.put(neighbor, newSteps);
                        previousCC.put(neighbor, current);
                        previousEdge.put(current, edge);
                        reachedCC.add(neighbor);
                        continue;
                    } 
                    if (!minSteps.containsKey(neighbor) || newSteps < minSteps.get(neighbor)) {
                        minSteps.put(neighbor, newSteps);
                        queue.add(neighbor);
                        previousCC.put(neighbor, current);
                        previousEdge.put(current, edge);
                        if(neighbor.day==dayMax)reachedCC.add(neighbor);
                    }
                }
                for (ConnectionEdge edge : graph.incomingEdgesOf(current)) {
                    if(edge.source.day==0)continue;
                    if(edge.hidden)continue;
                    CC neighbor = edge.source;
                    if (neighbor.day > dayMax){
                        minSteps.put(neighbor, newSteps);
                        previousCC.put(neighbor, current);
                        previousEdge.put(current, edge);
                        reachedCC.add(neighbor);
                        continue;
                    } 
                    if (!minSteps.containsKey(neighbor) || newSteps < minSteps.get(neighbor)) {
                        minSteps.put(neighbor, newSteps);
                        queue.add(neighbor);
                        previousCC.put(neighbor, current);
                        previousEdge.put(current, edge);
                        if(neighbor.day==dayMax)reachedCC.add(neighbor);
                    }
                }
            }
            // minSteps now contains the minimal number of CC traversed from cc1 to each reachable CC
            //System.out.println("For CC " + cc1 + " reached " + reachedCC.size() + " CCs");


            for(CC cc2:reachedCC){
                if(cc2.day==0)continue;
                if(cc1==cc2)continue;
                if(cc1.day>cc2.day)continue;

                ArrayList<CC> path = new ArrayList<>();
                CC current = cc2;
                while (current != null && current != cc1) {
                    path.add(current);
                    current = previousCC.get(current);
                }
                path.add(cc1); // Ajoute le départ
                Collections.reverse(path); // Pour avoir le chemin dans l'ordre cc1 -> cc2

                /*double cost=computeCostOfTwoDisconnectedCC(cc1, cc2, path,pph.getTypicalSpeed(),graph);
                if(Double.isNaN(cost)){
                    System.out.println("Strange NaN cost for connection between "+cc1+" and "+cc2+" through path "+path);
                    continue;
                }   
                if(cost>HUGE_COST)cost=HUGE_COST;
                */
                ConnectionEdge edge=new ConnectionEdge(-1,-1,-1,cc1,cc2,-1,-1);
                edge.hidden=true;
                edge.pathOfCC=path;
                /*edge.cost=cost;
                costTot+=cost;
                */
                nbConnexions++;
                graph.addEdge(cc1, cc2, edge);
            }
        }
        System.out.println("Info : Nb of vertices tested for hidden connexions : "+tot+" , after conjecture G : "+tot_AFTER_CONJ_G+"\n");
        System.out.println("End of step 2 . Nb of vertices : " + graph.vertexSet().size() + ", nb of edges : " + graph.edgeSet().size());
        System.out.println("Average cost of hidden connexions : " + (costTot/nbConnexions) + " , nb connexions : " + nbConnexions);
    }

    public static void buildStep3SetCostsWithSpeedInformation(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, PipelineParamHandler pph) {
        ArrayList<ConnectionEdge> listEdges=new ArrayList<>();
        for(CC cc:graph.vertexSet()){
            ConnectionEdge edge=cc.bestIncomingActivatedEdge();
            if(edge!=null){
                listEdges.add(edge);
                continue;
            }
            edge=cc.bestIncomingEdge();
            if(edge!=null)listEdges.add(edge);
        }
        for(ConnectionEdge edge:listEdges){
           double speed=computeVelocityOfNonHiddenEdgeInPixPerHour(edge,pph);
           double surfSourcePerHour=edge.source.nPixels/(edge.source.hourGuessedOfTip-edge.source.hourGuessedOfStart+(edge.source.hourGuessedOfTip==edge.source.hourGuessedOfStart?pph.typicalHourDelay/2.0:VitimageUtils.EPSILON));
           double surfTargetPerHour=edge.target.nPixels/(edge.target.hourGuessedOfTip-edge.target.hourGuessedOfStart+(edge.target.hourGuessedOfTip==edge.target.hourGuessedOfStart?pph.typicalHourDelay/2.0:VitimageUtils.EPSILON));
           double surfSourceOverTarget=surfSourcePerHour/surfTargetPerHour;
          statsAllFirstEdges.add(new double[]{Math.log(surfSourcePerHour),Math.log(surfSourceOverTarget),Math.log(speed)});
        }



        double costTotal=0;
        double nbConnexions=0;
        int nn=graph.edgeSet().size();
        int count=0;
    
        System.out.println("Processing all edges to set their costs with velocity information...over "+nn+" edges");
        for(ConnectionEdge edge : graph.edgeSet()){
            count++;
            if(count%(nn/10)==0)System.out.print("  "+count);
            CC source=graph.getEdgeSource(edge);
            CC target=graph.getEdgeTarget(edge);

            // Compute cost of connection between source and target
            double cost=computeAndSetCostOfEdgeWithVelocityInformation(edge,graph,pph);
            if(Double.isNaN(cost) || cost<0){
                System.out.println("Strange cost = "+cost+" for connection between "+source+" and "+target);
                cost=HUGE_COST;
                continue;
            }
            costTotal+=cost;
            nbConnexions++;
        }
        System.out.println("End of step 6 (second cost computation). Nb of vertices : " + graph.vertexSet().size() + ", nb of edges : " + graph.edgeSet().size());
        System.out.println("Average cost of connexions : " + (costTotal/nbConnexions) + " , nb connexions : " + nbConnexions);
        System.out.println();
    }
   

















    private static List<TreeCC> buildStep5PruneOutliersFirstPhase(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, PipelineParamHandler pph) {
/*        CC  Timestep 18 label62 : 992.0,337.0 (5952.0 - 2022.0) h=198.0 hStart=186.0 hCentroid=192.0 hTip=198.0 nPix=119 size=21x24
Toto 17 CC  Timestep 17 label75 : 1002.0,322.0 (6012.0 - 1932.0) h=186.0 hStart=174.0 hCentroid=180.0 hTip=186.0 nPix=53 size=12x12
Toto 16 CC  Timestep 16 label68 : 999.0,317.0 (5994.0 - 1902.0) h=174.0 hStart=162.0 hCentroid=168.0 hTip=174.0 nPix=82 size=17x15*/ 
    System.out.println("Adhoc modif for box 10 -> graph.removeEdge(getCCWithResolution(graph, 5175,1367).bestIncomingActivatedEdge());");
        //graph.removeEdge(getCCWithResolution(graph, 5175,1367).bestIncomingActivatedEdge());
        
        // Marquer les edges non activées comme exclus (mais ne pas les supprimer du graphe)
        int countExcludedEdges = 0;
        for(ConnectionEdge edge:graph.edgeSet()){
            if(!edge.activated) {
                edge.isOut = true;
                excludedEdgesNonActivated.add(edge);
                edgeExclusionReasons.put(edge, "NON_ACTIVATED_STEP5");
                countExcludedEdges++;
            }
        }
        System.out.println("Step 5: Marked " + countExcludedEdges + " non-activated edges as excluded (isOut=true)");

        //Identifier les organes principaux, par somme de surfaces en remontant de la fin, et generer la liste des points initiaux
        ArrayList<CC> localInitialPoints=new ArrayList<>();
        for(CC cc:graph.vertexSet()){
            if(cc.isOut) continue; // Ignorer les CC exclues
            if(cc.bestOutgoingActivatedCC()!=null)continue;//Pas une feuille
            CC ccTmp=cc;
            int countPix=0;
            int countSucc=0;
            while(ccTmp!=null){                    
                countPix+=ccTmp.nPixels;
                countSucc+=1;                
                ccTmp=ccTmp.bestIncomingActivatedCC();
                if(ccTmp!=null){
                    ccTmp.nbPixSuccessors+=countPix;ccTmp.nbSuccessors+=countSucc;
                }
            }
        }

        for(CC cc:graph.vertexSet()){
            if(cc.isOut) continue; // Ignorer les CC exclues
            if(cc.bestIncomingActivatedCC()==null){
                localInitialPoints.add(cc);
            }
        }
        
        // Sauvegarder dans la variable statique pour l'utiliser pendant les simulations
        initialPoints.clear();
        initialPoints.addAll(localInitialPoints);


         //Marquer les organes origine qui ont moins qu'un seuil minimum de nb de composantes ou un seuil minimum de surface totale
        ArrayList<CC>ccToMarkAsOut=new ArrayList<>();
        ArrayList<CC>ccToRemoveFromInitial=new ArrayList<>();
        for(CC cc:localInitialPoints){
            if(cc.nbSuccessors>=Utils.minNbCCPerPlant && (cc.nPixels+cc.nbPixSuccessors)>Utils.minSurfacePerPlant)continue;
            ccToRemoveFromInitial.add(cc);
            //Marquer tout le chemin comme exclus (mais ne pas le supprimer du graphe)
            //IMPORTANT: ne suivre QUE les edges activées pour ne pas contaminer d'autres arbres
            ArrayList<CC>ccToVisitList=new ArrayList<>();
            ccToVisitList.add(cc);
            ccToMarkAsOut.add(cc);
            while(ccToVisitList.size()>0){
                CC ccTmp=ccToVisitList.remove(0);
                if(!ccToMarkAsOut.contains(ccTmp))ccToMarkAsOut.add(ccTmp);
                for(ConnectionEdge edge:graph.outgoingEdgesOf(ccTmp)){
                    // NE SUIVRE QUE LES EDGES ACTIVÉES !
                    if(!edge.activated) continue;
                    if(edge.target.isOut) continue; // Ne pas revisiter les déjà exclus
                    ccToVisitList.add(edge.target);
                    edge.isOut = true; // Marquer l'edge comme exclue aussi
                    edgeExclusionReasons.put(edge, "SMALL_TREE_STEP5");
                }
            }
        }

        //for(CC cc:localInitialPoints)System.out.println("Initial point : "+cc.day+"-"+cc.n+" Successors = "+cc.nbSuccessors+" , total surface = "+(cc.nPixels+cc.nbPixSuccessors));

        //for(CC cc:ccToRemoveFromInitial)System.out.println("Gonna mark as excluded : "+cc.day+"-"+cc.n+" Successors = "+cc.nbSuccessors+" , total surface = "+(cc.nPixels+cc.nbPixSuccessors));
        
        // Marquer les arbres complets comme exclus (mais ne pas les supprimer du graphe)
        System.out.println("Step 5: Marking " + ccToMarkAsOut.size() + " CC from small trees as excluded (isOut=true)");
        for(CC cc:ccToMarkAsOut) {
            excludedCCFullTrees.add(cc);
            cc.isOut = true;
            exclusionReasons.put(cc, "SMALL_TREE_STEP5_nbSucc=" + cc.nbSuccessors + "_surf=" + (cc.nPixels+cc.nbPixSuccessors));
        }
        
        // Retirer uniquement de la liste des points initiaux (mais garder dans le graphe)
        localInitialPoints.removeAll(ccToRemoveFromInitial);
        
        // Mettre à jour la variable statique avec la liste finale
        initialPoints.clear();
        initialPoints.addAll(localInitialPoints);
        System.out.println("End of stepA. Initial points remaining : "+localInitialPoints.size());
        
        // Afficher un résumé des exclusions
        printExclusionSummary();
        
        //Definir les ordres 1 : c'est en partant de la racine le chemin en prenant le plus lourd de proche en proche. Puis les autres ordres
        //Pour chaque plante, attribuer à chaque composante du chemin principal un code d'organe principale et un ordre 1
       
        //Racines d'ordre 1
        for(CC cc:localInitialPoints.stream().sorted(Comparator.comparingDouble(c -> c.xCentralPixAbsolu)).collect(Collectors.toList())){
            int i=localInitialPoints.indexOf(cc);
            CC ccTmp=cc;
            CC ccLast=ccTmp;
            ccTmp.indexOfCorrespondingPlant=i;
            ccTmp.indexInOrgan=0;
            ccTmp.order=1;
            
            // Créer le noeud racine de l'arbre TreeCC pour cette plante
            TreeCC rootNode = new TreeCC(ccTmp, i, 1, 0, true, true);
            treeRoots.add(rootNode);
            ccToTreeMap.put(ccTmp, rootNode);
            TreeCC lastTreeNode = rootNode;

            while(ccTmp!=null){
                int maxVal=-1;
                CC ccBestNext=null;
                for(CC ccNext:graph.outgoingEdgesOf(ccTmp).stream().map(e->e.target).collect(Collectors.toList())){
                    if(ccNext.nbPixSuccessors>maxVal){
                        maxVal=ccNext.nbPixSuccessors;
                        ccBestNext=ccNext;
                    }
                }
                if(ccBestNext==null)break;
                               
                //Si la distance entre ccTmp.x , ccTmp.y et (1000,330) est inférieure à 20 pixels, on affiche ccTmp;
                //System.out.println(ccTmp);
                ccLast=ccTmp;
                ccTmp=ccBestNext;
                ccTmp.indexInOrgan=ccLast.indexInOrgan+1;
                ccTmp.indexOfCorrespondingPlant=i;
                ccTmp.order=1;
                ccTmp.indexOfCorrespondingOrganRelativeToParentStructure.add(new Integer(0));
                
                // Ajouter ce CC au TreeCC
                TreeCC newTreeNode = new TreeCC(ccTmp, i, 1, ccTmp.indexInOrgan, false, true);
                lastTreeNode.addChild(newTreeNode);
                ccToTreeMap.put(ccTmp, newTreeNode);
                lastTreeNode = newTreeNode;
            }
        }
        //Racines d'ordre 2 et plus
        for(int order=2;order<Utils.MAX_ORDER_ROOTS;order++){
            for(CC cc:graph.vertexSet().stream().sorted(Comparator.comparingDouble(cc -> cc.yCentralPixAbsolu)).collect(Collectors.toList())){
                if(cc.isOut) continue; // Ignorer les CC exclues
                CC ccTmp=cc;
                if(ccTmp.order!=0)continue;
                if(ccTmp.bestIncomingActivatedCC()==null){
                    System.out.println("Bug ici. Order = "+order+" , cc = "+ccTmp+" , best incoming activated = null");
                }
                if(ccTmp.bestIncomingActivatedCC().order!=order-1)continue;
               
                CC ccLast=ccTmp.bestIncomingActivatedCC();
                ccTmp.order=order;
                ccTmp.indexOfCorrespondingPlant=ccLast.indexOfCorrespondingPlant;
                ccTmp.indexInOrgan=0;
                
                // Créer le nœud TreeCC pour ce début d'organe latéral
                TreeCC parentTreeNode = ccToTreeMap.get(ccLast);
                TreeCC newOrganStart = new TreeCC(ccTmp, ccLast.indexOfCorrespondingPlant, order, 0, true, false);
                if(parentTreeNode != null) {
                    parentTreeNode.addChild(newOrganStart);
                }
                ccToTreeMap.put(ccTmp, newOrganStart);
                TreeCC lastTreeNode = newOrganStart;

                while(ccTmp!=null){
                    int maxVal=-1;
                    CC ccBestNext=null;
                    
                    for(CC ccNext:graph.outgoingEdgesOf(ccTmp).stream().filter(e->e.activated).map(e->e.target).collect(Collectors.toList())){
                        if(ccNext.nbPixSuccessors>maxVal){
                            maxVal=ccNext.nbPixSuccessors;
                            ccBestNext=ccNext;
                        }
                    }
                    if(ccBestNext==null)break;
                    ccLast=ccTmp;
                    ccTmp=ccBestNext;
                    ccTmp.indexInOrgan=ccLast.indexInOrgan+1;
                    ccTmp.indexOfCorrespondingPlant=ccLast.indexOfCorrespondingPlant;
                    ccTmp.order=order;
                    
                    // Ajouter ce CC au TreeCC
                    TreeCC newTreeNode = new TreeCC(ccTmp, ccTmp.indexOfCorrespondingPlant, order, ccTmp.indexInOrgan, false, false);
                    lastTreeNode.addChild(newTreeNode);
                    ccToTreeMap.put(ccTmp, newTreeNode);
                    lastTreeNode = newTreeNode;
                }
            }
        }
        
        // Afficher les statistiques des arbres construits
        System.out.println("\n=== TreeCC Construction Summary ===");
        System.out.println("Number of root trees (plants): " + treeRoots.size());
        for(int i = 0; i < treeRoots.size(); i++) {
            TreeCC root = treeRoots.get(i);
            System.out.println("Plant " + i + ": " + root.getNodeCount() + " nodes, " 
                + root.getAllLeaves().size() + " leaves, " 
                + root.getTotalPixelCount() + " total pixels");
        }
        System.out.println("===================================\n");
        
        // Validation de la cohérence des structures TreeCC après construction step 5
        System.out.println("Step 5: Checking TreeCC consistency...");
        int step5Inconsistencies = validateTreeCCConsistency(graph);
        if (step5Inconsistencies > 0) {
            System.out.println("!!! WARNING: Found " + step5Inconsistencies + " TreeCC inconsistencies after step 5 construction !!!");
        } else {
            System.out.println("Step 5: TreeCC consistency check OK");
        }

        // Retourner la liste des arbres racines
        return treeRoots;



    }








  /**
     * -------------------------------------------------------------------------------------------------------------------------------------------
      * -------------------------------------------------------------------------------------------------------------------------------------------
     * Cost computing functions and helpers *************************************************************************************
      * -------------------------------------------------------------------------------------------------------------------------------------------
     * -------------------------------------------------------------------------------------------------------------------------------------------
     */
    //This one is call at very start, for setting initial parameters (estimate velocity especially)
    public static double computeAndSetCostOfNonHiddenEdgeWithoutVelocityInformation(ConnectionEdge edge){
        boolean debug=false;
        CC source=edge.source;
        CC target=edge.target;
        if (source.day == 0) return (HUGE_COST);
        if (source.day > target.day) return (HUGE_COST);
        if(source==target)return(HUGE_COST);

        double[]vectTest=new double[]{0,1};
        if (edge.hintVector!=null)vectTest=edge.hintVector;
        double totalCost=
                    MAX_COST_TIME*time_cost(source.day,target.day)                +
                    MAX_COST_CON*0   /* They are connected */                     +
                    MAX_COST_SPACE*0  /* They are connected */                    +
                    MAX_COST_ORIENT*orientation_cost(
                        vectTest,
                        edge)+
                    MAX_COST_SURF*surface_cost(source, target);
        if(totalCost>HUGE_COST)totalCost=HUGE_COST;
        if(debug){
            System.out.println("\n\nComputing cost to connect "+source+" to "+target+" :");
            System.out.println("Time cost = "+time_cost(source.day,target.day)+" * "+MAX_COST_TIME);
            System.out.println("Conn cost = 0 * "+MAX_COST_CON);
            System.out.println("Dist cost = 0 * "+MAX_COST_SPACE);
            System.out.println("Orient cost = "+orientation_cost(
                new double[]{0,1},
                edge)+" * "+MAX_COST_ORIENT);
            System.out.println("Surf cost = "+surface_cost(source, target)+" * "+MAX_COST_SURF);
            System.out.println("TOTAL COST = "+totalCost);
        }   
        edge.cost=totalCost;
        return totalCost;
    }

    //Then this one is called for every edges
    public static double computeAndSetCostOfEdgeWithVelocityInformation(ConnectionEdge edge, SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, PipelineParamHandler pph){
        CC source=edge.source;
        CC target=edge.target;
        boolean debug=false && (target==getCCWithResolution(graph, 4275, 3326)) && (source==getCCWithResolution(graph,  4387,3135)||source==getCCWithResolution(graph, 4393, 3040));

        ArrayList<CC> identifiedPossiblePath=edge.pathOfCC;
        if (source.day == 0) return (HUGE_COST);
        if (source.day > target.day) return (HUGE_COST);
        if(source==target)return(HUGE_COST);

        double totalCost=
                    MAX_COST_TIME*time_cost(source.day,target.day)           +
                    MAX_COST_CON * (edge.hidden ? connectivity_cost(identifiedPossiblePath) : 0  )                                        +
                    MAX_COST_SPACE*distance_cost(edge,graph,pph)                 +
                    MAX_COST_ORIENT*orientation_cost(new double[]{0,1},edge) +
                    MAX_COST_SURF*surface_cost(source, target);
        if(totalCost>HUGE_COST)totalCost=HUGE_COST;
        // if(totalCost>3)debug=false;
        if(debug){
            System.out.println("\n\nComputing cost to hidden connect \n->"+source+" to\n "+target+" :");
            System.out.println("Time cost = "+time_cost(source.day,target.day)+" * "+MAX_COST_TIME);
            System.out.println("Conn cost = "+(edge.hidden ? connectivity_cost(identifiedPossiblePath) : 0  ) +" * "+MAX_COST_CON);
            System.out.println("Dist cost = "+distance_cost(edge,graph,pph)+" * "+MAX_COST_SPACE);
            System.out.println("Orient cost = "+orientation_cost(new double[]{0,1},edge));
            System.out.println("Surf cost = "+surface_cost(source, target)+" * "+MAX_COST_SURF);
            System.out.println("TOTAL COST = "+totalCost);
        }   
        edge.cost=totalCost;
        //if(debug)VitimageUtils.waitFor(60000000);
        return totalCost;
    }


    public static double estimatedSurfacicGrowthPerHour(CC cc, PipelineParamHandler pph) {
        double surfPerHour=cc.nPixels/(cc.hourGuessedOfTip-cc.hourGuessedOfStart + (cc.hourGuessedOfTip==cc.hourGuessedOfStart?pph.typicalHourDelay:VitimageUtils.EPSILON));
        return surfPerHour;
    }

    public static double edgeSpeedPerHourInTarget(ConnectionEdge edge, SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph,PipelineParamHandler pph) {
        return Utils.distance(edge.target.xCentralPixAbsolu,edge.target.yCentralPixAbsolu,edge.connectionX,edge.connectionY)/((edge.target.hourGuessedOfCentroid-edge.target.hourGuessedOfStart)+(edge.target.hourGuessedOfCentroid==edge.target.hourGuessedOfStart?pph.typicalHourDelay/2.0:VitimageUtils.EPSILON));
    }


    public static double distance_cost(ConnectionEdge edge, SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph,PipelineParamHandler pph) {
        double S0perHour=edge.source.nPixels/(edge.source.hourGuessedOfTip-edge.source.hourGuessedOfStart + (edge.source.hourGuessedOfTip==edge.source.hourGuessedOfStart?pph.typicalHourDelay:VitimageUtils.EPSILON));
        double S1perHour=edge.target.nPixels/(edge.target.hourGuessedOfTip-edge.target.hourGuessedOfStart + (edge.target.hourGuessedOfTip==edge.target.hourGuessedOfStart?pph.typicalHourDelay:VitimageUtils.EPSILON));
        double S0overS1=S0perHour/(VitimageUtils.EPSILON+S1perHour);
        boolean debug=(edge.target==getCCWithResolution(graph, 4275, 3326)) && (edge.source==getCCWithResolution(graph,  4387,3135)||edge.source==getCCWithResolution(graph, 4393, 3040));
        double logS0=Math.log(S0perHour+VitimageUtils.EPSILON);
        double logS0overS1=Math.log(S0overS1+VitimageUtils.EPSILON);

//        Timer tt=new Timer();
//        tt.print("\nstart");
        double []stats=getSpeedStatisticsOfNearestNeighbours(logS0,logS0overS1); //getSpeedStats(logS0,logS0overS1);
//        tt.print("stop1");
        double speedEdge=0;
        if(edge.hidden){
            speedEdge=estimateDistanceInCCPathFromCentroidToCentroid(edge.pathOfCC,graph)/((edge.target.hourGuessedOfCentroid-edge.source.hourGuessedOfCentroid)+(edge.target.hourGuessedOfCentroid==edge.source.hourGuessedOfCentroid?pph.typicalHourDelay/2.0:VitimageUtils.EPSILON));
        }
        else{
            speedEdge=Utils.distance(edge.target.xCentralPixAbsolu,edge.target.yCentralPixAbsolu,edge.connectionX,edge.connectionY)/((edge.target.hourGuessedOfCentroid-edge.target.hourGuessedOfStart)+(edge.target.hourGuessedOfCentroid==edge.target.hourGuessedOfStart?pph.typicalHourDelay/2.0:VitimageUtils.EPSILON));
        }
//        tt.print("stop2");
        double logSpeedEdge=Math.log(speedEdge+VitimageUtils.EPSILON);

        if(debug)System.out.println("Edge identified logS0="+logS0+"  logS0overS1="+logS0overS1+"     Logspeed of edge = "+logSpeedEdge+"  median="+stats[0]+"  mad-="+stats[1]+"  mad+="+stats[2]);
//        tt.print("stop3");

        return MADeCost(logSpeedEdge,stats,ALPHA_MAD_COST_DIST);
    }

    public static double[]getSpeedStats(double queryLogS0,double queryLogS0overS1){
         //Get the stats
        boolean debug=false;
        if(debug)System.out.println();
        if(debug)System.out.println("Debug getSpeedStats for queryLogS0="+queryLogS0+"  queryLogS0overS1="+queryLogS0overS1);
        int c1=-1;
        int c2=-1;
        for(int c=0;c<nCategoriesSpeedStats;c++){
            if(debug)System.out.println("-testing S0. Cat "+c+" : max="+maxCatS0[c]+" for query="+queryLogS0);
            if(queryLogS0<maxCatS0[c]){
                if(debug)System.out.println("---> found the corresponding cat "+c);
                c1=c;
                break;
            }
        }
        for(int c=0;c<nCategoriesSpeedStats;c++){
            if(debug)System.out.println("--testing S1OverS0. Cat "+c+" : max="+maxCatS0OverS1[c1][c]+" for query="+queryLogS0overS1);
            if(queryLogS0overS1<maxCatS0OverS1[c1][c]){
                if(debug)System.out.println("----> found the corresponding cat "+c);
                c2=c;
                break;
            }
        }
        if(debug)System.out.println("  -> category for logS0 = "+c1+" , category for logS0overS1 = "+c2);
        //if(debug)VitimageUtils.waitFor(100000);
        return statsOfCategories[c1][c2];
    }

    public static double MADeCost(double val,double[] stats,double alphaMAD){
        double median=stats[0];
        double madMinus=(stats[0]-stats[1]+VitimageUtils.EPSILON)*MAD_E_FACTEUR_CORRECTIF;
        double madPlus=(stats[2]-stats[0]+VitimageUtils.EPSILON)*MAD_E_FACTEUR_CORRECTIF;
        double diff=Math.abs(val-median);
        double nStd=0;
        if(val<median)nStd=(diff/(madMinus));
        else nStd=(diff/(madPlus));
        return (1-Math.exp(-nStd*alphaMAD/2));
    }

     public static double dissimilarity_cost(double d1,double d2) {
		double diff=Math.abs(d1-d2);
		double sum=(d1+d2);
		return diff/sum;
	}

    public static double computeVelocityOfNonHiddenEdgeInPixPerHour(ConnectionEdge edge, PipelineParamHandler pph){
        double dx=edge.target.xCentralPixAbsolu-edge.connectionX;
        double dy=edge.target.yCentralPixAbsolu-edge.connectionY;
        double dist=Math.sqrt(dx*dx+dy*dy);
        double dt=edge.target.hourGuessedOfCentroid-edge.target.hourGuessedOfStart+(edge.target.hourGuessedOfCentroid==edge.target.hourGuessedOfStart?pph.typicalHourDelay/2.0:VitimageUtils.EPSILON);
        return dist/dt;
    }
 
    public static double estimateDistanceInCCPathFromCentroidToCentroid(ArrayList<CC> path,SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph) {
        //TODO : can be changed into a new function that just aggregate the pixels, compute the pixelGraph, and then Dijkstra in it. What will be missing will be to avoid the borders in turns
        /* 
        CC ccFuse = CC.fuseListOfCCIntoSingleCC(path);
        int x0=path.get(0).xSafeAbsolu-ccFuse.xB;
        int y0=path.get(0).ySafe-ccFuse.yB;
        int x1=path.get(path.size()-1).xSafe-ccFuse.xB;
        int y1=path.get(path.size()-1).ySafe-ccFuse.yB;
        List<Pix>listPix=ccFuse.determineVoxelShortestPath(new int[]{x0,y0}, new int[]{x1,y1}, 8, null);
        return CC.euclidianlenghtOfPixPath(listPix);
    }   */
        ConnectionEdge edge=graph.getEdge(path.get(0), path.get(1));
        if(edge==null || edge.hidden){
            edge=graph.getEdge(path.get(1), path.get(0));
        }
        double x0=edge.connectionX;
        double y0=edge.connectionY;
        double x1=path.get(0).xCentralPixAbsolu;
        double y1=path.get(0).yCentralPixAbsolu;
        double dist=Utils.distance(x0, y0, x1, y1);
        for(int i=1;i<path.size()-1;i++){
            x0=x1;
            y0=y1;
            edge=graph.getEdge(path.get(i), path.get(i+1));
            if(edge==null|| edge.hidden){
                edge=graph.getEdge(path.get(i+1), path.get(i));
            }   
            x1=edge.connectionX;
            y1=edge.connectionY;
            dist+=Utils.distance(x0, y0, x1, y1);
        }
        x0=x1;
        y0=y1;
        x1=path.get(path.size()-1).xCentralPixAbsolu;
        y1=path.get(path.size()-1).yCentralPixAbsolu;
        dist+=Utils.distance(x0, y0, x1, y1);
        return dist;
    }
    
    

   public static double orientation_cost(double[] vect1, ConnectionEdge edge) {
        double[] vect2 = edge.hidden ?
                     new double[]{edge.target.xCentralPixAbsolu - edge.source.xCentralPixAbsolu+VitimageUtils.EPSILON, edge.target.yCentralPixAbsolu - edge.source.yCentralPixAbsolu+VitimageUtils.EPSILON} 
                     : new double[]{edge.target.xCentralPixAbsolu - edge.connectionX+VitimageUtils.EPSILON, edge.target.yCentralPixAbsolu - edge.connectionY+VitimageUtils.EPSILON};
        vect1 = TransformUtils.normalize(vect1);
        vect2 = TransformUtils.normalize(vect2);
        double val=(1 - TransformUtils.scalarProduct(vect1, vect2)) / 2.0;
        return (val*val);
    }

    public static double surface_cost(CC cc1,CC cc2) {
        return dissimilarity_cost(cc1.nPixels, cc2.nPixels);
    }

    public static double time_cost(int d1,int d2) {
        double diff=d2-d1;
        if(diff<0)return HUGE_COST;
        diff=diff-1;
        if(diff<0)diff=-diff;
        return (1-1/(1+ALPHA_DELTA_TIME*diff));
    }
    
    public static double connectivity_cost(ArrayList<CC> path) {
        int nbIntermediates=path.size()-2;
        if(nbIntermediates<0)return HUGE_COST;
        if(nbIntermediates==0)return 0;
        return (1-1/(1+ALPHA_CON*nbIntermediates));
    }









         /**
     * -------------------------------------------------------------------------------------------------------------------------------------------
      * -------------------------------------------------------------------------------------------------------------------------------------------
     * Post processing : pruning and analysis of the arborescence to avoid outliers *************************************************************************************
      * -------------------------------------------------------------------------------------------------------------------------------------------
     * -------------------------------------------------------------------------------------------------------------------------------------------
     */
    /**
     * Reconstructs a RootModel from a graph of connected components (CCs).
     * - Removes excluded components.
     * - Identifies trunk, primary, and lateral root features.
     * - For each primary root, traces its path, computes distances and interpolates time values.
     * - For each lateral root, finds its associated primary root, traces its path, and computes distances/times.
     * - Subsamples root paths for simplification and adds nodes to the model.
     * - Attaches lateral roots to their primary roots and finalizes root ordering.
     * Returns the completed RootModel with all roots and their spatial/temporal data.
     */
    public static RootModel buildStep9RefinePlongement(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph,
                                                      /*ImagePlus notUseddistOut,*/ PipelineParamHandler pph, int indexImg) {
        System.out.println("Running the plongement");
        

        boolean simplerSimplify = false;
        boolean debugPrim = false;
        boolean debugLat = false;
        double toleranceDistToCentralLine = pph.toleranceDistanceForBeuckerSimplification;

        double[] hoursExtremities = pph.getHoursExtremities(indexImg);
        hoursExtremities[0] = hoursExtremities[1];
        ArrayList<Root> listRprim = new ArrayList<Root>();
        ArrayList<Integer> listNprim = new ArrayList<Integer>();
        ArrayList<Integer> listDprim = new ArrayList<Integer>();
        ArrayList<ArrayList<Root>> listRlat = new ArrayList<ArrayList<Root>>();
        ArrayList<ArrayList<Integer>> listNlat = new ArrayList<ArrayList<Integer>>();
        ArrayList<ArrayList<Integer>> listDlat = new ArrayList<ArrayList<Integer>>();
        for(int i=0;i<Utils.MAX_ORDER_ROOTS;i++){
            listRlat.add(new ArrayList<Root>());
            listNlat.add(new ArrayList<Integer>());
            listDlat.add(new ArrayList<Integer>());
        }

        
        //Prepare output data storage
        RootModel rm = new RootModel();
        rm.pixelSize = (float) (pph.originalPixelSize * pph.subsamplingFactor);
        rm.setHoursFromPph(pph.getHoursExtremities(indexImg));


        //Exclude cc that were outed
        ArrayList<CC> cctoExclude = new ArrayList<CC>();
        for (CC cc : graph.vertexSet()) if (cc.isOut) cctoExclude.add(cc);
        for (CC cc : cctoExclude) graph.removeVertex(cc);


        //Identify some features of vertices
        for (CC cc : graph.vertexSet()) {
            if (cc.order==1){ 

                if (cc.bestIncomingActivatedCC()==null) cc.isPrimStart = true;
                if (cc.bestOutgoingActivatedCC() == null) cc.isPrimEnd = true;
            }
            if (cc.order>1) {
                if (cc.bestIncomingActivatedCC() != null && cc.bestIncomingActivatedCC().order<cc.order){
                 cc.isLatStart = true;
                 
                }
                if (cc.bestOutgoingActivatedCC() == null) cc.isLatEnd = true;
            }
        }


        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////// Primary
        //Extracting components of naturally connected CC for each primary root (excluding the hidden connections)
        for (CC cc : graph.vertexSet()) {
            if ((!cc.isPrimStart)) continue;
            if (debugPrim) System.out.println("\nProcessing a primary, starting with CC " + cc);
            CC ccNext = cc;
            ArrayList<ArrayList<CC>> llcc = new ArrayList<ArrayList<CC>>();
            ArrayList<ArrayList<Integer>> toKeep = new ArrayList<ArrayList<Integer>>();
            ArrayList<CC> lccFuse = new ArrayList<CC>();
            llcc.add(new ArrayList<CC>());
            llcc.get(0).add(ccNext);
            Root rPrim = new Root(null, rm, "", 1);
            listRprim.add(rPrim);
            listNprim.add(llcc.get(0).get(0).n);
            listDprim.add(llcc.get(0).get(0).day);
            int ind = 0;
            while (ccNext.getChild() != null) {
                if (ccNext.hasHiddenChild()) {
                    llcc.add(new ArrayList<CC>());
                    ind++;
                }
                ccNext = ccNext.getChild();
                listRprim.add(rPrim);
                listNprim.add(ccNext.n);
                listDprim.add(ccNext.day);
                llcc.get(ind).add(ccNext);
            }

            //removal of connected part of CCs that are exactly one CC but not the start or end)
            if (llcc.size() >= 3) {
                ArrayList<ArrayList<CC>> llccNew = new ArrayList<ArrayList<CC>>();
                llccNew.add(llcc.get(0));
                for (int i = 1; i < (llcc.size() - 1); i++) {
                    if (llcc.get(i).size() > 1) {
                        llccNew.add(llcc.get(i));
                    }
                }
                llccNew.add(llcc.get(llcc.size() - 1));
                llcc = llccNew;
            }

            //Identification of components of hidden (meaning, chains of CC connected by hidden edges strictly) within the chain of .getChild(), starting from cc, and finishing with the last child
            ccNext=cc;
            ArrayList<ArrayList<CC>> llccHidden = new ArrayList<ArrayList<CC>>();
            ArrayList<CC>lccHiddenFuse=new ArrayList<CC>();
            while (ccNext.getChild() != null) {
                if (ccNext.hasHiddenChild()) {
                    //Start of hidden chain
                    ArrayList<CC> lccHidden = new ArrayList<CC>();
                    lccHidden.add(ccNext);
                    ccNext = ccNext.getChild();
                    while (ccNext != null && ccNext.hasHiddenParent()) {
                        lccHidden.add(ccNext);
                        if (ccNext.getChild() != null)ccNext = ccNext.getChild();
                        else break;
                    }
                    llccHidden.add(lccHidden);
                    CC ccFuseHidden = CC.fuseListOfCCIntoSingleCC(lccHidden);
                    lccHiddenFuse.add(ccFuseHidden);
                } else {
                    ccNext = ccNext.getChild();
                }
            }





            //Dijkstra path processing of the visible respective parts separated. Compute starting distance (when be for lateral)
            double startingDistance = 0;
            double cumulatedDistance = startingDistance;
            ArrayList<Double> distInter = new ArrayList<Double>();
            ArrayList<Double> timeInter = new ArrayList<Double>();
            int[] nextSourceAbs = null;
            int[] currentSourceAbs = null;
            int[] currentTargetAbs = null;
            int[] previousTargetAbs = null;

            for (int indl = 0; indl < llcc.size(); indl++) {
                toKeep.add(new ArrayList<Integer>());
                List<CC> lcc = llcc.get(indl);
                int nCC = lcc.size();
                CC ccFirst = lcc.get(0);
                CC ccLast = lcc.get(nCC - 1);

                //Identify starting point
                if (indl > 0) {//It is at least the second connected component, thus source have been established previously
                    currentSourceAbs = nextSourceAbs;
                    cumulatedDistance += VitimageUtils.distance(previousTargetAbs[0], previousTargetAbs[1],
                            currentSourceAbs[0], currentSourceAbs[1]);
                } else {//Get the lower y pixel if prim start, or the facet connexion else
                    currentSourceAbs = ccFirst.getExpectedSource();
                    currentSourceAbs = new int[]{currentSourceAbs[0]+ccFirst.xMin, currentSourceAbs[1]+ccFirst.yMin};
                }
                //Identify target point
                if (ccLast.getChild() == null) {
                    //End of primary : Identify target in ccLast
                    int[] coords = ccLast.getExpectedSource();
                    currentTargetAbs = ccLast.determineTargetGeodesicallyFarestFromTheSource(coords);
                    currentTargetAbs = new int[]{currentTargetAbs[0] + ccLast.xMin, currentTargetAbs[1] + ccLast.yMin};
                } else {
                    //Identify source in next, then target in this - use facettes from the edge
                    CC ccFirstNext = ccLast.getChild();
                    ConnectionEdge edgeToNext = graph.getEdge(ccLast, ccFirstNext);
                    
                    // Use facette coordinates directly - they are in absolute coordinates
                    // We'll convert to ccFuse-relative coordinates after creating ccFuse
                    //TODO : danger with 0.5 facets


                    Pix p=ccLast.getNearestPix(edgeToNext.hiddenConnectingFacets.get(0)[0], edgeToNext.hiddenConnectingFacets.get(0)[1]);
                    currentTargetAbs=new int[]{p.x+ccLast.xMin,p.y+ccLast.yMin};
                    previousTargetAbs = new int[]{currentTargetAbs[0], currentTargetAbs[1]};
                    
                    p=ccFirstNext.getNearestPix(edgeToNext.hiddenConnectingFacets.get(edgeToNext.hiddenConnectingFacets.size()-1)[0], edgeToNext.hiddenConnectingFacets.get(edgeToNext.hiddenConnectingFacets.size()-1)[1]);
                    nextSourceAbs = new int[]{p.x+ccFirstNext.xMin,p.y+ccFirstNext.yMin};


                }

                //Compute dijkstra path within ccFuse
                CC ccFuse = CC.fuseListOfCCIntoSingleCC(llcc.get(indl));
                lccFuse.add(ccFuse);
                int[]sourc=new int[]{currentSourceAbs[0]-ccFuse.xMin,currentSourceAbs[1]-ccFuse.yMin};
                int[]targ=new int[]{currentTargetAbs[0]-ccFuse.xMin,currentTargetAbs[1]-ccFuse.yMin};
                ccFuse.determineVoxelShortestPath(sourc, targ, 8, null);
                cumulatedDistance = ccFuse.setDistancesToMainDijkstraPath(cumulatedDistance);


                //Evaluate the timing along dijkstra path. Set first pixel to birthDate of root, walking along dijkstraPath, and attribute to each a componentIndex
                int[] indices = new int[ccFuse.mainDjikstraPath.size()];
                for (int n = 0; n < ccFuse.mainDjikstraPath.size(); n++) {
                    Pix p = ccFuse.mainDjikstraPath.get(n);
                    int xx = p.x + ccFuse.xMin;
                    int yy = p.y + ccFuse.yMin;
                    for (int i = 0; i < nCC; i++) {
                        if (lcc.get(i).containsPixOfCoordinateAbsolute(xx, yy)) {
                            indices[n] = i;
                        }
                    }
                }


                //Eventually add the point for the first if it is the first component in llcc
                if (indl == 0) {
                    timeInter.add((double) (lcc.get(0).day - 1));
                    distInter.add(ccFuse.mainDjikstraPath.get(0).wayFromPrim);
                }

                //For each component except the last, identify the last point of it and If necessary, add the last
                // one (see the for loop condition)
                for (int i = 0; i < (lcc.size() - 1); i++) {
                    double distMax = -1;
                    int indMax = -1;
                    for (int n = 0; n < ccFuse.mainDjikstraPath.size(); n++) {
                        if (indices[n] == i) {
                            distMax = ccFuse.mainDjikstraPath.get(n).wayFromPrim;
                            indMax = n;
                        }
                    }
                    if (indMax != -1) {
                        distInter.add(distMax);
                        timeInter.add((double) (lcc.get(i).day));
                        toKeep.get(indl).add(indMax);
                    }
                    if (debugPrim)
                        System.out.println("Adding a point at indl=" + indl + " "+lcc.get(i).x()+" "+lcc.get(i).y()+" time=" + timeInter.get(timeInter.size() - 1) + " dist=" + distInter.get(timeInter.size() - 1));
                }
                if (indl == llcc.size() - 1) {
                    distInter.add(ccFuse.mainDjikstraPath.get(ccFuse.mainDjikstraPath.size() - 1).wayFromPrim);
                    timeInter.add((double) (lcc.get(lcc.size() - 1).day));
                }
            }
            
            //Convert results of correspondance into double tabs
            int N = distInter.size();
            double[] xPoints = new double[N];
            double[] yPointsHours = new double[N];
            double[] yPoints = new double[N];
            for (int i = 0; i < N; i++) {
                xPoints[i] = distInter.get(i);
                yPoints[i] = timeInter.get(i);
                yPointsHours[i] = hoursExtremities[(int) Math.round(timeInter.get(i))];
            }


            //Evaluate time for all the respective dijkstraPath	and convert to RSML
            for (int li = 0; li < lccFuse.size(); li++) {
                CC ccF = lccFuse.get(li);
                List<CC> lcc = llcc.get(li);
                //Propagate distance into the ccFuse's
                ccF.updateAllDistancesToTrunk();

                //Convert distance into time
                for (Pix p : ccF.pixGraph.vertexSet()) {
                    p.time = SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim, xPoints, yPoints);
                    p.timeOut = SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim + p.distOut, xPoints, yPoints);
                    p.timeHours = SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim, xPoints, yPointsHours);
                    p.timeOutHours = SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim + p.distOut, xPoints,
                    yPointsHours);
                }
                
                // Also calculate time for pixels in mainDjikstraPath that may not be in pixGraph
                for (Pix p : ccF.mainDjikstraPath) {
                    if (p.time == -1.0 || p.time == 0.0) {  // Only update if not already set
                        p.time = SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim, xPoints, yPoints);
                        p.timeOut = SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim + p.distOut, xPoints, yPoints);
                        p.timeHours = SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim, xPoints, yPointsHours);
                        p.timeOutHours = SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim + p.distOut, xPoints,
                        yPointsHours);
                    }
                }

                //Back copy to the initial CCs
                for (CC c : lcc) {
                    for (Pix p : c.pixGraph.vertexSet()) {
                        Pix p2 = ccF.getPix(p.x + c.xMin - ccF.xMin, p.y + c.yMin - ccF.yMin);
                        p.dist = p2.dist;
                        p.distanceToSkeleton = p2.distanceToSkeleton;
                        p.distOut = p2.distOut;
                        p.isSkeleton = p2.isSkeleton;
                        p.time = p2.time;
                        p.timeOut = p2.timeOut;
                        p.timeHours = p2.timeHours;
                        p.timeOutHours = p2.timeOutHours;
                        p.wayFromPrim = p2.wayFromPrim;
                        p2.offX = c.xMin;
                        p2.offY = c.yMin;
                    }
                }

                //Subsample respective dijkstra path with beucker algorithm, and collect RSML points with speed and

                List<Pix> list = null;

                list = simplerSimplify ? DouglasPeuckerSimplify.simplifySimpler(ccF.mainDjikstraPath, toKeep.get(li),
                        3) :
                        DouglasPeuckerSimplify.simplify(ccF.mainDjikstraPath, toKeep.get(li),
                                toleranceDistToCentralLine);
                //}
                for (int i = 0; i < list.size() - 1; i++) {
                    Pix p = list.get(i);
                    rPrim.addNode(p.x + ccF.xMin, p.y + ccF.yMin, p.time, p.timeHours, (i == 0) && (li == 0));
                }

                Pix p = list.get(list.size() - 1);
                rPrim.addNode(p.x + ccF.xMin, p.y + ccF.yMin, p.time, p.timeHours, false);
                if (li != (lccFuse.size() - 1)) rPrim.setLastNodeHidden();
            }
            rPrim.computeDistances();
            rPrim.order=1;
            rm.rootList.add(rPrim);
        }




        ////// Processing lateral roots /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        
        for(int order=2;order<=Utils.MAX_ORDER_ROOTS;order++){
            int incrLat = 1;
            for (CC cc : graph.vertexSet()) {
                if (!cc.isLatStart) continue;
                if(cc.order!=order)continue;
                debugLat=false; 
                
               if (debugLat)
                    System.out.println("\n\n\nProcessing lateral root #" + (incrLat++) + " : " + cc + " with order "+cc.order+"  whose source is" +cc.bestIncomingActivatedCC()+ "   "+cc.bestIncomingActivatedEdge().source.day+"-"+ cc.bestIncomingActivatedEdge().source.n+" with order "+cc.bestIncomingActivatedEdge().source.order);
                    
                //Identification of correspondant n-1th order
                Root myParent = null;
                if(order==2){
                    for (int i = 0; i < listRprim.size(); i++) {
                        if (listNprim.get(i) == cc.bestIncomingActivatedEdge().source.n && listDprim.get(i) == cc.bestIncomingActivatedEdge().source.day) {
                            myParent = listRprim.get(i);
                            break;
                        }
                    }
                    

                }else{
                    for (int i = 0; i < listRlat.get(order-1).size(); i++) {
                        if (debugLat)System.out.println("Testing potential parent root : "+listRlat.get(order-1).get(i)+" with n="+listNlat.get(order-1).get(i)+" and d="+listDlat.get(order-1).get(i));
                        if (listNlat.get(order-1).get(i) == cc.bestIncomingActivatedEdge().source.n && listDlat.get(order-1).get(i) == cc.bestIncomingActivatedEdge().source.day) {
                            myParent = listRlat.get(order-1).get(i);
                            if (debugLat) System.out.println("Found parent root : " + myParent);
                            break;
                        }
                    }
                }

                if(debugLat){
                    System.out.println("Found parent root : "+myParent+"\n");
                    waitForLong();
                }

                //Identification of connected part of the root
                CC ccNext = cc;
                ArrayList<ArrayList<CC>> llcc = new ArrayList<ArrayList<CC>>();
                ArrayList<ArrayList<Integer>> toKeep = new ArrayList<ArrayList<Integer>>();
                ArrayList<CC> lccFuse = new ArrayList<CC>();
                llcc.add(new ArrayList<CC>());
                llcc.get(0).add(ccNext);
                Root rLat = new Root(null, rm, "", order);
                listRlat.get(order).add(rLat);
                listNlat.get(order).add(llcc.get(0).get(0).n);
                listDlat.get(order).add(llcc.get(0).get(0).day);

                if (debugLat){
                    System.out.println("Just added the first CC of lateral root : "+ccNext);
                }
                int ind = 0;
                while (ccNext.getChild() != null) {
                    if (ccNext.hasHiddenChild()) {
                        if(debugLat)System.out.println("Hidden !");
                        llcc.add(new ArrayList<CC>());
                        ind++;
                    }
                    ccNext = ccNext.getChild();
                    listRlat.get(order).add(rLat);
                    listNlat.get(order).add(ccNext.n);
                    listDlat.get(order).add(ccNext.day);
                    if (debugLat){
                        System.out.println("Added CC to lateral root : "+ccNext);                        
                    }


                    llcc.get(ind).add(ccNext);
                }

                //Separate dijkstra path processing of the respective parts
                double startingDistance = 0;
                ArrayList<Double> distInter = new ArrayList<Double>();
                ArrayList<Double> timeInter = new ArrayList<Double>();
                double cumulatedDistance = startingDistance;
                int[] nextSourceAbs = null;
                int[] previousTargetAbs = null;
                int[] currentSourceAbs = null;
                int[] currentTargetAbs = null;

                for (int indl = 0; indl < llcc.size(); indl++) {
                    if(debugLat)System.out.println("\n\nStarting processing of segment number"+indl+" of lateral root ");
                    toKeep.add(new ArrayList<Integer>());
                    List<CC> lcc = llcc.get(indl);
                    int nCC = lcc.size();
                    CC ccFirst = lcc.get(0);
                    CC ccLast = lcc.get(nCC - 1);
                    CC ccFuse = CC.fuseListOfCCIntoSingleCC(llcc.get(indl));
                    lccFuse.add(ccFuse);
                    boolean debug = false;
                   
                    //Identify starting point
                    if (indl > 0) {//It is at least the second connected component
                        currentSourceAbs = nextSourceAbs;
                        cumulatedDistance += VitimageUtils.distance(previousTargetAbs[0], previousTargetAbs[1],
                                currentSourceAbs[0], currentSourceAbs[1]);
                    } else {
                        currentSourceAbs = ccFirst.getExpectedSource();
                        currentSourceAbs = new int[]{currentSourceAbs[0] + ccFirst.xMin, currentSourceAbs[1] + ccFirst.yMin};
                    }

                    if (indl == (llcc.size() - 1)) {
                        //Identify target point
                        if (debugLat) System.out.println("End of lateral : " + lcc.get(lcc.size() - 1));
                        int[] coords = ccFirst.getNextSourceFromFacetConnexion(ccFirst.bestIncomingActivatedEdge());
                        coords = new int[]{coords[0] + ccFirst.xMin - ccFuse.xMin, coords[1] + ccFirst.yMin - ccFuse.yMin};

                        currentTargetAbs = ccFuse.determineTargetGeodesicallyFarestFromTheSource(coords);
                        currentTargetAbs[0] += (ccFuse.xMin);
                        currentTargetAbs[1] += (ccFuse.yMin);
                        
                    } else {
                        if(debugLat)System.out.println("Hidden connection to next segment of lateral root.");
                        //Identify source in next, then target in this - use facettes from the edge
                        CC ccFirstNext = ccLast.getChild();
                        if(debugLat)System.out.println("ccFirstNext = "+ccFirstNext );
                        
                        ConnectionEdge edgeToNext = graph.getEdge(ccLast, ccFirstNext);
                        if(debugLat)System.out.println("edgeToNext = "+edgeToNext);

                        // Use facette coordinates directly - they are in absolute coordinates
                        // So we convert them directly to ccFuse-relative coordinates
                        if(debugLat)System.out.println("Debug : nb of hidden facets = "+edgeToNext.hiddenConnectingFacets.size());
                        if(debugLat)System.out.println("Debug : first hidden facet = "+Arrays.toString(edgeToNext.hiddenConnectingFacets.get(0)));
                        if(debugLat)System.out.println("Debug : last hidden facet = "+Arrays.toString(edgeToNext.hiddenConnectingFacets.get(edgeToNext.hiddenConnectingFacets.size()-1)));
                        Pix p=ccLast.getNearestPix(edgeToNext.hiddenConnectingFacets.get(0)[0], edgeToNext.hiddenConnectingFacets.get(0)[1]);
                        currentTargetAbs=new int[]{p.x+ccLast.xMin,p.y+ccLast.yMin};
                        previousTargetAbs = new int[]{currentTargetAbs[0], currentTargetAbs[1]};
                        if(debugLat)System.out.println("currentTargetAbs = "+Arrays.toString(currentTargetAbs));

                        p=ccFirstNext.getNearestPix(edgeToNext.hiddenConnectingFacets.get(edgeToNext.hiddenConnectingFacets.size()-1)[0], edgeToNext.hiddenConnectingFacets.get(edgeToNext.hiddenConnectingFacets.size()-1)[1]);
                        nextSourceAbs = new int[]{p.x+ccFirstNext.xMin,p.y+ccFirstNext.yMin};
                        if(debugLat)System.out.println("nextSourceAbs = "+Arrays.toString(nextSourceAbs));
                        

                    }

                    //Compute dijkstra path
                    int[]sourc=new int[]{currentSourceAbs[0]-ccFuse.xMin,currentSourceAbs[1]-ccFuse.yMin};
                    int[]targ=new int[]{currentTargetAbs[0]-ccFuse.xMin,currentTargetAbs[1]-ccFuse.yMin};
                    ccFuse.determineVoxelShortestPath(sourc, targ, 8, null);
                    if (debugLat) System.out.print("CumulatedDistance = "+cumulatedDistance);
                    cumulatedDistance = ccFuse.setDistancesToMainDijkstraPath(cumulatedDistance);
                    if (debugLat) System.out.println("   updated to = "+cumulatedDistance);
                    if (debugLat) System.out.println("Lenght of the path of this segment=" + ccFuse.mainDjikstraPath.size());


                    //Evaluate the timing along dijkstra path
                    int[] indices = new int[ccFuse.mainDjikstraPath.size()];
                    for (int n = 0; n < ccFuse.mainDjikstraPath.size(); n++) {
                        Pix p = ccFuse.mainDjikstraPath.get(n);
                        int xx = p.x + ccFuse.xMin;
                        int yy = p.y + ccFuse.yMin;
                        for (int i = 0; i < nCC; i++) {
                            if (lcc.get(i).containsPixOfCoordinateAbsolute(xx, yy)) {
                                indices[n] = i;
                            }
                        }
                    }


                    //Eventually add the point for the first if it is the first component in llcc
                    if (indl == 0) {
                        timeInter.add((double) (lcc.get(0).day - 1));
                        distInter.add(ccFuse.mainDjikstraPath.get(0).wayFromPrim);
                    }
                    //For each component except the last, identify the last point of it and If necessary, add the last
                    //one (see the for loop condition)
                    for (int i = 0; i < lcc.size() - 1; i++) {
                        double distMax = -1;
                        int indMax = -1;
                        for (int n = 0; n < ccFuse.mainDjikstraPath.size(); n++) {
                            if (indices[n] == i) {
                                distMax = ccFuse.mainDjikstraPath.get(n).wayFromPrim;
                                indMax = n;
                            }
                        }
                        if (indMax >= 0) {
                            distInter.add(distMax);
                            timeInter.add((double) (lcc.get(i).day));
                            toKeep.get(indl).add(indMax);
                        }
                    }
                    if (indl == llcc.size() - 1) {
                        distInter.add(ccFuse.mainDjikstraPath.get(ccFuse.mainDjikstraPath.size() - 1).wayFromPrim);
                        timeInter.add((double) (lcc.get(lcc.size() - 1).day));
                    }
                }
                
                //Convert results of correspondance into double tabs
                int N = distInter.size();
                double[] xPoints = new double[N];
                double[] yPoints = new double[N];
                double[] yPointsHours = new double[N];
                for (int i = 0; i < N; i++) {
                    xPoints[i] = distInter.get(i);
                    yPoints[i] = timeInter.get(i);
                    yPointsHours[i] = hoursExtremities[(int) Math.round(timeInter.get(i))];
                }
                
                // DEBUG: Display interpolation arrays
                if (debugLat) {
                    System.out.println("\n=== DEBUG: Interpolation arrays for lateral root ===");
                    System.out.println("xPoints (distances) and yPoints (times) for interpolation:");
                    for (int i = 0; i < N; i++) {
                        System.out.println("  [" + i + "] xPoints=" + xPoints[i] + ", yPoints=" + yPoints[i] + ", yPointsHours=" + yPointsHours[i]);
                    }
                }


                //Evaluate time for all the respective dijkstraPath	and convert to RPrimSML
                for (int li = 0; li < lccFuse.size(); li++) {
                    if (debugLat) System.out.println("Processing path for component " + li);
                    CC ccF = lccFuse.get(li);
                    List<CC> lcc = llcc.get(li);
                    //Propagate distance into the ccFuse's
                    ccF.updateAllDistancesToTrunk();

                    //Convert distance into time
                    for (Pix p : ccF.pixGraph.vertexSet()) {
                        p.time = SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim, xPoints, yPoints);
                        p.timeOut = SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim + p.distOut, xPoints, yPoints);
                        p.timeHours = SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim, xPoints, yPointsHours);
                        p.timeOutHours = SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim + p.distOut, xPoints,
                                yPointsHours);
                    }
                    
                    // IMPORTANT: Also calculate time for pixels in mainDjikstraPath that may not be in pixGraph
                    // (e.g., pixels added during hidden zone correction)
                    for (Pix p : ccF.mainDjikstraPath) {
                        if (p.time == -1.0 || p.time == 0.0) {  // Only update if not already set
                            p.time = SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim, xPoints, yPoints);
                            p.timeOut = SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim + p.distOut, xPoints, yPoints);
                            p.timeHours = SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim, xPoints, yPointsHours);
                            p.timeOutHours = SplineAndPolyLineUtils.linearInterpolation(p.wayFromPrim + p.distOut, xPoints,
                                    yPointsHours);
                        }
                    }
                    
                    // DEBUG: Check for pixels with problematic time values
                    if (false) {
                        double minTime = Double.MAX_VALUE;
                        double maxTime = Double.MIN_VALUE;
                        double minWayFromPrim = Double.MAX_VALUE;
                        double maxWayFromPrim = Double.MIN_VALUE;
                        int negativeTimeCount = 0;
                        
                        for (Pix p : ccF.pixGraph.vertexSet()) {
                            if (p.time < minTime) minTime = p.time;
                            if (p.time > maxTime) maxTime = p.time;
                            if (p.wayFromPrim < minWayFromPrim) minWayFromPrim = p.wayFromPrim;
                            if (p.wayFromPrim > maxWayFromPrim) maxWayFromPrim = p.wayFromPrim;
                            if (p.time < 0) negativeTimeCount++;
                        }
                        
                        System.out.println("  Component " + li + " pixel statistics:");
                        System.out.println("    wayFromPrim range: [" + minWayFromPrim + ", " + maxWayFromPrim + "]");
                        System.out.println("    time range: [" + minTime + ", " + maxTime + "]");
                        System.out.println("    xPoints range: [" + xPoints[0] + ", " + xPoints[xPoints.length-1] + "]");
                        if (negativeTimeCount > 0) {
                            System.out.println("    WARNING: " + negativeTimeCount + " pixels have NEGATIVE time!");
                        }
                        if (minWayFromPrim < xPoints[0]) {
                            System.out.println("    WARNING: Some wayFromPrim values (" + minWayFromPrim + 
                                             ") are LESS than xPoints[0] (" + xPoints[0] + ") - extrapolation will occur!");
                        }
                        if (maxWayFromPrim > xPoints[xPoints.length-1]) {
                            System.out.println("    WARNING: Some wayFromPrim values (" + maxWayFromPrim + 
                                             ") are GREATER than xPoints[last] (" + xPoints[xPoints.length-1] + ") - extrapolation will occur!");
                        }
                    }

                    //Back copy to the initial CCs
                    for (CC c : lcc) {
                        for (Pix p : c.pixGraph.vertexSet()) {
                            Pix p2 = ccF.getPix(p.x + c.xMin - ccF.xMin, p.y + c.yMin - ccF.yMin);
                            p.dist = p2.dist;
                            p.distanceToSkeleton = p2.distanceToSkeleton;
                            p.distOut = p2.distOut;
                            p.isSkeleton = p2.isSkeleton;
                            p.time = p2.time;
                            p.timeOut = p2.timeOut;
                            p.timeHours = p2.timeHours;
                            p.timeOutHours = p2.timeOutHours;
                            p.wayFromPrim = p2.wayFromPrim;
                            p2.offX = c.xMin;
                            p2.offY = c.yMin;
                        }
                    }

                    //Subsample respective dijkstra path with beucker algorithm, and collect RSML points
                    if(debugLat)System.out.println("Going to simplify a path of size " + ccF.mainDjikstraPath.size());
                    if(debugLat)System.out.println("simplifying the path \n -> "+ccF.mainDjikstraPath.get(0)+" \n -> "+ccF.mainDjikstraPath.get(ccF.mainDjikstraPath.size()/2)+" \n -> "+ccF.mainDjikstraPath.get(ccF.mainDjikstraPath.size()-1));

                    //Subsample respective dijkstra path with beucker algorithm, and collect RSML points

                    List<Pix> list = simplerSimplify ? DouglasPeuckerSimplify.simplifySimpler(ccF.mainDjikstraPath,
                            toKeep.get(li), 3) :
                            DouglasPeuckerSimplify.simplify(ccF.mainDjikstraPath, toKeep.get(li),
                                    toleranceDistToCentralLine);
                    if (debugLat) System.out.println("Simplifying a list of " + ccF.mainDjikstraPath.size() + " to list of " + list.size());
                    for (int i = 0; i < list.size() - 1; i++) {
                        Pix p = list.get(i);
                        rLat.addNode(p.x + ccF.xMin, p.y + ccF.yMin, p.time, p.timeHours, (i == 0) && (li == 0));
                    }
                    Pix p = list.get(list.size() - 1);
                    rLat.addNode(p.x + ccF.xMin, p.y + ccF.yMin, p.time, p.timeHours, false);
                    if (li != (lccFuse.size() - 1)) rLat.setLastNodeHidden();
                }
                rLat.computeDistances();
                rLat.order=order;
                
                // DEBUG: Display all nodes before resampleFlyingPoints to diagnose birthTime issues
                if (debugLat) {
                    System.out.println("\n=== DEBUG: Nodes in lateral root BEFORE resampleFlyingPoints ===");
                    System.out.println("hoursCorrespondingToTimePoints array size: " + rm.hoursCorrespondingToTimePoints.length);
                    System.out.println("Root nodes description:\n" + rLat);
                    System.out.println("=== END DEBUG ===\n");
                }
                
                rLat.resampleFlyingPoints(rm.hoursCorrespondingToTimePoints);
                myParent.attachChild(rLat);
                rLat.attachParent(myParent);
                rm.rootList.add(rLat);
                if(debugLat){
                    System.out.println("Finished processing lateral root #" + (incrLat-1) + " : " + cc + "\n\n\n");
                    
                }
            }
        }
        rm.standardOrderingOfRoots();
        return rm;
    }























    /**
     * -------------------------------------------------------------------------------------------------------------------------------------------
      * -------------------------------------------------------------------------------------------------------------------------------------------
     * RootModel and CC properties post processing computing helpers *************************************************************************************
      * -------------------------------------------------------------------------------------------------------------------------------------------
     * -------------------------------------------------------------------------------------------------------------------------------------------
     */
    public static ImagePlus getDistanceMapsToDateMaps(ImagePlus img) {
        ImagePlus seedImage = VitimageUtils.thresholdFloatImage(img, 0.5, 10000);
        seedImage.setDisplayRange(0, 1);
        IJ.run(seedImage, "8-bit", "");
        ImagePlus segImage = VitimageUtils.thresholdFloatImage(img, -0.5, 0.5);
        segImage.setDisplayRange(0, 1);
        IJ.run(segImage, "8-bit", "");
        seedImage = MorphoUtils.dilationCircle2D(seedImage, 1);
        ImagePlus distance = MorphoUtils.computeGeodesic(seedImage, segImage, false);
        VitimageUtils.makeOperationOnOneImage(distance, 2, 1 / 1000.0, false);
        return distance;
    }

    public static boolean isExtremity(CC cc, SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph) {
        if (graph.incomingEdgesOf(cc).size() < 1) return true;
        if (graph.outgoingEdgesOf(cc).size() < 1) return true;
        boolean hasParent = false;
        boolean hasChild = false;
        for (ConnectionEdge edge : graph.incomingEdgesOf(cc))
            if (edge.activated) {
                hasParent = true;
                break;
            }
        for (ConnectionEdge edge : graph.outgoingEdgesOf(cc))
            if (edge.activated) {
                hasChild = true;
                break;
            }
        return !(hasParent && hasChild);
    }




    /**
     * -------------------------------------------------------------------------------------------------------------------------------------------
      * -------------------------------------------------------------------------------------------------------------------------------------------
     * Simple graph and CC accessors and I/O helpers *************************************************************************************
      * -------------------------------------------------------------------------------------------------------------------------------------------
     * -------------------------------------------------------------------------------------------------------------------------------------------
     */
    public static int maxCCIndexOfDay(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, int d) {
        int max = 0;
        for (CC cc : graph.vertexSet()) if (cc.day == d && cc.n > max) max = cc.n;
        return max;
    }

    @SuppressWarnings("unchecked")
    public static ArrayList<CC> sortCC(ArrayList<CC> arIn) {
        Object[] tabIn = new Object[arIn.size()];
        for (int i = 0; i < arIn.size(); i++) tabIn[i] = arIn.get(i);
        Arrays.sort(tabIn, new CCComparator());
        ArrayList<CC> arOut = new ArrayList<CC>();
        for (int i = 0; i < arIn.size(); i++) arOut.add((CC) tabIn[i]);
        return arOut;
    }

    public static int getDayMax(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph) {
        int max = 0;
        for (CC cc : graph.vertexSet()) if (cc.day > max) max = cc.day;
        return max;
    }

    public static int isIn(CC cc, ArrayList<CC[]> tabCC) {
        for (int i = 0; i < tabCC.size(); i++) {
            if (cc == tabCC.get(i)[1]) return i;
        }
        return -1;
    }

    public static CC getRoot(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph) {
        for (CC cc : graph.vertexSet()) if (cc.day == 0) return cc;
        return null;
    }

    public static int getMaxDay(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph) {
        int max = 0;
        for (CC cc : graph.vertexSet()) {
            if (cc.day > max) max = cc.day;
        }
        return max;
    }

    public static SimpleDirectedWeightedGraph<CC, ConnectionEdge> readGraphFromFile(String path) {
        try {
            FileInputStream streamIn = new FileInputStream(path);
            ObjectInputStream objectinputstream = new ObjectInputStream(streamIn);
            @SuppressWarnings("unchecked")
            SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph =
                    (SimpleDirectedWeightedGraph<CC, ConnectionEdge>) objectinputstream.readObject();
            objectinputstream.close();
            streamIn.close();
            return graph;
        } catch (ClassNotFoundException | IOException c) {
            c.printStackTrace();
        }
        return null;
    }

    public static void writeGraphToFile(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, String path) {
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

    public static CC getCC(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, int day, int x, int y) {
        //System.out.println("Looking for CC");
        CC ret = null;
        double minDist = 1E8;
        for (CC cc : graph.vertexSet()) {
            if (cc.day != day) continue;
            double dist = VitimageUtils.distance(x, y, cc.x(), cc.y());
            if (dist < minDist) {
                minDist = dist;
                ret = cc;
            }
        }
        return ret;
    }

    public static CC getCC(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, int x, int y) {
        //System.out.println("Looking for CC");
        CC ret = null;
        double minDist = 1E8;
        for (CC cc : graph.vertexSet()) {
            double dist = VitimageUtils.distance(x, y, cc.x(), cc.y());
            if (dist < minDist) {
                minDist = dist;
                ret = cc;
            }
        }
        return ret;
    }

    public static CC getCCWithResolution(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, int x, int y) {
        //System.out.println("Looking for CC");
        int res=sizeFactor;
        CC ret = null;
        double minDist = 1E8;
        for (CC cc : graph.vertexSet()) {
            double dist = VitimageUtils.distance(x / res, y / res, cc.x(),
                    cc.y());
            if (dist < minDist) {
                minDist = dist;
                ret = cc;
            }
        }
        return ret;
    }

    @SuppressWarnings("rawtypes")
    static class CCComparator implements java.util.Comparator {
        public int compare(Object o1, Object o2) {
            return ((Double) ((CC) o1).x()).compareTo(((CC) o2).x());
        }
    }






    /**
     * -------------------------------------------------------------------------------------------------------------------------------------------
      * -------------------------------------------------------------------------------------------------------------------------------------------
     * Rendering helpers *************************************************************************************
      * -------------------------------------------------------------------------------------------------------------------------------------------
     * -------------------------------------------------------------------------------------------------------------------------------------------
     */
    public static void produceDebugImagesOfGraph(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph,
                                                 ImagePlus imgDatesTmp, double ray, int thickness, int sizeFactor,
                                                 String outputDataDir) {   

        System.out.println("Producing debug images of graph rendering...");
        imgDatesTmp=VitimageUtils.convertShortToFloatWithoutDynamicChanges(imgDatesTmp);
        ImagePlus imgDatesHigh = VitimageUtils.convertFloatToByteWithoutDynamicChanges(imgDatesTmp);
        IJ.run(imgDatesHigh, "Fire", "");
        imgDatesHigh.setDisplayRange(0, nDays);
        imgDatesHigh = VitimageUtils.resizeNearest(imgDatesTmp, imgDatesTmp.getWidth() * sizeFactor,
                imgDatesTmp.getHeight() * sizeFactor, 1);
        ImagePlus dates;
        ImagePlus graphs;
        int nDebug=0;
        String[] stepNames=new String[]{"step_1","step_2","step_3","step_4","step_5","step_6","step_7","step_8"};
        for(int i=0;i<stepNames.length;i++){
            if(new File(outputDataDir, "50_graph_v2_"+stepNames[i]+".ser").exists())nDebug++;
        }
        ImagePlus[] graphsImgs = new ImagePlus[nDebug];
        ImagePlus[] backImgs = new ImagePlus[nDebug];

        int iDeb=0;
        ImagePlus imgGraphToKeep=null;
        ImagePlus imgContourToKeep=null;
        System.out.println("Loop over steps");
        for(int i=0;i<stepNames.length;i++){
            if(new File(outputDataDir, "50_graph_v2_"+stepNames[i]+".ser").exists()){
                SimpleDirectedWeightedGraph<CC, ConnectionEdge> gg = readGraphFromFile(new File(outputDataDir,
                        "50_graph_v2_"+stepNames[i]+".ser").getAbsolutePath());
                // Afficher les edges non activées uniquement pour step_3
                boolean showNonActivated = stepNames[i].equals("step_3");
                ImagePlus []tabTmp=drawGraph(imgDatesTmp, gg, ray, thickness, sizeFactor,imgGraphToKeep,imgContourToKeep, showNonActivated);
                graphsImgs[iDeb] = tabTmp[0];
                if(imgGraphToKeep==null){
                    imgGraphToKeep=tabTmp[1];
                    imgContourToKeep=tabTmp[2];
                }
                VitimageUtils.addLabelOnAllSlices(graphsImgs[iDeb], stepNames[i]);
                backImgs[iDeb] = imgDatesHigh.duplicate();
                VitimageUtils.addLabelOnAllSlices(backImgs[iDeb], stepNames[i]);
                iDeb++;
            }
        }
        System.out.println("Loop ok");

        graphs = VitimageUtils.slicesToStack(graphsImgs);
        graphs = VitimageUtils.convertFloatToByteWithoutDynamicChanges(graphs);
        dates = VitimageUtils.convertFloatToByteWithoutDynamicChanges(VitimageUtils.slicesToStack(backImgs));

        System.out.println("Conversion ok");
        //Compute the combined rendering
        ImagePlus glob = VitimageUtils.hyperStackingChannels(new ImagePlus[]{dates, graphs});
        glob.setDisplayRange(0, nDays);
        IJ.run(glob, "Fire", "");
        System.out.println("Writing TIF to " + new File(outputDataDir, "51_graph_rendering.tif").getAbsolutePath());
        IJ.saveAsTiff(glob, new File(outputDataDir, "51_graph_rendering.tif").getAbsolutePath());
        System.out.println("Saved");
    }
    

     //This function draws either distance or time, either on the full region or only on the skeleton, to visualize
    //Progressive growth
    public static ImagePlus drawDistanceOrTime(ImagePlus source,
                                               SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, boolean trueDoDistFalseDoTime, boolean onlyDoSkeleton,
                                               int mode_1Total_2OutsideDistOrIntTime_3SourceDist) {
      double max=-1000;
      double min=100000;
                                                ImagePlus imgDist = VitimageUtils.convertToFloat(VitimageUtils.nullImage(source));
        imgDist = VitimageUtils.makeOperationOnOneImage(imgDist, 1, -VitimageUtils.EPSILON, true);
        System.out.println("In drawDistanceOrTime, mode=" + mode_1Total_2OutsideDistOrIntTime_3SourceDist + "  trueDoDistFalseDoTime=" + trueDoDistFalseDoTime + "  onlyDoSkeleton=" + onlyDoSkeleton);
       int X = imgDist.getWidth();
        float[] valDist = (float[]) imgDist.getStack().getProcessor(1).getPixels();
        for (CC cc : graph.vertexSet()) {
            int x0 = cc.xMin;
            int y0 = cc.yMin;
            for (Pix p : cc.pixGraph.vertexSet()) {
                int index = X * (p.y + y0) + (p.x + x0);
                if (onlyDoSkeleton && (!p.isSkeleton)) continue;
                if (mode_1Total_2OutsideDistOrIntTime_3SourceDist == 1){
                    valDist[index] = (trueDoDistFalseDoTime) ? ((float) (p.wayFromPrim + p.distOut)) :
                            (float) p.timeOutHours;
                }
                else if (mode_1Total_2OutsideDistOrIntTime_3SourceDist == 2){
                    valDist[index] = (trueDoDistFalseDoTime) ? ((float) (p.distOut)) : (float) (cc.hour);
                }   
                else {
                    valDist[index] = (trueDoDistFalseDoTime) ? ((float) (p.wayFromPrim)) : (float) (p.timeHours);
 
                }
                if (!trueDoDistFalseDoTime && valDist[index] < 0) {
                    valDist[index] = 0;
                }
                if(valDist[index]>max)max=valDist[index];
                if(valDist[index]<min)min=valDist[index];
            }
        }
        System.out.println("min max = "+min+"  "+max);
        return imgDist;
    }


    //Same function as above, but drawing both distance and time, either on the full region or only on the skeleton,
    public static ImagePlus drawDistanceTime(ImagePlus source, SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph,
                                             int mode_1Skel_2All_3AllWithTipDistance) {
        ImagePlus imgDist = VitimageUtils.convertToFloat(VitimageUtils.nullImage(source));
        ImagePlus imgTime = imgDist.duplicate();
        ImagePlus imgTimeOut = imgDist.duplicate();
        ImagePlus imgDistOut = imgDist.duplicate();
        ImagePlus imgDistSum = imgDist.duplicate();
        int X = imgDist.getWidth();
        float[] valDist = (float[]) imgDist.getStack().getProcessor(1).getPixels();
        float[] valDistOut = (float[]) imgDistOut.getStack().getProcessor(1).getPixels();
        float[] valDistSum = (float[]) imgDistSum.getStack().getProcessor(1).getPixels();
        float[] valTime = (float[]) imgTime.getStack().getProcessor(1).getPixels();
        float[] valTimeOut = (float[]) imgTimeOut.getStack().getProcessor(1).getPixels();
        for (CC cc : graph.vertexSet()) {
            int x0 = cc.xMin;
            int y0 = cc.yMin;
            for (Pix p : cc.pixGraph.vertexSet()) {
                int index = X * (p.y + y0) + (p.x + x0);
                if (mode_1Skel_2All_3AllWithTipDistance == 1 && (!p.isSkeleton)) continue;
                if (mode_1Skel_2All_3AllWithTipDistance < 3) {
                    valDist[index] = (float) p.wayFromPrim;
                    valTime[index] = (float) p.time;
                }
                if (mode_1Skel_2All_3AllWithTipDistance == 3) {
                    valDist[index] = (float) (p.wayFromPrim + p.distOut);
                    valTime[index] = (float) (p.timeOut);
                }
                if (mode_1Skel_2All_3AllWithTipDistance == 4 || mode_1Skel_2All_3AllWithTipDistance == 0) {
                    valDist[index] = (float) (p.wayFromPrim);
                    valDistOut[index] = (float) (p.distOut);
                    valDistSum[index] = (float) (p.wayFromPrim + p.distOut);
                    valTime[index] = (float) (p.time);
                    valTimeOut[index] = (float) (p.timeOut);
                }
            }
        }
        if (mode_1Skel_2All_3AllWithTipDistance == 0) return imgTimeOut;
        else if (mode_1Skel_2All_3AllWithTipDistance < 4)
            return VitimageUtils.slicesToStack(new ImagePlus[]{imgDist, imgTime});
        else return VitimageUtils.slicesToStack(new ImagePlus[]{imgDist, imgDistOut, imgDistSum, imgTime, imgTimeOut});
    }







        public static void drawCircleContourIntoImage(ImagePlus img,double ray,int x0,int y0,int z0,int value,double thickness){
            if(img.getType() == ImagePlus.GRAY32)return;
            int xM=img.getWidth();
            int yM=img.getHeight();
            int zM=img.getStackSize();
            double voxSX=img.getCalibration().pixelWidth;
            double voxSY=img.getCalibration().pixelHeight;
            double voxSZ=img.getCalibration().pixelDepth;
            double realDisX;
            double realDisY;
            double realDisZ;
            byte[][] valsImg=new byte[zM][];
            double distance;
            int zz0=(int) Math.round(Math.max(z0-ray/voxSZ, 0));
            int zz1=(int) Math.round(Math.min(z0+ray/voxSZ, zM-1));
            int xx0=(int) Math.round(Math.max(x0-ray/voxSX, 0));
            int xx1=(int) Math.round(Math.min(x0+ray/voxSX, xM-1));
            int yy0=(int) Math.round(Math.max(y0-ray/voxSY, 0));
            int yy1=(int) Math.round(Math.min(y0+ray/voxSY, yM-1));
            for(int z=zz0;z<=zz1;z++) {
                valsImg[z]=(byte [])img.getStack().getProcessor(z+1).getPixels();
                for(int x=xx0;x<=xx1;x++) {
                    for(int y=yy0;y<=yy1;y++) {
                        realDisX=(x-x0)*voxSX;
                        realDisY=(y-y0)*voxSY;
                        realDisZ=(z-z0)*voxSZ;
                        distance=Math.sqrt( realDisX * realDisX  +  realDisY * realDisY  + realDisZ * realDisZ  );
                        if(Math.abs(distance - ray)<thickness/2.0) {
                            valsImg[z][xM*y+x]=  (byte)( value & 0xff);
                        }
                    }
                }			
            }
            
        }










    /**
     * Helper class to store stunning text information for later rendering
     */
    private static class StunningTextInfo {
        String text;
        int textX;
        int textY;
        int textBottomX;
        int textBottomY;
        double refX;
        double refY;
        int color;
        
        StunningTextInfo(String text, int textX, int textY, int textBottomX, int textBottomY, 
                        double refX, double refY, int color) {
            this.text = text;
            this.textX = textX;
            this.textY = textY;
            this.textBottomX = textBottomX;
            this.textBottomY = textBottomY;
            this.refX = refX;
            this.refY = refY;
            this.color = color;
        }
    }

        /**
     * Draws a visual representation of the graph structure on an image.
     * - Draws contours and silhouettes of regions.
     * - Overlays circles for each vertex (connected component), with size and color based on properties.
     * - Draws lines for each edge (connection), with thickness and color reflecting edge attributes.
     * - Combines graph and contour masks for enhanced visualization.
     * Returns the resulting image with the graph structure overlaid.
     */
    public static ImagePlus[] drawGraph(ImagePlus imgDates, SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph,
                                      double circleRadius, int lineThickness, int sizeFactor,ImagePlus optionalImageGraphAlreadyDrawn,ImagePlus optionalImageContourAlreadyDrawn, boolean showNonActivatedEdges) {
        System.out.println();
        System.out.println();
        System.out.println("Drawing graph (showNonActivatedEdges=" + showNonActivatedEdges + ")");
        System.out.print("0- Preparing images  ");
        //Draw the silhouette


        ImagePlus contour= null;
        ImagePlus imgGraph=null;
        ImagePlus imgToKeepGraph=null;
        ImagePlus imgToKeepContour=null;
        int N = Math.round(VitimageUtils.maxOfImage(imgDates));
        int colorMax = N - 4; // Réserver les valeurs hautes (N-3 à N) pour les flags
        
        // Listes pour stocker les informations de textes stunning à dessiner à la fin (au-dessus de tout)
        ArrayList<StunningTextInfo> stunningTextsToDrawLater = new ArrayList<>();


        ////////////////////////////////////////////////////////PREPARE THE IMAGES////////////////////////////////////////////////////////////
        if(optionalImageGraphAlreadyDrawn!=null){
            System.out.print("Using pre-drawn graph image ");
            imgGraph=new Duplicator().run(optionalImageGraphAlreadyDrawn);
            contour=new Duplicator().run(optionalImageContourAlreadyDrawn);
            imgToKeepGraph=new Duplicator().run(optionalImageGraphAlreadyDrawn);
            imgToKeepContour=new Duplicator().run(optionalImageContourAlreadyDrawn);
        }
        else{
            contour = VitimageUtils.nullImage(imgDates);
            ImagePlus in = VitimageUtils.nullImage(imgDates);
            System.out.print("1 ");
            if (sizeFactor > 1) {
                ImagePlus bin = VitimageUtils.thresholdImage(imgDates, 0.5, 100000);
                ImagePlus binResize = VitimageUtils.resizeNearest(bin, imgDates.getWidth() * sizeFactor, imgDates.getHeight() * sizeFactor, 1);
                System.out.print("2 ");
                ImagePlus ero = MorphoUtils.erosionCircle2D(binResize, 1);
                ImagePlus dil = MorphoUtils.dilationCircle2D(binResize, 1);
                System.out.print(  "3 ");
                contour = VitimageUtils.makeOperationBetweenTwoImages(dil, ero, 4, false);

                ImagePlus nonBinResize = VitimageUtils.resizeNearest(imgDates, imgDates.getWidth() * sizeFactor,
                        imgDates.getHeight() * sizeFactor, 1);
                ImagePlus ero2 = MorphoUtils.erosionCircle2D(nonBinResize, 1);
                System.out.print(  "4 ");
                //ImagePlus dil2=MorphoUtils.dilationCircle2D(nonBinResize, 1);
                ImagePlus contour2 = VitimageUtils.makeOperationBetweenTwoImages(nonBinResize, ero2, 4, false);
                contour2 = VitimageUtils.thresholdImage(contour2, 0.5, 1000);
                System.out.print(  "5 ");
                contour2 = VitimageUtils.makeOperationOnOneImage(contour2, 2, 255, true);
                contour = VitimageUtils.binaryOperationBetweenTwoImages(contour, contour2, 1);
                System.out.print(  "6 ");
                in = VitimageUtils.makeOperationOnOneImage(ero, 3, 255, true);
                contour = VitimageUtils.makeOperationBetweenTwoImages(contour, in, 1, true);
                System.out.print(  "7 ");
            }
            contour = VitimageUtils.makeOperationOnOneImage(contour, 2, 255, true);
            imgGraph = imgDates.duplicate();
                System.out.print(  "8 ");
            imgGraph = VitimageUtils.makeOperationOnOneImage(imgGraph, 2, 0, true);
            imgGraph = VitimageUtils.convertFloatToByteWithoutDynamicChanges(imgGraph);
            System.out.print(  "9 ");
            if (sizeFactor > 1) {
                int dimX = imgDates.getWidth();
                int dimY = imgDates.getHeight();
                imgGraph = VitimageUtils.uncropImageByte(imgGraph, 0, 0, 0, dimX * sizeFactor, dimY * sizeFactor, 1);

            }
            imgToKeepGraph=new Duplicator().run(imgGraph);
            imgToKeepContour=new Duplicator().run(contour);
        }



        //////////////////////////////////////////////////////// DESSINER D'ABORD LES ÉLÉMENTS EXCLUS (pour qu'ils soient en arrière-plan) ////////////////////////////////////////////////////////////
        double vx = VitimageUtils.getVoxelSizes(imgGraph)[0];
        System.out.print("\n 0bis-Drawing excluded vertices (hollow style)  ");
        int excludedCCCount = 0;
        for (CC cc : graph.vertexSet()) {
            if (!cc.isOut) continue; // Ne dessiner que les exclus
            excludedCCCount++;
            
            double ccx = (cc.x());
            double ccy = (cc.y());
            double factor = 0.3 + 0.7 * Math.log10(cc.nPixels);
            
            // Style "hollow" : cercle extérieur avec couleur normale Fire LUT basée sur cc.day, cercle intérieur noir
            VitimageUtils.drawCircleIntoImage(imgGraph, vx * (factor * circleRadius + 1),
                    (int) Math.round(ccx * sizeFactor), (int) Math.round((ccy) * sizeFactor), 0, cc.day);
            VitimageUtils.drawCircleIntoImage(imgGraph, vx * (factor * circleRadius * 0.6),
                    (int) Math.round(ccx * sizeFactor), (int) Math.round((ccy) * sizeFactor), 0, 0);
        }
        System.out.println(excludedCCCount + " excluded CC drawn");
        
        // Dessiner les edges exclues (fines et très pointillées)
        System.out.print("   0ter-Drawing excluded edges (thin dotted style)  ");
        int excludedEdgeCount = 0;
        for (ConnectionEdge edge : graph.edgeSet()) {
            if (!edge.isOut) continue; // Ne dessiner que les exclus
            excludedEdgeCount++;
            
            CC cc1 = graph.getEdgeSource(edge);
            CC cc2 = graph.getEdgeTarget(edge);
            double cc1x = (cc1.x());
            double cc2x = (cc2.x());
            double cc1y = (cc1.y());
            double cc2y = (cc2.y());
            
            int grayColor = N/4; // rouge/orange
            
            if(!edge.hidden){
                double xCon = edge.connectionX + 0.5;
                double yCon = edge.connectionY + 0.5;
                // Pointillés très espacés : 1 pixel dessiné, 10 pixels vides, épaisseur 2
                drawDottedSegment(imgGraph, 2, grayColor,
                        cc1x * sizeFactor, cc1y * sizeFactor, xCon * sizeFactor, yCon * sizeFactor, 1, 4);
                drawDottedSegment(imgGraph, 2, grayColor,
                        xCon * sizeFactor, yCon * sizeFactor, cc2x * sizeFactor, cc2y * sizeFactor, 1, 4);
            } else {
                // Edge cachée exclue
                drawDottedSegment(imgGraph, 2, grayColor,
                        cc1x * sizeFactor, cc1y * sizeFactor, cc2x * sizeFactor, cc2y * sizeFactor, 1, 20);
            }
        }
        System.out.println(excludedEdgeCount + " excluded edges drawn");


        //////////////////////////////////////////////////////// VERTICES PART 1 : CERCLES PRINCIPAUX (actifs seulement) ////////////////////////////////////////////////////////////
        System.out.print("1-Drawing circles for active vertices  ");
        int incr = 0;
        int decile = graph.vertexSet().size() / 10;
        if (sizeFactor > 4) circleRadius *= 2;
        int stunningCCCount = 0;
        for (CC cc : graph.vertexSet()) {
            if (cc.isOut) continue; // Ignorer les exclus
            if(cc.primaryOfAnEmergingRootAtLastTimeOrFuzzyRegistration!=null) continue;
            if(cc.isArtifactIncludedIntoAnotherCCWithoutTouchingTheBorders) continue;
            if(cc.isArtifactMakingBelieveOfAnOrganStartButIsVerySmallRelativelyToSuccessor) continue;
            double ccx = (cc.x());
            double ccy = (cc.y());
            double factor = 0.3 + 0.7 * Math.log10(cc.nPixels);
            if (((incr++) % decile) == 0) {
                System.out.print(incr + " ");
            }
            boolean extremity = isExtremity(cc, graph);
            
            // Dessiner le cercle principal
            if (extremity) {
                if (cc.nPixels >= 2)
                    VitimageUtils.drawCircleIntoImage(imgGraph, vx * (factor * circleRadius + 3 + (cc.trunk ? 2 : 0))
                            , (int) Math.round(ccx * sizeFactor), (int) Math.round(ccy * sizeFactor), 0, 12);
            } else {
                VitimageUtils.drawCircleIntoImage(imgGraph, vx * (factor * circleRadius + 2 + (cc.trunk ? 2 : 0)),
                        (int) Math.round(ccx * sizeFactor), (int) Math.round((ccy) * sizeFactor), 0, colorMax);
                VitimageUtils.drawCircleIntoImage(imgGraph, vx * (factor * circleRadius + 1 + (cc.trunk ? 1 : 0)),
                        (int) Math.round(ccx * sizeFactor), (int) Math.round((ccy) * sizeFactor), 0, 0);
            }
            if ((int) Math.round((ccx) * sizeFactor) > 0)
                VitimageUtils.drawCircleIntoImage(imgGraph, vx * (factor * circleRadius),
                        (int) Math.round((ccx) * sizeFactor), (int) Math.round((ccy) * sizeFactor), 0, cc.day);
            
            // Compter les CC stunning (dessin fait plus tard pour être au-dessus)
            if (cc.stunningLevel > 0) {
                stunningCCCount++;
            }
        }
        






        ////////////////////////////////////////////////////////DRAW THE EDGES, PART 1 ////////////////////////////////////////////////////////////
        System.out.print("   2-Drawing " + graph.edgeSet().size() + " edges  ");
        boolean cheapEdgeDrawing = graph.edgeSet().size() > 5000;
        if(graph.edgeSet().size()>5000){
            System.out.println("Too many edges to draw, selecting the 5000 best" );
        }
        Set<ConnectionEdge> edgeSet=graph.edgeSet();
        if(cheapEdgeDrawing){
            List<ConnectionEdge> l=new ArrayList<>(graph.edgeSet());
            Collections.sort(l, new Comparator<ConnectionEdge>(){
                @Override
                public int compare(ConnectionEdge o1, ConnectionEdge o2) {
                    return Double.compare(o1.cost, o2.cost);
                }                
            });
            edgeSet=new HashSet<>(l.subList(0, 5000));
        }
        incr = 0;
        double maxCost=-1;
        double minCost=1E8;;
        for(ConnectionEdge edge:edgeSet){
            if(edge.isOut)continue;
            if(edge.activated==false)continue;
            if(edge.cost>maxCost)maxCost=edge.cost;
            if(edge.cost<minCost)minCost=edge.cost;
        }

        System.out.println("Edge cost min="+minCost+" max="+maxCost);
        decile = edgeSet.size() / 10;
        int incrAct = 0;
        int incrNonAct = 0;
        int stunningEdgesCount = 0;
        for (ConnectionEdge edge : edgeSet) {
            boolean debug=(edge==getEdgeWithResolution(graph, 17, 2939, 5472, 18, 2653, 5970));
            if(debug)System.out.println("DEBUG="+debug);
            if (edge.isOut) continue;
            
            // Compter les edges non activées et les gérer selon le paramètre showNonActivatedEdges
            if (!edge.activated) {
                incrNonAct++;
                if (!showNonActivatedEdges) continue; // Skip si on ne veut pas les afficher
            }
            
            if (edge.activated) incrAct++;
            if (edge.stunningLevel > 0) stunningEdgesCount++;
            if (((incr++) % decile) == 0) System.out.print(incr + " ");
            CC cc1 = graph.getEdgeSource(edge);
            CC cc2 = graph.getEdgeTarget(edge);
            double xCon = edge.connectionX + 0.5;
            double yCon = edge.connectionY + 0.5;
            double cc1x = (cc1.x());
            double cc2x = (cc2.x());
            double cc1y = (cc1.y());
            double cc2y = (cc2.y());

            double costNorm=1-(edge.cost - minCost)/(maxCost - minCost);
            int val = 5+(int)((colorMax - 5)*(costNorm));
            if (val < 5) val = 5;
            if (val > colorMax) val = colorMax;
            
            // Override visual parameters for stunning edges, hidden edges or non-activated edges
            int displayVal = val;
            int additionalThickness = 0;
            boolean drawAsMoreDotted = false; // Pour les edges non activées
            
            // Priorité 1 : Stunning edges (hidden ou non-hidden, tous en blanc)
            if (edge.stunningLevel > 0) {
                displayVal = 255; // Bright yellow/white in Fire LUT
                additionalThickness = 2 * edge.stunningLevel;
            }
            // Priorité 2 : Non-activated edges
            else if (!edge.activated) {
                additionalThickness = -2; // Réduire l'épaisseur
                drawAsMoreDotted = true; // Utiliser un style pointillé très prononcé
            }
            // Priorité 3 : Hidden edges non-stunning
            else if (edge.hidden) {
                displayVal = colorMax; // Jaune dans Fire LUT
            }
            
            boolean continuousTrack=(edge.source.order ==edge.target.order);
            if(!edge.hidden){
                // Pour les edges non activées, on dessine avec des pointillés très espacés
                // en dessinant seulement 1 pixel sur 4 au lieu de 1 sur 2
                if (drawAsMoreDotted) {
                    // Dessiner avec pointillés très prononcés : on alterne 2 pixels dessinés, 6 pixels vides
                    drawDottedSegment(imgGraph, Math.max(1, lineThickness + (continuousTrack ? 4 : 0) + additionalThickness), displayVal,
                            (cc1x) * sizeFactor, (cc1y) * sizeFactor, xCon * sizeFactor, yCon * sizeFactor, 2, 6);
                    drawDottedSegment(imgGraph, Math.max(1, lineThickness + (continuousTrack ? 4 : 0) + additionalThickness), displayVal,
                            xCon * sizeFactor, yCon * sizeFactor, cc2x * sizeFactor, cc2y * sizeFactor, 2, 6);
                } else {
                    // Dessiner l'edge principale
                    VitimageUtils.drawSegmentInto2DByteImage(imgGraph, lineThickness + (continuousTrack ? 4 : 0) + additionalThickness, displayVal,
                            (cc1x) * sizeFactor, (cc1y) * sizeFactor, xCon * sizeFactor, yCon * sizeFactor, edge.hidden);
                    VitimageUtils.drawSegmentInto2DByteImage(imgGraph, lineThickness + (continuousTrack ? 4 : 0) + additionalThickness, displayVal,
                            xCon * sizeFactor, yCon * sizeFactor, cc2x * sizeFactor, cc2y * sizeFactor, edge.hidden);
                    
                    // Pour les edges continuousTrack activées : ajouter des bordures noir et blanc pour les mettre en valeur
                    if (continuousTrack && edge.activated && edge.stunningLevel == 0) {
                        int mainThickness = lineThickness + 4 + additionalThickness;
                        // Trait blanc extérieur (N+1)
                        VitimageUtils.drawSegmentInto2DByteImage(imgGraph, mainThickness + 2, N+1,
                                (cc1x) * sizeFactor, (cc1y) * sizeFactor, xCon * sizeFactor, yCon * sizeFactor, edge.hidden);
                        VitimageUtils.drawSegmentInto2DByteImage(imgGraph, mainThickness + 2, N+1,
                                xCon * sizeFactor, yCon * sizeFactor, cc2x * sizeFactor, cc2y * sizeFactor, edge.hidden);
                        // Trait noir intermédiaire (0)
                        //VitimageUtils.drawSegmentInto2DByteImage(imgGraph, mainThickness + 1, 0,
                        //        (cc1x) * sizeFactor, (cc1y) * sizeFactor, xCon * sizeFactor, yCon * sizeFactor, edge.hidden);
                        //VitimageUtils.drawSegmentInto2DByteImage(imgGraph, mainThickness + 1, 0,
                        //        xCon * sizeFactor, yCon * sizeFactor, cc2x * sizeFactor, cc2y * sizeFactor, edge.hidden);
                        // Re-dessiner l'edge colorée par-dessus
                        VitimageUtils.drawSegmentInto2DByteImage(imgGraph, mainThickness, displayVal,
                                (cc1x) * sizeFactor, (cc1y) * sizeFactor, xCon * sizeFactor, yCon * sizeFactor, edge.hidden);
                        VitimageUtils.drawSegmentInto2DByteImage(imgGraph, mainThickness, displayVal,
                                xCon * sizeFactor, yCon * sizeFactor, cc2x * sizeFactor, cc2y * sizeFactor, edge.hidden);
                    }
                }
                if(!continuousTrack && !drawAsMoreDotted){
                    VitimageUtils.drawSegmentInto2DByteImage(imgGraph, 1 + (0), 0, cc1x * sizeFactor,
                            cc1y * sizeFactor, xCon * sizeFactor, yCon * sizeFactor, edge.hidden);
                    VitimageUtils.drawSegmentInto2DByteImage(imgGraph, 1 + (0), 0, xCon * sizeFactor,
                            yCon * sizeFactor, cc2x * sizeFactor, cc2y * sizeFactor, edge.hidden);
                }
                if (!drawAsMoreDotted) {
                    VitimageUtils.drawCircleIntoImage(imgGraph, 3, (int) Math.round(xCon * sizeFactor),
                            (int) Math.round(yCon * sizeFactor), 0, 12);
                }
            }
            else{
                // Edges cachées (hidden)
                if (drawAsMoreDotted) {
                    // Hidden non activée (cas rare) : pointillés
                    drawDottedSegment(imgGraph, Math.max(1, lineThickness + (continuousTrack ? 4 : 0) + additionalThickness), displayVal,
                        (cc1x) * sizeFactor, (cc1y) * sizeFactor, cc2x * sizeFactor, cc2y * sizeFactor, 2, 6);
                } else {
                    // Hidden activée : dessiner les segments via les facettes intermédiaires
                    int thickness = lineThickness + (continuousTrack ? 4 : 0) + additionalThickness;
                    
                    if (edge.hiddenConnectingFacets != null && !edge.hiddenConnectingFacets.isEmpty()) {
                        // Dessiner : source -> facette1 -> facette2 -> ... -> facetteN -> target
                        
                        // Segment 1 : source (cc1) -> première facette
                        double[] firstFacet = edge.hiddenConnectingFacets.get(0);
                        VitimageUtils.drawSegmentInto2DByteImage(imgGraph, thickness, displayVal,
                            cc1x * sizeFactor, cc1y * sizeFactor, 
                            firstFacet[0] * sizeFactor, firstFacet[1] * sizeFactor, edge.hidden);
                        
                        // Segments intermédiaires : facette[i] -> facette[i+1]
                        for (int i = 0; i < edge.hiddenConnectingFacets.size() - 1; i++) {
                            double[] facetFrom = edge.hiddenConnectingFacets.get(i);
                            double[] facetTo = edge.hiddenConnectingFacets.get(i + 1);
                            VitimageUtils.drawSegmentInto2DByteImage(imgGraph, thickness, displayVal,
                                facetFrom[0] * sizeFactor, facetFrom[1] * sizeFactor,
                                facetTo[0] * sizeFactor, facetTo[1] * sizeFactor, edge.hidden);
                        }
                        
                        // Segment final : dernière facette -> target (cc2)
                        double[] lastFacet = edge.hiddenConnectingFacets.get(edge.hiddenConnectingFacets.size() - 1);
                        VitimageUtils.drawSegmentInto2DByteImage(imgGraph, thickness, displayVal,
                            lastFacet[0] * sizeFactor, lastFacet[1] * sizeFactor,
                            cc2x * sizeFactor, cc2y * sizeFactor, edge.hidden);
                        
                        // Segment central noir SEULEMENT si c'est un branchement (changement d'ordre)
                        if (!continuousTrack) {
                            // Dessiner le segment noir central sur tous les segments
                            VitimageUtils.drawSegmentInto2DByteImage(imgGraph, 1, 0,
                                cc1x * sizeFactor, cc1y * sizeFactor,
                                firstFacet[0] * sizeFactor, firstFacet[1] * sizeFactor, edge.hidden);
                            
                            for (int i = 0; i < edge.hiddenConnectingFacets.size() - 1; i++) {
                                double[] facetFrom = edge.hiddenConnectingFacets.get(i);
                                double[] facetTo = edge.hiddenConnectingFacets.get(i + 1);
                                VitimageUtils.drawSegmentInto2DByteImage(imgGraph, 1, 0,
                                    facetFrom[0] * sizeFactor, facetFrom[1] * sizeFactor,
                                    facetTo[0] * sizeFactor, facetTo[1] * sizeFactor, edge.hidden);
                            }
                            
                            VitimageUtils.drawSegmentInto2DByteImage(imgGraph, 1, 0,
                                lastFacet[0] * sizeFactor, lastFacet[1] * sizeFactor,
                                cc2x * sizeFactor, cc2y * sizeFactor, edge.hidden);
                        }
                        
                        // Dessiner les petits carrés noirs aux positions des facettes (comme pour les edges non-hidden)
                        for (double[] facet : edge.hiddenConnectingFacets) {
                            VitimageUtils.drawCircleIntoImage(imgGraph, 3, (int) Math.round(facet[0] * sizeFactor),
                                (int) Math.round(facet[1] * sizeFactor), 0, 12);
                        }
                    } else {
                        // Fallback : si pas de facettes, dessiner direct (ancien comportement)
                        VitimageUtils.drawSegmentInto2DByteImage(imgGraph, thickness, displayVal,
                            cc1x * sizeFactor, cc1y * sizeFactor, cc2x * sizeFactor, cc2y * sizeFactor, edge.hidden);
                        
                        if (!continuousTrack) {
                            VitimageUtils.drawSegmentInto2DByteImage(imgGraph, 1, 0,
                                cc1x * sizeFactor, cc1y * sizeFactor, cc2x * sizeFactor, cc2y * sizeFactor, edge.hidden);
                        }
                    }
                }
            }
            
            // Préparer le texte descriptif pour les stunning edges (sera dessiné à la fin)
            if (edge.stunningLevel > 0 && edge.stunningReasons != null && !edge.stunningReasons.isEmpty()) {
                // Pour les hidden edges, utiliser la première facette comme point de référence
                double textRefX, textRefY;
                if (edge.hidden && edge.hiddenConnectingFacets != null && !edge.hiddenConnectingFacets.isEmpty()) {
                    // Utiliser la première facette
                    double[] firstFacet = edge.hiddenConnectingFacets.get(0);
                    textRefX = firstFacet[0];
                    textRefY = firstFacet[1];
                } else {
                    // Pour les edges normaux, utiliser le point de connexion
                    textRefX = xCon;
                    textRefY = yCon;
                }
                
                // Ajouter de l'aléatoire pour éviter les chevauchements (surtout en Y)
                double randomOffsetX = (Math.random() - 1) * 15 * sizeFactor; // +/- 15*sizeFactor en X
                double randomOffsetY = (Math.random() - 0.5) * 25 * sizeFactor; // +/- 25*sizeFactor en Y

                // Positionner le texte près du point de référence, en haut à gauche à environ 4*sizeFactor
                int textX = (int) Math.round(textRefX * sizeFactor - 11 * sizeFactor + randomOffsetX);
                int textY = (int) Math.round(textRefY * sizeFactor - 5 * sizeFactor + randomOffsetY);
                
                // Construire le texte avec une ligne par stunning situation
                StringBuilder textBuilder = new StringBuilder();
                for (int i = 0; i < edge.stunningReasons.size(); i++) {
                    if (i > 0) textBuilder.append("\n");
                    String reason = edge.stunningReasons.get(i);
                    double value = edge.stunningMADValues.get(i);
                    String acronym = getStunningAcronym(reason);
                    textBuilder.append(acronym).append(":").append(dou(value));
                }
                
                // STOCKER pour dessiner plus tard (au-dessus de tout)
                if (textBuilder.length() > 0) {
                    // Calculer la position du bas du texte (nombre de lignes * hauteur de ligne)
                    int nbLines = edge.stunningReasons.size();
                    int lineHeight = 14;
                    int textBottomX = textX+sizeFactor*4;
                    int textBottomY = textY + nbLines * lineHeight-sizeFactor*2;
                    
                    stunningTextsToDrawLater.add(new StunningTextInfo(
                        textBuilder.toString(), textX, textY, textBottomX, textBottomY,
                        textRefX * sizeFactor, textRefY * sizeFactor, (N*10)/21
                    ));
                }
            }
        }
        System.out.println("Numbers edges : "+incrAct + " activated and " + incrNonAct + " non-activated" + 
                          (stunningEdgesCount > 0 ? " (including " + stunningEdgesCount + " stunning edges)" : ""));
        
        
        ////////////////////////////////////////////////////////DRAW THE VERTICES, PART 2 : contour and square ////////////////////////////////////////////////////////////
        System.out.print("   3-Drawing central square for " + graph.vertexSet().size() + " vertices  ");
        incr = 0;
        decile = graph.vertexSet().size() / 10;
        for (CC cc : graph.vertexSet()) {
            if (cc.isOut) continue;
            if(cc.stunningLevel>0) continue;
            if(cc.isArtifactIncludedIntoAnotherCCWithoutTouchingTheBorders) continue;
            if(cc.isArtifactMakingBelieveOfAnOrganStartButIsVerySmallRelativelyToSuccessor) continue;
            double ccx = (cc.x());
            double ccy = (cc.y());
            if (((incr++) % decile) == 0) System.out.print(incr + " ");
            boolean extremity = isExtremity(cc, graph);
            if (cc.nPixels >= MIN_SIZE_CC || !extremity)
                VitimageUtils.drawCircleIntoImage(imgGraph, vx * 3, (int) Math.round(ccx * sizeFactor),
                        (int) Math.round(ccy * sizeFactor), 0, extremity ? 12 : 0);
            if (cc.nPixels >= MIN_SIZE_CC || !extremity)
                VitimageUtils.drawCircleIntoImage(imgGraph, vx * 2, (int) Math.round(ccx * sizeFactor),
                        (int) Math.round(ccy * sizeFactor), 0, extremity ? 12 : colorMax);
        }
        
        //Draw stunning CC markers (dessiner en dernier pour être au-dessus de tout)
        System.out.print("\n   3bis-Drawing stunning markers for " + stunningCCCount + " stunning CCs  ");
        incr = 0;
        decile = stunningCCCount > 0 ? stunningCCCount / 10 : 1;
        for (CC cc : graph.vertexSet()) {
            if (cc.isOut) continue;
            if (cc.stunningLevel == 0) continue; // Ne dessiner que les CC stunning
            if(cc.isArtifactIncludedIntoAnotherCCWithoutTouchingTheBorders) continue;
            
            double ccx = (cc.x());
            double ccy = (cc.y());
            if (((incr++) % decile) == 0) System.out.print(incr + " ");
            
            // Dessiner les anneaux concentriques blanc/noir pour marquer le CC stunning
            // Anneaux concentriques alternant noir (0) et blanc (255)
            VitimageUtils.drawCircleIntoImage(imgGraph, vx * sizeFactor * 3,
                    (int) Math.round(ccx * sizeFactor), (int) Math.round((ccy) * sizeFactor), 0,0 );            
            VitimageUtils.drawCircleIntoImage(imgGraph, vx * sizeFactor * 2.5,
                    (int) Math.round(ccx * sizeFactor), (int) Math.round((ccy) * sizeFactor), 0, N+1);
            VitimageUtils.drawCircleIntoImage(imgGraph, vx * sizeFactor * 2,
                    (int) Math.round(ccx * sizeFactor), (int) Math.round((ccy) * sizeFactor), 0, 0);
            VitimageUtils.drawCircleIntoImage(imgGraph, vx * sizeFactor * 1.5,
                    (int) Math.round(ccx * sizeFactor), (int) Math.round((ccy) * sizeFactor), 0, N+1);
            VitimageUtils.drawCircleIntoImage(imgGraph, vx * sizeFactor * 1,
                    (int) Math.round(ccx * sizeFactor), (int) Math.round((ccy) * sizeFactor), 0, 0);
            VitimageUtils.drawCircleIntoImage(imgGraph, vx * sizeFactor * 0.5,
                    (int) Math.round(ccx * sizeFactor), (int) Math.round((ccy) * sizeFactor), 0, N+1);
            
            // Préparer le texte descriptif des stunning reasons (sera dessiné à la fin)
            // Ajouter de l'aléatoire pour éviter les chevauchements (surtout en Y)
            double randomOffsetX = (Math.random() - 1) * 15 * sizeFactor; // +/- 15*sizeFactor en X
            double randomOffsetY = (Math.random() - 0.5) * 25 * sizeFactor; // +/- 25*sizeFactor en Y

            // Positionner le texte en haut à gauche du CC, à environ 4*sizeFactor du centre
            int textX = (int) Math.round(ccx * sizeFactor - 11 * sizeFactor + randomOffsetX);
            int textY = (int) Math.round(ccy * sizeFactor - 5 * sizeFactor + randomOffsetY);
            
            // Construire le texte avec une ligne par stunning situation
            StringBuilder textBuilder = new StringBuilder();
            for (int i = 0; i < cc.stunningReasons.size(); i++) {
                if (i > 0) textBuilder.append("\n");
                String reason = cc.stunningReasons.get(i);
                double value = cc.stunningMADValues.get(i);
                String acronym = getStunningAcronym(reason);
                textBuilder.append(acronym).append(":").append(doudou(value));
            }
            
            // STOCKER pour dessiner plus tard (au-dessus de tout)
            if (textBuilder.length() > 0) {
                // Calculer la position du bas du texte (nombre de lignes * hauteur de ligne)
                int nbLines = cc.stunningReasons.size();
                int lineHeight = 14;
                int textBottomX = textX+sizeFactor*4;
                int textBottomY = textY + nbLines * lineHeight-sizeFactor*2;
                
                stunningTextsToDrawLater.add(new StunningTextInfo(
                    textBuilder.toString(), textX, textY, textBottomX, textBottomY,
                    ccx * sizeFactor, ccy * sizeFactor, (N*10)/21
                ));
            }
        }

        //Draw stunning neighbourhood sum markers (cercles blancs concentriques)
        System.out.print("\n   3ter-Drawing stunning neighbourhood sum markers  ");
        incr = 0;
        int ccWithStunningNeighboursCount = 0;
        for (CC cc : graph.vertexSet()) {
            if (cc.isOut) continue;
            if (cc.stunningLevelSumNeighbours == 0) continue;
            ccWithStunningNeighboursCount++;
        }
        decile = ccWithStunningNeighboursCount > 0 ? ccWithStunningNeighboursCount / 10 : 1;
        
        for (CC cc : graph.vertexSet()) {
            if (cc.isOut) continue;
            if (cc.stunningLevelSumNeighbours == 0) continue; // Ne dessiner que si stunning > 0 dans le voisinage
            if(cc.isArtifactIncludedIntoAnotherCCWithoutTouchingTheBorders) continue;
            
            double ccx = (cc.x());
            double ccy = (cc.y());
            if (((incr++) % decile) == 0) System.out.print(incr + " ");
            
            // Dessiner les cercles blancs concentriques
            // Radius de départ : 6 x sizeFactor
            // Incrément : 2 pixels par cercle
            // Nombre de cercles : stunningLevelSumNeighbours
            int nbCircles = cc.stunningLevelSumNeighbours;
            double startRadius = 6.0 * sizeFactor;
            
            for (int i = 0; i < nbCircles; i++) {
                double radius = startRadius + (i * 3);
                drawCircleContourIntoImage(imgGraph, vx * radius,(int) Math.round(ccx * sizeFactor), (int) Math.round(ccy * sizeFactor), 0, N+1,1.5); // N+1 = blanc
            }
        }

        //Draw order numbers on each CC for debugging (dessiner en dernier pour être au-dessus de tout)
        System.out.print("\n   3quater-Drawing order numbers for debugging  ");
        incr = 0;
        decile = graph.vertexSet().size() / 10;
        for (CC cc : graph.vertexSet()) {
            if (cc.isOut) continue; // Ignorer les exclus
            if (((incr++) % decile) == 0) System.out.print(incr + " ");
            
            double ccx = (cc.x());
            double ccy = (cc.y());
            
            // Dessiner le numéro d'ordre centré sur le CC en noir (valeur 0)
            String orderText = String.valueOf(cc.order);
            int textX = (int) Math.round(ccx * sizeFactor);
            int textY = (int) Math.round(ccy * sizeFactor+3);
            
            // Utiliser drawTextOnImage pour écrire le numéro
            ImageProcessor ip = imgGraph.getProcessor();
            ip.setColor(1); // Noir
            ip.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD , 15));
            
            // Centrer approximativement le texte (en tenant compte de la largeur du texte)
            java.awt.FontMetrics fm = ip.getFontMetrics();
            int textWidth = fm.stringWidth(orderText);
            int textHeight = fm.getHeight();
            
            ip.drawString(orderText, textX - textWidth/2, textY + textHeight/3);
        }

        // DESSINER TOUS LES TEXTES STUNNING À LA FIN (au-dessus de tout)
        System.out.print("\n   3quinquies-Drawing " + stunningTextsToDrawLater.size() + " stunning texts on top  ");
        for (StunningTextInfo textInfo : stunningTextsToDrawLater) {
            // Dessiner le texte sur l'image
            drawTextOnImage(imgGraph, textInfo.text, textInfo.textX, textInfo.textY, textInfo.color);
            
            // Dessiner la ligne reliant le bas du texte au point de référence
            VitimageUtils.drawSegmentInto2DByteImage(imgGraph, 1, textInfo.color,
                textInfo.textBottomX, textInfo.textBottomY,
                textInfo.refX, textInfo.refY, false);
        }
        System.out.println("Ok.");

        imgDates.setDisplayRange(0, N + 1);
        System.out.print(" Ok.\n   4-High res misc drawing (100M +) ");

        System.out.print("1 ");//Build graphArea, a mask of all pixels where something is drawn (edges or vertices)
        ImagePlus graphArea = VitimageUtils.thresholdImage(imgGraph, 0.5, 1000000);
        graphArea = VitimageUtils.getBinaryMaskUnary(graphArea, 0.5);

        System.out.print("2 ");//Build contourArea, a mask of all contours, excepted pixels of graphArea
        ImagePlus contourArea = VitimageUtils.thresholdImage(contour, 0.5, 1000000000);
        contourArea = VitimageUtils.getBinaryMaskUnary(contourArea, 0.5);
        contourArea = VitimageUtils.binaryOperationBetweenTwoImages(contourArea, graphArea, 4);

        System.out.print("3 ");
        ImagePlus part1 = VitimageUtils.makeOperationBetweenTwoImages(imgGraph, graphArea, 2, true);//Draw pixels of
        // graph
        ImagePlus part2 = VitimageUtils.makeOperationBetweenTwoImages(contour, contourArea, 2, true);//Draw pixels of
        // contour

        System.out.print("4 ");
        imgGraph = VitimageUtils.makeOperationBetweenTwoImages(part1, part2, 1, false);
        imgGraph.setDisplayRange(0, N + 1);
        System.out.print(" Ok.  ");

        return new ImagePlus[]{imgGraph, imgToKeepGraph,imgToKeepContour};
    }


    /**
     * Helper function to generate acronyms from stunning reason codes
     * Takes the first letter of each word in the reason code
     */
    private static String getStunningAcronym(String reason) {
        if (reason == null || reason.isEmpty()) return "";
        
        // Map des acronymes pour chaque type de stunning reason
        switch (reason) {
            case "RADIUS_OUTLIER": return "RO";
            case "RADIUS_SUDDEN_CHANGE": return "RSC";
            case "FINAL_TIME_OUTLIER": return "FTO";
            case "SHARP_ANGLE_INTERMEDIATE": return "SAI";
            case "SHARP_ANGLE_TERMINAL": return "SAT";
            case "RADIUS_OUTLIER_IN_TARGET": return "ROIT";
            case "RADIUS_OUTLIER_IN_SOURCE": return "ROIS"; 
            case "RADIUS_SUDDEN_CHANGE_AT_TARGET": return "RSCT";
            case "RADIUS_SUDDEN_CHANGE_AT_SOURCE": return "RSCS";
            case "SHARP_ANGLE_AT_TARGET": return "SAAT";
            case "SHARP_ANGLE_AT_SOURCE": return "SAAS";
            case "SHARP_ANGLE_AT_TERMINAL_TARGET": return "SATT";
            case "SHARP_EDGE_ANGLE": return "SEA";
            default:
                // Fallback: prendre la première lettre de chaque mot
                String[] words = reason.split("_");
                StringBuilder acronym = new StringBuilder();
                for (String word : words) {
                    if (!word.isEmpty()) {
                        acronym.append(word.charAt(0));
                    }
                }
                return acronym.toString();
        }
    }
    
    /**
     * Helper function to draw a dotted segment with custom pattern
     * @param img The image to draw on
     * @param thickness Line thickness
     * @param color Color value
     * @param x1, y1 Start point
     * @param x2, y2 End point
     * @param drawLength Number of pixels to draw in each dot
     * @param skipLength Number of pixels to skip between dots
     */
    private static void drawDottedSegment(ImagePlus img, int thickness, int color,
                                         double x1, double y1, double x2, double y2,
                                         int drawLength, int skipLength) {
        // Calculer la longueur totale du segment
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);
        
        if (length == 0) return;
        
        // Normaliser le vecteur direction
        double ux = dx / length;
        double uy = dy / length;
        
        // Pattern total = drawLength + skipLength
        int patternLength = drawLength + skipLength;
        
        // Dessiner les segments pointillés
        double currentPos = 0;
        while (currentPos < length) {
            // Position de début du trait
            double startX = x1 + ux * currentPos;
            double startY = y1 + uy * currentPos;
            
            // Position de fin du trait (ne pas dépasser la fin totale)
            double endPos = Math.min(currentPos + drawLength, length);
            double endX = x1 + ux * endPos;
            double endY = y1 + uy * endPos;
            
            // Dessiner ce segment
            VitimageUtils.drawSegmentInto2DByteImage(img, thickness, color,
                startX, startY, endX, endY, false);
            
            // Avancer au prochain segment
            currentPos += patternLength;
        }
    }
    
    /**
     * Helper function to draw text on an image
     * Draws text character by character using simple pixel-based rendering
     */
    private static void drawTextOnImage(ImagePlus img, String text, int x, int y, int color) {
        if (text == null || text.isEmpty()) return;
        
        // Utiliser ImageJ pour dessiner le texte
        ImageProcessor ip = img.getProcessor();
        ip.setColor(color);
        ip.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 10));
        
        // Dessiner chaque ligne du texte
        String[] lines = text.split("\n");
        int lineHeight = 14; // hauteur approximative d'une ligne de texte
        for (int i = 0; i < lines.length; i++) {
            ip.drawString(lines[i], x, y + i * lineHeight);
        }
    }

    /**
     * Print a summary of all exclusions for debugging/analysis
     */
    private static void printExclusionSummary() {
        System.out.println("\n========== EXCLUSION SUMMARY ==========");
        System.out.println("Total excluded CC (full trees): " + excludedCCFullTrees.size());
        System.out.println("Total excluded edges (non-activated): " + excludedEdgesNonActivated.size());
        
        // Compter par raison d'exclusion
        HashMap<String, Integer> reasonCounts = new HashMap<>();
        for(String reason : exclusionReasons.values()) {
            reasonCounts.put(reason, reasonCounts.getOrDefault(reason, 0) + 1);
        }
        
/*         System.out.println("\nCC exclusion breakdown:");
        for(Map.Entry<String, Integer> entry : reasonCounts.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue());
        }*/
        
        HashMap<String, Integer> edgeReasonCounts = new HashMap<>();
        for(String reason : edgeExclusionReasons.values()) {
            edgeReasonCounts.put(reason, edgeReasonCounts.getOrDefault(reason, 0) + 1);
        }
        
         System.out.println("\nEdge exclusion breakdown:");
        for(Map.Entry<String, Integer> entry : edgeReasonCounts.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue());
        }
        System.out.println("=======================================\n");
    }





    /**
     * -------------------------------------------------------------------------------------------------------------------------------------------
      * -------------------------------------------------------------------------------------------------------------------------------------------
     * Other helpers *************************************************************************************
      * -------------------------------------------------------------------------------------------------------------------------------------------
     * -------------------------------------------------------------------------------------------------------------------------------------------
     */

    /**
     * Sort of a median filtering : every pixel surrounded with data different from zero will be replaced by the most represented value in its
     * surroundings
     */
    public static void medianFilteringOfImageDates(ImagePlus img) {
        ImagePlus test=img.duplicate();
        test.setTitle("Before median filtering");
        int X = img.getWidth();
        int Y = img.getHeight();
        short[] tabData = (short[]) img.getStack().getPixels(1);
        for (int x = 1; x < X - 1; x++){
            for (int y = 1; y < Y - 1; y++) { 
                
                if(x==625 && y==722){
                }//if (toInt(tabData[y * X + x]) != 0) continue;
                int[] vals = new int[]{
                       Utils.toInt(tabData[(y - 1) * X + (x - 1)]), Utils.toInt(tabData[(y - 1) * X + (x)]),
                        Utils.toInt(tabData[(y - 1) * X + (x + 1)]),
                        Utils.toInt(tabData[(y) * X + (x - 1)]), Utils.toInt(tabData[(y) * X + (x + 1)]),
                        Utils.toInt(tabData[(y + 1) * X + (x - 1)]), Utils.toInt(tabData[(y + 1) * X + (x)]),
                        Utils.toInt(tabData[(y + 1) * X + (x + 1)])};
                int b = 1;
                for (int i = 0; i < 8; i++) b = b * vals[i];
                if (b == 0) continue;
                b = MostRepresentedFilter.mostRepresentedValueWithBGExclusion(vals, new double[8], 256,0);
                tabData[y * X + x] = Utils.toShort(b);
            }
        }
    }

    /**
     * -------------------------------------------------------------------------------------------------------------------------------------------
      * -------------------------------------------------------------------------------------------------------------------------------------------
     * Main processor : solving of min cost problem *************************************************************************************
      * -------------------------------------------------------------------------------------------------------------------------------------------
     * -------------------------------------------------------------------------------------------------------------------------------------------
     */
    public static void buildStep7SolveMinCostAndActivateOptimalEdges(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, PipelineParamHandler pph){
        // Build an OR graph and solve the min cost problem
        // See topologicaltracking.MinCostRootSystemArchitectureSolver.java for details
        //double[][] costMatrix=getGraphAsCostMatrixForSolver(graph);
        for (ConnectionEdge edge:graph.edgeSet())edge.activated=false;
        ArrayList<CC> ccList = new ArrayList<>(graph.vertexSet());
        MinCostRootSystemArchitectureSolver solver = new MinCostRootSystemArchitectureSolver(ccList.size());
        double[][] costMatrix=getGraphAsCostMatrixForSolver(graph,ccList);
        solver.setupCostsAndCapacities(costMatrix,COST_START,COST_END);
        ArrayList<int[]> solution=solver.solveWithORTools();
        for(int[] matching:solution){
            if(matching[0]<0){
                ccList.get(matching[1]).isStart=true;
            }
            else if(matching[1]<0){
                ccList.get(matching[0]).isEnd=true;
            }
            else{
                CC source=ccList.get(matching[0]);
                CC target=ccList.get(matching[1]);
                ConnectionEdge edge=graph.getEdge(source, target);
                if(edge!=null)edge.activated=true;
            }
        }
    }

    public static double[][] getGraphAsCostMatrixForSolver(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph,ArrayList<CC> ccList){
        int n=graph.vertexSet().size();
        double[][] costMatrix=new double[n][n];
        for(int i=0;i<n;i++)for(int j=0;j<n;j++)costMatrix[i][j]=-1;
        
        //Collect the list of target of edge that are already activated (validated formally, and prevent that they can be proposed to another source)
        ArrayList<CC> activatedTargets = new ArrayList<>();
        for(ConnectionEdge edge:graph.edgeSet()){
            if(edge.activated)activatedTargets.add(edge.target);
        }

        for(ConnectionEdge edge:graph.edgeSet()){
            int i=ccList.indexOf(edge.source);
            int j=ccList.indexOf(edge.target);
            double cost=edge.cost;
            if(activatedTargets.contains(edge.target) && (!edge.activated))continue;

            if(cost>HUGE_COST)cost=HUGE_COST;
            costMatrix[i][j]=cost;
        }
        return costMatrix;
    }



    public static void oldbuildStep03EstimateTypicalSpeeds(SimpleDirectedWeightedGraph<CC, ConnectionEdge> graph, PipelineParamHandler pph) {
        maxCatS0=new double[nCategoriesSpeedStats];
        maxCatS0OverS1=new double[nCategoriesSpeedStats][nCategoriesSpeedStats];
        statsOfCategories=new double[nCategoriesSpeedStats][nCategoriesSpeedStats][3];
        //Collect the list of the best incoming edges (activated if any) for each CC
        ArrayList<ConnectionEdge> listEdges=new ArrayList<>();
        for(CC cc:graph.vertexSet()){
            ConnectionEdge edge=cc.bestIncomingActivatedEdge();
            if(edge!=null){
                listEdges.add(edge);
                continue;
            }
            edge=cc.bestIncomingEdge();
            if(edge!=null)listEdges.add(edge);
        }
        System.out.println("Nb of edges considered for speed estimation : "+listEdges.size()+" , over "+nCategoriesSpeedStats+" x "+nCategoriesSpeedStats+" categories"   );

        //For each edge, compute the velocity in pix/hour the surface per hour of source and the surface per hour of the target
        ArrayList<double[]> values = new ArrayList<>();
        ArrayList<ArrayList<double[]>> valueValues=new ArrayList<ArrayList<double[]>>();
        ArrayList<ArrayList<ArrayList<double[]>>> valueValueValues=new ArrayList<ArrayList<ArrayList<double[]>>>();
        for(int c1=0;c1<nCategoriesSpeedStats;c1++){
            valueValues.add(new ArrayList<>());
            valueValueValues.add(new ArrayList<>());
            for(int c2=0;c2<nCategoriesSpeedStats;c2++){
                valueValueValues.get(c1).add(new ArrayList<>());
            }
        }

        for(ConnectionEdge edge:listEdges){
           double speed=computeVelocityOfNonHiddenEdgeInPixPerHour(edge,pph);
           double surfSourcePerHour=edge.source.nPixels/(edge.source.hourGuessedOfTip-edge.source.hourGuessedOfStart+(edge.source.hourGuessedOfTip==edge.source.hourGuessedOfStart?pph.typicalHourDelay/2.0:VitimageUtils.EPSILON));
           double surfTargetPerHour=edge.target.nPixels/(edge.target.hourGuessedOfTip-edge.target.hourGuessedOfStart+(edge.target.hourGuessedOfTip==edge.target.hourGuessedOfStart?pph.typicalHourDelay/2.0:VitimageUtils.EPSILON));
           double surfSourceOverTarget=surfSourcePerHour/surfTargetPerHour;
           values.add(new double[]{Math.log(surfSourcePerHour),Math.log(surfSourceOverTarget),Math.log(speed)});
           statsAllFirstEdges.add(new double[]{Math.log(surfSourcePerHour),Math.log(surfSourceOverTarget),Math.log(speed)});
        }

        //Divide the populations into 7 categories regarding the first value (log of speed), keep in memory the boundaries of each category
        Collections.sort(values, new Comparator<double[]>() {
            @Override
            public int compare(double[] o1, double[] o2) {
                return Double.compare(o1[0], o2[0]);
            }
        });
        for(int c=1;c<nCategoriesSpeedStats;c++){
            maxCatS0[c-1]=values.get(c*values.size()/nCategoriesSpeedStats)[0];
            //System.out.println("Category "+c+" max log speed = "+maxCatS0[c-1]);
        }
        maxCatS0[nCategoriesSpeedStats-1]=Double.MAX_VALUE;

        //Extract the corresponding population
        for(double[] value:values){
            for(int c=0;c<nCategoriesSpeedStats;c++){
                if(value[0]<maxCatS0[c]){
                    valueValues.get(c).add(value);
                    continue;
                }
            }
        }
        
        //For each category, divide the populations into 7 categories regarding the second value (log of surfSourcePerHour), keep in memory the boundaries of each category
        for(int c1=0;c1<nCategoriesSpeedStats;c1++){
            Collections.sort(valueValues.get(c1), new Comparator<double[]>() {
                @Override
                public int compare(double[] o1, double[] o2) {
                    return Double.compare(o1[1], o2[1]);
                }
            });
            for(int c2=1;c2<nCategoriesSpeedStats;c2++){
                maxCatS0OverS1[c1][c2-1]=valueValues.get(c1).get(c2*valueValues.get(c1).size()/nCategoriesSpeedStats)[1];
            }
            maxCatS0OverS1[c1][nCategoriesSpeedStats-1]=Double.MAX_VALUE;

            //Extract the corresponding population
             for(double[] value:valueValues.get(c1)){
                for(int c2=0;c2<nCategoriesSpeedStats   ;c2++){
                    if(value[1]<maxCatS0OverS1[c1][c2]){
                        valueValueValues.get(c1).get(c2).add(value);
                        continue;
                    }
                }
            }
        }

        //For each category, compute the median and the median absolute deviation
        for(int c1=0;c1<nCategoriesSpeedStats;c1++){
            //System.out.println();
            for(int c2=0;c2<nCategoriesSpeedStats;c2++){
                boolean debug=false;
                double[]vals=new double[valueValueValues.get(c1).get(c2).size()];
                for(int i=0;i<vals.length;i++)vals[i]=valueValueValues.get(c1).get(c2).get(i)[2];
                double[] stats=MADeStatsDoubleSidedWithEpsilon(vals);
                statsOfCategories[c1][c2]=new double[]{stats[0], stats[1],stats[2]};
                if(debug)System.out.println("Category "+c1+"-"+c2+
                "   LogS0=[" +(c1==0 ? "-inf" : maxCatS0[c1-1])+ " ; "+maxCatS0[c1]+"]"+
                "   LogS1=[" +(c2==0 ? "-inf" : maxCatS0OverS1[c1][c2-1])+ " ; "+maxCatS0OverS1[c1][c2]+"]"+
                "   Median = "+dou(stats[0])+" , mad- = "+dou(stats[1])+" , mad+ = "+dou(stats[2]));
                if(debug)if(c1==nCategoriesSpeedStats-1 && c2==0){
                    for(int i=0;i<vals.length  ;i++){
                        System.out.println(dou(valueValueValues.get(c1).get(c2).get(i)[0])+" , "+dou(valueValueValues.get(c1).get(c2).get(i)[1])+" -> "+dou(valueValueValues.get(c1).get(c2).get(i)[2])+"  -- "+(dou(valueValueValues.get(c1).get(c2).get(i)[2]-dou(valueValueValues.get(c1).get(c2).get(i)[0])) +" - "+dou(valueValueValues.get(c1).get(c2).get(i)[2]+dou(valueValueValues.get(c1).get(c2).get(i)[1]))));
                    }
                }

                /*System.out.println("Category "+c1+"-"+c2+
                " with S0 from "+( (c1==0 ? "-inf" : maxCatS0[c1-1])+
                " to ") + ( (c1==nCategoriesSpeedStats-1 ? "+inf" : maxCatS0[c1]) )+
                "   ,   and S1 from "+( (c2==0 ? "-inf --> " : maxCatS0OverS1[c1][c2-1])+
                " to ") + ( (c2==nCategoriesSpeedStats-1 ? "+inf" : maxCatS0OverS1[c1][c2]) )+
                "   ,   median = "+stats[0]+" , mad+ = "+stats[1]+" , mad- = "+stats[2]+
                " , nb values = "+valueValueValues.get(c1).get(c2).size());*/
               
                if(debug){
                    System.out.println("Values : ");
                    for(int i=0;i<vals.length;i++)System.out.print(vals[i]+" , ");
                    System.out.println();
                }
            }
        }
    }



}









