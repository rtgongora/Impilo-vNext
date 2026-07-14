-- ============================================================================
-- V034: Virtual-pool queues — PCT materialisation substrate for TUSO virtual
-- service-delivery entities (virtual hospitals).
--
-- A queue is now scoped to EITHER a physical facility OR a virtual pool
-- (the routing seam target_ref, the frozen pool key carried by pool-routed
-- referrals as routing_pool_id). Exactly the same ownership doctrine as
-- facility queues: TUSO owns the definitions (tuso.virtual_service_queue_def),
-- PCT materialises them idempotently by sourceRef ('{vsUid}:{queueKey}'),
-- retires (never deletes), and fails safe when TUSO is unreachable.
--
-- Queue items gain sla_due_at (SLA timer from the queue definition rules) and
-- source_ref (the teleconsult referral id for pool items — pool items track
-- referrals, not patient journeys, so journey_id becomes nullable).
-- ============================================================================

ALTER TABLE pct_queues ALTER COLUMN facility_id DROP NOT NULL;
ALTER TABLE pct_queues ADD COLUMN IF NOT EXISTS virtual_pool_id VARCHAR(128);
ALTER TABLE pct_queues ADD CONSTRAINT chk_pct_queues_scope
    CHECK (facility_id IS NOT NULL OR virtual_pool_id IS NOT NULL);
CREATE INDEX IF NOT EXISTS idx_pct_queues_virtual_pool
    ON pct_queues (tenant_id, virtual_pool_id) WHERE virtual_pool_id IS NOT NULL;

ALTER TABLE pct_queue_items ALTER COLUMN journey_id DROP NOT NULL;
ALTER TABLE pct_queue_items ADD COLUMN IF NOT EXISTS sla_due_at TIMESTAMPTZ;
ALTER TABLE pct_queue_items ADD COLUMN IF NOT EXISTS source_ref VARCHAR(128);
-- SLA sweep: waiting items with a due timer and no escalation yet.
CREATE INDEX IF NOT EXISTS idx_pct_queue_items_sla
    ON pct_queue_items (status, sla_due_at) WHERE sla_due_at IS NOT NULL AND escalated_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_pct_queue_items_source_ref
    ON pct_queue_items (tenant_id, source_ref) WHERE source_ref IS NOT NULL;
