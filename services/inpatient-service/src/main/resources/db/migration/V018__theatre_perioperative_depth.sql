-- Theatre & Perioperative Depth Closure (WS#6 theatre-depth deferred-seam → real depth).
-- Extends the procedure-episode pipeline (V010–V012) with: OROS PROCEDURE intake linkage,
-- triage/priority, multi-owner readiness snapshots, a signable operative note, cancellation,
-- safety-event linkage (Rito/Madi/asset-registry), and death-in-theatre routing (PCT DeathWorkflow).
-- Owner-routed: this service NEVER becomes the SoR for orders/stock/blood/equipment/death — it
-- records the theatre-side fact + the owner reference.

-- 1. OROS PROCEDURE intake + triage on the episode -------------------------------------------------
ALTER TABLE inpatient.procedure_episode
    ADD COLUMN IF NOT EXISTS oros_order_id        VARCHAR(128),
    ADD COLUMN IF NOT EXISTS triage_priority      VARCHAR(16) NOT NULL DEFAULT 'ELECTIVE',
    ADD COLUMN IF NOT EXISTS theatre_room_id       UUID,
    ADD COLUMN IF NOT EXISTS scrub_nurse_id        VARCHAR(128),
    ADD COLUMN IF NOT EXISTS cancellation_reason   TEXT,
    ADD COLUMN IF NOT EXISTS cancelled_by          VARCHAR(128),
    ADD COLUMN IF NOT EXISTS cancelled_at          TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS emergency_override     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS emergency_override_reason TEXT,
    ADD COLUMN IF NOT EXISTS death_case_ref        VARCHAR(128);

-- triage_priority: IMMEDIATE | EMERGENCY | URGENT | ELECTIVE | DAY_CASE
CREATE INDEX IF NOT EXISTS idx_procedure_episode_oros_order
    ON inpatient.procedure_episode (oros_order_id) WHERE oros_order_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_procedure_episode_triage
    ON inpatient.procedure_episode (tenant_id, triage_priority, status);

-- 2. Multi-owner readiness snapshot (room/team/equipment/pack/blood/anaesthesia) -------------------
-- Each row is one owner-routed readiness check captured at booking/refresh time. We store the
-- owner's answer + blockers; we never fabricate availability.
CREATE TABLE IF NOT EXISTS inpatient.procedure_readiness_check (
    readiness_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    episode_id          UUID NOT NULL REFERENCES inpatient.procedure_episode(episode_id) ON DELETE CASCADE,
    domain              VARCHAR(24) NOT NULL,   -- ROOM | TEAM | EQUIPMENT | PACK | BLOOD | ANAESTHESIA | CONSENT
    owner_service       VARCHAR(32) NOT NULL,   -- inpatient | vashandi | varapi | asset-registry | dura | madi | mvumo
    status              VARCHAR(16) NOT NULL,   -- READY | BLOCKED | UNAVAILABLE | NOT_CHECKED
    owner_ref           VARCHAR(256),           -- owner-side id (reservation, profile, order) when present
    blockers_json       TEXT,                   -- list of {code,message} blockers from the owner
    detail_json         TEXT,                   -- owner-specific detail snapshot
    checked_by          VARCHAR(128),
    checked_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_procedure_readiness_episode
    ON inpatient.procedure_readiness_check (episode_id, domain, checked_at DESC);

-- 3. Signable operative / procedure note -----------------------------------------------------------
-- Extends ProcedureEpisodeDocument with a structured, signable intra-op note that links to the
-- Butano clinical record (Procedure/Encounter) on sign. Draft → SIGNED.
CREATE TABLE IF NOT EXISTS inpatient.procedure_note (
    note_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    episode_id          UUID NOT NULL REFERENCES inpatient.procedure_episode(episode_id) ON DELETE CASCADE,
    status              VARCHAR(16) NOT NULL DEFAULT 'DRAFT',  -- DRAFT | SIGNED | AMENDED
    performed_procedure TEXT,
    findings            TEXT,
    specimens           TEXT,
    implants            TEXT,
    estimated_blood_loss_ml INTEGER,
    complications       TEXT,
    counts_correct      BOOLEAN,
    postop_plan         TEXT,
    note_json           TEXT,                   -- full structured payload
    signed_by           VARCHAR(128),
    signed_provider_id  VARCHAR(128),           -- Varapi provider id of the signing surgeon
    signed_at           TIMESTAMPTZ,
    butano_procedure_ref VARCHAR(256),          -- FHIR Procedure id when written to Butano
    butano_document_ref  VARCHAR(256),          -- FHIR DocumentReference id
    created_by          VARCHAR(128),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_procedure_note_episode
    ON inpatient.procedure_note (episode_id, status);

-- 4. Theatre safety-event linkage (Rito / Madi / asset-registry / death-pathway) -------------------
-- We record the theatre-side fact + the owner the event was ROUTED to. The owner remains SoR for
-- the investigation (Rito quality/AE, Madi transfusion reaction, asset-registry device incident,
-- PCT DeathWorkflow for death-in-theatre).
CREATE TABLE IF NOT EXISTS inpatient.procedure_safety_event (
    safety_event_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    episode_id          UUID NOT NULL REFERENCES inpatient.procedure_episode(episode_id) ON DELETE CASCADE,
    category            VARCHAR(32) NOT NULL,   -- ADVERSE_EVENT | BLOOD_REACTION | DEVICE_INCIDENT | MEDICATION | DEATH | NEAR_MISS | RETAINED_ITEM
    severity            VARCHAR(16),            -- LOW | MODERATE | SEVERE | SENTINEL
    description         TEXT NOT NULL,
    routed_owner        VARCHAR(32) NOT NULL,   -- rito | madi | asset-registry | dura | pct-death | inpatient
    owner_ref           VARCHAR(256),           -- owner-side case/id created
    routed_status       VARCHAR(16) NOT NULL DEFAULT 'ROUTED', -- ROUTED | OWNER_UNAVAILABLE
    reported_by         VARCHAR(128),
    reported_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_procedure_safety_episode
    ON inpatient.procedure_safety_event (episode_id, category, reported_at DESC);
