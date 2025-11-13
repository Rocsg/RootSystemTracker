package io.github.rocsg.rsttest;
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


/* Resultats benchmark 

Generating random bipartite instance N=50 ...
=== RESULTS ===
OR-Tools: cost=53545, time=0,438 s
JGraphT : cost=53545,000, time=0,041 s
Cost difference (abs): 0,000

Generating random bipartite instance N=500 ...
=== RESULTS ===
OR-Tools: cost=46737, time=0,507 s
JGraphT : cost=46737,000, time=2,023 s
Cost difference (abs): 0,000

Generating random bipartite instance N=1000 ...
=== RESULTS ===
OR-Tools: cost=45514, time=0,795 s
JGraphT : cost=45514,000, time=13,933 s
Cost difference (abs): 0,000

Generating random bipartite instance N=2000 ...
=== RESULTS ===
OR-Tools: cost=45339, time=2,017 s
JGraphT : cost=45339,000, time=109,115 s
Cost difference (abs): 0,000

*/








public class BenchmarkMinCostFlow {

  // ==== Paramètres ====
  static final int N = 2000;                // #noeuds à gauche et à droite
  static final int COST_SCALE = 100;       // échelle si tu pars de floats
  static final long SEED = 42L;            // reproductible
  static final int CSTART = 500 * COST_SCALE;
  static final int CEND   = 50 * COST_SCALE;
  static final int CIJ_MAX    = 450 * COST_SCALE;


  public static void main(String[] args) {
    System.out.println("Generating random bipartite instance N=" + N + " ...");
    GraphData data = generateRandomInstance(N, SEED);

    // ---- OR-TOOLS ----
    long t0 = System.nanoTime();
    OrToolsResult orResult = solveWithORTools(data);
    long t1 = System.nanoTime();

    // ---- JGRAPHT ----
    long t2 = System.nanoTime();
    JGraphTResult jgtResult = solveWithJGraphT(data);
    long t3 = System.nanoTime();

    System.out.println("\n=== RESULTS ===");
    System.out.printf("OR-Tools: cost=%d, time=%.3f s\n",
        orResult.cost, (t1 - t0) / 1e9);
    System.out.printf("JGraphT : cost=%.3f, time=%.3f s\n",
        jgtResult.cost, (t3 - t2) / 1e9);

    // Petite vérif (ils devraient être égaux à l’échelle près)
    double diff = Math.abs(orResult.cost - jgtResult.cost);
    System.out.printf("Cost difference (abs): %.3f\n", diff);
  }








  static class GraphData {
    // IDs logiques pour mapping (OR-Tools utilise int node ids)
    int S, T;
    // coûts
    int[] cStart;    // c_start(i) pour chaque Li
    int[] cEnd;      // c_end(j) pour chaque Rj
    int[][] cIJ;     // coût Li->Rj
    public int[] col1Ids;
    public int[] col2Ids;
    public int[] costSourceToCol1;
    public int[] costCol2ToSink;
    public int[][] costCol1ToCol2;
    public int[] capaSourceToCol1;
    public int[] capaCol2ToSink;
    public int[][] capaCol1ToCol2;
  }



