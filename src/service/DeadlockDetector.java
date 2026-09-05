package service;

import dao.DeadlockLogDAO;
import dao.SecurityLogDAO;
import dao.WaitDependencyDAO;
import model.WaitDependency;

import java.util.*;

/**
 * File Location: src/service/DeadlockDetector.java
 * Short Purpose: Operating Systems Deadlock Detection using Wait-For Graph (WFG) and Depth-First Search (DFS).
 *                Identifies circular wait conditions (Coffman's 4th condition) and executes automated resolution.
 * Concepts:
 *   - OS Deadlock Theory: In a system with single-unit resources, a cycle in the WFG is a necessary and sufficient condition for deadlock.
 *   - Algorithm: 3-Color Depth-First Search (DFS) cycle finding with recursion tracking.
 *   - Automated Recovery: Victim selection and edge termination to restore system to a safe state.
 * Connections:
 *   - Uses WaitDependencyDAO to load active edges and remove broken dependencies.
 *   - Uses DeadlockLogDAO and SecurityLogDAO to audit detected deadlock incidents.
 *   - Supplies cycle paths and visual cues to the UI Deadlock WFG Screen.
 */
public class DeadlockDetector {

    private final WaitDependencyDAO waitDAO = new WaitDependencyDAO();
    private final DeadlockLogDAO deadlockDAO = new DeadlockLogDAO();
    private final SecurityLogDAO securityDAO = new SecurityLogDAO();

    // 3-Color State constants for DFS cycle detection
    private static final int COLOR_WHITE = 0; // Unvisited
    private static final int COLOR_GRAY = 1;  // Currently being processed (on recursion stack)
    private static final int COLOR_BLACK = 2; // Completely explored (subgraph has no cycles)

    /**
     * Runs DFS cycle detection against the current active Wait-For Graph.
     */
    public DetectionResult detectDeadlocks() {
        List<WaitDependency> dependencies = waitDAO.findAll();
        WaitForGraph graph = new WaitForGraph();
        graph.populateFromDependencies(dependencies);

        Map<Integer, Integer> colorMap = new HashMap<>();
        Map<Integer, Integer> parentMap = new HashMap<>();
        List<List<Integer>> cycles = new ArrayList<>();

        for (int vmId : graph.getVertices()) {
            colorMap.put(vmId, COLOR_WHITE);
        }

        // Run DFS from every unvisited vertex
        for (int vmId : graph.getVertices()) {
            if (colorMap.get(vmId) == COLOR_WHITE) {
                dfsVisit(vmId, graph, colorMap, parentMap, cycles);
            }
        }

        boolean deadlockFound = !cycles.isEmpty();
        List<String> formattedCyclePaths = new ArrayList<>();

        if (deadlockFound) {
            for (List<Integer> cycle : cycles) {
                StringBuilder pathBuilder = new StringBuilder();
                for (int i = 0; i < cycle.size(); i++) {
                    int id = cycle.get(i);
                    String name = graph.getVertexName(id);
                    pathBuilder.append("VM-").append(id).append(" (").append(name).append(")");
                    if (i < cycle.size() - 1) {
                        pathBuilder.append(" \u2192 "); // Arrow symbol ->
                    }
                }
                String pathStr = pathBuilder.toString();
                formattedCyclePaths.add(pathStr);

                // Record into database deadlock_logs & security_logs
                deadlockDAO.recordDeadlock(pathStr);
                securityDAO.recordEvent("DEADLOCK_DETECTED", "HIGH",
                    "Deadlock detected! Circular wait condition identified in OS graph: " + pathStr, "127.0.0.1", null);
            }
        }

        return new DetectionResult(deadlockFound, cycles, formattedCyclePaths, graph);
    }

