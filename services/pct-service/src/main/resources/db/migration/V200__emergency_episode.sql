-- The canonical emergency episode.
--
-- Emergency care in this estate was spread across five services with no single object that could
-- answer "is this patient still emergency's responsibility, and who has them now". The ED had a
-- visit, daidzai had a prehospital episode, inpatient had a resuscitation activation, and a patient
-- could pass between them while every individual record looked complete. This table is that object.
--
-- WHY A SIBLING OF ed_visit, NOT A PARENT
--
-- pct.ed_visit.journey_id is already NOT NULL UNIQUE REFERENCES pct_journeys (V012). One ED visit is
-- one journey is one facility visit (care-continuum doctrine CC-0). A ward arrest on day 5 of an
-- admission ALREADY has a journey — so an episode that owned ed_visit and minted one would fork a
-- second journey for a single facility visit and double-count it in every queue, census and
-- indicator built on journeys.
--
-- So the episode attaches to the journey alongside ed_visit, and is a parent of ed_visit only where
-- an ED occurrence exists. A ward, clinic, theatre-recovery, dialysis or teleconsult emergency gets
-- an episode with NO ed_visit and no second journey. That is also why this table carries no zone and
-- no track number: those are ED-unit facts and they stay on ed_visit.
--
-- WHAT THIS TABLE DELIBERATELY DOES NOT HOLD
--
-- Acuity and triage findings live in ed_triage_assessment. Disposition detail lives in
-- ed_disposition. Resuscitation lives in inpatient. The episode composes them; it does not copy
-- them, because two copies of an acuity is two answers to "how sick is this patient".
--
-- The one deliberate exception is the alert clock block below, and it is labelled as such.

