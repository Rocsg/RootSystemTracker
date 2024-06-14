package io.github.rocsg.rsml;


import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        TemporalGraph graph = new TemporalGraph();

        // Adding edges to the temporal graph
        graph.addEdge(1, 2, 1000, 5000);  // Edge from node 1 to node 2 between time 1000 and 5000
        graph.addEdge(2, 3, 2000, 6000);  // Edge from node 2 to node 3 between time 2000 and 6000
        graph.addEdge(3, 4, 3000, 7000);  // Edge from node 3 to node 4 between time 3000 and 7000

        // Querying edges active at time 4000
        List<TemporalEdge> activeEdgesAt4000 = graph.getEdgesAtTime(4000);
        System.out.println("Active edges at time 4000:");
        for (TemporalEdge edge : activeEdgesAt4000) {
            System.out.println("Edge from " + edge.getSource() + " to " + edge.getDestination());
        }
    }
}

class TemporalEdge {
    private final int source;
    private final int destination;
    private final long startTime;
    private final long endTime;

    public TemporalEdge(int source, int destination, long startTime, long endTime) {
        this.source = source;
        this.destination = destination;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public int getSource() {
        return source;
    }

    public int getDestination() {
        return destination;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }
}

class TemporalGraph {
    private final List<TemporalEdge> edges;

    public TemporalGraph() {
        this.edges = new ArrayList<>();
    }

    public void addEdge(int source, int destination, long startTime, long endTime) {
        edges.add(new TemporalEdge(source, destination, startTime, endTime));
    }

    public List<TemporalEdge> getEdgesAtTime(long time) {
        List<TemporalEdge> activeEdges = new ArrayList<>();
        for (TemporalEdge edge : edges) {
            if (time >= edge.getStartTime() && time <= edge.getEndTime()) {
                activeEdges.add(edge);
            }
        }
        return activeEdges;
    }

    public List<TemporalEdge> getAllEdges() {
        return new ArrayList<>(edges);
    }
}

