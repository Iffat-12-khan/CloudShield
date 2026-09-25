package model;

import java.sql.Timestamp;

/**
 * File Location: src/model/Allocation.java
 * Short Purpose: Encapsulates granted resource allocations linking Virtual Machines to hardware resources.
 * Connections:
 *   - Mapped to database table 'resource_allocations' in src/sql/02_tables.sql.
 *   - Managed by AllocationDAO inside JDBC transactions.
 *   - Inspected by DeadlockDetector to understand which VM holds which resource.
 *   - Displayed in the Active Allocations table on the dashboard.
 */
public class Allocation {
    private int allocationId;
    private int vmId;
    private String vmName;
    private int resourceId;
    private String resourceName;
    private int allocatedUnits;
    private Timestamp allocatedAt;
    private Timestamp releasedAt;

    public Allocation() {}

    public Allocation(int allocationId, int vmId, int resourceId, int allocatedUnits, 
                      Timestamp allocatedAt, Timestamp releasedAt) {
        this.allocationId = allocationId;
        this.vmId = vmId;
        this.resourceId = resourceId;
        this.allocatedUnits = allocatedUnits;
        this.allocatedAt = allocatedAt;
        this.releasedAt = releasedAt;
    }

    public int getAllocationId() { return allocationId; }
    public void setAllocationId(int allocationId) { this.allocationId = allocationId; }

    public int getVmId() { return vmId; }
    public void setVmId(int vmId) { this.vmId = vmId; }

    public String getVmName() { return vmName; }
    public void setVmName(String vmName) { this.vmName = vmName; }

    public int getResourceId() { return resourceId; }
    public void setResourceId(int resourceId) { this.resourceId = resourceId; }

    public String getResourceName() { return resourceName; }
    public void setResourceName(String resourceName) { this.resourceName = resourceName; }

    public int getAllocatedUnits() { return allocatedUnits; }
    public void setAllocatedUnits(int allocatedUnits) { this.allocatedUnits = allocatedUnits; }

    public Timestamp getAllocatedAt() { return allocatedAt; }
    public void setAllocatedAt(Timestamp allocatedAt) { this.allocatedAt = allocatedAt; }

    public Timestamp getReleasedAt() { return releasedAt; }
    public void setReleasedAt(Timestamp releasedAt) { this.releasedAt = releasedAt; }

    public boolean isActive() {
        return releasedAt == null;
    }

    public String toJson() {
        return String.format(
            "{\"allocationId\":%d,\"vmId\":%d,\"vmName\":\"%s\",\"resourceId\":%d,\"resourceName\":\"%s\",\"allocatedUnits\":%d,\"isActive\":%b}",
            allocationId, vmId, vmName != null ? escapeJson(vmName) : "VM-" + vmId,
            resourceId, resourceName != null ? escapeJson(resourceName) : "Res-" + resourceId,
            allocatedUnits, isActive()
        );
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
