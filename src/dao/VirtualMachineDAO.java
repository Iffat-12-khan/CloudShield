package dao;

import database.DBConnection;
import database.InMemoryStore;
import model.VirtualMachine;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * File Location: src/dao/VirtualMachineDAO.java
 * Short Purpose: Data Access Object for 'virtual_machines' table with full CRUD and status changes.
 * Connections:
 *   - Referenced by ResourceManager, WaitForGraph, and ApiController.
 *   - Manages VM lifecycle states: RUNNING, WAITING, STOPPED.
 */
public class VirtualMachineDAO {

    public List<VirtualMachine> findAll() {
        List<VirtualMachine> list = new ArrayList<>();
        if (!DBConnection.isPostgresAvailable()) {
            list.addAll(InMemoryStore.getInstance().vms.values());
            return list;
        }

        String sql = "SELECT vm.vm_id, vm.vm_name, vm.operating_system, vm.cpu_required, vm.ram_required, " +
                     "vm.storage_required, vm.status, vm.owner_id, u.username AS owner_username, vm.created_at " +
                     "FROM virtual_machines vm " +
                     "JOIN users u ON vm.owner_id = u.user_id " +
                     "ORDER BY vm.vm_id ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(extractVM(rs));
            }
        } catch (SQLException e) {
            System.err.println("[VirtualMachineDAO] Error fetching VMs: " + e.getMessage());
        }
        return list;
    }

    public VirtualMachine findById(int vmId) {
        if (!DBConnection.isPostgresAvailable()) {
            return InMemoryStore.getInstance().vms.get(vmId);
        }

        String sql = "SELECT vm.vm_id, vm.vm_name, vm.operating_system, vm.cpu_required, vm.ram_required, " +
                     "vm.storage_required, vm.status, vm.owner_id, u.username AS owner_username, vm.created_at " +
                     "FROM virtual_machines vm " +
                     "JOIN users u ON vm.owner_id = u.user_id " +
                     "WHERE vm.vm_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, vmId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return extractVM(rs);
            }
        } catch (SQLException e) {
            System.err.println("[VirtualMachineDAO] Error finding VM: " + e.getMessage());
        }
        return null;
    }

    public boolean createVM(VirtualMachine vm) {
        if (!DBConnection.isPostgresAvailable()) {
            int id = InMemoryStore.getInstance().nextVmId();
            vm.setVmId(id);
            vm.setCreatedAt(new Timestamp(System.currentTimeMillis()));
            InMemoryStore.getInstance().vms.put(id, vm);
            return true;
        }

        String sql = "INSERT INTO virtual_machines (vm_name, operating_system, cpu_required, ram_required, storage_required, status, owner_id) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING vm_id";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vm.getVmName());
            ps.setString(2, vm.getOperatingSystem());
            ps.setInt(3, vm.getCpuRequired());
            ps.setInt(4, vm.getRamRequired());
            ps.setInt(5, vm.getStorageRequired());
            ps.setString(6, vm.getStatus());
            ps.setInt(7, vm.getOwnerId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    vm.setVmId(rs.getInt(1));
                    return true;
                }
            }
        } catch (SQLException e) {
            System.err.println("[VirtualMachineDAO] Error creating VM: " + e.getMessage());
        }
        return false;
    }

    public boolean updateStatus(int vmId, String newStatus) {
        if (!DBConnection.isPostgresAvailable()) {
            VirtualMachine vm = InMemoryStore.getInstance().vms.get(vmId);
            if (vm != null) {
                vm.setStatus(newStatus);
                return true;
            }
            return false;
        }

        String sql = "UPDATE virtual_machines SET status = ? WHERE vm_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setInt(2, vmId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[VirtualMachineDAO] Error updating VM status: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteVM(int vmId) {
        if (!DBConnection.isPostgresAvailable()) {
            return InMemoryStore.getInstance().vms.remove(vmId) != null;
        }

        String sql = "DELETE FROM virtual_machines WHERE vm_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, vmId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[VirtualMachineDAO] Error deleting VM: " + e.getMessage());
            return false;
        }
    }

    private VirtualMachine extractVM(ResultSet rs) throws SQLException {
        VirtualMachine vm = new VirtualMachine(
            rs.getInt("vm_id"),
            rs.getString("vm_name"),
            rs.getString("operating_system"),
            rs.getInt("cpu_required"),
            rs.getInt("ram_required"),
            rs.getInt("storage_required"),
            rs.getString("status"),
            rs.getInt("owner_id"),
            rs.getTimestamp("created_at")
        );
        vm.setOwnerUsername(rs.getString("owner_username"));
        return vm;
    }
}
