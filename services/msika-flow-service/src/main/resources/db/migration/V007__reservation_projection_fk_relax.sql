-- V007 — relax mf_reservations FKs for the OF-B11 projection demotion.
--
-- mf_reservations was demoted from an owned lifecycle table to a read
-- projection of DURA (inventory-service) reservations. The projection writer
-- (CommitmentService.writeProjection) records order_id = the OROS order id and
-- line_id = the marketplace offer line id — neither exists in mf_orders /
-- mf_order_lines, so the V001 FKs now cause live FK violations (BUG-3,
-- Wave OF-A M2 proof). Drop both constraints; the ids are cross-service
-- references (OROS + msika-flow offer lines) with no local parent rows.
ALTER TABLE mf_reservations DROP CONSTRAINT IF EXISTS mf_reservations_order_id_fkey;
ALTER TABLE mf_reservations DROP CONSTRAINT IF EXISTS mf_reservations_line_id_fkey;

COMMENT ON COLUMN mf_reservations.order_id IS
    'OROS order id (cross-service reference; FK to mf_orders dropped in V007 — OF-B11 projection demotion)';
COMMENT ON COLUMN mf_reservations.line_id IS
    'Marketplace offer line id (cross-service reference; FK to mf_order_lines dropped in V007 — OF-B11 projection demotion)';
