-- Facility appointment scheduling (citizen self-booking + provider scheduling)
CREATE TABLE tuso.appointment (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    facility_id     BIGINT          NOT NULL REFERENCES tuso.facility(id),
    patient_id      VARCHAR(255),
    patient_cpid    VARCHAR(255)    NOT NULL,
    provider_id     VARCHAR(255),
    provider_name   VARCHAR(255),
    appointment_type VARCHAR(50)    NOT NULL,
    status          VARCHAR(30)     NOT NULL DEFAULT 'SCHEDULED',
    scheduled_at    TIMESTAMPTZ     NOT NULL,
    end_at          TIMESTAMPTZ,
    reason          TEXT,
    notes           TEXT,
    resource_id     UUID            REFERENCES tuso.resource(id),
    created_by      VARCHAR(255)    NOT NULL,
    cancelled_by    VARCHAR(255),
    cancelled_at    TIMESTAMPTZ,
    cancel_reason   TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ
);

CREATE INDEX idx_appt_tenant_cpid ON tuso.appointment (tenant_id, patient_cpid, scheduled_at DESC);
CREATE INDEX idx_appt_facility_time ON tuso.appointment (facility_id, scheduled_at)
    WHERE status NOT IN ('CANCELLED');
CREATE INDEX idx_appt_patient ON tuso.appointment (tenant_id, patient_id, scheduled_at DESC);
