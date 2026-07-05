CREATE SCHEMA IF NOT EXISTS org_registry;

CREATE TABLE org_registry.org_registry_organization (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    code                        VARCHAR(64) NOT NULL,
    legal_name                  VARCHAR(512) NOT NULL,
    org_type                    VARCHAR(64) NOT NULL,
    country_operation_ref       UUID,
    registration_identifiers    JSONB,
    status                      VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    verification_status         VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED',
    source                      VARCHAR(32) NOT NULL DEFAULT 'NATIVE',
    source_ref                  VARCHAR(255),
    verified_by                 VARCHAR(128),
    verified_at                 TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_org_registry_org_code UNIQUE (code),
    CONSTRAINT chk_org_registry_org_source CHECK (source IN ('NATIVE', 'WGV_MIRROR')),
    CONSTRAINT chk_org_registry_org_type CHECK (org_type IN (
        'GOVERNMENT_HEALTH_SERVICE', 'PUBLIC_HEALTH_AUTHORITY', 'LOCAL_AUTHORITY',
        'STATUTORY_REGULATOR', 'PROFESSIONAL_COUNCIL', 'PUBLIC_FACILITY_GROUP',
        'PRIVATE_PROVIDER_GROUP', 'MISSION_FAITH_BASED', 'NGO_IMPLEMENTING_PARTNER',
        'ACADEMIC_TRAINING', 'INSURER_MEDICAL_AID', 'LABORATORY_NETWORK',
        'IMAGING_RADIOLOGY', 'PHARMACY_COMMODITY', 'EMERGENCY_AMBULANCE',
        'TECHNOLOGY_PARTNER', 'OTHER'
    )),
    CONSTRAINT chk_org_registry_org_verification CHECK (verification_status IN (
        'UNVERIFIED', 'PENDING_VERIFICATION', 'VERIFIED', 'REJECTED', 'EXPIRED', 'REVOKED'
    ))
);

CREATE INDEX idx_org_registry_org_tenant ON org_registry.org_registry_organization (tenant_id);
CREATE INDEX idx_org_registry_org_status ON org_registry.org_registry_organization (tenant_id, status);
CREATE INDEX idx_org_registry_org_source_ref ON org_registry.org_registry_organization (source, source_ref);

CREATE TABLE org_registry.org_registry_authorized_representative (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    organization_id         UUID NOT NULL REFERENCES org_registry.org_registry_organization(id),
    person_health_id        VARCHAR(128) NOT NULL,
    role_title              VARCHAR(255),
    evidence_ref            VARCHAR(512),
    verification_status     VARCHAR(32) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    valid_from              DATE,
    valid_to                DATE,
    appointed_by            VARCHAR(128),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_org_registry_rep_verification CHECK (verification_status IN (
        'UNVERIFIED', 'PENDING_VERIFICATION', 'VERIFIED', 'REJECTED', 'EXPIRED', 'REVOKED'
    ))
);

CREATE INDEX idx_org_registry_rep_org ON org_registry.org_registry_authorized_representative (organization_id);
CREATE INDEX idx_org_registry_rep_health_id ON org_registry.org_registry_authorized_representative (tenant_id, person_health_id);

CREATE TABLE org_registry.org_registry_affiliation (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    organization_id         UUID NOT NULL REFERENCES org_registry.org_registry_organization(id),
    subject_type            VARCHAR(32) NOT NULL,
    subject_ref             VARCHAR(255) NOT NULL,
    affiliation_type        VARCHAR(64) NOT NULL,
    status                  VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    source_channel          VARCHAR(64),
    valid_from              DATE,
    valid_to                DATE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_org_registry_affiliation_subject CHECK (subject_type IN ('FACILITY', 'PROVIDER'))
);

CREATE INDEX idx_org_registry_affiliation_org ON org_registry.org_registry_affiliation (organization_id);
CREATE INDEX idx_org_registry_affiliation_subject ON org_registry.org_registry_affiliation (subject_type, subject_ref);

CREATE TABLE org_registry.event_outbox (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(64) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    schema_version  INT NOT NULL DEFAULT 1,
    correlation_id  UUID,
    causation_id    UUID,
    idempotency_key VARCHAR(255) NOT NULL,
    producer        VARCHAR(64) NOT NULL DEFAULT 'organization-registry-service',
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
    CONSTRAINT uq_org_registry_outbox_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_org_registry_outbox_unpublished ON org_registry.event_outbox (created_at) WHERE published_at IS NULL;
