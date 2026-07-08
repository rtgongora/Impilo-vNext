-- ============================================================================
-- organization-registry-service V005 — organisation-invitation onboarding
--
-- Channel-C onboarding path complementing the self-submitted claim: an
-- organisation's authorized representative INVITES a person to hold a role
-- (e.g. facility administrator). The invitee accepts the invitation to
-- establish an affiliation — no adjudication queue, because the inviting
-- organisation is the authority asserting the role.
--
-- Honest-state semantics:
--   status=PENDING   -> invitation issued, awaiting acceptance (before expiry)
--   status=ACCEPTED  -> invitee accepted; affiliation_id references the created
--                       org_registry_affiliation row
--   status=REVOKED   -> withdrawn by the inviting representative
--   status=EXPIRED   -> expires_at passed without acceptance
--
-- The token is a single-use opaque acceptance secret (never a PII carrier).
-- ============================================================================

CREATE TABLE org_registry.org_registry_invitation (
    id                        UUID PRIMARY KEY,
    tenant_id                 UUID NOT NULL,
    organization_id           UUID NOT NULL REFERENCES org_registry.org_registry_organization (id),
    facility_uuid             UUID,
    invited_by_rep_id         UUID NOT NULL,
    invitee_identifier        VARCHAR(255) NOT NULL,
    invitee_identifier_type   VARCHAR(32)  NOT NULL DEFAULT 'EMAIL',
    role                      VARCHAR(128) NOT NULL,
    token                     VARCHAR(128) NOT NULL,
    status                    VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at                TIMESTAMPTZ  NOT NULL,
    accepted_at               TIMESTAMPTZ,
    accepted_by_health_id     VARCHAR(128),
    affiliation_id            UUID,
    CONSTRAINT uq_org_registry_invitation_token UNIQUE (token)
);

CREATE INDEX idx_org_registry_invitation_org
    ON org_registry.org_registry_invitation (organization_id);

CREATE INDEX idx_org_registry_invitation_status
    ON org_registry.org_registry_invitation (tenant_id, status);

CREATE INDEX idx_org_registry_invitation_invitee
    ON org_registry.org_registry_invitation (tenant_id, invitee_identifier);
