-- vashandi-workforce-service V007 — theatre case teams (Wave 2 elective theatre)
-- VASHANDI owns the workforce truth. A theatre case team references a scheduling
-- session (session_ref) and optionally a specific OR list item. Each member is
-- validated to hold a covering vsh_shift (soft-validation, mirroring the
-- virtual-pool duty gate) — accepted-and-flagged, never blocking assembly.

CREATE TABLE vashandi.vsh_theatre_team (
    id              UUID            PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    session_ref     UUID            NOT NULL,       -- reference to scheduling.theatre_session
    or_list_item_ref UUID,                          -- optional reference to a specific OR item
    status          VARCHAR(24)     NOT NULL DEFAULT 'DRAFT',  -- DRAFT | CONFIRMED | STOOD_DOWN
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT chk_theatre_team_status CHECK (status IN ('DRAFT','CONFIRMED','STOOD_DOWN'))
);

CREATE INDEX idx_vsh_theatre_team_session
    ON vashandi.vsh_theatre_team (tenant_id, session_ref, status);

CREATE TABLE vashandi.vsh_theatre_team_member (
    id                  UUID            PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    team_id             UUID            NOT NULL REFERENCES vashandi.vsh_theatre_team(id) ON DELETE CASCADE,
    role                VARCHAR(32)     NOT NULL,
        -- SURGEON | ASSISTANT_SURGEON | ANAESTHETIST | SCRUB_NURSE | RECOVERY_NURSE | PORTER | RUNNER
    workforce_profile_id UUID           NOT NULL,
    shift_id            UUID,           -- covering vsh_shift reference (soft-validated)
    confirmed           BOOLEAN         NOT NULL DEFAULT FALSE,
    shift_coverage      VARCHAR(24)     NOT NULL DEFAULT 'UNVALIDATED',  -- COVERED | NO_SHIFT | UNVALIDATED
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT chk_theatre_member_role CHECK (role IN
        ('SURGEON','ASSISTANT_SURGEON','ANAESTHETIST','SCRUB_NURSE','RECOVERY_NURSE','PORTER','RUNNER')),
    CONSTRAINT uq_theatre_team_member UNIQUE (team_id, role, workforce_profile_id)
);

CREATE INDEX idx_vsh_theatre_team_member_team
    ON vashandi.vsh_theatre_team_member (team_id);
