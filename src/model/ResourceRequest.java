package model;

import java.sql.Timestamp;

/**
 * File Location: src/model/ResourceRequest.java
 * Short Purpose: Represents a request submitted by a VM to acquire units from a resource pool.
 * Connections:
 *   - Mapped to database table 'resource_requests' in src/sql/02_tables.sql.
 *   - Used by ResourceRequestDAO for queueing and managing request states (PENDING, APPROVED, ALLOCATED, REJECTED).
 *   - Processed by ResourceManager within ACID transactions.
 *   - Displayed on the Resource Requests screen.
 */
public class ResourceRequest {
    private int requestId;
    private int vmId;
    private String vmName;
    private int resourceId;
    private String resourceName;
    private int requestedUnits;
    private String status;         // "PENDING", "APPROVED", "REJECTED", "ALLOCATED"
    private Timestamp requestedAt;
    private Timestamp processedAt;

    public ResourceRequest() {
        this.status = "PENDING";
    }

    public ResourceRequest(int requestId, int vmId, int resourceId, int requestedUnits, 
                           String status, Timestamp requestedAt, Timestamp processedAt) {
        this.requestId = requestId;
        this.vmId = vmId;
        this.resourceId = resourceId;
        this.requestedUnits = requestedUnits;
        this.status = status;
        this.requestedAt = requestedAt;
        this.processedAt = processedAt;
    }

    public int getRequestId() { return requestId; }
    public void setRequestId(int requestId) { this.requestId = requestId; }

    public int getVmId() { return vmId; }
    public void setVmId(int vmId) { this.vmId = vmId; }

    public String getVmName() { return vmName; }
    public void setVmName(String vmName) { this.vmName = vmName; }

    public int getResourceId() { return resourceId; }
    public void setResourceId(int resourceId) { this.resourceId = resourceId; }

    public String getResourceName() { return resourceName; }
    public void setResourceName(String resourceName) { this.resourceName = resourceName; }

    public int getRequestedUnits() { return requestedUnits; }
    public void setRequestedUnits(int requestedUnits) { this.requestedUnits = requestedUnits; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Timestamp requestedAt) { this.requestedAt = requestedAt; }

    public Timestamp getProcessedAt() { return processedAt; }
    public void setProcessedAt(Timestamp processedAt) { this.processedAt = processedAt; }

    public String toJson() {
        return String.format(
            "{\"requestId\":%d,\"vmId\":%d,\"vmName\":\"%s\",\"resourceId\":%d,\"resourceName\":\"%s\",\"requestedUnits\":%d,\"status\":\"%s\"}",
            requestId, vmId, vmName != null ? escapeJson(vmName) : "VM-" + vmId,
            resourceId, resourceName != null ? escapeJson(resourceName) : "Res-" + resourceId,
            requestedUnits, status
        );
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
