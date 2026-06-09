-- MVUMO consent evidence, operative documents, theatre consumables, booking idempotency.

ALTER TABLE inpatient.procedure_episode
    ADD COLUMN IF NOT EXISTS mvumo_consent_request_id UUID,
    ADD COLUMN IF NOT EXISTS tshepo_consent_id UUID,
    ADD COLUMN IF NOT EXISTS consent_proof_ref VARCHAR(255),
    ADD COLUMN IF NOT EXISTS consent_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';

CREATE INDEX IF NOT EXISTS idx_procedure_episode_booking
    ON inpatient.procedure_episode (booking_id)
    WHERE booking_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS inpatient.procedure_episode_document (
    document_link_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    episode_id          UUID NOT NULL REFERENCES inpatient.procedure_episode(episode_id) ON DELETE CASCADE,
    document_object_id  UUID NOT NULL,
    document_type       VARCHAR(64) NOT NULL,
    title               VARCHAR(512) NOT NULL,
    storage_key         VARCHAR(1024),
    recorded_by         VARCHAR(128),
    recorded_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_procedure_document_episode ON inpatient.procedure_episode_document (episode_id, recorded_at DESC);

CREATE TABLE IF NOT EXISTS inpatient.procedure_consumable (
    consumable_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    episode_id          UUID NOT NULL REFERENCES inpatient.procedure_episode(episode_id) ON DELETE CASCADE,
    item_code           VARCHAR(64) NOT NULL,
    item_name           VARCHAR(256),
    quantity            INT NOT NULL CHECK (quantity > 0),
    facility_id         UUID NOT NULL,
    store_id            UUID NOT NULL,
    inventory_ledger_ref VARCHAR(128),
    recorded_by         VARCHAR(128),
    recorded_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_procedure_consumable_episode ON inpatient.procedure_consumable (episode_id, recorded_at DESC);
