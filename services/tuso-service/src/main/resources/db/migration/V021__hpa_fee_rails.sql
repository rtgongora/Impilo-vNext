-- ============================================================================
-- V021: HPA regulatory fee rails — the GOVERNED fee schedule (structure only).
--
-- Fee AMOUNTS are defined by Statutory Instrument 78 of 2017 and its successors,
-- NOT by the HPA manual and NEVER by this platform. This migration records the
-- fee SCHEDULE STRUCTURE (which application types attract which fee codes, per
-- facility class) with amounts left NULL and status PENDING_REGULATOR_APPROVAL.
-- Amounts arrive later through governed configuration (a new version row +
-- approve), or a PO-authorised migration once the SI values are provided —
-- amounts are never invented here.
--
-- The fee gate enforces ONLY when an ACTIVE, amount-configured schedule row
-- exists for the application; otherwise the application proceeds and its fee
-- state is recorded NOT_CONFIGURED (surfaced, never silently skipped).
-- ============================================================================

CREATE TABLE IF NOT EXISTS tuso.regulatory_fee_schedule (
    fee_id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_code             VARCHAR(64)  NOT NULL,         -- 'SI_78_2017'
    version                   INTEGER      NOT NULL DEFAULT 1,
    fee_code                  VARCHAR(64)  NOT NULL,         -- APPLICATION_INITIAL_REGISTRATION | APPLICATION_RENEWAL | ...
    applies_to_catalogue_code VARCHAR(64)  NOT NULL REFERENCES tuso.regulatory_application_type(code),
    applies_to_class_code     VARCHAR(16),                   -- A | B | C ; NULL = class-independent
    amount                    NUMERIC(14,2),                 -- NULL until governed configuration supplies the SI value
    currency                  VARCHAR(3),
    source_instrument         VARCHAR(128) NOT NULL,         -- 'SI 78 of 2017' / successor
    source_section            VARCHAR(64),
    status                    VARCHAR(64)  NOT NULL DEFAULT 'PENDING_REGULATOR_APPROVAL',
                                                             -- PENDING_REGULATOR_APPROVAL | ACTIVE | RETIRED
    effective_from            DATE,
    effective_to              DATE,
    approved_by               VARCHAR(255),
    approved_at               TIMESTAMPTZ,
    notes                     TEXT,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_fee_schedule UNIQUE (schedule_code, fee_code, applies_to_catalogue_code, applies_to_class_code, version)
);
CREATE INDEX IF NOT EXISTS idx_fee_schedule_lookup
    ON tuso.regulatory_fee_schedule (applies_to_catalogue_code, status);

-- Fee waiver provenance on the application (fee_state / fee_reference /
-- payment_reference already exist from V006/V018).
ALTER TABLE tuso.facility_application
    ADD COLUMN IF NOT EXISTS fee_schedule_ref  UUID,
    ADD COLUMN IF NOT EXISTS fee_waived_by     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS fee_waived_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS fee_waiver_reason TEXT;

-- Seed ONE structure row per fee-required application type — amount NULL,
-- PENDING_REGULATOR_APPROVAL. Class-independent (applies_to_class_code NULL);
-- class-specific rows can be added later through governed configuration.
INSERT INTO tuso.regulatory_fee_schedule
    (schedule_code, version, fee_code, applies_to_catalogue_code, applies_to_class_code,
     amount, currency, source_instrument, source_section, status, notes)
SELECT
    'SI_78_2017', 1,
    'FEE_' || t.code,
    t.code,
    NULL,
    NULL,               -- amount: NEVER invented; configured from SI 78 of 2017
    NULL,
    'SI 78 of 2017',
    NULL,
    'PENDING_REGULATOR_APPROVAL',
    'Structure only; the fee amount is defined by Statutory Instrument 78 of 2017 and is configured '
        || 'through governed approval, never hard-coded.'
FROM tuso.regulatory_application_type t
WHERE t.fee_required = true
ON CONFLICT ON CONSTRAINT uq_fee_schedule DO NOTHING;
