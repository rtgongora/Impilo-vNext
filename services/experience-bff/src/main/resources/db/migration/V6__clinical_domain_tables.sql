-- ============================================================================
-- Experience BFF — Clinical Domain Tables
-- V6: Vitals, Clinical Notes, Lab Orders, Lab Results (encounter-linked),
--     Referrals, Allergies, Conditions, Immunizations, Documents, Timeline
-- ============================================================================

-- Vitals records (individual vitals captures linked to encounters)
CREATE TABLE vitals_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL REFERENCES patients(id),
    encounter_id    UUID NOT NULL REFERENCES encounters(id),
    recorded_by     VARCHAR(255) NOT NULL,
    systolic        INTEGER,
    diastolic       INTEGER,
    heart_rate      INTEGER,
    temperature     DECIMAL(5,2),
    respiratory_rate INTEGER,
    oxygen_saturation DECIMAL(5,2),
    weight          DECIMAL(6,2),
    height          DECIMAL(5,2),
    bmi             DECIMAL(5,2),
    pain_score      INTEGER,
    notes           TEXT,
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_vitals_tenant ON vitals_records (tenant_id);
CREATE INDEX idx_vitals_patient ON vitals_records (patient_id);
CREATE INDEX idx_vitals_encounter ON vitals_records (encounter_id);

-- Clinical notes (SOAP/free-text notes linked to encounters)
CREATE TABLE clinical_notes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL REFERENCES patients(id),
    encounter_id    UUID REFERENCES encounters(id),
    note_type       VARCHAR(50) NOT NULL DEFAULT 'PROGRESS',
    subjective      TEXT,
    objective        TEXT,
    assessment      TEXT,
    plan            TEXT,
    body            TEXT,
    author_id       VARCHAR(255) NOT NULL,
    author_name     VARCHAR(500) NOT NULL DEFAULT '',
    status          VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    signed_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_clinical_notes_tenant ON clinical_notes (tenant_id);
CREATE INDEX idx_clinical_notes_patient ON clinical_notes (patient_id);
CREATE INDEX idx_clinical_notes_encounter ON clinical_notes (encounter_id);

-- Lab orders (orders placed during encounters)
CREATE TABLE lab_orders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL REFERENCES patients(id),
    encounter_id    UUID REFERENCES encounters(id),
    order_number    VARCHAR(100),
    test_name       VARCHAR(500) NOT NULL,
    test_code       VARCHAR(100),
    category        VARCHAR(100) NOT NULL DEFAULT 'LABORATORY',
    priority        VARCHAR(50) NOT NULL DEFAULT 'ROUTINE',
    status          VARCHAR(50) NOT NULL DEFAULT 'ORDERED',
    clinical_notes  TEXT,
    ordered_by      VARCHAR(255) NOT NULL,
    ordered_by_name VARCHAR(500) NOT NULL DEFAULT '',
    facility_id     UUID NOT NULL,
    collected_at    TIMESTAMPTZ,
    resulted_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_lab_orders_tenant ON lab_orders (tenant_id);
CREATE INDEX idx_lab_orders_patient ON lab_orders (patient_id);
CREATE INDEX idx_lab_orders_encounter ON lab_orders (encounter_id);
CREATE INDEX idx_lab_orders_status ON lab_orders (tenant_id, status);

