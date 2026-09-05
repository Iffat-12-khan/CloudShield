package dao;

import database.DBConnection;
import database.InMemoryStore;
import model.ResourceRequest;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * File Location: src/dao/ResourceRequestDAO.java
 * Short Purpose: Data Access Object for 'resource_requests' table.
 * Connections:
 *   - Inserts and updates resource requests.
 *   - Used by ResourceManager to query pending requests and update their state.
 */
public class ResourceRequestDAO {

    public List<ResourceRequest> findAll() {
        List<ResourceRequest> list = new ArrayList<>();
        if (!DBConnection.isPostgresAvailable()) {
            list.addAll(InMemoryStore.getInstance().requests.values());
            return list;
        }

        String sql = "SELECT req.request_id, req.vm_id, vm.vm_name, req.resource_id, r.resource_name, " +
                     "req.requested_units, req.status, req.requested_at, req.processed_at " +
                     "FROM resource_requests req " +
                     "JOIN virtual_machines vm ON req.vm_id = vm.vm_id " +
                     "JOIN resources r ON req.resource_id = r.resource_id " +
                     "ORDER BY req.request_id DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(extractRequest(rs));
            }
        } catch (SQLException e) {
            System.err.println("[ResourceRequestDAO] Error fetching requests: " + e.getMessage());
        }
        return list;
    }

    public ResourceRequest findById(int requestId) {
        if (!DBConnection.isPostgresAvailable()) {
            return InMemoryStore.getInstance().requests.get(requestId);
        }

        String sql = "SELECT req.request_id, req.vm_id, vm.vm_name, req.resource_id, r.resource_name, " +
                     "req.requested_units, req.status, req.requested_at, req.processed_at " +
                     "FROM resource_requests req " +
                     "JOIN virtual_machines vm ON req.vm_id = vm.vm_id " +
                     "JOIN resources r ON req.resource_id = r.resource_id " +
                     "WHERE req.request_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return extractRequest(rs);
            }
        } catch (SQLException e) {
            System.err.println("[ResourceRequestDAO] Error finding request: " + e.getMessage());
        }
        return null;
    }

    public boolean createRequest(ResourceRequest req) {
        if (!DBConnection.isPostgresAvailable()) {
            int id = InMemoryStore.getInstance().nextReqId();
            req.setRequestId(id);
            req.setRequestedAt(new Timestamp(System.currentTimeMillis()));
            var vm = InMemoryStore.getInstance().vms.get(req.getVmId());
            var res = InMemoryStore.getInstance().resources.get(req.getResourceId());
            if (vm != null) req.setVmName(vm.getVmName());
            if (res != null) req.setResourceName(res.getResourceName());
            InMemoryStore.getInstance().requests.put(id, req);
            return true;
        }

        String sql = "INSERT INTO resource_requests (vm_id, resource_id, requested_units, status) " +
                     "VALUES (?, ?, ?, 'PENDING') RETURNING request_id";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, req.getVmId());
            ps.setInt(2, req.getResourceId());
            ps.setInt(3, req.getRequestedUnits());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    req.setRequestId(rs.getInt(1));
                    return true;
                }
            }
        } catch (SQLException e) {
            System.err.println("[ResourceRequestDAO] Error creating request: " + e.getMessage());
        }
        return false;
    }

    public boolean updateStatus(int requestId, String status) {
        if (!DBConnection.isPostgresAvailable()) {
            ResourceRequest req = InMemoryStore.getInstance().requests.get(requestId);
            if (req != null) {
                req.setStatus(status);
                req.setProcessedAt(new Timestamp(System.currentTimeMillis()));
                return true;
            }
            return false;
        }

        String sql = "UPDATE resource_requests SET status = ?, processed_at = CURRENT_TIMESTAMP WHERE request_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, requestId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ResourceRequestDAO] Error updating request status: " + e.getMessage());
            return false;
        }
    }

    private ResourceRequest extractRequest(ResultSet rs) throws SQLException {
        ResourceRequest req = new ResourceRequest(
            rs.getInt("request_id"),
            rs.getInt("vm_id"),
            rs.getInt("resource_id"),
            rs.getInt("requested_units"),
            rs.getString("status"),
            rs.getTimestamp("requested_at"),
            rs.getTimestamp("processed_at")
        );
        req.setVmName(rs.getString("vm_name"));
        req.setResourceName(rs.getString("resource_name"));
        return req;
    }
}
