-- Mvumo — Delegated / assisted access (L5): an explicit, scoped, time-bounded, revocable
-- relationship letting a delegate (caregiver/guardian/CHW/facility staff) act FOR a subject.
-- Consent-backed delegations link the identity-verified grant; the trust plane (PolicyEngine
-- Step 4.5) authorises each delegated action against an ACTIVE, in-scope, unexpired relationship
-- with the delegate meeting the assurance floor. Mvumo is the act-of-record; it never decides access.
SET search_path TO public;

CREATE TABLE mvumo.delegation_relationship (
    id                        UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                 UUID         NOT NULL,
    delegator_subject_ref     VARCHAR(255) NOT NULL,                 -- the person being acted for
    delegate_actor_id         VARCHAR(255) NOT NULL,                 -- the person acting
    relationship_type         VARCHAR(32)  NOT NULL,                 -- GUARDIAN|CAREGIVER|CHW|FACILITY_STAFF
    legal_basis               VARCHAR(32)  NOT NULL,                 -- CONSENT|GUARDIANSHIP|FACILITY_ASSIGNMENT
    scope                     JSONB        NOT NULL DEFAULT '[]'::jsonb,  -- allowed capability/resource scopes
    min_delegate_loa          INT          NOT NULL DEFAULT 2,       -- assurance floor the delegate must meet (1..4)
    status                    VARCHAR(32)  NOT NULL,                 -- GRANTED(=ACTIVE)|WITHDRAWN|EXPIRED|SUPERSEDED
    backing_consent_request_id UUID        REFERENCES mvumo.consent_request (id),  -- identity-verified grant (CONSENT basis)
    starts_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at                TIMESTAMPTZ,
    revoked_reason            VARCHAR(512),
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Resolve "is delegate D allowed to act for subject S" (hot path) + the two review surfaces.
CREATE INDEX idx_mvumo_delegation_resolve
    ON mvumo.delegation_relationship (tenant_id, delegate_actor_id, delegator_subject_ref, status);
CREATE INDEX idx_mvumo_delegation_by_subject
    ON mvumo.delegation_relationship (tenant_id, delegator_subject_ref, status);
