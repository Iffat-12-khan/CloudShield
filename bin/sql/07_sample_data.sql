-- ============================================================================
-- File: src/sql/07_sample_data.sql
-- Purpose: Inserts realistic seed data into all 9 tables for demonstration and viva testing.
-- Connection: Executed after 06_triggers.sql. Mapped to test cases in Java layer.
-- ============================================================================

SET search_path TO public;

-- Clean existing data
TRUNCATE TABLE security_logs, login_logs, deadlock_logs, wait_dependencies, 
               resource_allocations, resource_requests, virtual_machines, 
               resources, users RESTART IDENTITY CASCADE;

-- ----------------------------------------------------------------------------
-- 1. Insert Users (Admin, Operator, Viewer with Salted SHA-256 Hashes)
-- Passwords:
-- admin   / admin123
-- john    / john123
-- alex    / alex123
-- ----------------------------------------------------------------------------
INSERT INTO users (username, password_hash, salt, full_name, email, role, account_status, failed_attempts) VALUES
('admin', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'cloudshield_salt', 'System Administrator', 'admin@cloudshield.io', 'ADMIN', 'ACTIVE', 0),
('john',  'd04b98f48e8f8bcc15c6ae5ac050801cd6dcfd428fb5f9e65c4e16e7807340fa', 'cloudshield_salt', 'John Smith (DevOps)',    'john@cloudshield.io',  'OPERATOR', 'ACTIVE', 0),
('alex',  '5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8', 'cloudshield_salt', 'Alex Johnson (Auditor)', 'alex@cloudshield.io',  'VIEWER', 'ACTIVE', 0);

-- ----------------------------------------------------------------------------
-- 2. Insert Cloud Resources (Physical Hardware Pools)
-- ----------------------------------------------------------------------------
INSERT INTO resources (resource_name, total_units, available_units, unit) VALUES
('CPU',     32,   18,   'Cores'),
('RAM',     128,  64,   'GB'),
('STORAGE', 2000, 1200, 'GB'),
('NETWORK', 100,  70,   'Gbps');

-- ----------------------------------------------------------------------------
-- 3. Insert Virtual Machines
-- ----------------------------------------------------------------------------
INSERT INTO virtual_machines (vm_name, operating_system, cpu_required, ram_required, storage_required, status, owner_id) VALUES
('Web-Frontend-Cluster', 'Ubuntu 22.04 LTS', 4,  16, 100, 'RUNNING', 1),
('Postgres-DB-Node',     'Debian 12 Bookworm', 8, 32, 500, 'RUNNING', 2),
('AI-Inference-Engine',  'Ubuntu 22.04 LTS', 16, 64, 800, 'WAITING', 3),
('Backup-Vault-Server',  'RedHat Enterprise 9', 4, 16, 600, 'WAITING', 2);

-- ----------------------------------------------------------------------------
-- 4. Insert Resource Requests
-- ----------------------------------------------------------------------------
INSERT INTO resource_requests (vm_id, resource_id, requested_units, status) VALUES
(3, 1, 8,  'PENDING'),   -- AI Engine waiting for 8 CPU cores
(4, 2, 32, 'PENDING');   -- Backup Vault waiting for 32 GB RAM

-- ----------------------------------------------------------------------------
-- 5. Insert Active Resource Allocations
-- ----------------------------------------------------------------------------
INSERT INTO resource_allocations (vm_id, resource_id, allocated_units) VALUES
(1, 1, 4),    -- Web-Frontend holds 4 CPU
(1, 2, 16),   -- Web-Frontend holds 16 RAM
(1, 3, 100),  -- Web-Frontend holds 100 Storage
(2, 1, 6),    -- DB-Node holds 6 CPU
(2, 2, 24),   -- DB-Node holds 24 RAM
(2, 3, 400),  -- DB-Node holds 400 Storage
(3, 1, 4),    -- AI-Engine holds 4 CPU
(3, 2, 24),   -- AI-Engine holds 24 RAM
(3, 3, 300);  -- AI-Engine holds 300 Storage

-- ----------------------------------------------------------------------------
-- 6. Insert Wait Dependencies (Configured to demonstrate a Deadlock Cycle!)
-- Cycle: VM 3 -> VM 2 -> VM 1 -> VM 3
-- ----------------------------------------------------------------------------
INSERT INTO wait_dependencies (waiting_vm_id, holding_vm_id, resource_id) VALUES
(3, 2, 2),   -- VM 3 (AI Engine) is waiting for VM 2 (DB Node) holding RAM
(2, 1, 1),   -- VM 2 (DB Node) is waiting for VM 1 (Web Cluster) holding CPU
(1, 3, 3);   -- VM 1 (Web Cluster) is waiting for VM 3 (AI Engine) holding STORAGE

-- ----------------------------------------------------------------------------
-- 7. Insert Deadlock Incident Logs
-- ----------------------------------------------------------------------------
INSERT INTO deadlock_logs (cycle_path, resolution_status) VALUES
('VM-3 (AI-Inference-Engine) -> VM-2 (Postgres-DB-Node) -> VM-1 (Web-Frontend-Cluster) -> VM-3', 'DETECTED');

-- ----------------------------------------------------------------------------
-- 8. Insert Login Logs
-- ----------------------------------------------------------------------------
INSERT INTO login_logs (username, ip_address, status, failure_reason) VALUES
('admin', '192.168.1.10', 'SUCCESS', NULL),
('john',  '192.168.1.25', 'SUCCESS', NULL),
('alex',  '192.168.1.42', 'SUCCESS', NULL),
('guest', '192.168.1.99', 'FAILED',  'User does not exist');

-- ----------------------------------------------------------------------------
-- 9. Insert Security Audit Logs
-- ----------------------------------------------------------------------------
INSERT INTO security_logs (event_type, severity, description, ip_address, user_id) VALUES
('SYSTEM_INITIALIZATION', 'LOW',      'CloudShield Kernel & Security Subsystems Initialized.', '127.0.0.1', 1),
('ACCESS_GRANTED',        'LOW',      'Administrator authenticated successfully via TLS 1.3.', '192.168.1.10', 1),
('DEADLOCK_ALERT',        'HIGH',     'Circular wait detected among VMs [3, 2, 1] on resources [RAM, CPU, STORAGE].', '127.0.0.1', NULL),
('ANOMALOUS_BURST',       'MEDIUM',   'VM-3 requested 8 cores exceeding typical moving average.', '192.168.1.42', 3);
