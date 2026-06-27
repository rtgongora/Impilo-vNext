-- V015 (Wave B B6): GDHCN readiness assessment.
-- Per-tenant assessment values for the fixed code catalogue of readiness domains
-- (GdhcnReadinessDomain). One row per (tenant, domain) once assessed; unassessed domains
-- default to NOT_STARTED in the service. Conservative by design — see
-- docs/architecture/tshepo-gdhcn-trust-and-certification.md.

CREATE TABLE tshepo_authz.gdhcn_readiness_assessment (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    domain_key          VARCHAR(64)  NOT NULL,
    current_maturity    VARCHAR(32)  NOT NULL DEFAULT 'NOT_STARTED',
    target_maturity     VARCHAR(32)  NOT NULL DEFAULT 'PRODUCTION_NOT_READY',
    owner               VARCHAR(255),
    evidence_status     VARCHAR(255),
    remediation_status  VARCHAR(255),
    linked_component    VARCHAR(255),
    updated_by          VARCHAR(255) NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_gdhcn_readiness_tenant_domain UNIQUE (tenant_id, domain_key)
);
CREATE INDEX idx_gdhcn_readiness_tenant ON tshepo_authz.gdhcn_readiness_assessment (tenant_id);
