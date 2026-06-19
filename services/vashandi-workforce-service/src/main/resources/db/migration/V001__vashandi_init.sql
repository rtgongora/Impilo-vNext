CREATE SCHEMA IF NOT EXISTS vashandi;

CREATE TABLE vashandi.vsh_workforce_profile (
    id                                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                           UUID NOT NULL,
    health_id                           VARCHAR(128),
    provider_worker_id                  VARCHAR(128),
    keycloak_user_id                    VARCHAR(128),
    primary_organisation_id             UUID,
    primary_employer_organisation_id    UUID,
    worker_type                         VARCHAR(64),
    profession                          VARCHAR(128),
    cadre                               VARCHAR(64),
    current_status                      VARCHAR(32) NOT NULL DEFAULT 'pending_appointment',
    professional_status_summary_json    JSONB,
    hsc_status_summary_json             JSONB,
    training_status_summary_json        JSONB,
    access_risk_status                  VARCHAR(32) NOT NULL DEFAULT 'clear',
    provenance_metadata_json            JSONB,
    created_at                          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_vsh_profile_status CHECK (current_status IN (
        'active', 'pending_appointment', 'on_leave', 'transferred_pending_assumption',
        'seconded', 'suspended', 'dismissed', 'retired', 'resigned',
        'contract_expired', 'under_review', 'offboarded'
    ))
);

CREATE INDEX idx_vsh_profile_tenant ON vashandi.vsh_workforce_profile (tenant_id);
CREATE INDEX idx_vsh_profile_health_id ON vashandi.vsh_workforce_profile (tenant_id, health_id);
CREATE INDEX idx_vsh_profile_provider_worker_id ON vashandi.vsh_workforce_profile (tenant_id, provider_worker_id);
CREATE INDEX idx_vsh_profile_keycloak_user_id ON vashandi.vsh_workforce_profile (tenant_id, keycloak_user_id);
CREATE INDEX idx_vsh_profile_org ON vashandi.vsh_workforce_profile (primary_organisation_id);
CREATE INDEX idx_vsh_profile_status ON vashandi.vsh_workforce_profile (tenant_id, current_status);

