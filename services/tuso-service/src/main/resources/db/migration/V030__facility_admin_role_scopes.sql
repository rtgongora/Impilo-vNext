-- =============================================================================
-- Tuso V030 — Role-scoped, expiring facility-admin appointments + anti-enum
-- claim hardening (Place Journey Doctrine §2/FJ1/FJ8, D-L4; Provider+Place W4)
--
-- Extends the V017 append-only appointment rail:
--   * role becomes a CLOSED vocabulary of management scopes (viewer → admin);
--   * one ACTIVE appointment per (facility, person, ROLE) — a person may hold
--     several distinct roles at one facility;
--   * appointments EXPIRE when valid_to passes (sweep job; ACTIVE → EXPIRED);
--   * claim_type records NEW vs RECOVERY (FJ8: recovery re-establishes access
--     on the SAME facility with stronger assurance — never a new record).
--
-- The eligibility/preview enumeration leak (legitimacy verdicts + administrator
-- counts disclosed to unproven claimants) is closed in FacilityClaimService in
-- the same wave: submission is generic; verdicts live in the steward review.
-- =============================================================================

-- Closed role vocabulary. NOT VALID: legacy rows keep historic values; every
-- new write must use the closed set.
ALTER TABLE tuso.facility_admin_appointment
    ADD CONSTRAINT chk_facility_admin_role CHECK (role IN (
        'FACILITY_VIEWER', 'DATA_STEWARD', 'SERVICE_CONFIG_MANAGER',
        'FACILITY_ADMINISTRATOR', 'REGULATORY_LIAISON')) NOT VALID;

ALTER TABLE tuso.facility_admin_appointment
    ADD COLUMN IF NOT EXISTS claim_type VARCHAR(16) NOT NULL DEFAULT 'NEW';

COMMENT ON COLUMN tuso.facility_admin_appointment.claim_type IS
    'NEW (first claim) | RECOVERY (FJ8: re-establish access to the same facility; stronger assurance, never a new record).';

-- Per-role ACTIVE uniqueness replaces the per-person one: several roles may be
-- held concurrently; the same role may not be duplicated while ACTIVE.
DROP INDEX IF EXISTS tuso.uq_facility_admin_appointment_active;
CREATE UNIQUE INDEX IF NOT EXISTS uq_facility_admin_appointment_active_role
    ON tuso.facility_admin_appointment (facility_uuid, person_health_id, role)
    WHERE approval_state = 'ACTIVE';

-- Expiry sweep support (ACTIVE + valid_to < today → EXPIRED).
CREATE INDEX IF NOT EXISTS idx_facility_admin_appointment_expiry
    ON tuso.facility_admin_appointment (valid_to)
    WHERE approval_state = 'ACTIVE' AND valid_to IS NOT NULL;

COMMENT ON COLUMN tuso.facility_admin_appointment.approval_state IS
    'PENDING (submitted, awaiting review) | ACTIVE (approved) | REJECTED | REVOKED | EXPIRED (valid_to passed; sweep-applied).';
