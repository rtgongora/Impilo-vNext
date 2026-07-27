-- FCV-W0d — one pending claim per person, per role, per facility.
--
-- V017/V030 constrain only ACTIVE appointments: UNIQUE (facility_uuid, person_health_id, role)
-- WHERE approval_state='ACTIVE'. Nothing stopped the same person filing the same claim repeatedly,
-- so a review queue could fill with duplicates of one request, and approving the second one after
-- the first would trip the ACTIVE index and surface as a raw constraint violation rather than a
-- decision. (Several people holding the same role at one facility is legitimate and stays allowed —
-- this constrains one person's duplicate requests, not the facility's roster.)
--
-- Idempotent and safe on the live estate: facility_admin_appointment currently holds zero rows, and
-- the index is partial, so it can only ever conflict with a genuine duplicate pending claim.
CREATE UNIQUE INDEX IF NOT EXISTS uq_facility_admin_appointment_pending_role
    ON tuso.facility_admin_appointment (facility_uuid, person_health_id, role)
    WHERE approval_state = 'PENDING';
