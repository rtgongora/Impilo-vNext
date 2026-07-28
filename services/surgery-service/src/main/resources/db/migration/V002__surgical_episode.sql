-- =====================================================================================
-- Surgery :: V002 — the surgical episode (S1)
--
-- A management-course record that ATTACHES TO PCT journeys and never contains them
-- (ADR-SURGERY-AND-PROCEDURES-SERVICE-BOUNDARIES §5, corrected by the 2026-07-26 CC-2
-- amendment §5a). Research first, before writing this migration: read pct-service's actual
-- pct_problems/pct_care_plans schema and write API (services/pct-service — ProblemController,
-- ProblemService, ClinicalAccessGuard). Findings that shaped what follows:
--
--   - pct_problems HAS a real, callable write API (POST /v1/problems) that already accepts a
--     free-text `responsible_service` field with no allow-list — surgery-service can genuinely
--     CONTRIBUTE a surgical condition today, not merely in principle. This migration's
--     pct_problem_ref column is that contribution's own reference back, not a copy of the
--     condition itself.
--   - pct_care_plans/pct_care_plan_goals has NO responsible_service-equivalent attribution
--     column, so "surgery executes the surveillance plan" cannot be distinguished from any
--     other contributor's writes today. Closing that is pct-service's own migration, not
--     this service's — deliberately NOT attempted here. Surveillance-plan EXECUTION is
--     therefore deferred past S1, named rather than silently skipped.
--   - There is no generic "decision to open/close a phase of care" API for elective surgery —
--     only the admission-handshake model, hard-wired to inpatient census admission. Inventing
--     one unilaterally inside surgery-service would itself be a CC-2 breach in the other
--     direction (claiming a decision authority PCT has never delegated). So this table records
--     the SURGICAL REASONING ONLY (operative indication, non-operative options weighed) — the
--     one thing decision 5/§5a explicitly permits surgery-service to own — and makes NO claim
--     to open or close anything. Where an operation needs inpatient admission, that continues
--     through the EXISTING, unmodified admission-handshake via inpatient-service.
--
-- WHAT THIS TABLE DOES NOT DO, which the CC-2 regression guard (surgery-scaffold-journeys.sh
-- J-S0-4) would catch if it tried: no condition/diagnosis/staging/severity/certainty/
-- recurrence column here — those live in pct_problems, referenced by pct_problem_ref, never
-- duplicated. No surveillance-plan/outcome content either.
-- =====================================================================================

CREATE TABLE surgery.surgical_episode (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                       UUID NOT NULL,
    subject_cpid                    VARCHAR(64) NOT NULL,

    -- CC-5 PCT anchor. NOT NULL (as a pair-or-neither), unlike inpatient.procedure_episode's own
    -- anchor columns — care-continuum-doctrine's own compliance table records THAT nullability
    -- as an open violation (V-1). This is a fresh table; it need not repeat a known violation
    -- just because the sibling table it references does.
    journey_id                      VARCHAR(64),
    encounter_id                    UUID,

    -- The operation itself, once booked or performed. Nullable: a surgical episode begins at
    -- surgical assessment, before any operation exists to reference — inpatient.procedure_episode
    -- is referenced, never contained (component test question 1).
    procedure_episode_ref           UUID,

    -- The CONTRIBUTION into pct_problems (question 2: contribute, not register). NULL means "not
    -- yet contributed", never "no condition" — the condition itself is never copied here.
    pct_problem_ref                 UUID,
    pct_problem_contributed_at      TIMESTAMPTZ,

    -- The surgical REASONING this service MAY own per §5a: the indication and the non-operative
    -- alternatives weighed. Never the diagnosis, staging or certainty — those are pct_problems'.
    operative_indication            TEXT NOT NULL,
    non_operative_options_considered TEXT,

    -- Tracks THIS SERVICE's own view of the surgical reasoning's progress — never a claim to have
    -- opened or closed a phase of care (question 3: execute, not decide). An operation requiring
    -- inpatient admission still goes through the unmodified admission-handshake in
    -- inpatient-service; this status is orthogonal to that decision, not a substitute for it.
    status                          VARCHAR(24) NOT NULL DEFAULT 'ASSESSMENT',

    -- Free text, not a closed vocabulary: the fifteen specialty extensions (S15) do not exist
    -- yet, and inventing a vocabulary this migration cannot back would repeat the mistake this
    -- programme has refused everywhere else (SNOMED, aftercare taxonomy, complication classes).
    specialty                       VARCHAR(48),

    created_by                      VARCHAR(255),
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_surgical_episode_pct_anchor CHECK (journey_id IS NOT NULL OR encounter_id IS NOT NULL),
    CONSTRAINT chk_surgical_episode_status CHECK (
        status IN ('ASSESSMENT', 'LISTED_FOR_SURGERY', 'OPERATED', 'CLOSED', 'ABANDONED')),
    CONSTRAINT chk_surgical_episode_indication CHECK (length(btrim(operative_indication)) > 0)
);

CREATE INDEX idx_surgical_episode_subject
    ON surgery.surgical_episode (tenant_id, subject_cpid, created_at DESC);
CREATE INDEX idx_surgical_episode_procedure_ref
    ON surgery.surgical_episode (procedure_episode_ref)
    WHERE procedure_episode_ref IS NOT NULL;
-- The reconciliation query the deferred pct-contribution posture (best-effort, per S1's own
-- client) exists to make cheap: episodes whose condition was never successfully contributed.
CREATE INDEX idx_surgical_episode_uncontributed
    ON surgery.surgical_episode (tenant_id, created_at)
    WHERE pct_problem_ref IS NULL;