CREATE TABLE emergency_episode (
    episode_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    facility_id             UUID NOT NULL,
    episode_reference       VARCHAR(48) NOT NULL,

    -- ── CC-5 ANCHOR ──────────────────────────────────────────────────────────────────────────
    -- Every clinical episode must carry a resolvable PCT anchor. journey_id is nullable ONLY for
    -- the pre-arrival window, where an ambulance has pre-alerted and no facility contact has
    -- happened yet. anchor_resolved_at records the moment it became resolvable, and the CHECK below
    -- makes the orphan state expressible but not persistable past that point.
    journey_id              VARCHAR(26) REFERENCES pct_journeys(journey_id),
    encounter_id            BIGINT REFERENCES pct_encounters(id),
    -- daidzai's cross-facility correlator. Deliberately NOT a foreign key: it lives in another
    -- service's schema, and the prehospital window is exactly when it exists and our anchor does not.
    trauma_episode_id       UUID,
    anchor_resolved_at      TIMESTAMPTZ,

    -- ── IDENTITY (care-first) ────────────────────────────────────────────────────────────────
    -- An unidentified patient is treated first and identified later. subject_cpid is an ATTRIBUTE,
    -- never the anchor: every clinical row in this pack hangs off episode_id, so a later VITO merge
    -- repoints an attribute and never re-parents a record.
    subject_cpid            VARCHAR(64),
    identity_mode           VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    emergency_case_id       UUID,
    identity_resolved_at    TIMESTAMPTZ,

    -- ── ENTRY ────────────────────────────────────────────────────────────────────────────────
    -- Emergencies do not only begin at an ED door. The route is recorded because it changes what
    -- must happen next: an ambulance arrival has a pre-alert to reconcile, a ward deterioration has
    -- a source encounter to preserve, a mass-casualty arrival has an incident to join.
    entry_route             VARCHAR(40) NOT NULL,
    entry_source_service    VARCHAR(48),
    entry_source_ref        VARCHAR(128),
    entry_context_json      JSONB,
    arrived_at              TIMESTAMPTZ NOT NULL,

    episode_class           VARCHAR(24) NOT NULL DEFAULT 'UNDIFFERENTIATED',
    sensitive               BOOLEAN NOT NULL DEFAULT FALSE,

    state                   VARCHAR(32) NOT NULL DEFAULT 'OPEN_UNTRIAGED',

    -- ── LOCATION, AS A CLOCK NOT A FIELD ─────────────────────────────────────────────────────
    -- location_confirmed_at is what makes "we have lost this patient" detectable. A nullable
    -- location field alone is null forever and alerts on nothing; a stale confirmation timestamp
    -- turns the patient wheeled to CT and forgotten into an overdue check.
    current_location        VARCHAR(120),
    location_confirmed_at   TIMESTAMPTZ,

    -- ── DERIVED PROJECTIONS — declared, reconciled, never the amendment target ────────────────
    -- These duplicate facts owned elsewhere, and that is a deliberate, narrow exception. The six
    -- required emergency alerts are time-based sweeps over every open episode; a sweep that had to
    -- read peer services over HTTP would stop firing exactly when a peer degraded, which is when it
    -- is most needed. Their systems of record are named per column and a reconciler re-derives them.
    triaged_at              TIMESTAMPTZ,
    target_first_contact_at TIMESTAMPTZ,
    first_contact_at        TIMESTAMPTZ,
    reassessment_due_at     TIMESTAMPTZ,
    last_observation_at     TIMESTAMPTZ,

    -- ── CLOSURE ──────────────────────────────────────────────────────────────────────────────
    -- Emergency responsibility ends on an accepted handover and on nothing else. handover_id is set
    -- only when an accepting party has written its own record id; see V209 (emergency_handover).
    handover_id             UUID,
    outcome                 VARCHAR(32),
    closed_at               TIMESTAMPTZ,

    -- Episodes are never deleted. A duplicate is merged and keeps its row, so a correlation that
    -- pointed at it still resolves and an audit trail written against it still reads.
    merged_into_id          UUID REFERENCES emergency_episode(episode_id),

    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by              VARCHAR(128),

    CONSTRAINT chk_ee_identity_mode CHECK (identity_mode IN ('UNKNOWN', 'PROVISIONAL', 'KNOWN')),

    CONSTRAINT chk_ee_entry_route CHECK (entry_route IN (
        'WALK_IN', 'AMBULANCE', 'STATUTORY_SERVICE', 'INTERFACILITY_REFERRAL', 'CHW_REFERRAL',
        'PRIVATE_VEHICLE', 'WORKPLACE_OR_SCHOOL', 'MASS_CASUALTY', 'IN_FACILITY_DETERIORATION',
        'CLINIC_ESCALATION', 'TELECONSULT_ESCALATION', 'PUBLIC_REQUEST', 'DIRECT_SPECIALIST',
        'UNKNOWN_OR_UNRESPONSIVE')),

    CONSTRAINT chk_ee_episode_class CHECK (episode_class IN (
        'TRAUMA', 'MEDICAL', 'OBSTETRIC', 'PAEDIATRIC', 'NEONATAL', 'TOXICOLOGY', 'PSYCHIATRIC',
        'ENVIRONMENTAL', 'BURNS', 'MCI', 'UNDIFFERENTIATED')),

    CONSTRAINT chk_ee_state CHECK (state IN (
        'PRE_ARRIVAL', 'OPEN_UNTRIAGED', 'OPEN_IN_CARE', 'OPEN_AWAITING_ACCEPTANCE',
        'CLOSED_HANDED_OVER', 'CLOSED_DISCHARGED', 'CLOSED_DIED',
        'CLOSED_LEFT_BEFORE_ASSESSMENT', 'CLOSED_LEFT_AGAINST_ADVICE', 'CLOSED_ABSCONDED',
        'CLOSED_DECLINED_CARE', 'MERGED')),

    -- CC-5, enforced rather than reviewed. Past the pre-arrival window an episode must resolve to a
    -- PCT anchor. An episode that reached a facility without one is the orphan the doctrine rejects.
    CONSTRAINT chk_ee_anchor_resolved CHECK (
        state = 'PRE_ARRIVAL' OR state = 'MERGED' OR journey_id IS NOT NULL),

    CONSTRAINT chk_ee_anchor_timestamp CHECK (
        (journey_id IS NULL AND anchor_resolved_at IS NULL)
        OR (journey_id IS NOT NULL AND anchor_resolved_at IS NOT NULL)),

    -- A handed-over episode must name the handover that closed it. Without this, "closed" and
    -- "somebody was asked" are indistinguishable in the data, which is the failure this pack exists
    -- to prevent: a referral, admission request or theatre request must not end emergency
    -- responsibility on its own.
    CONSTRAINT chk_ee_handover_required CHECK (
        state <> 'CLOSED_HANDED_OVER' OR handover_id IS NOT NULL),

    -- Every closure states an outcome and when. A closed episode with no outcome would be counted
    -- as a completed one by every indicator built on this table.
    CONSTRAINT chk_ee_closure_recorded CHECK (
        state NOT LIKE 'CLOSED%' OR (outcome IS NOT NULL AND closed_at IS NOT NULL)),

    CONSTRAINT chk_ee_merged_target CHECK (
        state <> 'MERGED' OR merged_into_id IS NOT NULL),

    -- A known identity has a cpid; an unknown one does not claim to.
    CONSTRAINT chk_ee_identity_resolution CHECK (
        (identity_mode = 'KNOWN' AND subject_cpid IS NOT NULL)
        OR identity_mode IN ('UNKNOWN', 'PROVISIONAL'))
);

