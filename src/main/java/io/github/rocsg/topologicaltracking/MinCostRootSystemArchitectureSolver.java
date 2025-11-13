package io.github.rocsg.topologicaltracking;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

import com.google.ortools.graph.*;
import  com.google.ortools.Loader;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.alg.flow.mincost.CapacityScalingMinimumCostFlow;
import org.jgrapht.alg.flow.mincost.MinimumCostFlowProblem;
import org.jgrapht.alg.interfaces.MinimumCostFlowAlgorithm;
import java.util.function.Function;
import org.jgrapht.alg.flow.mincost.MinimumCostFlowProblem;
import org.jgrapht.alg.flow.mincost.CapacityScalingMinimumCostFlow;
import org.jgrapht.alg.interfaces.MinimumCostFlowAlgorithm;


public class MinCostRootSystemArchitectureSolver {
    int sId=0, tId=1;
    public int[] col1Ids;
    public int[] col2Ids;

    public int[] costSourceToCol1;
    public int[] costCol2ToSink;
    public int[][] costCol1ToCol2;

    public int[] capaSourceToCol1;
    public int[] capaCol2ToSink;
    public int[][] capaCol1ToCol2;
    int nNodes;
    boolean isReady=false;
    private int COST_START;
    private int COST_END;

    


    public MinCostRootSystemArchitectureSolver() {}

    public MinCostRootSystemArchitectureSolver(int n) {
        int nextId = 2;
        col1Ids  = new int[n+1];
        col2Ids = new int[n+1];
        costSourceToCol1 = new int[n+1];
        costCol2ToSink = new int[n+1];
        costCol1ToCol2 = new int[n+1][n+1];
        capaSourceToCol1 = new int[n+1];
        capaCol2ToSink = new int[n+1];
        capaCol1ToCol2 = new int[n+1][n+1];
        for (int i = 0; i < n+1; i++) col1Ids[i]  = nextId++;
        for (int j = 0; j < n+1; j++) col2Ids[j] = nextId++;
        nNodes = n;
    }




    public void setupCostsAndCapacities(double[][] costCol1ToCol2,double cStart,double cEnd) {
      double max=0;
      for(int i=0;i<nNodes;i++) {
          for(int j=0;j<nNodes;j++) {
              if(costCol1ToCol2[i][j]>max)max=costCol1ToCol2[i][j];
          }
      }
      if(cStart>max)max=cStart;
      if(cEnd>max)max=cEnd;
      int scaleMax = 1000000;
      double scale = scaleMax*1.0/max;
      int[][] intCosts = new int[nNodes][nNodes];
      for(int i=0;i<nNodes;i++) {
          for(int j=0;j<nNodes;j++) {
              intCosts[i][j] = (int)Math.round(scale*costCol1ToCol2[i][j]);
          }
      }
      int intCStart = (int)Math.round(scale*cStart);
      int intCEnd = (int)Math.round(scale*cEnd);
      setupCostsAndCapacities(intCosts,intCStart,intCEnd);
    }



    public void setupCostsAndCapacities(int[][] costCol1ToCol2,int costStart,int costEnd) {
        COST_START = costStart;
        COST_END = costEnd;

        for(int i=0;i<nNodes+1;i++) {
            this.costSourceToCol1[i] = 0;
            this.costCol2ToSink[i] = 0;
            this.capaSourceToCol1[i] = nNodes;
            this.capaCol2ToSink[i] = 1;
        }
        this.capaCol2ToSink[nNodes] = nNodes;
        this.capaSourceToCol1[nNodes] = nNodes;//Redondant, mais mieux de le voir explicite

        for(int i=0;i<nNodes;i++) {
            for(int j=0;j<nNodes;j++) {
                if(costCol1ToCol2[i][j]<0){
                  this.costCol1ToCol2[i][j] = COST_START + COST_END + 1;
                  this.capaCol1ToCol2[i][j] = 0;
                }
                else{
                  this.costCol1ToCol2[i][j] = costCol1ToCol2[i][j];
                  this.capaCol1ToCol2[i][j] = 1;
                }
            }
        } 

        for(int i=0;i<nNodes;i++) {
            this.costCol1ToCol2[i][nNodes] = COST_END;
            this.costCol1ToCol2[nNodes][i] = COST_START;
            this.capaCol1ToCol2[i][nNodes] = 1;
            this.capaCol1ToCol2[nNodes][i] = 1;
        }

        for(int i=0;i<nNodes+1;i++) {
            this.costCol1ToCol2[i][i] = COST_START + COST_END + 1;
            this.capaCol1ToCol2[i][i] = 0;
        }
        isReady=true;
    }



