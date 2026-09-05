-- ============================================================================
-- File: src/sql/02_tables.sql
-- Purpose: Defines the 9 relational tables for CloudShield with integrity constraints.
-- Connection: Depends on 01_database.sql. Referenced by 03_constraints.sql, 04_views.sql, 
--             and mapped to Java Model classes in src/model/*.
-- ============================================================================

-- Ensure we work in public schema for straightforward JDBC access
SET search_path TO public;

-- Drop tables in reverse order of foreign key dependency
DROP TABLE IF EXISTS security_logs CASCADE;
DROP TABLE IF EXISTS login_logs CASCADE;
DROP TABLE IF EXISTS deadlock_logs CASCADE;
DROP TABLE IF EXISTS wait_dependencies CASCADE;
DROP TABLE IF EXISTS resource_allocations CASCADE;
DROP TABLE IF EXISTS resource_requests CASCADE;
DROP TABLE IF EXISTS virtual_machines CASCADE;
DROP TABLE IF EXISTS resources CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- ----------------------------------------------------------------------------
-- Table 1: USERS (Authentication, RBAC & Account Security)
-- ----------------------------------------------------------------------------
CREATE TABLE users (
    user_id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    salt VARCHAR(64) NOT NULL DEFAULT 'cloudshield_default_salt',
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

-- ----------------------------------------------------------------------------
-- Table 2: RESOURCES (Cloud Hardware Pools - CPU, RAM, Storage, Network)
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- Table 3: VIRTUAL_MACHINES (Cloud Compute Instances)
-- ----------------------------------------------------------------------------
CREATE TABLE virtual_machines (
    vm_id SERIAL PRIMARY KEY,
    vm_name VARCHAR(100) NOT NULL,
    operating_system VARCHAR(50) NOT NULL,
    cpu_required INT NOT NULL DEFAULT 1,
    ram_required INT NOT NULL DEFAULT 2,        -- in GB
    storage_required INT NOT NULL DEFAULT 20,   -- in GB
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

-- ----------------------------------------------------------------------------
-- Table 4: RESOURCE_REQUESTS (Requests submitted by VMs for resource allocation)
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- Table 5: RESOURCE_ALLOCATIONS (Active and historical resource grants)
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- Table 6: WAIT_DEPENDENCIES (Wait-For Graph Edges: Waiting VM -> Holding VM)
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- Table 7: DEADLOCK_LOGS (Audit log of circular wait deadlock incidents)
-- ----------------------------------------------------------------------------
CREATE TABLE deadlock_logs (
    log_id SERIAL PRIMARY KEY,
    cycle_path TEXT NOT NULL,
    detected_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resolution_status VARCHAR(30) NOT NULL DEFAULT 'DETECTED',
    resolved_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT chk_deadlock_resolution CHECK (resolution_status IN ('DETECTED', 'RESOLVED', 'MANUAL_INTERVENTION'))
);

-- ----------------------------------------------------------------------------
-- Table 8: LOGIN_LOGS (Audit log of user authentication attempts)
-- ----------------------------------------------------------------------------
CREATE TABLE login_logs (
    log_id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    ip_address VARCHAR(45) NOT NULL DEFAULT '127.0.0.1',
    status VARCHAR(20) NOT NULL,
    attempt_time TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    failure_reason VARCHAR(255),
    CONSTRAINT chk_login_status CHECK (status IN ('SUCCESS', 'FAILED', 'BLOCKED'))
);

-- ----------------------------------------------------------------------------
-- Table 9: SECURITY_LOGS (Audit trail for cybersecurity threat detection)
-- ----------------------------------------------------------------------------
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
