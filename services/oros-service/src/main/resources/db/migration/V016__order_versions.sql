-- OF-B1: immutable order-version aggregate (Vol II §8.2/§9.1).
-- Amendment = new version, never in-place mutation. Mirrors the oros_results
-- versioning idiom (V006): version counter + supersedes chain, head = max version.
-- UNIQUE(order_id, version) is the concurrent-amend race guard: two racing
-- amendments compete for the same next version number and exactly one wins.

CREATE TABLE oros_order_versions (
    order_version_id  VARCHAR(26) PRIMARY KEY,      -- ULID, app-assigned (§5 OrderVersionId)
    order_id          VARCHAR(26) NOT NULL REFERENCES oros_orders(order_id),
    tenant_id         UUID        NOT NULL,
    version           INT         NOT NULL,
    supersedes_version_id VARCHAR(26),
    content_json      JSONB       NOT NULL,          -- canonical order+items snapshot at this version
    content_hash      VARCHAR(64) NOT NULL,          -- SHA-256 of the canonical serialisation (signing binds here, OF-B2)
    reason            VARCHAR(512),
    created_by        VARCHAR(128),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_oros_order_versions UNIQUE (order_id, version)
);

CREATE INDEX idx_oros_order_versions_order ON oros_order_versions(order_id);
CREATE INDEX idx_oros_order_versions_tenant ON oros_order_versions(tenant_id, order_id);

-- ON_HOLD support (§9.1 T4/T5): remember the state to resume to, and the coded
-- reason for the last lifecycle action (full history lives in the outbox events
-- and version rows — never appended into clinical_notes).
ALTER TABLE oros_orders ADD COLUMN hold_prior_status VARCHAR(30);
ALTER TABLE oros_orders ADD COLUMN lifecycle_reason  VARCHAR(512);
