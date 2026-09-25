-- ============================================================================
-- File: src/database/databse.sql
-- Purpose: All-in-one consolidated PostgreSQL script for CloudShield.
-- Can be executed in a single run inside pgAdmin 4 Query Tool or psql.
-- ============================================================================

-- Step 1: Drop tables if existing
DROP TRIGGER IF EXISTS trg_login_security ON login_logs;
DROP TRIGGER IF EXISTS trg_resource_limits ON resources;
DROP TRIGGER IF EXISTS trg_sync_vm_waiting ON resource_requests;

DROP TABLE IF EXISTS security_logs CASCADE;
DROP TABLE IF EXISTS login_logs CASCADE;
DROP TABLE IF EXISTS deadlock_logs CASCADE;
DROP TABLE IF EXISTS wait_dependencies CASCADE;
DROP TABLE IF EXISTS resource_allocations CASCADE;
DROP TABLE IF EXISTS resource_requests CASCADE;
DROP TABLE IF EXISTS virtual_machines CASCADE;
DROP TABLE IF EXISTS resources CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- Step 2: Create Tables
CREATE TABLE users (
    user_id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    salt VARCHAR(64) NOT NULL DEFAULT 'cloudshield_salt',
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE,
    role VARCHAR(20) NOT NULL DEFAULT 'VIEWER',
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    failed_attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_user_role CHECK (role IN ('ADMIN', 'OPERATOR', 'VIEWER')),
    CONSTRAINT chk_user_status CHECK (account_status IN ('ACTIVE', 'BLOCKED')),
    CONSTRAINT chk_failed_attempts CHECK (failed_attempts >= 0)
);

CREATE TABLE resources (
    resource_id SERIAL PRIMARY KEY,
    resource_name VARCHAR(30) NOT NULL UNIQUE,
    total_units INT NOT NULL,
    available_units INT NOT NULL,
    unit VARCHAR(20) NOT NULL DEFAULT 'UNITS',
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_total_units CHECK (total_units > 0),
    CONSTRAINT chk_available_units CHECK (available_units >= 0 AND available_units <= total_units)
);

CREATE TABLE virtual_machines (
    vm_id SERIAL PRIMARY KEY,
    vm_name VARCHAR(100) NOT NULL,
    operating_system VARCHAR(50) NOT NULL,
    cpu_required INT NOT NULL DEFAULT 1,
    ram_required INT NOT NULL DEFAULT 2,
    storage_required INT NOT NULL DEFAULT 20,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    owner_id INT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vm_owner FOREIGN KEY (owner_id) 
        REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT chk_vm_status CHECK (status IN ('RUNNING', 'WAITING', 'STOPPED')),
    CONSTRAINT chk_vm_cpu CHECK (cpu_required > 0),
    CONSTRAINT chk_vm_ram CHECK (ram_required > 0),
    CONSTRAINT chk_vm_storage CHECK (storage_required > 0)
);

CREATE TABLE resource_requests (
    request_id SERIAL PRIMARY KEY,
    vm_id INT NOT NULL,
    resource_id INT NOT NULL,
    requested_units INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_req_vm FOREIGN KEY (vm_id) 
        REFERENCES virtual_machines(vm_id) ON DELETE CASCADE,
    CONSTRAINT fk_req_resource FOREIGN KEY (resource_id) 
        REFERENCES resources(resource_id) ON DELETE CASCADE,
    CONSTRAINT chk_req_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'ALLOCATED')),
    CONSTRAINT chk_requested_units CHECK (requested_units > 0)
);

CREATE TABLE resource_allocations (
    allocation_id SERIAL PRIMARY KEY,
    vm_id INT NOT NULL,
    resource_id INT NOT NULL,
    allocated_units INT NOT NULL,
    allocated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_alloc_vm FOREIGN KEY (vm_id) 
        REFERENCES virtual_machines(vm_id) ON DELETE CASCADE,
    CONSTRAINT fk_alloc_resource FOREIGN KEY (resource_id) 
        REFERENCES resources(resource_id) ON DELETE CASCADE,
    CONSTRAINT chk_allocated_units CHECK (allocated_units > 0)
);

CREATE TABLE wait_dependencies (
    dependency_id SERIAL PRIMARY KEY,
    waiting_vm_id INT NOT NULL,
    holding_vm_id INT NOT NULL,
    resource_id INT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wait_vm FOREIGN KEY (waiting_vm_id) 
        REFERENCES virtual_machines(vm_id) ON DELETE CASCADE,
    CONSTRAINT fk_hold_vm FOREIGN KEY (holding_vm_id) 
        REFERENCES virtual_machines(vm_id) ON DELETE CASCADE,
    CONSTRAINT fk_dep_resource FOREIGN KEY (resource_id) 
        REFERENCES resources(resource_id) ON DELETE CASCADE,
    CONSTRAINT chk_no_self_loop CHECK (waiting_vm_id <> holding_vm_id)
);

