-- Facility administrator appointment (IATG Wave 2, WS-E facility-claim lane).
--
-- Mirrors tuso.practitioner_in_charge_assignment (facility FK + role + approval_state PENDING),
-- but keys the subject on the CANONICAL facility UUID (tuso.facility.facility_uuid, V013) rather
-- than a Health ID: a facility is administered, it is not an identity that gets rebound. A
-- facility may have MANY administrators over time, so this is an append-only appointment record,
-- never a rebind of a single anchor.
--
-- Lifecycle: SUBMIT creates a PENDING row; APPROVE flips it to ACTIVE. Every transition emits a
-- tuso.event_outbox event (tuso.facility.admin_appointment.submitted / .approved). Eligibility to
-- submit is gated on the facility's FacilitySourceLegitimacy.allowed_on_platform verdict (V016) —
-- a facility not allowed on the platform is not claimable.

CREATE TABLE IF NOT EXISTS tuso.facility_admin_appointment (
    id                  BIGSERIAL PRIMARY KEY,
    facility_uuid       UUID NOT NULL,
    person_health_id    VARCHAR(128) NOT NULL,
    role                VARCHAR(64) NOT NULL DEFAULT 'FACILITY_ADMINISTRATOR',
    appointed_by        VARCHAR(255),
    approval_state      VARCHAR(64) NOT NULL DEFAULT 'PENDING',
    evidence_ref        VARCHAR(512),
    valid_from          DATE,
    valid_to            DATE,
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_facility_admin_appointment_facility
    ON tuso.facility_admin_appointment (facility_uuid);

CREATE INDEX IF NOT EXISTS idx_facility_admin_appointment_person
    ON tuso.facility_admin_appointment (person_health_id);

-- Only one ACTIVE appointment per (facility, person): re-appointing an already-active administrator
-- is a no-op conflict, but a facility can still have several distinct active administrators.
CREATE UNIQUE INDEX IF NOT EXISTS uq_facility_admin_appointment_active
    ON tuso.facility_admin_appointment (facility_uuid, person_health_id)
    WHERE approval_state = 'ACTIVE';

COMMENT ON TABLE tuso.facility_admin_appointment IS
    'Facility administrator appointments (WS-E). Subject is the canonical facility UUID (tuso.facility.facility_uuid). PENDING on submit, ACTIVE on approve; a facility may have many administrators over time.';
COMMENT ON COLUMN tuso.facility_admin_appointment.facility_uuid IS
    'Canonical facility UUID (tuso.facility.facility_uuid), not the numeric surrogate id.';
COMMENT ON COLUMN tuso.facility_admin_appointment.approval_state IS
    'PENDING (submitted, awaiting approval) | ACTIVE (approved) | REJECTED | REVOKED.';