CREATE TABLE vashandi.vsh_workforce_membership (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    workforce_profile_id    UUID NOT NULL REFERENCES vashandi.vsh_workforce_profile(id),
    organisation_id         UUID NOT NULL,
    organisation_type       VARCHAR(64) NOT NULL,
    membership_type         VARCHAR(64) NOT NULL,
    status                  VARCHAR(32) NOT NULL DEFAULT 'active',
    effective_date          DATE NOT NULL,
    expiry_date             DATE,
    source                  VARCHAR(64),
    audit_metadata_json     JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_vsh_membership_profile ON vashandi.vsh_workforce_membership (workforce_profile_id);
CREATE INDEX idx_vsh_membership_org ON vashandi.vsh_workforce_membership (organisation_id);
CREATE INDEX idx_vsh_membership_status ON vashandi.vsh_workforce_membership (tenant_id, status);

CREATE TABLE vashandi.vsh_workforce_assignment (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    workforce_profile_id    UUID NOT NULL REFERENCES vashandi.vsh_workforce_profile(id),
    assignment_type         VARCHAR(64) NOT NULL,
    organisation_id         UUID,
    facility_id             UUID,
    department_id           VARCHAR(128),
    unit_id                 VARCHAR(128),
    programme_id            VARCHAR(128),
    workspace_id            UUID,
    role_template_id        VARCHAR(128),
    supervisor_profile_id   UUID,
    status                  VARCHAR(32) NOT NULL DEFAULT 'draft',
    start_date              DATE,
    end_date                DATE,
    source_authority        VARCHAR(64),
    eligibility_status      VARCHAR(32) NOT NULL DEFAULT 'pending',
    opa_decision_id         VARCHAR(128),
    created_by              VARCHAR(128),
    approved_by             VARCHAR(128),
    audit_metadata_json     JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_vsh_assignment_status CHECK (status IN (
        'draft', 'requested', 'prechecked', 'pending_approval', 'approved',
        'active', 'suspended', 'ended', 'expired', 'rejected'
    ))
);

CREATE INDEX idx_vsh_assignment_profile ON vashandi.vsh_workforce_assignment (workforce_profile_id);
CREATE INDEX idx_vsh_assignment_org ON vashandi.vsh_workforce_assignment (organisation_id);
CREATE INDEX idx_vsh_assignment_facility ON vashandi.vsh_workforce_assignment (facility_id);
CREATE INDEX idx_vsh_assignment_department ON vashandi.vsh_workforce_assignment (department_id);
CREATE INDEX idx_vsh_assignment_role_template ON vashandi.vsh_workforce_assignment (role_template_id);
CREATE INDEX idx_vsh_assignment_status ON vashandi.vsh_workforce_assignment (tenant_id, status);

CREATE TABLE vashandi.vsh_roster (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    organisation_id         UUID NOT NULL,
    facility_id             UUID,
    department_id           VARCHAR(128),
    unit_id                 VARCHAR(128),
    roster_type             VARCHAR(64) NOT NULL,
    period_start            DATE NOT NULL,
    period_end              DATE NOT NULL,
    status                  VARCHAR(32) NOT NULL DEFAULT 'draft',
    created_by              VARCHAR(128),
    approved_by             VARCHAR(128),
    audit_metadata_json     JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_vsh_roster_org ON vashandi.vsh_roster (organisation_id);
CREATE INDEX idx_vsh_roster_facility ON vashandi.vsh_roster (facility_id);
CREATE INDEX idx_vsh_roster_department ON vashandi.vsh_roster (department_id);
CREATE INDEX idx_vsh_roster_period ON vashandi.vsh_roster (tenant_id, period_start, period_end);
CREATE INDEX idx_vsh_roster_status ON vashandi.vsh_roster (tenant_id, status);

CREATE TABLE vashandi.vsh_shift (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    roster_id               UUID NOT NULL REFERENCES vashandi.vsh_roster(id),
    workforce_profile_id    UUID NOT NULL REFERENCES vashandi.vsh_workforce_profile(id),
    assignment_id           UUID REFERENCES vashandi.vsh_workforce_assignment(id),
    shift_type              VARCHAR(64) NOT NULL,
    start_time              TIMESTAMPTZ NOT NULL,
    end_time                TIMESTAMPTZ NOT NULL,
    location_type           VARCHAR(64),
    facility_id             UUID,
    virtual_pool_id         VARCHAR(128),
    status                  VARCHAR(32) NOT NULL DEFAULT 'scheduled',
    check_in_required       BOOLEAN NOT NULL DEFAULT TRUE,
    audit_metadata_json     JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_vsh_shift_status CHECK (status IN (
        'scheduled', 'confirmed', 'checked_in', 'late', 'missed',
        'checked_out', 'completed', 'cancelled', 'swapped', 'covered'
    ))
);

CREATE INDEX idx_vsh_shift_roster ON vashandi.vsh_shift (roster_id);
CREATE INDEX idx_vsh_shift_profile ON vashandi.vsh_shift (workforce_profile_id);
CREATE INDEX idx_vsh_shift_assignment ON vashandi.vsh_shift (assignment_id);
CREATE INDEX idx_vsh_shift_facility ON vashandi.vsh_shift (facility_id);
CREATE INDEX idx_vsh_shift_start_end ON vashandi.vsh_shift (tenant_id, start_time, end_time);
CREATE INDEX idx_vsh_shift_status ON vashandi.vsh_shift (tenant_id, status);

CREATE TABLE vashandi.vsh_attendance_event (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    shift_id                UUID REFERENCES vashandi.vsh_shift(id),
    workforce_profile_id    UUID NOT NULL REFERENCES vashandi.vsh_workforce_profile(id),
    event_type              VARCHAR(64) NOT NULL,
    event_time              TIMESTAMPTZ NOT NULL,
    check_in_mode           VARCHAR(64),
    location_evidence_json  JSONB,
    device_id               VARCHAR(128),
    supervisor_confirmed_by VARCHAR(128),
    offline                 BOOLEAN NOT NULL DEFAULT FALSE,
    reconciled_at           TIMESTAMPTZ,
    audit_metadata_json     JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_vsh_attendance_profile ON vashandi.vsh_attendance_event (workforce_profile_id);
CREATE INDEX idx_vsh_attendance_shift ON vashandi.vsh_attendance_event (shift_id);
CREATE INDEX idx_vsh_attendance_event_time ON vashandi.vsh_attendance_event (tenant_id, event_time);
CREATE INDEX idx_vsh_attendance_event_type ON vashandi.vsh_attendance_event (tenant_id, event_type);

CREATE TABLE vashandi.vsh_leave_availability (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    workforce_profile_id    UUID NOT NULL REFERENCES vashandi.vsh_workforce_profile(id),
    leave_type              VARCHAR(64) NOT NULL,
    status                  VARCHAR(32) NOT NULL DEFAULT 'pending',
    start_date              DATE NOT NULL,
    end_date                DATE NOT NULL,
    approved_by             VARCHAR(128),
    source_authority        VARCHAR(64),
    audit_metadata_json     JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_vsh_leave_profile ON vashandi.vsh_leave_availability (workforce_profile_id);
CREATE INDEX idx_vsh_leave_dates ON vashandi.vsh_leave_availability (tenant_id, start_date, end_date);
CREATE INDEX idx_vsh_leave_status ON vashandi.vsh_leave_availability (tenant_id, status);

CREATE TABLE vashandi.vsh_access_risk (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    workforce_profile_id    UUID NOT NULL REFERENCES vashandi.vsh_workforce_profile(id),
    risk_type               VARCHAR(64) NOT NULL,
    severity                VARCHAR(16) NOT NULL DEFAULT 'medium',
    detected_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status                  VARCHAR(32) NOT NULL DEFAULT 'open',
    recommended_action      VARCHAR(255),
    resolved_by             VARCHAR(128),
    resolved_at             TIMESTAMPTZ,
    audit_metadata_json     JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_vsh_access_risk_severity CHECK (severity IN ('low', 'medium', 'high', 'critical')),
    CONSTRAINT chk_vsh_access_risk_status CHECK (status IN ('open', 'resolved', 'revoked', 'dismissed'))
);

CREATE INDEX idx_vsh_access_risk_profile ON vashandi.vsh_access_risk (workforce_profile_id);
CREATE INDEX idx_vsh_access_risk_status ON vashandi.vsh_access_risk (tenant_id, status);
CREATE INDEX idx_vsh_access_risk_severity ON vashandi.vsh_access_risk (tenant_id, severity);

CREATE TABLE vashandi.event_outbox (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(64) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    schema_version  INT NOT NULL DEFAULT 1,
    correlation_id  UUID,
    causation_id    UUID,
    idempotency_key VARCHAR(255) NOT NULL,
    producer        VARCHAR(64) NOT NULL DEFAULT 'vashandi-workforce-service',
    tenant_id       UUID NOT NULL,
    pod_id          VARCHAR(64) NOT NULL DEFAULT 'national-spine',
    subject_id      VARCHAR(255) NOT NULL,
    subject_type    VARCHAR(64) NOT NULL,
    partition_key   VARCHAR(255),
    occurred_at     TIMESTAMPTZ NOT NULL,
    payload_json    JSONB NOT NULL,
    publish_error   TEXT,
    retry_count     INT DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    CONSTRAINT uq_vsh_outbox_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_vsh_outbox_unpublished ON vashandi.event_outbox (created_at) WHERE published_at IS NULL;
