package service;

import model.WaitDependency;
import java.util.*;

/**
 * File Location: src/service/WaitForGraph.java
 * Short Purpose: Graph data structure modeling the Operating Systems Wait-For Graph (WFG).
 *                A directed graph G = (V, E) where:
 *                - V = Set of processes / Virtual Machines
 *                - E = Set of directed edges (Vi -> Vj), indicating Vi is waiting for a resource held by Vj.
 * Connections:
 *   - Constructed from 'wait_dependencies' via WaitDependencyDAO.
 *   - Traversed by DeadlockDetector using Depth-First Search (DFS) for cycle detection.
 *   - Serialized to JSON to render interactive graph nodes and arrows on HTML5 Canvas.
 */
public class WaitForGraph {

    // Adjacency list: Vertex VM_ID -> List of outgoing edges
    private final Map<Integer, List<GraphEdge>> adjList = new HashMap<>();
    // Vertex registry: VM_ID -> VM Name
    private final Map<Integer, String> vertexNames = new HashMap<>();

    public static class GraphEdge {
        public final int fromVmId;
        public final int toVmId;
        public final int resourceId;
        public final String resourceName;

        public GraphEdge(int fromVmId, int toVmId, int resourceId, String resourceName) {
            this.fromVmId = fromVmId;
            this.toVmId = toVmId;
            this.resourceId = resourceId;
            this.resourceName = resourceName;
        }

        public String toJson() {
            return String.format(
                "{\"from\":%d,\"to\":%d,\"resourceId\":%d,\"resourceName\":\"%s\"}",
                fromVmId, toVmId, resourceId, resourceName != null ? resourceName : ""
            );
        }
    }

    public void addVertex(int vmId, String vmName) {
        adjList.putIfAbsent(vmId, new ArrayList<>());
        if (vmName != null) {
            vertexNames.put(vmId, vmName);
        }
    }

    public void addEdge(int fromVmId, int toVmId, int resourceId, String resourceName) {
        addVertex(fromVmId, null);
        addVertex(toVmId, null);
        adjList.get(fromVmId).add(new GraphEdge(fromVmId, toVmId, resourceId, resourceName));
    }

    public Set<Integer> getVertices() {
        return adjList.keySet();
    }

    public List<GraphEdge> getOutgoingEdges(int vmId) {
        return adjList.getOrDefault(vmId, Collections.emptyList());
    }

    public String getVertexName(int vmId) {
        return vertexNames.getOrDefault(vmId, "VM-" + vmId);
    }

    public void clear() {
        adjList.clear();
        vertexNames.clear();
    }

    /**
     * Builds the graph from a collection of database WaitDependencies.
     */
    public void populateFromDependencies(List<WaitDependency> dependencies) {
        clear();
        for (WaitDependency d : dependencies) {
            addVertex(d.getWaitingVmId(), d.getWaitingVmName());
            addVertex(d.getHoldingVmId(), d.getHoldingVmName());
            addEdge(d.getWaitingVmId(), d.getHoldingVmId(), d.getResourceId(), d.getResourceName());
        }
    }

    /**
     * Serializes nodes and edges to JSON format for HTML5 Canvas rendering.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"nodes\":[");
        int nIdx = 0;
        for (Map.Entry<Integer, String> entry : vertexNames.entrySet()) {
            if (nIdx++ > 0) sb.append(",");
            sb.append(String.format("{\"id\":%d,\"label\":\"%s\"}", entry.getKey(), entry.getValue()));
        }
        sb.append("],\"edges\":[");
        int eIdx = 0;
        for (List<GraphEdge> edges : adjList.values()) {
            for (GraphEdge e : edges) {
                if (eIdx++ > 0) sb.append(",");
                sb.append(e.toJson());
            }
        }
        sb.append("]}");
        return sb.toString();
    }
}
