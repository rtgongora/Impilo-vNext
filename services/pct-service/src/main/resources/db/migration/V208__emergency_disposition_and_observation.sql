-- Episode-level disposition + observation/short-stay (W9b) — the mandatory-content gate + the
-- genuinely-absent observation capability the plan calls for.
--
-- NOT A DUPLICATE OF ed_disposition (V012): that table is ed_visit-scoped (visit_id NOT NULL
-- UNIQUE) and carries ED clinical coding — diagnosis codes, procedure codes, a discharge summary.
-- Per R2, not every emergency episode has an ed_visit (a ward arrest gets a disposition too), so a
-- disposition record keyed on visit_id cannot cover this pack's full entry-route surface.
-- emergency_disposition is the EPISODE-level routing/closure record — a different, narrower purpose
-- (where did this episode go, and does the mandatory-content gate hold) that coexists with
-- ed_disposition rather than replacing it. An ED occurrence may reasonably have both.

CREATE TABLE emergency_disposition (
    disposition_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID        NOT NULL,
    episode_id            UUID        NOT NULL UNIQUE REFERENCES emergency_episode (episode_id),

    -- 15 dispositions, matching the plan's own count. Distinct from EmergencyEpisodeState's 7
    -- CLOSED_* states: the episode FSM tracks the coarse lifecycle category, this record carries the
    -- finer clinical detail within it (e.g. CLOSED_HANDED_OVER covers ADMITTED_WARD, ADMITTED_ICU,
    -- ADMITTED_HDU, TO_THEATRE, TO_MENTAL_HEALTH and TRANSFERRED_OUT alike).
    disposition_type      VARCHAR(32) NOT NULL,

    -- THE MANDATORY-CONTENT GATE (R12's third mandatory gate, alongside triage priority and handover
    -- acceptance). Universal for every disposition, enforced below: a disposition with no reason and
    -- no recorded actor is not a disposition, it is a status flip nobody can audit later.
    disposition_reason    TEXT,
    disposed_by           VARCHAR(128),
    disposed_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Required only for the subset of types where a destination is the clinically essential fact —
    -- an admission or transfer with no stated destination is exactly the kind of half-recorded
    -- disposition this gate exists to refuse.
    destination            VARCHAR(256),

    -- Required only for DIED — the circumstances of a death in the ED must be on the record, not
    -- inferable from the bare fact of the disposition type.
    cause_or_circumstances TEXT,

    -- Required only for the three unplanned-departure types (LEFT_BEFORE_ASSESSMENT,
    -- LEFT_AGAINST_ADVICE, ABSCONDED) — when the patient was last actually seen is the one fact that
    -- makes an unplanned departure searchable/auditable later; "gone" with no last-seen time is not.
    last_seen_at            TIMESTAMPTZ,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_ed_disp_type CHECK (disposition_type IN (
        'DISCHARGED_HOME', 'DISCHARGED_WITH_FOLLOWUP', 'REFERRED_OUTPATIENT',
        'ADMITTED_WARD', 'ADMITTED_ICU', 'ADMITTED_HDU',
        'TO_THEATRE', 'TRANSFERRED_OUT', 'TO_MENTAL_HEALTH', 'OBSERVATION',
        'DIED', 'LEFT_BEFORE_ASSESSMENT', 'LEFT_AGAINST_ADVICE', 'ABSCONDED', 'DECLINED_CARE')),

    -- The universal mandatory-content gate: every disposition states who, when and why.
    CONSTRAINT chk_ed_disp_mandatory_content CHECK (
        disposed_by IS NOT NULL AND btrim(disposed_by) <> ''
        AND disposition_reason IS NOT NULL AND btrim(disposition_reason) <> ''),

    CONSTRAINT chk_ed_disp_destination_required CHECK (
        disposition_type NOT IN (
            'ADMITTED_WARD', 'ADMITTED_ICU', 'ADMITTED_HDU', 'TO_THEATRE',
            'TRANSFERRED_OUT', 'TO_MENTAL_HEALTH', 'REFERRED_OUTPATIENT')
        OR (destination IS NOT NULL AND btrim(destination) <> '')),

    CONSTRAINT chk_ed_disp_death_circumstances_required CHECK (
        disposition_type <> 'DIED'
        OR (cause_or_circumstances IS NOT NULL AND btrim(cause_or_circumstances) <> '')),

    CONSTRAINT chk_ed_disp_last_seen_required CHECK (
        disposition_type NOT IN ('LEFT_BEFORE_ASSESSMENT', 'LEFT_AGAINST_ADVICE', 'ABSCONDED')
        OR last_seen_at IS NOT NULL)
);

