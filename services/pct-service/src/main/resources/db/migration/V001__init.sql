-- =============================================
-- Service : pct-service
-- Flyway  : V001__init.sql
-- Purpose : Complete PCT schema — 13 tables for
--           patient journey tracking, queueing,
--           encounters, admissions, transfers,
--           discharges, death cases, tasks,
--           telemetry, and event outbox.
-- =============================================

CREATE SCHEMA IF NOT EXISTS pct;

-- ─── 1. pct_journeys ────────────────────────────────────────────────────────────

CREATE TABLE pct_journeys (
    journey_id      VARCHAR(26)     PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    facility_id     UUID            NOT NULL,
    patient_cpid    VARCHAR(64)     NOT NULL,
    state           VARCHAR(40)     NOT NULL DEFAULT 'ARRIVED',
    referral_source VARCHAR(128),
    referral_id     VARCHAR(128),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_journeys_tenant_facility ON pct_journeys (tenant_id, facility_id);
CREATE INDEX idx_pct_journeys_tenant_cpid ON pct_journeys (tenant_id, patient_cpid);
CREATE INDEX idx_pct_journeys_tenant_state ON pct_journeys (tenant_id, state);

-- ─── 2. pct_encounters ─────────────────────────────────────────────────────────

CREATE TABLE pct_encounters (
    id                      BIGSERIAL       PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    journey_id              VARCHAR(26)     NOT NULL REFERENCES pct_journeys(journey_id),
    butano_encounter_ref    VARCHAR(128),
    status                  VARCHAR(30)     NOT NULL DEFAULT 'STARTED',
    workspace_id            UUID,
    assigned_provider_id    VARCHAR(128),
    encounter_type          VARCHAR(50),
    started_at              TIMESTAMPTZ     DEFAULT now(),
    ended_at                TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_encounters_tenant_journey ON pct_encounters (tenant_id, journey_id);
CREATE INDEX idx_pct_encounters_tenant_status ON pct_encounters (tenant_id, status);

-- ─── 3. pct_workspace_sessions ──────────────────────────────────────────────────

CREATE TABLE pct_workspace_sessions (
    session_id  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID            NOT NULL,
    actor_id    VARCHAR(128)    NOT NULL,
    facility_id UUID            NOT NULL,
    workspace_id UUID           NOT NULL,
    shift_id    VARCHAR(128),
    duty_mode   VARCHAR(20)     NOT NULL DEFAULT 'CLINICAL',
    started_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    ended_at    TIMESTAMPTZ
);

CREATE INDEX idx_pct_ws_sessions_tenant_actor ON pct_workspace_sessions (tenant_id, actor_id, ended_at);
CREATE INDEX idx_pct_ws_sessions_tenant_facility_ws ON pct_workspace_sessions (tenant_id, facility_id, workspace_id);

-- ─── 4. pct_queues ──────────────────────────────────────────────────────────────

CREATE TABLE pct_queues (
    queue_id        UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    facility_id     UUID            NOT NULL,
    workspace_id    UUID,
    name            VARCHAR(128)    NOT NULL,
    queue_type      VARCHAR(20)     NOT NULL DEFAULT 'FIFO',
    rules           JSONB           DEFAULT '{}',
    active          BOOLEAN         NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_queues_tenant_facility ON pct_queues (tenant_id, facility_id);
CREATE INDEX idx_pct_queues_tenant_workspace ON pct_queues (tenant_id, workspace_id);

-- ─── 5. pct_queue_items ─────────────────────────────────────────────────────────

CREATE TABLE pct_queue_items (
    item_id             UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    queue_id            UUID            NOT NULL REFERENCES pct_queues(queue_id),
    journey_id          VARCHAR(26)     NOT NULL REFERENCES pct_journeys(journey_id),
    patient_cpid        VARCHAR(64)     NOT NULL,
    priority            INT             NOT NULL DEFAULT 0,
    status              VARCHAR(20)     NOT NULL DEFAULT 'WAITING',
    token_number        INT,
    called_by           VARCHAR(128),
    called_at           TIMESTAMPTZ,
    service_started_at  TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_queue_items_tenant_queue_status ON pct_queue_items (tenant_id, queue_id, status);
CREATE INDEX idx_pct_queue_items_tenant_journey ON pct_queue_items (tenant_id, journey_id);

-- ─── 6. pct_triage_records ──────────────────────────────────────────────────────

CREATE TABLE pct_triage_records (
    id          BIGSERIAL       PRIMARY KEY,
    tenant_id   UUID            NOT NULL,
    journey_id  VARCHAR(26)     NOT NULL REFERENCES pct_journeys(journey_id),
    acuity      INT             NOT NULL CHECK (acuity BETWEEN 1 AND 5),
    vitals      JSONB           DEFAULT '{}',
    notes       TEXT,
    triaged_by  VARCHAR(128)    NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_triage_records_tenant_journey ON pct_triage_records (tenant_id, journey_id);

-- ─── 7. pct_admissions ──────────────────────────────────────────────────────────

CREATE TABLE pct_admissions (
    admission_id    UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    journey_id      VARCHAR(26)     NOT NULL REFERENCES pct_journeys(journey_id),
    ward_id         UUID,
    bed_id          UUID,
    status          VARCHAR(30)     NOT NULL DEFAULT 'REQUESTED',
    admitted_by     VARCHAR(128),
    admitted_at     TIMESTAMPTZ,
    discharged_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_admissions_tenant_journey ON pct_admissions (tenant_id, journey_id);
CREATE INDEX idx_pct_admissions_tenant_status ON pct_admissions (tenant_id, status);
CREATE INDEX idx_pct_admissions_tenant_ward_bed_active ON pct_admissions (tenant_id, ward_id, bed_id) WHERE status = 'ADMITTED';

-- ─── 8. pct_transfers ───────────────────────────────────────────────────────────

CREATE TABLE pct_transfers (
    transfer_id     UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    journey_id      VARCHAR(26)     NOT NULL REFERENCES pct_journeys(journey_id),
    from_ward_id    UUID,
    from_bed_id     UUID,
    to_ward_id      UUID,
    to_bed_id       UUID,
    transfer_type   VARCHAR(30)     NOT NULL DEFAULT 'INTERNAL',
    status          VARCHAR(20)     NOT NULL DEFAULT 'REQUESTED',
    requested_by    VARCHAR(128)    NOT NULL,
    approved_by     VARCHAR(128),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_transfers_tenant_journey ON pct_transfers (tenant_id, journey_id);
CREATE INDEX idx_pct_transfers_tenant_status ON pct_transfers (tenant_id, status);

-- ─── 9. pct_discharge_cases ─────────────────────────────────────────────────────

CREATE TABLE pct_discharge_cases (
    case_id             UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    journey_id          VARCHAR(26)     NOT NULL REFERENCES pct_journeys(journey_id),
    discharge_type      VARCHAR(30)     NOT NULL DEFAULT 'CLINICAL',
    blockers            JSONB           DEFAULT '[]',
    clinical_cleared    BOOLEAN         NOT NULL DEFAULT false,
    billing_cleared     BOOLEAN         NOT NULL DEFAULT false,
    pharmacy_cleared    BOOLEAN         NOT NULL DEFAULT false,
    payment_cleared     BOOLEAN         NOT NULL DEFAULT false,
    summary_requested   BOOLEAN         NOT NULL DEFAULT false,
    exit_cleared        BOOLEAN         NOT NULL DEFAULT false,
    closed_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_discharge_cases_tenant_journey ON pct_discharge_cases (tenant_id, journey_id);

-- ─── 10. pct_death_cases ────────────────────────────────────────────────────────

CREATE TABLE pct_death_cases (
    case_id                 UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID            NOT NULL,
    journey_id              VARCHAR(26)     NOT NULL REFERENCES pct_journeys(journey_id),
    pronounced_by           VARCHAR(128)    NOT NULL,
    pronounced_at           TIMESTAMPTZ     NOT NULL,
    ubomi_notification_id   VARCHAR(128),
    ubomi_status            VARCHAR(30),
    cert_doc_id             VARCHAR(128),
    closed_at               TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_death_cases_tenant_journey ON pct_death_cases (tenant_id, journey_id);

-- ─── 11. pct_tasks ──────────────────────────────────────────────────────────────

CREATE TABLE pct_tasks (
    task_id         UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    journey_id      VARCHAR(26)     REFERENCES pct_journeys(journey_id),
    encounter_id    BIGINT          REFERENCES pct_encounters(id),
    task_type       VARCHAR(50)     NOT NULL,
    assignee_id     VARCHAR(128),
    assignee_role   VARCHAR(50),
    workspace_id    UUID,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    due_at          TIMESTAMPTZ,
    notes           TEXT,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_tasks_tenant_assignee_status ON pct_tasks (tenant_id, assignee_id, status);
CREATE INDEX idx_pct_tasks_tenant_journey ON pct_tasks (tenant_id, journey_id);
CREATE INDEX idx_pct_tasks_tenant_workspace_status ON pct_tasks (tenant_id, workspace_id, status);

-- ─── 12. pct_telemetry_events ───────────────────────────────────────────────────

CREATE TABLE pct_telemetry_events (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    facility_id     UUID            NOT NULL,
    workspace_id    UUID,
    event_type      VARCHAR(50)     NOT NULL,
    journey_id      VARCHAR(26),
    payload         JSONB           DEFAULT '{}',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_telemetry_tenant_facility_ts ON pct_telemetry_events (tenant_id, facility_id, created_at);
CREATE INDEX idx_pct_telemetry_tenant_type_ts ON pct_telemetry_events (tenant_id, event_type, created_at);

-- ─── 13. pct_event_outbox ───────────────────────────────────────────────────────

CREATE TABLE pct_event_outbox (
    id              BIGSERIAL       PRIMARY KEY,
    aggregate_type  VARCHAR(128)    NOT NULL,
    aggregate_id    VARCHAR(128)    NOT NULL,
    event_type      VARCHAR(128)    NOT NULL,
    payload         JSONB           NOT NULL,
    tenant_id       UUID            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_pct_outbox_unpublished ON pct_event_outbox (created_at) WHERE published_at IS NULL;
CREATE INDEX idx_pct_outbox_tenant_ts ON pct_event_outbox (tenant_id, created_at);
