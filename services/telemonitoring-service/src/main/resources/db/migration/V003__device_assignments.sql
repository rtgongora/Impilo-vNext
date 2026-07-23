-- =====================================================================================
-- Telemonitoring — Device clinical assignment :: V003 (OF-B24)
--
-- NOTE ON NUMBERING: V002 belongs to the OF-B21 clinical-governance lane (it landed
-- while this migration was in flight); V003 is the next free number after it.
--
-- Three-way device split (Vol II §6.2): iot-ingestion owns the device's DIGITAL identity
-- (DeviceId, telemetry, DLQ); asset-registry owns the PHYSICAL truth (EquipmentId,
-- custody, calibration); telemonitoring owns the CLINICAL binding — DeviceAssignmentId:
-- which patient, which plan, which clinician accountable. This table is that third leg.
--
-- Invariants encoded (§15.6):
--  * One device serves ONE patient at a time: at most one non-terminal assignment per
--    device_ref. Enforced by `device_active_guard` — mirrors device_ref while the
--    assignment is non-terminal, NULLed on RETURNED/LOST — under a UNIQUE constraint
--    (NULLs never collide), plus a Postgres partial-unique backstop on status itself.
--  * A plan MAY hold many devices (no per-plan uniqueness).
--  * Ending an assignment never deletes history — readings keep their assignment-era
--    provenance forever (terminal rows are retained, only the guard is cleared).
--  * owner_health_id in iot-ingestion records POSSESSION only; any code path inferring
--    the clinical subject from it is a defect [R70/OF-G18].
-- =====================================================================================

CREATE TABLE telemonitoring.tm_device_assignments (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL,
    plan_id              UUID NOT NULL REFERENCES telemonitoring.tm_monitoring_plans(id),
    patient_cpid         VARCHAR(64) NOT NULL,        -- CPID-only (R25 rule)
    device_ref           VARCHAR(64) NOT NULL,        -- iot-ingestion iot.device_registry device_id
    asset_ref            VARCHAR(64),                 -- asset-registry asr_equipment equipment_id (nullable)
    status               VARCHAR(24) NOT NULL DEFAULT 'ASSIGNED',
    -- ASSIGNED / ACTIVE / SUSPENDED / RETURNED / LOST (RETURNED and LOST terminal)
    assignment_type      VARCHAR(48),                 -- §15.6: ISSUED_TO_PATIENT / CAREGIVER_HELD / CHW_KIT / COMMUNITY_SHARED
    assigned_by          VARCHAR(128) NOT NULL,       -- assigner identity + accountability (§15.6)
    assigned_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    activated_at         TIMESTAMPTZ,                 -- training/activation moment (§15.6)
    activated_by         VARCHAR(128),
    training_confirmed   BOOLEAN NOT NULL DEFAULT FALSE, -- patient/caregiver training confirmation (§15.6)
    returned_at          TIMESTAMPTZ,                 -- set on RETURNED (and on LOST as closure time)
    lifecycle_reason     VARCHAR(512),                -- reason bound to the last transition
    notes                TEXT,
    -- Race guard: device_ref while non-terminal, NULL once terminal. UNIQUE => one
    -- non-terminal assignment per device, enforced in the DB, portable to H2 tests.
    device_active_guard  VARCHAR(64),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tm_device_active_guard UNIQUE (device_active_guard)
);

CREATE INDEX idx_tm_assign_tenant_plan ON telemonitoring.tm_device_assignments (tenant_id, plan_id);
CREATE INDEX idx_tm_assign_tenant_patient ON telemonitoring.tm_device_assignments (tenant_id, patient_cpid);
CREATE INDEX idx_tm_assign_device ON telemonitoring.tm_device_assignments (device_ref);

-- Belt-and-braces backstop independent of the guard column: even if application code
-- ever mishandles device_active_guard, Postgres itself refuses a second live assignment.
CREATE UNIQUE INDEX uq_tm_assign_one_live_per_device
    ON telemonitoring.tm_device_assignments (device_ref)
    WHERE status NOT IN ('RETURNED', 'LOST');
