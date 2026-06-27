-- V014 (Wave B B5): Tshepo Trust Authority registry.
-- Records the trust authorities, authorised issuer systems and document-signer certificate
-- METADATA (no private keys) that Tshepo recognises, plus an honest readiness assessment per
-- authority. This makes Tshepo GDHCN-ready; it does NOT assert GDHCN conformance.

CREATE TABLE tshepo_authz.trust_authority (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL,
    name          VARCHAR(255) NOT NULL,
    trust_domain  VARCHAR(255) NOT NULL,
    environment   VARCHAR(16)  NOT NULL,                 -- LOCAL / TEST / UAT / PROD
    status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT', -- DRAFT/PENDING/ACTIVE/SUSPENDED/RETIRED
    readiness     VARCHAR(32)  NOT NULL DEFAULT 'NOT_STARTED',
    created_by    VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_trust_authority_tenant ON tshepo_authz.trust_authority (tenant_id);

CREATE TABLE tshepo_authz.trust_issuer_system (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL,
    trust_authority_id UUID         NOT NULL REFERENCES tshepo_authz.trust_authority (id) ON DELETE CASCADE,
    name               VARCHAR(255) NOT NULL,
    issuer_uri         VARCHAR(512) NOT NULL,
    status             VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_trust_issuer_authority ON tshepo_authz.trust_issuer_system (trust_authority_id);

CREATE TABLE tshepo_authz.trust_document_signer_cert (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL,
    trust_authority_id UUID         NOT NULL REFERENCES tshepo_authz.trust_authority (id) ON DELETE CASCADE,
    kid                VARCHAR(255) NOT NULL,
    subject            VARCHAR(512) NOT NULL,
    not_before         TIMESTAMPTZ,
    not_after          TIMESTAMPTZ,
    status             VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_trust_dsc_authority ON tshepo_authz.trust_document_signer_cert (trust_authority_id);