    /**
     * Recursive DFS helper using 3-Color graph traversal.
     */
    private void dfsVisit(int current, WaitForGraph graph, Map<Integer, Integer> colorMap,
                          Map<Integer, Integer> parentMap, List<List<Integer>> cycles) {
        colorMap.put(current, COLOR_GRAY); // Mark as currently exploring

        for (WaitForGraph.GraphEdge edge : graph.getOutgoingEdges(current)) {
            int neighbor = edge.toVmId;
            int neighborColor = colorMap.getOrDefault(neighbor, COLOR_WHITE);

            if (neighborColor == COLOR_GRAY) {
                // BACK-EDGE DETECTED! A back-edge in DFS indicates a directed cycle in the graph.
                List<Integer> cycle = extractCycle(current, neighbor, parentMap);
                if (!cycle.isEmpty()) {
                    cycles.add(cycle);
                }
            } else if (neighborColor == COLOR_WHITE) {
                parentMap.put(neighbor, current);
                dfsVisit(neighbor, graph, colorMap, parentMap, cycles);
            }
        }

        colorMap.put(current, COLOR_BLACK); // Mark as fully visited
    }

    /**
     * Reconstructs the exact sequence of vertices that form the directed cycle.
     */
    private List<Integer> extractCycle(int current, int neighbor, Map<Integer, Integer> parentMap) {
        List<Integer> cycle = new ArrayList<>();
        cycle.add(neighbor);

        int curr = current;
        while (curr != neighbor && parentMap.containsKey(curr)) {
            cycle.add(curr);
            curr = parentMap.get(curr);
        }
        cycle.add(neighbor);
        Collections.reverse(cycle);
        return cycle;
    }

    /**
     * Automated Deadlock Resolution:
     * Selects a victim VM from the cycle and breaks the circular wait condition by preemption.
     */
    public ResolutionResult resolveDeadlock() {
        DetectionResult detection = detectDeadlocks();
        if (!detection.deadlockDetected || detection.cycles.isEmpty()) {
            return new ResolutionResult(false, "No active deadlock detected in the Wait-For Graph.", -1, null);
        }

        // Victim selection policy: Choose the last VM in the first detected cycle
        List<Integer> firstCycle = detection.cycles.get(0);
        int victimVmId = firstCycle.get(firstCycle.size() - 2); // Select node right before cycle closes
        String victimName = detection.graph.getVertexName(victimVmId);

        // Preempt: Remove all wait dependencies associated with victim VM
        waitDAO.removeByVm(victimVmId);

        // Mark deadlock log as resolved
        deadlockDAO.markResolved(-1);

        // Audit resolution event
        securityDAO.recordEvent("DEADLOCK_RESOLVED", "MEDIUM", 
            "Deadlock automatically resolved. Preempted wait dependencies for victim VM-" + victimVmId + " (" + victimName + ").",
            "127.0.0.1", null);

        // Verify that graph is now acyclic
        DetectionResult postCheck = detectDeadlocks();

        return new ResolutionResult(true, 
            "Deadlock successfully broken! Preempted victim VM-" + victimVmId + " (" + victimName + "). System is now in a safe state.",
            victimVmId, postCheck);
    }

    // Result container for detection
    public static class DetectionResult {
        public final boolean deadlockDetected;
        public final List<List<Integer>> cycles;
        public final List<String> formattedCyclePaths;
        public final WaitForGraph graph;

        public DetectionResult(boolean deadlockDetected, List<List<Integer>> cycles, 
                               List<String> formattedCyclePaths, WaitForGraph graph) {
            this.deadlockDetected = deadlockDetected;
            this.cycles = cycles;
            this.formattedCyclePaths = formattedCyclePaths;
            this.graph = graph;
        }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"deadlockDetected\":").append(deadlockDetected).append(",");
            sb.append("\"cycleCount\":").append(cycles.size()).append(",");
            sb.append("\"cyclePaths\":[");
            for (int i = 0; i < formattedCyclePaths.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(formattedCyclePaths.get(i).replace("\"", "\\\"")).append("\"");
            }
            sb.append("],");
            sb.append("\"graph\":").append(graph.toJson());
            sb.append("}");
            return sb.toString();
        }
    }

    // Result container for resolution
    public static class ResolutionResult {
        public final boolean resolved;
        public final String message;
        public final int victimVmId;
        public final DetectionResult postCheck;

        public ResolutionResult(boolean resolved, String message, int victimVmId, DetectionResult postCheck) {
            this.resolved = resolved;
            this.message = message;
            this.victimVmId = victimVmId;
            this.postCheck = postCheck;
        }

        public String toJson() {
            return String.format(
                "{\"resolved\":%b,\"message\":\"%s\",\"victimVmId\":%d}",
                resolved, message.replace("\"", "\\\""), victimVmId
            );
        }
    }
}
