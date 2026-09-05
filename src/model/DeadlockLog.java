package model;

import java.sql.Timestamp;

/**
 * File Location: src/model/DeadlockLog.java
 * Short Purpose: Encapsulates detected circular wait deadlock incidents, cycle paths, and resolution timestamps.
 * Connections:
 *   - Mapped to database table 'deadlock_logs' in src/sql/02_tables.sql.
 *   - Recorded by DeadlockDetector when a cycle is identified via DFS.
 *   - Updated to 'RESOLVED' when automated resolution / preemption takes place.
 *   - Displayed on the Deadlock Logs tab.
 */
public class DeadlockLog {
    private int logId;
    private String cyclePath;
    private Timestamp detectedAt;
    private String resolutionStatus;  // "DETECTED", "RESOLVED", "MANUAL_INTERVENTION"
    private Timestamp resolvedAt;

    public DeadlockLog() {
        this.resolutionStatus = "DETECTED";
    }

    public DeadlockLog(int logId, String cyclePath, Timestamp detectedAt, String resolutionStatus, Timestamp resolvedAt) {
        this.logId = logId;
        this.cyclePath = cyclePath;
        this.detectedAt = detectedAt;
        this.resolutionStatus = resolutionStatus;
        this.resolvedAt = resolvedAt;
    }

    public int getLogId() { return logId; }
    public void setLogId(int logId) { this.logId = logId; }

    public String getCyclePath() { return cyclePath; }
    public void setCyclePath(String cyclePath) { this.cyclePath = cyclePath; }

    public Timestamp getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Timestamp detectedAt) { this.detectedAt = detectedAt; }

    public String getResolutionStatus() { return resolutionStatus; }
    public void setResolutionStatus(String resolutionStatus) { this.resolutionStatus = resolutionStatus; }

    public Timestamp getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Timestamp resolvedAt) { this.resolvedAt = resolvedAt; }

    public String toJson() {
        return String.format(
            "{\"logId\":%d,\"cyclePath\":\"%s\",\"detectedAt\":\"%s\",\"resolutionStatus\":\"%s\",\"resolvedAt\":%s}",
            logId, escapeJson(cyclePath), detectedAt != null ? detectedAt.toString() : "", 
            resolutionStatus, resolvedAt != null ? "\"" + resolvedAt.toString() + "\"" : "null"
        );
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
