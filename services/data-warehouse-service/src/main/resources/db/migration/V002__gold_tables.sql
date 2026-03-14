-- ============================================================================
-- data-warehouse-service V002 — Gold analytical tables
-- Provides: materializer watermark, gold encounters, medications, lab results
-- ============================================================================

-- Watermark tracking for materializer replay safety
CREATE TABLE dwh_materializer_watermark (
    watermark_id        BIGSERIAL       PRIMARY KEY,
    source_table        VARCHAR(128)    NOT NULL UNIQUE,
    last_receipt_id     UUID,
    last_stored_at      TIMESTAMPTZ,
    processed_count     BIGINT          NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Gold encounters
CREATE TABLE dwh_gold_encounter (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    encounter_id        VARCHAR(255)    NOT NULL,
    patient_id          VARCHAR(255)    NOT NULL,
    facility_id         VARCHAR(255),
    encounter_type      VARCHAR(128),
    encounter_status    VARCHAR(64),
    start_date          TIMESTAMPTZ,
    end_date            TIMESTAMPTZ,
    provider_id         VARCHAR(255),
    diagnosis_codes     TEXT,
    source_event_id     VARCHAR(255)    NOT NULL,
    source_event_type   VARCHAR(128)    NOT NULL,
    materialized_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_dwh_gold_encounter UNIQUE (tenant_id, encounter_id)
);

CREATE INDEX idx_dwh_gold_enc_tenant ON dwh_gold_encounter (tenant_id);
CREATE INDEX idx_dwh_gold_enc_patient ON dwh_gold_encounter (patient_id);
CREATE INDEX idx_dwh_gold_enc_facility ON dwh_gold_encounter (facility_id);
CREATE INDEX idx_dwh_gold_enc_start ON dwh_gold_encounter (start_date);

-- Gold medications
CREATE TABLE dwh_gold_medication (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    medication_id       VARCHAR(255)    NOT NULL,
    patient_id          VARCHAR(255)    NOT NULL,
    encounter_id        VARCHAR(255),
    facility_id         VARCHAR(255),
    drug_code           VARCHAR(128),
    drug_name           VARCHAR(512),
    dosage              VARCHAR(255),
    frequency           VARCHAR(128),
    route               VARCHAR(128),
    prescribed_date     TIMESTAMPTZ,
    dispensed_date      TIMESTAMPTZ,
    prescriber_id       VARCHAR(255),
    source_event_id     VARCHAR(255)    NOT NULL,
    source_event_type   VARCHAR(128)    NOT NULL,
    materialized_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_dwh_gold_medication UNIQUE (tenant_id, medication_id)
);

CREATE INDEX idx_dwh_gold_med_tenant ON dwh_gold_medication (tenant_id);
CREATE INDEX idx_dwh_gold_med_patient ON dwh_gold_medication (patient_id);
CREATE INDEX idx_dwh_gold_med_drug ON dwh_gold_medication (drug_code);

-- Gold lab results
CREATE TABLE dwh_gold_lab (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    lab_result_id       VARCHAR(255)    NOT NULL,
    patient_id          VARCHAR(255)    NOT NULL,
    encounter_id        VARCHAR(255),
    facility_id         VARCHAR(255),
    test_code           VARCHAR(128),
    test_name           VARCHAR(512),
    result_value        VARCHAR(512),
    result_unit         VARCHAR(64),
    reference_range     VARCHAR(255),
    result_status       VARCHAR(64),
    collected_date      TIMESTAMPTZ,
    reported_date       TIMESTAMPTZ,
    source_event_id     VARCHAR(255)    NOT NULL,
    source_event_type   VARCHAR(128)    NOT NULL,
    materialized_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_dwh_gold_lab UNIQUE (tenant_id, lab_result_id)
);

CREATE INDEX idx_dwh_gold_lab_tenant ON dwh_gold_lab (tenant_id);
CREATE INDEX idx_dwh_gold_lab_patient ON dwh_gold_lab (patient_id);
CREATE INDEX idx_dwh_gold_lab_test ON dwh_gold_lab (test_code);

INSERT INTO dwh_materializer_watermark (source_table) VALUES ('din_bronze_event');