CREATE TABLE deadlock_logs (
    log_id SERIAL PRIMARY KEY,
    cycle_path TEXT NOT NULL,
    detected_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resolution_status VARCHAR(30) NOT NULL DEFAULT 'DETECTED',
    resolved_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT chk_deadlock_resolution CHECK (resolution_status IN ('DETECTED', 'RESOLVED', 'MANUAL_INTERVENTION'))
);

CREATE TABLE login_logs (
    log_id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    ip_address VARCHAR(45) NOT NULL DEFAULT '127.0.0.1',
    status VARCHAR(20) NOT NULL,
    attempt_time TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    failure_reason VARCHAR(255),
    CONSTRAINT chk_login_status CHECK (status IN ('SUCCESS', 'FAILED', 'BLOCKED'))
);

CREATE TABLE security_logs (
    log_id SERIAL PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'LOW',
    description TEXT NOT NULL,
    ip_address VARCHAR(45) NOT NULL DEFAULT '127.0.0.1',
    user_id INT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sec_user FOREIGN KEY (user_id) 
        REFERENCES users(user_id) ON DELETE SET NULL,
    CONSTRAINT chk_sec_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

-- Step 3: Views
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
    vm.created_at
FROM virtual_machines vm
JOIN users u ON vm.owner_id = u.user_id
LEFT JOIN resource_allocations ra ON vm.vm_id = ra.vm_id AND ra.released_at IS NULL
GROUP BY vm.vm_id, u.user_id;

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

-- Step 4: Sample Data
INSERT INTO users (username, password_hash, salt, full_name, email, role, account_status) VALUES
('admin', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'cloudshield_salt', 'System Administrator', 'admin@cloudshield.io', 'ADMIN', 'ACTIVE'),
('john',  'd04b98f48e8f8bcc15c6ae5ac050801cd6dcfd428fb5f9e65c4e16e7807340fa', 'cloudshield_salt', 'John Smith (DevOps)',    'john@cloudshield.io',  'OPERATOR', 'ACTIVE'),
('alex',  '5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8', 'cloudshield_salt', 'Alex Johnson (Auditor)', 'alex@cloudshield.io',  'VIEWER', 'ACTIVE');

INSERT INTO resources (resource_name, total_units, available_units, unit) VALUES
('CPU',     32,   18,   'Cores'),
('RAM',     128,  64,   'GB'),
('STORAGE', 2000, 1200, 'GB'),
('NETWORK', 100,  70,   'Gbps');

INSERT INTO virtual_machines (vm_name, operating_system, cpu_required, ram_required, storage_required, status, owner_id) VALUES
('Web-Frontend-Cluster', 'Ubuntu 22.04 LTS', 4,  16, 100, 'RUNNING', 1),
('Postgres-DB-Node',     'Debian 12 Bookworm', 8, 32, 500, 'RUNNING', 2),
('AI-Inference-Engine',  'Ubuntu 22.04 LTS', 16, 64, 800, 'WAITING', 3),
('Backup-Vault-Server',  'RedHat Enterprise 9', 4, 16, 600, 'WAITING', 2);

INSERT INTO resource_requests (vm_id, resource_id, requested_units, status) VALUES
(3, 1, 8,  'PENDING'),
(4, 2, 32, 'PENDING');

INSERT INTO resource_allocations (vm_id, resource_id, allocated_units) VALUES
(1, 1, 4), (1, 2, 16), (1, 3, 100),
(2, 1, 6), (2, 2, 24), (2, 3, 400),
(3, 1, 4), (3, 2, 24), (3, 3, 300);

-- Cycle: VM 3 -> VM 2 -> VM 1 -> VM 3
INSERT INTO wait_dependencies (waiting_vm_id, holding_vm_id, resource_id) VALUES
(3, 2, 2),
(2, 1, 1),
(1, 3, 3);

INSERT INTO deadlock_logs (cycle_path, resolution_status) VALUES
('VM-3 (AI-Inference-Engine) -> VM-2 (Postgres-DB-Node) -> VM-1 (Web-Frontend-Cluster) -> VM-3', 'DETECTED');

INSERT INTO login_logs (username, ip_address, status) VALUES
('admin', '192.168.1.10', 'SUCCESS'),
('john',  '192.168.1.25', 'SUCCESS'),
('alex',  '192.168.1.42', 'SUCCESS');

INSERT INTO security_logs (event_type, severity, description, ip_address, user_id) VALUES
('SYSTEM_INITIALIZATION', 'LOW',      'CloudShield Kernel & Security Subsystems Initialized.', '127.0.0.1', 1),
('ACCESS_GRANTED',        'LOW',      'Administrator authenticated successfully via TLS 1.3.', '192.168.1.10', 1),
('DEADLOCK_ALERT',        'HIGH',     'Circular wait detected among VMs [3, 2, 1] on resources [RAM, CPU, STORAGE].', '127.0.0.1', NULL),
('ANOMALOUS_BURST',       'MEDIUM',   'VM-3 requested 8 cores exceeding typical moving average.', '192.168.1.42', 3);