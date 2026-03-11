CREATE TABLE ndr_query_audit (
    audit_id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id UUID NOT NULL,
    principal_id VARCHAR(255) NOT NULL,
    dataset VARCHAR(255) NOT NULL,
    purpose_of_use VARCHAR(64) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    reason_codes_json TEXT,
    policy_version VARCHAR(64),
    query_type VARCHAR(64),
    correlation_id VARCHAR(255),
    decided_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ndr_query_audit_tenant ON ndr_query_audit (tenant_id);
CREATE INDEX idx_ndr_query_audit_principal ON ndr_query_audit (principal_id);
CREATE INDEX idx_ndr_query_audit_decided ON ndr_query_audit (decided_at);
