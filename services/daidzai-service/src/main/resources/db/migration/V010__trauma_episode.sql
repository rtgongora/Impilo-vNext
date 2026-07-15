-- =====================================================================================
-- Daidzai V010 — Canonical trauma-episode correlation spine.
--
-- Architecture decision #1 (trauma pipeline plan): the canonical trauma episode is a THIN
-- correlation spine owned by DAIDZAI. One injured patient = one dai_trauma_episode; each phase
-- owner (pct / inpatient / madi …) keeps its own system-of-record rows and stamps them with a
-- shared trauma_episode_id. DAIDZAI holds:
--   * dai_trauma_episode        — the episode identity + subject + lifecycle status
--   * dai_trauma_episode_phase  — a read-model timeline; one row per phase-owner event
-- It references peer truth by id only and never duplicates a clinical/blood/EMS record.
--
-- Mint is idempotent on (tenant_id, origin_key): DAIDZAI mints on incident triage
-- (origin_key = incident id), PCT mints on ED-first walk-in trauma (origin_key = ed_visit id).
-- The same origin_key always returns the same episode — a retried mint never forks the spine.
-- =====================================================================================

CREATE TABLE daidzai.dai_trauma_episode (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID NOT NULL,
    episode_reference      VARCHAR(48) NOT NULL,
    -- Where this episode was first minted from (idempotency anchor).
    origin_service         VARCHAR(48) NOT NULL,          -- daidzai / pct-service
    origin_kind            VARCHAR(32) NOT NULL,          -- INCIDENT / ED_WALK_IN
    origin_key             VARCHAR(128) NOT NULL,         -- incident id / ed_visit id
    incident_id            UUID,                          -- dai_emergency_incident.id when INCIDENT-origin
    -- Subject anchor (VITO CPID when known; provisional Health ID / temp ref for unknown patient).
    subject_identity_mode  VARCHAR(24) NOT NULL DEFAULT 'UNKNOWN',  -- KNOWN / PROVISIONAL / UNKNOWN
    subject_health_id      VARCHAR(128),
    subject_temp_ref       VARCHAR(128),
    -- Lifecycle: OPEN until disposition closes the episode (W6).
    status                 VARCHAR(24) NOT NULL DEFAULT 'OPEN',     -- OPEN / CLOSED
    current_phase          VARCHAR(32) NOT NULL DEFAULT 'INCIDENT', -- last phase recorded on the timeline
    opened_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at              TIMESTAMPTZ,
    close_reason           VARCHAR(255),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Idempotent dual-entry mint guard: one episode per (tenant, origin_key).
    CONSTRAINT uq_dai_trauma_episode_origin UNIQUE (tenant_id, origin_key)
);

CREATE INDEX idx_dai_trauma_episode_tenant   ON daidzai.dai_trauma_episode (tenant_id);
CREATE INDEX idx_dai_trauma_episode_incident ON daidzai.dai_trauma_episode (incident_id);
CREATE INDEX idx_dai_trauma_episode_subject  ON daidzai.dai_trauma_episode (tenant_id, subject_health_id);

-- Read-model timeline. DAIDZAI records the INCIDENT phase itself on mint; phase owners register
-- their phase (synchronously in W1, and via the outbox drainer in W2) so the episode timeline
-- reconstructs coherently from persisted rows. Idempotent on (episode, owner_service, owner_ref,
-- phase) so a redelivered/retried phase event never doubles a timeline row.
CREATE TABLE daidzai.dai_trauma_episode_phase (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID NOT NULL,
    trauma_episode_id      UUID NOT NULL REFERENCES daidzai.dai_trauma_episode (id) ON DELETE CASCADE,
    phase                  VARCHAR(32) NOT NULL,   -- INCIDENT/DISPATCH/PREHOSPITAL/ED/TRIAGE/RESUS/DIAGNOSTICS/BLOOD/DEFINITIVE/DISPOSITION
    owner_service          VARCHAR(48) NOT NULL,   -- daidzai / pct-service / inpatient-service / madi-service
    owner_ref              VARCHAR(128) NOT NULL,  -- the phase owner's SoR row id (ed_visit id, resuscitation_record id, blood_order id …)
    status                 VARCHAR(48),
    event_type             VARCHAR(96),            -- the source event type, when driven from an outbox event
    payload_json           TEXT,
    occurred_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_dai_trauma_phase UNIQUE (tenant_id, trauma_episode_id, owner_service, owner_ref, phase)
);

CREATE INDEX idx_dai_trauma_phase_episode ON daidzai.dai_trauma_episode_phase (trauma_episode_id, occurred_at);

-- DAIDZAI is the INCIDENT phase owner: stamp the shared id on its own incident row.
ALTER TABLE daidzai.dai_emergency_incident ADD COLUMN IF NOT EXISTS trauma_episode_id UUID;
CREATE INDEX IF NOT EXISTS idx_dai_incident_trauma_episode
    ON daidzai.dai_emergency_incident (trauma_episode_id);