CREATE INDEX idx_emergency_disposition_tenant_type
    ON emergency_disposition (tenant_id, disposition_type);

COMMENT ON TABLE emergency_disposition IS
    'The episode-level disposition record and the mandatory-content gate (R12''s third mandatory '
    'gate, with triage priority and handover acceptance). One per episode (UNIQUE episode_id) — a '
    'disposition is recorded once, corrected by amendment at the application layer if needed, not by '
    'a second competing row.';
COMMENT ON CONSTRAINT chk_ed_disp_mandatory_content ON emergency_disposition IS
    'Every disposition states who, when and why. A disposition with none of these is a status flip '
    'nobody can audit later — the exact failure the "mandatory content" gate exists to refuse.';

-- ---------------------------------------------------------------------------
-- Observation / short-stay. Genuinely absent before this migration — not an admission (no ward bed,
-- no admission record) and not a discharge (the patient has not left). Anchored on the episode; an
-- OBSERVATION disposition is what starts one, but the tables are not FK-coupled to each other in
-- either direction — a clinician can start an observation stay before formally recording the
-- disposition that triggered it, and the two are correlated by episode_id, not by a hard link.
-- ---------------------------------------------------------------------------
CREATE TABLE emergency_observation_stay (
    stay_id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID        NOT NULL,
    episode_id             UUID        NOT NULL REFERENCES emergency_episode (episode_id),

    started_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_by             VARCHAR(128) NOT NULL,

    -- Informational, not derived from CKP content: per-condition minimum-observation-minutes content
    -- (disposition_options_json.minimum_observation_minutes) does not exist yet in this pack's CKP
    -- schema. Recording it here as a plain nullable field is honest about that — a value a caller
    -- supplies, not a value this table computes or enforces.
    min_observation_minutes INTEGER,

    ended_at                TIMESTAMPTZ,
    ended_by                VARCHAR(128),
    outcome                 VARCHAR(32),

    notes                   TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_eos_outcome CHECK (
        outcome IS NULL OR outcome IN ('DISCHARGED', 'ADMITTED', 'ESCALATED_TO_RESUS', 'ABSCONDED')),

    -- Value-or-reason: an ended stay states who ended it and how it resolved.
    CONSTRAINT chk_eos_end_pair CHECK (
        ended_at IS NULL OR (ended_by IS NOT NULL AND outcome IS NOT NULL))
);

-- One OPEN observation stay per episode. A second concurrent stay for the same episode would be a
-- duplicate submission, not two real periods of observation — the same anti-noise reasoning as
-- emergency_handover's pending-request slot (V204), applied here because two open stays would make
-- "how long has this patient actually been under observation" ambiguous.
CREATE UNIQUE INDEX uq_emergency_observation_stay_open
    ON emergency_observation_stay (tenant_id, episode_id)
    WHERE ended_at IS NULL;

CREATE INDEX idx_emergency_observation_stay_episode
    ON emergency_observation_stay (tenant_id, episode_id, started_at DESC);

COMMENT ON TABLE emergency_observation_stay IS
    'Observation / short-stay — genuinely absent before this migration. Not an admission (no ward '
    'bed, no admission record) and not a discharge (the patient has not left). One open stay per '
    'episode at a time.';
