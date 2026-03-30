-- V005: Federation Control — federation pod registry for multi-sovereign trust topology.
-- Tracks registered federation peers, their authority config, and obligations.

CREATE TABLE tshepo_authz.federation_pod (
    id              BIGSERIAL       PRIMARY KEY,
    pod_id          VARCHAR(128)    NOT NULL,
    pod_name        VARCHAR(255)    NOT NULL,
    pod_type        VARCHAR(32)     NOT NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
    authority       JSONB,
    obligations     JSONB,
    routing_config  JSONB,
    registered_at   TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_federation_pod_id
    ON tshepo_authz.federation_pod (pod_id);

CREATE INDEX idx_federation_pod_status
    ON tshepo_authz.federation_pod (status);

CREATE INDEX idx_federation_pod_type
    ON tshepo_authz.federation_pod (pod_type);
