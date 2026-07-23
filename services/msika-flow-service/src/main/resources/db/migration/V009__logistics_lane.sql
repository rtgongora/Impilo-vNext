-- =============================================
-- Service : msika-flow-service (marketplace transaction plane)
-- Database: msika_flow
-- Flyway  : V009__logistics_lane.sql
--
-- Wave OF-B logistics lane (OF-B17/OF-B18) — §12 delivery orchestration seams:
--
--  * mf_marketplace_requests gains the fulfilment-pathway election and the
--    §11.8 post-commitment delivery-minimum block (name / contact / address).
--    NULL pathway => PICKUP (fail-closed: no delivery is ever dispatched
--    without an explicit election plus delivery details).
--    delivery_details_json is request-row-only truth: NEVER published in the
--    §11.8 invitation snapshot, vendor views or event payloads; disclosed to
--    Nhume only at delivery-task creation (§12.1).
--
--  * mf_selections gains the step-11 dispatch truth (RC-8 idempotency key is
--    the selection id; dispatch_ref stores the Nhume delivery id), the honest
--    DISPATCH_FAILED outcome + retry-sweep marker, the Nhume delivery-status
--    projection (Nhume owns the logistics truth — these columns are a read
--    projection, never a second state machine), the achieved §12.4 PoD grade,
--    the escrow-release seam status and the §12.7 refund-on-return reference.
-- =============================================

ALTER TABLE mf_marketplace_requests
    ADD COLUMN fulfilment_pathway    VARCHAR(16),   -- PICKUP | DELIVERY (NULL => PICKUP, fail-closed)
    ADD COLUMN delivery_details_json JSONB;         -- §11.8 post-commitment column (request-row-only)

ALTER TABLE mf_selections
    ADD COLUMN dispatch_status            VARCHAR(24),  -- DISPATCHED | DISPATCH_FAILED | NOT_REQUIRED
    ADD COLUMN dispatch_failure_code      VARCHAR(64),  -- coded cause when DISPATCH_FAILED
    ADD COLUMN delivery_status            VARCHAR(32),  -- projection of the Nhume 24-status machine
    ADD COLUMN delivery_status_at         TIMESTAMPTZ,
    ADD COLUMN delivery_proof_ref         VARCHAR(64),  -- nhume_delivery_proofs id on PoD
    ADD COLUMN delivery_verification_grade VARCHAR(32), -- achieved §12.4 grade on PoD
    ADD COLUMN escrow_release_status      VARCHAR(32),  -- ESCROW_RELEASE_PENDING | NOT_APPLICABLE | ...
    ADD COLUMN refund_ref                 VARCHAR(64);  -- MusheX refund id for §12.7 refund-on-return

-- Retry sweep input: committed selections whose dispatch failed.
CREATE INDEX idx_mf_sel_dispatch_retry ON mf_selections(dispatch_status)
    WHERE dispatch_status = 'DISPATCH_FAILED';

-- Nhume delivery id -> selection (write-back reception cross-check).
CREATE INDEX idx_mf_sel_dispatch_ref ON mf_selections(dispatch_ref)
    WHERE dispatch_ref IS NOT NULL;
