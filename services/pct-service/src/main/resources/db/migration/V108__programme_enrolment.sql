-- Health-programme enrolment (PCT) — HIV and TB, extensible to NCD.
--
-- HIV and TB are run as governed longitudinal PROGRAMMES: a person is enrolled, put on treatment,
-- monitored on a schedule the DAK defines, and eventually exits with an outcome that national
-- reporting counts. None of that had a home in the estate — verified green field (no viral_load,
-- art_regimen, cd4 or tb_treatment concept existed anywhere). This is that home.
--
-- WHAT THIS IS, AND WHAT IT DELIBERATELY IS NOT.
--
-- It is NOT a new problem store. The HIV or TB diagnosis is one row in pct_problems like any other
-- diagnosis, and this enrolment ANCHORS to it (anchor_problem_id, a hard FK) rather than
-- re-recording it. "One disease, one entry" (the W1 safety property) means a patient's HIV is a
-- single problem whether you look at it through the problem list or through the programme. An
-- enrolment cannot exist without its problem — that is the point of the NOT NULL FK.
--
-- It is NOT a rival to pct_medical_episodes. An episode is the generic clinical arc of a problem;
-- an enrolment is the GOVERNED programme membership layered on top — a lifecycle, a treatment
-- register, a monitoring schedule and an outcome the medical episode has no vocabulary for. The
-- enrolment may point at the managing episode (episode_id, nullable) but does not replace it.
--
-- It is NOT where the clinical measurements live. Viral load, CD4 and TB bacteriology are
-- observations and are recorded in pct_observations (V057) with governed codes — extend, do not
-- fork. This table holds the programme membership and its lifecycle; the regimen the patient is on
-- lives in V109 pct_treatment_regimens.
--
-- CONFIDENTIALITY. HIV is specially protected; TB is not (TB is a notifiable disease, confidential
-- from nobody). This table records which programme a person is in, and the confidential lane is
-- driven from the anchor problem's ICD code through zibo's governed confidential-category map
-- (B20–B24, Z21, R75 → HIV → SPECIALLY_PROTECTED, seeded in zibo V008) rather than a second
-- confidentiality flag stored here that could drift from it. There is exactly one source of truth
-- for "is this confidential", and it is the code, classified in zibo. The SPECIALLY_PROTECTED
-- enforcement seam ships in SHADOW pending an MoHCC governance act, so no HIV record is stamped with
-- a protection class today — the gap is stated in the pack deliverable, not hidden behind a label
-- that does not protect.

