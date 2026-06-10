-- Bootstrap state, authorised representatives, bulk import batches

CREATE TABLE wgv_bootstrap_state (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    bootstrap_open BOOLEAN NOT NULL DEFAULT TRUE,
    bootstrap_closed_at TIMESTAMPTZ,
    recovery_mode BOOLEAN NOT NULL DEFAULT FALSE,
    recovery_opened_at TIMESTAMPTZ,
    recovery_closed_at TIMESTAMPTZ,
    active_national_admin_user_id VARCHAR(255),
    bootstrap_account_id UUID,
    bootstrap_method VARCHAR(64),
    bootstrap_role_expires_at TIMESTAMPTZ,
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wgv_bootstrap_tenant UNIQUE (tenant_id)
);

CREATE TABLE wgv_authorised_representative (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    organisation_id UUID NOT NULL REFERENCES wgv_organisation (id),
    user_id VARCHAR(255) NOT NULL,
    representative_type VARCHAR(128) NOT NULL,
    role_template_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    effective_date DATE,
    expiry_date DATE,
    approved_by_user_id VARCHAR(255),
    data_responsibility_accepted_at TIMESTAMPTZ,
    audit_metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wgv_auth_rep UNIQUE (tenant_id, organisation_id, user_id, representative_type)
);

CREATE INDEX idx_wgv_auth_rep_org ON wgv_authorised_representative (organisation_id, status);

CREATE TABLE wgv_import_batch (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    organisation_id UUID NOT NULL REFERENCES wgv_organisation (id),
    import_type VARCHAR(128) NOT NULL,
    status VARCHAR(64) NOT NULL,
    uploaded_by_user_id VARCHAR(255) NOT NULL,
    source_file_name VARCHAR(512),
    file_hash VARCHAR(128),
    row_count INT NOT NULL DEFAULT 0,
    valid_row_count INT NOT NULL DEFAULT 0,
    exception_count INT NOT NULL DEFAULT 0,
    duplicate_count INT NOT NULL DEFAULT 0,
    ready_to_invite_count INT NOT NULL DEFAULT 0,
    created_access_request_count INT NOT NULL DEFAULT 0,
    source_of_record_status VARCHAR(64) NOT NULL DEFAULT 'workforce_governance',
    audit_status VARCHAR(64),
    expires_at TIMESTAMPTZ,
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wgv_import_batch_org ON wgv_import_batch (organisation_id, import_type, status);

CREATE TABLE wgv_import_row (
    id UUID PRIMARY KEY,
    import_batch_id UUID NOT NULL REFERENCES wgv_import_batch (id) ON DELETE CASCADE,
    row_number INT NOT NULL,
    raw_payload_json TEXT,
    normalized_payload_json TEXT,
    validation_status VARCHAR(64),
    match_status VARCHAR(64),
    precheck_status VARCHAR(64),
    outcome VARCHAR(128),
    subject_health_id VARCHAR(255),
    subject_provider_worker_id VARCHAR(255),
    subject_organisation_user_id VARCHAR(255),
    access_request_id VARCHAR(128),
    invitation_id VARCHAR(128),
    exception_summary TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wgv_import_row_batch ON wgv_import_row (import_batch_id, outcome);

CREATE TABLE wgv_import_exception (
    id UUID PRIMARY KEY,
    import_batch_id UUID NOT NULL REFERENCES wgv_import_batch (id) ON DELETE CASCADE,
    row_id UUID REFERENCES wgv_import_row (id) ON DELETE SET NULL,
    exception_type VARCHAR(128) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    message TEXT NOT NULL,
    resolution_status VARCHAR(64) NOT NULL DEFAULT 'open',
    resolved_by_user_id VARCHAR(255),
    resolved_at TIMESTAMPTZ,
    audit_metadata_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wgv_import_exception_batch ON wgv_import_exception (import_batch_id, resolution_status);

CREATE TABLE wgv_import_template (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    import_type VARCHAR(128) NOT NULL,
    template_version VARCHAR(32) NOT NULL,
    required_columns TEXT NOT NULL,
    optional_columns TEXT,
    schema_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wgv_import_template UNIQUE (tenant_id, import_type, template_version)
);

CREATE TABLE wgv_bootstrap_account (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    bootstrap_method VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    sovereign_organisation_id UUID,
    nominated_email VARCHAR(512) NOT NULL,
    nominated_full_name VARCHAR(512),
    nominated_phone VARCHAR(64),
    nominated_title VARCHAR(256),
    approval_reference VARCHAR(256),
    keycloak_user_id VARCHAR(128),
    invitation_id VARCHAR(128),
    role_expires_at TIMESTAMPTZ,
    activated_at TIMESTAMPTZ,
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wgv_bootstrap_account_status ON wgv_bootstrap_account (tenant_id, status);
