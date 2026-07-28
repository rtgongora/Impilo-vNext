-- =====================================================================================
-- Surgery — Surgery and Surgical Specialties domain pack :: V001 initial schema
-- Schema: surgery.
--
-- S0 is the scaffold: the schema and the outbox, nothing else. The surgical episode itself
-- lands in S1 and deserves its own wave for the same reason the sibling procedures-service
-- programme gave its catalogue a dedicated P1 rather than folding it into P0: S1 must extend
-- pct_problems and pct_care_plans rather than duplicate them (see below), and getting that
-- join right is real design work, not scaffold work.
--
-- WHAT THIS SERVICE MAY OWN (ADR-SURGERY-AND-PROCEDURES-SERVICE-BOUNDARIES §5, as corrected
-- by the 2026-07-26 CC-2 amendment §5a): the surgical episode as a management-course record
-- that ATTACHES TO PCT journeys and never contains them; structured surgical assessment
-- content; operative indication and the non-operative options weighed; prehabilitation and
-- optimisation execution; planned-versus-performed reconciliation; complication pathway
-- INSTANCES as workflow (the resulting problem lands in pct_problems); waiting-list clinical
-- revalidation state; the fifteen specialty extensions.
--
-- WHAT THIS SERVICE MUST NOT OWN, per CC-2 — these are PCT's:
--   - surgical condition/diagnosis/certainty/severity/staging/recurrence -> pct_problems
--     (this service writes with responsible_service='surgery', does not hold its own copy)
--   - the surveillance plan -> pct_care_plans + pct_care_plan_goals (proposes/executes only)
--   - the decision that opens or closes a phase of care -> PCT decides (admission-handshake
--     model); this service owns the surgical REASONING and executes, never decides
--   - functional/patient-reported outcomes -> PCT person-level (contributes measurements)
--   - serial burn assessment -> pct.burn_assessment (emergency lane delegation, no parallel
--     acute copy)
--
-- An earlier draft of decision 5 would have built surgical_condition, a surveillance plan, an
-- outcome registry and a decision-to-operate record INSIDE this service — four duplicate
-- person-level registries, exactly what the estate's guardrails forbid. Found by verifying an
-- unrelated doctrine citation the emergency lane made about a different question, which showed
-- the same prohibition (CC-2) applies here too. Every future migration in this schema must
-- keep answering the four-question test cited in SurgeryApplication's own javadoc:
-- reference, not contain; contribute, not register; execute, not decide; stamp, not spine.
--
-- This service is PCT-anchored per CC-5 and references inpatient.procedure_episode for each
-- operation; it is NOT a second theatre workflow and must never create one.
-- =====================================================================================

CREATE SCHEMA IF NOT EXISTS surgery;

-- -------------------------------------------------------------------------------------
-- Outbox — house pattern. Shape mirrors the estate's other services so the shared
-- publisher and the drainage rigs work unchanged.
-- -------------------------------------------------------------------------------------
CREATE TABLE surgery.event_outbox (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(64) NOT NULL,
    aggregate_id    VARCHAR(64) NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    schema_version  INT NOT NULL DEFAULT 1,
    correlation_id  UUID,
    causation_id    UUID,
    idempotency_key VARCHAR(255) NOT NULL,
    producer        VARCHAR(64) NOT NULL DEFAULT 'surgery-service',
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
    CONSTRAINT uq_surgery_outbox_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_surgery_outbox_unpublished
    ON surgery.event_outbox (created_at)
    WHERE published_at IS NULL;
