-- =====================================================================================
-- Telemonitoring — Community Remote Monitoring :: V001 initial schema (OF-B22)
-- Schema: telemonitoring.
--
-- Owns (Vol II §6 ownership row + §14): the MonitoringPlan aggregate and its lifecycle
-- (DRAFT -> PENDING_APPROVAL -> ACTIVE -> SUSPENDED -> COMPLETED / CANCELLED), the
-- programme profile catalogue (§14.1), and immutable versioned ThresholdProfiles.
-- Later OF-C epics land on top of this schema: AlertRule/AlertEpisode (OF-B26),
-- DeviceAssignment (OF-B24), monitoring-band Observation ingest/writer (OF-B25).
--
-- Doctrine invariants encoded here:
--  * Plans are PRESCRIBED — oros_order_id carries the OROS enrolment-order spine ref.
--  * Automated suggestions carry provenance (suggested_by / suggested_system_version)
--    and MUST NOT self-activate: activation requires approved_by + approved_at (§14.2).
--  * Threshold personalisation is an immutable version chain: a new row per amendment,
--    UNIQUE(plan_id, version), supersedes pointer — never an overwrite (§14.3 field 10/20).
--  * This service must NOT duplicate surveillance (population rules engine, separate
--    legal basis) or wellness (simba wellness_vitals_log) domains.
-- =====================================================================================

CREATE SCHEMA IF NOT EXISTS telemonitoring;

-- -------------------------------------------------------------------------------------
-- Programme profile catalogue (§14.1) — clinically governed templates. Plans REFERENCE
-- a programme by code (never copy). default_thresholds is the profile default band set,
-- personalised per-plan via tm_threshold_profiles.
-- -------------------------------------------------------------------------------------
CREATE TABLE telemonitoring.tm_monitoring_programmes (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                VARCHAR(64) NOT NULL,
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    condition_code      VARCHAR(64),            -- ZIBO condition code where one exists
    default_thresholds  JSONB NOT NULL DEFAULT '{}'::jsonb,  -- metric code -> bounds/severity bands
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tm_programme_code UNIQUE (code)
);

-- -------------------------------------------------------------------------------------
-- Monitoring plans (§14.3) — per-patient clinical aggregate, always clinician-approved.
-- -------------------------------------------------------------------------------------
CREATE TABLE telemonitoring.tm_monitoring_plans (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    patient_cpid                VARCHAR(64) NOT NULL,      -- CPID-only (R25 rule)
    programme_code              VARCHAR(64) NOT NULL,      -- references tm_monitoring_programmes.code
    status                      VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    -- DRAFT / PENDING_APPROVAL / ACTIVE / SUSPENDED / COMPLETED / CANCELLED
    oros_order_id               VARCHAR(64),               -- enrolment spine ref (OROS ClinicalOrderId)
    clinical_indication         VARCHAR(255),
    monitoring_setting          VARCHAR(48),               -- §14.1 settings table
    suggested_by                VARCHAR(128),              -- automated-suggestion provenance (§14.2)
    suggested_system_version    VARCHAR(64),
    suggested_at                TIMESTAMPTZ,
    approved_by                 VARCHAR(128),              -- clinician identity — REQUIRED before ACTIVE
    approved_at                 TIMESTAMPTZ,
    start_at                    TIMESTAMPTZ,
    end_at                      TIMESTAMPTZ,
    review_cadence              VARCHAR(48),               -- e.g. DAILY / WEEKLY / MONTHLY
    review_due_at               TIMESTAMPTZ,               -- mandatory review date for open-ended plans
    care_team                   JSONB NOT NULL DEFAULT '{}'::jsonb, -- escalation facility + responsible team refs
    lifecycle_reason            VARCHAR(512),              -- reason bound to the last transition
    created_by                  VARCHAR(128),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_tm_plan_tenant_patient ON telemonitoring.tm_monitoring_plans (tenant_id, patient_cpid);
CREATE INDEX idx_tm_plan_tenant_status ON telemonitoring.tm_monitoring_plans (tenant_id, status);
-- Enrolment idempotency: at most one plan per OROS enrolment order.
CREATE UNIQUE INDEX uq_tm_plan_oros_order
    ON telemonitoring.tm_monitoring_plans (oros_order_id)
    WHERE oros_order_id IS NOT NULL;

-- -------------------------------------------------------------------------------------
-- Threshold profiles — immutable version chain per plan (OF-B1 versioning idiom).
-- An amendment INSERTs version N+1 with supersedes = previous row id; rows are never
-- updated. UNIQUE(plan_id, version) is the concurrency race guard.
-- -------------------------------------------------------------------------------------
CREATE TABLE telemonitoring.tm_threshold_profiles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id         UUID NOT NULL REFERENCES telemonitoring.tm_monitoring_plans(id) ON DELETE CASCADE,
    tenant_id       UUID NOT NULL,
    version         INT NOT NULL,
    supersedes      UUID,                       -- previous version row id (null for v1)
    parameters      JSONB NOT NULL,             -- metric code -> {min,max,severityBands,...}
    reason          VARCHAR(512),               -- why this personalisation (§14.2 capture item 7)
    created_by      VARCHAR(128) NOT NULL,      -- who set it
    approved_by     VARCHAR(128),               -- clinician approval of the amendment
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tm_threshold_plan_version UNIQUE (plan_id, version)
);
CREATE INDEX idx_tm_threshold_plan ON telemonitoring.tm_threshold_profiles (plan_id, version DESC);

