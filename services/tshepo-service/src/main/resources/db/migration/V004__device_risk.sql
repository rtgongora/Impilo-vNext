-- TSHEPO — Device risk and reputation tracking

CREATE TABLE tshepo.device_profile (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    fingerprint     VARCHAR(128) NOT NULL,
    actor_id        VARCHAR(255),
    risk_level      VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN',  -- LOW, MEDIUM, HIGH, BLOCKED, UNKNOWN
    first_seen_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    metadata        JSONB,
    UNIQUE (tenant_id, fingerprint)
);

CREATE TABLE tshepo.event_outbox (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON tshepo.event_outbox (created_at) WHERE published_at IS NULL;
