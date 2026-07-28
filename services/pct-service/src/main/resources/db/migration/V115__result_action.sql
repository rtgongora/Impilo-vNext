-- =============================================================================================
-- V115 — what was DONE about a result (brief.md §11, and §25's "results are reviewed and actioned")
--
-- SYSTEM-OF-RECORD CHECK FIRST, BECAUSE HALF OF THIS ALREADY EXISTS
--
-- OROS owns orders, results and acknowledgement, and acknowledgement genuinely works:
--   * oros_acknowledgements (ack_type DEPARTMENT/CLINICIAN/CRITICAL, actor, notes, acked_at)
--   * oros_results.acknowledged_at / acknowledged_by
--   * V007 escalation for a critical result that breaches the ack threshold
--   * GET /v1/reconcile/diagnostics/critical-unacknowledged
-- The live path is wired: the mobile BFF calls orosClient.acknowledgeOrder, which reaches
-- AcknowledgementService.acknowledge and saves. None of that is rebuilt here.
--
-- What does NOT exist anywhere is the second half of §25's sentence. Searching every OROS migration
-- for action_taken / actioned / clinical_action / follow_up_action returns nothing. Acknowledgement
-- records that somebody SAW the result. It does not record what they DID about it, and the two are
-- different claims: a clinician can see a potassium of 6.8, tick it, and change nothing.
--
-- So this table records the CLINICAL ACTION, which is clinical truth and therefore PCT's, and points
-- at the OROS result rather than copying it. OROS keeps the result; PCT keeps what the result caused.
--
-- (Noted while checking, and NOT a live defect: ResultService.acknowledgeResult(UUID) in oros-service
-- looks the result up and returns it, setting and saving nothing, while its javadoc claims it
-- "delegates to AcknowledgementService". Nothing calls it — the live acknowledge path goes through
-- AcknowledgementService directly — so it is dead code rather than a silent no-op in production. It
-- is a trap for the next person who wires an endpoint to the plausibly-named method, and belongs to
-- the OROS lane.)
-- =============================================================================================

CREATE TABLE pct_result_actions (
    action_id        UUID            PRIMARY KEY,
    tenant_id        UUID            NOT NULL,
    subject_cpid     VARCHAR(64)     NOT NULL,

    -- care-continuum-doctrine CC-5.
    journey_id       UUID,
    encounter_id     UUID,

    -- Typed references into OROS. No FK: an FK can only name a local table, and copying the result
    -- here would fork the diagnostic record — the exact duplication the guardrails forbid.
    result_ref       VARCHAR(64)     NOT NULL,
    order_ref        VARCHAR(64),

    action           VARCHAR(32)     NOT NULL,

    -- Why. Required for NO_ACTION_NEEDED by the CHECK below, optional otherwise.
    rationale        TEXT,

    -- The problem this result changed, where it changed one. A result that starts a treatment
    -- without touching the problem list leaves the diagnosis it implies unrecorded.
    problem_id       UUID            REFERENCES pct_problems(problem_id),

    actioned_by      VARCHAR(255)    NOT NULL,
    actioned_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pct_result_actions_anchor_check CHECK (
        journey_id IS NOT NULL OR encounter_id IS NOT NULL
    ),

    CONSTRAINT pct_result_actions_action_check CHECK (action IN (
        'NO_ACTION_NEEDED',      -- reviewed, and nothing needed doing. Requires a reason.
        'TREATMENT_STARTED',
        'TREATMENT_CHANGED',
        'TREATMENT_STOPPED',
        'REPEAT_REQUESTED',
        'FURTHER_TEST_REQUESTED',
        'REFERRED',
        'PROBLEM_ADDED',         -- the result established a diagnosis
        'PROBLEM_RESOLVED',
        'ESCALATED',
        'PATIENT_INFORMED',
        'AWAITING_DISCUSSION'    -- e.g. pending an MDT; explicitly not a decision yet
    )),

    -- NO_ACTION_NEEDED is the reassuring answer, and therefore the dangerous one: it is what a
    -- clinician records when a result looked fine, and it is what an audit later reads as "reviewed
    -- and safely closed". It must carry its reason, so that a wrong one can be seen to be wrong.
    -- Every other action names itself; this one has to justify itself.
    CONSTRAINT pct_result_actions_no_action_reason_check CHECK (
        action <> 'NO_ACTION_NEEDED'
        OR (rationale IS NOT NULL AND length(btrim(rationale)) > 0)
    ),

    -- Claiming a result added or resolved a problem without naming which one is an attribution
    -- nobody can follow back to the problem list.
    CONSTRAINT pct_result_actions_problem_link_check CHECK (
        action NOT IN ('PROBLEM_ADDED', 'PROBLEM_RESOLVED') OR problem_id IS NOT NULL
    )
);

COMMENT ON TABLE pct_result_actions IS
    'What a clinician DID about a diagnostic result (brief.md §11, §25). OROS owns the order, the '
    'result and the acknowledgement — that somebody saw it. This owns what it caused, which is '
    'clinical truth and therefore PCT''s. A result can be acknowledged and change nothing, and those '
    'are different facts.';

COMMENT ON CONSTRAINT pct_result_actions_no_action_reason_check ON pct_result_actions IS
    'NO_ACTION_NEEDED is the reassuring answer and therefore the one that must justify itself. Every '
    'other action names itself.';

-- One action per result per actor: a clinician revising their action replaces it rather than
-- appending a second, and two clinicians acting on one result is a real and separate fact.
CREATE UNIQUE INDEX uq_pct_result_actions_result_actor
    ON pct_result_actions (tenant_id, result_ref, actioned_by);

CREATE INDEX idx_pct_result_actions_subject
    ON pct_result_actions (tenant_id, subject_cpid, actioned_at DESC);
CREATE INDEX idx_pct_result_actions_result
    ON pct_result_actions (tenant_id, result_ref);
