package dao;

import database.DBConnection;
import database.InMemoryStore;
import model.SecurityLog;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * File Location: src/dao/SecurityLogDAO.java
 * Short Purpose: Data Access Object for 'security_logs' table.
 * Connections:
 *   - Inserts alerts from ThreatDetector and SecurityManager.
 *   - Fetches threat logs for the Cybersecurity Threat Radar and Audit Logs screen.
 */
public class SecurityLogDAO {

    public List<SecurityLog> findAll(String severityFilter) {
        List<SecurityLog> list = new ArrayList<>();
        if (!DBConnection.isPostgresAvailable()) {
            for (SecurityLog sl : InMemoryStore.getInstance().securityLogs) {
                if (severityFilter == null || severityFilter.equalsIgnoreCase("ALL") || sl.getSeverity().equalsIgnoreCase(severityFilter)) {
                    list.add(sl);
                }
            }
            return list;
        }

        String sql = (severityFilter == null || severityFilter.equalsIgnoreCase("ALL"))
            ? "SELECT sl.log_id, sl.event_type, sl.severity, sl.description, sl.ip_address, sl.user_id, u.username, sl.created_at " +
              "FROM security_logs sl LEFT JOIN users u ON sl.user_id = u.user_id ORDER BY sl.log_id DESC LIMIT 100"
            : "SELECT sl.log_id, sl.event_type, sl.severity, sl.description, sl.ip_address, sl.user_id, u.username, sl.created_at " +
              "FROM security_logs sl LEFT JOIN users u ON sl.user_id = u.user_id WHERE sl.severity = ? ORDER BY sl.log_id DESC LIMIT 100";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (severityFilter != null && !severityFilter.equalsIgnoreCase("ALL")) {
                ps.setString(1, severityFilter.toUpperCase());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(extractLog(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[SecurityLogDAO] Error fetching security logs: " + e.getMessage());
        }
        return list;
    }

    public int recordEvent(String eventType, String severity, String description, String ipAddress, Integer userId) {
        if (!DBConnection.isPostgresAvailable()) {
            InMemoryStore store = InMemoryStore.getInstance();
            int id = store.nextSecId();
            Timestamp now = new Timestamp(System.currentTimeMillis());
            SecurityLog log = new SecurityLog(id, eventType, severity, description, ipAddress, userId, now);
            if (userId != null) {
                var u = store.users.get(userId);
                if (u != null) log.setUsername(u.getUsername());
            }
            store.securityLogs.add(0, log);
            return id;
        }

        String sql = "INSERT INTO security_logs (event_type, severity, description, ip_address, user_id) VALUES (?, ?, ?, ?, ?) RETURNING log_id";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventType);
            ps.setString(2, severity != null ? severity : "LOW");
            ps.setString(3, description);
            ps.setString(4, ipAddress != null ? ipAddress : "127.0.0.1");
            if (userId != null) ps.setInt(5, userId);
            else ps.setNull(5, Types.INTEGER);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("[SecurityLogDAO] Error recording security event: " + e.getMessage());
        }
        return -1;
    }

    private SecurityLog extractLog(ResultSet rs) throws SQLException {
        SecurityLog log = new SecurityLog(
            rs.getInt("log_id"),
            rs.getString("event_type"),
            rs.getString("severity"),
            rs.getString("description"),
            rs.getString("ip_address"),
            (Integer) rs.getObject("user_id"),
            rs.getTimestamp("created_at")
        );
        log.setUsername(rs.getString("username"));
        return log;
    }
}
