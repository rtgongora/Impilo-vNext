-- FCV-W3 — a governed batch by which the Ministry reasserts recognition of facilities already on
-- its own master list.
--
-- The problem this exists for: the 1,774 Ministry facilities were loaded by seed migration V027,
-- not through the importer, so they carry NO source-legitimacy rows at all. The seed said so
-- explicitly — "per-facility legitimacy/classification is a separate governed enrichment". This is
-- that enrichment, and it is deliberately a batch with a named authority and an audit trail rather
-- than a SQL UPDATE, because "the Ministry recognises these facilities" is a statement somebody has
-- to make.
--
-- What it asserts and what it does NOT. It writes MINISTRY_OPERATIONAL recognition — the facility is
-- on the national master list — while leaving PLATFORM_OPERATIONAL denying. Every facility in the
-- batch therefore stays operational=false until a verifier decides on it individually (FCV-W1).
-- Recognition is not operation, and a bulk act must never become a bulk opening.

CREATE TABLE IF NOT EXISTS tuso.ministry_recognition_batch (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,

    -- who authorised the assertion, and on what basis
    asserted_by         VARCHAR(64) NOT NULL,
    asserted_by_role    VARCHAR(64) NOT NULL,
    jurisdiction_code   VARCHAR(48) NOT NULL,
    source_label        VARCHAR(128) NOT NULL,   -- the master list being reasserted
    reason              TEXT NOT NULL,

    -- what happened
    dry_run             BOOLEAN NOT NULL DEFAULT TRUE,
    scanned             INT NOT NULL DEFAULT 0,
    recognised          INT NOT NULL DEFAULT 0,
    skipped             INT NOT NULL DEFAULT 0,
    status              VARCHAR(24) NOT NULL DEFAULT 'COMPLETED',

    -- reversal: a batch that cannot be undone should not be run
    reverted_at         TIMESTAMPTZ,
    reverted_by         VARCHAR(64),
    revert_reason       TEXT,

    correlation_id      VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_ministry_recognition_status CHECK (status IN ('COMPLETED', 'REVERTED'))
);

-- Which facilities a batch touched — the unit of reversal, and the audit answer to
-- "why does this facility carry Ministry recognition?"
CREATE TABLE IF NOT EXISTS tuso.ministry_recognition_batch_item (
    id              BIGSERIAL PRIMARY KEY,
    batch_id        UUID NOT NULL REFERENCES tuso.ministry_recognition_batch (id),
    facility_uuid   UUID NOT NULL,
    facility_code   VARCHAR(64),
    outcome         VARCHAR(32) NOT NULL,   -- RECOGNISED | SKIPPED_ALREADY_HAS_VERDICT | SKIPPED_DECISION_BOUND
    note            VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ministry_recognition_item UNIQUE (batch_id, facility_uuid)
);

CREATE INDEX IF NOT EXISTS idx_ministry_recognition_item_facility
    ON tuso.ministry_recognition_batch_item (facility_uuid);

COMMENT ON TABLE tuso.ministry_recognition_batch IS
    'Governed Ministry reassertion of recognition for facilities on the national master list '
    '(FCV-W3). Asserts MINISTRY_OPERATIONAL only — never platform operation, which remains a '
    'per-facility verifier decision.';
