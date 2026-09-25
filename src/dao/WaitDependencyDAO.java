package dao;

import database.DBConnection;
import database.InMemoryStore;
import model.WaitDependency;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * File Location: src/dao/WaitDependencyDAO.java
 * Short Purpose: Data Access Object for 'wait_dependencies' table.
 * Connections:
 *   - Fetches and modifies directed wait-edges (waiting_vm -> holding_vm).
 *   - Read by WaitForGraph and DeadlockDetector to discover cycles.
 *   - Cleaned up when deadlocks are resolved or resources are allocated.
 */
public class WaitDependencyDAO {

    public List<WaitDependency> findAll() {
        List<WaitDependency> list = new ArrayList<>();
        if (!DBConnection.isPostgresAvailable()) {
            list.addAll(InMemoryStore.getInstance().dependencies.values());
            return list;
        }

        String sql = "SELECT wd.dependency_id, wd.waiting_vm_id, wvm.vm_name AS waiting_vm_name, " +
                     "wd.holding_vm_id, hvm.vm_name AS holding_vm_name, wd.resource_id, r.resource_name, wd.created_at " +
                     "FROM wait_dependencies wd " +
                     "JOIN virtual_machines wvm ON wd.waiting_vm_id = wvm.vm_id " +
                     "JOIN virtual_machines hvm ON wd.holding_vm_id = hvm.vm_id " +
                     "JOIN resources r ON wd.resource_id = r.resource_id " +
                     "ORDER BY wd.dependency_id ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(extractDependency(rs));
            }
        } catch (SQLException e) {
            System.err.println("[WaitDependencyDAO] Error loading dependencies: " + e.getMessage());
        }
        return list;
    }

    public boolean addDependency(int waitingVmId, int holdingVmId, int resourceId) {
        if (waitingVmId == holdingVmId) return false;

        if (!DBConnection.isPostgresAvailable()) {
            InMemoryStore store = InMemoryStore.getInstance();
            int id = store.nextDepId();
            Timestamp now = new Timestamp(System.currentTimeMillis());
            WaitDependency wd = new WaitDependency(id, waitingVmId, holdingVmId, resourceId, now);
            var wVm = store.vms.get(waitingVmId);
            var hVm = store.vms.get(holdingVmId);
            var res = store.resources.get(resourceId);
            if (wVm != null) wd.setWaitingVmName(wVm.getVmName());
            if (hVm != null) wd.setHoldingVmName(hVm.getVmName());
            if (res != null) wd.setResourceName(res.getResourceName());
            store.dependencies.put(id, wd);
            return true;
        }

        String sql = "INSERT INTO wait_dependencies (waiting_vm_id, holding_vm_id, resource_id) VALUES (?, ?, ?) " +
                     "ON CONFLICT DO NOTHING";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, waitingVmId);
            ps.setInt(2, holdingVmId);
            ps.setInt(3, resourceId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[WaitDependencyDAO] Error adding dependency: " + e.getMessage());
            return false;
        }
    }

    public boolean removeDependency(int dependencyId) {
        if (!DBConnection.isPostgresAvailable()) {
            return InMemoryStore.getInstance().dependencies.remove(dependencyId) != null;
        }

        String sql = "DELETE FROM wait_dependencies WHERE dependency_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, dependencyId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[WaitDependencyDAO] Error deleting dependency: " + e.getMessage());
            return false;
        }
    }

    public boolean removeByVm(int vmId) {
        if (!DBConnection.isPostgresAvailable()) {
            return InMemoryStore.getInstance().dependencies.values()
                .removeIf(d -> d.getWaitingVmId() == vmId || d.getHoldingVmId() == vmId);
        }

        String sql = "DELETE FROM wait_dependencies WHERE waiting_vm_id = ? OR holding_vm_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, vmId);
            ps.setInt(2, vmId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[WaitDependencyDAO] Error clearing VM dependencies: " + e.getMessage());
            return false;
        }
    }

    public void clearAll() {
        if (!DBConnection.isPostgresAvailable()) {
            InMemoryStore.getInstance().dependencies.clear();
            return;
        }
        String sql = "TRUNCATE TABLE wait_dependencies RESTART IDENTITY";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[WaitDependencyDAO] Error clearing dependencies: " + e.getMessage());
        }
    }

    private WaitDependency extractDependency(ResultSet rs) throws SQLException {
        WaitDependency wd = new WaitDependency(
            rs.getInt("dependency_id"),
            rs.getInt("waiting_vm_id"),
            rs.getInt("holding_vm_id"),
            rs.getInt("resource_id"),
            rs.getTimestamp("created_at")
        );
        wd.setWaitingVmName(rs.getString("waiting_vm_name"));
        wd.setHoldingVmName(rs.getString("holding_vm_name"));
        wd.setResourceName(rs.getString("resource_name"));
        return wd;
    }
}
