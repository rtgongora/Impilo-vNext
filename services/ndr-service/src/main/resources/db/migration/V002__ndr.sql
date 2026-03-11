-- ============================================================================
-- ndr-service V002 — NDR domain tables
-- Bronze event store (local copy) and gold encounter materialized view.
-- ============================================================================

-- Bronze event store — NDR's own copy of ingested events
CREATE TABLE ndr_bronze_event (
    receipt_id          UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    pod_id              VARCHAR(64)     NOT NULL,
    request_id          VARCHAR(255)    NOT NULL,
    correlation_id      VARCHAR(255)    NOT NULL,
    idempotency_key     VARCHAR(255)    NOT NULL,
    event_id            VARCHAR(255)    NOT NULL,
    event_type          VARCHAR(128)    NOT NULL,
    schema_version      INT             NOT NULL,
    occurred_at         TIMESTAMPTZ     NOT NULL,
    emitted_at          TIMESTAMPTZ,
    subject_type        VARCHAR(64)     NOT NULL,
    subject_id          VARCHAR(255)    NOT NULL,
    partition_key       VARCHAR(255)    NOT NULL,
    envelope_json       TEXT            NOT NULL,
    stored_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_ndr_bronze_event_dedup
        UNIQUE (tenant_id, pod_id, event_id),

    CONSTRAINT uq_ndr_bronze_idempotency
        UNIQUE (tenant_id, pod_id, idempotency_key)
);

CREATE INDEX idx_ndr_bronze_event_type ON ndr_bronze_event (event_type);
CREATE INDEX idx_ndr_bronze_tenant ON ndr_bronze_event (tenant_id);
CREATE INDEX idx_ndr_bronze_stored_at ON ndr_bronze_event (stored_at);
CREATE INDEX idx_ndr_bronze_subject ON ndr_bronze_event (subject_type, subject_id);

-- Gold encounter — materialized from bronze encounter lifecycle events
CREATE TABLE ndr_gold_encounter (
    encounter_id        UUID            NOT NULL PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    patient_ref         VARCHAR(255)    NOT NULL,
    facility_ref        VARCHAR(255),
    started_at          TIMESTAMPTZ,
    status              VARCHAR(64)     NOT NULL DEFAULT 'UNKNOWN',
    source_event_id     VARCHAR(255)    NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ndr_gold_encounter_patient ON ndr_gold_encounter (patient_ref);
CREATE INDEX idx_ndr_gold_encounter_tenant ON ndr_gold_encounter (tenant_id);
CREATE INDEX idx_ndr_gold_encounter_facility ON ndr_gold_encounter (facility_ref);
