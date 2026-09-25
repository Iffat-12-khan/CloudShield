package dao;

import database.DBConnection;
import database.InMemoryStore;
import model.LoginLog;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * File Location: src/dao/LoginLogDAO.java
 * Short Purpose: Data Access Object for 'login_logs' table.
 * Connections:
 *   - Inserts authentication attempts from SecurityManager.
 *   - Queries failed login attempts within rolling time windows to detect brute-force attacks.
 *   - Displayed in the Audit Logs screen.
 */
public class LoginLogDAO {

    public List<LoginLog> findAll() {
        List<LoginLog> list = new ArrayList<>();
        if (!DBConnection.isPostgresAvailable()) {
            list.addAll(InMemoryStore.getInstance().loginLogs);
            return list;
        }

        String sql = "SELECT log_id, username, ip_address, status, attempt_time, failure_reason FROM login_logs ORDER BY log_id DESC LIMIT 50";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(extractLog(rs));
            }
        } catch (SQLException e) {
            System.err.println("[LoginLogDAO] Error fetching login logs: " + e.getMessage());
        }
        return list;
    }

    public boolean recordLogin(String username, String ipAddress, String status, String reason) {
        if (!DBConnection.isPostgresAvailable()) {
            int id = InMemoryStore.getInstance().nextLogId();
            Timestamp now = new Timestamp(System.currentTimeMillis());
            LoginLog log = new LoginLog(id, username, ipAddress, status, now, reason);
            InMemoryStore.getInstance().loginLogs.add(0, log);
            return true;
        }

        String sql = "INSERT INTO login_logs (username, ip_address, status, failure_reason) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, ipAddress != null ? ipAddress : "127.0.0.1");
            ps.setString(3, status);
            ps.setString(4, reason);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[LoginLogDAO] Error recording login attempt: " + e.getMessage());
            return false;
        }
    }

    /**
     * Counts failed logins for a specific username in the last 5 minutes.
     */
    public int countRecentFailures(String username, int windowMinutes) {
        if (!DBConnection.isPostgresAvailable()) {
            long threshold = System.currentTimeMillis() - (windowMinutes * 60 * 1000L);
            int count = 0;
            for (LoginLog l : InMemoryStore.getInstance().loginLogs) {
                if (l.getUsername().equalsIgnoreCase(username) && "FAILED".equalsIgnoreCase(l.getStatus())) {
                    if (l.getAttemptTime() != null && l.getAttemptTime().getTime() >= threshold) {
                        count++;
                    }
                }
            }
            return count;
        }

        String sql = "SELECT COUNT(*) FROM login_logs WHERE username = ? AND status = 'FAILED' AND attempt_time >= (CURRENT_TIMESTAMP - INTERVAL '" + windowMinutes + " minutes')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("[LoginLogDAO] Error counting failures: " + e.getMessage());
        }
        return 0;
    }

    private LoginLog extractLog(ResultSet rs) throws SQLException {
        return new LoginLog(
            rs.getInt("log_id"),
            rs.getString("username"),
            rs.getString("ip_address"),
            rs.getString("status"),
            rs.getTimestamp("attempt_time"),
            rs.getString("failure_reason")
        );
    }
}
