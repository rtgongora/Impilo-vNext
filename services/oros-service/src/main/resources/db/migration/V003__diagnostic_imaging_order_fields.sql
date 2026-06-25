-- =============================================
-- Service : oros-service
-- Flyway  : V003__diagnostic_imaging_order_fields.sql
-- Purpose : Diagnostic/imaging order journey foundation (OROS Wave 2).
--           Adds request source, accession-number lifecycle, referring-provider
--           identity (distinct from placed_by), scheduled acquisition time, and an
--           imaging safety questionnaire — plus imaging detail on order items.
-- All columns nullable / defaulted so existing order placement is unaffected.
-- =============================================

-- ── oros_orders: diagnostic-journey fields ───────────────────────────────
ALTER TABLE oros_orders
    ADD COLUMN request_source        VARCHAR(20)  NOT NULL DEFAULT 'INTERNAL',
        -- INTERNAL, PAPER, QR, WALK_IN, EXTERNAL
    ADD COLUMN accession_number      VARCHAR(64),
        -- RIS-style accession; reserved at submit for imaging orders
    ADD COLUMN referring_provider_id   VARCHAR(64),
        -- VARAPI provider public id of the requesting clinician (distinct from placed_by actor)
    ADD COLUMN referring_provider_name VARCHAR(255),
    ADD COLUMN scheduled_at          TIMESTAMPTZ,
        -- planned acquisition / appointment time
    ADD COLUMN safety_json           JSONB;
        -- imaging safety questionnaire: pregnancy, renal/creatinine, implants/devices,
        -- allergies, infectious risk, mobility/transport — captured at order time

-- Accession numbers are unique within a tenant (partial index ignores NULLs).
CREATE UNIQUE INDEX uq_oros_orders_accession
    ON oros_orders (tenant_id, accession_number)
    WHERE accession_number IS NOT NULL;

CREATE INDEX idx_oros_orders_request_source ON oros_orders (tenant_id, request_source);

-- ── oros_order_items: imaging examination detail ─────────────────────────
ALTER TABLE oros_order_items
    ADD COLUMN modality       VARCHAR(32),
        -- XR, US, CT, MRI, MG, FL, DEXA, ECG, ECHO, ENDO, OTHER
    ADD COLUMN laterality     VARCHAR(16),
        -- LEFT, RIGHT, BILATERAL, NA
    ADD COLUMN contrast       VARCHAR(16),
        -- YES, NO, UNKNOWN
    ADD COLUMN procedure_code VARCHAR(64);

-- ── oros_accession_counter: per-tenant/facility/year running sequence ────
CREATE TABLE oros_accession_counter (
    tenant_id   UUID         NOT NULL,
    facility_id UUID         NOT NULL,
    seq_year    INT          NOT NULL,
    last_seq    BIGINT       NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, facility_id, seq_year)
);
