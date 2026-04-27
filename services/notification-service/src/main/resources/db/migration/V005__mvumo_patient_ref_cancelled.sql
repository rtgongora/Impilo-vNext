-- V005: Patient-scoped queue rows for Mvumo preference gating and cancellation
ALTER TABLE ns_notifications ADD COLUMN patient_ref VARCHAR(300);

CREATE INDEX idx_ns_notifications_patient_pending
    ON ns_notifications (tenant_id, patient_ref, status)
    WHERE patient_ref IS NOT NULL;

COMMENT ON COLUMN ns_notifications.patient_ref IS 'FHIR-style Patient/CPID or CPID; used for preference gate + Kafka-triggered queue cancellation';
