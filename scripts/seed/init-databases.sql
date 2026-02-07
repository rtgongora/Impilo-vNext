-- =============================================================================
-- Impilo vNext — Per-service database initialization
-- Executed on first PostgreSQL container startup.
-- Each microservice gets its own database (no shared schemas).
-- =============================================================================

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
CREATE DATABASE tshepo_audit;
CREATE DATABASE tshepo_keys;
CREATE DATABASE tshepo_offline;
-- Landela-Integrated Credential & Document Suite
CREATE DATABASE landela_adapter;
CREATE DATABASE credential_verification;
CREATE DATABASE share_slip;
CREATE DATABASE card_print;
CREATE DATABASE keycloak;
CREATE DATABASE butano;
