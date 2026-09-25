-- ============================================================================
-- File: src/sql/06_triggers.sql
-- Purpose: Defines automated database triggers for cybersecurity and cloud state management.
-- Connection: Executed after 05_functions.sql. Reacts to INSERTs on login_logs and resource_requests.
-- ============================================================================

SET search_path TO public;

-- ----------------------------------------------------------------------------
-- Trigger 1: Automatic Brute-Force Detection & Account Lockout
-- When a FAILED login is logged, increment failed_attempts. If >= 3, BLOCK user!
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION trg_fn_handle_login_attempt()
RETURNS TRIGGER AS $$
DECLARE
    v_user_id INT;
    v_fails INT;
BEGIN
    IF NEW.status = 'FAILED' THEN
        -- Find user if exists
        SELECT user_id, failed_attempts INTO v_user_id, v_fails 
        FROM users 
        WHERE username = NEW.username;

        IF FOUND THEN
            v_fails := v_fails + 1;
            
            IF v_fails >= 3 THEN
                -- Auto-block account and log critical security breach
                UPDATE users 
                SET failed_attempts = v_fails, account_status = 'BLOCKED' 
                WHERE user_id = v_user_id;

                INSERT INTO security_logs (event_type, severity, description, ip_address, user_id)
                VALUES (
                    'BRUTE_FORCE_LOCKOUT', 
                    'CRITICAL', 
                    'Account ' || NEW.username || ' has been BLOCKED after ' || v_fails || ' failed login attempts.',
                    NEW.ip_address,
                    v_user_id
                );
            ELSE
                UPDATE users 
                SET failed_attempts = v_fails 
                WHERE user_id = v_user_id;

                INSERT INTO security_logs (event_type, severity, description, ip_address, user_id)
                VALUES (
                    'FAILED_LOGIN_ATTEMPT', 
                    'MEDIUM', 
                    'Failed login attempt (' || v_fails || '/3) for user: ' || NEW.username,
                    NEW.ip_address,
                    v_user_id
                );
            END IF;
        END IF;

    ELSIF NEW.status = 'SUCCESS' THEN
        -- Reset failed attempts on successful login
        UPDATE users 
        SET failed_attempts = 0 
        WHERE username = NEW.username;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_login_security ON login_logs;
CREATE TRIGGER trg_login_security
AFTER INSERT ON login_logs
FOR EACH ROW
EXECUTE FUNCTION trg_fn_handle_login_attempt();

-- ----------------------------------------------------------------------------
-- Trigger 2: Resource Pool Non-Negative Guard
-- Enforces integrity constraint before UPDATE on resources
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION trg_fn_check_resource_limits()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.available_units < 0 THEN
        RAISE EXCEPTION 'Resource pool breach: available units cannot be negative (% < 0)', NEW.available_units;
    END IF;
    IF NEW.available_units > NEW.total_units THEN
        RAISE EXCEPTION 'Resource pool breach: available units cannot exceed total units (% > %)', NEW.available_units, NEW.total_units;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_resource_limits ON resources;
CREATE TRIGGER trg_resource_limits
BEFORE UPDATE ON resources
FOR EACH ROW
EXECUTE FUNCTION trg_fn_check_resource_limits();

-- ----------------------------------------------------------------------------
-- Trigger 3: Synchronize VM Status to WAITING when Resource Request is Queued
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION trg_fn_sync_vm_waiting_status()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 'PENDING' THEN
        UPDATE virtual_machines 
        SET status = 'WAITING' 
        WHERE vm_id = NEW.vm_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_vm_waiting ON resource_requests;
CREATE TRIGGER trg_sync_vm_waiting
AFTER INSERT ON resource_requests
FOR EACH ROW
EXECUTE FUNCTION trg_fn_sync_vm_waiting_status();
