-- =====================================================================================
-- Procedures — Clinical Procedures Pipeline platform layer :: V001 initial schema
-- Schema: procedures.
--
-- P0 is the scaffold: the schema, the outbox, and the execution index. The catalogue
-- itself lands in P1, because it is the keystone and deserves its own wave — §5
-- appropriateness, §7 competence, §8 readiness, §9 safety pauses and §17 aftercare are
-- all functions of it and none can be built honestly before it exists.
--
-- WHAT THIS SERVICE OWNS (ADR-SURGERY-AND-PROCEDURES-SERVICE-BOUNDARIES, decision 1):
-- catalogue + requirements, appropriateness/duplication, competence and privilege
-- resolution, readiness engine, safety-pause templates, sedation requirement profiles,
-- aftercare templates, the execution index, pipeline analytics.
--
-- ENGINE-NOT-STORE (decision 2). This service evaluates; the executing service persists.
-- There is deliberately NO readiness table and NO checklist-completion table here:
-- inpatient.procedure_readiness_check and inpatient.procedure_checklist_item remain the
-- per-episode record of truth and become the persisted verdict of an evaluation made
-- here. Two records of one fact is the duplication the guardrails forbid. If a future
-- migration in this schema adds a table that stores a verdict rather than the inputs to
-- one, that is the rule being broken.
--
-- WHAT THIS SERVICE MUST NOT OWN: the request record (oros_orders, order_type=PROCEDURE,
-- guarded by ProcedureWorkflow); the execution record, specimens, devices, counts and
-- recovery (inpatient.procedure_episode and its satellites); consent (mvumo); terminology
-- (zibo); decision logic (clinical-knowledge-platform); and — per care-continuum-doctrine
-- CC-2 — person-level longitudinal clinical registries and the clinical decision that
-- opens or closes a phase of care, both of which are PCT's.
-- =====================================================================================

CREATE SCHEMA IF NOT EXISTS procedures;

-- -------------------------------------------------------------------------------------
-- Execution index (pipeline §4, §30) — the correlation spine, and the reason a request
-- cannot disappear.
--
-- A procedure request lives in OROS and its execution record lives in whichever service
-- runs the procedure. Nothing today joins the two, so a request that is never executed,
-- or an execution whose request was cancelled underneath it, is invisible. This table is
-- that join and nothing more: it holds references and lifecycle, never clinical content.
--
-- It is NOT a second request record. The authoritative request state stays in
-- oros_orders.workflow_state; this row carries the pipeline's own view of whether the
-- request reached an executor, which executor, and what happened — so an unreconciled
-- request is a query rather than an archaeology exercise.
-- -------------------------------------------------------------------------------------
CREATE TABLE procedures.procedure_execution_index (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,

    -- The originating request. oros_orders.id — a reference, never a copy of its state.
    request_ref             VARCHAR(64) NOT NULL,

    -- The governing catalogue entry (procedures.procedure_definition, P1). Nullable until
    -- the catalogue exists; NOT NULL is applied in P1 once every request resolves one.
    catalogue_ref           UUID,

    -- Subject. CPID only — no PII in this service, consistent with the SHR rule.
    subject_cpid            VARCHAR(64) NOT NULL,

    -- Where the procedure is executed and by whom, once accepted. executor_service names
    -- the owning system of record (today always 'inpatient-service'); executor_ref is its
    -- own identifier for the record (today procedure_episode.episode_id). Modelled as a
    -- pair rather than a bare id so a second executor can never be mistaken for the first.
    executor_service        VARCHAR(64),
    executor_ref            VARCHAR(64),

    -- Setting the procedure is executed in — the discriminator the pipeline exists to add.
    -- THEATRE | BEDSIDE | CLINIC | WARD | CRITICAL_CARE | ENDOSCOPY | CATH_LAB |
    -- INTERVENTIONAL_RADIOLOGY | DIALYSIS | INFUSION | DENTAL | OPHTHALMIC | DERMATOLOGICAL
    setting                 VARCHAR(48),

    -- Pipeline lifecycle. Deliberately coarse: the fine-grained fulfilment lifecycle is
    -- OROS's nineteen-state ProcedureWorkflow and the execution lifecycle is the executor's.
    -- This column answers only "did the request reach an executor and did it conclude".
    lifecycle_state         VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
    -- REQUESTED | ROUTED | ACCEPTED | IN_EXECUTION | CONCLUDED | ABANDONED

    -- Every non-progressing state carries why and what happens next. Pipeline §4 requires
    -- this of every rejection, cancellation and delay, and a nullable pair enforces nothing
    -- on its own — the service refuses the transition without them, and the CHECK below
    -- makes ABANDONED impossible to reach silently.
    state_reason            TEXT,
    next_action             TEXT,

    -- PCT anchor (CC-5). A procedure with no resolvable anchor is an orphan.
    pct_anchor_type         VARCHAR(32),   -- JOURNEY | ENCOUNTER | ADMISSION | TRAUMA_EPISODE
    pct_anchor_ref          VARCHAR(64),

    -- Emergency handover acceptance (cross-programme contract with the emergency lane).
    -- Set when this execution is the acceptance of a pct.emergency_handover.
    emergency_handover_ref  VARCHAR(64),

    requested_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    concluded_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                 BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_procedure_execution_request UNIQUE (tenant_id, request_ref),
    CONSTRAINT chk_procedure_execution_lifecycle CHECK (
        lifecycle_state IN ('REQUESTED','ROUTED','ACCEPTED','IN_EXECUTION','CONCLUDED','ABANDONED')),
    -- A request may not be abandoned without a recorded reason and a next action. This is
    -- pipeline §4's "no request disappears" expressed where it cannot be forgotten.
    CONSTRAINT chk_procedure_execution_abandon_reason CHECK (
        lifecycle_state <> 'ABANDONED'
        OR (state_reason IS NOT NULL AND length(btrim(state_reason)) > 0
            AND next_action IS NOT NULL AND length(btrim(next_action)) > 0)),
    -- An executor is a pair or it is nothing. A bare ref with no owning service is a
    -- reference nobody can resolve.
    CONSTRAINT chk_procedure_execution_executor_pair CHECK (
        (executor_service IS NULL AND executor_ref IS NULL)
        OR (executor_service IS NOT NULL AND executor_ref IS NOT NULL))
);

