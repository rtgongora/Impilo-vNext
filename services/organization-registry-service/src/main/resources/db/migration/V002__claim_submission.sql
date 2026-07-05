CREATE TABLE org_registry.org_registry_claim_submission (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    organization_id         UUID NOT NULL REFERENCES org_registry.org_registry_organization(id),
    submitted_by_rep_id     UUID NOT NULL REFERENCES org_registry.org_registry_authorized_representative(id),
    subject_health_id       VARCHAR(128) NOT NULL,
    claimed_role            VARCHAR(128) NOT NULL,
    trust_basis             VARCHAR(64) NOT NULL DEFAULT 'ORG_DELEGATED',
    evidence                JSONB,
    status                  VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
    adjudication_ref        VARCHAR(255),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_org_registry_claim_status CHECK (status IN (
        'SUBMITTED', 'UNDER_REVIEW', 'ACCEPTED', 'REJECTED'
    ))
);

CREATE INDEX idx_org_registry_claim_org ON org_registry.org_registry_claim_submission (organization_id);
CREATE INDEX idx_org_registry_claim_subject ON org_registry.org_registry_claim_submission (tenant_id, subject_health_id);
CREATE INDEX idx_org_registry_claim_status ON org_registry.org_registry_claim_submission (tenant_id, status);
