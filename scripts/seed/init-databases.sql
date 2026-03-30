-- =============================================================================
-- Impilo vNext — Per-service database initialization
-- Executed on first PostgreSQL container startup.
-- Each microservice gets its own database (no shared schemas).
-- =============================================================================

-- Create extension in template1 so all NEW databases get it
\c template1
CREATE EXTENSION IF NOT EXISTS pg_trgm;
\c postgres

-- Database and Schema Creation Helper
-- We create the database, connect to it, and create the service-specific schema.

-- Core services
CREATE DATABASE tshepo;
\c tshepo
CREATE SCHEMA IF NOT EXISTS tshepo;

CREATE DATABASE vito;
\c vito
CREATE SCHEMA IF NOT EXISTS vito;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE DATABASE varapi;
\c varapi
CREATE SCHEMA IF NOT EXISTS varapi;

CREATE DATABASE tuso;
\c tuso
CREATE SCHEMA IF NOT EXISTS tuso;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE DATABASE zibo;
\c zibo
CREATE SCHEMA IF NOT EXISTS zibo;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE DATABASE msika;
\c msika
CREATE SCHEMA IF NOT EXISTS msika;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE DATABASE ubomi;
\c ubomi
CREATE SCHEMA IF NOT EXISTS ubomi;

CREATE DATABASE pct;
\c pct
CREATE SCHEMA IF NOT EXISTS pct;

CREATE DATABASE oros;
\c oros
CREATE SCHEMA IF NOT EXISTS oros;

CREATE DATABASE pharmacy;
\c pharmacy
CREATE SCHEMA IF NOT EXISTS pharmacy;

CREATE DATABASE inpatient;
\c inpatient
CREATE SCHEMA IF NOT EXISTS inpatient;

CREATE DATABASE inventory;
\c inventory
CREATE SCHEMA IF NOT EXISTS inventory;

CREATE DATABASE mushex;
\c mushex
CREATE SCHEMA IF NOT EXISTS mushex;

CREATE DATABASE costing;
\c costing
CREATE SCHEMA IF NOT EXISTS costing;

CREATE DATABASE document_service;
\c document_service
CREATE SCHEMA IF NOT EXISTS document_service;

CREATE DATABASE notification;
\c notification
CREATE SCHEMA IF NOT EXISTS notification;

CREATE DATABASE jobs;
\c jobs
CREATE SCHEMA IF NOT EXISTS jobs;

CREATE DATABASE offline_sync;
\c offline_sync
CREATE SCHEMA IF NOT EXISTS offline_sync;

CREATE DATABASE integration_hub;
\c integration_hub
CREATE SCHEMA IF NOT EXISTS integration_hub;

-- TSHEPO decomposition
CREATE DATABASE tshepo_authz;
\c tshepo_authz
CREATE SCHEMA IF NOT EXISTS tshepo_authz;

CREATE DATABASE tshepo_identity;
\c tshepo_identity
CREATE SCHEMA IF NOT EXISTS tshepo_identity;

CREATE DATABASE tshepo_consent;
\c tshepo_consent
CREATE SCHEMA IF NOT EXISTS tshepo_consent;

CREATE DATABASE tshepo_audit;
\c tshepo_audit
CREATE SCHEMA IF NOT EXISTS tshepo_audit;

CREATE DATABASE tshepo_keys;
\c tshepo_keys
CREATE SCHEMA IF NOT EXISTS tshepo_keys;

CREATE DATABASE tshepo_offline;
\c tshepo_offline
CREATE SCHEMA IF NOT EXISTS tshepo_offline;

-- Experience Platform
CREATE DATABASE experience_bff;
\c experience_bff
CREATE SCHEMA IF NOT EXISTS experience_bff;

-- Auth / Identity
CREATE DATABASE keycloak;

-- Clinical / FHIR
CREATE DATABASE butano;
CREATE DATABASE butano_fhir;
CREATE DATABASE fhir_gateway;
CREATE DATABASE pacs;

-- Others (minimal init)
CREATE DATABASE product_registry;
CREATE DATABASE inventory_elmis;
CREATE DATABASE pharmacy_elmis;
CREATE DATABASE landela_adapter;
CREATE DATABASE credential_verification;
CREATE DATABASE share_slip;
CREATE DATABASE card_print;
CREATE DATABASE msika_flow;
CREATE DATABASE reporting;
CREATE DATABASE ndr;
CREATE DATABASE impilo_ndr;
CREATE DATABASE impilo_data_warehouse;
CREATE DATABASE pipeline;
CREATE DATABASE impilo_data_ingestion;
CREATE DATABASE impilo_data_governance;
CREATE DATABASE dags;
CREATE DATABASE secharden;
CREATE DATABASE surv;
CREATE DATABASE camp;
CREATE DATABASE impilo_audit_ledger;
CREATE DATABASE impilo_forms;
CREATE DATABASE impilo_search;
CREATE DATABASE impilo_channels;
CREATE DATABASE impilo_connector_fhir;
CREATE DATABASE impilo_dispatch;
CREATE DATABASE impilo_rules;
CREATE DATABASE impilo_workflow;
CREATE DATABASE impilo_schema_registry;
CREATE DATABASE impilo_iot_ingestion;
CREATE DATABASE impilo_asset_registry;
CREATE DATABASE impilo_offline_edge;
CREATE DATABASE impilo_coverage;
CREATE DATABASE impilo_indawo;
CREATE DATABASE costa_db;
CREATE DATABASE mushex_db;
CREATE DATABASE impilo_dev_portal;
CREATE DATABASE impilo_support;
CREATE DATABASE observability;
CREATE DATABASE impilo_identity_assurance;
