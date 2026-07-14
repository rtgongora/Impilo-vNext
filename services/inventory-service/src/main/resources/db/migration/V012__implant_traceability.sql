-- =============================================
-- Service : inventory-service  (Dura — sovereign stock brain)
-- Database: inventory
-- Flyway  : V012__implant_traceability.sql
-- Prefix  : inv_
--
-- Dura Wave 3: implant / device UDI + serial traceability.
--
-- Implantable devices are tracked at the physical UNIT level (finer than a lot):
-- each unit carries a UDI (Unique Device Identifier) and/or a serial number.
-- inv_implant_registry holds one row per physical unit; inv_patient_implant
-- links a unit to the patient/episode it was implanted in. Together they support
-- forward recall traceability: given a recalled UDI or lot, find every affected
-- patient.
-- =============================================

CREATE TABLE inv_implant_registry (
    implant_unit_id  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID          NOT NULL,
    udi              VARCHAR(128),
    serial_number    VARCHAR(128),
    lot_number       VARCHAR(128),
    expiry_date      DATE,
    item_code        VARCHAR(64),
    device_type      VARCHAR(128),
    brand            VARCHAR(128),
    model            VARCHAR(128),
    batch_lot_id     UUID,
    status           VARCHAR(24)   NOT NULL DEFAULT 'REGISTERED',
    created_by       VARCHAR(128),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- UDI/serial uniqueness is per physical unit, per tenant (partial: only when present).
CREATE UNIQUE INDEX uq_implant_registry_udi
    ON inv_implant_registry(tenant_id, udi)
    WHERE udi IS NOT NULL;
CREATE UNIQUE INDEX uq_implant_registry_serial
    ON inv_implant_registry(tenant_id, serial_number)
    WHERE serial_number IS NOT NULL;
CREATE INDEX idx_implant_registry_lot       ON inv_implant_registry(tenant_id, lot_number);
CREATE INDEX idx_implant_registry_itemcode  ON inv_implant_registry(tenant_id, item_code);

CREATE TABLE inv_patient_implant (
    patient_implant_id  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID          NOT NULL,
    implant_unit_id     UUID          NOT NULL REFERENCES inv_implant_registry(implant_unit_id),
    patient_cpid        VARCHAR(64)   NOT NULL,
    episode_ref         VARCHAR(128),
    body_site           VARCHAR(128),
    laterality          VARCHAR(16),
    implanted_by        VARCHAR(128),
    implanted_at        TIMESTAMPTZ,
    status              VARCHAR(24)   DEFAULT 'IMPLANTED',
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_patient_implant_patient ON inv_patient_implant(tenant_id, patient_cpid);
CREATE INDEX idx_patient_implant_unit    ON inv_patient_implant(implant_unit_id);
