CREATE SCHEMA IF NOT EXISTS ai_registry;

CREATE TABLE ai_registry.models (
    id BIGSERIAL PRIMARY KEY,
    model_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    model_type VARCHAR(32) NOT NULL,
    owner VARCHAR(128),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    approved_by VARCHAR(128),
    approved_at TIMESTAMPTZ,
    withdrawn_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE ai_registry.model_versions (
    id BIGSERIAL PRIMARY KEY,
    version_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    model_id UUID NOT NULL REFERENCES ai_registry.models(model_id),
    version VARCHAR(32) NOT NULL,
    artifact_ref VARCHAR(512),
    config_json JSONB DEFAULT '{}',
    approved_use_cases JSONB DEFAULT '[]',
    prohibited_use_cases JSONB DEFAULT '[]',
    drift_threshold DECIMAL(5,3),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE ai_registry.inference_records (
    id BIGSERIAL PRIMARY KEY,
    record_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id UUID NOT NULL,
    model_id UUID NOT NULL,
    model_version VARCHAR(32),
    inference_class VARCHAR(4) NOT NULL,
    workflow_ref VARCHAR(128),
    subject_type VARCHAR(32),
    subject_id VARCHAR(128),
    output_summary TEXT,
    score DECIMAL(5,4),
    confidence DECIMAL(5,4),
    human_review_required BOOLEAN DEFAULT TRUE,
    human_accepted BOOLEAN,
    override_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE ai_registry.drift_events (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id UUID NOT NULL,
    model_id UUID NOT NULL,
    model_version VARCHAR(32),
    drift_metric VARCHAR(64),
    drift_value DECIMAL(10,6),
    threshold DECIMAL(10,6),
    severity VARCHAR(16),
    detected_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE ai_registry.event_outbox (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    schema_version INT NOT NULL DEFAULT 1,
    correlation_id UUID,
    causation_id UUID,
    idempotency_key VARCHAR(255) NOT NULL,
    producer VARCHAR(64) NOT NULL DEFAULT 'ai-model-registry-service',
    tenant_id UUID NOT NULL,
    pod_id VARCHAR(64) NOT NULL DEFAULT 'national-spine',
    subject_id VARCHAR(255) NOT NULL,
    subject_type VARCHAR(64) NOT NULL,
    partition_key VARCHAR(255),
    occurred_at TIMESTAMPTZ NOT NULL,
    payload_json JSONB NOT NULL,
    publish_error TEXT,
    retry_count INT DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ,
    CONSTRAINT uq_ai_outbox_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_ai_outbox_unpublished ON ai_registry.event_outbox (created_at) WHERE published_at IS NULL;
