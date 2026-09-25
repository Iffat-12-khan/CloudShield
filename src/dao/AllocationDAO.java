package dao;

import database.DBConnection;
import database.InMemoryStore;
import model.Allocation;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * File Location: src/dao/AllocationDAO.java
 * Short Purpose: Manages resource allocations using explicit JDBC ACID Transactions.
 *                Implements setAutoCommit(false), commit(), and rollback() for data consistency.
 * Connections:
 *   - Called by ResourceManager to execute atomic multi-step resource grants.
 *   - Updates resources pool balance and marks requests ALLOCATED.
 *   - Highlighted in viva presentation to illustrate ACID transactions in Java.
 */
public class AllocationDAO {

    public List<Allocation> findAllActive() {
        List<Allocation> list = new ArrayList<>();
        if (!DBConnection.isPostgresAvailable()) {
            for (Allocation a : InMemoryStore.getInstance().allocations.values()) {
                if (a.isActive()) list.add(a);
            }
            return list;
        }

        String sql = "SELECT a.allocation_id, a.vm_id, vm.vm_name, a.resource_id, r.resource_name, " +
                     "a.allocated_units, a.allocated_at, a.released_at " +
                     "FROM resource_allocations a " +
                     "JOIN virtual_machines vm ON a.vm_id = vm.vm_id " +
                     "JOIN resources r ON a.resource_id = r.resource_id " +
                     "WHERE a.released_at IS NULL " +
                     "ORDER BY a.allocation_id DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(extractAllocation(rs));
            }
        } catch (SQLException e) {
            System.err.println("[AllocationDAO] Error fetching allocations: " + e.getMessage());
        }
        return list;
    }

    /**
     * Executes atomic resource allocation using standard JDBC Transaction management:
     * 1. conn.setAutoCommit(false)
     * 2. Lock & check available units (SELECT ... FOR UPDATE)
     * 3. Update resources table (available_units = available_units - requested)
     * 4. Insert into resource_allocations
     * 5. Update resource_requests (status = 'ALLOCATED')
     * 6. Remove any wait dependencies for this VM & resource
     * 7. conn.commit()
     * In case of any error: conn.rollback()
     */
    public boolean allocateResourceTx(int vmId, int resourceId, int units, Integer requestId) {
        if (!DBConnection.isPostgresAvailable()) {
            // In-memory atomic simulation
            InMemoryStore store = InMemoryStore.getInstance();
            synchronized (store) {
                var res = store.resources.get(resourceId);
                var vm = store.vms.get(vmId);
                if (res == null || vm == null || res.getAvailableUnits() < units) {
                    return false;
                }
                res.setAvailableUnits(res.getAvailableUnits() - units);
                int allocId = store.nextAllocId();
                Timestamp now = new Timestamp(System.currentTimeMillis());
                Allocation a = new Allocation(allocId, vmId, resourceId, units, now, null);
                a.setVmName(vm.getVmName());
                a.setResourceName(res.getResourceName());
                store.allocations.put(allocId, a);

                if (requestId != null) {
                    var req = store.requests.get(requestId);
                    if (req != null) {
                        req.setStatus("ALLOCATED");
                        req.setProcessedAt(now);
                    }
                }
                // Remove wait dependencies
                store.dependencies.values().removeIf(d -> d.getWaitingVmId() == vmId && d.getResourceId() == resourceId);
                vm.setStatus("RUNNING");
                return true;
            }
        }

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false); // Begin ACID Transaction

            // 1. Lock and inspect available units
            int available = -1;
            String checkSql = "SELECT available_units FROM resources WHERE resource_id = ? FOR UPDATE";
            try (PreparedStatement psCheck = conn.prepareStatement(checkSql)) {
                psCheck.setInt(1, resourceId);
                try (ResultSet rs = psCheck.executeQuery()) {
                    if (rs.next()) available = rs.getInt("available_units");
                }
            }

            if (available < units) {
                conn.rollback();
                return false;
            }

            // 2. Deduct available units
            String updateResSql = "UPDATE resources SET available_units = available_units - ? WHERE resource_id = ?";
            try (PreparedStatement psRes = conn.prepareStatement(updateResSql)) {
                psRes.setInt(1, units);
                psRes.setInt(2, resourceId);
                psRes.executeUpdate();
            }

            // 3. Insert allocation record
            String insertAllocSql = "INSERT INTO resource_allocations (vm_id, resource_id, allocated_units) VALUES (?, ?, ?)";
            try (PreparedStatement psAlloc = conn.prepareStatement(insertAllocSql)) {
                psAlloc.setInt(1, vmId);
                psAlloc.setInt(2, resourceId);
                psAlloc.setInt(3, units);
                psAlloc.executeUpdate();
            }

            // 4. Update request if provided
            if (requestId != null) {
                String updateReqSql = "UPDATE resource_requests SET status = 'ALLOCATED', processed_at = CURRENT_TIMESTAMP WHERE request_id = ?";
                try (PreparedStatement psReq = conn.prepareStatement(updateReqSql)) {
                    psReq.setInt(1, requestId);
                    psReq.executeUpdate();
                }
            }

            // 5. Remove corresponding wait dependencies
            String delDepSql = "DELETE FROM wait_dependencies WHERE waiting_vm_id = ? AND resource_id = ?";
            try (PreparedStatement psDep = conn.prepareStatement(delDepSql)) {
                psDep.setInt(1, vmId);
                psDep.setInt(2, resourceId);
                psDep.executeUpdate();
            }

            // 6. Update VM status to RUNNING
            String updateVmSql = "UPDATE virtual_machines SET status = 'RUNNING' WHERE vm_id = ?";
            try (PreparedStatement psVm = conn.prepareStatement(updateVmSql)) {
                psVm.setInt(1, vmId);
                psVm.executeUpdate();
            }

            conn.commit(); // Commit ACID Transaction
            return true;

        } catch (SQLException e) {
            System.err.println("[AllocationDAO] Transaction rolled back due to error: " + e.getMessage());
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            return false;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    /**
     * Releases an allocation and restores units back to the pool.
     */
    public boolean releaseAllocationTx(int allocationId) {
        if (!DBConnection.isPostgresAvailable()) {
            InMemoryStore store = InMemoryStore.getInstance();
            synchronized (store) {
                Allocation a = store.allocations.get(allocationId);
                if (a != null && a.isActive()) {
                    var res = store.resources.get(a.getResourceId());
                    if (res != null) {
                        res.setAvailableUnits(Math.min(res.getTotalUnits(), res.getAvailableUnits() + a.getAllocatedUnits()));
                    }
                    a.setReleasedAt(new Timestamp(System.currentTimeMillis()));
                    return true;
                }
                return false;
            }
        }

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            int resId = -1;
            int units = 0;
            String findSql = "SELECT resource_id, allocated_units FROM resource_allocations WHERE allocation_id = ? AND released_at IS NULL FOR UPDATE";
            try (PreparedStatement ps = conn.prepareStatement(findSql)) {
                ps.setInt(1, allocationId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        resId = rs.getInt("resource_id");
                        units = rs.getInt("allocated_units");
                    }
                }
            }

            if (resId == -1) {
                conn.rollback();
                return false;
            }

            // Restore units
            String restoreSql = "UPDATE resources SET available_units = available_units + ? WHERE resource_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(restoreSql)) {
                ps.setInt(1, units);
                ps.setInt(2, resId);
                ps.executeUpdate();
            }

            // Mark released
            String markSql = "UPDATE resource_allocations SET released_at = CURRENT_TIMESTAMP WHERE allocation_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(markSql)) {
                ps.setInt(1, allocationId);
                ps.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            System.err.println("[AllocationDAO] Release rollback: " + e.getMessage());
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            return false;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    private Allocation extractAllocation(ResultSet rs) throws SQLException {
        Allocation a = new Allocation(
            rs.getInt("allocation_id"),
            rs.getInt("vm_id"),
            rs.getInt("resource_id"),
            rs.getInt("allocated_units"),
            rs.getTimestamp("allocated_at"),
            rs.getTimestamp("released_at")
        );
        a.setVmName(rs.getString("vm_name"));
        a.setResourceName(rs.getString("resource_name"));
        return a;
    }
}
