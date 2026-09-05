package model;

import java.sql.Timestamp;

/**
 * File Location: src/model/VirtualMachine.java
 * Short Purpose: Encapsulates virtual machine instance attributes, specifications, status, and owner info.
 * Connections:
 *   - Mapped to database table 'virtual_machines' in src/sql/02_tables.sql.
 *   - Used by VirtualMachineDAO for VM lifecycle management (Create, Update, Stop, Start, Delete).
 *   - Used by WaitForGraph as graph vertices representing processes in deadlock detection.
 *   - Rendered on the UI Virtual Machines screen.
 */
public class VirtualMachine {
    private int vmId;
    private String vmName;
    private String operatingSystem;
    private int cpuRequired;
    private int ramRequired;
    private int storageRequired;
    private String status;         // "RUNNING", "WAITING", "STOPPED"
    private int ownerId;
    private String ownerUsername;  // Populated via JOIN with users table
    private Timestamp createdAt;

    public VirtualMachine() {
        this.status = "RUNNING";
    }

    public VirtualMachine(int vmId, String vmName, String operatingSystem, 
                          int cpuRequired, int ramRequired, int storageRequired, 
                          String status, int ownerId, Timestamp createdAt) {
        this.vmId = vmId;
        this.vmName = vmName;
        this.operatingSystem = operatingSystem;
        this.cpuRequired = cpuRequired;
        this.ramRequired = ramRequired;
        this.storageRequired = storageRequired;
        this.status = status;
        this.ownerId = ownerId;
        this.createdAt = createdAt;
    }

    public int getVmId() { return vmId; }
    public void setVmId(int vmId) { this.vmId = vmId; }

    public String getVmName() { return vmName; }
    public void setVmName(String vmName) { this.vmName = vmName; }

    public String getOperatingSystem() { return operatingSystem; }
    public void setOperatingSystem(String operatingSystem) { this.operatingSystem = operatingSystem; }

    public int getCpuRequired() { return cpuRequired; }
    public void setCpuRequired(int cpuRequired) { this.cpuRequired = cpuRequired; }

    public int getRamRequired() { return ramRequired; }
    public void setRamRequired(int ramRequired) { this.ramRequired = ramRequired; }

    public int getStorageRequired() { return storageRequired; }
    public void setStorageRequired(int storageRequired) { this.storageRequired = storageRequired; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getOwnerId() { return ownerId; }
    public void setOwnerId(int ownerId) { this.ownerId = ownerId; }

    public String getOwnerUsername() { return ownerUsername; }
    public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String toJson() {
        return String.format(
            "{\"vmId\":%d,\"vmName\":\"%s\",\"operatingSystem\":\"%s\",\"cpuRequired\":%d,\"ramRequired\":%d,\"storageRequired\":%d,\"status\":\"%s\",\"ownerId\":%d,\"ownerUsername\":\"%s\"}",
            vmId, escapeJson(vmName), escapeJson(operatingSystem), cpuRequired, ramRequired, storageRequired, status, ownerId, 
            ownerUsername != null ? escapeJson(ownerUsername) : ""
        );
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
