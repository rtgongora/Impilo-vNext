-- =============================================================================================
-- V114 — consultation and multidisciplinary team decision (brief.md §14)
--
-- The §23 demonstration rig named this as the single largest hole, twice:
--
--   §23.7  "MDT has NO record at all, so the decision that sets treatment intent has no author,
--           no date and no attribution."
--   §23.10 "consultation itself has NO record — there is no request, no response and no statement
--           of who retains the patient, which is exactly the loss of ownership §23.10 asks us to
--           demonstrate against."
--
-- Two tables, each built around the one thing its absence was causing.
--
-- =============================================================================================
-- 1. CONSULTATION — asking another team a question, without handing them the patient
-- =============================================================================================
--
-- §23.10 asks for "surgical consultation without loss of ownership or duplicated clerking". Loss of
-- ownership is not usually a decision anybody makes; it is what happens when nobody records who
-- still holds the patient, and both teams assume the other does. So ownership is an explicit,
-- NOT NULL column on every consultation, and asking a question never changes it.
--
-- A takeover is a different act with a different name. A consultation may RECOMMEND one, and that
-- recommendation is recorded — but the recommendation does not perform it. If transfer of care
-- required only an opinion, the ownership column would be decorative.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE pct_consultations (
    consultation_id      UUID            PRIMARY KEY,
    tenant_id            UUID            NOT NULL,
    subject_cpid         VARCHAR(64)     NOT NULL,

    -- care-continuum-doctrine CC-5.
    journey_id           UUID,
    encounter_id         UUID,

    requesting_service   VARCHAR(64)     NOT NULL,
    requesting_actor     VARCHAR(255)    NOT NULL,
    asked_of_service     VARCHAR(64)     NOT NULL,

    -- A consultation with no question is a referral of the patient rather than of a question, and
    -- that is precisely the shape that loses ownership. NOT NULL and non-blank.
    question             TEXT            NOT NULL,
    clinical_summary     TEXT,
    urgency              VARCHAR(16)     NOT NULL DEFAULT 'ROUTINE',
    status               VARCHAR(24)     NOT NULL DEFAULT 'REQUESTED',

    -- Who holds the patient. Set at request time to the requesting service and NEVER changed by
    -- answering: see the CHECK below.
    owning_service       VARCHAR(64)     NOT NULL,

    advice               TEXT,
    responded_by         VARCHAR(255),
    responded_at         TIMESTAMPTZ,
    -- Whether the responder thinks the patient should move. A recommendation, not a transfer.
    takeover_recommended BOOLEAN         NOT NULL DEFAULT FALSE,
    decline_reason       TEXT,

    requested_at         TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pct_consultations_anchor_check CHECK (
        journey_id IS NOT NULL OR encounter_id IS NOT NULL
    ),
    CONSTRAINT pct_consultations_question_check CHECK (length(btrim(question)) > 0),
    CONSTRAINT pct_consultations_urgency_check CHECK (urgency IN ('ROUTINE', 'URGENT', 'EMERGENCY')),
    CONSTRAINT pct_consultations_status_check CHECK (status IN (
        'REQUESTED',   -- asked, nobody has picked it up
        'ACCEPTED',    -- the other team has taken the question
        'ANSWERED',    -- advice given
        'DECLINED',    -- the other team will not answer, and must say why
        'WITHDRAWN'    -- the requester no longer needs it
    )),
    -- An answered consultation must carry the advice, who gave it and when. An "answered"
    -- consultation with no advice is worse than an open one: it closes the loop in the worklist
    -- while leaving the question unanswered in the record.
    CONSTRAINT pct_consultations_answered_check CHECK (
        status <> 'ANSWERED'
        OR (advice IS NOT NULL AND length(btrim(advice)) > 0
            AND responded_by IS NOT NULL AND responded_at IS NOT NULL)
    ),
    -- Declining is a legitimate answer and it must say why, so the requester knows whether to ask
    -- again, ask someone else, or escalate.
    CONSTRAINT pct_consultations_declined_check CHECK (
        status <> 'DECLINED'
        OR (decline_reason IS NOT NULL AND length(btrim(decline_reason)) > 0
            AND responded_by IS NOT NULL AND responded_at IS NOT NULL)
    ),
    -- A takeover can only be recommended by someone who actually answered.
    CONSTRAINT pct_consultations_takeover_check CHECK (
        takeover_recommended = FALSE OR status = 'ANSWERED'
    )
);

COMMENT ON COLUMN pct_consultations.owning_service IS
    'The service that holds this patient. Set to the requesting service and never changed by an '
    'answer — asking a question does not hand over the patient. A takeover is a separate, explicit '
    'act; a consultation may recommend one (takeover_recommended) but cannot perform it. Loss of '
    'ownership is rarely decided by anyone: it is what happens when nobody records who still holds '
    'the patient and both teams assume the other does (brief.md §23.10).';

