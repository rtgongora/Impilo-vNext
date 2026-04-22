ALTER TABLE vito.client
    ADD COLUMN IF NOT EXISTS middle_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS verification_status VARCHAR(64) NOT NULL DEFAULT 'UNVERIFIED',
    ADD COLUMN IF NOT EXISTS identity_assurance_level INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS active_flag BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS deceased_flag BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS golden_record_flag BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS merged_into_crid UUID,
    ADD COLUMN IF NOT EXISTS lifecycle_metadata JSONB DEFAULT '{}'::jsonb;

CREATE TABLE IF NOT EXISTS vito.client_registration (
    registration_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    client_health_id UUID,
    registration_type VARCHAR(64) NOT NULL,
    initiated_by VARCHAR(255) NOT NULL,
    initiated_channel VARCHAR(64) NOT NULL,
    linked_facility_id BIGINT,
    linked_provider_id VARCHAR(255),
    linked_service_context_id VARCHAR(255),
    submission_date TIMESTAMPTZ,
    workflow_state VARCHAR(64) NOT NULL,
    completion_state VARCHAR(64) NOT NULL,
    verification_state VARCHAR(64) NOT NULL,
    notes TEXT,
    provenance JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_client_registration_client
    ON vito.client_registration (tenant_id, client_health_id);
CREATE INDEX IF NOT EXISTS idx_client_registration_workflow
    ON vito.client_registration (tenant_id, workflow_state);

CREATE TABLE IF NOT EXISTS vito.client_identifier (
    identifier_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    client_health_id UUID NOT NULL,
    identifier_type VARCHAR(64) NOT NULL,
    identifier_value VARCHAR(255) NOT NULL,
    status VARCHAR(64) NOT NULL,
    primary_flag BOOLEAN NOT NULL DEFAULT FALSE,
    issue_date TIMESTAMPTZ,
    expiry_date TIMESTAMPTZ,
    source VARCHAR(255),
    active_flag BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_client_identifier_client
    ON vito.client_identifier (tenant_id, client_health_id);
CREATE INDEX IF NOT EXISTS idx_client_identifier_type
    ON vito.client_identifier (tenant_id, identifier_type);

CREATE TABLE IF NOT EXISTS vito.client_alias (
    alias_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    client_health_id UUID NOT NULL,
    alias_type VARCHAR(64) NOT NULL,
    alias_value VARCHAR(255) NOT NULL,
    source VARCHAR(255),
    confidence NUMERIC(5, 2),
    active_flag BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_client_alias_client
    ON vito.client_alias (tenant_id, client_health_id);

CREATE TABLE IF NOT EXISTS vito.client_identity_evidence (
    evidence_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    client_health_id UUID NOT NULL,
    registration_id UUID,
    evidence_type VARCHAR(64) NOT NULL,
    evidence_reference TEXT NOT NULL,
    verification_state VARCHAR(64) NOT NULL,
    verified_by VARCHAR(255),
    verified_at TIMESTAMPTZ,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_client_identity_evidence_client
    ON vito.client_identity_evidence (tenant_id, client_health_id);

CREATE TABLE IF NOT EXISTS vito.client_verification_review (
    review_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    client_health_id UUID NOT NULL,
    registration_id UUID,
    review_type VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    reviewer VARCHAR(255),
    decision VARCHAR(64),
    notes TEXT,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_client_verification_review_client
    ON vito.client_verification_review (tenant_id, client_health_id);

CREATE TABLE IF NOT EXISTS vito.client_merge_case (
    merge_case_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    primary_health_id UUID NOT NULL,
    secondary_health_id UUID NOT NULL,
    initiated_by VARCHAR(255) NOT NULL,
    reviewed_by VARCHAR(255),
    decision VARCHAR(64),
    survivorship_summary TEXT,
    status VARCHAR(64) NOT NULL,
    linked_match_id BIGINT,
    linked_dedup_case_id BIGINT,
    merge_history_id BIGINT,
    executed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_client_merge_case_primary
    ON vito.client_merge_case (tenant_id, primary_health_id);
CREATE INDEX IF NOT EXISTS idx_client_merge_case_secondary
    ON vito.client_merge_case (tenant_id, secondary_health_id);

CREATE TABLE IF NOT EXISTS vito.client_relationship (
    relationship_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    client_health_id UUID NOT NULL,
    related_client_health_id UUID NOT NULL,
    relationship_type VARCHAR(64) NOT NULL,
    start_date DATE,
    end_date DATE,
    status VARCHAR(64) NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_client_relationship_client
    ON vito.client_relationship (tenant_id, client_health_id);

CREATE TABLE IF NOT EXISTS vito.client_authorization_link (
    authorization_link_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    client_health_id UUID NOT NULL,
    authorisation_type VARCHAR(64) NOT NULL,
    reference_id VARCHAR(255) NOT NULL,
    status VARCHAR(64) NOT NULL,
    start_date TIMESTAMPTZ,
    end_date TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_client_authorization_link_client
    ON vito.client_authorization_link (tenant_id, client_health_id);

CREATE TABLE IF NOT EXISTS vito.client_stewardship_action (
    action_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    client_health_id UUID NOT NULL,
    related_registration_id UUID,
    related_merge_case_id UUID,
    action_type VARCHAR(64) NOT NULL,
    owner VARCHAR(255),
    due_date TIMESTAMPTZ,
    status VARCHAR(64) NOT NULL,
    completion_notes TEXT,
    verified_by VARCHAR(255),
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_client_stewardship_action_client
    ON vito.client_stewardship_action (tenant_id, client_health_id);
CREATE INDEX IF NOT EXISTS idx_client_stewardship_action_status
    ON vito.client_stewardship_action (tenant_id, status);

CREATE TABLE IF NOT EXISTS vito.client_status_history (
    status_history_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    client_health_id UUID NOT NULL,
    previous_status VARCHAR(64),
    new_status VARCHAR(64) NOT NULL,
    reason TEXT,
    changed_by VARCHAR(255) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    provenance_context JSONB DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_client_status_history_client
    ON vito.client_status_history (tenant_id, client_health_id);

CREATE TABLE IF NOT EXISTS vito.client_audit_event (
    audit_event_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    client_health_id UUID,
    actor VARCHAR(255) NOT NULL,
    actor_type VARCHAR(128),
    action VARCHAR(128) NOT NULL,
    target_entity VARCHAR(128) NOT NULL,
    target_id VARCHAR(255),
    before_state JSONB,
    after_state JSONB,
    correlation_id VARCHAR(255),
    provenance_context JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_client_audit_event_client
    ON vito.client_audit_event (tenant_id, client_health_id, created_at DESC);
