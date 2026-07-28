-- support-service: first-class technical/operational support context model.
--
-- Technical support has never had a scoped context — the nearest existing
-- thing was broad administrative roles or facility impersonation. This adds
-- a real, scoped assignment model so "Facility ICT Support — Facility X",
-- "District Support — District Y", "National Integration Support" etc. are
-- real, resolvable, revocable contexts, never a universal TECH_SUPPORT role
-- that opens every facility.
--
-- Ownership split (doctrine): VASHANDI stays authoritative for the person's
-- employment/posting/team membership (see vashandi V010, which links a
-- workforce assignment to a sup_support_team here); the organisation
-- registry stays authoritative for the support organisation; support-service
-- owns support cases (sup_tickets, already real) and case-linked elevated
-- access; TUSO supplies the facilities/assets being supported.
--
-- The hard boundary this model enforces: ordinary Support Mode NEVER reads
-- clinical data. Any exceptional diagnostic access is a distinct,
-- case-linked, approved, time-limited, auditable grant
-- (sup_support_elevated_access_grant) — never implied by holding a support
-- assignment.

CREATE TABLE sup_support_team (
    team_id             UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    team_code           VARCHAR(64)     NOT NULL,
    name                VARCHAR(255)    NOT NULL,
    support_tier        VARCHAR(32)     NOT NULL DEFAULT 'TIER_1',
        -- TIER_1 | TIER_2 | TIER_3 | BIOMEDICAL | DEPLOYMENT
    supported_service   VARCHAR(128),
        -- the platform/service this team supports, e.g. a service registry code
    geographic_scope    VARCHAR(128),
        -- a WGV jurisdiction_code, when the team's reach is geographic
    facility_scope_ref  VARCHAR(128),
        -- a TUSO facility id/uuid, when the team's reach is one facility
    organisation_scope  VARCHAR(128),
        -- an org-registry organisation id, when the team supports one org
    environment_scope   VARCHAR(32),
        -- PRODUCTION | PREVIEW | ALL — nullable when not environment-scoped
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    metadata_json       TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_sup_support_team_code UNIQUE (tenant_id, team_code)
);
CREATE INDEX idx_sup_support_team_tenant ON sup_support_team (tenant_id);
CREATE INDEX idx_sup_support_team_active ON sup_support_team (tenant_id) WHERE active;

-- A person's scoped support posting. VASHANDI's vsh_workforce_assignment
-- (V010) points here via support_team_id — this table records the support
-- authority itself (tier, scope, on-call status), Vashandi records the
-- employment/posting fact.
CREATE TABLE sup_support_assignment (
    assignment_id       UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID            NOT NULL,
    team_id              UUID            NOT NULL REFERENCES sup_support_team(team_id),
    person_health_id     VARCHAR(128)    NOT NULL,
    support_role         VARCHAR(64)     NOT NULL DEFAULT 'SUPPORT_OFFICER',
        -- SUPPORT_OFFICER | SENIOR_SUPPORT_OFFICER | TEAM_LEAD | BIOMEDICAL_ENGINEER
    on_call              BOOLEAN         NOT NULL DEFAULT FALSE,
    status                VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
        -- ACTIVE | SUSPENDED | ENDED
    start_date            DATE            NOT NULL DEFAULT CURRENT_DATE,
    end_date              DATE,
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by            VARCHAR(255),
    updated_by            VARCHAR(255)
);
CREATE INDEX idx_sup_support_assignment_team ON sup_support_assignment (team_id);
CREATE INDEX idx_sup_support_assignment_person ON sup_support_assignment (person_health_id);
CREATE INDEX idx_sup_support_assignment_active
    ON sup_support_assignment (person_health_id) WHERE status = 'ACTIVE';

-- The closed, positive vocabulary of what an ordinary support assignment
-- permits. Absence from this table means the action is not permitted —
-- support access is enumerated, not implied by role name.
CREATE TABLE sup_support_permitted_action (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    team_id         UUID            NOT NULL REFERENCES sup_support_team(team_id),
    action_code     VARCHAR(128)    NOT NULL,
        -- e.g. VIEW_SERVICE_HEALTH | VIEW_CONNECTIVITY | VIEW_INTEGRATION_STATUS |
        -- VIEW_DEVICE_STATUS | VIEW_DEPLOYMENT_STATUS | RESTART_SERVICE |
        -- VIEW_ACCESS_REQUESTS | VIEW_SUPPORT_CASES — never a clinical-data action
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_sup_permitted_action UNIQUE (team_id, action_code)
);
CREATE INDEX idx_sup_permitted_action_team ON sup_support_permitted_action (team_id);

-- Case-linked, time-bound, approved elevation to diagnostic access. This is
-- the ONLY path by which a support session may ever touch anything beyond
-- platform/config/device/integration signals, and it is deliberately a
-- distinct grant, not a broader team permission.
CREATE TABLE sup_support_elevated_access_grant (
    grant_id            UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    support_assignment_id UUID          NOT NULL REFERENCES sup_support_assignment(assignment_id),
    ticket_id           UUID            NOT NULL REFERENCES sup_tickets(ticket_id),
    stated_purpose       TEXT            NOT NULL,
    minimum_scope        TEXT            NOT NULL,
        -- the narrowest description of what is being elevated, e.g.
        -- "read-only sync-log access for encounter batch #4471"
    requested_by          VARCHAR(255)    NOT NULL,
    approved_by            VARCHAR(255),
    status                  VARCHAR(32)     NOT NULL DEFAULT 'PENDING_APPROVAL',
        -- PENDING_APPROVAL | ACTIVE | EXPIRED | REVOKED | DENIED
    granted_at              TIMESTAMPTZ,
    expires_at              TIMESTAMPTZ,
    revoked_at              TIMESTAMPTZ,
    revoked_by              VARCHAR(255),
    revocation_reason       TEXT,
    visible_indicator        BOOLEAN         NOT NULL DEFAULT TRUE,
        -- must always be true in production; the UI must show an active
        -- elevation banner while status='ACTIVE'
    created_at               TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT chk_sup_elevated_status
        CHECK (status IN ('PENDING_APPROVAL','ACTIVE','EXPIRED','REVOKED','DENIED')),
    CONSTRAINT chk_sup_elevated_expiry_present
        CHECK (status NOT IN ('ACTIVE') OR expires_at IS NOT NULL)
);
CREATE INDEX idx_sup_elevated_grant_assignment ON sup_support_elevated_access_grant (support_assignment_id);
CREATE INDEX idx_sup_elevated_grant_ticket ON sup_support_elevated_access_grant (ticket_id);
CREATE INDEX idx_sup_elevated_grant_active
    ON sup_support_elevated_access_grant (support_assignment_id) WHERE status = 'ACTIVE';

COMMENT ON TABLE sup_support_assignment IS
    'Scoped technical/operational support context (support-service SoR). Never '
    'a universal support role — every row is scoped to a team whose reach '
    '(facility/geography/organisation/environment) is explicit. Clinical data '
    'access requires a distinct, case-linked sup_support_elevated_access_grant.';
