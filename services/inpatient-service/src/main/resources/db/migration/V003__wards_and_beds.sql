-- Ward and bed inventory for inpatient bed management (sovereign source for BFF /internal/v1/beds).

CREATE TABLE inpatient.ward (
    id              UUID            PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    facility_id     UUID            NOT NULL,
    name            VARCHAR(200)    NOT NULL,
    ward_type       VARCHAR(50)     NOT NULL DEFAULT 'GENERAL',
    floor_label     VARCHAR(50),
    total_beds      INTEGER         NOT NULL DEFAULT 0,
    status          VARCHAR(30)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_ward_facility ON inpatient.ward (tenant_id, facility_id);

CREATE TABLE inpatient.bed (
    id                  UUID            PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    facility_id         UUID            NOT NULL,
    ward_id             UUID            NOT NULL REFERENCES inpatient.ward (id),
    bed_number          VARCHAR(50)     NOT NULL,
    bed_type            VARCHAR(50)     NOT NULL DEFAULT 'STANDARD',
    status              VARCHAR(30)     NOT NULL DEFAULT 'AVAILABLE',
    subject_cpid        VARCHAR(64),
    patient_name        VARCHAR(255),
    patient_diagnosis   TEXT,
    attending_physician VARCHAR(255),
    acuity_level        VARCHAR(20),
    patient_age         INTEGER,
    patient_gender      VARCHAR(20),
    occupied_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (ward_id, bed_number)
);

CREATE INDEX idx_bed_facility ON inpatient.bed (tenant_id, facility_id);
CREATE INDEX idx_bed_ward ON inpatient.bed (ward_id);
CREATE INDEX idx_bed_status ON inpatient.bed (status);