CREATE TABLE pct_programme_enrolments (
    enrolment_id         UUID            PRIMARY KEY,
    tenant_id            UUID            NOT NULL,
    subject_cpid         VARCHAR(128)    NOT NULL,

    -- Which governed programme. HIV_CARE and TB_TREATMENT today; NCD registers extend the set
    -- (V113–V116 in the lease) without a new table, because a programme enrolment is the same shape
    -- whatever the disease.
    programme            VARCHAR(32)     NOT NULL,

    -- The programme lifecycle. SCREENING (being assessed for eligibility) and ENROLLED (in the
    -- programme but not yet on drugs — a pre-ART or pre-treatment state that is real and must not be
    -- overstated as ON_TREATMENT) precede treatment; TREATMENT_INTERRUPTED is a patient still in the
    -- programme but off drugs, which is clinically different from having exited and must not collapse
    -- into it. EXITED is the single closed state, and the reason it closed is a separate axis.
    status               VARCHAR(32)     NOT NULL DEFAULT 'SCREENING',

    -- Why the person left the programme. Separate from status for the same reason the medical
    -- episode keeps end_reason separate: "completed treatment", "cured", "transferred out", "lost to
    -- follow-up", "died" and "stopped against advice" all leave status EXITED and are entirely
    -- different outcomes. A programme that defaulted this to a good outcome would report a patient
    -- who was lost to follow-up as a treatment success — the exact statistic the programme exists to
    -- get right. NULL unless EXITED.
    exit_reason          VARCHAR(48),

    -- The diagnosis this programme treats. A hard FK to the one clinical truth: you cannot enrol
    -- someone in HIV care without an HIV problem on their list (even a SUSPECTED one during
    -- screening). This is what makes "one disease, one entry" structural rather than hoped for.
    anchor_problem_id    UUID            NOT NULL REFERENCES pct_problems(problem_id),

    -- The managing course of care, when one has been opened. Nullable: a screening enrolment may
    -- precede any episode, and an episode is not mandatory to run a programme.
    episode_id           UUID            REFERENCES pct_medical_episodes(episode_id),

    -- National programme register identifier (ART number, TB registration number). An operational
    -- identifier, not a diagnosis; optional because it is often assigned after screening.
    programme_number     VARCHAR(64),

    enrolled_on          DATE            NOT NULL,
    exit_on              DATE,

    -- The ART or TB site accountable for this enrolment.
    managing_facility_id VARCHAR(64),
    responsible_actor    VARCHAR(255),

    notes                TEXT,

    -- Set when captured offline in the field and reconciled later; lets a replay be idempotent.
    client_offline_id    VARCHAR(128),

    created_by           VARCHAR(255)    NOT NULL,
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pct_programme_enrolments_programme_check CHECK (programme IN (
        'HIV_CARE',
        'TB_TREATMENT'
    )),
    CONSTRAINT pct_programme_enrolments_status_check CHECK (status IN (
        'SCREENING',
        'ENROLLED',
        'ON_TREATMENT',
        'TREATMENT_INTERRUPTED',
        'EXITED'
    )),
    CONSTRAINT pct_programme_enrolments_exit_reason_check CHECK (exit_reason IS NULL OR exit_reason IN (
        'TREATMENT_COMPLETED',   -- finished the planned course (TB continuation phase completed)
        'CURED',                 -- TB: bacteriologically confirmed cure
        'TRANSFERRED_OUT',       -- continuing care at another site
        'LOST_TO_FOLLOW_UP',     -- stopped attending; NOT a success
        'DIED',
        'STOPPED_BY_PATIENT',    -- declined to continue
        'TREATMENT_FAILED'       -- on-treatment failure ending this enrolment
    )),
    -- An enrolment that has exited must say when and why; one that has not must claim neither. This
    -- is "unfinished must never read as clean" as a database invariant: EXITED-with-no-reason and
    -- still-screening-with-an-exit-date are both incoherent states the record cannot hold.
    CONSTRAINT pct_programme_enrolments_exited_check CHECK (
        (status = 'EXITED' AND exit_on IS NOT NULL AND exit_reason IS NOT NULL)
        OR (status <> 'EXITED' AND exit_on IS NULL AND exit_reason IS NULL)
    ),
    CONSTRAINT pct_programme_enrolments_date_order_check CHECK (
        exit_on IS NULL OR exit_on >= enrolled_on
    )
);

-- One active enrolment per programme per patient. A returning patient who was lost to follow-up
-- gets a NEW enrolment, but only once the prior one is EXITED — you cannot be concurrently enrolled
-- in HIV care twice. Unlike a problem (a second primary cancer is genuinely new, so that guard
-- answers 409 rather than refusing), a duplicate active programme enrolment is always an error, so
-- this is enforced as a hard partial-unique constraint.
CREATE UNIQUE INDEX uq_pct_programme_enrolments_active
    ON pct_programme_enrolments (tenant_id, subject_cpid, programme)
    WHERE status <> 'EXITED';

CREATE INDEX idx_pct_programme_enrolments_subject
    ON pct_programme_enrolments (tenant_id, subject_cpid);
CREATE INDEX idx_pct_programme_enrolments_problem
    ON pct_programme_enrolments (tenant_id, anchor_problem_id);
CREATE INDEX idx_pct_programme_enrolments_offline
    ON pct_programme_enrolments (tenant_id, client_offline_id)
    WHERE client_offline_id IS NOT NULL;

COMMENT ON TABLE pct_programme_enrolments IS
    'Governed longitudinal enrolment in a health programme (HIV care, TB treatment, extensible to '
    'NCD). Anchors to the diagnosis in pct_problems rather than re-recording it, and layers a '
    'programme lifecycle and outcome on top of the medical episode. Clinical measurements stay in '
    'pct_observations; the regimen lives in pct_treatment_regimens.';
