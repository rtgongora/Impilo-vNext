CREATE TABLE rx_prescriptions (
    prescription_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL,
    facility_id          UUID NOT NULL,
    workspace_id         UUID,
    patient_cpid         VARCHAR(128) NOT NULL,
    encounter_id         VARCHAR(128),
    medication_name      VARCHAR(255) NOT NULL,
    generic_name         VARCHAR(255),
    dosage               VARCHAR(128),
    route                VARCHAR(64),
    frequency            VARCHAR(128),
    duration             VARCHAR(128),
    quantity             INTEGER,
    instructions         TEXT,
    indication           TEXT,
    prescribed_by        VARCHAR(128) NOT NULL,
    status               VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    cancel_reason        TEXT,
    cancelled_by         VARCHAR(128),
    cancelled_at         TIMESTAMPTZ,
    dispensed_by         VARCHAR(128),
    dispensed_at         TIMESTAMPTZ,
    refill_requested_at  TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_rx_prescriptions_patient_created
    ON rx_prescriptions(patient_cpid, created_at DESC);

CREATE INDEX idx_rx_prescriptions_tenant_patient_status
    ON rx_prescriptions(tenant_id, patient_cpid, status, created_at DESC);