-- Idempotent mint. The same ambulance pre-alert replayed, or a walk-in registered twice by two
-- clerks, must not produce two episodes for one patient — and on this estate replay is normal, not
-- exceptional. Partial, because a walk-in has no source ref to key on.
CREATE UNIQUE INDEX uq_emergency_episode_entry_source
    ON emergency_episode (tenant_id, entry_route, entry_source_ref)
    WHERE entry_source_ref IS NOT NULL;

CREATE UNIQUE INDEX uq_emergency_episode_reference
    ON emergency_episode (tenant_id, episode_reference);

-- The operational board: open episodes at a facility, most recent first.
CREATE INDEX idx_emergency_episode_board
    ON emergency_episode (tenant_id, facility_id, state, arrived_at DESC);

-- The alert sweeps. Partial so they scan only open episodes rather than the whole history.
CREATE INDEX idx_emergency_episode_first_contact_due
    ON emergency_episode (tenant_id, target_first_contact_at)
    WHERE state IN ('OPEN_UNTRIAGED', 'OPEN_IN_CARE') AND first_contact_at IS NULL;

CREATE INDEX idx_emergency_episode_reassessment_due
    ON emergency_episode (tenant_id, reassessment_due_at)
    WHERE state IN ('OPEN_UNTRIAGED', 'OPEN_IN_CARE') AND reassessment_due_at IS NOT NULL;

CREATE INDEX idx_emergency_episode_location_stale
    ON emergency_episode (tenant_id, location_confirmed_at)
    WHERE state IN ('OPEN_UNTRIAGED', 'OPEN_IN_CARE', 'OPEN_AWAITING_ACCEPTANCE');

CREATE INDEX idx_emergency_episode_subject
    ON emergency_episode (tenant_id, subject_cpid)
    WHERE subject_cpid IS NOT NULL;

CREATE INDEX idx_emergency_episode_journey
    ON emergency_episode (tenant_id, journey_id)
    WHERE journey_id IS NOT NULL;

CREATE INDEX idx_emergency_episode_trauma
    ON emergency_episode (tenant_id, trauma_episode_id)
    WHERE trauma_episode_id IS NOT NULL;

COMMENT ON TABLE emergency_episode IS
    'The canonical emergency episode: facility-scoped, journey-anchored, and a sibling of ed_visit '
    'rather than a parent of it. Cross-facility continuity stays with daidzai''s trauma_episode '
    'spine (CC-4). Emergency responsibility ends only on an accepted handover.';

COMMENT ON COLUMN emergency_episode.subject_cpid IS
    'An ATTRIBUTE, never the anchor. Emergency clinical rows hang off episode_id, so a VITO merge '
    'repoints this and never re-parents a record.';

COMMENT ON COLUMN emergency_episode.triaged_at IS
    'DERIVED PROJECTION. System of record: pct.ed_triage_assessment. Duplicated here only so the '
    'alert sweeps can be indexed; reconciled, never the amendment target.';
COMMENT ON COLUMN emergency_episode.target_first_contact_at IS
    'DERIVED PROJECTION from the triage priority plus national time targets (ENGINEERING_SEED until '
    'MoHCC ratifies them — the IITT FAQ is explicit that time-to-care per colour is for facilities '
    'to determine).';
COMMENT ON COLUMN emergency_episode.first_contact_at IS
    'DERIVED PROJECTION. System of record: the first clinician-authored encounter or observation.';
COMMENT ON COLUMN emergency_episode.reassessment_due_at IS
    'DERIVED PROJECTION from the triage result''s monitoring requirement.';
COMMENT ON COLUMN emergency_episode.last_observation_at IS
    'DERIVED PROJECTION. System of record: pct.pct_observations.';

COMMENT ON COLUMN emergency_episode.location_confirmed_at IS
    'When the location was last CONFIRMED, not when it was last guessed. Staleness here is what '
    'makes a lost patient detectable; a location field alone is null forever and alerts on nothing.';

COMMENT ON COLUMN emergency_episode.merged_into_id IS
    'Episodes are never deleted. A duplicate is merged and keeps its row so correlations still '
    'resolve and audit written against it still reads.';
