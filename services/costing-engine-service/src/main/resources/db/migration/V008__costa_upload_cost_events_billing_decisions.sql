-- Tariff upload pipeline (batch + row validation), automated cost events, adjunct billing decisions.

CREATE TABLE costa_tariff_upload_batches (
    upload_batch_id     UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    file_name           VARCHAR(500)    NOT NULL,
    file_type           VARCHAR(20)     NOT NULL,
    uploaded_by         VARCHAR(120),
    owner_organisation  VARCHAR(300),
    proposed_tariff_name VARCHAR(400)   NOT NULL,
    proposed_country    VARCHAR(80),
    proposed_currency   VARCHAR(3)      NOT NULL DEFAULT 'USD',
    upload_status       VARCHAR(40)     NOT NULL DEFAULT 'RECEIVED',
    validation_status   VARCHAR(40)     NOT NULL DEFAULT 'unvalidated',
    column_mapping      JSONB           NOT NULL DEFAULT '{}',
    rows_total          INT             NOT NULL DEFAULT 0,
    rows_valid          INT             NOT NULL DEFAULT 0,
    rows_with_warnings  INT             NOT NULL DEFAULT 0,
    rows_with_errors    INT             NOT NULL DEFAULT 0,
    raw_bytes           BYTEA,
    raw_text            TEXT,
    metadata            JSONB           NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ
);

CREATE INDEX idx_tariff_upload_batches_tenant ON costa_tariff_upload_batches (tenant_id, created_at DESC);

CREATE TABLE costa_tariff_upload_rows (
    id                  BIGSERIAL       PRIMARY KEY,
    upload_batch_id     UUID            NOT NULL REFERENCES costa_tariff_upload_batches(upload_batch_id) ON DELETE CASCADE,
    row_number          INT             NOT NULL,
    raw_payload         JSONB           NOT NULL,
    normalized_payload  JSONB,
    severity            VARCHAR(20)     NOT NULL DEFAULT 'unknown',
    messages            JSONB           NOT NULL DEFAULT '[]',
    UNIQUE (upload_batch_id, row_number)
);

CREATE INDEX idx_tariff_upload_rows_batch ON costa_tariff_upload_rows (upload_batch_id);

CREATE TABLE costa_cost_events (
    cost_event_id           VARCHAR(26)     PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    patient_cpid            VARCHAR(80),
    encounter_id            VARCHAR(80),
    facility_id             UUID,
    workspace_id            UUID,
    provider_id             UUID,
    provider_role           VARCHAR(120),
    event_type              VARCHAR(120)    NOT NULL,
    event_source            VARCHAR(120)    NOT NULL,
    service_domain          VARCHAR(120),
    service_category        VARCHAR(120),
    start_time              TIMESTAMPTZ,
    end_time                TIMESTAMPTZ,
    duration_minutes        INT,
    quantity                NUMERIC(14,4)   NOT NULL DEFAULT 1,
    unit_of_measure         VARCHAR(40),
    linked_order_id         VARCHAR(80),
    linked_procedure_id     VARCHAR(80),
    linked_charge_sheet_id  VARCHAR(26),
    costing_status          VARCHAR(40)     NOT NULL DEFAULT 'captured',
    metadata                JSONB           NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by              VARCHAR(120)
);

CREATE INDEX idx_cost_events_tenant_encounter ON costa_cost_events (tenant_id, encounter_id);
CREATE INDEX idx_cost_events_tenant_patient ON costa_cost_events (tenant_id, patient_cpid);
CREATE INDEX idx_cost_events_tenant_created ON costa_cost_events (tenant_id, created_at DESC);

CREATE TABLE costa_billing_decisions (
    billing_decision_id   UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID            NOT NULL,
    cost_estimate_id        UUID            REFERENCES costa_cost_estimates(cost_estimate_id),
    invoice_id              VARCHAR(26),
    claim_id                VARCHAR(80),
    gross_tariffed_amount   NUMERIC(14,2),
    patient_liability       NUMERIC(14,2),
    payer_liability         NUMERIC(14,2),
    exempted_amount         NUMERIC(14,2),
    waived_amount           NUMERIC(14,2)   NOT NULL DEFAULT 0,
    subsidised_amount       NUMERIC(14,2),
    unrecovered_amount      NUMERIC(14,2),
    reason_codes            JSONB           NOT NULL DEFAULT '[]',
    rules_applied           JSONB           NOT NULL DEFAULT '{}',
    lines                   JSONB           NOT NULL DEFAULT '[]',
    approval_required       BOOLEAN         NOT NULL DEFAULT false,
    audit_reference         VARCHAR(120),
    metadata                JSONB           NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_billing_decisions_tenant_estimate ON costa_billing_decisions (tenant_id, cost_estimate_id);
