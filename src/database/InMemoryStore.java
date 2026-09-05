package database;

import model.*;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * File Location: src/database/InMemoryStore.java
 * Short Purpose: Thread-safe in-memory database simulation loaded with sample seed data.
 *                Ensures full functionality during BCA viva demonstrations even if PostgreSQL
 *                credentials or local service are not configured.
 * Connections:
 *   - Referenced by DAO classes as a secondary fail-safe data provider.
 *   - Matches exact schema of src/sql/02_tables.sql and sample data of src/sql/07_sample_data.sql.
 */
public class InMemoryStore {
    private static final InMemoryStore INSTANCE = new InMemoryStore();

    public static InMemoryStore getInstance() {
        return INSTANCE;
    }

    public final Map<Integer, User> users = new ConcurrentHashMap<>();
    public final Map<Integer, Resource> resources = new ConcurrentHashMap<>();
    public final Map<Integer, VirtualMachine> vms = new ConcurrentHashMap<>();
    public final Map<Integer, ResourceRequest> requests = new ConcurrentHashMap<>();
    public final Map<Integer, Allocation> allocations = new ConcurrentHashMap<>();
    public final Map<Integer, WaitDependency> dependencies = new ConcurrentHashMap<>();
    public final List<DeadlockLog> deadlockLogs = Collections.synchronizedList(new ArrayList<>());
    public final List<LoginLog> loginLogs = Collections.synchronizedList(new ArrayList<>());
    public final List<SecurityLog> securityLogs = Collections.synchronizedList(new ArrayList<>());

    private final AtomicInteger userSeq = new AtomicInteger(4);
    private final AtomicInteger vmSeq = new AtomicInteger(5);
    private final AtomicInteger reqSeq = new AtomicInteger(3);
    private final AtomicInteger allocSeq = new AtomicInteger(10);
    private final AtomicInteger depSeq = new AtomicInteger(4);
    private final AtomicInteger dlSeq = new AtomicInteger(2);
    private final AtomicInteger logSeq = new AtomicInteger(5);
    private final AtomicInteger secSeq = new AtomicInteger(5);

    private InMemoryStore() {
        resetSampleData();
    }

