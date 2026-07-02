-- Table: cloud.users

-- DROP TABLE IF EXISTS cloud.users;

CREATE TABLE IF NOT EXISTS cloud.users
(
    user_id integer NOT NULL DEFAULT nextval('cloud.users_user_id_seq'::regclass),
    username character varying(50) COLLATE pg_catalog."default" NOT NULL,
    password character varying(255) COLLATE pg_catalog."default" NOT NULL,
    full_name character varying(100) COLLATE pg_catalog."default" NOT NULL,
    email character varying(100) COLLATE pg_catalog."default",
    role character varying(20) COLLATE pg_catalog."default" NOT NULL,
    account_status character varying(20) COLLATE pg_catalog."default" DEFAULT 'ACTIVE'::character varying,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT users_pkey PRIMARY KEY (user_id),
    CONSTRAINT users_email_key UNIQUE (email),
    CONSTRAINT users_username_key UNIQUE (username),
    CONSTRAINT users_account_status_check CHECK (account_status::text = ANY (ARRAY['ACTIVE'::character varying, 'BLOCKED'::character varying]::text[])),
    CONSTRAINT users_role_check CHECK (role::text = ANY (ARRAY['ADMIN'::character varying, 'OPERATOR'::character varying, 'VIEWER'::character varying]::text[]))
)

TABLESPACE pg_default;

ALTER TABLE IF EXISTS cloud.users
    OWNER to postgres;
    
-- Table: cloud.resources

-- DROP TABLE IF EXISTS cloud.resources;

CREATE TABLE IF NOT EXISTS cloud.resources
(
    resource_id integer NOT NULL DEFAULT nextval('cloud.resources_resource_id_seq'::regclass),
    resource_name character varying(30) COLLATE pg_catalog."default" NOT NULL,
    total_units integer NOT NULL,
    available_units integer NOT NULL,
    unit character varying(20) COLLATE pg_catalog."default",
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT resources_pkey PRIMARY KEY (resource_id),
    CONSTRAINT resources_resource_name_key UNIQUE (resource_name),
    CONSTRAINT resources_available_units_check CHECK (available_units >= 0),
    CONSTRAINT resources_total_units_check CHECK (total_units > 0)
)

TABLESPACE pg_default;

ALTER TABLE IF EXISTS cloud.resources
    OWNER to postgres;
    
-- Table: cloud.virtual_machines

-- DROP TABLE IF EXISTS cloud.virtual_machines;

CREATE TABLE IF NOT EXISTS cloud.virtual_machines
(
    vm_id integer NOT NULL DEFAULT nextval('cloud.virtual_machines_vm_id_seq'::regclass),
    vm_name character varying(100) COLLATE pg_catalog."default" NOT NULL,
    operating_system character varying(50) COLLATE pg_catalog."default" NOT NULL,
    cpu_required integer,
    ram_required integer,
    storage_required integer,
    status character varying(20) COLLATE pg_catalog."default" DEFAULT 'RUNNING'::character varying,
    owner_id integer NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT virtual_machines_pkey PRIMARY KEY (vm_id),
    CONSTRAINT fk_vm_user FOREIGN KEY (owner_id)
        REFERENCES cloud.users (user_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE,
    CONSTRAINT virtual_machines_cpu_required_check CHECK (cpu_required > 0),
    CONSTRAINT virtual_machines_ram_required_check CHECK (ram_required > 0),
    CONSTRAINT virtual_machines_status_check CHECK (status::text = ANY (ARRAY['RUNNING'::character varying, 'WAITING'::character varying, 'STOPPED'::character varying]::text[])),
    CONSTRAINT virtual_machines_storage_required_check CHECK (storage_required > 0)
)

TABLESPACE pg_default;

ALTER TABLE IF EXISTS cloud.virtual_machines
    OWNER to postgres;
    
INSERT INTO users
(username,password,full_name,email,role)

VALUES

('admin','admin123','System Administrator','admin@cloud.com','ADMIN'),

('john','john123','John Smith','john@cloud.com','OPERATOR'),

('alex','alex123','Alex Johnson','alex@cloud.com','VIEWER');

INSERT INTO resources (resource_name,total_units,available_units,unit) VALUES
('CPU',32,32,'CORES'), ('RAM',128,128,'GB'),
('STORAGE',1000,1000,'GB'), ('NETWORK',100,100,'Gbps');

INSERT INTO virtual_machines (vm_name, operating_system, cpu_required, ram_required, storage_required, owner_id) VALUES
('Web Server', 'Ubuntu', 2, 4, 50, 1), ('Database Server', 'Ubuntu', 4, 8, 100, 2),
('AI Server', 'Windows', 8, 16, 250, 2),('Backup Server', 'Linux', 2, 4, 200, 3);