  static GraphData generateRandomInstance(int n, long seed) {
    Random rnd = new Random(seed);
    GraphData gd = new GraphData();
    gd.col1Ids  = new int[n+1];
    gd.col2Ids = new int[n+1];
    gd.costSourceToCol1 = new int[n+1];
    gd.costCol2ToSink = new int[n+1];
    gd.costCol1ToCol2 = new int[n+1][n+1];
    gd.capaSourceToCol1 = new int[n+1];
    gd.capaCol2ToSink = new int[n+1];
    gd.capaCol1ToCol2 = new int[n+1][n+1];

    // Map des ids pour OR-Tools
    int nextId = 0;
    gd.S = nextId++;
    gd.T = nextId++;
    for (int i = 0; i < n+1; i++) gd.col1Ids[i]  = nextId++;
    for (int j = 0; j < n+1; j++) gd.col2Ids[j] = nextId++;

    for (int i = 0; i < n+1; i++) {
      gd.costSourceToCol1[i] = 0;//rnd.nextInt(CSTART_MAX + 1);
      gd.costCol2ToSink[i] = 0;//rnd.nextInt(CSTART_MAX + 1);
      gd.capaSourceToCol1[i] = n;//rnd.nextInt(CSTART_MAX + 1);
      gd.capaCol2ToSink[i] = 1;//rnd.nextInt(CSTART_MAX + 1);
    }
    gd.capaCol2ToSink[n] = n;//rnd.nextInt(CSTART_MAX + 1);

    for (int i = 0; i < n; i++) {
      gd.costCol1ToCol2[i][n] = CEND;
      gd.costCol1ToCol2[n][i] = CSTART;
    }
      gd.costCol1ToCol2[n][n] = CSTART*CEND;
    // coûts denses Li->Rj
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        gd.costCol1ToCol2[i][j] = rnd.nextInt(CIJ_MAX + 1);
        gd.capaCol1ToCol2[i][j] = 1;
      }
    }
    for (int i = 0; i < n; i++) {
      gd.capaCol1ToCol2[i][i] = 0;
      gd.costCol1ToCol2[i][i] = CSTART + CEND + 1;
    }
    return gd;
  }

  // ===== OR-TOOLS =====
  static class OrToolsResult {
    long cost;
  }

  static OrToolsResult solveWithORTools(GraphData gd) {
    Loader.loadNativeLibraries();

    MinCostFlow mcf = new MinCostFlow();

    // S -> Li (cap=1, coût = c_start(i))
    for (int i = 0; i < N+1; i++) {
      mcf.addArcWithCapacityAndUnitCost(gd.S, gd.col1Ids[i], gd.capaSourceToCol1[i], gd.costSourceToCol1[i]);
    }
    // Li -> Rj (cap=1, coût = c_ij)
    for (int i = 0; i < N+1; i++) {
      int u = gd.col1Ids[i];
      for (int j = 0; j < N+1; j++) {
        int v = gd.col2Ids[j];
        mcf.addArcWithCapacityAndUnitCost(u, v, gd.capaCol1ToCol2[i][j], gd.costCol1ToCol2[i][j]);
      }
    }
    // Rj -> T (cap=1, coût = c_end(j))
    for (int j = 0; j < N+1; j++) {
      mcf.addArcWithCapacityAndUnitCost(gd.col2Ids[j], gd.T, gd.capaCol2ToSink[j], gd.costCol2ToSink[j]);
    }

    // Supplies
    mcf.setNodeSupply(gd.S, +N);
    mcf.setNodeSupply(gd.T, -N);

    MinCostFlow.Status status = mcf.solve();
    if (status != MinCostFlow.Status.OPTIMAL) {
      throw new RuntimeException("OR-Tools: no optimal solution, status=" + status);
    }

    OrToolsResult r = new OrToolsResult();
    r.cost = mcf.getOptimalCost();
    List<int[]> matching = new ArrayList<>();
    for (int arc = 0; arc < mcf.getNumArcs(); arc++) {
      int u = mcf.getTail(arc);
      int v = mcf.getHead(arc);
      long flow = mcf.getFlow(arc);
      if (flow == 1) {
          int i = -1, j = -1;
          // Cherche l'indice i tel que col1Ids[i] == u
          for (int idx = 0; idx < gd.col1Ids.length; idx++) {
              if (gd.col1Ids[idx] == u) { i = idx; break; }
          }
          // Cherche l'indice j tel que col2Ids[j] == v
          for (int idx = 0; idx < gd.col2Ids.length; idx++) {
              if (gd.col2Ids[idx] == v) { j = idx; break; }
          }
          if (i != -1 && j != -1) {
              matching.add(new int[]{i, j});
          }
        }
    }
    // matching contient la liste des couples (i, j) avec flot = 1
    return r;
  }

  // ===== JGRAPHT =====
  static class JGraphTResult {
    double cost;
  }


  static JGraphTResult solveWithJGraphT(GraphData gd) {
    Graph<Integer, DefaultWeightedEdge> g =
        new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

    // sommets
    g.addVertex(gd.S);
    g.addVertex(gd.T);
    for (int i = 0; i < N+1; i++) g.addVertex(gd.col1Ids[i]);
    for (int j = 0; j < N+1; j++) g.addVertex(gd.col2Ids[j]);

    // capacités et poids (coûts) : cap=1 partout, poids = coûts (double)
    Map<DefaultWeightedEdge, Integer> cap = new HashMap<>();

    // S -> Li  (coût = cStart[i])
    for (int i = 0; i < N+1; i++) {
      DefaultWeightedEdge e = g.addEdge(gd.S, gd.col1Ids[i]);
      g.setEdgeWeight(e, gd.costSourceToCol1[i]);  // coût lu par le solver
      cap.put(e, gd.capaSourceToCol1[i]);
    }
    // Li -> Rj (coût = cIJ[i][j])
    for (int i = 0; i < N+1; i++) {
      int u = gd.col1Ids[i];
      for (int j = 0; j < N+1; j++) {
        int v = gd.col2Ids[j];
        DefaultWeightedEdge e = g.addEdge(u, v);
        g.setEdgeWeight(e, gd.costCol1ToCol2[i][j]);
        cap.put(e, gd.capaCol1ToCol2[i][j]);
      }
    }
    // Rj -> T  (coût = cEnd[j])
    for (int j = 0; j < N+1; j++) {
      DefaultWeightedEdge e = g.addEdge(gd.col2Ids[j], gd.T);
      g.setEdgeWeight(e, gd.costCol2ToSink[j]);
      cap.put(e, gd.capaCol2ToSink[j]);
    }

    // supplies (Σ = 0)
    Map<Integer, Integer> supply = new HashMap<>();
    supply.put(gd.S, +N);
    supply.put(gd.T, -N);

    // fonctions typées (évite l’ambiguïté lambda)
    Function<Integer, Integer> supplyFn = v -> supply.getOrDefault(v, 0);
    Function<DefaultWeightedEdge, Integer> capUpFn = e -> cap.getOrDefault(e, 1);
    // si tu as des bornes basses, fournis-les ; sinon 0 partout
    Function<DefaultWeightedEdge, Integer> capLoFn = e -> 0;

    // constructeur SANS fonction de coût (les coûts = poids d’arêtes du graphe)
    MinimumCostFlowProblem<Integer, DefaultWeightedEdge> problem =
        new MinimumCostFlowProblem.MinimumCostFlowProblemImpl<>(
            g, supplyFn, capUpFn, capLoFn
        );

    CapacityScalingMinimumCostFlow<Integer, DefaultWeightedEdge> solver =
        new CapacityScalingMinimumCostFlow<>();

    MinimumCostFlowAlgorithm.MinimumCostFlow<DefaultWeightedEdge> sol =
        solver.getMinimumCostFlow(problem);

    JGraphTResult r = new JGraphTResult();
    r.cost = sol.getCost();   // somme des poids * flux
    return r;
  }
}
