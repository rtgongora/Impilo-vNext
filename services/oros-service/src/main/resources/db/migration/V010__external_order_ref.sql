-- =============================================
-- Service : oros-service
-- Flyway  : V010__external_order_ref.sql
-- Purpose : Idempotency key for externally-originated orders (FHIR ServiceRequest-inbound / HL7
--           ORM-inbound): the external system's order identifier, unique per tenant, so a
--           re-sent inbound message resolves to the same order instead of duplicating it.
-- =============================================

ALTER TABLE oros_orders
    ADD COLUMN external_order_ref VARCHAR(128);

CREATE UNIQUE INDEX uq_oros_orders_external_ref
    ON oros_orders (tenant_id, external_order_ref)
    WHERE external_order_ref IS NOT NULL;
