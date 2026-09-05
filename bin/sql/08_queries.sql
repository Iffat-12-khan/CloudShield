-- ============================================================================
-- File: src/sql/08_queries.sql
-- Purpose: Sample advanced SQL queries for project viva, reports, and grading.
-- Concepts demonstrated: INNER/LEFT JOINs, Subqueries, Aggregations, GROUP BY, 
--                        HAVING, Window Functions, and Date Filtering.
-- Connection: Referenced during viva examination and tested against PostgreSQL.
-- ============================================================================

SET search_path TO public;

-- ----------------------------------------------------------------------------
-- Query 1: Multi-Table JOIN: List all VMs with owner name, total allocations, and status
-- Demonstrates: LEFT JOIN, INNER JOIN, GROUP BY, COALESCE
-- ----------------------------------------------------------------------------
SELECT 
    vm.vm_id,
    vm.vm_name,
    vm.operating_system,
    vm.status,
    u.username AS owner_name,
    u.role AS owner_role,
    COUNT(ra.allocation_id) AS total_allocations,
    COALESCE(SUM(ra.allocated_units), 0) AS total_units_allocated
FROM virtual_machines vm
INNER JOIN users u ON vm.owner_id = u.user_id
LEFT JOIN resource_allocations ra ON vm.vm_id = ra.vm_id AND ra.released_at IS NULL
GROUP BY vm.vm_id, vm.vm_name, vm.operating_system, vm.status, u.username, u.role
ORDER BY total_units_allocated DESC;

-- ----------------------------------------------------------------------------
-- Query 2: Deadlock Graph Query: Identify Circular Wait Dependencies
-- Demonstrates: Self-joins on dependencies to detect direct cycles
-- ----------------------------------------------------------------------------
SELECT 
    w1.waiting_vm_id AS vm_a,
    vm1.vm_name AS vm_a_name,
    w1.holding_vm_id AS vm_b,
    vm2.vm_name AS vm_b_name,
    r1.resource_name AS resource_waited_by_a,
    w2.holding_vm_id AS vm_c,
    r2.resource_name AS resource_waited_by_b
FROM wait_dependencies w1
JOIN wait_dependencies w2 ON w1.holding_vm_id = w2.waiting_vm_id
JOIN virtual_machines vm1 ON w1.waiting_vm_id = vm1.vm_id
JOIN virtual_machines vm2 ON w1.holding_vm_id = vm2.vm_id
JOIN resources r1 ON w1.resource_id = r1.resource_id
JOIN resources r2 ON w2.resource_id = r2.resource_id;

-- ----------------------------------------------------------------------------
-- Query 3: Aggregation with HAVING: Find resources with > 50% utilization
-- Demonstrates: Arithmetic expressions, GROUP BY, HAVING clause
-- ----------------------------------------------------------------------------
SELECT 
    r.resource_name,
    r.total_units,
    r.available_units,
    (r.total_units - r.available_units) AS allocated_units,
    ROUND(((r.total_units - r.available_units)::NUMERIC / r.total_units::NUMERIC) * 100, 2) AS usage_pct
FROM resources r
GROUP BY r.resource_id, r.resource_name, r.total_units, r.available_units
HAVING ((r.total_units - r.available_units)::NUMERIC / r.total_units::NUMERIC) >= 0.50;

-- ----------------------------------------------------------------------------
-- Query 4: Subquery: Identify users who own VMs that are currently WAITING
-- Demonstrates: Correlated subquery with IN predicate
-- ----------------------------------------------------------------------------
SELECT 
    u.user_id,
    u.username,
    u.full_name,
    u.email,
    u.role
FROM users u
WHERE u.user_id IN (
    SELECT owner_id 
    FROM virtual_machines 
    WHERE status = 'WAITING'
);

-- ----------------------------------------------------------------------------
-- Query 5: Window Function: Rank Virtual Machines by RAM Requirement within OS category
-- Demonstrates: DENSE_RANK() OVER (PARTITION BY ... ORDER BY ...)
-- ----------------------------------------------------------------------------
SELECT 
    vm_id,
    vm_name,
    operating_system,
    ram_required,
    DENSE_RANK() OVER (
        PARTITION BY operating_system 
        ORDER BY ram_required DESC
    ) AS rank_by_ram_in_os
FROM virtual_machines;

-- ----------------------------------------------------------------------------
-- Query 6: Cybersecurity Threat Analysis: High & Critical incidents in the last 24 hours
-- Demonstrates: Date arithmetic with CURRENT_TIMESTAMP, string aggregation
-- ----------------------------------------------------------------------------
SELECT 
    event_type,
    severity,
    COUNT(*) AS total_occurrences,
    STRING_AGG(ip_address, ', ') AS originating_ips,
    MAX(created_at) AS last_seen
FROM security_logs
WHERE severity IN ('HIGH', 'CRITICAL')
  AND created_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
GROUP BY event_type, severity
ORDER BY total_occurrences DESC;
