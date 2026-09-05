-- ============================================================================
-- File: src/sql/01_database.sql
-- Purpose: Creates the PostgreSQL database for the CloudShield system.
-- Connection: Executed first in pgAdmin 4 or psql command line before creating tables.
-- ============================================================================

-- Terminate active connections if recreating (optional for clean setup)
-- SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'cloudshield';

DROP DATABASE IF EXISTS cloudshield;

-- Create the database with UTF-8 character encoding
CREATE DATABASE cloudshield
    WITH 
    OWNER = postgres
    ENCODING = 'UTF8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;

\c cloudshield;

-- Create dedicated schema for cloud isolation (optional, can also use public schema)
CREATE SCHEMA IF NOT EXISTS cloud AUTHORIZATION postgres;
COMMENT ON SCHEMA cloud IS 'CloudShield Resource, Deadlock & Threat Management Schema';
