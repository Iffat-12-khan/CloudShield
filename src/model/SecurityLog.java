package model;

import java.sql.Timestamp;

/**
 * File Location: src/model/SecurityLog.java
 * Short Purpose: Encapsulates cybersecurity incident logs, attack categories, severity, and IP attribution.
 * Connections:
 *   - Mapped to database table 'security_logs' in src/sql/02_tables.sql.
 *   - Inserted by ThreatDetector, SecurityManager, and database triggers.
 *   - Displayed in the Cybersecurity Threat Radar and Audit Logs screen.
 */
public class SecurityLog {
    private int logId;
    private String eventType;      // "BRUTE_FORCE_LOCKOUT", "UNAUTHORIZED_ACCESS", "RESOURCE_ABUSE", "SUSPICIOUS_REQUEST"
    private String severity;       // "LOW", "MEDIUM", "HIGH", "CRITICAL"
    private String description;
    private String ipAddress;
    private Integer userId;
    private String username;       // Populated via JOIN with users table
    private Timestamp createdAt;

    public SecurityLog() {
        this.severity = "LOW";
    }

    public SecurityLog(int logId, String eventType, String severity, String description, 
                       String ipAddress, Integer userId, Timestamp createdAt) {
        this.logId = logId;
        this.eventType = eventType;
        this.severity = severity;
        this.description = description;
        this.ipAddress = ipAddress;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    public int getLogId() { return logId; }
    public void setLogId(int logId) { this.logId = logId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String toJson() {
        return String.format(
            "{\"logId\":%d,\"eventType\":\"%s\",\"severity\":\"%s\",\"description\":\"%s\",\"ipAddress\":\"%s\",\"userId\":%s,\"username\":\"%s\",\"createdAt\":\"%s\"}",
            logId, escapeJson(eventType), severity, escapeJson(description),
            escapeJson(ipAddress), userId != null ? userId.toString() : "null",
            username != null ? escapeJson(username) : "SYSTEM",
            createdAt != null ? createdAt.toString() : ""
        );
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
