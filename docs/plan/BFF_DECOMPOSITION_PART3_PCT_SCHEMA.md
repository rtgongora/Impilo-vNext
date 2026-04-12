# BFF Decomposition — Part 3: PCT Database Schema Design

**Version**: 1.0.0
**Date**: 2026-04-12
**Status**: PROPOSED

---

## 1. Database Identity

```
Database: pct_db
Owner: pct-service (port 8088)
Engine: PostgreSQL 16
Encryption: AES-256-GCM (app-layer) + TDE/LUKS (disk-layer)
Tenant Isolation: Row-Level Security (RLS) on all business tables
Connection: mTLS (ssl-mode=verify-full)
```

---

## 2. Schema Overview

```
pct_db
├── pct_core        — Journey, encounter, task, referral orchestration
├── pct_clinical    — Clinical content (allergies, conditions, vitals, etc.)
├── pct_orders      — Order/result/prescription tracking
├── pct_inpatient   — Admission, bed, ward, emergency, maternity, charting
├── pct_queue       — Queue definitions, items, triage
├── pct_finance     — Encounter billing refs, payment gates
└── pct_outbox      — Event outbox (v1.1 standard)
```

---

## 3. pct_core Schema

### 3.1 journeys

The central orchestration entity. Every patient interaction starts a journey.

```sql
CREATE TABLE pct_core.journeys (
    id              BIGSERIAL PRIMARY KEY,
    journey_id      UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    patient_cpid    VARCHAR(128) NOT NULL,
    facility_id     UUID NOT NULL,
    entry_type      VARCHAR(32) NOT NULL,  -- WALK_IN, APPOINTMENT, REFERRAL, TELEMEDICINE, OUTREACH
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, PAUSED, COMPLETED, CANCELLED
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    referral_source VARCHAR(255),
    referral_id     UUID,
    created_by      VARCHAR(128),
    version         INT NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- RLS
ALTER TABLE pct_core.journeys ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON pct_core.journeys USING (tenant_id = current_setting('app.current_tenant')::uuid);
-- Indexes
CREATE INDEX idx_journeys_patient ON pct_core.journeys (patient_cpid, tenant_id);
CREATE INDEX idx_journeys_facility ON pct_core.journeys (facility_id, status);
```

### 3.2 encounters

```sql
CREATE TABLE pct_core.encounters (
    id              BIGSERIAL PRIMARY KEY,
    encounter_id    UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    journey_id      UUID NOT NULL REFERENCES pct_core.journeys(journey_id),
    patient_cpid    VARCHAR(128) NOT NULL,
    provider_id     VARCHAR(128),
    workspace_id    UUID,
    encounter_type  VARCHAR(32) NOT NULL,  -- OPD, INPATIENT, EMERGENCY, TELEMEDICINE, OUTREACH
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, PAUSED, TRANSFERRED, CLOSED
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at       TIMESTAMPTZ,
    butano_ref      VARCHAR(255),  -- FHIR Encounter reference in BUTANO
    version         INT NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE pct_core.encounters ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON pct_core.encounters USING (tenant_id = current_setting('app.current_tenant')::uuid);
CREATE INDEX idx_encounters_journey ON pct_core.encounters (journey_id);
CREATE INDEX idx_encounters_patient ON pct_core.encounters (patient_cpid, tenant_id);
CREATE INDEX idx_encounters_active ON pct_core.encounters (status) WHERE status = 'ACTIVE';
```

### 3.3 encounter_tasks

```sql
CREATE TABLE pct_core.encounter_tasks (
    id              BIGSERIAL PRIMARY KEY,
    task_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    encounter_id    UUID NOT NULL REFERENCES pct_core.encounters(encounter_id),
    task_type       VARCHAR(64) NOT NULL,  -- LAB_ORDER, IMAGING_ORDER, PRESCRIPTION, REFERRAL, DOCUMENTATION, CLEARANCE
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',  -- PENDING, IN_PROGRESS, COMPLETED, BLOCKED, CANCELLED
    depends_on      UUID,  -- another task_id
    assigned_to     VARCHAR(128),
    external_ref    VARCHAR(255),  -- OROS order_id, Pharmacy dispense_id, BUTANO resource ref
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE pct_core.encounter_tasks ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON pct_core.encounter_tasks USING (tenant_id = current_setting('app.current_tenant')::uuid);
```

