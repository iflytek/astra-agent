-- PostgreSQL initialization script
-- Note: This script will be executed automatically when the container starts for the first time

-- Ensure we connect to the correct database
-- The database specified by POSTGRES_DB environment variable is already created at container startup
\c sparkdb_manager

-- Create schema
CREATE SCHEMA sparkdb_manager;

-- Create database metadata table
CREATE TABLE IF NOT EXISTS sparkdb_manager.database_meta (
  id bigint primary key not null,
  app_id character varying,
  uid character varying(64) not null,
  name character varying not null,
  description character varying,
  create_at timestamp without time zone not null default CURRENT_TIMESTAMP,
  update_at timestamp without time zone not null default CURRENT_TIMESTAMP,
  create_by character varying,
  update_by character varying,
  space_id character varying -- team space id
);

-- Create indexes
CREATE INDEX IF NOT EXISTS database_meta_app_id_index ON sparkdb_manager.database_meta USING btree (app_id);
CREATE INDEX IF NOT EXISTS database_meta_uid_index ON sparkdb_manager.database_meta USING btree (uid);
CREATE INDEX IF NOT EXISTS database_meta_space_id_index ON sparkdb_manager.database_meta USING btree (space_id);
CREATE UNIQUE INDEX IF NOT EXISTS unique_uid_name_space_id ON sparkdb_manager.database_meta USING btree (uid, name, space_id);

-- Add table comments
COMMENT ON COLUMN sparkdb_manager.database_meta.space_id IS 'team space id';

-- Create schema metadata table
CREATE TABLE IF NOT EXISTS sparkdb_manager.schema_meta (
  id bigint primary key not null,
  database_id bigint not null,
  schema_name character varying not null,
  create_at timestamp without time zone not null default CURRENT_TIMESTAMP,
  update_at timestamp without time zone not null default CURRENT_TIMESTAMP,
  create_by character varying,
  update_by character varying
);

-- Output initialization completion information
\echo 'PostgreSQL database initialization completed'
\echo 'Created tables: database_meta, schema_meta'