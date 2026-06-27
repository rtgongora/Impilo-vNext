-- V002 (Wave C C2a): identity assurance level + upgrade-request workflow.
-- identity-assurance-service is the canonical owner of identity assurance (see
-- docs/registry/system-of-record-map.md); other services must-not-own-identity-assurance-policy.

CREATE TABLE ia.assurance_record (
    id           BIGSERIAL    PRIMARY KEY,
    tenant_id    UUID         NOT NULL,
    actor_id     VARCHAR(255) NOT NULL,
    current_level VARCHAR(8)  NOT NULL DEFAULT 'LOA1',
    assessed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_assurance_record_actor UNIQUE (tenant_id, actor_id)
);
CREATE INDEX idx_assurance_record_tenant ON ia.assurance_record (tenant_id);

CREATE TABLE ia.assurance_upgrade_request (
    id            BIGSERIAL    PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    actor_id      VARCHAR(255) NOT NULL,
    current_level VARCHAR(8)   NOT NULL,
    target_level  VARCHAR(8)   NOT NULL,
    method        VARCHAR(64)  NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    reason        VARCHAR(512),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ  NOT NULL,
    decided_at    TIMESTAMPTZ,
    decided_by    VARCHAR(255)
);
CREATE INDEX idx_assurance_upgrade_tenant_status ON ia.assurance_upgrade_request (tenant_id, status);
CREATE INDEX idx_assurance_upgrade_actor ON ia.assurance_upgrade_request (tenant_id, actor_id);
