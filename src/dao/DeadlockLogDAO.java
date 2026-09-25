package dao;

import database.DBConnection;
import database.InMemoryStore;
import model.DeadlockLog;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * File Location: src/dao/DeadlockLogDAO.java
 * Short Purpose: Data Access Object for 'deadlock_logs' table.
 * Connections:
 *   - Inserts detected cycle incidents from DeadlockDetector.
 *   - Updates resolution status when a cycle is broken.
 *   - Displayed on the Deadlock Logs tab.
 */
public class DeadlockLogDAO {

    public List<DeadlockLog> findAll() {
        List<DeadlockLog> list = new ArrayList<>();
        if (!DBConnection.isPostgresAvailable()) {
            list.addAll(InMemoryStore.getInstance().deadlockLogs);
            return list;
        }

        String sql = "SELECT log_id, cycle_path, detected_at, resolution_status, resolved_at FROM deadlock_logs ORDER BY log_id DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(extractLog(rs));
            }
        } catch (SQLException e) {
            System.err.println("[DeadlockLogDAO] Error fetching deadlock logs: " + e.getMessage());
        }
        return list;
    }

    public int recordDeadlock(String cyclePath) {
        if (!DBConnection.isPostgresAvailable()) {
            int id = InMemoryStore.getInstance().nextDlId();
            Timestamp now = new Timestamp(System.currentTimeMillis());
            DeadlockLog dl = new DeadlockLog(id, cyclePath, now, "DETECTED", null);
            InMemoryStore.getInstance().deadlockLogs.add(0, dl);
            return id;
        }

        String sql = "INSERT INTO deadlock_logs (cycle_path, resolution_status) VALUES (?, 'DETECTED') RETURNING log_id";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cyclePath);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("[DeadlockLogDAO] Error recording deadlock: " + e.getMessage());
        }
        return -1;
    }

    public boolean markResolved(int logId) {
        if (!DBConnection.isPostgresAvailable()) {
            for (DeadlockLog dl : InMemoryStore.getInstance().deadlockLogs) {
                if (dl.getLogId() == logId || logId == -1) {
                    dl.setResolutionStatus("RESOLVED");
                    dl.setResolvedAt(new Timestamp(System.currentTimeMillis()));
                    return true;
                }
            }
            return false;
        }

        String sql = logId == -1 
            ? "UPDATE deadlock_logs SET resolution_status = 'RESOLVED', resolved_at = CURRENT_TIMESTAMP WHERE resolution_status = 'DETECTED'"
            : "UPDATE deadlock_logs SET resolution_status = 'RESOLVED', resolved_at = CURRENT_TIMESTAMP WHERE log_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (logId != -1) ps.setInt(1, logId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[DeadlockLogDAO] Error resolving deadlock log: " + e.getMessage());
            return false;
        }
    }

    private DeadlockLog extractLog(ResultSet rs) throws SQLException {
        return new DeadlockLog(
            rs.getInt("log_id"),
            rs.getString("cycle_path"),
            rs.getTimestamp("detected_at"),
            rs.getString("resolution_status"),
            rs.getTimestamp("resolved_at")
        );
    }
}