  static MinCostRootSystemArchitectureSolver generateRandomInstance(int n, long seed, int costStart, int costEnd) {
    Random rnd = new Random(seed);
    MinCostRootSystemArchitectureSolver solver = new MinCostRootSystemArchitectureSolver(n);
    int[][] costCol1ToCol2 = new int[n][n];
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        costCol1ToCol2[i][j] = 2+rnd.nextInt((int)Math.round(1.2*costEnd) );        
      }
    }

    solver.setupCostsAndCapacities(costCol1ToCol2,costStart,costEnd);
    return solver;
  }

  static MinCostRootSystemArchitectureSolver generateRandomInstanceDouble(int n, long seed, double costStart, double costEnd) {
    Random rnd = new Random(seed);
    MinCostRootSystemArchitectureSolver solver = new MinCostRootSystemArchitectureSolver(n);
    double[][] costCol1ToCol2 = new double [n][n];
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        costCol1ToCol2[i][j] = 2+1.2*costEnd*rnd.nextDouble();        
      }
    }

    solver.setupCostsAndCapacities(costCol1ToCol2,costStart,costEnd);
    return solver;
  }



  // Calcule le matching optimal. A la fin, la liste rendue contient la liste des couples (i, j, cost) avec flot = 1. 
  //Les indices i ou j peuvent valoir -1 , il s'agit alors d'une racine emergente ou d'une terminaison
  public ArrayList<int[]> solveWithORTools() {
    Loader.loadNativeLibraries();
    MinCostFlow mcf = new MinCostFlow();

    // source to col1
    for (int i = 0; i < nNodes+1; i++) {
      mcf.addArcWithCapacityAndUnitCost(sId, col1Ids[i], capaSourceToCol1[i], costSourceToCol1[i]);
    }
    // col2 to sink 
    for (int j = 0; j < nNodes+1; j++) {
      mcf.addArcWithCapacityAndUnitCost(col2Ids[j], tId, capaCol2ToSink[j], costCol2ToSink[j]);
    }

    // col1 to col2
    for (int i = 0; i < nNodes+1; i++) {
      int u = col1Ids[i];
      for (int j = 0; j < nNodes+1; j++) {
        int v = col2Ids[j];
        mcf.addArcWithCapacityAndUnitCost(u, v, capaCol1ToCol2[i][j], costCol1ToCol2[i][j]);
      }
    }

    // Supplies
    mcf.setNodeSupply(sId, +nNodes);
    mcf.setNodeSupply(tId, -nNodes);

    System.out.println("Solving with OR-Tools...");
    long startTime = System.currentTimeMillis();
    MinCostFlow.Status status = mcf.solve();
    if (status != MinCostFlow.Status.OPTIMAL) {
      throw new RuntimeException("OR-Tools: no optimal solution, status=" + status);
    }
    long endTime = System.currentTimeMillis();
    long duration = endTime - startTime;
    System.out.println("Solved with cost="+mcf.getOptimalCost()+" in "+(duration/1000.0)+" s");
    ArrayList<int[]> matching = new ArrayList<>();
    for (int arc = 0; arc < mcf.getNumArcs(); arc++) {
      int u = mcf.getTail(arc);
      int v = mcf.getHead(arc);
      long flow = mcf.getFlow(arc);
      if (flow == 1) {
          int i = -1, j = -1;
          // Cherche l'indice i tel que col1Ids[i] == u et l'indice j tel que col2Ids[j] == v
          for (int idx = 0; idx < col1Ids.length; idx++)   if (col1Ids[idx] == u) { i = idx; break; }
          for (int idx = 0; idx < col2Ids.length; idx++)  if (col2Ids[idx] == v) { j = idx; break; }
          
          //Ajouter le matching
          if (i != -1 && j != -1) {
            if(i==nNodes)i= -1;
            if(j==nNodes)j= -1;
            matching.add(new int[]{i, j,(int) mcf.getUnitCost(arc)});
          }
        }
    }
    return matching; 
  }
}
