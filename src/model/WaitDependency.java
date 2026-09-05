package model;

import java.sql.Timestamp;

/**
 * File Location: src/model/WaitDependency.java
 * Short Purpose: Encapsulates directed edges in the OS Wait-For Graph (WFG).
 *                Indicates that 'waitingVm' is blocked waiting for a resource currently held by 'holdingVm'.
 * Connections:
 *   - Mapped to database table 'wait_dependencies' in src/sql/02_tables.sql.
 *   - Used by WaitDependencyDAO to query and mutate dependency edges.
 *   - Used by WaitForGraph and DeadlockDetector to build the directed adjacency graph.
 *   - Sent to the frontend HTML5 Canvas for real-time visual graph rendering.
 */
public class WaitDependency {
    private int dependencyId;
    private int waitingVmId;
    private String waitingVmName;
    private int holdingVmId;
    private String holdingVmName;
    private int resourceId;
    private String resourceName;
    private Timestamp createdAt;

    public WaitDependency() {}

    public WaitDependency(int dependencyId, int waitingVmId, int holdingVmId, int resourceId, Timestamp createdAt) {
        this.dependencyId = dependencyId;
        this.waitingVmId = waitingVmId;
        this.holdingVmId = holdingVmId;
        this.resourceId = resourceId;
        this.createdAt = createdAt;
    }

    public int getDependencyId() { return dependencyId; }
    public void setDependencyId(int dependencyId) { this.dependencyId = dependencyId; }

    public int getWaitingVmId() { return waitingVmId; }
    public void setWaitingVmId(int waitingVmId) { this.waitingVmId = waitingVmId; }

    public String getWaitingVmName() { return waitingVmName; }
    public void setWaitingVmName(String waitingVmName) { this.waitingVmName = waitingVmName; }

    public int getHoldingVmId() { return holdingVmId; }
    public void setHoldingVmId(int holdingVmId) { this.holdingVmId = holdingVmId; }

    public String getHoldingVmName() { return holdingVmName; }
    public void setHoldingVmName(String holdingVmName) { this.holdingVmName = holdingVmName; }

    public int getResourceId() { return resourceId; }
    public void setResourceId(int resourceId) { this.resourceId = resourceId; }

    public String getResourceName() { return resourceName; }
    public void setResourceName(String resourceName) { this.resourceName = resourceName; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String toJson() {
        return String.format(
            "{\"dependencyId\":%d,\"waitingVmId\":%d,\"waitingVmName\":\"%s\",\"holdingVmId\":%d,\"holdingVmName\":\"%s\",\"resourceId\":%d,\"resourceName\":\"%s\"}",
            dependencyId, waitingVmId, waitingVmName != null ? escapeJson(waitingVmName) : "VM-" + waitingVmId,
            holdingVmId, holdingVmName != null ? escapeJson(holdingVmName) : "VM-" + holdingVmId,
            resourceId, resourceName != null ? escapeJson(resourceName) : "Res-" + resourceId
        );
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