    public synchronized void resetSampleData() {
        users.clear();
        resources.clear();
        vms.clear();
        requests.clear();
        allocations.clear();
        dependencies.clear();
        deadlockLogs.clear();
        loginLogs.clear();
        securityLogs.clear();

        Timestamp now = new Timestamp(System.currentTimeMillis());

        service.SecurityManager sm = new service.SecurityManager();
        String adminHash = sm.hashPassword("admin123", "cloudshield_salt");
        String johnHash = sm.hashPassword("john123", "cloudshield_salt");
        String alexHash = sm.hashPassword("alex123", "cloudshield_salt");

        // 1. Users
        users.put(1, new User(1, "admin", adminHash, "cloudshield_salt", "System Administrator", "admin@cloudshield.io", "ADMIN", "ACTIVE", 0, now));
        users.put(2, new User(2, "john", johnHash, "cloudshield_salt", "John Smith (DevOps)", "john@cloudshield.io", "OPERATOR", "ACTIVE", 0, now));
        users.put(3, new User(3, "alex", alexHash, "cloudshield_salt", "Alex Johnson (Auditor)", "alex@cloudshield.io", "VIEWER", "ACTIVE", 0, now));

        // 2. Resources
        resources.put(1, new Resource(1, "CPU", 32, 18, "Cores", now));
        resources.put(2, new Resource(2, "RAM", 128, 64, "GB", now));
        resources.put(3, new Resource(3, "STORAGE", 2000, 1200, "GB", now));
        resources.put(4, new Resource(4, "NETWORK", 100, 70, "Gbps", now));

        // 3. Virtual Machines
        VirtualMachine vm1 = new VirtualMachine(1, "Web-Frontend-Cluster", "Ubuntu 22.04 LTS", 4, 16, 100, "RUNNING", 1, now);
        vm1.setOwnerUsername("admin");
        VirtualMachine vm2 = new VirtualMachine(2, "Postgres-DB-Node", "Debian 12 Bookworm", 8, 32, 500, "RUNNING", 2, now);
        vm2.setOwnerUsername("john");
        VirtualMachine vm3 = new VirtualMachine(3, "AI-Inference-Engine", "Ubuntu 22.04 LTS", 16, 64, 800, "WAITING", 3, now);
        vm3.setOwnerUsername("alex");
        VirtualMachine vm4 = new VirtualMachine(4, "Backup-Vault-Server", "RedHat Enterprise 9", 4, 16, 600, "WAITING", 2, now);
        vm4.setOwnerUsername("john");

        vms.put(1, vm1);
        vms.put(2, vm2);
        vms.put(3, vm3);
        vms.put(4, vm4);

        // 4. Resource Requests
        ResourceRequest r1 = new ResourceRequest(1, 3, 1, 8, "PENDING", now, null);
        r1.setVmName("AI-Inference-Engine");
        r1.setResourceName("CPU");
        ResourceRequest r2 = new ResourceRequest(2, 4, 2, 32, "PENDING", now, null);
        r2.setVmName("Backup-Vault-Server");
        r2.setResourceName("RAM");
        requests.put(1, r1);
        requests.put(2, r2);

        // 5. Resource Allocations
        addAllocation(1, 1, 4, now);
        addAllocation(1, 2, 16, now);
        addAllocation(1, 3, 100, now);
        addAllocation(2, 1, 6, now);
        addAllocation(2, 2, 24, now);
        addAllocation(2, 3, 400, now);
        addAllocation(3, 1, 4, now);
        addAllocation(3, 2, 24, now);
        addAllocation(3, 3, 300, now);

        // 6. Wait Dependencies (Deadlock Cycle: 3 -> 2 -> 1 -> 3)
        addWaitDependency(3, 2, 2, now);
        addWaitDependency(2, 1, 1, now);
        addWaitDependency(1, 3, 3, now);

        // 7. Deadlock Logs
        deadlockLogs.add(new DeadlockLog(1, "VM-3 (AI-Inference-Engine) -> VM-2 (Postgres-DB-Node) -> VM-1 (Web-Frontend-Cluster) -> VM-3", now, "DETECTED", null));

        // 8. Login Logs
        loginLogs.add(new LoginLog(1, "admin", "192.168.1.10", "SUCCESS", now, null));
        loginLogs.add(new LoginLog(2, "john", "192.168.1.25", "SUCCESS", now, null));
        loginLogs.add(new LoginLog(3, "alex", "192.168.1.42", "SUCCESS", now, null));
        loginLogs.add(new LoginLog(4, "guest", "192.168.1.99", "FAILED", now, "User does not exist"));

        // 9. Security Logs
        securityLogs.add(new SecurityLog(1, "SYSTEM_INITIALIZATION", "LOW", "CloudShield Kernel & Security Subsystems Initialized.", "127.0.0.1", 1, now));
        securityLogs.add(new SecurityLog(2, "ACCESS_GRANTED", "LOW", "Administrator authenticated successfully via TLS 1.3.", "192.168.1.10", 1, now));
        securityLogs.add(new SecurityLog(3, "DEADLOCK_ALERT", "HIGH", "Circular wait detected among VMs [3, 2, 1] on resources [RAM, CPU, STORAGE].", "127.0.0.1", null, now));
        securityLogs.add(new SecurityLog(4, "ANOMALOUS_BURST", "MEDIUM", "VM-3 requested 8 cores exceeding typical moving average.", "192.168.1.42", 3, now));
    }

    private void addAllocation(int vmId, int resId, int units, Timestamp now) {
        int id = allocSeq.getAndIncrement();
        Allocation a = new Allocation(id, vmId, resId, units, now, null);
        VirtualMachine vm = vms.get(vmId);
        if (vm != null) a.setVmName(vm.getVmName());
        Resource res = resources.get(resId);
        if (res != null) a.setResourceName(res.getResourceName());
        allocations.put(id, a);
    }

    private void addWaitDependency(int waitVmId, int holdVmId, int resId, Timestamp now) {
        int id = depSeq.getAndIncrement();
        WaitDependency wd = new WaitDependency(id, waitVmId, holdVmId, resId, now);
        VirtualMachine wVm = vms.get(waitVmId);
        VirtualMachine hVm = vms.get(holdVmId);
        Resource res = resources.get(resId);
        if (wVm != null) wd.setWaitingVmName(wVm.getVmName());
        if (hVm != null) wd.setHoldingVmName(hVm.getVmName());
        if (res != null) wd.setResourceName(res.getResourceName());
        dependencies.put(id, wd);
    }

    public int nextUserId() { return userSeq.getAndIncrement(); }
    public int nextVmId() { return vmSeq.getAndIncrement(); }
    public int nextReqId() { return reqSeq.getAndIncrement(); }
    public int nextAllocId() { return allocSeq.getAndIncrement(); }
    public int nextDepId() { return depSeq.getAndIncrement(); }
    public int nextDlId() { return dlSeq.getAndIncrement(); }
    public int nextLogId() { return logSeq.getAndIncrement(); }
    public int nextSecId() { return secSeq.getAndIncrement(); }
}
