-- Surgery :: V008 — surgical pack analytics indicator catalogue (§23).
--
-- SCOPE, same ADR boundary as procedures-service's own V010: surgery-service owns GOVERNED
-- CONTENT declaring what each indicator IS and its real computation status today — never a
-- second execution engine recomputing what reporting-service already computes.
--
-- RECONCILING THE COUNT, not picking a number and hiding the rest. Three documents disagree:
-- audit.md finding 23 says "2 of 20"; dak-baseline.md line 170 says "`Y` marks the four with a
-- real projection today"; the indicator list on the very next lines (dak-baseline.md:172-176)
-- carries only THREE `Y` tags (surgical volume, emergency versus elective, procedure type). All
-- three numbers are pre-existing, written in earlier sessions — this migration does not silently
-- pick the most flattering one. It checks the ACTUAL projection
-- (reporting-service's rpt_theatre_case_metric, V002__theatre_report_catalog.sql) column by
-- column, the same discipline P14's V010 applied to the pipeline's own 22 indicators, and seeds
-- computation_status from what that table can genuinely answer:
--   - surgical volume: COMPUTED (total_cases exists).
--   - cancellation, day-of-surgery cancellation (partial — see its own row), complications,
--     unplanned return to theatre: COMPUTED (cancelled/complication_flag/returned_to_theatre
--     columns exist and are queried by the seeded theatre-utilisation report).
--   - emergency versus elective: the baseline's own `Y` tag is WRONG. rpt_theatre_case_metric
--     has NO triage/urgency column at all — confirmed by reading V002's CREATE TABLE directly,
--     not assumed from the tag. Seeded NOT_YET_INSTRUMENTED, not COMPUTED, because a wrong `Y`
--     is worse than an honest gap (the same P1 SNOMED lesson, applied to a documentation claim
--     instead of a clinical code).
--   - procedure type: PARTIAL — procedure_code/procedure_name exist per-case
--     (theatre-case-register already returns them) but the seeded catalog has no query GROUPing
--     by them into a distribution; the raw signal exists, the aggregation does not.
-- audit.md's "2 of 20" undercounts (cancellation and complications are both genuinely real).
-- dak-baseline's "four" is right on the COUNT by coincidence but wrong on the SET — it tags
-- emergency-vs-elective (not real) instead of unplanned-return-to-theatre (real, untagged). The
-- true answer this migration asserts, and the runtime-proof rig checks by count: FOUR indicators
-- COMPUTED — surgical volume, cancellation, complications, unplanned return to theatre.
--
-- FACILITY-LEVEL STRATIFICATION (FR18) IS ALSO NOT MET by the current projection:
-- rpt_theatre_case_metric.facility_id is a raw UUID column with no join to a facility-level
-- lookup and no GROUP BY facility_id in either seeded report query — named as its own cross-
-- cutting gap on the EQUITY row below, not claimed closed by facility_id's mere existence.
--
-- content_maturity/approving_authority follow this programme's standing rider — this is
-- engineering-authored content pending Ministry ratification, same as every other seed table.

CREATE TABLE surgery.analytics_indicator_definition (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID NOT NULL,
    indicator_code           VARCHAR(64) NOT NULL,
    indicator_name           VARCHAR(255) NOT NULL,
    numerator_description    TEXT NOT NULL,
    denominator_description  TEXT,
    computation_status       VARCHAR(24) NOT NULL DEFAULT 'NOT_YET_INSTRUMENTED',
    -- Cross-service reference by convention, same as procedures-service's V010 executable_via —
    -- deliberately not a foreign key, reporting-service's report_key lives in another database.
    executable_via           VARCHAR(255),
    gap_reason               TEXT,
    owning_service           VARCHAR(64),
    delegated_out_of_scope   BOOLEAN NOT NULL DEFAULT FALSE,
    lancet_core_indicator    TEXT,
    content_maturity         VARCHAR(32) NOT NULL DEFAULT 'ENGINEERING_SEED',
    approving_authority      VARCHAR(64) NOT NULL DEFAULT 'PENDING_MOHCC_RATIFICATION',
    display_order            INT NOT NULL DEFAULT 100,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_surgery_analytics_indicator_code UNIQUE (tenant_id, indicator_code),
    CONSTRAINT chk_surgery_analytics_indicator_status CHECK (
        computation_status IN ('COMPUTED', 'PARTIAL', 'NOT_YET_INSTRUMENTED')),
    CONSTRAINT chk_surgery_analytics_indicator_gap_reason CHECK (
        computation_status = 'COMPUTED'
        OR (gap_reason IS NOT NULL AND length(btrim(gap_reason)) > 0)),
    CONSTRAINT chk_surgery_analytics_indicator_executable_via CHECK (
        computation_status <> 'COMPUTED'
        OR (executable_via IS NOT NULL AND length(btrim(executable_via)) > 0))
);

CREATE INDEX idx_surgery_analytics_indicator_status
    ON surgery.analytics_indicator_definition (tenant_id, computation_status);

\set tenant '00000000-0000-4000-8000-000000000001'

INSERT INTO surgery.analytics_indicator_definition
    (tenant_id, indicator_code, indicator_name, numerator_description, denominator_description,
     computation_status, executable_via, gap_reason, owning_service, delegated_out_of_scope,
     lancet_core_indicator, display_order)
VALUES

(:'tenant', 'SURGICAL_VOLUME', 'Surgical volume',
 'Count of surgical cases run.', 'Reporting period.',
 'COMPUTED', 'reporting-service:theatre-utilisation', NULL, NULL, FALSE,
 'Lancet Commission on Global Surgery (2015) core indicator 1 — surgical volume.', 10),

(:'tenant', 'WAITING_TIME', 'Waiting time',
 'Time from waiting-list entry to operation.', 'Completed elective cases.',
 'PARTIAL', NULL,
 'scheduling-service''s waiting list carries real entry timestamps and surgery.surgical_waitlist_revalidation (V006, SB-2) carries revalidation timestamps, but no consumer joins waitlist-entry to theatre-case-start to compute the interval.',
 'reporting-service', FALSE, 'Lancet core indicator 2 — timely access to surgery.', 20),

(:'tenant', 'CANCELLATION', 'Cancellation',
 'Count and rate of cancelled cases.', 'Total booked cases.',
 'COMPUTED', 'reporting-service:theatre-utilisation', NULL, NULL, FALSE, NULL, 30),

(:'tenant', 'DAY_OF_SURGERY_CANCELLATION', 'Day-of-surgery cancellation',
 'Cancellations occurring on the scheduled operative day itself.', 'Total booked cases.',
 'PARTIAL', NULL,
 'rpt_theatre_case_metric.cancellation_reason is free text with no structured cancellation-timing field, so a same-day cancellation cannot be distinguished from one cancelled days in advance — the broader CANCELLATION row is real, this narrower one is not.',
 'reporting-service', FALSE, NULL, 40),

(:'tenant', 'EMERGENCY_VS_ELECTIVE', 'Emergency versus elective',
 'Split of cases by triage urgency.', 'Total cases.',
 'NOT_YET_INSTRUMENTED', NULL,
 'rpt_theatre_case_metric (reporting-service V002) has no triage/urgency column at all and TheatreReportingConsumer never reads inpatient.procedure_episode.triage_priority (V018) onto the event it projects from — the source fact exists on the episode, it is simply not carried onto theatre.* events today. dak-baseline.md''s own `Y` tag on this indicator is incorrect; recorded here as the correction.',
 'inpatient-service', FALSE, 'Lancet core indicator 2 (access) is commonly split this way.', 50),

(:'tenant', 'PROCEDURE_TYPE', 'Procedure type',
 'Distribution of cases by procedure code.', 'Total cases.',
 'PARTIAL', NULL,
 'procedure_code/procedure_name are real per-case columns (theatre-case-register already returns them) but neither seeded report query GROUPs BY them into a type distribution — the raw signal exists, the aggregation does not.',
 'reporting-service', FALSE, NULL, 60),

(:'tenant', 'BELLWETHER_ACCESS', 'Bellwether procedure access',
 'Facility-level availability of the three bellwether procedures (laparotomy, caesarean section, open fracture treatment).', 'Facilities expected to offer them.',
 'NOT_YET_INSTRUMENTED', NULL,
 'procedures.procedure_definition (P1) has no facility-capability join and no bellwether flag; facility surgical-capability reporting (dak-baseline FR16) has no data model anywhere in this programme.',
 'surgery-service', FALSE, 'Lancet core indicator 3 — bellwether procedures.', 70),

(:'tenant', 'CAESAREAN_INTEGRATION', 'Caesarean section rate',
 'Caesarean sections as a share of expected deliveries.', 'Expected deliveries in the population.',
 'NOT_YET_INSTRUMENTED', NULL,
 'Caesarean case data lives in the maternity/reproductive pack''s own model (per this pack''s explicit "no parallel obstetric model" rule, dak-baseline §3 scenario 6) — computing the rate is that pack''s remit, not a projection this service should build a parallel path for.',
 'rmnp-domain-pack', TRUE, 'Lancet core indicator 4 — caesarean delivery rate.', 80),

(:'tenant', 'MORTALITY', 'Perioperative mortality',
 'Deaths within 30 days of an operation.', 'Total operations.',
 'NOT_YET_INSTRUMENTED', NULL,
 'inpatient.procedure_episode has a DECEASED status but no post-discharge 30-day mortality linkage exists anywhere in this estate — death after discharge is not attributable back to the episode today.',
 'inpatient-service', FALSE, 'Lancet core indicator 5 — perioperative mortality.', 90),

(:'tenant', 'COMPLICATIONS', 'Complications',
 'Count of cases with at least one recorded complication.', 'Total completed cases.',
 'COMPUTED', 'reporting-service:theatre-utilisation', NULL, NULL, FALSE, NULL, 100),

(:'tenant', 'UNPLANNED_RETURN_TO_THEATRE', 'Unplanned return to theatre',
 'Count of unplanned returns to theatre.', 'Total completed cases.',
 'COMPUTED', 'reporting-service:theatre-utilisation', NULL, NULL, FALSE, NULL, 110),

(:'tenant', 'READMISSION', 'Readmission',
 'Unplanned readmission within 30 days of a surgical episode.', 'Total discharged surgical episodes.',
 'NOT_YET_INSTRUMENTED', NULL,
 'No readmission-linkage exists between a new admission and a prior surgical episode anywhere in inpatient-service or surgery-service today.',
 'inpatient-service', FALSE, NULL, 120),

(:'tenant', 'SURGICAL_SITE_INFECTION', 'Surgical-site infection',
 'Rate of surgical-site infection.', 'Total completed cases.',
 'PARTIAL', NULL,
 'SURGICAL_SITE_INFECTION is a named complication_class (procedures.complication_class, P10) and surgery.surgical_complication_pathway (SB-1) can carry an SSI-typed pathway, but the theatre.safety.* event stream folds it into the generic complication_flag reporting-service already sets — indistinguishable from any other complication type in the current projection.',
 'inpatient-service', FALSE, 'Lancet-adjacent perioperative-safety measure.', 130),

(:'tenant', 'LENGTH_OF_STAY', 'Length of stay',
 'Ward length of stay attributable to the surgical episode.', 'Total completed surgical episodes.',
 'PARTIAL', NULL,
 'rpt_theatre_case_metric.theatre_minutes measures time IN THEATRE only, not ward admission-to-discharge duration — a narrower fact than the named indicator; inpatient-service''s admission/discharge timestamps are not joined to theatre.* projections today.',
 'inpatient-service', FALSE, NULL, 140),

(:'tenant', 'HISTOLOGY_CLOSURE', 'Histology closure',
 'Rate of surgical episodes closed only after histology review (never closed on unreviewed histology).', 'Total episodes with a linked specimen.',
 'PARTIAL', NULL,
 'surgery.surgical_episode''s CLOSED transition already refuses on an unacknowledged specimen (SB-1, InpatientSpecimenClient/requireHistologyReviewed) — a real, enforced signal — but no event is emitted when the gate fires or when a closure genuinely waited on histology, so no consumer can report the rate.',
 'surgery-service', FALSE, NULL, 150),

(:'tenant', 'IMPLANT_OUTCOMES', 'Implant outcomes',
 'Rate of implant removal/revision, by device and manufacturer.', 'Total implants inserted.',
 'PARTIAL', NULL,
 'inventory.implant.recorded / .removed / .revised events are real and already published (ImplantTraceabilityService, P8) — the same real signal procedures-service''s own V010 named for its DEVICE_OUTCOMES row — but no consumer aggregates them into a per-device or per-manufacturer outcome metric.',
 'inventory-service', FALSE, NULL, 160),

(:'tenant', 'FUNCTIONAL_OUTCOMES', 'Functional outcomes',
 'Post-operative functional status change.', 'Total completed operations with a recorded pre-op baseline.',
 'NOT_YET_INSTRUMENTED', NULL,
 'surgery.surgical_assessment (S2) captures a pre-operative functional-status reviewed-flag+note, but no structured post-operative functional-status instrument or comparison exists anywhere in this programme.',
 'surgery-service', FALSE, NULL, 170),

(:'tenant', 'PATIENT_REPORTED_OUTCOMES', 'Patient-reported outcomes',
 'Structured patient-reported outcome measure (PROM) completion and score.', 'Total completed operations.',
 'NOT_YET_INSTRUMENTED', NULL,
 'No PROM instrument, capture surface or data model exists in surgery-service, Khuluma or Nompilo today.',
 'surgery-service', FALSE, NULL, 180),

(:'tenant', 'EQUITY', 'Equity',
 'Surgical access and outcomes by facility level, geography and demographic dimension.', 'Total cases.',
 'NOT_YET_INSTRUMENTED', NULL,
 'FR18''s facility-level stratification requirement is not met by the current projection: rpt_theatre_case_metric.facility_id is a raw UUID with no join to a facility-level lookup and no seeded report query GROUPs BY it. No demographic dimension travels on the theatre.* stream either. A cross-cutting enrichment concern, same shape as procedures-service V010''s own EQUITY row.',
 'reporting-service', FALSE, NULL, 190),

(:'tenant', 'FINANCIAL_PROTECTION', 'Financial protection',
 'Rate of surgical episodes proceeding without catastrophic out-of-pocket payment.', 'Total surgical episodes requiring financial clearance.',
 'PARTIAL', NULL,
 'inpatient.procedure_episode.financial_clearance_status (P12, V302) is a real, enforced per-episode projection of COSTA''s decision including EMERGENCY_OVERRIDE — the signal exists — but no consumer aggregates clearance outcomes into a financial-protection rate, and the underlying cost/payment amount itself is coverage-service/COSTA''s own remit by the ADR''s money delegation.',
 'costing-engine-service', TRUE, 'Lancet core indicator 6 — financial risk protection.', 200)

ON CONFLICT (tenant_id, indicator_code) DO NOTHING;
