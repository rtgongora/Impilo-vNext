-- The medical episode (PCT).
--
-- A problem list says what is wrong with someone. It cannot say what is being *done about it over
-- time*, and that is the object adult medicine is organised around: a course of care with a
-- responsible service, a start, an end, and a set of problems it exists to manage. Hypertension
-- diagnosed in 2019 and managed continuously since is one episode spanning dozens of contacts;
-- readmission for decompensated heart failure is a second episode of an existing problem, not a
-- second problem.
--
-- WHAT THIS IS NOT.
--
-- It is not an encounter. An encounter is one contact; this spans them.
--
-- It is not `pct.emergency_episode`, which the emergency lane owns and which is the canonical
-- record of ONE presentation to emergency care. That distinction is a frozen cross-pack contract
-- (docs/registry/iatg-adult-medicine-leases.md §4): an emergency episode is a presentation, a
-- medical episode is the arc of a problem across contacts, facilities and years. This table LINKS
-- to it via a nullable emergency_episode_id and never replaces, contains or re-derives it. No
-- foreign key, because the emergency lane has not created that table yet and a hard constraint
-- would couple our deploy order; the column is documented as a soft reference and validated in
-- application code once the table exists.
--
-- It is not a journey. `journey_id` in this codebase means one facility visit
-- (care-continuum-doctrine). An episode gathers journeys; it is not one.

CREATE TABLE pct_medical_episodes (
    episode_id           UUID            PRIMARY KEY,
    tenant_id            UUID            NOT NULL,
    subject_cpid         VARCHAR(128)    NOT NULL,

    -- What kind of course of care this is.
    episode_type         VARCHAR(48)     NOT NULL,
    -- FHIR EpisodeOfCare status. PLANNED exists so a course of care can be agreed before it starts
    -- — a dialysis pathway decided in clinic but not yet begun is a real state, and recording it as
    -- ACTIVE would overstate what is happening to the patient.
    status               VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',

    title                VARCHAR(512)    NOT NULL,

    -- Who is accountable. NULL is a finding on a multimorbidity view, not a blank to be ignored:
    -- an episode nobody owns is the thing that goes unreviewed.
    responsible_service  VARCHAR(64),
    responsible_actor    VARCHAR(255),
    managing_facility_id VARCHAR(64),

    started_on           DATE            NOT NULL,
    ended_on             DATE,
    -- Why it ended. Recorded separately from the status because "completed", "the patient died",
    -- "transferred to another facility" and "lost to follow-up" are clinically and statistically
    -- different endings that all leave status FINISHED, and collapsing them makes an outcome
    -- indicator unbuildable.
    end_reason           VARCHAR(48),

    -- Soft reference to pct.emergency_episode (emergency lane, not yet created). Set when this
    -- course of care began with a presentation to emergency — the seam that makes demonstrations 3
    -- and 5 (heart failure, stroke) provable end to end.
    emergency_episode_id UUID,

    notes                TEXT,
    created_by           VARCHAR(255)    NOT NULL,
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pct_medical_episodes_status_check CHECK (status IN (
        'PLANNED',
        'ACTIVE',
        'ON_HOLD',
        'FINISHED',
        'CANCELLED'
    )),
    CONSTRAINT pct_medical_episodes_type_check CHECK (episode_type IN (
        'ACUTE_ILLNESS',            -- a single illness with an expected end
        'CHRONIC_DISEASE',          -- ongoing management of a long-term condition
        'MULTIMORBIDITY',           -- a consolidated plan across several conditions
        'DIAGNOSTIC_WORKUP',        -- an investigation arc, before a diagnosis exists
        'REHABILITATION',
        'PALLIATIVE',
        'PREVENTIVE'
    )),
    CONSTRAINT pct_medical_episodes_end_reason_check CHECK (end_reason IS NULL OR end_reason IN (
        'COMPLETED',
        'TRANSFERRED',
        'LOST_TO_FOLLOW_UP',
        'DIED',
        'PATIENT_DECLINED',
        'CANCELLED'
    )),
    -- An episode that has ended must say when, and one that has not must not claim an end date.
    -- Without this, "finished" and "still running" become indistinguishable from the dates alone,
    -- which is the difference between a completed course of treatment and one nobody closed.
    CONSTRAINT pct_medical_episodes_ended_check CHECK (
        (status IN ('FINISHED', 'CANCELLED') AND ended_on IS NOT NULL)
        OR (status NOT IN ('FINISHED', 'CANCELLED') AND ended_on IS NULL)
    ),
    CONSTRAINT pct_medical_episodes_date_order_check CHECK (ended_on IS NULL OR ended_on >= started_on)
);

CREATE INDEX idx_pct_medical_episodes_subject ON pct_medical_episodes (tenant_id, subject_cpid);
CREATE INDEX idx_pct_medical_episodes_open ON pct_medical_episodes (tenant_id, subject_cpid)
    WHERE status IN ('PLANNED', 'ACTIVE', 'ON_HOLD');
CREATE INDEX idx_pct_medical_episodes_emergency ON pct_medical_episodes (tenant_id, emergency_episode_id)
    WHERE emergency_episode_id IS NOT NULL;

COMMENT ON TABLE pct_medical_episodes IS
    'A course of medical care over time — the arc of a problem across contacts, facilities and '
    'years. Not an encounter (one contact), not a journey (one facility visit), and not '
    'pct.emergency_episode (one presentation to emergency care), which it links to rather than '
    'replaces.';

-- Which problems this episode exists to manage ---------------------------------
--
-- Many-to-many on purpose. One episode can manage several problems (a multimorbidity plan is the
-- whole point), and one problem can span several episodes over a lifetime — hypertension has one
-- problem row and an episode per period of active management. Putting an episode_id column on
-- pct_problems instead would have forced the second case to duplicate the problem, which is the
-- fork the W1 duplicate guard exists to prevent.
CREATE TABLE pct_medical_episode_problems (
    episode_id   UUID         NOT NULL REFERENCES pct_medical_episodes(episode_id) ON DELETE CASCADE,
    problem_id   UUID         NOT NULL REFERENCES pct_problems(problem_id) ON DELETE CASCADE,
    tenant_id    UUID         NOT NULL,
    -- Whether this problem is the reason the episode exists or something managed alongside it.
    -- A heart-failure admission that also manages the patient's diabetes is not a diabetes episode.
    role         VARCHAR(24)  NOT NULL DEFAULT 'PRIMARY',
    added_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    PRIMARY KEY (episode_id, problem_id),
    CONSTRAINT pct_episode_problems_role_check CHECK (role IN ('PRIMARY', 'SECONDARY'))
);

CREATE INDEX idx_pct_episode_problems_problem ON pct_medical_episode_problems (tenant_id, problem_id);

COMMENT ON TABLE pct_medical_episode_problems IS
    'Which problems a medical episode manages. Many-to-many: an episode may manage several '
    'problems, and a problem may run through several episodes over a lifetime.';
