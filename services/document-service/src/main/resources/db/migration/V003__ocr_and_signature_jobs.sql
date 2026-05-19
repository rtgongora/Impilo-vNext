CREATE TABLE document_store.ocr_jobs (
    id BIGSERIAL PRIMARY KEY,
    job_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    object_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    provider_type VARCHAR(80) NOT NULL,
    status VARCHAR(40) NOT NULL,
    extracted_text TEXT,
    confidence NUMERIC(5,4),
    error_message TEXT,
    requested_by VARCHAR(255) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ocr_jobs_object ON document_store.ocr_jobs(object_id);
CREATE INDEX idx_ocr_jobs_tenant ON document_store.ocr_jobs(tenant_id);

CREATE TABLE document_store.signature_jobs (
    id BIGSERIAL PRIMARY KEY,
    job_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    object_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    provider_type VARCHAR(80) NOT NULL,
    status VARCHAR(40) NOT NULL,
    signer_id VARCHAR(255),
    signature_ref VARCHAR(255),
    verification_url VARCHAR(1000),
    signed_at TIMESTAMPTZ,
    error_message TEXT,
    requested_by VARCHAR(255) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_signature_jobs_object ON document_store.signature_jobs(object_id);
CREATE INDEX idx_signature_jobs_tenant ON document_store.signature_jobs(tenant_id);
