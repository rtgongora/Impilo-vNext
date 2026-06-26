-- V026: Facility-context scope on enrolment (B3). Additive/nullable.
-- facility_id references a TUSO facility BY ID only — TUSO remains the SoR for facility
-- identity; learning stores the reference for facility-readiness reporting/scoping.

ALTER TABLE lrn_enrolment ADD COLUMN IF NOT EXISTS facility_id UUID NULL;
CREATE INDEX IF NOT EXISTS idx_lrn_enrolment_facility ON lrn_enrolment (tenant_id, facility_id);
