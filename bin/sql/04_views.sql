-- ============================================================================
-- File: src/sql/04_views.sql
-- Purpose: Defines analytical database views for reporting, metrics and dashboard queries.
-- Connection: Used by ReportServlet, ApiController, and displayed on the UI Reports tab.
-- ============================================================================

SET search_path TO public;

-- ----------------------------------------------------------------------------
-- View 1: VM Resource Summary (Joins VMs, Users, and Active Allocations)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW view_vm_resource_summary AS
SELECT 
    vm.vm_id,
    vm.vm_name,
    vm.operating_system,
    vm.status AS vm_status,
    u.user_id AS owner_id,
    u.username AS owner_username,
    u.role AS owner_role,
    vm.cpu_required,
    vm.ram_required,
    vm.storage_required,
    COALESCE(COUNT(ra.allocation_id), 0) AS active_allocations_count,
    COALESCE(SUM(CASE WHEN r.resource_name = 'CPU' THEN ra.allocated_units ELSE 0 END), 0) AS allocated_cpu,
    COALESCE(SUM(CASE WHEN r.resource_name = 'RAM' THEN ra.allocated_units ELSE 0 END), 0) AS allocated_ram,
    COALESCE(SUM(CASE WHEN r.resource_name = 'STORAGE' THEN ra.allocated_units ELSE 0 END), 0) AS allocated_storage,
    COALESCE(SUM(CASE WHEN r.resource_name = 'NETWORK' THEN ra.allocated_units ELSE 0 END), 0) AS allocated_network,
    vm.created_at
FROM virtual_machines vm
JOIN users u ON vm.owner_id = u.user_id
LEFT JOIN resource_allocations ra ON vm.vm_id = ra.vm_id AND ra.released_at IS NULL
LEFT JOIN resources r ON ra.resource_id = r.resource_id
GROUP BY vm.vm_id, u.user_id;

-- ----------------------------------------------------------------------------
-- View 2: Resource Pool Utilization (Live usage percentage per resource)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW view_resource_utilization AS
SELECT 
    r.resource_id,
    r.resource_name,
    r.total_units,
    r.available_units,
    (r.total_units - r.available_units) AS allocated_units,
    ROUND(((r.total_units - r.available_units)::NUMERIC / r.total_units::NUMERIC) * 100, 2) AS utilization_percentage,
    r.unit,
    r.created_at
FROM resources r;

-- ----------------------------------------------------------------------------
-- View 3: Active Wait Graph (Human-readable wait dependencies)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW view_active_wait_graph AS
SELECT 
    wd.dependency_id,
    wvm.vm_id AS waiting_vm_id,
    wvm.vm_name AS waiting_vm_name,
    hvm.vm_id AS holding_vm_id,
    hvm.vm_name AS holding_vm_name,
    r.resource_id,
    r.resource_name,
    wd.created_at
FROM wait_dependencies wd
JOIN virtual_machines wvm ON wd.waiting_vm_id = wvm.vm_id
JOIN virtual_machines hvm ON wd.holding_vm_id = hvm.vm_id
JOIN resources r ON wd.resource_id = r.resource_id;

-- ----------------------------------------------------------------------------
-- View 4: Security Incident Analytics (Aggregated by event type & severity)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW view_security_incident_report AS
SELECT 
    event_type,
    severity,
    COUNT(*) AS incident_count,
    MAX(created_at) AS latest_incident_at
FROM security_logs
GROUP BY event_type, severity
ORDER BY incident_count DESC;

-- ----------------------------------------------------------------------------
-- View 5: System Key Performance Indicators (Dashboard High-level KPIs)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW view_system_kpis AS
SELECT 
    (SELECT COUNT(*) FROM virtual_machines) AS total_vms,
    (SELECT COUNT(*) FROM virtual_machines WHERE status = 'RUNNING') AS running_vms,
    (SELECT COUNT(*) FROM virtual_machines WHERE status = 'WAITING') AS waiting_vms,
    (SELECT COUNT(*) FROM users) AS total_users,
    (SELECT COUNT(*) FROM users WHERE account_status = 'BLOCKED') AS blocked_users,
    (SELECT COUNT(*) FROM resource_requests WHERE status = 'PENDING') AS pending_requests,
    (SELECT COUNT(*) FROM wait_dependencies) AS active_dependencies_count,
    (SELECT COUNT(*) FROM deadlock_logs WHERE resolution_status = 'DETECTED') AS unresolved_deadlocks,
    (SELECT COUNT(*) FROM security_logs WHERE severity IN ('HIGH', 'CRITICAL')) AS critical_threats;
