-- =============================================
-- Service : inventory-service  (Dura — sovereign stock brain)
-- Database: inventory
-- Flyway  : V014__reservation_events_expiry.sql
-- Prefix  : inv_
--
-- OF-B11 (Vol II §8.9): DURA inv_stock_reservations is the SOLE reservation
-- ledger for the fulfilment marketplace; msika-flow's mf_reservations is a
-- demoted read-projection fed by the reservation outbox events added in this
-- wave (inventory.reservation.{created,released,consumed,expired}.v1 plus the
-- legacy inventory.reservation.status_changed shape).
--
-- Marketplace commitments reserve with ref_type = 'MARKETPLACE_SELECTION'
-- (ref_id = selection id); controlled marketplace commitments additionally
-- write the V013 controlled register with ref_type = 'MARKETPLACE_COMMITMENT'.
--
-- This migration adds the sweep index for the scheduled TTL expiry
-- (ACTIVE + lapsed expires_at → EXPIRED, evented — no zombie holds).
-- =============================================

CREATE INDEX IF NOT EXISTS idx_inv_reservation_expiry
    ON inv_stock_reservations(status, expires_at)
    WHERE expires_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_inv_reservation_ref
    ON inv_stock_reservations(ref_type, ref_id);
