-- =============================================
-- Service : inventory-service  (Dura — sovereign stock brain)
-- Database: inventory
-- Flyway  : V007__dura_recalls.sql
-- Prefix  : inv_
--
-- Dura Wave: product recalls. A recall freezes affected batches by
-- transitioning them to RECALLED (a blocked status). Quarantine itself is
-- expressed via the batch governance status (QUARANTINED) on inv_batch_lots.
-- =============================================

CREATE TABLE inv_recall_events (
    recall_id        UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID          NOT NULL,
    recall_reference VARCHAR(100),
    item_code        VARCHAR(50),
    batch_number     VARCHAR(100),
    source           VARCHAR(20)   NOT NULL DEFAULT 'INTERNAL',
    severity         VARCHAR(20)   NOT NULL DEFAULT 'MEDIUM',
    reason           VARCHAR(1000),
    status           VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    affected_batches INTEGER       NOT NULL DEFAULT 0,
    initiated_by     VARCHAR(100),
    closure_note     VARCHAR(1000),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    closed_at        TIMESTAMPTZ
);

CREATE INDEX idx_recalls_tenant_status ON inv_recall_events(tenant_id, status);
CREATE INDEX idx_recalls_item_batch    ON inv_recall_events(tenant_id, item_code, batch_number);