-- Lab order results (individual result values for lab orders)
CREATE TABLE lab_order_results (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    lab_order_id    UUID NOT NULL REFERENCES lab_orders(id),
    analyte_name    VARCHAR(500) NOT NULL,
    analyte_code    VARCHAR(100),
    value           VARCHAR(500),
    unit            VARCHAR(100),
    reference_low   VARCHAR(100),
    reference_high  VARCHAR(100),
    interpretation  VARCHAR(50),
    abnormal_flag   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_lab_order_results_order ON lab_order_results (lab_order_id);

-- Referrals (clinical referrals)
CREATE TABLE referrals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL REFERENCES patients(id),
    encounter_id    UUID REFERENCES encounters(id),
    referral_type   VARCHAR(100) NOT NULL DEFAULT 'SPECIALIST',
    specialty       VARCHAR(255),
    referred_to     VARCHAR(500),
    referred_to_facility VARCHAR(500),
    reason          TEXT NOT NULL,
    urgency         VARCHAR(50) NOT NULL DEFAULT 'ROUTINE',
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    clinical_summary TEXT,
    referred_by     VARCHAR(255) NOT NULL,
    referred_by_name VARCHAR(500) NOT NULL DEFAULT '',
    scheduled_at    TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    outcome         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_referrals_tenant ON referrals (tenant_id);
CREATE INDEX idx_referrals_patient ON referrals (patient_id);
CREATE INDEX idx_referrals_encounter ON referrals (encounter_id);
CREATE INDEX idx_referrals_status ON referrals (tenant_id, status);

-- Allergies (patient allergy list)
CREATE TABLE allergies (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL REFERENCES patients(id),
    allergen        VARCHAR(500) NOT NULL,
    allergen_type   VARCHAR(100) NOT NULL DEFAULT 'MEDICATION',
    reaction        TEXT,
    severity        VARCHAR(50) NOT NULL DEFAULT 'MODERATE',
    status          VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    onset_date      DATE,
    recorded_by     VARCHAR(255) NOT NULL,
    verified_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_allergies_tenant ON allergies (tenant_id);
CREATE INDEX idx_allergies_patient ON allergies (patient_id);

-- Conditions (problem list / diagnoses)
CREATE TABLE conditions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL REFERENCES patients(id),
    encounter_id    UUID REFERENCES encounters(id),
    condition_name  VARCHAR(500) NOT NULL,
    icd_code        VARCHAR(20),
    category        VARCHAR(100) NOT NULL DEFAULT 'ENCOUNTER_DIAGNOSIS',
    clinical_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    severity        VARCHAR(50) NOT NULL DEFAULT 'MODERATE',
    onset_date      DATE,
    abatement_date  DATE,
    recorded_by     VARCHAR(255) NOT NULL,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_conditions_tenant ON conditions (tenant_id);
CREATE INDEX idx_conditions_patient ON conditions (patient_id);
CREATE INDEX idx_conditions_encounter ON conditions (encounter_id);

-- Immunizations (vaccination records)
CREATE TABLE immunizations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL REFERENCES patients(id),
    encounter_id    UUID REFERENCES encounters(id),
    vaccine_name    VARCHAR(500) NOT NULL,
    vaccine_code    VARCHAR(100),
    dose_number     INTEGER,
    dose_sequence   VARCHAR(50),
    lot_number      VARCHAR(100),
    site            VARCHAR(100),
    route           VARCHAR(100),
    status          VARCHAR(50) NOT NULL DEFAULT 'COMPLETED',
    administered_by VARCHAR(255) NOT NULL,
    administered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expiration_date DATE,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_immunizations_tenant ON immunizations (tenant_id);
CREATE INDEX idx_immunizations_patient ON immunizations (patient_id);

-- Clinical documents (uploaded documents)
CREATE TABLE clinical_documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL REFERENCES patients(id),
    encounter_id    UUID REFERENCES encounters(id),
    document_type   VARCHAR(100) NOT NULL DEFAULT 'CLINICAL_NOTE',
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    mime_type       VARCHAR(100) NOT NULL DEFAULT 'application/pdf',
    file_size       BIGINT,
    storage_key     VARCHAR(500),
    uploaded_by     VARCHAR(255) NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_clinical_documents_tenant ON clinical_documents (tenant_id);
CREATE INDEX idx_clinical_documents_patient ON clinical_documents (patient_id);

-- Clinical timeline entries (aggregated timeline view)
CREATE TABLE clinical_timeline (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL REFERENCES patients(id),
    encounter_id    UUID REFERENCES encounters(id),
    event_type      VARCHAR(100) NOT NULL,
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    source_type     VARCHAR(100) NOT NULL,
    source_id       UUID NOT NULL,
    actor_id        VARCHAR(255),
    actor_name      VARCHAR(500),
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_clinical_timeline_tenant ON clinical_timeline (tenant_id);
CREATE INDEX idx_clinical_timeline_patient ON clinical_timeline (patient_id, occurred_at DESC);
CREATE INDEX idx_clinical_timeline_encounter ON clinical_timeline (encounter_id);