### 3.4 referrals

```sql
CREATE TABLE pct_core.referrals (
    id                  BIGSERIAL PRIMARY KEY,
    referral_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    source_journey_id   UUID NOT NULL REFERENCES pct_core.journeys(journey_id),
    source_facility_id  UUID NOT NULL,
    source_provider_id  VARCHAR(128),
    target_facility_id  UUID NOT NULL,
    target_workspace    VARCHAR(128),
    reason              TEXT,
    urgency             VARCHAR(16) DEFAULT 'ROUTINE',  -- STAT, URGENT, ROUTINE
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',  -- PENDING, ACCEPTED, REJECTED, COMPLETED, EXPIRED
    accepted_at         TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    target_journey_id   UUID,  -- created at destination
    clinical_summary    JSONB,  -- referral package
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE pct_core.referrals ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON pct_core.referrals USING (tenant_id = current_setting('app.current_tenant')::uuid);
```

---

## 4. pct_clinical Schema

### 4.1 allergies

```sql
CREATE TABLE pct_clinical.allergies (
    id              BIGSERIAL PRIMARY KEY,
    allergy_id      UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    patient_cpid    VARCHAR(128) NOT NULL,
    encounter_id    UUID REFERENCES pct_core.encounters(encounter_id),
    allergen        VARCHAR(255) NOT NULL,
    allergen_code   VARCHAR(64),   -- SNOMED/ATC code
    allergen_type   VARCHAR(32),   -- MEDICATION, FOOD, ENVIRONMENTAL, BIOLOGICAL
    reaction        VARCHAR(255),
    severity        VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',  -- MILD, MODERATE, SEVERE, UNKNOWN
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE, INACTIVE, RESOLVED
    onset_date      DATE,
    recorded_by     VARCHAR(128),
    verified_at     TIMESTAMPTZ,
    butano_ref      VARCHAR(255),  -- FHIR AllergyIntolerance ref
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE pct_clinical.allergies ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON pct_clinical.allergies USING (tenant_id = current_setting('app.current_tenant')::uuid);
CREATE INDEX idx_allergies_patient ON pct_clinical.allergies (patient_cpid, tenant_id);
CREATE INDEX idx_allergies_active ON pct_clinical.allergies (patient_cpid, status) WHERE status = 'ACTIVE';
```

### 4.2 conditions

```sql
CREATE TABLE pct_clinical.conditions (
    id              BIGSERIAL PRIMARY KEY,
    condition_id    UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    patient_cpid    VARCHAR(128) NOT NULL,
    encounter_id    UUID REFERENCES pct_core.encounters(encounter_id),
    code            VARCHAR(32) NOT NULL,   -- ICD-10/ICD-11 code
    code_system     VARCHAR(64) DEFAULT 'ICD10',
    display         VARCHAR(512),
    category        VARCHAR(32),   -- PROBLEM_LIST, ENCOUNTER_DIAGNOSIS
    clinical_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, RECURRENCE, RELAPSE, INACTIVE, REMISSION, RESOLVED
    verification    VARCHAR(32) DEFAULT 'UNCONFIRMED',  -- UNCONFIRMED, PROVISIONAL, DIFFERENTIAL, CONFIRMED
    onset_date      DATE,
    abatement_date  DATE,
    recorded_by     VARCHAR(128),
    butano_ref      VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE pct_clinical.conditions ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON pct_clinical.conditions USING (tenant_id = current_setting('app.current_tenant')::uuid);
CREATE INDEX idx_conditions_patient ON pct_clinical.conditions (patient_cpid, tenant_id);
```

### 4.3 vitals

```sql
CREATE TABLE pct_clinical.vitals (
    id              BIGSERIAL PRIMARY KEY,
    vital_id        UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    patient_cpid    VARCHAR(128) NOT NULL,
    encounter_id    UUID REFERENCES pct_core.encounters(encounter_id),
    vital_type      VARCHAR(64) NOT NULL,  -- HEART_RATE, BLOOD_PRESSURE, TEMPERATURE, SPO2, RESPIRATORY_RATE, WEIGHT, HEIGHT, BMI
    value_numeric   DECIMAL(12,4),
    value_text      VARCHAR(128),          -- for composite values like BP "120/80"
    unit            VARCHAR(32),
    recorded_by     VARCHAR(128),
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    butano_ref      VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE pct_clinical.vitals ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON pct_clinical.vitals USING (tenant_id = current_setting('app.current_tenant')::uuid);
CREATE INDEX idx_vitals_patient ON pct_clinical.vitals (patient_cpid, tenant_id);
CREATE INDEX idx_vitals_encounter ON pct_clinical.vitals (encounter_id);
```

