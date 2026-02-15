-- V002: Upgrade integration-hub to v2 — transforms, dispatch attempts, dead-letter queue

-- Extend route definitions with match criteria, transform rules, and target timeout
ALTER TABLE ih_route_definitions
    ADD COLUMN match_method             VARCHAR(16),
    ADD COLUMN match_path_regex         VARCHAR(512),
    ADD COLUMN transform_headers_json   TEXT,
    ADD COLUMN transform_field_renames_json TEXT,
    ADD COLUMN target_timeout_ms        INT;

-- Dispatch attempts — records every dispatch command processed
CREATE TABLE ih_dispatch_attempts (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    route_id        VARCHAR(36),
    method          VARCHAR(16)     NOT NULL,
    path            VARCHAR(512)    NOT NULL,
    request_body    TEXT,
    matched         BOOLEAN         NOT NULL DEFAULT FALSE,
    status          VARCHAR(32)     NOT NULL,
    transformed_body TEXT,
    error_message   TEXT,
    tenant_id       VARCHAR(64)     NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL,
    correlation_id  VARCHAR(64)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX idx_ih_dispatch_attempts_tenant ON ih_dispatch_attempts (tenant_id);
CREATE INDEX idx_ih_dispatch_attempts_route  ON ih_dispatch_attempts (route_id);
CREATE INDEX idx_ih_dispatch_attempts_status ON ih_dispatch_attempts (status);

-- Dead-letter queue — captures failed dispatch attempts for later inspection/retry
CREATE TABLE ih_dead_letter_queue (
    id                  VARCHAR(36)     NOT NULL PRIMARY KEY,
    dispatch_attempt_id VARCHAR(36)     NOT NULL,
    route_id            VARCHAR(36),
    method              VARCHAR(16)     NOT NULL,
    path                VARCHAR(512)    NOT NULL,
    request_body        TEXT,
    error_reason        TEXT            NOT NULL,
    retry_count         INT             NOT NULL DEFAULT 0,
    last_retry_at       TIMESTAMPTZ,
    resolved            BOOLEAN         NOT NULL DEFAULT FALSE,
    tenant_id           VARCHAR(64)     NOT NULL,
    pod_id              VARCHAR(64)     NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX idx_ih_dead_letter_tenant     ON ih_dead_letter_queue (tenant_id);
CREATE INDEX idx_ih_dead_letter_unresolved ON ih_dead_letter_queue (created_at) WHERE resolved = FALSE;