CREATE INDEX idx_procedure_execution_subject
    ON procedures.procedure_execution_index (tenant_id, subject_cpid, requested_at DESC);
CREATE INDEX idx_procedure_execution_state
    ON procedures.procedure_execution_index (tenant_id, lifecycle_state);
-- The reconciliation query this table exists to make cheap: requests that never reached
-- an executor. A partial index because the healthy majority is uninteresting.
CREATE INDEX idx_procedure_execution_unrouted
    ON procedures.procedure_execution_index (tenant_id, requested_at)
    WHERE executor_ref IS NULL AND lifecycle_state IN ('REQUESTED','ROUTED');
CREATE INDEX idx_procedure_execution_executor
    ON procedures.procedure_execution_index (executor_service, executor_ref)
    WHERE executor_ref IS NOT NULL;

-- -------------------------------------------------------------------------------------
-- Outbox — house pattern. Shape mirrors the estate's other services so the shared
-- publisher and the drainage rigs work unchanged.
-- -------------------------------------------------------------------------------------
CREATE TABLE procedures.event_outbox (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(64) NOT NULL,
    aggregate_id    VARCHAR(64) NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    schema_version  INT NOT NULL DEFAULT 1,
    correlation_id  UUID,
    causation_id    UUID,
    idempotency_key VARCHAR(255) NOT NULL,
    producer        VARCHAR(64) NOT NULL DEFAULT 'procedures-service',
    tenant_id       UUID NOT NULL,
    pod_id          VARCHAR(64) NOT NULL DEFAULT 'national-spine',
    subject_id      VARCHAR(64) NOT NULL,
    subject_type    VARCHAR(64) NOT NULL,
    partition_key   VARCHAR(255),
    occurred_at     TIMESTAMPTZ NOT NULL,
    payload_json    JSONB NOT NULL,
    published_at    TIMESTAMPTZ,
    publish_error   TEXT,
    retry_count     INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_procedures_outbox_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_procedures_outbox_unpublished
    ON procedures.event_outbox (created_at)
    WHERE published_at IS NULL;
