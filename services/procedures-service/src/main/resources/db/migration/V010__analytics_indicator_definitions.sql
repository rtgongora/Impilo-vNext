-- Procedures :: V010 — pipeline analytics indicator catalogue (§26).
--
-- SCOPE, per ADR-SURGERY-AND-PROCEDURES-SERVICE-BOUNDARIES item 9: procedures-service owns
-- "pipeline analytics projection" as GOVERNED CONTENT — what each indicator IS (its numerator,
-- denominator, and today's real computation status) — not a second execution engine that
-- recomputes numbers reporting-service already computes.
--
-- reporting-service ALREADY has the one proven-executable projection in this estate:
-- TheatreReportingConsumer projects theatre.* events (inpatient.events / inpatient.safety) into
-- `rpt_theatre_case_metric`, and the seeded `theatre-utilisation` / `theatre-case-register`
-- report definitions (V002__theatre_report_catalog.sql) query it directly — confirmed executable
-- (real event projection -> local table -> JDBC query, no cross-database reference). Building a
-- SECOND projection table in procedures-service consuming the SAME topics to recompute the SAME
-- facts would be exactly the duplicate-system-of-record mistake this programme corrected in P8/P9
-- (see V007's header) — so this migration does not do that. Where a real computation path
-- already exists, this catalogue REFERENCES it (`executable_via`); it does not re-derive it.
--
-- HONESTY ON THE COUNT. dak-baseline.md §7 and audit.md both assert "the twenty-four
-- indicators" / "twenty-four indicators", but the checked-in indicator list itself
-- (dak-baseline.md §7) enumerates only twenty-two dot-separated names. That is a pre-existing
-- inconsistency in documents written in an earlier session, not something to paper over by
-- inventing two more indicators to hit the round number — this migration seeds exactly the
-- twenty-two indicators actually named in the baseline text.
--
-- computation_status is one of:
--   COMPUTED             — a real, executable query answers this today (executable_via set,
--                          gap_reason may be null).
--   PARTIAL              — the raw signal exists on an event bus or in a table somewhere, but no
--                          consumer aggregates it into a queryable metric yet, OR an existing
--                          metric is a proxy that under- or over-covers the named indicator.
--   NOT_YET_INSTRUMENTED — no signal exists anywhere yet; gap_reason names what would need to be
--                          built and by which service.
-- Every non-COMPUTED row MUST carry a gap_reason (enforced by CHECK) so a reader never has to
-- guess why a number is not available — the same "don't skirt incomplete functionality" standard
-- V006/V007/V008/V009 all held themselves to.

CREATE TABLE procedures.analytics_indicator_definition (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID NOT NULL,
    indicator_code           VARCHAR(64) NOT NULL,
    indicator_name           VARCHAR(255) NOT NULL,
    numerator_description    TEXT NOT NULL,
    denominator_description  TEXT,
    computation_status       VARCHAR(24) NOT NULL DEFAULT 'NOT_YET_INSTRUMENTED',
    -- Free-text reference into another service's real query surface, e.g.
    -- 'reporting-service:theatre-utilisation'. Deliberately not a foreign key — reporting-service's
    -- report_key lives in a different service's database, the same cross-service-reference-by-code
    -- pattern V008 uses for typical_grade_code, resolved by convention rather than a schema-level FK.
    executable_via           VARCHAR(255),
    gap_reason               TEXT,
    -- Which service owns the data model or must add instrumentation to close the gap. Null only
    -- when computation_status = 'COMPUTED' (the gap is already closed, so there is nothing to own).
    owning_service            VARCHAR(64),
    -- True when the ADR's delegation table (money -> coverage-service/COSTA; commodities ->
    -- inventory-service) places this indicator's computation outside procedures-service's own
    -- remit entirely, as distinct from an indicator procedures-service COULD close but has not
    -- yet — an important distinction for anyone reading this catalogue to plan work against it.
    delegated_out_of_scope    BOOLEAN NOT NULL DEFAULT FALSE,
    source_citation           TEXT,
    display_order             INT NOT NULL DEFAULT 100,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_analytics_indicator_code UNIQUE (tenant_id, indicator_code),
    CONSTRAINT chk_analytics_indicator_status CHECK (
        computation_status IN ('COMPUTED', 'PARTIAL', 'NOT_YET_INSTRUMENTED')),
    CONSTRAINT chk_analytics_indicator_gap_reason CHECK (
        computation_status = 'COMPUTED'
        OR (gap_reason IS NOT NULL AND length(btrim(gap_reason)) > 0)),
    CONSTRAINT chk_analytics_indicator_executable_via CHECK (
        computation_status <> 'COMPUTED'
        OR (executable_via IS NOT NULL AND length(btrim(executable_via)) > 0))
);

CREATE INDEX idx_analytics_indicator_status ON procedures.analytics_indicator_definition (tenant_id, computation_status);

INSERT INTO procedures.analytics_indicator_definition
    (tenant_id, indicator_code, indicator_name, numerator_description, denominator_description,
     computation_status, executable_via, gap_reason, owning_service, delegated_out_of_scope,
     source_citation, display_order)
VALUES
('00000000-0000-4000-8000-000000000001', 'PROCEDURE_VOLUME', 'Procedure volume',
 'Count of theatre cases booked.', 'Reporting period.',
 'COMPUTED', 'reporting-service:theatre-utilisation', NULL, NULL, FALSE,
 'Lancet Commission on Global Surgery (2015) core indicator 1 — surgical volume.', 10),

('00000000-0000-4000-8000-000000000001', 'INDICATION', 'Indication',
 'Count of procedures by clinical indication/diagnosis.', 'Total procedures.',
 'NOT_YET_INSTRUMENTED', NULL,
 'theatre.case.* events carry procedure_code/procedure_name only, never an indication or diagnosis code; would need oros-service (the request) or pct-service (the problem) to attach one and inpatient-service to carry it forward onto the event.',
 'oros-service', FALSE, NULL, 20),

('00000000-0000-4000-8000-000000000001', 'SPECIALTY', 'Specialty',
 'Count of procedures by performing specialty.', 'Total procedures.',
 'NOT_YET_INSTRUMENTED', NULL,
 'procedures.procedure_definition (the catalogue, V002/V003) has no specialty column and theatre.* events carry no specialty field; would need a catalogue attribute plus event enrichment before it exists anywhere.',
 'procedures-service', FALSE, NULL, 30),

('00000000-0000-4000-8000-000000000001', 'WAITING_TIME', 'Waiting time',
 'Time from booking to procedure start.', 'Completed and started cases.',
 'PARTIAL', NULL,
 'theatre.case.booked and theatre.case.started both carry a real occurred_at timestamp on the outbox envelope, but rpt_theatre_case_metric (reporting-service V002) does not persist booked_at/started_at columns to compute the interval from — the raw signal exists, the aggregation does not.',
 'reporting-service', FALSE, NULL, 40),

('00000000-0000-4000-8000-000000000001', 'CANCELLATION', 'Cancellation',
 'Count and rate of cancelled cases.', 'Total booked cases.',
 'COMPUTED', 'reporting-service:theatre-utilisation', NULL, NULL, FALSE, NULL, 50),

('00000000-0000-4000-8000-000000000001', 'DELAY_REASON', 'Delay reason',
 'Distribution of reasons for a delayed-but-not-cancelled case.', 'Delayed cases.',
 'PARTIAL', NULL,
 'inpatient.procedure_episode has no delayed-but-still-proceeding state distinct from CANCELLED — the pipeline audit (docs/clinical/procedures-pipeline/audit.md finding 6) already names this lifecycle gap; cancellation_reason on theatre.case.cancelled captures WHY a case was cancelled, not why a still-proceeding case slipped.',
 'inpatient-service', FALSE, NULL, 60),

('00000000-0000-4000-8000-000000000001', 'COMPLETION', 'Completion',
 'Count of cases reaching COMPLETED status.', 'Total booked cases.',
 'COMPUTED', 'reporting-service:theatre-utilisation', NULL, NULL, FALSE, NULL, 70),

('00000000-0000-4000-8000-000000000001', 'FAILURE', 'Failure',
 'Count of procedures that failed to achieve their clinical objective.', 'Total attempted procedures.',
 'NOT_YET_INSTRUMENTED', NULL,
 'inpatient.procedure_episode''s lifecycle has only BOOKED/PREOP/READY_FOR_THEATRE/IN_PROGRESS/PACU/COMPLETED/CANCELLED/DECEASED — no FAILED state exists to count from (pipeline audit finding 6).',
 'inpatient-service', FALSE, NULL, 80),

('00000000-0000-4000-8000-000000000001', 'ABORTED', 'Aborted',
 'Count of procedures stopped after starting, before completion.', 'Total started procedures.',
 'NOT_YET_INSTRUMENTED', NULL,
 'Same lifecycle gap as FAILURE — no ABORTED state exists on inpatient.procedure_episode today (pipeline audit finding 6).',
 'inpatient-service', FALSE, NULL, 90),

('00000000-0000-4000-8000-000000000001', 'COMPLICATIONS', 'Complications',
 'Count of cases with at least one recorded complication or safety event.', 'Total completed cases.',
 'COMPUTED', 'reporting-service:theatre-utilisation', NULL, NULL, FALSE,
 'Lancet Commission on Global Surgery (2015) core indicator area — perioperative safety.', 100),

('00000000-0000-4000-8000-000000000001', 'UNPLANNED_ADMISSION', 'Unplanned admission',
 'Count of unplanned post-procedure admissions.', 'Total procedures not already inpatient.',
 'PARTIAL', 'reporting-service:theatre-utilisation',
 'rpt_theatre_case_metric.unplanned_icu_transfers (from theatre.pacu.discharge-decision) covers unplanned ICU transfer specifically, not the broader case of an unplanned ward admission after what was meant to be a day-case procedure — a narrower proxy, not the full indicator.',
 'inpatient-service', FALSE, NULL, 110),

('00000000-0000-4000-8000-000000000001', 'UNPLANNED_SURGERY', 'Unplanned surgery (return to theatre)',
 'Count of unplanned returns to theatre.', 'Total completed cases.',
 'COMPUTED', 'reporting-service:theatre-utilisation', NULL, NULL, FALSE, NULL, 120),

('00000000-0000-4000-8000-000000000001', 'SPECIMEN_ADEQUACY', 'Specimen adequacy',
 'Rate of specimens assessed ADEQUATE vs INADEQUATE.', 'Total specimens collected.',
 'NOT_YET_INSTRUMENTED', NULL,
 'inpatient-service''s SpecimenCustodyService.assessAdequacy (V301, Wave P8) writes procedure_specimen.adequacy locally but never calls appendOutbox — no event of this fact reaches any downstream consumer today.',
 'inpatient-service', FALSE, NULL, 130),

('00000000-0000-4000-8000-000000000001', 'RESULT_TURNAROUND', 'Result turnaround',
 'Time from specimen collection to final result.', 'Specimens with a final result.',
 'NOT_YET_INSTRUMENTED', NULL,
 'Final-result timestamps live in oros-service''s own result lifecycle (preliminary/final/released), which is not on any topic procedures-service or reporting-service consumes; theatre.specimen.acknowledged marks acknowledgement of a result arriving, not when it was finalised.',
 'oros-service', FALSE, NULL, 140),

('00000000-0000-4000-8000-000000000001', 'RESULT_ACKNOWLEDGEMENT', 'Result acknowledgement',
 'Rate and latency of critical-result acknowledgement.', 'Critical results issued.',
 'PARTIAL', NULL,
 'theatre.specimen.acknowledged and theatre.specimen.critical (TheatreService, both real, emitted today) carry the signal, but no consumer — not reporting-service''s current projection, not any procedures-service table — aggregates them into an acknowledgement-rate metric.',
 'reporting-service', FALSE, NULL, 150),

('00000000-0000-4000-8000-000000000001', 'SEDATION_SAFETY', 'Sedation safety',
 'Rate of sedation-related adverse events.', 'Procedures performed under sedation.',
 'NOT_YET_INSTRUMENTED', NULL,
 'procedures.sedation_level (V005, Wave P7) is requirements content — what monitoring a sedation depth requires — not execution telemetry; no event distinguishes a sedation-specific adverse event from a generic theatre.safety.* event today.',
 'inpatient-service', FALSE, NULL, 160),

('00000000-0000-4000-8000-000000000001', 'INFECTION', 'Infection',
 'Rate of surgical-site and other procedure-associated infection.', 'Total completed cases.',
 'PARTIAL', NULL,
 'SURGICAL_SITE_INFECTION is a named complication_class (procedures.complication_class, V008, Wave P10) so the vocabulary exists, but a per-case infection occurrence is not tagged distinctly in the theatre.safety.* event stream — it folds into the generic complication_flag reporting-service already sets, indistinguishable from any other complication type.',
 'inpatient-service', FALSE, NULL, 170),

('00000000-0000-4000-8000-000000000001', 'DEVICE_OUTCOMES', 'Device outcomes',
 'Rate of implant removal/revision, by device and manufacturer.', 'Total implants inserted.',
 'PARTIAL', NULL,
 'inventory.implant.recorded / .removed / .revised events are real and already published today (ImplantTraceabilityService, Wave P8, on inventory-service''s own inventory.events topic) — but no consumer anywhere aggregates them into a per-device or per-manufacturer outcome metric.',
 'inventory-service', FALSE, NULL, 180),

('00000000-0000-4000-8000-000000000001', 'OPERATOR_AND_SUPERVISION', 'Operator and supervision',
 'Supervision-adequacy of trainee-logged cases — a safety-improvement measure, never a league table (dak-baseline.md §7''s own explicit constraint on this indicator).', 'Trainee-logged cases.',
 'PARTIAL', NULL,
 'theatre.trainee.logbook is a real, already-published event (TheatreService, prior wave) carrying supervision context, but no consumer aggregates it into a supervision-adequacy metric today.',
 'reporting-service', FALSE, NULL, 190),

('00000000-0000-4000-8000-000000000001', 'EQUITY', 'Equity',
 'Procedure access and outcomes by demographic/geographic dimension.', 'Total procedures.',
 'NOT_YET_INSTRUMENTED', NULL,
 'No demographic or facility-tier dimension travels on the theatre.* event stream today; would need enrichment from tshepo-identity/facility-registry data at projection time, a cross-cutting concern well beyond this indicator alone.',
 'reporting-service', FALSE, NULL, 200),

('00000000-0000-4000-8000-000000000001', 'COST', 'Cost',
 'Per-procedure actual cost.', 'Total procedures.',
 'NOT_YET_INSTRUMENTED', NULL,
 'ADR-SURGERY-AND-PROCEDURES-SERVICE-BOUNDARIES delegates money to coverage-service (Ruvimbo) and costing-engine-service (COSTA) "after clinical prioritisation, never before" — this is not a gap for procedures-service to close, it is out of this service''s remit by design.',
 'costing-engine-service', TRUE, NULL, 210),

('00000000-0000-4000-8000-000000000001', 'STOCKOUT_IMPACT', 'Stockout impact',
 'Procedures delayed or cancelled due to commodity stockout.', 'Total procedures.',
 'NOT_YET_INSTRUMENTED', NULL,
 'ADR-SURGERY-AND-PROCEDURES-SERVICE-BOUNDARIES delegates commodities to inventory-service (Dura) — computing this indicator is that service''s remit, not procedures-service''s, and no per-procedure stockout-attribution event exists yet regardless of owner.',
 'inventory-service', TRUE, NULL, 220)
ON CONFLICT (tenant_id, indicator_code) DO NOTHING;