### 4.4 immunizations

```sql
CREATE TABLE pct_clinical.immunizations (
    id                  BIGSERIAL PRIMARY KEY,
    immunization_id     UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    patient_cpid        VARCHAR(128) NOT NULL,
    encounter_id        UUID REFERENCES pct_core.encounters(encounter_id),
    vaccine_code        VARCHAR(32) NOT NULL,  -- CVX code
    vaccine_display     VARCHAR(255),
    lot_number          VARCHAR(64),
    expiration_date     DATE,
    site                VARCHAR(64),
    route               VARCHAR(64),
    dose_quantity       DECIMAL(8,2),
    dose_unit           VARCHAR(16),
    dose_number         INT,
    series              VARCHAR(64),
    administered_by     VARCHAR(128),
    administered_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    butano_ref          VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE pct_clinical.immunizations ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON pct_clinical.immunizations USING (tenant_id = current_setting('app.current_tenant')::uuid);
```

### 4.5 clinical_notes

```sql
CREATE TABLE pct_clinical.clinical_notes (
    id              BIGSERIAL PRIMARY KEY,
    note_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    patient_cpid    VARCHAR(128) NOT NULL,
    encounter_id    UUID REFERENCES pct_core.encounters(encounter_id),
    note_type       VARCHAR(32) NOT NULL,  -- SOAP, PROGRESS, HISTORY, ASSESSMENT, PLAN, FREE_TEXT
    subjective      TEXT,
    objective        TEXT,
    assessment      TEXT,
    plan            TEXT,
    content         TEXT,   -- for non-SOAP notes
    recorded_by     VARCHAR(128),
    butano_ref      VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE pct_clinical.clinical_notes ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON pct_clinical.clinical_notes USING (tenant_id = current_setting('app.current_tenant')::uuid);
```

### 4.6 care_plans, care_plan_goals, care_plan_interventions

