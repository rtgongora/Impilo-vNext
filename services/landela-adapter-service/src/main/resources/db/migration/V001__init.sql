CREATE SCHEMA IF NOT EXISTS landela_adapter;

CREATE TABLE landela_adapter.documents (
    id BIGSERIAL PRIMARY KEY,
    document_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    subject_type VARCHAR(50) NOT NULL,  -- CLIENT|PROVIDER|FACILITY|ENCOUNTER|REFERRAL|OTHER
    subject_id VARCHAR(255) NOT NULL,
    document_type_code VARCHAR(100),
    source VARCHAR(20) NOT NULL DEFAULT 'uploaded',  -- scanned|uploaded|generated
    issuer VARCHAR(255),
    confidentiality VARCHAR(30) NOT NULL DEFAULT 'NORMAL',
    hash_sha256 VARCHAR(64) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_name VARCHAR(500),
    file_size_bytes BIGINT,
    storage_provider VARCHAR(20) NOT NULL DEFAULT 'INTERNAL',
    storage_ref VARCHAR(500),  -- MinIO object key or Landela document ID
    retention_policy_id VARCHAR(100),
    tags JSONB DEFAULT '{}',
    lifecycle_state VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    superseded_by BIGINT REFERENCES landela_adapter.documents(id),
    version INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX idx_docs_tenant_subject ON landela_adapter.documents(tenant_id, subject_type, subject_id);
CREATE INDEX idx_docs_type_code ON landela_adapter.documents(document_type_code);
CREATE INDEX idx_docs_lifecycle ON landela_adapter.documents(lifecycle_state);
CREATE INDEX idx_docs_created_at ON landela_adapter.documents(created_at DESC);
CREATE INDEX idx_docs_hash ON landela_adapter.documents(hash_sha256);

CREATE TABLE landela_adapter.document_audit (
    id BIGSERIAL PRIMARY KEY,
    document_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    actor_type VARCHAR(50),
    purpose_of_use VARCHAR(100),
    device_fingerprint VARCHAR(255),
    correlation_id UUID,
    details JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_doc_audit_document ON landela_adapter.document_audit(document_id);
CREATE INDEX idx_doc_audit_actor ON landela_adapter.document_audit(actor_id);

CREATE TABLE landela_adapter.event_outbox (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
