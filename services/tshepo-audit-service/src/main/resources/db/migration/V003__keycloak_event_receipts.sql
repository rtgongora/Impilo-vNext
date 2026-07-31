CREATE TABLE IF NOT EXISTS tshepo_audit.keycloak_event_receipt (
    event_key VARCHAR(320) PRIMARY KEY,
    source VARCHAR(24) NOT NULL,
    keycloak_event_time TIMESTAMPTZ,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_keycloak_receipt_time ON tshepo_audit.keycloak_event_receipt (ingested_at DESC);
