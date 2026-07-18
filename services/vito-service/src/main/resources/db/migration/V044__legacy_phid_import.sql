-- Identity Contract §5.2, §14 / E2: bulk legacy-PHID import provenance ledger.
-- One row per imported PHID with full provenance and the reconciliation decision.
-- Assigned PHIDs become LEGACY_PHID aliases; unassigned pool numbers go to
-- vito.phid_pool (never client aliases); same-PHID-two-people is QUARANTINED.
CREATE TABLE IF NOT EXISTS vito.legacy_phid_import (
    id                  BIGSERIAL     PRIMARY KEY,
    tenant_id           UUID          NOT NULL,
    batch_id            UUID          NOT NULL,
    phid                VARCHAR(16)   NOT NULL,
    source_system       VARCHAR(64)   NOT NULL,
    source_facility_id  VARCHAR(64),
    issuance_date       DATE,
    -- ASSIGNED_LINKED | ASSIGNED_NEW | RESERVED_POOL | QUARANTINED | INVALID_FORMAT
    disposition         VARCHAR(24)   NOT NULL,
    health_id           UUID,
    reason              TEXT,
    imported_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_legacy_phid_import_batch
    ON vito.legacy_phid_import (tenant_id, batch_id);
CREATE INDEX IF NOT EXISTS idx_legacy_phid_import_phid
    ON vito.legacy_phid_import (tenant_id, phid);

COMMENT ON TABLE vito.legacy_phid_import IS
    'Legacy PHID bulk-import provenance ledger (Identity Contract §14): per-PHID source, disposition, decision.';
