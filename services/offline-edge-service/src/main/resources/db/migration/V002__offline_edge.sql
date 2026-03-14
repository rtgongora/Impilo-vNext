-- offline-edge-service: offline event capture store + reconciliation queue
-- Table prefix: ofe_
-- Implements "capture vitals offline" (Class C) workflow

-- Signed entitlement tokens issued for offline operation
CREATE TABLE ofe_entitlements (
    entitlement_id  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    actor_id        VARCHAR(255)    NOT NULL,
    facility_ref    VARCHAR(255)    NOT NULL,
    workflow_type   VARCHAR(64)     NOT NULL DEFAULT 'CAPTURE_VITALS',
    scope_json      TEXT            NOT NULL,
    token_hash      VARCHAR(64)     NOT NULL,
    issued_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ     NOT NULL,
    revoked         BOOLEAN         NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_ofe_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_ofe_entitlements_actor ON ofe_entitlements (actor_id, expires_at);
CREATE INDEX idx_ofe_entitlements_tenant ON ofe_entitlements (tenant_id);

-- Offline captured actions (local store, append-only until replayed)
CREATE TABLE ofe_captured_actions (
    action_id       UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    entitlement_id  UUID            NOT NULL REFERENCES ofe_entitlements(entitlement_id),
    tenant_id       UUID            NOT NULL,
    actor_id        VARCHAR(255)    NOT NULL,
    patient_ref     VARCHAR(255)    NOT NULL,
    workflow_type   VARCHAR(64)     NOT NULL DEFAULT 'CAPTURE_VITALS',
    action_type     VARCHAR(64)     NOT NULL,
    payload_json    TEXT            NOT NULL,
    captured_at     TIMESTAMPTZ     NOT NULL,
    device_id       VARCHAR(255),
    sequence_num    INT             NOT NULL DEFAULT 1,
    status          VARCHAR(32)     NOT NULL DEFAULT 'QUEUED',
    replayed_at     TIMESTAMPTZ,
    replay_error    TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_ofe_actions_entitlement ON ofe_captured_actions (entitlement_id);
CREATE INDEX idx_ofe_actions_status ON ofe_captured_actions (status, created_at);
CREATE INDEX idx_ofe_actions_patient ON ofe_captured_actions (patient_ref, captured_at);
CREATE INDEX idx_ofe_actions_tenant ON ofe_captured_actions (tenant_id, status);

-- Reconciliation batches tracking
CREATE TABLE ofe_reconciliation_batches (
    batch_id        UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    entitlement_id  UUID            NOT NULL REFERENCES ofe_entitlements(entitlement_id),
    total_actions   INT             NOT NULL DEFAULT 0,
    replayed_count  INT             NOT NULL DEFAULT 0,
    failed_count    INT             NOT NULL DEFAULT 0,
    status          VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_ofe_batches_status ON ofe_reconciliation_batches (status, created_at);
