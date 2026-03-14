-- V003: Template versioning lifecycle and delivery receipts

-- ===== Template lifecycle fields =====
ALTER TABLE ns_templates ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE ns_templates ADD COLUMN current_version INT NOT NULL DEFAULT 1;

-- ===== Template versions =====
CREATE TABLE ns_template_versions (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    template_id     VARCHAR(36)     NOT NULL REFERENCES ns_templates(id),
    version         INT             NOT NULL,
    content         TEXT            NOT NULL,
    subject         VARCHAR(512),
    changelog       VARCHAR(1024),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_ns_template_versions_tid_ver UNIQUE (template_id, version)
);

CREATE INDEX idx_ns_template_versions_tid ON ns_template_versions (template_id);

-- ===== Delivery receipts =====
CREATE TABLE ns_delivery_receipts (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    notification_id VARCHAR(36)     NOT NULL,
    channel         VARCHAR(32)     NOT NULL,
    recipient_addr  VARCHAR(256)    NOT NULL,
    status          VARCHAR(32)     NOT NULL,
    provider_ref    VARCHAR(256),
    delivered_at    TIMESTAMPTZ,
    failure_reason  TEXT,
    tenant_id       VARCHAR(64)     NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_ns_delivery_receipts_nid ON ns_delivery_receipts (notification_id);
CREATE INDEX idx_ns_delivery_receipts_tenant_status ON ns_delivery_receipts (tenant_id, status);
