package dao;

import database.DBConnection;
import database.InMemoryStore;
import model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * File Location: src/dao/UserDAO.java
 * Short Purpose: Data Access Object for 'users' table using PreparedStatements.
 * Connections:
 *   - Called by SecurityManager for authentication, RBAC, and brute-force account locking.
 *   - Implements SQL queries against PostgreSQL with fallback to InMemoryStore.
 */
public class UserDAO {

    public User findByUsername(String username) {
        if (!DBConnection.isPostgresAvailable()) {
            for (User u : InMemoryStore.getInstance().users.values()) {
                if (u.getUsername().equalsIgnoreCase(username)) return u;
            }
            return null;
        }

        String sql = "SELECT user_id, username, password_hash, salt, full_name, email, role, account_status, failed_attempts, created_at FROM users WHERE username = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return extractUser(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] Error querying user: " + e.getMessage());
        }
        return null;
    }

    public List<User> findAll() {
        List<User> list = new ArrayList<>();
        if (!DBConnection.isPostgresAvailable()) {
            list.addAll(InMemoryStore.getInstance().users.values());
            return list;
        }

        String sql = "SELECT user_id, username, password_hash, salt, full_name, email, role, account_status, failed_attempts, created_at FROM users ORDER BY user_id ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(extractUser(rs));
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] Error loading users: " + e.getMessage());
        }
        return list;
    }

    public boolean updateFailedAttempts(int userId, int attempts, boolean blockAccount) {
        if (!DBConnection.isPostgresAvailable()) {
            User u = InMemoryStore.getInstance().users.get(userId);
            if (u != null) {
                u.setFailedAttempts(attempts);
                if (blockAccount) u.setAccountStatus("BLOCKED");
                return true;
            }
            return false;
        }

        String sql = blockAccount 
            ? "UPDATE users SET failed_attempts = ?, account_status = 'BLOCKED' WHERE user_id = ?"
            : "UPDATE users SET failed_attempts = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, attempts);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[UserDAO] Error updating failed attempts: " + e.getMessage());
            return false;
        }
    }

    public boolean resetFailedAttempts(int userId) {
        return updateFailedAttempts(userId, 0, false);
    }

    public boolean unblockUser(int userId) {
        if (!DBConnection.isPostgresAvailable()) {
            User u = InMemoryStore.getInstance().users.get(userId);
            if (u != null) {
                u.setAccountStatus("ACTIVE");
                u.setFailedAttempts(0);
                return true;
            }
            return false;
        }

        String sql = "UPDATE users SET account_status = 'ACTIVE', failed_attempts = 0 WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[UserDAO] Error unblocking user: " + e.getMessage());
            return false;
        }
    }

    private User extractUser(ResultSet rs) throws SQLException {
        return new User(
            rs.getInt("user_id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("salt"),
            rs.getString("full_name"),
            rs.getString("email"),
            rs.getString("role"),
            rs.getString("account_status"),
            rs.getInt("failed_attempts"),
            rs.getTimestamp("created_at")
        );
    }
}