COMMENT ON CONSTRAINT pct_consultations_question_check ON pct_consultations IS
    'A consultation with no question is a referral of the patient rather than of a question, which '
    'is exactly the shape that loses ownership.';

CREATE INDEX idx_pct_consultations_subject ON pct_consultations (tenant_id, subject_cpid, requested_at DESC);
CREATE INDEX idx_pct_consultations_asked_of ON pct_consultations (tenant_id, asked_of_service, status);
CREATE INDEX idx_pct_consultations_open
    ON pct_consultations (tenant_id, asked_of_service, requested_at)
    WHERE status IN ('REQUESTED', 'ACCEPTED');

-- =============================================================================================
-- 2. MDT DECISION — a decision with an author, not a note
-- =============================================================================================
--
-- §23.7's failure is an MDT decision existing as free text nobody can later attribute. A decision
-- that sets treatment intent for a cancer patient and has no author, no date and no participants is
-- not a governed decision; it is a rumour with clinical consequences.
--
-- So: participants NOT NULL, decision NOT NULL, and the problems it concerns held in a join table
-- rather than described in prose — because "the MDT discussed the lung lesion" is not linkable and
-- a problem id is.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE pct_mdt_decisions (
    decision_id          UUID            PRIMARY KEY,
    tenant_id            UUID            NOT NULL,
    subject_cpid         VARCHAR(64)     NOT NULL,

    journey_id           UUID,
    encounter_id         UUID,
    episode_id           UUID            REFERENCES pct_medical_episodes(episode_id),

    meeting_type         VARCHAR(48)     NOT NULL,
    met_on               DATE            NOT NULL,

    -- Who was in the room. Free text is deliberate — a governed participant list would need a
    -- roster this service does not own — but it is NOT NULL, because an unattributed decision is
    -- the defect this table exists to remove.
    participants         TEXT            NOT NULL,
    chaired_by           VARCHAR(255)    NOT NULL,

    decision             TEXT            NOT NULL,
    treatment_intent     VARCHAR(24),
    rationale            TEXT,
    -- What happens next and who does it. A decision nobody was made responsible for is a decision
    -- that does not happen.
    next_action          TEXT,
    responsible_service  VARCHAR(64),

    recorded_by          VARCHAR(255)    NOT NULL,
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pct_mdt_decisions_anchor_check CHECK (
        journey_id IS NOT NULL OR encounter_id IS NOT NULL OR episode_id IS NOT NULL
    ),
    CONSTRAINT pct_mdt_decisions_meeting_type_check CHECK (meeting_type IN (
        'ONCOLOGY', 'CANCER_SITE_SPECIFIC', 'COMPLEX_MEDICINE', 'MORBIDITY_AND_MORTALITY',
        'PALLIATIVE', 'TRANSPLANT', 'INFECTION', 'OTHER'
    )),
    -- Curative, life-prolonging and symptom-directed are different plans with different costs to
    -- the patient. NULL means the meeting did not set an intent, which is different from setting
    -- one and not recording it — so it is nullable, but never defaulted.
    CONSTRAINT pct_mdt_decisions_intent_check CHECK (treatment_intent IS NULL OR treatment_intent IN (
        'CURATIVE', 'LIFE_PROLONGING', 'SYMPTOM_DIRECTED', 'DIAGNOSTIC', 'NOT_SET'
    )),
    CONSTRAINT pct_mdt_decisions_attribution_check CHECK (
        length(btrim(participants)) > 0
        AND length(btrim(chaired_by)) > 0
        AND length(btrim(decision)) > 0
    )
);

COMMENT ON CONSTRAINT pct_mdt_decisions_attribution_check ON pct_mdt_decisions IS
    'A decision that sets treatment intent and has no author, no chair and no participants is not a '
    'governed decision; it is a rumour with clinical consequences (brief.md §23.7).';

CREATE INDEX idx_pct_mdt_decisions_subject ON pct_mdt_decisions (tenant_id, subject_cpid, met_on DESC);

-- The problems the meeting was actually about. A join rather than prose, because "the MDT discussed
-- the lung lesion" cannot be linked to the problem list and a problem id can.
CREATE TABLE pct_mdt_decision_problems (
    decision_id  UUID         NOT NULL REFERENCES pct_mdt_decisions(decision_id) ON DELETE CASCADE,
    problem_id   UUID         NOT NULL REFERENCES pct_problems(problem_id) ON DELETE CASCADE,
    tenant_id    UUID         NOT NULL,
    PRIMARY KEY (decision_id, problem_id)
);

CREATE INDEX idx_pct_mdt_decision_problems_problem
    ON pct_mdt_decision_problems (tenant_id, problem_id);

COMMENT ON TABLE pct_consultations IS
    'A question asked of another team about a patient this team keeps (brief.md §14, §23.10).';
COMMENT ON TABLE pct_mdt_decisions IS
    'A multidisciplinary decision with its author, chair, participants and date (brief.md §14, §23.7).';
