-- ============================================================================
-- File: src/sql/05_functions.sql
-- Purpose: Defines PostgreSQL stored functions for ACID transactions and cybersecurity procedures.
-- Connection: Called by JDBC DAOs (AllocationDAO, SecurityManager) and triggers.
-- ============================================================================

SET search_path TO public;

-- Enable pgcrypto extension if available for cryptographic hashing in PostgreSQL
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ----------------------------------------------------------------------------
-- Function 1: Hash Password with Salt using SHA-256
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_hash_password(p_password VARCHAR, p_salt VARCHAR)
RETURNS VARCHAR AS $$
BEGIN
    RETURN encode(digest(p_password || p_salt, 'sha256'), 'hex');
EXCEPTION
    WHEN undefined_function THEN
        -- Fallback if pgcrypto extension is restricted
        RETURN md5(p_password || p_salt);
END;
$$ LANGUAGE plpgsql;

-- ----------------------------------------------------------------------------
-- Function 2: Transactional Resource Allocation (ACID Compliant)
-- Atomically validates available units, inserts allocation record, and updates available balance.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_allocate_resource(
    p_vm_id INT,
    p_resource_id INT,
    p_units INT
)
RETURNS INT AS $$
DECLARE
    v_available INT;
    v_allocation_id INT;
BEGIN
    -- Check if VM exists and is not stopped
    IF NOT EXISTS (SELECT 1 FROM virtual_machines WHERE vm_id = p_vm_id AND status <> 'STOPPED') THEN
        RAISE EXCEPTION 'VM % is either non-existent or STOPPED.', p_vm_id;
    END IF;

    -- Lock resource row for update to prevent race conditions (Concurrency control)
    SELECT available_units INTO v_available 
    FROM resources 
    WHERE resource_id = p_resource_id 
    FOR UPDATE;

    IF v_available IS NULL THEN
        RAISE EXCEPTION 'Resource % not found.', p_resource_id;
    END IF;

    IF v_available < p_units THEN
        RAISE EXCEPTION 'Insufficient resources: requested %, only % available.', p_units, v_available;
    END IF;

    -- Deduct available units
    UPDATE resources 
    SET available_units = available_units - p_units 
    WHERE resource_id = p_resource_id;

    -- Insert into allocation table
    INSERT INTO resource_allocations (vm_id, resource_id, allocated_units)
    VALUES (p_vm_id, p_resource_id, p_units)
    RETURNING allocation_id INTO v_allocation_id;

    -- Mark corresponding pending request as ALLOCATED if one exists
    UPDATE resource_requests 
    SET status = 'ALLOCATED', processed_at = CURRENT_TIMESTAMP
    WHERE vm_id = p_vm_id AND resource_id = p_resource_id AND status = 'PENDING';

    -- Remove any wait dependency for this VM on this resource
    DELETE FROM wait_dependencies 
    WHERE waiting_vm_id = p_vm_id AND resource_id = p_resource_id;

    -- Update VM status to RUNNING
    UPDATE virtual_machines 
    SET status = 'RUNNING' 
    WHERE vm_id = p_vm_id;

    RETURN v_allocation_id;
END;
$$ LANGUAGE plpgsql;

-- ----------------------------------------------------------------------------
-- Function 3: Transactional Resource Release
-- Atomically returns allocated units back to the pool and marks allocation released.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_release_resource(p_allocation_id INT)
RETURNS BOOLEAN AS $$
DECLARE
    v_resource_id INT;
    v_units INT;
    v_vm_id INT;
BEGIN
    SELECT resource_id, allocated_units, vm_id 
    INTO v_resource_id, v_units, v_vm_id
    FROM resource_allocations 
    WHERE allocation_id = p_allocation_id AND released_at IS NULL;

    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;

    -- Add units back to available pool
    UPDATE resources 
    SET available_units = available_units + v_units 
    WHERE resource_id = v_resource_id;

    -- Mark allocation released
    UPDATE resource_allocations 
    SET released_at = CURRENT_TIMESTAMP 
    WHERE allocation_id = p_allocation_id;

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

-- ----------------------------------------------------------------------------
-- Function 4: Centralized Security Incident Recorder
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_record_security_event(
    p_event_type VARCHAR,
    p_severity VARCHAR,
    p_description TEXT,
    p_ip_address VARCHAR,
    p_user_id INT
)
RETURNS INT AS $$
DECLARE
    v_log_id INT;
BEGIN
    INSERT INTO security_logs (event_type, severity, description, ip_address, user_id)
    VALUES (p_event_type, p_severity, p_description, COALESCE(p_ip_address, '127.0.0.1'), p_user_id)
    RETURNING log_id INTO v_log_id;

    RETURN v_log_id;
END;
$$ LANGUAGE plpgsql;
