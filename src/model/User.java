package model;

import java.sql.Timestamp;

/**
 * File Location: src/model/User.java
 * Short Purpose: Encapsulates user entity data for authentication, Role-Based Access Control (RBAC),
 *                and account security status (ACTIVE / BLOCKED).
 * Connections:
 *   - Mapped to database table 'users' in src/sql/02_tables.sql.
 *   - Used by UserDAO for database persistence and credential checks.
 *   - Used by SecurityManager for password verification, brute-force locking, and role checks.
 *   - Serialized to JSON by ApiController for client session state.
 */
public class User {
    private int userId;
    private String username;
    private String passwordHash;
    private String salt;
    private String fullName;
    private String email;
    private String role;           // "ADMIN", "OPERATOR", "VIEWER"
    private String accountStatus;   // "ACTIVE", "BLOCKED"
    private int failedAttempts;
    private Timestamp createdAt;

    public User() {
        this.role = "VIEWER";
        this.accountStatus = "ACTIVE";
        this.failedAttempts = 0;
    }

    public User(int userId, String username, String passwordHash, String salt, 
                String fullName, String email, String role, String accountStatus, 
                int failedAttempts, Timestamp createdAt) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.accountStatus = accountStatus;
        this.failedAttempts = failedAttempts;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getAccountStatus() { return accountStatus; }
    public void setAccountStatus(String accountStatus) { this.accountStatus = accountStatus; }

    public int getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public boolean isBlocked() {
        return "BLOCKED".equalsIgnoreCase(this.accountStatus);
    }

    /**
     * Sanitized JSON output without sensitive password hashes.
     */
    public String toJson() {
        return String.format(
            "{\"userId\":%d,\"username\":\"%s\",\"fullName\":\"%s\",\"email\":\"%s\",\"role\":\"%s\",\"accountStatus\":\"%s\",\"failedAttempts\":%d}",
            userId, escapeJson(username), escapeJson(fullName), 
            email != null ? escapeJson(email) : "", 
            role, accountStatus, failedAttempts
        );
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
