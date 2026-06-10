-- Access requests (SoR), HSC employment records, organisation membership

CREATE TABLE wgv_access_request (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    request_type VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    requester_id VARCHAR(255) NOT NULL,
    requester_name VARCHAR(512),
    organisation_id UUID,
    target_subject_id VARCHAR(255),
    requested_role VARCHAR(128),
    requested_scope TEXT,
    requested_environment VARCHAR(64),
    requested_data_scope VARCHAR(128),
    risk_level VARCHAR(32),
    policy_precheck_result VARCHAR(64),
    approvals_required TEXT,
    payload TEXT,
    audit_trail TEXT,
    fallback_request_id VARCHAR(64),
    downstream_correlation_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wgv_ar_tenant ON wgv_access_request (tenant_id, status);
CREATE INDEX idx_wgv_ar_requester ON wgv_access_request (tenant_id, requester_id);

CREATE TABLE wgv_hsc_employment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    provider_worker_id VARCHAR(255) NOT NULL,
    linked_health_id VARCHAR(255),
    employer_organisation_id UUID,
    employment_status VARCHAR(64) NOT NULL,
    post_id VARCHAR(128),
    post_title VARCHAR(512),
    grade VARCHAR(64),
    cadre VARCHAR(128),
    establishment_unit VARCHAR(256),
    current_posting_province VARCHAR(128),
    current_posting_district VARCHAR(128),
    current_posting_facility VARCHAR(128),
    current_posting_department VARCHAR(128),
    appointment_date DATE,
    transfer_status VARCHAR(64),
    promotion_status VARCHAR(64),
    disciplinary_employment_status VARCHAR(64),
    verification_status VARCHAR(64),
    effective_date DATE,
    expiry_date DATE,
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wgv_hsc_provider ON wgv_hsc_employment (tenant_id, provider_worker_id);
CREATE INDEX idx_wgv_hsc_facility ON wgv_hsc_employment (tenant_id, current_posting_facility);

CREATE TABLE wgv_organisation_membership (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    organisation_id UUID NOT NULL REFERENCES wgv_organisation (id),
    subject_id VARCHAR(255) NOT NULL,
    subject_type VARCHAR(32) NOT NULL DEFAULT 'USER',
    role_template VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wgv_org_member UNIQUE (tenant_id, organisation_id, subject_id, subject_type)
);

CREATE INDEX idx_wgv_org_member_org ON wgv_organisation_membership (organisation_id);
