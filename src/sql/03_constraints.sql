-- ============================================================================
-- File: src/sql/03_constraints.sql
-- Purpose: Defines explicit indexes, multi-column unique constraints and performance tuning.
-- Connection: Executed after 02_tables.sql. Used to optimize joins in DAO queries and Views.
-- ============================================================================

SET search_path TO public;

-- Prevent duplicate wait-for graph edges between same VMs for same resource
ALTER TABLE wait_dependencies 
    ADD CONSTRAINT uq_wait_edge UNIQUE (waiting_vm_id, holding_vm_id, resource_id);

-- Prevent duplicate pending requests for identical VM and resource
CREATE UNIQUE INDEX idx_uq_pending_req 
    ON resource_requests(vm_id, resource_id) 
    WHERE status = 'PENDING';

-- Performance Indexes for foreign key lookups and JOIN queries
CREATE INDEX idx_vm_owner ON virtual_machines(owner_id);
CREATE INDEX idx_req_vm ON resource_requests(vm_id);
CREATE INDEX idx_req_resource ON resource_requests(resource_id);
CREATE INDEX idx_req_status ON resource_requests(status);
CREATE INDEX idx_alloc_vm ON resource_allocations(vm_id);
CREATE INDEX idx_alloc_resource ON resource_allocations(resource_id);
CREATE INDEX idx_wait_waiting_vm ON wait_dependencies(waiting_vm_id);
CREATE INDEX idx_wait_holding_vm ON wait_dependencies(holding_vm_id);
CREATE INDEX idx_login_logs_time ON login_logs(attempt_time DESC);
CREATE INDEX idx_security_logs_time ON security_logs(created_at DESC);
CREATE INDEX idx_security_logs_severity ON security_logs(severity);
