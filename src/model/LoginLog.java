package model;

import java.sql.Timestamp;

/**
 * File Location: src/model/LoginLog.java
 * Short Purpose: Encapsulates authentication events, IP origin, status (SUCCESS/FAILED/BLOCKED), and failure reason.
 * Connections:
 *   - Mapped to database table 'login_logs' in src/sql/02_tables.sql.
 *   - Inserted by SecurityManager upon every login attempt.
 *   - Queried by ThreatDetector to identify brute-force patterns (rapid repeated failures).
 *   - Monitored by PostgreSQL trigger 'trg_login_security' to auto-block accounts.
 */
public class LoginLog {
    private int logId;
    private String username;
    private String ipAddress;
    private String status;         // "SUCCESS", "FAILED", "BLOCKED"
    private Timestamp attemptTime;
    private String failureReason;

    public LoginLog() {}

    public LoginLog(int logId, String username, String ipAddress, String status, Timestamp attemptTime, String failureReason) {
        this.logId = logId;
        this.username = username;
        this.ipAddress = ipAddress;
        this.status = status;
        this.attemptTime = attemptTime;
        this.failureReason = failureReason;
    }

    public int getLogId() { return logId; }
    public void setLogId(int logId) { this.logId = logId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getAttemptTime() { return attemptTime; }
    public void setAttemptTime(Timestamp attemptTime) { this.attemptTime = attemptTime; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public String toJson() {
        return String.format(
            "{\"logId\":%d,\"username\":\"%s\",\"ipAddress\":\"%s\",\"status\":\"%s\",\"attemptTime\":\"%s\",\"failureReason\":\"%s\"}",
            logId, escapeJson(username), escapeJson(ipAddress), status,
            attemptTime != null ? attemptTime.toString() : "",
            failureReason != null ? escapeJson(failureReason) : ""
        );
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
