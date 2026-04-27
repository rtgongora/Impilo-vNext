-- =============================================================================
-- Impilo vNext — Per-service database initialization
-- Executed on first PostgreSQL container startup.
-- Each microservice gets its own database (no shared schemas).
-- =============================================================================

-- Core services
CREATE DATABASE tshepo;
CREATE DATABASE vito;
CREATE DATABASE varapi;
CREATE DATABASE tuso;
CREATE DATABASE zibo;
CREATE DATABASE msika;
CREATE DATABASE ubomi;
CREATE DATABASE pct;
CREATE DATABASE oros;
CREATE DATABASE pharmacy;
CREATE DATABASE inpatient;
CREATE DATABASE inventory;
CREATE DATABASE mushex;
CREATE DATABASE costing;
CREATE DATABASE document_service;
CREATE DATABASE notification;
CREATE DATABASE jobs;
CREATE DATABASE offline_sync;
CREATE DATABASE integration_hub;

-- TSHEPO decomposition (6 sub-service databases)
CREATE DATABASE tshepo_authz;
CREATE DATABASE tshepo_identity;
CREATE DATABASE tshepo_consent;
CREATE DATABASE mvumo;
CREATE DATABASE tshepo_audit;
CREATE DATABASE tshepo_keys;
CREATE DATABASE tshepo_offline;

-- Landela-Integrated Credential & Document Suite
CREATE DATABASE landela_adapter;
CREATE DATABASE credential_verification;
CREATE DATABASE share_slip;
CREATE DATABASE card_print;

-- Procurement / Supply Chain
CREATE DATABASE msika_flow;
CREATE DATABASE product_registry;
CREATE DATABASE inventory_elmis;
CREATE DATABASE pharmacy_elmis;

-- Clinical / FHIR
CREATE DATABASE butano;
CREATE DATABASE butano_fhir;
CREATE DATABASE fhir_gateway;
CREATE DATABASE pacs;

-- Auth / Identity
CREATE DATABASE keycloak;

-- Experience Platform
CREATE DATABASE experience_bff;
CREATE DATABASE guidance;
CREATE DATABASE clinical_knowledge;

-- Reporting & Analytics
CREATE DATABASE reporting;
CREATE DATABASE ndr;
CREATE DATABASE impilo_ndr;
CREATE DATABASE impilo_data_warehouse;
CREATE DATABASE pipeline;
CREATE DATABASE impilo_data_ingestion;
CREATE DATABASE impilo_data_governance;

-- Governance & Compliance
CREATE DATABASE dags;
CREATE DATABASE secharden;
CREATE DATABASE surv;
CREATE DATABASE camp;
CREATE DATABASE impilo_audit_ledger;

-- Integration & Operations
CREATE DATABASE impilo_forms;
CREATE DATABASE impilo_search;
CREATE DATABASE impilo_channels;
CREATE DATABASE impilo_connector_fhir;
CREATE DATABASE impilo_dispatch;
CREATE DATABASE impilo_rules;
CREATE DATABASE impilo_workflow;
CREATE DATABASE impilo_schema_registry;

-- IoT & Devices
CREATE DATABASE impilo_iot_ingestion;
CREATE DATABASE impilo_asset_registry;

-- Edge & Offline
CREATE DATABASE impilo_offline_edge;

-- Coverage & Finance
CREATE DATABASE impilo_coverage;
CREATE DATABASE impilo_indawo;
CREATE DATABASE costa_db;
CREATE DATABASE mushex_db;

-- Developer & Support
CREATE DATABASE impilo_dev_portal;
CREATE DATABASE impilo_support;

-- Observability
CREATE DATABASE observability;
CREATE DATABASE impilo_identity_assurance;

-- =============================================================================
-- PostgreSQL Extensions (must be enabled per-database before Flyway runs)
-- =============================================================================

\c tuso
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gin;

\c impilo_search
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gin;
