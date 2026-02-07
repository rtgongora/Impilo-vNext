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
CREATE DATABASE product_registry;
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
CREATE DATABASE keycloak;
CREATE DATABASE butano;
