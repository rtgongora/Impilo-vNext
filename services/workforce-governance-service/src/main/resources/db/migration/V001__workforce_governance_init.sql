-- Workforce governance core tables (PostgreSQL)

CREATE TABLE wgv_organisation (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    organisation_code VARCHAR(64) NOT NULL,
    name VARCHAR(512) NOT NULL,
    legal_name VARCHAR(512),
    organisation_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    parent_organisation_id UUID,
    active_flag BOOLEAN NOT NULL DEFAULT TRUE,
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wgv_org_code UNIQUE (tenant_id, organisation_code)
);

CREATE INDEX idx_wgv_org_tenant ON wgv_organisation (tenant_id);
CREATE INDEX idx_wgv_org_parent ON wgv_organisation (parent_organisation_id);

CREATE TABLE wgv_organisation_unit (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    organisation_id UUID NOT NULL REFERENCES wgv_organisation (id),
    parent_unit_id UUID REFERENCES wgv_organisation_unit (id),
    unit_code VARCHAR(64) NOT NULL,
    name VARCHAR(512) NOT NULL,
    unit_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    active_flag BOOLEAN NOT NULL DEFAULT TRUE,
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wgv_org_unit_code UNIQUE (tenant_id, organisation_id, unit_code)
);

CREATE INDEX idx_wgv_org_unit_org ON wgv_organisation_unit (organisation_id);

CREATE TABLE wgv_jurisdiction (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    jurisdiction_code VARCHAR(64) NOT NULL,
    name VARCHAR(512) NOT NULL,
    jurisdiction_type VARCHAR(64) NOT NULL,
    parent_jurisdiction_id UUID REFERENCES wgv_jurisdiction (id),
    status VARCHAR(32) NOT NULL,
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wgv_jurisdiction_code UNIQUE (tenant_id, jurisdiction_code)
);

CREATE TABLE wgv_jurisdiction_link (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    jurisdiction_id UUID NOT NULL REFERENCES wgv_jurisdiction (id),
    relationship_type VARCHAR(64) NOT NULL,
    primary_flag BOOLEAN NOT NULL DEFAULT FALSE,
    start_date DATE,
    end_date DATE,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wgv_jlink_target ON wgv_jurisdiction_link (tenant_id, target_type, target_id);

CREATE TABLE wgv_role_definition (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    name VARCHAR(512) NOT NULL,
    description TEXT,
    role_category VARCHAR(64) NOT NULL,
    role_level VARCHAR(64) NOT NULL,
    allowed_target_types TEXT NOT NULL,
    requires_provider_flag BOOLEAN NOT NULL DEFAULT FALSE,
    requires_professional_standing_flag BOOLEAN NOT NULL DEFAULT FALSE,
    special_governance_flag BOOLEAN NOT NULL DEFAULT FALSE,
    active_flag BOOLEAN NOT NULL DEFAULT TRUE,
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wgv_role_code UNIQUE (tenant_id, role_code)
);

CREATE TABLE wgv_facility_organisation_link (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    facility_id BIGINT NOT NULL,
    organisation_id UUID NOT NULL REFERENCES wgv_organisation (id),
    organisation_unit_id UUID REFERENCES wgv_organisation_unit (id),
    relationship_type VARCHAR(64) NOT NULL,
    start_date DATE,
    end_date DATE,
    status VARCHAR(32) NOT NULL,
    primary_flag BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wgv_fol_facility ON wgv_facility_organisation_link (tenant_id, facility_id);
CREATE INDEX idx_wgv_fol_org ON wgv_facility_organisation_link (organisation_id);

CREATE TABLE wgv_site_organisation_link (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    site_id UUID NOT NULL,
    organisation_id UUID NOT NULL REFERENCES wgv_organisation (id),
    organisation_unit_id UUID REFERENCES wgv_organisation_unit (id),
    relationship_type VARCHAR(64) NOT NULL,
    start_date DATE,
    end_date DATE,
    status VARCHAR(32) NOT NULL,
    primary_flag BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wgv_sol_site ON wgv_site_organisation_link (tenant_id, site_id);

CREATE TABLE wgv_multi_site_group (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(512) NOT NULL,
    organisation_id UUID REFERENCES wgv_organisation (id),
    group_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wgv_msg_code UNIQUE (tenant_id, code)
);

CREATE TABLE wgv_multi_site_group_member (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    group_id UUID NOT NULL REFERENCES wgv_multi_site_group (id),
    member_target_type VARCHAR(32) NOT NULL,
    member_target_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    start_date DATE,
    end_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wgv_msgm_group ON wgv_multi_site_group_member (group_id);

CREATE TABLE wgv_assignment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_id VARCHAR(255) NOT NULL,
    role_definition_id UUID NOT NULL REFERENCES wgv_role_definition (id),
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    organisation_id UUID REFERENCES wgv_organisation (id),
    organisation_unit_id UUID REFERENCES wgv_organisation_unit (id),
    jurisdiction_id UUID REFERENCES wgv_jurisdiction (id),
    start_date DATE,
    end_date DATE,
    status VARCHAR(32) NOT NULL,
    primary_flag BOOLEAN NOT NULL DEFAULT FALSE,
    secondary_flag BOOLEAN NOT NULL DEFAULT FALSE,
    scope_summary TEXT,
    authority_level VARCHAR(64),
    reporting_line_ref VARCHAR(255),
    notes TEXT,
    extension_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wgv_asg_subject ON wgv_assignment (tenant_id, subject_type, subject_id);
CREATE INDEX idx_wgv_asg_target ON wgv_assignment (tenant_id, target_type, target_id);
CREATE INDEX idx_wgv_asg_status ON wgv_assignment (tenant_id, status);

CREATE TABLE wgv_assignment_status_history (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    assignment_id UUID NOT NULL REFERENCES wgv_assignment (id),
    previous_status VARCHAR(32),
    new_status VARCHAR(32) NOT NULL,
    changed_by VARCHAR(255) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reason TEXT,
    authority_context TEXT
);

CREATE INDEX idx_wgv_asgh_assignment ON wgv_assignment_status_history (assignment_id);

CREATE TABLE wgv_event_outbox (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    tenant_id VARCHAR(64),
    correlation_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_wgv_outbox_published_at ON wgv_event_outbox (published_at);