-- -------------------------------------------------------------------------------------
-- Transactional outbox (relayed by TelemonitoringOutboxPublisher). Canonical Impilo shape.
-- -------------------------------------------------------------------------------------
CREATE TABLE telemonitoring.event_outbox (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(64) NOT NULL,
    aggregate_id    VARCHAR(64) NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    schema_version  INT NOT NULL DEFAULT 1,
    correlation_id  UUID,
    causation_id    UUID,
    idempotency_key VARCHAR(255) NOT NULL,
    producer        VARCHAR(64) NOT NULL DEFAULT 'telemonitoring-service',
    tenant_id       UUID NOT NULL,
    pod_id          VARCHAR(64) NOT NULL DEFAULT 'national-spine',
    subject_id      VARCHAR(64) NOT NULL,
    subject_type    VARCHAR(64) NOT NULL,
    partition_key   VARCHAR(255),
    occurred_at     TIMESTAMPTZ NOT NULL,
    payload_json    JSONB NOT NULL,
    publish_error   TEXT,
    retry_count     INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ
);
CREATE INDEX idx_tm_outbox_unpublished
    ON telemonitoring.event_outbox (created_at)
    WHERE published_at IS NULL;

-- -------------------------------------------------------------------------------------
-- Seed: national programme profile catalogue at launch (§14.1 table — 20 profiles).
-- Default threshold bands are seeded for the highest-volume chronic profiles; the rest
-- start empty and are governed in via the clinical-governance function (OF-B21).
-- -------------------------------------------------------------------------------------
INSERT INTO telemonitoring.tm_monitoring_programmes (code, name, condition_code, default_thresholds, description) VALUES
 ('HYPERTENSION', 'Hypertension', NULL,
  '{"systolic_bp":{"unit":"mmHg","greenMax":140,"amberMax":160,"redMax":180},"diastolic_bp":{"unit":"mmHg","greenMax":90,"amberMax":100,"redMax":110},"pulse":{"unit":"bpm","greenMin":50,"greenMax":100}}'::jsonb,
  'BP, pulse, weight, symptoms. Personalised thresholds mandatory.'),
 ('DIABETES', 'Diabetes', NULL,
  '{"glucose_fasting":{"unit":"mmol/L","greenMin":4.0,"greenMax":7.0,"amberMax":10.0,"redMax":15.0},"glucose_random":{"unit":"mmol/L","greenMax":11.1,"amberMax":15.0,"redMax":20.0}}'::jsonb,
  'Glucose (fasting/random), weight, foot-check prompts, symptoms. Adherence signals for insulin cohorts.'),
 ('HEART_FAILURE', 'Heart failure', NULL,
  '{"weight":{"unit":"kg","rateOfChangePerDayMax":2.0},"spo2":{"unit":"%","greenMin":94,"amberMin":90,"redMin":85}}'::jsonb,
  'Daily weight, BP, pulse, SpO2, oedema/symptom diary. Rate-of-change on weight is a first-class trigger.'),
 ('COPD_CHRONIC_RESP', 'COPD / chronic respiratory', NULL,
  '{"spo2":{"unit":"%","greenMin":92,"amberMin":88,"redMin":85},"respiratory_rate":{"unit":"breaths/min","greenMax":20,"amberMax":25,"redMax":30}}'::jsonb,
  'SpO2, respiratory rate, symptom diary, rescue-inhaler use.'),
 ('HIGH_RISK_PREGNANCY', 'High-risk pregnancy', NULL,
  '{"systolic_bp":{"unit":"mmHg","greenMax":140,"amberMax":150,"redMax":160},"diastolic_bp":{"unit":"mmHg","greenMax":90,"amberMax":100,"redMax":110}}'::jsonb,
  'BP, weight, urine dip, fetal-movement diary, danger-sign checklist. Escalation biased to urgent teleconsult.'),
 ('POSTNATAL_MOTHER', 'Postnatal mother', NULL, '{}'::jsonb,
  'BP, bleeding/danger-sign checklist, mood screening. Time-boxed (e.g. 6 weeks) with automatic review.'),
 ('NEWBORN', 'Newborn', NULL, '{}'::jsonb,
  'Weight, feeding, jaundice checklist, temperature. CHW-administered or caregiver-assisted settings dominate.'),
 ('CHILD_GROWTH_NUTRITION', 'Child growth / nutrition', NULL, '{}'::jsonb,
  'Weight, MUAC, height/length, feeding diary. Integrates with community outreach visit machinery.'),
 ('HIV_TREATMENT_SUPPORT', 'HIV treatment support', NULL, '{}'::jsonb,
  'Adherence signals, symptom diary, appointment adherence. Heightened consent/visibility regime.'),
 ('TB_TREATMENT_SUPPORT', 'TB treatment support', NULL, '{}'::jsonb,
  'Adherence (incl. VOT where commissioned), symptom diary, weight. Heightened-sensitivity regime.'),
 ('MEDICATION_ADHERENCE', 'Medication adherence (general)', NULL, '{}'::jsonb,
  'Dose-taken confirmations, smart-dispenser events, missed-dose flags.'),
 ('POSTOPERATIVE', 'Postoperative', NULL, '{}'::jsonb,
  'Wound photos, temperature, pain score, mobility diary. Time-boxed.'),
 ('WOUND_CARE', 'Wound care', NULL, '{}'::jsonb,
  'Wound photos, measurement, exudate/odour checklist. Image quality flags mandatory.'),
 ('REHABILITATION', 'Rehabilitation', NULL, '{}'::jsonb,
  'Exercise completion, range-of-motion, pain score.'),
 ('MENTAL_HEALTH_FOLLOWUP', 'Mental-health follow-up', NULL, '{}'::jsonb,
  'Structured mood/symptom instruments, check-in cadence, safety-plan prompts.'),
 ('PALLIATIVE_CARE', 'Palliative care', NULL, '{}'::jsonb,
  'Symptom burden, pain score, caregiver strain, comfort checklist. Goal is comfort response.'),
 ('ELDERLY_FRAILTY', 'Elderly / frailty', NULL, '{}'::jsonb,
  'Activity, falls, weight, cognition prompts.'),
 ('HOME_OXYGEN', 'Home oxygen', NULL,
  '{"spo2":{"unit":"%","greenMin":92,"amberMin":88,"redMin":85}}'::jsonb,
  'SpO2, device runtime/flow telemetry, cylinder/concentrator status. Equipment truth stays in asset-registry.'),
 ('FEVER_OUTBREAK_FOLLOWUP', 'Fever / outbreak follow-up', NULL,
  '{"temperature":{"unit":"C","greenMax":37.5,"amberMax":38.5,"redMax":39.5}}'::jsonb,
  'Temperature, symptom checklist, household-contact prompts. Surveillance feed is a separate, documented interface.'),
 ('OCCUPATIONAL_HEALTH', 'Occupational health', NULL, '{}'::jsonb,
  'Exposure-linked observation sets. Employer visibility never includes clinical values.');
