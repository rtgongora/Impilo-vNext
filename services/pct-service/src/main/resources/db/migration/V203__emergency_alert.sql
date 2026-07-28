-- Emergency alerts: the mechanism by which a patient cannot quietly disappear (W5).
--
-- Six time-based conditions over open emergency episodes. All six read clocks that
-- V200__emergency_episode.sql already declares as DERIVED PROJECTIONS with named systems of record,
-- so this wave adds NO new clock and no new duplicated fact — it adds the alert that reads them.
-- The projections exist precisely so a sweep never has to read a peer over HTTP: an alert that
-- depends on a cross-service call stops firing exactly when a peer degrades, which is when it is
-- most needed.
--
-- Shaped deliberately on inpatient.deterioration_escalation (V016): same status vocabulary
-- (RAISED -> ACKNOWLEDGED -> RESPONDED -> CLOSED, with OVERDUE), same response_due_at clock, same
-- rito_case_ref as a LINK to a safety case this service does not own. Two escalation models with
-- different vocabularies in one estate would make a cross-surface responder view impossible.
--
-- THE ANTI-NOISE CONSTRAINT IS A SAFETY CONSTRAINT, not tidiness. Alert noise is itself a way
-- patients disappear: a board showing forty open alerts is read as wallpaper and the real one is
-- missed. Hence the partial unique below — one open alert per (episode, type), so a sweep that runs
-- every minute re-raises nothing. Re-raising after closure IS allowed, because a second episode of
-- the same failure is a real event.

-- ---------------------------------------------------------------------------
-- First: give the acceptance clock a column of its own.
--
-- ACCEPTANCE_OVERDUE needs to know how long an episode has been waiting for an accepting party.
-- The obvious proxy — emergency_episode.updated_at — is WRONG in the unsafe direction: it is
-- refreshed by ANY write, so recording an unrelated observation would silently push the acceptance
-- alarm further away, and a busy episode would never alert at all. The clock that measures a
-- handover must only be reset by a handover.
--
-- Same band, same owner, and V203 > V200, so this ALTER is ordering-safe on a fresh boot.
ALTER TABLE pct.emergency_episode
    ADD COLUMN awaiting_acceptance_since TIMESTAMPTZ;

COMMENT ON COLUMN pct.emergency_episode.awaiting_acceptance_since IS
    'Set when the episode enters OPEN_AWAITING_ACCEPTANCE, cleared when it leaves. Deliberately NOT '
    'updated_at: that is refreshed by any write, so an unrelated update would delay the '
    'ACCEPTANCE_OVERDUE alert — a clock measuring a handover must only be reset by a handover.';

