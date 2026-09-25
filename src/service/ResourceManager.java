package service;

import dao.*;
import model.*;

import java.util.List;

/**
 * File Location: src/service/ResourceManager.java
 * Short Purpose: Cloud Resource Provisioning Coordinator.
 *                Manages physical resource allocations, requests, limits, and wait dependencies.
 * Connections:
 *   - Coordinates between VirtualMachineDAO, ResourceDAO, ResourceRequestDAO, AllocationDAO, and WaitDependencyDAO.
 *   - Enforces cloud resource limits and triggers ACID transactions for allocation.
 */
public class ResourceManager {

    private final ResourceDAO resourceDAO = new ResourceDAO();
    private final VirtualMachineDAO vmDAO = new VirtualMachineDAO();
    private final ResourceRequestDAO requestDAO = new ResourceRequestDAO();
    private final AllocationDAO allocationDAO = new AllocationDAO();
    private final WaitDependencyDAO waitDAO = new WaitDependencyDAO();
    private final SecurityLogDAO securityDAO = new SecurityLogDAO();

    public List<Resource> getAllResources() {
        return resourceDAO.findAll();
    }

    public List<VirtualMachine> getAllVMs() {
        return vmDAO.findAll();
    }

    public List<ResourceRequest> getAllRequests() {
        return requestDAO.findAll();
    }

    public List<Allocation> getActiveAllocations() {
        return allocationDAO.findAllActive();
    }

    /**
     * Submits a request for resources. If resources are immediately available,
     * it can be allocated directly; otherwise, it is queued as PENDING and a wait dependency may be established.
     */
    public boolean submitResourceRequest(int vmId, int resourceId, int units) {
        Resource res = resourceDAO.findById(resourceId);
        VirtualMachine vm = vmDAO.findById(vmId);

        if (res == null || vm == null || units <= 0) {
            return false;
        }

        // Suspicious check: requesting more than total pool capacity
        if (units > res.getTotalUnits()) {
            securityDAO.recordEvent("SUSPICIOUS_REQUEST", "HIGH",
                "VM-" + vmId + " (" + vm.getVmName() + ") attempted to request " + units + " " + res.getUnit() + 
                ", exceeding cloud max capacity of " + res.getTotalUnits(), "127.0.0.1", vm.getOwnerId());
            return false;
        }

        ResourceRequest req = new ResourceRequest();
        req.setVmId(vmId);
        req.setResourceId(resourceId);
        req.setRequestedUnits(units);
        req.setStatus("PENDING");

        boolean created = requestDAO.createRequest(req);
        if (created) {
            // Check if available units are insufficient
            if (res.getAvailableUnits() < units) {
                vmDAO.updateStatus(vmId, "WAITING");

                // Establish a wait dependency if another VM holds this resource
                List<Allocation> activeAllocations = allocationDAO.findAllActive();
                for (Allocation a : activeAllocations) {
                    if (a.getResourceId() == resourceId && a.getVmId() != vmId) {
                        waitDAO.addDependency(vmId, a.getVmId(), resourceId);
                        break; // Link to the primary holder
                    }
                }
            }
        }
        return created;
    }

    /**
     * Approves and executes transactional allocation for a pending request.
     */
    public boolean approveAndAllocate(int requestId) {
        ResourceRequest req = requestDAO.findById(requestId);
        if (req == null || !"PENDING".equalsIgnoreCase(req.getStatus())) {
            return false;
        }

        boolean success = allocationDAO.allocateResourceTx(req.getVmId(), req.getResourceId(), req.getRequestedUnits(), requestId);
        if (success) {
            securityDAO.recordEvent("RESOURCE_ALLOCATED", "LOW",
                "Allocated " + req.getRequestedUnits() + " units of resource #" + req.getResourceId() + " to VM #" + req.getVmId(),
                "127.0.0.1", null);
        }
        return success;
    }

    /**
     * Rejects a pending request.
     */
    public boolean rejectRequest(int requestId) {
        return requestDAO.updateStatus(requestId, "REJECTED");
    }

    /**
     * Releases an active allocation and returns units to the available pool.
     */
    public boolean releaseAllocation(int allocationId) {
        boolean released = allocationDAO.releaseAllocationTx(allocationId);
        if (released) {
            securityDAO.recordEvent("RESOURCE_RELEASED", "LOW",
                "Resource allocation #" + allocationId + " released back to cloud capacity pool.",
                "127.0.0.1", null);
        }
        return released;
    }

    /**
     * Creates a new Virtual Machine.
     */
    public boolean createVM(VirtualMachine vm) {
        return vmDAO.createVM(vm);
    }

    /**
     * Updates VM operational status (RUNNING, STOPPED).
     */
    public boolean changeVMStatus(int vmId, String newStatus) {
        return vmDAO.updateStatus(vmId, newStatus);
    }

    /**
     * Deletes a VM and preempts its allocations and dependencies.
     */
    public boolean deleteVM(int vmId) {
        waitDAO.removeByVm(vmId);
        return vmDAO.deleteVM(vmId);
    }
}
