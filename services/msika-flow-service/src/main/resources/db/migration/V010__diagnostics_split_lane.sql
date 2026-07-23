-- =============================================
-- Service : msika-flow-service (marketplace transaction plane)
-- Database: msika_flow
-- Flyway  : V010__diagnostics_split_lane.sql
--
-- Wave OF-B diagnostics/split lane (OF-B13 + OF-B14):
--  * OF-B13 — DIAGNOSTICS request/offer completeness: the request records the
--    phlebotomy home-collection capability requirement (§11.8 allow-listed
--    capability flag; also carried in published_lines_json.capabilityFlags);
--    a vendor offer may declare home collection with a concrete collection
--    window; the committed selection records the fulfilment expectation
--    (collection mode + window) and the OROS order ref so specimen/result flow
--    stays on the LIVE OROS diagnostics spine — no new result machinery here.
--  * OF-B14 — split fulfilment: selections gain the split-group id linking the
--    members of one patient-chosen offer combination (§8.6.4).
--
-- RC-1 EXTENSION (OF-B14, binding): the Wave OF-A single-active-selection-per-
-- request invariant becomes per-LINE-coverage — no two live (non-FAILED/
-- CANCELLED) selections may cover the same published request line. That
-- invariant spans mf_selections -> mf_fulfillment_offer_lines and is NOT
-- expressible as a partial index on one table, so it is enforced in code
-- (CommitmentService.assertNoActiveLineOverlap) + tests. What IS expressible
-- stays in the schema: at most one live selection per (request, offer).
-- =============================================

ALTER TABLE mf_marketplace_requests
    ADD COLUMN home_collection_requested BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE mf_fulfillment_offers
    ADD COLUMN home_collection          BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN collection_window_start  TIMESTAMPTZ,
    ADD COLUMN collection_window_end    TIMESTAMPTZ;

ALTER TABLE mf_selections
    ADD COLUMN split_group_id           VARCHAR(26),
    ADD COLUMN oros_order_id            VARCHAR(64),
    ADD COLUMN collection_mode          VARCHAR(24),   -- HOME_COLLECTION | WALK_IN (DIAGNOSTICS profile)
    ADD COLUMN collection_window_start  TIMESTAMPTZ,
    ADD COLUMN collection_window_end    TIMESTAMPTZ;

-- OF-B14: split members are multiple live selections on one request — the old
-- one-per-request unique gate must go; its honest replacement is per-offer.
DROP INDEX IF EXISTS uq_mf_selection_single_active;

CREATE UNIQUE INDEX uq_mf_selection_offer_active
    ON mf_selections(request_id, offer_id)
    WHERE status NOT IN ('FAILED', 'CANCELLED');

CREATE INDEX idx_mf_selection_split_group
    ON mf_selections(split_group_id)
    WHERE split_group_id IS NOT NULL;