CREATE TABLE pct.emergency_alert (
    alert_id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL,
    facility_id         UUID,

    -- The episode is the durable spine. No ON DELETE CASCADE, deliberately and in both directions:
    -- an episode is never deleted (V200 merges instead), and an alert must outlive any attempt to
    -- tidy one away. An alert that vanishes with its subject is the exact failure this table exists
    -- to prevent.
    episode_id          UUID        NOT NULL REFERENCES pct.emergency_episode(episode_id),

    -- Denormalised from the episode so the responder board and the sweep never need a join, and so
    -- an alert stays readable if identity is later repointed. subject_cpid is nullable because an
    -- unidentified emergency patient is the normal case at the door, not an error.
    subject_cpid        VARCHAR(64),

    alert_type          VARCHAR(32) NOT NULL,
    severity            VARCHAR(16) NOT NULL DEFAULT 'HIGH',
    status              VARCHAR(20) NOT NULL DEFAULT 'RAISED',

    -- Why this fired, in the sweep's own words, with the clock reading that triggered it. A responder
    -- must be able to see the evidence without re-deriving it, and an alert whose reason cannot be
    -- reconstructed later is not auditable.
    reason              TEXT        NOT NULL,
    detected_value_at   TIMESTAMPTZ,

    raised_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    raised_by           VARCHAR(128) NOT NULL DEFAULT 'EmergencyAlertSweepJob',
    response_due_at     TIMESTAMPTZ NOT NULL,

    acknowledged_by     VARCHAR(128),
    acknowledged_at     TIMESTAMPTZ,
    responded_by        VARCHAR(128),
    responded_at        TIMESTAMPTZ,
    response_note       TEXT,

    closed_at           TIMESTAMPTZ,
    close_reason        VARCHAR(64),

    -- Link, not ownership: rito owns the safety case. An overdue acceptance raises one.
    rito_case_ref       VARCHAR(128),

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_ea_type CHECK (alert_type IN (
        -- 1. Triaged but nobody has made contact by the target. Reads target_first_contact_at.
        'TRIAGE_OVERDUE',
        -- 2. A re-triage interval has elapsed. Reads reassessment_due_at.
        'REASSESSMENT_DUE',
        -- 3. No observation recorded for too long. Reads last_observation_at.
        'OBSERVATION_OVERDUE',
        -- 4. Nobody has confirmed where the patient IS. Fires on STALENESS of location_confirmed_at,
        --    not on a null location — the patient wheeled to CT and forgotten is the case that a
        --    nullable location field can never detect.
        'LOCATION_UNKNOWN',
        -- 5. OPEN_AWAITING_ACCEPTANCE for too long. No timeout ever discharges responsibility, so
        --    this escalates and opens a rito case; it never closes the episode.
        'ACCEPTANCE_OVERDUE',
        -- 6. The reconciliation sweep re-derived an open episode against its peers and disagreed.
        --    An event-only design fails silently exactly when the broker fails; this is the check
        --    that notices the silence.
        'CORRELATION_DIVERGENCE')),

    CONSTRAINT chk_ea_severity CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),

    CONSTRAINT chk_ea_status CHECK (status IN (
        'RAISED', 'ACKNOWLEDGED', 'RESPONDED', 'OVERDUE', 'CLOSED')),

    -- Value-or-reason, in the V057__observations.sql style: a transition is not claimable without
    -- the actor and time that made it. Half-recorded acknowledgement reads as a response nobody gave.
    CONSTRAINT chk_ea_ack_pair CHECK (
        (acknowledged_by IS NULL) = (acknowledged_at IS NULL)),
    CONSTRAINT chk_ea_responded_pair CHECK (
        (responded_by IS NULL) = (responded_at IS NULL)),

    -- A status cannot claim a transition that was never recorded.
    CONSTRAINT chk_ea_ack_recorded CHECK (
        status NOT IN ('ACKNOWLEDGED', 'RESPONDED') OR acknowledged_at IS NOT NULL),
    CONSTRAINT chk_ea_responded_recorded CHECK (
        status <> 'RESPONDED' OR responded_at IS NOT NULL),
    CONSTRAINT chk_ea_closed_recorded CHECK (
        status <> 'CLOSED' OR (closed_at IS NOT NULL AND close_reason IS NOT NULL))
);

-- ONE OPEN ALERT PER (episode, type). The sweep runs on a schedule and must be able to run every
-- minute without producing a second row for a condition that is already raised and visible.
CREATE UNIQUE INDEX uq_emergency_alert_open
    ON pct.emergency_alert (tenant_id, episode_id, alert_type)
    WHERE status IN ('RAISED', 'ACKNOWLEDGED', 'OVERDUE');

-- The responder board: open alerts for a facility, most urgent clock first.
CREATE INDEX idx_emergency_alert_open_board
    ON pct.emergency_alert (tenant_id, facility_id, response_due_at)
    WHERE status IN ('RAISED', 'ACKNOWLEDGED', 'OVERDUE');

-- The overdue sweep: anything past its response_due_at that nobody has responded to.
CREATE INDEX idx_emergency_alert_overdue
    ON pct.emergency_alert (tenant_id, response_due_at)
    WHERE status IN ('RAISED', 'ACKNOWLEDGED');

CREATE INDEX idx_emergency_alert_episode
    ON pct.emergency_alert (tenant_id, episode_id, raised_at DESC);

COMMENT ON TABLE pct.emergency_alert IS
    'Time-based safety alerts over open emergency episodes. Reads the DERIVED PROJECTION clocks on '
    'emergency_episode (V200) rather than calling peers, so an alert keeps firing when a peer is '
    'degraded. One open alert per (episode, type) by partial unique index — alert noise is itself a '
    'way patients disappear.';
COMMENT ON COLUMN pct.emergency_alert.rito_case_ref IS
    'LINK to a rito safety case; rito owns it. Set when an ACCEPTANCE_OVERDUE escalates. No timeout '
    'may discharge emergency responsibility, so this escalates and never closes the episode.';
COMMENT ON COLUMN pct.emergency_alert.detected_value_at IS
    'The clock reading that triggered the raise (e.g. the stale location_confirmed_at), so a '
    'responder sees the evidence without re-deriving it and an auditor can reconstruct the decision.';
