CREATE SCHEMA IF NOT EXISTS document_store;

CREATE TABLE document_store.objects (
    id BIGSERIAL PRIMARY KEY,
    object_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    bucket VARCHAR(255) NOT NULL,
    object_key VARCHAR(1000) NOT NULL UNIQUE,
    original_filename VARCHAR(500),
    mime_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    hash_sha256 VARCHAR(64) NOT NULL,
    scan_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING|CLEAN|INFECTED|SKIPPED
    scan_result VARCHAR(500),
    metadata JSONB DEFAULT '{}',
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_objects_tenant ON document_store.objects(tenant_id);
CREATE INDEX idx_objects_hash ON document_store.objects(hash_sha256);
CREATE INDEX idx_objects_scan ON document_store.objects(scan_status);

CREATE TABLE document_store.signed_urls (
    id BIGSERIAL PRIMARY KEY,
    object_id UUID NOT NULL,
    url_token VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    accessed_at TIMESTAMPTZ
);

CREATE INDEX idx_signed_urls_token ON document_store.signed_urls(url_token);
CREATE INDEX idx_signed_urls_expires ON document_store.signed_urls(expires_at);

CREATE TABLE document_store.event_outbox (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
