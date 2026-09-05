package dao;

import database.DBConnection;
import database.InMemoryStore;
import model.Resource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * File Location: src/dao/ResourceDAO.java
 * Short Purpose: Data Access Object for cloud hardware resource pools (CPU, RAM, Storage, Network).
 * Connections:
 *   - Called by ResourceManager to query capacity and adjust available units.
 *   - Queried by ApiController to populate dashboard hardware meters.
 */
public class ResourceDAO {

    public List<Resource> findAll() {
        List<Resource> list = new ArrayList<>();
        if (!DBConnection.isPostgresAvailable()) {
            list.addAll(InMemoryStore.getInstance().resources.values());
            return list;
        }

        String sql = "SELECT resource_id, resource_name, total_units, available_units, unit, created_at FROM resources ORDER BY resource_id ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(extractResource(rs));
            }
        } catch (SQLException e) {
            System.err.println("[ResourceDAO] Error fetching resources: " + e.getMessage());
        }
        return list;
    }

    public Resource findById(int resourceId) {
        if (!DBConnection.isPostgresAvailable()) {
            return InMemoryStore.getInstance().resources.get(resourceId);
        }

        String sql = "SELECT resource_id, resource_name, total_units, available_units, unit, created_at FROM resources WHERE resource_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, resourceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return extractResource(rs);
            }
        } catch (SQLException e) {
            System.err.println("[ResourceDAO] Error finding resource: " + e.getMessage());
        }
        return null;
    }

    public boolean updateAvailableUnits(Connection conn, int resourceId, int newAvailable) throws SQLException {
        String sql = "UPDATE resources SET available_units = ? WHERE resource_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newAvailable);
            ps.setInt(2, resourceId);
            return ps.executeUpdate() > 0;
        }
    }

    private Resource extractResource(ResultSet rs) throws SQLException {
        return new Resource(
            rs.getInt("resource_id"),
            rs.getString("resource_name"),
            rs.getInt("total_units"),
            rs.getInt("available_units"),
            rs.getString("unit"),
            rs.getTimestamp("created_at")
        );
    }
}