```sql
CREATE TABLE pct_clinical.care_plans (
    id              BIGSERIAL PRIMARY KEY,
    care_plan_id    UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    patient_cpid    VARCHAR(128) NOT NULL,
    encounter_id    UUID REFERENCES pct_core.encounters(encounter_id),
    title           VARCHAR(255) NOT NULL,
    plan_type       VARCHAR(32),
    status          VARCHAR(32) DEFAULT 'ACTIVE',
    created_by      VARCHAR(128),
    butano_ref      VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE pct_clinical.care_plan_goals (
    id              BIGSERIAL PRIMARY KEY,
    goal_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    care_plan_id    UUID NOT NULL REFERENCES pct_clinical.care_plans(care_plan_id),
    category        VARCHAR(64),
    description     TEXT NOT NULL,
    target_value    VARCHAR(128),
    priority        VARCHAR(16) DEFAULT 'MEDIUM',
    target_date     DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE pct_clinical.care_plan_interventions (
    id                  BIGSERIAL PRIMARY KEY,
    intervention_id     UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    care_plan_id        UUID NOT NULL REFERENCES pct_clinical.care_plans(care_plan_id),
    goal_id             UUID REFERENCES pct_clinical.care_plan_goals(goal_id),
    category            VARCHAR(64),
    description         TEXT NOT NULL,
    frequency           VARCHAR(64),
    responsible_role    VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 4.7 early_warning_scores, growth_measurements, apgar_scores

```sql
CREATE TABLE pct_clinical.early_warning_scores (
    id              BIGSERIAL PRIMARY KEY,
    ews_id          UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    patient_cpid    VARCHAR(128) NOT NULL,
    encounter_id    UUID REFERENCES pct_core.encounters(encounter_id),
    score_type      VARCHAR(16) NOT NULL DEFAULT 'NEWS2',
    total_score     INT NOT NULL,
    risk_level      VARCHAR(16) NOT NULL,  -- LOW, MEDIUM, HIGH, CRITICAL
    components      JSONB NOT NULL,
    escalation_required BOOLEAN DEFAULT FALSE,
    recorded_by     VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE pct_clinical.growth_measurements (
    id              BIGSERIAL PRIMARY KEY,
    measurement_id  UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    patient_cpid    VARCHAR(128) NOT NULL,
    encounter_id    UUID REFERENCES pct_core.encounters(encounter_id),
    measurement_type VARCHAR(32) NOT NULL,  -- WEIGHT, HEIGHT, HEAD_CIRCUMFERENCE, MUAC, BMI
    value           DECIMAL(8,2) NOT NULL,
    unit            VARCHAR(16) NOT NULL,
    z_score         DECIMAL(6,3),
    percentile      DECIMAL(6,2),
    recorded_by     VARCHAR(128),
    measured_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE pct_clinical.apgar_scores (
    id              BIGSERIAL PRIMARY KEY,
    apgar_id        UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    patient_cpid    VARCHAR(128) NOT NULL,
    encounter_id    UUID REFERENCES pct_core.encounters(encounter_id),
    minute          INT NOT NULL,  -- 1, 5, 10
    appearance      INT NOT NULL,
    pulse           INT NOT NULL,
    grimace         INT NOT NULL,
    activity        INT NOT NULL,
    respiration     INT NOT NULL,
    total           INT NOT NULL,
    recorded_by     VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## 5. pct_orders Schema

```sql
CREATE TABLE pct_orders.order_tracking (
    id              BIGSERIAL PRIMARY KEY,
    tracking_id     UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    encounter_id    UUID NOT NULL REFERENCES pct_core.encounters(encounter_id),
    patient_cpid    VARCHAR(128) NOT NULL,
    order_type      VARCHAR(32) NOT NULL,  -- LAB, IMAGING, PROCEDURE
    oros_order_id   VARCHAR(128),  -- reference to OROS
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',  -- PENDING, ACCEPTED, IN_PROGRESS, COMPLETED, CANCELLED
    ordered_by      VARCHAR(128),
    ordered_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    items           JSONB,  -- order line items
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE pct_orders.order_results (
    id              BIGSERIAL PRIMARY KEY,
    result_id       UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    tracking_id     UUID NOT NULL REFERENCES pct_orders.order_tracking(tracking_id),
    result_type     VARCHAR(32),
    value           JSONB NOT NULL,  -- result data
    status          VARCHAR(32) DEFAULT 'AVAILABLE',  -- AVAILABLE, REVIEWED, RELEASED
    reviewed_by     VARCHAR(128),
    reviewed_at     TIMESTAMPTZ,
    butano_ref      VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE pct_orders.prescription_tracking (
    id                      BIGSERIAL PRIMARY KEY,
    tracking_id             UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id               UUID NOT NULL,
    encounter_id            UUID NOT NULL REFERENCES pct_core.encounters(encounter_id),
    patient_cpid            VARCHAR(128) NOT NULL,
    pharmacy_dispense_id    VARCHAR(128),  -- reference to Pharmacy
    medication_code         VARCHAR(64),
    medication_name         VARCHAR(255),
    dosage                  VARCHAR(128),
    frequency               VARCHAR(64),
    duration                VARCHAR(64),
    quantity                INT,
    status                  VARCHAR(32) NOT NULL DEFAULT 'PRESCRIBED',  -- PRESCRIBED, DISPENSED, PARTIALLY_DISPENSED, CANCELLED
    prescribed_by           VARCHAR(128),
    prescribed_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    dispensed_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## 6. pct_queue Schema

```sql
CREATE TABLE pct_queue.queue_definitions (
    id              BIGSERIAL PRIMARY KEY,
    queue_def_id    UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    facility_id     UUID NOT NULL,
    service_point   VARCHAR(64) NOT NULL,
    queue_type      VARCHAR(32) DEFAULT 'FIFO',  -- FIFO, PRIORITY, SCHEDULED
    active          BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE pct_queue.queue_items (
    id              BIGSERIAL PRIMARY KEY,
    item_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    queue_def_id    UUID NOT NULL REFERENCES pct_queue.queue_definitions(queue_def_id),
    journey_id      UUID NOT NULL REFERENCES pct_core.journeys(journey_id),
    patient_cpid    VARCHAR(128) NOT NULL,
    priority        INT NOT NULL DEFAULT 5,  -- 1=highest
    position        INT,
    status          VARCHAR(32) DEFAULT 'WAITING',  -- WAITING, CALLED, IN_SERVICE, COMPLETED, NO_SHOW
    waited_since    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    called_at       TIMESTAMPTZ,
    served_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE pct_queue.triage_records (
    id              BIGSERIAL PRIMARY KEY,
    triage_id       UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    journey_id      UUID NOT NULL REFERENCES pct_core.journeys(journey_id),
    patient_cpid    VARCHAR(128) NOT NULL,
    category        VARCHAR(16) NOT NULL,  -- RED, ORANGE, YELLOW, GREEN, BLUE
    acuity_score    INT,
    chief_complaint VARCHAR(512),
    triaged_by      VARCHAR(128),
    triaged_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## 7. pct_outbox Schema

Standard v1.1 outbox per shared-kernel-java EventEnvelope contract.

```sql
CREATE TABLE pct_outbox.event_outbox (
    id                  BIGSERIAL PRIMARY KEY,
    event_id            UUID NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type      VARCHAR(64) NOT NULL,
    aggregate_id        VARCHAR(255) NOT NULL,
    event_type          VARCHAR(128) NOT NULL,
    schema_version      INT NOT NULL DEFAULT 1,
    correlation_id      UUID,
    causation_id        UUID,
    idempotency_key     VARCHAR(255) NOT NULL,
    producer            VARCHAR(64) NOT NULL DEFAULT 'pct-service',
    tenant_id           UUID NOT NULL,
    pod_id              VARCHAR(64) NOT NULL DEFAULT 'national-spine',
    subject_id          VARCHAR(255) NOT NULL,
    subject_type        VARCHAR(64) NOT NULL,
    partition_key       VARCHAR(255),
    occurred_at         TIMESTAMPTZ NOT NULL,
    payload_json        JSONB NOT NULL,
    publish_error       TEXT,
    retry_count         INT DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at        TIMESTAMPTZ,
    CONSTRAINT uq_pct_outbox_idempotency UNIQUE (idempotency_key)
);
CREATE INDEX idx_pct_outbox_unpublished ON pct_outbox.event_outbox (created_at) WHERE published_at IS NULL;
```

---

## 8. Redis Cache Structure

```
pct:queue:{facility_id}:{service_point}    → sorted set (priority, item_id)
pct:encounter:active:{patient_cpid}        → hash (encounter_id, provider, workspace, started_at)
pct:session:{provider_id}                  → hash (facility_id, workspace_id, shift_id, started_at)
pct:orders:pending:{encounter_id}          → counter (pending order count)
pct:beds:{facility_id}:{ward_id}           → hash (bed_id → status, patient_cpid)
```

All keys expire or are explicitly invalidated on state transitions.
Redis is cache; PostgreSQL is truth. On Redis failure, PCT rebuilds from Postgres.

---

## 9. BUTANO Publication Events

PCT writes to pct_outbox. The outbox publisher emits to Kafka. BUTANO consumes and creates FHIR resources.

| PCT Event | BUTANO Action |
|-----------|--------------|
| clinical.pct.allergy.recorded.v1 | Create AllergyIntolerance |
| clinical.pct.condition.recorded.v1 | Create/Update Condition |
| clinical.pct.vital.recorded.v1 | Create Observation (vital-signs) |
| clinical.pct.immunization.recorded.v1 | Create Immunization |
| clinical.pct.note.recorded.v1 | Create DocumentReference |
| clinical.pct.encounter.completed.v1 | Create/Update Encounter |
| clinical.pct.care_plan.created.v1 | Create CarePlan |
| clinical.pct.order.completed.v1 | Create DiagnosticReport |
| clinical.pct.prescription.dispensed.v1 | Create MedicationDispense |
| clinical.pct.growth.recorded.v1 | Create Observation |
| clinical.pct.ews.recorded.v1 | Create Observation |

BUTANO's existing consumer infrastructure (refactored in commit 92c61d0) handles ingestion.
