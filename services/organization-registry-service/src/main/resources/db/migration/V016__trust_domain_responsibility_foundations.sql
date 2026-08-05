-- Wave 1 — Trust-Domain and Responsibility Foundations
-- Implements the frozen architecture v1.3.8 (ADR-0054): §3.2 canonical schema, §3.3
-- lifecycles, §3A.1 responsibility profile, §3A.4 processing_role_assignment.
--
-- THE ONE RULE THIS SCHEMA EXISTS TO ENFORCE (§3A.2 [D]):
--   No authority may be inferred from any operator field. infrastructure_operator_id,
--   platform_operator_id, application_operator_id, key_custodian_id and the rest say who
--   RUNS something. They never say who is legally responsible for it. Only
--   processing_role_assignment and the profile's declared authority fields do that.
--
-- The MoHCC hospital controllership question (§26.2 #1) is an OPEN [L] legal
-- determination. Nothing in this migration decides it, defaults it, or backfills it.

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Trust domain — the governed data-control and disclosure boundary (§3.2)
--
-- A trust domain identifies the applicable controller or controller ARRANGEMENT.
-- It is NOT itself necessarily a legal person, and it is never automatically the
-- controller of anything (§1 boundary line, §3A.2).
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE org_registry.trust_domain (
    trust_domain_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                        VARCHAR(48)  NOT NULL UNIQUE,
    display_name                TEXT         NOT NULL,
    controller_type             VARCHAR(32)  NOT NULL,
    -- The legal person named as the domain's declared controlling party, where one is
    -- known. NULLABLE, deliberately, and this DEVIATES from §3.2's NOT NULL: for a
    -- domain whose controlling party is an open [L] determination, NOT NULL could only
    -- be satisfied by inventing an answer. UNDETERMINED must stay representable
    -- (§3A.4 resolution order ends in "refused and flagged", never a guess).
    data_controller_legal_name  TEXT         NULL,
    data_controller_contact     JSONB        NULL,
    controller_declaration_state VARCHAR(32) NOT NULL DEFAULT 'UNDETERMINED',
    jurisdiction_id             UUID         NULL,
    accreditation_status        VARCHAR(24)  NOT NULL DEFAULT 'PROVISIONAL',
    accredited_at               TIMESTAMPTZ  NULL,
    suspended_at                TIMESTAMPTZ  NULL,
    withdrawn_at                TIMESTAMPTZ  NULL,
    default_data_sharing_policy_id UUID      NULL,
    key_custody_mode            VARCHAR(24)  NOT NULL DEFAULT 'NATIONAL',
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by_actor            VARCHAR(128) NOT NULL,
    version                     INT          NOT NULL DEFAULT 0,

    CONSTRAINT chk_trust_domain_controller_type CHECK (controller_type IN (
        'MINISTRY', 'PRIVATE_GROUP', 'MISSION', 'LOCAL_AUTHORITY',
        'SECURITY_SECTOR', 'UNIVERSITY', 'REGULATOR', 'PARTNER')),
    -- §3.3: PROVISIONAL -> ACCREDITED -> (SUSPENDED <-> ACCREDITED) -> WITHDRAWN
    CONSTRAINT chk_trust_domain_accreditation CHECK (accreditation_status IN (
        'PROVISIONAL', 'ACCREDITED', 'SUSPENDED', 'WITHDRAWN')),
    CONSTRAINT chk_trust_domain_key_custody CHECK (key_custody_mode IN ('NATIONAL', 'INSTITUTIONAL')),
    CONSTRAINT chk_trust_domain_controller_declaration CHECK (controller_declaration_state IN (
        'UNDETERMINED', 'PENDING_GOVERNANCE_REVIEW', 'DECLARED')),
    -- A declared controller needs a name; an undetermined one must not carry one.
    CONSTRAINT chk_trust_domain_declaration_consistent CHECK (
        (controller_declaration_state = 'DECLARED' AND data_controller_legal_name IS NOT NULL)
        OR (controller_declaration_state <> 'DECLARED' AND data_controller_legal_name IS NULL))
);

COMMENT ON COLUMN org_registry.trust_domain.data_controller_legal_name IS
    'Declared controlling party for the domain, where determined. NULL means UNDETERMINED. '
    'Never inferred from hosting, custody, operation, source authority or ownership (§3A.2).';

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Trust-domain hierarchy (§6A) — one family, institutional subdomains
--
-- §6A is [L — recommendation given, determination pending]. This table makes ALL
-- THREE options expressible without remodelling: a single MoHCC domain (no rows), a
-- flat set of hospital domains (no rows), or a hierarchical family (parent rows).
-- Which option is adopted is a governance decision recorded as DATA, not a migration.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE org_registry.trust_domain_relationship (
    relationship_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_trust_domain_id UUID NOT NULL REFERENCES org_registry.trust_domain(trust_domain_id),
    child_trust_domain_id  UUID NOT NULL REFERENCES org_registry.trust_domain(trust_domain_id),
    relationship_type   VARCHAR(32) NOT NULL DEFAULT 'FAMILY_SUBDOMAIN',
    -- Membership of a family NEVER implies clinical access between members (§6A.3,
    -- doctrine 10). Any such access requires its own federation agreement.
    grants_clinical_access BOOLEAN NOT NULL DEFAULT FALSE,
    delegated_governance JSONB NULL,
    effective_from      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    effective_to        TIMESTAMPTZ NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_actor    VARCHAR(128) NOT NULL,

    CONSTRAINT chk_tdr_type CHECK (relationship_type IN ('FAMILY_SUBDOMAIN', 'DELEGATED_GOVERNANCE')),
    CONSTRAINT chk_tdr_status CHECK (status IN ('ACTIVE', 'SUPERSEDED', 'WITHDRAWN')),
    CONSTRAINT chk_tdr_not_self CHECK (parent_trust_domain_id <> child_trust_domain_id),
    -- A family subdomain must never be a route to clinical data.
    CONSTRAINT chk_tdr_no_clinical_access CHECK (grants_clinical_access = FALSE)
);
CREATE UNIQUE INDEX ux_tdr_active_child ON org_registry.trust_domain_relationship
    (child_trust_domain_id) WHERE status = 'ACTIVE';
CREATE INDEX ix_tdr_parent ON org_registry.trust_domain_relationship (parent_trust_domain_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Service responsibility profile (§3A.1)
--
-- The operator fields say who RUNS things. data_controller_id says who is legally
-- responsible — and it is NULLABLE because §26.2 #1 is undecided (§3A.4, F6).
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE org_registry.service_responsibility_profile (
    responsibility_profile_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_code                    VARCHAR(64) NOT NULL,
    hosting_model                   VARCHAR(32) NOT NULL,

    -- Operator fields — capability, never authority (§3A.2).
    infrastructure_operator_id      UUID NULL,
    platform_operator_id            UUID NULL,
    application_operator_id         UUID NULL,
    data_processor_id               UUID NULL,
    identity_operator_id            UUID NULL,
    key_custodian_id                UUID NULL,
    backup_operator_id              UUID NULL,
    release_approver_id             UUID NULL,
    security_monitoring_operator_id UUID NULL,

    -- Authority fields — the ONLY sources of authority (§3A.2).
    -- NEVER inferred from any operator field above.
    data_controller_id              UUID NULL,
    clinical_governance_authority_id UUID NULL,

    -- Makes "we have not decided" a first-class, queryable state rather than a NULL
    -- whose meaning must be guessed.
    controller_resolution_state     VARCHAR(32) NOT NULL DEFAULT 'UNDETERMINED',

    support_access_policy_id        UUID NULL,
    maintenance_window_policy_id    UUID NULL,

    status                          VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    version                         INT NOT NULL DEFAULT 1,
    supersedes_profile_id           UUID NULL REFERENCES org_registry.service_responsibility_profile(responsibility_profile_id),
    supersession_reason             TEXT NULL,
    effective_from                  TIMESTAMPTZ NULL,
    effective_to                    TIMESTAMPTZ NULL,
    approved_by_actor               VARCHAR(128) NULL,
    approved_at                     TIMESTAMPTZ NULL,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_actor                VARCHAR(128) NOT NULL,
    lock_version                    INT NOT NULL DEFAULT 0,

    CONSTRAINT chk_srp_hosting_model CHECK (hosting_model IN (
        'NATIONAL_INFRASTRUCTURE', 'NATIONAL_DATA_CENTRE', 'INSTITUTION_PREMISES',
        'SOVEREIGN_ON_PREMISES', 'APPROVED_SOVEREIGN_CLOUD', 'DR_SITE')),
    -- §Scope C lifecycle.
    CONSTRAINT chk_srp_status CHECK (status IN (
        'DRAFT', 'VALIDATED', 'PENDING_APPROVAL', 'ACTIVE',
        'SUSPENDED', 'EXPIRED', 'TERMINATED', 'SUPERSEDED')),
    CONSTRAINT chk_srp_resolution_state CHECK (controller_resolution_state IN (
        'UNDETERMINED', 'PENDING_GOVERNANCE_REVIEW', 'RESOLVED')),
    -- RESOLVED requires a controller; anything else must NOT carry one. This is what
    -- stops a fabricated controller entering through the default path.
    CONSTRAINT chk_srp_resolution_consistent CHECK (
        (controller_resolution_state = 'RESOLVED' AND data_controller_id IS NOT NULL)
        OR (controller_resolution_state <> 'RESOLVED' AND data_controller_id IS NULL)),
    CONSTRAINT chk_srp_no_self_supersede CHECK (
        supersedes_profile_id IS NULL OR supersedes_profile_id <> responsibility_profile_id)
);
CREATE INDEX ix_srp_code_status ON org_registry.service_responsibility_profile (profile_code, status);
CREATE INDEX ix_srp_supersedes ON org_registry.service_responsibility_profile (supersedes_profile_id);

COMMENT ON COLUMN org_registry.service_responsibility_profile.data_controller_id IS
    'The legally responsible party. NULLABLE because §26.2 #1 is an OPEN [L] determination. '
    'NOT NULL here could only be satisfied by guessing an undecided legal fact (§3A.4).';

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Service agreement (§3A.1)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE org_registry.service_agreement (
    service_agreement_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trust_domain_id           UUID NOT NULL REFERENCES org_registry.trust_domain(trust_domain_id),
    organisation_id           UUID NOT NULL REFERENCES org_registry.org_registry_organization(id),
    consumption_profile       VARCHAR(32) NOT NULL,
    isolation_tier            VARCHAR(8)  NULL,
    commercial_service_tier   VARCHAR(32) NULL,
    responsibility_profile_id UUID NOT NULL REFERENCES org_registry.service_responsibility_profile(responsibility_profile_id),
    effective_from            TIMESTAMPTZ NOT NULL,
    effective_to              TIMESTAMPTZ NULL,
    status                    VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    signed_by_platform        JSONB NULL,
    signed_by_organisation    JSONB NULL,
    version                   INT NOT NULL DEFAULT 1,
    supersedes_agreement_id   UUID NULL REFERENCES org_registry.service_agreement(service_agreement_id),
    supersession_reason       TEXT NULL,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_actor          VARCHAR(128) NOT NULL,
    lock_version              INT NOT NULL DEFAULT 0,

    CONSTRAINT chk_sa_consumption CHECK (consumption_profile IN (
        'NATIONAL_SHARED_HOSTED', 'DEDICATED_HOSTED', 'MANAGED_ON_PREMISES',
        'SOVEREIGN_ON_PREMISES', 'FACILITY_EDGE')),
    CONSTRAINT chk_sa_isolation CHECK (isolation_tier IS NULL OR isolation_tier IN ('D1','D2','D3','D4','D5','D6')),
    CONSTRAINT chk_sa_status CHECK (status IN (
        'DRAFT', 'VALIDATED', 'PENDING_SIGNATURE', 'ACTIVE',
        'SUSPENDED', 'EXPIRED', 'TERMINATED', 'SUPERSEDED')),
    CONSTRAINT chk_sa_period CHECK (effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT chk_sa_no_self_supersede CHECK (
        supersedes_agreement_id IS NULL OR supersedes_agreement_id <> service_agreement_id)
);
-- At most one ACTIVE agreement per (trust domain, organisation) at any instant.
-- Contradictory concurrent agreements for one governed scope are a governance defect,
-- not a merge conflict to resolve later.
ALTER TABLE org_registry.service_agreement
    ADD CONSTRAINT ex_sa_one_active_per_scope
    EXCLUDE USING gist (
        trust_domain_id WITH =, organisation_id WITH =,
        tstzrange(effective_from, effective_to) WITH &&
    ) WHERE (status = 'ACTIVE');
CREATE INDEX ix_sa_org ON org_registry.service_agreement (organisation_id, status);
CREATE INDEX ix_sa_domain ON org_registry.service_agreement (trust_domain_id, status);

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Processing role assignment (§3A.4) — controllership per (scope, domain, purpose)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE org_registry.processing_role_assignment (
    assignment_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scope_type           VARCHAR(24) NOT NULL,
    scope_id             UUID NOT NULL,
    data_domain          VARCHAR(64) NOT NULL,
    purpose              VARCHAR(64) NOT NULL,
    legal_basis          VARCHAR(48) NOT NULL,
    role_type            VARCHAR(32) NOT NULL,
    party_id             UUID NOT NULL,
    joint_arrangement_id UUID NULL,
    instrument_reference TEXT NULL,
    effective_from       TIMESTAMPTZ NOT NULL,
    effective_to         TIMESTAMPTZ NULL,
    status               VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    recorded_by          VARCHAR(128) NOT NULL,
    recorded_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    lock_version         INT NOT NULL DEFAULT 0,

    CONSTRAINT chk_pra_scope_type CHECK (scope_type IN (
        'TRUST_DOMAIN', 'ORGANISATION', 'FACILITY', 'NODE', 'SERVICE_AGREEMENT', 'PROGRAMME')),
    CONSTRAINT chk_pra_legal_basis CHECK (legal_basis IN (
        'CONSENT', 'LEGAL_OBLIGATION', 'VITAL_INTEREST', 'PUBLIC_TASK',
        'CONTRACT', 'LEGITIMATE_INTEREST')),
    CONSTRAINT chk_pra_role_type CHECK (role_type IN (
        'LEGAL_CONTROLLER', 'JOINT_CONTROLLER', 'PROCESSOR', 'SUB_PROCESSOR',
        'SOURCE_AUTHORITY', 'CUSTODIAN', 'RIGHTSHOLDER',
        'DISCLOSURE_AUTHORITY', 'CLINICAL_GOVERNANCE')),
    CONSTRAINT chk_pra_status CHECK (status IN ('DRAFT', 'ACTIVE', 'SUPERSEDED', 'WITHDRAWN')),
    CONSTRAINT chk_pra_period CHECK (effective_to IS NULL OR effective_to > effective_from)
);

-- §3A.4's non-negotiable rule: at most ONE active legal controller per
-- (scope, data_domain, purpose) at any instant. Joint control is expressed by
-- JOINT_CONTROLLER rows sharing a joint_arrangement_id, never by two controllers.
ALTER TABLE org_registry.processing_role_assignment
    ADD CONSTRAINT ex_pra_single_active_controller
    EXCLUDE USING gist (
        scope_id WITH =, data_domain WITH =, purpose WITH =, role_type WITH =,
        tstzrange(effective_from, effective_to) WITH &&
    ) WHERE (status = 'ACTIVE' AND role_type = 'LEGAL_CONTROLLER');

CREATE INDEX ix_pra_resolution ON org_registry.processing_role_assignment
    (scope_type, scope_id, data_domain, purpose, status);
CREATE INDEX ix_pra_party ON org_registry.processing_role_assignment (party_id, status);

CREATE TABLE org_registry.joint_controller_arrangement (
    joint_arrangement_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    description            TEXT NOT NULL,
    responsibility_split   JSONB NOT NULL,
    contact_point_party_id UUID NOT NULL,
    instrument_reference   TEXT NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_actor       VARCHAR(128) NOT NULL
);
ALTER TABLE org_registry.processing_role_assignment
    ADD CONSTRAINT fk_pra_joint_arrangement FOREIGN KEY (joint_arrangement_id)
    REFERENCES org_registry.joint_controller_arrangement(joint_arrangement_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. Governance audit (§Scope G) — append-only, no clinical content, no secrets
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE org_registry.responsibility_audit_event (
    audit_event_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor_id           VARCHAR(128) NOT NULL,
    actor_authority    VARCHAR(128) NULL,
    actor_scope_type   VARCHAR(24)  NULL,
    actor_scope_id     UUID         NULL,
    operation          VARCHAR(64)  NOT NULL,
    target_type        VARCHAR(48)  NOT NULL,
    target_id          UUID         NULL,
    trust_domain_id    UUID         NULL,
    organisation_id    UUID         NULL,
    facility_id        UUID         NULL,
    previous_version   INT          NULL,
    new_version        INT          NULL,
    decision           VARCHAR(16)  NOT NULL,
    reason_code        VARCHAR(64)  NULL,
    reason_detail      TEXT         NULL,
    effective_date     TIMESTAMPTZ  NULL,
    correlation_id     VARCHAR(128) NULL,
    request_id         VARCHAR(128) NULL,
    source_service     VARCHAR(64)  NOT NULL DEFAULT 'organization-registry-service',

    CONSTRAINT chk_rae_decision CHECK (decision IN ('PERMITTED', 'REFUSED'))
);
CREATE INDEX ix_rae_target ON org_registry.responsibility_audit_event (target_type, target_id, occurred_at DESC);
CREATE INDEX ix_rae_actor ON org_registry.responsibility_audit_event (actor_id, occurred_at DESC);

-- Append-only: the audit trail is evidence, and evidence that can be edited is not
-- evidence. Enforced at the database so a service defect cannot quietly rewrite it.
CREATE OR REPLACE FUNCTION org_registry.reject_audit_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'responsibility_audit_event is append-only (%).', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_rae_no_update BEFORE UPDATE ON org_registry.responsibility_audit_event
    FOR EACH ROW EXECUTE FUNCTION org_registry.reject_audit_mutation();
CREATE TRIGGER trg_rae_no_delete BEFORE DELETE ON org_registry.responsibility_audit_event
    FOR EACH ROW EXECUTE FUNCTION org_registry.reject_audit_mutation();

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. Organisation membership of a trust domain (§3.2)
--
-- DELIBERATE DEVIATION FROM §3.2, REPORTED:
--   §3.2 specifies `organisation ADD COLUMN trust_domain_id UUID NOT NULL` with
--   "backfill every existing organisation -> the MoHCC trust domain". That backfill is
--   unsafe against the actual data: the closed org_type vocabulary in V001 already
--   includes PRIVATE_PROVIDER_GROUP and LOCAL_AUTHORITY, which are NOT in the MoHCC
--   trust domain. Backfilling them would write a false membership and then enforce it
--   with NOT NULL.
--   Membership is therefore NULLABLE with an explicit UNMAPPED state. Assignment is a
--   governance act performed per organisation with evidence, in a later wave.
--   Membership is NOT controllership either way (§3A.2).
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE org_registry.org_registry_organization
    ADD COLUMN trust_domain_id UUID NULL REFERENCES org_registry.trust_domain(trust_domain_id),
    ADD COLUMN trust_domain_membership_state VARCHAR(32) NOT NULL DEFAULT 'UNMAPPED',
    ADD COLUMN trust_domain_assigned_at TIMESTAMPTZ NULL,
    ADD COLUMN trust_domain_assigned_by VARCHAR(128) NULL,
    ADD CONSTRAINT chk_org_td_membership_state CHECK (trust_domain_membership_state IN (
        'UNMAPPED', 'PENDING_GOVERNANCE_REVIEW', 'ASSIGNED')),
    ADD CONSTRAINT chk_org_td_membership_consistent CHECK (
        (trust_domain_membership_state = 'ASSIGNED' AND trust_domain_id IS NOT NULL)
        OR (trust_domain_membership_state <> 'ASSIGNED' AND trust_domain_id IS NULL));

CREATE INDEX ix_org_trust_domain ON org_registry.org_registry_organization (trust_domain_id)
    WHERE trust_domain_id IS NOT NULL;

-- No seed data. Creating MOHCC-ZW here would require declaring its controlling party,
-- which is exactly the open [L] question (§26.2 #1). Trust domains are created through
-- the governed API by an authorised actor, with an audit event, or as test fixtures.
