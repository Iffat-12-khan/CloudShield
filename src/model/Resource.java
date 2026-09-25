package model;

import java.sql.Timestamp;

/**
 * File Location: src/model/Resource.java
 * Short Purpose: Encapsulates cloud hardware resource pool data (CPU, RAM, Storage, Network).
 * Connections:
 *   - Mapped to database table 'resources' in src/sql/02_tables.sql.
 *   - Used by ResourceDAO for querying resource balances and capacity.
 *   - Used by ResourceManager to evaluate allocations and enforce limits.
 *   - Serialized to JSON for displaying progress bars and utilization on the dashboard.
 */
public class Resource {
    private int resourceId;
    private String resourceName;
    private int totalUnits;
    private int availableUnits;
    private String unit;
    private Timestamp createdAt;

    public Resource() {}

    public Resource(int resourceId, String resourceName, int totalUnits, int availableUnits, String unit, Timestamp createdAt) {
        this.resourceId = resourceId;
        this.resourceName = resourceName;
        this.totalUnits = totalUnits;
        this.availableUnits = availableUnits;
        this.unit = unit;
        this.createdAt = createdAt;
    }

    public int getResourceId() { return resourceId; }
    public void setResourceId(int resourceId) { this.resourceId = resourceId; }

    public String getResourceName() { return resourceName; }
    public void setResourceName(String resourceName) { this.resourceName = resourceName; }

    public int getTotalUnits() { return totalUnits; }
    public void setTotalUnits(int totalUnits) { this.totalUnits = totalUnits; }

    public int getAvailableUnits() { return availableUnits; }
    public void setAvailableUnits(int availableUnits) { this.availableUnits = availableUnits; }

    public int getAllocatedUnits() {
        return Math.max(0, totalUnits - availableUnits);
    }

    public double getUtilizationPercentage() {
        if (totalUnits <= 0) return 0.0;
        return ((double) (totalUnits - availableUnits) / totalUnits) * 100.0;
    }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String toJson() {
        return String.format(
            "{\"resourceId\":%d,\"resourceName\":\"%s\",\"totalUnits\":%d,\"availableUnits\":%d,\"allocatedUnits\":%d,\"utilizationPct\":%.2f,\"unit\":\"%s\"}",
            resourceId, resourceName, totalUnits, availableUnits, getAllocatedUnits(), getUtilizationPercentage(), unit
        );
    }
}
