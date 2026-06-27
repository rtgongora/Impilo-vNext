-- =============================================
-- Service : oros-service
-- Flyway  : V004__imaging_workflow_state.sql
-- Purpose : Fine-grained diagnostic/imaging workflow state (spec §3), tracked as a projection
--           over the coarse canonical order status. Only populated for IMAGING orders.
-- =============================================

ALTER TABLE oros_orders
    ADD COLUMN imaging_state VARCHAR(32);
    -- RECEIVED, ACCEPTED, SCHEDULED, ARRIVED, IN_PROGRESS, PERFORMED, AWAITING_IMAGES,
    -- IMAGES_LINKED, AWAITING_REPORT, PRELIMINARY_REPORT, FINAL_REPORT, RELEASED, ACKNOWLEDGED,
    -- CLOSED + exceptions (RETURNED_FOR_CLARIFICATION, REJECTED, CANCELLED, DEFERRED, NO_SHOW,
    -- FAILED, REASSIGNED, AMENDED, SUPERSEDED)

CREATE INDEX idx_oros_orders_imaging_state
    ON oros_orders (tenant_id, facility_id, imaging_state)
    WHERE imaging_state IS NOT NULL;
