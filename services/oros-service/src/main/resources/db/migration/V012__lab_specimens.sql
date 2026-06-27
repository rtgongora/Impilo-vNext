-- =============================================
-- Service : oros-service
-- Flyway  : V012__lab_specimens.sql
-- Purpose : First-class laboratory specimen tracking (spec §7). A lab order can have one or more
--           specimens, each with its own collection → dispatch → receipt → (rejection/recollection)
--           lifecycle and a chain-of-custody audit trail. Specimen transitions drive the order's
--           generalised LAB workflow_state (V011) via the LabWorkflow guard.
-- =============================================

CREATE TABLE oros_specimens (
    specimen_id       UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id          VARCHAR(26)     NOT NULL REFERENCES oros_orders(order_id),
    tenant_id         UUID            NOT NULL,
    facility_id       UUID,
    specimen_type     VARCHAR(100),               -- blood, urine, CSF, sputum, swab, tissue, ...
    specimen_source   VARCHAR(100),               -- venous, arterial, catheter, ...
    body_site         VARCHAR(100),
    sample_id         VARCHAR(64),                -- label / barcode applied at collection
    lab_number        VARCHAR(64),                -- accession / lab number at the receiving lab
    status            VARCHAR(24)     NOT NULL DEFAULT 'COLLECTED',
                                                  -- COLLECTED, LABELLED, DISPATCHED, IN_TRANSIT,
                                                  -- RECEIVED, REJECTED, LOST, RECOLLECTED
    fasting_status    VARCHAR(16),                -- FASTING, NON_FASTING, UNKNOWN
    collection_instructions TEXT,
    collected_by      VARCHAR(128),
    collected_at      TIMESTAMPTZ,
    collection_site   VARCHAR(255),
    dispatched_by     VARCHAR(128),
    dispatched_at     TIMESTAMPTZ,
    received_by       VARCHAR(128),
    received_at       TIMESTAMPTZ,
    rejection_reason  TEXT,
    chain_of_custody  JSONB,                      -- append-only array of custody events
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ
);

CREATE INDEX idx_oros_specimens_order ON oros_specimens (order_id);
CREATE INDEX idx_oros_specimens_tenant_status ON oros_specimens (tenant_id, status);
CREATE UNIQUE INDEX uq_oros_specimens_sample
    ON oros_specimens (tenant_id, sample_id) WHERE sample_id IS NOT NULL;
