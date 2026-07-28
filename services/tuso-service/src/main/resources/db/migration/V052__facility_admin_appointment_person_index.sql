-- Cross-facility "which facility-management appointments does this person hold"
-- read, needed by the experience-bff work-context resolver's
-- FacilityAdminContextSource (Phase C) — today facility_admin_appointment can
-- only be queried per-facility or cross-facility-by-state, never by person.

CREATE INDEX IF NOT EXISTS idx_facility_admin_appointment_person_state
    ON tuso.facility_admin_appointment (person_health_id, approval_state);
