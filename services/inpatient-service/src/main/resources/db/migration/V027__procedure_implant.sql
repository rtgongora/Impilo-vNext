-- inpatient-service V027 — Surgical implant LINK/PROJECTION (Wave 3, spec §8/§9).
-- The implanted device register. inventory-service (DURA) is the SoR for the implant UNIT (UDI/serial/
-- lot/expiry registry) and the patient-implant link; this table holds ONLY the peer ids + a projection
-- of the UDI/serial/lot for the theatre episode, so a recalled UDI/lot can be traced from the case side
-- too. Driven from the operative note's implant fields via TheatreService.recordImplant →
-- InventoryImplantClient. Today the note only had free-text implants; this is the real register.

CREATE TABLE IF NOT EXISTS inpatient.procedure_implant (
    implant_id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                     UUID NOT NULL,
    episode_id                    UUID NOT NULL REFERENCES inpatient.procedure_episode(episode_id) ON DELETE CASCADE,
    seq                           INT NOT NULL DEFAULT 1,
    -- Peer record ids (inventory/DURA = SoR). Null until the owner returns them.
    inventory_implant_unit_id     VARCHAR(128),
    inventory_patient_implant_id  VARCHAR(128),
    -- Projection of the per-UNIT identifiers (finer than a lot).
    udi                           VARCHAR(128),
    serial_number                 VARCHAR(128),
    lot_number                    VARCHAR(128),
    device_type                   VARCHAR(128),
    body_site                     VARCHAR(128),
    laterality                    VARCHAR(16),   -- LEFT | RIGHT | BILATERAL | NA
    implanted_by                  VARCHAR(128),
    implanted_at                  TIMESTAMPTZ,
    status                        VARCHAR(24) NOT NULL DEFAULT 'RECORDED', -- RECORDED | IMPLANTED | UNAVAILABLE
    idempotency_key               VARCHAR(128),
    created_by                    VARCHAR(128),
    created_at                    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_procedure_implant UNIQUE (tenant_id, episode_id, seq)
);
CREATE INDEX IF NOT EXISTS idx_procedure_implant_episode
    ON inpatient.procedure_implant (episode_id);
-- Case-side recall lookup by UDI / lot.
CREATE INDEX IF NOT EXISTS idx_procedure_implant_udi
    ON inpatient.procedure_implant (tenant_id, udi) WHERE udi IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_procedure_implant_lot
    ON inpatient.procedure_implant (tenant_id, lot_number) WHERE lot_number IS NOT NULL;
